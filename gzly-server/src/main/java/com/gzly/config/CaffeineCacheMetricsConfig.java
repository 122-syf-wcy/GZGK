package com.gzly.config;

import com.gzly.service.AnhuiBatchSupportService;
import com.gzly.service.BatchSupportService;
import com.gzly.service.SichuanBatchSupportService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * v7.54：把三个 BatchSupport 服务的 Caffeine 缓存注册到 Micrometer，
 * 让 hit/miss/eviction/load 等指标在 /actuator/prometheus 暴露。
 *
 * <p>之前三个服务里 Caffeine builder 都调了 {@code .recordStats()}，但没注册到
 * MeterRegistry，所以 Prometheus 看不到 cache 指标，Grafana 也画不出命中率图。</p>
 *
 * <p>每个 cache 注册一个名字方便区分：</p>
 * <ul>
 *   <li>{@code gzly_batch_support_gz}：贵州 batch-support 缓存（256 容量）</li>
 *   <li>{@code gzly_batch_support_sc}：四川 batch-support 缓存（128 容量）</li>
 *   <li>{@code gzly_batch_support_ah}：安徽 batch-support 缓存（128 容量）</li>
 * </ul>
 *
 * <p>MeterRegistry 可能在 actuator 未启用时不存在，使用 ObjectProvider/required=false 软绑定。</p>
 */
@Configuration
@Slf4j
public class CaffeineCacheMetricsConfig {

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    private BatchSupportService batchSupportService;

    @Autowired(required = false)
    private SichuanBatchSupportService sichuanBatchSupportService;

    @Autowired(required = false)
    private AnhuiBatchSupportService anhuiBatchSupportService;

    @PostConstruct
    public void registerCaffeineCaches() {
        if (meterRegistry == null) {
            log.info("[CaffeineCacheMetrics] MeterRegistry not available, skip cache metrics registration");
            return;
        }
        int registered = 0;
        if (batchSupportService != null && batchSupportService.getCacheForMetrics() != null) {
            CaffeineCacheMetrics.monitor(meterRegistry,
                    batchSupportService.getCacheForMetrics(), "gzly_batch_support_gz");
            registered++;
        }
        if (sichuanBatchSupportService != null && sichuanBatchSupportService.getCacheForMetrics() != null) {
            CaffeineCacheMetrics.monitor(meterRegistry,
                    sichuanBatchSupportService.getCacheForMetrics(), "gzly_batch_support_sc");
            registered++;
        }
        if (anhuiBatchSupportService != null && anhuiBatchSupportService.getCacheForMetrics() != null) {
            CaffeineCacheMetrics.monitor(meterRegistry,
                    anhuiBatchSupportService.getCacheForMetrics(), "gzly_batch_support_ah");
            registered++;
        }
        log.info("[CaffeineCacheMetrics] registered {} caffeine caches to MeterRegistry", registered);
    }
}
