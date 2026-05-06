-- 四川数据获取与复核导入接口所需来源登记字段。
-- 幂等执行：生产上可单独导入本文件，不影响既有 GZ/SC 主表数据。

SET @table_schema := DATABASE();

ALTER TABLE `data_source_registry`
  MODIFY COLUMN `source_url` TEXT NULL COMMENT '来源文件/图片/网页';

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'data_source_registry'
    AND column_name = 'batch'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_source_registry` ADD COLUMN `batch` VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''批次'' AFTER `data_type`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'data_source_registry'
    AND column_name = 'source_level'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_source_registry` ADD COLUMN `source_level` VARCHAR(30) NOT NULL DEFAULT '''' COMMENT ''official/school/manual_verified'' AFTER `source_url`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'data_source_registry'
    AND column_name = 'source_hash'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_source_registry` ADD COLUMN `source_hash` VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''来源内容SHA-256或复核批次哈希'' AFTER `source_level`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'data_source_registry'
    AND column_name = 'last_checked_at'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_source_registry` ADD COLUMN `last_checked_at` DATETIME DEFAULT NULL COMMENT ''最近检查时间'' AFTER `row_count`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @table_schema
    AND table_name = 'data_source_registry'
    AND index_name = 'uk_source_registry_identity'
);
SET @sql := IF(@idx_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_source_registry` ADD UNIQUE KEY `uk_source_registry_identity` (`province_code`, `year`, `subject_type`, `data_type`, `batch`, `source_page_url`(191))'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
