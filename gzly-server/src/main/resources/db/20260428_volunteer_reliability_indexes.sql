-- 2026-04-28 志愿可靠性过滤与排序索引
SET NAMES utf8mb4;

SET @idx_major_reliability_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'data_major_score_gz'
    AND index_name = 'idx_major_reliability_filter'
);

SET @sql := IF(@idx_major_reliability_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_major_score_gz` ADD INDEX `idx_major_reliability_filter` (`subject_type`, `year`, `batch`, `min_rank`)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_score_reliability_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'data_score_line_gz'
    AND index_name = 'idx_score_reliability_filter'
);

SET @sql := IF(@idx_score_reliability_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_score_line_gz` ADD INDEX `idx_score_reliability_filter` (`subject_type`, `year`, `batch`, `min_rank`)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
