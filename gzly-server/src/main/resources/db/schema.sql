-- =============================================
-- GZLY 贵州高考志愿智能填报系统 - 数据库初始化
-- MySQL 8.0 + utf8mb4
-- =============================================

CREATE DATABASE IF NOT EXISTS `gzly` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `gzly`;

-- 1. 院校信息表
CREATE TABLE IF NOT EXISTS `sys_university` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL COMMENT '掌上高考院校ID',
  `name` VARCHAR(100) NOT NULL COMMENT '院校名称',
  `province` VARCHAR(20) DEFAULT '' COMMENT '省份',
  `city` VARCHAR(50) DEFAULT '' COMMENT '城市',
  `level` VARCHAR(20) DEFAULT '' COMMENT '层次(985/211/双一流/普通本科)',
  `type_name` VARCHAR(20) DEFAULT '' COMMENT '类型(综合/理工/师范等)',
  `tags` JSON COMMENT '标签数组',
  `f985` TINYINT DEFAULT 0,
  `f211` TINYINT DEFAULT 0,
  `dual_class` TINYINT DEFAULT 0 COMMENT '双一流',
  `nature_name` VARCHAR(20) DEFAULT '' COMMENT '公办/民办',
  `belong` VARCHAR(100) DEFAULT '' COMMENT '隶属',
  `logo_url` VARCHAR(500) DEFAULT '' COMMENT '校徽URL',
  `school_site` VARCHAR(200) DEFAULT '' COMMENT '官网',
  `phone` VARCHAR(100) DEFAULT '' COMMENT '招生电话',
  `email` VARCHAR(100) DEFAULT '' COMMENT '招生邮箱',
  `address` VARCHAR(300) DEFAULT '' COMMENT '地址',
  `content` TEXT COMMENT '简介',
  `qa_disabled` TINYINT DEFAULT 0 COMMENT '问答是否关闭 0=否 1=是',
  `qa_disabled_reason` VARCHAR(200) DEFAULT '' COMMENT '问答关闭原因',
  `qa_disabled_until` DATETIME DEFAULT NULL COMMENT '问答关闭截止时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_school_id` (`school_id`),
  KEY `idx_name` (`name`),
  KEY `idx_province` (`province`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='院校信息表';

-- 2. 贵州历年投档分数线（算法基石）
CREATE TABLE IF NOT EXISTS `data_score_line_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL COMMENT '院校ID',
  `university_name` VARCHAR(100) NOT NULL COMMENT '院校名称',
  `major_name` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '专业名称/招生类型',
  `major_id` VARCHAR(20) DEFAULT '' COMMENT '专业ID',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `subject_type` VARCHAR(10) NOT NULL COMMENT '科类(物理类/历史类/理科/文科)',
  `min_score` INT DEFAULT NULL COMMENT '最低分',
  `max_score` INT DEFAULT NULL COMMENT '最高分',
  `avg_score` INT DEFAULT NULL COMMENT '平均分',
  `min_rank` INT DEFAULT NULL COMMENT '最低位次(核心测算依据)',
  `plan_count` INT DEFAULT NULL COMMENT '招生计划数',
  `batch` VARCHAR(50) DEFAULT '本科批' COMMENT '批次',
  `resubject_requirement` VARCHAR(100) DEFAULT '' COMMENT '再选科目要求(化学/不限等)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_school` (`school_id`),
  KEY `idx_uni_name` (`university_name`),
  KEY `idx_year_subject` (`year`, `subject_type`),
  KEY `idx_year_subject_resub` (`year`, `subject_type`, `resubject_requirement`),
  KEY `idx_min_rank` (`min_rank`),
  KEY `idx_batch` (`batch`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州历年投档分数线';

-- 2.1 贵州官方一分一段表
CREATE TABLE IF NOT EXISTS `data_score_rank_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `year` SMALLINT NOT NULL COMMENT '年份',
  `province` VARCHAR(20) NOT NULL DEFAULT '贵州' COMMENT '省份',
  `subject_type` VARCHAR(10) NOT NULL COMMENT '首选科目类别(物理类/历史类)',
  `score` SMALLINT NOT NULL COMMENT '分数',
  `score_label` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '原始分数段标签，如683及以上',
  `segment_count` INT NOT NULL DEFAULT 0 COMMENT '本段人数',
  `cumulative_count` INT NOT NULL COMMENT '累计人数(该分及以上)',
  `cumulative_rate` DECIMAL(7,3) DEFAULT NULL COMMENT '累计比例%',
  `rank_low` INT NOT NULL COMMENT '同分最好位次，累计人数-本段人数+1',
  `rank_high` INT NOT NULL COMMENT '同分保守位次，累计人数',
  `source_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '数据来源名称',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_page_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_file` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '来源文件名',
  `parse_method` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_year_subject_score` (`year`, `subject_type`, `score`),
  KEY `idx_year_subject_score` (`year`, `subject_type`, `score`),
  KEY `idx_year_subject_rank` (`year`, `subject_type`, `rank_low`, `rank_high`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州官方一分一段表';

-- 2.2 贵州官方专业选科要求库
CREATE TABLE IF NOT EXISTS `data_major_requirement_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `year` SMALLINT NOT NULL COMMENT '年份',
  `school_id` VARCHAR(20) NOT NULL COMMENT '院校ID',
  `university_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '院校名称',
  `major_id` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '专业/专业组代码',
  `major_name` VARCHAR(200) NOT NULL COMMENT '专业名称',
  `subject_type` VARCHAR(10) NOT NULL COMMENT '首选科目类别(物理类/历史类)',
  `first_subject_requirement` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '首选科目要求',
  `resubject_requirement` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '再选科目要求(不限/化学/化学和生物等)',
  `requirement_text` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方原文或解析备注',
  `source_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '数据来源名称',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_file` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '来源文件名',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_year_school_major_subject` (`year`, `school_id`, `major_name`, `subject_type`),
  KEY `idx_school_major_subject` (`school_id`, `major_name`, `subject_type`),
  KEY `idx_year_subject_requirement` (`year`, `subject_type`, `resubject_requirement`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州官方专业选科要求库';

-- 2.3 多省通用官方一分一段表
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

-- 2.4 多省院校专业组调档线/投档线
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

-- 2.5 多省院校专业组招生计划
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

-- 2.6 多省专业/专业组选科要求
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

-- 2.7 多省数据来源登记
CREATE TABLE IF NOT EXISTS `data_source_registry` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `province_code` VARCHAR(10) NOT NULL COMMENT '省份代码',
  `province_name` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '省份名称',
  `year` SMALLINT NOT NULL COMMENT '年份',
  `subject_type` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '科类',
  `data_type` VARCHAR(50) NOT NULL COMMENT 'score_rank/group_line/group_plan/requirement',
  `batch` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '批次',
  `source_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_page_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_url` TEXT NULL COMMENT '来源文件/图片/网页',
  `source_level` VARCHAR(30) NOT NULL DEFAULT '' COMMENT 'official/school/manual_verified',
  `source_hash` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '来源内容SHA-256或复核批次哈希',
  `parse_method` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `status` VARCHAR(30) NOT NULL DEFAULT 'pending' COMMENT 'pending/imported/manual_review/failed',
  `row_count` INT NOT NULL DEFAULT 0 COMMENT '导入行数',
  `last_checked_at` DATETIME DEFAULT NULL COMMENT '最近检查时间',
  `notes` TEXT NULL COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_source_registry` (`province_code`, `data_type`, `year`, `status`),
  UNIQUE KEY `uk_source_registry_identity` (`province_code`, `year`, `subject_type`, `data_type`, `batch`, `source_page_url`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多省数据来源登记';

-- 3. 卡密授权表
CREATE TABLE IF NOT EXISTS `biz_card_key` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `card_key` VARCHAR(32) NOT NULL COMMENT '卡密字符串',
  `face_value` INT NOT NULL DEFAULT 1 COMMENT '面值次数',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未使用 1=已激活 2=已用完',
  `user_id` BIGINT DEFAULT NULL COMMENT '绑定用户ID',
  `activate_time` DATETIME DEFAULT NULL COMMENT '激活时间',
  `batch_no` VARCHAR(50) DEFAULT '' COMMENT '批次号(管理用)',
  `remark` VARCHAR(200) DEFAULT '' COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_card_key` (`card_key`),
  KEY `idx_status` (`status`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡密授权表';

-- 4. 简易用户表
CREATE TABLE IF NOT EXISTS `biz_user` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `identifier` VARCHAR(100) NOT NULL COMMENT '用户标识(设备指纹/手机号)',
  `remain_count` INT NOT NULL DEFAULT 0 COMMENT '剩余可用次数',
  `total_used` INT NOT NULL DEFAULT 0 COMMENT '累计使用次数',
  `last_active_time` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_identifier` (`identifier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简易用户表';

-- 5. 志愿方案生成记录表
CREATE TABLE IF NOT EXISTS `biz_plan_history` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `client_ip` VARCHAR(50) DEFAULT '' COMMENT '客户端IP',
  `province_code` VARCHAR(10) NOT NULL DEFAULT 'GZ' COMMENT '省份代码 GZ/SC',
  `volunteer_unit_type` VARCHAR(40) NOT NULL DEFAULT 'MAJOR_96' COMMENT '志愿单位类型',
  `target_batch` VARCHAR(50) NOT NULL DEFAULT '普通本科批' COMMENT '目标批次',
  `agreed_disclaimer` TINYINT NOT NULL DEFAULT 0 COMMENT '是否确认生成前风险告知',
  `disclaimer_version` VARCHAR(40) DEFAULT NULL COMMENT '确认的风险告知版本',
  `disclaimer_confirmed_at` DATETIME DEFAULT NULL COMMENT '风险告知确认时间',
  `total_score` INT NOT NULL COMMENT '高考总分',
  `province_rank` INT NOT NULL COMMENT '全省排位',
  `first_subject` VARCHAR(10) NOT NULL COMMENT '首选科目(物理/历史)',
  `resubjects` VARCHAR(100) DEFAULT '' COMMENT '再选科目(JSON数组)',
  `preferred_majors` VARCHAR(500) DEFAULT NULL COMMENT '意向专业(JSON)',
  `preferred_regions` VARCHAR(500) DEFAULT NULL COMMENT '意向地区(JSON)',
  `strategy_mode` VARCHAR(20) DEFAULT NULL COMMENT '方案取向',
  `decision_priority` VARCHAR(20) DEFAULT NULL COMMENT '决策优先级',
  `career_goal` VARCHAR(20) DEFAULT NULL COMMENT '长期目标',
  `tuition_budget` VARCHAR(20) DEFAULT NULL COMMENT '预算偏好',
  `accept_private` TINYINT DEFAULT NULL COMMENT '是否接受民办 0/1',
  `accept_sino_foreign` TINYINT DEFAULT NULL COMMENT '是否接受中外/港澳台合作 0/1',
  `plan_json` LONGTEXT COMMENT '生成的96志愿方案JSON',
  `item_count` INT DEFAULT 0 COMMENT '志愿数量',
  `ai_analysis` TEXT COMMENT 'AI分析内容',
  `data_quality_warning` TEXT NULL COMMENT '数据质量警告',
  `manual_review_json` MEDIUMTEXT NULL COMMENT '强制人工复核清单(JSON)',
  `metrics_json` VARCHAR(2000) NULL COMMENT '生成耗时/复核率等监控指标(JSON)',
  `request_snapshot_json` MEDIUMTEXT NULL COMMENT '完整 GenerateRequest 快照(JSON)',
  `safety_code_hash` VARCHAR(120) NULL COMMENT '方案安全码 BCrypt 哈希',
  `safety_code_fingerprint` CHAR(64) NULL COMMENT '方案安全码指纹，用于跨设备历史列表',
  `safety_code_created_at` DATETIME NULL COMMENT '安全码创建时间',
  `safety_code_version` TINYINT NOT NULL DEFAULT 1 COMMENT '安全码版本',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记 0=正常 1=已删除',
  `deleted_at` DATETIME NULL COMMENT '软删除时间',
  `deleted_by` BIGINT NULL COMMENT '软删除操作人',
  `delete_reason` VARCHAR(200) NULL COMMENT '软删除原因',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user_id` (`user_id`),
  KEY `idx_province_created` (`province_code`, `created_at`),
  KEY `idx_deleted_created` (`deleted`, `created_at`),
  KEY `idx_deleted_user_created` (`deleted`, `user_id`, `created_at`),
  KEY `idx_deleted_fp_created` (`deleted`, `safety_code_fingerprint`, `created_at`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案生成记录';

-- 5.1 专业选择规划结果表
CREATE TABLE IF NOT EXISTS `major_planner_result` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `plan_no` VARCHAR(40) NOT NULL COMMENT '规划编号',
  `plan_code_hash` VARCHAR(120) NOT NULL COMMENT '规划码 BCrypt 哈希',
  `plan_code_fingerprint` CHAR(64) NOT NULL COMMENT '规划码不可逆指纹，用于找回定位',
  `plan_code_masked` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '规划码掩码展示',
  `province_code` VARCHAR(10) DEFAULT NULL COMMENT '所在省份，可空',
  `subject_category` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '选科/科类',
  `score` INT DEFAULT NULL COMMENT '分数，可空',
  `province_rank` INT DEFAULT NULL COMMENT '位次，可空',
  `answers_json` MEDIUMTEXT NOT NULL COMMENT '问卷答案 JSON',
  `result_json` LONGTEXT NOT NULL COMMENT '规则评分结果 JSON',
  `ai_summary` MEDIUMTEXT NULL COMMENT 'AI 深度解读',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL COMMENT '软删除时间',
  UNIQUE KEY `uk_major_planner_plan_no` (`plan_no`),
  KEY `idx_major_planner_code_fp` (`plan_code_fingerprint`),
  KEY `idx_major_planner_created` (`created_at`),
  KEY `idx_major_planner_deleted_created` (`deleted_at`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专业选择规划结果表';

-- =============================================
-- 校友共建系统
-- =============================================

-- 6. 校友管理员表
CREATE TABLE IF NOT EXISTS `sys_alumni_admin` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL COMMENT '院校ID(关联sys_university)',
  `nickname` VARCHAR(50) NOT NULL COMMENT '昵称',
  `phone` VARCHAR(20) DEFAULT '' COMMENT '手机号',
  `email` VARCHAR(100) DEFAULT '' COMMENT '邮箱',
  `avatar_url` VARCHAR(500) DEFAULT '' COMMENT '头像',
  `credential_url` VARCHAR(500) DEFAULT '' COMMENT '学生证/毕业证照片URL',
  `graduation_year` SMALLINT DEFAULT NULL COMMENT '毕业年份',
  `major` VARCHAR(100) DEFAULT '' COMMENT '就读专业',
  `bio` VARCHAR(500) DEFAULT '' COMMENT '个人简介',
  `role` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待审核 1=学校管理员 9=超级管理员',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待审核 1=已通过 2=已拒绝 3=已禁用',
  `reject_reason` VARCHAR(200) DEFAULT '' COMMENT '拒绝原因',
  `password_hash` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '密码哈希',
  `last_login_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_school_id` (`school_id`),
  KEY `idx_phone` (`phone`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校友管理员';

-- 7. 大学图片/视频资源表
CREATE TABLE IF NOT EXISTS `uni_media` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL COMMENT '院校ID',
  `uploader_id` BIGINT NOT NULL COMMENT '上传者(alumni_admin.id)',
  `media_type` TINYINT NOT NULL DEFAULT 1 COMMENT '1=照片 2=资讯 3=文件 4=背景横幅',
  `url` VARCHAR(500) NOT NULL COMMENT '资源URL',
  `thumb_url` VARCHAR(500) DEFAULT '' COMMENT '缩略图URL',
  `caption` VARCHAR(200) DEFAULT '' COMMENT '图片说明',
  `sort_order` INT DEFAULT 0 COMMENT '排序(越小越前)',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待AI审核 1=已发布 2=已拒绝 3=待人工审核',
  `ai_review_result` VARCHAR(500) DEFAULT NULL COMMENT 'AI审核结果JSON',
  `review_note` VARCHAR(200) DEFAULT '' COMMENT '人工审核备注',
  `review_actor_role` VARCHAR(20) DEFAULT '' COMMENT '最终审核角色(ai/alumni/admin/system)',
  `review_actor_id` BIGINT DEFAULT NULL COMMENT '最终审核人ID',
  `reviewed_at` DATETIME DEFAULT NULL COMMENT '最终审核时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_school_status` (`school_id`, `status`),
  KEY `idx_uploader` (`uploader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大学媒体资源';

-- 8. 大学内容编辑记录表
CREATE TABLE IF NOT EXISTS `uni_content_edit` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL COMMENT '院校ID',
  `editor_id` BIGINT NOT NULL COMMENT '编辑者(alumni_admin.id)',
  `field_name` VARCHAR(50) NOT NULL COMMENT '编辑字段(content/address/phone等)',
  `old_value` TEXT COMMENT '修改前的值',
  `new_value` TEXT NOT NULL COMMENT '修改后的值',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待AI审核 1=已采纳 2=已拒绝 3=待人工审核',
  `ai_review_result` VARCHAR(500) DEFAULT NULL COMMENT 'AI审核结果JSON',
  `review_note` VARCHAR(200) DEFAULT '' COMMENT '审核备注',
  `review_actor_role` VARCHAR(20) DEFAULT '' COMMENT '最终审核角色(ai/alumni/admin/system)',
  `review_actor_id` BIGINT DEFAULT NULL COMMENT '最终审核人ID',
  `reviewed_at` DATETIME DEFAULT NULL COMMENT '最终审核时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_school` (`school_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大学内容编辑记录';

-- 9. 官方报考资料入口
CREATE TABLE IF NOT EXISTS `uni_official_link` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `school_id` VARCHAR(20) NOT NULL COMMENT '院校ID',
  `school_name` VARCHAR(100) DEFAULT '' COMMENT '院校名称',
  `source_domain` VARCHAR(200) DEFAULT '' COMMENT '来源域名',
  `school_site` VARCHAR(500) DEFAULT '' COMMENT '学校官网',
  `admission_site` VARCHAR(500) DEFAULT '' COMMENT '招生网',
  `admission_brochure_url` VARCHAR(500) DEFAULT '' COMMENT '招生章程',
  `major_catalog_url` VARCHAR(500) DEFAULT '' COMMENT '专业目录',
  `tuition_info_url` VARCHAR(500) DEFAULT '' COMMENT '收费标准',
  `tuition_remark` VARCHAR(500) DEFAULT '' COMMENT '收费备注',
  `capture_method` VARCHAR(50) DEFAULT 'manual' COMMENT 'manual/scraper',
  `capture_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待补充 1=已收录 2=待核验',
  `last_verified_at` DATETIME DEFAULT NULL COMMENT '最近人工核验时间',
  `last_captured_at` DATETIME DEFAULT NULL COMMENT '最近采集时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_school_id` (`school_id`),
  KEY `idx_status` (`capture_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='院校官方报考资料入口';

-- 10. 院校问答表
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

-- 11. 系统公告表
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

-- 12. 用户意见反馈表
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

-- 13. 考生加油留言墙
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
