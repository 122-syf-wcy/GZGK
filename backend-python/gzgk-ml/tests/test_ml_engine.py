from app.model_engine import MlInferenceEngine


def test_batch_predict_handles_rank_and_shrink_penalty():
    engine = MlInferenceEngine()
    preds = engine.predict_batch_sync(
        candidate_rank=8450,
        items=[
            {
                "item_id": "steady-1",
                "min_rank_last_year": 9150,
                "rank_volatility_3y": 0.06,
                "plan_change_rate": 0.0,
                "data_confidence": 90,
            },
            {
                "item_id": "shrink-1",
                "min_rank_last_year": 9150,
                "rank_volatility_3y": 0.06,
                "plan_change_rate": -0.45,
                "data_confidence": 90,
            },
        ],
    )
    assert len(preds) == 2
    assert preds[0].chance_score > preds[1].chance_score
    assert preds[1].predicted_min_rank < preds[0].predicted_min_rank
    assert 1 <= preds[0].chance_score <= 99
