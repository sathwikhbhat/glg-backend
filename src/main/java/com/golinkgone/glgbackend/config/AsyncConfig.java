package com.golinkgone.glgbackend.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean("analyticsExecutor")
    public Executor analyticsExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("analytics-vt-");
        executor.setVirtualThreads(true);
        return executor;
    }

    @Bean("dashboardReadExecutor")
    public Executor dashboardReadExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("dashboard-vt-");
        executor.setVirtualThreads(true);
        return executor;
    }
}
