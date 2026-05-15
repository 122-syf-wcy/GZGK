"""GZLY chance-score 模型训练与规则兜底。

历史问题（v1.0.0/v1.0.1）：
- weak supervision label = (min_rank - candidate_rank) > 3000；
- ETL 中 candidate_rank = min_rank * 0.95（一一对应 min_rank）；
- 训练把全部 CSV 列作为特征，导致 LightGBM 只要看 min_rank 或其 lag 就能完美还原 label，
  AUC=0.99999、Brier=0.001 是典型 target leakage 指纹。

v2 重训方案（本模块）：
- 对每条真实录取行扩样多个 candidate_rank（独立采样，与 min_rank 解耦）；
- 严格剔除当年 min_rank 及其派生列，inference 也走相同特征集；
- 评估指标除 AUC / Brier 外补 ECE 与 Hit-Rate@K，对应贵州研究报告 R5；
- 训练 / 测试按 year 时间切分，避免同 (school, major) 跨年泄漏。
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.metrics import brier_score_loss, roc_auc_score


CHANCE_FEATURE_COLUMNS: list[str] = [
    "candidate_rank",
    "current_plan_count",
    "last_year_plan_count",
    "plan_change_rate",
    "min_rank_lag_1",
    "min_rank_lag_2",
    "min_rank_lag_3",
    "avg_rank_lag_3",
    "median_rank_lag_3",
    "rank_volatility_3y",
    "rank_trend_3y",
    "major_hot_score",
    "school_ranking_score",
    "employment_score",
    "is_985",
    "is_211",
    "is_double_first_class",
    "is_public",
    "has_supplement_lag_1",
    "first_round_full_lag_1",
]

CHANCE_CATEGORICAL_COLUMNS: list[str] = [
    "batch_code",
    "candidate_type",
    "subject_type",
    # subject_regime: "new" = 2024 起贵州 3+1+2 新高考；"old" = 2021-2023 文/理。
    # 推理永远走 "new"，让模型用 categorical 维度自适应历史样本权重，避免老制度位次曲线
    # 直接拉偏新制度推断。
    "subject_regime",
    "school_level",
]

CHANCE_FORBIDDEN_LEAK_COLUMNS: frozenset[str] = frozenset(
    {"min_rank", "label", "sample_weight"}
)

CHANCE_MODEL_VERSION = "chance-score-v2.1.0"


def chance_from_rank_diff(rank_diff: int, candidate_rank: int, item: dict[str, Any]) -> dict[str, Any]:
    rank_scale = max(1000.0, float(candidate_rank or 30000) * 0.08)
    base = 1.0 / (1.0 + np.exp(-(rank_diff / rank_scale)))
    plan_change = float(item.get("planChangeRate") or 0)
    volatility = float(item.get("rankVolatility3y") or item.get("volatilityRisk") or 0)
    confidence_raw = float(item.get("dataConfidence") or item.get("dataConfidenceScore") or 60)
    confidence = confidence_raw / 100.0 if confidence_raw > 1 else confidence_raw
    hot_trend = float(item.get("majorHotScore") or item.get("hotTrendScore") or 0)
    volatility_penalty = min(0.20, max(0.0, volatility) * 0.5)
    plan_penalty = min(0.15, abs(plan_change) * 0.5) if plan_change < 0 else 0.0
    data_penalty = (1.0 - max(0.0, min(1.0, confidence))) * 0.15
    hot_penalty = min(0.10, max(0.0, hot_trend) * 0.1)
    internal = float(np.clip(base - volatility_penalty - plan_penalty - data_penalty - hot_penalty, 0.01, 0.99))
    score = int(round(internal * 100))
    if score >= 90:
        level, risk = "兜底参考", "很低"
    elif score >= 75:
        level, risk = "稳妥参考", "较低"
    elif score >= 50:
        level, risk = "适中", "中等"
    else:
        level, risk = "冲刺参考", "较高"
    confidence_score = int(round(max(0.0, min(1.0, confidence)) * 100))
    confidence_level = (
        "高" if confidence_score >= 85 else "中" if confidence_score >= 70 else "低" if confidence_score >= 55 else "数据不足"
    )
    return {
        "internal": internal,
        "chanceScore": score,
        "chanceLevel": level,
        "riskLevel": risk,
        "dataConfidence": confidence_score,
        "confidenceLevel": confidence_level,
        "volatilityRisk": round(volatility, 4),
        "hotTrendScore": hot_trend,
    }


def expected_calibration_error(y_true: np.ndarray, y_prob: np.ndarray, n_bins: int = 10) -> float:
    """实际命中率与预测概率的 bin-wise 加权平均偏差，越接近 0 越校准。"""
    y_true = np.asarray(y_true).astype(float)
    y_prob = np.clip(np.asarray(y_prob).astype(float), 0.0, 1.0)
    if y_true.size == 0:
        return 0.0
    bin_edges = np.linspace(0.0, 1.0, n_bins + 1)
    ece = 0.0
    total = y_true.size
    for i in range(n_bins):
        lo, hi = bin_edges[i], bin_edges[i + 1]
        mask = (y_prob > lo) & (y_prob <= hi) if i > 0 else (y_prob >= lo) & (y_prob <= hi)
        if not np.any(mask):
            continue
        bucket_prob = y_prob[mask].mean()
        bucket_acc = y_true[mask].mean()
        ece += (mask.sum() / total) * abs(bucket_prob - bucket_acc)
    return float(ece)


def hit_rate_at_k(y_true: np.ndarray, y_prob: np.ndarray, k: int = 10) -> float:
    """把样本按预测概率倒序排，取 top-K 内真实命中的比例（K 不大于样本数）。"""
    y_true = np.asarray(y_true).astype(int)
    y_prob = np.asarray(y_prob).astype(float)
    if y_true.size == 0:
        return 0.0
    actual_k = min(k, y_true.size)
    order = np.argsort(-y_prob)[:actual_k]
    return float(y_true[order].mean())


def _coerce_numeric_columns(df: pd.DataFrame, columns: list[str]) -> pd.DataFrame:
    out = df.copy()
    for col in columns:
        if col in out.columns:
            out[col] = pd.to_numeric(out[col], errors="coerce")
        else:
            out[col] = np.nan
    return out


def _expand_candidate_rank_samples(
    df: pd.DataFrame,
    samples_per_row: int = 5,
    rng_seed: int = 42,
) -> pd.DataFrame:
    """对每条录取记录扩样多个 candidate_rank，与 min_rank 解耦。

    candidate_rank ~ Uniform(max(1, 0.4*min_rank), 1.8*min_rank)。
    label = (candidate_rank <= min_rank)，sample_weight 在阈值附近降权。
    """
    if df.empty:
        return df.assign(label=pd.Series(dtype=int), sample_weight=pd.Series(dtype=float))
    rng = np.random.default_rng(rng_seed)
    repeated = df.loc[df.index.repeat(samples_per_row)].reset_index(drop=True)
    min_rank = repeated["min_rank"].astype(float)
    lower = np.maximum(1.0, min_rank * 0.4)
    upper = np.maximum(lower + 1.0, min_rank * 1.8)
    candidate_rank = rng.uniform(lower, upper)
    candidate_rank = np.maximum(1.0, np.round(candidate_rank)).astype(int)
    repeated = repeated.drop(columns=[c for c in ("candidate_rank",) if c in repeated.columns])
    repeated["candidate_rank"] = candidate_rank
    diff = repeated["min_rank"].astype(float) - candidate_rank
    repeated["label"] = (diff >= 0).astype(int)
    margin = np.abs(diff)
    repeated["sample_weight"] = np.where(margin < 3000, 0.5, 1.0)
    return repeated


def _train_test_split_by_year(df: pd.DataFrame, test_year_quota: float = 0.2) -> tuple[pd.DataFrame, pd.DataFrame]:
    if "year" not in df.columns or df["year"].dropna().empty:
        idx = np.arange(len(df))
        np.random.default_rng(42).shuffle(idx)
        cut = int(round(len(df) * (1.0 - test_year_quota)))
        return df.iloc[idx[:cut]].copy(), df.iloc[idx[cut:]].copy()
    years_sorted = sorted({int(y) for y in df["year"].dropna().astype(int).unique().tolist()})
    if len(years_sorted) <= 1:
        idx = np.arange(len(df))
        np.random.default_rng(42).shuffle(idx)
        cut = int(round(len(df) * (1.0 - test_year_quota)))
        return df.iloc[idx[:cut]].copy(), df.iloc[idx[cut:]].copy()
    test_year = years_sorted[-1]
    test_df = df[df["year"].astype(int) == test_year].copy()
    train_df = df[df["year"].astype(int) != test_year].copy()
    if len(test_df) < max(50, int(0.05 * len(df))) and len(years_sorted) >= 2:
        test_years = set(years_sorted[-2:])
        test_df = df[df["year"].astype(int).isin(test_years)].copy()
        train_df = df[~df["year"].astype(int).isin(test_years)].copy()
    return train_df, test_df


def _prepare_feature_frame(df: pd.DataFrame) -> tuple[pd.DataFrame, list[str]]:
    """剔除任何泄漏列，统一列顺序，对类别列做 one-hot。"""
    df = _coerce_numeric_columns(df, CHANCE_FEATURE_COLUMNS)
    keep_columns = [c for c in CHANCE_FEATURE_COLUMNS if c in df.columns]
    cat_columns = [c for c in CHANCE_CATEGORICAL_COLUMNS if c in df.columns]
    base = df[keep_columns].copy()
    for col in keep_columns:
        base[col] = base[col].fillna(0)
    if cat_columns:
        cat_df = pd.get_dummies(df[cat_columns].astype(str).fillna("UNKNOWN"), prefix=cat_columns)
        base = pd.concat([base.reset_index(drop=True), cat_df.reset_index(drop=True)], axis=1)
    return base, list(base.columns)


def train_chance_model(data_path: str | None, output_dir: str) -> dict:
    try:
        from lightgbm import LGBMClassifier
    except Exception as exc:  # noqa: BLE001 - record import failure
        return {"status": "failed", "reason": f"lightgbm unavailable: {exc}"}

    if not data_path:
        return {"status": "skipped", "reason": "dataPath is required"}
    df = pd.read_csv(data_path)
    if "min_rank" not in df.columns:
        return {"status": "failed", "reason": "training CSV missing min_rank label column"}

    leak_present = [c for c in CHANCE_FORBIDDEN_LEAK_COLUMNS if c in df.columns and c != "min_rank"]
    if leak_present:
        df = df.drop(columns=leak_present)

    df = df[pd.to_numeric(df["min_rank"], errors="coerce").gt(0)].copy()
    if df.empty:
        return {"status": "failed", "reason": "no rows with positive min_rank label"}

    train_raw, test_raw = _train_test_split_by_year(df)
    if train_raw.empty or test_raw.empty:
        return {"status": "failed", "reason": "insufficient rows after train/test split"}

    expanded_train = _expand_candidate_rank_samples(train_raw, samples_per_row=5, rng_seed=42)
    expanded_test = _expand_candidate_rank_samples(test_raw, samples_per_row=3, rng_seed=7)

    if expanded_train["label"].nunique() < 2 or expanded_test["label"].nunique() < 2:
        return {"status": "failed", "reason": "expanded labels are degenerate (single class)"}

    X_train_df, train_columns = _prepare_feature_frame(expanded_train)
    X_test_df, test_columns = _prepare_feature_frame(expanded_test)
    all_columns = list(dict.fromkeys(train_columns + test_columns))
    X_train_df = X_train_df.reindex(columns=all_columns, fill_value=0)
    X_test_df = X_test_df.reindex(columns=all_columns, fill_value=0)

    y_train = expanded_train["label"].astype(int).to_numpy()
    y_test = expanded_test["label"].astype(int).to_numpy()
    w_train = expanded_train["sample_weight"].astype(float).to_numpy()

    if X_train_df.shape[1] == 0:
        return {"status": "failed", "reason": "no usable feature columns after leak guard"}

    model = LGBMClassifier(
        n_estimators=240,
        learning_rate=0.05,
        num_leaves=63,
        min_child_samples=40,
        reg_lambda=1.0,
        random_state=42,
    )
    model.fit(X_train_df, y_train, sample_weight=w_train)
    proba_test = model.predict_proba(X_test_df)[:, 1]

    metrics: dict[str, float] = {
        "brier": float(brier_score_loss(y_test, proba_test)),
        "ece": expected_calibration_error(y_test, proba_test, n_bins=10),
        "hitRate@10": hit_rate_at_k(y_test, proba_test, k=10),
        "hitRate@30": hit_rate_at_k(y_test, proba_test, k=30),
        "positiveRate": float(np.mean(y_test == 1)),
        "trainRows": int(len(X_train_df)),
        "testRows": int(len(X_test_df)),
    }
    if pd.Series(y_test).nunique() > 1:
        metrics["auc"] = float(roc_auc_score(y_test, proba_test))

    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)
    model_path = out / "chance_score_lgbm.joblib"
    payload = {
        "model": model,
        "columns": list(X_train_df.columns),
        "modelVersion": CHANCE_MODEL_VERSION,
        "featureSchema": {
            "numeric": [c for c in CHANCE_FEATURE_COLUMNS if c in df.columns],
            "categorical": [c for c in CHANCE_CATEGORICAL_COLUMNS if c in df.columns],
            "label": "candidate_rank <= min_rank (expanded weak supervision)",
            "leakGuard": sorted(CHANCE_FORBIDDEN_LEAK_COLUMNS),
        },
    }
    joblib.dump(payload, model_path)
    return {
        "status": "ok",
        "modelVersion": CHANCE_MODEL_VERSION,
        "modelType": "LightGBMClassifier",
        "modelPath": str(model_path),
        "modelFilePath": str(model_path),
        "trainDataCount": int(len(df)),
        "metrics": metrics,
        "featureSchema": payload["featureSchema"],
    }


def train_chance_model_xgb(data_path: str | None, output_dir: str) -> dict:
    """与 train_chance_model 同口径但用 XGBoost，对应贵州研究报告"概率融合 XGBoost 主模型"。

    特征工程 / 时间切分 / candidate_rank 扩样策略与 LightGBM 路径完全一致，仅替换底层学习器；
    输出 ``chance_score_xgb.joblib``，metrics 含 auc/brier/ece/hitRate@K，可与 LightGBM 同期对比。
    """
    try:
        from xgboost import XGBClassifier
    except Exception as exc:  # noqa: BLE001 - record import failure
        return {"status": "failed", "reason": f"xgboost unavailable: {exc}"}

    if not data_path:
        return {"status": "skipped", "reason": "dataPath is required"}
    df = pd.read_csv(data_path)
    if "min_rank" not in df.columns:
        return {"status": "failed", "reason": "training CSV missing min_rank label column"}

    leak_present = [c for c in CHANCE_FORBIDDEN_LEAK_COLUMNS if c in df.columns and c != "min_rank"]
    if leak_present:
        df = df.drop(columns=leak_present)

    df = df[pd.to_numeric(df["min_rank"], errors="coerce").gt(0)].copy()
    if df.empty:
        return {"status": "failed", "reason": "no rows with positive min_rank label"}

    train_raw, test_raw = _train_test_split_by_year(df)
    if train_raw.empty or test_raw.empty:
        return {"status": "failed", "reason": "insufficient rows after train/test split"}

    expanded_train = _expand_candidate_rank_samples(train_raw, samples_per_row=5, rng_seed=42)
    expanded_test = _expand_candidate_rank_samples(test_raw, samples_per_row=3, rng_seed=7)

    if expanded_train["label"].nunique() < 2 or expanded_test["label"].nunique() < 2:
        return {"status": "failed", "reason": "expanded labels are degenerate (single class)"}

    X_train_df, train_columns = _prepare_feature_frame(expanded_train)
    X_test_df, test_columns = _prepare_feature_frame(expanded_test)
    all_columns = list(dict.fromkeys(train_columns + test_columns))
    X_train_df = X_train_df.reindex(columns=all_columns, fill_value=0)
    X_test_df = X_test_df.reindex(columns=all_columns, fill_value=0)

    y_train = expanded_train["label"].astype(int).to_numpy()
    y_test = expanded_test["label"].astype(int).to_numpy()
    w_train = expanded_train["sample_weight"].astype(float).to_numpy()

    if X_train_df.shape[1] == 0:
        return {"status": "failed", "reason": "no usable feature columns after leak guard"}

    model = XGBClassifier(
        n_estimators=300,
        max_depth=6,
        learning_rate=0.05,
        subsample=0.85,
        colsample_bytree=0.85,
        reg_lambda=1.0,
        random_state=42,
        eval_metric="logloss",
        n_jobs=2,
        tree_method="hist",
    )
    model.fit(X_train_df, y_train, sample_weight=w_train)
    proba_test = model.predict_proba(X_test_df)[:, 1]

    metrics: dict[str, float] = {
        "brier": float(brier_score_loss(y_test, proba_test)),
        "ece": expected_calibration_error(y_test, proba_test, n_bins=10),
        "hitRate@10": hit_rate_at_k(y_test, proba_test, k=10),
        "hitRate@30": hit_rate_at_k(y_test, proba_test, k=30),
        "positiveRate": float(np.mean(y_test == 1)),
        "trainRows": int(len(X_train_df)),
        "testRows": int(len(X_test_df)),
    }
    if pd.Series(y_test).nunique() > 1:
        metrics["auc"] = float(roc_auc_score(y_test, proba_test))

    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)
    model_path = out / "chance_score_xgb.joblib"
    version = CHANCE_MODEL_VERSION.replace("v2", "xgb-v2")
    payload = {
        "model": model,
        "columns": list(X_train_df.columns),
        "modelVersion": version,
        "featureSchema": {
            "numeric": [c for c in CHANCE_FEATURE_COLUMNS if c in df.columns],
            "categorical": [c for c in CHANCE_CATEGORICAL_COLUMNS if c in df.columns],
            "label": "candidate_rank <= min_rank (expanded weak supervision)",
            "leakGuard": sorted(CHANCE_FORBIDDEN_LEAK_COLUMNS),
            "engine": "xgboost",
        },
    }
    joblib.dump(payload, model_path)
    return {
        "status": "ok",
        "modelVersion": version,
        "modelType": "XGBClassifier",
        "modelPath": str(model_path),
        "modelFilePath": str(model_path),
        "trainDataCount": int(len(df)),
        "metrics": metrics,
        "featureSchema": payload["featureSchema"],
    }
