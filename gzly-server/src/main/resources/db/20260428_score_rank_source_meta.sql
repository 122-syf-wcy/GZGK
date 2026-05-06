SET @has_source_page_url := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'data_score_rank_gz'
    AND column_name = 'source_page_url'
);
SET @ddl_source_page_url := IF(
  @has_source_page_url = 0,
  'ALTER TABLE `data_score_rank_gz` ADD COLUMN `source_page_url` VARCHAR(500) NOT NULL DEFAULT '''' COMMENT ''官方发布页面'' AFTER `source_url`',
  'SELECT 1'
);
PREPARE stmt_source_page_url FROM @ddl_source_page_url;
EXECUTE stmt_source_page_url;
DEALLOCATE PREPARE stmt_source_page_url;

SET @has_parse_method := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'data_score_rank_gz'
    AND column_name = 'parse_method'
);
SET @ddl_parse_method := IF(
  @has_parse_method = 0,
  'ALTER TABLE `data_score_rank_gz` ADD COLUMN `parse_method` VARCHAR(40) NOT NULL DEFAULT '''' COMMENT ''解析方式'' AFTER `source_file`',
  'SELECT 1'
);
PREPARE stmt_parse_method FROM @ddl_parse_method;
EXECUTE stmt_parse_method;
DEALLOCATE PREPARE stmt_parse_method;
