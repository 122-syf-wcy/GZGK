-- Backfill optional subject requirements for major-level scores.
-- This migration keeps existing rows and derives requirements from the notes column
-- produced by the data completion scraper when that column is present.

SET @score_column_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'data_score_line_gz'
    AND column_name = 'resubject_requirement'
);

SET @sql := IF(@score_column_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_score_line_gz` ADD COLUMN `resubject_requirement` VARCHAR(100) DEFAULT '''' COMMENT ''再选科目要求'' AFTER `batch`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @score_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'data_score_line_gz'
    AND index_name = 'idx_year_subject_resub'
);

SET @sql := IF(@score_index_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_score_line_gz` ADD INDEX `idx_year_subject_resub` (`year`, `subject_type`, `resubject_requirement`)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @major_plan_count_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'data_major_score_gz'
    AND column_name = 'plan_count'
);

SET @sql := IF(@major_plan_count_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_major_score_gz` ADD COLUMN `plan_count` SMALLINT DEFAULT NULL COMMENT ''招生计划数'' AFTER `min_rank`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'data_major_score_gz'
    AND column_name = 'resubject_requirement'
);

SET @sql := IF(@column_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_major_score_gz` ADD COLUMN `resubject_requirement` VARCHAR(100) DEFAULT '''' COMMENT ''再选科目要求'' AFTER `batch`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @notes_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'data_major_score_gz'
    AND column_name = 'notes'
);

SET @sql := IF(@notes_exists = 0,
  'SELECT 1',
  'UPDATE `data_major_score_gz`
   SET `resubject_requirement` = CASE
     WHEN `notes` LIKE ''%再选不限%'' OR `notes` LIKE ''%再选科目不限%'' OR `notes` LIKE ''%再选：不限%'' THEN ''不限''
     WHEN `notes` LIKE ''%化学和生物%'' OR `notes` LIKE ''%化学、生物%'' OR `notes` LIKE ''%化学+生物%'' THEN ''化学和生物''
     WHEN `notes` LIKE ''%化学或生物%'' THEN ''化学或生物''
     WHEN `notes` LIKE ''%再选化学%'' OR `notes` LIKE ''%再选科目：化学%'' OR `notes` LIKE ''%再选 化学%'' THEN ''化学''
     WHEN `notes` LIKE ''%再选生物%'' OR `notes` LIKE ''%再选科目：生物%'' THEN ''生物''
     WHEN `notes` LIKE ''%再选政治%'' OR `notes` LIKE ''%再选思想政治%'' THEN ''政治''
     WHEN `notes` LIKE ''%再选地理%'' THEN ''地理''
     ELSE `resubject_requirement`
   END
   WHERE (`resubject_requirement` IS NULL OR `resubject_requirement` = '''')'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'data_major_score_gz'
    AND index_name = 'idx_year_subject_resub'
);

SET @sql := IF(@index_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_major_score_gz` ADD INDEX `idx_year_subject_resub` (`year`, `subject_type`, `resubject_requirement`)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
