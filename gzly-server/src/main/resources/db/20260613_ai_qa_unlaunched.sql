-- ============================================================================
-- 未上线地区 AI 志愿问答（独立会话系统）
-- 仅服务未接入完整志愿推荐的地区；与已上线 8 省（GZ/SC/AH/HB/GX/HI/YN/HA）解耦。
-- 不写正式招生表、不导入官方数据、不承诺录取。
-- 对话码只存 hash + 不可逆指纹，绝不存明文。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `ai_qa_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_uid` VARCHAR(40) NOT NULL COMMENT '对外公开会话ID（不可枚举）',
  `region_code` VARCHAR(16) NOT NULL COMMENT '未上线地区代码（排除已上线8省）',
  `region_name` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '地区名称',
  `exam_year` INT NULL COMMENT '咨询参考年份',
  `score` INT NULL COMMENT '考生分数',
  `province_rank` INT NULL COMMENT '考生位次',
  `subjects` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '选科（逗号分隔）',
  `batch` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '批次',
  `major_preference` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '专业偏好',
  `region_preference` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '地区/城市偏好',
  `code_hash` VARCHAR(100) NOT NULL COMMENT '对话码 BCrypt 哈希（不可逆）',
  `code_fingerprint` VARCHAR(80) NOT NULL COMMENT '对话码确定性指纹（仅用于定位会话）',
  `context_summary` MEDIUMTEXT COMMENT '上下文压缩摘要',
  `memory_facts` MEDIUMTEXT COMMENT '结构化记忆（分数/位次/选科/偏好/已给建议/排除项/重要来源/未解决问题）',
  `message_count` INT NOT NULL DEFAULT 0 COMMENT '累计消息数',
  `compaction_count` INT NOT NULL DEFAULT 0 COMMENT '累计压缩次数',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常 0关闭',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_active_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最近活跃时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_qa_session_uid` (`session_uid`),
  UNIQUE KEY `uk_ai_qa_session_fingerprint` (`code_fingerprint`),
  KEY `idx_ai_qa_session_region` (`region_code`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='未上线地区AI志愿问答会话';

CREATE TABLE IF NOT EXISTS `ai_qa_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` BIGINT NOT NULL COMMENT '会话ID（ai_qa_session.id）',
  `role` VARCHAR(16) NOT NULL COMMENT '角色：user/assistant',
  `content` MEDIUMTEXT NOT NULL COMMENT '消息内容（已脱敏，不含对话码/Key）',
  `token_estimate` INT NOT NULL DEFAULT 0 COMMENT '估算token，用于压缩阈值判断',
  `compacted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已折叠进压缩摘要：1是 0否',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_qa_message_session` (`session_id`, `created_at`),
  KEY `idx_ai_qa_message_session_live` (`session_id`, `compacted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='未上线地区AI志愿问答消息';

CREATE TABLE IF NOT EXISTS `ai_qa_evidence` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` BIGINT NOT NULL COMMENT '会话ID',
  `message_id` BIGINT NOT NULL COMMENT '所属助手消息ID',
  `title` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '来源标题',
  `url` VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '来源机构',
  `summary` VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '来源摘要',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_qa_evidence_message` (`message_id`),
  KEY `idx_ai_qa_evidence_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='未上线地区AI志愿问答来源卡片';

CREATE TABLE IF NOT EXISTS `ai_qa_context_compaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` BIGINT NOT NULL COMMENT '会话ID',
  `from_message_id` BIGINT NULL COMMENT '折叠起始消息ID',
  `to_message_id` BIGINT NULL COMMENT '折叠结束消息ID',
  `compacted_count` INT NOT NULL DEFAULT 0 COMMENT '本次折叠的消息数',
  `summary` MEDIUMTEXT COMMENT '压缩摘要（非简单截断）',
  `preserved_facts` MEDIUMTEXT COMMENT '本次压缩保留的关键事实JSON',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_qa_compaction_session` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='未上线地区AI志愿问答上下文压缩快照';
