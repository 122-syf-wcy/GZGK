-- ============================================================================
-- V4: PGVector HNSW + Full-Text Hybrid RAG Store & LangGraph Checkpointer Schema
-- Single PostgreSQL 16 instance serves OLTP + Vector + FTS + Agent State!
-- ============================================================================

CREATE TABLE IF NOT EXISTS kb_document (
    doc_id        VARCHAR(64)  PRIMARY KEY,
    doc_type      VARCHAR(32)  NOT NULL, -- policy | charter | strategy_skill
    province_code VARCHAR(10)  NOT NULL DEFAULT 'ALL',
    school_id     VARCHAR(32)  NOT NULL DEFAULT '',
    school_name   VARCHAR(120) NOT NULL DEFAULT '',
    year          SMALLINT     NOT NULL DEFAULT 2025,
    title         VARCHAR(300) NOT NULL,
    source_url    VARCHAR(500) NOT NULL DEFAULT '',
    content_hash  VARCHAR(64)  NOT NULL DEFAULT '',
    verified      BOOLEAN      NOT NULL DEFAULT TRUE,
    metadata      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kb_doc_filter ON kb_document (doc_type, province_code, year);

CREATE TABLE IF NOT EXISTS kb_document_chunk (
    chunk_id      VARCHAR(80)  PRIMARY KEY,
    doc_id        VARCHAR(64)  NOT NULL REFERENCES kb_document(doc_id) ON DELETE CASCADE,
    chunk_index   INT          NOT NULL,
    doc_type      VARCHAR(32)  NOT NULL,
    province_code VARCHAR(10)  NOT NULL DEFAULT 'ALL',
    school_id     VARCHAR(32)  NOT NULL DEFAULT '',
    school_name   VARCHAR(120) NOT NULL DEFAULT '',
    year          SMALLINT     NOT NULL DEFAULT 2025,
    parent_title  VARCHAR(300) NOT NULL DEFAULT '',
    content       TEXT         NOT NULL,
    source_url    VARCHAR(500) NOT NULL DEFAULT '',
    verified      BOOLEAN      NOT NULL DEFAULT TRUE,
    embedding     vector(1024),
    content_tsv   tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(parent_title, '') || ' ' || coalesce(school_name, '') || ' ' || content)
    ) STORED,
    metadata      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (doc_id, chunk_index)
);

-- HNSW Vector Index for low-latency Approximate Nearest Neighbor recall
CREATE INDEX IF NOT EXISTS idx_kb_chunk_embedding_hnsw
    ON kb_document_chunk USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 128);

-- Hybrid Sparse Recall: Full-Text GIN + Trigram GIN (supports Chinese exact substrings natively!)
CREATE INDEX IF NOT EXISTS idx_kb_chunk_tsv_gin ON kb_document_chunk USING GIN (content_tsv);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_content_trgm ON kb_document_chunk USING GIN (content gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_meta_filter ON kb_document_chunk (province_code, doc_type, year, school_name);

-- Dedicated schema for LangGraph AsyncPostgresSaver checkpoint persistence
CREATE SCHEMA IF NOT EXISTS langgraph;
