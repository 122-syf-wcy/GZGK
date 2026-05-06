-- 四川招生计划专业唯一键补 subject_type。
-- 幂等执行：四川历史类/物理类可复用同一院校专业组代码和专业代码。

SET @table_schema := DATABASE();

SET @index_has_subject := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @table_schema
    AND table_name = 'data_admission_group_plan'
    AND index_name = 'uk_group_plan_major'
    AND column_name = 'subject_type'
);

SET @sql := IF(@index_has_subject > 0,
  'SELECT 1',
  'ALTER TABLE `data_admission_group_plan` DROP INDEX `uk_group_plan_major`, ADD UNIQUE KEY `uk_group_plan_major` (`province_code`, `year`, `school_id`, `group_code`, `subject_type`, `major_code`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
