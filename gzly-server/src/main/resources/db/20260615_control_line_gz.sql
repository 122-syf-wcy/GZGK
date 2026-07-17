-- 贵州省控线（批次控制线：本科线 / 特殊类型招生控制线 / 高职专科线）结构化表 + 2025 官方数据
-- 数据来源：贵州省教育高质量发展委员会 2025-06-25 划定公告
--   官方链接：https://mzt.guizhou.gov.cn/xwzx/mzyw/202506/t20250625_88186360.html
-- 手动执行：mysql -u<user> -p <db> < 20260615_control_line_gz.sql
-- 说明：建表后 GuizhouAdapter.queryControlLine 即按 year+subject_type 返回 AVAILABLE，
--      /api/score-lines/gz/control-lines 不再 MISSING；App 分布图自动叠加本科线/特控线。

CREATE TABLE IF NOT EXISTS `data_batch_line_gz` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `year` SMALLINT NOT NULL,
  `candidate_type` VARCHAR(30) NOT NULL DEFAULT '普通类',
  `subject_type` VARCHAR(10) NOT NULL COMMENT '物理类 / 历史类',
  `batch_code` VARCHAR(60) NOT NULL,
  `batch_name` VARCHAR(100) NOT NULL COMMENT '本科批 / 特殊类型招生控制线 / 高职（专科）批',
  `control_score` SMALLINT NOT NULL,
  `source_name` VARCHAR(120) NOT NULL DEFAULT '贵州省招生考试院',
  `source_url` VARCHAR(500) NOT NULL DEFAULT '',
  `source_page_url` VARCHAR(500) NOT NULL DEFAULT '',
  `source_file` VARCHAR(240) NOT NULL DEFAULT '',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_year_subject_batch` (`year`, `candidate_type`, `subject_type`, `batch_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贵州批次控制线（省控线）';

INSERT INTO `data_batch_line_gz`
  (`year`,`candidate_type`,`subject_type`,`batch_code`,`batch_name`,`control_score`,`source_name`,`source_url`)
VALUES
  (2025,'普通类','物理类','NORMAL_UNDERGRADUATE','本科批',387,'贵州省招生考试院','https://mzt.guizhou.gov.cn/xwzx/mzyw/202506/t20250625_88186360.html'),
  (2025,'普通类','物理类','SPECIAL_TYPE_CONTROL','特殊类型招生控制线',483,'贵州省招生考试院','https://mzt.guizhou.gov.cn/xwzx/mzyw/202506/t20250625_88186360.html'),
  (2025,'普通类','物理类','VOCATIONAL','高职（专科）批',180,'贵州省招生考试院','https://mzt.guizhou.gov.cn/xwzx/mzyw/202506/t20250625_88186360.html'),
  (2025,'普通类','历史类','NORMAL_UNDERGRADUATE','本科批',458,'贵州省招生考试院','https://mzt.guizhou.gov.cn/xwzx/mzyw/202506/t20250625_88186360.html'),
  (2025,'普通类','历史类','SPECIAL_TYPE_CONTROL','特殊类型招生控制线',517,'贵州省招生考试院','https://mzt.guizhou.gov.cn/xwzx/mzyw/202506/t20250625_88186360.html'),
  (2025,'普通类','历史类','VOCATIONAL','高职（专科）批',180,'贵州省招生考试院','https://mzt.guizhou.gov.cn/xwzx/mzyw/202506/t20250625_88186360.html')
ON DUPLICATE KEY UPDATE
  `batch_name`=VALUES(`batch_name`),
  `control_score`=VALUES(`control_score`),
  `source_name`=VALUES(`source_name`),
  `source_url`=VALUES(`source_url`),
  `updated_at`=CURRENT_TIMESTAMP;
