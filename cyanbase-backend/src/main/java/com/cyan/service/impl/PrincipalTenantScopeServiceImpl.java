package com.cyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.config.CustomTenantHandler;
import com.cyan.entity.PrincipalTenantScope;
import com.cyan.mapper.PrincipalTenantScopeMapper;
import com.cyan.service.PrincipalTenantScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 平台主体-租户 Scope 服务实现
 * <p>
 * principal_tenant_scope 表不参与租户拦截（已加入 IGNORED_TABLES）。
 * 但主体查询"自己"的 scope 时仍需 skip——因为主体 sys_user.tenant_id IS NULL，
 * 走 TenantLine 会被过滤掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrincipalTenantScopeServiceImpl
        extends ServiceImpl<PrincipalTenantScopeMapper, PrincipalTenantScope>
        implements PrincipalTenantScopeService {

    private final PrincipalTenantScopeMapper principalTenantScopeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignScopes(Long principalUserId, List<ScopeAssignment> assignments) {
        CustomTenantHandler.skip();
        try {
            // 1. 删除该主体原有全部 scope（含 roleId）
            baseMapper.delete(
                    new LambdaQueryWrapper<PrincipalTenantScope>()
                            .eq(PrincipalTenantScope::getPrincipalUserId, principalUserId)
            );

            // 2. 批量插入新 scope（带 roleId，可为 null——前端保存时强制要求勾选租户必须选角色，
            //    但后端不强制，兼容历史存量数据 role_id 为 NULL 的场景）
            if (assignments != null && !assignments.isEmpty()) {
                String currentUsername = SecurityUtils.getUsername();
                LocalDateTime now = LocalDateTime.now();
                for (ScopeAssignment a : assignments) {
                    PrincipalTenantScope scope = new PrincipalTenantScope();
                    scope.setPrincipalUserId(principalUserId);
                    scope.setTenantId(a.getTenantId());
                    scope.setRoleId(a.getRoleId());  // 可空
                    scope.setCreateBy(currentUsername);
                    scope.setCreateTime(now);
                    scope.setUpdateBy(currentUsername);
                    scope.setUpdateTime(now);
                    baseMapper.insert(scope);
                }
                log.info("Principal scope assigned: principalUserId={}, count={}, assignments={}",
                        principalUserId, assignments.size(), assignments);
            }
        } finally {
            CustomTenantHandler.reset();
        }
    }

    @Override
    public List<Long> listScopedTenantIds(Long principalUserId) {
        CustomTenantHandler.skip();
        try {
            List<PrincipalTenantScope> scopes = baseMapper.selectList(
                    new LambdaQueryWrapper<PrincipalTenantScope>()
                            .eq(PrincipalTenantScope::getPrincipalUserId, principalUserId)
                            .select(PrincipalTenantScope::getTenantId)
            );
            return scopes.stream()
                    .map(PrincipalTenantScope::getTenantId)
                    .collect(Collectors.toList());
        } finally {
            CustomTenantHandler.reset();
        }
    }

    @Override
    public boolean hasScope(Long principalUserId, Long tenantId) {
        if (principalUserId == null || tenantId == null) {
            return false;
        }
        CustomTenantHandler.skip();
        try {
            return baseMapper.selectCount(
                    new LambdaQueryWrapper<PrincipalTenantScope>()
                            .eq(PrincipalTenantScope::getPrincipalUserId, principalUserId)
                            .eq(PrincipalTenantScope::getTenantId, tenantId)
            ) > 0;
        } finally {
            CustomTenantHandler.reset();
        }
    }

    @Override
    public Long getRoleId(Long principalUserId, Long tenantId) {
        if (principalUserId == null || tenantId == null) {
            return null;
        }
        CustomTenantHandler.skip();
        try {
            PrincipalTenantScope scope = baseMapper.selectOne(
                    new LambdaQueryWrapper<PrincipalTenantScope>()
                            .eq(PrincipalTenantScope::getPrincipalUserId, principalUserId)
                            .eq(PrincipalTenantScope::getTenantId, tenantId)
                            .select(PrincipalTenantScope::getRoleId)
                            .last("LIMIT 1")
            );
            return scope != null ? scope.getRoleId() : null;
        } finally {
            CustomTenantHandler.reset();
        }
    }

    @Override
    public List<ScopeAssignment> listWithRoles(Long principalUserId) {
        CustomTenantHandler.skip();
        try {
            List<PrincipalTenantScope> scopes = baseMapper.selectList(
                    new LambdaQueryWrapper<PrincipalTenantScope>()
                            .eq(PrincipalTenantScope::getPrincipalUserId, principalUserId)
                            .select(PrincipalTenantScope::getTenantId, PrincipalTenantScope::getRoleId)
            );
            return scopes.stream().map(s -> {
                ScopeAssignment a = new ScopeAssignment();
                a.setTenantId(s.getTenantId());
                a.setRoleId(s.getRoleId());
                return a;
            }).collect(Collectors.toList());
        } finally {
            CustomTenantHandler.reset();
        }
    }

    @Override
    public List<Long> getScopeRoleIds(Long principalUserId, Long tenantId) {
        Long roleId = getRoleId(principalUserId, tenantId);
        if (roleId == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(roleId);
    }
}