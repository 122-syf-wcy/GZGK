-- GZLY 2026 staging -> formal 晋级模板
-- 默认不执行写入。必须 SET @confirmed = 1 后才允许 INSERT。
-- 只允许用户单独确认后执行；本模板不应自动执行。

SET @confirmed = 0;
SET @batch_id = '__SET_IMPORT_BATCH_ID__';
SET @formal_batch_id = CONCAT(@batch_id, '_formal');

-- 晋级前只读预检：招生计划 staging 分布
SELECT 'precheck.staging_admission_plan_distribution' AS report_name,
       batch_code, candidate_type, subject_type, COUNT(*) AS row_count, SUM(plan_count) AS plan_sum
FROM stg_gz_2026_admission_plan
WHERE import_batch_id = @batch_id
GROUP BY batch_code, candidate_type, subject_type
ORDER BY batch_code, candidate_type, subject_type;

-- 晋级前只读预检：目标正式表是否已有 2026 数据
SELECT 'precheck.formal_admission_plan_existing' AS report_name,
       year, province, batch_code, candidate_type, subject_type, COUNT(*) AS row_count, SUM(plan_count) AS plan_sum
FROM admission_plan
WHERE year = 2026 AND province = 'GZ'
GROUP BY year, province, batch_code, candidate_type, subject_type
ORDER BY batch_code, candidate_type, subject_type;

-- 招生计划晋级到 admission_plan
-- 注意：admission_plan 主表仍不直接承载 raw_text；本模板同步写 admission_plan_import_audit，
-- 回滚必须优先依赖 audit 映射，禁止无精确 WHERE 的 DELETE。
INSERT INTO admission_plan (
  year, province, batch_code, candidate_type, subject_type, selected_subject_requirement,
  school_code, school_name, major_code, major_name, major_category, plan_count,
  tuition, duration, campus, remarks, special_limit,
  is_public, is_private, is_chinese_foreign_coop,
  school_level, school_province, school_city
)
SELECT
  year, province, batch_code, candidate_type, subject_type, selected_subject_requirement,
  school_code, school_name, major_code, major_name, major_category, plan_count,
  tuition, duration, campus, remarks, special_limit,
  is_public, is_private, is_chinese_foreign_coop,
  school_level, school_province, school_city
FROM stg_gz_2026_admission_plan
WHERE @confirmed = 1
  AND import_batch_id = @batch_id
  AND year = 2026
  AND review_required = 0
  AND plan_count > 0
ON DUPLICATE KEY UPDATE
  selected_subject_requirement = VALUES(selected_subject_requirement),
  plan_count = VALUES(plan_count),
  tuition = VALUES(tuition),
  duration = VALUES(duration),
  campus = VALUES(campus),
  remarks = VALUES(remarks),
  special_limit = VALUES(special_limit),
  is_public = VALUES(is_public),
  is_private = VALUES(is_private),
  is_chinese_foreign_coop = VALUES(is_chinese_foreign_coop),
  school_level = VALUES(school_level),
  school_province = VALUES(school_province),
  school_city = VALUES(school_city),
  updated_at = CURRENT_TIMESTAMP;

-- 招生计划正式表导入审计映射：用于按 import_batch_id 追溯和回滚。
INSERT INTO admission_plan_import_audit (
  import_batch_id, formal_batch_id, year, province, batch_code, candidate_type, subject_type,
  school_code, school_name, major_code, major_name, plan_count,
  source_file, source_url, source_page_url, source_page, raw_text,
  promote_action, promoted_by
)
SELECT
  import_batch_id, @formal_batch_id, year, province, batch_code, candidate_type, subject_type,
  school_code, school_name, major_code, major_name, plan_count,
  source_file, source_url, source_page_url, source_page, raw_text,
  'insert_or_update', ''
FROM stg_gz_2026_admission_plan
WHERE @confirmed = 1
  AND import_batch_id = @batch_id
  AND year = 2026
  AND review_required = 0
  AND plan_count > 0
ON DUPLICATE KEY UPDATE
  formal_batch_id = VALUES(formal_batch_id),
  plan_count = VALUES(plan_count),
  source_file = VALUES(source_file),
  source_url = VALUES(source_url),
  source_page_url = VALUES(source_page_url),
  source_page = VALUES(source_page),
  raw_text = VALUES(raw_text),
  promoted_at = CURRENT_TIMESTAMP;

-- 一分一段晋级到 data_score_rank_gz
INSERT INTO data_score_rank_gz (
  year, province, subject_type, score, score_label,
  segment_count, cumulative_count, cumulative_rate, rank_low, rank_high,
  source_name, source_url, source_page_url, source_file, parse_method
)
SELECT
  year, province, subject_type, score, score_label,
  segment_count, cumulative_count, cumulative_rate, rank_low, rank_high,
  source_name, source_url, source_page_url, source_file, parse_method
FROM stg_gz_2026_score_segment
WHERE @confirmed = 1
  AND import_batch_id = @batch_id
  AND year = 2026
  AND review_required = 0
ON DUPLICATE KEY UPDATE
  score_label = VALUES(score_label),
  segment_count = VALUES(segment_count),
  cumulative_count = VALUES(cumulative_count),
  cumulative_rate = VALUES(cumulative_rate),
  rank_low = VALUES(rank_low),
  rank_high = VALUES(rank_high),
  source_name = VALUES(source_name),
  source_url = VALUES(source_url),
  source_page_url = VALUES(source_page_url),
  source_file = VALUES(source_file),
  parse_method = VALUES(parse_method),
  updated_at = CURRENT_TIMESTAMP;

-- 政策晋级到 policy_rule_config
INSERT INTO policy_rule_config (
  province, year, candidate_type, batch_code, batch_name, volunteer_mode,
  max_volunteer_count, major_per_school_count, has_adjustment,
  filing_principle, admission_order, policy_status,
  official_source_title, official_source_url, official_source_text, enabled
)
SELECT
  province, year, candidate_type, batch_code, batch_name, volunteer_mode,
  max_volunteer_count, major_per_school_count, has_adjustment,
  filing_principle, admission_order, 'confirmed',
  official_source_title, official_source_url, raw_text, 1
FROM stg_gz_2026_policy_rule
WHERE @confirmed = 1
  AND import_batch_id = @batch_id
  AND year = 2026
  AND review_required = 0
ON DUPLICATE KEY UPDATE
  batch_name = VALUES(batch_name),
  volunteer_mode = VALUES(volunteer_mode),
  max_volunteer_count = VALUES(max_volunteer_count),
  major_per_school_count = VALUES(major_per_school_count),
  has_adjustment = VALUES(has_adjustment),
  filing_principle = VALUES(filing_principle),
  admission_order = VALUES(admission_order),
  policy_status = VALUES(policy_status),
  official_source_title = VALUES(official_source_title),
  official_source_url = VALUES(official_source_url),
  official_source_text = VALUES(official_source_text),
  enabled = VALUES(enabled),
  updated_at = CURRENT_TIMESTAMP;

-- 选科要求晋级到 data_major_requirement_gz
INSERT INTO data_major_requirement_gz (
  year, school_id, university_name, major_id, major_name, subject_type,
  first_subject_requirement, resubject_requirement, requirement_text,
  source_name, source_url, source_file
)
SELECT
  year, school_code, school_name, major_code, major_name, subject_type,
  first_subject_requirement, resubject_requirement, requirement_text,
  source_name, source_url, source_file
FROM stg_gz_2026_major_requirement
WHERE @confirmed = 1
  AND import_batch_id = @batch_id
  AND year = 2026
  AND review_required = 0
ON DUPLICATE KEY UPDATE
  first_subject_requirement = VALUES(first_subject_requirement),
  resubject_requirement = VALUES(resubject_requirement),
  requirement_text = VALUES(requirement_text),
  source_name = VALUES(source_name),
  source_url = VALUES(source_url),
  source_file = VALUES(source_file),
  updated_at = CURRENT_TIMESTAMP;

-- 晋级后只读检查
SELECT 'postcheck.admission_plan_2026' AS report_name,
       batch_code, candidate_type, subject_type, COUNT(*) AS row_count, SUM(plan_count) AS plan_sum
FROM admission_plan
WHERE year = 2026 AND province = 'GZ'
GROUP BY batch_code, candidate_type, subject_type
ORDER BY batch_code, candidate_type, subject_type;

SELECT 'postcheck.score_rank_2026' AS report_name,
       subject_type, COUNT(*) AS row_count, MIN(score) AS min_score, MAX(score) AS max_score, MAX(cumulative_count) AS max_cumulative
FROM data_score_rank_gz
WHERE year = 2026
GROUP BY subject_type;

SELECT 'postcheck.policy_2026' AS report_name,
       candidate_type, batch_code, policy_status, enabled
FROM policy_rule_config
WHERE province = 'GZ' AND year = 2026
ORDER BY candidate_type, batch_code;

SELECT 'postcheck.major_requirement_2026' AS report_name,
       subject_type, COUNT(*) AS row_count, COUNT(DISTINCT school_id) AS school_count
FROM data_major_requirement_gz
WHERE year = 2026
GROUP BY subject_type;

SELECT 'postcheck.admission_plan_import_audit' AS report_name,
       import_batch_id, formal_batch_id, COUNT(*) AS row_count, SUM(plan_count) AS plan_sum
FROM admission_plan_import_audit
WHERE import_batch_id = @batch_id
GROUP BY import_batch_id, formal_batch_id;
