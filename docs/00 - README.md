# CyanBase 技术文档索引

> 全局总览 + 跨模块硬性约束。各模块业务规则/隐性约束/坑点已拆到对应子文档。
---
## 全局架构
```
┌─────────────────────────────────────────────────────────┐
│  nginx (38024)                                          │
│  前端 dist try_files + API 精确 regex 反代               │
├─────────────────────────────────────────────────────────┤
│  Spring Boot (8090)                                     │
│  cyanbase-backend（主服务，认证嵌入主服务）               │
├─────────────────────────────────────────────────────────┤
│  MariaDB + Redis                                        │
├─────────────────────────────────────────────────────────┤
│  Vue3 + Element Plus + Pinia + Vite                     │
│  cyanbase-frontend                                      │
└─────────────────────────────────────────────────────────┘
```
### 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot + MyBatis-Plus + JWT + AOP，Java 17 |
| 前端 | Vue3 + Element Plus + Pinia + Vite |
| DB | MariaDB + Redis |
| 反代 | nginx |

### 代码目录

```
cyan-base-master/
├── cyanbase-backend/src/main/java/com/cyan/
│   ├── config/                    ← 全局配置（MybatisPlus/租户拦截器/安全）
│   ├── security/                   ← RBAC + JWT + AOP 切面
│   │   ├── aspect/PermissionGuard.java  ← 三层权限漏斗原子校验统一入口（L1租户→L2 DataScope→L3按钮）
│   │   ├── aspect/PermissionAspect.java ← AOP 兼容入口，内部委托 PermissionGuard
│   │   ├── datascope/ResourceScope.java ← 写操作 targetScope record（targetTenantId / targetDeptId / targetCreateBy）
│   ├── controller.system/          ← 平台侧系统管理 Controller
│   ├── tenant/controller/          ← 租户侧 Controller
│   ├── modules/workflow/           ← 工作流引擎
│   ├── modules/form/               ← 表单设计器
│   └── modules/sydyd/              ← 三重一大业务
└── cyanbase-frontend/src/
    ├── api/                        ← 后端接口封装
    └── views/                      ← 各页面
```

---

## 跨模块硬性约束（违反即 P0 事故）

| # | 约束 | 说明 |
|---|------|------|
| 1 | **add 方法第一行 setId(null)**（或对应主键字段） | 主键冲突防御；主键名不一定是 id（SysPost→postId / SysNotice→noticeId），写前先 grep `@TableId` |
| 2 | **DB 字段名先 SHOW FULL COLUMNS** | sys_menu 是 sort_num / route_path / parent_id，不是 order_num / path / parentId |
| 3 | **import 不猜包结构，grep 已有代码复制** | RequiresPermission 在 com.cyan.security.annotation，没有 .common |
| 4 | **改完权限清 Redis 缓存** | `redis-cli KEYS "rbac:perms:*" \| xargs redis-cli DEL` + rbac:menu:* |
| 5 | **RBAC 验证用 test6 无权限账号** | test6（租户ID=6, userId=7）/ 密码 654123，预期写操作 403、下拉查询 200 |
| 6 | **业务模块下拉接口不加注解** | 前端渲染表单依赖，卡死填报流程（如 /sydyd/system/all） |
| 7 | **前端分页显式 handlePageChange** | 不能写 @current-change="fetchList" 直接调用（v-model 时序 bug） |
| 8 | **纯前端路由不能被 API regex 接走** | workflow 模块 regex 应为 workflow/(approve\|instance\|definition\|node\|copy) 精确匹配，不应该是 workflow 全匹配 |
| 9 | **租户侧 Controller 必须抄全平台侧所有接口** | 不能漏 saveDataScope / saveMenuPerm / getDataScope 等 |
| 10 | **租户侧和平台侧共用一套 perms** | 不要造 tenant:xxx:*，两套 Controller → 一套 sys_menu |
| 11 | **PermissionGuard 三层封装为原子单元** | 不暴露 checkTenantIsolation / checkDataScope / checkButtonPerms 分层方法，业务只能调 check(...) 统一入口，禁止拆分调用 |
| 12 | **写操作必须传 ResourceScope** | check(ResourceScope, ...) 不允许 targetScope=null，写操作显式传 targetTenantId / targetDeptId / targetCreateBy；读操作传 null，L2 主动校验跳过由 SQL 拦截器兜底 |
| 13 | **超管只在 PermissionGuard 入口统一放行** | doCheck() 开头一次性跳过全部三层，三层内部禁止独立写 isSuperAdmin 判断 |
| 14 | **PermissionGuardTest 必须放 src/test/java** | spring-boot-starter-test scope=test 只对 test 路径可见，main 路径编译 JUnit/Mockito 全部找不到 |
| 15 | **Mockito LENIENT + Wrapper 泛型** | 测 IService.list() 用 `any(Wrapper.class)` 或 `ArgumentMatchers.<Wrapper>any()` 消除 list(Wrapper)/list(IPage) 重载歧义；测 scope=ALL 场景 mockRole() 会 stub 多余方法，加 `@MockitoSettings(strictness=LENIENT)` |
| 16 | **通配符双向匹配** | SecurityUtils.hasPermi 只覆盖 userPerm带*匹配requiredPerm具体，PermissionGuard 自己实现正向+反向双向通配（requiredPerm带*也能被匹配） |
---
## 文档索引

| 文档 | 覆盖内容 | 快速跳转场景 |
|---|---|---|
| [01 - 租户底座.md](./01%20-%20租户底座.md) | 多租户拦截器链、租户侧 Controller 规则、租户+平台共用 perms | "TenantLineInnerInterceptor 做什么的？" / "租户侧 Controller 要抄什么？" |
| [02-RBAC 权限.md](./02-RBAC%20权限.md) | menu_type、查询链路、AOP 切面、DB 按钮权限清单、隐形约束、踩坑表 | "写 Controller 怎么加权限注解？" / "perms 命名规范？" |
| [03 - 自研 Workflow 引擎.md](./03%20-%20自研%20Workflow%20引擎.md) | 三张核心表、approve 双重角色、节点流转、trail 接口、轨迹渲染、动画 | "approve 表 action=null 是什么意思？" / "轨迹怎么同时显示已走和待审批？" |
| [04 - 三重一大业务.md](./04%20-%20三重一大业务.md) | 业务模块特殊规则、@DataScope、下拉不加注解 | "三重一大哪些接口不加注解？" |
| [05 - 前端响应式 H5.md](./05%20-%20前端响应式%20H5.md) | 全局隐性约束、逐模块坑点、分页最佳实践 | "手机上 Dialog 怎么自适应？" / "翻页显示一样数据？" |
| [06 - 部署 规范.md](./06%20-%20部署%20规范.md) | nginx 反代、SPA 刷新 404 修复、jar 部署、故障排查 | "浏览器 F5 刷新前端路由 404？" |
|07 - formcreate自定义表单.md
08 - 后面要做的事.md

##使用CURL测试接口样式：
1.获取平台侧admin的TOKEN：
export TOKEN=$(curl -s -X POST http://120.48.47.253:38024/auth/login \
-H "Content-Type: application/json" \
-d '{"userName":"admin","password":"654123"}' \
| python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")

2.获取租户侧管理员test6的TOKEN：
export TOKEN=$(curl -s -X POST http://localhost:8090/tenant-auth/login \
-H "Content-Type: application/json" \
-d '{"tenantId":6,"username":"test6","password":"654123"}' \
| python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")

3.获取租户侧填报人员的TOKEN：
export TOKEN=$(curl -s -X POST http://localhost:8090/tenant-auth/login \
-H "Content-Type: application/json" \
-d '{"tenantId":6,"username":"test7","password":"654123"}' \
| python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")

##日志路径：
1.后端日志：/home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend/logs/cyanbase.log
##数据库访问：
1.在终端输入:
mysql -u petclinic -p cyan_base -e "查询语句？？？"，等待我输入密码即可。
例：mysql -u petclinic -p cyan_base -e "SELECT COUNT(*) FROM wf_process_instance WHERE initiator_id=7;"