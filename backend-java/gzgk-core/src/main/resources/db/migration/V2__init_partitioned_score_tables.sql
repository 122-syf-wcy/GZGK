-- ============================================================================
-- V2: Partitioned Admission Score Lines, Score-Rank Segments & Requirements
-- Demonstrates PostgreSQL 16 Declarative LIST Partitioning with Partition Pruning
-- ============================================================================

-- 1. Major Admission Score Lines — Declarative LIST Partitioning by province_code
-- Smoothly scales from 160k (Guizhou) to tens of millions of nationwide records.
CREATE TABLE IF NOT EXISTS data_major_score_line (
    id                    BIGSERIAL,
    province_code         VARCHAR(10)  NOT NULL,
    school_id             VARCHAR(32)  NOT NULL,
    university_name       VARCHAR(120) NOT NULL DEFAULT '',
    major_code            VARCHAR(40)  NOT NULL DEFAULT '',
    major_name            VARCHAR(220) NOT NULL,
    year                  SMALLINT     NOT NULL,
    subject_type          VARCHAR(20)  NOT NULL, -- 物理类 / 历史类 / 综合 / 理科 / 文科
    batch                 VARCHAR(60)  NOT NULL DEFAULT '普通本科批',
    resubject_requirement VARCHAR(100) NOT NULL DEFAULT '',
    min_score             INT,
    max_score             INT,
    avg_score             INT,
    min_rank              INT,
    plan_count            INT,
    tuition               VARCHAR(60)  NOT NULL DEFAULT '',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (province_code, id)
) PARTITION BY LIST (province_code);

CREATE TABLE IF NOT EXISTS data_major_score_line_gz PARTITION OF data_major_score_line FOR VALUES IN ('GZ');
CREATE TABLE IF NOT EXISTS data_major_score_line_sc PARTITION OF data_major_score_line FOR VALUES IN ('SC');
CREATE TABLE IF NOT EXISTS data_major_score_line_hb PARTITION OF data_major_score_line FOR VALUES IN ('HB');
CREATE TABLE IF NOT EXISTS data_major_score_line_ah PARTITION OF data_major_score_line FOR VALUES IN ('AH');
CREATE TABLE IF NOT EXISTS data_major_score_line_gx PARTITION OF data_major_score_line FOR VALUES IN ('GX');
CREATE TABLE IF NOT EXISTS data_major_score_line_hi PARTITION OF data_major_score_line FOR VALUES IN ('HI');
CREATE TABLE IF NOT EXISTS data_major_score_line_yn PARTITION OF data_major_score_line FOR VALUES IN ('YN');
CREATE TABLE IF NOT EXISTS data_major_score_line_ha PARTITION OF data_major_score_line FOR VALUES IN ('HA');
CREATE TABLE IF NOT EXISTS data_major_score_line_default PARTITION OF data_major_score_line DEFAULT;

-- Partitioned Indexes (automatically propagated to all child partitions)
CREATE INDEX IF NOT EXISTS idx_major_score_recall
    ON data_major_score_line (province_code, year, subject_type, min_rank)
    WHERE min_rank IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_major_score_school
    ON data_major_score_line (province_code, school_id, year DESC);
CREATE INDEX IF NOT EXISTS idx_major_score_major_trgm
    ON data_major_score_line USING GIN (major_name gin_trgm_ops);

-- 2. Unified Multi-Province Official One-Score-One-Rank Segment Table
CREATE TABLE IF NOT EXISTS data_score_rank (
    id               BIGSERIAL PRIMARY KEY,
    province_code    VARCHAR(10)   NOT NULL,
    province_name    VARCHAR(32)   NOT NULL DEFAULT '',
    year             SMALLINT      NOT NULL,
    subject_type     VARCHAR(20)   NOT NULL,
    score            SMALLINT      NOT NULL,
    score_label      VARCHAR(32)   NOT NULL DEFAULT '',
    segment_count    INT           NOT NULL DEFAULT 0,
    cumulative_count INT           NOT NULL DEFAULT 0,
    cumulative_rate  NUMERIC(7, 3),
    rank_low         INT           NOT NULL,
    rank_high        INT           NOT NULL,
    source_name      VARCHAR(120)  NOT NULL DEFAULT '',
    source_url       VARCHAR(500)  NOT NULL DEFAULT '',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (province_code, year, subject_type, score)
);

CREATE INDEX IF NOT EXISTS idx_score_rank_lookup
    ON data_score_rank (province_code, year, subject_type, rank_low, rank_high);

-- 3. Unified Multi-Province Official Subject Requirement Catalog
CREATE TABLE IF NOT EXISTS data_major_requirement (
    id                        BIGSERIAL PRIMARY KEY,
    province_code             VARCHAR(10)  NOT NULL,
    year                      SMALLINT     NOT NULL,
    school_id                 VARCHAR(32)  NOT NULL,
    university_name           VARCHAR(120) NOT NULL DEFAULT '',
    group_code                VARCHAR(48)  NOT NULL DEFAULT '',
    major_id                  VARCHAR(48)  NOT NULL DEFAULT '',
    major_name                VARCHAR(220) NOT NULL,
    subject_type              VARCHAR(20)  NOT NULL,
    first_subject_requirement VARCHAR(60)  NOT NULL DEFAULT '',
    resubject_requirement     VARCHAR(100) NOT NULL DEFAULT '',
    requirement_text          VARCHAR(500) NOT NULL DEFAULT '',
    source_name               VARCHAR(120) NOT NULL DEFAULT '',
    source_url                VARCHAR(500) NOT NULL DEFAULT '',
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (province_code, year, school_id, group_code, major_name, subject_type)
);

CREATE INDEX IF NOT EXISTS idx_major_req_lookup
    ON data_major_requirement (province_code, year, school_id, subject_type);

-- 4. Multi-Province Professional Group Admission Lines (SC / HB / AH / GX / HI / YN / HA)
CREATE TABLE IF NOT EXISTS data_admission_group_line (
    id                        BIGSERIAL PRIMARY KEY,
    province_code             VARCHAR(10)  NOT NULL,
    year                      SMALLINT     NOT NULL,
    school_id                 VARCHAR(32)  NOT NULL,
    university_name           VARCHAR(120) NOT NULL DEFAULT '',
    group_code                VARCHAR(48)  NOT NULL,
    group_name                VARCHAR(200) NOT NULL DEFAULT '',
    subject_type              VARCHAR(20)  NOT NULL,
    resubject_requirement     VARCHAR(100) NOT NULL DEFAULT '',
    min_score                 SMALLINT,
    min_rank                  INT,
    plan_count                INT,
    batch                     VARCHAR(60)  NOT NULL DEFAULT '普通本科批',
    source_url                VARCHAR(500) NOT NULL DEFAULT '',
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (province_code, year, school_id, group_code, subject_type, batch)
);

CREATE INDEX IF NOT EXISTS idx_group_line_rank
    ON data_admission_group_line (province_code, year, subject_type, min_rank);
