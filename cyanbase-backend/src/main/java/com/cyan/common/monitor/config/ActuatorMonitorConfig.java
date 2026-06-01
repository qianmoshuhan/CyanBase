package com.cyan.common.monitor.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator 监控扩展配置
 */
@Configuration
public class ActuatorMonitorConfig {

    /**
     * 自定义业务健康指标 - 在线用户数
     */
    @Bean
    public HealthIndicator onlineUserHealthIndicator() {
        return () -> {
            // TODO: 后续可集成 Redis 获取在线用户数
            return Health.up().withDetail("onlineUsers", 0).build();
        };
    }

    /**
     * 自定义业务健康指标 - 定时任务运行状态
     */
    @Bean
    public HealthIndicator jobSchedulerHealthIndicator() {
        return () -> {
            // TODO: 后续可检查定时任务调度器状态
            return Health.up().withDetail("jobScheduler", "running").build();
        };
    }
}