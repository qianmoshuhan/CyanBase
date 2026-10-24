package com.cyan.security.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.cyan.common.enums.DataScopeEnum;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysDept;
import com.cyan.entity.SysRole;
import com.cyan.security.domain.LoginUser;
import com.cyan.security.service.PermissionService;
import com.cyan.service.SysDeptService;
import com.cyan.service.SysRoleDeptService;
import com.cyan.service.SysRoleService;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * CyanBase 数据权限处理器 —— MyBatis Plus 3.5.x DataPermissionInterceptor 的 Handler。
 * <p>
 * 追加规则（追加到 SELECT / UPDATE / DELETE 的 WHERE 子句，INSERT 自动跳过）：
 * <ul>
 *   <li>scope=ALL(1) → 不追加条件（TenantLineInnerInterceptor 已保证租户隔离）</li>
 *   <li>scope=DEPT(2) → AND dept_id = 当前用户部门</li>
 *   <li>scope=DEPT_AND_CHILD(3) → AND dept_id IN (本部门 + 递归子部门)</li>
 *   <li>scope=SELF(4) → AND create_by = 当前登录用户名</li>
 *   <li>scope=CUSTOM(5) → AND dept_id IN (sys_role_dept 中绑定的部门)</li>
 * </ul>
 * <p>
 * 与 TenantLineInnerInterceptor 配合：先追加 tenant_id 条件，再追加数据范围条件，
 * 双重保险不会跨租户。
 */
@Slf4j
@Component
public class CyanDataPermissionHandler implements MultiDataPermissionHandler {

    /** 显式不追加数据权限的表（全局系统表） */
    private static final Set<String> IGNORED_TABLES = new HashSet<>(Arrays.asList(
            "sys_user_role", "sys_role_menu", "sys_role_dept", "sys_tenant_menu",
            "sys_menu", "sys_dict_type", "sys_dict_data", "sys_config", "sys_tenant",
            "sys_storage_config", "sys_third_config"
    ));

    /** 业务表部门ID列名约定（大部分表用 dept_id，少数可用表别名覆盖） */
    private static final String DEPT_COLUMN = "dept_id";
    private static final String CREATE_BY_COLUMN = "create_by";

    /**
     * ⚠️ 必须用 @Autowired @Lazy 字段注入，不能用构造器注入！
     * <p>
     * 循环依赖链：MybatisPlusAutoConfiguration 创建 sqlSessionFactory
     * → tenantMybatisPlusInterceptor 拦截器链需要 CyanDataPermissionHandler
     * → Handler 如果用构造器注入 Service → Service 依赖 baseMapper → sqlSessionFactory 还没创建好
     * <p>
     * @Lazy 让 Spring 先创建 Handler 空壳 Bean（字段先放代理），
     * 拦截器链配置 → sqlSessionFactory 创建 → 真正执行拦截器逻辑时才去拿 Service。
     */
    @Autowired @Lazy private SysRoleService roleService;
    @Autowired @Lazy private SysRoleDeptService roleDeptService;
    @Autowired @Lazy private SysDeptService deptService;
    @Autowired @Lazy private PermissionService permissionService;

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        // 1. 显式跳过开关（ThreadLocal / 注解）
        if (DataScopeContext.isSkipped()) {
            log.debug("[DataScope] ThreadLocal 跳过，表 {}", table.getName());
            return null;
        }

        // 2. 无登录态 / 超管 → 不追加
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getSysUser() == null) {
            log.debug("[DataScope] 无登录态，跳过表 {}", table.getName());
            return null;
        }
        if (permissionService.isSuperAdmin(loginUser.getUserId())) {
            log.debug("[DataScope] 平台超管，跳过表 {}", table.getName());
            return null;
        }

        // 3. 忽略表
        String tableName = table.getName();
        if (IGNORED_TABLES.contains(tableName)) {
            return null;
        }

        // 4. 拿当前用户角色（多角色时取最严格的数据范围；一般一人一角色）
        List<Long> roleIds = new ArrayList<>();
        try {
            String roles = loginUser.getRoles();
            if (roles != null && !roles.isEmpty()) {
                for (String r : roles.split(",")) {
                    if (!r.trim().isEmpty()) {
                        roleIds.add(Long.valueOf(r.trim()));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[DataScope] 解析角色失败: {}", e.getMessage());
        }
        if (roleIds.isEmpty()) return null;

        List<SysRole> roles = roleService.listByIds(roleIds);
        if (roles == null || roles.isEmpty()) return null;

        // 多角色取最严格（SELF > CUSTOM > DEPT > DEPT_AND_CHILD > ALL）
        int strictest = DataScopeEnum.ALL.value;
        for (SysRole r : roles) {
            int s = r.getDataScope() != null ? r.getDataScope() : DataScopeEnum.ALL.value;
            if (scopeWeight(s) > scopeWeight(strictest)) strictest = s;
        }
        DataScopeEnum scope = DataScopeEnum.of(strictest);

        // 5. 按 scope 构造条件
        String alias = table.getAlias() != null ? table.getAlias().getName() : tableName;
        Long deptId = loginUser.getSysUser().getDeptId();
        String userName = loginUser.getUserName();

        switch (scope) {
            case ALL:
                return null; // 租户拦截器已覆盖

            case SELF: {
                EqualsTo eq = new EqualsTo();
                eq.setLeftExpression(new Column(alias + "." + CREATE_BY_COLUMN));
                eq.setRightExpression(new StringValue(userName));
                return eq;
            }

            case DEPT: {
                if (deptId == null) return null;
                return deptIdEq(alias, deptId);
            }

            case DEPT_AND_CHILD: {
                if (deptId == null) return null;
                List<Long> ids = collectSelfAndChildren(deptId);
                if (ids.isEmpty()) return null;
                return deptIdIn(alias, ids);
            }

            case CUSTOM: {
                // 取所有角色绑定的部门集合
                List<Long> allDeptIds = new ArrayList<>();
                for (SysRole r : roles) {
                    allDeptIds.addAll(roleDeptService.list(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cyan.entity.SysRoleDept>()
                                    .eq(com.cyan.entity.SysRoleDept::getRoleId, r.getId())
                    ).stream().map(com.cyan.entity.SysRoleDept::getDeptId).toList());
                }
                if (allDeptIds.isEmpty()) return null;
                // 对每个绑定部门也展开子部门，用户体验更好
                Set<Long> expanded = new HashSet<>();
                for (Long d : allDeptIds) {
                    expanded.addAll(collectSelfAndChildren(d));
                }
                return deptIdIn(alias, new ArrayList<>(expanded));
            }
        }
        return null;
    }

    /** 计算范围严格度权重（数字越大越严格） */
    private static int scopeWeight(int scope) {
        // 使用 if-else 而非 switch：枚举实例字段 .value 不是编译时常量，不能用作 case 标签
        if (scope == DataScopeEnum.SELF.value) return 5;
        if (scope == DataScopeEnum.CUSTOM.value) return 4;
        if (scope == DataScopeEnum.DEPT.value) return 3;
        if (scope == DataScopeEnum.DEPT_AND_CHILD.value) return 2;
        return 1; // ALL
    }

    /** 递归收集部门 ID（含自身） */
    private List<Long> collectSelfAndChildren(Long parentId) {
        List<Long> result = new ArrayList<>();
        result.add(parentId);
        collectChildren(parentId, result);
        return result;
    }

    private void collectChildren(Long parentId, List<Long> bucket) {
        List<SysDept> children = deptService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getParentId, parentId)
        );
        if (children == null || children.isEmpty()) return;
        for (SysDept c : children) {
            bucket.add(c.getId());
            collectChildren(c.getId(), bucket);
        }
    }

    private Expression deptIdEq(String alias, Long deptId) {
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(alias + "." + DEPT_COLUMN));
        eq.setRightExpression(new LongValue(deptId));
        return eq;
    }

    private Expression deptIdIn(String alias, List<Long> deptIds) {
        ExpressionList list = new ExpressionList();
        list.addExpressions(deptIds.stream().map(LongValue::new).toList());
        InExpression in = new InExpression();
        in.setLeftExpression(new Column(alias + "." + DEPT_COLUMN));
        in.setRightItemsList(list);
        return in;
    }
}