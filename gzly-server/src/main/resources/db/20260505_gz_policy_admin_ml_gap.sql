-- GZLY 贵州闭环补齐：2026 待确认政策 seed 与 ML 注册表兜底。
-- 幂等执行；只新增/修正配置，不删除旧数据。

CREATE TABLE IF NOT EXISTS `ml_model_registry` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `model_name` VARCHAR(120) NOT NULL DEFAULT '' COMMENT '模型名称',
  `model_type` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '模型类型',
  `model_version` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '模型版本',
  `train_year_range` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '训练年份范围',
  `train_data_count` INT NOT NULL DEFAULT 0 COMMENT '训练样本数',
  `feature_schema_json` JSON NULL COMMENT '特征结构',
  `metrics_json` JSON NULL COMMENT '评估指标',
  `model_file_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '模型文件路径',
  `status` VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT 'draft/active/archived',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `activated_at` DATETIME DEFAULT NULL COMMENT '启用时间',
  UNIQUE KEY `uk_ml_model_version` (`model_name`, `model_version`),
  KEY `idx_ml_model_status` (`model_name`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器学习模型注册表';

INSERT INTO `policy_rule_config`
(`province`, `year`, `candidate_type`, `batch_code`, `batch_name`, `volunteer_mode`, `max_volunteer_count`,
 `major_per_school_count`, `has_adjustment`, `filing_principle`, `admission_order`, `policy_status`,
 `official_source_title`, `official_source_url`, `official_source_text`, `enabled`)
VALUES
('GZ', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '普通类本科批', '专业类平行志愿', 96, 0, 0,
 '分数优先、遵循志愿、一轮投档', '按考生志愿顺序逐项检索', 'pending_confirm',
 '贵州省普通高校招生工作规定（待2026正式文件确认）', 'https://zsksy.guizhou.gov.cn/',
 '2026 年最终政策以贵州省招生考试院正式文件为准；当前仅按上一年度同制度配置生成待确认提示。', 1),
('GZ', 2026, '普通类', 'NORMAL_SPECIALTY', '普通类高职（专科）批', '专业类平行志愿', 96, 0, 0,
 '分数优先、遵循志愿、一轮投档', '按考生志愿顺序逐项检索', 'pending_confirm',
 '贵州省普通高校招生工作规定（待2026正式文件确认）', 'https://zsksy.guizhou.gov.cn/',
 '2026 年最终政策以贵州省招生考试院正式文件为准；当前仅按上一年度同制度配置生成待确认提示。', 1),
('GZ', 2026, '普通类', 'EARLY_C', '普通类本科提前批C段', '专业类平行志愿', 60, 0, 0,
 '分数优先、遵循志愿、一轮投档', '按考生志愿顺序逐项检索', 'pending_confirm',
 '贵州省普通高校招生工作规定（待2026正式文件确认）', 'https://zsksy.guizhou.gov.cn/',
 '2026 年最终政策以贵州省招生考试院正式文件为准；当前仅按上一年度同制度配置生成待确认提示。', 1),
('GZ', 2026, '普通类', 'EARLY_A_B', '普通类本科提前批A/B段', '院校顺序志愿', 1, 6, 1,
 '院校顺序志愿', '按院校顺序投档并结合专业志愿与调剂信息', 'pending_confirm',
 '贵州省普通高校招生工作规定（待2026正式文件确认）', 'https://zsksy.guizhou.gov.cn/',
 '2026 年最终政策以贵州省招生考试院正式文件为准；当前仅按上一年度同制度配置生成待确认提示。', 1)
ON DUPLICATE KEY UPDATE
  `batch_name` = VALUES(`batch_name`),
  `volunteer_mode` = VALUES(`volunteer_mode`),
  `max_volunteer_count` = VALUES(`max_volunteer_count`),
  `major_per_school_count` = VALUES(`major_per_school_count`),
  `has_adjustment` = VALUES(`has_adjustment`),
  `filing_principle` = VALUES(`filing_principle`),
  `admission_order` = VALUES(`admission_order`),
  `policy_status` = VALUES(`policy_status`),
  `official_source_title` = VALUES(`official_source_title`),
  `official_source_url` = VALUES(`official_source_url`),
  `official_source_text` = VALUES(`official_source_text`),
  `enabled` = VALUES(`enabled`),
  `updated_at` = CURRENT_TIMESTAMP;
