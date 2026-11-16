# RBAC 权限系统

> PermissionGuard 三层原子校验 + DB 按钮权限清单 + 新增菜单三层漏斗 Checklist。
> 全局约束请先读 👉 参考：00-README.md

---

## 1. 核心定义：menu_type 枚举（MariaDB schema 已确认）

```sql
menu_type COMMENT: '类型 1目录 2菜单 3按钮'
-- 1=目录（无路由无组件，纯分组）
-- 2=菜单（有路由/组件，页面可访问，perms 也被 PermissionServiceImpl 查询到）
-- 3=按钮（无路由无组件，纯权限标识 perms，被 PermissionServiceImpl 查询到）
```
---
## 2. 权限查询链路

```
Controller 方法
  → @RequiresPermission("system:role:add")
  → PermissionAspect.around()
  → SecurityUtils.hasPermi("system:role:add")
  → SecurityUtils.getCurrentUserPermissions()
  → LoginUser.permissions（JWT filter 注入）
  → 或 Redis cache "rbac:perms:{userId}"
  → 或 PermissionServiceImpl.queryUserPermissions(userId)
      （SQL JOIN sys_user_role → sys_role_menu → sys_menu
       WHERE menu_type IN (2,3)）
```

## 3. 约束规则：超管放通统一入口 [R]

👉 参考：00-README.md [约束 #13] + [核心定义-平台 admin 跨租户行为]

> 超管（role_key='admin', tenantId=0）放通逻辑 **只在 PermissionGuard.doCheck() 入口统一跳过全部三层**，三层内部禁止独立写 isSuperAdmin 判断。

```java
// PermissionGuard.java doCheck() 入口统一放通：
if (isSuperAdmin || userPerms.contains("*:*:*")) {
    return;  // 跳过 L1/L2/L3 全部校验
}
```

[DEPRECATED] ~~PermissionAspect.java 内部独立超管判断~~：
```java
// ❌ 旧废弃方案，已全部委托 PermissionGuard
if (ann.superAdminBypass() && userPerms.contains("*:*:*")) {
    return pjp.proceed();
}
```

---

---

## 4. PermissionGuard：三层执行门槛统一入口

> 2026-10-23 落地。原来的 TenantAuthAspect（租户身份）+ PermissionAspect（按钮权限）两个 AOP 切面，以及 Controller 里散放的 tenantId.equals() 比对 + DataScope 主动校验完全缺失，现在全部统一到 PermissionGuard 一个入口。三层顺序 **L1→L2→L3 强制不可颠倒，不允许拆分调用**。

### 4.1 三层原子校验顺序

```
┌──────────────────────────────────────────────────────────┐
│            PermissionGuard.check(scope, ...perms)        │
│                                                          │
│  ⚡ 入口统一放行：isSuperAdmin || perms 含 *:*:*          │
│     → 跳过全部三层，禁止分层重复写超管判断                  │
│                                                          │
│  第 1 层 L1  租户硬隔离（401/403 BusinessException）        │
│     登录态 → 租户身份 → 资源归属比对                        │
│                                                          │
│  第 2 层 L2  DataScope 数据范围（403 BusinessException）    │
│     解析最严格 scope → 校验目标 dept/createBy 是否在范围内  │
│     读操作 targetScope=null 自动跳过                        │
│                                                          │
│  第 3 层 L3  按钮级 RBAC（403 AccessDeniedException）       │
│     AND/OR 逻辑 + 双向通配符匹配                             │
│                                                          │
│  ✅ 三层全过 → proceed()                                  │
└──────────────────────────────────────────────────────────┘
```

### 4.2 与 AOP / MP 拦截器的关系

| 组件 | 定位 | 是否保留 |
|---|---|---|
| **PermissionGuard** | 新增，Controller 层前置主动校验，三层原子入口 | ✅ 核心 |
| **PermissionAspect** | AOP 兼容入口，内部委托 PermissionGuard.check(null, ..., perms) | ✅ 保留作注解入口 |
| **TenantAuthAspect** | 原租户身份校验 AOP | ✅ 保留作日志/兜底 |
| **DataPermissionInterceptor** | SQL 级数据范围过滤（读操作兜底） | ✅ 保留 |
| **TenantLineInnerInterceptor** | SQL 级租户隔离（兜底） | ✅ 保留 |

### 4.3 为什么需要三层原子校验？

原来的问题（已修复）：

| 问题 | 根因 | 修复 |
|---|---|---|
| SELF 范围用户改别人创建的角色/用户/部门 | DataScope 只有 SQL 拦截器过滤**查询结果**，写操作**主动校验完全缺失** | L2 主动校验补漏洞 |
| TenantRoleController update 等方法散放 tenantId.equals() 比对 | 各 Controller 自己写，14 处散落 + 不一致 | PermissionGuard L1 统一承担 |
| TenantDeptController.update 无任何主动校验，只靠 MP 兜底 | 漏写了 | PermissionGuard 强制全三层 |

---

---

## 5. 历史快照：项目进度（2026-10-20，仅供参考）

> [R] 所有 add 方法第一行 setId(null) 👉 参考：00-README.md [约束 #1]

### P0 已完成（setId 防御已补全）

| Controller | 侧 | DB perms | setId 防御 |
|---|---|---|---|
| SysRoleController | 平台 | system:role:* | sysRole.setId(null) |
| TenantRoleController | 租户 | system:role:* | 已有 |
| SysUserController | 平台 | system:user:* | sysUser.setId(null) |
| TenantUserController | 租户 | system:user:* | sysUser.setId(null) |
| SysMenuController | 平台 | system:menu:* | sysMenu.setId(null) |
| SysDeptController | 平台 | system:dept:* | sysDept.setId(null) |
| TenantDeptController | 租户 | 同上 | sysDept.setId(null) |
| SysPostController | 平台 | system:post:* | post.setPostId(null) ⚠️ 主键是 postId |

### P1 已完成（403 验证通过）

| Controller | perms | setId 防御 |
|---|---|---|
| SysDictTypeController | system:dict:*（Type+Data 共用一套） | dictType.setId(null) |
| SysDictDataController | 同上 | dictData.setId(null) |
| SysConfigController | system:config:* | config.setId(null) |
| SysNoticeController | system:notice:* | notice.setNoticeId(null) ⚠️ 主键是 noticeId |

> 主键字段名不一定叫 id——写 setId(null) 前先 grep `@TableId` 确认。

---

## 6. DB sys_menu 按钮权限清单

| id | parent_id | 菜单名称 | perms | 备注 |
|---|---|---|---|---|
| 19 | 4 岗位管理 | 岗位新增 | system:post:add | — |
| 20 | 4 | 岗位修改 | system:post:edit | — |
| 21 | 4 | 岗位删除 | system:post:remove | — |
| 22 | 5 字典管理 | 字典新增 | system:dict:add | Type+Data 两 Controller 共用 |
| 23 | 5 | 字典修改 | system:dict:edit | 同上 |
| 24 | 5 | 字典删除 | system:dict:remove | 同上 |
| 25 | 8 在线用户 | 强退用户 | monitor:online:kick | — |
| 26 | 9 定时任务 | 任务执行 | monitor:job:start | — |
| 27 | 9 | 任务暂停 | monitor:job:pause | — |
| 32 | 29 用户管理 | 用户新增 | system:user:add | — |
| 33 | 29 | 用户修改 | system:user:edit | — |
| 34 | 29 | 用户删除 | system:user:remove | — |
| 35 | 30 角色管理 | 角色新增 | system:role:add | — |
| 36 | 30 | 角色修改 | system:role:edit | — |
| 37 | 30 | 角色删除 | system:role:remove | — |
| 38 | 31 菜单管理 | 菜单新增 | system:menu:add | — |
| 39 | 31 | 菜单修改 | system:menu:edit | — |
| 40 | 31 | 菜单删除 | system:menu:remove | — |
| 66 | 41 部门管理 | 部门新增 | system:dept:add | id=69 重复已删 |
| 67 | 41 | 部门修改 | system:dept:edit | id=70 重复已删 |
| 68 | 41 | 部门删除 | system:dept:remove | id=71 重复已删 |
| 72 | 6 参数配置 | 参数新增 | system:config:add | P1 新建 |
| 73 | 6 | 参数修改 | system:config:edit | P1 新建 |
| 74 | 6 | 参数删除 | system:config:remove | P1 新建 |
| 75 | 7 公告管理 | 通知新增 | system:notice:add | P1 新建 |
| 76 | 7 | 通知修改 | system:notice:edit | P1 新建 |
| 77 | 7 | 通知删除 | system:notice:remove | P1 新建 |
| — | — | 审批 | workflow:approve | 2026-10-21 新增（之前漏了导致审批被拒） |

---

## 7. 约束规则：接口排除规则（不加注解的场景） [R]

👉 参考：00-README.md [约束 #6] 业务模块下拉接口不加注解

| 接口类型 | 示例 | 原因 |
|---|---|---|
| 用户改自己密码 | PUT /tenant/user/profile/password | 个人功能，不是管理员功能 |
| 查自己信息 | GET /tenant/user/profile | 个人功能 |
| 下拉辅助查询 | GET /system/user/allRole, /system/user/getRoleIds/{uid} | 页面渲染依赖 |
| 工作流远程搜索 | GET /system/user/search, /system/dept/search | 工作流节点配置用 |
| 纯列表查询 | GET /system/menu/list, /system/role/list | 一般查询不加 |
| **业务模块下拉** | GET /sydyd/system/all, /workflow/definition/published | 前端表单/节点配置依赖，卡死填报流程 |

---

## 8. 隐形约束固化表（必须严格遵守）

| # | 约束 | 说明 |
|---|------|------|
| 1 | **注解写法固定** | add→xxx:add, update→xxx:edit, delete→xxx:remove，不能自定义 |
| 2 | **DB 先查再插** | 加按钮权限前先 `SELECT ... WHERE perms LIKE 'xxx:%' AND menu_type=3` 确认有没有 |
| 3 | **所有 add 方法 setId(null)** | 主键冲突防御（注意主键字段名不一定是 id） |
| 4 | **租户侧 Controller 必须抄全** | 平台侧有多少写操作接口必须一比一抄 |
| 5 | **租户和平台共用 perms** | 不要造 tenant:xxx:* |
| 6 | **DB 字段名先 SHOW FULL COLUMNS** | sys_menu 是 sort_num / route_path / parent_id，不是 order_num / path / parentId |
| 7 | **字典两个 Controller 共用权限** | SysDictTypeController 和 SysDictDataController 都用 system:dict:* |
| 8 | **改完必须清 Redis 缓存** | `redis-cli KEYS "rbac:perms:*" \| xargs redis-cli DEL` + rbac:menu:* |
| 9 | **验证用无权限账号** | test6（租户ID=6, userId=7）/ 654123 → 预期写操作 403、下拉查询 200 |
| 10 | **业务模块下拉接口不加注解** | 前端渲染表单用，加注解卡死填报流程 |
| 11 | **运维动作复用已有按钮权限** | 定时任务 pause/resume/executeOnce 用 monitor:job:pause / monitor:job:start，不要造新 perms |
| 12 | **SHOW FULL COLUMNS 是神器** | 枚举值含义、字段注释、字段类型一键看全 |

---

## 9. 剩余未排期 Controller 清单

| Controller | 目录 | 建议 perms | 需补 DB 按钮 |
|---|---|---|---|
| SysFileController | com.cyan.controller.system | system:file:* | add/edit/remove |
| SysFormController | com.cyan.controller.system | system:form:* | add/edit/remove |
| SysThirdConfigController | com.cyan.controller.system | system:third:* | add/edit/remove |
| SysTenantController | com.cyan.controller | system:tenant:* | add/edit/remove（超管专属） |
| GenTableController | com.cyan.controller.system | tool:gen:* | add/edit/remove/preview |
| SysMessageController | com.cyan.modules.message.controller | system:message:* | add/edit/remove |
| FormDefinitionController | com.cyan.modules.form.controller | system:form:* | add/edit/remove |
| FormFieldController | com.cyan.modules.form.controller | system:form:* | edit（跟随 definition） |
| FormDataController | com.cyan.modules.form.controller | system:form:* | add/edit/remove |

> 规范提醒：每个 Controller 新增时必须同时做三件事：① 写注解 ② 补 DB 按钮权限（挂 parent_id 下，menu_type=3）③ 写 setId(null) 防御。

---

## 10. 踩坑避坑表（22 条）

| # | 坑根因 | 教训 |
|---|--------|------|
| 1 | `bean(*Controller)` 切点不命中：Spring bean name 是 `tenantRoleController`（小写 c） | AOP `bean()` 用小写，或用 `execution` 包范围 |
| 2 | BCrypt hash 错给了 "password" 不是真实密码 | 必须用 `htpasswd -nbBC 10 "" 654123` 生成 |
| 3 | bash 里 `$$` grep 搜不到（是当前 shell PID） | 搜字符串别带 `$$`，直接 grep "Permission" |
| 4 | PermissionServiceImpl 漏查 menu_type=3：in(menuType, 1, 2) 漏掉按钮 | 从 DB schema 确认枚举值：1目录/2菜单/3按钮 |
| 5 | TenantRoleController 用了 tenant:role:* 但 DB 是 system:role:* | 租户侧和平台侧共用一套 perms |
| 6 | SysPost.postSort NOT NULL 无默认值 → 插入 SQL 错 | NOT NULL 字段加 Java 默认值或 DB DEFAULT |
| 7 | 前端 POST 带 id + IdType.AUTO → Duplicate entry | **所有 add 方法第一行必须显式 setId(null)** |
| 8 | TenantRoleController 漏了 getDataScope / saveDataScope | **租户侧必须抄全平台侧所有接口** |
| 9 | 切面日志用 $Permission$ grep 搜不到 | 同上 #3 |
| 10 | 实体类主键字段名不统一（SysPost→postId / SysNotice→noticeId） | 写 setId 前先 grep @TableId 确认 |
| 11 | INSERT sys_menu 字段名拼写错：order_num/path 实际是 sort_num/route_path | INSERT 前先 SHOW FULL COLUMNS |
| 12 | 部门按钮权限 INSERT 重复两次（66-68 和 69-71） | INSERT 前先 SELECT 查一遍，或加 NOT EXISTS 防重 |
| 13 | 以为有 TenantDictController，实际不存在 | 新增前先 Glob 确认目录里有没有 |
| 14 | DictTypeController + DictDataController 两个类 | 共用 system:dict:add/edit/remove 一套 perms |
| 15 | RequiresPermission import 路径：不是 com.cyan.common.security.annotation，而是 com.cyan.security.annotation | **grep 已有可编译代码复制，不要猜** |
| 16 | workflow:start 不需要插按钮权限：menu_type=2 的菜单 perms 也被查到 | 查 DB 确认菜单本身 perms 已设就不用再插 |
| 17 | 定时任务 Controller 在 modules.job.controller，不是 com.cyan.controller.system | Controller 分布：system→com.cyan.controller.system、monitor→同级、job→modules.job.controller、workflow→modules.workflow.controller、tenant→tenant.controller |
| 18 | DELETE /log/clean 和 DELETE /log/{id} 共用同一个 perms | 同一资源的不同删除动作合并一个 remove |
| 19 | PUT /pause/{id} 和 PUT /execute/{id} 复用 DB 已有 monitor:job:start / monitor:job:pause | 先查 DB 清单，已有的直接复用 |
| 20 | SHOW FULL COLUMNS 比 describe 更准 | 带 COMMENT，枚举含义直接看 |
| 21 | 三重一大 GET /system/all /list/bySystem /list/byCategory 是前端下拉接口 | 业务模块所有下拉 GET 都不加注解 |
| 22 | workflow:approve 按钮权限漏了导致 test6 审批被拒 | menu_type=2 菜单 perms 虽然被 PermissionServiceImpl 查到，但 @RequiresPermission("workflow:approve") 必须有 menu_type=3 的按钮记录 |

---

## 11. 前端自动联动

后端完善 RBAC 按钮级权限后，前端页面自动出现相应功能，无需前端改代码。

```
后端 sys_menu 加 menu_type=3 按钮记录
    ↓
前端「分配权限」调用 GET /system/menu/tree
    ↓
后端递归查 sys_menu（WHERE tenant_id=当前 AND menu_type IN (1,2,3)）
    ↓
前端 Tree 组件自动渲染 → 按钮级权限节点作为叶子节点出现
    ↓
角色勾选 → 保存 sys_role_menu 关联
    ↓
PermissionServiceImpl JOIN sys_role_menu WHERE menu_type IN (2,3) → 按钮 perms 被带出
    ↓
@RequiresPermission AOP 拦截生效 ✅
```

> **后端是 RBAC 权限的唯一事实来源（Single Source of Truth）**。新增按钮权限 = 后端加 sys_menu 记录 + Controller 加注解，前端自动联动。后端必须对 DB 按钮权限的正确性负全责。

---

## 12. 菜单可见性三层过滤（DB 配置，非执行门槛）

> ⚠️ **本节讲的是「菜单发现链路」——用户登录后看到哪些菜单，是 DB 层面的三层过滤配置**。和 §4 的「执行门槛三层」（PermissionGuard L1→L2→L3，每次写操作执行时的权限校验）是**完全不同的两个概念**。
>
> - §4 执行门槛三层 → 每次 API 调用都走 PermissionGuard
> - 本节 DB 配置三层 → 新增菜单时必须同时配 sys_menu / sys_tenant_menu / sys_role_menu

```
第 1 层：sys_menu
┃  全系统菜单库（所有新菜单必须先插入这里，tenant_id=0 或平台级）
┃
┃ ▼  TenentLine 拦截器
┃
第 2 层：sys_tenant_menu
┃  租户菜单白名单（tenant_id=N 的租户必须显式分配新菜单，否则交集干掉）
┃  ── filterByTenant() 在这一层取交集 ──
┃
┃ ▼  MyBatis-Plus JOIN
┃
第 3 层：sys_role_menu
┃  角色 ↔ 菜单关联（⚠️ tenant_id 必须对齐角色自身的 tenant_id！）
┃  ┌─────────────────────────────────────────┐
┃  │ role_id=1, admin 超管（tenant_id=0）     │  → rm.tenant_id = 0 ✅
┃  │ role_id=15, 峨眉管理（tenant_id=6）      │  → rm.tenant_id = 6 ✅ 不能写 0！
┃  └─────────────────────────────────────────┘
┃
┃ ▼  TenantLine 拦截器自动加 WHERE tenant_id = 当前租户
┃
最终菜单 = sys_role_menu 中 tenant_id 匹配 AND sys_tenant_menu 白名单命中
```

### 验证 SQL（三层全中才算通）
```sql
-- 第 1 层：菜单定义存在
SELECT * FROM sys_menu WHERE id = 113;                          -- ✅ 必须有

-- 第 2 层：租户白名单
SELECT * FROM sys_tenant_menu WHERE tenant_id = 6 AND menu_id = 113;  -- ✅ 必须有

-- 第 3 层：角色关联 + tenant_id 对齐
SELECT rm.*, r.role_name, r.tenant_id AS role_tenant, rm.tenant_id AS rm_tenant
FROM sys_role_menu rm JOIN sys_role r ON rm.role_id = r.id
WHERE rm.menu_id = 113 AND r.role_name = '峨眉管理';
-- ✅ role_tenant = rm_tenant = 6（租户级角色）
-- ✅ role_tenant = rm_tenant = 0（平台超管）
```

### 后端缓存 Key（getUserMenu 150 行）
```java
String cacheKey = "rbac:menu:flat:" + userId + ":" + (tenantId != null ? tenantId : "root");
// ⚠️ 租户用户的缓存 Key 末尾是 "userId:tenantId"
// 清缓存必须清 rbac:menu:flat:* 全部
```

---

## 13. 新增菜单完整 Checklist（三层漏斗版）

> 2026-10-23 实测：之前只做了第 1 层和第 3 层的一半（rm.tenant_id=0 没对齐），导致 curl getUserMenu 看不到新菜单。

| # | 步骤 | SQL / 操作 | 验证 |
|---|------|------------|------|
| ① | `sys_menu` 插菜单定义（含页面 type=2 + 按钮 type=3） | `INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, route_path, perms, ...)` | `SELECT * FROM sys_menu WHERE id IN (...);` |
| ② | **sys_tenant_menu 分配白名单** | <details><summary>历史旧方式</summary>`INSERT INTO sys_tenant_menu (tenant_id, menu_id) VALUES (6, 113), (6, 115), ...`</details><br>**当前版本：调用 `POST /system/role/saveMenuPerm/{roleId}` 接口会自动同步维护 sys_tenant_menu 租户菜单白名单，同时自动保证 sys_role_menu.tenant_id 与角色自身 tenant_id 对齐，无需手写 SQL；只有批量租户初始化场景才需要手动操作该表。** | `SELECT * FROM sys_tenant_menu WHERE tenant_id=6 AND menu_id IN (...);` |
| ③ | `sys_role_menu` 角色授权 | <details><summary>历史旧方式</summary>手写 SQL 必须保证 `sys_role_menu.tenant_id = 角色自身tenant_id`</details><br>**当前版本：saveMenuPerm 接口内部自动完成 tenant_id 对齐；手动写 SQL 脚本导入权限时仍然必须遵守该约束。** | 跑上面的三层验证 SQL |
| ④ | 清 Redis 缓存 | `redis-cli KEYS "rbac:*"  xargs redis-cli DEL` | — |
| ⑤ | curl 接口验证 | `curl ... /system/menu/getUserMenu  python3 递归打印` | 新菜单出现在工作流管理子节点下 |
| ⑥ | 前端刷新验证 | 浏览器强刷（Ctrl+F5） | 左侧菜单出现 |

### 隐形坑：admin 超管的 role_menu tenant_id
```
admin 超管角色（role_id=1, tenant_id=0）
  → sys_role_menu.tenant_id = 0（平台级关联，全局可见）

峨眉管理角色（role_id=15, tenant_id=6）
  → sys_role_menu.tenant_id = 6（必须对齐，TenantLine 才能查到）
  → 绝对不能默认写 0！！！
```

> **口诀**：role_menu 的 tenant_id = 角色自身的 tenant_id，一毛不差。

---

## 14. 本次新增菜单踩坑补录（接第 10 节）

| # | 坑根因 | 现象 | 教训 | 日期 |
|---|--------|------|------|------|
| 23 | 只配 sys_role_menu 没配 sys_tenant_menu | SQL 直查全有 → curl getUserMenu 全缺 | saveMenuPerm 接口会自动维护 sys_tenant_menu；**仅手动导入 SQL 脚本时会复现该坑**；通过接口分配角色菜单不会触发。 | 2026-10-23 |
| 24 | sys_role_menu.tenant_id 默认写 0 没对齐角色 | role_tenant=6 → rm_tenant=0 → TenantLine WHERE tenant_id=6 过滤掉 | saveMenuPerm 接口自动对齐 tenant_id；**仅手写 SQL 脚本导入权限时，必须保证 rm.tenant_id=r.tenant_id；接口调用不会出现该问题。** | 2026-10-23 |
| 25 | Redis 缓存没清 | 改完 DB curl 还是读老数据 | 新增菜单后必须 `redis-cli KEYS "rbac:*"  xargs redis-cli DEL` | 2026-10-23 |
| 26 | admin 管理端「分配权限」页面勾选新菜单后不生效 | 根因是三层过滤，不是前端 Bug | 旧场景根因：缺少 sys_tenant_menu、rm.tenant_id 不对齐；**现在后端 saveMenuPerm 接口自动处理**；如果页面调用该接口仍然不生效，排查 Redis 权限缓存是否清理。 | 2026-10-23 |
| 27 | PermissionGuard L3 通配符反向不匹配 | SecurityUtils.hasPermi 只做 userPerm带*→requiredPerm具体，requiredPerm带*→userPerm具体 反向不匹配 | PermissionGuard 自己实现双向通配 permMatches()，两边都试 wildcardMatches() | 2026-10-23 |
| 28 | PermissionGuard.check(String...) 便捷重载链错乱 | 转调 check(null, AND, perms) 被 check(ResourceScope,...) 的 null 检查拦截 | 便捷重载直接调 doCheck(null, AND, perms)，绕开 null 校验 | 2026-10-23 |
| 29 | Mockito IService.list() 重载歧义 | list(Wrapper)/list(IPage) 两重载，裸 any() 推断失败 | any(Wrapper.class) 或 ArgumentMatchers.<Wrapper>any() | 2026-10-23 |
| 34 | JwtAuthenticationFilter 没设 sysUser | JWT claims 缺 deptId → Handler `sysUser==null` 永远跳过 → L2 静默失效 | claims.putDeptId → Filter 解析构造轻量 SysUser | 2026-11-04 |
| 35 | Handler 内部 DB 查询无 skip 保护 → 无限递归 | isSuperAdmin() 查 sys_role → 又触发 Handler → StackOverflow | 决策阶段整体 `DataScopeContext.skip()` 包裹 | 2026-11-04 |
| 36 | sys_api_monitor / sys_message_user 无 dept_id | 被追加 `dept_id IN (...)` → SQL 报错 Unknown column | 加进 IGNORED_TABLES；**以后新增实体必须先 SHOW FULL COLUMNS 确认 dept_id** | 2026-11-04 |
| 37 | sys_dept 主键 id 不是 dept_id | DEPT_COLUMN 硬编码 dept_id → 过滤 sys_dept 报错 | DEPT_COLUMN_MAP 映射 sys_dept → id | 2026-11-04 |
| 38 | SysPostController.list 同时 skip 两个拦截器 | 手动 wrapper 只补租户条件 → DataScope 静默失效 | **只 skip CustomTenantHandler**，让 DataPermissionInterceptor 自动追加 | 2026-11-04 |
| 39 | 改完 DataScope 忘了重新登录 | 旧 token 无 deptId → Filter 解析不到 → 依旧 sysUser==null | JWT 是一次性写入的，必须重新登录或 refresh 才生效 | 2026-11-04 |
| 40 | 以为平台管理员 tenantId=0 也会被 DataScope 过滤 | Handler 内部 isSuperAdmin() 检查 role_key='admin' → 直接跳过 DataScope | 租户管理员（tenantId>0）才是 DataScope 受影响对象；验证用租户管理员账号测试 | 2026-11-04 |
| 41 | nginx 裸路径 POST (无尾斜杠) 掉进 SPA try_files | POST `/tenant/sydyd/system` 无尾斜杠、regex location 没覆盖到 → **405 Not Allowed**；try_files 只兜底 GET，POST 裸路径会 405 | **业务模块裸路径必须单独加 location regex**：`location ~ ^/tenant/sydyd/(topic\|system\|meeting\|list\|item\|execute)$ { proxy_pass http://127.0.0.1:8090; }` | 2026-11-04 |
| 42 | OrExpression 不加 Parenthesis 包 → AND > OR 优先级逃逸 | `WHERE tenant_id=6 AND (scope条件) OR dept_id IS NULL` → **所有全局角色被放出来** → TooManyResultsException | 返回 jsqlparser Expression 时，所有 OrExpression 分支必须用 **new Parenthesis() 整体包裹** | 2026-11-04 |
| 43 | jsqlparser 版本差异 IsNullExpression 类路径不一致 | 编译找不到 IsNullExpression 或 IsNull → ❌ | **统一用 `CCJSqlParserUtil.parseExpression("col IS NULL")`**，所有 jsqlparser 版本都有这个静态工厂 | 2026-11-04 |
| 44 | SecurityUtils 没暴露 getDeptId() 方法 | 编译找不到 `SecurityUtils.getDeptId()` | **先 grep SecurityUtils.java 的 public static 列表**，不要猜；deptId 在 `SecurityUtils.getLoginUser().getSysUser().getDeptId()` | 2026-11-04 |
| 45 | PermissionGuard.checkDataScope 缺 skip 保护 → 查 sys_role 被 Handler 二次过滤 | 移除 IGNORED_TABLES 后，roleService.listByIds(roleIds) 被 DataPermissionInterceptor 加 `dept_id IN (...)` → 当前用户只能看到本部门角色 → scope 解析残缺 → L2 校验误判越权 | PermissionGuard.checkDataScope 整体包 `DataScopeContext.skip() + try + finally reset()`，内部 sys_role/sys_dept/sys_role_dept 查询全量执行 | 2026-11-05 |
| 46 | 以为 PermissionGuard 是纯逻辑类不会触发 MP 拦截器 | PermissionGuard 调 roleService.listByIds() 走 Service → Mapper → 自动过 TenantLine + DataPermissionInterceptor | **任何 Service/IService 调用都走拦截器链**，不管在哪个类里调；Handler 内部决策阶段必须统一 skip | 2026-11-05 |
| 47 | sys_role 新增 dept_id 列后历史数据全 NULL | 旧角色没有 deptId → 都是全局角色（语义正确，但部门管理员创建的新角色需要自动填当前部门） | Controller 层 add() 方法兜底：`if (sysRole.getDeptId() == null) sysRole.setDeptId(SecurityUtils.getLoginUser().getSysUser().getDeptId())` | 2026-11-05 |
| 48 | Handler 构造 sys_role 条件时忘了 IS NULL 必须用 parseExpression | 手写 `new IsNullExpression()` 在 jsqlparser 4.6 改名 → 编译错误 | **所有 IS NULL 条件统一用 `CCJSqlParserUtil.parseExpression("dept_id IS NULL")`** | 2026-11-05 |
| 49 | PermissionGuard 三层与 MP 拦截器链的 skip 保护范围不一致 | Handler 内部已有 skip 保护，但 PermissionGuard 内部没有 → 双层不统一 | **Handler 决策阶段 + PermissionGuard.checkDataScope + 任何需要查元数据的 Service 内部方法**，三处统一 DataScopeContext.skip() | 2026-11-05 |
| 50 | 移除 IGNORED_TABLES 前没全局排查该表的 Service 调用点 | 只改了 Handler 忘了 PermissionGuard → L2 静默失效 | **改 IGNORED_TABLES 前 grep 全项目该表名的 service/mapper 调用**，逐一确认调用点是否需要 skip 保护 | 2026-11-05 |
| 51 | 改完 PermissionGuard skip 保护忘了清 Redis + 重新登录 | Redis 里 `rbac:perms:*` 缓存旧的 scope 数据；旧 JWT 无 deptId | **mvn clean compile + 清 rbac:* 缓存 + 重新登录**（JWT 必须重新签发才带 deptId） | 2026-11-05 |
| 52 | **双 Controller 镜像后处理逻辑漏改** | 只在平台侧 SysRoleController 加了 bumpVersionForRole，租户侧 TenantRoleController 三个变更点完全漏掉 | **任何写操作的后处理（清缓存/bump版本号），平台侧每加一处，租户侧镜像必须 grep 同步**，见 01-租户底座.md [双 Controller 同步 Checklist] | 2026-11-06 |
| 53 | **bumpVersionForRole 静默无日志 + 提前 return** | 入口、查询结果、空结果分支全没打日志，排障时完全无法定位"为什么没执行" | 日志覆盖 4 点：①入口 roleId ②查询用户数 ③空结果 WARN ④完成 INFO；**提前 return 分支也必须打 WARN 日志** | 2026-11-06 |
| 54 | **bump 调用被 updateById 返回值包裹** | `if(ok) { bump() }` → 字段无变化返回 false 时 bump 被跳过 | bump 是"权限变更的副作用"，与业务 update 成功与否无关，**必须写在 if 判断外部** | 2026-11-06 |
| 55 | **bumpVersionForRole 内部查 sys_user_role 无 skip 保护** | sys_user_role 从 IGNORED 移除 → TenantLine 追加 tenant_id 条件 → 查不到关联用户 | **必须显式 `CustomTenantHandler.skip() + try-finally reset` 防御性包裹**，即使表当前在 IGNORED 里也不能假设永远安全 | 2026-11-06 |
| 56 | **JWT Token 签发入口漏 putPermVer** | 三处签发（AuthController login/refresh + TenantAuthServiceImpl），少一处对应登录方式的用户永远没有 permVer | **grep 全项目 putPermVer 或 getOrInitVersion，确保三个入口全覆盖**，与 putTenantId/putDeptId 范式一致 | 2026-11-06 |

## 15. PermissionGuard L2/L3 细节与 Controller 散落替换清单

### 15.1 L2 DataScope 主动校验（原来只有 SQL 拦截器，现在前置）

```java
// 取所有角色 → 多角色取最严格 scope
// SELF(5) > CUSTOM(4) > DEPT(3) > DEPT_AND_CHILD(2) > ALL(1)

case ALL            → 放通（TenantLine 已兜底）
case DEPT           → targetDeptId == 当前用户 deptId
case DEPT_AND_CHILD → targetDeptId ∈ (本部门 + 递归子部门集合)
case SELF           → targetCreateBy == 当前用户名
case CUSTOM         → targetDeptId ∈ (sys_role_dept 绑定部门 + 展开子部门集合)
```

> 复用 CyanDataPermissionHandler 的 scope 解析规则，从「构造 SQL 条件」改为「主动校验目标资源归属」。

### 15.2 L3 按钮权限双向通配符

```java
// SecurityUtils.hasPermi 只覆盖：
//   正向：userPerm="system:role:*" 匹配 requiredPerm="system:role:add" ✅
// PermissionGuard 额外覆盖：
//   反向：userPerm="system:role:add" 匹配 requiredPerm="system:role:*"   ✅  ← 新增

// permMatches() 实现
if (up.equals(required)) return true;             // 精确
if (wildcardMatches(up, required)) return true;   // 正向通配
if (wildcardMatches(required, up)) return true;   // 反向通配（新增）
```

### 15.3 Controller 散落代码替换清单（2026-10-23 已全部改造）

| Controller | 原散落方式 | 改造后调用 | 散落处数 |
|---|---|---|---|
| TenantRoleController | `tenantId.equals(exist.getTenantId())` 散在 update / delete / getById / saveDataScope / saveMenuPerm / getDataScope / getMenuIds | `permissionGuard.check(ResourceScope.of(...), "system:role:edit")` | 7 |
| TenantUserController | `tenantId.equals(exist.getTenantId())` 散在 getById / update / delete / getUserBindRoleIds / saveRolePerm | `permissionGuard.check(ResourceScope.of(...), "system:user:edit")` | 5 |
| TenantDeptController | getById / delete 散放 + update **完全缺失主动校验** | `permissionGuard.check(ResourceScope.of(...), "system:dept:edit")` | 2+1 |

**实测 403 样例参考（L3 按钮权限校验）：**

- test6（租户 token）调用平台接口 `POST /system/tenant`，返回 403，提示需要 `system:tenant:add`
- test6（租户 token）调用平台接口 `DELETE /system/dict/type/{dictId}`，返回 403，提示需要 `system:dict:remove`

说明：平台 Controller 的 @RequiresPermission 按钮权限注解，对租户侧 token 一样生效，用来阻止租户直接调用平台专属接口。

### 15.4 资源管控与 PermissionGuard 的边界

PermissionGuard 只管**操作者身份**的通用门槛（"你能不能碰"），以下**资源归属特例**不在本类范围内，在校验通过后由业务层单独执行：

| 特例 | 示例 |
|---|---|
| 平台分发表单/流程 | 当前租户是否在 sys_tenant_form 分发列表里？ |
| 资源编辑权限 | 表单/流程定义是否允许租户编辑？ |
| 审批流节点权限 | 当前用户是否是当前节点审批人？ |
| 流程定义状态 | definition.status == PUBLISHED？ |

---

## 16. 工作流/表单新增菜单清单（2026-10-23）

| id | parent_id | 菜单名 | menu_type | perms | route_path | 用途 |
|----|-----------|--------|-----------|-------|------------|------|
| 113 | 65（工作流管理） | 表单流程绑定 | 2 | workflow:binding:query | binding | 管理员端绑定配置页 |
| 115 | 113 | 绑定查询 | 3 | workflow:binding:query | — | 按钮权限 |
| 116 | 113 | 绑定新增 | 3 | workflow:binding:add | — | 按钮权限 |
| 117 | 113 | 绑定修改 | 3 | workflow:binding:edit | — | 按钮权限 |
| 118 | 113 | 绑定删除 | 3 | workflow:binding:delete | — | 按钮权限 |
| 119 | 65（工作流管理） | 我的表单 | 2 | workflow:myform:list | myForm | 操作员填报入口 |
| 120 | 119 | 表单列表 | 3 | workflow:myform:list | — | 按钮权限 |
| 121 | 119 | 填报发起 | 3 | workflow:myform:start | — | 按钮权限 |

---

## 17. DataScope 数据范围原理（2026-11-04 修复版）

### 17.1 三层权限拦截器链（执行顺序不可颠倒）

```
请求 → JwtAuthenticationFilter 构建 LoginUser（含轻量 SysUser）
      ↓
      MyBatis-Plus 拦截器链（TenantMybatisPlusConfig）
      ↓
      ① PaginationInnerInterceptor    → 分页
      ② DataPermissionInterceptor    → CyanDataPermissionHandler.getSqlSegment()
      ③ TenantLineInnerInterceptor    → tenant_id=? 追加
```

### 17.2 CyanDataPermissionHandler 工作机制

```
getSqlSegment(table, where, mappedStatementId)
  │
  ├─ ① ThreadLocal skip？ → DataScopeContext.skip() 保护的调用点
  ├─ ② 无登录态 / sysUser==null → 跳过（修复前永远命中这里！）
  ├─ ③ IGNORED_TABLES？→ 跳过
  └─ ④ 整体 DataScopeContext.skip()  ← 决策阶段内部 DB 查询（sys_role/sys_dept/sys_role_dept）
          └─ doGetSqlSegment()
              ├─ 超管？→ 跳过
              ├─ scope=ALL(1) → 跳过（TenantLine 已兜底）
              ├─ scope=DEPT(2) → AND dept_id = 当前用户部门
              ├─ scope=DEPT_AND_CHILD(3) → AND dept_id IN (本部门 + 递归子部门)
              ├─ scope=SELF(4) → AND create_by = 当前用户名
              └─ scope=CUSTOM(5) → AND dept_id IN (sys_role_dept 绑定部门 + 展开子部门)
```

### 17.3 本次修复完整 Bug 链路（2026-11-04）

| # | 环节 | Bug | 修复 |
|---|------|-----|------|
| 1 | JWT claims | 三处 token 生成（AuthController login/refresh + TenantAuthServiceImpl buildLoginResponse）**都没放 deptId** | `JwtUtil.putDeptId(claims, deptId)` |
| 2 | JwtAuthenticationFilter | 构建 LoginUser 时**只设 userId/userName/roles/tenantId**，没设 sysUser | 解析 deptId → 构造轻量 SysUser（只填 deptId）→ `loginUser.setSysUser()` |
| 3 | CyanDataPermissionHandler | 第 87 行 `loginUser.getSysUser() == null` 永远命中 → **所有表全部跳过** | sysUser 有值后 Handler 才真正工作 |
| 4 | Handler 内部递归 | `doGetSqlSegment` 内部 `permissionService.isSuperAdmin()` 和 `collectChildren` 查 sys_dept → 又触发 Handler → ♻️ StackOverflow | `DataScopeContext.skip()` 整体包裹决策阶段，内部 DB 查询不再被递归拦截 |
| 5 | IGNORED_TABLES 漏表 | `sys_api_monitor`、`sys_message_user` 没有 dept_id 字段但被追加 `dept_id IN (...)` → SQL 报错 | 加进 IGNORED_TABLES |
| 6 | 列名硬编码 | `DEPT_COLUMN="dept_id"` 对 sys_dept 错误（主键是 id） | 新增 `DEPT_COLUMN_MAP` 映射，sys_dept → id |
| 7 | SysPostController.list | 同时 skip CustomTenantHandler + DataScopeContext，手动 wrapper **只补了租户条件没补 DataScope** | 去掉 `DataScopeContext.skip()`，让 DataPermissionInterceptor 自动追加 |

### 17.4 IGNORED_TABLES 当前清单

| 分类 | 表 | 原因 |
|------|-----|------|
| RBAC 元数据 (关联表) | sys_user_role, sys_role_menu, sys_role_dept, sys_tenant_menu | L1 租户隔离已覆盖，Handler 内部查这些表不能递归 |
| RBAC 角色定义 | sys_role | ⚠️ **已移除 IGNORED！** 2026-11-04 实现部门私有角色，dept_id=NULL 是全局角色所有人可见 |
| RBAC 菜单定义 | sys_menu | L1 租户隔离已覆盖，全局菜单树 |
| 系统配置 | sys_dict_type, sys_dict_data, sys_config, sys_tenant, sys_storage_config, sys_third_config | 全局通用配置 |
| 无 dept_id | sys_api_monitor, sys_message_user | 表结构里根本没有 dept_id 字段 |
| 三重一大配置 | t_three_big_system (制度), t_three_big_list (清单) | 全局配置，不分部门 |
| 工作流配置 | wf_process_definition, wf_process_node, wf_definition_tenant (分发) | 全局流程定义，不分部门 |
| 表单配置 | form_definition, form_field | 全局表单设计，不分部门 |
| 中间表 | t_three_big_meeting_topic | 会议-议题关联表，业务过滤在 meeting/topic 两侧 |

> **决策原则**：配置/定义类表 → IGNORED；业务单据类表 → 加 dept_id + Handler 管控。遇到新业务表先 SHOW FULL COLUMNS 确认有没有 dept_id，没有就判断语义。

### 17.5 各表部门列名映射 (DEPT_COLUMN_MAP)

| 表 | 部门列 | 原因 |
|----|--------|------|
| sys_user, sys_post, sys_log_operation, sys_role, 业务表 | dept_id（默认） | 外键关联 sys_dept.id |
| sys_dept | **id** | 主键就是部门 ID，没有 dept_id 字段 |
| t_three_big_item | submit_dept_id | 填报部门（业务语义不同于 "归属部门"） |
| t_three_big_execute | execute_dept_id | 执行牵头部门 |

> ⚠️ 新增业务表时如果部门列名不是 dept_id，必须在 CyanDataPermissionHandler.DEPT_COLUMN_MAP 加映射！

---

### 17.6 sys_role 部门私有角色特殊机制 (2026-11-04)

#### 表结构
```sql
-- sys_role 新增 dept_id 列
ALTER TABLE sys_role 
  ADD COLUMN dept_id BIGINT NULL DEFAULT NULL 
    COMMENT '部门私有角色归属部门（NULL=全局角色，非NULL=部门私有角色）' 
    AFTER tenant_id,
  ADD INDEX idx_sys_role_dept (dept_id);
-- 现有记录 dept_id 全 NULL → 自动就是全局角色 ✅
```

#### Handler 追加的 SQL (区域经理, deptId=25, scope=DEPT_AND_CHILD)
```sql
-- 关键：必须用 Parenthesis 包 OR 条件！
SELECT * FROM sys_role WHERE dept_id IN (25, 30, 31) OR dept_id IS NULL
                                                   ↑ Parenthesis 包起来
等价于: (dept_id IN (25,30,31)) OR dept_id IS NULL
```

#### 语义矩阵
| dept_id 值 | 角色类型 | 谁能看到 | 谁创建 |
|---|---|---|---|
| NULL | 全局角色 | 所有人 (超管 + 区域经理 + 部门管理员) | 超管 |
| 25 | 部门私有角色 | 本部门 + 子部门 (25,30,31) 的用户 | deptId=25 的部门管理员 |
| 30 | 部门私有角色 | 本部门 + 子部门 (30) 的用户 | deptId=30 的部门管理员 |

#### 自动填充逻辑 (Controller 层)
```java
// SysRoleController.add() / TenantRoleController.add()
if (sysRole.getDeptId() == null) {
    try {
        Long currentDeptId = SecurityUtils.getLoginUser().getSysUser().getDeptId();
        if (currentDeptId != null) {
            sysRole.setDeptId(currentDeptId);  // 部门管理员 → 部门私有角色
        }
        // 超管 (currentDeptId=null) → role.deptId 保持 null → 全局角色
    } catch (Exception ignored) {}
}
```

#### PermissionGuard.checkDataScope skip 保护代码（2026-11-05 修复）
```java
// PermissionGuard.java — 完整重构
private void checkDataScope(ResourceScope targetScope) {
    DataScopeContext.skip();          // ← 跳过当前线程所有 DataPermissionInterceptor
    try {
        doCheckDataScope(targetScope); // 纯业务逻辑：解析 role.dataScope → switch 校验
    } finally {
        DataScopeContext.reset();      // ← 必须 finally 清掉，线程池复用会污染后续请求
    }
}

/** checkDataScope 的实际执行逻辑（在 skip 保护下运行） */
private void doCheckDataScope(ResourceScope targetScope) {
    // 内部三个 DB 查询全部在 skip 保护下执行：
    //   ① roleService.listByIds(roleIds)    → 查 sys_role（刚从 IGNORED 移除！不能被 dept_id 过滤）
    //   ② collectSelfAndChildren 递归 deptService.list() → 查 sys_dept 完整部门树
    //   ③ collectCustomScopeDepts roleDeptService.list() → 查 sys_role_dept 完整绑定

    // 解析当前用户最严格数据范围
    List<SysRole> roles = roleService.listByIds(roleIds);
    int strictest = DataScopeEnum.ALL.value;
    for (SysRole r : roles) {
        int s = r.getDataScope() != null ? r.getDataScope() : DataScopeEnum.ALL.value;
        if (scopeWeight(s) > scopeWeight(strictest)) strictest = s;
    }
    DataScopeEnum scope = DataScopeEnum.of(strictest);

    boolean inScope = switch (scope) {
        case ALL           -> true;
        case DEPT          -> deptId != null && targetScope.targetDeptId() != null
                                  && deptId.equals(targetScope.targetDeptId());
        case DEPT_AND_CHILD-> {
            Set<Long> allowed = collectSelfAndChildren(deptId);
            yield allowed.contains(targetScope.targetDeptId());
        }
        case SELF          -> targetScope.targetCreateBy() != null
                                  && userName.equals(targetScope.targetCreateBy());
        case CUSTOM         -> {
            Set<Long> allowed = collectCustomScopeDepts(roleIds);
            yield allowed.contains(targetScope.targetDeptId());
        }
    };
    if (!inScope) throw new BusinessException(403, "无数据权限");
}
```

#### 隐性约束
- PermissionGuard.checkDataScope 必须加 skip 保护（已修复），内部查 sys_role 元数据不能被 Handler 过滤
- Handler 内部决策块已有 skip 保护，不受影响
- 所有 OrExpression 返回给 Handler 必须用 Parenthesis 包裹（约束 #42）
- 改完必须 mvn clean compile + 清 rbac:* 缓存 + 重新登录（JWT 重新签发才带 deptId）

---

## 18. 权限版本预埋（permVer）warn 模式完整设计（2026-11-06）

👉 全局约束参考：00-README.md [约束 #43] [约束 #44] [约束 #45] [约束 #46] [约束 #47]
👉 双 Controller 镜像同步参考：01-租户底座.md [双 Controller 同步 Checklist]

### 18.1 设计定位：预埋 warn 模式，不阻断

| 维度 | 设计 |
|---|---|
| 存储 | Redis `sys:user:perm:ver:{userId}` 存 Long 单调递增计数器 |
| JWT 携带 | claims `permVer` 字段，与 `tenantId`/`deptId` 范式对齐 |
| 校验方式 | 过滤器比对：JWT permVer < Redis current → **打 WARN 放行** |
| 阻断模式 | 同一处分支把 `log.warn` 换成 `reject` 即可，架构已预埋 |
| 降级策略 | Redis 异常/key 不存在 → **静默放行**，不阻断业务 |
| 开关 | `permission.version-warn-enabled: true`（配置文件） |

### 18.2 完整链路

```
① 权限变更（角色菜单/dataScope/基础信息变更 / 用户角色绑定变更）
  → Controller 调用 PermissionVersionService.bumpVersionForRole(roleId)
    或 bumpVersion(userId)
  → Redis INCR sys:user:perm:ver:{userId}
  → 关联用户版本号 +1

② 用户下次请求
  → JwtAuthenticationFilter 解析 JWT 拿到 permVer
  → 从 Redis 读 currentPermVer
  → permVer < currentPermVer → log.warn 放行（warn 模式）
  → permVer == currentPermVer → 正常放行

③ 用户重新登录
  → PermissionVersionService.getOrInitVersion(userId)
  → Lua 原子：key 已存在返回当前值；不存在 SET 为 0
  → JWT 携带最新版本号
```

### 18.3 Token 签发点 Checklist（三处，缺一不可）

| # | 文件 | 方法 | putPermVer |
|---|------|------|------------|
| ① | `AuthController.java` | `login()` | `jwtUtil.putPermVer(claims, getOrInitVersion(sysUser.getId()))` |
| ② | `AuthController.java` | `refresh()` | `jwtUtil.putPermVer(claims, getOrInitVersion(userId))` |
| ③ | `TenantAuthServiceImpl.java` | `buildLoginResponse()` | `jwtUtil.putPermVer(claims, getOrInitVersion(sysUser.getId()))` |

> 🔐 这三处任何一处漏了 putPermVer，对应登录方式的用户 JWT 就永远没有版本号，过滤器比对时 permVer=null → 静默放行 → warn 模式失效。

### 18.4 bump 变更点全量清单（平台侧 + 租户侧镜像）✅ 全部已落地

| # | 变更类型 | 平台侧入口 | 租户侧镜像入口 | bump 调用 | 状态 |
|---|----------|-----------|---------------|-----------|------|
| ① | 角色基础信息修改 | `SysRoleController.update()` | `TenantRoleController.update()` | `bumpVersionForRole(roleId)` | ✅ |
| ② | 角色菜单权限变更 | `SysRoleController.saveRoleMenu()` | `TenantRoleController.saveMenuPerm()` | `bumpVersionForRole(roleId)` | ✅ |
| ③ | 角色数据范围变更 | `SysRoleController.saveDataScope()` | `TenantRoleController.saveDataScope()` | `bumpVersionForRole(roleId)` | ✅ |
| ④ | **角色删除** | `SysRoleController.delete()` | `TenantRoleController.delete()` | `bumpVersionForRole(roleId)` | ✅ |
| ⑤ | 用户角色绑定变更 | `SysUserServiceImpl.updateUserAndRole()` | `TenantUserController.saveRolePerm()` | `bumpVersion(userId)` | ✅ |
| ⑥ | **用户基础信息修改（含部门）** | `SysUserServiceImpl.updateUserAndRole()`（ServiceImpl 内 bump） | `TenantUserController.update()`（同上 ServiceImpl 间接） | `bumpVersion(userId)` | ✅ |

> ⚠️ **隐性坑**：⑥ TenantUserController.update 没有直接调 bump——bump 在 `SysUserServiceImpl.updateUserAndRole()` 内部执行。Controller 层只补了诊断日志（`log.info("[TenantUser-update] userId=...")`）。如果未来 ServiceImpl 被绕过直接调 updateById，就会漏 bump。

### 18.5 bumpVersionForRole 内部 skip 保护的必要性

```
bumpVersionForRole(roleId) 内部执行：
  sysUserRoleMapper.selectList(eq(roleId))
  → 查 sys_user_role 表

虽然 sys_user_role 在 IGNORED_TABLES 里（TenantLine 自动跳过），
但仍需显式 CustomTenantHandler.skip() + try-finally reset，原因：

1. 防御性：未来如果 sys_user_role 被移出 IGNORED，这里不会静默坏
2. 查关联用户时可能跨租户（平台 admin 给租户角色 bump）
3. IGNORED_TABLES 不是永久契约，随时可能因业务调整变动
```

### 18.6 bump 必须写在 if(ok) 判断外部

```java
// ❌ 错误：updateById 返回 false（字段无变化）时 bump 被跳过
boolean ok = sysRoleService.updateById(sysRole);
if (ok) {
    permissionVersionService.bumpVersionForRole(sysRole.getId());
}

// ✅ 正确：版本号递增是权限变更的副作用，与 update 成功与否无关
boolean ok = sysRoleService.updateById(sysRole);
permissionVersionService.bumpVersionForRole(sysRole.getId());  // 在 if 外部
return ok ? R.ok() : R.fail("更新失败");
```

### 18.7 ⚠️⚠️⚠️ warn → block 切换点（全局唯一拦截入口，后续必改）

> 🔴🔴🔴 **这是架构预埋的核心切换点**。从 warn 模式（仅打日志放行）切到 block 模式（拦截拒绝），**全项目只改这一处代码**。
>
> 切换时机：等线上观察一段时间，确认权限变更 → bump 链路稳定、无漏改、无误伤后执行。

**位置**：`JwtAuthenticationFilter.java`，JWT 校验通过后的版本比对分支

**当前 warn 模式代码**：
```java
if (permVer != null && currentPermVer != null && permVer < currentPermVer) {
    // ✅ 当前：warn 模式 —— 只打日志，放行请求
    log.warn("[PermVer] 权限快照过期: userId={}, jwtPermVer={}, currentPermVer={}",
             userId, permVer, currentPermVer);
}
```

**切换步骤（三步走）**：

| 步骤 | 改动 | 文件/位置 |
|------|------|-----------|
| ① | 把上面 `log.warn(...)` 换成 `throw new AccessDeniedException("权限已变更，请重新登录")` | `JwtAuthenticationFilter.java` 版本比对分支 |
| ② | 关闭开关：`application.yml` 里 `permission.version-warn-enabled: false` | 配置文件（如需灰度控制） |
| ③ | 清 Redis 所有 `sys:user:perm:ver:*` + 强制全部用户重新登录（重新签发带最新 permVer 的 JWT） | 运维操作 |

**注意事项**：
- 切换后旧 JWT（permVer < currentPermVer）访问会**直接 403 AccessDeniedException**，前端跳登录页
- 首次切换建议先灰度：开关只对部分租户关闭 warn，或先在测试环境全量验证
- 如果发现某个 bump 变更点漏改 → 某个场景权限变更不触发 bump → 用户版本号不变 → block 模式下永远不被拦截 → 等于白切

### 18.8 修复链路完整回顾（2026-11-06）

```
问题：修改角色菜单/dataScope → bumpForRole 无日志、未执行
  ↓ 排查
  根因 ①：租户侧用户请求走 /tenant/role/** → 完全不进 SysRoleController
           → 只改了平台侧，TenantRoleController 三个变更点完全漏改
  根因 ②：bump 被 if(updateById 返回值) 包裹 → 字段无变化返回 false 时 bump 跳过
  根因 ③：bumpVersionForRole 入口静默无日志 + 提前 return → 完全无法排障
  根因 ④：内部查 sys_user_role 无 skip 保护 → 表移出 IGNORED 会静默坏
  ↓ 修复（5 步）
  ① 新增 PermissionVersionService → Lua 原子版本号管理 + skip + 全链路日志
  ② Token 三处签发入口补 putPermVer（login + refresh + buildLoginResponse）
  ③ 过滤器 warn 模式比对分支（配置开关）
  ④ 补全 平台侧 5 个 + 租户侧 6 个 bump 变更点（含角色删除）
  ⑤ bump 全部移到 if(ok) 外部
  ↓ 验证
  ✅ mvn clean compile 编译通过
  ✅ 三个修复场景日志精准打出
  ✅ 租户侧删角色 → boundUsers=0 → bumpForRole WARN（预期行为，已 boundUsers 校验拦截在前面）
  ✅ warn 模式下旧 token 权限过期 → log.warn + 放行
```

---

## 19. 文件管理菜单停用 vs 按钮权限边界（2026-10-26）

### 19.1 两层含义不能混淆

sys_menu 表里的记录有两层含义，**修改时必须只改一层**：

```
┌───────────────────────────────────────────────────────┐
│  sys_menu.visible / status （菜单入口层）              │
│  ───────────────────────────────────────
│  visible=1 → 前端路由不注册 → 侧边栏不显示            │
│  status=1  → 后端菜单树过滤 → 同样不显示              │
│  ❌ 只影响前端入口，不影响后端接口！                  │
│                                                       │
│  sys_role_menu 关联的 menu_type=3 按钮（权限码层）    │
│  ───────────────────────────────────────
│  perms=system:file:add → 后端 @RequiresPermission 拦截│
│  ✅ 只要按钮权限还在 → 后端放行                         │
│  ✅ 三重一大/工作流的附件上传能正常调 upload 接口       │
└───────────────────────────────────────────────────────┘
```

### 19.2 文件管理停用 SQL

```sql
-- 只禁用文件管理主菜单（让前端侧边栏入口消失）
-- 绝对不能把菜单下的「上传文件」按钮权限也禁用！
UPDATE sys_menu SET visible = 1, status = 1 WHERE id = 13;

-- ✅ 保留：menu_id=125 上传文件（system:file:add）
-- ✅ 保留：menu_id=126 删除文件（system:file:remove，后端已 403，前端可不管）
```

### 19.3 download/preview 不加 L3 的设计决策

```java
// ✅ upload：加 L3 add 权限（租户需要上传能力）
@RequiresPermission("system:file:add")
public R<SysFile> upload(...) { ... }

// ✅ download/preview：不加 L3，只靠 L1 租户归属校验
public ResponseEntity<?> download(@PathVariable Long fileId) {
    SysFile file = sysFileService.getById(fileId);
    permissionGuard.check(ResourceScope.of(file.getTenantId(), null, null));  // 只有 L1
    ...
}
```

**决策理由**：附件权限依附于所属业务单据的 DataScope，不单独提供「查看本租户全部附件」的入口。如果 download/preview 也加 `system:file:query` 权限码，租户管理员角色必须同时分配 add 和 query 才能上传后再下载，多一道配置容易漏配。

### 19.4 风险提示

download/preview 不加 L3 只靠 L1 是**设计选择**而非安全缺陷。如果未来需要引入「某个角色不能下载附件但能上传」的细粒度控制，再补上 L3 即可。当前版本租户安全由以下三层兜底：

| 层 | 机制 | 作用 |
|---|---|---|
| L1 租户归属 | PermissionGuard.check(ResourceScope.of(file.getTenantId(),...)) | 只能下载自己租户的文件 |
| MP 拦截器 | TenantLineInnerInterceptor | 读操作 SQL 自动追加 tenant_id 过滤（兜底） |
| 上传 tenantId | TenantMetaObjectHandler | INSERT 自动填 tenant_id，不会填错 |

---

## 20. sys_menu route_path 重复导致 Element Plus 菜单高亮冲突

### 20.1 现象
平台侧登录 → 系统监控下面有两个「接口监控」菜单 → 点任意一个 → **两个按钮都显示选中状态**。

### 20.2 根因
数据库里有两条 API 监控菜单 `route_path` 完全一样（比如一条是 `apiMonitor`，另一条是 `api-monitor` 但最终都匹配前端的 `/monitor/apiMonitor` 路由），导致 Element Plus 菜单组件的高亮匹配了两个菜单项。

### 20.3 修复

```sql
-- 先确认重复记录
SELECT id, parent_id, menu_name, route_path, component, visible, status
FROM sys_menu WHERE route_path LIKE '%apiMonitor%' OR route_path LIKE '%api-monitor%';

-- 保留一条，另一条停用或删除
UPDATE sys_menu SET visible = 1, status = 1 WHERE id = <重复那条的id>;
```

### 20.4 预防

**新增菜单前必须查 route_path 唯一性**：
```sql
SELECT COUNT(*) FROM sys_menu WHERE parent_id = <同一父菜单id> AND route_path = '<要新增的路径>';
```

---

## 21. 按钮权限 Checklist 更新（含今日增量）

| # | 步骤 | 今日新增注意事项 |
|---|------|------|
| ① | sys_menu 插菜单定义 | **route_path 同级唯一**（防止菜单高亮冲突） |
| ② | sys_tenant_menu 分配白名单 | — |
| ③ | sys_role_menu 角色授权 | role_menu.tenant_id = 角色自身 tenant_id（必须对齐） |
| ④ | 清 Redis 缓存 | `redis-cli KEYS "rbac:*" \| xargs redis-cli DEL` |
| ⑤ | curl 接口验证 | — |
| ⑥ | 前端刷新验证 | — |
| ⑦ | **校验前端 prop ↔ 后端字段名** | 后端返回 `id/title/senderName`，前端 prop 必须严格匹配，不能随意缩写成 `msgId/msgTitle` |
| ⑧ | **校验 el-option value ↔ 后端参数类型** | 后端要 Integer → `:value="1"`；后端要 String → `value="1"` |
| ⑨ | **MP 拦截器自动填充 tenantId** | 新增业务表不需要手动 setTenantId，让拦截器管 |
| ⑩ | **文件下载/预览不加 L3** | 只靠 L1 租户归属，避免租户漏配权限无法下载自己上传的附件 |

---

## 租户菜单池越权修复（2026-11-04 · P0 全链路修复）（见原 §21 节，编号冲突保留）

### 四层防御体系

```
① 源头层    SysRoleController.availableMenus
              GET /system/role/availableMenus?tenantId=N
              → sys_tenant_menu 取池 + 补齐父节点 → 构树
              ✅ 前端下拉租户后菜单树自动裁剪，源头就看不到池外菜单

② 写入层    SysRoleController.saveMenuPerm
              POST /system/role/saveMenuPerm/{roleId}
              Step 1: ⭐ 先校验截断（tenantId>0 时过滤池外 menuId）
              Step 2: 清旧 sys_role_menu
              Step 3: 写新 sys_role_menu（tenantId 显式对齐）
              ✅ 越权菜单进不了 DB

③ 桥梁层    AuthController + JWT
              login 时 jwtUtil.putTenantId(claims, sysUser.getTenantId())
              → JwtAuthenticationFilter 解析放入 LoginUser
              ✅ 后端链路能准确拿到租户边界，不依赖 SecurityUtils 隐式上下文

④ 兜底层    SysMenuController.filterByTenant + PermissionServiceImpl.filterByTenantMenuPool
              菜单展示层：角色菜单 ∩ 租户菜单池
              perms 组装层：角色菜单 ∩ 租户菜单池 → perms 码
              ✅ 历史脏数据也无法生效，@RequiresPermission 兜底
```

### 关键代码位置

| 层 | 文件 | 方法/行号 | 核心逻辑 |
|----|------|-----------|----------|
| ① 源头层 | SysRoleController.java | availableMenus():255 | 查 sys_tenant_menu 取池 + collectParentIds 补父节点 |
| ② 写入层 | SysRoleController.java | saveRoleMenu():242 | Step 1 先校验截断，池为空时 return fail |
| ③ 桥梁层 | AuthController.java | login():104 | jwtUtil.putTenantId(claims, sysUser.getTenantId()) |
| ③ 桥梁层 | JwtAuthenticationFilter.java | :80 | Long tenantId = claims.get("tenantId", Long.class) |
| ④ 兜底层 1 | SysMenuController.java | filterByTenant() | roleMenus ∩ tenantMenuIds |
| ④ 兜底层 2 | PermissionServiceImpl.java | filterByTenantMenuPool():110 | menuIds ∩ poolMenuIds → perms |
| 回显过滤 | SysRoleController.java | getMenuIds() | 按 tenant_menu 池过滤脏数据 |

### 本次修复的 Bug 清单

| # | 位置 | 现象 | 根因 | 修复 |
|---|------|------|------|------|
| Bug-1 | SysRoleController saveRoleMenu Step 3 | admin 给租户角色分配菜单 → 自动追加到 sys_tenant_menu → 撑爆菜单池 | 设计错误：saveBatch(toInsert) 把 role_menu 反向同步进了 tenant_menu | **删除该逻辑**，改为先校验截断再写入 |
| Bug-2 | SysRoleController saveRoleMenu 执行顺序 | 越权数据已落库后才截断 | Step 1 删旧 → Step 2 写新 → Step 3 截断 | 调整为 Step 1 先校验截断 → Step 2 删旧 → Step 3 写新 |
| Bug-3 | AuthController login() | SecurityUtils.getTenantId() 全 null | JWT claims 里没有 tenantId | jwtUtil.putTenantId(claims, sysUser.getTenantId()) |
| Bug-4 | PermissionServiceImpl getPermsByUserId() | @RequiresPermission 绕过租户边界 | 没做 tenant_menu 池交集过滤 | 新增 filterByTenantMenuPool() 硬边界 |
| Bug-5 | SysRoleController getMenuIds() | 历史越权数据回显到编辑弹窗 | 直接查 sys_role_menu 原样返回 | 加 tenant_menu 池交集过滤 |

### 踩坑补录（30-33）

| # | 坑 | 根因 | 教训 | 日期 |
|---|----|------|------|------|
| 30 | 新增 availableMenus 接口后 SysRoleController 编译不过 | SysMenu / TreeBuildUtil / SysTenantMenuService 没 import + 没 @Resource | 新增接口前先 grep 项目已有类似代码的 import 列表，不要猜包路径 | 2026-11-04 |
| 31 | PermissionServiceImpl 重复定义 filterByTenantMenuPool | 同时在 ServiceImpl 和 Controller 各写了一份 → javac 编译中断 → Lombok 注解处理器不执行 → 大量 get/set 连锁报错 | **一个方法只定义一份**；编译报大量 get/set 错时，第一条错误才是根因，后面全是连锁反应 | 2026-11-04 |
| 32 | SysRoleController saveTenantMenus 接口描述错误 | 之前文档写「saveMenuPerm 会自动维护 sys_tenant_menu」→ 反向污染根因 | 租户菜单池只能由平台侧维护，角色菜单写入前只做校验截断，**绝对不能反向同步** | 2026-11-04 |

> 📌 后续踩坑补录统一维护在 **§14 主坑点表**（line 362+），本节专题区域不再重复。

---

## 21. 隐形约束固化表增量（今日补充）

| # | 约束 | 说明 |
|---|------|------|
| 13 | **前端 prop 必须与后端实体字段名严格一致** | SysMessage 返回 `id/title/senderName`，前端不能写 `msgId/msgTitle/createBy`，否则 el-table 渲染空白 |
| 14 | **新增业务表 ALTER TABLE 先 SHOW FULL COLUMNS** | 不要假设某个字段存在，用 SHOW FULL COLUMNS 确认 |
| 15 | **sys_menu route_path 同级唯一** | 重复导致 Element Plus 菜单高亮冲突，新增前必须 COUNT 查唯一性 |
| 16 | **download/preview 不加 L3 按钮权限，只靠 L1 租户归属** | 附件权限依附于业务单据的 DataScope，不单独提供租户级附件列表入口 |
| 17 | **停用文件管理主菜单 = UPDATE sys_menu visible=1 status=1** | 只隐藏侧边栏入口，按钮 perms 不受影响，三重一大/工作流的附件上传不受影响 |
| 18 | **新增只读/预览类接口通常不加 L3 按钮权限** | 版本预览 `GET /workflow/definition/{id}/version/{versionId}/bpmn` 属于只读查看场景，复用已有 workflow:definition:query 权限即可，不额外造 version:preview 新 perms |
| 19 | **版本快照是只读数据，不涉及 RBAC 写操作授权** | 版本功能本质是流程定义的历史快照查看，所有写操作（新建版本/保存快照）最终都是往 wf_def_version 表 INSERT，复用 workflow:definition:add 或 workflow:definition:edit 权限，不需要新增 workflow:version:* 全套按钮 |
| 20 | **axios 接口参数类型与后端 @RequestParam/@PathVariable 类型必须对齐** | 后端 Controller 用 `@PathVariable Long versionId`，前端必须把 versionId 直接拼在 URL 里（`/version/${versionId}`），**不能**用 `data: { versionId }` 走 @RequestBody 路径——axios 会把 Long 转 JSON Object，后端 Spring 尝试 Object → Long 直接炸 |
| 21 | **新建业务子模块（如版本表 wf_def_version）不需要新增独立 sys_menu 按钮权限** | 复用父模块的 perms 体系：版本列表用 workflow:definition:query、创建版本用 workflow:definition:add、保存版本快照用 workflow:definition:edit、删除版本用 workflow:definition:remove，**不造 workflow:version:* 新 perms 前缀** |
| 22 | **JWT 必须携带 deptId（方案 C，与 tenantId 范式对齐）** | 三处 token 生成（AuthController login/refresh + TenantAuthServiceImpl buildLoginResponse）必须 claims.put("deptId", sysUser.getDeptId())；JwtAuthenticationFilter 必须解析后 `loginUser.setSysUser(new SysUser())` 只填 deptId；用户换部门后需重新登录/刷新 token 生效（与 tenantId 行为一致） |
| 23 | **Controller 不要同时 skip CustomTenantHandler 和 DataScopeContext** | 要解决 count/records 不对称，**只 skip CustomTenantHandler 即可**，DataPermissionInterceptor 对两者追加同一条条件不会不对称；同时 skip 两个 → 手动 wrapper 只补租户条件、漏了 DataScope → L2 静默失效 |
| 24 | **新增实体必须先 SHOW FULL COLUMNS 确认是否有 dept_id 字段** | 有 dept_id → 自动被 DataPermissionInterceptor 覆盖；没有 → 必须加进 CyanDataPermissionHandler.IGNORED_TABLES（否则 SQL 报错 Unknown column） |
| 25 | **Handler 内部 DB 查询必须整体 skip 保护** | doGetSqlSegment 内部查 sys_role（isSuperAdmin + listByIds）、查 sys_dept（collectChildren）、查 sys_role_dept（CUSTOM scope）全部用 `DataScopeContext.skip()` 包裹 + finally reset；任何漏保护都会导致 ♻️ 无限递归 StackOverflow |
| 26 | **sys_dept 表过滤用 id 不用 dept_id** | CyanDataPermissionHandler.DEPT_COLUMN_MAP.put("sys_dept", "id") 映射硬编码；业务表统一默认 dept_id；以后新增特殊表必须加映射 |

---

## 22. 工作流版本功能权限总结（2026-10-28 新增）

## 23. 用户管理新增校验接口权限（2026-10-30）

### 23.1 新增的接口

| 接口 | HTTP | @RequiresPermission | 说明 |
|---|---|---|---|
| `GET /tenant/user/checkPhone?phone=&excludeId=` | GET | ❌ 不加 | 前端表单实时校验手机号租户内唯一性，属于辅助查询接口 |
| `GET /system/user/checkPhone?phone=&tenantId=&excludeId=` | GET | ❌ 不加 | 平台侧同样功能，需显式传 tenantId |

### 23.2 为什么不加 @RequiresPermission

实时校验接口和工作流远程搜索（`/system/user/search`）、岗位管理下拉类似——**前端表单渲染依赖**。加了权限注解后，租户管理员角色必须额外分配 `system:user:query` 权限才能使用"新增用户"表单的手机号校验功能，多一道配置容易漏。

### 23.3 已删除的接口约束失效（2026-10-30）

00-README.md 约束 #19：`GET /tenant-auth/tenant/list 为匿名不鉴权接口` → **已删除该接口，此约束失效**，后续可清理。

### 21.1 版本功能权限归属：复用现有 perms，不新增

| 接口 | HTTP | 复用 perms | 原因 |
|------|------|-----------|------|
| 版本列表 | `GET /workflow/definition/{id}/versions` | workflow:definition:query | 只读查询 |
| 新建版本 | `POST /workflow/definition/{id}/version` | workflow:definition:add | 本质是 INSERT 一条版本记录 |
| 版本 BPMN 预览 | `GET /workflow/definition/{id}/version/{vid}/bpmn` | workflow:definition:query | 只读查看 |
| 保存版本快照 | `PUT /workflow/definition/{id}/version/{vid}` | workflow:definition:edit | 本质是 UPDATE 版本记录的 flow_json |

### 21.2 版本前端按钮权限处理

版本功能的前端按钮（版本选择下拉、新建版本、保存快照）都在工作流设计器页面内部，由 workflow:definition:query/add/edit/remove 四个已有按钮权限控制，不需要新增前端 v-perm 指令或后端 sys_menu 记录。

### 21.3 版本权限的边界

版本功能的核心约束不是 RBAC，而是**数据隔离**：
- 版本快照 `flow_json` 里存的是完整自洽数据，不依赖主表任何 ID，天然跨版本隔离
- 租户侧只能看到本租户创建的版本（TenantLine 自动过滤 wf_def_version.tenant_id）
- 平台 admin 可以看到全租户版本但不能编辑（只读模式强制 disabled）
- 版本删除 = 物理删除 wf_def_version 一条记录，级联删除不需要 skip（TenantLine 追加 tenant_id 条件是正确的，因为 wf_def_version 有 tenant_id 字段）