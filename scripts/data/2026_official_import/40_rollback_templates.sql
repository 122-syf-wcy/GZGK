-- GZLY 2026 rollback 模板
-- 默认不执行写入。必须 SET @confirmed = 1 后才允许 DELETE/UPDATE。
-- 默认保留 staging evidence；正式表回滚必须依赖 import_batch_id 或可证明的自然键映射。

SET @confirmed = 0;
SET @cleanup_staging = 0;
SET @batch_id = '__SET_IMPORT_BATCH_ID__';

-- staging 回滚前统计
SELECT 'pre_rollback.stg_score_segment' AS report_name, COUNT(*) AS rows_count FROM stg_gz_2026_score_segment WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'pre_rollback.stg_admission_plan', COUNT(*) FROM stg_gz_2026_admission_plan WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'pre_rollback.stg_policy_rule', COUNT(*) FROM stg_gz_2026_policy_rule WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'pre_rollback.stg_major_requirement', COUNT(*) FROM stg_gz_2026_major_requirement WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'pre_rollback.stg_major_meta', COUNT(*) FROM stg_gz_2026_major_meta WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'pre_rollback.stg_batch_line', COUNT(*) FROM stg_gz_2026_batch_line WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'pre_rollback.stg_art_sports_rule', COUNT(*) FROM stg_gz_2026_art_sports_rule WHERE import_batch_id = @batch_id
UNION ALL
SELECT 'pre_rollback.stg_special_plan_rule', COUNT(*) FROM stg_gz_2026_special_plan_rule WHERE import_batch_id = @batch_id;

-- 正式表回滚说明
-- admission_plan 主表无 import_batch_id/source 字段，必须优先使用 admission_plan_import_audit 按批次回滚。
-- 如 audit 缺失，则不能自动回滚 admission_plan；只能由人工复核自然键清单后另行确认。
-- 禁止执行无精确 WHERE 的 DELETE。

SELECT 'pre_rollback.plan_audit_rows' AS report_name, COUNT(*) AS rows_count
FROM admission_plan_import_audit
WHERE import_batch_id = @batch_id;

-- 基于 audit 映射删除 admission_plan 中本批导入项。
DELETE ap
FROM admission_plan ap
JOIN admission_plan_import_audit audit
  ON audit.import_batch_id = @batch_id
 AND ap.year = audit.year
 AND ap.province = audit.province
 AND ap.batch_code = audit.batch_code
 AND ap.candidate_type = audit.candidate_type
 AND ap.subject_type = audit.subject_type
 AND ap.school_code = audit.school_code
 AND ap.major_code = audit.major_code
 AND ap.major_name = audit.major_name
WHERE @confirmed = 1
  AND ap.year = 2026
  AND ap.province = 'GZ';

UPDATE admission_plan_import_audit
SET promote_action = CASE WHEN @confirmed = 1 THEN 'rolled_back' ELSE promote_action END
WHERE import_batch_id = @batch_id;

-- data_score_rank_gz 可按 year+subject+score 精确回滚，但会删除所有匹配 2026 官方一分一段。
-- 仅当确认该批就是当前 2026 正式数据时才可启用。
DELETE dsr
FROM data_score_rank_gz dsr
JOIN stg_gz_2026_score_segment stg
  ON stg.import_batch_id = @batch_id
 AND dsr.year = stg.year
 AND dsr.subject_type = stg.subject_type
 AND dsr.score = stg.score
WHERE @confirmed = 1
  AND dsr.year = 2026;

-- policy_rule_config 回滚：建议不要删除政策，改为 pending_confirm/disabled，避免前端/后端缺配置。
UPDATE policy_rule_config pr
JOIN stg_gz_2026_policy_rule stg
  ON stg.import_batch_id = @batch_id
 AND pr.province = stg.province
 AND pr.year = stg.year
 AND pr.candidate_type = stg.candidate_type
 AND pr.batch_code = stg.batch_code
SET pr.policy_status = CASE WHEN @confirmed = 1 THEN 'pending_confirm' ELSE pr.policy_status END,
    pr.enabled = CASE WHEN @confirmed = 1 THEN 0 ELSE pr.enabled END,
    pr.updated_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE pr.updated_at END
WHERE pr.year = 2026 AND pr.province = 'GZ';

-- data_major_requirement_gz 回滚：基于 staging 自然键。
DELETE req
FROM data_major_requirement_gz req
JOIN stg_gz_2026_major_requirement stg
  ON stg.import_batch_id = @batch_id
 AND req.year = stg.year
 AND req.school_id = stg.school_code
 AND req.major_name = stg.major_name
 AND req.subject_type = stg.subject_type
WHERE @confirmed = 1
  AND req.year = 2026;

-- readiness 回滚到 PRE_OFFICIAL_DATA
UPDATE data_year_readiness
SET policy_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE policy_ready END,
    score_segment_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE score_segment_ready END,
    admission_plan_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE admission_plan_ready END,
    major_requirement_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE major_requirement_ready END,
    major_meta_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE major_meta_ready END,
    ml_training_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE ml_training_ready END,
    recommendation_phase = CASE WHEN @confirmed = 1 THEN 'PRE_OFFICIAL_DATA' ELSE recommendation_phase END,
    latest_import_batch_id = CASE WHEN @confirmed = 1 THEN @batch_id ELSE latest_import_batch_id END,
    remarks = CASE WHEN @confirmed = 1 THEN '按回滚模板撤回 2026 官方数据导入状态，恢复 PRE_OFFICIAL_DATA。' ELSE remarks END,
    last_checked_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE last_checked_at END,
    updated_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE updated_at END
WHERE province_code = 'GZ' AND year = 2026;

-- 回滚后核验
SELECT 'post_rollback.readiness' AS report_name,
       province_code, year, policy_ready, score_segment_ready, admission_plan_ready,
       major_requirement_ready, major_meta_ready, ml_training_ready, recommendation_phase
FROM data_year_readiness
WHERE province_code = 'GZ' AND year = 2026;

SELECT 'post_rollback.admission_plan_2026' AS report_name, batch_code, candidate_type, subject_type, COUNT(*) AS row_count
FROM admission_plan
WHERE year = 2026 AND province = 'GZ'
GROUP BY batch_code, candidate_type, subject_type;

SELECT 'post_rollback.plan_audit_rows' AS report_name, promote_action, COUNT(*) AS rows_count
FROM admission_plan_import_audit
WHERE import_batch_id = @batch_id
GROUP BY promote_action;

-- staging 清理必须最后执行。
-- 默认 @cleanup_staging = 0，保留 evidence 便于复核；只有归档完成后才允许清理 staging。
DELETE FROM stg_gz_2026_score_segment WHERE @confirmed = 1 AND @cleanup_staging = 1 AND import_batch_id = @batch_id;
DELETE FROM stg_gz_2026_admission_plan WHERE @confirmed = 1 AND @cleanup_staging = 1 AND import_batch_id = @batch_id;
DELETE FROM stg_gz_2026_policy_rule WHERE @confirmed = 1 AND @cleanup_staging = 1 AND import_batch_id = @batch_id;
DELETE FROM stg_gz_2026_major_requirement WHERE @confirmed = 1 AND @cleanup_staging = 1 AND import_batch_id = @batch_id;
DELETE FROM stg_gz_2026_major_meta WHERE @confirmed = 1 AND @cleanup_staging = 1 AND import_batch_id = @batch_id;
DELETE FROM stg_gz_2026_batch_line WHERE @confirmed = 1 AND @cleanup_staging = 1 AND import_batch_id = @batch_id;
DELETE FROM stg_gz_2026_art_sports_rule WHERE @confirmed = 1 AND @cleanup_staging = 1 AND import_batch_id = @batch_id;
DELETE FROM stg_gz_2026_special_plan_rule WHERE @confirmed = 1 AND @cleanup_staging = 1 AND import_batch_id = @batch_id;
