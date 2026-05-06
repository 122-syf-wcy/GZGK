-- 扩展 biz_plan_history：保存完整请求快照、强制人工复核清单与监控指标。
-- 所有字段允许 NULL，旧记录可以平滑兼容。

SET @table_schema := DATABASE();

-- 偏好快照（JSON 字符串），用于跨设备恢复
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'preferred_majors'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `preferred_majors` VARCHAR(500) DEFAULT NULL COMMENT ''意向专业(JSON)'' AFTER `resubjects`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'preferred_regions'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `preferred_regions` VARCHAR(500) DEFAULT NULL COMMENT ''意向地区(JSON)'' AFTER `preferred_majors`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'strategy_mode'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `strategy_mode` VARCHAR(20) DEFAULT NULL COMMENT ''方案取向'' AFTER `preferred_regions`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'decision_priority'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `decision_priority` VARCHAR(20) DEFAULT NULL COMMENT ''决策优先级'' AFTER `strategy_mode`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'career_goal'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `career_goal` VARCHAR(20) DEFAULT NULL COMMENT ''长期目标'' AFTER `decision_priority`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'tuition_budget'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `tuition_budget` VARCHAR(20) DEFAULT NULL COMMENT ''预算偏好'' AFTER `career_goal`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'accept_private'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `accept_private` TINYINT DEFAULT NULL COMMENT ''是否接受民办 0/1'' AFTER `tuition_budget`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'accept_sino_foreign'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `accept_sino_foreign` TINYINT DEFAULT NULL COMMENT ''是否接受中外/港澳台合作 0/1'' AFTER `accept_private`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 数据质量警告 / 强制复核清单 / 监控指标 / 完整请求快照
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'data_quality_warning'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `data_quality_warning` TEXT NULL COMMENT ''数据质量警告'' AFTER `ai_analysis`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'manual_review_json'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `manual_review_json` MEDIUMTEXT NULL COMMENT ''强制人工复核清单(JSON)'' AFTER `data_quality_warning`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'metrics_json'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `metrics_json` VARCHAR(2000) NULL COMMENT ''生成耗时/复核率等监控指标(JSON)'' AFTER `manual_review_json`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'request_snapshot_json'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `request_snapshot_json` MEDIUMTEXT NULL COMMENT ''完整 GenerateRequest 快照(JSON)'' AFTER `metrics_json`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
