from __future__ import annotations

import re
import time
from typing import Any

from langgraph.store.memory import InMemoryStore


class LongTermMemoryManager:
    """
    Cross-thread Long-Term Semantic User Memory backed by LangGraph Store
    (`InMemoryStore` locally, seamlessly swappable to `PostgresStore` on PG16).

    Persists durable student constraints & preferences across separate threads:
      - region (e.g., 川渝 / 长三角 / 省内)
      - major (e.g., 计算机 / 软件工程 / 电子信息)
      - budget (e.g., 学费 ≤ 8000 / 不接受民办 / 不接受中外合作)
      - medical (e.g., 色弱 / 色盲体检注意)
      - career (e.g., 读研深造 / 直接就业 / 考公)
    """

    def __init__(self) -> None:
        self.store = InMemoryStore()

    @staticmethod
    def _ns(user_id: str) -> tuple[str, str]:
        return (user_id or "demo-student", "preferences")

    def remember(self, user_id: str, key: str, category: str, value: str, source_quote: str = "") -> dict[str, Any]:
        record = {
            "key": key,
            "category": category,
            "value": value,
            "sourceQuote": source_quote[:80],
            "updatedAt": int(time.time() * 1000),
        }
        self.store.put(self._ns(user_id), key, record)
        return record

    def forget(self, user_id: str, key: str) -> bool:
        existing = self.store.get(self._ns(user_id), key)
        if existing is None:
            return False
        self.store.delete(self._ns(user_id), key)
        return True

    def list_memories(self, user_id: str) -> list[dict[str, Any]]:
        items = self.store.search(self._ns(user_id), limit=30)
        records = [item.value for item in items if isinstance(item.value, dict)]
        return sorted(records, key=lambda r: r.get("updatedAt", 0), reverse=True)

    def format_for_system_prompt(self, user_id: str) -> str:
        memories = self.list_memories(user_id)
        if not memories:
            return "暂无跨会话长期偏好记忆。"
        lines = [f"- [{m.get('category', '偏好')}] {m.get('value', '')}" for m in memories]
        return "\n".join(lines)

    def extract_and_persist(self, user_id: str, user_message: str) -> list[dict[str, Any]]:
        """
        Extracts durable student preferences from natural user utterances so subsequent
        threads automatically recall them without the student repeating themselves.
        """
        msg = (user_message or "").strip()
        if not msg:
            return []

        saved: list[dict[str, Any]] = []

        # 1. Regions
        regions = [r for r in ("川渝", "重庆", "成都", "四川", "贵州", "长三角", "珠三角", "北京", "上海", "杭州", "深圳", "南京", "武汉", "西安", "西南") if r in msg]
        if regions:
            saved.append(self.remember(user_id, "pref_region", "意向城市", "倾向地域：" + "、".join(regions), msg))

        # 2. Majors
        majors = [m for m in ("计算机", "软件工程", "人工智能", "电子信息", "通信", "自动化", "电气", "临床医学", "法学", "金融", "师范") if m in msg]
        if majors:
            saved.append(self.remember(user_id, "pref_major", "意向专业", "优先专业方向：" + "、".join(majors), msg))

        # 3. School nature & budget redlines
        if any(k in msg for k in ("不接受民办", "不要民办", "只看公办", "只报公办")):
            saved.append(self.remember(user_id, "redline_private", "办学性质红线", "不接受民办高校，优先公办本科", msg))
        if any(k in msg for k in ("不接受中外合作", "不要中外合作", "不报中外合作")):
            saved.append(self.remember(user_id, "redline_sino", "学费与合作办学", "不接受高学费中外合作项目", msg))
        budget_m = re.search(r"预算\s*(\d{4,5})", msg)
        if budget_m:
            saved.append(self.remember(user_id, "pref_budget", "家庭学费预算", f"学费预算约 {budget_m.group(1)} 元/年以内", msg))

        # 4. Medical redlines
        if "色弱" in msg or "色盲" in msg:
            cond = "色盲" if "色盲" in msg else "色弱"
            saved.append(self.remember(user_id, "redline_medical", "体检限报约束", f"考生备注存在{cond}，需避开受限医学/化工/光电类专业", msg))

        # 5. Long-term career goal
        if any(k in msg for k in ("考研", "读研", "保研", "深造")):
            saved.append(self.remember(user_id, "pref_career", "发展目标", "以读研深造为主要规划方向，看重院校层次与保研率", msg))
        elif any(k in msg for k in ("考公", "体制内", "选调")):
            saved.append(self.remember(user_id, "pref_career", "发展目标", "倾向考公/体制内发展，重视专业对口与公办综合平台", msg))

        return saved
