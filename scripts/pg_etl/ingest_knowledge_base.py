#!/usr/bin/env python3
"""
Knowledge Base Chunker & PGVector Hybrid RAG Ingestion Pipeline.

Reads Markdown documents from `scripts/pg_etl/seed_kb_docs/` (plus optional extra dirs),
performs heading-aware semantic chunking (chunk=500 chars, overlap=80 chars, preserving
parent heading context), computes 1024-dim embeddings via OpenAI-compatible API (or
deterministic normalized n-gram hash fallback when no API key is configured), and upserts
into `kb_document` + `kb_document_chunk`.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import sys
import urllib.request
from pathlib import Path
from typing import Any

ROOT_DIR = Path(__file__).resolve().parents[2]
SEED_DOCS_DIR = ROOT_DIR / "scripts" / "pg_etl" / "seed_kb_docs"
EMBEDDING_DIM = int(os.environ.get("EMBEDDING_DIM", "1024"))


def parse_frontmatter(raw: str) -> tuple[dict[str, str], str]:
    if not raw.startswith("---"):
        return {}, raw
    parts = raw.split("---", 2)
    if len(parts) < 3:
        return {}, raw
    meta: dict[str, str] = {}
    for line in parts[1].splitlines():
        if ":" in line:
            k, v = line.split(":", 1)
            meta[k.strip()] = v.strip().strip('"').strip("'")
    return meta, parts[2].strip()


def chunk_markdown(body: str, default_title: str, chunk_size: int = 480, overlap: int = 80) -> list[tuple[str, str]]:
    """Heading-aware semantic splitter that keeps parent heading context per chunk."""
    sections: list[tuple[str, str]] = []
    cur_heading = default_title
    cur_lines: list[str] = []
    for line in body.splitlines():
        m = re.match(r"^#{1,3}\s+(.+)$", line.strip())
        if m:
            if cur_lines:
                sections.append((cur_heading, "\n".join(cur_lines).strip()))
                cur_lines = []
            cur_heading = m.group(1).strip()
        else:
            cur_lines.append(line)
    if cur_lines:
        sections.append((cur_heading, "\n".join(cur_lines).strip()))

    chunks: list[tuple[str, str]] = []
    for heading, text in sections:
        if not text:
            continue
        if len(text) <= chunk_size:
            chunks.append((heading, text))
            continue
        start = 0
        while start < len(text):
            end = min(len(text), start + chunk_size)
            piece = text[start:end].strip()
            if piece:
                chunks.append((heading, piece))
            if end >= len(text):
                break
            start = max(start + 1, end - overlap)
    return chunks


def hash_embedding(text: str, dim: int = EMBEDDING_DIM) -> list[float]:
    """Deterministic char 2-gram/3-gram projection fallback for offline & CI environments."""
    vec = [0.0] * dim
    cleaned = re.sub(r"\s+", "", text.lower())
    grams: list[str] = []
    for n in (2, 3):
        for i in range(max(0, len(cleaned) - n + 1)):
            grams.append(cleaned[i : i + n])
    if not grams:
        grams = [cleaned or "empty"]
    for g in grams:
        digest = hashlib.blake2b(g.encode("utf-8"), digest_size=8).digest()
        val = int.from_bytes(digest[:4], "little")
        sign = 1.0 if (digest[4] & 1) == 0 else -1.0
        vec[val % dim] += sign
    norm = math.sqrt(sum(v * v for v in vec)) or 1.0
    return [round(v / norm, 6) for v in vec]


def fetch_remote_embeddings(texts: list[str], base_url: str, api_key: str, model: str) -> list[list[float]]:
    url = base_url.rstrip("/") + "/embeddings"
    payload = json.dumps({"model": model, "input": texts, "dimensions": EMBEDDING_DIM}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {api_key}"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    ordered = sorted(data["data"], key=lambda x: x["index"])
    return [item["embedding"][:EMBEDDING_DIM] for item in ordered]


def embed_texts(texts: list[str]) -> list[list[float]]:
    base_url = os.environ.get("EMBEDDING_BASE_URL", "").strip()
    api_key = os.environ.get("EMBEDDING_API_KEY", "").strip()
    model = os.environ.get("EMBEDDING_MODEL", "text-embedding-v3").strip()
    if base_url and api_key:
        try:
            return fetch_remote_embeddings(texts, base_url, api_key, model)
        except Exception as exc:
            print(f"[kb-ingest] WARN: Remote embedding failed ({exc}); falling back to local deterministic vector.")
    return [hash_embedding(t) for t in texts]


def collect_documents(docs_dir: Path) -> list[dict[str, Any]]:
    docs: list[dict[str, Any]] = []
    for md_file in sorted(docs_dir.glob("*.md")):
        raw = md_file.read_text(encoding="utf-8")
        meta, body = parse_frontmatter(raw)
        doc_id = meta.get("doc_id") or md_file.stem
        title = meta.get("title") or md_file.stem
        chunks = chunk_markdown(body, title)
        docs.append(
            {
                "doc_id": doc_id,
                "doc_type": meta.get("doc_type", "policy"),
                "province_code": meta.get("province_code", "ALL"),
                "school_id": meta.get("school_id", ""),
                "school_name": meta.get("school_name", ""),
                "year": int(meta.get("year", "2025")),
                "title": title,
                "source_url": meta.get("source_url", ""),
                "content_hash": hashlib.sha256(raw.encode("utf-8")).hexdigest(),
                "chunks": chunks,
            }
        )
    return docs


def main() -> int:
    parser = argparse.ArgumentParser(description="Ingest policy/charter/strategy Markdown docs into PGVector.")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--dsn", default=os.environ.get("PG_DSN", ""))
    parser.add_argument("--docs-dir", default=str(SEED_DOCS_DIR))
    args = parser.parse_args()

    docs = collect_documents(Path(args.docs_dir))
    total_chunks = sum(len(d["chunks"]) for d in docs)
    print(f"[kb-ingest] Loaded {len(docs)} documents -> {total_chunks} semantic chunks.")

    if args.dry_run:
        assert len(docs) >= 4 and total_chunks >= 10
        sample_vec = hash_embedding("贵州物理类96平行志愿")
        assert len(sample_vec) == EMBEDDING_DIM
        print(f"[kb-ingest] DRY-RUN PASSED (embedding_dim={len(sample_vec)}).")
        return 0

    if not args.dsn:
        print("[kb-ingest] ERROR: Provide --dsn or set PG_DSN.", file=sys.stderr)
        return 1

    import psycopg

    with psycopg.connect(args.dsn, autocommit=False) as conn:
        with conn.cursor() as cur:
            for doc in docs:
                cur.execute(
                    """
                    INSERT INTO kb_document
                        (doc_id, doc_type, province_code, school_id, school_name, year, title, source_url, content_hash, verified)
                    VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,TRUE)
                    ON CONFLICT (doc_id) DO UPDATE SET
                        title = EXCLUDED.title,
                        content_hash = EXCLUDED.content_hash,
                        updated_at = NOW()
                    """,
                    (
                        doc["doc_id"], doc["doc_type"], doc["province_code"], doc["school_id"],
                        doc["school_name"], doc["year"], doc["title"], doc["source_url"], doc["content_hash"],
                    ),
                )
                texts = [f"{heading}\n{content}" for heading, content in doc["chunks"]]
                vectors = embed_texts(texts)
                for idx, ((heading, content), vec) in enumerate(zip(doc["chunks"], vectors)):
                    chunk_id = f"{doc['doc_id']}#{idx:03d}"
                    vec_literal = "[" + ",".join(f"{x:.6f}" for x in vec) + "]"
                    cur.execute(
                        """
                        INSERT INTO kb_document_chunk
                            (chunk_id, doc_id, chunk_index, doc_type, province_code, school_id, school_name,
                             year, parent_title, content, source_url, verified, embedding)
                        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,TRUE,%s::vector)
                        ON CONFLICT (chunk_id) DO UPDATE SET
                            parent_title = EXCLUDED.parent_title,
                            content = EXCLUDED.content,
                            embedding = EXCLUDED.embedding
                        """,
                        (
                            chunk_id, doc["doc_id"], idx, doc["doc_type"], doc["province_code"],
                            doc["school_id"], doc["school_name"], doc["year"], heading, content,
                            doc["source_url"], vec_literal,
                        ),
                    )
        conn.commit()
    print(f"[kb-ingest] Upserted {len(docs)} docs & {total_chunks} chunks into PGVector.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
