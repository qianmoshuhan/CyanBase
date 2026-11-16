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
import com.cyan.service.TenantBizUnitHelper;
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
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
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

    /** 显式不追加数据权限的表（全局系统表 / 无 dept_id 字段的表） */
    private static final Set<String> IGNORED_TABLES = new HashSet<>(Arrays.asList(
            // ════════ RBAC 元数据表（L1 租户隔离已覆盖） ════════
            "sys_user_role", "sys_role_menu", "sys_role_dept", "sys_tenant_menu", "sys_tenant_module",
            // ════════ 租户内经营单元绑定（无 dept_id 中间表） ════════
            "sys_biz_unit_scope",
            // ⚠️ sys_role 已加 dept_id → 不在 IGNORED，实现部门私有角色（NULL=全局角色）
            "sys_menu",

            // ════════ 系统配置/字典表（全局通用） ════════
            "sys_dict_type", "sys_dict_data", "sys_config", "sys_tenant",
            "sys_storage_config", "sys_third_config",

            // ════════ 无 dept_id 字段的系统级表（追加 dept_id 条件会 SQL 报错） ════════
            "sys_api_monitor",           // API 监控统计，系统级数据
            "sys_message_user",          // 用户消息关联，按 user_id 维度

            // ════════ 工作流表（定义类无 dept_id；实例类已加 dept_id → 从 IGNORED 移除，由 Handler 自动管控） ════════
            "wf_process_definition",     // 流程定义（全局配置）
            "wf_process_node",           // 流程节点（全局配置）
            "wf_process_approve",        // 审批记录（依附实例，实例管控即可）
            "wf_process_copy",           // 抄送记录（依附实例）
            "wf_node_runtime",           // 节点运行时（依附实例）
            "wf_def_version",            // 流程定义版本
            "wf_definition_tenant",      // 租户分发关联表
            // ⚠️ wf_process_instance 已加 dept_id → 不在 IGNORED，由 Handler 自动追加发起部门条件

            // ════════ 表单模块表（定义类无 dept_id；填报实例已加 dept_id） ════════
            "form_definition",           // 表单定义（全局配置）
            "form_def_version",          // 表单版本
            "form_field",                // 表单字段配置
            "form_data_item",            // 表单数据项（依附 form_data，实例管控即可）
            // ⚠️ form_data 已加 dept_id → 不在 IGNORED，由 Handler 自动追加填报部门条件

            // ════════ 消息模块 ════════
            "sys_message",               // 消息通知表（只有 tenant_id，无 dept_id）

            // ════════ 定时任务 ════════
            "sys_job",                   // 定时任务
            "sys_job_log",               // 定时任务日志

            // ════════ 三大系统业务表（t_three_big_system/list/meeting_topic 是配置/关联表；meeting/topic/execute 已加 dept_id） ════════
            "t_three_big_system",        // 制度（全局配置，按 publish_dept VARCHAR 显示）
            "t_three_big_list",          // 清单（全局配置）
            "t_three_big_meeting_topic", // 会议-议题关联表（中间表）
            // ⚠️ t_three_big_meeting, t_three_big_topic 已加 dept_id → 不在 IGNORED
            // ⚠️ t_three_big_execute 已加 execute_dept_id → 不在 IGNORED，由 DEPT_COLUMN_MAP 映射

            // ════════ 系统日志/审计/其他 ════════
            "sys_log_login",             // 登录日志
            "sys_logininfor",            // 登录信息（旧表）
            "sys_oper_log",              // 操作日志（旧表）
            "sys_notice",                // 通知公告
            "sys_user_online",           // 在线用户
            "sys_file",                  // 文件上传
            "sys_form",                  // 旧表单表（兼容）
            "wf_definition",             // 旧流程定义表（兼容）
            "gen_table"                  // 代码生成表

            // ⚠️ 有 dept_id（或自定义部门列）的表 → 不要加进 IGNORED，让 DataPermissionInterceptor 自动覆盖：
            //    sys_user (dept_id), sys_post (dept_id), sys_log_operation (dept_id),
            //    wf_form_process_binding (dept_id), t_three_big_item (submit_dept_id) ← 需 DEPT_COLUMN_MAP 映射
    ));

    /** 业务表部门ID列名：大部分表用 dept_id，sys_dept 主键直接是 id */
    private static final String DEFAULT_DEPT_COLUMN = "dept_id";
    private static final String CREATE_BY_COLUMN = "create_by";
    /**
     * 特殊表的部门列名映射（表名 → 部门列名）
     * ⚠️ 以后新增业务表如果部门列名不是默认 dept_id，必须在此加映射！
     */
    private static final Map<String, String> DEPT_COLUMN_MAP = Map.of(
            "sys_dept", "id",                         // sys_dept 主键是 id，没有 dept_id 字段
            "t_three_big_item", "submit_dept_id",      // 三大事项表用 submit_dept_id（填报部门）
            "t_three_big_execute", "execute_dept_id"   // 三大执行跟踪表用 execute_dept_id（执行部门）
    );

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
    @Autowired @Lazy private TenantBizUnitHelper bizUnitHelper;

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        // 1. 显式跳过开关（ThreadLocal / 注解）
        if (DataScopeContext.isSkipped()) {
            log.debug("[DataScope] ThreadLocal 跳过，表 {}", table.getName());
            return null;
        }

        // 2. 忽略表 → 无论如何都不追加（不依赖登录态，省掉后续开销）
        String tableName = table.getName();
        if (IGNORED_TABLES.contains(tableName)) {
            return null;
        }

        // 3. 无登录态 → 不追加
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getSysUser() == null) {
            log.debug("[DataScope] 无登录态，跳过表 {}", tableName);
            return null;
        }

        // ✅ 关键：scope 决策阶段需要查 sys_role / sys_dept / sys_role_dept 等元数据表，
        // 这些查询本身会再次触发 DataPermissionInterceptor → 又进 getSqlSegment → ♻️ 无限递归
        // 解决：决策阶段整体 skip，元数据查询不应该再被 DataScope 过滤
        DataScopeContext.skip();
        try {
            return doGetSqlSegment(table, tableName, loginUser);
        } finally {
            DataScopeContext.reset();
        }
    }

    /**
     * 实际的 DataScope 决策逻辑（在 skip 保护块内执行）
     */
    private Expression doGetSqlSegment(Table table, String tableName, LoginUser loginUser) {
        // ══════ 优先介入：租户内经营单元覆盖模式 ══════
        // 设计对齐：只对普通租户用户生效，平台主体账号（platformPrincipal=true）完全禁用此能力
        // 介入时机：在原角色 dataScope switch 判断之前，有 activeBizUnitId 则直接覆盖
        Long activeBizUnitId = loginUser.getActiveBizUnitId();
        boolean isPlatformPrincipal = Boolean.TRUE.equals(loginUser.getPlatformPrincipal());
        if (!isPlatformPrincipal && activeBizUnitId != null) {
            return doBizUnitOverride(table, tableName, loginUser, activeBizUnitId);
        }

        // 超管 → 跳过（内部查 sys_role，需要 skip 保护）
        if (permissionService.isSuperAdmin(loginUser.getUserId())) {
            log.debug("[DataScope] 平台超管，跳过表 {}", tableName);
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

        // ✅ L2 DataScope 决策日志（debug，方便排查）
        Long deptIdForLog = loginUser.getSysUser().getDeptId();
        log.debug("[DataScope] 决策: userId={}, deptId={}, roles={}, scope={}({}), 表={}",
                loginUser.getUserId(), deptIdForLog,
                roles.stream().map(r -> r.getId() + ":" + r.getRoleName()).toList(),
                scope, scope.value, tableName);

        // 5. 按 scope 构造条件
        String alias = table.getAlias() != null ? table.getAlias().getName() : tableName;
        Long deptId = loginUser.getSysUser().getDeptId();
        String userName = loginUser.getUserName();
        // 关键：不同表的"部门列名"不一样！sys_dept 用 id，其他表用 dept_id
        String deptColumn = DEPT_COLUMN_MAP.getOrDefault(tableName, DEFAULT_DEPT_COLUMN);

        Expression result;
        switch (scope) {
            case ALL:
                return null; // 租户拦截器已覆盖（ALL 范围全量可见，包括全局+部门私有角色）

            case SELF: {
                if (userName == null) {
                    log.debug("[DataScope] SELF → 跳过: userName 为 null");
                    return null;
                }
                EqualsTo eq = new EqualsTo();
                eq.setLeftExpression(new Column(alias + "." + CREATE_BY_COLUMN));
                eq.setRightExpression(new StringValue(userName));
                log.debug("[DataScope] SELF(create_by='{}') → SQL片段: {}", userName, eq);
                result = eq;
                break;
            }

            case DEPT: {
                if (deptId == null) {
                    log.debug("[DataScope] DEPT → 跳过: deptId 为 null");
                    return null;
                }
                result = deptIdEq(alias, deptColumn, deptId);
                log.debug("[DataScope] DEPT({}={}) → SQL片段: {}", deptColumn, deptId, result);
                break;
            }

            case DEPT_AND_CHILD: {
                if (deptId == null) {
                    log.debug("[DataScope] DEPT_AND_CHILD → 跳过: deptId 为 null");
                    return null;
                }
                List<Long> ids = collectSelfAndChildren(deptId);
                if (ids.isEmpty()) {
                    log.debug("[DataScope] DEPT_AND_CHILD → 跳过: roots={} 收集到 0 个部门", deptId);
                    return null;
                }
                result = deptIdIn(alias, deptColumn, ids);
                log.debug("[DataScope] DEPT_AND_CHILD(roots={}, 收集到{}个部门) → SQL片段: {}", deptId, ids.size(), result);
                break;
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
                if (allDeptIds.isEmpty()) {
                    log.debug("[DataScope] CUSTOM → 跳过: sys_role_dept 无绑定部门");
                    return null;
                }
                // 对每个绑定部门也展开子部门，用户体验更好
                Set<Long> expanded = new HashSet<>();
                for (Long d : allDeptIds) {
                    expanded.addAll(collectSelfAndChildren(d));
                }
                result = deptIdIn(alias, deptColumn, new ArrayList<>(expanded));
                log.debug("[DataScope] CUSTOM(绑定部门={}, 展开后{}个) → SQL片段: {}", allDeptIds, expanded.size(), result);
                break;
            }

            default:
                return null;
        }

        // ⭐ sys_role 特殊处理：所有范围都能看到全局角色 (dept_id IS NULL)
        // 语义：全局角色 (dept_id=NULL) 不归属于任何部门，所有用户应该都能看到并分配给本部门用户
        //       部门私有角色 (dept_id=非空) 只有本部门+子部门的用户能看到
        if ("sys_role".equals(tableName)) {
            try {
                // 用 CCJSqlParserUtil 解析字符串表达式，避免不同 jsqlparser 版本 IsNull 类路径不一致
                Expression isNullExpr = CCJSqlParserUtil.parseExpression(alias + ".dept_id IS NULL");
                OrExpression or = new OrExpression();
                or.setLeftExpression(result);
                or.setRightExpression(isNullExpr);
                // ⚠️ 关键！必须用 Parenthesis 包起来！
                // 因为 MyBatisPlus 会把 Handler 返回的 Expression 用 AND 拼到 WHERE 里：
                //   WHERE 原有条件 AND (dept_id IN (...) OR dept_id IS NULL)
                // 不加括号 → WHERE 原有条件 AND dept_id IN (...) OR dept_id IS NULL
                //           → AND 优先级 > OR → 所有全局角色被放出来！TooManyResultsException！
                Parenthesis wrapped = new Parenthesis();
                wrapped.setExpression(or);
                result = wrapped;
                log.debug("[DataScope] sys_role 追加全局角色可见条件 → SQL片段: {}", result);
            } catch (Exception e) {
                log.warn("[DataScope] sys_role 构造 IS NULL 条件失败: {}", e.getMessage());
                // 失败就返回原条件，至少不会报错
            }
        }
        return result;
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
        // 注意：此方法调用方已在外层 getSqlSegment 里统一 DataScopeContext.skip() 保护，
        // 内部 deptService.list 不会再触发 DataPermissionInterceptor 递归
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

    private Expression deptIdEq(String alias, String column, Long deptId) {
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(alias + "." + column));
        eq.setRightExpression(new LongValue(deptId));
        return eq;
    }

    private Expression deptIdIn(String alias, String column, List<Long> deptIds) {
        ExpressionList list = new ExpressionList();
        list.addExpressions(deptIds.stream().map(LongValue::new).toList());
        InExpression in = new InExpression();
        in.setLeftExpression(new Column(alias + "." + column));
        in.setRightItemsList(list);
        return in;
    }

    /**
     * ══════ 租户内经营单元覆盖模式（首期仅实现覆盖，AND 叠加预留） ══════
     * <p>
     * 规则：activeBizUnitId 存在时，直接跳过角色本身 dataScope 配置，
     * 改为经营单元自身 + 所有子部门做 dept_id IN (...) 过滤。
     * </p>
     *
     * @param bizUnitId 当前选中的经营单元部门ID
     * @return SQL 条件片段，null=不追加
     */
    private Expression doBizUnitOverride(Table table, String tableName, LoginUser loginUser, Long bizUnitId) {
        String alias = table.getAlias() != null ? table.getAlias().getName() : tableName;
        String deptColumn = DEPT_COLUMN_MAP.getOrDefault(tableName, DEFAULT_DEPT_COLUMN);

        List<Long> ids = bizUnitHelper.collectBizUnitAndChildren(bizUnitId);
        if (ids.isEmpty()) {
            log.debug("[DataScope-BizUnit] 覆盖模式：bizUnitId={} 展开到 0 个部门，跳过表 {}", bizUnitId, tableName);
            return null;
        }

        Expression result = deptIdIn(alias, deptColumn, ids);
        log.debug("[DataScope-BizUnit] 覆盖模式：bizUnitId={}, 展开{}个部门, 表={}, SQL={}",
                bizUnitId, ids.size(), tableName, result);
        return result;
    }
}