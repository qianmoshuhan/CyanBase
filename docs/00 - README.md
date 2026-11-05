# CyanBase 技术文档索引（AI 友好型项目知识库 · 总纲）

> 全局总览 + 跨模块硬性约束 + 隐性坑点 + 全局边界定义。
> **阅读规则：所有开发任务、文档校验、文档更新，必须优先读取本文件。**
> 本文件定义的全局规则 > 各模块文档的模块专属规则，模块规则不能违背全局规则，仅用于补充模块独有逻辑。
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
│   ├── CyanBaseApplication.java    ← Spring Boot 启动类
│   │
│   ├── config/                      ← 全局配置层
│   │   ├── CustomTenantHandler.java ← ⭐ MyBatis-Plus 租户处理器（skip/reset 控制 SQL 拦截）
│   │   ├── MybatisPlusConfig.java   ← MP 基础配置
│   │   ├── TenantMybatisPlusConfig.java ← ⭐ 租户专用 MP 配置（注册三层拦截器链 + @Primary）
│   │   └── WebConfig.java           ← Web MVC 拦截器注册
│   │
│   ├── common/                      ← ⭐ 通用工具层（所有模块共用）
│   │   ├── enums/                   ← 枚举：DataScopeEnum/ResultCodeEnum
│   │   ├── exception/               ← 异常体系：BaseException/AuthException/BusinessException/ParamValidException
│   │   ├── handler/                 ← GlobalExceptionHandler（全局异常兜底）
│   │   ├── log/                     ← 操作日志注解 @Log + RequestLogInterceptor
│   │   ├── message/                 ← 消息推送 MessageUtils
│   │   ├── mybatisplus/config/      ← ⭐ MP 自动填充：MyMetaObjectHandler（createBy/updateBy）/ TenantMetaObjectHandler（tenantId 自动注入）
│   │   ├── spel/                    ← ⭐ SpEL 工具：MapPropertyAccessor（让 SpEL 能直接访问 Map key）
│   │   ├── util/                    ← TreeUtil/TreeBuildUtil（树形构造）/ XssFilter（XSS 白名单）
│   │   └── R.java / ResultCode.java ← 统一响应封装
│   │
│   ├── security/                    ← ⭐ 权限守卫层（三层漏斗原子校验）
│   │   ├── aspect/PermissionGuard.java   ← L1 租户硬隔离 → L2 DataScope → L3 按钮权限（禁止拆分调用）
│   │   ├── aspect/PermissionAspect.java  ← AOP 兼容入口，委托 PermissionGuard
│   │   ├── datascope/ResourceScope.java   ← 写操作 targetScope（targetTenantId/targetDeptId/targetCreateBy）
│   │   ├── datascope/DataScopeContext.java ← ⭐ ThreadLocal skip/reset（DataPermissionInterceptor 跳过开关）
│   │   └── util/SecurityUtils.java        ← 当前用户工具：getLoginUser/hasPermi
│   │
│   ├── controller/                  ← Controller 层（按租户/平台分目录）
│   │   ├── system/                  ← ⭐ 平台侧：SysUserController/SysRoleController/SysPostController/SysDeptController/SysMenuController/SysTenantController/SysDictType+DataController/SysConfigController/SysNoticeController/SysFileController/SysFormController/GenTableController/SysApiMonitorController/SysUserOnlineController/WfDefinitionController
│   │   ├── AuthController.java      ← 平台侧登录
│   │   ├── SysLogLogin/Operation/Controller.java ← 登录日志/操作日志
│   │   ├── PasswordController.java  ← 独立密码管理
│   │   └── SysDeptController.java   ← 部门管理
│   │
│   ├── tenant/controller/           ← ⭐ 租户侧 Controller（平台侧 system 的租户镜像，接口一比一抄）
│   │   ├── TenantUserController.java  ← 租户用户管理
│   │   ├── TenantRoleController.java  ← 租户角色管理
│   │   └── TenantDeptController.java  ← 租户部门管理
│   ├── tenant/aspect/               ← TenantAuthAspect 租户身份兜底切面
│   │
│   ├── tenantauth/                  ← ⭐ 租户独立认证模块（租户侧登录/Token 签发）
│   │   ├── controller/TenantAuthController.java
│   │   ├── service/TenantAuthService.java
│   │   └── dto/TenantLoginReq.java / TenantItemVO.java
│   │
│   ├── entity/                      ← ⭐ 全局实体层（BaseEntity + 所有 @TableName 业务实体）
│   │   ├── BaseEntity.java          ← createBy/updateBy/createTime/updateTime 公共字段
│   │   ├── SysUser / SysRole / SysPost / SysDept / SysMenu / SysTenant
│   │   ├── SysDictType / SysDictData / SysConfig / SysNotice / SysFile
│   │   ├── SysRoleMenu / SysRoleDept / SysRoleMenu / SysTenantMenu ← 关联表
│   │   ├── SysLogLogin / SysLogOperation / SysLogininfor / SysUserOnline
│   │   ├── GenTable / SysApiMonitor / SysForm / SysThirdConfig / SysStorageConfig
│   │   └── WfDefinition.java         ← 旧版工作流定义实体（已迁移到 modules/workflow）
│   │
│   ├── mapper/                      ← ⭐ 全局 Mapper 层（和 entity 一一对应）
│   │   └── SysUserMapper / SysRoleMapper / ...（所有 Sys* + GenTable + WfDefinition）
│   │
│   ├── dto/SysPostDTO.java          ← 请求/响应 DTO
│   │
│   └── modules/                     ← ⭐ 业务模块层（各自独立 controller/entity/mapper/service 四层）
│       ├── workflow/                ← 🔑 自研工作流引擎（最复杂模块）
│       │   ├── controller/          ← 6 个：Definition/Node/Instance/Approve/Binding/Copy
│       │   ├── entity/              ← 8 张核心表：Definition/Node/Instance/Approve/Runtime/Copy/Binding/DefinitionTenant
│       │   ├── mapper/              ← 对应 8 个 Mapper
│       │   ├── service/impl/        ← 10 个 ServiceImpl：Definition/Node/Instance/Approve/Binding/Copy/Runtime 等
│       │   ├── util/BpmnXmlGenerator.java ← BPMN 2.0 XML 生成器
│       │   └── job/WorkflowTimeoutJob.java ← 审批超时定时任务
│       │
│       ├── form/                    ← 自研表单设计器
│       │   ├── controller/          ← FormDefinitionController/FormFieldController/FormDataController
│       │   ├── entity/              ← FormDefinition/FormField/FormData/FormDataItem
│       │   └── service/impl/        ← 对应 ServiceImpl
│       │
│       ├── sydyd/                   ← 三重一大业务
│       │   ├── entity/              ← 7 张表：System/Topic/Meeting/MeetingTopic/Item/List/Execute
│       │   └── mapper/ service/impl/
│       │
│       ├── message/                 ← 消息通知
│       │   ├── controller/SysMessageController.java
│       │   ├── entity/SysMessage / SysMessageUser
│       │   └── service/impl/ + MessagePushService
│       │
│       ├── job/                     ← 定时任务
│       │   ├── controller/SysJobController.java
│       │   ├── entity/SysJob / SysJobLog
│       │   ├── mapper/ + service/impl/
│       │   └── task/SysJobTask.java  ← 实际执行器
│       │
│       └── log/                     ← 操作日志（服务层）
│           └── service/impl/SysOperLogServiceImpl.java
│
└── cyanbase-frontend/src/
    ├── main.js / App.vue             ← Vue 入口
    ├── router/index.js               ← Vue Router
    ├── store/user.js / tagsView.js   ← Pinia 状态
    ├── plugins/formCreate.js         ← form-create v3 表单设计器插件
    ├── directives/permission.js      ← v-perm 自定义指令（按钮权限）
    ├── utils/                        ← request.js（axios）/ storage.js（localStorage）
    ├── assets/styles/index.css       ← 全局样式
    ├── components/TagsView/          ← 标签页组件
    ├── layout/index.vue              ← PC 端整体布局
    │
    ├── api/                          ← ⭐ 后端接口封装（和 controller 层一一对应）
    │   ├── auth.js / tenantAuth.js   ← 平台+租户两套登录
    │   ├── user.js / role.js / post.js / dept.js / menu.js  ← 系统管理
    │   ├── tenant.js / dictType.js / dictData.js / config.js / notice.js
    │   ├── file.js / form.js / gen.js / storage.js / thirdConfig.js
    │   ├── wfDefinition.js / binding.js / sydyd.js  ← 业务模块
    │   ├── job.js / jobLog.js / loginLog.js / operLog.js / online.js / apiMonitor.js  ← 监控
    │   ├── message.js / common.js
    │
    └── views/                        ← ⭐ 页面层（按业务模块分文件夹）
        ├── login/index.vue           ← 平台侧登录页
        ├── tenantLogin/index.vue     ← 租户侧登录页
        ├── home/index.vue            ← 平台首页
        ├── profile/index.vue         ← 个人中心
        ├── redirect/index.vue        ← 登录重定向中转
        ├── mobile/home.vue           ← 移动端门户首页
        │
        ├── system/                   ← 平台系统管理（13 个子页）
        │   ├── user/ role/ post/ dept/ menu/ tenant/          ← 用户/角色/岗位/部门/菜单/租户
        │   ├── dict/type/ dict/data/                          ← 字典类型/字典数据
        │   ├── config/ notice/ file/ thirdConfig/ sysForm/    ← 参数/公告/文件/第三方/系统表单
        │   └── gen/                                           ← 代码生成器
        │
        ├── workflow/                 ← 🔑 工作流（7 个子页）
        │   ├── designer/index.vue    ← ⭐ 流程设计器（含审批节点配置/条件构造器/BPMN 预览）
        │   ├── binding/index.vue     ← 表单-流程绑定（管理员路由配置）
        │   ├── start/index.vue       ← 发起流程
        │   ├── todo/index.vue        ← 我的待办
        │   ├── approve/index.vue     ← 审批（含轨迹/Timeline）
        │   ├── done/index.vue        ← 已办
        │   └── myForm/index.vue      ← 我的表单（操作员填报入口）
        │
        ├── form/                     ← 自研表单设计器
        │   ├── designer/index.vue    ← 表单设计（form-create v3 可视化拖拽）
        │   ├── dataList/index.vue    ← 表单数据列表
        │   └── submit/index.vue      ← 表单填报
        │
        ├── sydyd/                    ← 三重一大业务（6 个子页）
        │   ├── system/ topic/ meeting/ item/ list/ execute/
        │
        ├── monitor/                  ← 监控（8 个子页）
        │   ├── apiMonitor/ server/ online/ job/ jobLog/ log/ loginLog/ operLog/
        │
        ├── message/myMessage/        ← 消息中心
        └── security/ rateLimit/ xss/  ← 安全工具
```

---

## 核心定义：三层执行门槛 vs 三层拦截器链（边界清晰化）

> 解决 B1 边界模糊：PermissionGuard 三层（主动校验）和 MP 三层拦截器链（SQL 拦截）是**双层并行、互不替代**的两个独立机制。

| 层 | PermissionGuard 主动校验（Controller 入口） | MP 拦截器链（SQL 层） |
|---|---|---|
| L1 / TenantLine | 租户硬隔离（登录态→身份→资源归属） | SQL 自动追加 `WHERE tenant_id = ?` |
| L2 / DataScope | 数据范围（SELF/DEPT/CUSTOM 主动校验目标归属） | SQL 追加 `WHERE dept_id IN (...) OR create_by = ?` |
| L3 / — | 按钮级 RBAC（双向通配符） | — |
| **谁是主** | 写操作的唯一主动校验入口，补 SELF 范围改别人记录的漏洞 | 读操作的 SQL 兜底，写操作入参漏洞它管不住 |
| **调用方** | Controller 手动调 `permissionGuard.check(...)` | Service 层 MP 自动触发，无需手动调用 |
| **双层并行** | Controller 通过 → Service 层再走拦截器链 → 数据库，两层独立生效 |

## 核心定义：平台 admin（tenantId=0）跨租户行为（B2 边界）

| 场景 | 行为 | 依据 |
|---|---|---|
| admin 调**租户侧** Controller（/tenant/role 等） | PermissionGuard 入口统一跳过 L1/L2/L3，可跨租户读写 | doCheck() 开头 isSuperAdmin 一次性放通 |
| admin 调**平台侧** Controller（/system/tenant 等） | 同上，全局跳过 | 同上 |
| **普通租户**调**平台侧** Controller（/system/message/sendAll 等） | L1 租户身份通过（不是租户用户才被拦），L3 按钮权限可能 403（如 system:message:send） | 00 约束 #17 |
| **普通租户**跨租户写资源 | TenantLine SQL 直接过滤查不到 → 返回"记录不存在"，**不会进入 PermissionGuard L1** | 00 约束 #18 |

## 核心定义：流程版本快照读写权限（B3 边界）

| 规则 | 说明 |
|---|---|
| 版本快照 flow_json 本身**不可被编辑** | 创建后是只读历史快照，不允许原地修改 |
| 可执行的操作：新建版本、删除版本 | 新建 = 从主表或已有版本复制生成新 wf_def_version 记录；删除 = 物理删整条 |
| "保存版本快照"实际含义 | 创建版本时将主表节点完整序列化写入 flow_json，是创建操作而非编辑操作 |
| 已发起的流程实例 | 锁定指向创建时的 definition_id + 当时的节点状态，不受后续主表编辑或新建版本影响 |

---

## 跨模块硬性约束（违反即 P0 事故）

> 以下全部为全局强制规则，模块文档只做引用：`👉 参考：00-README.md [约束 #N]`

| #  | 约束 | 说明 | 性质 |
|----|------|------|------|
| 1  | **add 方法第一行 setId(null)**（或对应主键字段） | 主键冲突防御；主键名不一定是 id（SysPost→postId / SysNotice→noticeId），写前先 grep `@TableId` |
| 2  | **DB 字段名先 SHOW FULL COLUMNS** | sys_menu 是 sort_num / route_path / parent_id，不是 order_num / path / parentId |
| 3  | **import 不猜包结构，grep 已有代码复制** | RequiresPermission 在 com.cyan.security.annotation，没有 .common |
| 4  | **改完权限清 Redis 缓存** | `redis-cli KEYS "rbac:perms:*" \| xargs redis-cli DEL` + rbac:menu:* |
| 5  | **RBAC 验证用 test6 无权限账号** | test6（租户ID=6, userId=7）/ 密码 654123，预期写操作 403、下拉查询 200 |
| 6  | **业务模块下拉接口不加注解** | 前端渲染表单依赖，卡死填报流程（如 /sydyd/system/all） |
| 7  | **前端分页显式 handlePageChange** | 不能写 @current-change="fetchList" 直接调用（v-model 时序 bug） |
| 8  | **纯前端路由不能被 API regex 接走** | workflow 模块 regex 应为 workflow/(approve\|instance\|definition\|node\|copy) 精确匹配，不应该是 workflow 全匹配 |
| 9  | **租户侧 Controller 必须抄全平台侧所有接口** | 不能漏 saveDataScope / saveMenuPerm / getDataScope 等 |
| 10 | **租户侧和平台侧共用一套 perms** | 不要造 tenant:xxx:*，两套 Controller → 一套 sys_menu |
| 11 | **PermissionGuard 三层封装为原子单元** | 不暴露 checkTenantIsolation / checkDataScope / checkButtonPerms 分层方法，业务只能调 check(...) 统一入口，禁止拆分调用 |
| 12 | **写操作必须传 ResourceScope** | check(ResourceScope, ...) 不允许 targetScope=null，写操作显式传 targetTenantId / targetDeptId / targetCreateBy；读操作传 null，L2 主动校验跳过由 SQL 拦截器兜底 |
| 13 | **超管只在 PermissionGuard 入口统一放行** | doCheck() 开头一次性跳过全部三层，三层内部禁止独立写 isSuperAdmin 判断 |
| 14 | **PermissionGuardTest 必须放 src/test/java** | spring-boot-starter-test scope=test 只对 test 路径可见，main 路径编译 JUnit/Mockito 全部找不到 |
| 15 | **Mockito LENIENT + Wrapper 泛型** | 测 IService.list() 用 `any(Wrapper.class)` 或 `ArgumentMatchers.<Wrapper>any()` 消除 list(Wrapper)/list(IPage) 重载歧义；测 scope=ALL 场景 mockRole() 会 stub 多余方法，加 `@MockitoSettings(strictness=LENIENT)` |
| 16 | **通配符双向匹配** | SecurityUtils.hasPermi 只覆盖 userPerm带*匹配requiredPerm具体，PermissionGuard 自己实现正向+反向双向通配（requiredPerm带*也能被匹配） |
| 17 | **平台`/system/*`接口按钮权限对租户 token 同样生效** | 普通租户直接调用平台 Controller 接口，会被 L3 按钮权限 403 拦截；示例：test6 调用`POST /system/tenant`返回 403 需要`system:tenant:add`；调用`DELETE /system/dict/type`返回 403 需要`system:dict:remove`；印证两套 Controller 共用同一套 sys_menu perms 规则 |
| 18 | **普通租户跨租户写资源表现为「记录不存在」而非 403** | 普通租户跨租户操作，优先被 TenantLine SQL 层过滤查不到数据，直接返回业务提示角色 / 记录不存在，**不会进入 PermissionGuard L1 校验**；安全上实现资源隐身，但调试排错容易混淆：分不清是 ID 错误还是跨租户被拦截 |
| 19 | **`GET /tenant-auth/tenant/list` 为匿名不鉴权接口** | 登录页面下拉加载租户列表使用，不走 JWT 鉴权，全局放行，不要增加 @RequiresPermission 注解 |
| 20 | **前端字段 prop 必须与后端实体字段名严格一致** | 后端 SysMessage 返回 `id/title/senderName`，前端不能写 `msgId/msgTitle/createBy`，否则 el-table 渲染空白；下划线→驼峰是 MP 自动映射，不是前端随意缩写 |
| 21 | **MP 拦截器自动填充 tenantId 优先于业务逻辑 set** | INSERT 时 TenantMetaObjectHandler 自动补 tenant_id，业务代码手动 setTenantId() 会被覆盖；新增业务表不要手动 setTenantId，让拦截器管 |
| 22 | **数据库 LONGTEXT 字段前端用富文本时必须考虑 XSS** | 公告/表单等存 HTML 的字段，后端必须加 XssFilter 白名单过滤，不能只靠前端 v-html 渲染；Jsoup SafeList + img 协议过滤 + style expression 正则兜底 |
| 23 | **TenantLineInnerInterceptor 对所有 SQL 类型一视同仁追加 tenant_id** | 不分 @Select/@Insert/@Update/@Delete，**自定义 SQL（含 @Delete 物理删除、@Select 带 @Param 的自定义查询）全部被自动追加 WHERE tenant_id=?**；业务代码里只要出现跨租户/全局唯一标识场景，必须主动 `CustomTenantHandler.skip()` |
| 24 | **前端列表 sortOrder/排序号必须在保存时按数组位置重新编号** | 新增/删除/移动节点后，前端数组顺序才是用户看到的真实顺序，节点上残留的 sortOrder 可能跳号/乱序；**保存时一律用 `nodes.value.map((n,i) => ({...n, sortOrder: i+1}))` 重新编号，后端也必须兜底按遍历顺序再编一次**，双重保险不信任任何旧值 |
| 25 | **全局唯一外键关联的子表操作必须 skip 租户拦截** | 如果表的主外键（如 wf_process_node.definition_id）本身就是全局唯一标识，子表通过外键完全确定归属，**不需要也不应该再叠加 tenant_id 做二次隔离**；此时任何对子表的物理删除/全量查询/批量保存都必须用 `CustomTenantHandler.skip()` 绕开，否则租户侧登录会删不动/查不全 |
| 26 | **flow_json 快照内 nodes[].id 与 nextNodeIds 必须使用同一套编号体系** | 版本快照完整序列化时，主表自增 dbId → nodeKey → 虚拟 Long ID 三套标识不能混用；快照是完全自洽的数据，不依赖主表任何 ID，两套编号会导致引擎流转时找不到节点 |
| 27 | **Long/大整数请求参数必须走 URL 路径参数或 @RequestParam，不能放 @RequestBody** | axios 发 POST 时 data body 会被 JSON 序列化，后端 Spring 尝试把 `[object Object]` 转 Long 直接 NumberFormatException；**版本ID/节点ID 等 Long 一律用 `@PathVariable Long versionId`** |
| 28 | **序列化实体做快照必须过滤审计/租户字段** | WorkflowNode 实体含 LocalDateTime createTime/updateTime + String createBy/updateBy + Long tenantId，Jackson 默认用 Java 8 时间反序列化直接炸；**快照序列化时只取节点业务字段（nodeKey/nodeType/nodeName/conditionExpr/nextNodeIds/...），审计字段一律排除** |
| 29 | **axios 接口返回值必须从 R 结构取内层 .data** | 后端统一响应 `R<T>` 封装，前端 `res.data` 才是业务数据；新建版本接口返回 `R<Long>`，必须 `const newVersionId = res.data`，直接把 R 对象当 URL 参数会变成 `[object Object]` | [R]
| 30 | **版本快照数据只读——任何写入操作都直接打到主表** | 版本快照存的是 flow_json JSON 大字段，不是可编辑的实体列表；快照加载时转成前端只读副本，前端不能对快照对象做增删改，必须调主表接口；只读状态要在前端显式 disabled 所有编辑按钮 | [R]
| 31 | **CustomTenantHandler.skip() 必须 try-finally reset()** | skip 让当前线程所有 SQL 绕开租户拦截，异常时忘了 reset → 后续所有请求都 skip → **全租户数据泄露 P0 安全事故**；标准模板：`CustomTenantHandler.skip(); try { ... } finally { CustomTenantHandler.reset(); }` | [R] |
| 32 | **rbac:perms 缓存 Key 标准格式** | `rbac:perms:{tenantId}:{userId}` (tenantId 在前，userId 在后)；冒烟测试里写 `rbac:perms:7` 是临时简写，**生产代码和 Redis 操作必须用完整格式** | [R] |
| 33 | CustomTenantHandler.ignoreTable() admin tenantId=0 自动返回 true，所有查询天然跨租户，不需要 skip() | admin 登录（tenantId=0）的 ignoreTable 第 211 行直接 return true，TenantLine 完全不拦截。平台侧 SysUserController.list() / add() / update() 跨租户操作（手机号唯一性校验 / 列表查询）不需要额外 skip | 
| 34 | CustomTenantHandler 用 ThreadLocal 单变量存储 skipFlag，多层 skip/reset 是破坏性的 | 外层 skip() flag=true；内层 skip() 幂等仍 true；但内层 reset() 直接置 false → 把外层也清了。fillDeptName / fillPostName 都有自己的 skip/reset，如果外层再包一层，内层 reset 会清掉外层 | 
| 35 | @TableLogic 字段不要再手写 eq 条件 | SysUser.delFlag 有 @TableLogic 注解，MP 自动在所有 SELECT 追加 WHERE del_flag = 0，手动再写 .eq(SysUser::getDelFlag, 0) 会生成 WHERE del_flag=0 AND del_flag=0 重复条件（冗余不报错） |
| 36 | ✅ 权限上下文规范：调用 `CustomTenantHandler.skip()` / `DataScopeContext.skip()` 时，强制 `try-finally` 包裹并在 finally 执行 `reset()`，防止同请求权限开关状态污染；登录身份上下文由 JwtAuthenticationFilter 统一 finally `SecurityContextHolder.clearContext()` 兜底清理，防范线程池复用串户。
| 37 | **租户菜单池反向污染禁止**：`saveMenuPerm` 绝对不能反向把 role_menu 同步进 tenant_menu，租户菜单池是平台硬上限，只能由平台侧 `PUT /system/tenant/{tid}/menus` 维护；角色菜单写入前必须先按 tenant_menu 池校验截断再写入 DB（顺序关键，先写后截 = 越权数据落库）。 |
| 38 | **JWT 必须放 tenantId**：`AuthController.login` 登录时必须 `jwtUtil.putTenantId(claims, sysUser.getTenantId())`，否则后续 `SecurityUtils.getTenantId()` 全返回 null → 所有租户过滤跳过。 |
| 39 | **角色菜单回显必须过滤脏数据**：`getMenuIds` 接口返回时必须按 tenant_menu 池做交集过滤，历史越权数据不能回显到前端弹窗。 |
| 40 | **PermissionGuard.checkDataScope 必须 DataScopeContext.skip() 整体包裹** | 方法体内部 roleService.listByIds / deptService.list / roleDeptService.list 三个 DB 查询，移除 sys_role 的 IGNORED 后会被 Handler 二次过滤导致 scope 解析残缺。必须在方法入口 skip + try-finally reset（`checkDataScope` 仅做上下文管理，纯业务逻辑下沉到 `doCheckDataScope`）。 |
| 41 | **返回 jsqlparser Expression 时 OrExpression 必须用 Parenthesis 整体包裹** | AND 优先级 > OR，不加括号会生成 `WHERE tenant_id=N AND (scope条件) OR dept_id IS NULL` → **所有全局角色被放出来** → TooManyResultsException。统一写法：`new Parenthesis(new OrExpression(scopeCond, isNullCond))`。 |
---

## 今日修复摘要（2026-11-05 · RBAC+DataScope+sys_role 部门私有角色）

### 根因总览

```
sys_role 从 IGNORED_TABLES 移除（实现部门私有角色）
    ↓
PermissionGuard.checkDataScope 内部 roleService.listByIds(roleIds)
    ↓ 被 CyanDataPermissionHandler 加 dept_id IN (...) 过滤
当前用户只能看到本部门角色 → scope 解析残缺
    ↓ L2 主动校验
误判越权 / 漏判越权
```

### 三层修复链路

| 层 | 文件 | 问题 | 修复 |
|---|------|------|------|
| ① DB | sys_role | 无 dept_id 列 → 无法实现部门私有角色 | `ALTER TABLE sys_role ADD dept_id BIGINT NULL DEFAULT NULL COMMENT '部门私有角色归属（NULL=全局）'` |
| ② Handler | CyanDataPermissionHandler | sys_role 不在 IGNORED 后，条件未区分全局 vs 私有；IS NULL 表达式写法不兼容 jsqlparser 版本 | DEPT_COLUMN_MAP 保持默认 dept_id；OrExpression(`scopeCond`, `parseExpression("dept_id IS NULL")`) 用 **new Parenthesis() 整体包裹** |
| ③ PermissionGuard | PermissionGuard.checkDataScope | 内部 3 个 DB 查询被 Handler 二次过滤 | `DataScopeContext.skip() + try { doCheckDataScope() } finally { reset() }` — 方法入口做上下文管理，业务逻辑下沉到 private `doCheckDataScope()` |
| ④ Controller | SysRoleController.add / TenantRoleController.add | 新增角色不会自动填部门归属 | `if (sysRole.getDeptId() == null) sysRole.setDeptId(SecurityUtils.getLoginUser().getSysUser().getDeptId())` → 超管登录(deptId=null) 自动创建全局角色；部门管理员登录自动填当前部门创建部门私有角色 |

### DB 变更（已执行）
```sql
ALTER TABLE sys_role 
  ADD COLUMN dept_id BIGINT NULL DEFAULT NULL 
    COMMENT '部门私有角色归属部门（NULL=全局角色，非NULL=部门私有角色）' 
    AFTER tenant_id,
  ADD INDEX idx_sys_role_dept (dept_id);
```

### 验证清单
| # | 步骤 | 预期 |
|---|------|------|
| ① | `mvn clean compile` | ✅ 编译通过 |
| ② | `redis-cli KEYS "rbac:*"  xargs redis-cli DEL` | 清掉旧 scope/perms 缓存 |
| ③ | 用 test6（tenantId=6, userId=7, 654123）**重新登录** | JWT 重新签发才带 deptId |
| ④ | 平台超管创建新角色，不手动填 deptId | 新角色 dept_id=NULL（全局） |
| ⑤ | 部门管理员(deptId=25)创建新角色，不手动填 deptId | 新角色 dept_id=25（部门私有） |
| ⑥ | test6 写操作触发 PermissionGuard L2 | 不再出现 scope 残缺导致的误判 |

---

## 今日修复摘要（2026-11-04 · 租户菜单池越权漏洞）

### 根因
| # | 文件 | 问题 |
|---|------|------|
| P0-1 | `SysRoleController.saveMenuPerm` Step 3 | ❌ 反向同步：把 role_menu 菜单自动 INSERT 进 sys_tenant_menu → 撑爆租户边界 |
| P0-2 | `SysRoleController.saveMenuPerm` 执行顺序 | ❌ 先写 DB 后截断：Step 1 删旧 → Step 2 写新 → Step 3 截断（越权数据已落库） |
| P0-3 | `AuthController.login` | ❌ JWT 没放 tenantId → SecurityUtils.getTenantId() 全 null → 过滤全跳过 |
| P0-4 | `PermissionServiceImpl` perms 组装 | ❌ 没按 tenant_menu 池过滤 → @RequiresPermission 兜底失效 |
| P0-5 | `getMenuIds` 回显 | ❌ 历史脏数据直接回显 |

### 四层防御体系（修复后）
```
① 源头层   availableMenus 接口 → 前端下拉租户后菜单树自动裁剪
② 写入层   saveMenuPerm 先校验截断再写 DB → 越权进不了库
③ 桥梁层   JWT 带 tenantId → 链路能取租户边界
④ 兜底层   SysMenuController.filterByTenant + PermissionServiceImpl.filterByTenantMenuPool
           → 菜单展示 + perms 组装双重过滤，历史脏数据也无法生效
```

### 新增接口
| 接口 | 用途 |
|------|------|
| `GET /system/role/availableMenus?tenantId=N` | 平台侧角色编辑：按租户加载可分配菜单池 |
| （已存在）`GET /tenant/role/availableMenus` | 租户侧角色编辑：自动加载本租户菜单池 |

### 前端配套
| 文件 | 改动 |
|------|------|
| `src/api/role.js` | `getMenuList(tenantId)` 改为按 tenantId 调 availableMenus |
| `src/views/system/role/index.vue` | 所属租户必选 + `@change` 时刷新菜单树 + 清空旧勾选 |


## 今日新增：全局隐性坑点表（2026-10-26 补录）

| #  | 模块 | 坑 | 根因 | 教训 |
|----|------|----|------|------|
| 1  | 后端 Jackson | SysMenu implements TreeUtil 反序列化 500 | Lombok @Data 生成 `List<SysMenu> getChildren()` 与手写 `@Override List<? extends TreeUtil> getChildren()` 同参不同返回类型，JVM 有两个方法，Jackson 扫描到 @JsonIgnore 的 override 方法直接跳过整个字段 | **interface 必须泛型化**：`TreeUtil<T>`，实体类 `implements TreeUtil<SysMenu>`，Lombok 自动生成的 getter 正好匹配接口签名，不需要手写 override |
| 2  | 后端 Jackson | @JsonIgnore 加错位置丢 JSON | 把 @JsonIgnore 加在了 Lombok 生成的 getter 上而非手写 override 上 | **只在手写 override 的 interface 方法上加 @JsonIgnore**，实体字段本身保持纯净，让 Lombok 生成的 getter 正常序列化 |
| 3  | 前端 Element Plus | 树形表格「全部展开」没反应 | expand-row-keys 对深层嵌套的树形表格只做顶层 key 匹配，不保证子级自动展开；且 Vue 数组引用不变时 el-table treeStore 不重算 | **用 tableRef.toggleRowExpansion(row, true/false) 官方 API 递归遍历**：展开从外到内（父先开让子级 DOM 出现），收起从内到外（子先收再收父）；必须 nextTick 后执行 |
| 4  | 前端 Element Plus | router_name 与后端 perms 不同步 | apiMonitor→接口监控，menuManagement→菜单管理 改了中文名但没同步改 router_name | **router_name 必须与后端 perms 保持命名风格一致**，否则 Element Plus 菜单高亮匹配不上路由 |
| 5  | 前端 Element Plus | 搜索项 el-option value 类型不匹配 | 后端 messageType 要 Integer，前端 el-option value 写字符串 '1' → 后端转 int 报错 | **el-option value 必须与后端期望参数类型严格一致**，用 `:value="1"` 数字绑定而非 `value="1"` 字符串 |
| 6  | 前端 HTTP | navigator.clipboard undefined | `http://IP:端口` 非 HTTPS 环境下 Clipboard API 不可用 | **复制加 fallback**：`navigator.clipboard?.writeText(text)  document.execCommand('copy')` |
| 7  | 后端 | mvn compile 跳过遗留错误 | WorkflowInstanceServiceImpl 有 WorkflowNode 没 getId() 等遗留编译错误 | `mvn compile -Dmaven.compiler.failOnError=false` 跳过；⚠️ 只临时跳过，遗留错误必须在对应模块修复
| 8  | 后端 | 重复 import 没清理 | SysRoleController 有两行一样的 `import com.cyan.entity.SysTenantMenu` | diff 合并时人工检查 import 区，重复的删掉
| 9  | DB | sys_menu route_path 重复导致 Element Plus 菜单高亮冲突 | 数据库里两条 API 监控菜单 route_path 完全一样 → 前端渲染两个菜单项，点击后都选中 | **DELETE 前先 SELECT 查 route_path 唯一性**，同级 parent_id 下 route_path 必须唯一 |
| 10 | 后端 | Jsoup XSS 过滤 SafeList 不够严 | SafeList.relaxed() 允许 style 属性里有 expression( 等 CSS 注入 | **正则兜底**：过滤 style 里的 expression/url(javascript:) 等，以及 `javascript:`/`vbscript:` URL 协议 |
| 11 | 后端 XssFilter | HTML 标签大小写不敏感 | `<SCRIPT>alert(1)</SCRIPT>` SafeList 会放过 | **Jsoup 默认转小写**，但自定义正则过滤必须加 `Pattern.CASE_INSENSITIVE` |
| 12 | 前端 WangEditor | @wangeditor/editor-for-vue 版本必须 @next | 不带 @next 安装的是 Vue2 版本 | **Vue3 项目必须装 @wangeditor/editor-for-vue@next** |
| 13 | 后端 TenantMetaObjectHandler | listByIds + in 批量查绕过租户拦截 | listByIds 默认走 Wrapper 但 in 条件传入 ID 集合时 MP 拦截器可能不追加 tenant_id | **禁止用 listByIds + in 批量跨租户查**，必须加 `.eq(实体::getTenantId, 当前租户ID)` 显式过滤 |
| 14 | DB | sys_post 没有 dept_id 关联岗位和部门 | 业务需求变更时先 SHOW FULL COLUMNS 确认现有字段 | **表结构必须先查再改**，不要假设某个字段存在；新需求要关联新实体 = 先 ALTER TABLE 加外键 |
| 15 | **通用** | BPMN XML 生成必须做双重安全过滤 | 后端 BpmnXmlGenerator.escapeXml 只做了 `&<>"'` 实体转义，漏掉了 `\x00-\x1F\x7F` 不可见控制字符；用户从 Excel/网页粘贴文本经常带入 | **后端 escapeXml 必须包含两层**：①XML 1.0 非法控制字符过滤（只保留 \t\n\r）；②标准实体转义（&<>"'）；部署前用 Python ElementTree 验证 `ET.parse()` 能过才交给前端 bpmn-js |
| 16 | **通用** | jar 部署进程未真正重启导致新旧 class 混用 | 服务器上 `mvn clean package` 后用 `pkill -f cyanbase-backend` 杀进程，但旧进程可能没杀掉（nohup / systemd 托管），新 jar 启动后实际还在跑旧代码 | **改了 Java 代码的部署流程必须三步确认**：①`ps aux | grep java | grep -v grep` 看 PID 确实不存在；②新 jar 启动日志确认版本号；③curl 接口验证返回内容包含新代码特征字符串（比如加个 `logger.info("v2")`） |
| 17 | **前端通用** | 全屏弹层方案优先级：CSS 模拟 > teleported + z-index > requestFullscreen | 三种方案都试过：requestFullscreen 有原生层叠隔离导致 popper 挂 body 被盖住；teleported=false 会被表格 overflow 裁剪；getPopupContainer + :key 强制重绘逻辑复杂易错 | **任何需要弹层（el-select/el-cascader/el-date-picker）的全屏场景，一律用 CSS 模拟方案**：`position:fixed + inset:0 + z-index:100`，所有弹层继续挂 body（默认 teleported=true），全局 `.el-popper { z-index:100000 !important }` 兜底，ESC 退出用 keydown 监听 |
| 18 | **前端 Element Plus** | 多源 diff 改动同一区域导致文件结构损坏 | 连续 Accept 多个 diff + 手动 sed 盲插，可能把 CSS 插到 JS 里、把属性拆成两行、或形成重复代码 | **每轮改动结束后必须验证**：前端 `npm run build` 必须过；后端 `mvn clean compile` 必须过；XML/JSON 生成类接口用 Python 标准库先 parse 验证；diff 方案确定后再统一 Accept，不要边试边改 |
| 19 | **前后端联调** | el-tree-select / el-cascader 内部用 `===` 严格匹配 | :props.value 绑定的是后端返回的原始类型（如 deptTree.id 是 Number 16），但正则反解析 SpEL 表达式捕获组永远是 String "16" → `"16" !== 16` → 找不到对应项 → 显示裸数字 | **正则/JSON 反序列化出来的数字字符串必须显式 `Number(x)` 转成 Number 类型**，或后端存值时统一用 String 类型（保持两端一致即可） |
| 20 | **自研工作流 flow_json 铁律** | nodes[].id 和 nodes[].nextNodeIds 必须同一套编号 | 历史快照 id=null + nextNodeIds="870"(dbId)，虚拟ID注入后变成 id=501553215(虚拟) + nextNodeIds="870" → 两套数字对不上 → BPMN 无连线 / 前端下拉全崩 | **任何时候 flow_json 里 id 和 nextNodeIds 必须同一套**：要么全主表 dbId，要么全虚拟ID，要么全 nodeKey 字符串 —— 绝不能混 |
| 21 | **Vue3 computed 必须显式 import** | 写了 `computed(() => ...)` 但没加 import | ReferenceError: computed is not defined → 页面空白 | `import { ref, reactive, computed, onMounted } from 'vue'`，用到什么必须加什么 |
| 22 | **API 层参数类型必须看清楚** | createVersionFrom(sourceId, {versionName:"x"}) 多包了一层对象 | API 层内部 `data: { versionName }` 会把传入对象再包一层 → 后端 body.get("versionName") 拿到内层对象 → toString() → "{versionName=x}" | 调用 API 函数前先看 js 文件里的签名，第二个参数直接传期望类型（字符串就传字符串，别传对象） |
| 23 | 后端 CustomTenantHandler | 误以为平台侧跨租户查询需要 skip() → 加了 skip 但 try-finally 没正确闭合 → 编译 21 条连锁错误 | 没读源码就加 skip。ignoreTable 第 211 行 tenantId==0 直接 return true，admin 天然跳过拦截 | 改代码前 grep 源码确认现有逻辑，不要想当然 | [P0] |
| 24 | 后端 CustomTenantHandler 嵌套 skip/reset | fillDeptName 内部 skip + reset → 把外层 skip 也清了 → 后续 SQL 被 TenantLine 拦截 | ThreadLocal 单变量，reset 不管层级直接置 false | skip 范围最小化，不要嵌套 |
| 25 | 后端 TenantLine 匿名接口不拦截 | 租户登录接口（匿名）TenantLine 自动放行，手机号跨租户查询不需要 skip | resolveTenantId() 返回 null 时 skip | 匿名接口 = skip；admin 登录 = ignoreTable return true；普通租户 = 正常拦截 | [P1] | 
| 26 | 前端 Element Plus 图标名瞎写 | prefix-icon="I-phone" → 图标不显示，控制台无报错 | Element Plus 图标库没有 I-phone，正确的是 Cellphone | 写图标名前先 grep 项目已有代码用了哪些 | [P2] |

## 文档索引（按阅读顺序，高关联已标注）

| 文档 | 一句话摘要 | 高关联 | 核心场景 |
|---|---|---|---|
| [01 - 租户底座.md](./01%20-%20租户底座.md) | 多租户拦截器链 + 租户侧 Controller 镜像规则 + skip/reset 边界决策树 | 02(RBAC)、03(Workflow 依赖 skip 读节点)、08(冒烟测试) | 租户侧 Controller 要抄什么？什么时候必须 skip？ |
| [02 - RBAC 权限.md](./02%20-%20RBAC%20权限.md) | PermissionGuard 三层原子校验 + DB 按钮权限清单 + 新增菜单三层漏斗 Checklist | 01(租户隔离)、04(DataScope 下沉业务)、03(工作流按钮权限 workflow:approve) | 写 Controller 怎么加权限注解？新增菜单 Checklist？ |
| [03 - 自研 Workflow 引擎.md](./03%20-%20自研%20Workflow%20引擎.md) | 三张核心表 + approve 双重角色 + SpEL 网关 + 节点批量保存 + 版本快照 | 01(节点必须 skip)、02(workflow:approve 按钮权限)、07(form-create 表单)、08冒烟) | approve action=null 是什么意思？BPMN 预览报错？节点保存后顺序乱？ |
| [04 - 三重一大业务.md](./04%20-%20三重一大业务.md) | 业务模块特有规则：tenant侧CRUD、@DataScope、workflow businessType 关联 | 03(businessType=threeBigItem 发起流程)、02(下拉不加注解) | 三重一大哪些接口不加注解？ |
| [05 - 前端响应式 H5.md](./05%20-%20前端响应式%20H5.md) | 前端分页最佳实践 + Vue3 坑点 + Workflow 版本功能前端实现 | 03(Workflow 版本前端)、00(全局前端约束 #7/#20) | 翻页显示一样数据？手机上 Dialog 怎么自适应？ |
| [06 - 部署 规范.md](./06%20-%20部署%20规范.md) | nginx 反代精确 regex + SPA try_files + Spring Boot jar 部署 + 故障排查 | 00(全局架构图) | 浏览器 F5 刷新前端路由 404？jar 改了没生效？ |
| [07 - formcreate自定义表单.md](./07%20-%20formcreate自定义表单.md) | fc-designer 组件类型怪癖 + form-create v3 API + form_field 字段元数据同步 | 03(工作流 SpEL 依赖 form_field.fieldKey) | fc-designer 存 input 但 SpEL 比较炸？form-create v2→v3 怎么迁？ |
| [08 - 冒烟测试工作.md](./08%20-%20冒烟测试工作.md) | 冒烟测试用例手册 + 权限专项 Apifox 自动化方向 + 已完成节点 | 01/02/03(全模块回归) | test6 写操作 403 是预期还是 Bug？如何清理脏缓存？ |
| [09 - 后面要做的事情.md](./09%20-%20后面要做的事情.md) | TODO 清单：P0 立即完成 / P1 近期需求 / P2 优化改进 / P3 代码清理 | 03(bpmn render 改造)、全模块 | 后面还要做什么？验收 Checklist？ |

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

---

> **备注**：平台 admin (tenantId=0) 作为超管，会在 PermissionGuard 入口统一跳过全部 L1/L2/L3 三层校验，允许跨租户直接修改各租户内业务资源，这是当前代码现实行为；后续如果需要平台 admin 增加租户操作隔离，需要在 doCheck() 入口调整超管放行逻辑。