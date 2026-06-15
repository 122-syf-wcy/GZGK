SET @table_schema := DATABASE();

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'biz_plan_history'
    AND column_name = 'deleted'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''软删除标记 0=正常 1=已删除'' AFTER `safety_code_version`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'biz_plan_history'
    AND column_name = 'deleted_at'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `deleted_at` DATETIME NULL COMMENT ''软删除时间'' AFTER `deleted`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'biz_plan_history'
    AND column_name = 'deleted_by'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `deleted_by` BIGINT NULL COMMENT ''软删除操作人'' AFTER `deleted_at`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'biz_plan_history'
    AND column_name = 'delete_reason'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `delete_reason` VARCHAR(200) NULL COMMENT ''软删除原因'' AFTER `deleted_by`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @table_schema
    AND table_name = 'biz_plan_history'
    AND index_name = 'idx_deleted_created'
);
SET @sql := IF(@idx_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD INDEX `idx_deleted_created` (`deleted`, `created_at`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @table_schema
    AND table_name = 'biz_plan_history'
    AND index_name = 'idx_deleted_user_created'
);
SET @sql := IF(@idx_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD INDEX `idx_deleted_user_created` (`deleted`, `user_id`, `created_at`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @table_schema
    AND table_name = 'biz_plan_history'
    AND index_name = 'idx_deleted_fp_created'
);
SET @sql := IF(@idx_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD INDEX `idx_deleted_fp_created` (`deleted`, `safety_code_fingerprint`, `created_at`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
