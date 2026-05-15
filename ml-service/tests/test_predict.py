from pathlib import Path

import importlib.util

import numpy as np
import pandas as pd
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.models.chance_score_model import (
    CHANCE_MODEL_VERSION,
    chance_from_rank_diff,
    expected_calibration_error,
    hit_rate_at_k,
    train_chance_model,
)


client = TestClient(app)

requires_lightgbm = pytest.mark.skipif(
    importlib.util.find_spec("lightgbm") is None,
    reason="lightgbm not installed (mac dev environment); chance v2 training test runs on server",
)


def test_health_check():
    response = client.get("/ml/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_batch_predict_returns_safe_public_fields():
    payload = {
        "modelVersion": "latest",
        "candidate": {"rank": 21000, "score": 602},
        "items": [
            {
                "itemId": "10657_080901",
                "historyMinRank": 23000,
                "planChangeRate": 0.08,
                "rankVolatility3y": 0.12,
                "dataConfidence": 82,
                "majorHotScore": 0.7,
            }
        ],
    }

    response = client.post("/ml/predict/batch", json=payload)

    assert response.status_code == 200
    body = response.json()
    expected_versions = {
        "fallback-rule-v1",
        CHANCE_MODEL_VERSION,
        f"{CHANCE_MODEL_VERSION}-partial",
        "chance-score-v1.0.0",
        "chance-score-v1.0.0-partial",
    }
    assert body["modelVersion"] in expected_versions
    assert "rankModelUsed" in body and "chanceModelUsed" in body
    assert len(body["predictions"]) == 1
    prediction = body["predictions"][0]
    # predictedMinRank：模型不在时回退到 historyMinRank=23000，模型在时给真实预测；任意正数都合规
    assert prediction["predictedMinRank"] >= 0
    assert 0 <= prediction["chanceScore"] <= 100
    assert prediction["chanceLevel"] in {"冲刺参考", "适中", "稳妥参考", "兜底参考"}
    assert prediction["riskLevel"] in {"较高", "中等", "较低", "很低"}
    assert "admissionProb" not in prediction
    assert "probLevel" not in prediction
    assert "modelUsed" in prediction["featureContribution"]


def test_predict_status_returns_loader_state():
    response = client.get("/ml/predict/status")
    assert response.status_code == 200
    body = response.json()
    assert "rankModelLoaded" in body
    assert "chanceModelLoaded" in body
    assert "modelsDir" in body


def test_chance_score_increases_when_rank_advantage_is_better():
    weak = chance_from_rank_diff(-3500, 21000, {"dataConfidence": 80})
    strong = chance_from_rank_diff(3500, 21000, {"dataConfidence": 80})

    assert strong["chanceScore"] > weak["chanceScore"]
    assert strong["riskLevel"] != "较高"


def test_low_data_confidence_marks_insufficient_signal():
    """无论是否加载模型，dataConfidence < 55 必须显式标注 confidenceLevel='数据不足'，
    避免对低参考度的志愿做出过度自信的展示。"""
    response = client.post(
        "/ml/predict/batch",
        json={
            "candidate": {"rank": 32000, "score": 555},
            "items": [{"itemId": "no-history", "dataConfidence": 35}],
        },
    )

    assert response.status_code == 200
    prediction = response.json()["predictions"][0]
    assert prediction["confidenceLevel"] == "数据不足"
    assert prediction["dataConfidence"] <= 55


def test_train_aliases_are_available_without_dataset():
    rank_response = client.post("/ml/train/rank-prediction", json={})
    chance_response = client.post("/ml/train/chance-score", json={})

    assert rank_response.status_code == 200
    assert chance_response.status_code == 200
    assert rank_response.json()["status"] in {"skipped", "failed"}
    assert chance_response.json()["status"] in {"skipped", "failed"}


def test_expected_calibration_error_is_zero_when_perfect():
    y_true = np.array([0, 0, 1, 1, 1, 0, 0, 1, 1, 0])
    y_prob = y_true.astype(float)

    assert expected_calibration_error(y_true, y_prob, n_bins=5) == 0.0


def test_expected_calibration_error_detects_over_confidence():
    y_true = np.array([0] * 50 + [1] * 50)
    y_prob_over_confident = np.array([0.9] * 50 + [0.95] * 50)

    ece = expected_calibration_error(y_true, y_prob_over_confident, n_bins=10)

    assert ece > 0.4


def test_hit_rate_at_k_orders_by_probability():
    y_true = np.array([0, 1, 0, 1, 1, 0])
    y_prob = np.array([0.1, 0.9, 0.2, 0.85, 0.3, 0.05])

    assert hit_rate_at_k(y_true, y_prob, k=2) == 1.0
    assert hit_rate_at_k(y_true, y_prob, k=3) >= 2 / 3 - 1e-9


def _build_synthetic_training_csv(path: Path) -> None:
    rng = np.random.default_rng(2026)
    rows = []
    for year in (2022, 2023, 2024, 2025):
        for school in range(40):
            min_rank = int(np.clip(rng.normal(35000, 18000), 1500, 110000))
            rows.append(
                {
                    "year": year,
                    "tuition": 4500,
                    "current_plan_count": int(rng.integers(2, 18)),
                    "last_year_plan_count": int(rng.integers(2, 18)),
                    "plan_change_rate": float(rng.normal(0, 0.15)),
                    "min_rank_lag_1": int(min_rank + rng.normal(0, 2500)),
                    "min_rank_lag_2": int(min_rank + rng.normal(0, 3500)),
                    "min_rank_lag_3": int(min_rank + rng.normal(0, 5000)),
                    "avg_rank_lag_3": int(min_rank + rng.normal(0, 2000)),
                    "median_rank_lag_3": int(min_rank + rng.normal(0, 2000)),
                    "rank_volatility_3y": float(np.clip(abs(rng.normal(0.15, 0.1)), 0, 1)),
                    "rank_trend_3y": float(rng.normal(0, 1500)),
                    "major_hot_score": float(np.clip(abs(rng.normal(0.4, 0.2)), 0, 1)),
                    "school_ranking_score": int(rng.integers(0, 100)),
                    "employment_score": int(rng.integers(0, 100)),
                    "batch_code": "NORMAL_UNDERGRADUATE",
                    "candidate_type": "普通类",
                    "subject_type": "物理类" if school % 2 == 0 else "历史类",
                    "school_code": str(10000 + school),
                    "major_code": f"M{school:03d}",
                    "school_level": "本科" if school < 30 else "专科",
                    "is_985": int(school < 5),
                    "is_211": int(school < 12),
                    "is_double_first_class": int(school < 18),
                    "is_public": 1,
                    "school_city": "贵阳市" if school % 3 else "遵义市",
                    "major_category": "工学",
                    "has_supplement_lag_1": int(rng.integers(0, 2)),
                    "first_round_full_lag_1": int(rng.integers(0, 2)),
                    "min_rank": min_rank,
                }
            )
    pd.DataFrame(rows).to_csv(path, index=False)


@requires_lightgbm
def test_train_chance_model_v2_does_not_leak_target(tmp_path: Path) -> None:
    csv_path = tmp_path / "training.csv"
    _build_synthetic_training_csv(csv_path)

    result = train_chance_model(str(csv_path), str(tmp_path / "models"))

    assert result["status"] == "ok", result
    assert result["modelVersion"] == CHANCE_MODEL_VERSION
    assert result["modelType"] == "LightGBMClassifier"
    metrics = result["metrics"]
    assert metrics["brier"] >= 0.005, "Brier ≈ 0 typically signals target leakage"
    assert metrics["brier"] <= 0.30
    assert metrics["auc"] < 0.999
    assert metrics["auc"] > 0.65
    assert metrics["ece"] < 0.20
    assert 0.0 <= metrics["hitRate@10"] <= 1.0
    schema = result["featureSchema"]
    assert "min_rank" in schema["leakGuard"]
    assert "min_rank" not in schema["numeric"]
