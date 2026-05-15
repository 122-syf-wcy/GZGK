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


def train_chance_model(data_path: str | None, output_dir: str) -> dict:
    try:
        from lightgbm import LGBMClassifier
    except Exception as exc:
        return {"status": "failed", "reason": f"lightgbm unavailable: {exc}"}

    if not data_path:
        return {"status": "skipped", "reason": "dataPath is required"}
    df = pd.read_csv(data_path)
    # 弱监督 label 由 min_rank - candidate_rank 推导,
    # 因此训练特征里必须显式剔除这两列,否则 LightGBM 会直接学到
    # f(min_rank, candidate_rank) > 3000 这条规则本身,
    # 形成 target leakage (生产线上观察到 AUC=0.99999, Brier=0.001 即是此症状)。
    weak_supervision_leak_cols = {"min_rank", "candidate_rank"}
    if "label" not in df.columns:
        if "candidate_rank" not in df.columns or "min_rank" not in df.columns:
            return {"status": "failed", "reason": "missing label or weak-supervision columns"}
        diff = df["min_rank"] - df["candidate_rank"]
        df["label"] = (diff > 3000).astype(int)
        df["sample_weight"] = np.where(diff.abs() < 3000, 0.35, 1.0)
    excluded = {"label", "sample_weight"} | weak_supervision_leak_cols
    feature_cols = [col for col in df.columns if col not in excluded]
    if not feature_cols:
        return {"status": "failed", "reason": "no usable feature columns after leak guard"}
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
