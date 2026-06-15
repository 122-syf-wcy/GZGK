-- ============================================================================
-- 上线后运营收口优化（不动正式招生表/不改 8 省推荐链路）
--  1) 反馈工单状态：新增 handle_status / result_id / province_code / handled_at / handle_note
--     说明：保留原 status（0未读/1已读）语义不变；新增 handle_status 表达工单处理状态。
--  2) AI 调用日志：ai_call_log（用于后台 AI 状态检测与最近调用日志，不存 API Key / 对话码）
-- ============================================================================

ALTER TABLE `biz_user_feedback`
  ADD COLUMN `handle_status` TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态：0未处理 1处理中 2已解决 3已忽略';
ALTER TABLE `biz_user_feedback`
  ADD COLUMN `result_id` BIGINT NULL COMMENT '关联方案ID(resultId)';
ALTER TABLE `biz_user_feedback`
  ADD COLUMN `province_code` VARCHAR(16) NULL COMMENT '关联省份代码';
ALTER TABLE `biz_user_feedback`
  ADD COLUMN `handled_at` DATETIME NULL COMMENT '处理时间';
ALTER TABLE `biz_user_feedback`
  ADD COLUMN `handle_note` VARCHAR(500) NULL COMMENT '处理备注';
ALTER TABLE `biz_user_feedback`
  ADD KEY `idx_feedback_handle_status` (`handle_status`, `created_at`);

CREATE TABLE IF NOT EXISTS `ai_call_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scene` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '调用场景：ai_qa/volunteer_analysis/advisor_chat/test_connection/test_volunteer/test_ai_qa',
  `success` TINYINT NOT NULL DEFAULT 0 COMMENT '是否成功：1是 0否',
  `http_status` INT NULL COMMENT 'HTTP 状态码',
  `error_code` VARCHAR(80) NULL COMMENT '错误码（如 INSUFFICIENT_BALANCE）',
  `model` VARCHAR(100) NULL COMMENT '模型名',
  `latency_ms` INT NULL COMMENT '耗时毫秒',
  `message` VARCHAR(500) NULL COMMENT '简要信息（已脱敏，不含 API Key/对话码）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_call_log_created` (`created_at`),
  KEY `idx_ai_call_log_scene_created` (`scene`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用日志（运营状态检测用）';
