CREATE TABLE IF NOT EXISTS `sys_ai_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `provider_name` VARCHAR(100) NOT NULL DEFAULT 'OpenAI兼容服务' COMMENT '服务商名称',
  `base_url` VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'OpenAI兼容接口Base URL',
  `api_key` VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'API Key，仅后端使用，不返回前端',
  `chat_model` VARCHAR(100) NOT NULL DEFAULT 'gpt-4o-mini' COMMENT '志愿AI解读模型',
  `review_model` VARCHAR(100) NOT NULL DEFAULT 'gpt-4o-mini' COMMENT '文本审核模型',
  `vision_model` VARCHAR(100) NOT NULL DEFAULT 'gpt-4o' COMMENT '图片审核模型',
  `max_tokens` INT NOT NULL DEFAULT 2600 COMMENT '志愿AI解读最大输出Token',
  `temperature` DOUBLE NOT NULL DEFAULT 0.7 COMMENT '志愿AI解读采样温度',
  `system_prompt` TEXT COMMENT '志愿AI解读系统提示词',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用AI服务：0停用，1启用',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_config_enabled` (`enabled`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI服务配置表';
