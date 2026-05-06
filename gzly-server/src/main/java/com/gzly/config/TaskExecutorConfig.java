package com.gzly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class TaskExecutorConfig {

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        return new ThreadPoolExecutor(
                6,
                16,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("gzly-task-" + thread.getId());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
