-- 2026-04-27 志愿结果近三年录取记录查询索引
SET NAMES utf8mb4;

SET @idx_major_history_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'data_major_score_gz'
    AND index_name = 'idx_major_score_history'
);

SET @sql := IF(@idx_major_history_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_major_score_gz` ADD INDEX `idx_major_score_history` (`school_id`, `subject_type`, `major_name`(80), `year`)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_school_history_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'data_score_line_gz'
    AND index_name = 'idx_score_line_history'
);

SET @sql := IF(@idx_school_history_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_score_line_gz` ADD INDEX `idx_score_line_history` (`school_id`, `subject_type`, `year`)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
