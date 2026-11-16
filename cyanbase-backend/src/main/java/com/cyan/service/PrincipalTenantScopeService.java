package com.cyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.entity.PrincipalTenantScope;
import lombok.Data;

import java.util.List;

/**
 * 平台主体 ↔ 允许访问租户（及对应角色）关联 Service。
 * <p>
 * 负责维护平台主体（sys_user.tenant_id IS NULL 的账号）可访问的租户范围，
 * 以及进入每个租户时使用的角色（roleId）。
 */
public interface PrincipalTenantScopeService extends IService<PrincipalTenantScope> {

    /**
     * 给指定平台主体批量分配租户访问范围及角色（全量覆盖，先删后插，事务内执行）。
     *
     * @param principalUserId 平台主体用户ID（sys_user.tenant_id 必须为 NULL）
     * @param assignments      每条 = 一个租户 + 对应角色（roleId 可空，历史存量数据允许为空）
     */
    void assignScopes(Long principalUserId, List<ScopeAssignment> assignments);

    /**
     * 查询指定平台主体已分配的租户ID列表（向后兼容，不含 roleId）。
     */
    List<Long> listScopedTenantIds(Long principalUserId);

    /**
     * 判断指定平台主体是否被授权访问指定租户。
     */
    boolean hasScope(Long principalUserId, Long tenantId);

    /**
     * 查询平台主体对指定租户的角色绑定。
     *
     * @param principalUserId 平台主体用户ID
     * @param tenantId        目标租户ID
     * @return 绑定的 roleId，null 表示未授权或未绑定
     */
    Long getRoleId(Long principalUserId, Long tenantId);

    /**
     * 查询平台主体的全部 scope（带 roleId，用于 Controller GET 接口回显）。
     *
     * @param principalUserId 平台主体用户ID
     * @return 包含 tenantId + roleId 的 ScopeAssignment 列表
     */
    List<ScopeAssignment> listWithRoles(Long principalUserId);

    /**
     * 查询平台主体在指定租户下的 scope 角色。
     * <p>
     * 唯一约束保证一主体对一租户只有一个角色，返回列表以兼容现有接口约定。
     *
     * @param principalUserId 平台主体用户ID
     * @param tenantId        目标租户ID
     * @return 包含 0 或 1 个 roleId 的列表
     */
    List<Long> getScopeRoleIds(Long principalUserId, Long tenantId);

    // ---------- 内部 DTO ----------

    /**
     * Scope 分配条目：一个平台主体对一个租户的角色绑定。
     */
    @Data
    class ScopeAssignment {
        /** 目标租户ID（sys_tenant.id） */
        private Long tenantId;
        /** 该租户下使用的角色ID（sys_role.id），可空——存量历史数据 role_id 为 NULL */
        private Long roleId;
    }
}