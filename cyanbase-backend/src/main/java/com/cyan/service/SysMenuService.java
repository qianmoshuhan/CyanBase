package com.cyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.entity.SysMenu;
import java.util.List;

public interface SysMenuService extends IService<SysMenu> {
    // 根据用户ID查询可用菜单集合（平铺 JSON，暂不组装树形）
    List<SysMenu> selectMenuByUserId(Long userId);

    /**
     * 重载：显式传入 activeTenantId + platformPrincipal，避免依赖 SecurityContext。
     * 平台主体场景必须用这个重载，否则 scope 角色合并会被跳过。
     */
    List<SysMenu> selectMenuByUserId(Long userId, Long activeTenantId, Boolean platformPrincipal);

    /**
     * 完整重载：再追加 activeBizUnitId（租户内经营单元上下文）。
     * <p>
     * 普通租户用户选中经营单元时，菜单角色必须与 PermissionServiceImpl 保持一致，
     * 走 {@code sys_user_role ∪ sys_biz_unit_scope} 合并，否则会出现
     * 「权限码变了但菜单树不变」的视图错位。
     * </p>
     */
    List<SysMenu> selectMenuByUserId(Long userId, Long activeTenantId, Boolean platformPrincipal,
                                     Long activeBizUnitId);
}