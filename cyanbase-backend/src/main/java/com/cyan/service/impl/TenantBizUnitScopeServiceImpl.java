package com.cyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.entity.SysBizUnitScope;
import com.cyan.entity.SysDept;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysUser;
import com.cyan.mapper.SysBizUnitScopeMapper;
import com.cyan.service.TenantBizUnitScopeService;
import com.cyan.service.SysDeptService;
import com.cyan.service.SysRoleService;
import com.cyan.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TenantBizUnitScopeService 实现。
 * <p>
 * 范式对齐 PrincipalTenantScopeServiceImpl：
 * <ul>
 *   <li>先删后插事务</li>
 *   <li>IGNORED_TABLES 表 → 业务层做跨租户校验</li>
 *   <li>所有校验前置：经营单元归属 / 角色归属 / 角色状态 / 超管角色拦截</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantBizUnitScopeServiceImpl implements TenantBizUnitScopeService {

    private final SysBizUnitScopeMapper bizUnitScopeMapper;
    private final SysDeptService deptService;
    private final SysRoleService roleService;
    private final SysUserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignScope(Long userId, Long bizUnitId, Long roleId) {
        // ── 用户校验 ──
        SysUser user = userService.getById(userId);
        if (user == null || user.getTenantId() == null || user.getTenantId() <= 0) {
            throw new IllegalArgumentException("用户不存在或非租户用户，不能绑定经营单元");
        }
        Long tenantId = user.getTenantId();

        // ── 经营单元校验（拆四步，一次查 dept 复用）──
        if (bizUnitId == null) {
            throw new IllegalArgumentException("经营单元ID不能为空");
        }
        SysDept dept = deptService.getById(bizUnitId);
        if (dept == null) {
            throw new IllegalArgumentException("部门不存在: " + bizUnitId);
        }
        if (dept.getStatus() != null && dept.getStatus() == 1) {
            throw new IllegalArgumentException("部门已停用: " + bizUnitId);
        }
        if (dept.getTenantId() == null || !dept.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("经营单元不属于当前租户: " + bizUnitId);
        }
        if (!Integer.valueOf(1).equals(dept.getIsBizUnit())) {
            throw new IllegalArgumentException("该部门不是经营单元(is_biz_unit=1): " + bizUnitId);
        }

        // roleId 为 null → 解除绑定（跳过角色校验）
        if (roleId != null) {
            SysRole role = roleService.getById(roleId);
            if (role == null) {
                throw new IllegalArgumentException("角色不存在: " + roleId);
            }
            // 跨租户拦截
            if (role.getTenantId() != null && role.getTenantId() > 0
                    && !role.getTenantId().equals(tenantId)) {
                throw new IllegalArgumentException("角色不属于当前租户: " + role.getId());
            }
            // 超管角色拦截
            if ("admin".equals(role.getRoleKey())) {
                throw new IllegalArgumentException("不允许绑定超级管理员角色");
            }
            // 状态正常
            if (role.getStatus() != null && role.getStatus() == 1) {
                throw new IllegalArgumentException("角色已停用: " + role.getId());
            }
        }

        // ── 先删后插 ──
        // 该用户对该经营单元的所有绑定先清掉（理论上只有一条）
        bizUnitScopeMapper.delete(new LambdaQueryWrapper<SysBizUnitScope>()
                .eq(SysBizUnitScope::getUserId, userId)
                .eq(SysBizUnitScope::getBizUnitId, bizUnitId));

        if (roleId != null) {
            SysBizUnitScope s = new SysBizUnitScope();
            s.setUserId(userId);
            s.setBizUnitId(bizUnitId);
            s.setRoleId(roleId);
            String userName = user.getUserName() != null ? user.getUserName() : "";
            s.setCreateBy(userName);
            s.setUpdateBy(userName);
            bizUnitScopeMapper.insert(s);
        }

        log.info("[BizUnitScope] assignScope userId={}, bizUnitId={}, roleId={}", userId, bizUnitId, roleId);
    }

    @Override
    public List<Object> listBizUnitTree(Long tenantId) {
        if (tenantId == null || tenantId <= 0) return Collections.emptyList();

        // 拿该租户下所有 status=0 的部门（⚠️ 去掉 is_biz_unit=1 过滤！
        // 因为中间层级的集团节点/子集团节点不是经营单元，但必须保留用来构建完整组织树结构）
        List<SysDept> all = deptService.list(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getTenantId, tenantId)
                .eq(SysDept::getStatus, 0)
                .orderByAsc(SysDept::getSortNum));

        if (all.isEmpty()) return Collections.emptyList();

        // 构建树：按 parentId 分组
        Map<Long, List<SysDept>> byParent = all.stream()
                .collect(Collectors.groupingBy(d -> d.getParentId() != null ? d.getParentId() : 0L));

        // 递归构建节点（用 Map 而非单独 VO，保持轻量）
        List<Object> roots = new ArrayList<>();
        for (SysDept d : all) {
            if (d.getParentId() == null || d.getParentId() == 0L || !byParent.containsKey(d.getParentId())) {
                roots.add(buildNode(d, byParent));
            }
        }
        // 如果没找到 parentId 在 all 中的节点，全部当根返回
        if (roots.isEmpty()) {
            roots.addAll(all.stream().map(this::toMapNode).toList());
        }
        return roots;
    }

    private Map<String, Object> buildNode(SysDept dept, Map<Long, List<SysDept>> byParent) {
        Map<String, Object> node = toMapNode(dept);
        List<SysDept> children = byParent.get(dept.getId());
        if (children != null && !children.isEmpty()) {
            List<Map<String, Object>> childNodes = new ArrayList<>();
            for (SysDept c : children) {
                childNodes.add(buildNode(c, byParent));
            }
            node.put("children", childNodes);
        }
        return node;
    }

    private Map<String, Object> toMapNode(SysDept d) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", d.getId());
        node.put("parentId", d.getParentId());
        node.put("deptName", d.getDeptName());
        node.put("sortNum", d.getSortNum());
        node.put("status", d.getStatus());
        // ⭐ 关键：带上 isBizUnit 标记，前端根据此标记高亮/可选中
        node.put("isBizUnit", d.getIsBizUnit() != null ? d.getIsBizUnit() : 0);
        return node;
    }

    @Override
    public List<SysBizUnitScope> listWithRoles(Long userId) {
        if (userId == null) return Collections.emptyList();
        return bizUnitScopeMapper.selectList(
                new LambdaQueryWrapper<SysBizUnitScope>()
                        .eq(SysBizUnitScope::getUserId, userId));
    }

    @Override
    public Long getScopedRoleId(Long userId, Long bizUnitId) {
        if (userId == null || bizUnitId == null) return null;
        SysBizUnitScope one = bizUnitScopeMapper.selectOne(
                new LambdaQueryWrapper<SysBizUnitScope>()
                        .eq(SysBizUnitScope::getUserId, userId)
                        .eq(SysBizUnitScope::getBizUnitId, bizUnitId)
                        .last("LIMIT 1"));
        return one != null ? one.getRoleId() : null;
    }

    @Override
    public List<Long> getScopedRoleIds(Long userId, Long bizUnitId) {
        Long id = getScopedRoleId(userId, bizUnitId);
        return id != null ? List.of(id) : Collections.emptyList();
    }

    @Override
    public boolean hasBizUnit(Long userId, Long bizUnitId) {
        if (userId == null || bizUnitId == null) return false;
        return bizUnitScopeMapper.selectCount(
                new LambdaQueryWrapper<SysBizUnitScope>()
                        .eq(SysBizUnitScope::getUserId, userId)
                        .eq(SysBizUnitScope::getBizUnitId, bizUnitId)) > 0;
    }

    @Override
    public boolean isValidBizUnit(Long bizUnitId) {
        if (bizUnitId == null) return false;
        SysDept dept = deptService.getById(bizUnitId);
        return dept != null && Integer.valueOf(1).equals(dept.getIsBizUnit())
                && (dept.getStatus() == null || dept.getStatus() == 0);
    }

    @Override
    public boolean isBizUnitBelongTenant(Long bizUnitId, Long tenantId) {
        if (bizUnitId == null || tenantId == null) return false;
        SysDept dept = deptService.getById(bizUnitId);
        return dept != null && tenantId.equals(dept.getTenantId());
    }

    @Override
    public List<Map<String, Object>> listUsersByBizUnit(Long bizUnitId, Long tenantId) {
        if (bizUnitId == null || tenantId == null || tenantId <= 0) return Collections.emptyList();

        // 1. 经营单元归属校验
        SysDept dept = deptService.getById(bizUnitId);
        if (dept == null || !Integer.valueOf(1).equals(dept.getIsBizUnit())
                || !tenantId.equals(dept.getTenantId())) {
            log.warn("[BizUnitScope] listUsersByBizUnit 经营单元非法 bizUnitId={}, tenantId={}", bizUnitId, tenantId);
            return Collections.emptyList();
        }

        // 2. 查该经营单元的所有绑定
        List<SysBizUnitScope> scopes = bizUnitScopeMapper.selectList(
                new LambdaQueryWrapper<SysBizUnitScope>()
                        .eq(SysBizUnitScope::getBizUnitId, bizUnitId));
        if (scopes.isEmpty()) return Collections.emptyList();

        Set<Long> userIds = scopes.stream().map(SysBizUnitScope::getUserId).collect(Collectors.toSet());
        Set<Long> roleIds = scopes.stream()
                .map(SysBizUnitScope::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3. 并行查用户 + 角色（同租户过滤）
        Map<Long, SysUser> userMap = userService.list(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds)
                        .eq(SysUser::getTenantId, tenantId))
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u));

        Map<Long, SysRole> roleMap = roleIds.isEmpty() ? Collections.emptyMap()
                : roleService.listByIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r));

        // 4. 组装 VO（带 deptName：用户所在部门名）
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysBizUnitScope s : scopes) {
            SysUser u = userMap.get(s.getUserId());
            if (u == null) continue;   // 跨租户的/已删除的跳过

            SysRole role = s.getRoleId() != null ? roleMap.get(s.getRoleId()) : null;
            SysDept userDept = u.getDeptId() != null ? deptService.getById(u.getDeptId()) : null;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", u.getId());
            row.put("userName", u.getUserName());
            row.put("nickName", u.getNickName());
            row.put("phone", u.getPhone());
            row.put("email", u.getEmail());
            row.put("status", u.getStatus());
            row.put("deptName", userDept != null ? userDept.getDeptName() : null);
            row.put("deptId", u.getDeptId());
            row.put("roleId", s.getRoleId());
            row.put("roleName", role != null ? role.getRoleName() : null);
            row.put("bindTime", s.getCreateTime());
            result.add(row);
        }
        return result;
    }
}