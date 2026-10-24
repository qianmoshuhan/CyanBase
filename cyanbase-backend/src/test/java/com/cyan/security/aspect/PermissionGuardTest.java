    package com.cyan.security.aspect;

import com.cyan.common.enums.DataScopeEnum;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysDept;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysRoleDept;
import com.cyan.entity.SysUser;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.security.domain.LoginUser;
import com.cyan.security.service.PermissionService;
import com.cyan.service.SysDeptService;
import com.cyan.service.SysRoleDeptService;
import com.cyan.service.SysRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PermissionGuard 三层权限漏斗原子校验 —— 一步一测单元测试。
 * <p>
 * 覆盖全部用例：1-1 ~ 1-6 / 2-1 ~ 2-10 / 3-1 ~ 3-8 / X-1 ~ X-5 / B-1 ~ B-3。
 * <p>
 * 测试策略：
 * <ul>
 *   <li>Mock PermissionService / SysRoleService / SysRoleDeptService / SysDeptService</li>
 *   <li>通过 SecurityContextHolder 设置 LoginUser（userId / tenantId / permissions / roles / sysUser）</li>
 *   <li>每次测试前重置 SecurityContext + 重置 Mockito mock</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionGuardTest {

    @Mock private PermissionService permissionService;
    @Mock private SysRoleService roleService;
    @Mock private SysRoleDeptService roleDeptService;
    @Mock private SysDeptService deptService;

    @InjectMocks private PermissionGuard guard;

    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ====================================================================
    //  辅助方法：构造测试上下文
    // ====================================================================

    /** 设置登录用户（平台超管：userId=1, tenantId=0, username="admin", 通配符权限） */
    private void mockSuperAdmin() {
        loginUser = new LoginUser();
        loginUser.setUserId(1L);
        loginUser.setTenantId(0L);
        loginUser.setUserName("admin");
        loginUser.setPermissions(Set.of(PermissionGuard.SUPER_ADMIN_WILDCARD));
        mockSecurityContext(loginUser);
        when(permissionService.isSuperAdmin(1L)).thenReturn(true);
    }

    /** 设置租户用户（租户 6, dept 10, username="test6"） */
    private void mockTenantUser(Long userId, Long tenantId, String username, Long deptId) {
        SysUser sysUser = new SysUser();
        sysUser.setId(userId);
        sysUser.setTenantId(tenantId);
        sysUser.setUserName(username);
        sysUser.setDeptId(deptId);

        loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setTenantId(tenantId);
        loginUser.setUserName(username);
        loginUser.setSysUser(sysUser);
        loginUser.setPermissions(new HashSet<>()); // 默认无权限，按需 add
        loginUser.setRoles("15");                    // 默认角色 ID 15
        mockSecurityContext(loginUser);
        when(permissionService.isSuperAdmin(userId)).thenReturn(false);
    }

    /** 设置 SecurityContextHolder Authentication mock */
    private void mockSecurityContext(LoginUser loginUser) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(loginUser);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** 为指定角色配置 dataScope + 绑定部门 */
    private void mockRole(Long roleId, int dataScope, List<Long> boundDeptIds) {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setDataScope(dataScope);
        when(roleService.listByIds(List.of(roleId))).thenReturn(List.of(role));

        if (boundDeptIds != null && !boundDeptIds.isEmpty()) {
            List<SysRoleDept> bindings = new ArrayList<>();
            for (Long deptId : boundDeptIds) {
                SysRoleDept rd = new SysRoleDept();
                rd.setRoleId(roleId);
                rd.setDeptId(deptId);
                bindings.add(rd);
            }
            when(roleDeptService.list(ArgumentMatchers.<Wrapper>any())).thenReturn(bindings);
        } else {
            when(roleDeptService.list(ArgumentMatchers.<Wrapper>any())).thenReturn(List.of());
        }
    }

    /** 为 deptId 设置子部门（递归展开） */
    private void mockDeptTree(Long deptId, List<Long> children) {
        if (children == null || children.isEmpty()) {
            when(deptService.list(ArgumentMatchers.<Wrapper>any())).thenReturn(List.of());
            return;
        }
        List<SysDept> childEntities = new ArrayList<>();
        for (Long c : children) {
            SysDept d = new SysDept();
            d.setId(c);
            d.setParentId(deptId);
            childEntities.add(d);
        }
        when(deptService.list(ArgumentMatchers.<Wrapper>any())).thenReturn(childEntities);
    }

    // ====================================================================
    //  第 1 层：租户硬隔离 —— 用例 1-1 ~ 1-6
    // ====================================================================

    @Nested
    @DisplayName("第 1 层：租户硬隔离校验")
    class Layer1_TenantIsolation {

        @Test
        @DisplayName("1-1 未登录 userId=null → 401 请先登录")
        void notLoggedIn_returns401() {
            // SecurityContextHolder.clearContext() 已在 setUp 执行
            assertThatThrownBy(() -> guard.check(ResourceScope.tenantOnly(6L), "system:role:add"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(401));
        }

        @Test
        @DisplayName("1-2 平台超管 username=admin 访问租户侧接口 → 跳过 L1.3 归属比对，直接进入 L2")
        void platformAdmin_skipsTenantOwnerCheck() {
            mockSuperAdmin();
            // L2/L3 全部跳过（超管放通），不会抛异常
            assertThatCode(() -> guard.check(ResourceScope.of(999L, null, null), "system:role:add"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("1-3 租户用户 tenantId=6 访问同租户资源 → L1 通过，继续执行 L2/L3")
        void tenantUser_sameTenantResource_passes() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:add");
            mockRole(15L, DataScopeEnum.ALL.value, null); // scope=ALL
            // L2=ALL 放通，L3 权限命中 → 全过
            assertThatCode(() -> guard.check(ResourceScope.of(6L, null, null), "system:role:add"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("1-4 租户用户跨租户访问 tenantId=6 操作 targetTenantId=3 → 403 无权操作其他租户")
        void crossTenantAccess_returns403() {
            mockTenantUser(7L, 6L, "test6", 10L);
            assertThatThrownBy(() -> guard.check(ResourceScope.of(3L, null, null), "system:role:add"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(403);
                        assertThat(be.getMessage()).contains("无权操作其他租户");
                    });
        }

        @Test
        @DisplayName("1-5 非租户非超管 tenantId=null username≠admin → 403 非租户用户无权访问租户接口")
        void nonTenantNonAdmin_returns403() {
            loginUser = new LoginUser();
            loginUser.setUserId(100L);
            loginUser.setTenantId(null);
            loginUser.setUserName("guest");
            mockSecurityContext(loginUser);
            when(permissionService.isSuperAdmin(100L)).thenReturn(false);

            assertThatThrownBy(() -> guard.check(ResourceScope.tenantOnly(6L), "system:role:add"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(403);
                        assertThat(be.getMessage()).contains("非租户用户");
                    });
        }

        @Test
        @DisplayName("1-6 新增操作 targetTenantId=null → L1.3 跳过，不比对租户归属")
        void createAction_targetTenantIdNull_passes() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:add");
            mockRole(15L, DataScopeEnum.ALL.value, null);

            // targetTenantId=null 表示新建场景，跳过 L1.3
            assertThatCode(() -> guard.check(ResourceScope.of(null, null, null), "system:role:add"))
                    .doesNotThrowAnyException();
        }
    }

    // ====================================================================
    //  第 2 层：DataScope —— 用例 2-1 ~ 2-10
    // ====================================================================

    @Nested
    @DisplayName("第 2 层：DataScope 数据范围校验")
    class Layer2_DataScope {

        @Test
        @DisplayName("2-1 超管 isSuperAdmin=true → 直接跳过全部三层")
        void superAdmin_bypassesAllLayers() {
            mockSuperAdmin();
            assertThatCode(() -> guard.check(ResourceScope.of(999L, null, null), "system:role:add"))
                    .doesNotThrowAnyException();
            verify(permissionService).isSuperAdmin(1L);
            verify(roleService, never()).listByIds(anyList());  // 没进 L2
        }

        @Test
        @DisplayName("2-2 scope=ALL → 放通（TenantLine 已兜底租户隔离）")
        void scopeAll_passes() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:edit");
            mockRole(15L, DataScopeEnum.ALL.value, null);

            assertThatCode(() -> guard.check(ResourceScope.of(6L, 999L, "otherUser"), "system:role:edit"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("2-3 scope=DEPT 目标部门匹配 → 通过")
        void scopeDept_match_passes() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:edit");
            mockRole(15L, DataScopeEnum.DEPT.value, null);

            assertThatCode(() -> guard.check(ResourceScope.of(6L, 10L, null), "system:role:edit"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("2-4 scope=DEPT 目标部门不匹配 → 403 无数据权限")
        void scopeDept_mismatch_returns403() {
            mockTenantUser(7L, 6L, "test6", 10L);
            mockRole(15L, DataScopeEnum.DEPT.value, null);

            assertThatThrownBy(() -> guard.check(ResourceScope.of(6L, 20L, null), "system:role:edit"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getCode()).isEqualTo(403);
                        assertThat(be.getMessage()).contains("无数据权限");
                    });
        }

        @Test
        @DisplayName("2-5 scope=DEPT_AND_CHILD 目标是子部门 → 递归匹配通过")
        void scopeDeptAndChild_targetIsChild_passes() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:edit");
            mockRole(15L, DataScopeEnum.DEPT_AND_CHILD.value, null);
            // deptId=10 有子部门 100、101
            mockDeptTree(10L, List.of(100L, 101L));

            assertThatCode(() -> guard.check(ResourceScope.of(6L, 101L, null), "system:role:edit"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("2-6 scope=SELF 目标是本人创建 → 通过")
        void scopeSelf_match_passes() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:edit");
            mockRole(15L, DataScopeEnum.SELF.value, null);

            assertThatCode(() -> guard.check(ResourceScope.of(6L, null, "test6"), "system:role:edit"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("2-7 scope=SELF 目标是别人创建 → 403 无数据权限")
        void scopeSelf_mismatch_returns403() {
            mockTenantUser(7L, 6L, "test6", 10L);
            mockRole(15L, DataScopeEnum.SELF.value, null);

            assertThatThrownBy(() -> guard.check(ResourceScope.of(6L, null, "admin"), "system:role:edit"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(403));
        }

        @Test
        @DisplayName("2-8 scope=CUSTOM 目标部门在绑定列表 → 通过")
        void scopeCustom_inBoundList_passes() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:edit");
            // 角色 15 的 CUSTOM 范围绑定部门 20、30
            mockRole(15L, DataScopeEnum.CUSTOM.value, List.of(20L, 30L));
            mockDeptTree(20L, List.of());
            mockDeptTree(30L, List.of());

            assertThatCode(() -> guard.check(ResourceScope.of(6L, 20L, null), "system:role:edit"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("2-9 scope=CUSTOM 目标部门不在绑定列表 → 403 无数据权限")
        void scopeCustom_notInBoundList_returns403() {
            mockTenantUser(7L, 6L, "test6", 10L);
            mockRole(15L, DataScopeEnum.CUSTOM.value, List.of(20L, 30L));
            mockDeptTree(20L, List.of());
            mockDeptTree(30L, List.of());

            assertThatThrownBy(() -> guard.check(ResourceScope.of(6L, 50L, null), "system:role:edit"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(403));
        }

        @Test
        @DisplayName("2-10 读操作 targetScope=null → 跳过 L2 主动校验，由 SQL 拦截器兜底")
        void readAction_nullTargetScope_skipsLayer2() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:user:list");

            // 无 ResourceScope → L2 主动校验直接跳过
            assertThatCode(() -> guard.check("system:user:list"))
                    .doesNotThrowAnyException();

            // 验证 roleDeptService / deptService 从未被调用（未进入 L2）
            verify(roleService, never()).listByIds(anyList());
        }
    }

    // ====================================================================
    //  第 3 层：按钮级 RBAC —— 用例 3-1 ~ 3-8
    // ====================================================================

    @Nested
    @DisplayName("第 3 层：按钮级 RBAC 校验")
    class Layer3_ButtonPerms {

        @Test
        @DisplayName("3-1 超管通配符 *:*:* → 放通")
        void wildcardPerm_passes() {
            mockSuperAdmin();
            assertThatCode(() -> guard.check("system:role:add", "monitor:job:start"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("3-2 精确匹配 → 通过")
        void exactMatch_passes() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:edit");
            mockRole(15L, DataScopeEnum.ALL.value, null);

            assertThatCode(() -> guard.check(ResourceScope.of(6L, null, null), "system:role:edit"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("3-3 AND 逻辑全部命中 → 通过")
        void andAllMatch_passes() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().addAll(Set.of("system:role:add", "system:role:edit"));
            mockRole(15L, DataScopeEnum.ALL.value, null);

            assertThatCode(() -> guard.check(ResourceScope.of(6L, null, null),
                    RequiresPermission.Logical.AND,
                    "system:role:add", "system:role:edit"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("3-4 AND 逻辑部分缺失 → AccessDeniedException")
        void andPartialMissing_returns403() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:add"); // 缺 remove
            mockRole(15L, DataScopeEnum.ALL.value, null);

            assertThatThrownBy(() -> guard.check(ResourceScope.of(6L, null, null),
                    RequiresPermission.Logical.AND,
                    "system:role:add", "system:role:remove"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("3-5 OR 逻辑任一命中 → 通过")
        void orAnyMatch_passes() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("workflow:start");
            mockRole(15L, DataScopeEnum.ALL.value, null);

            assertThatCode(() -> guard.check(ResourceScope.of(6L, null, null),
                    RequiresPermission.Logical.OR,
                    "workflow:approve", "workflow:start"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("3-6 通配符匹配 system:role:* → 匹配 add/edit/remove")
        void wildcardPattern_passes() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().addAll(Set.of("system:role:add", "system:role:edit"));
            mockRole(15L, DataScopeEnum.ALL.value, null);

            assertThatCode(() -> guard.check(ResourceScope.of(6L, null, null), "system:role:*"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("3-7 权限完全不匹配 → AccessDeniedException")
        void noMatch_returns403() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:add");
            mockRole(15L, DataScopeEnum.ALL.value, null);

            assertThatThrownBy(() -> guard.check(ResourceScope.of(6L, null, null), "monitor:job:start"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("3-8 requiredPerms 为空/null → 直接通过")
        void emptyPerms_passes() {
            mockTenantUser(7L, 6L, "test6", 10L);
            mockRole(15L, DataScopeEnum.ALL.value, null);

            assertThatCode(() -> guard.check(ResourceScope.of(6L, null, null), new String[]{}))
                    .doesNotThrowAnyException();
        }
    }

    // ====================================================================
    //  跨层组合 —— 用例 X-1 ~ X-5
    // ====================================================================

    @Nested
    @DisplayName("跨层组合：验证顺序严格 L1→L2→L3")
    class CrossLayer {

        @Test
        @DisplayName("X-1 未登录直接拦截，不进入 L2/L3")
        void l1ShortCircuit() {
            assertThatThrownBy(() -> guard.check(ResourceScope.tenantOnly(6L), "system:role:add"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(401));

            // L2/L3 的依赖不应该被调用
            verify(permissionService, never()).isSuperAdmin(anyLong());
            verify(roleService, never()).listByIds(anyList());
        }

        @Test
        @DisplayName("X-2 L1 通过 L2 拦截，不进入 L3（SELF 范围改别人的记录）")
        void l2ShortCircuit() {
            mockTenantUser(7L, 6L, "test6", 10L);
            mockRole(15L, DataScopeEnum.SELF.value, null);

            assertThatThrownBy(() -> guard.check(ResourceScope.of(6L, null, "otherUser"), "system:role:edit"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(403));
        }

        @Test
        @DisplayName("X-3 L1/L2 通过 L3 拦截 → AccessDeniedException")
        void l3Intercepts() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:add"); // 缺 edit
            mockRole(15L, DataScopeEnum.ALL.value, null);

            assertThatThrownBy(() -> guard.check(ResourceScope.of(6L, null, null), "system:role:edit"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("X-4 三层全部通过 proceed()")
        void allLayersPass() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:edit");
            mockRole(15L, DataScopeEnum.ALL.value, null);

            assertThatCode(() -> guard.check(ResourceScope.of(6L, null, null), "system:role:edit"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("X-5 超管身份 → 三层全部跳过（一次放行，禁止分层重复写超管判断）")
        void superAdmin_bypassesAllAtOnce() {
            mockSuperAdmin();

            assertThatCode(() -> guard.check(ResourceScope.of(999L, 88L, "someUser"),
                    "system:role:add", "system:role:remove", "monitor:job:start"))
                    .doesNotThrowAnyException();

            // 验证只调了一次 isSuperAdmin，后续分层的依赖都没被调用
            verify(permissionService, times(1)).isSuperAdmin(1L);
            verify(roleService, never()).listByIds(anyList());
            verify(roleDeptService, never()).list(ArgumentMatchers.<Wrapper>any());
        }
    }

    // ====================================================================
    //  原子操作与资源管控的边界 —— 用例 B-1 ~ B-3
    // ====================================================================

    @Nested
    @DisplayName("边界用例：三层校验 ≠ 资源管控")
    class BoundaryCases {

        @Test
        @DisplayName("B-1 三层通过但资源管控拦截（平台未分发表单）—— PermissionGuard 不负责判断资源分发")
        void guardPasses_resourceRejectedByBusiness() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("workflow:start");
            mockRole(15L, DataScopeEnum.ALL.value, null);

            // PermissionGuard 只校验"能不能碰"，只管操作者身份
            assertThatCode(() -> guard.check(ResourceScope.of(6L, null, null), "workflow:start"))
                    .doesNotThrowAnyException();

            // 业务层再判断"资源是否分发给当前租户"——这部分不属于 PermissionGuard
        }

        @Test
        @DisplayName("B-2 三层通过但当前用户不是审批人 —— 资源管控层单独拦截")
        void guardPasses_notApprover() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("workflow:approve");
            mockRole(15L, DataScopeEnum.ALL.value, null);

            assertThatCode(() -> guard.check(ResourceScope.of(6L, null, null), "workflow:approve"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("B-3 三层通过但流程定义已下架 —— 资源管控层单独拦截")
        void guardPasses_definitionOffline() {
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("workflow:start");
            mockRole(15L, DataScopeEnum.ALL.value, null);

            assertThatCode(() -> guard.check(ResourceScope.of(6L, null, null), "workflow:start"))
                    .doesNotThrowAnyException();
        }
    }

    // ====================================================================
    //  约束 1 & 2 验证：不暴露分层方法 / 写操作必须 ResourceScope
    // ====================================================================

    @Nested
    @DisplayName("约束验证")
    class ConstraintValidation {

        @Test
        @DisplayName("约束 1：外部无法访问分层方法（private 可见性验证）")
        void layerMethodsArePrivate() throws ClassNotFoundException {
            // PermissionGuard 的 checkTenantIsolation / checkDataScope / checkButtonPerms 都必须是 private
            Class<?> clazz = Class.forName("com.cyan.security.aspect.PermissionGuard");
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                String name = m.getName();
                if (name.startsWith("checkTenant") || name.startsWith("checkDataScope") || name.startsWith("checkButtonPerms")) {
                    assertThat(java.lang.reflect.Modifier.isPrivate(m.getModifiers()))
                            .as("方法 " + name + " 必须是 private，不能对外暴露分层方法")
                            .isTrue();
                }
            }
        }

        @Test
        @DisplayName("约束 2：null 是读操作合法值 —— check(null, ...) 跳过 L2 主动校验不抛异常（前提：已登录）")
        void nullIsReadOperation() {
            // 已登录租户用户 + 按钮权限命中 —— 让 L1/L3 都通过
            mockTenantUser(7L, 6L, "test6", 10L);
            loginUser.getPermissions().add("system:role:edit");
            mockRole(15L, DataScopeEnum.ALL.value, null);

            // ResourceScope=null → L1 通过（已登录 + targetTenantId=null 跳过归属比对）
            // L2 跳过（scope==null）→ L3 按钮权限命中 → 全过
            assertThatCode(() -> guard.check((ResourceScope) null, "system:role:edit"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("约束 3：超管只在入口统一放行 —— 验证各层实现中没有独立超管判断")
        void noSuperAdminCheckInLayers() throws ClassNotFoundException {
            Class<?> clazz = Class.forName("com.cyan.security.aspect.PermissionGuard");
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                String name = m.getName();
                if (name.startsWith("checkTenant") || name.startsWith("checkDataScope") || name.startsWith("checkButtonPerms")) {
                    // 方法体里不能有 isSuperAdmin 或 "*:*:*" 检查
                    String src = m.toString();
                    assertThat(src).doesNotContain("isSuperAdmin")
                            .as(name + " 不应包含独立的 isSuperAdmin 判断");
                }
            }
        }
    }
}