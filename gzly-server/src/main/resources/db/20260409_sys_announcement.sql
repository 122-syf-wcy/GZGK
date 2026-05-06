CREATE TABLE IF NOT EXISTS `sys_announcement` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `title` VARCHAR(200) NOT NULL COMMENT '公告标题',
  `content_md` MEDIUMTEXT NOT NULL COMMENT 'Markdown 内容',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布 2=已停用',
  `popup_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '首页是否弹窗展示 0=否 1=是',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值，越大越靠前',
  `published_at` DATETIME DEFAULT NULL COMMENT '发布时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_status_popup` (`status`, `popup_enabled`),
  KEY `idx_sort_published` (`sort_order`, `published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告';
