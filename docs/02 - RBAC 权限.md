# RBAC 权限系统

---

## 1. menu_type 枚举（MariaDB schema 已确认）

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
--
## 3. 超管放通逻辑

```java
// PermissionAspect.java:62
if (ann.superAdminBypass() && userPerms.contains("*:*:*")) {
    return pjp.proceed(); // 跳过所有校验
}
```

数据库里给超管角色（role_key='admin'）的某个菜单 perms 设成 `*:*:*`，就自动全放行。

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

## 5. 权限查询链路

---

## 5. 项目进度（2026-10-20 快照，仅供参考）

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

## 7. 接口排除规则（不加注解的场景）

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
| ② | **sys_tenant_menu 分配白名单**（⚠️ 之前漏了！） | `INSERT INTO sys_tenant_menu (tenant_id, menu_id) VALUES (6, 113), (6, 115), ...` | `SELECT * FROM sys_tenant_menu WHERE tenant_id=6 AND menu_id IN (...);` |
| ③ | `sys_role_menu` 角色授权（⚠️ tenant_id 对齐！） | `INSERT INTO sys_role_menu (role_id, menu_id, tenant_id) SELECT r.id, m.id, r.tenant_id FROM sys_role r CROSS JOIN sys_menu m WHERE r.role_name='峨眉管理'` | 跑上面的三层验证 SQL |
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
| 23 | 只配 sys_role_menu 没配 sys_tenant_menu | SQL 直查全有 → curl getUserMenu 全缺 | **租户用户要通过 filterByTenant 交集，必须同时配两层** | 2026-10-23 |
| 24 | sys_role_menu.tenant_id 默认写 0 没对齐角色 | role_tenant=6 → rm_tenant=0 → TenantLine WHERE tenant_id=6 过滤掉 | rm.tenant_id = r.tenant_id，不能偷懒写死 0 | 2026-10-23 |
| 25 | Redis 缓存没清 | 改完 DB curl 还是读老数据 | 新增菜单后必须 `redis-cli KEYS "rbac:*"  xargs redis-cli DEL` | 2026-10-23 |
| 26 | admin 管理端「分配权限」页面勾选新菜单后不生效 | 根因是三层过滤，不是前端 Bug | 页面只帮配 sys_role_menu，sys_tenant_menu 和 rm.tenant_id 对齐必须手动 SQL | 2026-10-23 |
| 27 | PermissionGuard L3 通配符反向不匹配 | SecurityUtils.hasPermi 只做 userPerm带*→requiredPerm具体，requiredPerm带*→userPerm具体 反向不匹配 | PermissionGuard 自己实现双向通配 permMatches()，两边都试 wildcardMatches() | 2026-10-23 |
| 28 | PermissionGuard.check(String...) 便捷重载链错乱 | 转调 check(null, AND, perms) 被 check(ResourceScope,...) 的 null 检查拦截 | 便捷重载直接调 doCheck(null, AND, perms)，绕开 null 校验 | 2026-10-23 |
| 29 | Mockito IService.list() 重载歧义 | list(Wrapper)/list(IPage) 两重载，裸 any() 推断失败 | any(Wrapper.class) 或 ArgumentMatchers.<Wrapper>any() | 2026-10-23 |

---

## 16. PermissionGuard L2/L3 细节与 Controller 散落替换清单

### 16.1 L2 DataScope 主动校验（原来只有 SQL 拦截器，现在前置）

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

### 16.2 L3 按钮权限双向通配符

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

### 16.3 Controller 散落代码替换清单（2026-10-23 已全部改造）

| Controller | 原散落方式 | 改造后调用 | 散落处数 |
|---|---|---|---|
| TenantRoleController | `tenantId.equals(exist.getTenantId())` 散在 update / delete / getById / saveDataScope / saveMenuPerm / getDataScope / getMenuIds | `permissionGuard.check(ResourceScope.of(...), "system:role:edit")` | 7 |
| TenantUserController | `tenantId.equals(exist.getTenantId())` 散在 getById / update / delete / getUserBindRoleIds / saveRolePerm | `permissionGuard.check(ResourceScope.of(...), "system:user:edit")` | 5 |
| TenantDeptController | getById / delete 散放 + update **完全缺失主动校验** | `permissionGuard.check(ResourceScope.of(...), "system:dept:edit")` | 2+1 |

### 16.4 资源管控与 PermissionGuard 的边界

PermissionGuard 只管**操作者身份**的通用门槛（"你能不能碰"），以下**资源归属特例**不在本类范围内，在校验通过后由业务层单独执行：

| 特例 | 示例 |
|---|---|
| 平台分发表单/流程 | 当前租户是否在 sys_tenant_form 分发列表里？ |
| 资源编辑权限 | 表单/流程定义是否允许租户编辑？ |
| 审批流节点权限 | 当前用户是否是当前节点审批人？ |
| 流程定义状态 | definition.status == PUBLISHED？ |

---

## 15. 工作流/表单新增菜单清单（2026-10-23）

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

### 数据范围原理

```
@RequiresPermission("system:user:add")  // 按钮级：AOP 拦截
    ↓ 通过后进入 Controller
    ↓
@DataScope(deptAlias="d", userAlias="u")  // 数据级：MyBatis 拦截器
    ↓
自动在 SQL 里加 WHERE dept_id IN (子部门ID列表) OR create_by = 当前用户
```

### 前端配合方案

```typescript
// 方案1：登录接口返回 permissions 数组
const { permissions } = login();
store.setPermissions(permissions);

// 方案2：自定义指令 v-perm
// <el-button v-perm="'system:role:add'">新建</el-button>

// 方案3：直接判断
// <el-button v-if="permissions.includes('system:role:add')">编辑</el-button>
```