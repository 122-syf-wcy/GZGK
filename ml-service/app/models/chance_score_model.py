from __future__ import annotations

from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.metrics import brier_score_loss, roc_auc_score
from sklearn.model_selection import train_test_split


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
    confidence_level = "高" if confidence_score >= 85 else "中" if confidence_score >= 70 else "低" if confidence_score >= 55 else "数据不足"
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


# 弱监督标签 label = (min_rank - candidate_rank > 3000) 由这些列直接构成；
# 把它们放进特征等于让模型背规则本身（AUC≈1 的假象，无预测意义）。
_WEAK_LABEL_LEAKAGE_COLS = {"min_rank", "candidate_rank", "label", "sample_weight"}
# 弱监督场景允许的特征白名单：与线上推理构造的衍生列一致（predict.py _batch_predict_chance），
# 且不含标签构成成分。同时保证训练/推理特征空间一致（此前训练用全表列、推理只构造 6 列，
# 绝大多数训练列线上恒为 0）。
_WEAK_LABEL_SAFE_FEATURES = ["rank_volatility_3y", "plan_change_rate", "major_hot_score", "data_confidence"]


def train_chance_model(data_path: str | None, output_dir: str, allow_weak_label: bool = False) -> dict:
    try:
        from lightgbm import LGBMClassifier
    except Exception as exc:
        return {"status": "failed", "reason": f"lightgbm unavailable: {exc}"}

    if not data_path:
        return {"status": "skipped", "reason": "dataPath is required"}
    df = pd.read_csv(data_path)
    weak_label = "label" not in df.columns
    if weak_label:
        if not allow_weak_label:
            # 默认拒绝：ETL 的 candidate_rank 由 min_rank 合成（0.95×min_rank），弱监督标签
            # 退化为 min_rank 阈值规则，任何含 rank 列的训练都是标签泄漏；在接入真实录取
            # 标签（label 列）之前不产出模型文件，避免"注册即热加载"误上线泄漏模型。
            return {
                "status": "blocked_weak_label",
                "reason": "训练集无真实 label 列，弱监督标签由 rank 差构成，训练存在标签泄漏；"
                          "接入真实录取标签后再训练，或显式传 allow_weak_label=True（仅研究用途，"
                          "特征将强制走去泄漏白名单）",
            }
        if "candidate_rank" not in df.columns or "min_rank" not in df.columns:
            return {"status": "failed", "reason": "missing label or weak-supervision columns"}
        diff = df["min_rank"] - df["candidate_rank"]
        df["label"] = (diff > 3000).astype(int)
        df["sample_weight"] = np.where(diff.abs() < 3000, 0.35, 1.0)
        feature_cols = [col for col in _WEAK_LABEL_SAFE_FEATURES if col in df.columns]
        if not feature_cols:
            return {"status": "failed", "reason": "去泄漏白名单特征在训练集中不存在"}
    else:
        # 真实录取标签下 rank 列是合法特征，保持原始全列口径
        feature_cols = [col for col in df.columns if col not in {"label", "sample_weight"}]
    X = pd.get_dummies(df[feature_cols].fillna(0))
    y = df["label"]
    weights = df.get("sample_weight")
    X_train, X_test, y_train, y_test, w_train, _ = train_test_split(
        X, y, weights, test_size=0.2, random_state=42, stratify=y if y.nunique() > 1 else None
    )
    model = LGBMClassifier(n_estimators=180, learning_rate=0.05, random_state=42)
    model.fit(X_train, y_train, sample_weight=w_train)
    proba = model.predict_proba(X_test)[:, 1]
    metrics = {"brier": float(brier_score_loss(y_test, proba))}
    if y_test.nunique() > 1:
        metrics["auc"] = float(roc_auc_score(y_test, proba))
    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)
    model_path = out / "chance_score_lgbm.joblib"
    joblib.dump({"model": model, "columns": X.columns.tolist()}, model_path)
    return {
        "status": "ok",
        "modelVersion": "chance-score-v1.0.0",
        "modelType": "LightGBMClassifier",
        "modelPath": str(model_path),
        "modelFilePath": str(model_path),
        "trainDataCount": int(len(df)),
        "metrics": metrics,
    }
