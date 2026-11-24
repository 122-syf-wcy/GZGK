from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

STANDARD_DISCLAIMER = (
    "【公益辅助提示】以上分析及数据仅供志愿填报参考，不构成任何录取承诺，请以省教育考试院及高校官方招生章程为准。"
)

BUILTIN_HARD_TERMS = [
    "保证不滑档", "随便报都能上", "百分百录取", "100%录取", "保证录取", "一定录取",
    "绝对录取", "确保录取", "铁定录取", "上岸概率", "录取概率", "绝对安全",
    "没有风险", "内部信息", "内部数据", "一定能上", "确定能上", "肯定能上",
    "保录取", "不滑档", "不脱档", "成功率", "录取率", "上榜率", "命中率",
    "包录取", "百分百", "零风险", "闭眼报", "稳上", "必上", "保送",
    "包过", "稳进", "稳过", "保录", "保上", "必录", "包上",
]

BUILTIN_SOFT_TERMS = ["一定能", "绝对能", "铁定", "稳了"]


@dataclass
class SanitizeOutcome:
    text: str
    hits: list[str]
    modified: bool


class ComplianceGuardrail:
    """Loads shared regulatory lexicon from `contracts/compliance-terms.json`."""

    def __init__(self, contracts_root: str | Path) -> None:
        path = Path(contracts_root) / "compliance-terms.json"
        hard = list(BUILTIN_HARD_TERMS)
        soft = list(BUILTIN_SOFT_TERMS)
        if path.exists():
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
                if data.get("hard"):
                    hard = list(dict.fromkeys(data["hard"]))
                if data.get("soft"):
                    soft = list(dict.fromkeys(data["soft"]))
            except Exception:
                pass
        self.hard_terms = sorted(hard, key=len, reverse=True)
        self.soft_terms = sorted(soft, key=len, reverse=True)
        self.max_term_len = max((len(t) for t in (self.hard_terms + self.soft_terms)), default=8)

    @staticmethod
    def _replacement(term: str) -> str:
        if "概率" in term or "率" in term:
            return "机会参考度"
        if any(k in term for k in ("滑档", "脱档", "风险", "安全")):
            return "风险控制参考"
        return "历史参考评估"

    def sanitize(self, text: str) -> SanitizeOutcome:
        if not text:
            return SanitizeOutcome("", [], False)
        out = text
        hits: list[str] = []
        for term in self.hard_terms:
            if term in out:
                hits.append(term)
                out = out.replace(term, self._replacement(term))
        for term in self.soft_terms:
            if term in out:
                hits.append(term)
                out = out.replace(term, "相对更有机会")
        return SanitizeOutcome(out, hits, bool(hits))

    def new_stream_buffer(self) -> StreamingComplianceBuffer:
        return StreamingComplianceBuffer(self)


class StreamingComplianceBuffer:
    """
    Sliding-window real-time compliance filter for SSE token streaming.
    Retains a tail window of `max(12, max_term_len)` chars before emitting chunks,
    preventing split-token regulatory leaks (e.g., "100%" + "录" + "取") from ever
    flashing on the user's screen.
    """

    def __init__(self, guardrail: ComplianceGuardrail) -> None:
        self._guardrail = guardrail
        self._window = max(12, guardrail.max_term_len)
        self._buf = ""
        self.hits: list[str] = []

    def push(self, delta: str) -> str:
        if not delta:
            return ""
        self._buf += delta
        outcome = self._guardrail.sanitize(self._buf)
        if outcome.modified:
            self._buf = outcome.text
            for h in outcome.hits:
                if h not in self.hits:
                    self.hits.append(h)
        if len(self._buf) <= self._window:
            return ""
        emit_len = len(self._buf) - self._window
        to_emit = self._buf[:emit_len]
        self._buf = self._buf[emit_len:]
        return to_emit

    def flush(self) -> str:
        if not self._buf:
            return ""
        outcome = self._guardrail.sanitize(self._buf)
        if outcome.modified:
            for h in outcome.hits:
                if h not in self.hits:
                    self.hits.append(h)
        rem = outcome.text
        self._buf = ""
        return rem
