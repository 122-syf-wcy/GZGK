package com.gzly.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 志愿生成与 AI 解读的关键监控指标采集器。
 * <p>
 * 采用进程内 ConcurrentHashMap + AtomicLong 累加，无第三方依赖；
 * 通过 {@link #snapshot()} 暴露当前累计值，可被管理后台或 Actuator 端点读取。
 * 后续接入 Micrometer/Prometheus 时只需在该类内部替换实现，调用点保持不变。
 */
@Slf4j
@Component
public class VolunteerMetricsRecorder {

    public static final String GENERATE_TOTAL = "volunteer.generate.total";
    public static final String GENERATE_SUCCESS = "volunteer.generate.success";
    public static final String GENERATE_FAILURE = "volunteer.generate.failure";
    public static final String GENERATE_INCOMPLETE = "volunteer.generate.incomplete"; // 不足 96 条
    public static final String GENERATE_COST_MS_TOTAL = "volunteer.generate.cost_ms_total";
    public static final String GENERATE_COST_MS_MAX = "volunteer.generate.cost_ms_max";
    public static final String DATA_QUALITY_WARNING_TRIGGERED = "volunteer.data_quality_warning.triggered";
    public static final String MANUAL_REVIEW_TRIGGERED = "volunteer.manual_review.triggered";
    public static final String AI_ANALYSIS_TOTAL = "volunteer.ai.total";
    public static final String AI_ANALYSIS_FAILURE = "volunteer.ai.failure";
    public static final String AI_ADVISOR_CHAT_TOTAL = "volunteer.ai.advisor_chat.total";
    public static final String AI_ADVISOR_CHAT_FAILURE = "volunteer.ai.advisor_chat.failure";
    // === 算法报告 P1 评估指标（V7.38 新增） ===
    /** 规则前置被绕过的累计次数（任意主列表包含非 NORMAL 条目时 +1，红线指标）。 */
    public static final String RULE_VIOLATION_TRIGGERED = "volunteer.rule_violation.triggered";
    /** 规则前置在所有生成中的样本总数（用作 ruleViolationRate 的分母）。 */
    public static final String RULE_VIOLATION_SAMPLE_TOTAL = "volunteer.rule_violation.sample_total";
    /** overRiskExposure 超过 10% 的生成累计次数。 */
    public static final String OVER_RISK_EXPOSURE_TRIGGERED = "volunteer.over_risk_exposure.triggered";
    /** firstTwentyHitRate 低于基线的生成累计次数（基线默认 0.30）。 */
    public static final String FIRST_TWENTY_HIT_RATE_LOW = "volunteer.first_twenty_hit_rate.low";

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public void incr(String name) {
        increment(name, 1);
    }

    public void increment(String name, long delta) {
        if (name == null || name.isBlank()) return;
        counters.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
    }

    /**
     * 累加耗时并维护峰值。调用方传入毫秒数。
     */
    public void recordCost(long costMs) {
        if (costMs < 0) costMs = 0;
        counters.computeIfAbsent(GENERATE_COST_MS_TOTAL, k -> new AtomicLong()).addAndGet(costMs);
        AtomicLong max = gauges.computeIfAbsent(GENERATE_COST_MS_MAX, k -> new AtomicLong());
        long current;
        do {
            current = max.get();
            if (costMs <= current) {
                return;
            }
        } while (!max.compareAndSet(current, costMs));
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        counters.forEach((k, v) -> result.put(k, v.get()));
        gauges.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public long get(String name) {
        AtomicLong value = counters.get(name);
        if (value != null) return value.get();
        AtomicLong gauge = gauges.get(name);
        return gauge != null ? gauge.get() : 0L;
    }
}
