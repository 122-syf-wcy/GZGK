-- 扩展 biz_plan_history：记录生成前风险告知确认版本与确认时间。
-- 字段允许 NULL，旧记录平滑兼容；后续新记录由后端强校验后写入。

SET @table_schema := DATABASE();

ALTER TABLE `biz_plan_history`
  MODIFY COLUMN `agreed_disclaimer` TINYINT NOT NULL DEFAULT 0 COMMENT '是否确认生成前风险告知';

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'disclaimer_version'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `disclaimer_version` VARCHAR(40) DEFAULT NULL COMMENT ''确认的风险告知版本'' AFTER `agreed_disclaimer`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'disclaimer_confirmed_at'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `disclaimer_confirmed_at` DATETIME DEFAULT NULL COMMENT ''风险告知确认时间'' AFTER `disclaimer_version`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
