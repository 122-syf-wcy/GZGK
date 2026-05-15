-- GZLY 2026 data_year_readiness 状态切换模板
-- 默认不执行写入。必须显式 SET @confirmed = 1 且人工确认后才会 UPDATE。
-- 不导入数据、不开启 FULL_RECOMMEND、不重启服务。

SET @confirmed = 0;
SET @province_code = 'GZ';
SET @target_year = 2026;
SET @batch_id = '__SET_IMPORT_BATCH_ID__';

-- 只读当前状态
SELECT 'current_readiness' AS report_name,
       province_code, year, policy_ready, score_segment_ready, admission_plan_ready,
       major_requirement_ready, major_meta_ready, ml_training_ready, historical_training_ready,
       recommendation_phase, latest_import_batch_id, remarks, last_checked_at, updated_at
FROM data_year_readiness
WHERE province_code = @province_code AND year = @target_year;

-- PRE_OFFICIAL_DATA -> OFFICIAL_DATA_PARTIAL
-- 条件：至少一类 2026 官方数据进入 staging 并通过来源追溯基础检查。
UPDATE data_year_readiness
SET recommendation_phase = CASE WHEN @confirmed = 1 THEN 'OFFICIAL_DATA_PARTIAL' ELSE recommendation_phase END,
    latest_import_batch_id = CASE WHEN @confirmed = 1 THEN @batch_id ELSE latest_import_batch_id END,
    remarks = CASE WHEN @confirmed = 1 THEN '部分 2026 官方数据已进入 staging 或完成部分导入；仍禁止 FULL_RECOMMEND。' ELSE remarks END,
    last_checked_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE last_checked_at END,
    updated_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE updated_at END
WHERE province_code = @province_code
  AND year = @target_year
  AND recommendation_phase = 'PRE_OFFICIAL_DATA';

-- OFFICIAL_DATA_PARTIAL -> OFFICIAL_DATA_IMPORTED
-- 条件：政策、一分一段、招生计划、选科要求、专业备注/限制关键质检通过。
-- 注意：本阶段只允许普通本科/专科进入 TRIAL_RECOMMEND 或更保守状态；不得因本 SQL 直接打开 FULL_RECOMMEND。
UPDATE data_year_readiness
SET policy_ready = CASE WHEN @confirmed = 1 THEN 1 ELSE policy_ready END,
    score_segment_ready = CASE WHEN @confirmed = 1 THEN 1 ELSE score_segment_ready END,
    admission_plan_ready = CASE WHEN @confirmed = 1 THEN 1 ELSE admission_plan_ready END,
    major_requirement_ready = CASE WHEN @confirmed = 1 THEN 1 ELSE major_requirement_ready END,
    major_meta_ready = CASE WHEN @confirmed = 1 THEN 1 ELSE major_meta_ready END,
    ml_training_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE ml_training_ready END,
    recommendation_phase = CASE WHEN @confirmed = 1 THEN 'OFFICIAL_DATA_IMPORTED' ELSE recommendation_phase END,
    latest_import_batch_id = CASE WHEN @confirmed = 1 THEN @batch_id ELSE latest_import_batch_id END,
    remarks = CASE WHEN @confirmed = 1 THEN '2026 关键官方数据已导入并通过质检；待模型重训和上线 smoke。' ELSE remarks END,
    last_checked_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE last_checked_at END,
    updated_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE updated_at END
WHERE province_code = @province_code
  AND year = @target_year
  AND recommendation_phase IN ('PRE_OFFICIAL_DATA', 'OFFICIAL_DATA_PARTIAL');

-- OFFICIAL_DATA_IMPORTED -> MODEL_RETRAINED
-- 条件：2024/2025/2026 数据参与训练，离线回测不退化，模型 draft 人工确认并激活。
UPDATE data_year_readiness
SET ml_training_ready = CASE WHEN @confirmed = 1 THEN 1 ELSE ml_training_ready END,
    recommendation_phase = CASE WHEN @confirmed = 1 THEN 'MODEL_RETRAINED' ELSE recommendation_phase END,
    latest_import_batch_id = CASE WHEN @confirmed = 1 THEN @batch_id ELSE latest_import_batch_id END,
    remarks = CASE WHEN @confirmed = 1 THEN '2026 数据已参与模型重训，active 模型通过离线回测和 smoke。' ELSE remarks END,
    last_checked_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE last_checked_at END,
    updated_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE updated_at END
WHERE province_code = @province_code
  AND year = @target_year
  AND recommendation_phase = 'OFFICIAL_DATA_IMPORTED'
  AND policy_ready = 1
  AND score_segment_ready = 1
  AND admission_plan_ready = 1
  AND major_requirement_ready = 1
  AND major_meta_ready = 1;

-- 回退到 OFFICIAL_DATA_IMPORTED：模型撤回
UPDATE data_year_readiness
SET ml_training_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE ml_training_ready END,
    recommendation_phase = CASE WHEN @confirmed = 1 THEN 'OFFICIAL_DATA_IMPORTED' ELSE recommendation_phase END,
    remarks = CASE WHEN @confirmed = 1 THEN '模型已回退；2026 官方数据仍保留，正式 FULL_RECOMMEND 暂停。' ELSE remarks END,
    last_checked_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE last_checked_at END,
    updated_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE updated_at END
WHERE province_code = @province_code
  AND year = @target_year
  AND recommendation_phase = 'MODEL_RETRAINED';

-- 回退到 OFFICIAL_DATA_PARTIAL：关键数据发现问题
UPDATE data_year_readiness
SET admission_plan_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE admission_plan_ready END,
    major_requirement_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE major_requirement_ready END,
    major_meta_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE major_meta_ready END,
    ml_training_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE ml_training_ready END,
    recommendation_phase = CASE WHEN @confirmed = 1 THEN 'OFFICIAL_DATA_PARTIAL' ELSE recommendation_phase END,
    remarks = CASE WHEN @confirmed = 1 THEN '2026 部分关键数据发现问题，已回退到部分导入阶段；禁止 FULL_RECOMMEND。' ELSE remarks END,
    last_checked_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE last_checked_at END,
    updated_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE updated_at END
WHERE province_code = @province_code
  AND year = @target_year
  AND recommendation_phase IN ('OFFICIAL_DATA_IMPORTED', 'MODEL_RETRAINED');

-- 回退到 PRE_OFFICIAL_DATA：官方数据撤回或整体不可用
UPDATE data_year_readiness
SET policy_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE policy_ready END,
    score_segment_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE score_segment_ready END,
    admission_plan_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE admission_plan_ready END,
    major_requirement_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE major_requirement_ready END,
    major_meta_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE major_meta_ready END,
    ml_training_ready = CASE WHEN @confirmed = 1 THEN 0 ELSE ml_training_ready END,
    recommendation_phase = CASE WHEN @confirmed = 1 THEN 'PRE_OFFICIAL_DATA' ELSE recommendation_phase END,
    remarks = CASE WHEN @confirmed = 1 THEN '2026 官方关键数据不可用或需重做，已回退到未发布/未就绪阶段。' ELSE remarks END,
    last_checked_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE last_checked_at END,
    updated_at = CASE WHEN @confirmed = 1 THEN CURRENT_TIMESTAMP ELSE updated_at END
WHERE province_code = @province_code
  AND year = @target_year;

-- 写后核验
SELECT 'after_readiness' AS report_name,
       province_code, year, policy_ready, score_segment_ready, admission_plan_ready,
       major_requirement_ready, major_meta_ready, ml_training_ready, historical_training_ready,
       recommendation_phase, latest_import_batch_id, remarks, last_checked_at, updated_at
FROM data_year_readiness
WHERE province_code = @province_code AND year = @target_year;
