-- GZLY 2026 official import job admin tables.
-- Safety boundary: these tables record dry-run/staging/quality packages only.
-- They do not write formal admission tables, do not promote data, and do not activate recommendation.

CREATE TABLE IF NOT EXISTS `official_import_job` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `job_id` VARCHAR(80) NOT NULL COMMENT '任务ID',
  `province_code` VARCHAR(16) NOT NULL DEFAULT 'GZ' COMMENT '省份代码',
  `year` INT NOT NULL DEFAULT 2026 COMMENT '招生年份',
  `data_type` VARCHAR(80) NOT NULL COMMENT 'SCORE_SEGMENT/ADMISSION_PLAN/POLICY_RULE/MAJOR_REQUIREMENT/MAJOR_META/SCORE_LINE/ART_SPORTS_RULE/SPECIAL_ELIGIBILITY',
  `import_batch_id` VARCHAR(140) NOT NULL COMMENT '导入批次ID',
  `source_file` VARCHAR(240) NOT NULL DEFAULT '' COMMENT '官方来源文件名',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方来源URL',
  `source_manifest` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方来源清单路径或URL',
  `raw_text` MEDIUMTEXT NULL COMMENT '官方原文片段或人工登记说明',
  `status` VARCHAR(40) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/FILE_REGISTERED/STAGING_READY/QUALITY_CHECKED/FORMAL_SQL_GENERATED/WAITING_CONFIRMATION/PROMOTED/ROLLBACK_READY/FAILED',
  `current_step` VARCHAR(80) NOT NULL DEFAULT 'CREATED' COMMENT '当前步骤',
  `total_rows` INT NOT NULL DEFAULT 0 COMMENT '总行数',
  `clean_rows` INT NOT NULL DEFAULT 0 COMMENT '通过行数',
  `review_rows` INT NOT NULL DEFAULT 0 COMMENT '需复核行数',
  `error_rows` INT NOT NULL DEFAULT 0 COMMENT '错误行数',
  `quality_report_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '质量报告路径',
  `formal_sql_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '正式导入确认SQL路径',
  `rollback_sql_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '回滚SQL路径',
  `message` VARCHAR(800) NOT NULL DEFAULT '' COMMENT '最近一次执行说明',
  `created_by` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_official_import_job_id` (`job_id`),
  UNIQUE KEY `uk_official_import_batch_id` (`import_batch_id`),
  KEY `idx_official_import_job_year_type` (`province_code`, `year`, `data_type`),
  KEY `idx_official_import_job_status` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='2026官方数据导入任务';

CREATE TABLE IF NOT EXISTS `official_import_job_file` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `job_id` VARCHAR(80) NOT NULL COMMENT '任务ID',
  `import_batch_id` VARCHAR(140) NOT NULL COMMENT '导入批次ID',
  `source_file` VARCHAR(240) NOT NULL DEFAULT '' COMMENT '官方来源文件名',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方来源URL',
  `source_manifest` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方来源清单路径或URL',
  `file_hash` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '官方文件hash',
  `raw_text` MEDIUMTEXT NULL COMMENT '官方原文片段',
  `status` VARCHAR(40) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED/STAGED/REJECTED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_import_job_file_job` (`job_id`),
  KEY `idx_import_job_file_batch` (`import_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='2026官方数据导入任务来源文件';

CREATE TABLE IF NOT EXISTS `official_import_quality_report` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `job_id` VARCHAR(80) NOT NULL COMMENT '任务ID',
  `import_batch_id` VARCHAR(140) NOT NULL COMMENT '导入批次ID',
  `data_type` VARCHAR(80) NOT NULL COMMENT '数据类型',
  `report_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '质量报告路径',
  `gate_status` VARCHAR(40) NOT NULL DEFAULT 'DRY_RUN' COMMENT 'DRY_RUN/PASSED/FAILED',
  `summary` VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '摘要',
  `total_rows` INT NOT NULL DEFAULT 0 COMMENT '总行数',
  `clean_rows` INT NOT NULL DEFAULT 0 COMMENT '通过行数',
  `review_rows` INT NOT NULL DEFAULT 0 COMMENT '需复核行数',
  `error_rows` INT NOT NULL DEFAULT 0 COMMENT '错误行数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_quality_report_job` (`job_id`),
  KEY `idx_quality_report_batch` (`import_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='2026官方数据导入质量报告';

CREATE TABLE IF NOT EXISTS `official_import_rollback_plan` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `job_id` VARCHAR(80) NOT NULL COMMENT '任务ID',
  `import_batch_id` VARCHAR(140) NOT NULL COMMENT '导入批次ID',
  `rollback_sql_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '回滚SQL路径',
  `summary` VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '回滚说明',
  `executable` TINYINT NOT NULL DEFAULT 0 COMMENT '本轮是否可执行，默认不可执行',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_rollback_plan_job` (`job_id`),
  KEY `idx_rollback_plan_batch` (`import_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='2026官方数据导入回滚计划';

CREATE TABLE IF NOT EXISTS `data_year_readiness` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `province_code` VARCHAR(16) NOT NULL COMMENT '省份代码',
  `year` INT NOT NULL COMMENT '招生年份',
  `policy_ready` TINYINT NOT NULL DEFAULT 0 COMMENT '政策是否就绪',
  `score_segment_ready` TINYINT NOT NULL DEFAULT 0 COMMENT '一分一段是否就绪',
  `admission_plan_ready` TINYINT NOT NULL DEFAULT 0 COMMENT '招生计划是否就绪',
  `major_requirement_ready` TINYINT NOT NULL DEFAULT 0 COMMENT '选科要求是否就绪',
  `major_meta_ready` TINYINT NOT NULL DEFAULT 0 COMMENT '专业限制元数据是否就绪',
  `ml_training_ready` TINYINT NOT NULL DEFAULT 0 COMMENT '模型重训是否就绪',
  `historical_training_ready` TINYINT NOT NULL DEFAULT 1 COMMENT '历史训练数据是否就绪',
  `recommendation_phase` VARCHAR(40) NOT NULL DEFAULT 'PRE_OFFICIAL_DATA' COMMENT '推荐阶段',
  `latest_import_batch_id` VARCHAR(140) DEFAULT NULL COMMENT '最近导入批次',
  `remarks` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `last_checked_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近检查时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_data_year_readiness_province_year` (`province_code`, `year`),
  KEY `idx_data_year_readiness_phase` (`recommendation_phase`),
  KEY `idx_data_year_readiness_checked_at` (`last_checked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招生年份数据就绪状态';
