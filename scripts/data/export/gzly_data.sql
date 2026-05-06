-- GZLY 数据 2026-04-05 11:51:34
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_university` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, `school_id` VARCHAR(20) NOT NULL UNIQUE,
  `name` VARCHAR(100) NOT NULL, `province` VARCHAR(20) DEFAULT '',
  `city` VARCHAR(50) DEFAULT '', `level` VARCHAR(20) DEFAULT '',
  `type_name` VARCHAR(20) DEFAULT '', `nature` VARCHAR(20) DEFAULT '',
  `f985` TINYINT DEFAULT 0, `f211` TINYINT DEFAULT 0, `dual_class` TINYINT DEFAULT 0,
  `belong` VARCHAR(50) DEFAULT '', `logo_url` VARCHAR(500) DEFAULT '',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `sys_university` (`school_id`,`name`,`province`,`city`,`level`,`type_name`,`nature`,`f985`,`f211`,`dual_class`,`belong`,`logo_url`) VALUES ('935','贵州大学','贵州','贵阳市','本科','综合类','公办',0,1,1,'贵州省','');

CREATE TABLE IF NOT EXISTS `data_score_line_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, `school_id` VARCHAR(20) NOT NULL,
  `university_name` VARCHAR(100), `year` SMALLINT NOT NULL,
  `subject_type` VARCHAR(10) NOT NULL, `batch` VARCHAR(50) DEFAULT '',
  `recruit_type` VARCHAR(50) DEFAULT '', `min_score` SMALLINT, `min_rank` INT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_sy` (`school_id`,`year`), KEY `idx_ys` (`year`,`subject_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'物理类','本科批','普通类',516,41632);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'物理类','本科提前批B段','高校专项计划',557,20194);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'物理类','本科批','护理类',530,33468);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'物理类','本科批','国家专项计划',510,45035);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'物理类','本科批','地方专项计划',508,46657);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'物理类','本科批','预科',505,48976);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'物理类','本科批','中外合作办学',489,60705);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'历史类','本科批','普通类',516,41632);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'历史类','本科提前批B段','高校专项计划',557,20194);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'历史类','本科批','护理类',530,33468);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'历史类','本科批','国家专项计划',510,45035);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'历史类','本科批','地方专项计划',508,46657);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'历史类','本科批','预科',505,48976);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'历史类','本科批','中外合作办学',489,60705);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','普通类',516,41632);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科提前批B段','高校专项计划',557,20194);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','护理类',530,33468);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','国家专项计划',510,45035);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','地方专项计划',508,46657);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','预科',505,48976);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','中外合作办学',489,60705);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','普通类',516,41632);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科提前批B段','高校专项计划',557,20194);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','护理类',530,33468);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','国家专项计划',510,45035);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','地方专项计划',508,46657);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','预科',505,48976);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','中外合作办学',489,60705);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','普通类',516,41632);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科提前批B段','高校专项计划',557,20194);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','护理类',530,33468);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','国家专项计划',510,45035);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','地方专项计划',508,46657);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','预科',505,48976);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','中外合作办学',489,60705);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','普通类',516,41632);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科提前批B段','高校专项计划',557,20194);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','护理类',530,33468);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','国家专项计划',510,45035);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','地方专项计划',508,46657);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','预科',505,48976);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','中外合作办学',489,60705);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','普通类',516,41632);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科提前批B段','高校专项计划',557,20194);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','护理类',530,33468);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','国家专项计划',510,45035);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','地方专项计划',508,46657);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','预科',505,48976);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','中外合作办学',489,60705);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','普通类',516,41632);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科提前批B段','高校专项计划',557,20194);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','护理类',530,33468);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','国家专项计划',510,45035);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','地方专项计划',508,46657);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','预科',505,48976);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','中外合作办学',489,60705);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','普通类',516,41632);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科提前批B段','高校专项计划',557,20194);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','护理类',530,33468);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','国家专项计划',510,45035);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','地方专项计划',508,46657);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','预科',505,48976);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'理科','本科批','中外合作办学',489,60705);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','普通类',516,41632);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科提前批B段','高校专项计划',557,20194);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','护理类',530,33468);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','国家专项计划',510,45035);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','地方专项计划',508,46657);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','预科',505,48976);
INSERT INTO `data_score_line_gz` (`school_id`,`university_name`,`year`,`subject_type`,`batch`,`recruit_type`,`min_score`,`min_rank`) VALUES ('935','贵州大学',2025,'文科','本科批','中外合作办学',489,60705);

CREATE TABLE IF NOT EXISTS `data_major_score_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, `school_id` VARCHAR(20) NOT NULL,
  `university_name` VARCHAR(100), `major_name` VARCHAR(200) NOT NULL,
  `year` SMALLINT NOT NULL, `subject_type` VARCHAR(10) NOT NULL,
  `min_score` SMALLINT, `min_rank` INT, `notes` TEXT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_sy` (`school_id`,`year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2024,'物理类',590,9520,'（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2024,'物理类',590,9417,'（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','法学',2024,'物理类',588,9927,'（外语语种要求：不限） 选科要求：首选物理，再选不限');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2024,'物理类',588,10092,'（国家专项计划）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2024,'物理类',588,9838,'（国家专项计划）（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2024,'历史类',590,9520,'（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2024,'历史类',590,9417,'（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','法学',2024,'历史类',588,9927,'（外语语种要求：不限） 选科要求：首选物理，再选不限');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2024,'历史类',588,10092,'（国家专项计划）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2024,'历史类',588,9838,'（国家专项计划）（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2023,'理科',590,9520,'（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2023,'理科',590,9417,'（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','法学',2023,'理科',588,9927,'（外语语种要求：不限） 选科要求：首选物理，再选不限');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2023,'理科',588,10092,'（国家专项计划）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2023,'理科',588,9838,'（国家专项计划）（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2023,'文科',590,9520,'（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2023,'文科',590,9417,'（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','法学',2023,'文科',588,9927,'（外语语种要求：不限） 选科要求：首选物理，再选不限');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2023,'文科',588,10092,'（国家专项计划）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2023,'文科',588,9838,'（国家专项计划）（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2022,'理科',590,9520,'（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2022,'理科',590,9417,'（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','法学',2022,'理科',588,9927,'（外语语种要求：不限） 选科要求：首选物理，再选不限');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2022,'理科',588,10092,'（国家专项计划）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2022,'理科',588,9838,'（国家专项计划）（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2022,'文科',590,9520,'（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2022,'文科',590,9417,'（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','法学',2022,'文科',588,9927,'（外语语种要求：不限） 选科要求：首选物理，再选不限');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2022,'文科',588,10092,'（国家专项计划）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2022,'文科',588,9838,'（国家专项计划）（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2021,'理科',590,9520,'（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2021,'理科',590,9417,'（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','法学',2021,'理科',588,9927,'（外语语种要求：不限） 选科要求：首选物理，再选不限');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2021,'理科',588,10092,'（国家专项计划）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2021,'理科',588,9838,'（国家专项计划）（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2021,'文科',590,9520,'（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2021,'文科',590,9417,'（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','法学',2021,'文科',588,9927,'（外语语种要求：不限） 选科要求：首选物理，再选不限');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2021,'文科',588,10092,'（国家专项计划）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2021,'文科',588,9838,'（国家专项计划）（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2020,'理科',590,9520,'（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2020,'理科',590,9417,'（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','法学',2020,'理科',588,9927,'（外语语种要求：不限） 选科要求：首选物理，再选不限');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2020,'理科',588,10092,'（国家专项计划）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2020,'理科',588,9838,'（国家专项计划）（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2020,'文科',590,9520,'（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2020,'文科',590,9417,'（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','法学',2020,'文科',588,9927,'（外语语种要求：不限） 选科要求：首选物理，再选不限');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','电气工程及其自动化',2020,'文科',588,10092,'（国家专项计划）（外语语种要求：不限） 选科要求：首选物理，再选化学');
INSERT INTO `data_major_score_gz` (`school_id`,`university_name`,`major_name`,`year`,`subject_type`,`min_score`,`min_rank`,`notes`) VALUES ('935','贵州大学','计算机类',2020,'文科',588,9838,'（国家专项计划）（包含计算机科学与技术、信息安全）（外语语种要求：不限） 选科要求：首选物理，再选化学');
