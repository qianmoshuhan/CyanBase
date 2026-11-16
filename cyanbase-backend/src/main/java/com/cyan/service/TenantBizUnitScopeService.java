package com.cyan.service;

import com.cyan.entity.SysBizUnitScope;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 租户内经营单元 Scope 服务（平级 PrincipalTenantScopeService）。
 * <p>
 * 设计对齐：
 * <ul>
 *   <li>绑定表 sys_biz_unit_scope 无 tenant_id → IGNORED_TABLES → 业务层做归属校验</li>
 *   <li>先删后插事务范式 → 参考 PrincipalTenantScopeServiceImpl</li>
 *   <li>和 PrincipalTenantScopeService 代码隔离，互不耦合</li>
 * </ul>
 */
public interface TenantBizUnitScopeService {

    /**
     * 分配绑定（先删后插，事务包裹）。
     * <p>
     * 同一用户对同一经营单元的旧绑定先清掉，再按传入 roleId 重新插入。
     * roleId 传 null → 解除该经营单元的全部绑定。
     * </p>
     * <p>
     * ⚠️ 业务约束：一个用户 × 一个经营单元 仅允许绑定一个角色
     * （对应 sys_biz_unit_scope 的唯一索引 uk_user_bizunit(user_id, biz_unit_id)）
     * </p>
     *
     * @param userId    用户ID
     * @param bizUnitId 经营单元部门ID
     * @param roleId    角色ID（null=解除绑定）
     */
    void assignScope(Long userId, Long bizUnitId, Long roleId);

    /**
     * 查询当前租户下所有标记为经营单元的部门，返回树形结构。
     *
     * @param tenantId 租户ID
     * @return 树形节点（只含 is_biz_unit=1 的节点，保留父子关系）
     */
    List<Object> listBizUnitTree(Long tenantId);

    /**
     * 查询指定用户已绑定的全部经营单元 + 角色。
     *
     * @param userId 用户ID
     * @return 绑定记录（含 bizUnitId + roleId + bizUnitName + roleName）
     */
    List<SysBizUnitScope> listWithRoles(Long userId);

    /**
     * 查询指定经营单元绑定给指定用户的角色ID。
     *
     * @return 角色ID（最多一个；无绑定时返回 null）
     */
    Long getScopedRoleId(Long userId, Long bizUnitId);

    /**
     * 查询指定经营单元绑定给指定用户的角色ID列表（兼容场景：实际最多一个）。
     * <p>
     * 保留 List 返回类型是为了 PermissionServiceImpl 等合并场景的 Stream.concat 链路兼容。
     * </p>
     */
    default List<Long> getScopedRoleIds(Long userId, Long bizUnitId) {
        Long id = getScopedRoleId(userId, bizUnitId);
        return id != null ? List.of(id) : Collections.emptyList();
    }

    /**
     * 判断用户是否绑定了指定经营单元。
     */
    boolean hasBizUnit(Long userId, Long bizUnitId);

    /**
     * 校验：指定部门节点是否是标记为 is_biz_unit=1 的合法经营单元。
     *
     * @param bizUnitId 部门ID
     * @return true=合法经营单元
     */
    boolean isValidBizUnit(Long bizUnitId);

    /**
     * 校验：指定经营单元是否归属指定租户（避免跨租户选择）。
     */
    boolean isBizUnitBelongTenant(Long bizUnitId, Long tenantId);

    /**
     * 反向查询：指定经营单元下所有已绑定的用户列表（含用户信息 + 绑定角色）。
     * <p>
     * A 阶段独立授权管理页右栏展示用。
     * </p>
     *
     * @param bizUnitId 经营单元部门ID
     * @param tenantId  租户ID（用于校验经营单元归属 + 过滤同租户用户）
     * @return 用户绑定记录，每条含 userId、userName、nickName、phone、deptName、status、roleId、roleName
     */
    List<Map<String, Object>> listUsersByBizUnit(Long bizUnitId, Long tenantId);
}