-- 2026-04-10 高并发优化：核心查询索引
SET NAMES utf8mb4;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'data_score_line_gz'
        AND index_name = 'idx_subject_rank_year'
    ),
    'SELECT 1',
    'ALTER TABLE `data_score_line_gz` ADD INDEX `idx_subject_rank_year` (`subject_type`, `min_rank`, `year`)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'data_major_score_gz'
        AND index_name = 'idx_subject_rank_year'
    ),
    'SELECT 1',
    'ALTER TABLE `data_major_score_gz` ADD INDEX `idx_subject_rank_year` (`subject_type`, `min_rank`, `year`)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
