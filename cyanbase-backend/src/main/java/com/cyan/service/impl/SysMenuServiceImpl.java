package com.cyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysMenu;
import com.cyan.entity.SysRoleMenu;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysMenuMapper;
import com.cyan.mapper.SysRoleMenuMapper;
import com.cyan.mapper.SysUserRoleMapper;
import com.cyan.security.domain.LoginUser;
import com.cyan.service.PrincipalTenantScopeService;
import com.cyan.service.SysMenuService;
import com.cyan.service.TenantBizUnitScopeService;
import com.cyan.common.security.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {
    @Resource
    private SysUserRoleMapper userRoleMapper;
    @Resource
    private SysRoleMenuMapper roleMenuMapper;
    @Resource
    private PrincipalTenantScopeService principalTenantScopeService;
    @Resource
    private TenantBizUnitScopeService tenantBizUnitScopeService;

    @Override
    public List<SysMenu> selectMenuByUserId(Long userId) {
        // 从 SecurityContext 取参数，然后调主逻辑（含经营单元维度）
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Long activeTenantId = loginUser != null ? loginUser.getActiveTenantId() : null;
        Boolean platformPrincipal = loginUser != null ? loginUser.getPlatformPrincipal() : null;
        Long activeBizUnitId = loginUser != null ? loginUser.getActiveBizUnitId() : null;
        return selectMenuByUserId(userId, activeTenantId, platformPrincipal, activeBizUnitId);
    }

    @Override
    public List<SysMenu> selectMenuByUserId(Long userId, Long activeTenantId, Boolean platformPrincipal) {
        // 兼容旧调用：不带经营单元上下文
        return selectMenuByUserId(userId, activeTenantId, platformPrincipal, null);
    }

    @Override
    public List<SysMenu> selectMenuByUserId(Long userId, Long activeTenantId, Boolean platformPrincipal,
                                            Long activeBizUnitId) {
        // 1. 查 sys_user_role 绑定的角色 ID（平台侧角色）
        List<Long> platformRoleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .select(SysUserRole::getRoleId)
        ).stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

        // 1.5 三类 scope 角色合并（互斥场景，与 PermissionServiceImpl.getPermsByUserId 对齐）：
        //     ① 平台主体 + activeTenantId    → sys_user_role ∪ principal_tenant_scope
        //     ② 普通租户用户 + activeBizUnitId → sys_user_role ∪ sys_biz_unit_scope
        //     ③ 未选中任何 scope             → 只有 sys_user_role
        List<Long> roleIds;
        if (Boolean.TRUE.equals(platformPrincipal) && activeTenantId != null) {
            Long scopeRoleId = principalTenantScopeService.getRoleId(userId, activeTenantId);
            List<Long> scopeRoleIds = scopeRoleId != null ? List.of(scopeRoleId) : Collections.emptyList();
            roleIds = Stream.concat(platformRoleIds.stream(), scopeRoleIds.stream())
                    .distinct()
                    .collect(Collectors.toList());
        } else if (!Boolean.TRUE.equals(platformPrincipal) && activeBizUnitId != null) {
            List<Long> bizUnitRoleIds = tenantBizUnitScopeService.getScopedRoleIds(userId, activeBizUnitId);
            roleIds = Stream.concat(platformRoleIds.stream(), bizUnitRoleIds.stream())
                    .distinct()
                    .collect(Collectors.toList());
            log.debug("[MENU-BIZUNIT] userId={}, activeBizUnitId={}, bizUnitRoleIds={}, mergedRoleIds={}",
                    userId, activeBizUnitId, bizUnitRoleIds, roleIds);
        } else {
            roleIds = platformRoleIds;
        }

        if (roleIds.isEmpty()) return List.of();

        // 2. 根据角色查绑定菜单
        List<Long> menuIds = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds)
        ).stream().map(SysRoleMenu::getMenuId).distinct().collect(Collectors.toList());

        if (menuIds.isEmpty()) return List.of();

        // 3. 收集所有父节点
        Set<Long> allMenuIds = new HashSet<>(menuIds);
        for (Long mid : menuIds) {
            collectParentIds(mid, allMenuIds);
        }

        // 4. 查询完整菜单集合
        LambdaQueryWrapper<SysMenu> menuWrapper = new LambdaQueryWrapper<>();
        menuWrapper.in(SysMenu::getId, allMenuIds)
                .eq(SysMenu::getStatus, 0)
                .eq(SysMenu::getVisible, 0)
                .eq(SysMenu::getDelFlag, 0)
                .orderByAsc(SysMenu::getSortNum);
        return baseMapper.selectList(menuWrapper);
    }

        /**
 * 递归收集所有父菜单ID
 */
private void collectParentIds(Long menuId, Set<Long> allMenuIds) {
    SysMenu parent = baseMapper.selectById(menuId);
    if (parent != null && parent.getParentId() != 0) {
        allMenuIds.add(parent.getParentId());
        collectParentIds(parent.getParentId(), allMenuIds);
    }
}
    
}