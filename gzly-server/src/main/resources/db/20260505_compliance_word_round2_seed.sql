-- GZLY 合规敏感词补齐 round2：覆盖需求文档第 2/18 节剩余口语变体与兜底替换规则。
-- 幂等：使用 INSERT ... ON DUPLICATE KEY UPDATE，依赖 (word) 唯一索引；如表无 uk 则使用 NOT EXISTS 保护。

-- 1) 补 word 唯一索引（若不存在）。MySQL 8 不支持 IF NOT EXISTS for INDEX，先尝试创建忽略错误。
SET @stmt := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.statistics
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='compliance_sensitive_word' AND INDEX_NAME='uk_compliance_word') = 0,
  'ALTER TABLE compliance_sensitive_word ADD UNIQUE KEY uk_compliance_word (word)',
  'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) 补 round2 词条。
INSERT INTO compliance_sensitive_word (word, word_type, severity, replacement, enabled)
VALUES
  ('保底必上', 'admission_commitment', 'high', '可作为兜底参考志愿', 1),
  ('保你上', 'admission_commitment', 'high', '不可承诺录取结果', 1),
  ('保你录', 'admission_commitment', 'high', '不可承诺录取结果', 1),
  ('稳过', 'admission_commitment', 'high', '风险相对较低', 1),
  ('稳进', 'admission_commitment', 'high', '风险相对较低', 1),
  ('稳冲', 'admission_commitment', 'medium', '建议作为冲刺参考', 1),
  ('十拿九稳', 'admission_commitment', 'high', '具备一定参考优势', 1),
  ('万无一失', 'admission_commitment', 'high', '仍需结合官方数据谨慎参考', 1),
  ('绝对能上', 'admission_commitment', 'high', '具备一定参考优势', 1),
  ('必中', 'admission_commitment', 'high', '具备一定参考优势', 1),
  ('不会落榜', 'absolute_safety', 'high', '仍需关注整体风险', 1)
ON DUPLICATE KEY UPDATE
  word_type   = VALUES(word_type),
  severity    = VALUES(severity),
  replacement = VALUES(replacement),
  enabled     = VALUES(enabled),
  updated_at  = CURRENT_TIMESTAMP;
