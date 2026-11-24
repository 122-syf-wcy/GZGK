-- ============================================================================
-- V3: Monthly-Partitioned Volunteer Plan Snapshots, HITL Mutation Log & AI Audit
-- Supports Kafka Async Persistence (Idempotent via event_id) & Zero-Lock Archiving
-- ============================================================================

-- 1. Volunteer Plan History — Declarative RANGE Partitioning by created_at (Monthly)
CREATE TABLE IF NOT EXISTS biz_plan_history (
    plan_id               VARCHAR(32)  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version               INT          NOT NULL DEFAULT 1, -- Optimistic locking for HITL mutations
    event_id              VARCHAR(64)  NOT NULL,           -- Kafka consumer deduplication key
    user_key              VARCHAR(100) NOT NULL DEFAULT 'guest',
    province_code         VARCHAR(10)  NOT NULL DEFAULT 'GZ',
    volunteer_unit_type   VARCHAR(40)  NOT NULL DEFAULT 'MAJOR_96',
    target_batch          VARCHAR(60)  NOT NULL DEFAULT '普通类本科批',
    total_score           INT          NOT NULL,
    province_rank         INT          NOT NULL,
    first_subject         VARCHAR(16)  NOT NULL,
    resubjects            JSONB        NOT NULL DEFAULT '[]'::jsonb,
    strategy_mode         VARCHAR(24)  NOT NULL DEFAULT 'balanced',
    fingerprint           VARCHAR(64)  NOT NULL DEFAULT '',
    item_count            INT          NOT NULL DEFAULT 0,
    list_hit_rate         NUMERIC(6,4) NOT NULL DEFAULT 0,
    safety_code_hash      VARCHAR(120) NOT NULL DEFAULT '',
    agreed_disclaimer     BOOLEAN      NOT NULL DEFAULT TRUE,
    disclaimer_version    VARCHAR(32)  NOT NULL DEFAULT '2026.1',
    plan_json             JSONB        NOT NULL DEFAULT '[]'::jsonb,
    manual_review_json    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    metrics_json          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    request_snapshot_json JSONB        NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (plan_id, created_at),
    UNIQUE (event_id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE IF NOT EXISTS biz_plan_history_2026_11 PARTITION OF biz_plan_history
    FOR VALUES FROM ('2026-11-01 00:00:00+08') TO ('2026-12-01 00:00:00+08');
CREATE TABLE IF NOT EXISTS biz_plan_history_2026_12 PARTITION OF biz_plan_history
    FOR VALUES FROM ('2026-12-01 00:00:00+08') TO ('2027-01-01 00:00:00+08');
CREATE TABLE IF NOT EXISTS biz_plan_history_2027_01 PARTITION OF biz_plan_history
    FOR VALUES FROM ('2027-01-01 00:00:00+08') TO ('2027-02-01 00:00:00+08');
CREATE TABLE IF NOT EXISTS biz_plan_history_default PARTITION OF biz_plan_history DEFAULT;

CREATE INDEX IF NOT EXISTS idx_biz_plan_user_time ON biz_plan_history (user_key, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_biz_plan_fp ON biz_plan_history (fingerprint);

-- 2. HITL Agent Plan Mutation Audit & One-Click Rollback Log
CREATE TABLE IF NOT EXISTS biz_plan_mutation_log (
    mutation_id     VARCHAR(32)  PRIMARY KEY,
    plan_id         VARCHAR(32)  NOT NULL,
    from_version    INT          NOT NULL,
    to_version      INT          NOT NULL,
    thread_id       VARCHAR(80)  NOT NULL DEFAULT '',
    summary         VARCHAR(500) NOT NULL DEFAULT '',
    hit_rate_before NUMERIC(6,4) NOT NULL DEFAULT 0,
    hit_rate_after  NUMERIC(6,4) NOT NULL DEFAULT 0,
    operations_json JSONB        NOT NULL DEFAULT '[]'::jsonb,
    before_snapshot JSONB        NOT NULL DEFAULT '[]'::jsonb,
    after_snapshot  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_plan_mutation_plan_ver ON biz_plan_mutation_log (plan_id, to_version DESC);

-- 3. AI Agent Compliance & Telemetry Audit Log (Monthly Partitioned, Kafka Async Sink)
CREATE TABLE IF NOT EXISTS ai_audit_log (
    audit_id            VARCHAR(32)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    event_id            VARCHAR(64)  NOT NULL,
    trace_id            VARCHAR(64)  NOT NULL DEFAULT '',
    thread_id           VARCHAR(80)  NOT NULL DEFAULT '',
    plan_id             VARCHAR(32)  NOT NULL DEFAULT '',
    intent              VARCHAR(40)  NOT NULL DEFAULT '',
    model_used          VARCHAR(80)  NOT NULL DEFAULT '',
    cache_hit           BOOLEAN      NOT NULL DEFAULT FALSE,
    latency_ms          INT          NOT NULL DEFAULT 0,
    prompt_tokens       INT          NOT NULL DEFAULT 0,
    completion_tokens   INT          NOT NULL DEFAULT 0,
    compliance_action   VARCHAR(24)  NOT NULL DEFAULT 'pass', -- pass | sanitize | refuse
    compliance_hits     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    tool_calls_json     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    question_preview    VARCHAR(500) NOT NULL DEFAULT '',
    answer_preview      VARCHAR(800) NOT NULL DEFAULT '',
    PRIMARY KEY (audit_id, created_at),
    UNIQUE (event_id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE IF NOT EXISTS ai_audit_log_2026_11 PARTITION OF ai_audit_log
    FOR VALUES FROM ('2026-11-01 00:00:00+08') TO ('2026-12-01 00:00:00+08');
CREATE TABLE IF NOT EXISTS ai_audit_log_2026_12 PARTITION OF ai_audit_log
    FOR VALUES FROM ('2026-12-01 00:00:00+08') TO ('2027-01-01 00:00:00+08');
CREATE TABLE IF NOT EXISTS ai_audit_log_default PARTITION OF ai_audit_log DEFAULT;

CREATE INDEX IF NOT EXISTS idx_ai_audit_plan_time ON ai_audit_log (plan_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_audit_thread ON ai_audit_log (thread_id, created_at DESC);
