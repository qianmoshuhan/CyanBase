package com.cyan.security.datascope;

/**
 * 三层权限漏斗 — 目标资源归属信息（写操作必填，读操作传 null）。
 * <p>
 * 由调用方在执行写操作前填充，用于 {@link com.cyan.security.aspect.PermissionGuard}
 * 进行第 1 层租户归属校验和第 2 层 DataScope 主动校验。
 * <p>
 * 字段说明：
 * <ul>
 *   <li>{@code targetTenantId} — 目标资源所属租户（跨租户隔离校验用）。null 表示"新建"场景，由业务逻辑在 save 时 setTenantId，校验层跳过归属比对。</li>
 *   <li>{@code targetDeptId}   — 目标资源所属部门（DEPT / DEPT_AND_CHILD / CUSTOM 范围校验用）。</li>
 *   <li>{@code targetCreateBy} — 目标资源创建人用户名（SELF 范围校验用）。</li>
 * </ul>
 * <p>
 * 典型用法：
 * <pre>
 * // 写操作 — 先查目标资源，再构造 ResourceScope 传入
 * SysRole exist = roleService.getById(id);
 * ResourceScope scope = ResourceScope.of(exist.getTenantId(), exist.getDeptId(), exist.getCreateBy());
 * PermissionGuard.check(scope, "system:role:edit");
 * </pre>
 */
public record ResourceScope(
        Long targetTenantId,
        Long targetDeptId,
        String targetCreateBy
) {

    /** 构造器。参数可按需传 null，但写操作应至少有 targetTenantId。 */
    public static ResourceScope of(Long targetTenantId, Long targetDeptId, String targetCreateBy) {
        return new ResourceScope(targetTenantId, targetDeptId, targetCreateBy);
    }

    /** 只有租户归属的场景（资源本身无部门/创建人维度，如系统配置） */
    public static ResourceScope tenantOnly(Long targetTenantId) {
        return new ResourceScope(targetTenantId, null, null);
    }
}
