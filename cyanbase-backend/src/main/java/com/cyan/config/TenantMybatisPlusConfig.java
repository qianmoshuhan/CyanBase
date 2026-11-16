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
 * <h3>拦截器顺序（2026-11-11 修正）</h3>
 * 遵循 MyBatis-Plus 官方「先过滤、后分页」原则，SQL 改写类拦截器必须排在分页之前：
 * <ol>
 *   <li>TenantLineInnerInterceptor    → 追加 tenant_id</li>
 *   <li>DataPermissionInterceptor     → 追加 dept_id / create_by</li>
 *   <li>PaginationInnerInterceptor   → 最后基于已改写的 SQL 生成 COUNT(*) 与 LIMIT</li>
 * </ol>
 * ⚠️ 旧顺序把分页放在最前，导致 COUNT 用的是尚未追加 DataScope 条件的原始 SQL，
 * 表现为「经营单元切换后 records=1 但 total=8」。分页放前面还会让 count 与列表口径不一致。
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

        // ⭐ 拦截顺序（先执行者先改写 SQL）：租户 → 数据权限 → 分页
        // ① 租户拦截器（追加 tenant_id 过滤，最终 SQL 必然受限在本租户内）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(customTenantHandler));

        // ② 数据权限拦截器（追加 dept_id / create_by 过滤）
        DataPermissionInterceptor dataPermissionInterceptor = new DataPermissionInterceptor();
        dataPermissionInterceptor.setDataPermissionHandler(dataPermissionHandler);
        interceptor.addInnerInterceptor(dataPermissionInterceptor);

        // ③ 分页拦截器必须最后：此时 SQL 已带全 tenant_id + dept_id 条件，
        //    由它生成的 COUNT(*) 才会包含同样的过滤条件，total 与 records 口径一致。
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());

        log.info("[Tenant] MyBatis-Plus 拦截器已注册（租户 → 数据权限 → 分页），Handler: {}",
                customTenantHandler.getClass().getSimpleName());
        return interceptor;
    }
}