-- GZLY 2026 官方数据 staging 质检模板
-- 只读 SELECT，不写正式表。
-- 使用方式：SET @batch_id='gz_2026_xxx_v1_YYYYMMDD'; 然后执行对应数据类型检查。

SET @batch_id = COALESCE(@batch_id, '__SET_IMPORT_BATCH_ID__');

-- 通用：batch 是否存在
SELECT 'common.batch_rows' AS check_name, @batch_id AS import_batch_id, 'stg_gz_2026_score_segment' AS table_name, COUNT(*) AS rows_count
FROM stg_gz_2026_score_segment WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'common.batch_rows', @batch_id, 'stg_gz_2026_admission_plan', COUNT(*)
FROM stg_gz_2026_admission_plan WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'common.batch_rows', @batch_id, 'stg_gz_2026_policy_rule', COUNT(*)
FROM stg_gz_2026_policy_rule WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'common.batch_rows', @batch_id, 'stg_gz_2026_major_requirement', COUNT(*)
FROM stg_gz_2026_major_requirement WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'common.batch_rows', @batch_id, 'stg_gz_2026_major_meta', COUNT(*)
FROM stg_gz_2026_major_meta WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'common.batch_rows', @batch_id, 'stg_gz_2026_batch_line', COUNT(*)
FROM stg_gz_2026_batch_line WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'common.batch_rows', @batch_id, 'stg_gz_2026_art_sports_rule', COUNT(*)
FROM stg_gz_2026_art_sports_rule WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'common.batch_rows', @batch_id, 'stg_gz_2026_special_plan_rule', COUNT(*)
FROM stg_gz_2026_special_plan_rule WHERE import_batch_id = @batch_id;

-- 一分一段：年份、科类、来源、单调性
SELECT 'score_segment.year_not_2026' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_score_segment
WHERE import_batch_id = @batch_id AND year <> 2026;

SELECT 'score_segment.bad_subject' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_score_segment
WHERE import_batch_id = @batch_id AND subject_type NOT IN ('物理类', '历史类');

SELECT 'score_segment.empty_source' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_score_segment
WHERE import_batch_id = @batch_id
  AND (source_file = '' OR source_url = '' OR raw_text IS NULL OR raw_text = '');

SELECT 'score_segment.bad_count_or_rank' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_score_segment
WHERE import_batch_id = @batch_id
  AND (segment_count < 0 OR cumulative_count <= 0 OR rank_low <= 0 OR rank_high <= 0 OR rank_low > rank_high);

SELECT 'score_segment.duplicate_natural_key' AS check_name, COUNT(*) AS duplicate_groups
FROM (
  SELECT year, subject_type, score, COUNT(*) AS c
  FROM stg_gz_2026_score_segment
  WHERE import_batch_id = @batch_id
  GROUP BY year, subject_type, score
  HAVING c > 1
) t;

SELECT 'score_segment.distribution' AS report_name, subject_type, COUNT(*) AS row_count, MIN(score) AS min_score, MAX(score) AS max_score, MAX(cumulative_count) AS max_cumulative
FROM stg_gz_2026_score_segment
WHERE import_batch_id = @batch_id
GROUP BY subject_type
ORDER BY subject_type;

-- 招生计划：自然键、计划数、批次串扰、来源追溯
SELECT 'admission_plan.year_not_2026' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_admission_plan
WHERE import_batch_id = @batch_id AND year <> 2026;

SELECT 'admission_plan.plan_count_bad' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_admission_plan
WHERE import_batch_id = @batch_id AND (plan_count <= 0 OR plan_count > 300);

SELECT 'admission_plan.empty_source' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_admission_plan
WHERE import_batch_id = @batch_id
  AND (source_file = '' OR source_url = '' OR raw_text IS NULL OR raw_text = '');

SELECT 'admission_plan.duplicate_natural_key' AS check_name, COUNT(*) AS duplicate_groups
FROM (
  SELECT year, batch_code, candidate_type, subject_type, school_code, major_code, major_name, COUNT(*) AS c
  FROM stg_gz_2026_admission_plan
  WHERE import_batch_id = @batch_id
  GROUP BY year, batch_code, candidate_type, subject_type, school_code, major_code, major_name
  HAVING c > 1
) t;

SELECT 'admission_plan.normal_semantic_mixed' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_admission_plan
WHERE import_batch_id = @batch_id
  AND batch_code IN ('NORMAL_UNDERGRADUATE', 'NORMAL_SPECIALTY')
  AND CONCAT_WS(' ', major_name, remarks, special_limit, raw_text) REGEXP '提前|专项|艺术|体育|民族班|预科|免费医学|公费师范|优师|军检|政审|面试|航海|招飞|飞行技术|定向|订单培养';

SELECT 'admission_plan.batch_distribution' AS report_name, batch_code, candidate_type, subject_type, COUNT(*) AS row_count, SUM(plan_count) AS plan_sum, COUNT(DISTINCT school_code) AS school_count
FROM stg_gz_2026_admission_plan
WHERE import_batch_id = @batch_id
GROUP BY batch_code, candidate_type, subject_type
ORDER BY batch_code, candidate_type, subject_type;

-- 政策：确认状态和来源
SELECT 'policy_rule.empty_source' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_policy_rule
WHERE import_batch_id = @batch_id
  AND (official_source_url = '' OR raw_text IS NULL OR raw_text = '');

SELECT 'policy_rule.pending_review' AS check_name, COUNT(*) AS review_rows
FROM stg_gz_2026_policy_rule
WHERE import_batch_id = @batch_id AND review_required = 1;

SELECT 'policy_rule.distribution' AS report_name, candidate_type, batch_code, batch_name, volunteer_mode, max_volunteer_count, policy_status
FROM stg_gz_2026_policy_rule
WHERE import_batch_id = @batch_id
ORDER BY candidate_type, batch_code;

-- 选科要求
SELECT 'major_requirement.empty_source' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_major_requirement
WHERE import_batch_id = @batch_id
  AND (source_file = '' OR source_url = '' OR raw_text IS NULL OR raw_text = '');

SELECT 'major_requirement.bad_subject' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_major_requirement
WHERE import_batch_id = @batch_id AND subject_type NOT IN ('物理类', '历史类');

SELECT 'major_requirement.empty_requirement' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_major_requirement
WHERE import_batch_id = @batch_id
  AND first_subject_requirement = '' AND resubject_requirement = '' AND requirement_text = '';

SELECT 'major_requirement.duplicate_natural_key' AS check_name, COUNT(*) AS duplicate_groups
FROM (
  SELECT year, school_code, major_code, major_name, subject_type, COUNT(*) AS c
  FROM stg_gz_2026_major_requirement
  WHERE import_batch_id = @batch_id
  GROUP BY year, school_code, major_code, major_name, subject_type
  HAVING c > 1
) t;

-- 专业备注/限制
SELECT 'major_meta.empty_source' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_major_meta
WHERE import_batch_id = @batch_id
  AND (source_file = '' OR source_url = '' OR raw_text IS NULL OR raw_text = '');

SELECT 'major_meta.limit_distribution' AS report_name,
       SUM(physical_limit <> '') AS physical_limit_rows,
       SUM(single_subject_limit <> '') AS single_subject_limit_rows,
       SUM(language_limit <> '') AS language_limit_rows,
       SUM(gender_limit <> '') AS gender_limit_rows,
       SUM(COALESCE(special_limit, '') <> '') AS special_limit_rows
FROM stg_gz_2026_major_meta
WHERE import_batch_id = @batch_id;

-- 批次线：年份、批次、分数和来源
SELECT 'batch_line.year_not_2026' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_batch_line
WHERE import_batch_id = @batch_id AND year <> 2026;

SELECT 'batch_line.empty_source' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_batch_line
WHERE import_batch_id = @batch_id
  AND (source_file = '' OR source_url = '' OR raw_text IS NULL OR raw_text = '');

SELECT 'batch_line.bad_score' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_batch_line
WHERE import_batch_id = @batch_id
  AND (control_score <= 0 OR control_score > 750);

SELECT 'batch_line.duplicate_natural_key' AS check_name, COUNT(*) AS duplicate_groups
FROM (
  SELECT year, candidate_type, subject_type, batch_code, COUNT(*) AS c
  FROM stg_gz_2026_batch_line
  WHERE import_batch_id = @batch_id
  GROUP BY year, candidate_type, subject_type, batch_code
  HAVING c > 1
) t;

SELECT 'batch_line.distribution' AS report_name, candidate_type, subject_type, batch_code, batch_name, control_score
FROM stg_gz_2026_batch_line
WHERE import_batch_id = @batch_id
ORDER BY candidate_type, subject_type, batch_code;

-- 艺术/体育综合分规则：来源、公式、结构化字段
SELECT 'art_sports_rule.year_not_2026' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_art_sports_rule
WHERE import_batch_id = @batch_id AND year <> 2026;

SELECT 'art_sports_rule.empty_source' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_art_sports_rule
WHERE import_batch_id = @batch_id
  AND (source_file = '' OR source_url = '' OR raw_text IS NULL OR raw_text = '');

SELECT 'art_sports_rule.bad_category' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_art_sports_rule
WHERE import_batch_id = @batch_id
  AND category NOT IN ('艺术类', '体育类', 'ART', 'SPORTS', 'art', 'sports');

SELECT 'art_sports_rule.empty_formula' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_art_sports_rule
WHERE import_batch_id = @batch_id
  AND (formula_text IS NULL OR formula_text = '');

SELECT 'art_sports_rule.empty_components' AS check_name, COUNT(*) AS review_rows
FROM stg_gz_2026_art_sports_rule
WHERE import_batch_id = @batch_id
  AND score_components_json IS NULL;

SELECT 'art_sports_rule.duplicate_natural_key' AS check_name, COUNT(*) AS duplicate_groups
FROM (
  SELECT year, category, batch_code, COUNT(*) AS c
  FROM stg_gz_2026_art_sports_rule
  WHERE import_batch_id = @batch_id
  GROUP BY year, category, batch_code
  HAVING c > 1
) t;

SELECT 'art_sports_rule.distribution' AS report_name, category, batch_code, LEFT(formula_text, 120) AS formula_preview, review_required
FROM stg_gz_2026_art_sports_rule
WHERE import_batch_id = @batch_id
ORDER BY category, batch_code;

-- 特殊计划资格规则：来源、资格文本、结构化字段
SELECT 'special_plan_rule.year_not_2026' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_special_plan_rule
WHERE import_batch_id = @batch_id AND year <> 2026;

SELECT 'special_plan_rule.empty_source' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_special_plan_rule
WHERE import_batch_id = @batch_id
  AND (source_file = '' OR source_url = '' OR raw_text IS NULL OR raw_text = '');

SELECT 'special_plan_rule.bad_plan_type' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_special_plan_rule
WHERE import_batch_id = @batch_id
  AND plan_type NOT IN ('国家专项', '地方专项', '高校专项', '免费医学', '优师专项', '定向招生', '民族班', '预科班',
                        'NATIONAL_SPECIAL', 'LOCAL_SPECIAL', 'UNIVERSITY_SPECIAL', 'FREE_MEDICAL',
                        'TEACHER_EXCELLENCE', 'ORIENTED', 'ETHNIC_CLASS', 'PREPARATORY');

SELECT 'special_plan_rule.empty_eligibility' AS check_name, COUNT(*) AS failed_rows
FROM stg_gz_2026_special_plan_rule
WHERE import_batch_id = @batch_id
  AND (eligibility_text IS NULL OR eligibility_text = '');

SELECT 'special_plan_rule.empty_fields' AS check_name, COUNT(*) AS review_rows
FROM stg_gz_2026_special_plan_rule
WHERE import_batch_id = @batch_id
  AND eligibility_fields_json IS NULL;

SELECT 'special_plan_rule.duplicate_natural_key' AS check_name, COUNT(*) AS duplicate_groups
FROM (
  SELECT year, plan_type, batch_code, COUNT(*) AS c
  FROM stg_gz_2026_special_plan_rule
  WHERE import_batch_id = @batch_id
  GROUP BY year, plan_type, batch_code
  HAVING c > 1
) t;

SELECT 'special_plan_rule.distribution' AS report_name, plan_type, batch_code, LEFT(eligibility_text, 120) AS eligibility_preview, review_required
FROM stg_gz_2026_special_plan_rule
WHERE import_batch_id = @batch_id
ORDER BY plan_type, batch_code;
