from __future__ import annotations

import asyncio
import os
import time
from contextlib import asynccontextmanager

import grpc
from fastapi import FastAPI, Response
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest

from app.model_engine import MlInferenceEngine

ML_REQUESTS = Counter("gzgk_ml_requests_total", "Total ML batch prediction requests")
ML_LATENCY = Histogram("gzgk_ml_inference_seconds", "ML batch prediction latency")

engine = MlInferenceEngine()


def _create_grpc_server(port: int) -> grpc.aio.Server | None:
    try:
        from app.proto import ml_predict_pb2, ml_predict_pb2_grpc
    except ImportError:
        return None

    class MlPredictServicer(ml_predict_pb2_grpc.MlPredictServiceServicer):
        async def BatchPredict(self, request, context):
            t0 = time.perf_counter()
            ML_REQUESTS.inc()
            items = [
                {
                    "item_id": i.item_id,
                    "min_rank_last_year": i.min_rank_last_year,
                    "rank_volatility_3y": i.rank_volatility_3y,
                    "plan_change_rate": i.plan_change_rate,
                    "major_hot_score": i.major_hot_score,
                    "data_confidence": i.data_confidence,
                    "province_sigma_min": i.province_sigma_min,
                }
                for i in request.items
            ]
            loop = asyncio.get_running_loop()
            preds = await loop.run_in_executor(
                engine.executor, engine.predict_batch_sync, request.candidate.candidate_rank, items
            )
            elapsed_ms = int((time.perf_counter() - t0) * 1000)
            ML_LATENCY.observe(elapsed_ms / 1000.0)
            return ml_predict_pb2.BatchPredictResponse(
                model_version=engine.model_version,
                rank_model_used=engine.rank_model is not None,
                chance_model_used=engine.chance_model is not None,
                inference_time_ms=elapsed_ms,
                predictions=[
                    ml_predict_pb2.PredictItemResult(
                        item_id=p.item_id,
                        predicted_min_rank=p.predicted_min_rank,
                        internal_chance_score=p.internal_chance_score,
                        chance_score=p.chance_score,
                        chance_level=p.chance_level,
                        risk_level=p.risk_level,
                        data_confidence=p.data_confidence,
                        confidence_level=p.confidence_level,
                        prediction_confidence=p.prediction_confidence,
                        volatility_risk=p.volatility_risk,
                        hot_trend_score=p.hot_trend_score,
                        rank_diff=p.rank_diff,
                    )
                    for p in preds
                ],
            )

        async def GetModelStatus(self, request, context):
            st = engine.status()
            return ml_predict_pb2.ModelStatusResponse(**st)

    server = grpc.aio.server()
    ml_predict_pb2_grpc.add_MlPredictServiceServicer_to_server(MlPredictServicer(), server)
    server.add_insecure_port(f"[::]:{port}")
    return server


@asynccontextmanager
async def lifespan(_: FastAPI):
    grpc_port = int(os.environ.get("ML_GRPC_PORT", "50051"))
    grpc_server = _create_grpc_server(grpc_port)
    if grpc_server is not None:
        await grpc_server.start()
    try:
        yield
    finally:
        if grpc_server is not None:
            await grpc_server.stop(grace=2)


app = FastAPI(title="GZGK v2 ML Inference Service", version="2.0.0", lifespan=lifespan)


@app.get("/health")
def health():
    return engine.status()


@app.get("/metrics")
def metrics():
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
