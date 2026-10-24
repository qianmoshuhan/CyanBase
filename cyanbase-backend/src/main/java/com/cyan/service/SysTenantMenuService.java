package com.cyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.entity.SysTenantMenu;

/**
 * 租户-菜单关联 Service
 * <p>
 * 纯中间表（在 CustomTenantHandler.IGNORED_TABLES 中），无 TenantLineInterceptor 隔离，
 * 但有 TenantMetaObjectHandler.insertFill() 自动填 tenantId。
 * 提供幂等批量确保某租户拥有某菜单白名单的原子方法。
 * </p>
 */
public interface SysTenantMenuService extends IService<SysTenantMenu> {
}
