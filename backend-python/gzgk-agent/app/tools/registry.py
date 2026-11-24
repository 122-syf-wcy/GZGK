from __future__ import annotations

import json
from typing import Any

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, Field

from app.clients.core_client import CoreServiceClient
from app.mcp.client import McpToolRegistry
from app.memory.long_term import LongTermMemoryManager
from app.rag.hybrid_retriever import HybridRagRetriever


class EstimateRankArgs(BaseModel):
    province_code: str = Field(default="GZ", description="省份代码，如 GZ / SC / HB")
    subject_type: str = Field(default="物理类", description="科类：物理类 / 历史类 / 综合")
    score: int = Field(..., description="考生高考总分，如 586")


class QuerySchoolHistoryArgs(BaseModel):
    province_code: str = Field(default="GZ", description="省份代码")
    school_name: str = Field(..., description="院校名称，如 重庆邮电大学 / 贵州大学")
    major_name: str = Field(default="", description="可选专业关键词，如 计算机")
    subject_type: str = Field(default="物理类", description="科类")


class GetPlanItemsArgs(BaseModel):
    plan_id: str = Field(default="", description="当前志愿方案 ID")
    only_shrink_warning: bool = Field(default=False, description="是否仅返回存在明显缩招预警的条目")
    gradient: str = Field(default="", description="可选按梯度过滤：冲 / 稳 / 保 / 垫")


class SearchKnowledgeArgs(BaseModel):
    query: str = Field(..., description="检索关键词或问题，如 重庆邮电大学 招生章程 单科限制")
    province_code: str = Field(default="GZ", description="省份代码")
    top_k: int = Field(default=3, description="返回最相关切片数")


class SimulateSandboxArgs(BaseModel):
    plan_id: str = Field(default="", description="目标方案 ID")
    remove_index: int = Field(default=5, description="待移除或替换的原志愿序号（1..96）")
    add_school_name: str = Field(default="重庆邮电大学", description="待增补院校名称")
    add_major_name: str = Field(default="计算机科学与技术", description="待增补专业名称")
    insert_index: int = Field(default=19, description="建议插入的目标位次序号")
    reason: str = Field(default="替换高波动缩招冲档，补充川渝计算机稳档", description="调优理由")


class SavePreferenceArgs(BaseModel):
    user_id: str = Field(default="demo-student", description="考生标识")
    category: str = Field(..., description="偏好类别：意向城市 / 意向专业 / 学费预算 / 体检限报 / 发展目标")
    value: str = Field(..., description="需要跨会话长期记住的具体约束或偏好")


class UnifiedToolRegistry:
    """
    Combines Native Java gRPC/Core Deterministic Tools, Hybrid PGVector RAG Tools,
    Long-Term Memory Tools, AND Dynamically Discovered MCP Server Tools into one
    unified LangChain StructuredTool registry bound to the ReAct Agent LLM.
    """

    def __init__(
        self,
        core_client: CoreServiceClient,
        retriever: HybridRagRetriever,
        memory_manager: LongTermMemoryManager,
        mcp_registry: McpToolRegistry,
    ) -> None:
        self.core_client = core_client
        self.retriever = retriever
        self.memory_manager = memory_manager
        self.mcp_registry = mcp_registry
        self._native_tools = self._build_native_tools()
        self._all_tools: list[StructuredTool] | None = None

    def _build_native_tools(self) -> list[StructuredTool]:
        async def estimate_rank_fn(province_code: str = "GZ", subject_type: str = "物理类", score: int = 586) -> str:
            est, ms = await self.core_client.estimate_rank(province_code, subject_type, score)
            return json.dumps({"latencyMs": ms, "estimate": est}, ensure_ascii=False)

        async def query_school_history_fn(
            province_code: str = "GZ", school_name: str = "", major_name: str = "", subject_type: str = "物理类"
        ) -> str:
            rows, ms = await self.core_client.query_school_history(province_code, school_name, major_name, subject_type)
            return json.dumps({"latencyMs": ms, "count": len(rows), "records": rows[:6]}, ensure_ascii=False)

        async def get_plan_items_fn(plan_id: str = "", only_shrink_warning: bool = False, gradient: str = "") -> str:
            summary, ms = await self.core_client.get_plan_summary(plan_id)
            items = summary.get("items") or []
            if gradient:
                items = [i for i in items if i.get("gradient") == gradient]
            if only_shrink_warning:
                items = [i for i in items if float(i.get("planChangeRate") or 0) <= -0.25]
            return json.dumps(
                {
                    "latencyMs": ms,
                    "planId": summary.get("planId"),
                    "version": summary.get("version", 1),
                    "gradientCounts": summary.get("gradientCounts"),
                    "listHitRate": summary.get("listHitRate"),
                    "matchedItems": items[:8],
                },
                ensure_ascii=False,
            )

        async def search_knowledge_hybrid_fn(query: str, province_code: str = "GZ", top_k: int = 3) -> str:
            chunks = await self.retriever.search(query, province_code, top_k=top_k)
            payload = [
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
                }
                for c in chunks
            ]
            return json.dumps({"sources": payload}, ensure_ascii=False)

        async def simulate_plan_adjustment_fn(
            plan_id: str = "",
            remove_index: int = 5,
            add_school_name: str = "重庆邮电大学",
            add_major_name: str = "计算机科学与技术",
            insert_index: int = 19,
            reason: str = "替换高波动缩招冲档，补充川渝计算机稳档",
        ) -> str:
            ops = [
                {
                    "opType": "REMOVE",
                    "targetIndex": remove_index,
                    "newIndex": 0,
                    "schoolId": "",
                    "universityName": "",
                    "majorName": "",
                    "reason": "冲档计划明显缩减，波动风险抬升",
                },
                {
                    "opType": "ADD",
                    "targetIndex": 0,
                    "newIndex": insert_index,
                    "schoolId": "141" if "重庆邮电" in add_school_name else "",
                    "universityName": add_school_name,
                    "majorName": add_major_name,
                    "reason": reason,
                },
            ]
            sim, ms = await self.core_client.simulate_plan_adjustment(plan_id, ops)
            return json.dumps({"latencyMs": ms, "simulation": sim, "operations": ops}, ensure_ascii=False)

        async def save_student_preference_fn(user_id: str = "demo-student", category: str = "偏好", value: str = "") -> str:
            rec = self.memory_manager.remember(user_id, f"agent_{abs(hash(category + value)) % 10000}", category, value)
            return json.dumps({"saved": True, "memory": rec}, ensure_ascii=False)

        return [
            StructuredTool.from_function(
                coroutine=estimate_rank_fn,
                name="estimate_rank",
                description="[Core gRPC] 按省份、科类与高考总分查询官方一分一段位次区间。",
                args_schema=EstimateRankArgs,
            ),
            StructuredTool.from_function(
                coroutine=query_school_history_fn,
                name="query_school_major_history",
                description="[Core gRPC] 查询目标高校（可选专业）近三年官方录取最低分、最低位次与招生计划。",
                args_schema=QuerySchoolHistoryArgs,
            ),
            StructuredTool.from_function(
                coroutine=get_plan_items_fn,
                name="get_plan_items",
                description="[Core gRPC] 查看当前志愿方案概览、梯度分布，并可筛选缩招预警或指定梯度条目。",
                args_schema=GetPlanItemsArgs,
            ),
            StructuredTool.from_function(
                coroutine=search_knowledge_hybrid_fn,
                name="search_knowledge_hybrid",
                description="[PGVector Hybrid RAG] 混合检索省招考政策、志愿策略库与高校官方招生章程原文切片（带溯源 ID）。",
                args_schema=SearchKnowledgeArgs,
            ),
            StructuredTool.from_function(
                coroutine=simulate_plan_adjustment_fn,
                name="simulate_plan_adjustment",
                description="[Java Monte Carlo Sandbox] 无副作用沙箱推演移除/新增志愿后的梯度变化与 2000 次蒙特卡洛防滑档参考度对比，生成待考生确认的 HITL Diff 卡片。",
                args_schema=SimulateSandboxArgs,
            ),
            StructuredTool.from_function(
                coroutine=save_student_preference_fn,
                name="save_student_preference",
                description="[Long-Term Memory] 将考生的长期偏好或硬性红线（城市/预算/体检/专业方向）持久化写入跨会话长期记忆。",
                args_schema=SavePreferenceArgs,
            ),
        ]

    async def get_all_tools(self) -> list[StructuredTool]:
        if self._all_tools is None:
            mcp_tools = await self.mcp_registry.load_tools()
            self._all_tools = [*self._native_tools, *mcp_tools]
        return self._all_tools

    async def catalog(self) -> list[dict[str, Any]]:
        await self.get_all_tools()
        native = [
            {
                "name": t.name,
                "source": "native-grpc",
                "server": "gzgk-core",
                "description": t.description,
            }
            for t in self._native_tools
        ]
        return native + self.mcp_registry.catalog()
