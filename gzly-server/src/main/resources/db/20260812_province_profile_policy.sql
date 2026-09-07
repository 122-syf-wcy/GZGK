-- 多省份省级画像与批次政策扩展（阶段 0 / 阶段 1，详见 docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md）。
-- 全部语句幂等：CREATE TABLE IF NOT EXISTS、information_schema 守卫的 ALTER、ON DUPLICATE KEY UPDATE 种子。
-- 政策数值来源：2026 年各省考试院实施规定 / 官方问答的联网核查（文档第二部分），
-- 落库前仍需人工到考试院官网复核原文；policy_status 统一为 pending_confirm。

SET @table_schema := DATABASE();

-- ─────────────────────────────────────────────────────────────
-- 1. 省级画像表：只放"省级、不随批次变化"的属性
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `province_profile` (
  `province_code` VARCHAR(10) NOT NULL COMMENT '省份代码 GZ/SC/HB/AH/GX/HI/YN/HA',
  `province_name` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '省份名称',
  `subject_mode` VARCHAR(20) NOT NULL DEFAULT '3+1+2' COMMENT '选科模式 3+1+2 / 3+3',
  `new_gaokao_first_year` SMALLINT NOT NULL DEFAULT 0 COMMENT '新高考首年，之前年份数据不可直接比较',
  `score_system` VARCHAR(20) NOT NULL DEFAULT 'RAW_750' COMMENT '分数制 RAW_750 / STANDARD_900',
  `rank_tie_break_rule` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '同分排序规则说明（人工核验后填写）',
  `official_source_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '官方来源名称',
  `official_source_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '官方来源链接',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否对外开放该省份入口',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`province_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='省份画像（省级属性，批次级属性在 policy_rule_config）';

-- ─────────────────────────────────────────────────────────────
-- 2. policy_rule_config 扩展批次级算法字段（守卫式 ALTER）
-- ─────────────────────────────────────────────────────────────
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'policy_rule_config'
    AND column_name = 'volunteer_unit_type'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `policy_rule_config` ADD COLUMN `volunteer_unit_type` VARCHAR(40) NOT NULL DEFAULT '''' COMMENT ''志愿单位类型 MAJOR_96/PROFESSIONAL_GROUP_45/SCHOOL_SEQUENTIAL'' AFTER `volunteer_mode`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'policy_rule_config'
    AND column_name = 'major_per_group_count'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `policy_rule_config` ADD COLUMN `major_per_group_count` INT NOT NULL DEFAULT 0 COMMENT ''组内专业志愿数（广西20/云南10/多数省6，专业+院校模式为0）'' AFTER `major_per_school_count`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'policy_rule_config'
    AND column_name = 'gradient_preset_json'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `policy_rule_config` ADD COLUMN `gradient_preset_json` TEXT NULL COMMENT ''冲稳保垫目标数量与位次区间预设 JSON'' AFTER `admission_order`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'policy_rule_config'
    AND column_name = 'chance_params_json'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `policy_rule_config` ADD COLUMN `chance_params_json` TEXT NULL COMMENT ''机会指数按省参数覆盖 JSON（σ先验/退档折减等）'' AFTER `gradient_preset_json`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'policy_rule_config'
    AND column_name = 'data_year_from'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `policy_rule_config` ADD COLUMN `data_year_from` SMALLINT NOT NULL DEFAULT 0 COMMENT ''该批次可用历史数据最早年份（=新高考首年）'' AFTER `chance_params_json`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- 3. 省份画像种子（8 省）
--    梯度预设依据：湖北省招办 15/15/15、海南省考试局 10/10/10、云南省招生考试院 20%/40%/40%，
--    其余省份无官方比例，由回测校准（文档 2.3 / 11 部分）。
-- ─────────────────────────────────────────────────────────────
INSERT INTO `province_profile`
(`province_code`, `province_name`, `subject_mode`, `new_gaokao_first_year`, `score_system`,
 `rank_tie_break_rule`, `official_source_name`, `official_source_url`, `enabled`)
VALUES
('GZ', '贵州', '3+1+2', 2024, 'RAW_750',
 '语数之和→语数单科最高→外语→首选→再选最高→再选次高（已核查）', '贵州省招生考试院', 'https://zsksy.guizhou.gov.cn/', 1),
('SC', '四川', '3+1+2', 2025, 'RAW_750',
 '语数之和→语数单科最高→外语→首选→再选最高→再选次高（已核查）', '四川省教育考试院', 'https://www.sceea.cn/', 1),
('HB', '湖北', '3+1+2', 2021, 'RAW_750',
 '待逐字核验', '湖北省教育考试院', 'http://www.hbea.edu.cn/', 1),
('AH', '安徽', '3+1+2', 2024, 'RAW_750',
 '语数之和→语数单科最高→外语→首选→再选最高→再选次高，兜底报名序号（已核查）', '安徽省教育招生考试院', 'https://www.ahzsks.cn/', 1),
('GX', '广西', '3+1+2', 2024, 'RAW_750',
 '语数之和→语数单科最高→外语→首选→再选最高→再选次高（已核查）', '广西壮族自治区招生考试院', 'https://www.gxeea.cn/', 1),
('HI', '海南', '3+3', 2020, 'STANDARD_900',
 '待逐字核验（标准分体系）', '海南省考试局', 'http://ea.hainan.gov.cn/', 1),
('YN', '云南', '3+1+2', 2025, 'RAW_750',
 '待逐字核验', '云南省招生考试院', 'https://www.ynzs.cn/', 1),
('HA', '河南', '3+1+2', 2025, 'RAW_750',
 '语数之和→语数单科最高→外语→首选→再选最高→再选次高（已核查）', '河南省教育考试院', 'http://www.haeea.cn/', 1)
ON DUPLICATE KEY UPDATE
  `province_name` = VALUES(`province_name`),
  `subject_mode` = VALUES(`subject_mode`),
  `new_gaokao_first_year` = VALUES(`new_gaokao_first_year`),
  `score_system` = VALUES(`score_system`),
  `rank_tie_break_rule` = VALUES(`rank_tie_break_rule`),
  `official_source_name` = VALUES(`official_source_name`),
  `official_source_url` = VALUES(`official_source_url`),
  `updated_at` = CURRENT_TIMESTAMP;

-- ─────────────────────────────────────────────────────────────
-- 4. 批次政策种子：7 个非贵州省份的普通类本科批
--    2026 行：全部 7 省（志愿数按 2026 官方核查：SC45/HB45/AH45/GX40/HI30/YN40/HA48）。
--    2025 行：仅 SC/HB/AH（与既有代码口径一致，其余省 2025 口径未核查不伪造）。
-- ─────────────────────────────────────────────────────────────
-- gradient_preset_json：4 档目标数量预设。湖北（省招办：冲稳保约 15/15/15）、海南（省考试局：10/10/10）、
-- 云南（省招生考试院：冲20%/稳40%/保40%）有官方建议比例；按"垫为保的下沿细分"拆成 4 档（对外展示垫并入保）。
-- 其余省份无官方比例，留 NULL 由 GradientAllocationEngine 按策略模式分配，后续经回测校准后再填。
INSERT INTO `policy_rule_config`
(`province`, `year`, `candidate_type`, `batch_code`, `batch_name`, `volunteer_mode`, `volunteer_unit_type`,
 `max_volunteer_count`, `major_per_school_count`, `major_per_group_count`, `has_adjustment`, `data_year_from`,
 `gradient_preset_json`,
 `filing_principle`, `admission_order`, `policy_status`,
 `official_source_title`, `official_source_url`, `official_source_text`, `enabled`)
VALUES
('SC', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '普通本科批B段', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
 45, 0, 6, 1, 2025,
 NULL,
 '位次优先、遵循志愿、一轮投档；官方按执行计划数1:1投档', '按考生志愿顺序逐组检索', 'pending_confirm',
 '四川省2026年普通高校招生实施规定（待人工复核原文）', 'https://www.sceea.cn/',
 '2026 联网核查：本科批B段45个院校专业组，组内6个专业+服从调剂；正式口径以四川省教育考试院文件为准。', 1),
('HB', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '本科普通批', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
 45, 0, 6, 1, 2021,
 '{"counts":{"冲":15,"稳":15,"保":11,"垫":4}}',
 '分数优先、遵循志愿、一轮投档；投档比例不超过105%', '按考生志愿顺序逐组检索', 'pending_confirm',
 '2026年湖北省普通高校阳光招生政策暨志愿填报问答（待人工复核原文）', 'http://www.hbea.edu.cn/',
 '2026 联网核查：本科普通批45个院校专业组，组内6个专业+服从调剂；专项/预科单设组混合填报。官方梯度建议约冲15/稳15/保15。', 1),
('AH', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '普通本科批次', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
 45, 0, 6, 1, 2024,
 NULL,
 '分数优先、遵循志愿、一轮投档', '按考生志愿顺序逐组检索', 'pending_confirm',
 '安徽省2026年普通高校招生工作实施办法（待人工复核原文）', 'https://www.ahzsks.cn/',
 '2026 联网核查：普通本科批45个院校专业组，组内6个专业+专业服从志愿。', 1),
('GX', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '本科普通批', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
 40, 0, 20, 1, 2024,
 NULL,
 '分数优先、遵循志愿、一轮投档；官方1:1投档', '按考生志愿顺序逐组检索', 'pending_confirm',
 '广西2026年普通高校招生考试和录取工作方案（待人工复核原文）', 'https://www.gxeea.cn/',
 '2026 联网核查：本科普通批40个院校专业组（非45），组内20个专业+组内调剂；官方明确1:1投档。', 1),
('HI', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '本科普通批', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
 30, 0, 6, 1, 2020,
 '{"counts":{"冲":10,"稳":10,"保":7,"垫":3}}',
 '分数优先、遵循志愿、一次投档；标准分900分制', '按考生志愿顺序逐组检索', 'pending_confirm',
 '2026年海南省普通高校招生本科批填报志愿公告（待人工复核原文）', 'http://ea.hainan.gov.cn/',
 '2026 联网核查：本科普通批30个院校专业组，组内6个专业+服从调剂；3+3选科、标准分制。官方梯度建议冲10/稳10/保10。', 1),
('YN', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '本科批', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
 40, 0, 10, 1, 2025,
 '{"counts":{"冲":8,"稳":16,"保":11,"垫":5}}',
 '分数优先、遵循志愿、一轮投档', '按考生志愿顺序逐组检索', 'pending_confirm',
 '云南省2026年普通高校招生网上填报志愿考生须知（待人工复核原文）', 'https://www.ynzs.cn/',
 '2026 联网核查：本科批40个院校专业组（非45），组内10个专业+专业服从院校调剂；专项资格+10、预科资格+10。官方梯度建议冲20%/稳40%/保40%。', 1),
('HA', 2026, '普通类', 'NORMAL_UNDERGRADUATE', '普通本科批', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
 48, 0, 6, 1, 2025,
 NULL,
 '分数优先、遵循志愿、一轮投档', '按考生志愿顺序逐组检索', 'pending_confirm',
 '河南省2026年普通高校招生工作相关规定（待人工复核原文）', 'http://www.haeea.cn/',
 '2026 联网核查：普通本科批48个院校专业组（非45），组内6个专业+服从调剂；同分密度全国最高，梯度需最保守；违约考生限报24个。', 1),
('SC', 2025, '普通类', 'NORMAL_UNDERGRADUATE', '普通本科批B段', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
 45, 0, 6, 1, 2025,
 NULL,
 '位次优先、遵循志愿、一轮投档', '按考生志愿顺序逐组检索', 'confirmed',
 '四川省2025年普通高校招生实施规定', 'https://www.sceea.cn/',
 '2025 为四川新高考首年，本科批B段45个院校专业组。', 1),
('HB', 2025, '普通类', 'NORMAL_UNDERGRADUATE', '本科普通批', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
 45, 0, 6, 1, 2021,
 '{"counts":{"冲":15,"稳":15,"保":11,"垫":4}}',
 '分数优先、遵循志愿、一轮投档', '按考生志愿顺序逐组检索', 'confirmed',
 '2025年湖北省普通高校招生工作实施办法', 'http://www.hbea.edu.cn/',
 '湖北2021年起新高考，本科普通批45个院校专业组。', 1),
('AH', 2025, '普通类', 'NORMAL_UNDERGRADUATE', '普通本科批次', '院校专业组平行志愿', 'PROFESSIONAL_GROUP_45',
 45, 0, 6, 1, 2024,
 NULL,
 '分数优先、遵循志愿、一轮投档', '按考生志愿顺序逐组检索', 'confirmed',
 '安徽省2025年普通高校招生工作实施办法', 'https://www.ahzsks.cn/',
 '安徽2024年起新高考，普通本科批45个院校专业组。', 1)
ON DUPLICATE KEY UPDATE
  `batch_name` = VALUES(`batch_name`),
  `volunteer_mode` = VALUES(`volunteer_mode`),
  `volunteer_unit_type` = VALUES(`volunteer_unit_type`),
  `max_volunteer_count` = VALUES(`max_volunteer_count`),
  `major_per_group_count` = VALUES(`major_per_group_count`),
  `has_adjustment` = VALUES(`has_adjustment`),
  `data_year_from` = VALUES(`data_year_from`),
  `gradient_preset_json` = VALUES(`gradient_preset_json`),
  `filing_principle` = VALUES(`filing_principle`),
  `admission_order` = VALUES(`admission_order`),
  `policy_status` = VALUES(`policy_status`),
  `official_source_title` = VALUES(`official_source_title`),
  `official_source_url` = VALUES(`official_source_url`),
  `official_source_text` = VALUES(`official_source_text`),
  `enabled` = VALUES(`enabled`),
  `updated_at` = CURRENT_TIMESTAMP;

-- ─────────────────────────────────────────────────────────────
-- 5. 回填贵州既有行的志愿单位类型与数据窗口（不改其余字段）
-- ─────────────────────────────────────────────────────────────
UPDATE `policy_rule_config`
SET `volunteer_unit_type` = 'MAJOR_96', `data_year_from` = 2024
WHERE `province` = 'GZ' AND (`volunteer_unit_type` = '' OR `volunteer_unit_type` IS NULL);

-- ─────────────────────────────────────────────────────────────
-- 6. 候选检索性能索引（阶段 4）：
--    findMajorCandidates / findCandidates 的谓词是 subject_type + min_rank BETWEEN，
--    16 万行的 data_major_score_gz 此前没有匹配的复合索引。守卫式创建，表缺失时跳过。
-- ─────────────────────────────────────────────────────────────
SET @tbl_exists := (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = @table_schema AND table_name = 'data_major_score_gz'
);
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @table_schema AND table_name = 'data_major_score_gz'
    AND index_name = 'idx_subject_minrank'
);
SET @sql := IF(@tbl_exists = 0 OR @idx_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_major_score_gz` ADD INDEX `idx_subject_minrank` (`subject_type`, `min_rank`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @tbl_exists := (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = @table_schema AND table_name = 'data_score_line_gz'
);
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @table_schema AND table_name = 'data_score_line_gz'
    AND index_name = 'idx_subject_minrank'
);
SET @sql := IF(@tbl_exists = 0 OR @idx_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_score_line_gz` ADD INDEX `idx_subject_minrank` (`subject_type`, `min_rank`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- 6.5 分数线表与生产/导出文件的结构对齐：
--     scripts/data/export/major_scores.sql 使用 recruit_type 列、
--     parallel_scores.sql 使用 notes 列，生产表已有但 schema.sql 缺失，
--     新环境重放导入会失败。守卫式补列。
-- ─────────────────────────────────────────────────────────────
SET @tbl_exists := (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = @table_schema AND table_name = 'data_score_line_gz'
);
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'data_score_line_gz'
    AND column_name = 'recruit_type'
);
SET @sql := IF(@tbl_exists = 0 OR @col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_score_line_gz` ADD COLUMN `recruit_type` VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''招生类型（中外合作办学等）'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @tbl_exists := (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = @table_schema AND table_name = 'data_major_score_gz'
);
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'data_major_score_gz'
    AND column_name = 'notes'
);
SET @sql := IF(@tbl_exists = 0 OR @col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `data_major_score_gz` ADD COLUMN `notes` TEXT NULL COMMENT ''备注'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- 7. 回测报告支持多省份（组级调档线口径），补省份列
-- ─────────────────────────────────────────────────────────────
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @table_schema AND table_name = 'algo_backtest_report'
    AND column_name = 'province_code'
);
SET @sql := IF(@col_exists > 0,
  'SELECT 1',
  'ALTER TABLE `algo_backtest_report` ADD COLUMN `province_code` VARCHAR(10) NOT NULL DEFAULT ''GZ'' COMMENT ''省份代码'' AFTER `id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- 8. 河南保守参数（chance_params_json 按省覆盖首例）：
--    2025 首年新高考 + 同分密度全国最高（物理类本科上线 34.8 万），
--    log-rank σ 下限按文档 11.8 上调为全表最大；生产回测后可再校准。
-- ─────────────────────────────────────────────────────────────
UPDATE `policy_rule_config`
SET `chance_params_json` = '{"sigmaMin":0.12}'
WHERE `province` = 'HA' AND `year` = 2026 AND `batch_code` = 'NORMAL_UNDERGRADUATE'
  AND (`chance_params_json` IS NULL OR `chance_params_json` = '');
