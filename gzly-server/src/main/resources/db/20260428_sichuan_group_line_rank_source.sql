-- 四川院校专业组线位次来源元数据。
-- 幂等执行：用于区分原始投档位次与一分一段换算位次。

SET @table_schema := DATABASE();

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'data_admission_group_line'
    AND column_name = 'rank_source_type'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_admission_group_line` ADD COLUMN `rank_source_type` VARCHAR(30) NOT NULL DEFAULT '''' COMMENT ''位次来源类型 original/score_rank_converted/missing'' AFTER `min_rank`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'data_admission_group_line'
    AND column_name = 'rank_source_note'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_admission_group_line` ADD COLUMN `rank_source_note` VARCHAR(500) NOT NULL DEFAULT '''' COMMENT ''位次来源说明'' AFTER `rank_source_type`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'data_admission_group_line'
    AND column_name = 'rank_source_url'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_admission_group_line` ADD COLUMN `rank_source_url` VARCHAR(500) NOT NULL DEFAULT '''' COMMENT ''位次来源链接'' AFTER `rank_source_note`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'data_admission_group_line'
    AND column_name = 'rank_source_page_url'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_admission_group_line` ADD COLUMN `rank_source_page_url` VARCHAR(500) NOT NULL DEFAULT '''' COMMENT ''位次来源发布页面'' AFTER `rank_source_url`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
