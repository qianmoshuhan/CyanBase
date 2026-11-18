一、BPMN 流程设计器 — 开发复盘与技术沉淀
markdown



# BPMN 可视化流程设计器 · 开发复盘文档

> 阶段：第一阶段 · 只做编辑器（不接运行引擎）
> 状态：✅ 核心链路已跑通（2026-11-17 修复完成，详见 §8 完整复盘）

---

## 1. 项目架构总览

### 1.1 隔离原则（第一优先级，绝对不能破坏）

现有两套流程定义**完全平行、解耦**：

| 维度 | 自研配置式（老） | BPMN 可视化（新） |
|------|----------------|-----------------|
| 前端路由 | `/system/wf/definition` + `/workflow/designer` | `/workflow/bpmn/definition` + `/workflow/bpmn/designer` |
| 后端路径 | `/workflow/definition` | `/workflow/bpmn/definition` |
| 数据库表 | `wf_process_definition` | `wf_bpmn_definition` |
| 菜单 sys_menu | route_path=`wfDefinition`, parent_id=65 | route_path=`bpmn/definition`, parent_id=65 |
| 后端包结构 | `com.cyan.modules.workflow` | `com.cyan.modules.workflow.bpmn`（子包，零 import 老代码） |
| 数据格式 | 节点 JSON 配置 | BPMN 2.0 XML + 自定义 extensionElements |

**约束：本期不做两套数据互转、不引入 Flowable/Activiti、不改现有 workflow 模块任何代码。**

### 1.2 技术栈

- 前端：Vue 3 + Element Plus + Vite 5.4.21 + bpmn-js 18.16.1
- 后端：Spring Boot + MyBatis-Plus + Java 17 + MariaDB/MySQL
- 组织架构接口：复用 `/system/org/tree`、`/system/post/all`、`/system/user/search`

### 1.3 文件清单

**后端（5 个新文件，全部新建在 `com.cyan.modules.workflow.bpmn` 包下）：**
cyanbase-backend/src/main/java/com/cyan/modules/workflow/bpmn/ ├── entity/WfBpmnDefinition.java ← @TableName("wf_bpmn_definition") ├── mapper/WfBpmnDefinitionMapper.java ← extends BaseMapper，@Mapper ├── service/WfBpmnDefinitionService.java ├── service/impl/WfBpmnDefinitionServiceImpl.java └── controller/WfBpmnDefinitionController.java ← @RequestMapping("/workflow/bpmn/definition")

text




**前端（4 个新文件 + 2 处修改）：**
cyanbase-frontend/src/ ├── api/bpmnDefinition.js ← 新建 ├── api/user.js ← 修改：追加 searchUsersApi（/user/search 路由镜像） ├── api/post.js ← 已有，getAllPost() 直接用 ├── api/org.js ← 已有，getOrgTree() 直接用 ├── router/index.js ← 修改：追加两条路由 └── views/workflow/bpmn/ ├── list/index.vue ← 新建：列表页 └── designer/ ├── index.vue ← 新建：可视化设计器（核心） ├── cyan-moddle.json ← ❌ 已废弃，不要创建 └── i18n/translate.js ← 新建：内联汉化函数

text




**数据库：**

- 建表 SQL：见后端 Entity 对应 DDL
- sys_menu 注册：parent_id=65（工作流管理），menu_type=2，route_path=`bpmn/definition`
- sys_tenant_menu：批量授权给所有 tenant

### 1.4 sys_menu 路由拼接机制（关键隐性约束）

sys_menu.route_path **不是** Vue Router 的 path，而是由 Layout 组件的 `getPath` 函数拼接：

```javascript
// Layout 里的路由拼接逻辑（已废弃但记录在案） const routeMap = { 'wfDefinition': '/system/wf/definition', // ... }

// 现有菜单"工作流管理"(id=65) → parentPath = 'workflow' // sys_menu.route_path = 'bpmn/definition' // → 拼接结果 = '/workflow/bpmn/definition' ✅ 正好等于 Vue Router path

text




所以：sys_menu.route_path 填 `bpmn/definition`，Layout 自动拼成 `/workflow/bpmn/definition`，**和 Vue Router 硬编码的 path 一致**。

---

## 2. 数据库表结构

```sql
CREATE TABLE wf_bpmn_definition ( id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL COMMENT 'TenantMetaObjectHandler 自动填充', process_name VARCHAR(200) NOT NULL, process_key VARCHAR(100) NOT NULL, description VARCHAR(500) COMMENT '流程描述', category VARCHAR(100) COMMENT '流程分类', version INT NOT NULL DEFAULT 1, business_type VARCHAR(50) COMMENT '预留', status TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布', bpmn_xml LONGTEXT NOT NULL COMMENT 'BPMN 2.0 XML（含 extensionElements）', bpmn_viewport TEXT COMMENT '画布视口 JSON（可选）', del_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) COMMENT '⚠️ BaseEntity 有此字段，必须建', create_by VARCHAR(50), create_time DATETIME, update_by VARCHAR(50), update_time DATETIME, PRIMARY KEY (id), UNIQUE KEY uk_process_key_version (process_key, version), KEY idx_tenant (tenant_id) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

text




**⚠️ 关键：必须有 `remark` 字段！** 因为 `BaseEntity` 里有 `remark` 属性，MP 会自动 SELECT，如果漏建会报 `Unknown column 'remark' in 'SELECT'`。

---

## 3. 踩坑日志（按时间顺序）

### Pitfall 1：Entity tenantId 没有 fill 注解 → INSERT 炸

**现象：** Field 'tenant_id' doesn't have a default value  
**根因：** `TenantMetaObjectHandler` 的 `strictInsertFill` 内部有检查——如果目标字段**没有** `@TableField(fill = FieldFill.INSERT)`，**静默跳过填充**，不报错、不警告。  
**修复：** Entity 的 tenantId 字段加上：
```java
@TableField(value = "tenant_id", fill = FieldFill.INSERT) private Long tenantId;

text




**额外知识点：** 还有一条兜底链路 `TenantLineInnerInterceptor`（SQL 拦截器层），当 tenantId > 0 时自动改写 SQL 追加 tenant_id 值。但 tenantId=0（超级管理员）时拦截器让位给实体层，这时没 fill 注解就炸了。

### Pitfall 2：BaseEntity 有 remark 字段 → 建表漏列

**现象：** Unknown column 'remark' in 'SELECT'  
**根因：** `BaseEntity` 继承来的属性，MP 会自动 SELECT。建表时忘了加 `remark` 列。  
**修复：** ALTER TABLE ADD remark VARCHAR(500)，或 Entity 加 `@TableField(exist = false)` 排除。

### Pitfall 3：@MapperScan 不覆盖新子包 → Mapper 找不到

**现象：** No qualifying bean of type `WfBpmnDefinitionMapper`  
**根因：** `@MapperScan` 扫 `com.cyan` 下的所有包，但新增的 `bpmn` 子包路径太深时可能被某些过滤规则跳过？实际原因是启动类的 scanBasePackages 没覆盖。  
**修复：** 确认 `@MapperScan("com.cyan")` 能覆盖到 `com.cyan.modules.workflow.bpmn.mapper`。

### Pitfall 4：bpmn-js v17+ 不支持 keyboard.bindTo

**现象：** Keyboard binding is now implicit  
**根因：** bpmn-js v17 底层 diagram-js 做了改动，键盘绑定改成隐式自动绑定到 document。显式传 `keyboard: { bindTo: ... }` 就警告并丢弃。  
**修复：** Modeler 构造参数里删掉 `keyboard: { bindTo: document }`。

### Pitfall 5：空 BPMN 模板 XML 闭合标签 typo

**现象：** Error: unparsable content `</di:BPMNEdge>` detected / closing tag mismatch  
**根因：** 手敲模板 XML 时，两个 `<bpmndi:BPMNEdge>` 的闭合标签写成了 `</di:BPMNEdge>`。BPMN XML 对命名空间前缀严格敏感——`<bpmndi:BPMNEdge>` 和 `</di:BPMNEdge>` 被认为是完全不同的标签。  
**修复：** 统一用 `<bpmndi:BPMNEdge>...</bpmndi:BPMNEdge>`，根元素显式声明 `xmlns:di`。

### Pitfall 6：bpmn-js-i18n-zh 包 ESM 默认导出失败

**现象：** "default" is not exported by `bpmn-js-i18n-zh`  
**根因：** 该包是 CJS 导出，Rollup/Vite 的 ESM 默认导入解析失败。  
**修复：** **换方案——内联 translate 函数**。bpmn-js 的 translate 机制本质上就是 `(key, interpolations) => string` 函数，自己维护一个 CN_MAP 字典完全 OK，还更可控。文件位置：`views/workflow/bpmn/designer/i18n/translate.js`。

### Pitfall 7：Vue 3 Proxy 和 bpmn-js 原生对象属性冲突

**现象：** `TypeError: 'get' on proxy: property 'labels' is a read-only and non-configurable data property on the proxy target but the proxy did not return its actual value`  
**根因：** Vue 3 的 Proxy 对 bpmn-js 原生 element 对象做**深度代理**，但 bpmn-js 的元素对象有自定义 getter（`.labels` 返回 Array）、部分属性 `configurable: false`、内部有 `Array.isArray(el.labels)` 类型检查。Proxy 拦截后返回值和原始 descriptor 声明不一致。  
**修复：** 存 bpmn-js 原生对象的 ref 必须：
```javascript
const selectedElement = shallowRef(null) // shallowRef：.value 不深代理 // 赋值时 selectedElement.value = markRaw(el) // markRaw：显式标记跳过代理

text




### Pitfall 8（严重，连锁 3 炸）：cyan:* 自定义扩展到底怎么创建？

> 2026-11-17 修复，挖了 bpmn-moddle 源码才搞清楚。经历了 3 次方案迭代才找到正确答案。

**现象链：**
```
第 1 次：moddle.create('cyan:assignee') → unknown type "cyan:assignee"（没注册 descriptor）
    ↓
第 2 次：注册 cyan-moddle.json descriptor → 更严重：unknown type "bpmn:extensionElements"
         （descriptor 格式写错 + 注册机制破坏了内置类型查找）
    ↓
第 3 次：手动 JSON 构建 { $type: 'cyan:assignee', ... }
         → Cannot read properties of undefined (reading 'isGeneric')
         （writer 序列化时要读 $descriptor，手动 JSON 没有 $descriptor）
    ↓
第 4 次：moddle.createAny('cyan:assignee', CYAN_NS_URI, props) → ✅ 终于对了！
```

**根因（bpmn-moddle 源码级约束）：**

| API | 适用范围 | 要求 |
|-----|---------|------|
| `moddle.create('xxx')` | **已注册 descriptor 的类型** | 内置 BPMN 类型 + 你手动注册的扩展类型 |
| `moddle.createAny('prefix:name', nsURI, props)` | **未注册的未知扩展类型** | 自动构建 `{ isGeneric: true }` 的 `$descriptor`，writer 走 generic 序列化路径 |
| 手动 JSON `{ $type, ... }` | ❌ 不行 | 没有 `$descriptor`，writer 崩在 `.isGeneric` |

**修复：** 统一用 `moddle.createAny()` 创建 cyan:* 扩展：
```javascript
const CYAN_NS_URI = 'http://cyan.org/bpmn-ext'
function makeCyanAny(moddle, localName, props) {
  // 空值过滤：undefined/null/空字符串 不写入
  const clean = {}
  for (const [k, v] of Object.entries(props)) {
    if (k === '$children') clean[k] = v
    else if (k === '$body' && v) clean[k] = String(v)
    else if (v !== undefined && v !== null && v !== '') clean[k] = String(v)
  }
  return moddle.createAny('cyan:' + localName, CYAN_NS_URI, clean)
}

// 内置 BPMN 类型（extensionElements / FormalExpression）还是用 moddle.create()
moddle.create('bpmn:ExtensionElements')       // ✅ 已注册
moddle.create('bpmn:FormalExpression', { $body: expr })  // ✅ 已注册
```

**关键：** `cyan-moddle.json` descriptor 文件**完全不需要**，删掉它！

### Pitfall 9：TenantUserController 缺 /search 路由 → 租户登录时用户搜索炸

**现象：** `/tenant/user/search` 返回 `code:10005, "参数类型错误: id 需要 Long 类型"`  
**根因：** 两套用户接口（SysUserController /tenant/user 和 TenantUserController /tenant/user）。`SysUserController` 有 `/search` 但 `TenantUserController` 没有。租户登录时前端路由前缀自动切 `/tenant`，请求 `/tenant/user/search` 被 TenantUserController 的 `@GetMapping("/{id}")` 贪婪匹配吞了，`"search"` 字符串转 Long 失败。  
**修复：** 给 TenantUserController 也加一套 `/search` 路由镜像，和 SysUserController 的 searchUsers 保持一致。TenantLine 拦截器自动隔离 tenantId，wrapper 里不显式加。

### Pitfall 10：用户搜索下拉 label 写错

**现象：** 下拉选项显示 `undefined()`  
**根因：** 后端 `/system/user/search` 返回精简结构 `{id, label: "昵称(用户名)"}`，是专门给工作流下拉用的。前端却按完整用户对象解构 `u.userName` + `u.nickName`。  
**修复：** el-option 直接用后端返回的 `u.label`。

### Pitfall 11（严重）：el-tree-select 的 node-key / props.value 字段名写错

**现象：** 选了部门不回显 + 展开时所有节点被误判高亮为选中态  
**根因：** 后端 `/system/org/tree` 返回的 `OrgNodeVO` 里**根本没有 `id` 字段**！实际字段：
```
nodeId:  "platform-root" / "tenant-1" / "dept-6"    ← 唯一标识
nodeType: "platform-root" / "tenant" / "dept"
deptId:  6                                          ← 只有 dept 节点有
label:   "人力资源部"
```
但模板写的是 `node-key="id"` + `props.value="id"` → el-tree-select 找不到 key → 无法匹配选中态。  
**修复：**
```html
<el-tree-select
  node-key="nodeId"                              ← 唯一标识
  :props="{ value: 'deptId', disabled: 'nodeDisabled', ... }"  ← deptId 才是部门 ID
/>
```
加载 deptTree 后还要递归给非 dept 节点（platform-root / tenant）加 `nodeDisabled: true`。

### Pitfall 12：岗位 / 用户 el-select 缺 @change → item.name 永远是空字符串

**现象：** 选中岗位或用户后，保存成功，但 XML 里 `name=""`，下次打开 el-select 显示原始 ID 而不是名字  
**根因：** 部门有 `@change="(v) => { item.name = findDeptName(v) }"`，但岗位和用户的 el-select **都没有 @change 来写 item.name**！  
→ 保存到 XML 时 `name: a.name || ''` → name 为空
→ 读回来注入 userOptions 条件 `a.value && a.name` → name 为空 → 跳过注入
→ userOptions 无该选项 → el-select 匹配不到 → 显示原始 value "15" 而不是 "苏振邦"  
**修复：** 给岗位和用户都补 @change：
```html
<!-- 岗位 -->
@change="(v) => { const p = postList.find(x => String(x.id) === String(v)); item.name = p ? p.postName : '' }"
<!-- 用户 -->
@change="(v) => { const u = userOptions.find(x => String(x.id) === String(v)); item.name = u ? u.label : '' }"
```

### Pitfall 13：remote el-select 回显需要 options 里有对应项

**现象：** 打开已保存流程，用户类型审批人显示 ID 而不是名字（即使 name 在 XML 里有值）  
**根因：** Element Plus 的 remote el-select **只有在 options 数组里能找到 value 对应的 option 才会显示 label**。打开流程时 userOptions 是空的（还没搜索过），所以匹配不到。  
**修复：** 在 `readElementProps` 里把已保存的 user 选项注入 userOptions（unshift 放最前）。注入条件**不能依赖 a.name**（兼容老脏数据 name 为空的情况）。

### Pitfall 14（严重）：FormalExpression 用了 $body → 被序列化成 attribute

**现象：** 保存后再次打开，conditionExpression 回显为空；保存后的 XML 里是 `$body="amount > 500"`（attribute）而不是标签内容。  
**根因：** `$body` 是 **generic 扩展元素**（createAny）的手写约定。但 `FormalExpression` 是 **BPMN 标准类型**（moddle 已注册 descriptor），它有正式属性 `body`（descriptor 里定义了 `isBody: true`）。用 `$body` 时 writer 把它当普通 attribute 输出 → XML 里 `$body="..."`。  
**修复：**
```javascript
// ❌ 错误（$body 是 createAny 约定）
moddle.create('bpmn:FormalExpression', { $body: 'amount > 500' })
// ✅ 正确（body 是 bpmn 标准属性）
moddle.create('bpmn:FormalExpression', { body: 'amount > 500', language: 'spEL' })
```
**关键区分表：**
| 创建方式 | 内容属性 | XML 输出 |
|---------|---------|----------|
| `moddle.createAny('cyan:xxx', NS, { $body: 'OR' })` | `$body`（generic 约定） | `<cyan:xxx>OR</cyan:xxx>` ✅ |
| `moddle.create('bpmn:FormalExpression', { body: expr })` | `body`（标准属性） | `<bpmn:conditionExpression>expr</...>` ✅ |
| 反过来交叉用 | ❌ 错 | 被当 attribute 输出 |

### Pitfall 15：modeler.getRootElement() 不存在 + 层级拿错

**现象：** `TypeError: d.getRootElement is not a function` 或 persistFormRefToDefs 静默失败。  
**根因：** bpmn-js Modeler **没有** `getRootElement()` 这个 API！正确的调用链是：
```javascript
// 图形层根元素（Process 图形 Element）
const processEl = modeler.get('canvas').getRootElement()
// 业务对象层
const processBO = processEl.businessObject         // bpmn:Process
const definitions = processBO.$parent              // bpmn:Definitions ← 这才是根！
```
**之前的错误：**
| 错误写法 | 拿到什么 | 后果 |
|---------|---------|------|
| `modeler.getRootElement()` | ❌ 根本不存在 | 直接 TypeError 崩 |
| `canvas.getRootElement().businessObject` | bpmn:Process | formRef 写到了 process 上，还写错属性名 |
| 属性名叫 `rootExtensionElements` | Process 上没这属性 | 被序列化成 `rootExtensionElements="[object Object]"` |
**正确写法：**
```javascript
const processEl = modeler.get('canvas').getRootElement()
const definitions = processEl.businessObject.$parent
modeling.updateModdleProperties(processEl, definitions, {
  extensionElements: extEls  // bpmn 标准属性名
})
```

### Pitfall 16（严重）：readElementProps 早退逻辑挡了 conditionExpression

**现象：** 保存了 SequenceFlow 的 conditionExpression，保存成功，但再次打开流程右侧面板条件全空；保存后的 XML 里 conditionExpression 存在。  
**根因：** `readElementProps` 里有 `if (!bo.extensionElements) return` 早退逻辑。但 `conditionExpression` 是 `bpmn:SequenceFlow` 的**标准直接属性**（类似 `sourceRef`、`targetRef`），存在 `bo.conditionExpression` 上，和 `extensionElements`（扩展节点容器）**毫无关系**！

```
旧代码执行路径：
  bo.name → elementProps.name ✅
  清 conditionExpr = ''
  if (!bo.extensionElements) return  ← 💥 没填 cyan:desc 时 extensionElements 不存在！
  ... SequenceFlow conditionExpression 读取 💀 永远不执行
```

**修复：** SequenceFlow 的 conditionExpression 读取逻辑挪到 `extensionElements` 早退**之前**：
```javascript
function readElementProps(el) {
  const bo = el.businessObject
  elementProps.name = bo.name || ''
  // 先清
  elementProps.conditionExpr = ''
  // ⭐ SequenceFlow 标准属性：在 extensionElements 早退之前处理！
  if (el.type === 'bpmn:SequenceFlow') {
    const cond = bo.conditionExpression
    if (cond && cond.body) {
      elementProps.conditionExpr = cond.body
      parseConditionToCols(cond.body)
    }
  }
  if (!bo.extensionElements) return  // 现在早退不影响 conditionExpression 了
  // ... 下面处理 extensionElements 的 cyan:* 扩展
}
```

### Pitfall 17：Nginx 正则 location 把 SPA 前端路由当 API 代理

**现象：** 生产环境硬刷新 `/workflow/bpmn/designer?id=16&action=edit` 返回 404。本地 Vite dev server 通过。  
**根因：** Nginx 正则 `location ~ ^/workflow/(bpmn|instance|definition|approve|node|copy|binding)/(.*)一、BPMN 流程设计器 — 开发复盘与技术沉淀
markdown



# BPMN 可视化流程设计器 · 开发复盘文档

> 阶段：第一阶段 · 只做编辑器（不接运行引擎）
> 状态：✅ 核心链路已跑通（2026-11-17 修复完成，详见 §8 完整复盘）

---

## 1. 项目架构总览

### 1.1 隔离原则（第一优先级，绝对不能破坏）

现有两套流程定义**完全平行、解耦**：

| 维度 | 自研配置式（老） | BPMN 可视化（新） |
|------|----------------|-----------------|
| 前端路由 | `/system/wf/definition` + `/workflow/designer` | `/workflow/bpmn/definition` + `/workflow/bpmn/designer` |
| 后端路径 | `/workflow/definition` | `/workflow/bpmn/definition` |
| 数据库表 | `wf_process_definition` | `wf_bpmn_definition` |
| 菜单 sys_menu | route_path=`wfDefinition`, parent_id=65 | route_path=`bpmn/definition`, parent_id=65 |
| 后端包结构 | `com.cyan.modules.workflow` | `com.cyan.modules.workflow.bpmn`（子包，零 import 老代码） |
| 数据格式 | 节点 JSON 配置 | BPMN 2.0 XML + 自定义 extensionElements |

**约束：本期不做两套数据互转、不引入 Flowable/Activiti、不改现有 workflow 模块任何代码。**

### 1.2 技术栈

- 前端：Vue 3 + Element Plus + Vite 5.4.21 + bpmn-js 18.16.1
- 后端：Spring Boot + MyBatis-Plus + Java 17 + MariaDB/MySQL
- 组织架构接口：复用 `/system/org/tree`、`/system/post/all`、`/system/user/search`

### 1.3 文件清单

**后端（5 个新文件，全部新建在 `com.cyan.modules.workflow.bpmn` 包下）：**
cyanbase-backend/src/main/java/com/cyan/modules/workflow/bpmn/ ├── entity/WfBpmnDefinition.java ← @TableName("wf_bpmn_definition") ├── mapper/WfBpmnDefinitionMapper.java ← extends BaseMapper，@Mapper ├── service/WfBpmnDefinitionService.java ├── service/impl/WfBpmnDefinitionServiceImpl.java └── controller/WfBpmnDefinitionController.java ← @RequestMapping("/workflow/bpmn/definition")

text




**前端（4 个新文件 + 2 处修改）：**
cyanbase-frontend/src/ ├── api/bpmnDefinition.js ← 新建 ├── api/user.js ← 修改：追加 searchUsersApi（/user/search 路由镜像） ├── api/post.js ← 已有，getAllPost() 直接用 ├── api/org.js ← 已有，getOrgTree() 直接用 ├── router/index.js ← 修改：追加两条路由 └── views/workflow/bpmn/ ├── list/index.vue ← 新建：列表页 └── designer/ ├── index.vue ← 新建：可视化设计器（核心） ├── cyan-moddle.json ← ❌ 已废弃，不要创建 └── i18n/translate.js ← 新建：内联汉化函数

text




**数据库：**

- 建表 SQL：见后端 Entity 对应 DDL
- sys_menu 注册：parent_id=65（工作流管理），menu_type=2，route_path=`bpmn/definition`
- sys_tenant_menu：批量授权给所有 tenant

### 1.4 sys_menu 路由拼接机制（关键隐性约束）

sys_menu.route_path **不是** Vue Router 的 path，而是由 Layout 组件的 `getPath` 函数拼接：

```javascript
// Layout 里的路由拼接逻辑（已废弃但记录在案） const routeMap = { 'wfDefinition': '/system/wf/definition', // ... }

// 现有菜单"工作流管理"(id=65) → parentPath = 'workflow' // sys_menu.route_path = 'bpmn/definition' // → 拼接结果 = '/workflow/bpmn/definition' ✅ 正好等于 Vue Router path

text




所以：sys_menu.route_path 填 `bpmn/definition`，Layout 自动拼成 `/workflow/bpmn/definition`，**和 Vue Router 硬编码的 path 一致**。

---

## 2. 数据库表结构

```sql
CREATE TABLE wf_bpmn_definition ( id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL COMMENT 'TenantMetaObjectHandler 自动填充', process_name VARCHAR(200) NOT NULL, process_key VARCHAR(100) NOT NULL, description VARCHAR(500) COMMENT '流程描述', category VARCHAR(100) COMMENT '流程分类', version INT NOT NULL DEFAULT 1, business_type VARCHAR(50) COMMENT '预留', status TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布', bpmn_xml LONGTEXT NOT NULL COMMENT 'BPMN 2.0 XML（含 extensionElements）', bpmn_viewport TEXT COMMENT '画布视口 JSON（可选）', del_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) COMMENT '⚠️ BaseEntity 有此字段，必须建', create_by VARCHAR(50), create_time DATETIME, update_by VARCHAR(50), update_time DATETIME, PRIMARY KEY (id), UNIQUE KEY uk_process_key_version (process_key, version), KEY idx_tenant (tenant_id) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

text




**⚠️ 关键：必须有 `remark` 字段！** 因为 `BaseEntity` 里有 `remark` 属性，MP 会自动 SELECT，如果漏建会报 `Unknown column 'remark' in 'SELECT'`。

---

## 3. 踩坑日志（按时间顺序）

### Pitfall 1：Entity tenantId 没有 fill 注解 → INSERT 炸

**现象：** Field 'tenant_id' doesn't have a default value  
**根因：** `TenantMetaObjectHandler` 的 `strictInsertFill` 内部有检查——如果目标字段**没有** `@TableField(fill = FieldFill.INSERT)`，**静默跳过填充**，不报错、不警告。  
**修复：** Entity 的 tenantId 字段加上：
```java
@TableField(value = "tenant_id", fill = FieldFill.INSERT) private Long tenantId;

text




**额外知识点：** 还有一条兜底链路 `TenantLineInnerInterceptor`（SQL 拦截器层），当 tenantId > 0 时自动改写 SQL 追加 tenant_id 值。但 tenantId=0（超级管理员）时拦截器让位给实体层，这时没 fill 注解就炸了。

### Pitfall 2：BaseEntity 有 remark 字段 → 建表漏列

**现象：** Unknown column 'remark' in 'SELECT'  
**根因：** `BaseEntity` 继承来的属性，MP 会自动 SELECT。建表时忘了加 `remark` 列。  
**修复：** ALTER TABLE ADD remark VARCHAR(500)，或 Entity 加 `@TableField(exist = false)` 排除。

### Pitfall 3：@MapperScan 不覆盖新子包 → Mapper 找不到

**现象：** No qualifying bean of type `WfBpmnDefinitionMapper`  
**根因：** `@MapperScan` 扫 `com.cyan` 下的所有包，但新增的 `bpmn` 子包路径太深时可能被某些过滤规则跳过？实际原因是启动类的 scanBasePackages 没覆盖。  
**修复：** 确认 `@MapperScan("com.cyan")` 能覆盖到 `com.cyan.modules.workflow.bpmn.mapper`。

### Pitfall 4：bpmn-js v17+ 不支持 keyboard.bindTo

**现象：** Keyboard binding is now implicit  
**根因：** bpmn-js v17 底层 diagram-js 做了改动，键盘绑定改成隐式自动绑定到 document。显式传 `keyboard: { bindTo: ... }` 就警告并丢弃。  
**修复：** Modeler 构造参数里删掉 `keyboard: { bindTo: document }`。

### Pitfall 5：空 BPMN 模板 XML 闭合标签 typo

**现象：** Error: unparsable content `</di:BPMNEdge>` detected / closing tag mismatch  
**根因：** 手敲模板 XML 时，两个 `<bpmndi:BPMNEdge>` 的闭合标签写成了 `</di:BPMNEdge>`。BPMN XML 对命名空间前缀严格敏感——`<bpmndi:BPMNEdge>` 和 `</di:BPMNEdge>` 被认为是完全不同的标签。  
**修复：** 统一用 `<bpmndi:BPMNEdge>...</bpmndi:BPMNEdge>`，根元素显式声明 `xmlns:di`。

### Pitfall 6：bpmn-js-i18n-zh 包 ESM 默认导出失败

**现象：** "default" is not exported by `bpmn-js-i18n-zh`  
**根因：** 该包是 CJS 导出，Rollup/Vite 的 ESM 默认导入解析失败。  
**修复：** **换方案——内联 translate 函数**。bpmn-js 的 translate 机制本质上就是 `(key, interpolations) => string` 函数，自己维护一个 CN_MAP 字典完全 OK，还更可控。文件位置：`views/workflow/bpmn/designer/i18n/translate.js`。

### Pitfall 7：Vue 3 Proxy 和 bpmn-js 原生对象属性冲突

**现象：** `TypeError: 'get' on proxy: property 'labels' is a read-only and non-configurable data property on the proxy target but the proxy did not return its actual value`  
**根因：** Vue 3 的 Proxy 对 bpmn-js 原生 element 对象做**深度代理**，但 bpmn-js 的元素对象有自定义 getter（`.labels` 返回 Array）、部分属性 `configurable: false`、内部有 `Array.isArray(el.labels)` 类型检查。Proxy 拦截后返回值和原始 descriptor 声明不一致。  
**修复：** 存 bpmn-js 原生对象的 ref 必须：
```javascript
const selectedElement = shallowRef(null) // shallowRef：.value 不深代理 // 赋值时 selectedElement.value = markRaw(el) // markRaw：显式标记跳过代理

text




### Pitfall 8（严重，连锁 3 炸）：cyan:* 自定义扩展到底怎么创建？

> 2026-11-17 修复，挖了 bpmn-moddle 源码才搞清楚。经历了 3 次方案迭代才找到正确答案。

**现象链：**
```
第 1 次：moddle.create('cyan:assignee') → unknown type "cyan:assignee"（没注册 descriptor）
    ↓
第 2 次：注册 cyan-moddle.json descriptor → 更严重：unknown type "bpmn:extensionElements"
         （descriptor 格式写错 + 注册机制破坏了内置类型查找）
    ↓
第 3 次：手动 JSON 构建 { $type: 'cyan:assignee', ... }
         → Cannot read properties of undefined (reading 'isGeneric')
         （writer 序列化时要读 $descriptor，手动 JSON 没有 $descriptor）
    ↓
第 4 次：moddle.createAny('cyan:assignee', CYAN_NS_URI, props) → ✅ 终于对了！
```

**根因（bpmn-moddle 源码级约束）：**

| API | 适用范围 | 要求 |
|-----|---------|------|
| `moddle.create('xxx')` | **已注册 descriptor 的类型** | 内置 BPMN 类型 + 你手动注册的扩展类型 |
| `moddle.createAny('prefix:name', nsURI, props)` | **未注册的未知扩展类型** | 自动构建 `{ isGeneric: true }` 的 `$descriptor`，writer 走 generic 序列化路径 |
| 手动 JSON `{ $type, ... }` | ❌ 不行 | 没有 `$descriptor`，writer 崩在 `.isGeneric` |

**修复：** 统一用 `moddle.createAny()` 创建 cyan:* 扩展：
```javascript
const CYAN_NS_URI = 'http://cyan.org/bpmn-ext'
function makeCyanAny(moddle, localName, props) {
  // 空值过滤：undefined/null/空字符串 不写入
  const clean = {}
  for (const [k, v] of Object.entries(props)) {
    if (k === '$children') clean[k] = v
    else if (k === '$body' && v) clean[k] = String(v)
    else if (v !== undefined && v !== null && v !== '') clean[k] = String(v)
  }
  return moddle.createAny('cyan:' + localName, CYAN_NS_URI, clean)
}

// 内置 BPMN 类型（extensionElements / FormalExpression）还是用 moddle.create()
moddle.create('bpmn:ExtensionElements')       // ✅ 已注册
moddle.create('bpmn:FormalExpression', { $body: expr })  // ✅ 已注册
```

**关键：** `cyan-moddle.json` descriptor 文件**完全不需要**，删掉它！

### Pitfall 9：TenantUserController 缺 /search 路由 → 租户登录时用户搜索炸

**现象：** `/tenant/user/search` 返回 `code:10005, "参数类型错误: id 需要 Long 类型"`  
**根因：** 两套用户接口（SysUserController /tenant/user 和 TenantUserController /tenant/user）。`SysUserController` 有 `/search` 但 `TenantUserController` 没有。租户登录时前端路由前缀自动切 `/tenant`，请求 `/tenant/user/search` 被 TenantUserController 的 `@GetMapping("/{id}")` 贪婪匹配吞了，`"search"` 字符串转 Long 失败。  
**修复：** 给 TenantUserController 也加一套 `/search` 路由镜像，和 SysUserController 的 searchUsers 保持一致。TenantLine 拦截器自动隔离 tenantId，wrapper 里不显式加。

### Pitfall 10：用户搜索下拉 label 写错

**现象：** 下拉选项显示 `undefined()`  
**根因：** 后端 `/system/user/search` 返回精简结构 `{id, label: "昵称(用户名)"}`，是专门给工作流下拉用的。前端却按完整用户对象解构 `u.userName` + `u.nickName`。  
**修复：** el-option 直接用后端返回的 `u.label`。

### Pitfall 11（严重）：el-tree-select 的 node-key / props.value 字段名写错

**现象：** 选了部门不回显 + 展开时所有节点被误判高亮为选中态  
**根因：** 后端 `/system/org/tree` 返回的 `OrgNodeVO` 里**根本没有 `id` 字段**！实际字段：
```
nodeId:  "platform-root" / "tenant-1" / "dept-6"    ← 唯一标识
nodeType: "platform-root" / "tenant" / "dept"
deptId:  6                                          ← 只有 dept 节点有
label:   "人力资源部"
```
但模板写的是 `node-key="id"` + `props.value="id"` → el-tree-select 找不到 key → 无法匹配选中态。  
**修复：**
```html
<el-tree-select
  node-key="nodeId"                              ← 唯一标识
  :props="{ value: 'deptId', disabled: 'nodeDisabled', ... }"  ← deptId 才是部门 ID
/>
```
加载 deptTree 后还要递归给非 dept 节点（platform-root / tenant）加 `nodeDisabled: true`。

### Pitfall 12：岗位 / 用户 el-select 缺 @change → item.name 永远是空字符串

**现象：** 选中岗位或用户后，保存成功，但 XML 里 `name=""`，下次打开 el-select 显示原始 ID 而不是名字  
**根因：** 部门有 `@change="(v) => { item.name = findDeptName(v) }"`，但岗位和用户的 el-select **都没有 @change 来写 item.name**！  
→ 保存到 XML 时 `name: a.name || ''` → name 为空
→ 读回来注入 userOptions 条件 `a.value && a.name` → name 为空 → 跳过注入
→ userOptions 无该选项 → el-select 匹配不到 → 显示原始 value "15" 而不是 "苏振邦"  
**修复：** 给岗位和用户都补 @change：
```html
<!-- 岗位 -->
@change="(v) => { const p = postList.find(x => String(x.id) === String(v)); item.name = p ? p.postName : '' }"
<!-- 用户 -->
@change="(v) => { const u = userOptions.find(x => String(x.id) === String(v)); item.name = u ? u.label : '' }"
```

 把**整个 `/workflow/bpmn/**`** 都当后端 API 代理了。真实 BPMN Controller 只有 `/workflow/bpmn/definition/**`（列表/新增/详情/更新/删除/发布共 8 个 endpoint），但前端路由 `/workflow/bpmn/designer` 和 `/workflow/bpmn/definition`（列表页 GET）被正则抢走转发给 Spring Boot → 404。  
**为什么自研 `/workflow/designer` 没问题？** 那个正则要求两层路径（`/workflow/XXX/(.*)`），`/workflow/designer` 只有一层，没被正则抢走，落到 `location / { try_files }` 就正常了。  
**修复（改 2 处 nginx 规则）：**
```nginx
# 第 1 处：bpmn 从大杂烩正则里拎出来单独写（只 proxy 真实 API definition）
# ❌ 原来：location ~ ^/workflow/(bpmn|instance|...)/(.*)$ { proxy_pass ... }
# ✅ 改成：
location ~ ^/workflow/bpmn/definition(/.*)?$ { proxy_pass http://127.0.0.1:8090; }
location ~ ^/workflow/(instance|definition|approve|node|copy|binding)/(.*)$ { proxy_pass http://127.0.0.1:8090; }

# 第 2 处：裸路径规则同步改
# ❌ 原来：location ~ ^/workflow/(bpmn|...)$ { proxy_pass ... }
# ✅ 改成：
location ~ ^/workflow/bpmn/definition$ { proxy_pass http://127.0.0.1:8090; }
location ~ ^/workflow/(definition|instance|approve|node|copy|binding)$ { proxy_pass http://127.0.0.1:8090; }
```
**改完后效果：**
| 请求路径 | 匹配 | 结果 |
|---------|------|------|
| `/workflow/bpmn/definition/list` | 新正则 definition | proxy → 后端 ✅ |
| `/workflow/bpmn/designer?id=16` | **没匹配任何 API 正则** → `location / { try_files }` | SPA fallback ✅ |

### Pitfall 18：流程级扩展（formRef）必须写 definitions.extensionElements

**现象：** formRef 保存后重新打开读不到（或写到了 process 上变成脏 attribute）。  
**根因：** 混淆了三个层级的 extensionElements：
```
层级 1：bpmn:definitions.extensionElements   ← ✅ 全局流程级扩展（formRef 放这里）
层级 2：bpmn:process.extensionElements       ← ❌ 别放这里
层级 3：bpmn:userTask / sequenceFlow.extensionElements ← ✅ 节点级扩展（assignees/desc 放这里）
```
**流程级 formRef 设计：** 整个 BPMN XML 只有一个 definitions 根元素，放这里最合理。前端通过 `modeler.get('canvas').getRootElement().businessObject.$parent` 拿到 definitions，用 `modeling.updateModdleProperties` 更新它的 extensionElements。

---

## 4. 自定义属性存储方案（extensionElements 设计）

### 4.1 Schema（2026-11-17 修正确认）

```xml
<!-- UserTask 示例：审批人 + 会签类型 -->
<bpmn:userTask id="UserTask_1" name="部门经理审批">
  <bpmn:extensionElements>
    <cyan:assignees>
      <cyan:assignee type="dept" value="6"  name="人力资源部" />
      <cyan:assignee type="post" value="15" name="部门经理" />
      <cyan:assignee type="user"  value="7"  name="张三" />
    </cyan:assignees>
    <cyan:signType>OR</cyan:signType>
  </bpmn:extensionElements>
</bpmn:userTask>

<!-- SequenceFlow 示例：条件表达式 + 描述 -->
<bpmn:sequenceFlow id="Flow_1" sourceRef="UserTask_1" targetRef="UserTask_2">
  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">amount >= 100000</bpmn:conditionExpression>
  <bpmn:extensionElements>
    <cyan:desc>大额审批</cyan:desc>
  </bpmn:extensionElements>
</bpmn:sequenceFlow>
```

**cyan:assignee.type 取值：** `initiator` / `dept` / `post` / `user`
**cyan:signType 取值：** `OR`（或签） / `AND`（会签）

### 4.2 读写方式（2026-11-17 修复定稿：混合策略）

**核心原则：** BPMN 内置类型和 cyan:* 自定义扩展走不同路径，绝不能混淆。

```
bpmn:extensionElements / bpmn:FormalExpression  → moddle.create() （已注册 descriptor）
cyan:assignee / cyan:assignees / cyan:signType   → moddle.createAny() （未注册，generic 元素）
```

**❌ 三个绝对不能碰的坑：**

| 错误 | 后果 | 原因 |
|------|------|------|
| `moddle.create('cyan:xxx')` | unknown type | cyan 没注册 descriptor |
| 手动 JSON `{ $type: 'cyan:xxx' }` | 崩在 `.isGeneric` | writer 需要 `$descriptor`，手动 JSON 没有 |
| cyan:assignees 的子 assignee 存成普通属性 `{ assignee: [...] }` | XML 里变成 `assignee="[object Object]"` | 普通属性被当 XML attribute，嵌套子元素必须存 `$children` |

**✅ 正确写入示例（persistElementProps 核心逻辑）：**
```javascript
// 1. cyan:assignees（容器）+ cyan:assignee（子元素）
const children = assignees.map(a =>
  makeCyanAny(moddle, 'assignee', { type: a.type, value: a.value, name: a.name })
)
// ⭐ 嵌套子元素放 $children，不是普通属性 assignee
newExts.push(makeCyanAny(moddle, 'assignees', { $children: children }))

// 2. cyan:signType / cyan:desc（文本内容节点）
newExts.push(makeCyanAny(moddle, 'signType', { $body: 'OR' }))

// 3. cyan:* 作为 extensionElements.values 的子节点
extEls = moddle.create('bpmn:ExtensionElements')  // 内置类型，用 create()
// splice 原地替换，不要替换 values 数组引用
extEls.values.splice(0, extEls.values.length, ...filteredOld, ...newExts)

// 4. conditionExpression（BPMN 内置类型，独立处理）
modeling.updateModdleProperties(el, bo, {
  conditionExpression: moddle.create('bpmn:FormalExpression', { $body: expr })
})
```

**✅ 正确读取（importXML 后自动反序列化）：**
```javascript
// importXML 时 bpmn-js 自动把 <cyan:xxx> 反序列化成带 $descriptor 的 moddle 对象
// values[i].$type === 'cyan:assignee'
// values[i].$children → 子 assignee 数组（自动序列化嵌套）
// values[i].$body → 文本节点内容（<cyan:signType>OR</cyan:signType> → $body='OR'）
// values[i].type / value / name → XML attributes
```




---

## 5. 后端接口清单

| Method | URL | 权限 | 说明 |
|--------|-----|------|------|
| GET | `/workflow/bpmn/definition/list` | workflow:bpmn:list | 分页列表 |
| GET | `/workflow/bpmn/definition/{id}` | workflow:bpmn:list | 详情（含 bpmn_xml） |
| GET | `/workflow/bpmn/definition/{id}/xml` | workflow:bpmn:list | 仅 XML |
| POST | `/workflow/bpmn/definition` | workflow:bpmn:edit | **新增**（processKey 由后端自动生成：`bpmn_` + UUID去横线前16位，冲突自动重试最多5次；前端不传也不校验） |
| PUT | `/workflow/bpmn/definition` | workflow:bpmn:edit | 更新 |
| DELETE | `/workflow/bpmn/definition/{id}` | workflow:bpmn:delete | 逻辑删除 |
| PUT | `/workflow/bpmn/definition/publish/{id}` | workflow:bpmn:publish | 发布（0→1，只标记状态） |

**复用接口（不新建）：**

| 接口 | 用途 |
|------|------|
| `GET /system/org/tree` | 属性面板部门树 |
| `GET /system/post/all` | 属性面板岗位下拉 |
| `GET /system/user/search?keyword=xxx` | 属性面板用户搜索（前后端各一套镜像） |

---

## 6. 全局隐性约束清单

### 6.1 后端约束

| # | 约束 | 影响 |
|---|------|------|
| 1 | **sys_menu 的 menu_type 是 tinyint**：1=目录 2=菜单 3=按钮 | SQL 不能写 'C'/'F' |
| 2 | **sys_menu 的 sort_num 不是 sort_order** | 字段名别写错 |
| 3 | **sys_menu.route_path ≠ Vue Router path** | Layout 的 getPath 做 parentPath+route_path 拼接 |
| 4 | **TenantMetaObjectHandler 只填有 fill 注解的字段** | Entity 字段必须加 `@TableField(fill = FieldFill.INSERT)` |
| 5 | **BaseEntity 有 remark + create_by/update_by/create_time/update_time** | 建表列不能漏 |
| 6 | **MP 逻辑删除 @TableLogic + del_flag 列** | 自动处理，业务代码不手动 set |
| 7 | **TenantLine 自动加 tenant_id 过滤** | wrapper 里不显式加 tenantId 条件 |
| 8 | **tenantId=0 时 TenantLine 让位** | 超级管理员绕过租户隔离 |
| 9 | **SysUserController 和 TenantUserController 接口必须镜像** | 租户登录路由前缀自动切 /tenant，缺接口会被 /{id} 贪婪匹配吞掉 |
| 10 | **OrgNodeVO 字段**：nodeId / nodeType / label / deptId / tenantId — 没有 id 字段！ | el-tree-select 的 node-key 和 props.value 必须用 nodeId / deptId |

### 6.2 前端 + bpmn-js 约束

| # | 约束 | 影响 |
|---|------|------|
| 11 | **Vue 3 存 bpmn-js 原生对象必须 shallowRef + markRaw** | 不然 Proxy 炸 |
| 12 | **bpmn-js 不支持 keyboard.bindTo** | v17+ 隐式绑定，删掉即可 |
| 13 | **bpmn-i18n-zh 包 ESM 导出有问题** | 内联 translate 函数更可控 |
| 14 | **moddle.createAny() 创建 cyan:* 扩展** | moddle.create() 只认已注册 descriptor 的类型；手动 JSON 没 $descriptor 崩 .isGeneric |
| 15 | **moddle 类型名首字母大写** | ExtensionElements（不是 extensionElements）、FormalExpression（不是 tFormalExpression） |
| 16 | **嵌套子元素存 $children** | 普通属性被 XML 序列化当 attribute → `[object Object]` |
| 17 | **extensionElements 必须是 moddle 真对象** | 不能手动 JSON 构建，要用 `moddle.create('bpmn:ExtensionElements')` |
| 18 | **空值属性必须 clean 过滤** | 写入 undefined → XML 里变成脏 `value="undefined"` attribute |
| 19 | **remote el-select 回显必须注入 options** | Element Plus 只有 options 里有对应项才显示 label，打开流程时 options 为空 → 显示 ID |
| 20 | **岗位 / 用户 el-select 必须有 @change** | 要从 postList / userOptions 查 label 写 item.name，否则 XML 里 name 永远是空 |
| 21 | **bpmn.io logo 隐藏写在非 scoped style** | .bjs-powered-by 是 bpmn-js 动态生成的 DOM，不在 scoped 作用域内 |
| 22 | **Nginx 反代 regex location**：bpmn 只 proxy `/workflow/bpmn/definition(/.*)?一、BPMN 流程设计器 — 开发复盘与技术沉淀
markdown



# BPMN 可视化流程设计器 · 开发复盘文档

> 阶段：第一阶段 · 只做编辑器（不接运行引擎）
> 状态：✅ 核心链路已跑通（2026-11-17 修复完成，详见 §8 完整复盘）

---

## 1. 项目架构总览

### 1.1 隔离原则（第一优先级，绝对不能破坏）

现有两套流程定义**完全平行、解耦**：

| 维度 | 自研配置式（老） | BPMN 可视化（新） |
|------|----------------|-----------------|
| 前端路由 | `/system/wf/definition` + `/workflow/designer` | `/workflow/bpmn/definition` + `/workflow/bpmn/designer` |
| 后端路径 | `/workflow/definition` | `/workflow/bpmn/definition` |
| 数据库表 | `wf_process_definition` | `wf_bpmn_definition` |
| 菜单 sys_menu | route_path=`wfDefinition`, parent_id=65 | route_path=`bpmn/definition`, parent_id=65 |
| 后端包结构 | `com.cyan.modules.workflow` | `com.cyan.modules.workflow.bpmn`（子包，零 import 老代码） |
| 数据格式 | 节点 JSON 配置 | BPMN 2.0 XML + 自定义 extensionElements |

**约束：本期不做两套数据互转、不引入 Flowable/Activiti、不改现有 workflow 模块任何代码。**

### 1.2 技术栈

- 前端：Vue 3 + Element Plus + Vite 5.4.21 + bpmn-js 18.16.1
- 后端：Spring Boot + MyBatis-Plus + Java 17 + MariaDB/MySQL
- 组织架构接口：复用 `/system/org/tree`、`/system/post/all`、`/system/user/search`

### 1.3 文件清单

**后端（5 个新文件，全部新建在 `com.cyan.modules.workflow.bpmn` 包下）：**
cyanbase-backend/src/main/java/com/cyan/modules/workflow/bpmn/ ├── entity/WfBpmnDefinition.java ← @TableName("wf_bpmn_definition") ├── mapper/WfBpmnDefinitionMapper.java ← extends BaseMapper，@Mapper ├── service/WfBpmnDefinitionService.java ├── service/impl/WfBpmnDefinitionServiceImpl.java └── controller/WfBpmnDefinitionController.java ← @RequestMapping("/workflow/bpmn/definition")

text




**前端（4 个新文件 + 2 处修改）：**
cyanbase-frontend/src/ ├── api/bpmnDefinition.js ← 新建 ├── api/user.js ← 修改：追加 searchUsersApi（/user/search 路由镜像） ├── api/post.js ← 已有，getAllPost() 直接用 ├── api/org.js ← 已有，getOrgTree() 直接用 ├── router/index.js ← 修改：追加两条路由 └── views/workflow/bpmn/ ├── list/index.vue ← 新建：列表页 └── designer/ ├── index.vue ← 新建：可视化设计器（核心） ├── cyan-moddle.json ← ❌ 已废弃，不要创建 └── i18n/translate.js ← 新建：内联汉化函数

text




**数据库：**

- 建表 SQL：见后端 Entity 对应 DDL
- sys_menu 注册：parent_id=65（工作流管理），menu_type=2，route_path=`bpmn/definition`
- sys_tenant_menu：批量授权给所有 tenant

### 1.4 sys_menu 路由拼接机制（关键隐性约束）

sys_menu.route_path **不是** Vue Router 的 path，而是由 Layout 组件的 `getPath` 函数拼接：

```javascript
// Layout 里的路由拼接逻辑（已废弃但记录在案） const routeMap = { 'wfDefinition': '/system/wf/definition', // ... }

// 现有菜单"工作流管理"(id=65) → parentPath = 'workflow' // sys_menu.route_path = 'bpmn/definition' // → 拼接结果 = '/workflow/bpmn/definition' ✅ 正好等于 Vue Router path

text




所以：sys_menu.route_path 填 `bpmn/definition`，Layout 自动拼成 `/workflow/bpmn/definition`，**和 Vue Router 硬编码的 path 一致**。

---

## 2. 数据库表结构

```sql
CREATE TABLE wf_bpmn_definition ( id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL COMMENT 'TenantMetaObjectHandler 自动填充', process_name VARCHAR(200) NOT NULL, process_key VARCHAR(100) NOT NULL, description VARCHAR(500) COMMENT '流程描述', category VARCHAR(100) COMMENT '流程分类', version INT NOT NULL DEFAULT 1, business_type VARCHAR(50) COMMENT '预留', status TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布', bpmn_xml LONGTEXT NOT NULL COMMENT 'BPMN 2.0 XML（含 extensionElements）', bpmn_viewport TEXT COMMENT '画布视口 JSON（可选）', del_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) COMMENT '⚠️ BaseEntity 有此字段，必须建', create_by VARCHAR(50), create_time DATETIME, update_by VARCHAR(50), update_time DATETIME, PRIMARY KEY (id), UNIQUE KEY uk_process_key_version (process_key, version), KEY idx_tenant (tenant_id) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

text




**⚠️ 关键：必须有 `remark` 字段！** 因为 `BaseEntity` 里有 `remark` 属性，MP 会自动 SELECT，如果漏建会报 `Unknown column 'remark' in 'SELECT'`。

---

## 3. 踩坑日志（按时间顺序）

### Pitfall 1：Entity tenantId 没有 fill 注解 → INSERT 炸

**现象：** Field 'tenant_id' doesn't have a default value  
**根因：** `TenantMetaObjectHandler` 的 `strictInsertFill` 内部有检查——如果目标字段**没有** `@TableField(fill = FieldFill.INSERT)`，**静默跳过填充**，不报错、不警告。  
**修复：** Entity 的 tenantId 字段加上：
```java
@TableField(value = "tenant_id", fill = FieldFill.INSERT) private Long tenantId;

text




**额外知识点：** 还有一条兜底链路 `TenantLineInnerInterceptor`（SQL 拦截器层），当 tenantId > 0 时自动改写 SQL 追加 tenant_id 值。但 tenantId=0（超级管理员）时拦截器让位给实体层，这时没 fill 注解就炸了。

### Pitfall 2：BaseEntity 有 remark 字段 → 建表漏列

**现象：** Unknown column 'remark' in 'SELECT'  
**根因：** `BaseEntity` 继承来的属性，MP 会自动 SELECT。建表时忘了加 `remark` 列。  
**修复：** ALTER TABLE ADD remark VARCHAR(500)，或 Entity 加 `@TableField(exist = false)` 排除。

### Pitfall 3：@MapperScan 不覆盖新子包 → Mapper 找不到

**现象：** No qualifying bean of type `WfBpmnDefinitionMapper`  
**根因：** `@MapperScan` 扫 `com.cyan` 下的所有包，但新增的 `bpmn` 子包路径太深时可能被某些过滤规则跳过？实际原因是启动类的 scanBasePackages 没覆盖。  
**修复：** 确认 `@MapperScan("com.cyan")` 能覆盖到 `com.cyan.modules.workflow.bpmn.mapper`。

### Pitfall 4：bpmn-js v17+ 不支持 keyboard.bindTo

**现象：** Keyboard binding is now implicit  
**根因：** bpmn-js v17 底层 diagram-js 做了改动，键盘绑定改成隐式自动绑定到 document。显式传 `keyboard: { bindTo: ... }` 就警告并丢弃。  
**修复：** Modeler 构造参数里删掉 `keyboard: { bindTo: document }`。

### Pitfall 5：空 BPMN 模板 XML 闭合标签 typo

**现象：** Error: unparsable content `</di:BPMNEdge>` detected / closing tag mismatch  
**根因：** 手敲模板 XML 时，两个 `<bpmndi:BPMNEdge>` 的闭合标签写成了 `</di:BPMNEdge>`。BPMN XML 对命名空间前缀严格敏感——`<bpmndi:BPMNEdge>` 和 `</di:BPMNEdge>` 被认为是完全不同的标签。  
**修复：** 统一用 `<bpmndi:BPMNEdge>...</bpmndi:BPMNEdge>`，根元素显式声明 `xmlns:di`。

### Pitfall 6：bpmn-js-i18n-zh 包 ESM 默认导出失败

**现象：** "default" is not exported by `bpmn-js-i18n-zh`  
**根因：** 该包是 CJS 导出，Rollup/Vite 的 ESM 默认导入解析失败。  
**修复：** **换方案——内联 translate 函数**。bpmn-js 的 translate 机制本质上就是 `(key, interpolations) => string` 函数，自己维护一个 CN_MAP 字典完全 OK，还更可控。文件位置：`views/workflow/bpmn/designer/i18n/translate.js`。

### Pitfall 7：Vue 3 Proxy 和 bpmn-js 原生对象属性冲突

**现象：** `TypeError: 'get' on proxy: property 'labels' is a read-only and non-configurable data property on the proxy target but the proxy did not return its actual value`  
**根因：** Vue 3 的 Proxy 对 bpmn-js 原生 element 对象做**深度代理**，但 bpmn-js 的元素对象有自定义 getter（`.labels` 返回 Array）、部分属性 `configurable: false`、内部有 `Array.isArray(el.labels)` 类型检查。Proxy 拦截后返回值和原始 descriptor 声明不一致。  
**修复：** 存 bpmn-js 原生对象的 ref 必须：
```javascript
const selectedElement = shallowRef(null) // shallowRef：.value 不深代理 // 赋值时 selectedElement.value = markRaw(el) // markRaw：显式标记跳过代理

text




### Pitfall 8（严重，连锁 3 炸）：cyan:* 自定义扩展到底怎么创建？

> 2026-11-17 修复，挖了 bpmn-moddle 源码才搞清楚。经历了 3 次方案迭代才找到正确答案。

**现象链：**
```
第 1 次：moddle.create('cyan:assignee') → unknown type "cyan:assignee"（没注册 descriptor）
    ↓
第 2 次：注册 cyan-moddle.json descriptor → 更严重：unknown type "bpmn:extensionElements"
         （descriptor 格式写错 + 注册机制破坏了内置类型查找）
    ↓
第 3 次：手动 JSON 构建 { $type: 'cyan:assignee', ... }
         → Cannot read properties of undefined (reading 'isGeneric')
         （writer 序列化时要读 $descriptor，手动 JSON 没有 $descriptor）
    ↓
第 4 次：moddle.createAny('cyan:assignee', CYAN_NS_URI, props) → ✅ 终于对了！
```

**根因（bpmn-moddle 源码级约束）：**

| API | 适用范围 | 要求 |
|-----|---------|------|
| `moddle.create('xxx')` | **已注册 descriptor 的类型** | 内置 BPMN 类型 + 你手动注册的扩展类型 |
| `moddle.createAny('prefix:name', nsURI, props)` | **未注册的未知扩展类型** | 自动构建 `{ isGeneric: true }` 的 `$descriptor`，writer 走 generic 序列化路径 |
| 手动 JSON `{ $type, ... }` | ❌ 不行 | 没有 `$descriptor`，writer 崩在 `.isGeneric` |

**修复：** 统一用 `moddle.createAny()` 创建 cyan:* 扩展：
```javascript
const CYAN_NS_URI = 'http://cyan.org/bpmn-ext'
function makeCyanAny(moddle, localName, props) {
  // 空值过滤：undefined/null/空字符串 不写入
  const clean = {}
  for (const [k, v] of Object.entries(props)) {
    if (k === '$children') clean[k] = v
    else if (k === '$body' && v) clean[k] = String(v)
    else if (v !== undefined && v !== null && v !== '') clean[k] = String(v)
  }
  return moddle.createAny('cyan:' + localName, CYAN_NS_URI, clean)
}

// 内置 BPMN 类型（extensionElements / FormalExpression）还是用 moddle.create()
moddle.create('bpmn:ExtensionElements')       // ✅ 已注册
moddle.create('bpmn:FormalExpression', { $body: expr })  // ✅ 已注册
```

**关键：** `cyan-moddle.json` descriptor 文件**完全不需要**，删掉它！

### Pitfall 9：TenantUserController 缺 /search 路由 → 租户登录时用户搜索炸

**现象：** `/tenant/user/search` 返回 `code:10005, "参数类型错误: id 需要 Long 类型"`  
**根因：** 两套用户接口（SysUserController /tenant/user 和 TenantUserController /tenant/user）。`SysUserController` 有 `/search` 但 `TenantUserController` 没有。租户登录时前端路由前缀自动切 `/tenant`，请求 `/tenant/user/search` 被 TenantUserController 的 `@GetMapping("/{id}")` 贪婪匹配吞了，`"search"` 字符串转 Long 失败。  
**修复：** 给 TenantUserController 也加一套 `/search` 路由镜像，和 SysUserController 的 searchUsers 保持一致。TenantLine 拦截器自动隔离 tenantId，wrapper 里不显式加。

### Pitfall 10：用户搜索下拉 label 写错

**现象：** 下拉选项显示 `undefined()`  
**根因：** 后端 `/system/user/search` 返回精简结构 `{id, label: "昵称(用户名)"}`，是专门给工作流下拉用的。前端却按完整用户对象解构 `u.userName` + `u.nickName`。  
**修复：** el-option 直接用后端返回的 `u.label`。

### Pitfall 11（严重）：el-tree-select 的 node-key / props.value 字段名写错

**现象：** 选了部门不回显 + 展开时所有节点被误判高亮为选中态  
**根因：** 后端 `/system/org/tree` 返回的 `OrgNodeVO` 里**根本没有 `id` 字段**！实际字段：
```
nodeId:  "platform-root" / "tenant-1" / "dept-6"    ← 唯一标识
nodeType: "platform-root" / "tenant" / "dept"
deptId:  6                                          ← 只有 dept 节点有
label:   "人力资源部"
```
但模板写的是 `node-key="id"` + `props.value="id"` → el-tree-select 找不到 key → 无法匹配选中态。  
**修复：**
```html
<el-tree-select
  node-key="nodeId"                              ← 唯一标识
  :props="{ value: 'deptId', disabled: 'nodeDisabled', ... }"  ← deptId 才是部门 ID
/>
```
加载 deptTree 后还要递归给非 dept 节点（platform-root / tenant）加 `nodeDisabled: true`。

### Pitfall 12：岗位 / 用户 el-select 缺 @change → item.name 永远是空字符串

**现象：** 选中岗位或用户后，保存成功，但 XML 里 `name=""`，下次打开 el-select 显示原始 ID 而不是名字  
**根因：** 部门有 `@change="(v) => { item.name = findDeptName(v) }"`，但岗位和用户的 el-select **都没有 @change 来写 item.name**！  
→ 保存到 XML 时 `name: a.name || ''` → name 为空
→ 读回来注入 userOptions 条件 `a.value && a.name` → name 为空 → 跳过注入
→ userOptions 无该选项 → el-select 匹配不到 → 显示原始 value "15" 而不是 "苏振邦"  
**修复：** 给岗位和用户都补 @change：
```html
<!-- 岗位 -->
@change="(v) => { const p = postList.find(x => String(x.id) === String(v)); item.name = p ? p.postName : '' }"
<!-- 用户 -->
@change="(v) => { const u = userOptions.find(x => String(x.id) === String(v)); item.name = u ? u.label : '' }"
```

### Pitfall 13：remote el-select 回显需要 options 里有对应项

**现象：** 打开已保存流程，用户类型审批人显示 ID 而不是名字（即使 name 在 XML 里有值）  
**根因：** Element Plus 的 remote el-select **只有在 options 数组里能找到 value 对应的 option 才会显示 label**。打开流程时 userOptions 是空的（还没搜索过），所以匹配不到。  
**修复：** 在 `readElementProps` 里把已保存的 user 选项注入 userOptions（unshift 放最前）。注入条件**不能依赖 a.name**（兼容老脏数据 name 为空的情况）。

### Pitfall 14（严重）：FormalExpression 用了 $body → 被序列化成 attribute

**现象：** 保存后再次打开，conditionExpression 回显为空；保存后的 XML 里是 `$body="amount > 500"`（attribute）而不是标签内容。  
**根因：** `$body` 是 **generic 扩展元素**（createAny）的手写约定。但 `FormalExpression` 是 **BPMN 标准类型**（moddle 已注册 descriptor），它有正式属性 `body`（descriptor 里定义了 `isBody: true`）。用 `$body` 时 writer 把它当普通 attribute 输出 → XML 里 `$body="..."`。  
**修复：**
```javascript
// ❌ 错误（$body 是 createAny 约定）
moddle.create('bpmn:FormalExpression', { $body: 'amount > 500' })
// ✅ 正确（body 是 bpmn 标准属性）
moddle.create('bpmn:FormalExpression', { body: 'amount > 500', language: 'spEL' })
```
**关键区分表：**
| 创建方式 | 内容属性 | XML 输出 |
|---------|---------|----------|
| `moddle.createAny('cyan:xxx', NS, { $body: 'OR' })` | `$body`（generic 约定） | `<cyan:xxx>OR</cyan:xxx>` ✅ |
| `moddle.create('bpmn:FormalExpression', { body: expr })` | `body`（标准属性） | `<bpmn:conditionExpression>expr</...>` ✅ |
| 反过来交叉用 | ❌ 错 | 被当 attribute 输出 |

### Pitfall 15：modeler.getRootElement() 不存在 + 层级拿错

**现象：** `TypeError: d.getRootElement is not a function` 或 persistFormRefToDefs 静默失败。  
**根因：** bpmn-js Modeler **没有** `getRootElement()` 这个 API！正确的调用链是：
```javascript
// 图形层根元素（Process 图形 Element）
const processEl = modeler.get('canvas').getRootElement()
// 业务对象层
const processBO = processEl.businessObject         // bpmn:Process
const definitions = processBO.$parent              // bpmn:Definitions ← 这才是根！
```
**之前的错误：**
| 错误写法 | 拿到什么 | 后果 |
|---------|---------|------|
| `modeler.getRootElement()` | ❌ 根本不存在 | 直接 TypeError 崩 |
| `canvas.getRootElement().businessObject` | bpmn:Process | formRef 写到了 process 上，还写错属性名 |
| 属性名叫 `rootExtensionElements` | Process 上没这属性 | 被序列化成 `rootExtensionElements="[object Object]"` |
**正确写法：**
```javascript
const processEl = modeler.get('canvas').getRootElement()
const definitions = processEl.businessObject.$parent
modeling.updateModdleProperties(processEl, definitions, {
  extensionElements: extEls  // bpmn 标准属性名
})
```

### Pitfall 16（严重）：readElementProps 早退逻辑挡了 conditionExpression

**现象：** 保存了 SequenceFlow 的 conditionExpression，保存成功，但再次打开流程右侧面板条件全空；保存后的 XML 里 conditionExpression 存在。  
**根因：** `readElementProps` 里有 `if (!bo.extensionElements) return` 早退逻辑。但 `conditionExpression` 是 `bpmn:SequenceFlow` 的**标准直接属性**（类似 `sourceRef`、`targetRef`），存在 `bo.conditionExpression` 上，和 `extensionElements`（扩展节点容器）**毫无关系**！

```
旧代码执行路径：
  bo.name → elementProps.name ✅
  清 conditionExpr = ''
  if (!bo.extensionElements) return  ← 💥 没填 cyan:desc 时 extensionElements 不存在！
  ... SequenceFlow conditionExpression 读取 💀 永远不执行
```

**修复：** SequenceFlow 的 conditionExpression 读取逻辑挪到 `extensionElements` 早退**之前**：
```javascript
function readElementProps(el) {
  const bo = el.businessObject
  elementProps.name = bo.name || ''
  // 先清
  elementProps.conditionExpr = ''
  // ⭐ SequenceFlow 标准属性：在 extensionElements 早退之前处理！
  if (el.type === 'bpmn:SequenceFlow') {
    const cond = bo.conditionExpression
    if (cond && cond.body) {
      elementProps.conditionExpr = cond.body
      parseConditionToCols(cond.body)
    }
  }
  if (!bo.extensionElements) return  // 现在早退不影响 conditionExpression 了
  // ... 下面处理 extensionElements 的 cyan:* 扩展
}
```

### Pitfall 17：Nginx 正则 location 把 SPA 前端路由当 API 代理

**现象：** 生产环境硬刷新 `/workflow/bpmn/designer?id=16&action=edit` 返回 404。本地 Vite dev server 通过。  
**根因：** Nginx 正则 `location ~ ^/workflow/(bpmn|instance|definition|approve|node|copy|binding)/(.*)一、BPMN 流程设计器 — 开发复盘与技术沉淀
markdown



# BPMN 可视化流程设计器 · 开发复盘文档

> 阶段：第一阶段 · 只做编辑器（不接运行引擎）
> 状态：✅ 核心链路已跑通（2026-11-17 修复完成，详见 §8 完整复盘）

---

## 1. 项目架构总览

### 1.1 隔离原则（第一优先级，绝对不能破坏）

现有两套流程定义**完全平行、解耦**：

| 维度 | 自研配置式（老） | BPMN 可视化（新） |
|------|----------------|-----------------|
| 前端路由 | `/system/wf/definition` + `/workflow/designer` | `/workflow/bpmn/definition` + `/workflow/bpmn/designer` |
| 后端路径 | `/workflow/definition` | `/workflow/bpmn/definition` |
| 数据库表 | `wf_process_definition` | `wf_bpmn_definition` |
| 菜单 sys_menu | route_path=`wfDefinition`, parent_id=65 | route_path=`bpmn/definition`, parent_id=65 |
| 后端包结构 | `com.cyan.modules.workflow` | `com.cyan.modules.workflow.bpmn`（子包，零 import 老代码） |
| 数据格式 | 节点 JSON 配置 | BPMN 2.0 XML + 自定义 extensionElements |

**约束：本期不做两套数据互转、不引入 Flowable/Activiti、不改现有 workflow 模块任何代码。**

### 1.2 技术栈

- 前端：Vue 3 + Element Plus + Vite 5.4.21 + bpmn-js 18.16.1
- 后端：Spring Boot + MyBatis-Plus + Java 17 + MariaDB/MySQL
- 组织架构接口：复用 `/system/org/tree`、`/system/post/all`、`/system/user/search`

### 1.3 文件清单

**后端（5 个新文件，全部新建在 `com.cyan.modules.workflow.bpmn` 包下）：**
cyanbase-backend/src/main/java/com/cyan/modules/workflow/bpmn/ ├── entity/WfBpmnDefinition.java ← @TableName("wf_bpmn_definition") ├── mapper/WfBpmnDefinitionMapper.java ← extends BaseMapper，@Mapper ├── service/WfBpmnDefinitionService.java ├── service/impl/WfBpmnDefinitionServiceImpl.java └── controller/WfBpmnDefinitionController.java ← @RequestMapping("/workflow/bpmn/definition")

text




**前端（4 个新文件 + 2 处修改）：**
cyanbase-frontend/src/ ├── api/bpmnDefinition.js ← 新建 ├── api/user.js ← 修改：追加 searchUsersApi（/user/search 路由镜像） ├── api/post.js ← 已有，getAllPost() 直接用 ├── api/org.js ← 已有，getOrgTree() 直接用 ├── router/index.js ← 修改：追加两条路由 └── views/workflow/bpmn/ ├── list/index.vue ← 新建：列表页 └── designer/ ├── index.vue ← 新建：可视化设计器（核心） ├── cyan-moddle.json ← ❌ 已废弃，不要创建 └── i18n/translate.js ← 新建：内联汉化函数

text




**数据库：**

- 建表 SQL：见后端 Entity 对应 DDL
- sys_menu 注册：parent_id=65（工作流管理），menu_type=2，route_path=`bpmn/definition`
- sys_tenant_menu：批量授权给所有 tenant

### 1.4 sys_menu 路由拼接机制（关键隐性约束）

sys_menu.route_path **不是** Vue Router 的 path，而是由 Layout 组件的 `getPath` 函数拼接：

```javascript
// Layout 里的路由拼接逻辑（已废弃但记录在案） const routeMap = { 'wfDefinition': '/system/wf/definition', // ... }

// 现有菜单"工作流管理"(id=65) → parentPath = 'workflow' // sys_menu.route_path = 'bpmn/definition' // → 拼接结果 = '/workflow/bpmn/definition' ✅ 正好等于 Vue Router path

text




所以：sys_menu.route_path 填 `bpmn/definition`，Layout 自动拼成 `/workflow/bpmn/definition`，**和 Vue Router 硬编码的 path 一致**。

---

## 2. 数据库表结构

```sql
CREATE TABLE wf_bpmn_definition ( id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL COMMENT 'TenantMetaObjectHandler 自动填充', process_name VARCHAR(200) NOT NULL, process_key VARCHAR(100) NOT NULL, description VARCHAR(500) COMMENT '流程描述', category VARCHAR(100) COMMENT '流程分类', version INT NOT NULL DEFAULT 1, business_type VARCHAR(50) COMMENT '预留', status TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布', bpmn_xml LONGTEXT NOT NULL COMMENT 'BPMN 2.0 XML（含 extensionElements）', bpmn_viewport TEXT COMMENT '画布视口 JSON（可选）', del_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) COMMENT '⚠️ BaseEntity 有此字段，必须建', create_by VARCHAR(50), create_time DATETIME, update_by VARCHAR(50), update_time DATETIME, PRIMARY KEY (id), UNIQUE KEY uk_process_key_version (process_key, version), KEY idx_tenant (tenant_id) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

text




**⚠️ 关键：必须有 `remark` 字段！** 因为 `BaseEntity` 里有 `remark` 属性，MP 会自动 SELECT，如果漏建会报 `Unknown column 'remark' in 'SELECT'`。

---

## 3. 踩坑日志（按时间顺序）

### Pitfall 1：Entity tenantId 没有 fill 注解 → INSERT 炸

**现象：** Field 'tenant_id' doesn't have a default value  
**根因：** `TenantMetaObjectHandler` 的 `strictInsertFill` 内部有检查——如果目标字段**没有** `@TableField(fill = FieldFill.INSERT)`，**静默跳过填充**，不报错、不警告。  
**修复：** Entity 的 tenantId 字段加上：
```java
@TableField(value = "tenant_id", fill = FieldFill.INSERT) private Long tenantId;

text




**额外知识点：** 还有一条兜底链路 `TenantLineInnerInterceptor`（SQL 拦截器层），当 tenantId > 0 时自动改写 SQL 追加 tenant_id 值。但 tenantId=0（超级管理员）时拦截器让位给实体层，这时没 fill 注解就炸了。

### Pitfall 2：BaseEntity 有 remark 字段 → 建表漏列

**现象：** Unknown column 'remark' in 'SELECT'  
**根因：** `BaseEntity` 继承来的属性，MP 会自动 SELECT。建表时忘了加 `remark` 列。  
**修复：** ALTER TABLE ADD remark VARCHAR(500)，或 Entity 加 `@TableField(exist = false)` 排除。

### Pitfall 3：@MapperScan 不覆盖新子包 → Mapper 找不到

**现象：** No qualifying bean of type `WfBpmnDefinitionMapper`  
**根因：** `@MapperScan` 扫 `com.cyan` 下的所有包，但新增的 `bpmn` 子包路径太深时可能被某些过滤规则跳过？实际原因是启动类的 scanBasePackages 没覆盖。  
**修复：** 确认 `@MapperScan("com.cyan")` 能覆盖到 `com.cyan.modules.workflow.bpmn.mapper`。

### Pitfall 4：bpmn-js v17+ 不支持 keyboard.bindTo

**现象：** Keyboard binding is now implicit  
**根因：** bpmn-js v17 底层 diagram-js 做了改动，键盘绑定改成隐式自动绑定到 document。显式传 `keyboard: { bindTo: ... }` 就警告并丢弃。  
**修复：** Modeler 构造参数里删掉 `keyboard: { bindTo: document }`。

### Pitfall 5：空 BPMN 模板 XML 闭合标签 typo

**现象：** Error: unparsable content `</di:BPMNEdge>` detected / closing tag mismatch  
**根因：** 手敲模板 XML 时，两个 `<bpmndi:BPMNEdge>` 的闭合标签写成了 `</di:BPMNEdge>`。BPMN XML 对命名空间前缀严格敏感——`<bpmndi:BPMNEdge>` 和 `</di:BPMNEdge>` 被认为是完全不同的标签。  
**修复：** 统一用 `<bpmndi:BPMNEdge>...</bpmndi:BPMNEdge>`，根元素显式声明 `xmlns:di`。

### Pitfall 6：bpmn-js-i18n-zh 包 ESM 默认导出失败

**现象：** "default" is not exported by `bpmn-js-i18n-zh`  
**根因：** 该包是 CJS 导出，Rollup/Vite 的 ESM 默认导入解析失败。  
**修复：** **换方案——内联 translate 函数**。bpmn-js 的 translate 机制本质上就是 `(key, interpolations) => string` 函数，自己维护一个 CN_MAP 字典完全 OK，还更可控。文件位置：`views/workflow/bpmn/designer/i18n/translate.js`。

### Pitfall 7：Vue 3 Proxy 和 bpmn-js 原生对象属性冲突

**现象：** `TypeError: 'get' on proxy: property 'labels' is a read-only and non-configurable data property on the proxy target but the proxy did not return its actual value`  
**根因：** Vue 3 的 Proxy 对 bpmn-js 原生 element 对象做**深度代理**，但 bpmn-js 的元素对象有自定义 getter（`.labels` 返回 Array）、部分属性 `configurable: false`、内部有 `Array.isArray(el.labels)` 类型检查。Proxy 拦截后返回值和原始 descriptor 声明不一致。  
**修复：** 存 bpmn-js 原生对象的 ref 必须：
```javascript
const selectedElement = shallowRef(null) // shallowRef：.value 不深代理 // 赋值时 selectedElement.value = markRaw(el) // markRaw：显式标记跳过代理

text




### Pitfall 8（严重，连锁 3 炸）：cyan:* 自定义扩展到底怎么创建？

> 2026-11-17 修复，挖了 bpmn-moddle 源码才搞清楚。经历了 3 次方案迭代才找到正确答案。

**现象链：**
```
第 1 次：moddle.create('cyan:assignee') → unknown type "cyan:assignee"（没注册 descriptor）
    ↓
第 2 次：注册 cyan-moddle.json descriptor → 更严重：unknown type "bpmn:extensionElements"
         （descriptor 格式写错 + 注册机制破坏了内置类型查找）
    ↓
第 3 次：手动 JSON 构建 { $type: 'cyan:assignee', ... }
         → Cannot read properties of undefined (reading 'isGeneric')
         （writer 序列化时要读 $descriptor，手动 JSON 没有 $descriptor）
    ↓
第 4 次：moddle.createAny('cyan:assignee', CYAN_NS_URI, props) → ✅ 终于对了！
```

**根因（bpmn-moddle 源码级约束）：**

| API | 适用范围 | 要求 |
|-----|---------|------|
| `moddle.create('xxx')` | **已注册 descriptor 的类型** | 内置 BPMN 类型 + 你手动注册的扩展类型 |
| `moddle.createAny('prefix:name', nsURI, props)` | **未注册的未知扩展类型** | 自动构建 `{ isGeneric: true }` 的 `$descriptor`，writer 走 generic 序列化路径 |
| 手动 JSON `{ $type, ... }` | ❌ 不行 | 没有 `$descriptor`，writer 崩在 `.isGeneric` |

**修复：** 统一用 `moddle.createAny()` 创建 cyan:* 扩展：
```javascript
const CYAN_NS_URI = 'http://cyan.org/bpmn-ext'
function makeCyanAny(moddle, localName, props) {
  // 空值过滤：undefined/null/空字符串 不写入
  const clean = {}
  for (const [k, v] of Object.entries(props)) {
    if (k === '$children') clean[k] = v
    else if (k === '$body' && v) clean[k] = String(v)
    else if (v !== undefined && v !== null && v !== '') clean[k] = String(v)
  }
  return moddle.createAny('cyan:' + localName, CYAN_NS_URI, clean)
}

// 内置 BPMN 类型（extensionElements / FormalExpression）还是用 moddle.create()
moddle.create('bpmn:ExtensionElements')       // ✅ 已注册
moddle.create('bpmn:FormalExpression', { $body: expr })  // ✅ 已注册
```

**关键：** `cyan-moddle.json` descriptor 文件**完全不需要**，删掉它！

### Pitfall 9：TenantUserController 缺 /search 路由 → 租户登录时用户搜索炸

**现象：** `/tenant/user/search` 返回 `code:10005, "参数类型错误: id 需要 Long 类型"`  
**根因：** 两套用户接口（SysUserController /tenant/user 和 TenantUserController /tenant/user）。`SysUserController` 有 `/search` 但 `TenantUserController` 没有。租户登录时前端路由前缀自动切 `/tenant`，请求 `/tenant/user/search` 被 TenantUserController 的 `@GetMapping("/{id}")` 贪婪匹配吞了，`"search"` 字符串转 Long 失败。  
**修复：** 给 TenantUserController 也加一套 `/search` 路由镜像，和 SysUserController 的 searchUsers 保持一致。TenantLine 拦截器自动隔离 tenantId，wrapper 里不显式加。

### Pitfall 10：用户搜索下拉 label 写错

**现象：** 下拉选项显示 `undefined()`  
**根因：** 后端 `/system/user/search` 返回精简结构 `{id, label: "昵称(用户名)"}`，是专门给工作流下拉用的。前端却按完整用户对象解构 `u.userName` + `u.nickName`。  
**修复：** el-option 直接用后端返回的 `u.label`。

### Pitfall 11（严重）：el-tree-select 的 node-key / props.value 字段名写错

**现象：** 选了部门不回显 + 展开时所有节点被误判高亮为选中态  
**根因：** 后端 `/system/org/tree` 返回的 `OrgNodeVO` 里**根本没有 `id` 字段**！实际字段：
```
nodeId:  "platform-root" / "tenant-1" / "dept-6"    ← 唯一标识
nodeType: "platform-root" / "tenant" / "dept"
deptId:  6                                          ← 只有 dept 节点有
label:   "人力资源部"
```
但模板写的是 `node-key="id"` + `props.value="id"` → el-tree-select 找不到 key → 无法匹配选中态。  
**修复：**
```html
<el-tree-select
  node-key="nodeId"                              ← 唯一标识
  :props="{ value: 'deptId', disabled: 'nodeDisabled', ... }"  ← deptId 才是部门 ID
/>
```
加载 deptTree 后还要递归给非 dept 节点（platform-root / tenant）加 `nodeDisabled: true`。

### Pitfall 12：岗位 / 用户 el-select 缺 @change → item.name 永远是空字符串

**现象：** 选中岗位或用户后，保存成功，但 XML 里 `name=""`，下次打开 el-select 显示原始 ID 而不是名字  
**根因：** 部门有 `@change="(v) => { item.name = findDeptName(v) }"`，但岗位和用户的 el-select **都没有 @change 来写 item.name**！  
→ 保存到 XML 时 `name: a.name || ''` → name 为空
→ 读回来注入 userOptions 条件 `a.value && a.name` → name 为空 → 跳过注入
→ userOptions 无该选项 → el-select 匹配不到 → 显示原始 value "15" 而不是 "苏振邦"  
**修复：** 给岗位和用户都补 @change：
```html
<!-- 岗位 -->
@change="(v) => { const p = postList.find(x => String(x.id) === String(v)); item.name = p ? p.postName : '' }"
<!-- 用户 -->
@change="(v) => { const u = userOptions.find(x => String(x.id) === String(v)); item.name = u ? u.label : '' }"
```

 把**整个 `/workflow/bpmn/**`** 都当后端 API 代理了。真实 BPMN Controller 只有 `/workflow/bpmn/definition/**`（列表/新增/详情/更新/删除/发布共 8 个 endpoint），但前端路由 `/workflow/bpmn/designer` 和 `/workflow/bpmn/definition`（列表页 GET）被正则抢走转发给 Spring Boot → 404。  
**为什么自研 `/workflow/designer` 没问题？** 那个正则要求两层路径（`/workflow/XXX/(.*)`），`/workflow/designer` 只有一层，没被正则抢走，落到 `location / { try_files }` 就正常了。  
**修复（改 2 处 nginx 规则）：**
```nginx
# 第 1 处：bpmn 从大杂烩正则里拎出来单独写（只 proxy 真实 API definition）
# ❌ 原来：location ~ ^/workflow/(bpmn|instance|...)/(.*)$ { proxy_pass ... }
# ✅ 改成：
location ~ ^/workflow/bpmn/definition(/.*)?$ { proxy_pass http://127.0.0.1:8090; }
location ~ ^/workflow/(instance|definition|approve|node|copy|binding)/(.*)$ { proxy_pass http://127.0.0.1:8090; }

# 第 2 处：裸路径规则同步改
# ❌ 原来：location ~ ^/workflow/(bpmn|...)$ { proxy_pass ... }
# ✅ 改成：
location ~ ^/workflow/bpmn/definition$ { proxy_pass http://127.0.0.1:8090; }
location ~ ^/workflow/(definition|instance|approve|node|copy|binding)$ { proxy_pass http://127.0.0.1:8090; }
```
**改完后效果：**
| 请求路径 | 匹配 | 结果 |
|---------|------|------|
| `/workflow/bpmn/definition/list` | 新正则 definition | proxy → 后端 ✅ |
| `/workflow/bpmn/designer?id=16` | **没匹配任何 API 正则** → `location / { try_files }` | SPA fallback ✅ |

### Pitfall 18：流程级扩展（formRef）必须写 definitions.extensionElements

**现象：** formRef 保存后重新打开读不到（或写到了 process 上变成脏 attribute）。  
**根因：** 混淆了三个层级的 extensionElements：
```
层级 1：bpmn:definitions.extensionElements   ← ✅ 全局流程级扩展（formRef 放这里）
层级 2：bpmn:process.extensionElements       ← ❌ 别放这里
层级 3：bpmn:userTask / sequenceFlow.extensionElements ← ✅ 节点级扩展（assignees/desc 放这里）
```
**流程级 formRef 设计：** 整个 BPMN XML 只有一个 definitions 根元素，放这里最合理。前端通过 `modeler.get('canvas').getRootElement().businessObject.$parent` 拿到 definitions，用 `modeling.updateModdleProperties` 更新它的 extensionElements。

---

## 4. 自定义属性存储方案（extensionElements 设计）

### 4.1 Schema（2026-11-17 修正确认）

```xml
<!-- UserTask 示例：审批人 + 会签类型 -->
<bpmn:userTask id="UserTask_1" name="部门经理审批">
  <bpmn:extensionElements>
    <cyan:assignees>
      <cyan:assignee type="dept" value="6"  name="人力资源部" />
      <cyan:assignee type="post" value="15" name="部门经理" />
      <cyan:assignee type="user"  value="7"  name="张三" />
    </cyan:assignees>
    <cyan:signType>OR</cyan:signType>
  </bpmn:extensionElements>
</bpmn:userTask>

<!-- SequenceFlow 示例：条件表达式 + 描述 -->
<bpmn:sequenceFlow id="Flow_1" sourceRef="UserTask_1" targetRef="UserTask_2">
  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">amount >= 100000</bpmn:conditionExpression>
  <bpmn:extensionElements>
    <cyan:desc>大额审批</cyan:desc>
  </bpmn:extensionElements>
</bpmn:sequenceFlow>
```

**cyan:assignee.type 取值：** `initiator` / `dept` / `post` / `user`
**cyan:signType 取值：** `OR`（或签） / `AND`（会签）

### 4.2 读写方式（2026-11-17 修复定稿：混合策略）

**核心原则：** BPMN 内置类型和 cyan:* 自定义扩展走不同路径，绝不能混淆。

```
bpmn:extensionElements / bpmn:FormalExpression  → moddle.create() （已注册 descriptor）
cyan:assignee / cyan:assignees / cyan:signType   → moddle.createAny() （未注册，generic 元素）
```

**❌ 三个绝对不能碰的坑：**

| 错误 | 后果 | 原因 |
|------|------|------|
| `moddle.create('cyan:xxx')` | unknown type | cyan 没注册 descriptor |
| 手动 JSON `{ $type: 'cyan:xxx' }` | 崩在 `.isGeneric` | writer 需要 `$descriptor`，手动 JSON 没有 |
| cyan:assignees 的子 assignee 存成普通属性 `{ assignee: [...] }` | XML 里变成 `assignee="[object Object]"` | 普通属性被当 XML attribute，嵌套子元素必须存 `$children` |

**✅ 正确写入示例（persistElementProps 核心逻辑）：**
```javascript
// 1. cyan:assignees（容器）+ cyan:assignee（子元素）
const children = assignees.map(a =>
  makeCyanAny(moddle, 'assignee', { type: a.type, value: a.value, name: a.name })
)
// ⭐ 嵌套子元素放 $children，不是普通属性 assignee
newExts.push(makeCyanAny(moddle, 'assignees', { $children: children }))

// 2. cyan:signType / cyan:desc（文本内容节点）
newExts.push(makeCyanAny(moddle, 'signType', { $body: 'OR' }))

// 3. cyan:* 作为 extensionElements.values 的子节点
extEls = moddle.create('bpmn:ExtensionElements')  // 内置类型，用 create()
// splice 原地替换，不要替换 values 数组引用
extEls.values.splice(0, extEls.values.length, ...filteredOld, ...newExts)

// 4. conditionExpression（BPMN 内置类型，独立处理）
modeling.updateModdleProperties(el, bo, {
  conditionExpression: moddle.create('bpmn:FormalExpression', { $body: expr })
})
```

**✅ 正确读取（importXML 后自动反序列化）：**
```javascript
// importXML 时 bpmn-js 自动把 <cyan:xxx> 反序列化成带 $descriptor 的 moddle 对象
// values[i].$type === 'cyan:assignee'
// values[i].$children → 子 assignee 数组（自动序列化嵌套）
// values[i].$body → 文本节点内容（<cyan:signType>OR</cyan:signType> → $body='OR'）
// values[i].type / value / name → XML attributes
```




---

## 5. 后端接口清单

| Method | URL | 权限 | 说明 |
|--------|-----|------|------|
| GET | `/workflow/bpmn/definition/list` | workflow:bpmn:list | 分页列表 |
| GET | `/workflow/bpmn/definition/{id}` | workflow:bpmn:list | 详情（含 bpmn_xml） |
| GET | `/workflow/bpmn/definition/{id}/xml` | workflow:bpmn:list | 仅 XML |
| POST | `/workflow/bpmn/definition` | workflow:bpmn:edit | 新增（key+version 唯一性校验） |
| PUT | `/workflow/bpmn/definition` | workflow:bpmn:edit | 更新 |
| DELETE | `/workflow/bpmn/definition/{id}` | workflow:bpmn:delete | 逻辑删除 |
| PUT | `/workflow/bpmn/definition/publish/{id}` | workflow:bpmn:publish | 发布（0→1，只标记状态） |

**复用接口（不新建）：**

| 接口 | 用途 |
|------|------|
| `GET /system/org/tree` | 属性面板部门树 |
| `GET /system/post/all` | 属性面板岗位下拉 |
| `GET /system/user/search?keyword=xxx` | 属性面板用户搜索（前后端各一套镜像） |

---

## 6. 全局隐性约束清单

### 6.1 后端约束

| # | 约束 | 影响 |
|---|------|------|
| 1 | **sys_menu 的 menu_type 是 tinyint**：1=目录 2=菜单 3=按钮 | SQL 不能写 'C'/'F' |
| 2 | **sys_menu 的 sort_num 不是 sort_order** | 字段名别写错 |
| 3 | **sys_menu.route_path ≠ Vue Router path** | Layout 的 getPath 做 parentPath+route_path 拼接 |
| 4 | **TenantMetaObjectHandler 只填有 fill 注解的字段** | Entity 字段必须加 `@TableField(fill = FieldFill.INSERT)` |
| 5 | **BaseEntity 有 remark + create_by/update_by/create_time/update_time** | 建表列不能漏 |
| 6 | **MP 逻辑删除 @TableLogic + del_flag 列** | 自动处理，业务代码不手动 set |
| 7 | **TenantLine 自动加 tenant_id 过滤** | wrapper 里不显式加 tenantId 条件 |
| 8 | **tenantId=0 时 TenantLine 让位** | 超级管理员绕过租户隔离 |
| 9 | **SysUserController 和 TenantUserController 接口必须镜像** | 租户登录路由前缀自动切 /tenant，缺接口会被 /{id} 贪婪匹配吞掉 |
| 10 | **OrgNodeVO 字段**：nodeId / nodeType / label / deptId / tenantId — 没有 id 字段！ | el-tree-select 的 node-key 和 props.value 必须用 nodeId / deptId |

### 6.2 前端 + bpmn-js 约束

| # | 约束 | 影响 |
|---|------|------|
| 11 | **Vue 3 存 bpmn-js 原生对象必须 shallowRef + markRaw** | 不然 Proxy 炸 |
| 12 | **bpmn-js 不支持 keyboard.bindTo** | v17+ 隐式绑定，删掉即可 |
| 13 | **bpmn-i18n-zh 包 ESM 导出有问题** | 内联 translate 函数更可控 |
| 14 | **moddle.createAny() 创建 cyan:* 扩展** | moddle.create() 只认已注册 descriptor 的类型；手动 JSON 没 $descriptor 崩 .isGeneric |
| 15 | **moddle 类型名首字母大写** | ExtensionElements（不是 extensionElements）、FormalExpression（不是 tFormalExpression） |
| 16 | **嵌套子元素存 $children** | 普通属性被 XML 序列化当 attribute → `[object Object]` |
| 17 | **extensionElements 必须是 moddle 真对象** | 不能手动 JSON 构建，要用 `moddle.create('bpmn:ExtensionElements')` |
| 18 | **空值属性必须 clean 过滤** | 写入 undefined → XML 里变成脏 `value="undefined"` attribute |
| 19 | **remote el-select 回显必须注入 options** | Element Plus 只有 options 里有对应项才显示 label，打开流程时 options 为空 → 显示 ID |
| 20 | **岗位 / 用户 el-select 必须有 @change** | 要从 postList / userOptions 查 label 写 item.name，否则 XML 里 name 永远是空 |
| 21 | **bpmn.io logo 隐藏写在非 scoped style** | .bjs-powered-by 是 bpmn-js 动态生成的 DOM，不在 scoped 作用域内 |
，designer 等前端路由走 SPA fallback | 大杂烩正则会把前端路由抢走 → 404 |
| 23 | **Redis 缓存刷新**：sys_menu 改了后清 rbac:* | 不然菜单不显示 |
| 24 | **bpmn 标准类型用 body 属性，generic 扩展用 $body** | FormalExpression/ExtensionElements 等 moddle.create() 创建的类型用 body；cyan:* createAny() 用 $body。交叉用会被序列化成 attribute |
| 25 | **Modeler API 拿 Definitions**：`modeler.get('canvas').getRootElement().businessObject.$parent` | 没有 modeler.getRootElement()；canvas.getRootElement() 返回 Process 图形元素，.businessObject.$parent 才是 Definitions |
| 26 | **readElementProps 顺序**：标准属性（conditionExpression）必须在 `if (!bo.extensionElements) return` 之前处理 | extensionElements 不存在不代表没有 conditionExpression，早退会挡掉 |
| 27 | **流程级 formRef 存 definitions.extensionElements** | 不是 process.extensionElements；整个 BPMN XML 只有一个 definitions 根元素 |
| 28 | **流程 Key 自动生成**：后端 UUID 去横线前 16 位 + `bpmn_` 前缀 | 前端不传 processKey，后端 Controller.add() 强制 setProcessKey(null) 后生成；用户无感 |

---

## 7. 验收 Checklist

- [x] npm run build ✅
- [x] mvn clean compile ✅
- [x] 菜单可见（侧边栏工作流下）
- [x] 画布拖拽正常（Start/End/UserTask/排他网关/并行网关/连线）
- [x] 工具栏 + 右键菜单汉化
- [x] 部门树选择（/system/org/tree）
- [x] 岗位下拉（/system/post/all）
- [x] 用户搜索（/system/user/search）
- [x] **保存 → 后端 → 拉取 → 还原 extensionElements** ✅ 2026-11-17 修复
- [x] **导出 .bpmn 文件** ✅
- [x] **隔离验证**（老 /workflow/designer 不受影响） ✅
- [x] **租户隔离**（不同租户数据互不可见） ✅
- [x] **流程 Key 自动生成**（UUID `bpmn_` 前缀，前端无感） ✅ 2026-11-18
- [x] **关联表单**（definitions.extensionElements.cyan:formRef） ✅ 2026-11-18
- [x] **分支条件可视化编辑器**（三列：字段/操作符/值 → 自动拼 SpEL → FormalExpression.body） ✅ 2026-11-18
- [x] **conditionExpression 读写回显**（body 属性，不是 $body） ✅ 2026-11-18
- [x] **生产环境硬刷新 404 修复**（nginx 正则精准 proxy 真实 API） ✅ 2026-11-18

---

## 8. 保存链路完整修复复盘（2026-11-17）

### 8.1 修复前的症状

```
保存前修改了审批人 → 点保存 → 接口成功返回 → 
拉取回来的 XML 里：
  <cyan:assignees assignee="[object Object]" />
  ← assignee 数组被当普通属性序列化成字符串了！
  
  <cyan:signType value="undefined">OR</cyan:signType>
  ← undefined 也被序列化成脏 attribute 了
  
重新 importXML → 属性面板全部空的 → 保存的数据全是脏的
```

### 8.2 修复过程（3 次迭代 + 源码分析）

```
第 1 次：moddle.create('cyan:assignee') 
  → unknown type "cyan:assignee" ❌
  根因：cyan 没注册 moddle descriptor

第 2 次：手动 JSON { $type: 'cyan:assignee', ... }
  → writer 序列化时崩：Cannot read properties of undefined (reading 'isGeneric') ❌
  根因：bpmn-moddle 源码里，writer.writeAttribute() 会检查 node.$descriptor.isGeneric
        手动 JSON 没有 $descriptor → undefined.isGeneric → 崩

第 3 次：moddle.createAny('cyan:assignee', nsURI, props) ✅
  → 自动构建带 { isGeneric: true } 的 $descriptor
  → writer 走 generic 序列化路径（parseGenericNsAttributes + parseGenericContainments）
  → 嵌套子元素必须放 $children
  → 文本内容必须放 $body
```

### 8.3 核心坑点总结

| 坑 | 错误写法 | 正确写法 |
|----|---------|----------|
| cyan:* 对象创建 | `moddle.create('cyan:xxx')` 或手动 JSON | `moddle.createAny('cyan:xxx', NS, props)` |
| 嵌套 assignee 子元素 | `{ assignee: [child1, child2] }` | `{ $children: [child1, child2] }` |
| 文本内容节点 | `{ value: 'OR' }` | `{ $body: 'OR' }` |
| ExtensionElements 创建 | 手动 JSON 或 camelCase 类型名 | `moddle.create('bpmn:ExtensionElements')` |
| FormalExpression 创建 | `moddle.create('bpmn:tFormalExpression')` | `moddle.create('bpmn:FormalExpression')` |
| FormalExpression 内容属性 | `{ $body: expr }` | `{ body: expr, language: 'spEL' }`（标准类型用 body，不是 $body） |
| bpmn:Definitions 获取 | `modeler.getRootElement()`（不存在） | `modeler.get('canvas').getRootElement().businessObject.$parent`（Process → $parent → Definitions） |
| formRef 位置 | 写到 process.extensionElements（不对） | 写到 **definitions.extensionElements**（流程级，全局唯一） |
| formRef 属性名 | `rootExtensionElements`（瞎编的） | `extensionElements`（bpmn 标准属性名） |
| Nginx SPA fallback | 大杂烩正则 `^/workflow/bpmn/(.*)一、BPMN 流程设计器 — 开发复盘与技术沉淀
markdown



# BPMN 可视化流程设计器 · 开发复盘文档

> 阶段：第一阶段 · 只做编辑器（不接运行引擎）
> 状态：✅ 核心链路已跑通（2026-11-17 修复完成，详见 §8 完整复盘）

---

## 1. 项目架构总览

### 1.1 隔离原则（第一优先级，绝对不能破坏）

现有两套流程定义**完全平行、解耦**：

| 维度 | 自研配置式（老） | BPMN 可视化（新） |
|------|----------------|-----------------|
| 前端路由 | `/system/wf/definition` + `/workflow/designer` | `/workflow/bpmn/definition` + `/workflow/bpmn/designer` |
| 后端路径 | `/workflow/definition` | `/workflow/bpmn/definition` |
| 数据库表 | `wf_process_definition` | `wf_bpmn_definition` |
| 菜单 sys_menu | route_path=`wfDefinition`, parent_id=65 | route_path=`bpmn/definition`, parent_id=65 |
| 后端包结构 | `com.cyan.modules.workflow` | `com.cyan.modules.workflow.bpmn`（子包，零 import 老代码） |
| 数据格式 | 节点 JSON 配置 | BPMN 2.0 XML + 自定义 extensionElements |

**约束：本期不做两套数据互转、不引入 Flowable/Activiti、不改现有 workflow 模块任何代码。**

### 1.2 技术栈

- 前端：Vue 3 + Element Plus + Vite 5.4.21 + bpmn-js 18.16.1
- 后端：Spring Boot + MyBatis-Plus + Java 17 + MariaDB/MySQL
- 组织架构接口：复用 `/system/org/tree`、`/system/post/all`、`/system/user/search`

### 1.3 文件清单

**后端（5 个新文件，全部新建在 `com.cyan.modules.workflow.bpmn` 包下）：**
cyanbase-backend/src/main/java/com/cyan/modules/workflow/bpmn/ ├── entity/WfBpmnDefinition.java ← @TableName("wf_bpmn_definition") ├── mapper/WfBpmnDefinitionMapper.java ← extends BaseMapper，@Mapper ├── service/WfBpmnDefinitionService.java ├── service/impl/WfBpmnDefinitionServiceImpl.java └── controller/WfBpmnDefinitionController.java ← @RequestMapping("/workflow/bpmn/definition")

text




**前端（4 个新文件 + 2 处修改）：**
cyanbase-frontend/src/ ├── api/bpmnDefinition.js ← 新建 ├── api/user.js ← 修改：追加 searchUsersApi（/user/search 路由镜像） ├── api/post.js ← 已有，getAllPost() 直接用 ├── api/org.js ← 已有，getOrgTree() 直接用 ├── router/index.js ← 修改：追加两条路由 └── views/workflow/bpmn/ ├── list/index.vue ← 新建：列表页 └── designer/ ├── index.vue ← 新建：可视化设计器（核心） ├── cyan-moddle.json ← ❌ 已废弃，不要创建 └── i18n/translate.js ← 新建：内联汉化函数

text




**数据库：**

- 建表 SQL：见后端 Entity 对应 DDL
- sys_menu 注册：parent_id=65（工作流管理），menu_type=2，route_path=`bpmn/definition`
- sys_tenant_menu：批量授权给所有 tenant

### 1.4 sys_menu 路由拼接机制（关键隐性约束）

sys_menu.route_path **不是** Vue Router 的 path，而是由 Layout 组件的 `getPath` 函数拼接：

```javascript
// Layout 里的路由拼接逻辑（已废弃但记录在案） const routeMap = { 'wfDefinition': '/system/wf/definition', // ... }

// 现有菜单"工作流管理"(id=65) → parentPath = 'workflow' // sys_menu.route_path = 'bpmn/definition' // → 拼接结果 = '/workflow/bpmn/definition' ✅ 正好等于 Vue Router path

text




所以：sys_menu.route_path 填 `bpmn/definition`，Layout 自动拼成 `/workflow/bpmn/definition`，**和 Vue Router 硬编码的 path 一致**。

---

## 2. 数据库表结构

```sql
CREATE TABLE wf_bpmn_definition ( id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL COMMENT 'TenantMetaObjectHandler 自动填充', process_name VARCHAR(200) NOT NULL, process_key VARCHAR(100) NOT NULL, description VARCHAR(500) COMMENT '流程描述', category VARCHAR(100) COMMENT '流程分类', version INT NOT NULL DEFAULT 1, business_type VARCHAR(50) COMMENT '预留', status TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布', bpmn_xml LONGTEXT NOT NULL COMMENT 'BPMN 2.0 XML（含 extensionElements）', bpmn_viewport TEXT COMMENT '画布视口 JSON（可选）', del_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) COMMENT '⚠️ BaseEntity 有此字段，必须建', create_by VARCHAR(50), create_time DATETIME, update_by VARCHAR(50), update_time DATETIME, PRIMARY KEY (id), UNIQUE KEY uk_process_key_version (process_key, version), KEY idx_tenant (tenant_id) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

text




**⚠️ 关键：必须有 `remark` 字段！** 因为 `BaseEntity` 里有 `remark` 属性，MP 会自动 SELECT，如果漏建会报 `Unknown column 'remark' in 'SELECT'`。

---

## 3. 踩坑日志（按时间顺序）

### Pitfall 1：Entity tenantId 没有 fill 注解 → INSERT 炸

**现象：** Field 'tenant_id' doesn't have a default value  
**根因：** `TenantMetaObjectHandler` 的 `strictInsertFill` 内部有检查——如果目标字段**没有** `@TableField(fill = FieldFill.INSERT)`，**静默跳过填充**，不报错、不警告。  
**修复：** Entity 的 tenantId 字段加上：
```java
@TableField(value = "tenant_id", fill = FieldFill.INSERT) private Long tenantId;

text




**额外知识点：** 还有一条兜底链路 `TenantLineInnerInterceptor`（SQL 拦截器层），当 tenantId > 0 时自动改写 SQL 追加 tenant_id 值。但 tenantId=0（超级管理员）时拦截器让位给实体层，这时没 fill 注解就炸了。

### Pitfall 2：BaseEntity 有 remark 字段 → 建表漏列

**现象：** Unknown column 'remark' in 'SELECT'  
**根因：** `BaseEntity` 继承来的属性，MP 会自动 SELECT。建表时忘了加 `remark` 列。  
**修复：** ALTER TABLE ADD remark VARCHAR(500)，或 Entity 加 `@TableField(exist = false)` 排除。

### Pitfall 3：@MapperScan 不覆盖新子包 → Mapper 找不到

**现象：** No qualifying bean of type `WfBpmnDefinitionMapper`  
**根因：** `@MapperScan` 扫 `com.cyan` 下的所有包，但新增的 `bpmn` 子包路径太深时可能被某些过滤规则跳过？实际原因是启动类的 scanBasePackages 没覆盖。  
**修复：** 确认 `@MapperScan("com.cyan")` 能覆盖到 `com.cyan.modules.workflow.bpmn.mapper`。

### Pitfall 4：bpmn-js v17+ 不支持 keyboard.bindTo

**现象：** Keyboard binding is now implicit  
**根因：** bpmn-js v17 底层 diagram-js 做了改动，键盘绑定改成隐式自动绑定到 document。显式传 `keyboard: { bindTo: ... }` 就警告并丢弃。  
**修复：** Modeler 构造参数里删掉 `keyboard: { bindTo: document }`。

### Pitfall 5：空 BPMN 模板 XML 闭合标签 typo

**现象：** Error: unparsable content `</di:BPMNEdge>` detected / closing tag mismatch  
**根因：** 手敲模板 XML 时，两个 `<bpmndi:BPMNEdge>` 的闭合标签写成了 `</di:BPMNEdge>`。BPMN XML 对命名空间前缀严格敏感——`<bpmndi:BPMNEdge>` 和 `</di:BPMNEdge>` 被认为是完全不同的标签。  
**修复：** 统一用 `<bpmndi:BPMNEdge>...</bpmndi:BPMNEdge>`，根元素显式声明 `xmlns:di`。

### Pitfall 6：bpmn-js-i18n-zh 包 ESM 默认导出失败

**现象：** "default" is not exported by `bpmn-js-i18n-zh`  
**根因：** 该包是 CJS 导出，Rollup/Vite 的 ESM 默认导入解析失败。  
**修复：** **换方案——内联 translate 函数**。bpmn-js 的 translate 机制本质上就是 `(key, interpolations) => string` 函数，自己维护一个 CN_MAP 字典完全 OK，还更可控。文件位置：`views/workflow/bpmn/designer/i18n/translate.js`。

### Pitfall 7：Vue 3 Proxy 和 bpmn-js 原生对象属性冲突

**现象：** `TypeError: 'get' on proxy: property 'labels' is a read-only and non-configurable data property on the proxy target but the proxy did not return its actual value`  
**根因：** Vue 3 的 Proxy 对 bpmn-js 原生 element 对象做**深度代理**，但 bpmn-js 的元素对象有自定义 getter（`.labels` 返回 Array）、部分属性 `configurable: false`、内部有 `Array.isArray(el.labels)` 类型检查。Proxy 拦截后返回值和原始 descriptor 声明不一致。  
**修复：** 存 bpmn-js 原生对象的 ref 必须：
```javascript
const selectedElement = shallowRef(null) // shallowRef：.value 不深代理 // 赋值时 selectedElement.value = markRaw(el) // markRaw：显式标记跳过代理

text




### Pitfall 8（严重，连锁 3 炸）：cyan:* 自定义扩展到底怎么创建？

> 2026-11-17 修复，挖了 bpmn-moddle 源码才搞清楚。经历了 3 次方案迭代才找到正确答案。

**现象链：**
```
第 1 次：moddle.create('cyan:assignee') → unknown type "cyan:assignee"（没注册 descriptor）
    ↓
第 2 次：注册 cyan-moddle.json descriptor → 更严重：unknown type "bpmn:extensionElements"
         （descriptor 格式写错 + 注册机制破坏了内置类型查找）
    ↓
第 3 次：手动 JSON 构建 { $type: 'cyan:assignee', ... }
         → Cannot read properties of undefined (reading 'isGeneric')
         （writer 序列化时要读 $descriptor，手动 JSON 没有 $descriptor）
    ↓
第 4 次：moddle.createAny('cyan:assignee', CYAN_NS_URI, props) → ✅ 终于对了！
```

**根因（bpmn-moddle 源码级约束）：**

| API | 适用范围 | 要求 |
|-----|---------|------|
| `moddle.create('xxx')` | **已注册 descriptor 的类型** | 内置 BPMN 类型 + 你手动注册的扩展类型 |
| `moddle.createAny('prefix:name', nsURI, props)` | **未注册的未知扩展类型** | 自动构建 `{ isGeneric: true }` 的 `$descriptor`，writer 走 generic 序列化路径 |
| 手动 JSON `{ $type, ... }` | ❌ 不行 | 没有 `$descriptor`，writer 崩在 `.isGeneric` |

**修复：** 统一用 `moddle.createAny()` 创建 cyan:* 扩展：
```javascript
const CYAN_NS_URI = 'http://cyan.org/bpmn-ext'
function makeCyanAny(moddle, localName, props) {
  // 空值过滤：undefined/null/空字符串 不写入
  const clean = {}
  for (const [k, v] of Object.entries(props)) {
    if (k === '$children') clean[k] = v
    else if (k === '$body' && v) clean[k] = String(v)
    else if (v !== undefined && v !== null && v !== '') clean[k] = String(v)
  }
  return moddle.createAny('cyan:' + localName, CYAN_NS_URI, clean)
}

// 内置 BPMN 类型（extensionElements / FormalExpression）还是用 moddle.create()
moddle.create('bpmn:ExtensionElements')       // ✅ 已注册
moddle.create('bpmn:FormalExpression', { $body: expr })  // ✅ 已注册
```

**关键：** `cyan-moddle.json` descriptor 文件**完全不需要**，删掉它！

### Pitfall 9：TenantUserController 缺 /search 路由 → 租户登录时用户搜索炸

**现象：** `/tenant/user/search` 返回 `code:10005, "参数类型错误: id 需要 Long 类型"`  
**根因：** 两套用户接口（SysUserController /tenant/user 和 TenantUserController /tenant/user）。`SysUserController` 有 `/search` 但 `TenantUserController` 没有。租户登录时前端路由前缀自动切 `/tenant`，请求 `/tenant/user/search` 被 TenantUserController 的 `@GetMapping("/{id}")` 贪婪匹配吞了，`"search"` 字符串转 Long 失败。  
**修复：** 给 TenantUserController 也加一套 `/search` 路由镜像，和 SysUserController 的 searchUsers 保持一致。TenantLine 拦截器自动隔离 tenantId，wrapper 里不显式加。

### Pitfall 10：用户搜索下拉 label 写错

**现象：** 下拉选项显示 `undefined()`  
**根因：** 后端 `/system/user/search` 返回精简结构 `{id, label: "昵称(用户名)"}`，是专门给工作流下拉用的。前端却按完整用户对象解构 `u.userName` + `u.nickName`。  
**修复：** el-option 直接用后端返回的 `u.label`。

### Pitfall 11（严重）：el-tree-select 的 node-key / props.value 字段名写错

**现象：** 选了部门不回显 + 展开时所有节点被误判高亮为选中态  
**根因：** 后端 `/system/org/tree` 返回的 `OrgNodeVO` 里**根本没有 `id` 字段**！实际字段：
```
nodeId:  "platform-root" / "tenant-1" / "dept-6"    ← 唯一标识
nodeType: "platform-root" / "tenant" / "dept"
deptId:  6                                          ← 只有 dept 节点有
label:   "人力资源部"
```
但模板写的是 `node-key="id"` + `props.value="id"` → el-tree-select 找不到 key → 无法匹配选中态。  
**修复：**
```html
<el-tree-select
  node-key="nodeId"                              ← 唯一标识
  :props="{ value: 'deptId', disabled: 'nodeDisabled', ... }"  ← deptId 才是部门 ID
/>
```
加载 deptTree 后还要递归给非 dept 节点（platform-root / tenant）加 `nodeDisabled: true`。

### Pitfall 12：岗位 / 用户 el-select 缺 @change → item.name 永远是空字符串

**现象：** 选中岗位或用户后，保存成功，但 XML 里 `name=""`，下次打开 el-select 显示原始 ID 而不是名字  
**根因：** 部门有 `@change="(v) => { item.name = findDeptName(v) }"`，但岗位和用户的 el-select **都没有 @change 来写 item.name**！  
→ 保存到 XML 时 `name: a.name || ''` → name 为空
→ 读回来注入 userOptions 条件 `a.value && a.name` → name 为空 → 跳过注入
→ userOptions 无该选项 → el-select 匹配不到 → 显示原始 value "15" 而不是 "苏振邦"  
**修复：** 给岗位和用户都补 @change：
```html
<!-- 岗位 -->
@change="(v) => { const p = postList.find(x => String(x.id) === String(v)); item.name = p ? p.postName : '' }"
<!-- 用户 -->
@change="(v) => { const u = userOptions.find(x => String(x.id) === String(v)); item.name = u ? u.label : '' }"
```

### Pitfall 13：remote el-select 回显需要 options 里有对应项

**现象：** 打开已保存流程，用户类型审批人显示 ID 而不是名字（即使 name 在 XML 里有值）  
**根因：** Element Plus 的 remote el-select **只有在 options 数组里能找到 value 对应的 option 才会显示 label**。打开流程时 userOptions 是空的（还没搜索过），所以匹配不到。  
**修复：** 在 `readElementProps` 里把已保存的 user 选项注入 userOptions（unshift 放最前）。注入条件**不能依赖 a.name**（兼容老脏数据 name 为空的情况）。

---

## 4. 自定义属性存储方案（extensionElements 设计）

### 4.1 Schema（2026-11-17 修正确认）

```xml
<!-- UserTask 示例：审批人 + 会签类型 -->
<bpmn:userTask id="UserTask_1" name="部门经理审批">
  <bpmn:extensionElements>
    <cyan:assignees>
      <cyan:assignee type="dept" value="6"  name="人力资源部" />
      <cyan:assignee type="post" value="15" name="部门经理" />
      <cyan:assignee type="user"  value="7"  name="张三" />
    </cyan:assignees>
    <cyan:signType>OR</cyan:signType>
  </bpmn:extensionElements>
</bpmn:userTask>

<!-- SequenceFlow 示例：条件表达式 + 描述 -->
<bpmn:sequenceFlow id="Flow_1" sourceRef="UserTask_1" targetRef="UserTask_2">
  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">amount >= 100000</bpmn:conditionExpression>
  <bpmn:extensionElements>
    <cyan:desc>大额审批</cyan:desc>
  </bpmn:extensionElements>
</bpmn:sequenceFlow>
```

**cyan:assignee.type 取值：** `initiator` / `dept` / `post` / `user`
**cyan:signType 取值：** `OR`（或签） / `AND`（会签）

### 4.2 读写方式（2026-11-17 修复定稿：混合策略）

**核心原则：** BPMN 内置类型和 cyan:* 自定义扩展走不同路径，绝不能混淆。

```
bpmn:extensionElements / bpmn:FormalExpression  → moddle.create() （已注册 descriptor）
cyan:assignee / cyan:assignees / cyan:signType   → moddle.createAny() （未注册，generic 元素）
```

**❌ 三个绝对不能碰的坑：**

| 错误 | 后果 | 原因 |
|------|------|------|
| `moddle.create('cyan:xxx')` | unknown type | cyan 没注册 descriptor |
| 手动 JSON `{ $type: 'cyan:xxx' }` | 崩在 `.isGeneric` | writer 需要 `$descriptor`，手动 JSON 没有 |
| cyan:assignees 的子 assignee 存成普通属性 `{ assignee: [...] }` | XML 里变成 `assignee="[object Object]"` | 普通属性被当 XML attribute，嵌套子元素必须存 `$children` |

**✅ 正确写入示例（persistElementProps 核心逻辑）：**
```javascript
// 1. cyan:assignees（容器）+ cyan:assignee（子元素）
const children = assignees.map(a =>
  makeCyanAny(moddle, 'assignee', { type: a.type, value: a.value, name: a.name })
)
// ⭐ 嵌套子元素放 $children，不是普通属性 assignee
newExts.push(makeCyanAny(moddle, 'assignees', { $children: children }))

// 2. cyan:signType / cyan:desc（文本内容节点）
newExts.push(makeCyanAny(moddle, 'signType', { $body: 'OR' }))

// 3. cyan:* 作为 extensionElements.values 的子节点
extEls = moddle.create('bpmn:ExtensionElements')  // 内置类型，用 create()
// splice 原地替换，不要替换 values 数组引用
extEls.values.splice(0, extEls.values.length, ...filteredOld, ...newExts)

// 4. conditionExpression（BPMN 内置类型，独立处理）
modeling.updateModdleProperties(el, bo, {
  conditionExpression: moddle.create('bpmn:FormalExpression', { $body: expr })
})
```

**✅ 正确读取（importXML 后自动反序列化）：**
```javascript
// importXML 时 bpmn-js 自动把 <cyan:xxx> 反序列化成带 $descriptor 的 moddle 对象
// values[i].$type === 'cyan:assignee'
// values[i].$children → 子 assignee 数组（自动序列化嵌套）
// values[i].$body → 文本节点内容（<cyan:signType>OR</cyan:signType> → $body='OR'）
// values[i].type / value / name → XML attributes
```




---

## 5. 后端接口清单

| Method | URL | 权限 | 说明 |
|--------|-----|------|------|
| GET | `/workflow/bpmn/definition/list` | workflow:bpmn:list | 分页列表 |
| GET | `/workflow/bpmn/definition/{id}` | workflow:bpmn:list | 详情（含 bpmn_xml） |
| GET | `/workflow/bpmn/definition/{id}/xml` | workflow:bpmn:list | 仅 XML |
| POST | `/workflow/bpmn/definition` | workflow:bpmn:edit | 新增（key+version 唯一性校验） |
| PUT | `/workflow/bpmn/definition` | workflow:bpmn:edit | 更新 |
| DELETE | `/workflow/bpmn/definition/{id}` | workflow:bpmn:delete | 逻辑删除 |
| PUT | `/workflow/bpmn/definition/publish/{id}` | workflow:bpmn:publish | 发布（0→1，只标记状态） |

**复用接口（不新建）：**

| 接口 | 用途 |
|------|------|
| `GET /system/org/tree` | 属性面板部门树 |
| `GET /system/post/all` | 属性面板岗位下拉 |
| `GET /system/user/search?keyword=xxx` | 属性面板用户搜索（前后端各一套镜像） |

---

## 6. 全局隐性约束清单

### 6.1 后端约束

| # | 约束 | 影响 |
|---|------|------|
| 1 | **sys_menu 的 menu_type 是 tinyint**：1=目录 2=菜单 3=按钮 | SQL 不能写 'C'/'F' |
| 2 | **sys_menu 的 sort_num 不是 sort_order** | 字段名别写错 |
| 3 | **sys_menu.route_path ≠ Vue Router path** | Layout 的 getPath 做 parentPath+route_path 拼接 |
| 4 | **TenantMetaObjectHandler 只填有 fill 注解的字段** | Entity 字段必须加 `@TableField(fill = FieldFill.INSERT)` |
| 5 | **BaseEntity 有 remark + create_by/update_by/create_time/update_time** | 建表列不能漏 |
| 6 | **MP 逻辑删除 @TableLogic + del_flag 列** | 自动处理，业务代码不手动 set |
| 7 | **TenantLine 自动加 tenant_id 过滤** | wrapper 里不显式加 tenantId 条件 |
| 8 | **tenantId=0 时 TenantLine 让位** | 超级管理员绕过租户隔离 |
| 9 | **SysUserController 和 TenantUserController 接口必须镜像** | 租户登录路由前缀自动切 /tenant，缺接口会被 /{id} 贪婪匹配吞掉 |
| 10 | **OrgNodeVO 字段**：nodeId / nodeType / label / deptId / tenantId — 没有 id 字段！ | el-tree-select 的 node-key 和 props.value 必须用 nodeId / deptId |

### 6.2 前端 + bpmn-js 约束

| # | 约束 | 影响 |
|---|------|------|
| 11 | **Vue 3 存 bpmn-js 原生对象必须 shallowRef + markRaw** | 不然 Proxy 炸 |
| 12 | **bpmn-js 不支持 keyboard.bindTo** | v17+ 隐式绑定，删掉即可 |
| 13 | **bpmn-i18n-zh 包 ESM 导出有问题** | 内联 translate 函数更可控 |
| 14 | **moddle.createAny() 创建 cyan:* 扩展** | moddle.create() 只认已注册 descriptor 的类型；手动 JSON 没 $descriptor 崩 .isGeneric |
| 15 | **moddle 类型名首字母大写** | ExtensionElements（不是 extensionElements）、FormalExpression（不是 tFormalExpression） |
| 16 | **嵌套子元素存 $children** | 普通属性被 XML 序列化当 attribute → `[object Object]` |
| 17 | **extensionElements 必须是 moddle 真对象** | 不能手动 JSON 构建，要用 `moddle.create('bpmn:ExtensionElements')` |
| 18 | **空值属性必须 clean 过滤** | 写入 undefined → XML 里变成脏 `value="undefined"` attribute |
| 19 | **remote el-select 回显必须注入 options** | Element Plus 只有 options 里有对应项才显示 label，打开流程时 options 为空 → 显示 ID |
| 20 | **岗位 / 用户 el-select 必须有 @change** | 要从 postList / userOptions 查 label 写 item.name，否则 XML 里 name 永远是空 |
| 21 | **bpmn.io logo 隐藏写在非 scoped style** | .bjs-powered-by 是 bpmn-js 动态生成的 DOM，不在 scoped 作用域内 |
| 22 | **Nginx 反代 regex location**：`/workflow/bpmn/(definition|designer)` | 不能被 `/workflow/` 通用 location 吞掉 |
| 23 | **Redis 缓存刷新**：sys_menu 改了后清 rbac:* | 不然菜单不显示 |

---

## 7. 验收 Checklist

- [x] npm run build ✅
- [x] mvn clean compile ✅
- [x] 菜单可见（侧边栏工作流下）
- [x] 画布拖拽正常（Start/End/UserTask/排他网关/并行网关/连线）
- [x] 工具栏 + 右键菜单汉化
- [x] 部门树选择（/system/org/tree）
- [x] 岗位下拉（/system/post/all）
- [x] 用户搜索（/system/user/search）
- [x] **保存 → 后端 → 拉取 → 还原 extensionElements** ✅ 2026-11-17 修复
- [x] **导出 .bpmn 文件** ✅
- [x] **隔离验证**（老 /workflow/designer 不受影响） ✅
- [x] **租户隔离**（不同租户数据互不可见） ✅

---

## 8. 保存链路完整修复复盘（2026-11-17）

### 8.1 修复前的症状

```
保存前修改了审批人 → 点保存 → 接口成功返回 → 
拉取回来的 XML 里：
  <cyan:assignees assignee="[object Object]" />
  ← assignee 数组被当普通属性序列化成字符串了！
  
  <cyan:signType value="undefined">OR</cyan:signType>
  ← undefined 也被序列化成脏 attribute 了
  
重新 importXML → 属性面板全部空的 → 保存的数据全是脏的
```

### 8.2 修复过程（3 次迭代 + 源码分析）

```
第 1 次：moddle.create('cyan:assignee') 
  → unknown type "cyan:assignee" ❌
  根因：cyan 没注册 moddle descriptor

第 2 次：手动 JSON { $type: 'cyan:assignee', ... }
  → writer 序列化时崩：Cannot read properties of undefined (reading 'isGeneric') ❌
  根因：bpmn-moddle 源码里，writer.writeAttribute() 会检查 node.$descriptor.isGeneric
        手动 JSON 没有 $descriptor → undefined.isGeneric → 崩

第 3 次：moddle.createAny('cyan:assignee', nsURI, props) ✅
  → 自动构建带 { isGeneric: true } 的 $descriptor
  → writer 走 generic 序列化路径（parseGenericNsAttributes + parseGenericContainments）
  → 嵌套子元素必须放 $children
  → 文本内容必须放 $body
```

### 8.3 核心坑点总结

| 坑 | 错误写法 | 正确写法 |
|----|---------|----------|
| cyan:* 对象创建 | `moddle.create('cyan:xxx')` 或手动 JSON | `moddle.createAny('cyan:xxx', NS, props)` |
| 嵌套 assignee 子元素 | `{ assignee: [child1, child2] }` | `{ $children: [child1, child2] }` |
| 文本内容节点 | `{ value: 'OR' }` | `{ $body: 'OR' }` |
| ExtensionElements 创建 | 手动 JSON 或 camelCase 类型名 | `moddle.create('bpmn:ExtensionElements')` |
 把 designer 也抢走 | 精准 proxy 真实 API `/workflow/bpmn/definition(/.*)?一、BPMN 流程设计器 — 开发复盘与技术沉淀
markdown



# BPMN 可视化流程设计器 · 开发复盘文档

> 阶段：第一阶段 · 只做编辑器（不接运行引擎）
> 状态：✅ 核心链路已跑通（2026-11-17 修复完成，详见 §8 完整复盘）

---

## 1. 项目架构总览

### 1.1 隔离原则（第一优先级，绝对不能破坏）

现有两套流程定义**完全平行、解耦**：

| 维度 | 自研配置式（老） | BPMN 可视化（新） |
|------|----------------|-----------------|
| 前端路由 | `/system/wf/definition` + `/workflow/designer` | `/workflow/bpmn/definition` + `/workflow/bpmn/designer` |
| 后端路径 | `/workflow/definition` | `/workflow/bpmn/definition` |
| 数据库表 | `wf_process_definition` | `wf_bpmn_definition` |
| 菜单 sys_menu | route_path=`wfDefinition`, parent_id=65 | route_path=`bpmn/definition`, parent_id=65 |
| 后端包结构 | `com.cyan.modules.workflow` | `com.cyan.modules.workflow.bpmn`（子包，零 import 老代码） |
| 数据格式 | 节点 JSON 配置 | BPMN 2.0 XML + 自定义 extensionElements |

**约束：本期不做两套数据互转、不引入 Flowable/Activiti、不改现有 workflow 模块任何代码。**

### 1.2 技术栈

- 前端：Vue 3 + Element Plus + Vite 5.4.21 + bpmn-js 18.16.1
- 后端：Spring Boot + MyBatis-Plus + Java 17 + MariaDB/MySQL
- 组织架构接口：复用 `/system/org/tree`、`/system/post/all`、`/system/user/search`

### 1.3 文件清单

**后端（5 个新文件，全部新建在 `com.cyan.modules.workflow.bpmn` 包下）：**
cyanbase-backend/src/main/java/com/cyan/modules/workflow/bpmn/ ├── entity/WfBpmnDefinition.java ← @TableName("wf_bpmn_definition") ├── mapper/WfBpmnDefinitionMapper.java ← extends BaseMapper，@Mapper ├── service/WfBpmnDefinitionService.java ├── service/impl/WfBpmnDefinitionServiceImpl.java └── controller/WfBpmnDefinitionController.java ← @RequestMapping("/workflow/bpmn/definition")

text




**前端（4 个新文件 + 2 处修改）：**
cyanbase-frontend/src/ ├── api/bpmnDefinition.js ← 新建 ├── api/user.js ← 修改：追加 searchUsersApi（/user/search 路由镜像） ├── api/post.js ← 已有，getAllPost() 直接用 ├── api/org.js ← 已有，getOrgTree() 直接用 ├── router/index.js ← 修改：追加两条路由 └── views/workflow/bpmn/ ├── list/index.vue ← 新建：列表页 └── designer/ ├── index.vue ← 新建：可视化设计器（核心） ├── cyan-moddle.json ← ❌ 已废弃，不要创建 └── i18n/translate.js ← 新建：内联汉化函数

text




**数据库：**

- 建表 SQL：见后端 Entity 对应 DDL
- sys_menu 注册：parent_id=65（工作流管理），menu_type=2，route_path=`bpmn/definition`
- sys_tenant_menu：批量授权给所有 tenant

### 1.4 sys_menu 路由拼接机制（关键隐性约束）

sys_menu.route_path **不是** Vue Router 的 path，而是由 Layout 组件的 `getPath` 函数拼接：

```javascript
// Layout 里的路由拼接逻辑（已废弃但记录在案） const routeMap = { 'wfDefinition': '/system/wf/definition', // ... }

// 现有菜单"工作流管理"(id=65) → parentPath = 'workflow' // sys_menu.route_path = 'bpmn/definition' // → 拼接结果 = '/workflow/bpmn/definition' ✅ 正好等于 Vue Router path

text




所以：sys_menu.route_path 填 `bpmn/definition`，Layout 自动拼成 `/workflow/bpmn/definition`，**和 Vue Router 硬编码的 path 一致**。

---

## 2. 数据库表结构

```sql
CREATE TABLE wf_bpmn_definition ( id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL COMMENT 'TenantMetaObjectHandler 自动填充', process_name VARCHAR(200) NOT NULL, process_key VARCHAR(100) NOT NULL, description VARCHAR(500) COMMENT '流程描述', category VARCHAR(100) COMMENT '流程分类', version INT NOT NULL DEFAULT 1, business_type VARCHAR(50) COMMENT '预留', status TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布', bpmn_xml LONGTEXT NOT NULL COMMENT 'BPMN 2.0 XML（含 extensionElements）', bpmn_viewport TEXT COMMENT '画布视口 JSON（可选）', del_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) COMMENT '⚠️ BaseEntity 有此字段，必须建', create_by VARCHAR(50), create_time DATETIME, update_by VARCHAR(50), update_time DATETIME, PRIMARY KEY (id), UNIQUE KEY uk_process_key_version (process_key, version), KEY idx_tenant (tenant_id) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

text




**⚠️ 关键：必须有 `remark` 字段！** 因为 `BaseEntity` 里有 `remark` 属性，MP 会自动 SELECT，如果漏建会报 `Unknown column 'remark' in 'SELECT'`。

---

## 3. 踩坑日志（按时间顺序）

### Pitfall 1：Entity tenantId 没有 fill 注解 → INSERT 炸

**现象：** Field 'tenant_id' doesn't have a default value  
**根因：** `TenantMetaObjectHandler` 的 `strictInsertFill` 内部有检查——如果目标字段**没有** `@TableField(fill = FieldFill.INSERT)`，**静默跳过填充**，不报错、不警告。  
**修复：** Entity 的 tenantId 字段加上：
```java
@TableField(value = "tenant_id", fill = FieldFill.INSERT) private Long tenantId;

text




**额外知识点：** 还有一条兜底链路 `TenantLineInnerInterceptor`（SQL 拦截器层），当 tenantId > 0 时自动改写 SQL 追加 tenant_id 值。但 tenantId=0（超级管理员）时拦截器让位给实体层，这时没 fill 注解就炸了。

### Pitfall 2：BaseEntity 有 remark 字段 → 建表漏列

**现象：** Unknown column 'remark' in 'SELECT'  
**根因：** `BaseEntity` 继承来的属性，MP 会自动 SELECT。建表时忘了加 `remark` 列。  
**修复：** ALTER TABLE ADD remark VARCHAR(500)，或 Entity 加 `@TableField(exist = false)` 排除。

### Pitfall 3：@MapperScan 不覆盖新子包 → Mapper 找不到

**现象：** No qualifying bean of type `WfBpmnDefinitionMapper`  
**根因：** `@MapperScan` 扫 `com.cyan` 下的所有包，但新增的 `bpmn` 子包路径太深时可能被某些过滤规则跳过？实际原因是启动类的 scanBasePackages 没覆盖。  
**修复：** 确认 `@MapperScan("com.cyan")` 能覆盖到 `com.cyan.modules.workflow.bpmn.mapper`。

### Pitfall 4：bpmn-js v17+ 不支持 keyboard.bindTo

**现象：** Keyboard binding is now implicit  
**根因：** bpmn-js v17 底层 diagram-js 做了改动，键盘绑定改成隐式自动绑定到 document。显式传 `keyboard: { bindTo: ... }` 就警告并丢弃。  
**修复：** Modeler 构造参数里删掉 `keyboard: { bindTo: document }`。

### Pitfall 5：空 BPMN 模板 XML 闭合标签 typo

**现象：** Error: unparsable content `</di:BPMNEdge>` detected / closing tag mismatch  
**根因：** 手敲模板 XML 时，两个 `<bpmndi:BPMNEdge>` 的闭合标签写成了 `</di:BPMNEdge>`。BPMN XML 对命名空间前缀严格敏感——`<bpmndi:BPMNEdge>` 和 `</di:BPMNEdge>` 被认为是完全不同的标签。  
**修复：** 统一用 `<bpmndi:BPMNEdge>...</bpmndi:BPMNEdge>`，根元素显式声明 `xmlns:di`。

### Pitfall 6：bpmn-js-i18n-zh 包 ESM 默认导出失败

**现象：** "default" is not exported by `bpmn-js-i18n-zh`  
**根因：** 该包是 CJS 导出，Rollup/Vite 的 ESM 默认导入解析失败。  
**修复：** **换方案——内联 translate 函数**。bpmn-js 的 translate 机制本质上就是 `(key, interpolations) => string` 函数，自己维护一个 CN_MAP 字典完全 OK，还更可控。文件位置：`views/workflow/bpmn/designer/i18n/translate.js`。

### Pitfall 7：Vue 3 Proxy 和 bpmn-js 原生对象属性冲突

**现象：** `TypeError: 'get' on proxy: property 'labels' is a read-only and non-configurable data property on the proxy target but the proxy did not return its actual value`  
**根因：** Vue 3 的 Proxy 对 bpmn-js 原生 element 对象做**深度代理**，但 bpmn-js 的元素对象有自定义 getter（`.labels` 返回 Array）、部分属性 `configurable: false`、内部有 `Array.isArray(el.labels)` 类型检查。Proxy 拦截后返回值和原始 descriptor 声明不一致。  
**修复：** 存 bpmn-js 原生对象的 ref 必须：
```javascript
const selectedElement = shallowRef(null) // shallowRef：.value 不深代理 // 赋值时 selectedElement.value = markRaw(el) // markRaw：显式标记跳过代理

text




### Pitfall 8（严重，连锁 3 炸）：cyan:* 自定义扩展到底怎么创建？

> 2026-11-17 修复，挖了 bpmn-moddle 源码才搞清楚。经历了 3 次方案迭代才找到正确答案。

**现象链：**
```
第 1 次：moddle.create('cyan:assignee') → unknown type "cyan:assignee"（没注册 descriptor）
    ↓
第 2 次：注册 cyan-moddle.json descriptor → 更严重：unknown type "bpmn:extensionElements"
         （descriptor 格式写错 + 注册机制破坏了内置类型查找）
    ↓
第 3 次：手动 JSON 构建 { $type: 'cyan:assignee', ... }
         → Cannot read properties of undefined (reading 'isGeneric')
         （writer 序列化时要读 $descriptor，手动 JSON 没有 $descriptor）
    ↓
第 4 次：moddle.createAny('cyan:assignee', CYAN_NS_URI, props) → ✅ 终于对了！
```

**根因（bpmn-moddle 源码级约束）：**

| API | 适用范围 | 要求 |
|-----|---------|------|
| `moddle.create('xxx')` | **已注册 descriptor 的类型** | 内置 BPMN 类型 + 你手动注册的扩展类型 |
| `moddle.createAny('prefix:name', nsURI, props)` | **未注册的未知扩展类型** | 自动构建 `{ isGeneric: true }` 的 `$descriptor`，writer 走 generic 序列化路径 |
| 手动 JSON `{ $type, ... }` | ❌ 不行 | 没有 `$descriptor`，writer 崩在 `.isGeneric` |

**修复：** 统一用 `moddle.createAny()` 创建 cyan:* 扩展：
```javascript
const CYAN_NS_URI = 'http://cyan.org/bpmn-ext'
function makeCyanAny(moddle, localName, props) {
  // 空值过滤：undefined/null/空字符串 不写入
  const clean = {}
  for (const [k, v] of Object.entries(props)) {
    if (k === '$children') clean[k] = v
    else if (k === '$body' && v) clean[k] = String(v)
    else if (v !== undefined && v !== null && v !== '') clean[k] = String(v)
  }
  return moddle.createAny('cyan:' + localName, CYAN_NS_URI, clean)
}

// 内置 BPMN 类型（extensionElements / FormalExpression）还是用 moddle.create()
moddle.create('bpmn:ExtensionElements')       // ✅ 已注册
moddle.create('bpmn:FormalExpression', { $body: expr })  // ✅ 已注册
```

**关键：** `cyan-moddle.json` descriptor 文件**完全不需要**，删掉它！

### Pitfall 9：TenantUserController 缺 /search 路由 → 租户登录时用户搜索炸

**现象：** `/tenant/user/search` 返回 `code:10005, "参数类型错误: id 需要 Long 类型"`  
**根因：** 两套用户接口（SysUserController /tenant/user 和 TenantUserController /tenant/user）。`SysUserController` 有 `/search` 但 `TenantUserController` 没有。租户登录时前端路由前缀自动切 `/tenant`，请求 `/tenant/user/search` 被 TenantUserController 的 `@GetMapping("/{id}")` 贪婪匹配吞了，`"search"` 字符串转 Long 失败。  
**修复：** 给 TenantUserController 也加一套 `/search` 路由镜像，和 SysUserController 的 searchUsers 保持一致。TenantLine 拦截器自动隔离 tenantId，wrapper 里不显式加。

### Pitfall 10：用户搜索下拉 label 写错

**现象：** 下拉选项显示 `undefined()`  
**根因：** 后端 `/system/user/search` 返回精简结构 `{id, label: "昵称(用户名)"}`，是专门给工作流下拉用的。前端却按完整用户对象解构 `u.userName` + `u.nickName`。  
**修复：** el-option 直接用后端返回的 `u.label`。

### Pitfall 11（严重）：el-tree-select 的 node-key / props.value 字段名写错

**现象：** 选了部门不回显 + 展开时所有节点被误判高亮为选中态  
**根因：** 后端 `/system/org/tree` 返回的 `OrgNodeVO` 里**根本没有 `id` 字段**！实际字段：
```
nodeId:  "platform-root" / "tenant-1" / "dept-6"    ← 唯一标识
nodeType: "platform-root" / "tenant" / "dept"
deptId:  6                                          ← 只有 dept 节点有
label:   "人力资源部"
```
但模板写的是 `node-key="id"` + `props.value="id"` → el-tree-select 找不到 key → 无法匹配选中态。  
**修复：**
```html
<el-tree-select
  node-key="nodeId"                              ← 唯一标识
  :props="{ value: 'deptId', disabled: 'nodeDisabled', ... }"  ← deptId 才是部门 ID
/>
```
加载 deptTree 后还要递归给非 dept 节点（platform-root / tenant）加 `nodeDisabled: true`。

### Pitfall 12：岗位 / 用户 el-select 缺 @change → item.name 永远是空字符串

**现象：** 选中岗位或用户后，保存成功，但 XML 里 `name=""`，下次打开 el-select 显示原始 ID 而不是名字  
**根因：** 部门有 `@change="(v) => { item.name = findDeptName(v) }"`，但岗位和用户的 el-select **都没有 @change 来写 item.name**！  
→ 保存到 XML 时 `name: a.name || ''` → name 为空
→ 读回来注入 userOptions 条件 `a.value && a.name` → name 为空 → 跳过注入
→ userOptions 无该选项 → el-select 匹配不到 → 显示原始 value "15" 而不是 "苏振邦"  
**修复：** 给岗位和用户都补 @change：
```html
<!-- 岗位 -->
@change="(v) => { const p = postList.find(x => String(x.id) === String(v)); item.name = p ? p.postName : '' }"
<!-- 用户 -->
@change="(v) => { const u = userOptions.find(x => String(x.id) === String(v)); item.name = u ? u.label : '' }"
```

### Pitfall 13：remote el-select 回显需要 options 里有对应项

**现象：** 打开已保存流程，用户类型审批人显示 ID 而不是名字（即使 name 在 XML 里有值）  
**根因：** Element Plus 的 remote el-select **只有在 options 数组里能找到 value 对应的 option 才会显示 label**。打开流程时 userOptions 是空的（还没搜索过），所以匹配不到。  
**修复：** 在 `readElementProps` 里把已保存的 user 选项注入 userOptions（unshift 放最前）。注入条件**不能依赖 a.name**（兼容老脏数据 name 为空的情况）。

---

## 4. 自定义属性存储方案（extensionElements 设计）

### 4.1 Schema（2026-11-17 修正确认）

```xml
<!-- UserTask 示例：审批人 + 会签类型 -->
<bpmn:userTask id="UserTask_1" name="部门经理审批">
  <bpmn:extensionElements>
    <cyan:assignees>
      <cyan:assignee type="dept" value="6"  name="人力资源部" />
      <cyan:assignee type="post" value="15" name="部门经理" />
      <cyan:assignee type="user"  value="7"  name="张三" />
    </cyan:assignees>
    <cyan:signType>OR</cyan:signType>
  </bpmn:extensionElements>
</bpmn:userTask>

<!-- SequenceFlow 示例：条件表达式 + 描述 -->
<bpmn:sequenceFlow id="Flow_1" sourceRef="UserTask_1" targetRef="UserTask_2">
  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">amount >= 100000</bpmn:conditionExpression>
  <bpmn:extensionElements>
    <cyan:desc>大额审批</cyan:desc>
  </bpmn:extensionElements>
</bpmn:sequenceFlow>
```

**cyan:assignee.type 取值：** `initiator` / `dept` / `post` / `user`
**cyan:signType 取值：** `OR`（或签） / `AND`（会签）

### 4.2 读写方式（2026-11-17 修复定稿：混合策略）

**核心原则：** BPMN 内置类型和 cyan:* 自定义扩展走不同路径，绝不能混淆。

```
bpmn:extensionElements / bpmn:FormalExpression  → moddle.create() （已注册 descriptor）
cyan:assignee / cyan:assignees / cyan:signType   → moddle.createAny() （未注册，generic 元素）
```

**❌ 三个绝对不能碰的坑：**

| 错误 | 后果 | 原因 |
|------|------|------|
| `moddle.create('cyan:xxx')` | unknown type | cyan 没注册 descriptor |
| 手动 JSON `{ $type: 'cyan:xxx' }` | 崩在 `.isGeneric` | writer 需要 `$descriptor`，手动 JSON 没有 |
| cyan:assignees 的子 assignee 存成普通属性 `{ assignee: [...] }` | XML 里变成 `assignee="[object Object]"` | 普通属性被当 XML attribute，嵌套子元素必须存 `$children` |

**✅ 正确写入示例（persistElementProps 核心逻辑）：**
```javascript
// 1. cyan:assignees（容器）+ cyan:assignee（子元素）
const children = assignees.map(a =>
  makeCyanAny(moddle, 'assignee', { type: a.type, value: a.value, name: a.name })
)
// ⭐ 嵌套子元素放 $children，不是普通属性 assignee
newExts.push(makeCyanAny(moddle, 'assignees', { $children: children }))

// 2. cyan:signType / cyan:desc（文本内容节点）
newExts.push(makeCyanAny(moddle, 'signType', { $body: 'OR' }))

// 3. cyan:* 作为 extensionElements.values 的子节点
extEls = moddle.create('bpmn:ExtensionElements')  // 内置类型，用 create()
// splice 原地替换，不要替换 values 数组引用
extEls.values.splice(0, extEls.values.length, ...filteredOld, ...newExts)

// 4. conditionExpression（BPMN 内置类型，独立处理）
modeling.updateModdleProperties(el, bo, {
  conditionExpression: moddle.create('bpmn:FormalExpression', { $body: expr })
})
```

**✅ 正确读取（importXML 后自动反序列化）：**
```javascript
// importXML 时 bpmn-js 自动把 <cyan:xxx> 反序列化成带 $descriptor 的 moddle 对象
// values[i].$type === 'cyan:assignee'
// values[i].$children → 子 assignee 数组（自动序列化嵌套）
// values[i].$body → 文本节点内容（<cyan:signType>OR</cyan:signType> → $body='OR'）
// values[i].type / value / name → XML attributes
```




---

## 5. 后端接口清单

| Method | URL | 权限 | 说明 |
|--------|-----|------|------|
| GET | `/workflow/bpmn/definition/list` | workflow:bpmn:list | 分页列表 |
| GET | `/workflow/bpmn/definition/{id}` | workflow:bpmn:list | 详情（含 bpmn_xml） |
| GET | `/workflow/bpmn/definition/{id}/xml` | workflow:bpmn:list | 仅 XML |
| POST | `/workflow/bpmn/definition` | workflow:bpmn:edit | 新增（key+version 唯一性校验） |
| PUT | `/workflow/bpmn/definition` | workflow:bpmn:edit | 更新 |
| DELETE | `/workflow/bpmn/definition/{id}` | workflow:bpmn:delete | 逻辑删除 |
| PUT | `/workflow/bpmn/definition/publish/{id}` | workflow:bpmn:publish | 发布（0→1，只标记状态） |

**复用接口（不新建）：**

| 接口 | 用途 |
|------|------|
| `GET /system/org/tree` | 属性面板部门树 |
| `GET /system/post/all` | 属性面板岗位下拉 |
| `GET /system/user/search?keyword=xxx` | 属性面板用户搜索（前后端各一套镜像） |

---

## 6. 全局隐性约束清单

### 6.1 后端约束

| # | 约束 | 影响 |
|---|------|------|
| 1 | **sys_menu 的 menu_type 是 tinyint**：1=目录 2=菜单 3=按钮 | SQL 不能写 'C'/'F' |
| 2 | **sys_menu 的 sort_num 不是 sort_order** | 字段名别写错 |
| 3 | **sys_menu.route_path ≠ Vue Router path** | Layout 的 getPath 做 parentPath+route_path 拼接 |
| 4 | **TenantMetaObjectHandler 只填有 fill 注解的字段** | Entity 字段必须加 `@TableField(fill = FieldFill.INSERT)` |
| 5 | **BaseEntity 有 remark + create_by/update_by/create_time/update_time** | 建表列不能漏 |
| 6 | **MP 逻辑删除 @TableLogic + del_flag 列** | 自动处理，业务代码不手动 set |
| 7 | **TenantLine 自动加 tenant_id 过滤** | wrapper 里不显式加 tenantId 条件 |
| 8 | **tenantId=0 时 TenantLine 让位** | 超级管理员绕过租户隔离 |
| 9 | **SysUserController 和 TenantUserController 接口必须镜像** | 租户登录路由前缀自动切 /tenant，缺接口会被 /{id} 贪婪匹配吞掉 |
| 10 | **OrgNodeVO 字段**：nodeId / nodeType / label / deptId / tenantId — 没有 id 字段！ | el-tree-select 的 node-key 和 props.value 必须用 nodeId / deptId |

### 6.2 前端 + bpmn-js 约束

| # | 约束 | 影响 |
|---|------|------|
| 11 | **Vue 3 存 bpmn-js 原生对象必须 shallowRef + markRaw** | 不然 Proxy 炸 |
| 12 | **bpmn-js 不支持 keyboard.bindTo** | v17+ 隐式绑定，删掉即可 |
| 13 | **bpmn-i18n-zh 包 ESM 导出有问题** | 内联 translate 函数更可控 |
| 14 | **moddle.createAny() 创建 cyan:* 扩展** | moddle.create() 只认已注册 descriptor 的类型；手动 JSON 没 $descriptor 崩 .isGeneric |
| 15 | **moddle 类型名首字母大写** | ExtensionElements（不是 extensionElements）、FormalExpression（不是 tFormalExpression） |
| 16 | **嵌套子元素存 $children** | 普通属性被 XML 序列化当 attribute → `[object Object]` |
| 17 | **extensionElements 必须是 moddle 真对象** | 不能手动 JSON 构建，要用 `moddle.create('bpmn:ExtensionElements')` |
| 18 | **空值属性必须 clean 过滤** | 写入 undefined → XML 里变成脏 `value="undefined"` attribute |
| 19 | **remote el-select 回显必须注入 options** | Element Plus 只有 options 里有对应项才显示 label，打开流程时 options 为空 → 显示 ID |
| 20 | **岗位 / 用户 el-select 必须有 @change** | 要从 postList / userOptions 查 label 写 item.name，否则 XML 里 name 永远是空 |
| 21 | **bpmn.io logo 隐藏写在非 scoped style** | .bjs-powered-by 是 bpmn-js 动态生成的 DOM，不在 scoped 作用域内 |
| 22 | **Nginx 反代 regex location**：`/workflow/bpmn/(definition|designer)` | 不能被 `/workflow/` 通用 location 吞掉 |
| 23 | **Redis 缓存刷新**：sys_menu 改了后清 rbac:* | 不然菜单不显示 |

---

## 7. 验收 Checklist

- [x] npm run build ✅
- [x] mvn clean compile ✅
- [x] 菜单可见（侧边栏工作流下）
- [x] 画布拖拽正常（Start/End/UserTask/排他网关/并行网关/连线）
- [x] 工具栏 + 右键菜单汉化
- [x] 部门树选择（/system/org/tree）
- [x] 岗位下拉（/system/post/all）
- [x] 用户搜索（/system/user/search）
- [x] **保存 → 后端 → 拉取 → 还原 extensionElements** ✅ 2026-11-17 修复
- [x] **导出 .bpmn 文件** ✅
- [x] **隔离验证**（老 /workflow/designer 不受影响） ✅
- [x] **租户隔离**（不同租户数据互不可见） ✅

---

## 8. 保存链路完整修复复盘（2026-11-17）

### 8.1 修复前的症状

```
保存前修改了审批人 → 点保存 → 接口成功返回 → 
拉取回来的 XML 里：
  <cyan:assignees assignee="[object Object]" />
  ← assignee 数组被当普通属性序列化成字符串了！
  
  <cyan:signType value="undefined">OR</cyan:signType>
  ← undefined 也被序列化成脏 attribute 了
  
重新 importXML → 属性面板全部空的 → 保存的数据全是脏的
```

### 8.2 修复过程（3 次迭代 + 源码分析）

```
第 1 次：moddle.create('cyan:assignee') 
  → unknown type "cyan:assignee" ❌
  根因：cyan 没注册 moddle descriptor

第 2 次：手动 JSON { $type: 'cyan:assignee', ... }
  → writer 序列化时崩：Cannot read properties of undefined (reading 'isGeneric') ❌
  根因：bpmn-moddle 源码里，writer.writeAttribute() 会检查 node.$descriptor.isGeneric
        手动 JSON 没有 $descriptor → undefined.isGeneric → 崩

第 3 次：moddle.createAny('cyan:assignee', nsURI, props) ✅
  → 自动构建带 { isGeneric: true } 的 $descriptor
  → writer 走 generic 序列化路径（parseGenericNsAttributes + parseGenericContainments）
  → 嵌套子元素必须放 $children
  → 文本内容必须放 $body
```

### 8.3 核心坑点总结

| 坑 | 错误写法 | 正确写法 |
|----|---------|----------|
| cyan:* 对象创建 | `moddle.create('cyan:xxx')` 或手动 JSON | `moddle.createAny('cyan:xxx', NS, props)` |
| 嵌套 assignee 子元素 | `{ assignee: [child1, child2] }` | `{ $children: [child1, child2] }` |
| 文本内容节点 | `{ value: 'OR' }` | `{ $body: 'OR' }` |
| ExtensionElements 创建 | 手动 JSON 或 camelCase 类型名 | `moddle.create('bpmn:ExtensionElements')` |
，前端路由走 try_files |
| 空值属性 | 直接写入 | clean 过滤 undefined/null/'' |
| values 数组替换 | `extEls.values = newArr`（替换引用） | `splice(0, len, ...items)` 原地修改 |

### 8.4 完整 persistElementProps 实现（定稿）

```javascript
const CYAN_NS_URI = 'http://cyan.org/bpmn-ext'

function makeCyanAny(moddle, localName, props) {
  const clean = {}
  for (const [k, v] of Object.entries(props)) {
    if (k === '$children') clean[k] = v
    else if (k === '$body' && v) clean[k] = String(v)
    else if (v !== undefined && v !== null && v !== '') clean[k] = String(v)
  }
  return moddle.createAny('cyan:' + localName, CYAN_NS_URI, clean)
}

function persistElementProps() {
  if (!selectedElement.value) return
  const modeling = modeler.get('modeling')
  const moddle = modeler.get('moddle')
  const bo = selectedElement.value.businessObject

  modeling.updateProperties(selectedElement.value, { name: elementProps.name || '' })
  const newExts = []

  if (selectedElement.value.type === 'bpmn:UserTask') {
    if (elementProps.assignees?.length > 0) {
      const children = elementProps.assignees.map(a =>
        makeCyanAny(moddle, 'assignee', { type: a.type, value: a.value, name: a.name })
      )
      newExts.push(makeCyanAny(moddle, 'assignees', { $children: children }))
    }
    newExts.push(makeCyanAny(moddle, 'signType', { $body: elementProps.signType }))
  }

  if (selectedElement.value.type === 'bpmn:SequenceFlow') {
    if (elementProps.desc) newExts.push(makeCyanAny(moddle, 'desc', { $body: elementProps.desc }))
    if (elementProps.conditionExpr) {
      // ⚠️ FormalExpression 是 bpmn 标准类型 → 用 body（正式属性），不是 $body！
      // $body 是 generic 扩展 createAny 的手写约定，标准 bpmn 类型有 descriptor，
      // body 属性定义了 isBody:true，writer 会输出为标签内容而非 attribute
      modeling.updateModdleProperties(selectedElement.value, bo, {
        conditionExpression: moddle.create('bpmn:FormalExpression', {
          body: elementProps.conditionExpr,
          language: 'spEL'  // 加 language，明确标识表达式类型
        })
      })
    } else if (bo.conditionExpression) {
      modeling.updateModdleProperties(selectedElement.value, bo, { conditionExpression: undefined })
    }
  }

  let extEls = bo.extensionElements
  if (!extEls && newExts.length > 0) {
    extEls = moddle.create('bpmn:ExtensionElements')
    modeling.updateModdleProperties(selectedElement.value, bo, { extensionElements: extEls })
  }
  if (extEls) {
    const values = extEls.values || []
    const filtered = values.filter(v => !String(v.$type || '').startsWith('cyan:'))
    values.splice(0, values.length, ...filtered, ...newExts)
    extEls.values = values
  }
}
```

---

## 9. bpmn-moddle 源码级深度分析（2026-11-17 新增）

> 本节记录为了搞懂 createAny 而深挖 bpmn-moddle 源码的发现，对后续迭代至关重要。

### 9.1 create() vs createAny() 分界线

```
moddle.create('bpmn:ExtensionElements', props)
  → 内部查 descriptorRegistry.get('bpmn:ExtensionElements')
  → 找到 descriptor（bpmn-moddle 内置注册）
  → 正常构建 + 赋值

moddle.create('cyan:assignee', props)
  → descriptorRegistry.get('cyan:assignee')
  → undefined（没注册）
  → throw new Error(`unknown type <cyan:assignee>`)

moddle.createAny('cyan:assignee', nsURI, props)
  → 不查 descriptorRegistry
  → 自动构建 { name: 'cyan:assignee', isGeneric: true, properties: {} } 作为 $descriptor
  → props 里的非 $ 开头属性 → $attrs（XML attributes）
  → props.$children → $children（嵌套子元素）
  → props.$body → $body（文本节点内容）
```

### 9.2 类型命名规则（隐性约束）

| 注册名（create 用） | XML 标签名 | 说明 |
|---------------------|-----------|------|
| `ExtensionElements` | `<bpmn:extensionElements>` | 首字母大写注册，XML 小驼峰 |
| `FormalExpression` | `<bpmn:conditionExpression>` | 同上，XSD 里叫 tFormalExpression |
| `UserTask` | `<bpmn:userTask>` | 同上 |
| `StartEvent` | `<bpmn:startEvent>` | 同上 |

**⚠️ 为什么 XML 里是 `extensionElements`（小驼峰）但 create 要用 `ExtensionElements`（首字母大写）？**

bpmn-moddle 内部 descriptor 注册时统一**首字母大写**，但 XML Schema 里元素名是**小驼峰**。moddle 的 reader/writer 自动做这个转换。你在 `moddle.create()` 里必须用**注册名**（首字母大写），否则找不到 descriptor。

### 9.3 generic 元素序列化规则（writer 内部）

```
moddleWriter.writeElement(node)  // cyan:assignees 元素
  │
  ├── 检查 node.$descriptor.isGeneric === true
  │   → 是，走 generic 路径
  │
  ├── parseGenericNsAttributes(node, attrs)
  │   → 所有 node 上非 $ 开头的属性 → XML attributes
  │   → node.type, node.value → type="dept" value="6"
  │   ⚠️ 数组属性被 String() → assignee="[object Object]" ← 之前的坑！
  │
  ├── parseGenericContainments(node, children)
  │   → node.$children 数组 → 递归 writeElement 每个子元素
  │   → 这是嵌套子元素的**唯一**正确入口
  │
  └── writeStartTag + writeChildren + writeEndTag
      <cyan:assignees type="...">
        <cyan:assignee type="dept" value="6" />
        <cyan:assignee type="user" value="7" />
      </cyan:assignees>


moddleWriter.writeAttribute(node)  // cyan:signType 元素
  │
  └── node.$body 存在 → <cyan:signType>OR</cyan:signType>
      （文本节点内容不写成 attribute）
```

### 9.4 reader 反序列化（importXML 时）

```
XML <cyan:assignees type="...">
      <cyan:assignee ... />
    </cyan:assignees>
  │
  ├── 找不到 descriptor（cyan:* 没注册）
  │   → 自动调 createAny('cyan:assignees', nsURI)
  │
  ├── 读 attributes → 写入 $attrs
  ├── 读子元素 → 自动 find 嵌套 → 存入 $children
  ├── 读文本节点 → 存入 $body
  │
  └── 最终对象：
      {
        $type: 'cyan:assignees',
        $descriptor: { isGeneric: true },
        type: '...',           // attribute
        $children: [           // 嵌套子元素数组
          { $type: 'cyan:assignee', type: 'dept', value: '6', $descriptor: {...} },
        ],
        $body: undefined
      }
```

### 9.5 读写对称性

| 写入（persistElementProps） | 序列化（saveXML） | 反序列化（importXML） | 读取 |
|---------------------------|-------------------|---------------------|------|
| `moddle.createAny('cyan:xxx', NS, props)` | → XML `<cyan:xxx>` | → 自动 createAny | `.value` |
| `{ $children: [...] }` | → 嵌套子元素 | → 自动存入 $children | `.$children` |
| `{ $body: 'OR' }` | → 文本节点 | → 自动存入 $body | `.$body` |
| `{ type: 'dept' }` | → attribute `type="dept"` | → 自动存入属性 | `.type` |

**核心结论：** createAny() 创建的对象和 importXML 反序列化回来的对象结构完全对称，读写零转换成本。

---

## 10. 后续迭代注意事项

### 10.1 extensionElements 后端解析（二期运行引擎）

后端需要解析 cyan:assignee 配置来决定运行时审批人：
```java
// 用 DOM + getElementsByTagNameNS，不需要引 Flowable/Activiti
Document doc = DocumentBuilderFactory.newInstance()
    .newDocumentBuilder().parse(new ByteArrayInputStream(bpmnXml.getBytes()));

// 找 UserTask 的 extensionElements/cyan:assignees/cyan:assignee
NodeList assignees = doc.getElementsByTagNameNS("http://cyan.org/bpmn-ext", "assignee");
for (int i = 0; i < assignees.getLength(); i++) {
    Element a = (Element) assignees.item(i);
    String type  = a.getAttribute("type");   // initiator/dept/post/user
    String value = a.getAttribute("value");  // 具体 ID
    String name  = a.getAttribute("name");   // 显示名（可选）
    // → 根据 type+value 解析出实际审批人列表
}
```

**注意：** cyan:desc、cyan:signType 等节点的文本内容用 `getTextContent()` 读取，**不是** getAttribute()。

### 10.2 老脏数据清理

修复前的旧流程 XML 里有两种脏数据：
```xml
<!-- 脏 1：assignee 数组被当 attribute 序列化 -->
<cyan:assignees assignee="[object Object]" />

<!-- 脏 2：undefined 被序列化成脏 attribute -->
<cyan:signType value="undefined">OR</cyan:signType>
```

**处理方式：** 用户手动删除旧流程 → 新建。二期运行引擎解析时如果遇到这种脏格式，DOM 解析不会报错（只是拿不到正确数据），审批人列表会为空 → 前端 UI 上审批人配置面板全空。**建议在二期运行引擎的解析逻辑里加防御：assignee 数组为空时兜底用发起人。**

### 10.3 版本号规范

和自研流程保持一致：`uk_process_key_version(process_key, version)` 唯一索引。新版本 = 新记录，老版本保留历史。

### 10.4 importXML 校验

```javascript
try {
  await modeler.importXML(xml)
  // 自动恢复画布 + extensionElements（moddle 反序列化原生支持）
} catch (err) {
  // 旧脏数据可能导致 parse 失败？实测：bpmn-js 对未知扩展类型宽容，
  // 只要 XML 结构合法就不会炸。脏数据是逻辑层问题，不是解析层问题。
  ElMessage.error('BPMN 解析失败：' + err.message)
}
```

### 10.5 导出 .bpmn 文件

```javascript
const { xml } = await modeler.saveXML({ format: true })
const blob = new Blob([xml], { type: 'application/xml' })
const a = document.createElement('a')
a.href = URL.createObjectURL(blob)
a.download = `${form.processKey || 'process'}.bpmn20.xml`
a.click()
```

### 10.6 后端校验（不接 Flowable）

后端可以轻量校验 BPMN XML：
- DOM 解析 + schema 校验（可选，引 jbpm-bpmn2 或 bpmn-xml-validator）
- 必检查点：UserTask 必须有 cyan:assignees，SequenceFlow 网关出边条件互斥
- XML 非法控制字符过滤（参考 03 自研引擎 Pitfall #31）

### 10.7 bpmn.io 标志隐藏

已在全局 `<style>` 块实现：
```css
.bjs-powered-by { display: none !important; }
```
**注意：** 必须在**非 scoped** style 块里写。因为 `.bjs-powered-by` 是 bpmn-js 动态生成的 DOM 元素，不在 Vue scoped 作用域内。

### 10.8 回显完整链路（readElementProps 时序）

```
点节点 → selection.changed 事件触发
  → handleElementClick(el)
  → modeler.getModdle() / getBusinessObject()
  → readElementProps(bo)
      │
      ├── extensionElements.values 遍历
      │   ├── $type === 'cyan:assignees'
      │   │   └── $children.map(a => ({
      │   │         type: a.type,     // 'dept'/'post'/'user'
      │   │         value: a.value,   // ⚠️ XML 读回全是 String
      │   │         name: a.name
      │   │       }))
      │   └── $type === 'cyan:signType'
      │       └── $body → 'OR'/'AND'
      │
      ├── ★ 类型适配（关键！）
      │   ├── dept:  value = Number(value)  // el-tree-select deptId 是 number
      │   ├── post:  value = String(value)  // el-select :value="String(p.id)"
      │   └── user:  value = String(value)  // remote el-select :value="String(u.id)"
      │
      ├── ★ 注入 options（关键！）
      │   ├── user: unshift → userOptions（不要求 name 有值）
      │   └── post: 从 postList 匹配补 name
      │
      └── elementProps.assignees = mapped
          → Vue 响应式更新 → 模板 el-select / el-tree-select 匹配 v-model → 显示 label ✅
```

### 10.9 ~~二期扩展：绑定表单~~ ✅ 已完成（2026-11-18）

**实现：**
- 属性面板「📋 基础信息」加「关联表单」el-select，调用 `GET /form/definition/all-active` 获取表单定义下拉
- 选中表单后自动调用 `GET /form/field/list/{definitionId}` 拉取字段元数据
- 存储位置：**bpmn:definitions.extensionElements.cyan:formRef**（流程级扩展，全局唯一）
- 后端 Entity 新增 processKey 自动生成（UUID 去横线前 16 位）
- 前端无感：设计器属性面板不显示 processKey 字段
- watch + @change 双保险：`watch(() => form.formDefinitionId, ...)` + `@change` 都触发字段加载
- **前端路由**：`/workflow/bpmn/designer` 和 `/workflow/bpmn/definition`（列表页）
- **后端 Controller**：`/workflow/bpmn/definition`（list/get/{id}/xml/add/update/delete/publish 共 8 个 endpoint）

**formRef XML 示例：**
```xml
<bpmn:definitions ...>
  <bpmn:extensionElements>
    <cyan:formRef xmlns:cyan="http://cyan.org/bpmn-ext" 
                  formDefinitionId="45" 
                  formName="三重一大审批表单" />
  </bpmn:extensionElements>
  <bpmn:process ...>
```

### 10.10 ~~二期扩展：分支条件绑定表单字段~~ ✅ 已完成（2026-11-18）

**实现：**
- 属性面板 SequenceFlow 三列可视化条件编辑器：
  ```
  [表单字段 ▼]  [操作符 ▼]  [值 ▼]
  ```
- **左列字段下拉**：内置变量（submitterDeptId）+ 表单字段（从 formRef 对应表单的 JSON Schema 自动提取）
- **中列操作符**：=、!=、>、>=、<、<=、contains 等
- **右列值**：动态切换——部门字段用 el-tree-select（deptTree）、select/radio/checkbox 用 el-select、普通字段用 el-input
- **SpEL 语法**：字段名直接拼（如 `Fwgpmvjh7a9fajc > 500`），和自研流程 SpEL 条件完全一致，运行引擎通用
- **保存**：persistElementProps → modeling.updateModdleProperties → FormalExpression.body + language:'spEL' → saveXML
- **回显**：importXML 自动反序列化 FormalExpression → readElementProps 在 extensionElements 早退之前读 bo.conditionExpression.body → parseConditionToCols 反解析 SpEL 到三列辅助字段
- **正则反解析**：`/^(.+?)\s*(>=|<=|!=|==|>|<|=)\s*(.+)$/` 匹配 SpEL

**生成的 XML：**
```xml
<bpmn:sequenceFlow id="Flow_0jxjktq" ...>
  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression" language="spEL">Fwgpmvjh7a9fajc &gt; 500</bpmn:conditionExpression>
</bpmn:sequenceFlow>
```

**后续可扩展：**
- AND/OR 多条件组合（自研流程目前单条件，BPMN 对齐）
- 值列支持多选字段（checkbox → `in ['a','b']` 语法）
- 条件可视化预览（高亮网关出边的条件分支）

### 10.11 二期扩展：多实例 / 会签属性

当前 `cyan:signType` 只有 OR/AND，后续可扩展：
```xml
<cyan:multiInstance isSequential="false">
  <cyan:collection>${assigneeList}</cyan:collection>
  <cyan:completionCondition>${nrOfCompletedInstances / nrOfInstances >= 0.8}</cyan:completionCondition>
</cyan:multiInstance>
```
- 前端属性面板加「完成条件」输入框
- 运行引擎解析后决定是否自动跳过已完成节点
- 自研流程的会签机制已有实现，对齐即可