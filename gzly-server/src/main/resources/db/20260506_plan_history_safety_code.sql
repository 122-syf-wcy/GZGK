ALTER TABLE `biz_plan_history`
  ADD COLUMN `safety_code_hash` VARCHAR(120) NULL COMMENT '方案安全码 BCrypt 哈希' AFTER `request_snapshot_json`;
