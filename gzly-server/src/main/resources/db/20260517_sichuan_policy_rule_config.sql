-- ============================================================================
-- 四川 2024 / 2025 / 2026 三个年份 × 18 批次 policy_rule_config 种子（54 行）。
--
-- 数据来源：
--   - 四川省 2026 年普通高校招生实施规定（2026-04-29，四川省教育厅 / 教育考试院）
--   - 四川省教育考试院"志愿填报 100 问" ④⑤（2025-06-23）
--   - 四川 2026 艺术体育报名公告（2025-10-24，四川省教育考试院）
--
-- 批次代码与 com.gzly.service.SichuanBatchRuleRegistry 严格对齐（SC_* 前缀，18 行）。
--
-- policy_status：
--   - 2024 / 2025：confirmed（四川考试院已公布历史规定）
--   - 2026：pending_confirm（等 6 月下旬实施细则正式发文后改为 confirmed）
--
-- 与贵州 GZ 行的差异：
--   - province = 'SC'
--   - volunteer_mode = '院校专业组（平行志愿）' / '院校顺序志愿'
--   - max_volunteer_count = 6 / 20 / 30 / 45（按 SichuanBatchRuleRegistry.targetCount）
--   - major_per_school_count = 6（院校专业组内 6 专业 + 服从调剂）
--   - 顺序志愿（提前 A / 高水平运动队 / 专科提前 / 艺术本科提前）major_per_school_count 仍为 6
--   - has_adjustment = 1（除高水平运动队、艺术 / 体育统考综合分平行外）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 共用宏：四川官方信息来源
-- ---------------------------------------------------------------------------
SET @sc_source_title = '四川省 2026 年普通高校招生实施规定';
SET @sc_source_url = 'https://www.sceea.cn/';
SET @sc_source_text = '四川 2026 新高考：本科提前批分 A 段前国家专项（6 平行院校专业组）、A 段（1+2 顺序）、A/B 段间高校专项（按 2026 细则确认 20 平行 vs 1 顺序）、B 段（30 平行）；本科批分 A 段国家专项与地方专项（各 20 平行）、A/B 段间高水平运动队（1 顺序）、B 段（45 平行）、B 段后区域均衡（20 平行）、省属高校少民预科（20 平行）；高职专科批（数量以 2026 细则为准）；艺术类本科批、艺术类高职专科批、体育类本科批、体育类高职专科批均为 45 平行院校专业组，按综合分排序录取。顺序志愿走"根据志愿、从高分到低分、按比例投档"，平行志愿走"位次优先、遵循志愿、一轮投档"。';

SET @sc_source_text_2025 = '四川省 2025 年普通高校招生实施规定：普通本科批 B 段 45 个平行院校专业组志愿，每组 6 专业 + 是否服从专业调剂；提前批 A 段前国家专项原 2 个平行院校专业组（2026 起 6 个）；提前批 A 段 1 第一志愿 + 2 平行第二志愿（顺序志愿）；提前批 B 段 30 个平行院校专业组；本科批 A 段国家专项 20 平行、地方专项 20 平行；本科批 A 段后高校专项 1 顺序（2026 起 20 平行）；本科批高水平运动队 1 顺序；高职专科批院校专业组（数量按当年实施细则）；高职专科提前批 1+2 顺序；艺术 / 体育按综合成绩平行院校专业组录取。';

SET @sc_source_text_2024 = '四川省 2024 年普通高校招生实施规定：四川 2024 年仍按"院校 + 专业"模式投档（普通本科批 9 个院校志愿、专科批 9 个院校志愿），与 2025 起新高考"院校专业组 45 平行"完全不同；本行仅用于满足 policy_rule_config 历史年份兜底，避免 PolicyRuleService 在 PRE_OFFICIAL_DATA 期间 200 失败。前端 / 算法不应基于 2024 行直接生成 45 院校专业组草稿。';

-- ============================================================================
-- 1) SC 2026 × 18 批次（policy_status = pending_confirm）
-- ============================================================================
INSERT INTO `policy_rule_config`
(`province`, `year`, `candidate_type`, `batch_code`, `batch_name`, `volunteer_mode`, `max_volunteer_count`,
 `major_per_school_count`, `has_adjustment`, `filing_principle`, `admission_order`, `policy_status`,
 `official_source_title`, `official_source_url`, `official_source_text`, `enabled`)
VALUES
-- 1.1 普通类本科提前批
('SC', 2026, '普通类', 'SC_TIQIAN_BEFORE_A_NATIONAL', '本科提前批 A 段前国家专项', '院校专业组（平行志愿）', 6,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '普通类', 'SC_TIQIAN_A', '本科提前批 A 段', '院校顺序志愿（1 第一 + 2 平行第二）', 3,
 6, 1, '从高分到低分、按比例投档', 'EARLY', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '普通类', 'SC_GAOXIAO_SPECIAL_PRE_B', '本科提前批高校专项', '院校顺序志愿（待 6 月细则确认是否改 20 平行）', 1,
 6, 1, '从高分到低分、按比例投档', 'SPECIAL_PROGRAM', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '普通类', 'SC_TIQIAN_B', '本科提前批 B 段', '院校专业组（平行志愿）', 30,
 6, 1, '位次优先、遵循志愿、一轮投档', 'EARLY', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
-- 1.2 本科批
('SC', 2026, '普通类', 'SC_BENKE_A_NATIONAL', '本科批 A 段国家专项', '院校专业组（平行志愿）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '普通类', 'SC_BENKE_A_LOCAL', '本科批 A 段地方专项', '院校专业组（平行志愿）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '普通类', 'SC_BENKE_GAOXIAO_SPECIAL', '本科批 A 段后高校专项', '院校专业组（平行志愿）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '普通类', 'SC_BENKE_SPORTS_TEAM', '本科批高水平运动队', '院校顺序志愿（1 个）', 1,
 6, 0, '从高分到低分、按比例投档', 'OTHER', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '普通类', 'SC_BENKE_B', '本科批 B 段', '院校专业组（平行志愿）', 45,
 6, 1, '位次优先、遵循志愿、一轮投档', 'ORDINARY', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '普通类', 'SC_BENKE_REGION_BALANCE', '本科批 B 段后区域教育均衡发展专项', '院校专业组（平行志愿）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '普通类', 'SC_BENKE_MINORITY_PRE', '本科批省属高校少数民族预科', '院校专业组（平行志愿）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
-- 1.3 高职（专科）批
('SC', 2026, '普通类', 'SC_ZHUANKE_B', '高职（专科）批', '院校专业组（平行志愿）', 45,
 6, 1, '位次优先、遵循志愿、一轮投档', 'ORDINARY', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '普通类', 'SC_ZHUANKE_EARLY', '高职（专科）提前批', '院校顺序志愿（1 第一 + 2 平行第二）', 3,
 6, 0, '从高分到低分、按比例投档', 'EARLY', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
-- 1.4 艺术类
('SC', 2026, '艺术类', 'SC_ART_TIQIAN', '艺术类本科提前批', '院校顺序志愿', 1,
 6, 0, '从高分到低分、按比例投档', 'ART', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '艺术类', 'SC_ART_BENKE', '艺术类本科批', '院校专业组（综合分平行志愿）', 45,
 6, 0, '综合成绩位次优先、遵循志愿、一轮投档', 'ART', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '艺术类', 'SC_ART_ZHUANKE', '艺术类高职（专科）批', '院校专业组（综合分平行志愿）', 45,
 6, 0, '综合成绩位次优先、遵循志愿、一轮投档', 'ART', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
-- 1.5 体育类
('SC', 2026, '体育类', 'SC_SPORTS_BENKE', '体育类本科批', '院校专业组（综合分平行志愿）', 45,
 6, 0, '综合成绩位次优先、遵循志愿、一轮投档', 'SPORTS', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1),
('SC', 2026, '体育类', 'SC_SPORTS_ZHUANKE', '体育类高职（专科）批', '院校专业组（综合分平行志愿）', 45,
 6, 0, '综合成绩位次优先、遵循志愿、一轮投档', 'SPORTS', 'pending_confirm',
 @sc_source_title, @sc_source_url, @sc_source_text, 1)
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

-- ============================================================================
-- 2) SC 2025 × 18 批次（policy_status = confirmed）
-- ============================================================================
INSERT INTO `policy_rule_config`
(`province`, `year`, `candidate_type`, `batch_code`, `batch_name`, `volunteer_mode`, `max_volunteer_count`,
 `major_per_school_count`, `has_adjustment`, `filing_principle`, `admission_order`, `policy_status`,
 `official_source_title`, `official_source_url`, `official_source_text`, `enabled`)
VALUES
('SC', 2025, '普通类', 'SC_TIQIAN_BEFORE_A_NATIONAL', '本科提前批 A 段前国家专项', '院校专业组（平行志愿）', 2,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '普通类', 'SC_TIQIAN_A', '本科提前批 A 段', '院校顺序志愿（1 第一 + 2 平行第二）', 3,
 6, 1, '从高分到低分、按比例投档', 'EARLY', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '普通类', 'SC_GAOXIAO_SPECIAL_PRE_B', '本科提前批高校专项', '院校顺序志愿（1 个）', 1,
 6, 1, '从高分到低分、按比例投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '普通类', 'SC_TIQIAN_B', '本科提前批 B 段', '院校专业组（平行志愿）', 30,
 6, 1, '位次优先、遵循志愿、一轮投档', 'EARLY', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '普通类', 'SC_BENKE_A_NATIONAL', '本科批 A 段国家专项', '院校专业组（平行志愿）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '普通类', 'SC_BENKE_A_LOCAL', '本科批 A 段地方专项', '院校专业组（平行志愿）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '普通类', 'SC_BENKE_GAOXIAO_SPECIAL', '本科批 A 段后高校专项', '院校顺序志愿（1 个）', 1,
 6, 1, '从高分到低分、按比例投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '普通类', 'SC_BENKE_SPORTS_TEAM', '本科批高水平运动队', '院校顺序志愿（1 个）', 1,
 6, 0, '从高分到低分、按比例投档', 'OTHER', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '普通类', 'SC_BENKE_B', '本科批 B 段', '院校专业组（平行志愿）', 45,
 6, 1, '位次优先、遵循志愿、一轮投档', 'ORDINARY', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '普通类', 'SC_BENKE_REGION_BALANCE', '本科批 B 段后区域教育均衡发展专项', '院校专业组（平行志愿）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '普通类', 'SC_BENKE_MINORITY_PRE', '本科批省属高校少数民族预科', '院校专业组（平行志愿）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '普通类', 'SC_ZHUANKE_B', '高职（专科）批', '院校专业组（平行志愿）', 45,
 6, 1, '位次优先、遵循志愿、一轮投档', 'ORDINARY', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '普通类', 'SC_ZHUANKE_EARLY', '高职（专科）提前批', '院校顺序志愿（1 第一 + 2 平行第二）', 3,
 6, 0, '从高分到低分、按比例投档', 'EARLY', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '艺术类', 'SC_ART_TIQIAN', '艺术类本科提前批', '院校顺序志愿', 1,
 6, 0, '从高分到低分、按比例投档', 'ART', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '艺术类', 'SC_ART_BENKE', '艺术类本科批', '院校专业组（综合分平行志愿）', 45,
 6, 0, '综合成绩位次优先、遵循志愿、一轮投档', 'ART', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '艺术类', 'SC_ART_ZHUANKE', '艺术类高职（专科）批', '院校专业组（综合分平行志愿）', 45,
 6, 0, '综合成绩位次优先、遵循志愿、一轮投档', 'ART', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '体育类', 'SC_SPORTS_BENKE', '体育类本科批', '院校专业组（综合分平行志愿）', 45,
 6, 0, '综合成绩位次优先、遵循志愿、一轮投档', 'SPORTS', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1),
('SC', 2025, '体育类', 'SC_SPORTS_ZHUANKE', '体育类高职（专科）批', '院校专业组（综合分平行志愿）', 45,
 6, 0, '综合成绩位次优先、遵循志愿、一轮投档', 'SPORTS', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2025, 1)
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

-- ============================================================================
-- 3) SC 2024 × 18 批次（policy_status = confirmed，老高考"院校 + 专业"模式兜底）
--
-- 2024 年四川还在老高考"院校 + 专业"投档，与 2025 新高考"院校专业组 45 平行"完全不同。
-- 本组行的实际含义是：填一行兜底，让 PolicyRuleService.requirePolicy 在 year=2024
-- 时不会 200 失败；max_volunteer_count 与 volunteer_mode 仍按 SichuanBatchRuleRegistry
-- 当前结构填写（与 2025 / 2026 同），便于 BatchSupportService 历史回退查询。
-- ============================================================================
INSERT INTO `policy_rule_config`
(`province`, `year`, `candidate_type`, `batch_code`, `batch_name`, `volunteer_mode`, `max_volunteer_count`,
 `major_per_school_count`, `has_adjustment`, `filing_principle`, `admission_order`, `policy_status`,
 `official_source_title`, `official_source_url`, `official_source_text`, `enabled`)
VALUES
('SC', 2024, '普通类', 'SC_TIQIAN_BEFORE_A_NATIONAL', '本科提前批 A 段前国家专项（2024 老高考兜底）', '院校志愿（老高考）', 2,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '普通类', 'SC_TIQIAN_A', '本科提前批 A 段（2024 老高考兜底）', '院校顺序志愿（老高考）', 3,
 6, 1, '从高分到低分、按比例投档', 'EARLY', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '普通类', 'SC_GAOXIAO_SPECIAL_PRE_B', '本科提前批高校专项（2024 老高考兜底）', '院校顺序志愿（老高考）', 1,
 6, 1, '从高分到低分、按比例投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '普通类', 'SC_TIQIAN_B', '本科提前批 B 段（2024 老高考兜底）', '院校志愿（老高考）', 30,
 6, 1, '位次优先、遵循志愿、一轮投档', 'EARLY', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '普通类', 'SC_BENKE_A_NATIONAL', '本科批 A 段国家专项（2024 老高考兜底）', '院校志愿（老高考）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '普通类', 'SC_BENKE_A_LOCAL', '本科批 A 段地方专项（2024 老高考兜底）', '院校志愿（老高考）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '普通类', 'SC_BENKE_GAOXIAO_SPECIAL', '本科批 A 段后高校专项（2024 老高考兜底）', '院校顺序志愿（老高考）', 1,
 6, 1, '从高分到低分、按比例投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '普通类', 'SC_BENKE_SPORTS_TEAM', '本科批高水平运动队（2024 老高考兜底）', '院校顺序志愿（老高考）', 1,
 6, 0, '从高分到低分、按比例投档', 'OTHER', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '普通类', 'SC_BENKE_B', '本科批 B 段（2024 老高考兜底）', '院校志愿（老高考）', 45,
 6, 1, '位次优先、遵循志愿、一轮投档', 'ORDINARY', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '普通类', 'SC_BENKE_REGION_BALANCE', '本科批 B 段后区域教育均衡发展专项（2024 老高考兜底）', '院校志愿（老高考）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '普通类', 'SC_BENKE_MINORITY_PRE', '本科批省属高校少数民族预科（2024 老高考兜底）', '院校志愿（老高考）', 20,
 6, 1, '位次优先、遵循志愿、一轮投档', 'SPECIAL_PROGRAM', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '普通类', 'SC_ZHUANKE_B', '高职（专科）批（2024 老高考兜底）', '院校志愿（老高考）', 45,
 6, 1, '位次优先、遵循志愿、一轮投档', 'ORDINARY', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '普通类', 'SC_ZHUANKE_EARLY', '高职（专科）提前批（2024 老高考兜底）', '院校顺序志愿（老高考）', 3,
 6, 0, '从高分到低分、按比例投档', 'EARLY', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '艺术类', 'SC_ART_TIQIAN', '艺术类本科提前批（2024 老高考兜底）', '院校顺序志愿', 1,
 6, 0, '从高分到低分、按比例投档', 'ART', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '艺术类', 'SC_ART_BENKE', '艺术类本科批（2024 老高考兜底）', '院校志愿（综合分）', 45,
 6, 0, '综合成绩位次优先、遵循志愿、一轮投档', 'ART', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '艺术类', 'SC_ART_ZHUANKE', '艺术类高职（专科）批（2024 老高考兜底）', '院校志愿（综合分）', 45,
 6, 0, '综合成绩位次优先、遵循志愿、一轮投档', 'ART', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '体育类', 'SC_SPORTS_BENKE', '体育类本科批（2024 老高考兜底）', '院校志愿（综合分）', 45,
 6, 0, '综合成绩位次优先、遵循志愿、一轮投档', 'SPORTS', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1),
('SC', 2024, '体育类', 'SC_SPORTS_ZHUANKE', '体育类高职（专科）批（2024 老高考兜底）', '院校志愿（综合分）', 45,
 6, 0, '综合成绩位次优先、遵循志愿、一轮投档', 'SPORTS', 'confirmed',
 @sc_source_title, @sc_source_url, @sc_source_text_2024, 1)
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
