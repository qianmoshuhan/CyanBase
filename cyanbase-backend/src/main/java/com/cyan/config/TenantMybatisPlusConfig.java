package com.cyan.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * MyBatis-Plus 多租户拦截配置
 * <p>
 * 通过注册独立的 @Primary MybatisPlusInterceptor Bean，
 * 将租户拦截器与原有分页拦截器整合在同一个拦截器链中，
 * 原有 {@code com.cyan.config.MybatisPlusConfig} 保持零修改。
 * </p>
 *
 * <h3>为什么用 @Primary</h3>
 * MyBatis-Plus 自动配置类会从 Spring 容器中拿一个 MybatisPlusInterceptor，
 * 有多个同名 Bean 时会报 NoUniqueBeanDefinitionException。
 * 通过 @Primary 让我们这个 Bean 优先被选中，原有 Bean 自然被忽略，
 * 拦截器链中同时包含「分页 + 租户」，功能完整且零侵入。
 *
 * <h3>拦截器顺序</h3>
 * 外层：PaginationInnerInterceptor  → 先处理分页 / 物理删除
 * 内层：TenantLineInnerInterceptor  → 再处理租户条件
 * 顺序不能反，否则分页 SQL 里的租户条件会被分页器当成业务条件。
 *
 * <h3>Handler 注入方式</h3>
 * 不是继承 TenantLineInnerInterceptor 重写 getTenantId()（那是 TenantLineHandler 的方法），
 * 而是：new TenantLineInnerInterceptor() + setTenantLineHandler(独立 Component)
 * 这样彻底避开接口方法不匹配的编译错误。
 */
@Slf4j
@Configuration
public class TenantMybatisPlusConfig {

    /**
     * 注册包含「分页 + 租户」的 MybatisPlusInterceptor。
     * <p>
     * Bean 名必须与 com.cyan.config.MybatisPlusConfig 中的 mybatisPlusInterceptor 不同，
     * 否则 Spring Boot 默认禁用同名 Bean 覆盖会直接启动失败。
     * MyBatis-Plus 自动配置类是按类型 ObjectProvider<MybatisPlusInterceptor> 查找的，
     * 不依赖 Bean 名，所以改名完全不影响被正确加载。
     * </p>
     * <p>
     * Handler 使用构造器注入：{@code new TenantLineInnerInterceptor(customTenantHandler)}。
     * </p>
     */
    @Bean(name = "tenantMybatisPlusInterceptor")
    @Primary
    public MybatisPlusInterceptor tenantMybatisPlusInterceptor(CustomTenantHandler customTenantHandler,
                                                               com.cyan.security.datascope.CyanDataPermissionHandler dataPermissionHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 拦截顺序（外层先执行）：分页 → 数据权限 → 租户
        // 外层：分页拦截器
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());

        // 中层：数据权限拦截器（追加 dept_id / create_by 过滤）
        DataPermissionInterceptor dataPermissionInterceptor = new DataPermissionInterceptor();
        dataPermissionInterceptor.setDataPermissionHandler(dataPermissionHandler);
        interceptor.addInnerInterceptor(dataPermissionInterceptor);

        // 内层：租户拦截器（追加 tenant_id 过滤，最终 SQL 必然受限在本租户内）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(customTenantHandler));

        log.info("[Tenant] MyBatis-Plus 多租户拦截器已注册（分页 + 租户），Handler: {}",
                customTenantHandler.getClass().getSimpleName());
        return interceptor;
    }
}