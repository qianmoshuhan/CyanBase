# 自研 Workflow 引擎

> 三张核心表 + approve 双重角色 + SpEL 排他网关 + 节点批量保存 + 版本快照机制。
> 全局约束请先读 👉 参考：00-README.md
> 租户底座 skip 决策树 👉 参考：01 - 租户底座.md

---

## 1. 核心定义：三张核心表

| 表 | 含义 | 关键字段 |
|---|---|---|
| wf_process_instance | 流程实例（一条流程发起 = 一条记录） | id, definitionId, initiatorId, status(running/completed/rejected/cancelled) |
| wf_node_runtime | 节点运行时（实例经过的每个节点） | instanceId, nodeId, status(PENDING/WAITING/APPROVED/REJECTED), sortOrder, approvedCount, totalApprovers |
| wf_process_approve | 审批记录 + 待办队列（**双重角色！**） | instanceId, nodeId, approverId, action(null=待办/approve/reject/skip/transfer), signType(NORMAL/ADD/COUNTER) |

---

## 2. approve 表的双重角色设计（踩过坑！）

```
action = NULL        → 这是一条待办（前端"我的待办"列表用）
action = "approve"   → 已通过
action = "reject"    → 已驳回
action = "skip"      → 跳过（会签剩余人 或 或签已有人通过）
action = "transfer"  → 转办

❌ 错误：审批时 INSERT 新记录
   → 同一审批人产生两条：待办还在（action=null）+ 审批日志
   → 2026-10-21 实测：approve 表同一人同一节点有 2~9 条重复记录！

✅ 正确：审批时 UPDATE 原来的待办记录
   → 同一条记录：action 从 null → "approve"
   → 2026-10-21 修复：WorkflowApproveServiceImpl.approveProcess() 改为 UPDATE
   → 2026-10-21 清理脏数据：DELETE a.id < b.id AND a.action IS NULL AND b.action IS NOT NULL
```

### approve 表的 4 种 INSERT / UPDATE 场景

| 场景 | 操作 | 代码位置 |
|---|---|---|
| startProcess 生成第一节点待办 | INSERT | WorkflowStartServiceImpl.generateFirstTodo() |
| forwardToNextNode 推进节点 | INSERT | WorkflowApproveServiceImpl 审批后 |
| addSigner 加签 | INSERT（新增人，合理） | WorkflowApproveServiceImpl |
| transfer 转办 | 原审批人 UPDATE 成 transfer + INSERT 新审批人 | WorkflowApproveServiceImpl |
| **approveProcess 审批通过/驳回** | **UPDATE**（原待办的 action 从 null → 值） | WorkflowApproveServiceImpl |
| cancelRemainingTodos 作废剩余待办 | UPDATE（action 改成 skip） | WorkflowRuntimeServiceImpl |

---

## 3. 节点流转类型

| 类型 | 说明 | runtime 推进逻辑 |
|---|---|---|
| **或签（OR）** | 任一人审批通过即过 | approvedCount >= 1 → runtime.status=APPROVED → 推进下一节点 |
| **会签（AND）** | 所有人都要审批完才过 | approvedCount >= totalApprovers → runtime.status=APPROVED → 推进 |
| **驳回** | 任一人驳回即过 | runtime.status=REJECTED → 停止流转 → 实例 status=rejected |

---

## 4. 轨迹 trail 接口（前端渲染完整链路）

**GET /workflow/instance/{id}/trail**

```json
{
  "nodeStatusList": [
    { "nodeId": 148, "nodeName": "start", "nodeType": "start",    "status": "COMPLETED" },
    { "nodeId": 149, "nodeName": "部门经理审批", "nodeType": "userTask", "status": "CURRENT" },
    { "nodeId": 150, "nodeName": "总经理审批", "nodeType": "userTask", "status": "PENDING" },
    { "nodeId": 151, "nodeName": "end", "nodeType": "end", "status": "PENDING" }
  ],
  "approveRecords": [ ... ],
  "completedNodeKeys": [...],
  "currentNodeKeys": [...],
  "instanceId": 29,
  "status": "running"
}
```

### 节点状态推断规则（后端 Controller 里做的）

```
runtime 表里的节点 + definition 里的全量节点 → 推断：

  start 节点              → COMPLETED（永远）
  end 节点 + 实例已完成   → COMPLETED
  节点在 runtime 且 status=APPROVED → COMPLETED
  节点在 runtime 且 status=WAITING → CURRENT（当前）
  节点在 runtime 且 status=REJECTED → REJECTED
  不在 runtime 里         → PENDING（还没流转到）
```

---

## 5. 前端轨迹渲染要点

### 节点流转 Timeline

| 状态 | Timeline 颜色 | 节点标签 | 特效 |
|---|---|---|---|
| COMPLETED | #67c23a 绿色 | 已完成（start/end 显示类型标签） | - |
| CURRENT | #e6a23c 橙色 | 当前节点 | 三层波纹散开动画 |
| REJECTED | #f56c6c 红色 | 已驳回 | - |
| PENDING | #c0c4cc 灰色 | （待审批）| - |

### 审批明细按节点分组

```
nodeStatusList（后端返回）
    ↓
groupedRecords = nodeStatusList.map(ns => {
  records = approveRecords.filter(r => r.nodeId === ns.nodeId)
  virtualRecord = start/end 节点造虚拟 approve 记录
    ↓
})
```

start/end 节点在 approve 表里没有记录，前端**虚拟化**一条：
```typescript
// start 节点虚拟记录：
{ action: 'approve', approverName: instance.initiatorName, comment: '发起人提交' }
// end 节点虚拟记录：
{ action: 'approve' / 'rejected', approverName: instance.initiatorName, comment: '流程完结，提交人确认' }
```

**start/end 的 approverName 都用 instance.initiatorName — 谁发起谁闭环。**

### isCurrent 三层判断（重要！）

```javascript
// ❌ 错误：只看 ID 匹配（completed 流程最后一个 userTask 会被误判）
isCurrent = curNodeId != null && r.nodeId === curNodeId

// ✅ 正确：三层判断
isCurrent = (instance.status === 'running')        // 1. 流程必须在运行中
         && (nodeStatusList.status === 'CURRENT')   // 2. 后端明确标记为当前
         && (nodeId 匹配)                           // 3. ID 兜底匹配
```

---

## 6. 动画：三件套模式

```
节点流转 Timeline 波纹：
  本体     → box-shadow 扩散 1.4s
  ::before → scale + opacity 2.4s（立即）
  ::after  → scale + opacity 2.4s（delay -0.8s 错相）

审批明细当前节点边框：
  本体     → box-shadow 扩散 1.4s（对齐 Timeline 本体）
  ::before → scale + opacity 2.4s（对齐 Timeline ::before）
  ::after  → scale + opacity 2.4s（delay -0.8s 错相，对齐 Timeline ::after）

❗ border 永远固定 1px —— 用 transform + opacity（GPU 加速），
   绝对不要改 border-width（会触发 reflow，布局抖动）
```

视觉语言统一：上下两处"当前节点"的波纹动画时间参数、缓动函数、错相 delay 完全一致。

---

## 7. 执行流程：已撤销/已驳回流程手动删除

```
DELETE /workflow/instance/{id}

审计约束（三层铁闸）：
① 前端 UI：v-if 隐藏 running/completed 的删除按钮
② Controller：@RequiresPermission("workflow:start")
③ Service：
   - 实例存在校验
   - 只能删自己发起的（initiatorId 匹配当前用户）
   - 只有 cancelled / rejected 可删
   - 级联删 approve + runtime + instance 三张表
```

---

## 8. 约束规则：WorkflowNodeServiceImpl 三个方法必须 skip 租户拦截 [R]

👉 参考：00-README.md [约束 #23] + [约束 #25] + [约束 #31]

> wf_process_node.definition_id 是全局唯一外键，一个节点通过 definition_id 就能精确归属到某个流程定义，**不需要也不应该叠加 tenant_id 做二次隔离**。

### 必须 skip 的三个方法

| 方法 | 被拦截的 SQL | 后果 |
|---|---|---|
| `listByDefinitionId` | SELECT ... WHERE definition_id = ? **AND tenant_id = ?** | 查询结果不全，表格显示乱 |
| `batchSaveNodes` 内的 `deleteByDefinitionIdPhysical` | DELETE ... WHERE definition_id = ? **AND tenant_id = ?** | 物理删除 0 行，旧节点删不动 |
| `deleteByDefinitionId` | 同上（软删也被追加） | 软删无效 |

### 标准模板

```java
CustomTenantHandler.skip();
try {
    // deleteByDefinitionIdPhysical / batchSaveNodes / listByDefinitionId
} finally {
    CustomTenantHandler.reset();  // ⚠️ 必须 finally reset
}
```

---

## 9. 核心定义：字典 + 表单字段 + SpEL 网关 完整链路（2026-10-22 新增）

### 9.1 数据模型

```
sys_dict_type          (字典类型表：用户性别、审批状态等)
  └─ dict_type_code    (唯一编码，如 sys_user_gender)

sys_dict_data          (字典数据表：字典类型下的具体选项)
  └─ dict_type_code    (关联字典类型)
  └─ dict_label        (显示文本：男/女/保密)
  └─ dict_value        (实际存值：1/0/2)

form_definition        (表单定义)
  └─ layout_config     (form-create rule JSON 字符串，存完整表单结构)

form_field             (表单字段元数据，独立于 layoutConfig 的结构化字段)
  └─ definition_id     (关联 form_definition.id)
  └─ field_key         (字段标识，如 amount —— ⭐ SpEL 直接引用这个名)
  └─ field_name        (字段名称，如 流程金额)
  └─ field_type        (number / input / select / textarea ...)
  └─ dict_type_code    (关联字典类型编码 —— 独立列，不塞 options JSON)

wf_process_definition  (工作流定义)
  └─ form_definition_id (关联表单定义 —— 选了这个表单才能加载字段做 SpEL)

wf_process_node        (工作流节点)
  └─ condition_expr    (SpEL 表达式，排他网关分支条件)
  └─ gateway_type      (exclusive = 排他网关)
```

### 9.2 完整数据流（发起流程 → SpEL 网关分流）

```
① 表单设计器
   拖一个数字输入组件 → fieldKey="amount" → 保存
   └─ layoutConfig JSON 存完整结构
   └─ form_field 表同步存字段元数据（handleSave 里 extractFormFields）

② 工作流设计器
   关联表单 = 审批流03 (formDefinitionId)
   排他网关 → 分支A(userTask test6):
     conditionExpr = "amount >= 1000000"   ← 直接用表单的 fieldKey！
   排他网关 → 分支B(兜底):
     conditionExpr = ""   ← 留空！后端自动当 default
   保存 → 发布

③ 发起流程 /workflow/start
   选 spel 流程 → 自动加载关联表单 → form-create 渲染
   用户填金额 = 2000000
   handleSubmit → formApi.formData() → {amount: 2000000}
   POST /workflow/instance/start → variables = JSON.stringify({amount: 2000000})

④ 后端 SpEL 网关求值
   vars.put("amount", 2000000)   // 从发起表单的 variables JSON 解析出来
   ctx = StandardEvaluationContext(vars)
   parser.parseExpression("amount >= 1000000").getValue(ctx)
   → 2000000 >= 1000000 = TRUE
   → 走 test6 分支 ✅
```

### 9.3 隐性约束清单

| 约束 | 说明 |
|---|---|
| FormField.dict_type_code 独立列 | 不塞进 options JSON，方便条件网关直接查 `SELECT * FROM form_field WHERE dict_type_code = ?` |
| 排他网关至少一个分支留空 | conditionExpr="" 自动当 default 兜底分支 |
| 金额字段必须选 number 类型 | 否则 SpEL `amount >= 1000000` 变成字符串字典序（"99" > "1000" 逻辑炸） |
| variables 必须 JSON.stringify | 后端 StartProcessRequest.variables 是 String 类型，不是 Map |
| form-create v3 取数据用 formData() | `formApi.formData()` 或 `formApi.form`（属性），**不是** `formApi.form()` |
| 跨租户读平台表单要 skip | CustomTenantHandler.skip() → 查询 → CustomTenantHandler.reset() |
| RBAC 缓存键 rbac:perms:* | 改角色权限后必须 DEL 缓存，否则前端菜单不更新 |
| 字典选项：label 给人看 / value 给机器存 | SpEL 里比较的是 dict_value（机器存的值），不是 dict_label（显示文本） |

---

## 10. SpEL 排他网关条件配置语法（2026-10-22 新增）

### 10.1 合法写法

```spel
// 数值比较（字段必须是 number 类型）
amount >= 1000000
#variables.amount >= 1000000           // 和上面等价，更显式

// 字符串比较（字段是 input/select 类型）
status == 'approved'
type != 'rejected'

// 包含关系
org in ['finance', 'hr']

// 复杂逻辑
amount >= 1000000 && type == 'bigItem'

// 带行注释（SpEL 支持 // 和 /* */）
amount >= 1000000   // 大额走这里
```

### 10.2 后端执行逻辑（WorkflowInstanceServiceImpl.java gateway 部分）

```java
// 1. 从 node.nextNodeIds 取所有候选分支
for (Long nid : nextIds) {
    WorkflowNode n = workflowNodeService.getById(nid);
    
    // 2. conditionExpr 为空 → 标记为兜底分支
    if (n.getConditionExpr() 空) {
        defaultNode = n;
        continue;  // 但不 break，继续找更具体的
    }
    
    // 3. 有条件的 → SpEL 求值
    if (parser.parseExpression(n.getConditionExpr()).getValue(ctx)) {
        picked = n;
        break;  // 命中就停
    }
}

// 4. 最终：picked 有值走 picked，没值走 defaultNode（兜底）
```

### 10.3 排他网关三要素（缺任何一个都炸）

| 要素 | 说明 |
|---|---|
| ① 至少一个分支 conditionExpr 留空 | 当 default 兜底分支 |
| ② 各分支条件互斥 | 不能两个条件同时为 true（否则永远命中先写的那个） |
| ③ 变量名匹配 form_field.field_key | SpEL 里写的就是表单设计器里的 fieldKey |

### 10.4 常见错误

| ❌ 错误写法 | 原因 | ✅ 正确写法 |
|---|---|---|
| `amount >= '1000000'` | 数值比较加引号 → 字符串字典序 | `amount >= 1000000` |
| `Fc9vmvjdx5u6abc == '值'` | 前端占位符 `'值'` 没改 | 改成真实比较值 |
| 两个分支条件相同 | 复制粘贴 → 后端命中第一个就 break | 每个分支条件必须互斥 |
| 所有分支都写了 conditionExpr | 没兜底 → 条件都不满足时 picked=null | 至少一个留空 |

---

## 11. 跨租户读取平台数据（2026-10-22 新增）

### 11.1 问题

平台 admin 创建的表单（tenant_id=0），租户用户发起流程时 `GET /form/definition/{id}` 被 AOP 租户拦截返回 null。

### 11.2 根因

MyBatis-Plus TenantLineInnerInterceptor 自动加 `WHERE tenant_id = ?`，admin 创建的数据 tenant_id=0 不等于当前租户 id。

### 11.3 修复模式（标准三行）

```java
CustomTenantHandler.skip();
try {
    FormDefinition def = formDefinitionService.getById(id);  // 跨租户查
} finally {
    CustomTenantHandler.reset();
}
```

### 11.4 必须 skip 的接口清单

| 接口 | 原因 |
|---|---|
| `GET /form/definition/{id}` | 工作流关联的表单可能是平台创建的 |
| `GET /form/definition/all-active` | 工作流设计器下拉选关联表单，需要看到平台分发的表单 |
| `GET /workflow/definition/published` | 工作流可能是平台分发给租户的 |
| `GET /workflow/definition/{id}` | 编辑/发起流程时跨租户读 definition + nodes |

---

## 12. form-create v3 API 迁移备忘（2026-10-22 新增）

| 功能 | v2 | v3 | 备注 |
|---|---|---|---|
| 组件渲染 | `<form-create :option>` | `<form-create v-model:api="fApi" :rule="rules" />` | v3 用 v-model:api 拿到实例 |
| 取全部数据 | `api.form()` | `api.formData()` 或 `api.form`（属性） | **最常见的坑** |
| 取部分字段数据 | `api.form(['a','b'])` | `api.formData(['a','b'])` | |
| 校验 | `api.validate()` | `api.validate()` | 没变 |
| 表单值双向绑定 | `api.form.a = 123` | `api.form.a = 123` | form 是 v3 的响应式属性 |

---

## 13. 表单字段元数据同步机制（2026-10-22 新增）

### 问题

表单设计器保存时**只存 layoutConfig JSON**（完整表单结构），但工作流条件网关需要**结构化字段元数据**（field_key / field_name / field_type / dict_type_code），form_field 表永远为空。

### 修复

FormDesigner handleSave() 里加一步：

```javascript
// 1. 解析 layoutConfig 里的所有字段
const fields = extractFormFields(layoutConfig)
// 2. 同步入库
await batchSaveFormFields(formDefId, fields)
```

extractFormFields 递归扫描 rule 数组，把每个字段组件的 `field`（= fieldKey）、`title`（= fieldName）、`type`（= fieldType）拆出来。

### 为什么需要独立的 form_field 表？

| 方案 | 优点 | 缺点 |
|---|---|---|
| 只存 layoutConfig JSON | 简单，一个字段 | 条件网关要找字段时得解析整个 JSON，性能差 + 代码复杂 |
| layoutConfig + form_field | layoutConfig 负责渲染，form_field 负责查询 | 保存时多一步同步 |
| **推荐方案** | 分离关注点，互不干扰 | |

## 14. 历史坑点 & 注意事项

> 全局铁律 👉 参考：00-README.md

| # | 坑根因 | 教训 | 性质 | 日期 |
|---|--------|------|------|------|
| 1 | 审批时 INSERT 新记录 → 同一人同一节点 2~9 条重复 | 审批 **UPDATE** 原待办的 action 字段（null → 值），不 INSERT | [P] | 2026-10-21 |
| 2 | 后端没返回 nodeStatusList → 前端只能显示已走过的节点 | WorkflowInstanceController.getTrail() 必须拼装 definition 全量节点 + runtime 推断状态 | 2026-10-21 |
| 3 | approveRecords 扁平不分组 → 会签场景用户看不懂 | 前端按 nodeId 分组渲染，start/end 造虚拟 approve 记录 | 2026-10-21 |
| 4 | isCurrent 判断只看 ID 匹配 → completed 流程最后一个 userTask 被误判为当前 | 必须三层判断：实例 status=running + nodeStatusList.status=CURRENT + ID 匹配 | 2026-10-21 |
| 5 | start/end 节点硬编码 approverName="系统" → 用户困惑谁在操作 | 改成 instance.initiatorName（谁发起谁闭环） | 2026-10-21 |
| 6 | 审批明细 border-width 动画导致布局抖动 | 永远固定 border 1px，用 transform:scale() + opacity 做动画（GPU 加速） | 2026-10-21 |
| 7 | deleteProcess 删除了 running/completed 流程没审计约束 | Service 层强制校验 status 只允许 cancelled/rejected | 2026-10-21 |
| 8 | `StartProcessRequest.variables` 是 String 但前端传 Object → Jackson 反序列化炸 | 前端必须 `JSON.stringify(variables)` | 2026-10-22 |
| 9 | 两处创建 WorkflowApprove 漏塞 approverName → 数据库 NOT NULL 约束炸 | WorkflowApproveServiceImpl.forwardToNextNode() 必须加 setApproverName("") | 2026-10-22 |
| 10 | `/form/definition/{id}` 没 skip 租户拦截 → 租户用户查不到平台 admin 创建的表单 | FormDefinitionController 读取接口加 CustomTenantHandler.skip() | 2026-10-22 |
| 11 | form-create v3 `api.form()` 是 v2 旧 API → `is not a function` | v3 用 `api.formData()` 方法或 `api.form` 属性 | 2026-10-22 |
| 12 | 表单设计器保存只存 layoutConfig → form_field 表永远空 | handleSave() 同步调 batchSaveFormFields() 拆字段入库 | 2026-10-22 |
| 13 | 工作流设计器"字段"按钮死锁：disabled 依赖空数组 | 按钮 disabled 只在预览模式，其他时候可点 | 2026-10-22 |
| 14 | SpEL `amount >= '1000000'` 加了引号 → 字符串比较或 TypeMismatch | 数值比较**不加引号**：`amount >= 1000000` | 2026-10-22 |
| 15 | 排他网关两个分支 conditionExpr 相同 → 复制粘贴坑 | 后端命中第一个就 break → 另一分支永远走不到 | 2026-10-22 |
 16 | 排他网关所有分支都写了 conditionExpr → 无 default 兜底 | 至少一个分支 conditionExpr **留空**（空的自动当 default） | 2026-10-22 |
| 17 | SpEL 表达式带 `// 中文注释` → `Unsupported character '金'` | 后端解析前 strip `//` 注释，前端插入模板不带中文 | 2026-10-22 |
| 18 | SpEL 找不到 Map key → `Property or field 'xxx' cannot be found on LinkedHashMap` | 必须注册自定义 `MapPropertyAccessor` 告诉 SpEL 用 `map.get(key)` | 2026-10-22 |
| 19 | 旧版 Spring 没有内置 `MapAccessor` → `cannot find symbol` | 自己实现 `com.cyan.common.spel.MapPropertyAccessor` | 2026-10-22 |
| 20 | 只在 WorkflowInstanceServiceImpl 修了网关分流 → 审批通过后还是不生效 | 网关分流在 **两个独立类** 都有：`resolveGatewayBranch`（发起时）和 `resolveNextNodes`（审批推进时），**两个都要加**防御 | 2026-10-22 |
| 21 | form-create input 组件存字符串 `"5670000"` → SpEL 类型不匹配 | 后端网关求值前防御性转：纯数字 String → Long/Double | 2026-10-22 |
| 22 | fc-designer inputNumber 顶层 type="input" 不是 "inputNumber" → form-create 渲染 el-input | 保存 layoutConfig 前 normalizeFcRule 改成 "inputNumber" | 2026-10-22 |

## 15. SpEL 中文注释陷阱（2026-10-22 新增）

### 15.1 现象

```
WARN Gateway condition 解析失败 expr=amount >= 1000000   // 金额
err=Unsupported character '金' (37329) encountered at position 33 in expression.
```

SpEL 解析器在**expression 解析阶段**就炸了，根本没走到比较那一步 → 直接 catch → picked=null → 永远走兜底分支。

### 15.2 根因

Spring SpEL 虽然声称支持 `//` 行注释，但对注释里的 **非 ASCII 字符（中文）有 bug**。老版本 Spring 遇到中文直接抛 `SpelParseException`。

### 15.3 修复：后端 strip // 注释

在 SpEL 解析前先把 `//` 后面的内容裁掉：

```java
// WorkflowApproveServiceImpl.resolveNextNodes() 和
// WorkflowInstanceServiceImpl.resolveGatewayBranch() 都要加
String cleanExpr = n.getConditionExpr();
int commentIdx = cleanExpr.indexOf("//");
if (commentIdx >= 0) {
    cleanExpr = cleanExpr.substring(0, commentIdx).trim();
}
Object val = parser.parseExpression(cleanExpr).getValue(ctx);
```

### 15.4 前端也不要再生成中文注释

workflow/designer/index.vue 字段插入模板去掉 `// ${field.fieldName}`，字典选项用 ElMessage 单独弹提示：

```javascript
// ❌ 原来的模板（会炸！）
spel = `${field.fieldKey} == '值'   // ${field.fieldName}，字典选项: ${opts}`

// ✅ 新模板（干净的纯表达式）
spel = `${field.fieldKey} >= 1000000`
ElMessage.info(`字典选项: ${opts}`)  // 选项放提示里，不进 SpEL
```

---

## 16. SpEL 找不到 Map key 的隐形坑（2026-10-22 新增）

### 16.1 现象

```
ERROR EL1008E: Property or field 'amount' cannot be found on object of type 'java.util.LinkedHashMap'
```

SpEL 把 `StandardEvaluationContext(rootObject=varsMap)` 的 rootObject 当 **JavaBean** 找 `getAmount()` getter —— 不会自动走 `map.get("amount")`！

### 16.2 根因

StandardEvaluationContext 默认只注册 `ReflectivePropertyAccessor`，它只认 JavaBean getter/setter。Map 的 key 不属于 JavaBean 属性。

### 16.3 修复：注册自定义 MapPropertyAccessor

旧版 Spring 没有内置 `MapAccessor`（Spring 5.3+ 才有），自己写一个：

```java
// com.cyan.common.spel.MapPropertyAccessor
public class MapPropertyAccessor implements PropertyAccessor {
    @Override public Class<?>[] getSpecificTargetClasses() { return new Class<?>[]{Map.class}; }
    @Override public boolean canRead(EvaluationContext ctx, Object target, String name) {
        return target instanceof Map;
    }
    @Override public TypedValue read(EvaluationContext ctx, Object target, String name) {
        Map<?,?> map = (Map<?,?>) target;
        return new TypedValue(map.get(name));
    }
    // canWrite / write 返回 false/抛 UnsupportedOperationException
}
```

然后在两个网关分流方法里都注册：

```java
// WorkflowApproveServiceImpl.resolveNextNodes()
// WorkflowInstanceServiceImpl.resolveGatewayBranch()
StandardEvaluationContext ctx = new StandardEvaluationContext(vars);
ctx.addPropertyAccessor(new MapPropertyAccessor());  // ⭐ 关键！
ctx.setVariable("variables", vars);
```

### 16.4 这样就能写两种风格

```spel
// 风格 A：裸字段名（推荐，前提：注册了 MapPropertyAccessor）
Fwgpmvjh7a9fajc >= 1000000

// 风格 B：显式引用（不需要 MapPropertyAccessor 也能跑，但啰嗦）
#variables.Fwgpmvjh7a9fajc >= 1000000
```

---

## 17. WorkflowApproveServiceImpl vs WorkflowInstanceServiceImpl（2026-10-22 新增）

网关分流逻辑存在于**两个独立的类**中，修复防御性代码时**两个都要加**：

| 时机 | 类 | 方法 | 说明 |
|---|---|---|---|
| **流程发起时** | WorkflowInstanceServiceImpl | `resolveGatewayBranch()` | 发起流程 → 第一个节点就是网关 → 分流 |
| **审批推进时** | WorkflowApproveServiceImpl | `resolveNextNodes()` | 审批通过 → 下一节点是网关 → 分流 |

### 已确认两处都要加的防御清单

| 防御 | 两个都加了吗？ |
|---|---|
| ✅ SpEL 前 strip `//` 注释 | 两个都加 |
| ✅ MapPropertyAccessor 注册 | 两个都加 |
| ✅ 数字 String → Number 自动转换 | 两个都加 |
| ✅ CustomTenantHandler.skip() | 两个都加 |

---

## 18. 表单 ↔ 流程 多对多绑定机制（2026-10-23 新增）

### 18.1 数据表

```
wf_form_process_binding（中间表）
  └─ id, formDefinitionId, processDefinitionId, tenantId, priority
  └─ deptPath, deptId, businessType  ← 路由规则字段
  └─ enabled, remark, createTime
```

### 18.2 路由匹配优先级（FormProcessBindingServiceImpl.matchProcess）

```
用户发起「我的表单」→ 系统自动 match 流程：

1. deptPath（部门路径前缀匹配，最精确）
2. deptId（部门精确匹配）
3. businessType（业务类型标签匹配）
4. 空规则（兜底，该表单绑定的所有 enabled 中 priority 最高的）

→ 同优先级按 priority 降序取第一条
```

### 18.3 接口

| 接口 | 用途 | 调用时机 |
|------|------|----------|
| GET /form/definition/my-list | 当前租户已发布表单，含 hasBinding 标记 | 「我的表单」页面列表渲染 |
| GET /workflow/binding/match | 给定表单 definitionId + userId → 自动匹配 processDefinitionId | 用户点表单卡片时调 |
| POST /workflow/instance/start | 用 matched 的 processDefinitionId + 填好的 variables 发起 | 填报 Dialog 提交时 |

### 18.4 前端页面

| 页面 | 路径 | 权限 |
|------|------|------|
| 表单流程绑定（管理员配置） | /workflow/binding | workflow:binding:query（第 3 层漏斗全中可见） |
| 我的表单（操作员填报入口） | /workflow/myForm | workflow:myform:list |

### 18.5 前端填报完整链路

```
用户点「我的表单」卡片
  ↓
matchProcess(formDefinitionId, userId) → 返回 matched=true + processDefinitionId
  ↓
getFormDefinitionById(formDefinitionId) → 跨租户读取平台表单（skip CustomTenantHandler）
  ↓
parseLayoutConfig(layoutConfig) → form-create 动态渲染
  ↓
formApi.formData() → 校验 → JSON.stringify(variables)
  ↓
startWorkflow({ definitionId: processDefinitionId, variables }) → 流程实例启动 ✅
```

### 18.6 ⚠️ 待修复 Bug（见 08 - 后面要做的事）

**admin 平台创建的表单（tenant_id=0），租户用户 my-list 接口也能看到——应该只返回租户自己的表单！**

根因待排查：FormDefinitionController.my-list 可能没有正确通过 CustomTenantHandler 隔离，或者 form_definition 表缺少 tenant_id 字段过滤。

---

## 19. 表单/绑定/流程表的租户隔离策略

| 表 | 隔离方式 | skip 场景 |
|----|----------|-----------|
| form_definition | CustomTenantHandler 自动过滤 tenant_id | 工作流跨租户读表单（发起/设计时） → skip |
| wf_process_definition | CustomTenantHandler 自动过滤 tenant_id | 租户设计器选流程下拉 → skip 读平台分发的流程 |
| wf_form_process_binding | 绑定记录必须 tenant_id 对齐所属租户 | matchProcess 接口内部自动 skip 读全量绑定后内存过滤 |
| wf_process_instance | 实例 tenant_id = 发起时的租户 | 不需要 skip |

---

## 20. 坑点补录（2026-10-23，接第 14 节）

| # | 坑根因 | 现象 | 教训 | 日期 |
|---|--------|------|------|------|
| 23 | 只配 sys_role_menu 没配 sys_tenant_menu | SQL 直查全有 → curl getUserMenu 全缺 | 三层漏斗缺一不可，filterByTenant 严格取交集 | 2026-10-23 |
| 24 | sys_role_menu.tenant_id 默认写 0 没对齐角色 | role_tenant=6, rm_tenant=0 → TenantLine WHERE tenant_id=6 过滤掉 | rm.tenant_id = r.tenant_id，一毛不差 | 2026-10-23 |
| 25 | Redis 菜单缓存 key 是 rbac:menu:flat:{userId}:{tenantId} | 只清了 rbac:perms:* 没清菜单缓存 | 新增菜单后 `redis-cli KEYS "rbac:*"  xargs redis-cli DEL` 全清 | 2026-10-23 |
| 26 | admin 前端「分配权限」页面勾选新菜单后不生效 | 页面只帮配 sys_role_menu，sys_tenant_menu 和 rm.tenant_id 对齐必须手动 SQL | 三层漏斗的后两层前端工具链不覆盖，手动 SQL 兜底 | 2026-10-23 |
| 27 | form_process_binding 批量查询用 N+1 | 循环里逐个 selectById → 接口慢 | 一次查出所有绑定后用 Map<defId, List<Binding>> 分组 | 2026-10-23 |
| 28 | hasBinding 标记 SQL 用 IN 子查询判断存在性 | 原始写法：SELECT COUNT(*) → 优化成 EXISTS 子查询 | 列表接口性能敏感，EXISTS 比 COUNT(*) 早停 | 2026-10-23 |
| 29 | CustomTenantHandler.skip() 必须 try-finally reset() | 异常时忘了 reset → 后续请求都 skip → 全租户数据泄露 | 严格 try { skip(); doQuery(); } finally { reset(); } | 2026-10-22 |
| 30 | my-list 接口返回了平台 admin 建的表单（tenant_id=0） | 租户用户 test6 能看到 admin 的 spel01 → 待修复 | my-list 必须显式排除 tenant_id=0，或 CustomTenantHandler 加策略 | 2026-10-23 |
| 31 | **BPMN XML 非法控制字符** | 5 个流程里 3 个 BPMN 预览报错 `unparsable content ... invalid token line 26 column 136` | 用户从 Excel/网页粘贴文本到节点名称/条件时混入 `\x00-\x1F\x7F` 不可见控制字符，Java 拼 XML 直接输出，任何 XML 解析器一炸 | `escapeXml()` 必须同时过滤非法控制字符：只保留 `\t\n\r`，其余全部丢弃；mvn 部署前用 `python3 -c "import xml.etree.ElementTree as ET; ET.parse('file.xml')"` 验证 | 2026-10-27 |
| 32 | **BPMN sequenceFlow 非标准 flowElementRef 属性** | bpmn-js 严格模式拒绝解析，即使该属性是扩展也导致 `closing tag mismatch` | 自创 `flowElementRef` 属性不是 BPMN 2.0 标准，bpmn-js XML 校验器遇到即报错 | **只使用 BPMN 2.0 标准属性**：`id/sourceRef/targetRef/name` 加可选子节点 `conditionExpression`，不要自创任何属性 | 2026-10-27 |
| 33 | **SpEL 条件表达式配在网关上** | 引擎取不到条件，所有分支同时激活或全部走兜底 | 网关的 conditionExpr 对排他分流毫无意义——网关出边指向的每个目标节点才有独立条件 | **条件一律配在目标节点**：网关 nextNodeIds 指向的节点上存 conditionExpr，空 = 默认兜底分支 | 2026-10-27 |
| 34 | **兜底分支 "其他情况" 填充范围错误** | start→userTask 这种必经链路的连线也显示 "其他情况" | 后端逻辑没判断连线起点是不是网关，所有 showDesc 为空的连线都兜底 | **只对网关的出边做兜底**：起点 nodeType=gateway 且目标节点 conditionExpr 为空 才填 showDesc="其他情况"；非网关连线 showDesc 为空就是空字符串，不显示 | 2026-10-27 |
| 35 | **BPMN Label 位置重叠导致文字跑到错误连线** | 网关两条分支（一条水平线 y=65，一条折线终点 y=280）的 label 都算在 y=83，两个 "其他情况" 挤在一起 | 折线 label 位置用了 `min(sy, ey)` 取最小值，不管折线实际走到哪里 | **分情况算 label 坐标**：①纯水平线 → midY = sy + 20；②有折线 → 取折线最后一段水平段（目标节点前那段）中点，midY = ey + 20；两条折线 label 各在各的线条下方，互不重叠 | 2026-10-27 |
| 36 | **后端改了代码 jar 没重启** | curl /workflow/definition/XX/bpmn 返回的 XML 里 sequenceFlow 还是旧属性，BpmnXmlGenerator.java grep 有 BPMNLabel 代码但 XML 输出没有 | Linux 上 `pkill -f cyanbase-backend` 没杀干净，或 nohup 老进程还在跑旧 class | **改了 Java 代码必须确认进程重启**：① `ps aux | grep java` 看旧 PID 确实不存在；② 新 jar 启动后 curl 接口看返回内容是否包含新代码的特征字符串；③ 可以加一行 INFO 日志打印版本号快速验证 | 2026-10-27 |
| 37 | **SpEL 表达式用字段 key 而非友好名** | 前端条件面板自动拼接 showDesc 时，_condField 是 `F4ubmvjfn7wzamc`（表单字段内部 key），showDesc 变成 "F4ubmvjfn7wzamc 大于 10000" 而不是 "金额 大于 10000" | formFieldList 加载了 fieldKey 和 fieldName，syncConditionExpr 里只拿了 fieldKey 拼描述 | **showDesc 必须用 fieldName（友好名）**：`formFieldList.value.find(f => f.fieldKey === row._condField)?.fieldName || row._condField`，拼描述时优先 fieldName，找不到再 fallback fieldKey | 2026-10-27 |
| 38 | **租户侧编辑流程保存后，删除的节点还在** | 租户侧登录 → 编辑节点 → 删除 → 保存成功 → 再次打开，删的节点全回来了 | TenantLineInnerInterceptor 对物理删除 SQL 自动追加 tenant_id 条件 | 👉 参考：00 [约束 #23] + 本文件 [约束规则-三个方法必须 skip] | [P] | 2026-10-28 |
| 39 | **编辑后新增节点和原有节点顺序混乱** | 保存成功后再次打开，新增节点跑到表格最后，删除的节点位置留下空洞 | 前端 addNode() 永远赋 `sortOrder = 当前长度 + 1` 不管插入位置；保存时直接信任旧 sortOrder 没重新编号；后端也没兜底 | **双重保险**：①前端 handleSave 用 `nodes.value.map((n,i) => ...)` 按数组位置 `i+1` 重新编 sortOrder；②后端 batchSaveNodes 遍历保存时再用 `i+1` 兜底覆盖；数据库 sortOrder 永远连续无跳号 | 2026-10-28 |
| 40 | **submitterDeptId 反解析后值显示成裸部门 ID 数字** | 保存后重新打开流程设计器，部门树形选择器显示 "16" 而不是 "人力资源部" | 正则反解析 conditionExpr 时捕获组 `m[3]` 永远返回 String "16"，但 el-tree-select 内部用 `===` 严格匹配 deptTree.id（Number 16）→ 类型不匹配 → 找不到对应部门 | submitterDeptId 反解析后 `_condValue = Number(m[3])`，显式转成数字类型 | 2026-10-28 |
| 41 | **分支条件列被挤压，运算符（≥/≤ 等）显示不全** | min-width="260" 太窄，三列 flex 布局（字段下拉 + 运算符 + 值列）互相挤压 | Element Plus 的 min-width 是弹性最小值，空间不够时子元素被压缩 | 分支条件列 min-width 加宽到 380px，运算符下拉 width 从 72px 加到 80px | 2026-10-28 |
| 42 | **batchSaveNodes 里 newIds 映射修正漏了某些 nextNodeIds** | 批量保存时前端节点带临时 ID（负数或自增），后端 save 后分配新自增 ID；newIds 映射把 node.nextNodeIds 里的旧 ID 替换成新 ID；但如果有节点循环引用或网关分支指向未保存的中间节点 | nextNodeIds 的映射修正必须在所有节点 save 完后统一做一次，不能边 save 边修正（后面节点引用的旧 ID 可能还没被替换） | 标准流程：①物理清空旧节点 → ②所有节点 save 完建 idMap → ③统一遍历更新 nextNodeIds 里的旧→新映射 | 2026-10-28 |
| 43 | **WorkflowNodeServiceImpl import CustomTenantHandler 路径写错** | 写了 `import com.cyan.common.tenant.CustomTenantHandler` 但实际在 `com.cyan.config.CustomTenantHandler` | 不要猜包路径，grep 已有引用或直接 Glob 搜文件位置 | CustomTenantHandler 在 `com.cyan.config` 包下，和 MybatisPlusConfig 同级 | 2026-10-28 |

---

## 21. 执行流程：节点批量保存完整机制（WorkflowNodeServiceImpl.batchSaveNodes）

> 2026-10-28 修复租户侧编辑流程节点混乱 bug 时梳理清楚的核心机制。

### 21.1 保存流程图

```
batchSaveNodes(definitionId, nodesFromFrontend)
│
├── CustomTenantHandler.skip()   ⭐ 必须绕开租户拦截，definition_id 全局唯一
│
├── ① 物理删除旧节点
│     baseMapper.deleteByDefinitionIdPhysical(definitionId)
│     DELETE FROM wf_process_node WHERE definition_id = ?
│     （不能用 MP 软删 remove，软删记录占主键会导致下一次 insert 冲突）
│
├── ② 遍历保存新节点（i = 0, 1, 2, ...）
│     for (int i = 0; i < nodes.size(); i++) {
│         node.setId(null)                  ⭐ 强制清空前端临时 ID
│         node.setDefinitionId(definitionId)
│         node.setSortOrder(i + 1)          ⭐ 兜底重新编号，不信任前端
│         this.save(node)                   → MP 自动回填新自增 ID
│         idMap.put(旧ID, node.getId())     → 记录新旧 ID 映射
│     }
│
├── ③ 统一修正 nextNodeIds 里的旧→新 ID
│     for (WorkflowNode node : savedNodes) {
│         node.setNextNodeIds(
│             oldIds.split(",")
│                 .map(idStr -> idMap.getOrDefault(旧ID, 旧ID))  // 未找到的兜底保留
│                 .join(",")
│         )
│         this.updateById(node)
│     }
│
└── CustomTenantHandler.reset()  ⭐ finally 里必须 reset
```

### 21.2 三个关键设计决策

| 决策 | 原因 |
|---|---|
| **物理删除不用软删** | 软删（@TableLogic 逻辑删除）会在表里留下 del_flag=1 的记录，下次 save 时如果前端传来的节点带了和软删记录相同的 ID，会 Duplicate entry 主键冲突 |
| **所有节点 id 强制置 null** | 前端节点可能带临时负 ID（新增节点）、或带从后端读回来的旧 ID（编辑已有节点），一律清掉让数据库自增分配，保证主键唯一 |
| **sortOrder 按保存顺序重新编号** | 前端 sortOrder 在新增/删除/移动后会跳号（如 1,2,3,5,6），后端不信任，按遍历 i+1 强制连续编 |

### 21.3 为什么必须 skip 租户拦截

```
wf_process_node 表结构：
  id (BIGINT AUTO_INCREMENT PRIMARY KEY)
  definition_id (BIGINT, FK → wf_process_definition.id)
  tenant_id (BIGINT)

definition_id 本身是全局唯一主键。
  → 一个节点通过 definition_id 就能精确归属到某个流程定义
  → 再加 tenant_id 条件纯属多余
  → 租户侧登录时 tenantId=6，但节点表里的 tenant_id 可能是 0 或 6
  → 拦截器追加 AND tenant_id = 6 → 查不全 / 删不动 / 存不进
```

### 21.4 必须 skip 的三个方法

| 方法 | 被拦截的 SQL | 后果 |
|---|---|---|
| `listByDefinitionId` | SELECT ... WHERE definition_id = ? **AND tenant_id = ?** | 查询结果不全，表格显示乱 |
| `batchSaveNodes` 内的 `deleteByDefinitionIdPhysical` | DELETE ... WHERE definition_id = ? **AND tenant_id = ?** | 物理删除 0 行，旧节点删不动 |
| `deleteByDefinitionId` | 同上（软删也被追加） | 软删无效 |

---

## 22. 流程定义版本快照机制（2026-10-28 新增）

### 22.1 核心设计理念：版本自洽不依赖主表

```
wf_process_definition（主表）
  └─ definitionId=41 流程定义
       ├─ wf_process_node（主表节点）: dbId=148,149,150...  ← 可修改可删除
       │
       ├─ wf_def_version v1（版本快照）
       │    └─ flow_json = { nodes:[{id:-1,nodeKey:'n001',...}], processXml:'<bpmn>...', bpmnViewport:{...} }
       │    ⭐ 完全自洽，nodes里存的是虚拟ID，不引用主表dbId
       │
       └─ wf_def_version v2（创建新版本）
            └─ flow_json = { nodes:[{id:-1,nodeKey:'n001',nextNodeIds:[-2]}, ...], ... }
            ⭐ 从v1或主表复制，快照独立，主表改了不影响版本
```

**为什么不能依赖主表？** 用户可能在新版本里删除某个节点，或主表节点被他人修改——版本应该锁定保存时的那一刻状态，不能随着主表变化而变化。

### 22.2 版本表结构

```sql
CREATE TABLE wf_def_version (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  definition_id   BIGINT       NOT NULL COMMENT '关联主表 definition.id',
  version_name    VARCHAR(100) NOT NULL COMMENT '用户输入的版本名称（非标识）',
  version_no      INT          NOT NULL COMMENT '版本号（自动递增，从1开始）',
  flow_json       LONGTEXT     NOT NULL COMMENT '⭐ 完整快照：节点列表 + BPMN XML + 视口配置',
  remark          VARCHAR(500)          COMMENT '可选备注',
  create_by       VARCHAR(64)           COMMENT '审计字段',
  create_time     DATETIME              COMMENT '审计字段',
  tenant_id       BIGINT       NOT NULL COMMENT 'TenantMetaObjectHandler 自动填充',
  INDEX idx_def (definition_id)
);
```

### 22.3 flow_json 快照结构

```json
{
  "definitionMeta": {
    "name": "审批流01",
    "identifier": "approval_flow_01",
    "formDefinitionId": 43,
    "tenantId": 6
  },
  "nodes": [
    {
      "id": -1,              /* ⭐ 快照虚拟ID，负数，不依赖主表dbId */
      "nodeKey": "n001",     /* 唯一业务标识，新建时UUID生成 */
      "nodeType": "start",
      "nodeName": "开始",
      "nextNodeIds": "-2",   /* ⭐ 引用同一套虚拟ID，不混用主表dbId */
      "x": 100, "y": 200
    },
    {
      "id": -2,
      "nodeKey": "n002",
      "nodeType": "userTask",
      "nodeName": "部门经理审批",
      "assigneeType": "dept",
      "assigneeValue": "manager",
      "signType": "OR",
      "nextNodeIds": "-3",
      "conditionExpr": "",
      "x": 300, "y": 200
    },
    {
      "id": -3,
      "nodeKey": "n003",
      "nodeType": "end",
      "nodeName": "结束",
      "nextNodeIds": "",
      "x": 500, "y": 200
    }
  ],
  "processXml": "<?xml version='1.0' encoding='UTF-8'?>...<bpmn:definitions>...",
  "bpmnViewport": {
    "x": 50, "y": 0, "scale": 1
  },
  "capturedAt": "2026-10-28T15:30:00"
}
```

### 22.4 核心铁律：快照 ID 体系统一

| 字段 | 规则 | 违反后果 |
|------|------|----------|
| `nodes[].id` | 快照内使用负数虚拟ID（-1, -2, -3...）或基于 nodeKey.hashCode() 的稳定 Long | 不能混用主表自增 dbId |
| `nodes[].nextNodeIds` | 引用的值必须和 `nodes[].id` 是同一套编号体系 | 混用会导致引擎流转时找不到目标节点（如快照节点id=-2但nextNodeIds存了主表dbId=149） |
| `flow_json.definitionMeta` | 存 definitionName / identifier / formDefinitionId 的快照副本 | 主表这些字段改了版本也不受影响 |

### 22.5 快照序列化必须过滤的字段

**WorkflowNode 实体含有的审计/租户字段不能进快照 JSON**：

| 字段 | 类型 | 为什么要过滤 |
|------|------|-------------|
| id | Long | 主表自增ID，快照里用虚拟ID替代 |
| createTime / updateTime | LocalDateTime | ⚠️ Java 8 时间默认序列化失败（`LocalDateTime not supported by default`） |
| createBy / updateBy | String | 审计字段，版本快照不需要 |
| tenantId | Long | TenantMetaObjectHandler 自动填，快照不依赖 |
| sortOrder | Integer | 快照 nodes 数组位置即顺序，不需要独立 sortOrder |

### 22.6 新建版本完整流程

```
前端点击「新建版本」
  ↓
① 版本列表弹窗立即刷新一条新记录：version_no = 现有最大 + 1
  ↓
② 用户输入 version_name → 点确认
  ↓
③ 后端 POST /workflow/definition/{id}/version
   ① 从主表读当前最新 nodes（listByDefinitionId，skip 租户拦截）
   ② 读主表 definition 元信息
   ③ 把所有节点的 dbId 替换成 nodeKey 虚拟ID
   ④ 序列化 flow_json（只取业务字段，过滤审计）
   ⑤ INSERT wf_def_version，TenantMetaObjectHandler 自动填 tenantId
   ↓
④ 后端返回新版本 ID
  ↓
⑤ 前端带着新 versionId 跳转到设计器页面（版本只读模式）
```

### 22.7 版本只读模式控制

```javascript
// 前端设计器 index.vue
const currentVersionId = ref(null)
const isVersionMode = computed(() => currentVersionId.value != null)
const isReadonly = computed(() => isVersionMode.value)

// ❌ 错误：isVersionMode 写成普通布尔变量却访问 .value
const isVersionMode = ref(false)
isVersionMode.value = true
// 然后某处写成 if (isVersionMode) { ... }  // 布尔变量，OK
// 但如果写成 isVersionMode.value  // OK
// 真正的坑：把 isVersionMode 写成 let isVersionMode = false  // 普通布尔，没有 .value 属性
// 然后代码里写 if (isVersionMode.value)  // ReferenceError: isVersionMode.value is not defined

// ✅ 正确：computed 派生状态，响应式
const isVersionMode = computed(() => currentVersionId.value != null)
const isReadonly = computed(() => isVersionMode.value)

// 所有编辑按钮 / 节点拖拽 / 连线创建都依赖 isReadonly
// <el-button :disabled="isReadonly">保存</el-button>
// <bpmn-viewer :readonly="isReadonly">...</bpmn-viewer>
```

### 22.8 版本 BPMN XML 动态生成

后端 `BpmnXmlGenerator.generateFromSnapshot(flowJson)`：

```java
// 快照节点没有 dbId，BpmnXmlGenerator 里调用 node.getId() 会 null → NPE
// 解法：用 nodeKey.hashCode() 注入稳定虚拟 Long ID
for (SnapshotNode node : flowJson.getNodes()) {
    Long virtualId = (long) Math.abs(node.getNodeKey().hashCode()) % Long.MAX_VALUE;
    node.setId(virtualId);  // ⭐ 运行时注入，不修改原始快照数据
}
// 然后正常生成 BPMN XML，虚拟 ID 作为 XML 里 node id 和 sequenceFlow sourceRef/targetRef
```

### 22.9 BPMN 缓存回写清除

BpmnXmlGenerator 生成 XML 后，为了避免每次重新生成（耗时），设计了缓存字段：

```json
// flow_json 里有个 processXml 缓存字段
{ "nodes": [...], "processXml": "<?xml version...<bpmn>旧错误XML..." }
```

**坑点**：如果某次生成的 XML 有问题（如节点无连线），错误 XML 被回写到 processXml 缓存，下次请求直接命中缓存跳过生成——必须手动清除。

```sql
-- 清除缓存，让后端下次请求时重新生成
UPDATE wf_def_version 
SET flow_json = JSON_REMOVE(flow_json, '$.processXml') 
WHERE id = <版本ID>;
```

**预防**：新版本保存时，如果 BPMN 生成失败，**不要回写 processXml**，让下次请求能重试生成。

---

## 23. 三套节点标识 + 跳链容错（2026-10-28 核心机制）

### 23.1 三套标识体系

```
主表节点：
  WorkflowNode.dbId       = 148, 149, 150...    ← MyBatis-Plus AUTO_INCREMENT 自增
  WorkflowNode.nodeKey    = "n001", "n002"...   ← UUID 生成的唯一业务标识（稳定不变）

版本快照节点：
  snapshot.nodes[].id     = -1, -2, -3...      ← 快照虚拟ID（负数或 hashCode 注入）
  snapshot.nodes[].nodeKey = "n001", "n002"...  ← 复用主表 nodeKey，贯穿始终
```

**转换链路**（从主表创建版本时）：

```
主表 dbId=148 + nodeKey="n001" → 版本快照 id=-1（新虚拟ID）
主表 dbId=149 + nodeKey="n002" → 版本快照 id=-2
主表 dbId=150 + nodeKey="n003" → 版本快照 id=-3

同时需要修正 nextNodeIds 里的引用：
主表节点 nextNodeIds = "149,150" → 版本快照 nextNodeIds = "-2,-3"
（因为版本快照里目标节点的 id 已经是 -2/-3，不是 149/150 了）
```

### 23.2 跳链容错

**问题场景**：用户在主表里删除了某个节点（dbId=151, nodeKey="n005"），但版本快照里 node="n004" 的 nextNodeIds 还存着 "-5"（对应已删除的 n005）——跳转时找不到目标节点。

**解决策略**：遍历 nextNodeIds 时做"存在性检查 + 跳过"：

```java
// 版本快照节点跳转
List<Long> targetIds = Arrays.stream(node.getNextNodeIds().split(","))
    .map(String::trim).filter(s -> !s.isEmpty())
    .map(Long::parseLong).collect(Collectors.toList());

// 跳链容错：只保留快照里实际存在的节点 ID
List<Long> validTargets = targetIds.stream()
    .filter(targetId -> 
        snapshotNodes.stream().anyMatch(n -> n.getId().equals(targetId))
    )
    .collect(Collectors.toList());

if (validTargets.size() < targetIds.size()) {
    int skipped = targetIds.size() - validTargets.size();
    log.warn("跳链容错：节点 nodeKey={} 有 {} 个 nextNodeId 指向已删除节点，已跳过", 
             node.getNodeKey(), skipped);
}

return validTargets;  // 只返回有效目标，无效的自动跳过
```

**为什么用 nodeKey 贯穿？**

| 标识 | 稳定性 | 用途 |
|------|--------|------|
| dbId | 不稳定（物理删除后重新创建=新ID） | 主表内部操作，版本快照不用 |
| nodeKey | **永远稳定**（UUID 生成后不变） | 主表↔版本快照↔运行时 跨链路关联的唯一锚点 |
| 虚拟ID | 仅在版本快照内有意义 | 快照内部流转引用，必须和快照内其他节点虚拟ID同体系 |

---

## 24. 版本功能开发链路坑点补录（2026-10-28）

| # | 坑根因 | 现象 | 教训 | 日期 |
|---|--------|------|------|------|
| 44 | **LocalDateTime Java 8 时间默认序列化失败** | 版本快照序列化完整 WorkflowNode 实体 → Jackson 报 `LocalDateTime not supported by default` | 快照序列化只取节点业务字段（nodeKey/nodeType/nodeName/nextNodeIds/conditionExpr/x/y...），**过滤审计/租户/时间字段**（id/createTime/updateTime/createBy/updateBy/tenantId/sortOrder） | 2026-10-28 |
| 45 | **版本 Long ID 放 @RequestBody 被 axios 转成对象** | 保存快照 `PUT /workflow/definition/{id}/version/{versionId}` 把 versionId 放 body → axios 序列化 → 后端 Spring 尝试 Object → Long 报 `NumberFormatException: "[object Object]"` | **Long/大整数请求参数一律走 `@PathVariable` URL 路径参数或 `@RequestParam` 查询参数**，不走 `@RequestBody` | 2026-10-28 |
| 46 | **新建版本接口返回值未取内层 .data** | 新建版本返回 `R<Long>(versionId)` → 前端直接把 R 对象当 URL 参数 → `GET /workflow/designer?versionId=[object Object]` 400 | axios 后端统一响应 `R<T>` 封装，必须 `const vid = res.data` 取内层业务数据再使用 | 2026-10-28 |
| 47 | **isVersionMode 写成普通布尔变量却访问 .value** | BPMN 预览始终返回旧版本（versionMode 分支永远不命中） | Vue3 中 `let x = false` 是普通布尔值（无 .value），`const x = ref(false)` 才是响应式；推荐用 computed 派生状态：`const isVersionMode = computed(() => currentVersionId.value != null)` | 2026-10-28 |
| 48 | **快照节点无 dbId，BpmnXmlGenerator 空指针** | BPMN 预览报 `Cannot invoke Long.longValue() because WorkflowNode.getId() is null` | 快照节点用 nodeKey.hashCode() 注入稳定虚拟 Long ID，让 Generator 正常生成；注入是运行时操作，不修改原始快照数据 | 2026-10-28 |
| 49 | **历史节点 tenant_id=0 导致租户侧查不到，BPMN 纵向无连线** | 租户 test6 登录 → listByDefinitionId 被 TenantLine 追加 `AND tenant_id=6` → 历史节点 tenant_id=0 全部被过滤 → 查 0 条 → nextNodeIds 无法转换 → BPMN 全纵向 | **修复不是绕过拦截器（破坏租户隔离）**，而是加兼容查询条件 `.and(q -> q.eq(tenantId, ?).or().eq(tenantId, 0))`；新流程 TenantMetaObjectHandler 自动填正确 tenantId | 2026-10-28 |
| 50 | **错误 BPMN XML 被回写到 processXml 缓存，每次请求命中坏缓存** | 第一次生成了错误 XML（无连线）→ 缓存回写 → 清除 Redis 没用（因为缓存存在 MySQL flow_json 字段里）→ 必须手动 SQL 清除 | `UPDATE wf_def_version SET flow_json = JSON_REMOVE(flow_json, '$.processXml') WHERE id = ?`；**新版本保存时如果 BPMN 生成失败不要回写缓存** | 2026-10-28 |
| 51 | **版本名称被多包一层对象显示成 `{versionName=简化版01}`** | 前端调 API 时写 `saveVersionSnapshot({ versionName: xxx }, versionId)` → Spring 接收到 Map 对象 → 后端 `version.setVersionName(req.toString())` → 显示成 Map.toString() | API 调用直接传字符串参数：`saveVersionSnapshot(versionNameString, versionId)`，不要额外嵌套对象包装 | 2026-10-28 |
| 52 | **快照内 id 和 nextNodeIds 混用主表 dbId 导致跳转失败** | 版本预览 BPMN 能渲染但流转找不到下一个节点 | 快照内必须使用**同一套编号体系**——全部用虚拟ID（负数或 hashCode 注入），nextNodeIds 里存的值必须和 nodes[].id 是同一套；禁止混用主表 dbId | 2026-10-28 |
| 53 | **设计器新增 computed 却忘了在 import 里声明** | 版本模式下 console 报 `ReferenceError: computed is not defined` | Vue3 Composition API 每个用到的响应式函数（ref/reactive/computed/watch/onMounted）都必须显式 import；代码写完 grep 检查 import 列表 | 2026-10-28 |

---

---