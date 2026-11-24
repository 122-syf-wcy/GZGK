from __future__ import annotations

import time
from collections import OrderedDict
from dataclasses import dataclass
from typing import Any

from app.rag.hybrid_retriever import _cosine, _hash_embed


@dataclass
class SemanticCacheEntry:
    query: str
    vector: list[float]
    answer: str
    sources: list[dict[str, Any]]
    created_at: float


class SemanticCache:
    """
    Cosine-similarity Semantic Cache for repetitive policy/methodology questions
    during Gaokao peaks (delivers <200ms TTFT without burning LLM tokens).
    Only caches general plan-independent policy & methodology queries.
    """

    def __init__(self, threshold: float = 0.94, max_entries: int = 512, ttl_seconds: int = 1800) -> None:
        self.threshold = threshold
        self.max_entries = max_entries
        self.ttl_seconds = ttl_seconds
        self._store: OrderedDict[str, SemanticCacheEntry] = OrderedDict()

    def lookup(self, query: str) -> SemanticCacheEntry | None:
        now = time.time()
        q_vec = _hash_embed(query)
        best: SemanticCacheEntry | None = None
        best_sim = 0.0
        expired_keys: list[str] = []

        for key, entry in self._store.items():
            if now - entry.created_at > self.ttl_seconds:
                expired_keys.append(key)
                continue
            sim = _cosine(q_vec, entry.vector)
            if sim >= self.threshold and sim > best_sim:
                best_sim = sim
                best = entry

        for k in expired_keys:
            self._store.pop(k, None)
        return best

    def put(self, query: str, answer: str, sources: list[dict[str, Any]]) -> None:
        vec = _hash_embed(query)
        self._store[query] = SemanticCacheEntry(query, vec, answer, sources, time.time())
        self._store.move_to_end(query)
        while len(self._store) > self.max_entries:
            self._store.popitem(last=False)
