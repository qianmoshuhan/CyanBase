# 经营单元（BizUnit）权限体系 · 完整链路文档

> 创建日期：2026-11-13
> 版本：v1.0（B 阶段 + A 阶段 + 部门弹窗复选框全部落地）
> 维护者：后端 + 前端

---

## 一、架构全景

### 1.1 核心概念

| 概念 | 说明 | 数据表 |
|------|------|--------|
| **经营单元（BizUnit）** | 部门层面的标记（`is_biz_unit=1`），表示"这个部门是独立法人子公司/子经营实体" | `sys_dept.is_biz_unit` |
| **经营单元 Scope 绑定** | 用户 ↔ 经营单元 ↔ 角色 的多对多绑定 | `sys_biz_unit_scope` |
| **DataScope 覆盖** | 租户用户切换经营单元后，后端 DataScope 从「用户自己的部门」切换为「经营单元 + 递归子部门」 | 拦截器逻辑，无表 |
| **TenantLine** | MyBatis-Plus 自动追加 `tenant_id` 过滤。`sys_biz_unit_scope` 在 IGNORED_TABLES 黑名单中（无 tenant_id 字段），业务层手动做租户归属校验 | 拦截器配置 |

### 1.2 两套 Scope 体系对照

| | PrincipalTenantScope（平台主体） | BizUnitScope（租户内经营单元） |
|---|---|---|
| 适用人群 | `platformPrincipal=true` 跨租户审计账号 | **非平台主体 + tenantId>0** 的租户用户 |
| 切换效果 | JWT 带 `activeTenantId` → TenantLine 追加 tenant_id 过滤 | JWT 带 `activeBizUnitId` → TenantLine **不变**，`CyanDataPermissionHandler.doBizUnitOverride()` 覆盖 DataScope |
| 角色合并 | sys_user_role ∪ principal_tenant_scope 绑定角色 | sys_user_role ∪ sys_biz_unit_scope 追加角色 |
| 中间表 | `principal_tenant_scope`（有 tenant_id） | `sys_biz_unit_scope`（**无** tenant_id + **无** dept_id，两处 IGNORED_TABLES） |

### 1.3 身份判定（JWT claim，绝不能信 localStorage）
JWT claims 字段： ├── userId 用户 ID ├── tenantId 租户 ID ├── deptId 部门 ID ├── roles 角色 ID 逗号串 ├── permVer 权限版本号（缓存失效用） ├── platformPrincipal 是否平台主体审计账号 ├── activeTenantId (scope 激活时) 当前激活的租户 └── activeBizUnitId (scope 激活时) 当前激活的经营单元

text




身份判定规则（`store/user.js` 的 `readTokenClaim`）：

| 条件 | 身份 |
|------|------|
| `platformPrincipal=true` | 平台主体审计账号 → 禁止调 `/tenant/biz-unit/*` 接口 |
| `tenantId=0` + `roleKey=admin` | 平台 admin 超管 → 可跨租户操作经营单元 |
| `tenantId>0` | 普通租户用户 → 必须同租户操作 |

---

## 二、后端接口全景

### 2.1 基础接口（TenantBizUnitScopeController，前缀 `/tenant/biz-unit`）

| # | 方法 | 路径 | 权限 | 说明 |
|---|------|------|------|------|
| 1 | GET | `/tree` | 无（但 platformPrincipal 403） | 全部门树 + isBizUnit 标记。admin 需传 `tenantId` 参数 |
| 2 | GET | `/my-scopes` | 无 | 当前登录人已绑定的经营单元 + 角色列表（roles 是嵌套数组 `[{roleId, roleName}]`） |
| 3 | POST | `/assign` | `system:user:bizUnitAssign` | 管理端给指定用户绑经营单元角色。body: `{userId, bizUnitId, roleId}`；roleId=null 解绑；不传 userId 默认给自己绑 |
| 4 | PUT | `/switch` | 无（普通用户自用） | 切换自己的经营单元。body: `{bizUnitId, refreshToken}`；bizUnitId=null 清空 |
| 5 | GET | `/get-available-biz-units` | 无（普通用户自用） | 当前登录人可切换的经营单元扁平列表 |
| 6 | **新增** GET | `/user-scopes` | `system:user:bizUnitAssign` | 管理端查询指定用户已绑定的经营单元 + 角色。params: `userId` |
| 7 | **新增** GET | `/biz-unit-users` | `system:user:bizUnitAssign` | 管理端查询指定经营单元下所有已绑定的用户（A 阶段页面用）。params: `bizUnitId, tenantId` |
| 8 | **新增** GET | `/available-roles` | `system:user:bizUnitAssign` | 管理端查询指定租户下可分配的角色列表。params: `tenantId` |

### 2.2 关键接口细节

#### POST /assign（最核心）

```java
// 请求体 { "userId": 14, // 目标用户 ID（可空，空=给自己绑） "bizUnitId": 34, // 经营单元部门 ID "roleId": 31 // 角色 ID（null=解绑） }

// 后端校验链路： // 1. PermissionGuard: hasAuthority('system:user:bizUnitAssign') // 2. platformPrincipal 拦截（平台主体禁止） // 3. 目标用户存在性校验 // 4. 租户归属校验（admin 跳过；租户用户必须同 tenantId） // 5. Service 层：经营单元合法性 + 角色合法性 + 用户状态 // 6. 先删后插事务 // 7. 缓存失效：bump permVer + 精准删除 Redis rbac:perms / rbac:menu:flat

text




#### PUT /switch（必传 refreshToken）

```java
// 请求体 { "bizUnitId": 34, "refreshToken": "eyJhbGciOi..." // ⚠️ 必传！不传会降级 }

// 后端行为： // 1. 轮换双 token：accessToken + refreshToken // 2. 把 activeBizUnitId 写入 Redis refresh ctx（key: auth:refresh:{token}） // 3. 新 JWT claims 追加 activeBizUnitId // 4. 清空时新 JWT 不含 activeBizUnitId 字段（不是 null）

text




#### 权限码 `system:user:bizUnitAssign` 生效条件

| 身份 | 有此权限 | 无此权限 |
|------|---------|---------|
| admin(tenantId=0) | 可给任何人绑 | PermissionGuard 403 |
| 租户管理员 | 可给同租户任何人绑 | PermissionGuard 403 |
| 普通租户用户 | 可以给自己绑（可选场景） | PermissionGuard 403 |

### 2.3 其他后端改动

| Controller | 改动 |
|-----------|------|
| `SysDeptController.update` | 原 `isBizUnit=1` → 新值 `=0` 时，查 `sys_biz_unit_scope` 有无绑定，有则拒绝 |
| `TenantDeptController.update` | 同上（租户侧镜像） |

### 2.4 Redis key 全景

| Key 模式 | 说明 |
|----------|------|
| `auth:refresh:{token}` | refresh token 上下文（含 activeBizUnitId） |
| `auth:user:refresh:{userId}` | 用户 → refresh token 索引 |
| `rbac:perms:{userId}:{tenantId}:{bizUnitKey}` | 权限缓存（经营单元态追加 bizUnitKey 维度） |
| `rbac:menu:flat:{userId}:{tenantId}:{bizUnitKey}` | 菜单缓存 |
| `permVer:{userId}` | 权限版本号（递增后前端下次 getUserInfo 自动从 DB 重算） |

---

## 三、前端接口全景

### 3.1 api/bizUnitScope.js

```javascript
// ===== 基础接口（普通用户自用）===== export function getBizUnitTree(params = {}) { return request({ url: '/tenant/biz-unit/tree', method: 'get', params }) }

export function getMyBizUnitScopes() { return request({ url: '/tenant/biz-unit/my-scopes', method: 'get' }) }

export function switchBizUnit(data) { return request({ url: '/tenant/biz-unit/switch', method: 'put', data }) }

export function getAvailableBizUnits() { return request({ url: '/tenant/biz-unit/get-available-biz-units', method: 'get' }) }

// ===== 管理端接口（需 system:user:bizUnitAssign 权限）===== /** 管理端：给指定用户分配/解除经营单元角色绑定 */ export function assignBizUnitScope(data) { return request({ url: '/tenant/biz-unit/assign', method: 'post', data }) }

/** 管理端：查指定用户已绑定的经营单元 + 角色 */ export function getUserBizUnitScopes(userId) { return request({ url: '/tenant/biz-unit/user-scopes', method: 'get', params: { userId } }) }

/** 管理端：查指定经营单元下所有已绑定用户 */ export function listBizUnitScopeUsers(bizUnitId) { return request({ url: '/tenant/biz-unit/biz-unit-users', method: 'get', params: { bizUnitId } }) }

/** 管理端：查指定租户下可分配的角色列表 */ export function listAvailableRoles(tenantId) { return request({ url: '/tenant/biz-unit/available-roles', method: 'get', params: { tenantId } }) }

text




### 3.2 涉及页面

| 页面 | 路径 | 改动 |
|------|------|------|
| 用户管理 | `views/system/user/index.vue` | 操作列加「经营单元」按钮 + el-drawer 抽屉 |
| 部门管理 | `views/system/dept/index.vue` | 弹窗加复选框 + 表格加经营单元 Tag 列 |
| 经营单元授权管理（A 阶段） | `views/system/bizUnitScope/index.vue` | 新建页面：左栏经营单元树 + 右栏绑定用户表 + 批量绑定 Dialog |

### 3.3 store/user.js 关键方法

```javascript
// 切换经营单元：立即覆盖双 localStorage token → 清 userInfo/roles/permissions → reset tagsView // 调用方后续必须 fetchUserInfo() 重拉 async switchBizUnitAndUpdateToken(bizUnitId) { ... }

// 权限判断 hasPerm('system:user:bizUnitAssign')

text




### 3.4 权限码前端显隐方式

```vue
<el-button v-haspermi="['system:user:bizUnitAssign']">经营单元</el-button>

text




---

## 四、隐性约束 & 坑点

### 🔴 必遵守则（踩过的坑）

#### 1. ThreadLocal 对称性
```java
// CustomTenantHandler.skip() / DataScopeContext.skip() 必须 try/finally reset() // 禁止嵌套！reset 顺序固定：先 reset DataScope 再 reset TenantHandler

try { CustomTenantHandler.skip(); DataScopeContext.skip(); // ...业务代码... } finally { DataScopeContext.reset(); // 先 DataScope CustomTenantHandler.reset(); // 再 TenantHandler }

text




#### 2. MyBatis-Plus 拦截器顺序
TenantLine → DataPermission → Pagination 反了分页 total 不准！

text




#### 3. SysUserController.list 的 DataScopeContext.skip()
```java
// fillRoleNames / fillDeptName / fillPostName 用 DataScopeContext.skip() 包住 // 否则经营单元态下 sys_role / sys_dept 被裁 → roleNames 空串

text




#### 4. 经营单元态下 sys_dept 会被裁剪
只剩该单元 + 子部门，**部门树只剩部分节点是设计效果，不是 bug**。

#### 5. Redis 权限缓存 key 含经营单元维度
rbac:perms:{userId}:{tenantId}:{bizUnitKey} rbac:menu:flat:{userId}:{tenantId}:{bizUnitKey}

text



分配变更后必须 bump permVer + 清 Redis，否则用户下次切换还读旧权限。

#### 6. switch 的 refreshToken 必传
后端轮换双 token，activeBizUnitId 写进 refresh ctx。**不传会降级，自动续期后 claim 丢失、DataScope 覆盖静默失效**。

#### 7. platformPrincipal 403
平台主体审计账号禁止调 `/tenant/biz-unit/*` 所有接口，这是硬拦截。

### 🟡 注意事项

| # | 项 | 说明 |
|---|------|------|
| 1 | nginx 接口路径 | `/tenant/biz-unit/(.*)` 正则已覆盖，新增接口不用改 nginx |
| 2 | 菜单 component 路径 | 新建菜单时 component 字段写 `system/bizUnitScope/index`（不带 `.vue` 后缀） |
| 3 | 菜单 route_path | 和 component 目录对应，前端 router 会按 `/system/bizUnitScope` 匹配 |
| 4 | 平台 admin 调 /tree 必须传 tenantId | admin 的 tenantId=0，TenantLine 不拦截，但后端业务逻辑需要指定租户才能查部门树 |
| 5 | 前端 userStore 正确 getter | 用 `userStore.getTenantId`（getter），**不是** `userStore.tenantId`（undefined） |
| 6 | LoginUser 无 tenantName 字段 | 后端 getUserInfo 不回填 tenantName，前端需从 `/system/tenant/all` 接口或 sys_tenant 表取 |

### 🟢 上线部署 checklist

```bash
后端改了必须重启（拦截器顺序是 Bean 结构，热部署不生效）
cd ~/Cyan/CyanBase/cyanbase-backend mvn -B -DskipTests clean package

然后重启后端服务
前端 build + cp（别踩旧 index.html 引用不存在的 JS → nginx try_files 回退 → 浏览器 MIME type 转圈）
cd ~/Cyan/CyanBase/cyanbase-frontend npm run build sudo cp -a dist/. /usr/share/nginx/html/cyan/ # ⚠️ dist/. 末尾有个点！

Redis 清缓存（权限表/菜单表改了必须清）
redis-cli KEYS "rbac:menu:flat:" | xargs redis-cli DEL redis-cli KEYS "rbac:perms:" | xargs redis-cli DEL

浏览器硬刷新
Ctrl+Shift+R

text




---

## 五、权限码完整列表

| 权限码 | 出现位置 | 说明 |
|--------|---------|------|
| `system:user:bizUnitAssign` | 用户管理页按钮 + A 阶段页面 | 给他人绑经营单元角色的核心权限 |
| `system:dept:add` | 部门管理新增按钮 | |
| `system:dept:edit` | 部门管理编辑按钮 | |
| `system:dept:delete` | 部门管理删除按钮 | |

---

## 六、测试基线账号

| userId | tenantId | 姓名 | 经营单元绑定 | 切换后权限变化 |
|--------|----------|------|-------------|---------------|
| 14 | 3 | 张素芳 | 经营单元 34=华安化工 + 子部门 35、36 + 角色 31 | permissions 53 → 72 |
| admin | 0 | 平台 admin | 无（admin 自己不绑经营单元） | 跨租户操作 |

---

## 七、后续迭代方向

| 阶段 | 功能 | 状态 | 备注 |
|------|------|------|------|
| **B + A + 本期** | 全部落地 | ✅ 完成 | |
| **下一阶段可选** | 经营单元配置 Tab（部门管理 Tab2） | 🔜 待做 | 当前只做了弹窗复选框，独立 Tab 可做更丰富的配置（如启用/禁用经营单元、查看绑定统计等） |
| **可选增强** | 经营单元切换历史/审计日志 | 🔜 待做 | 记录谁在什么时候给谁绑了什么经营单元角色，方便审计 |
| **可选增强** | 批量解绑入口 | 🔜 待做 | 当前 A 阶段页面有单条解绑，可加批量解绑按钮 |
| **可选增强** | 经营单元有效期 | 🔜 待做 | `sys_biz_unit_scope` 加 `expire_time` 字段，到期自动失效 |
| **可选增强** | admin 跨租户批量搜用户 | 🔜 待做 | 当前 A 阶段页面用 `/tenant/user/list`，TenantLine 会按当前登录用户 tenantId 过滤。admin 选了 tenantId=3 但搜出来的还是 tenantId=0 的用户，需要后端支持 admin skip TenantLine 后按外部传入 tenantId 过滤 |

---

## 八、核心数据表结构参考

### sys_dept（is_biz_unit 字段）
```sql
ALTER TABLE sys_dept ADD COLUMN is_biz_unit TINYINT DEFAULT 0 COMMENT '是否独立法人经营单元 0-否 1-是';

text




### sys_biz_unit_scope（中间表，无 tenant_id，IGNORED_TABLES）
```sql
CREATE TABLE sys_biz_unit_scope ( id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, biz_unit_id BIGINT NOT NULL, role_id BIGINT, create_by VARCHAR(50), create_time DATETIME DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_user_bizunit (user_id, biz_unit_id) ) COMMENT='用户经营单元Scope绑定表';

text




### sys_menu（权限码插入）
```sql
-- 插入按钮权限（挂在用户管理 parent_id=29 下，menu_type=3） INSERT INTO sys_menu (parent_id, menu_name, menu_type, route_path, component, perms, icon, sort_num, visible, status, remark, create_by, create_time) VALUES (29, '经营单元绑定', 3, '', '', 'system:user:bizUnitAssign', '#', 99, 0, 0, '给租户用户分配/解除经营单元角色绑定', 'admin', NOW());

-- 插 A 阶段菜单（挂在系统管理 parent_id=1 下，menu_type=2） INSERT INTO sys_menu (parent_id, menu_name, menu_type, route_path, component, perms, icon, sort_num, visible, status, remark, create_by, create_time) VALUES (1, '经营单元授权', 2, 'bizUnitScope', 'system/bizUnitScope/index', 'system:bizUnitScope:list', 'OfficeBuilding', 55, 0, 0, '独立经营单元授权管理页', 'admin', NOW());

text




---

## 九、Nginx 配置（不变，仅参考）

```nginx
接口代理（已覆盖所有经营单元接口）
location ~ ^/tenant/(user|role|dept|biz-unit)/(.*)$ { proxy_pass http://127.0.0.1:8090; }

2 层裸路径
location ~ ^/tenant/(user|role|dept|biz-unit)$ { limit_except GET HEAD OPTIONS { proxy_pass http://127.0.0.1:8090; } return 418; }

text




---

**文档终。如有疑问请翻对应文件的 diff 记录或 grep 日志。**