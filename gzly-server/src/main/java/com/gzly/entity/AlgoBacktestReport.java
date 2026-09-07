package com.gzly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推荐算法回测报告。
 *
 * 一行代表一次"用 cutoffYear 及以前数据推荐、用 evaluationYear 真实录取结果验证"的离线回测。
 */
@Data
@TableName("algo_backtest_report")
public class AlgoBacktestReport {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 省份代码（20260812 迁移新增；专业组省份用组级调档线口径回测）。 */
    private String provinceCode;

    /** 真实结果年份（ground truth 年）。 */
    private Integer evaluationYear;

    /** 数据截止年份，推荐只允许使用 <= cutoffYear 的数据。 */
    private Integer cutoffYear;

    private String subjectType;

    private String strategyMode;

    /** 参与回测的模拟考生位次数量。 */
    private Integer sampleCandidates;

    /** 实际评估的 (考生位次, 志愿候选) 对数。 */
    private Integer evaluatedPairs;

    /** 有真实结果可对照的候选占比 0-1。 */
    private Double coverageRatio;

    /** 全部评估对的达线率 0-1。 */
    private Double overallHitRate;

    /** 机会指数概率校准误差（Brier 分数，越低越好）。 */
    private Double brierScore;

    /** 各梯度命中统计 JSON。 */
    private String gradientStatsJson;

    /** 机会指数分桶校准 JSON。 */
    private String calibrationJson;

    /** 本次回测使用的引擎参数与口径快照 JSON。 */
    private String paramsSnapshotJson;

    /** 触发说明（人工备注）。 */
    private String note;

    private LocalDateTime createdAt;
}
