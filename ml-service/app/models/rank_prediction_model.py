from __future__ import annotations

from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.metrics import mean_absolute_error
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder


NUMERIC_FEATURES = [
    "year",
    "tuition",
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
]

CATEGORICAL_FEATURES = [
    "batch_code",
    "candidate_type",
    "subject_type",
    "school_code",
    "major_code",
    "school_level",
    "is_985",
    "is_211",
    "is_double_first_class",
    "is_public",
    "school_city",
    "major_category",
    "has_supplement_lag_1",
    "first_round_full_lag_1",
]


def predict_min_rank(item: dict[str, Any]) -> tuple[int, float]:
    history = item.get("historyMinRank") or item.get("min_rank_lag_1") or item.get("predictedMinRank")
    if history:
        return max(1, int(history)), 0.62
    rank_lag = [item.get("min_rank_lag_1"), item.get("min_rank_lag_2"), item.get("min_rank_lag_3")]
    values = [int(v) for v in rank_lag if v]
    if values:
        return max(1, int(np.median(values))), 0.7
    return 0, 0.25


def train_rank_model(data_path: str | None, output_dir: str) -> dict:
    try:
        from lightgbm import LGBMRegressor
    except Exception as exc:
        return {"status": "failed", "reason": f"lightgbm unavailable: {exc}"}

    if not data_path:
        return {"status": "skipped", "reason": "dataPath is required"}
    df = pd.read_csv(data_path)
    if "min_rank" not in df.columns:
        return {"status": "failed", "reason": "missing label min_rank"}
    features = [col for col in NUMERIC_FEATURES + CATEGORICAL_FEATURES if col in df.columns]
    numeric = [col for col in NUMERIC_FEATURES if col in features]
    categorical = [col for col in CATEGORICAL_FEATURES if col in features]
    X = df[features]
    y = df["min_rank"]
    preprocessor = ColumnTransformer(
        transformers=[
            ("num", SimpleImputer(strategy="median"), numeric),
            ("cat", Pipeline([("impute", SimpleImputer(strategy="most_frequent")), ("onehot", OneHotEncoder(handle_unknown="ignore"))]), categorical),
        ]
    )
    model = Pipeline(
        steps=[
            ("preprocess", preprocessor),
            ("model", LGBMRegressor(n_estimators=220, learning_rate=0.05, random_state=42)),
        ]
    )
    model.fit(X, y)
    pred = model.predict(X)
    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)
    model_path = out / "rank_prediction_lgbm.joblib"
    joblib.dump({"model": model, "features": features}, model_path)
    return {
        "status": "ok",
        "modelVersion": "rank-prediction-v1.0.0",
        "modelType": "LightGBMRegressor",
        "modelPath": str(model_path),
        "modelFilePath": str(model_path),
        "trainDataCount": int(len(df)),
        "metrics": {"mae": float(mean_absolute_error(y, pred))},
    }
