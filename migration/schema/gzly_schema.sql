-- MySQL dump 10.13  Distrib 8.0.44, for Linux (x86_64)
--
-- Host: localhost    Database: gzly
-- ------------------------------------------------------
-- Server version	8.0.44

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `_bak_data_source_registry_20260515`
--

DROP TABLE IF EXISTS `_bak_data_source_registry_20260515`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `_bak_data_source_registry_20260515` (
  `id` bigint NOT NULL DEFAULT '0',
  `province_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '省份代码',
  `province_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '省份名称',
  `year` smallint NOT NULL COMMENT '年份',
  `subject_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '科类',
  `data_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'score_rank/group_line/group_plan/requirement',
  `batch` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '批次',
  `source_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_page_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '来源文件/图片/网页',
  `source_level` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT 'official/school/manual_verified',
  `source_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '来源内容SHA-256或复核批次哈希',
  `parse_method` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '解析方式',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT 'pending/imported/manual_review/failed',
  `row_count` int NOT NULL DEFAULT '0' COMMENT '导入行数',
  `last_checked_at` datetime DEFAULT NULL COMMENT '最近检查时间',
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `_bak_data_year_readiness_20260515`
--

DROP TABLE IF EXISTS `_bak_data_year_readiness_20260515`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `_bak_data_year_readiness_20260515` (
  `id` bigint NOT NULL DEFAULT '0',
  `province_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `year` int NOT NULL,
  `policy_ready` tinyint NOT NULL DEFAULT '0',
  `score_segment_ready` tinyint NOT NULL DEFAULT '0',
  `admission_plan_ready` tinyint NOT NULL DEFAULT '0',
  `major_requirement_ready` tinyint NOT NULL DEFAULT '0',
  `major_meta_ready` tinyint NOT NULL DEFAULT '0',
  `ml_training_ready` tinyint NOT NULL DEFAULT '0',
  `historical_training_ready` tinyint NOT NULL DEFAULT '1',
  `recommendation_phase` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PRE_OFFICIAL_DATA',
  `latest_import_batch_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `remarks` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `last_checked_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `_bak_major_requirement_match_gz_20260515`
--

DROP TABLE IF EXISTS `_bak_major_requirement_match_gz_20260515`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `_bak_major_requirement_match_gz_20260515` (
  `id` bigint NOT NULL DEFAULT '0',
  `history_year` smallint NOT NULL COMMENT '历史专业分年份',
  `requirement_year` smallint NOT NULL COMMENT '选科要求年份',
  `school_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '院校ID',
  `university_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '院校名称',
  `subject_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '科类',
  `history_major_name` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '历史专业名',
  `normalized_history_major_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '历史专业归一名',
  `requirement_id` bigint DEFAULT NULL COMMENT 'data_major_requirement_gz.id',
  `requirement_major_name` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '命中的选科要求专业名',
  `normalized_requirement_major_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '命中专业归一名',
  `resubject_requirement` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '再选科目要求',
  `match_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'missing' COMMENT 'exact/normalized/missing',
  `match_score` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '匹配分',
  `review_required` tinyint NOT NULL DEFAULT '1' COMMENT '是否需要人工复核',
  `reason` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '匹配说明',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `admin_import_job`
--

DROP TABLE IF EXISTS `admin_import_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_import_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `province_code` varchar(16) NOT NULL,
  `year` int NOT NULL,
  `batch_code` varchar(80) NOT NULL DEFAULT '',
  `subject_type` varchar(40) NOT NULL DEFAULT '',
  `import_type` varchar(80) NOT NULL DEFAULT '',
  `source_type` varchar(80) NOT NULL DEFAULT '',
  `status` varchar(40) NOT NULL DEFAULT 'CREATED',
  `source_dir` varchar(500) NOT NULL DEFAULT '',
  `output_dir` varchar(500) NOT NULL DEFAULT '',
  `created_by` varchar(80) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_import_job_province_year` (`province_code`,`year`),
  KEY `idx_admin_import_job_status` (`status`),
  KEY `idx_admin_import_job_updated_at` (`updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `admin_import_job_artifact`
--

DROP TABLE IF EXISTS `admin_import_job_artifact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_import_job_artifact` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint NOT NULL,
  `artifact_type` varchar(80) NOT NULL DEFAULT '',
  `artifact_path` varchar(500) NOT NULL DEFAULT '',
  `sha256` char(64) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_import_job_artifact_job` (`job_id`),
  KEY `idx_admin_import_job_artifact_type` (`artifact_type`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `admin_import_job_file`
--

DROP TABLE IF EXISTS `admin_import_job_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_import_job_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint NOT NULL,
  `file_name` varchar(255) NOT NULL DEFAULT '',
  `file_path` varchar(500) NOT NULL DEFAULT '',
  `sha256` char(64) NOT NULL DEFAULT '',
  `file_size` bigint NOT NULL DEFAULT '0',
  `file_type` varchar(80) NOT NULL DEFAULT '',
  `source_url` varchar(1000) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_import_job_file_path` (`job_id`,`file_path`),
  KEY `idx_admin_import_job_file_job` (`job_id`),
  KEY `idx_admin_import_job_file_type` (`file_type`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `admin_import_job_gate`
--

DROP TABLE IF EXISTS `admin_import_job_gate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_import_job_gate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint NOT NULL,
  `gate_name` varchar(128) NOT NULL DEFAULT '',
  `gate_status` varchar(32) NOT NULL DEFAULT '',
  `expected_value` varchar(500) NOT NULL DEFAULT '',
  `actual_value` varchar(500) NOT NULL DEFAULT '',
  `sample_path` varchar(500) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_import_job_gate_job` (`job_id`),
  KEY `idx_admin_import_job_gate_status` (`gate_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `admission_plan_import_audit`
--

DROP TABLE IF EXISTS `admission_plan_import_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admission_plan_import_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `import_batch_id` varchar(120) NOT NULL COMMENT 'staging导入批次ID',
  `formal_batch_id` varchar(140) NOT NULL DEFAULT '' COMMENT '正式表晋级批次ID',
  `year` smallint NOT NULL COMMENT '年份',
  `province` varchar(20) NOT NULL COMMENT '省份',
  `batch_code` varchar(60) NOT NULL COMMENT '批次代码',
  `candidate_type` varchar(30) NOT NULL COMMENT '考生类别',
  `subject_type` varchar(30) NOT NULL COMMENT '科类',
  `school_code` varchar(60) NOT NULL COMMENT '院校代码',
  `school_name` varchar(160) NOT NULL DEFAULT '' COMMENT '院校名称',
  `major_code` varchar(80) NOT NULL DEFAULT '' COMMENT '专业代码',
  `major_name` varchar(240) NOT NULL COMMENT '专业名称',
  `plan_count` int NOT NULL DEFAULT '0' COMMENT '计划数',
  `source_file` varchar(240) NOT NULL DEFAULT '' COMMENT '来源文件',
  `source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '来源URL',
  `source_page_url` varchar(500) NOT NULL DEFAULT '' COMMENT '来源页面URL',
  `source_page` varchar(80) NOT NULL DEFAULT '' COMMENT '来源页码',
  `raw_text` mediumtext COMMENT '官方原文片段',
  `promote_action` varchar(40) NOT NULL DEFAULT 'insert_or_update' COMMENT 'insert/update/insert_or_update',
  `promoted_by` varchar(80) NOT NULL DEFAULT '' COMMENT '晋级执行人',
  `promoted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '晋级时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_import_audit_nk` (`import_batch_id`,`year`,`province`,`batch_code`,`candidate_type`,`subject_type`,`school_code`,`major_code`,`major_name`),
  KEY `idx_plan_import_audit_formal_batch` (`formal_batch_id`),
  KEY `idx_plan_import_audit_year_batch` (`year`,`province`,`batch_code`,`candidate_type`,`subject_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招生计划正式表导入批次映射审计';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_analysis_compliance_log`
--

DROP TABLE IF EXISTS `ai_analysis_compliance_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_analysis_compliance_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `business_type` varchar(60) NOT NULL DEFAULT '' COMMENT '业务类型',
  `business_id` varchar(80) NOT NULL DEFAULT '' COMMENT '业务ID',
  `raw_text` mediumtext COMMENT '原文',
  `sanitized_text` mediumtext COMMENT '合规后文本',
  `hit_words_json` json DEFAULT NULL COMMENT '命中词',
  `action` varchar(20) NOT NULL DEFAULT 'pass' COMMENT 'pass/replace/block',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_compliance_business` (`business_type`,`business_id`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=1681 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 合规审查日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_chat_message`
--

DROP TABLE IF EXISTS `ai_chat_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plan_id` bigint NOT NULL DEFAULT '0' COMMENT '方案ID',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户ID',
  `role` varchar(20) NOT NULL DEFAULT '' COMMENT 'user/assistant/system',
  `content` mediumtext COMMENT '内容',
  `sanitized_content` mediumtext COMMENT '合规后内容',
  `source_type` varchar(40) NOT NULL DEFAULT '' COMMENT 'deep_analysis/skills_qa',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_chat_plan` (`plan_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 追问消息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_card_key`
--

DROP TABLE IF EXISTS `biz_card_key`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_card_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `card_key` varchar(40) NOT NULL COMMENT '卡密明文（32 位字母数字 + 分隔）',
  `batch_no` varchar(40) NOT NULL DEFAULT '' COMMENT '批次号，便于回收',
  `status` varchar(20) NOT NULL DEFAULT 'unused' COMMENT 'unused/active/revoked/expired',
  `user_id` bigint DEFAULT NULL COMMENT '激活后绑定的 biz_user.id',
  `secret_code_hash` varchar(120) DEFAULT NULL COMMENT '安全码 BCrypt 哈希',
  `max_plans` int NOT NULL DEFAULT '5' COMMENT '允许生成方案上限',
  `used_plans` int NOT NULL DEFAULT '0' COMMENT '已生成方案数',
  `expires_at` datetime DEFAULT NULL COMMENT '失效时间（默认创建后 90 天）',
  `activated_at` datetime DEFAULT NULL COMMENT '激活时间',
  `revoked_at` datetime DEFAULT NULL COMMENT '撤销时间',
  `revoke_reason` varchar(200) DEFAULT NULL COMMENT '撤销原因',
  `note` varchar(200) DEFAULT NULL COMMENT '运营备注（发给谁、原因）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_card_key_card` (`card_key`),
  KEY `idx_biz_card_key_status` (`status`),
  KEY `idx_biz_card_key_batch` (`batch_no`),
  KEY `idx_biz_card_key_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡密激活与安全码绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_encouragement_message`
--

DROP TABLE IF EXISTS `biz_encouragement_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_encouragement_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nickname` varchar(30) NOT NULL DEFAULT '贵州考生' COMMENT '展示昵称',
  `content` varchar(240) NOT NULL COMMENT '鼓励留言内容',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0=隐藏 1=展示',
  `ip_hash` varchar(64) DEFAULT NULL COMMENT 'IP 脱敏哈希',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_created` (`status`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考生加油留言墙';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_plan_history`
--

DROP TABLE IF EXISTS `biz_plan_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_plan_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `client_ip` varchar(50) DEFAULT '' COMMENT '客户端IP',
  `province_code` varchar(10) NOT NULL DEFAULT 'GZ' COMMENT '省份代码 GZ/SC',
  `volunteer_unit_type` varchar(40) NOT NULL DEFAULT 'MAJOR_96' COMMENT '志愿单位类型',
  `target_batch` varchar(50) NOT NULL DEFAULT '普通本科批' COMMENT '目标批次',
  `agreed_disclaimer` tinyint NOT NULL DEFAULT '0' COMMENT '是否确认生成前风险告知',
  `disclaimer_version` varchar(40) DEFAULT NULL COMMENT '确认的风险告知版本',
  `disclaimer_confirmed_at` datetime DEFAULT NULL COMMENT '风险告知确认时间',
  `total_score` int NOT NULL COMMENT '高考总分',
  `province_rank` int NOT NULL COMMENT '全省排位',
  `first_subject` varchar(10) NOT NULL DEFAULT '' COMMENT '首选科目',
  `resubjects` varchar(100) DEFAULT '' COMMENT '再选科目(JSON数组)',
  `preferred_majors` varchar(500) DEFAULT NULL COMMENT '意向专业(JSON)',
  `preferred_regions` varchar(500) DEFAULT NULL COMMENT '意向地区(JSON)',
  `strategy_mode` varchar(20) DEFAULT NULL COMMENT '方案取向',
  `decision_priority` varchar(20) DEFAULT NULL COMMENT '决策优先级',
  `career_goal` varchar(20) DEFAULT NULL COMMENT '长期目标',
  `tuition_budget` varchar(20) DEFAULT NULL COMMENT '预算偏好',
  `accept_private` tinyint DEFAULT NULL COMMENT '是否接受民办 0/1',
  `accept_sino_foreign` tinyint DEFAULT NULL COMMENT '是否接受中外/港澳台合作 0/1',
  `plan_json` longtext COMMENT '生成的96志愿方案JSON',
  `item_count` int DEFAULT '0' COMMENT '志愿数量',
  `ai_analysis` text COMMENT 'AI分析内容',
  `data_quality_warning` text COMMENT '数据质量警告',
  `manual_review_json` mediumtext COMMENT '强制人工复核清单(JSON)',
  `metrics_json` varchar(2000) DEFAULT NULL COMMENT '生成耗时/复核率等监控指标(JSON)',
  `request_snapshot_json` mediumtext COMMENT '完整 GenerateRequest 快照(JSON)',
  `safety_code_hash` varchar(120) DEFAULT NULL COMMENT '安全码 BCrypt 哈希',
  `safety_code_fingerprint` varchar(128) DEFAULT NULL COMMENT '安全码指纹(SHA-256)',
  `safety_code_created_at` datetime DEFAULT NULL COMMENT '安全码创建时间',
  `safety_code_version` int NOT NULL DEFAULT '1' COMMENT '安全码版本',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created` (`created_at`),
  KEY `idx_province_created` (`province_code`,`created_at`),
  KEY `idx_biz_plan_history_fp` (`safety_code_fingerprint`)
) ENGINE=InnoDB AUTO_INCREMENT=293 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='志愿方案生成记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_university_qa`
--

DROP TABLE IF EXISTS `biz_university_qa`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_university_qa` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `content` text NOT NULL,
  `author_name` varchar(50) DEFAULT '匿名考生',
  `author_type` enum('student','alumni','anonymous') DEFAULT 'anonymous',
  `status` tinyint DEFAULT '0',
  `ai_review_result` varchar(500) DEFAULT NULL COMMENT 'AI审核结果JSON',
  `review_note` varchar(200) DEFAULT '' COMMENT '人工审核备注',
  `review_actor_role` varchar(20) DEFAULT '' COMMENT '最终审核角色(ai/alumni/admin/system)',
  `review_actor_id` bigint DEFAULT NULL COMMENT '最终审核人ID',
  `reviewed_at` datetime DEFAULT NULL COMMENT '最终审核时间',
  `like_count` int DEFAULT '0',
  `ip_hash` varchar(64) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_school_status` (`school_id`,`status`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_user`
--

DROP TABLE IF EXISTS `biz_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `identifier` varchar(100) NOT NULL COMMENT '用户标识(设备指纹/手机号)',
  `card_key_id` bigint DEFAULT NULL COMMENT '关联 biz_card_key.id',
  `nickname` varchar(40) NOT NULL DEFAULT '' COMMENT '昵称',
  `remain_count` int NOT NULL DEFAULT '0' COMMENT '剩余可用次数',
  `total_used` int NOT NULL DEFAULT '0' COMMENT '累计使用次数',
  `last_active_time` datetime DEFAULT NULL,
  `last_login_at` datetime DEFAULT NULL COMMENT '最近登录时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identifier` (`identifier`),
  KEY `idx_biz_user_card_key` (`card_key_id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简易用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_user_feedback`
--

DROP TABLE IF EXISTS `biz_user_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_user_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL COMMENT '反馈内容',
  `source_page` varchar(120) DEFAULT '' COMMENT '来源页面',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=未读 1=已读',
  `ip_hash` varchar(64) DEFAULT NULL COMMENT 'IP 脱敏哈希',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `read_at` datetime DEFAULT NULL COMMENT '已读时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_created` (`status`,`created_at`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户意见反馈';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `compliance_sensitive_word`
--

DROP TABLE IF EXISTS `compliance_sensitive_word`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `compliance_sensitive_word` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `word` varchar(120) NOT NULL DEFAULT '' COMMENT '敏感词',
  `word_type` varchar(40) NOT NULL DEFAULT '' COMMENT '类型',
  `severity` varchar(20) NOT NULL DEFAULT 'medium' COMMENT '严重级别',
  `replacement` varchar(200) NOT NULL DEFAULT '' COMMENT '替换词',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compliance_word` (`word`)
) ENGINE=InnoDB AUTO_INCREMENT=58 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合规敏感词表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_admission_group_line`
--

DROP TABLE IF EXISTS `data_admission_group_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_admission_group_line` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `province_code` varchar(10) NOT NULL COMMENT '省份代码',
  `province_name` varchar(20) NOT NULL DEFAULT '' COMMENT '省份名称',
  `year` smallint NOT NULL COMMENT '年份',
  `school_id` varchar(20) NOT NULL COMMENT '院校ID',
  `university_name` varchar(100) NOT NULL DEFAULT '' COMMENT '院校名称',
  `group_code` varchar(50) NOT NULL COMMENT '院校专业组代码',
  `group_name` varchar(200) NOT NULL DEFAULT '' COMMENT '院校专业组名称',
  `subject_type` varchar(10) NOT NULL COMMENT '物理类/历史类',
  `first_subject_requirement` varchar(50) NOT NULL DEFAULT '' COMMENT '首选科目要求',
  `resubject_requirement` varchar(100) NOT NULL DEFAULT '' COMMENT '再选科目要求',
  `min_score` smallint DEFAULT NULL COMMENT '最低调档分/投档分',
  `min_rank` int DEFAULT NULL COMMENT '最低调档位次/投档位次',
  `rank_source_type` varchar(30) NOT NULL DEFAULT '' COMMENT '位次来源类型 original/score_rank_converted/missing',
  `rank_source_note` varchar(500) NOT NULL DEFAULT '' COMMENT '位次来源说明',
  `rank_source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '位次来源链接',
  `rank_source_page_url` varchar(500) NOT NULL DEFAULT '' COMMENT '位次来源发布页面',
  `plan_count` int DEFAULT NULL COMMENT '计划数',
  `batch` varchar(50) NOT NULL DEFAULT '' COMMENT '批次',
  `source_name` varchar(100) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_page_url` varchar(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_level` varchar(30) NOT NULL DEFAULT '' COMMENT 'official/school/manual_verified',
  `parse_method` varchar(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_line` (`province_code`,`year`,`school_id`,`group_code`,`subject_type`,`batch`),
  KEY `idx_group_rank` (`province_code`,`year`,`subject_type`,`min_rank`),
  KEY `idx_group_school` (`province_code`,`school_id`,`group_code`,`subject_type`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多省院校专业组投档/调档线';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_admission_group_plan`
--

DROP TABLE IF EXISTS `data_admission_group_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_admission_group_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `province_code` varchar(10) NOT NULL COMMENT '省份代码',
  `province_name` varchar(20) NOT NULL DEFAULT '' COMMENT '省份名称',
  `year` smallint NOT NULL COMMENT '年份',
  `school_id` varchar(20) NOT NULL COMMENT '院校ID',
  `university_name` varchar(100) NOT NULL DEFAULT '' COMMENT '院校名称',
  `group_code` varchar(50) NOT NULL COMMENT '院校专业组代码',
  `group_name` varchar(200) NOT NULL DEFAULT '' COMMENT '院校专业组名称',
  `major_code` varchar(50) NOT NULL DEFAULT '' COMMENT '专业代码',
  `major_name` varchar(200) NOT NULL COMMENT '专业名称',
  `subject_type` varchar(10) NOT NULL COMMENT '物理类/历史类',
  `first_subject_requirement` varchar(50) NOT NULL DEFAULT '' COMMENT '首选科目要求',
  `resubject_requirement` varchar(100) NOT NULL DEFAULT '' COMMENT '再选科目要求',
  `plan_count` int DEFAULT NULL COMMENT '计划数',
  `tuition` varchar(50) NOT NULL DEFAULT '' COMMENT '学费',
  `study_years` varchar(50) NOT NULL DEFAULT '' COMMENT '学制',
  `batch` varchar(50) NOT NULL DEFAULT '' COMMENT '批次',
  `source_name` varchar(100) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_page_url` varchar(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_level` varchar(30) NOT NULL DEFAULT '' COMMENT 'official/school/manual_verified',
  `parse_method` varchar(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_plan_major` (`province_code`,`year`,`school_id`,`group_code`,`subject_type`,`major_code`),
  KEY `idx_group_plan` (`province_code`,`year`,`school_id`,`group_code`),
  KEY `idx_group_plan_subject` (`province_code`,`year`,`subject_type`,`resubject_requirement`)
) ENGINE=InnoDB AUTO_INCREMENT=193 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多省院校专业组招生计划';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_admission_plan_gz`
--

DROP TABLE IF EXISTS `data_admission_plan_gz`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_admission_plan_gz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `year` smallint NOT NULL,
  `province` varchar(20) NOT NULL DEFAULT '贵州',
  `batch_code` varchar(40) NOT NULL DEFAULT '',
  `candidate_type` varchar(30) NOT NULL DEFAULT '',
  `subject_type` varchar(30) NOT NULL DEFAULT '',
  `school_id` varchar(20) DEFAULT NULL,
  `school_code` varchar(40) NOT NULL DEFAULT '',
  `school_name` varchar(160) NOT NULL DEFAULT '',
  `major_code` varchar(60) NOT NULL DEFAULT '',
  `major_name` varchar(300) NOT NULL DEFAULT '',
  `normalized_major_name` varchar(220) NOT NULL DEFAULT '',
  `selected_subject_requirement` varchar(200) DEFAULT NULL,
  `plan_count` int DEFAULT NULL,
  `tuition` varchar(80) DEFAULT NULL,
  `duration` varchar(40) DEFAULT NULL,
  `campus` varchar(160) DEFAULT NULL,
  `remarks` text,
  `special_limit` text,
  `is_chinese_foreign_coop` tinyint NOT NULL DEFAULT '0',
  `is_high_fee` tinyint NOT NULL DEFAULT '0',
  `source_file` varchar(200) NOT NULL DEFAULT '',
  `source_url` varchar(800) DEFAULT NULL,
  `source_page` int DEFAULT NULL,
  `source_column` varchar(4) DEFAULT NULL,
  `raw_text` text,
  `review_required` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `import_batch_id` varchar(80) DEFAULT NULL COMMENT '数据导入批次号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gz_plan_source_major` (`year`,`subject_type`,`batch_code`,`school_code`,`major_code`,`major_name`(120)),
  KEY `idx_gz_plan_school_major` (`school_id`,`subject_type`,`normalized_major_name`),
  KEY `idx_gz_plan_batch_subject` (`year`,`batch_code`,`subject_type`),
  KEY `idx_gz_plan_review` (`review_required`),
  KEY `idx_data_admission_plan_gz_import_batch` (`import_batch_id`)
) ENGINE=InnoDB AUTO_INCREMENT=270012 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='贵州2025招生计划目录结构化数据';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_major_meta_gz`
--

DROP TABLE IF EXISTS `data_major_meta_gz`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_major_meta_gz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `year` smallint NOT NULL COMMENT '年份',
  `province` varchar(20) NOT NULL DEFAULT '贵州',
  `school_id` varchar(20) NOT NULL COMMENT '院校ID',
  `school_code` varchar(40) NOT NULL DEFAULT '',
  `school_name` varchar(160) NOT NULL DEFAULT '',
  `university_name` varchar(100) NOT NULL DEFAULT '' COMMENT '院校名称',
  `subject_type` varchar(10) NOT NULL DEFAULT '' COMMENT '科类',
  `batch_code` varchar(40) NOT NULL DEFAULT '',
  `major_code` varchar(50) NOT NULL DEFAULT '' COMMENT '专业代码',
  `major_name` varchar(300) NOT NULL COMMENT '专业名称原文',
  `normalized_major_name` varchar(200) NOT NULL DEFAULT '' COMMENT '归一化专业名',
  `medical_limit` varchar(1000) DEFAULT NULL,
  `single_subject_rule` varchar(1000) DEFAULT NULL,
  `foreign_language_limit` varchar(1000) DEFAULT NULL,
  `gender_limit` varchar(500) DEFAULT NULL,
  `qualification_required` varchar(1000) DEFAULT NULL,
  `raw_remarks` text,
  `batch` varchar(100) NOT NULL DEFAULT '' COMMENT '批次',
  `source_table` varchar(64) NOT NULL COMMENT '来源表',
  `source_id` bigint NOT NULL DEFAULT '0' COMMENT '来源表主键',
  `raw_text` varchar(1000) NOT NULL DEFAULT '' COMMENT '用于提取的原始文本',
  `restriction_text` varchar(1000) NOT NULL DEFAULT '' COMMENT '限制/备注文本',
  `has_physical_limit` tinyint NOT NULL DEFAULT '0' COMMENT '是否存在体检/身体条件限制',
  `has_color_weakness_limit` tinyint NOT NULL DEFAULT '0' COMMENT '是否限制色弱',
  `has_color_blindness_limit` tinyint NOT NULL DEFAULT '0' COMMENT '是否限制色盲',
  `has_vision_limit` tinyint NOT NULL DEFAULT '0' COMMENT '是否限制视力/单色识别',
  `has_height_limit` tinyint NOT NULL DEFAULT '0' COMMENT '是否限制身高',
  `has_language_limit` tinyint NOT NULL DEFAULT '0' COMMENT '是否限制外语语种或单科成绩',
  `required_language` varchar(50) NOT NULL DEFAULT '' COMMENT '要求语种',
  `has_gender_limit` tinyint NOT NULL DEFAULT '0' COMMENT '是否限制性别',
  `gender_requirement` varchar(20) NOT NULL DEFAULT '' COMMENT '男/女等性别要求',
  `is_ethnic_class` tinyint NOT NULL DEFAULT '0' COMMENT '民族班',
  `is_preparatory` tinyint NOT NULL DEFAULT '0' COMMENT '预科',
  `is_targeted` tinyint NOT NULL DEFAULT '0' COMMENT '定向',
  `is_teacher_plan` tinyint NOT NULL DEFAULT '0' COMMENT '优师/公费师范等计划',
  `is_free_medical` tinyint NOT NULL DEFAULT '0' COMMENT '免费医学定向',
  `is_chinese_foreign_coop` tinyint NOT NULL DEFAULT '0' COMMENT '中外合作办学',
  `is_high_fee` tinyint NOT NULL DEFAULT '0' COMMENT '高收费/较高收费',
  `is_major_category_bundle` tinyint NOT NULL DEFAULT '0' COMMENT '专业类/包含多个专业',
  `review_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否需要人工复核',
  `extraction_version` varchar(40) NOT NULL DEFAULT 'major-meta-v1' COMMENT '提取规则版本',
  `source_file` varchar(200) NOT NULL DEFAULT '',
  `source_page` int DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_meta_source` (`source_table`,`source_id`),
  KEY `idx_major_meta_year_subject` (`year`,`subject_type`),
  KEY `idx_major_meta_school_major` (`school_id`,`normalized_major_name`),
  KEY `idx_major_meta_flags` (`has_physical_limit`,`has_language_limit`,`has_gender_limit`,`review_required`),
  KEY `idx_gz_meta_compat_school_major` (`school_id`,`subject_type`,`normalized_major_name`),
  KEY `idx_gz_meta_compat_review` (`review_required`)
) ENGINE=InnoDB AUTO_INCREMENT=67612 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='贵州专业限制与招生属性结构化元数据';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_major_requirement`
--

DROP TABLE IF EXISTS `data_major_requirement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_major_requirement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `province_code` varchar(10) NOT NULL COMMENT '省份代码',
  `province_name` varchar(20) NOT NULL DEFAULT '' COMMENT '省份名称',
  `year` smallint NOT NULL COMMENT '年份',
  `school_id` varchar(20) NOT NULL COMMENT '院校ID',
  `university_name` varchar(100) NOT NULL DEFAULT '' COMMENT '院校名称',
  `group_code` varchar(50) NOT NULL DEFAULT '' COMMENT '院校专业组代码',
  `major_code` varchar(50) NOT NULL DEFAULT '' COMMENT '专业代码',
  `major_name` varchar(200) NOT NULL COMMENT '专业名称',
  `subject_type` varchar(10) NOT NULL COMMENT '科类',
  `first_subject_requirement` varchar(50) NOT NULL DEFAULT '' COMMENT '首选科目要求',
  `resubject_requirement` varchar(100) NOT NULL DEFAULT '' COMMENT '再选科目要求',
  `requirement_text` varchar(500) NOT NULL DEFAULT '' COMMENT '官方原文',
  `source_name` varchar(100) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_page_url` varchar(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_level` varchar(30) NOT NULL DEFAULT '' COMMENT 'official/school/manual_verified',
  `parse_method` varchar(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_requirement` (`province_code`,`year`,`school_id`,`group_code`,`major_name`,`subject_type`),
  KEY `idx_major_requirement` (`province_code`,`year`,`subject_type`,`resubject_requirement`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多省专业/专业组选科要求';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_major_requirement_gz`
--

DROP TABLE IF EXISTS `data_major_requirement_gz`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_major_requirement_gz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `year` smallint NOT NULL COMMENT '年份',
  `school_id` varchar(20) NOT NULL COMMENT '院校ID',
  `university_name` varchar(100) NOT NULL DEFAULT '' COMMENT '院校名称',
  `major_id` varchar(50) NOT NULL DEFAULT '' COMMENT '专业/专业组代码',
  `major_name` varchar(200) NOT NULL COMMENT '专业名称',
  `subject_type` varchar(10) NOT NULL COMMENT '首选科目类别(物理类/历史类)',
  `first_subject_requirement` varchar(50) NOT NULL DEFAULT '' COMMENT '首选科目要求',
  `resubject_requirement` varchar(100) NOT NULL DEFAULT '' COMMENT '再选科目要求(不限/化学/化学和生物等)',
  `requirement_text` varchar(500) NOT NULL DEFAULT '' COMMENT '官方原文或解析备注',
  `source_name` varchar(100) NOT NULL DEFAULT '' COMMENT '数据来源名称',
  `source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_file` varchar(200) NOT NULL DEFAULT '' COMMENT '来源文件名',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_year_school_major_subject` (`year`,`school_id`,`major_name`,`subject_type`),
  KEY `idx_school_major_subject` (`school_id`,`major_name`,`subject_type`),
  KEY `idx_year_subject_requirement` (`year`,`subject_type`,`resubject_requirement`)
) ENGINE=InnoDB AUTO_INCREMENT=73799 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='贵州官方专业选科要求库';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_major_score_gz`
--

DROP TABLE IF EXISTS `data_major_score_gz`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_major_score_gz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL,
  `university_name` varchar(100) DEFAULT '',
  `major_name` varchar(200) NOT NULL,
  `major_id` varchar(20) DEFAULT '',
  `year` smallint NOT NULL,
  `subject_type` varchar(10) NOT NULL,
  `batch` varchar(50) DEFAULT '',
  `resubject_requirement` varchar(100) DEFAULT '' COMMENT '再选科目要求',
  `min_score` smallint DEFAULT NULL,
  `max_score` smallint DEFAULT NULL,
  `avg_score` smallint DEFAULT NULL,
  `min_rank` int DEFAULT NULL,
  `plan_count` smallint DEFAULT NULL COMMENT '招生计划数',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record` (`school_id`,`major_name`(100),`year`,`subject_type`,`batch`(30)),
  KEY `idx_school_year` (`school_id`,`year`),
  KEY `idx_year_subject` (`year`,`subject_type`),
  KEY `idx_major` (`major_name`(100)),
  KEY `idx_subject_rank_year` (`subject_type`,`min_rank`,`year`),
  KEY `idx_year_subject_resub` (`year`,`subject_type`,`resubject_requirement`),
  KEY `idx_major_score_history` (`school_id`,`subject_type`,`major_name`(80),`year`)
) ENGINE=InnoDB AUTO_INCREMENT=297169 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='贵州专业投档线';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_score_line_gz`
--

DROP TABLE IF EXISTS `data_score_line_gz`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_score_line_gz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL COMMENT '院校ID',
  `university_name` varchar(100) NOT NULL COMMENT '院校名称',
  `major_name` varchar(200) NOT NULL COMMENT '专业名称',
  `major_id` varchar(20) DEFAULT '' COMMENT '专业ID',
  `year` smallint NOT NULL COMMENT '年份',
  `subject_type` varchar(10) NOT NULL COMMENT '科类(文科/理科/物理类/历史类)',
  `min_score` int DEFAULT NULL COMMENT '最低分',
  `max_score` int DEFAULT NULL COMMENT '最高分',
  `avg_score` int DEFAULT NULL COMMENT '平均分',
  `min_rank` int DEFAULT NULL COMMENT '最低位次',
  `plan_count` int DEFAULT NULL COMMENT '招生计划数',
  `batch` varchar(50) DEFAULT '本科批' COMMENT '批次',
  `resubject_requirement` varchar(100) DEFAULT '' COMMENT '再选科目要求',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_school` (`school_id`),
  KEY `idx_year_subject` (`year`,`subject_type`),
  KEY `idx_uni_name` (`university_name`),
  KEY `idx_min_rank` (`min_rank`),
  KEY `idx_subject_rank_year` (`subject_type`,`min_rank`,`year`),
  KEY `idx_year_subject_resub` (`year`,`subject_type`,`resubject_requirement`),
  KEY `idx_score_line_history` (`school_id`,`subject_type`,`year`)
) ENGINE=InnoDB AUTO_INCREMENT=47920 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='贵州历年投档分数线';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_score_rank`
--

DROP TABLE IF EXISTS `data_score_rank`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_score_rank` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `province_code` varchar(10) NOT NULL COMMENT '省份代码，如 GZ/SC',
  `province_name` varchar(20) NOT NULL DEFAULT '' COMMENT '省份名称',
  `year` smallint NOT NULL COMMENT '年份',
  `subject_type` varchar(10) NOT NULL COMMENT '科类(物理类/历史类/理科/文科)',
  `score` smallint NOT NULL COMMENT '分数',
  `score_label` varchar(20) NOT NULL DEFAULT '' COMMENT '分数段标签',
  `segment_count` int NOT NULL DEFAULT '0' COMMENT '本段人数',
  `cumulative_count` int NOT NULL DEFAULT '0' COMMENT '累计人数',
  `cumulative_rate` decimal(7,3) DEFAULT NULL COMMENT '累计比例%',
  `rank_low` int NOT NULL COMMENT '同分最好位次',
  `rank_high` int NOT NULL COMMENT '同分保守位次',
  `source_name` varchar(100) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '来源链接/文件',
  `source_page_url` varchar(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_file` varchar(200) NOT NULL DEFAULT '' COMMENT '来源文件名',
  `parse_method` varchar(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_province_year_subject_score` (`province_code`,`year`,`subject_type`,`score`),
  KEY `idx_province_year_subject_score` (`province_code`,`year`,`subject_type`,`score`),
  KEY `idx_province_year_subject_rank` (`province_code`,`year`,`subject_type`,`rank_low`,`rank_high`)
) ENGINE=InnoDB AUTO_INCREMENT=2111 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多省官方一分一段表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_score_rank_gz`
--

DROP TABLE IF EXISTS `data_score_rank_gz`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_score_rank_gz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `year` smallint NOT NULL COMMENT '年份',
  `province` varchar(20) NOT NULL DEFAULT '贵州' COMMENT '省份',
  `subject_type` varchar(10) NOT NULL COMMENT '首选科目类别(物理类/历史类)',
  `score` smallint NOT NULL COMMENT '分数',
  `score_label` varchar(20) NOT NULL DEFAULT '' COMMENT '原始分数段标签，如683及以上',
  `segment_count` int NOT NULL DEFAULT '0' COMMENT '本段人数',
  `cumulative_count` int NOT NULL COMMENT '累计人数(该分及以上)',
  `cumulative_rate` decimal(7,3) DEFAULT NULL COMMENT '累计比例%',
  `rank_low` int NOT NULL COMMENT '同分最好位次，累计人数-本段人数+1',
  `rank_high` int NOT NULL COMMENT '同分保守位次，累计人数',
  `source_name` varchar(100) NOT NULL DEFAULT '' COMMENT '数据来源名称',
  `source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '来源链接',
  `source_page_url` varchar(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_file` varchar(200) NOT NULL DEFAULT '' COMMENT '来源文件名',
  `parse_method` varchar(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_year_subject_score` (`year`,`subject_type`,`score`),
  KEY `idx_year_subject_score` (`year`,`subject_type`,`score`),
  KEY `idx_year_subject_rank` (`year`,`subject_type`,`rank_low`,`rank_high`)
) ENGINE=InnoDB AUTO_INCREMENT=2436 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='贵州官方一分一段表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_source_registry`
--

DROP TABLE IF EXISTS `data_source_registry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_source_registry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `province_code` varchar(10) NOT NULL COMMENT '省份代码',
  `province_name` varchar(20) NOT NULL DEFAULT '' COMMENT '省份名称',
  `year` smallint NOT NULL COMMENT '年份',
  `subject_type` varchar(20) NOT NULL DEFAULT '' COMMENT '科类',
  `data_type` varchar(50) NOT NULL COMMENT 'score_rank/group_line/group_plan/requirement',
  `batch` varchar(50) NOT NULL DEFAULT '' COMMENT '批次',
  `source_name` varchar(100) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_page_url` varchar(500) NOT NULL DEFAULT '' COMMENT '官方发布页面',
  `source_url` text COMMENT '来源文件/图片/网页',
  `source_level` varchar(30) NOT NULL DEFAULT '' COMMENT 'official/school/manual_verified',
  `source_hash` varchar(128) NOT NULL DEFAULT '' COMMENT '来源内容SHA-256或复核批次哈希',
  `parse_method` varchar(40) NOT NULL DEFAULT '' COMMENT '解析方式',
  `status` varchar(30) NOT NULL DEFAULT 'pending' COMMENT 'pending/imported/manual_review/failed',
  `row_count` int NOT NULL DEFAULT '0' COMMENT '导入行数',
  `last_checked_at` datetime DEFAULT NULL COMMENT '最近检查时间',
  `notes` text COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_registry_identity` (`province_code`,`year`,`subject_type`,`data_type`,`batch`,`source_page_url`(191)),
  KEY `idx_source_registry` (`province_code`,`data_type`,`year`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多省数据来源登记';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_year_readiness`
--

DROP TABLE IF EXISTS `data_year_readiness`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_year_readiness` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `province_code` varchar(16) NOT NULL,
  `year` int NOT NULL,
  `policy_ready` tinyint NOT NULL DEFAULT '0',
  `score_segment_ready` tinyint NOT NULL DEFAULT '0',
  `admission_plan_ready` tinyint NOT NULL DEFAULT '0',
  `major_requirement_ready` tinyint NOT NULL DEFAULT '0',
  `major_meta_ready` tinyint NOT NULL DEFAULT '0',
  `ml_training_ready` tinyint NOT NULL DEFAULT '0',
  `historical_training_ready` tinyint NOT NULL DEFAULT '1',
  `recommendation_phase` varchar(40) NOT NULL DEFAULT 'PRE_OFFICIAL_DATA',
  `latest_import_batch_id` varchar(120) DEFAULT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `last_checked_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_data_year_readiness_province_year` (`province_code`,`year`),
  KEY `idx_data_year_readiness_phase` (`recommendation_phase`),
  KEY `idx_data_year_readiness_checked_at` (`last_checked_at`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_year_readiness_batch`
--

DROP TABLE IF EXISTS `data_year_readiness_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_year_readiness_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `province_code` varchar(16) NOT NULL COMMENT '省份代码',
  `year` int NOT NULL COMMENT '招生年份',
  `data_type` varchar(80) NOT NULL COMMENT 'score_segment/admission_plan/policy_rule/major_requirement/major_meta/batch_line/art_sports_rule/special_plan_rule',
  `import_batch_id` varchar(120) NOT NULL COMMENT '导入批次ID',
  `status` varchar(40) NOT NULL DEFAULT 'staged' COMMENT 'staged/quality_passed/promoted/rolled_back/rejected',
  `source_manifest` varchar(500) NOT NULL DEFAULT '' COMMENT '官方来源清单路径或URL',
  `source_file` varchar(240) NOT NULL DEFAULT '' COMMENT '官方来源文件名',
  `source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '官方来源URL',
  `file_hash` varchar(128) NOT NULL DEFAULT '' COMMENT '官方文件hash',
  `quality_report_path` varchar(500) NOT NULL DEFAULT '' COMMENT '质检报告路径',
  `rollback_sql_path` varchar(500) NOT NULL DEFAULT '' COMMENT '回滚SQL路径',
  `row_count` int NOT NULL DEFAULT '0' COMMENT '本批行数',
  `failed_gate_count` int NOT NULL DEFAULT '0' COMMENT '质检失败项数',
  `reviewed_by` varchar(80) NOT NULL DEFAULT '' COMMENT '人工复核人',
  `reviewed_at` datetime DEFAULT NULL COMMENT '人工复核时间',
  `remarks` varchar(800) NOT NULL DEFAULT '' COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_readiness_batch_identity` (`province_code`,`year`,`data_type`,`import_batch_id`),
  KEY `idx_readiness_batch_year_status` (`province_code`,`year`,`status`),
  KEY `idx_readiness_batch_import` (`import_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招生年份官方数据导入批次审计';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `major_info`
--

DROP TABLE IF EXISTS `major_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `major_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `major_code` varchar(60) NOT NULL DEFAULT '' COMMENT '专业代码',
  `major_name` varchar(200) NOT NULL DEFAULT '' COMMENT '专业名称',
  `major_category` varchar(100) NOT NULL DEFAULT '' COMMENT '专业门类',
  `discipline` varchar(100) NOT NULL DEFAULT '' COMMENT '学科',
  `employment_score` decimal(6,2) DEFAULT NULL COMMENT '就业分',
  `salary_score` decimal(6,2) DEFAULT NULL COMMENT '薪酬分',
  `postgraduate_score` decimal(6,2) DEFAULT NULL COMMENT '升学分',
  `civil_service_score` decimal(6,2) DEFAULT NULL COMMENT '考公分',
  `hot_score` decimal(6,2) DEFAULT NULL COMMENT '热度分',
  `risk_score` decimal(6,2) DEFAULT NULL COMMENT '风险分',
  `suitable_subjects` json DEFAULT NULL COMMENT '适配科目',
  `limitation_tags` json DEFAULT NULL COMMENT '限制标签',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_info_code` (`major_code`),
  KEY `idx_major_info_name` (`major_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专业信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `major_requirement_match_gz`
--

DROP TABLE IF EXISTS `major_requirement_match_gz`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `major_requirement_match_gz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `history_year` smallint NOT NULL COMMENT '历史专业分年份',
  `requirement_year` smallint NOT NULL COMMENT '选科要求年份',
  `school_id` varchar(20) NOT NULL COMMENT '院校ID',
  `university_name` varchar(100) NOT NULL DEFAULT '' COMMENT '院校名称',
  `subject_type` varchar(10) NOT NULL COMMENT '科类',
  `history_major_name` varchar(300) NOT NULL COMMENT '历史专业名',
  `normalized_history_major_name` varchar(200) NOT NULL DEFAULT '' COMMENT '历史专业归一名',
  `requirement_id` bigint DEFAULT NULL COMMENT 'data_major_requirement_gz.id',
  `requirement_major_name` varchar(300) NOT NULL DEFAULT '' COMMENT '命中的选科要求专业名',
  `normalized_requirement_major_name` varchar(200) NOT NULL DEFAULT '' COMMENT '命中专业归一名',
  `resubject_requirement` varchar(100) NOT NULL DEFAULT '' COMMENT '再选科目要求',
  `match_type` varchar(30) NOT NULL DEFAULT 'missing' COMMENT 'exact/normalized/missing',
  `match_score` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '匹配分',
  `review_required` tinyint NOT NULL DEFAULT '1' COMMENT '是否需要人工复核',
  `reason` varchar(300) NOT NULL DEFAULT '' COMMENT '匹配说明',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_requirement_match_history` (`history_year`,`requirement_year`,`school_id`,`subject_type`,`history_major_name`),
  KEY `idx_requirement_match_req` (`requirement_id`),
  KEY `idx_requirement_match_school_norm` (`school_id`,`subject_type`,`normalized_history_major_name`),
  KEY `idx_requirement_match_review` (`review_required`,`match_type`)
) ENGINE=InnoDB AUTO_INCREMENT=36394 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='贵州历史专业分与2025选科要求匹配结果';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ml_model_registry`
--

DROP TABLE IF EXISTS `ml_model_registry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ml_model_registry` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `model_name` varchar(120) NOT NULL DEFAULT '' COMMENT '模型名称',
  `model_type` varchar(80) NOT NULL DEFAULT '' COMMENT '模型类型',
  `model_version` varchar(80) NOT NULL DEFAULT '' COMMENT '模型版本',
  `train_year_range` varchar(80) NOT NULL DEFAULT '' COMMENT '训练年份范围',
  `train_data_count` int NOT NULL DEFAULT '0' COMMENT '训练样本数',
  `feature_schema_json` json DEFAULT NULL COMMENT '特征结构',
  `metrics_json` json DEFAULT NULL COMMENT '评估指标',
  `model_file_path` varchar(500) NOT NULL DEFAULT '' COMMENT '模型文件路径',
  `status` varchar(30) NOT NULL DEFAULT 'draft' COMMENT 'draft/active/archived',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `activated_at` datetime DEFAULT NULL COMMENT '启用时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ml_model_version` (`model_name`,`model_version`),
  KEY `idx_ml_model_status` (`model_name`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='机器学习模型注册表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `official_import_job`
--

DROP TABLE IF EXISTS `official_import_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `official_import_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_id` varchar(80) NOT NULL COMMENT '任务ID',
  `province_code` varchar(16) NOT NULL DEFAULT 'GZ' COMMENT '省份代码',
  `year` int NOT NULL DEFAULT '2026' COMMENT '招生年份',
  `data_type` varchar(80) NOT NULL COMMENT 'SCORE_SEGMENT/ADMISSION_PLAN/POLICY_RULE/MAJOR_REQUIREMENT/MAJOR_META/SCORE_LINE/ART_SPORTS_RULE/SPECIAL_ELIGIBILITY',
  `import_batch_id` varchar(140) NOT NULL COMMENT '导入批次ID',
  `source_file` varchar(240) NOT NULL DEFAULT '' COMMENT '官方来源文件名',
  `source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '官方来源URL',
  `source_manifest` varchar(500) NOT NULL DEFAULT '' COMMENT '官方来源清单路径或URL',
  `raw_text` mediumtext COMMENT '官方原文片段或人工登记说明',
  `status` varchar(40) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/FILE_REGISTERED/STAGING_READY/QUALITY_CHECKED/FORMAL_SQL_GENERATED/WAITING_CONFIRMATION/PROMOTED/ROLLBACK_READY/FAILED',
  `current_step` varchar(80) NOT NULL DEFAULT 'CREATED' COMMENT '当前步骤',
  `total_rows` int NOT NULL DEFAULT '0' COMMENT '总行数',
  `clean_rows` int NOT NULL DEFAULT '0' COMMENT '通过行数',
  `review_rows` int NOT NULL DEFAULT '0' COMMENT '需复核行数',
  `error_rows` int NOT NULL DEFAULT '0' COMMENT '错误行数',
  `quality_report_path` varchar(500) NOT NULL DEFAULT '' COMMENT '质量报告路径',
  `formal_sql_path` varchar(500) NOT NULL DEFAULT '' COMMENT '正式导入确认SQL路径',
  `rollback_sql_path` varchar(500) NOT NULL DEFAULT '' COMMENT '回滚SQL路径',
  `message` varchar(800) NOT NULL DEFAULT '' COMMENT '最近一次执行说明',
  `created_by` varchar(80) NOT NULL DEFAULT '' COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_official_import_job_id` (`job_id`),
  UNIQUE KEY `uk_official_import_batch_id` (`import_batch_id`),
  KEY `idx_official_import_job_year_type` (`province_code`,`year`,`data_type`),
  KEY `idx_official_import_job_status` (`status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='2026官方数据导入任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `official_import_job_file`
--

DROP TABLE IF EXISTS `official_import_job_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `official_import_job_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_id` varchar(80) NOT NULL COMMENT '任务ID',
  `import_batch_id` varchar(140) NOT NULL COMMENT '导入批次ID',
  `source_file` varchar(240) NOT NULL DEFAULT '' COMMENT '官方来源文件名',
  `source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '官方来源URL',
  `source_manifest` varchar(500) NOT NULL DEFAULT '' COMMENT '官方来源清单路径或URL',
  `file_hash` varchar(128) NOT NULL DEFAULT '' COMMENT '官方文件hash',
  `raw_text` mediumtext COMMENT '官方原文片段',
  `status` varchar(40) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED/STAGED/REJECTED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_import_job_file_job` (`job_id`),
  KEY `idx_import_job_file_batch` (`import_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='2026官方数据导入任务来源文件';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `official_import_quality_report`
--

DROP TABLE IF EXISTS `official_import_quality_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `official_import_quality_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_id` varchar(80) NOT NULL COMMENT '任务ID',
  `import_batch_id` varchar(140) NOT NULL COMMENT '导入批次ID',
  `data_type` varchar(80) NOT NULL COMMENT '数据类型',
  `report_path` varchar(500) NOT NULL DEFAULT '' COMMENT '质量报告路径',
  `gate_status` varchar(40) NOT NULL DEFAULT 'DRY_RUN' COMMENT 'DRY_RUN/PASSED/FAILED',
  `summary` varchar(1000) NOT NULL DEFAULT '' COMMENT '摘要',
  `total_rows` int NOT NULL DEFAULT '0' COMMENT '总行数',
  `clean_rows` int NOT NULL DEFAULT '0' COMMENT '通过行数',
  `review_rows` int NOT NULL DEFAULT '0' COMMENT '需复核行数',
  `error_rows` int NOT NULL DEFAULT '0' COMMENT '错误行数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_quality_report_job` (`job_id`),
  KEY `idx_quality_report_batch` (`import_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='2026官方数据导入质量报告';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `official_import_rollback_plan`
--

DROP TABLE IF EXISTS `official_import_rollback_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `official_import_rollback_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_id` varchar(80) NOT NULL COMMENT '任务ID',
  `import_batch_id` varchar(140) NOT NULL COMMENT '导入批次ID',
  `rollback_sql_path` varchar(500) NOT NULL DEFAULT '' COMMENT '回滚SQL路径',
  `summary` varchar(1000) NOT NULL DEFAULT '' COMMENT '回滚说明',
  `executable` tinyint NOT NULL DEFAULT '0' COMMENT '本轮是否可执行，默认不可执行',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_rollback_plan_job` (`job_id`),
  KEY `idx_rollback_plan_batch` (`import_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='2026官方数据导入回滚计划';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `policy_rule_config`
--

DROP TABLE IF EXISTS `policy_rule_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `policy_rule_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `province` varchar(20) NOT NULL DEFAULT '' COMMENT '省份代码，如 GZ',
  `year` smallint NOT NULL COMMENT '年份',
  `candidate_type` varchar(30) NOT NULL DEFAULT '' COMMENT '考生类别',
  `batch_code` varchar(40) NOT NULL DEFAULT '' COMMENT '批次代码',
  `batch_name` varchar(80) NOT NULL DEFAULT '' COMMENT '批次名称',
  `volunteer_mode` varchar(60) NOT NULL DEFAULT '' COMMENT '志愿模式',
  `max_volunteer_count` int NOT NULL DEFAULT '0' COMMENT '最大志愿数量',
  `major_per_school_count` int NOT NULL DEFAULT '0' COMMENT '每校专业数量',
  `has_adjustment` tinyint NOT NULL DEFAULT '0' COMMENT '是否有专业调剂',
  `filing_principle` varchar(200) NOT NULL DEFAULT '' COMMENT '投档原则',
  `admission_order` varchar(200) NOT NULL DEFAULT '' COMMENT '录取顺序说明',
  `policy_status` varchar(30) NOT NULL DEFAULT 'pending_confirm' COMMENT 'confirmed/draft/pending_confirm',
  `official_source_title` varchar(200) NOT NULL DEFAULT '' COMMENT '官方来源标题',
  `official_source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '官方来源链接',
  `official_source_text` text COMMENT '官方来源摘录',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_policy_rule` (`province`,`year`,`candidate_type`,`batch_code`),
  KEY `idx_policy_enabled` (`province`,`year`,`enabled`,`policy_status`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='年度招生政策规则配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `safety_code_identity`
--

DROP TABLE IF EXISTS `safety_code_identity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `safety_code_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `safety_code_hash` varchar(128) NOT NULL,
  `safety_code_version` varchar(20) NOT NULL DEFAULT 'v1',
  `created_at` datetime NOT NULL,
  `last_seen_at` datetime DEFAULT NULL,
  `plan_count` int NOT NULL DEFAULT '0',
  `enabled` tinyint NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_safety_code_hash` (`safety_code_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=65 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全码匿名身份';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `school_info`
--

DROP TABLE IF EXISTS `school_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `school_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `school_code` varchar(40) NOT NULL DEFAULT '' COMMENT '院校代码',
  `school_name` varchar(160) NOT NULL DEFAULT '' COMMENT '院校名称',
  `province` varchar(40) NOT NULL DEFAULT '' COMMENT '省份',
  `city` varchar(80) NOT NULL DEFAULT '' COMMENT '城市',
  `school_type` varchar(80) NOT NULL DEFAULT '' COMMENT '院校类型',
  `school_level` varchar(80) NOT NULL DEFAULT '' COMMENT '院校层次',
  `is_985` tinyint NOT NULL DEFAULT '0' COMMENT '是否985',
  `is_211` tinyint NOT NULL DEFAULT '0' COMMENT '是否211',
  `is_double_first_class` tinyint NOT NULL DEFAULT '0' COMMENT '是否双一流',
  `is_public` tinyint NOT NULL DEFAULT '0' COMMENT '是否公办',
  `ranking_score` decimal(6,2) DEFAULT NULL COMMENT '院校排名分',
  `employment_score` decimal(6,2) DEFAULT NULL COMMENT '就业分',
  `postgraduate_score` decimal(6,2) DEFAULT NULL COMMENT '升学分',
  `tags` json DEFAULT NULL COMMENT '标签',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_school_info_code` (`school_code`),
  KEY `idx_school_info_name` (`school_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='院校信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `score_rank_segment`
--

DROP TABLE IF EXISTS `score_rank_segment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_rank_segment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `year` smallint NOT NULL COMMENT '年份',
  `province` varchar(20) NOT NULL DEFAULT '' COMMENT '省份代码',
  `candidate_type` varchar(30) NOT NULL DEFAULT '' COMMENT '考生类别',
  `subject_type` varchar(30) NOT NULL DEFAULT '' COMMENT '科类',
  `score` smallint NOT NULL COMMENT '分数',
  `same_score_count` int NOT NULL DEFAULT '0' COMMENT '同分人数',
  `cumulative_count` int NOT NULL DEFAULT '0' COMMENT '累计人数',
  `rank_min` int NOT NULL DEFAULT '0' COMMENT '同分最好位次',
  `rank_max` int NOT NULL DEFAULT '0' COMMENT '同分保守位次',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_score_rank_segment` (`year`,`province`,`candidate_type`,`subject_type`,`score`),
  KEY `idx_score_rank_segment_rank` (`province`,`year`,`subject_type`,`rank_min`,`rank_max`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一分一段表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `skills_chunk`
--

DROP TABLE IF EXISTS `skills_chunk`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `skills_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `document_id` bigint NOT NULL COMMENT '文档ID',
  `chunk_index` int NOT NULL DEFAULT '0' COMMENT '切片序号',
  `chunk_text` text COMMENT '切片文本',
  `embedding_vector` json DEFAULT NULL COMMENT 'embedding 向量 JSON',
  `tags_json` json DEFAULT NULL COMMENT '标签',
  `token_count` int NOT NULL DEFAULT '0' COMMENT '估算 token 数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skills_chunk_index` (`document_id`,`chunk_index`),
  FULLTEXT KEY `ft_skills_chunk_text` (`chunk_text`)
) ENGINE=InnoDB AUTO_INCREMENT=99 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='skills 切片表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `skills_document`
--

DROP TABLE IF EXISTS `skills_document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `skills_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `source_id` bigint NOT NULL DEFAULT '0' COMMENT '来源ID',
  `doc_key` varchar(200) NOT NULL DEFAULT '' COMMENT '文档键',
  `title` varchar(300) NOT NULL DEFAULT '' COMMENT '标题',
  `file_path` varchar(500) NOT NULL DEFAULT '' COMMENT '文件路径',
  `content_hash` varchar(80) NOT NULL DEFAULT '' COMMENT '内容hash',
  `raw_content` mediumtext COMMENT '原文',
  `sanitized_content` mediumtext COMMENT '合规后原文',
  `tags_json` json DEFAULT NULL COMMENT '标签',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skills_document_key` (`source_id`,`doc_key`),
  KEY `idx_skills_document_enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='skills 文档表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `skills_query_log`
--

DROP TABLE IF EXISTS `skills_query_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `skills_query_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plan_id` bigint NOT NULL DEFAULT '0' COMMENT '方案ID',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户ID',
  `question` text COMMENT '问题',
  `retrieved_chunks_json` json DEFAULT NULL COMMENT '召回切片',
  `raw_answer` mediumtext COMMENT '原始回答',
  `sanitized_answer` mediumtext COMMENT '合规后回答',
  `compliance_status` varchar(30) NOT NULL DEFAULT '' COMMENT '合规状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_skills_query_plan` (`plan_id`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='skills 问答日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `skills_source`
--

DROP TABLE IF EXISTS `skills_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `skills_source` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `source_name` varchar(160) NOT NULL DEFAULT '' COMMENT '来源名称',
  `source_type` varchar(30) NOT NULL DEFAULT 'local' COMMENT 'github/local/admin_upload',
  `source_url` varchar(500) NOT NULL DEFAULT '' COMMENT '来源URL',
  `local_path` varchar(500) NOT NULL DEFAULT '' COMMENT '本地路径',
  `branch` varchar(80) NOT NULL DEFAULT '' COMMENT '分支',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最后同步时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_skills_source_enabled` (`enabled`,`source_type`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='skills 来源表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `special_admission_policy`
--

DROP TABLE IF EXISTS `special_admission_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `special_admission_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `year` int NOT NULL DEFAULT '2026' COMMENT '招生年份',
  `category` varchar(50) NOT NULL COMMENT '分类编码',
  `category_name` varchar(50) NOT NULL COMMENT '分类名称',
  `title` varchar(200) NOT NULL COMMENT '政策标题',
  `summary` varchar(500) NOT NULL DEFAULT '' COMMENT '摘要',
  `content_md` mediumtext NOT NULL COMMENT 'Markdown正文',
  `official_url` varchar(500) NOT NULL DEFAULT '' COMMENT '官方或权威转载链接',
  `source_name` varchar(100) NOT NULL DEFAULT '贵州省招生考试院' COMMENT '来源名称',
  `source_type` varchar(30) NOT NULL DEFAULT 'official' COMMENT '来源类型：official/wechat/reprint',
  `apply_start` date DEFAULT NULL COMMENT '报名或申报开始日期',
  `apply_end` date DEFAULT NULL COMMENT '报名或申报截止日期',
  `exam_time` varchar(100) NOT NULL DEFAULT '' COMMENT '考试或确认时间',
  `target_students` varchar(200) NOT NULL DEFAULT '' COMMENT '适用考生',
  `requirements` varchar(500) NOT NULL DEFAULT '' COMMENT '核心资格要求',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0草稿，1启用，2停用',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_policy_year_category_title` (`year`,`category`,`title`),
  KEY `idx_policy_category_status` (`category`,`status`,`sort_order`),
  KEY `idx_policy_year_status` (`year`,`status`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='特殊类型招生政策表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stg_gz_admission_plan_candidate`
--

DROP TABLE IF EXISTS `stg_gz_admission_plan_candidate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stg_gz_admission_plan_candidate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `import_batch_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `csv_row_number` int DEFAULT NULL,
  `year` smallint NOT NULL,
  `province` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '贵州',
  `province_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GZ',
  `candidate_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '普通类',
  `subject_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `batch_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `batch_name` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `school_id` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `school_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `school_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `major_code` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `major_name` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `normalized_major_name` varchar(220) COLLATE utf8mb4_unicode_ci NOT NULL,
  `selected_subject_requirement` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `plan_count` int DEFAULT NULL,
  `tuition` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `campus` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remarks` text COLLATE utf8mb4_unicode_ci,
  `special_limit` text COLLATE utf8mb4_unicode_ci,
  `is_chinese_foreign_coop` tinyint NOT NULL DEFAULT '0',
  `is_high_fee` tinyint NOT NULL DEFAULT '0',
  `source_file` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_url` varchar(800) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_page` int DEFAULT NULL,
  `source_column` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `raw_text` text COLLATE utf8mb4_unicode_ci,
  `parse_confidence` decimal(5,4) NOT NULL DEFAULT '0.9000',
  `review_required` tinyint NOT NULL DEFAULT '0',
  `review_flags` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_stg_import_batch` (`import_batch_id`),
  KEY `idx_stg_year_subject_batch` (`year`,`subject_type`,`batch_code`),
  KEY `idx_stg_school_major` (`school_code`,`normalized_major_name`),
  KEY `idx_stg_review_required` (`review_required`),
  KEY `idx_stg_review_flags` (`review_flags`(191)),
  KEY `idx_stg_batch_clean` (`import_batch_id`,`review_required`,`year`,`subject_type`,`batch_code`)
) ENGINE=InnoDB AUTO_INCREMENT=163390 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='GZ admission plan candidate staging table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stg_gz_admission_plan_clean_candidate`
--

DROP TABLE IF EXISTS `stg_gz_admission_plan_clean_candidate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stg_gz_admission_plan_clean_candidate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `import_batch_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `csv_row_number` int DEFAULT NULL,
  `year` smallint NOT NULL,
  `province` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '贵州',
  `province_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GZ',
  `candidate_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '普通类',
  `subject_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `batch_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `batch_name` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `school_id` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `school_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `school_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `major_code` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `major_name` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `normalized_major_name` varchar(220) COLLATE utf8mb4_unicode_ci NOT NULL,
  `selected_subject_requirement` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `plan_count` int DEFAULT NULL,
  `tuition` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `campus` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remarks` text COLLATE utf8mb4_unicode_ci,
  `special_limit` text COLLATE utf8mb4_unicode_ci,
  `is_chinese_foreign_coop` tinyint NOT NULL DEFAULT '0',
  `is_high_fee` tinyint NOT NULL DEFAULT '0',
  `source_file` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_url` varchar(800) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_page` int DEFAULT NULL,
  `source_column` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `raw_text` text COLLATE utf8mb4_unicode_ci,
  `parse_confidence` decimal(5,4) NOT NULL DEFAULT '0.9000',
  `review_required` tinyint NOT NULL DEFAULT '0',
  `review_flags` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_stg_import_batch` (`import_batch_id`),
  KEY `idx_stg_year_subject_batch` (`year`,`subject_type`,`batch_code`),
  KEY `idx_stg_school_major` (`school_code`,`normalized_major_name`),
  KEY `idx_stg_review_required` (`review_required`),
  KEY `idx_stg_review_flags` (`review_flags`(191)),
  KEY `idx_stg_batch_clean` (`import_batch_id`,`review_required`,`year`,`subject_type`,`batch_code`)
) ENGINE=InnoDB AUTO_INCREMENT=75018 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='GZ admission plan candidate staging table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stg_gz_admission_plan_formal_conflict`
--

DROP TABLE IF EXISTS `stg_gz_admission_plan_formal_conflict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stg_gz_admission_plan_formal_conflict` (
  `import_batch_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `staging_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`import_batch_id`,`staging_id`),
  KEY `idx_stg_conflict_staging_id` (`staging_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='formal conflict staging row ids';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stg_gz_admission_plan_formal_key`
--

DROP TABLE IF EXISTS `stg_gz_admission_plan_formal_key`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stg_gz_admission_plan_formal_key` (
  `import_batch_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `key_hash` char(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`import_batch_id`,`key_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='formal plan key snapshot for staging validation';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stg_gz_early_admission_plan_candidate`
--

DROP TABLE IF EXISTS `stg_gz_early_admission_plan_candidate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stg_gz_early_admission_plan_candidate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `import_batch_id` varchar(80) DEFAULT NULL,
  `year` smallint DEFAULT NULL,
  `province` varchar(20) DEFAULT NULL,
  `batch_code` varchar(40) DEFAULT NULL,
  `batch_name` varchar(80) DEFAULT NULL,
  `original_batch_code` varchar(40) DEFAULT NULL,
  `candidate_type` varchar(30) DEFAULT NULL,
  `subject_type` varchar(30) DEFAULT NULL,
  `school_id` varchar(20) DEFAULT NULL,
  `school_code` varchar(40) DEFAULT NULL,
  `school_name` varchar(160) DEFAULT NULL,
  `major_code` varchar(60) DEFAULT NULL,
  `major_name` varchar(300) DEFAULT NULL,
  `normalized_major_name` varchar(220) DEFAULT NULL,
  `selected_subject_requirement` varchar(200) DEFAULT NULL,
  `plan_count` int DEFAULT NULL,
  `tuition` varchar(80) DEFAULT NULL,
  `duration` varchar(40) DEFAULT NULL,
  `campus` varchar(160) DEFAULT NULL,
  `remarks` text,
  `special_limit` text,
  `is_chinese_foreign_coop` tinyint DEFAULT NULL,
  `is_high_fee` tinyint DEFAULT NULL,
  `source_file` varchar(200) DEFAULT NULL,
  `source_url` varchar(800) DEFAULT NULL,
  `source_page` int DEFAULT NULL,
  `source_column` varchar(4) DEFAULT NULL,
  `raw_text` text,
  `review_required` tinyint DEFAULT NULL,
  `review_flags` varchar(1000) DEFAULT NULL,
  `page_segment_evidence` varchar(500) DEFAULT NULL,
  `military_check` tinyint DEFAULT NULL,
  `political_check` tinyint DEFAULT NULL,
  `interview_required` tinyint DEFAULT NULL,
  `physical_exam_required` tinyint DEFAULT NULL,
  `fitness_test_required` tinyint DEFAULT NULL,
  `gender_limit` tinyint DEFAULT NULL,
  `gender_requirement` varchar(20) DEFAULT NULL,
  `flight_related` tinyint DEFAULT NULL,
  `flight_type` varchar(80) DEFAULT NULL,
  `navigation_related` tinyint DEFAULT NULL,
  `police_justice_related` tinyint DEFAULT NULL,
  `public_funded_teacher` tinyint DEFAULT NULL,
  `teacher_excellence` tinyint DEFAULT NULL,
  `free_medical` tinyint DEFAULT NULL,
  `targeted` tinyint DEFAULT NULL,
  `specialty_soldier` tinyint DEFAULT NULL,
  `structured_flags_json` json DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_stg_gz_early_batch` (`import_batch_id`,`year`,`subject_type`,`batch_code`),
  KEY `idx_stg_gz_early_natural` (`year`,`subject_type`,`batch_code`,`school_code`,`major_code`,`normalized_major_name`),
  KEY `idx_stg_gz_early_review` (`import_batch_id`,`review_required`)
) ENGINE=InnoDB AUTO_INCREMENT=2500 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stg_gz_p0_major_meta`
--

DROP TABLE IF EXISTS `stg_gz_p0_major_meta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stg_gz_p0_major_meta` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `import_batch_id` varchar(120) NOT NULL,
  `csv_row_number` int NOT NULL,
  `province_code` varchar(10) NOT NULL,
  `year` smallint NOT NULL,
  `subject_type` varchar(30) NOT NULL,
  `batch_code` varchar(60) NOT NULL,
  `school_code` varchar(60) NOT NULL,
  `school_name` varchar(160) NOT NULL DEFAULT '',
  `school_id` varchar(60) NOT NULL DEFAULT '',
  `major_code` varchar(80) NOT NULL DEFAULT '',
  `major_name` varchar(240) NOT NULL,
  `normalized_major_name` varchar(240) NOT NULL DEFAULT '',
  `tuition` varchar(100) NOT NULL DEFAULT '',
  `duration` varchar(60) NOT NULL DEFAULT '',
  `campus` varchar(180) NOT NULL DEFAULT '',
  `remarks` text,
  `medical_limit` varchar(1000) NOT NULL DEFAULT '',
  `single_subject_rule` varchar(1000) NOT NULL DEFAULT '',
  `foreign_language_limit` varchar(1000) NOT NULL DEFAULT '',
  `gender_limit` varchar(500) NOT NULL DEFAULT '',
  `qualification_required` varchar(1000) NOT NULL DEFAULT '',
  `targeted` tinyint NOT NULL DEFAULT '0',
  `agreement_required` tinyint NOT NULL DEFAULT '0',
  `source_name` varchar(120) NOT NULL DEFAULT '',
  `source_url` varchar(500) NOT NULL,
  `source_file` varchar(240) NOT NULL,
  `source_page` int DEFAULT NULL,
  `raw_text` text NOT NULL,
  `review_required` tinyint NOT NULL DEFAULT '0',
  `review_flags` varchar(1000) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stg_gz_p0_meta` (`import_batch_id`,`csv_row_number`),
  KEY `idx_stg_gz_p0_meta_batch` (`import_batch_id`,`year`,`subject_type`,`batch_code`),
  KEY `idx_stg_gz_p0_meta_school` (`school_id`,`major_code`)
) ENGINE=InnoDB AUTO_INCREMENT=91706 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stg_gz_p0_major_requirement`
--

DROP TABLE IF EXISTS `stg_gz_p0_major_requirement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stg_gz_p0_major_requirement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `import_batch_id` varchar(120) NOT NULL,
  `csv_row_number` int NOT NULL,
  `province_code` varchar(10) NOT NULL,
  `year` smallint NOT NULL,
  `subject_type` varchar(30) NOT NULL,
  `batch_code` varchar(60) NOT NULL,
  `school_code` varchar(60) NOT NULL,
  `school_name` varchar(160) NOT NULL DEFAULT '',
  `school_id` varchar(60) NOT NULL DEFAULT '',
  `major_code` varchar(80) NOT NULL DEFAULT '',
  `major_name` varchar(240) NOT NULL,
  `first_subject_requirement` varchar(80) NOT NULL DEFAULT '',
  `resubject_requirement` varchar(160) NOT NULL DEFAULT '',
  `requirement_text` varchar(800) NOT NULL DEFAULT '',
  `source_name` varchar(120) NOT NULL DEFAULT '',
  `source_url` varchar(500) NOT NULL,
  `source_file` varchar(240) NOT NULL,
  `source_page` int DEFAULT NULL,
  `raw_text` text NOT NULL,
  `review_required` tinyint NOT NULL DEFAULT '0',
  `review_flags` varchar(1000) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stg_gz_p0_req` (`import_batch_id`,`csv_row_number`),
  KEY `idx_stg_gz_p0_req_batch` (`import_batch_id`,`year`,`subject_type`,`batch_code`),
  KEY `idx_stg_gz_p0_req_school` (`school_id`,`major_code`)
) ENGINE=InnoDB AUTO_INCREMENT=91706 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_ai_config`
--

DROP TABLE IF EXISTS `sys_ai_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_ai_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `provider_name` varchar(100) NOT NULL DEFAULT 'OpenAI兼容服务' COMMENT '服务商名称',
  `base_url` varchar(500) NOT NULL DEFAULT '' COMMENT 'OpenAI兼容接口Base URL',
  `api_key` varchar(500) NOT NULL DEFAULT '' COMMENT 'API Key，仅后端使用，不返回前端',
  `chat_model` varchar(100) NOT NULL DEFAULT 'gpt-4o-mini' COMMENT '志愿AI解读模型',
  `review_model` varchar(100) NOT NULL DEFAULT 'gpt-4o-mini' COMMENT '文本审核模型',
  `vision_model` varchar(100) NOT NULL DEFAULT 'gpt-4o' COMMENT '图片审核模型',
  `max_tokens` int NOT NULL DEFAULT '2600' COMMENT '志愿AI解读最大输出Token',
  `temperature` double NOT NULL DEFAULT '0.7' COMMENT '志愿AI解读采样温度',
  `system_prompt` text COMMENT '志愿AI解读系统提示词',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用AI服务：0停用，1启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_config_enabled` (`enabled`,`updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI服务配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_alumni_admin`
--

DROP TABLE IF EXISTS `sys_alumni_admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_alumni_admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL,
  `nickname` varchar(50) NOT NULL,
  `phone` varchar(20) DEFAULT '',
  `email` varchar(100) DEFAULT '',
  `avatar_url` varchar(500) DEFAULT '',
  `credential_url` varchar(500) DEFAULT '',
  `graduation_year` smallint DEFAULT NULL,
  `major` varchar(100) DEFAULT '',
  `bio` varchar(500) DEFAULT '',
  `role` tinyint NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '0',
  `reject_reason` varchar(200) DEFAULT '',
  `password_hash` varchar(128) NOT NULL DEFAULT '',
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_school_id` (`school_id`),
  KEY `idx_phone` (`phone`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='校友管理员';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_announcement`
--

DROP TABLE IF EXISTS `sys_announcement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_announcement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '公告标题',
  `content_md` mediumtext NOT NULL COMMENT 'Markdown 内容',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=草稿 1=已发布 2=已停用',
  `popup_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '首页是否弹窗展示 0=否 1=是',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值，越大越靠前',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_popup` (`status`,`popup_enabled`),
  KEY `idx_sort_published` (`sort_order`,`published_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统公告';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_university`
--

DROP TABLE IF EXISTS `sys_university`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_university` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL COMMENT '掌上高考院校ID',
  `name` varchar(100) NOT NULL COMMENT '院校名称',
  `province` varchar(20) DEFAULT '' COMMENT '省份',
  `city` varchar(50) DEFAULT '' COMMENT '城市',
  `level` varchar(20) DEFAULT '' COMMENT '层次(985/211/双一流/一本/二本)',
  `type_name` varchar(20) DEFAULT '' COMMENT '类型(综合/理工/师范等)',
  `tags` varchar(200) DEFAULT '' COMMENT '标签JSON',
  `f985` tinyint DEFAULT '0',
  `f211` tinyint DEFAULT '0',
  `dual_class` tinyint DEFAULT '0',
  `nature_name` varchar(20) DEFAULT '' COMMENT '公办/民办',
  `belong` varchar(100) DEFAULT '' COMMENT '隶属',
  `logo_url` varchar(500) DEFAULT '' COMMENT '校徽URL',
  `school_site` varchar(200) DEFAULT '' COMMENT '官网',
  `phone` varchar(100) DEFAULT '' COMMENT '招生电话',
  `email` varchar(100) DEFAULT '' COMMENT '招生邮箱',
  `address` varchar(300) DEFAULT '' COMMENT '地址',
  `content` text COMMENT '简介',
  `qa_disabled` tinyint DEFAULT '0' COMMENT '问答是否关闭 0=否 1=是',
  `qa_disabled_reason` varchar(200) DEFAULT '' COMMENT '问答关闭原因',
  `qa_disabled_until` datetime DEFAULT NULL COMMENT '问答关闭截止时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_school_id` (`school_id`),
  KEY `idx_name` (`name`),
  KEY `idx_province` (`province`)
) ENGINE=InnoDB AUTO_INCREMENT=2199 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='院校信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `uni_content_edit`
--

DROP TABLE IF EXISTS `uni_content_edit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `uni_content_edit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL,
  `editor_id` bigint NOT NULL,
  `field_name` varchar(50) NOT NULL,
  `old_value` text,
  `new_value` text NOT NULL,
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=待AI审核 1=已采纳 2=已拒绝 3=待人工审核',
  `ai_review_result` varchar(500) DEFAULT NULL COMMENT 'AI审核结果JSON',
  `review_note` varchar(200) DEFAULT '',
  `review_actor_role` varchar(20) DEFAULT '' COMMENT '最终审核角色(ai/alumni/admin/system)',
  `review_actor_id` bigint DEFAULT NULL COMMENT '最终审核人ID',
  `reviewed_at` datetime DEFAULT NULL COMMENT '最终审核时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_school` (`school_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大学内容编辑记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `uni_media`
--

DROP TABLE IF EXISTS `uni_media`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `uni_media` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL,
  `uploader_id` bigint NOT NULL,
  `media_type` tinyint NOT NULL DEFAULT '1' COMMENT '1=照片 2=资讯 3=文件 4=背景横幅',
  `url` varchar(500) NOT NULL,
  `thumb_url` varchar(500) DEFAULT '',
  `caption` varchar(200) DEFAULT '',
  `sort_order` int DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=待AI审核 1=已发布 2=已拒绝 3=待人工审核',
  `ai_review_result` varchar(500) DEFAULT NULL COMMENT 'AI审核结果JSON',
  `review_note` varchar(200) DEFAULT '' COMMENT '人工审核备注',
  `review_actor_role` varchar(20) DEFAULT '' COMMENT '最终审核角色(ai/alumni/admin/system)',
  `review_actor_id` bigint DEFAULT NULL COMMENT '最终审核人ID',
  `reviewed_at` datetime DEFAULT NULL COMMENT '最终审核时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_school_status` (`school_id`,`status`),
  KEY `idx_uploader` (`uploader_id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大学媒体资源';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `uni_official_link`
--

DROP TABLE IF EXISTS `uni_official_link`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `uni_official_link` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `school_id` varchar(20) NOT NULL COMMENT '院校ID',
  `school_name` varchar(100) DEFAULT '' COMMENT '院校名称',
  `source_domain` varchar(200) DEFAULT '' COMMENT '来源域名',
  `school_site` text,
  `admission_site` text,
  `admission_brochure_url` text,
  `major_catalog_url` text,
  `tuition_info_url` text,
  `tuition_remark` varchar(500) DEFAULT '' COMMENT '收费备注',
  `capture_method` varchar(50) DEFAULT 'manual' COMMENT 'manual/scraper',
  `capture_status` tinyint NOT NULL DEFAULT '0' COMMENT '0=待补充 1=已收录 2=待核验',
  `last_verified_at` datetime DEFAULT NULL COMMENT '最近人工核验时间',
  `last_captured_at` datetime DEFAULT NULL COMMENT '最近采集时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tuition_summary` text COMMENT '收费标准摘要',
  `major_catalog_summary` text COMMENT '专业目录摘要',
  `adjustment_rule` text COMMENT '调剂规则',
  `foreign_language_rule` text COMMENT '外语语种要求',
  `physical_exam_rule` text COMMENT '体检限制',
  `single_subject_rule` text COMMENT '单科成绩要求',
  `parser_notes` text COMMENT '解析备注',
  `parse_status` tinyint NOT NULL DEFAULT '0' COMMENT '0=未解析 1=已解析 2=待复核',
  `last_parsed_at` datetime DEFAULT NULL COMMENT '最近解析时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_school_id` (`school_id`),
  KEY `idx_status` (`capture_status`)
) ENGINE=InnoDB AUTO_INCREMENT=2247084 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='院校官方报考资料入口';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `volunteer_ai_analysis`
--

DROP TABLE IF EXISTS `volunteer_ai_analysis`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `volunteer_ai_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plan_id` bigint NOT NULL COMMENT '方案ID',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户ID',
  `analysis_status` varchar(30) NOT NULL DEFAULT 'pending' COMMENT '状态',
  `conclusion_text` text COMMENT '结论',
  `gradient_summary_json` json DEFAULT NULL COMMENT '梯度摘要',
  `key_keep_items_json` json DEFAULT NULL COMMENT '保留项',
  `high_risk_items_json` json DEFAULT NULL COMMENT '风险项',
  `diagnosis_text` mediumtext COMMENT '诊断正文',
  `action_steps_json` json DEFAULT NULL COMMENT '行动步骤',
  `sanitized_analysis_text` mediumtext COMMENT '合规后正文',
  `sensitive_words_json` json DEFAULT NULL COMMENT '命中词',
  `compliance_status` varchar(30) NOT NULL DEFAULT 'pending' COMMENT '合规状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_volunteer_ai_analysis_plan` (`plan_id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 深度解读结果表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `volunteer_plan`
--

DROP TABLE IF EXISTS `volunteer_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `volunteer_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户ID',
  `year` smallint NOT NULL COMMENT '年份',
  `province` varchar(20) NOT NULL DEFAULT '' COMMENT '省份代码',
  `candidate_type` varchar(30) NOT NULL DEFAULT '' COMMENT '考生类别',
  `subject_type` varchar(30) NOT NULL DEFAULT '' COMMENT '科类',
  `selected_subjects_json` json DEFAULT NULL COMMENT '选科JSON',
  `score` smallint NOT NULL DEFAULT '0' COMMENT '分数',
  `rank` int NOT NULL DEFAULT '0' COMMENT '位次',
  `batch_code` varchar(40) NOT NULL DEFAULT '' COMMENT '批次代码',
  `risk_preference` varchar(30) NOT NULL DEFAULT '' COMMENT '风险偏好',
  `policy_rule_id` bigint DEFAULT NULL COMMENT '政策规则ID',
  `ml_model_version` varchar(80) NOT NULL DEFAULT '' COMMENT '模型版本',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '志愿数量',
  `safety_code_hash` varchar(120) DEFAULT NULL COMMENT '安全码 BCrypt 哈希',
  `safety_code_fingerprint` varchar(128) DEFAULT NULL COMMENT '安全码指纹(SHA-256)',
  `safety_code_created_at` datetime DEFAULT NULL COMMENT '安全码创建时间',
  `safety_code_version` int NOT NULL DEFAULT '1' COMMENT '安全码版本',
  `status` varchar(30) NOT NULL DEFAULT 'generated' COMMENT '状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_volunteer_plan_user` (`user_id`,`created_at`),
  KEY `idx_volunteer_plan_policy` (`province`,`year`,`batch_code`),
  KEY `idx_volunteer_plan_fp` (`safety_code_fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='志愿方案主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `volunteer_plan_item`
--

DROP TABLE IF EXISTS `volunteer_plan_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `volunteer_plan_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plan_id` bigint NOT NULL COMMENT '方案ID',
  `volunteer_index` int NOT NULL DEFAULT '0' COMMENT '志愿序号',
  `gradient` varchar(10) NOT NULL DEFAULT '' COMMENT '冲稳保垫',
  `school_code` varchar(40) NOT NULL DEFAULT '' COMMENT '院校代码',
  `school_name` varchar(160) NOT NULL DEFAULT '' COMMENT '院校名称',
  `major_code` varchar(60) NOT NULL DEFAULT '' COMMENT '专业代码',
  `major_name` varchar(200) NOT NULL DEFAULT '' COMMENT '专业名称',
  `school_city` varchar(80) NOT NULL DEFAULT '' COMMENT '城市',
  `tuition` varchar(80) NOT NULL DEFAULT '' COMMENT '学费',
  `candidate_rank` int NOT NULL DEFAULT '0' COMMENT '考生位次',
  `predicted_min_rank` int DEFAULT NULL COMMENT '预测参考位次',
  `rank_diff` int DEFAULT NULL COMMENT '预测参考位次-考生位次',
  `internal_score` decimal(8,4) DEFAULT NULL COMMENT '内部机会分，仅后端使用',
  `chance_score` int NOT NULL DEFAULT '0' COMMENT '机会指数',
  `chance_level` varchar(30) NOT NULL DEFAULT '' COMMENT '机会等级',
  `risk_level` varchar(30) NOT NULL DEFAULT '' COMMENT '风险等级',
  `confidence_level` varchar(30) NOT NULL DEFAULT '' COMMENT '参考度等级',
  `data_confidence` decimal(6,2) DEFAULT NULL COMMENT '数据参考度',
  `final_score` decimal(8,2) DEFAULT NULL COMMENT '最终排序分',
  `recommend_reason_json` json DEFAULT NULL COMMENT '推荐理由',
  `risk_warning_json` json DEFAULT NULL COMMENT '风险提示',
  `model_feature_contribution_json` json DEFAULT NULL COMMENT '特征贡献',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_volunteer_plan_item_index` (`plan_id`,`volunteer_index`),
  KEY `idx_volunteer_plan_item_school` (`school_code`,`major_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='志愿方案明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'gzly'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-16 23:03:58
