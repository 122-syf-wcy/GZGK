from fastapi import APIRouter
from pydantic import BaseModel

from app.models.chance_score_model import train_chance_model
from app.models.rank_prediction_model import train_rank_model

router = APIRouter()


class TrainRequest(BaseModel):
    dataPath: str | None = None
    outputDir: str = "models"


@router.post("/train/rank")
def train_rank(req: TrainRequest) -> dict:
    return train_rank_model(req.dataPath, req.outputDir)


@router.post("/train/rank-prediction")
def train_rank_prediction(req: TrainRequest) -> dict:
    return train_rank_model(req.dataPath, req.outputDir)


@router.post("/train/chance")
def train_chance(req: TrainRequest) -> dict:
    return train_chance_model(req.dataPath, req.outputDir)


@router.post("/train/chance-score")
def train_chance_score(req: TrainRequest) -> dict:
    return train_chance_model(req.dataPath, req.outputDir)
