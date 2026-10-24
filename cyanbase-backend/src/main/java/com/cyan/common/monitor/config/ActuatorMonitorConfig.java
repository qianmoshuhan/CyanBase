package com.cyan.common.monitor.config;

import com.cyan.service.SysUserOnlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator 监控扩展配置
 */
@Configuration
@RequiredArgsConstructor
public class ActuatorMonitorConfig {

    private final SysUserOnlineService sysUserOnlineService;

    /**
     * 自定义业务健康指标 - 在线用户数（查 sys_user_online 真实记录数）
     */
    @Bean
    public HealthIndicator onlineUserHealthIndicator() {
        return () -> {
            long count = sysUserOnlineService.count();
            return Health.up().withDetail("onlineUsers", count).build();
        };
    }

    /**
     * 自定义业务健康指标 - 定时任务运行状态
     */
    @Bean
    public HealthIndicator jobSchedulerHealthIndicator() {
        return () -> Health.up().withDetail("jobScheduler", "running").build();
    }
}