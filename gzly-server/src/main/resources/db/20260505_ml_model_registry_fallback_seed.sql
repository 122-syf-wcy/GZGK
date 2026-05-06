-- GZLY ml_model_registry fallback 兜底记录：让 MlPredictionService 在无真实模型时仍能给出 modelInfo.modelVersion。
-- 幂等：依赖 uk_ml_model_version (model_name, model_version)。

INSERT INTO ml_model_registry
  (model_name, model_type, model_version, train_year_range, train_data_count,
   feature_schema_json, metrics_json, model_file_path, status, activated_at)
VALUES
  ('chance-score', 'rule_fallback', 'fallback-v1.0.0', '2021-2025', 0,
   JSON_OBJECT(
     'features', JSON_ARRAY('rankDiff','planChangeRate','rankVolatility3y','dataConfidence','majorHotScore','schoolLevel'),
     'note', '规则降级模型，无真实训练，仅做位次分布 + 经验命中率 Beta 平滑'),
   JSON_OBJECT('mae', NULL, 'r2', NULL, 'note', 'fallback rule engine, no metrics'),
   '', 'active', CURRENT_TIMESTAMP),
  ('rank-prediction', 'rule_fallback', 'fallback-v1.0.0', '2021-2025', 0,
   JSON_OBJECT(
     'features', JSON_ARRAY('latestMinRank','avgMinRank3y','medianMinRank3y','rankVolatility3y','planChangeRate','firstRoundFull','hasSupplement'),
     'note', '规则降级位次预测，使用近三年最低位次中位数 + 计划变化系数'),
   JSON_OBJECT('mae', NULL, 'r2', NULL, 'note', 'fallback rule engine, no metrics'),
   '', 'active', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
  model_type           = VALUES(model_type),
  train_year_range     = VALUES(train_year_range),
  train_data_count     = VALUES(train_data_count),
  feature_schema_json  = VALUES(feature_schema_json),
  metrics_json         = VALUES(metrics_json),
  model_file_path      = VALUES(model_file_path),
  status               = VALUES(status),
  activated_at         = COALESCE(activated_at, VALUES(activated_at));
