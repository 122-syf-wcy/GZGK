-- GZLY 志愿推荐闭环升级：政策配置、推荐方案、skills RAG、合规审查、ML 注册表。
-- 幂等执行；现有 biz_plan_history 与数据表继续保留。

CREATE TABLE IF NOT EXISTS `policy_rule_config` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `province` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '省份代码，如 GZ',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `candidate_type` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '考生类别',
  `batch_code` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '批次代码',
  `batch_name` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '批次名称',
  `volunteer_mode` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '志愿模式',
  `max_volunteer_count` INT NOT NULL DEFAULT 0 COMMENT '最大志愿数量',
  `major_per_school_count` INT NOT NULL DEFAULT 0 COMMENT '每校专业数量',
  `has_adjustment` TINYINT NOT NULL DEFAULT 0 COMMENT '是否有专业调剂',
  `filing_principle` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '投档原则',
  `admission_order` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '录取顺序说明',
  `policy_status` VARCHAR(30) NOT NULL DEFAULT 'pending_confirm' COMMENT 'confirmed/draft/pending_confirm',
  `official_source_title` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '官方来源标题',
  `official_source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方来源链接',
  `official_source_text` TEXT NULL COMMENT '官方来源摘录',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_policy_rule` (`province`, `year`, `candidate_type`, `batch_code`),
  KEY `idx_policy_enabled` (`province`, `year`, `enabled`, `policy_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年度招生政策规则配置';

CREATE TABLE IF NOT EXISTS `admission_plan` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `province` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '招生省份代码',
  `batch_code` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '批次代码',
  `candidate_type` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '考生类别',
  `subject_type` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '科类',
  `selected_subject_requirement` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '选科要求',
  `school_code` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '院校代码',
  `school_name` VARCHAR(160) NOT NULL DEFAULT '' COMMENT '院校名称',
  `major_code` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '专业代码',
  `major_name` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '专业名称',
  `major_category` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '专业门类',
  `plan_count` INT NOT NULL DEFAULT 0 COMMENT '招生计划数',
  `tuition` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '学费',
  `duration` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '学制',
  `campus` VARCHAR(160) NOT NULL DEFAULT '' COMMENT '校区',
  `remarks` TEXT NULL COMMENT '备注',
  `special_limit` TEXT NULL COMMENT '特殊限制',
  `is_public` TINYINT NOT NULL DEFAULT 0 COMMENT '是否公办',
  `is_private` TINYINT NOT NULL DEFAULT 0 COMMENT '是否民办',
  `is_chinese_foreign_coop` TINYINT NOT NULL DEFAULT 0 COMMENT '是否中外合作',
  `school_level` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '院校层次',
  `school_province` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '院校省份',
  `school_city` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '院校城市',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_admission_plan` (`year`, `province`, `batch_code`, `candidate_type`, `subject_type`, `school_code`, `major_code`),
  KEY `idx_admission_plan_filter` (`province`, `year`, `batch_code`, `candidate_type`, `subject_type`),
  KEY `idx_admission_plan_school` (`school_code`, `major_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当年招生计划表';

CREATE TABLE IF NOT EXISTS `admission_history` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `province` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '省份代码',
  `batch_code` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '批次代码',
  `candidate_type` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '考生类别',
  `subject_type` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '科类',
  `school_code` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '院校代码',
  `school_name` VARCHAR(160) NOT NULL DEFAULT '' COMMENT '院校名称',
  `major_code` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '专业代码',
  `major_name` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '专业名称',
  `min_score` SMALLINT DEFAULT NULL COMMENT '最低分',
  `min_rank` INT DEFAULT NULL COMMENT '最低位次',
  `avg_score` SMALLINT DEFAULT NULL COMMENT '平均分',
  `avg_rank` INT DEFAULT NULL COMMENT '平均位次',
  `max_score` SMALLINT DEFAULT NULL COMMENT '最高分',
  `max_rank` INT DEFAULT NULL COMMENT '最高位次',
  `plan_count` INT DEFAULT NULL COMMENT '计划数',
  `admitted_count` INT DEFAULT NULL COMMENT '录取人数',
  `first_round_full` TINYINT NOT NULL DEFAULT 0 COMMENT '首轮是否满额',
  `has_supplement` TINYINT NOT NULL DEFAULT 0 COMMENT '是否征集',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_admission_history` (`year`, `province`, `batch_code`, `candidate_type`, `subject_type`, `school_code`, `major_code`),
  KEY `idx_admission_history_rank` (`province`, `batch_code`, `subject_type`, `min_rank`),
  KEY `idx_admission_history_school` (`school_code`, `major_code`, `year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史录取数据表';

CREATE TABLE IF NOT EXISTS `score_rank_segment` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `province` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '省份代码',
  `candidate_type` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '考生类别',
  `subject_type` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '科类',
  `score` SMALLINT NOT NULL COMMENT '分数',
  `same_score_count` INT NOT NULL DEFAULT 0 COMMENT '同分人数',
  `cumulative_count` INT NOT NULL DEFAULT 0 COMMENT '累计人数',
  `rank_min` INT NOT NULL DEFAULT 0 COMMENT '同分最好位次',
  `rank_max` INT NOT NULL DEFAULT 0 COMMENT '同分保守位次',
  UNIQUE KEY `uk_score_rank_segment` (`year`, `province`, `candidate_type`, `subject_type`, `score`),
  KEY `idx_score_rank_segment_rank` (`province`, `year`, `subject_type`, `rank_min`, `rank_max`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一分一段表';

CREATE TABLE IF NOT EXISTS `school_info` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `school_code` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '院校代码',
  `school_name` VARCHAR(160) NOT NULL DEFAULT '' COMMENT '院校名称',
  `province` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '省份',
  `city` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '城市',
  `school_type` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '院校类型',
  `school_level` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '院校层次',
  `is_985` TINYINT NOT NULL DEFAULT 0 COMMENT '是否985',
  `is_211` TINYINT NOT NULL DEFAULT 0 COMMENT '是否211',
  `is_double_first_class` TINYINT NOT NULL DEFAULT 0 COMMENT '是否双一流',
  `is_public` TINYINT NOT NULL DEFAULT 0 COMMENT '是否公办',
  `ranking_score` DECIMAL(6,2) DEFAULT NULL COMMENT '院校排名分',
  `employment_score` DECIMAL(6,2) DEFAULT NULL COMMENT '就业分',
  `postgraduate_score` DECIMAL(6,2) DEFAULT NULL COMMENT '升学分',
  `tags` JSON NULL COMMENT '标签',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_school_info_code` (`school_code`),
  KEY `idx_school_info_name` (`school_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='院校信息表';

CREATE TABLE IF NOT EXISTS `major_info` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `major_code` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '专业代码',
  `major_name` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '专业名称',
  `major_category` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '专业门类',
  `discipline` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '学科',
  `employment_score` DECIMAL(6,2) DEFAULT NULL COMMENT '就业分',
  `salary_score` DECIMAL(6,2) DEFAULT NULL COMMENT '薪酬分',
  `postgraduate_score` DECIMAL(6,2) DEFAULT NULL COMMENT '升学分',
  `civil_service_score` DECIMAL(6,2) DEFAULT NULL COMMENT '考公分',
  `hot_score` DECIMAL(6,2) DEFAULT NULL COMMENT '热度分',
  `risk_score` DECIMAL(6,2) DEFAULT NULL COMMENT '风险分',
  `suitable_subjects` JSON NULL COMMENT '适配科目',
  `limitation_tags` JSON NULL COMMENT '限制标签',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_major_info_code` (`major_code`),
  KEY `idx_major_info_name` (`major_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专业信息表';

CREATE TABLE IF NOT EXISTS `volunteer_plan` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `province` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '省份代码',
  `candidate_type` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '考生类别',
  `subject_type` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '科类',
  `selected_subjects_json` JSON NULL COMMENT '选科JSON',
  `score` SMALLINT NOT NULL DEFAULT 0 COMMENT '分数',
  `rank` INT NOT NULL DEFAULT 0 COMMENT '位次',
  `batch_code` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '批次代码',
  `risk_preference` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '风险偏好',
  `policy_rule_id` BIGINT DEFAULT NULL COMMENT '政策规则ID',
  `ml_model_version` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '模型版本',
  `total_count` INT NOT NULL DEFAULT 0 COMMENT '志愿数量',
  `status` VARCHAR(30) NOT NULL DEFAULT 'generated' COMMENT '状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_volunteer_plan_user` (`user_id`, `created_at`),
  KEY `idx_volunteer_plan_policy` (`province`, `year`, `batch_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案主表';

CREATE TABLE IF NOT EXISTS `volunteer_plan_item` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `plan_id` BIGINT NOT NULL COMMENT '方案ID',
  `volunteer_index` INT NOT NULL DEFAULT 0 COMMENT '志愿序号',
  `gradient` VARCHAR(10) NOT NULL DEFAULT '' COMMENT '冲稳保垫',
  `school_code` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '院校代码',
  `school_name` VARCHAR(160) NOT NULL DEFAULT '' COMMENT '院校名称',
  `major_code` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '专业代码',
  `major_name` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '专业名称',
  `school_city` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '城市',
  `tuition` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '学费',
  `candidate_rank` INT NOT NULL DEFAULT 0 COMMENT '考生位次',
  `predicted_min_rank` INT DEFAULT NULL COMMENT '预测参考位次',
  `rank_diff` INT DEFAULT NULL COMMENT '预测参考位次-考生位次',
  `internal_score` DECIMAL(8,4) DEFAULT NULL COMMENT '内部机会分，仅后端使用',
  `chance_score` INT NOT NULL DEFAULT 0 COMMENT '机会指数',
  `chance_level` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '机会等级',
  `risk_level` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '风险等级',
  `confidence_level` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '参考度等级',
  `data_confidence` DECIMAL(6,2) DEFAULT NULL COMMENT '数据参考度',
  `final_score` DECIMAL(8,2) DEFAULT NULL COMMENT '最终排序分',
  `recommend_reason_json` JSON NULL COMMENT '推荐理由',
  `risk_warning_json` JSON NULL COMMENT '风险提示',
  `model_feature_contribution_json` JSON NULL COMMENT '特征贡献',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_volunteer_plan_item_index` (`plan_id`, `volunteer_index`),
  KEY `idx_volunteer_plan_item_school` (`school_code`, `major_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案明细表';

CREATE TABLE IF NOT EXISTS `volunteer_ai_analysis` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `plan_id` BIGINT NOT NULL COMMENT '方案ID',
  `user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
  `analysis_status` VARCHAR(30) NOT NULL DEFAULT 'pending' COMMENT '状态',
  `conclusion_text` TEXT NULL COMMENT '结论',
  `gradient_summary_json` JSON NULL COMMENT '梯度摘要',
  `key_keep_items_json` JSON NULL COMMENT '保留项',
  `high_risk_items_json` JSON NULL COMMENT '风险项',
  `diagnosis_text` MEDIUMTEXT NULL COMMENT '诊断正文',
  `action_steps_json` JSON NULL COMMENT '行动步骤',
  `sanitized_analysis_text` MEDIUMTEXT NULL COMMENT '合规后正文',
  `sensitive_words_json` JSON NULL COMMENT '命中词',
  `compliance_status` VARCHAR(30) NOT NULL DEFAULT 'pending' COMMENT '合规状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_volunteer_ai_analysis_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 深度解读结果表';

CREATE TABLE IF NOT EXISTS `skills_source` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `source_name` VARCHAR(160) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_type` VARCHAR(30) NOT NULL DEFAULT 'local' COMMENT 'github/local/admin_upload',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '来源URL',
  `local_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '本地路径',
  `branch` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '分支',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `last_sync_time` DATETIME DEFAULT NULL COMMENT '最后同步时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_skills_source_enabled` (`enabled`, `source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='skills 来源表';

CREATE TABLE IF NOT EXISTS `skills_document` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `source_id` BIGINT NOT NULL DEFAULT 0 COMMENT '来源ID',
  `doc_key` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '文档键',
  `title` VARCHAR(300) NOT NULL DEFAULT '' COMMENT '标题',
  `file_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '文件路径',
  `content_hash` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '内容hash',
  `raw_content` MEDIUMTEXT NULL COMMENT '原文',
  `sanitized_content` MEDIUMTEXT NULL COMMENT '合规后原文',
  `tags_json` JSON NULL COMMENT '标签',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_skills_document_key` (`source_id`, `doc_key`),
  KEY `idx_skills_document_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='skills 文档表';

CREATE TABLE IF NOT EXISTS `skills_chunk` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `document_id` BIGINT NOT NULL COMMENT '文档ID',
  `chunk_index` INT NOT NULL DEFAULT 0 COMMENT '切片序号',
  `chunk_text` TEXT NULL COMMENT '切片文本',
  `embedding_vector` JSON NULL COMMENT 'embedding 向量 JSON',
  `tags_json` JSON NULL COMMENT '标签',
  `token_count` INT NOT NULL DEFAULT 0 COMMENT '估算 token 数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_skills_chunk_index` (`document_id`, `chunk_index`),
  FULLTEXT KEY `ft_skills_chunk_text` (`chunk_text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='skills 切片表';

CREATE TABLE IF NOT EXISTS `skills_query_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `plan_id` BIGINT NOT NULL DEFAULT 0 COMMENT '方案ID',
  `user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
  `question` TEXT NULL COMMENT '问题',
  `retrieved_chunks_json` JSON NULL COMMENT '召回切片',
  `raw_answer` MEDIUMTEXT NULL COMMENT '原始回答',
  `sanitized_answer` MEDIUMTEXT NULL COMMENT '合规后回答',
  `compliance_status` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '合规状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_skills_query_plan` (`plan_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='skills 问答日志';

CREATE TABLE IF NOT EXISTS `ai_chat_message` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `plan_id` BIGINT NOT NULL DEFAULT 0 COMMENT '方案ID',
  `user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
  `role` VARCHAR(20) NOT NULL DEFAULT '' COMMENT 'user/assistant/system',
  `content` MEDIUMTEXT NULL COMMENT '内容',
  `sanitized_content` MEDIUMTEXT NULL COMMENT '合规后内容',
  `source_type` VARCHAR(40) NOT NULL DEFAULT '' COMMENT 'deep_analysis/skills_qa',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_ai_chat_plan` (`plan_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 追问消息表';

CREATE TABLE IF NOT EXISTS `compliance_sensitive_word` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `word` VARCHAR(120) NOT NULL DEFAULT '' COMMENT '敏感词',
  `word_type` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '类型',
  `severity` VARCHAR(20) NOT NULL DEFAULT 'medium' COMMENT '严重级别',
  `replacement` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '替换词',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_compliance_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合规敏感词表';

CREATE TABLE IF NOT EXISTS `ai_analysis_compliance_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `business_type` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '业务类型',
  `business_id` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '业务ID',
  `raw_text` MEDIUMTEXT NULL COMMENT '原文',
  `sanitized_text` MEDIUMTEXT NULL COMMENT '合规后文本',
  `hit_words_json` JSON NULL COMMENT '命中词',
  `action` VARCHAR(20) NOT NULL DEFAULT 'pass' COMMENT 'pass/replace/block',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_ai_compliance_business` (`business_type`, `business_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 合规审查日志';

CREATE TABLE IF NOT EXISTS `ml_model_registry` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `model_name` VARCHAR(120) NOT NULL DEFAULT '' COMMENT '模型名称',
  `model_type` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '模型类型',
  `model_version` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '模型版本',
  `train_year_range` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '训练年份范围',
  `train_data_count` INT NOT NULL DEFAULT 0 COMMENT '训练样本数',
  `feature_schema_json` JSON NULL COMMENT '特征结构',
  `metrics_json` JSON NULL COMMENT '评估指标',
  `model_file_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '模型文件路径',
  `status` VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT 'draft/active/archived',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `activated_at` DATETIME DEFAULT NULL COMMENT '启用时间',
  UNIQUE KEY `uk_ml_model_version` (`model_name`, `model_version`),
  KEY `idx_ml_model_status` (`model_name`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器学习模型注册表';

INSERT INTO `policy_rule_config`
(`province`, `year`, `candidate_type`, `batch_code`, `batch_name`, `volunteer_mode`, `max_volunteer_count`,
 `major_per_school_count`, `has_adjustment`, `filing_principle`, `admission_order`, `policy_status`,
 `official_source_title`, `official_source_url`, `official_source_text`, `enabled`)
VALUES
('GZ', 2025, '普通类', 'NORMAL_UNDERGRADUATE', '普通类本科批', '专业类平行志愿', 96, 0, 0,
 '分数优先、遵循志愿、一轮投档', '按考生志愿顺序逐项检索', 'confirmed',
 '贵州省2025年普通高校招生工作规定', 'https://zsksy.guizhou.gov.cn/', '以贵州省招生考试院当年正式文件为准。', 1),
('GZ', 2025, '普通类', 'NORMAL_SPECIALTY', '普通类高职（专科）批', '专业类平行志愿', 96, 0, 0,
 '分数优先、遵循志愿、一轮投档', '按考生志愿顺序逐项检索', 'confirmed',
 '贵州省2025年普通高校招生工作规定', 'https://zsksy.guizhou.gov.cn/', '以贵州省招生考试院当年正式文件为准。', 1),
('GZ', 2025, '普通类', 'EARLY_C', '普通类本科提前批C段', '专业类平行志愿', 60, 0, 0,
 '分数优先、遵循志愿、一轮投档', '按考生志愿顺序逐项检索', 'confirmed',
 '贵州省2025年普通高校招生工作规定', 'https://zsksy.guizhou.gov.cn/', '以贵州省招生考试院当年正式文件为准。', 1),
('GZ', 2025, '普通类', 'EARLY_A_B', '普通类本科提前批A/B段', '院校顺序志愿', 1, 6, 1,
 '院校顺序志愿', '按院校顺序投档并结合专业志愿与调剂信息', 'confirmed',
 '贵州省2025年普通高校招生工作规定', 'https://zsksy.guizhou.gov.cn/', '以贵州省招生考试院当年正式文件为准。', 1)
ON DUPLICATE KEY UPDATE
  `batch_name` = VALUES(`batch_name`),
  `volunteer_mode` = VALUES(`volunteer_mode`),
  `max_volunteer_count` = VALUES(`max_volunteer_count`),
  `major_per_school_count` = VALUES(`major_per_school_count`),
  `has_adjustment` = VALUES(`has_adjustment`),
  `filing_principle` = VALUES(`filing_principle`),
  `admission_order` = VALUES(`admission_order`),
  `policy_status` = VALUES(`policy_status`),
  `official_source_title` = VALUES(`official_source_title`),
  `official_source_url` = VALUES(`official_source_url`),
  `official_source_text` = VALUES(`official_source_text`),
  `enabled` = VALUES(`enabled`),
  `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `compliance_sensitive_word` (`word`, `word_type`, `severity`, `replacement`, `enabled`) VALUES
('保证录取', 'admission_commitment', 'high', '仅作为辅助参考', 1),
('保录', 'admission_commitment', 'high', '仅作为辅助参考', 1),
('包录取', 'admission_commitment', 'high', '不可承诺录取结果', 1),
('包上', 'admission_commitment', 'high', '不可承诺录取结果', 1),
('稳上', 'admission_commitment', 'high', '风险相对较低', 1),
('必上', 'admission_commitment', 'high', '具备一定参考优势', 1),
('必录', 'admission_commitment', 'high', '具备一定参考优势', 1),
('100%录取', 'admission_commitment', 'high', '机会指数较高', 1),
('百分百录取', 'admission_commitment', 'high', '机会指数较高', 1),
('一定能上', 'admission_commitment', 'high', '具备一定参考优势', 1),
('一定录取', 'admission_commitment', 'high', '具备一定参考优势', 1),
('绝对安全', 'absolute_safety', 'high', '风险相对较低，但仍需谨慎参考', 1),
('没有风险', 'absolute_safety', 'high', '风险相对较低，但仍需谨慎参考', 1),
('零风险', 'absolute_safety', 'high', '风险相对较低，但仍需谨慎参考', 1),
('保证不滑档', 'admission_commitment', 'high', '仍需关注整体风险', 1),
('确保录取', 'admission_commitment', 'high', '仅作为辅助参考', 1),
('铁定录取', 'admission_commitment', 'high', '仅作为辅助参考', 1),
('稳了', 'admission_commitment', 'medium', '风险相对较低', 1),
('闭眼报', 'unsafe_advice', 'high', '仍需逐条复核', 1),
('随便报都能上', 'unsafe_advice', 'high', '仍需结合官方数据谨慎参考', 1),
('录取概率', 'unsafe_metric', 'high', '机会指数', 1),
('上岸概率', 'unsafe_metric', 'high', '机会指数', 1),
('命中率', 'unsafe_metric', 'medium', '参考匹配度', 1)
ON DUPLICATE KEY UPDATE
  `word_type` = VALUES(`word_type`),
  `severity` = VALUES(`severity`),
  `replacement` = VALUES(`replacement`),
  `enabled` = VALUES(`enabled`),
  `updated_at` = CURRENT_TIMESTAMP;
