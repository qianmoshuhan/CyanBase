package com.cyan.common.pool.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 全局线程池配置
 */
@Configuration
public class ThreadPoolConfig {

    @Value("${thread-pool.core-pool-size:10}")
    private int corePoolSize;

    @Value("${thread-pool.max-pool-size:50}")
    private int maxPoolSize;

    @Value("${thread-pool.queue-capacity:200}")
    private int queueCapacity;

    @Value("${thread-pool.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    /**
     * 全局公用线程池
     */
    @Bean("commonTaskExecutor")
    public ThreadPoolTaskExecutor commonTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("cyan-common-");
        // 拒绝策略：由调用线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 定时任务专用线程池
     */
    @Bean("scheduledTaskExecutor")
    public ThreadPoolTaskExecutor scheduledTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("cyan-scheduled-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}