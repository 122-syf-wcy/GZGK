from __future__ import annotations

import json
import re
import uuid
from typing import Any

from langchain_core.messages import AIMessage, AnyMessage, HumanMessage, ToolMessage
from langchain_openai import ChatOpenAI

from app.config import AgentSettings


class DeterministicToolCallingModel:
    """
    Protocol-faithful multi-turn ReAct Tool-Calling LLM simulator used when `LLM_MOCK_MODE=true`.
    Unlike bypassing the graph, it emits genuine LangChain `AIMessage(tool_calls=[...])` frames
    so the EXACT SAME LangGraph `react_agent <-> ToolNode` loop, parallel tool invocation,
    MCP dynamic tools, observation pruning, and HITL interrupt path execute end-to-end.
    """

    def __init__(self, bound_tools: list[Any] | None = None) -> None:
        self._tool_names = {t.name for t in (bound_tools or [])}

    def bind_tools(self, tools: list[Any]) -> DeterministicToolCallingModel:
        return DeterministicToolCallingModel(tools)

    async def ainvoke(self, messages: list[AnyMessage]) -> AIMessage:
        # Locate the latest HumanMessage and any ToolMessages produced in THIS active turn
        last_human_idx = -1
        for i in range(len(messages) - 1, -1, -1):
            if isinstance(messages[i], HumanMessage):
                last_human_idx = i
                break

        user_text = str(messages[last_human_idx].content) if last_human_idx >= 0 else ""
        turn_slice = messages[last_human_idx + 1 :] if last_human_idx >= 0 else []
        called_in_turn = {
            tc.get("name")
            for m in turn_slice
            if isinstance(m, AIMessage) and m.tool_calls
            for tc in m.tool_calls
        }
        tool_outputs: dict[str, str] = {}
        for m in turn_slice:
            if isinstance(m, ToolMessage) and m.name:
                tool_outputs[m.name] = str(m.content)

        # Extract context metadata embedded in the SystemMessage
        sys_text = str(messages[0].content) if messages else ""
        plan_m = re.search(r"plan_id=([^\s,|]+)", sys_text)
        prov_m = re.search(r"province_code=([A-Z]{2})", sys_text)
        plan_id = plan_m.group(1) if plan_m else "demo-gz-586"
        prov = prov_m.group(1) if prov_m else "GZ"

        wants_sandbox = any(k in user_text for k in ("替换", "去掉", "删掉", "增补", "补2个", "补几个", "调优", "修改方案", "优化志愿", "缩招"))
        wants_career = any(k in user_text for k in ("就业", "读研", "前景", "课程", "软件工程", "计算机", "电子信息", "自动化"))
        wants_medical = any(k in user_text for k in ("色弱", "色盲", "体检", "限报", "单科", "学费"))
        wants_policy = any(k in user_text for k in ("政策", "调剂", "平行志愿", "专业组", "退档", "规则"))
        score_m = re.search(r"([3-7]\d{2})\s*分", user_text)
        school_hit = next(
            (s for s in ("重庆邮电大学", "贵州大学", "西南大学", "福州大学", "昆明理工大学") if s in user_text or s.replace("大学", "") in user_text),
            "",
        )

        # ---- ReAct Round 1: Parallel multi-tool discovery (Native gRPC + Hybrid RAG + MCP!) ----
        if not called_in_turn:
            calls: list[dict[str, Any]] = []
            if score_m and "estimate_rank" in self._tool_names:
                calls.append(self._tc("estimate_rank", {"province_code": prov, "subject_type": "物理类", "score": int(score_m.group(1))}))
            if school_hit and "query_school_major_history" in self._tool_names:
                calls.append(self._tc("query_school_major_history", {"province_code": prov, "school_name": school_hit}))
            if wants_sandbox and "get_plan_items" in self._tool_names:
                calls.append(self._tc("get_plan_items", {"plan_id": plan_id, "only_shrink_warning": True}))
            if "search_knowledge_hybrid" in self._tool_names:
                q = user_text + (" 重庆邮电大学 计算机 章程 缩招" if wants_sandbox else "")
                calls.append(self._tc("search_knowledge_hybrid", {"query": q, "province_code": prov, "top_k": 3}))
            if wants_policy and "mcp_lookup_province_policy" in self._tool_names:
                calls.append(self._tc("mcp_lookup_province_policy", {"province_code": prov}))
            if wants_career and "mcp_major_career_insights" in self._tool_names:
                major = next((m for m in ("计算机科学与技术", "软件工程", "电子信息类", "自动化") if m[:2] in user_text), "计算机科学与技术")
                calls.append(self._tc("mcp_major_career_insights", {"major_name": major}))
            if (wants_medical or wants_sandbox) and "mcp_check_medical_and_subject_redlines" in self._tool_names:
                calls.append(self._tc("mcp_check_medical_and_subject_redlines", {"major_name": "计算机科学与技术", "student_notes": user_text}))

            if calls:
                return AIMessage(content="", tool_calls=calls)

        # ---- ReAct Round 2: Follow-up Java Monte Carlo Sandbox tool after inspecting plan & charters ----
        if wants_sandbox and "simulate_plan_adjustment" in self._tool_names and "simulate_plan_adjustment" not in called_in_turn:
            return AIMessage(
                content="",
                tool_calls=[
                    self._tc(
                        "simulate_plan_adjustment",
                        {
                            "plan_id": plan_id,
                            "remove_index": 5,
                            "add_school_name": "重庆邮电大学",
                            "add_major_name": "计算机科学与技术",
                            "insert_index": 19,
                            "reason": "替换冲档计划大幅缩减条目，增补川渝公办计算机稳档（章程已核验）",
                        },
                    )
                ],
            )

        # ---- Final Synthesis Turn: Grounded answer strictly citing actual ToolMessage payloads ----
        sections: list[str] = []

        if "estimate_rank" in tool_outputs:
            data = self._safe_json(tool_outputs["estimate_rank"]).get("estimate", {})
            sections.append(
                f"**官方一分一段定位**：{data.get('score')} 分对应全省参考位次 **{data.get('rankLow', 8412):,} ~ {data.get('rankHigh', 8530):,} 名**"
                f"（同分约 {data.get('segmentCount', 118)} 人，超越约 {data.get('beatPercentage', 95.8)}% 考生）。"
            )

        if "query_school_major_history" in tool_outputs:
            recs = self._safe_json(tool_outputs["query_school_major_history"]).get("records", [])
            if recs:
                lines = [
                    f"- {r.get('year')} 年 **{r.get('universityName')} · {r.get('majorName')}**：最低分 {r.get('minScore')} / 位次 **{r.get('minRank')}**（选科：{r.get('resubjectRequirement') or '不限'}）"
                    for r in recs[:4]
                ]
                sections.append("**核心分数线库核查**：\n" + "\n".join(lines))

        if "mcp_lookup_province_policy" in tool_outputs:
            pol = self._safe_json(tool_outputs["mcp_lookup_province_policy"])
            sections.append(
                f"**MCP 省份政策核验（{pol.get('province') or pol.get('provinceName', prov)}）**："
                f"{pol.get('mode') or pol.get('subjectMode', '3+1+2')} 模式，容量 **{pol.get('maxVolunteers') or pol.get('maxVolunteerCount', 96)}** 志愿；"
                f"{pol.get('notes') or pol.get('volunteerModeLabel', '')}"
            )

        if "mcp_major_career_insights" in tool_outputs:
            car = self._safe_json(tool_outputs["mcp_major_career_insights"])
            sections.append(
                f"**MCP 专业就业与读研画像（{car.get('majorName')}）**：读研参考比例 {car.get('postgradRate')}，"
                f"核心就业城市：{' / '.join(car.get('typicalCities', [])[:4])}。避坑提醒：{car.get('riskHints')}"
            )

        if "mcp_check_medical_and_subject_redlines" in tool_outputs:
            med = self._safe_json(tool_outputs["mcp_check_medical_and_subject_redlines"])
            warns = med.get("warnings") or []
            if warns:
                sections.append("**MCP 体检与单科红线核查**：" + "；".join(warns))

        if "simulate_plan_adjustment" in tool_outputs:
            sim = self._safe_json(tool_outputs["simulate_plan_adjustment"]).get("simulation", {})
            b_pct = round(float(sim.get("beforeListHitRate", 0.961)) * 100, 1)
            a_pct = round(float(sim.get("afterListHitRate", 0.984)) * 100, 1)
            d_pct = round((a_pct - b_pct), 1)
            sign = "+" if d_pct >= 0 else ""
            sections.append(
                f"**Java 蒙特卡洛 2000 次沙箱量化对比**：移除冲档大幅缩招条目，在第 #19 位增补 **重庆邮电大学 · 计算机科学与技术** [1][2]，"
                f"整表投档参考度由 **{b_pct}% → {a_pct}%（{sign}{d_pct}%）**，梯度衔接更平稳。\n\n"
                "请在下方「沙箱变更确认」卡片中决定是否采纳并写入左侧志愿工作台。"
            )
        elif "search_knowledge_hybrid" in tool_outputs:
            srcs = self._safe_json(tool_outputs["search_knowledge_hybrid"]).get("sources", [])
            if srcs:
                cite_lines = "\n".join([f"- **{s['title']}** [{s['index']}]：{s['highlight']}" for s in srcs[:3]])
                sections.append("**招生政策与章程原文依据**：\n" + cite_lines)

        # Reference long-term memory naturally when recalled in system prompt
        if "长期记忆" in sys_text and "暂无跨会话长期偏好记忆" not in sys_text and ("回忆" in user_text or "记住" in user_text or "偏好" in user_text or "适合" in user_text):
            mem_block = sys_text.split("【长期记忆 · 考生偏好画像】")[-1].split("【")[0].strip()
            if mem_block:
                sections.append(f"**已自动结合你的跨会话长期偏好记忆**：\n{mem_block}")

        if not sections:
            sections.append("已完成信息检索与核验。你可以继续让我做「缩招沙箱替换推演」「院校历年位次对比」或「多专家深度报告」。")

        return AIMessage(content="\n\n".join(sections))

    @staticmethod
    def _tc(name: str, args: dict[str, Any]) -> dict[str, Any]:
        return {"id": f"call_{uuid.uuid4().hex[:10]}", "name": name, "args": args, "type": "tool_call"}

    @staticmethod
    def _safe_json(raw: str) -> dict[str, Any]:
        try:
            obj = json.loads(raw)
            return obj if isinstance(obj, dict) else {}
        except Exception:
            return {}


def build_tool_calling_llm(cfg: AgentSettings, tools: list[Any]) -> Any:
    if cfg.use_live_llm:
        primary = ChatOpenAI(
            base_url=cfg.llm_base_url,
            api_key=cfg.llm_api_key,
            model=cfg.llm_primary_model,
            temperature=0.2,
            timeout=45,
        ).bind_tools(tools)
        if cfg.llm_backup_base_url and cfg.llm_backup_api_key:
            backup = ChatOpenAI(
                base_url=cfg.llm_backup_base_url,
                api_key=cfg.llm_backup_api_key,
                model=cfg.llm_backup_model or cfg.llm_primary_model,
                temperature=0.2,
                timeout=45,
            ).bind_tools(tools)
            return primary.with_fallbacks([backup])
        return primary
    return DeterministicToolCallingModel(tools)
