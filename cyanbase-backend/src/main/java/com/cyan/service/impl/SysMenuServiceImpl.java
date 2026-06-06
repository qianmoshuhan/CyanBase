package com.cyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.entity.SysMenu;
import com.cyan.entity.SysRoleMenu;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysMenuMapper;
import com.cyan.mapper.SysRoleMenuMapper;
import com.cyan.mapper.SysUserRoleMapper;
import com.cyan.service.SysMenuService;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;

@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {
    @Resource
    private SysUserRoleMapper userRoleMapper;
    @Resource
    private SysRoleMenuMapper roleMenuMapper;

        @Override
        public List<SysMenu> selectMenuByUserId(Long userId) {
            //1.根据用户id查绑定角色id
            LambdaQueryWrapper<SysUserRole> urWrapper = new LambdaQueryWrapper<>();
            urWrapper.eq(SysUserRole::getUserId, userId);
            List<Long> roleIds = userRoleMapper.selectList(urWrapper).stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
            if(roleIds.isEmpty()) return List.of();

            //2.根据角色id查绑定菜单id
            LambdaQueryWrapper<SysRoleMenu> rmWrapper = new LambdaQueryWrapper<>();
            rmWrapper.in(SysRoleMenu::getRoleId, roleIds);
            List<Long> menuIds = roleMenuMapper.selectList(rmWrapper).stream().map(SysRoleMenu::getMenuId).distinct().collect(Collectors.toList());

            // 3. 获取所有父节点（包含所有层级祖先）
Set<Long> allMenuIds = new HashSet<>(menuIds);
for (Long mid : menuIds) {
    collectParentIds(mid, allMenuIds);
}

// 4. 查询完整菜单集合（自身 + 所有父节点）
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