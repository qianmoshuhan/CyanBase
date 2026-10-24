package com.cyan.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ⚠️ 已废弃：分页 + 租户拦截器统一由
 * {@link com.cyan.config.TenantMybatisPlusConfig} 提供（@Primary）。
 * <p>
 * 本类原有的 PaginationInnerInterceptor Bean 与 TenantMybatisPlusConfig
 * 中的 Bean 同时存在时，MybatisPlusAutoConfiguration 会把两个
 * MybatisPlusInterceptor 都注册到 SqlSessionFactory，导致 count SQL 被
 * PaginationInnerInterceptor 双重包装，生成 {@code ... ORDER BY xxx) TOTAL) TOTAL}
 * 这样的非法 SQL，MariaDB 直接报 500。
 */
@Configuration
public class MybatisPlusConfig {

    // 已废弃的 Bean —— 注释掉，让 TenantMybatisPlusConfig 唯一生效
    // @Bean
    // public MybatisPlusInterceptor mybatisPlusInterceptor() { ... }
}