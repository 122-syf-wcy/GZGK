from fastapi import FastAPI

from app.api.health import router as health_router
from app.api.predict import router as predict_router
from app.api.train import router as train_router

app = FastAPI(title="GZLY ML Service", version="1.0.0")
app.include_router(health_router, prefix="/ml")
app.include_router(predict_router, prefix="/ml")
app.include_router(train_router, prefix="/ml")
