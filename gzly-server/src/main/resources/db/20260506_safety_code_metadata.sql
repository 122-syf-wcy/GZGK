ALTER TABLE `biz_plan_history`
  ADD COLUMN IF NOT EXISTS `safety_code_hash` VARCHAR(120) NULL COMMENT '安全码 BCrypt 哈希' AFTER `request_snapshot_json`,
  ADD COLUMN IF NOT EXISTS `safety_code_created_at` DATETIME NULL COMMENT '安全码创建时间' AFTER `safety_code_hash`,
  ADD COLUMN IF NOT EXISTS `safety_code_version` INT NOT NULL DEFAULT 1 COMMENT '安全码版本' AFTER `safety_code_created_at`;

ALTER TABLE `volunteer_plan`
  ADD COLUMN IF NOT EXISTS `safety_code_hash` VARCHAR(120) NULL COMMENT '安全码 BCrypt 哈希' AFTER `total_count`,
  ADD COLUMN IF NOT EXISTS `safety_code_created_at` DATETIME NULL COMMENT '安全码创建时间' AFTER `safety_code_hash`,
  ADD COLUMN IF NOT EXISTS `safety_code_version` INT NOT NULL DEFAULT 1 COMMENT '安全码版本' AFTER `safety_code_created_at`;
