package com.cyan.common.log.config;

import com.cyan.common.log.interceptor.RequestLogInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 - 注册请求日志拦截器
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestLogInterceptor requestLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册请求日志拦截器，排除静态资源和接口文档
        registry.addInterceptor(requestLogInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/static/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/**"
                );
    }
}