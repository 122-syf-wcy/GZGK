import pytest
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage

from app.clients.core_client import CoreServiceClient
from app.config import AgentSettings
from app.graph.workflow import AdvisorMultiAgentGraph
from app.guardrails.compliance import ComplianceGuardrail
from app.memory.compressor import ContextWindowManager
from app.rag.hybrid_retriever import HybridRagRetriever
from app.rag.semantic_cache import SemanticCache


@pytest.fixture
def runtime():
    cfg = AgentSettings(llm_mock_mode=True)
    guardrail = ComplianceGuardrail(cfg.contracts_root)
    retriever = HybridRagRetriever(cfg.seed_kb_dir)
    cache = SemanticCache()
    core = CoreServiceClient("127.0.0.1:9090", "http://127.0.0.1:59999")
    graph = AdvisorMultiAgentGraph(cfg, core, retriever, guardrail, cache)
    return cfg, guardrail, retriever, cache, graph


def test_streaming_compliance_blocks_split_token_banned_phrases(runtime):
    """Red-team test: banned phrases split across SSE chunk boundaries must NEVER leak."""
    _, guardrail, _, _, _ = runtime
    buf = guardrail.new_stream_buffer()
    split_chunks = ["该志愿方案", "保", "录", "取且", "100%", "录", "取，", "录", "取概", "率高达99%，", "保证不", "滑档！"]
    emitted = []
    for ch in split_chunks:
        part = buf.push(ch)
        if part:
            emitted.append(part)
    emitted.append(buf.flush())
    full = "".join(emitted)

    for forbidden in ("保录取", "100%录取", "录取概率", "保证不滑档"):
        assert forbidden not in full, f"Leaked forbidden phrase: {forbidden}"
    assert len(buf.hits) >= 4


@pytest.mark.asyncio
async def test_mcp_server_dynamic_discovery_and_react_invocation(runtime):
    """Verifies official FastMCP tools are dynamically discovered and autonomously called inside ReAct ToolNode."""
    _, _, _, _, graph = runtime
    await graph.ensure_initialized()
    catalog = await graph.tool_registry.catalog()
    mcp_names = {t["name"] for t in catalog if t["source"] == "mcp"}
    assert "mcp_lookup_province_policy" in mcp_names
    assert "mcp_major_career_insights" in mcp_names
    assert "mcp_check_medical_and_subject_redlines" in mcp_names

    turn = await graph.run_turn(
        thread_id="test-mcp-react-001",
        plan_id="demo-gz-586",
        province_code="GZ",
        message="我有点色弱，看看计算机科学与技术的就业前景和体检红线，顺便核对贵州政策",
        mode="advisor",
    )
    trace_labels = " ".join(t.get("label", "") for t in turn["traces"])
    assert "mcp_major_career_insights" in trace_labels
    assert "mcp_check_medical_and_subject_redlines" in trace_labels
    assert "mcp_lookup_province_policy" in trace_labels
    assert "读研" in turn["answer"] or "就业" in turn["answer"]
    assert turn["contextStats"]["mcpToolsCount"] >= 3


@pytest.mark.asyncio
async def test_cross_thread_long_term_memory_persistence_and_recall(runtime):
    """Preferences stated in Thread A must persist in LangGraph Store and be recalled in Thread B."""
    _, _, _, _, graph = runtime
    uid = "student-memory-test"

    turn_a = await graph.run_turn(
        thread_id="thread-memory-A",
        plan_id="demo-gz-586",
        province_code="GZ",
        message="我不接受民办、预算8000，有点色弱，想去川渝读计算机，打算以后考研",
        user_id=uid,
    )
    cats = {m["category"] for m in turn_a["memories"]}
    assert "意向城市" in cats
    assert "意向专业" in cats
    assert "办学性质红线" in cats
    assert "体检限报约束" in cats
    assert "家庭学费预算" in cats

    # Brand new thread_id must automatically recall long-term preferences
    turn_b = await graph.run_turn(
        thread_id="thread-memory-B",
        plan_id="demo-gz-586",
        province_code="GZ",
        message="结合你记住的我的偏好，帮我看看选专业要注意什么？",
        user_id=uid,
    )
    assert "川渝" in turn_b["answer"] or "公办" in turn_b["answer"] or "色弱" in turn_b["answer"]
    assert turn_b["contextStats"]["memoryCount"] >= 5


@pytest.mark.asyncio
async def test_context_compression_protects_tool_call_atomic_boundaries():
    """Rolling compression must NEVER orphan a ToolMessage from its preceding AIMessage(tool_calls)."""
    mgr = ContextWindowManager(budget_tokens=1000, trigger_tokens=120, keep_recent_messages=3, tool_output_max_tokens=40)
    msgs = [
        HumanMessage(id="m1", content="第一轮：查询贵州586分位次与历年线"),
        AIMessage(id="m2", content="", tool_calls=[{"id": "c1", "name": "estimate_rank", "args": {"score": 586}, "type": "tool_call"}]),
        ToolMessage(id="m3", tool_call_id="c1", name="estimate_rank", content="位次8450名 " + ("详细原始数据 " * 60)),
        AIMessage(id="m4", content="586分对应贵州物理类8450名，可重点关注211与行业强校。"),
        HumanMessage(id="m5", content="第二轮：查重庆邮电大学计算机章程"),
        AIMessage(id="m6", content="", tool_calls=[{"id": "c2", "name": "search_knowledge_hybrid", "args": {"query": "重邮"}, "type": "tool_call"}]),
        ToolMessage(id="m7", tool_call_id="c2", name="search_knowledge_hybrid", content="重庆邮电大学计算机选科物理+化学，不限外语单科。"),
        AIMessage(id="m8", content="已核验重庆邮电大学招生章程，无外语单科限制。"),
    ]
    res = await mgr.compact_if_needed(msgs, running_summary="", force=False)
    assert res.compressed is True
    assert res.after_tokens < res.before_tokens
    assert res.pruned_tool_outputs >= 1
    assert len(res.removals) >= 4
    assert res.updated_summary

    removed_ids = {r.id for r in res.removals}
    kept = [m for m in msgs if m.id not in removed_ids]
    # First kept message must NEVER be an orphaned ToolMessage!
    assert not isinstance(kept[0], ToolMessage)


@pytest.mark.asyncio
async def test_manual_compact_thread_shrinks_active_checkpointer(runtime):
    _, _, _, _, graph = runtime
    tid = "thread-compact-001"
    await graph.run_turn(tid, "demo-gz-586", "GZ", "查一下586分在贵州的位次")
    await graph.run_turn(tid, "demo-gz-586", "GZ", "看看重庆邮电大学计算机的历年线和就业")
    turn3 = await graph.run_turn(tid, "demo-gz-586", "GZ", "贵州平行志愿退档风险怎么看？")
    before_msgs = turn3["contextStats"]["messageCount"]
    before_comp = turn3["contextStats"]["compressionCount"]
    assert before_msgs >= 6

    compacted = await graph.compact_thread(tid)
    assert compacted["compressed"] is True
    assert compacted["removedCount"] >= 2
    assert compacted["contextStats"]["compressionCount"] == before_comp + 1
    assert compacted["contextStats"]["messageCount"] < before_msgs
    assert compacted["runningSummary"]


@pytest.mark.asyncio
async def test_hitl_sandbox_interrupt_and_resume_approve_flow(runtime):
    """Multi-turn ReAct (Round 1 inspect/RAG/MCP -> Round 2 Monte Carlo Sandbox -> HITL interrupt -> resume)."""
    _, _, _, _, graph = runtime
    thread_id = "test-hitl-thread-001"
    turn1 = await graph.run_turn(
        thread_id=thread_id,
        plan_id="demo-gz-586",
        province_code="GZ",
        message="把冲档里缩招严重的专业去掉，帮我补2个川渝方向计算机稳档",
        mode="advisor",
    )
    assert turn1["awaitingHitl"] is True
    assert turn1["diffCard"] is not None
    assert turn1["diffCard"]["afterHitRate"] > turn1["diffCard"]["beforeHitRate"]
    assert len(turn1["sources"]) >= 1
    # Proves multi-round ReAct executed at least 2 tool rounds
    assert any("ReAct 第 2 轮" in t.get("label", "") for t in turn1["traces"])

    resumed = await graph.resume_hitl(thread_id, "approve")
    assert resumed["decision"] == "approve"
    assert resumed["applyResult"]["applied"] is True
    assert resumed["applyResult"]["newVersion"] == 4
    assert "保录取" not in resumed["answer"]


@pytest.mark.asyncio
async def test_map_reduce_three_experts_parallel_report(runtime):
    _, _, _, _, graph = runtime
    turn = await graph.run_turn(
        thread_id="test-report-thread-001",
        plan_id="demo-gz-586",
        province_code="GZ",
        message="请帮我生成96志愿多专家深度诊断报告",
        mode="report",
    )
    assert turn["intent"] == "deep_report"
    assert len(turn["expertReports"]) == 3
    ids = {e["expertId"] for e in turn["expertReports"]}
    assert ids == {"gradient_risk", "charter_audit", "career_city"}


@pytest.mark.asyncio
async def test_semantic_cache_hits_on_repeated_policy_query(runtime):
    _, _, _, _, graph = runtime
    q = "贵州新高考96个平行志愿有专业调剂吗？"
    first = await graph.run_turn("t-cache-1", "", "GZ", q)
    assert first["cacheHit"] is False
    second = await graph.run_turn("t-cache-2", "", "GZ", q)
    assert second["cacheHit"] is True
