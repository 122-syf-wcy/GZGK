from __future__ import annotations

import logging
from typing import Any

import numpy as np
import pandas as pd
from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.models.chance_score_model import chance_from_rank_diff
from app.models.rank_prediction_model import predict_min_rank
from app.utils.model_loader import get_chance_model, get_rank_model, model_status

LOGGER = logging.getLogger("gzly.ml.predict")

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
        rows: list[dict[str, Any]] = []
        missing_counts: dict[str, int] = {f: 0 for f in features}
        for item in items:
            row: dict[str, Any] = {}
            for f in features:
                # 训练侧特征 schema 是 snake_case，而 predict 入参（Java 侧）是 camelCase：
                # 先按 camelCase 命中，再回退到 snake_case 原键（兼容内部/历史调用）。
                value = item.get(_snake_to_camel(f), item.get(f))
                if _is_missing(value):
                    missing_counts[f] += 1
                row[f] = value
            rows.append(row)
        df = pd.DataFrame(rows)
        if df.empty:
            return fallback
        _warn_low_feature_hit_rate(missing_counts, len(items))
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
        for idx, item in enumerate(items):
            predicted_rank, _ = rank_predictions[idx]
            row = {
                "candidate_rank": candidate_rank or 30000,
                "min_rank": predicted_rank or 0,
                "rank_volatility_3y": float(item.get("rankVolatility3y") or 0),
                "plan_change_rate": float(item.get("planChangeRate") or 0),
                "major_hot_score": float(item.get("majorHotScore") or 0),
                "data_confidence": float(item.get("dataConfidence") or 60),
            }
            feature_rows.append(row)
        df = pd.get_dummies(pd.DataFrame(feature_rows).fillna(0))
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


# 特征命中率看门狗：缺失率超过该比例即告警，避免命名不匹配导致的全 NaN 静默降级
_FEATURE_MISSING_WARN_RATIO = 0.5


def _snake_to_camel(name: str) -> str:
    """对齐 predict 入参命名：训练侧特征 schema 是 snake_case，Java 侧请求体是 camelCase。"""
    parts = name.split("_")
    if len(parts) == 1:
        return name
    head, *tail = parts
    return head + "".join(part[:1].upper() + part[1:] for part in tail)


def _is_missing(value: Any) -> bool:
    """判断特征值是否缺失（None 或 NaN），供命中率统计使用。"""
    if value is None:
        return True
    try:
        return bool(pd.isna(value))
    except (TypeError, ValueError):
        return False


def _warn_low_feature_hit_rate(missing_counts: dict[str, int], sample_count: int) -> None:
    """统计 rank 模型输入特征的缺失率；超过阈值时记 warning，并列出缺失最多的特征名，
    防止入参与训练 schema 命名不匹配时静默退化为常量/规则预测。"""
    if sample_count <= 0 or not missing_counts:
        return
    total_cells = sample_count * len(missing_counts)
    total_missing = sum(missing_counts.values())
    if total_cells <= 0:
        return
    missing_ratio = total_missing / total_cells
    if missing_ratio <= _FEATURE_MISSING_WARN_RATIO:
        return
    worst = sorted(missing_counts.items(), key=lambda kv: kv[1], reverse=True)
    missing_detail = ", ".join(
        f"{name}={count * 100 // sample_count}%" for name, count in worst[:5] if count > 0
    )
    LOGGER.warning(
        "[predict] rank 特征缺失率 %.1f%% 超过阈值 %.0f%%（样本 %d，特征 %d 项）；"
        "缺失最多：%s。疑似 predict 入参与训练 schema 命名不匹配，已继续推理，请检查。",
        missing_ratio * 100,
        _FEATURE_MISSING_WARN_RATIO * 100,
        sample_count,
        len(missing_counts),
        missing_detail or "无",
    )


def _resolve_model_version(rank_used: bool, chance_used: bool) -> str:
    if rank_used and chance_used:
        return "chance-score-v1.0.0"
    if rank_used or chance_used:
        return "chance-score-v1.0.0-partial"
    return "fallback-rule-v1"
