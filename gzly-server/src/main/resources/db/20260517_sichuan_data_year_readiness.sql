-- 四川 data_year_readiness 种子（与贵州同款）
-- 三年（2024 / 2025 / 2026）：
--   - 2024 老高考"院校 + 专业"模式：score_segment / admission_plan ready=1 但 ml_training_ready=0
--     （SC 2024 老高考数据与新高考"院校专业组"完全不同口径，不能直接做 ML 训练样本）
--   - 2025 新高考首年：当前 score_segment_ready=1（一分一段已导入 541+514），
--     但 admission_plan_ready=0（仅 27/90 组 line + 21/90 组 plan）；ml_training_ready=0
--   - 2026 PRE_OFFICIAL_DATA：等 6 月底官方数据 + 重训

INSERT INTO data_year_readiness (
  province_code, year,
  policy_ready, score_segment_ready, admission_plan_ready, major_requirement_ready,
  major_meta_ready, ml_training_ready, historical_training_ready,
  recommendation_phase, latest_import_batch_id, remarks
) VALUES
('SC', 2024,
 1, 0, 0, 0,
 0, 0, 0,
 'MODEL_RETRAINED', NULL,
 '四川 2024 仍为老高考"院校+专业"模式，与 2025 起新高考"院校专业组"完全不同口径；本行仅作为历史年份兜底，不进入 ML 训练。'),
('SC', 2025,
 1, 1, 0, 0,
 0, 0, 1,
 'PRE_OFFICIAL_DATA', NULL,
 '四川 2025 新高考首年：一分一段 541+514 已导入；普通本科批B段院校专业组线 27/90、招生计划 21/90，等补齐后开放 ML 训练 baseline；选科要求未导入。'),
('SC', 2026,
 0, 0, 0, 0,
 0, 0, 1,
 'PRE_OFFICIAL_DATA', NULL,
 '四川 2026 官方招生计划、一分一段表和模型重训尚未完成；仅允许历史数据趋势分析和缺口提示。')
ON DUPLICATE KEY UPDATE
  policy_ready = VALUES(policy_ready),
  score_segment_ready = VALUES(score_segment_ready),
  admission_plan_ready = VALUES(admission_plan_ready),
  major_requirement_ready = VALUES(major_requirement_ready),
  major_meta_ready = VALUES(major_meta_ready),
  ml_training_ready = VALUES(ml_training_ready),
  historical_training_ready = VALUES(historical_training_ready),
  recommendation_phase = VALUES(recommendation_phase),
  latest_import_batch_id = VALUES(latest_import_batch_id),
  remarks = VALUES(remarks),
  updated_at = CURRENT_TIMESTAMP;
