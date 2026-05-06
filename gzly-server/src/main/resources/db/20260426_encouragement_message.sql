CREATE TABLE IF NOT EXISTS `biz_encouragement_message` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `nickname` VARCHAR(30) NOT NULL DEFAULT '贵州考生' COMMENT '展示昵称',
  `content` VARCHAR(240) NOT NULL COMMENT '鼓励留言内容',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=隐藏 1=展示',
  `ip_hash` VARCHAR(64) DEFAULT NULL COMMENT 'IP 脱敏哈希',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考生加油留言墙';
