package com.cyan.common.doc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 接口文档配置
 */
@Configuration
public class OpenApiConfig {

    @Value("${springdoc.enabled:true}")
    private boolean enabled;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CyanBase 青渊数字底座 API 文档")
                        .description("CyanBase 青渊数字底座后端接口文档，支持 JWT 认证调试")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("CyanBase Team")
                                .email("support@cyanbase.com")))
                // 添加 JWT 安全认证方案
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("请输入 JWT Token，格式：Bearer {token}")));
    }
}