ALTER TABLE `uni_official_link`
  ADD COLUMN `tuition_summary` TEXT NULL COMMENT '收费标准摘要',
  ADD COLUMN `major_catalog_summary` TEXT NULL COMMENT '专业目录摘要',
  ADD COLUMN `adjustment_rule` TEXT NULL COMMENT '调剂规则',
  ADD COLUMN `foreign_language_rule` TEXT NULL COMMENT '外语语种要求',
  ADD COLUMN `physical_exam_rule` TEXT NULL COMMENT '体检限制',
  ADD COLUMN `single_subject_rule` TEXT NULL COMMENT '单科成绩要求',
  ADD COLUMN `parser_notes` TEXT NULL COMMENT '解析备注',
  ADD COLUMN `parse_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未解析 1=已解析 2=待复核',
  ADD COLUMN `last_parsed_at` DATETIME NULL COMMENT '最近解析时间';
