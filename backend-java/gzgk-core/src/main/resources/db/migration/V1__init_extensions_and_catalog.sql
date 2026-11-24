-- ============================================================================
-- V1: Extensions, University Catalog, Multi-Province Profiles & Policy Rules
-- Target: PostgreSQL 16 + pgvector + pg_trgm
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;

-- 1. National University Catalog (2,198+ institutions)
CREATE TABLE IF NOT EXISTS sys_university (
    id                 BIGSERIAL PRIMARY KEY,
    school_id          VARCHAR(32)  NOT NULL UNIQUE,
    name               VARCHAR(120) NOT NULL,
    province           VARCHAR(32)  NOT NULL DEFAULT '',
    city               VARCHAR(64)  NOT NULL DEFAULT '',
    level              VARCHAR(32)  NOT NULL DEFAULT '',
    type_name          VARCHAR(32)  NOT NULL DEFAULT '',
    tags               JSONB        NOT NULL DEFAULT '[]'::jsonb,
    is_985             BOOLEAN      NOT NULL DEFAULT FALSE,
    is_211             BOOLEAN      NOT NULL DEFAULT FALSE,
    is_dual_class      BOOLEAN      NOT NULL DEFAULT FALSE,
    nature_name        VARCHAR(32)  NOT NULL DEFAULT '',
    belong             VARCHAR(120) NOT NULL DEFAULT '',
    logo_url           VARCHAR(500) NOT NULL DEFAULT '',
    school_site        VARCHAR(300) NOT NULL DEFAULT '',
    phone              VARCHAR(120) NOT NULL DEFAULT '',
    email              VARCHAR(120) NOT NULL DEFAULT '',
    address            VARCHAR(300) NOT NULL DEFAULT '',
    summary            TEXT         NOT NULL DEFAULT '',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sys_university_province ON sys_university (province);
CREATE INDEX IF NOT EXISTS idx_sys_university_level ON sys_university (level);
CREATE INDEX IF NOT EXISTS idx_sys_university_name_trgm ON sys_university USING GIN (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_sys_university_tags_gin ON sys_university USING GIN (tags);

-- 2. Official Admission Links & Charter Provenance
CREATE TABLE IF NOT EXISTS uni_official_link (
    id                     BIGSERIAL PRIMARY KEY,
    school_id              VARCHAR(32)  NOT NULL UNIQUE,
    school_name            VARCHAR(120) NOT NULL DEFAULT '',
    school_site            VARCHAR(500) NOT NULL DEFAULT '',
    admission_site         VARCHAR(500) NOT NULL DEFAULT '',
    admission_brochure_url VARCHAR(500) NOT NULL DEFAULT '',
    major_catalog_url      VARCHAR(500) NOT NULL DEFAULT '',
    tuition_info_url       VARCHAR(500) NOT NULL DEFAULT '',
    tuition_remark         VARCHAR(500) NOT NULL DEFAULT '',
    capture_status         SMALLINT     NOT NULL DEFAULT 1,
    last_verified_at       TIMESTAMPTZ,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 3. Multi-Province Static Profile (8 provinces)
CREATE TABLE IF NOT EXISTS province_profile (
    province_code        VARCHAR(10)  PRIMARY KEY,
    province_name        VARCHAR(32)  NOT NULL,
    subject_mode         VARCHAR(16)  NOT NULL DEFAULT '3+1+2', -- 3+1+2 | 3+3
    new_gaokao_first_year SMALLINT    NOT NULL DEFAULT 2024,
    score_system         VARCHAR(24)  NOT NULL DEFAULT 'RAW_750', -- RAW_750 | STANDARD_900
    rank_tie_break_rule  VARCHAR(500) NOT NULL DEFAULT '',
    official_source_name VARCHAR(120) NOT NULL DEFAULT '',
    official_source_url  VARCHAR(500) NOT NULL DEFAULT '',
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 4. Batch-Level Policy Rule Config (with JSONB gradient presets & chance calibration overrides)
CREATE TABLE IF NOT EXISTS policy_rule_config (
    id                     BIGSERIAL PRIMARY KEY,
    province_code          VARCHAR(10)  NOT NULL REFERENCES province_profile(province_code),
    year                   SMALLINT     NOT NULL,
    candidate_type         VARCHAR(32)  NOT NULL DEFAULT '普通类',
    batch_code             VARCHAR(48)  NOT NULL DEFAULT 'NORMAL_UNDERGRADUATE',
    batch_name             VARCHAR(80)  NOT NULL,
    volunteer_mode         VARCHAR(64)  NOT NULL,
    volunteer_unit_type    VARCHAR(40)  NOT NULL DEFAULT 'MAJOR_96', -- MAJOR_96 | PROFESSIONAL_GROUP_45
    max_volunteer_count    INT          NOT NULL DEFAULT 96,
    major_per_group_count  INT          NOT NULL DEFAULT 0,
    has_adjustment         BOOLEAN      NOT NULL DEFAULT FALSE,
    data_year_from         SMALLINT     NOT NULL DEFAULT 2024,
    gradient_preset        JSONB,
    chance_params          JSONB,
    filing_principle       VARCHAR(240) NOT NULL DEFAULT '',
    policy_status          VARCHAR(32)  NOT NULL DEFAULT 'confirmed',
    official_source_title  VARCHAR(240) NOT NULL DEFAULT '',
    official_source_url    VARCHAR(500) NOT NULL DEFAULT '',
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (province_code, year, candidate_type, batch_code)
);

CREATE INDEX IF NOT EXISTS idx_policy_rule_lookup ON policy_rule_config (province_code, year, enabled);

-- 5. Regulatory Compliance Lexicon (Mirrored from contracts/compliance-terms.json)
CREATE TABLE IF NOT EXISTS compliance_sensitive_word (
    id          BIGSERIAL PRIMARY KEY,
    word        VARCHAR(120) NOT NULL UNIQUE,
    severity    VARCHAR(16)  NOT NULL DEFAULT 'hard', -- hard | soft
    replacement VARCHAR(120) NOT NULL DEFAULT '参考评估',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Seed 8-Province Profiles
INSERT INTO province_profile
    (province_code, province_name, subject_mode, new_gaokao_first_year, score_system, rank_tie_break_rule, official_source_name, official_source_url, enabled)
VALUES
    ('GZ', '贵州', '3+1+2', 2024, 'RAW_750', '语数之和→语数单科最高→外语→首选→再选最高→再选次高', '贵州省招生考试院', 'https://zsksy.guizhou.gov.cn/', TRUE),
    ('SC', '四川', '3+1+2', 2025, 'RAW_750', '语数之和→语数单科最高→外语→首选→再选最高→再选次高', '四川省教育考试院', 'https://www.sceea.cn/', TRUE),
    ('HB', '湖北', '3+1+2', 2021, 'RAW_750', '语数之和→语数单科最高→外语→首选→再选最高→再选次高', '湖北省教育考试院', 'http://www.hbea.edu.cn/', TRUE),
    ('AH', '安徽', '3+1+2', 2024, 'RAW_750', '语数之和→语数单科最高→外语→首选→再选最高→再选次高', '安徽省教育招生考试院', 'https://www.ahzsks.cn/', TRUE),
    ('GX', '广西', '3+1+2', 2024, 'RAW_750', '语数之和→语数单科最高→外语→首选→再选最高→再选次高', '广西壮族自治区招生考试院', 'https://www.gxeea.cn/', TRUE),
    ('HI', '海南', '3+3',   2020, 'STANDARD_900', '标准分900分制同分排序规则', '海南省考试局', 'http://ea.hainan.gov.cn/', TRUE),
    ('YN', '云南', '3+1+2', 2025, 'RAW_750', '语数之和→语数单科最高→外语→首选→再选最高→再选次高', '云南省招生考试院', 'https://www.ynzs.cn/', TRUE),
    ('HA', '河南', '3+1+2', 2025, 'RAW_750', '语数之和→语数单科最高→外语→首选→再选最高→再选次高', '河南省教育考试院', 'http://www.haeea.cn/', TRUE)
ON CONFLICT (province_code) DO UPDATE SET
    province_name = EXCLUDED.province_name,
    subject_mode  = EXCLUDED.subject_mode,
    updated_at    = NOW();

-- Seed Batch Policy Rules (2026 + 2025 core)
INSERT INTO policy_rule_config
    (province_code, year, candidate_type, batch_code, batch_name, volunteer_mode, volunteer_unit_type,
     max_volunteer_count, major_per_group_count, has_adjustment, data_year_from, gradient_preset, chance_params,
     filing_principle, policy_status, official_source_title, official_source_url, enabled)
VALUES
    ('GZ', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '普通类本科批', '专业(类)+院校平行志愿', 'MAJOR_96',
     96, 0, FALSE, 2024, '{"sprint":18,"steady":36,"safe":28,"pad":14}'::jsonb, '{"sigmaMin":0.06,"groupWithdrawPrior":0.0}'::jsonb,
     '分数优先、遵循志愿、一轮投档', 'confirmed', '贵州省普通高校招生工作规定', 'https://zsksy.guizhou.gov.cn/', TRUE),
    ('SC', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '普通本科批B段', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
     45, 6, TRUE, 2025, '{"sprint":10,"steady":18,"safe":12,"pad":5}'::jsonb, '{"sigmaMin":0.06,"groupWithdrawPrior":0.01}'::jsonb,
     '位次优先、遵循志愿、一轮投档；1:1投档', 'pending_confirm', '四川省普通高校招生实施规定', 'https://www.sceea.cn/', TRUE),
    ('HB', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '本科普通批', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
     45, 6, TRUE, 2021, '{"sprint":15,"steady":15,"safe":11,"pad":4}'::jsonb, '{"sigmaMin":0.06,"groupWithdrawPrior":0.03}'::jsonb,
     '分数优先、遵循志愿、一轮投档；投档比例≤105%', 'pending_confirm', '湖北省阳光招生政策问答', 'http://www.hbea.edu.cn/', TRUE),
    ('AH', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '普通本科批次', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
     45, 6, TRUE, 2024, '{"sprint":10,"steady":18,"safe":12,"pad":5}'::jsonb, '{"sigmaMin":0.06,"groupWithdrawPrior":0.03}'::jsonb,
     '分数优先、遵循志愿、一轮投档', 'pending_confirm', '安徽省普通高校招生实施办法', 'https://www.ahzsks.cn/', TRUE),
    ('GX', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '本科普通批', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
     40, 20, TRUE, 2024, '{"sprint":9,"steady":16,"safe":11,"pad":4}'::jsonb, '{"sigmaMin":0.06,"groupWithdrawPrior":0.01}'::jsonb,
     '分数优先、遵循志愿、一轮投档；1:1投档', 'pending_confirm', '广西普通高校招生录取工作方案', 'https://www.gxeea.cn/', TRUE),
    ('HI', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '本科普通批', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
     30, 6, TRUE, 2020, '{"sprint":10,"steady":10,"safe":7,"pad":3}'::jsonb, '{"sigmaMin":0.07,"groupWithdrawPrior":0.03}'::jsonb,
     '分数优先、遵循志愿、一次投档；标准分900分制', 'pending_confirm', '海南省本科批填报志愿公告', 'http://ea.hainan.gov.cn/', TRUE),
    ('YN', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '本科批', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
     40, 10, TRUE, 2025, '{"sprint":8,"steady":16,"safe":11,"pad":5}'::jsonb, '{"sigmaMin":0.06,"groupWithdrawPrior":0.03}'::jsonb,
     '分数优先、遵循志愿、一轮投档', 'pending_confirm', '云南省网上填报志愿考生须知', 'https://www.ynzs.cn/', TRUE),
    ('HA', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '普通本科批', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
     48, 6, TRUE, 2025, '{"sprint":10,"steady":18,"safe":14,"pad":6}'::jsonb, '{"sigmaMin":0.08,"groupWithdrawPrior":0.03}'::jsonb,
     '分数优先、遵循志愿、一轮投档（同分密度高需保守）', 'pending_confirm', '河南省普通高校招生工作规定', 'http://www.haeea.cn/', TRUE)
ON CONFLICT (province_code, year, candidate_type, batch_code) DO NOTHING;
