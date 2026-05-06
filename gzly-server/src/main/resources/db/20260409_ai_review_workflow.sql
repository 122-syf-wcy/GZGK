SET @has_ai_review_result := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'uni_content_edit'
    AND COLUMN_NAME = 'ai_review_result'
);

SET @add_ai_review_result_sql := IF(
  @has_ai_review_result = 0,
  'ALTER TABLE `uni_content_edit` ADD COLUMN `ai_review_result` VARCHAR(500) DEFAULT NULL COMMENT ''AI审核结果JSON'' AFTER `status`',
  'SELECT 1'
);

PREPARE stmt_add_ai_review_result FROM @add_ai_review_result_sql;
EXECUTE stmt_add_ai_review_result;
DEALLOCATE PREPARE stmt_add_ai_review_result;

SET @has_media_ai_review_result := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'uni_media'
    AND COLUMN_NAME = 'ai_review_result'
);

SET @add_media_ai_review_result_sql := IF(
  @has_media_ai_review_result = 0,
  'ALTER TABLE `uni_media` ADD COLUMN `ai_review_result` VARCHAR(500) DEFAULT NULL COMMENT ''AI审核结果JSON'' AFTER `status`',
  'SELECT 1'
);

PREPARE stmt_add_media_ai_review_result FROM @add_media_ai_review_result_sql;
EXECUTE stmt_add_media_ai_review_result;
DEALLOCATE PREPARE stmt_add_media_ai_review_result;

CREATE TABLE IF NOT EXISTS `biz_university_qa` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL COMMENT '院校ID',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父问题ID，null表示提问',
  `content` TEXT NOT NULL COMMENT '问题或回答内容',
  `author_name` VARCHAR(50) DEFAULT '匿名考生' COMMENT '发布者名称',
  `author_type` ENUM('student','alumni','anonymous') DEFAULT 'anonymous' COMMENT '发布者类型',
  `status` TINYINT DEFAULT 0 COMMENT '0=待AI审核 1=已发布 2=已拒绝 3=待人工审核',
  `ai_review_result` VARCHAR(500) DEFAULT NULL COMMENT 'AI审核结果JSON',
  `review_note` VARCHAR(200) DEFAULT '' COMMENT '人工审核备注',
  `review_actor_role` VARCHAR(20) DEFAULT '' COMMENT '最终审核角色(ai/alumni/admin/system)',
  `review_actor_id` BIGINT DEFAULT NULL COMMENT '最终审核人ID',
  `reviewed_at` DATETIME DEFAULT NULL COMMENT '最终审核时间',
  `like_count` INT DEFAULT 0 COMMENT '点赞数',
  `ip_hash` VARCHAR(64) DEFAULT NULL COMMENT 'IP脱敏哈希',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_school_status` (`school_id`, `status`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='院校问答';

SET @has_qa_ai_review_result := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'biz_university_qa'
    AND COLUMN_NAME = 'ai_review_result'
);

SET @add_qa_ai_review_result_sql := IF(
  @has_qa_ai_review_result = 0,
  'ALTER TABLE `biz_university_qa` ADD COLUMN `ai_review_result` VARCHAR(500) DEFAULT NULL COMMENT ''AI审核结果JSON'' AFTER `status`',
  'SELECT 1'
);

PREPARE stmt_add_qa_ai_review_result FROM @add_qa_ai_review_result_sql;
EXECUTE stmt_add_qa_ai_review_result;
DEALLOCATE PREPARE stmt_add_qa_ai_review_result;

SET @has_media_review_note := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'uni_media'
    AND COLUMN_NAME = 'review_note'
);

SET @add_media_review_note_sql := IF(
  @has_media_review_note = 0,
  'ALTER TABLE `uni_media` ADD COLUMN `review_note` VARCHAR(200) DEFAULT '''' COMMENT ''人工审核备注'' AFTER `ai_review_result`',
  'SELECT 1'
);

PREPARE stmt_add_media_review_note FROM @add_media_review_note_sql;
EXECUTE stmt_add_media_review_note;
DEALLOCATE PREPARE stmt_add_media_review_note;

SET @has_qa_review_note := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'biz_university_qa'
    AND COLUMN_NAME = 'review_note'
);

SET @add_qa_review_note_sql := IF(
  @has_qa_review_note = 0,
  'ALTER TABLE `biz_university_qa` ADD COLUMN `review_note` VARCHAR(200) DEFAULT '''' COMMENT ''人工审核备注'' AFTER `ai_review_result`',
  'SELECT 1'
);

PREPARE stmt_add_qa_review_note FROM @add_qa_review_note_sql;
EXECUTE stmt_add_qa_review_note;
DEALLOCATE PREPARE stmt_add_qa_review_note;

SET @has_media_review_actor_role := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'uni_media' AND COLUMN_NAME = 'review_actor_role'
);
SET @sql_media_review_actor_role := IF(
  @has_media_review_actor_role = 0,
  'ALTER TABLE `uni_media` ADD COLUMN `review_actor_role` VARCHAR(20) DEFAULT '''' COMMENT ''最终审核角色(ai/alumni/admin/system)'' AFTER `review_note`',
  'SELECT 1'
);
PREPARE stmt_media_review_actor_role FROM @sql_media_review_actor_role;
EXECUTE stmt_media_review_actor_role;
DEALLOCATE PREPARE stmt_media_review_actor_role;

SET @has_media_review_actor_id := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'uni_media' AND COLUMN_NAME = 'review_actor_id'
);
SET @sql_media_review_actor_id := IF(
  @has_media_review_actor_id = 0,
  'ALTER TABLE `uni_media` ADD COLUMN `review_actor_id` BIGINT DEFAULT NULL COMMENT ''最终审核人ID'' AFTER `review_actor_role`',
  'SELECT 1'
);
PREPARE stmt_media_review_actor_id FROM @sql_media_review_actor_id;
EXECUTE stmt_media_review_actor_id;
DEALLOCATE PREPARE stmt_media_review_actor_id;

SET @has_media_reviewed_at := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'uni_media' AND COLUMN_NAME = 'reviewed_at'
);
SET @sql_media_reviewed_at := IF(
  @has_media_reviewed_at = 0,
  'ALTER TABLE `uni_media` ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL COMMENT ''最终审核时间'' AFTER `review_actor_id`',
  'SELECT 1'
);
PREPARE stmt_media_reviewed_at FROM @sql_media_reviewed_at;
EXECUTE stmt_media_reviewed_at;
DEALLOCATE PREPARE stmt_media_reviewed_at;

SET @has_edit_review_actor_role := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'uni_content_edit' AND COLUMN_NAME = 'review_actor_role'
);
SET @sql_edit_review_actor_role := IF(
  @has_edit_review_actor_role = 0,
  'ALTER TABLE `uni_content_edit` ADD COLUMN `review_actor_role` VARCHAR(20) DEFAULT '''' COMMENT ''最终审核角色(ai/alumni/admin/system)'' AFTER `review_note`',
  'SELECT 1'
);
PREPARE stmt_edit_review_actor_role FROM @sql_edit_review_actor_role;
EXECUTE stmt_edit_review_actor_role;
DEALLOCATE PREPARE stmt_edit_review_actor_role;

SET @has_edit_review_actor_id := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'uni_content_edit' AND COLUMN_NAME = 'review_actor_id'
);
SET @sql_edit_review_actor_id := IF(
  @has_edit_review_actor_id = 0,
  'ALTER TABLE `uni_content_edit` ADD COLUMN `review_actor_id` BIGINT DEFAULT NULL COMMENT ''最终审核人ID'' AFTER `review_actor_role`',
  'SELECT 1'
);
PREPARE stmt_edit_review_actor_id FROM @sql_edit_review_actor_id;
EXECUTE stmt_edit_review_actor_id;
DEALLOCATE PREPARE stmt_edit_review_actor_id;

SET @has_edit_reviewed_at := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'uni_content_edit' AND COLUMN_NAME = 'reviewed_at'
);
SET @sql_edit_reviewed_at := IF(
  @has_edit_reviewed_at = 0,
  'ALTER TABLE `uni_content_edit` ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL COMMENT ''最终审核时间'' AFTER `review_actor_id`',
  'SELECT 1'
);
PREPARE stmt_edit_reviewed_at FROM @sql_edit_reviewed_at;
EXECUTE stmt_edit_reviewed_at;
DEALLOCATE PREPARE stmt_edit_reviewed_at;

SET @has_qa_review_actor_role := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_university_qa' AND COLUMN_NAME = 'review_actor_role'
);
SET @sql_qa_review_actor_role := IF(
  @has_qa_review_actor_role = 0,
  'ALTER TABLE `biz_university_qa` ADD COLUMN `review_actor_role` VARCHAR(20) DEFAULT '''' COMMENT ''最终审核角色(ai/alumni/admin/system)'' AFTER `review_note`',
  'SELECT 1'
);
PREPARE stmt_qa_review_actor_role FROM @sql_qa_review_actor_role;
EXECUTE stmt_qa_review_actor_role;
DEALLOCATE PREPARE stmt_qa_review_actor_role;

SET @has_qa_review_actor_id := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_university_qa' AND COLUMN_NAME = 'review_actor_id'
);
SET @sql_qa_review_actor_id := IF(
  @has_qa_review_actor_id = 0,
  'ALTER TABLE `biz_university_qa` ADD COLUMN `review_actor_id` BIGINT DEFAULT NULL COMMENT ''最终审核人ID'' AFTER `review_actor_role`',
  'SELECT 1'
);
PREPARE stmt_qa_review_actor_id FROM @sql_qa_review_actor_id;
EXECUTE stmt_qa_review_actor_id;
DEALLOCATE PREPARE stmt_qa_review_actor_id;

SET @has_qa_reviewed_at := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_university_qa' AND COLUMN_NAME = 'reviewed_at'
);
SET @sql_qa_reviewed_at := IF(
  @has_qa_reviewed_at = 0,
  'ALTER TABLE `biz_university_qa` ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL COMMENT ''最终审核时间'' AFTER `review_actor_id`',
  'SELECT 1'
);
PREPARE stmt_qa_reviewed_at FROM @sql_qa_reviewed_at;
EXECUTE stmt_qa_reviewed_at;
DEALLOCATE PREPARE stmt_qa_reviewed_at;

SET @has_uni_qa_disabled := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_university' AND COLUMN_NAME = 'qa_disabled'
);
SET @sql_uni_qa_disabled := IF(
  @has_uni_qa_disabled = 0,
  'ALTER TABLE `sys_university` ADD COLUMN `qa_disabled` TINYINT DEFAULT 0 COMMENT ''问答是否关闭 0=否 1=是'' AFTER `content`',
  'SELECT 1'
);
PREPARE stmt_uni_qa_disabled FROM @sql_uni_qa_disabled;
EXECUTE stmt_uni_qa_disabled;
DEALLOCATE PREPARE stmt_uni_qa_disabled;

SET @has_uni_qa_disabled_reason := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_university' AND COLUMN_NAME = 'qa_disabled_reason'
);
SET @sql_uni_qa_disabled_reason := IF(
  @has_uni_qa_disabled_reason = 0,
  'ALTER TABLE `sys_university` ADD COLUMN `qa_disabled_reason` VARCHAR(200) DEFAULT '''' COMMENT ''问答关闭原因'' AFTER `qa_disabled`',
  'SELECT 1'
);
PREPARE stmt_uni_qa_disabled_reason FROM @sql_uni_qa_disabled_reason;
EXECUTE stmt_uni_qa_disabled_reason;
DEALLOCATE PREPARE stmt_uni_qa_disabled_reason;

SET @has_uni_qa_disabled_until := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_university' AND COLUMN_NAME = 'qa_disabled_until'
);
SET @sql_uni_qa_disabled_until := IF(
  @has_uni_qa_disabled_until = 0,
  'ALTER TABLE `sys_university` ADD COLUMN `qa_disabled_until` DATETIME DEFAULT NULL COMMENT ''问答关闭截止时间'' AFTER `qa_disabled_reason`',
  'SELECT 1'
);
PREPARE stmt_uni_qa_disabled_until FROM @sql_uni_qa_disabled_until;
EXECUTE stmt_uni_qa_disabled_until;
DEALLOCATE PREPARE stmt_uni_qa_disabled_until;

ALTER TABLE `uni_media`
  MODIFY COLUMN `media_type` TINYINT NOT NULL DEFAULT 1 COMMENT '1=照片 2=资讯 3=文件 4=背景横幅',
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待AI审核 1=已发布 2=已拒绝 3=待人工审核';

ALTER TABLE `uni_content_edit`
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待AI审核 1=已采纳 2=已拒绝 3=待人工审核';
