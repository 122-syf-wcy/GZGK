from __future__ import annotations

from typing import Any

import numpy as np
import pandas as pd
from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.models.chance_score_model import (
    CHANCE_CATEGORICAL_COLUMNS,
    CHANCE_FEATURE_COLUMNS,
    CHANCE_MODEL_VERSION,
    chance_from_rank_diff,
)
from app.models.rank_prediction_model import predict_min_rank
from app.utils.model_loader import get_chance_model, get_rank_model, model_status

router = APIRouter()


class PredictRequest(BaseModel):
    modelVersion: str = "latest"
    candidate: dict[str, Any] = Field(default_factory=dict)
    items: list[dict[str, Any]] = Field(default_factory=list)


@router.get("/predict/status")
def predict_status() -> dict[str, Any]:
    """模型加载状态调试接口。"""
    return model_status()


@router.post("/predict/batch")
def predict_batch(req: PredictRequest) -> dict:
    """批量预测：模型存在时用 LightGBM；不存在时回退规则公式。返回 modelVersion 反映实际使用版本。"""
    candidate_rank = int(req.candidate.get("rank") or req.candidate.get("candidateRank") or 0)
    candidate_score = int(req.candidate.get("score") or req.candidate.get("candidateScore") or 0)

    rank_model_payload = get_rank_model()
    chance_model_payload = get_chance_model()

    rank_predictions = _batch_predict_rank(req.items, rank_model_payload)
    chance_predictions = _batch_predict_chance(req.items, candidate_rank, rank_predictions, chance_model_payload)

    using_rank_model = rank_model_payload is not None
    using_chance_model = chance_model_payload is not None
    model_version = _resolve_model_version(using_rank_model, using_chance_model)

    out = []
    for idx, item in enumerate(req.items):
        predicted_rank, confidence = rank_predictions[idx]
        rank_diff = predicted_rank - candidate_rank if candidate_rank > 0 and predicted_rank > 0 else 0
        chance = chance_predictions[idx]
        out.append(
            {
                "itemId": str(item.get("itemId") or item.get("id") or idx),
                "predictedMinRank": predicted_rank,
                "internalChanceScore": round(chance["internal"], 4),
                "chanceScore": chance["chanceScore"],
                "chanceLevel": chance["chanceLevel"],
                "riskLevel": chance["riskLevel"],
                "dataConfidence": chance["dataConfidence"],
                "confidenceLevel": chance["confidenceLevel"],
                "predictionConfidence": confidence,
                "volatilityRisk": chance["volatilityRisk"],
                "hotTrendScore": chance["hotTrendScore"],
                "featureContribution": {
                    "rankDiff": rank_diff,
                    "planChangeRate": item.get("planChangeRate", 0),
                    "majorHotScore": item.get("majorHotScore", 0),
                    "schoolLevel": item.get("schoolLevel", ""),
                    "modelUsed": model_version,
                },
                "candidateScore": candidate_score,
            }
        )
    return {
        "modelVersion": model_version,
        "rankModelUsed": using_rank_model,
        "chanceModelUsed": using_chance_model,
        "predictions": out,
    }


def _batch_predict_rank(items: list[dict[str, Any]], payload: dict | None) -> list[tuple[int, float]]:
    """优先用训练好的 LightGBM；模型推理失败 / 缺特征时单条降级到 predict_min_rank。"""
    fallback = [predict_min_rank(item) for item in items]
    if not payload or not items:
        return fallback
    try:
        model = payload["model"]
        features = payload.get("features") or []
        df = pd.DataFrame([{f: item.get(_camel_to_snake(f), item.get(f)) for f in features} for item in items])
        if df.empty:
            return fallback
        preds = model.predict(df)
        out: list[tuple[int, float]] = []
        for i, val in enumerate(preds):
            try:
                rank = int(round(float(val)))
                if rank <= 0:
                    out.append(fallback[i])
                else:
                    out.append((rank, 0.85))
            except (TypeError, ValueError):
                out.append(fallback[i])
        return out
    except Exception:  # noqa: BLE001 - defensive: any model failure → fallback
        return fallback


def _batch_predict_chance(items: list[dict[str, Any]],
                          candidate_rank: int,
                          rank_predictions: list[tuple[int, float]],
                          payload: dict | None) -> list[dict[str, Any]]:
    """ChanceScore 永远先用规则公式（合规可解释）；如果模型可用则做加权融合：0.5*rule + 0.5*model。"""
    rule_results: list[dict[str, Any]] = []
    for idx, item in enumerate(items):
        predicted_rank, _ = rank_predictions[idx]
        rank_diff = predicted_rank - candidate_rank if candidate_rank > 0 and predicted_rank > 0 else 0
        rule_results.append(chance_from_rank_diff(rank_diff, candidate_rank, item))

    if not payload or not items:
        return rule_results

    try:
        model = payload["model"]
        columns = payload.get("columns") or []
        feature_rows = []
        cat_rows = []
        for idx, item in enumerate(items):
            row = {col: _coerce_float(item.get(_snake_to_camel(col), item.get(col))) for col in CHANCE_FEATURE_COLUMNS}
            row["candidate_rank"] = float(candidate_rank or row.get("candidate_rank") or 30000)
            feature_rows.append(row)
            cat_rows.append({col: str(item.get(_snake_to_camel(col), item.get(col)) or "UNKNOWN")
                             for col in CHANCE_CATEGORICAL_COLUMNS})
        numeric_df = pd.DataFrame(feature_rows).fillna(0)
        cat_df = pd.get_dummies(pd.DataFrame(cat_rows).fillna("UNKNOWN"), prefix=CHANCE_CATEGORICAL_COLUMNS)
        df = pd.concat([numeric_df.reset_index(drop=True), cat_df.reset_index(drop=True)], axis=1)
        df = df.reindex(columns=columns, fill_value=0)
        proba = model.predict_proba(df)[:, 1]
        for idx, p in enumerate(proba):
            rule = rule_results[idx]
            blended = float(np.clip(0.5 * rule["internal"] + 0.5 * float(p), 0.01, 0.99))
            score = int(round(blended * 100))
            rule["internal"] = blended
            rule["chanceScore"] = score
            rule["chanceLevel"] = _level_from_score(score)[0]
            rule["riskLevel"] = _level_from_score(score)[1]
        return rule_results
    except Exception:  # noqa: BLE001 - defensive
        return rule_results


def _level_from_score(score: int) -> tuple[str, str]:
    if score >= 90:
        return "兜底参考", "很低"
    if score >= 75:
        return "稳妥参考", "较低"
    if score >= 50:
        return "适中", "中等"
    return "冲刺参考", "较高"


def _camel_to_snake(name: str) -> str:
    """对齐 ETL CSV 字段：训练时是 snake_case，predict 入参用 camelCase。"""
    out = []
    for i, ch in enumerate(name):
        if ch.isupper() and i > 0:
            out.append("_")
        out.append(ch.lower())
    return "".join(out)


def _snake_to_camel(name: str) -> str:
    parts = name.split("_")
    return parts[0] + "".join(seg.capitalize() for seg in parts[1:])


def _coerce_float(value: Any) -> float:
    try:
        if value is None or value == "":
            return 0.0
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def _resolve_model_version(rank_used: bool, chance_used: bool) -> str:
    if rank_used and chance_used:
        return CHANCE_MODEL_VERSION
    if rank_used or chance_used:
        return f"{CHANCE_MODEL_VERSION}-partial"
    return "fallback-rule-v1"
