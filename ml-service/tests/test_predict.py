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


def test_camel_case_payload_hits_snake_case_features(monkeypatch):
    """Java 侧发来的 camelCase 载荷必须命中 snake_case 训练特征列，命中率 100% 且不落回 fallback。

    回归背景：预测侧曾把映射方向写反（对已是 snake_case 的特征名做 camel→snake 转换），
    导致 camelCase 入参两跳都取不到值、整列 NaN，最终静默降级为规则常量预测。
    """
    import numpy as np

    import app.api.predict as predict_module

    captured = {}

    class _StubRankModel:
        def predict(self, df):
            captured["df"] = df
            return np.array([19800.0] * len(df))

    features = ["plan_change_rate", "major_hot_score", "rank_volatility_3y", "school_level"]
    monkeypatch.setattr(predict_module, "get_rank_model",
                        lambda: {"model": _StubRankModel(), "features": features})
    monkeypatch.setattr(predict_module, "get_chance_model", lambda: None)

    response = client.post(
        "/ml/predict/batch",
        json={
            "candidate": {"rank": 21000, "score": 602},
            "items": [
                {
                    "itemId": "10657_080901",
                    "planChangeRate": 0.08,
                    "majorHotScore": 0.7,
                    "rankVolatility3y": 0.12,
                    "schoolLevel": "211",
                }
            ],
        },
    )

    assert response.status_code == 200
    body = response.json()
    df = captured["df"]
    # 命中率 100%：camelCase 入参全部解析到对应 snake_case 特征列，无缺失
    assert not df.isna().any().any()
    assert df.iloc[0]["plan_change_rate"] == 0.08
    assert df.iloc[0]["major_hot_score"] == 0.7
    assert df.iloc[0]["rank_volatility_3y"] == 0.12
    assert df.iloc[0]["school_level"] == "211"
    # 未落回 fallback：使用模型输出（19800 / 置信度 0.85），而非规则预测
    assert body["rankModelUsed"] is True
    prediction = body["predictions"][0]
    assert prediction["predictedMinRank"] == 19800
    assert prediction["predictionConfidence"] == 0.85
