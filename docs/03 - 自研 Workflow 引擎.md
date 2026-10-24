# 自研 Workflow 引擎

---

## 1. 核心三张表

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

## 7. 已撤销/已驳回流程手动删除

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
## 9. 字典 + 表单字段 + SpEL 网关 完整链路（2026-10-22 新增）
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

| # | 坑根因 | 教训 | 日期 |
|---|--------|------|------|
| 1 | 审批时 INSERT 新记录 → 同一人同一节点 2~9 条重复 | 审批 **UPDATE** 原待办的 action 字段（null → 值），不 INSERT | 2026-10-21 |
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

---