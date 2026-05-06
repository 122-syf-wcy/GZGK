from fastapi.testclient import TestClient

from app.main import app
from app.models.chance_score_model import chance_from_rank_diff


client = TestClient(app)


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
    # modelVersion 反映真实使用：模型不存在时为 fallback-rule-v1，存在时为 chance-score-*
    assert body["modelVersion"] in {"fallback-rule-v1", "chance-score-v1.0.0", "chance-score-v1.0.0-partial"}
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
