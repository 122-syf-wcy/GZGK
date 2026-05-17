package com.gzly.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class TaskExecutorConfig {

    @Bean("taskExecutor")
    public Executor taskExecutor(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                6,
                16,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                namedDaemonFactory("gzly-task-"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        return monitor(executor, "gzly.task.executor", meterRegistryProvider);
    }

    /**
     * v7.41 高并发优化：把 AI SSE 长流（30~90s 的 mimo / openai 调用）从通用 taskExecutor
     * 解耦到独立池，避免一波 AI 用户把短异步任务（导出、Excel 渲染等）也堵在队列后面。
     * - core=8 / max=32：单 host 4 vCPU + Tomcat 200 worker 下，AI SSE 真正占线程的只是
     *   读 OkHttp 响应流，CPU 极轻，max=32 足以扛 20 并发 SSE。
     * - SynchronousQueue：不缓冲，直接交出去；超过 max 立刻被 CallerRunsPolicy 退化为
     *   Tomcat 线程同步执行（前端会自然感到慢，避免假成功后真排队 5 分钟）。
     * - daemon 线程不阻止 JVM graceful shutdown；线程名 gzly-ai-sse-* 方便 jstack / prom
     *   按 thread name 切分指标。
     */
    @Bean("aiAnalysisExecutor")
    public ExecutorService aiAnalysisExecutor(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                8,
                32,
                60,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                namedDaemonFactory("gzly-ai-sse-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return monitor(executor, "gzly.ai.sse.executor", meterRegistryProvider);
    }

    private ThreadFactory namedDaemonFactory(String namePrefix) {
        AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(namePrefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private ExecutorService monitor(ThreadPoolExecutor executor,
                                    String name,
                                    ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return executor;
        }
        return ExecutorServiceMetrics.monitor(registry, executor, name);
    }
}
