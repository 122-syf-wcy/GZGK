-- =====================================================================
-- GZLY 卡密激活账号 + 安全码会话登录 基础表迁移
--
-- 一句话：新增 biz_card_key（卡密 + 安全码 + 用量上限），并给既有
--         biz_user 加 card_key_id / nickname / last_login_at 三列。
--
-- 反复执行幂等：建表 IF NOT EXISTS；列添加用 information_schema 校验。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. biz_card_key
--   生产存在同名遗留表（face_value/status TINYINT 早期 schema，已确认 0 行
--   且当前 jar 无引用），安全 drop 后按新版 schema 建表。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_card_key`;
CREATE TABLE `biz_card_key` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `card_key` VARCHAR(40) NOT NULL COMMENT '卡密明文（32 位字母数字 + 分隔）',
  `batch_no` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '批次号，便于回收',
  `status` VARCHAR(20) NOT NULL DEFAULT 'unused' COMMENT 'unused/active/revoked/expired',
  `user_id` BIGINT NULL COMMENT '激活后绑定的 biz_user.id',
  `secret_code_hash` VARCHAR(120) NULL COMMENT '安全码 BCrypt 哈希',
  `max_plans` INT NOT NULL DEFAULT 5 COMMENT '允许生成方案上限',
  `used_plans` INT NOT NULL DEFAULT 0 COMMENT '已生成方案数',
  `expires_at` DATETIME NULL COMMENT '失效时间（默认创建后 90 天）',
  `activated_at` DATETIME NULL COMMENT '激活时间',
  `revoked_at` DATETIME NULL COMMENT '撤销时间',
  `revoke_reason` VARCHAR(200) NULL COMMENT '撤销原因',
  `note` VARCHAR(200) NULL COMMENT '运营备注（发给谁、原因）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_biz_card_key_card` (`card_key`),
  KEY `idx_biz_card_key_status` (`status`),
  KEY `idx_biz_card_key_batch` (`batch_no`),
  KEY `idx_biz_card_key_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡密激活与安全码绑定表';

-- ---------------------------------------------------------------------
-- 2. biz_user 加列：card_key_id / nickname / last_login_at（幂等添加）
-- ---------------------------------------------------------------------

-- 2.1 card_key_id
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE() AND table_name = 'biz_user' AND column_name = 'card_key_id'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE biz_user ADD COLUMN card_key_id BIGINT NULL COMMENT ''关联 biz_card_key.id'' AFTER identifier, ADD KEY idx_biz_user_card_key (card_key_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.2 nickname
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE() AND table_name = 'biz_user' AND column_name = 'nickname'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE biz_user ADD COLUMN nickname VARCHAR(40) NOT NULL DEFAULT '''' COMMENT ''昵称'' AFTER card_key_id',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.3 last_login_at
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE() AND table_name = 'biz_user' AND column_name = 'last_login_at'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE biz_user ADD COLUMN last_login_at DATETIME NULL COMMENT ''最近登录时间'' AFTER last_active_time',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
