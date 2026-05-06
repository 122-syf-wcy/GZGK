CREATE TABLE IF NOT EXISTS `biz_user_feedback` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `content` TEXT NOT NULL COMMENT '反馈内容',
  `source_page` VARCHAR(120) DEFAULT '' COMMENT '来源页面',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未读 1=已读',
  `ip_hash` VARCHAR(64) DEFAULT NULL COMMENT 'IP 脱敏哈希',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `read_at` DATETIME DEFAULT NULL COMMENT '已读时间',
  KEY `idx_status_created` (`status`, `created_at`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户意见反馈';
