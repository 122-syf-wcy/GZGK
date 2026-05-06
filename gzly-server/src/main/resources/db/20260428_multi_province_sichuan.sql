-- 多省架构与四川普通本科批B段院校专业组首版。
-- 旧贵州表继续保留；四川及后续省份写入通用表。

SET @table_schema := DATABASE();

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'province_code'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `province_code` VARCHAR(10) NOT NULL DEFAULT ''GZ'' COMMENT ''省份代码 GZ/SC'' AFTER `client_ip`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'volunteer_unit_type'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `volunteer_unit_type` VARCHAR(40) NOT NULL DEFAULT ''MAJOR_96'' COMMENT ''志愿单位类型'' AFTER `province_code`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND column_name = 'target_batch'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD COLUMN `target_batch` VARCHAR(50) NOT NULL DEFAULT ''普通本科批'' COMMENT ''目标批次'' AFTER `volunteer_unit_type`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @table_schema AND table_name = 'biz_plan_history'
    AND index_name = 'idx_province_created'
);
SET @sql := IF(@idx_exists > 0,
  'SELECT 1',
  'ALTER TABLE `biz_plan_history` ADD INDEX `idx_province_created` (`province_code`, `created_at`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `data_score_rank` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `province_code` VARCHAR(10) NOT NULL COMMENT '省份代码，如 GZ/SC',
  `province_name` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '省份名称',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `subject_type` VARCHAR(10) NOT NULL COMMENT '科类(物理类/历史类/理科/文科)',
  `score` SMALLINT NOT NULL COMMENT '分数',
  `score_label` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '分数段标签',
  `segment_count` INT NOT NULL DEFAULT 0 COMMENT '本段人数',
  `cumulative_count` INT NOT NULL DEFAULT 0 COMMENT '累计人数',
  `cumulative_rate` DECIMAL(7,3) DEFAULT NULL COMMENT '累计比例%',
  `rank_low` INT NOT NULL COMMENT '同分最好位次',
  `rank_high` INT NOT NULL COMMENT '同分保守位次',
  `source_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '来源链接/文件',
  `source_page_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_file` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '来源文件名',
  `parse_method` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_province_year_subject_score` (`province_code`, `year`, `subject_type`, `score`),
  KEY `idx_province_year_subject_score` (`province_code`, `year`, `subject_type`, `score`),
  KEY `idx_province_year_subject_rank` (`province_code`, `year`, `subject_type`, `rank_low`, `rank_high`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多省官方一分一段表';

CREATE TABLE IF NOT EXISTS `data_admission_group_line` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `province_code` VARCHAR(10) NOT NULL COMMENT '省份代码',
  `province_name` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '省份名称',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `school_id` VARCHAR(20) NOT NULL COMMENT '院校ID',
  `university_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '院校名称',
  `group_code` VARCHAR(50) NOT NULL COMMENT '院校专业组代码',
  `group_name` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '院校专业组名称',
  `subject_type` VARCHAR(10) NOT NULL COMMENT '物理类/历史类',
  `first_subject_requirement` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '首选科目要求',
  `resubject_requirement` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '再选科目要求',
  `min_score` SMALLINT DEFAULT NULL COMMENT '最低调档分/投档分',
  `min_rank` INT DEFAULT NULL COMMENT '最低调档位次/投档位次',
  `rank_source_type` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '位次来源类型 original/score_rank_converted/missing',
  `rank_source_note` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '位次来源说明',
  `rank_source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '位次来源链接',
  `rank_source_page_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '位次来源发布页面',
  `plan_count` INT DEFAULT NULL COMMENT '计划数',
  `batch` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '批次',
  `source_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_page_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_level` VARCHAR(30) NOT NULL DEFAULT '' COMMENT 'official/school/manual_verified',
  `parse_method` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_group_line` (`province_code`, `year`, `school_id`, `group_code`, `subject_type`, `batch`),
  KEY `idx_group_rank` (`province_code`, `year`, `subject_type`, `min_rank`),
  KEY `idx_group_school` (`province_code`, `school_id`, `group_code`, `subject_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多省院校专业组投档/调档线';

CREATE TABLE IF NOT EXISTS `data_admission_group_plan` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `province_code` VARCHAR(10) NOT NULL COMMENT '省份代码',
  `province_name` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '省份名称',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `school_id` VARCHAR(20) NOT NULL COMMENT '院校ID',
  `university_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '院校名称',
  `group_code` VARCHAR(50) NOT NULL COMMENT '院校专业组代码',
  `group_name` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '院校专业组名称',
  `major_code` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '专业代码',
  `major_name` VARCHAR(200) NOT NULL COMMENT '专业名称',
  `subject_type` VARCHAR(10) NOT NULL COMMENT '物理类/历史类',
  `first_subject_requirement` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '首选科目要求',
  `resubject_requirement` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '再选科目要求',
  `plan_count` INT DEFAULT NULL COMMENT '计划数',
  `tuition` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '学费',
  `study_years` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '学制',
  `batch` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '批次',
  `source_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_page_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_level` VARCHAR(30) NOT NULL DEFAULT '' COMMENT 'official/school/manual_verified',
  `parse_method` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_group_plan_major` (`province_code`, `year`, `school_id`, `group_code`, `subject_type`, `major_code`),
  KEY `idx_group_plan` (`province_code`, `year`, `school_id`, `group_code`),
  KEY `idx_group_plan_subject` (`province_code`, `year`, `subject_type`, `resubject_requirement`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多省院校专业组招生计划';

CREATE TABLE IF NOT EXISTS `data_major_requirement` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `province_code` VARCHAR(10) NOT NULL COMMENT '省份代码',
  `province_name` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '省份名称',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `school_id` VARCHAR(20) NOT NULL COMMENT '院校ID',
  `university_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '院校名称',
  `group_code` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '院校专业组代码',
  `major_code` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '专业代码',
  `major_name` VARCHAR(200) NOT NULL COMMENT '专业名称',
  `subject_type` VARCHAR(10) NOT NULL COMMENT '科类',
  `first_subject_requirement` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '首选科目要求',
  `resubject_requirement` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '再选科目要求',
  `requirement_text` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方原文',
  `source_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_page_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_level` VARCHAR(30) NOT NULL DEFAULT '' COMMENT 'official/school/manual_verified',
  `parse_method` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_major_requirement` (`province_code`, `year`, `school_id`, `group_code`, `major_name`, `subject_type`),
  KEY `idx_major_requirement` (`province_code`, `year`, `subject_type`, `resubject_requirement`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多省专业/专业组选科要求';

CREATE TABLE IF NOT EXISTS `data_source_registry` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `province_code` VARCHAR(10) NOT NULL COMMENT '省份代码',
  `province_name` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '省份名称',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `subject_type` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '科类',
  `data_type` VARCHAR(50) NOT NULL COMMENT 'score_rank/group_line/group_plan/requirement',
  `source_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_page_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '来源文件/图片/网页',
  `parse_method` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `status` VARCHAR(30) NOT NULL DEFAULT 'pending' COMMENT 'pending/imported/manual_review/failed',
  `row_count` INT NOT NULL DEFAULT 0 COMMENT '导入行数',
  `notes` TEXT NULL COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_source_registry` (`province_code`, `data_type`, `year`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多省数据来源登记';
