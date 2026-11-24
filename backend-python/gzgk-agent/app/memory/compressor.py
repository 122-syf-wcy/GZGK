from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any

import tiktoken
from langchain_core.messages import AIMessage, AnyMessage, HumanMessage, RemoveMessage, SystemMessage, ToolMessage


@dataclass
class ContextCompressionResult:
    removals: list[RemoveMessage]
    updated_summary: str
    compressed: bool
    pruned_tool_outputs: int
    before_tokens: int
    after_tokens: int
    budget_tokens: int
    kept_messages: int


class ContextWindowManager:
    """
    Production 3-Layer Context Window & Rolling Compression Engine:
      1. Accurate `tiktoken` (`cl100k_base`) token budgeting across System, Summary, History & Tool Calls.
      2. Layer-1 Observation Pruning: compacts oversized old `ToolMessage` payloads while preserving
         numerical facts & citation IDs.
      3. Layer-2/3 Atomic Boundary-Safe Rolling Summarization + LangGraph `RemoveMessage`:
         Compresses older conversation turns into an incremental `running_summary` while NEVER
         splitting an `AIMessage(tool_calls)` from its corresponding `ToolMessage`s.
    """

    def __init__(
        self,
        budget_tokens: int = 6000,
        trigger_tokens: int = 3800,
        keep_recent_messages: int = 6,
        tool_output_max_tokens: int = 380,
    ) -> None:
        self.budget_tokens = budget_tokens
        self.trigger_tokens = trigger_tokens
        self.keep_recent_messages = keep_recent_messages
        self.tool_output_max_tokens = tool_output_max_tokens
        try:
            self._enc = tiktoken.get_encoding("cl100k_base")
        except Exception:
            self._enc = None

    def count_text_tokens(self, text: str) -> int:
        if not text:
            return 0
        if self._enc is not None:
            return len(self._enc.encode(text))
        return max(1, len(text) // 2)

    def count_message_tokens(self, msg: AnyMessage) -> int:
        total = 6  # role & framing overhead
        content = msg.content
        if isinstance(content, str):
            total += self.count_text_tokens(content)
        else:
            total += self.count_text_tokens(json.dumps(content, ensure_ascii=False, default=str))
        if isinstance(msg, AIMessage) and msg.tool_calls:
            total += self.count_text_tokens(json.dumps(msg.tool_calls, ensure_ascii=False, default=str))
        return total

    def count_messages_tokens(self, messages: list[AnyMessage], running_summary: str = "") -> int:
        return sum(self.count_message_tokens(m) for m in messages) + self.count_text_tokens(running_summary)

    def prune_old_tool_outputs(self, messages: list[AnyMessage]) -> tuple[list[AnyMessage], int]:
        """
        Returns a prompt-ready copy of messages where older verbose ToolMessages (> tool_output_max_tokens)
        are trimmed into concise structured previews without mutating persisted raw checkpointer state.
        """
        if len(messages) <= 4:
            return list(messages), 0
        cutoff = len(messages) - 4
        pruned_count = 0
        out: list[AnyMessage] = []
        for idx, m in enumerate(messages):
            if idx < cutoff and isinstance(m, ToolMessage) and isinstance(m.content, str):
                toks = self.count_text_tokens(m.content)
                if toks > self.tool_output_max_tokens:
                    compact = m.content[:420].rstrip() + f" ...[已裁剪旧工具输出，原长 {toks} tokens]"
                    out.append(ToolMessage(content=compact, tool_call_id=m.tool_call_id, name=m.name, id=m.id))
                    pruned_count += 1
                    continue
            out.append(m)
        return out, pruned_count

    def _safe_split_index(self, messages: list[AnyMessage], keep_recent: int) -> int:
        """
        Finds the largest split point <= len(messages) - keep_recent that NEVER orphans
        a ToolMessage from its preceding AIMessage(tool_calls).
        """
        n = len(messages)
        if n <= keep_recent:
            return 0
        split = n - keep_recent
        # Walk backward while split lands on a ToolMessage or directly after an AIMessage with tool_calls
        while split > 0:
            cur = messages[split]
            prev = messages[split - 1]
            if isinstance(cur, ToolMessage):
                split -= 1
                continue
            if isinstance(prev, AIMessage) and prev.tool_calls:
                split -= 1
                continue
            break
        return split

    async def compact_if_needed(
        self,
        messages: list[AnyMessage],
        running_summary: str = "",
        llm: Any = None,
        force: bool = False,
    ) -> ContextCompressionResult:
        before_tokens = self.count_messages_tokens(messages, running_summary)
        _, pruned_count = self.prune_old_tool_outputs(messages)

        should_compress = force or (before_tokens >= self.trigger_tokens)
        if not should_compress or len(messages) <= self.keep_recent_messages:
            return ContextCompressionResult(
                removals=[],
                updated_summary=running_summary,
                compressed=False,
                pruned_tool_outputs=pruned_count,
                before_tokens=before_tokens,
                after_tokens=before_tokens,
                budget_tokens=self.budget_tokens,
                kept_messages=len(messages),
            )

        keep_n = min(self.keep_recent_messages, max(2, len(messages) - 2)) if force else self.keep_recent_messages
        split = self._safe_split_index(messages, keep_n)
        if split <= 0:
            return ContextCompressionResult(
                removals=[],
                updated_summary=running_summary,
                compressed=False,
                pruned_tool_outputs=pruned_count,
                before_tokens=before_tokens,
                after_tokens=before_tokens,
                budget_tokens=self.budget_tokens,
                kept_messages=len(messages),
            )

        evict_slice = messages[:split]
        kept_slice = messages[split:]
        new_summary = await self._summarize_slice(running_summary, evict_slice, llm)
        removals = [RemoveMessage(id=m.id) for m in evict_slice if getattr(m, "id", None)]
        after_tokens = self.count_messages_tokens(kept_slice, new_summary)

        return ContextCompressionResult(
            removals=removals,
            updated_summary=new_summary,
            compressed=True,
            pruned_tool_outputs=pruned_count,
            before_tokens=before_tokens,
            after_tokens=after_tokens,
            budget_tokens=self.budget_tokens,
            kept_messages=len(kept_slice),
        )

    async def _summarize_slice(self, prev_summary: str, slice_msgs: list[AnyMessage], llm: Any = None) -> str:
        transcript_lines: list[str] = []
        for m in slice_msgs:
            if isinstance(m, HumanMessage):
                transcript_lines.append(f"考生：{str(m.content)[:200]}")
            elif isinstance(m, AIMessage):
                if m.tool_calls:
                    t_names = ", ".join(tc.get("name", "") for tc in m.tool_calls)
                    transcript_lines.append(f"顾问调用工具：[{t_names}]")
                if m.content:
                    transcript_lines.append(f"顾问结论：{str(m.content)[:240]}")
            elif isinstance(m, ToolMessage):
                transcript_lines.append(f"工具返回({m.name or 'tool'})：{str(m.content)[:180]}")

        block = "\n".join(transcript_lines)
        if llm is not None:
            try:
                prompt = (
                    "你是对话上下文压缩器。请将「历史摘要」与「新增轮次」合并为不超过 260 字的结构化中文滚动摘要，"
                    "必须保留：考生分数/位次/偏好、已查过的院校和关键位次、已做过的沙箱修改或拒绝记录、尚未解决的问题。\n\n"
                    f"【历史摘要】\n{prev_summary or '无'}\n\n【新增轮次】\n{block}"
                )
                resp = await llm.ainvoke([SystemMessage(content=prompt)])
                text = str(resp.content).strip()
                if text:
                    return text[:600]
            except Exception:
                pass

        # Deterministic structured progressive summary fallback
        user_turns = [str(m.content).strip()[:60] for m in slice_msgs if isinstance(m, HumanMessage)]
        tools_used = sorted(
            {
                tc.get("name", "")
                for m in slice_msgs
                if isinstance(m, AIMessage) and m.tool_calls
                for tc in m.tool_calls
                if tc.get("name")
            }
        )
        ai_notes = [str(m.content).strip().replace("\n", " ")[:110] for m in slice_msgs if isinstance(m, AIMessage) and m.content]
        parts: list[str] = []
        if prev_summary:
            parts.append(prev_summary.strip()[:260])
        if user_turns:
            parts.append("历史关注点：" + "；".join(user_turns[-3:]))
        if tools_used:
            parts.append("已核查工具：" + "、".join(tools_used))
        if ai_notes:
            parts.append("阶段结论：" + ai_notes[-1])
        return " | ".join(parts)[:560]

    def telemetry(
        self,
        messages: list[AnyMessage],
        running_summary: str,
        compression_count: int,
        memory_count: int,
        native_tools_count: int,
        mcp_tools_count: int,
    ) -> dict[str, Any]:
        total = self.count_messages_tokens(messages, running_summary)
        return {
            "totalTokens": total,
            "budgetTokens": self.budget_tokens,
            "triggerTokens": self.trigger_tokens,
            "usagePercent": min(100, round((total / max(1, self.budget_tokens)) * 100, 1)),
            "messageCount": len(messages),
            "compressionCount": compression_count,
            "hasRunningSummary": bool(running_summary.strip()),
            "runningSummaryPreview": running_summary[:180] if running_summary else "",
            "memoryCount": memory_count,
            "nativeToolsCount": native_tools_count,
            "mcpToolsCount": mcp_tools_count,
        }
