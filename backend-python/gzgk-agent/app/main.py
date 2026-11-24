from __future__ import annotations

import asyncio
import json
import time
from collections import deque
from typing import Any, AsyncGenerator, Literal

from fastapi import FastAPI, Header, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest
from pydantic import BaseModel, Field

from app.clients.core_client import CoreServiceClient
from app.config import settings
from app.graph.workflow import AdvisorMultiAgentGraph
from app.guardrails.compliance import ComplianceGuardrail, STANDARD_DISCLAIMER
from app.rag.hybrid_retriever import HybridRagRetriever
from app.rag.semantic_cache import SemanticCache

AGENT_REQUESTS = Counter("gzgk_agent_requests_total", "Total agent chat requests", ["intent"])
AGENT_LATENCY = Histogram("gzgk_agent_turn_seconds", "Agent end-to-end turn latency")
COMPLIANCE_INTERCEPTS = Counter("gzgk_agent_compliance_intercepts_total", "Total compliance term redactions")

guardrail = ComplianceGuardrail(settings.contracts_root)
retriever = HybridRagRetriever(settings.seed_kb_dir, settings.pg_dsn, settings.embedding_dim)
semantic_cache = SemanticCache()
core_client = CoreServiceClient(settings.core_grpc_target, settings.core_http_base)
agent_graph = AdvisorMultiAgentGraph(settings, core_client, retriever, guardrail, semantic_cache)

_thread_events: dict[str, deque[tuple[str, str]]] = {}
_recent_threads: dict[str, dict[str, Any]] = {}

app = FastAPI(title="GZGK v2 LangGraph Autonomous ReAct + MCP Multi-Agent Service", version="2.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class ChatStreamRequest(BaseModel):
    userId: str = "demo-student"
    threadId: str = Field(default_factory=lambda: f"thread-{int(time.time() * 1000)}")
    planId: str = ""
    provinceCode: str = "GZ"
    message: str
    mode: str = "advisor"


class ResumeHitlRequest(BaseModel):
    userId: str = "demo-student"
    decision: Literal["approve", "reject"]


class SaveMemoryRequest(BaseModel):
    category: str
    value: str


def _format_sse(event_id: str, thread_id: str, event_type: str, payload: dict[str, Any]) -> str:
    body = json.dumps(
        {
            "eventId": event_id,
            "threadId": thread_id,
            "type": event_type,
            "timestampMs": int(time.time() * 1000),
            "payload": payload,
        },
        ensure_ascii=False,
    )
    frame = f"id: {event_id}\ndata: {body}\n\n"
    buf = _thread_events.setdefault(thread_id, deque(maxlen=160))
    buf.append((event_id, frame))
    return frame


@app.get("/health")
async def health():
    tools = await agent_graph.tool_registry.catalog()
    return {
        "status": "ok",
        "service": "gzgk-agent",
        "architecture": "LangGraph ReAct + Dynamic MCP + LongTermMemory + 3-Layer Context Compression",
        "llmMode": "live" if settings.use_live_llm else "deterministic-react-tool-calling",
        "kbChunks": len(retriever._local_chunks),
        "complianceHardTerms": len(guardrail.hard_terms),
        "toolsCount": len(tools),
        "mcpToolsCount": sum(1 for t in tools if t.get("source") == "mcp"),
    }


@app.get("/metrics")
def metrics():
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.get("/tools")
async def list_tools():
    """Inspect all dynamically bound Native gRPC + MCP Server tools."""
    return {"code": 0, "data": await agent_graph.tool_registry.catalog()}


@app.get("/memory/{user_id}")
def get_long_term_memory(user_id: str):
    return {"code": 0, "data": agent_graph.memory_manager.list_memories(user_id)}


@app.post("/memory/{user_id}")
def add_long_term_memory(user_id: str, req: SaveMemoryRequest):
    rec = agent_graph.memory_manager.remember(user_id, f"manual_{int(time.time())}", req.category, req.value, "手动添加")
    return {"code": 0, "data": rec}


@app.delete("/memory/{user_id}/{key}")
def delete_long_term_memory(user_id: str, key: str):
    deleted = agent_graph.memory_manager.forget(user_id, key)
    return {"code": 0, "data": {"deleted": deleted}}


@app.post("/threads/{thread_id}/compact")
async def compact_thread_context(thread_id: str, userId: str = "demo-student"):
    """Manually triggers rolling summary + LangGraph RemoveMessage compression for a conversation thread."""
    res = await agent_graph.compact_thread(thread_id, userId)
    return {"code": 0, "message": "ok", "data": res}


@app.get("/threads")
def list_threads():
    return {"code": 0, "data": list(_recent_threads.values())}


@app.get("/rag/search")
async def rag_search(q: str, provinceCode: str = "GZ", topK: int = 4):
    hits = await retriever.search(q, provinceCode, topK)
    return {
        "code": 0,
        "data": [
            {
                "index": c.index,
                "chunkId": c.chunk_id,
                "title": c.title,
                "docType": c.doc_type,
                "schoolName": c.school_name,
                "excerpt": c.excerpt,
                "highlight": c.highlight,
                "sourceUrl": c.source_url,
                "verified": c.verified,
                "score": c.score,
            }
            for c in hits
        ],
    }


@app.post("/chat/stream")
async def chat_stream(
    req: ChatStreamRequest,
    last_event_id: str | None = Header(default=None, alias="Last-Event-ID"),
):
    async def event_generator() -> AsyncGenerator[str, None]:
        t0 = time.perf_counter()
        seq = int(time.time() * 1000)

        if last_event_id and req.threadId in _thread_events:
            seen = False
            for ev_id, frame in list(_thread_events[req.threadId]):
                if seen:
                    yield frame
                elif ev_id == last_event_id:
                    seen = True
            if seen:
                yield "data: [DONE]\n\n"
                return

        yield _format_sse(f"{seq}-0", req.threadId, "status", {"stage": "start", "label": "加载长期记忆与检查上下文窗口…"})

        result = await agent_graph.run_turn(
            thread_id=req.threadId,
            plan_id=req.planId,
            province_code=req.provinceCode,
            message=req.message,
            mode=req.mode,
            user_id=req.userId,
        )
        AGENT_REQUESTS.labels(intent=result["intent"]).inc()
        if result["complianceHits"]:
            COMPLIANCE_INTERCEPTS.inc(len(result["complianceHits"]))

        _recent_threads[req.threadId] = {
            "threadId": req.threadId,
            "planId": req.planId,
            "title": req.message[:22],
            "updatedAt": int(time.time() * 1000),
            "intent": result["intent"],
        }

        for i, tr in enumerate(result["traces"], start=1):
            ev_type = "tool_result" if tr.get("tool") else "status"
            yield _format_sse(f"{seq}-tr-{i}", req.threadId, ev_type, tr)

        for i, exp in enumerate(result.get("expertReports") or [], start=1):
            yield _format_sse(f"{seq}-exp-{i}", req.threadId, "expert_progress", exp)

        if result["sources"]:
            yield _format_sse(f"{seq}-src", req.threadId, "sources", {"sources": result["sources"]})

        # Stream answer through sliding-window ComplianceBuffer (zero banned-word flash!)
        stream_buf = guardrail.new_stream_buffer()
        answer_text = result["answer"]
        step = 18
        for pos in range(0, len(answer_text), step):
            safe_chunk = stream_buf.push(answer_text[pos : pos + step])
            if safe_chunk:
                yield _format_sse(f"{seq}-c-{pos}", req.threadId, "content", {"text": safe_chunk})
                await asyncio.sleep(0.008)
        tail = stream_buf.flush()
        if tail:
            yield _format_sse(f"{seq}-c-tail", req.threadId, "content", {"text": tail})

        if result.get("diffCard"):
            yield _format_sse(f"{seq}-diff", req.threadId, "diff_card", {"diffCard": result["diffCard"]})

        # Emit real-time Context Window + Memory + MCP Telemetry
        yield _format_sse(
            f"{seq}-ctx",
            req.threadId,
            "context_stats",
            {
                "contextStats": result.get("contextStats"),
                "memories": result.get("memories", []),
            },
        )

        yield _format_sse(
            f"{seq}-final",
            req.threadId,
            "final",
            {
                "text": answer_text,
                "complianceSanitized": bool(result["complianceHits"] or stream_buf.hits),
                "disclaimer": STANDARD_DISCLAIMER,
                "cacheHit": result["cacheHit"],
                "awaitingHitl": result["awaitingHitl"],
            },
        )
        AGENT_LATENCY.observe(time.perf_counter() - t0)
        yield "data: [DONE]\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.post("/threads/{thread_id}/resume")
async def resume_hitl(thread_id: str, req: ResumeHitlRequest):
    res = await agent_graph.resume_hitl(thread_id, req.decision, req.userId)
    return {"code": 0, "message": "ok", "data": res}


@app.post("/report/generate")
async def generate_report(req: ChatStreamRequest):
    res = await agent_graph.run_turn(
        thread_id=req.threadId,
        plan_id=req.planId,
        province_code=req.provinceCode,
        message=req.message or "生成96志愿多专家深度诊断报告",
        mode="report",
        user_id=req.userId,
    )
    return {"code": 0, "message": "ok", "data": res}
