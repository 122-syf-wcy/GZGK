-- 推荐算法回测报告表
-- 记录"用历史截止年数据推荐、用后一年真实录取结果验证"的离线回测结果，
-- 供 /api/admin/backtest 查询与调参对比使用。

CREATE TABLE IF NOT EXISTS `algo_backtest_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `evaluation_year` INT NOT NULL COMMENT '真实结果年份',
    `cutoff_year` INT NOT NULL COMMENT '推荐可用数据截止年份',
    `subject_type` VARCHAR(16) NOT NULL COMMENT '物理类/历史类',
    `strategy_mode` VARCHAR(16) NOT NULL COMMENT '保守型/均衡型/冲刺型',
    `sample_candidates` INT NOT NULL DEFAULT 0 COMMENT '模拟考生位次数量',
    `evaluated_pairs` INT NOT NULL DEFAULT 0 COMMENT '评估的考生-候选对数',
    `coverage_ratio` DOUBLE NULL COMMENT '有真实结果对照的候选占比 0-1',
    `overall_hit_rate` DOUBLE NULL COMMENT '整体达线率 0-1',
    `brier_score` DOUBLE NULL COMMENT '机会指数校准 Brier 分数',
    `gradient_stats_json` TEXT NULL COMMENT '各梯度命中统计 JSON',
    `calibration_json` TEXT NULL COMMENT '机会指数分桶校准 JSON',
    `params_snapshot_json` TEXT NULL COMMENT '引擎参数与口径快照 JSON',
    `note` VARCHAR(255) NULL COMMENT '触发说明',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_backtest_year_subject` (`evaluation_year`, `subject_type`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '推荐算法回测报告';
