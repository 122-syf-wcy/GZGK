"""ML 模型缓存加载器：按文件 mtime 自动重载，无需重启 ml-service。

- 首次访问时尝试 joblib.load，失败则保持 None（让 caller 走规则降级）。
- 后续访问比较文件 mtime，比缓存新则热重载。
"""
from __future__ import annotations

import logging
import os
import threading
from pathlib import Path
from typing import Any

import joblib

LOGGER = logging.getLogger("gzly.ml.model_loader")

DEFAULT_MODELS_DIR = Path(os.environ.get("GZLY_ML_MODELS_DIR", "models"))


class _ModelHolder:
    def __init__(self, path: Path):
        self.path = path
        self.payload: Any = None
        self.mtime: float = 0.0
        self.lock = threading.Lock()

    def get(self) -> Any:
        try:
            cur_mtime = self.path.stat().st_mtime
        except FileNotFoundError:
            return None
        if self.payload is None or cur_mtime > self.mtime:
            with self.lock:
                if self.payload is None or cur_mtime > self.mtime:
                    try:
                        self.payload = joblib.load(self.path)
                        self.mtime = cur_mtime
                        LOGGER.info("[model_loader] loaded %s mtime=%s", self.path, cur_mtime)
                    except Exception as exc:  # pragma: no cover - defensive
                        LOGGER.warning("[model_loader] load failed %s: %s", self.path, exc)
                        self.payload = None
        return self.payload


_RANK_HOLDER = _ModelHolder(DEFAULT_MODELS_DIR / "rank_prediction_lgbm.joblib")
_CHANCE_HOLDER = _ModelHolder(DEFAULT_MODELS_DIR / "chance_score_lgbm.joblib")


def get_rank_model() -> dict | None:
    """Returns the rank-prediction model dict {"model": Pipeline, "features": [...]} or None."""
    return _RANK_HOLDER.get()


def get_chance_model() -> dict | None:
    """Returns the chance-score model dict {"model": Classifier, "columns": [...]} or None."""
    return _CHANCE_HOLDER.get()


def model_status() -> dict[str, Any]:
    return {
        "rankModelLoaded": get_rank_model() is not None,
        "chanceModelLoaded": get_chance_model() is not None,
        "modelsDir": str(DEFAULT_MODELS_DIR.resolve()),
    }
