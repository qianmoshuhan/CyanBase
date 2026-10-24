
# form-create 自定义表单 & fc-designer 隐性约束

---

## 1. fc-designer 组件类型的怪癖

### 1.1 顶层 type 和 props.type 二级分类

fc-designer 对输入类组件做了**两级分类**，这是最大的坑：

```json
// fc-designer 生成的 inputNumber 组件 — 顶层 type 是 "input"！
{
  "type": "input",           // ← 顶层永远是 "input"！
  "field": "amount",
  "title": "金额",
  "props": {
    "type": "number"         // ← 真实子类型藏在 props.type 里！
  },
  "_fc_drag_tag": "input"
}

```

### 1.2 fc-designer → form-create 类型映射表

| 你拖的组件 | 顶层 type | props.type | fc-designer 标记 | 我们应该识别成 |
|---|---|---|---|---|
| 单行输入 | `input` | `text` | `_fc_drag_tag: input` | `input` |
| **数字输入** | `input` | `number` | `_fc_drag_tag: input` | **`inputNumber`** ← 必须特殊处理 |
| 多行文本 | `input` | `textarea` | `_fc_drag_tag: textarea` | `textarea` |
| 密码 | `input` | `password` | `_fc_drag_tag: input` | `input` |
| 邮箱 | `input` | `email` | `_fc_drag_tag: input` | `input` |
| 下拉选择 | `select` | — | `_fc_drag_tag: select` | `select` |
| 单选 | `radio` | — | `_fc_drag_tag: radio` | `radio` |
| 复选 | `checkbox` | — | `_fc_drag_tag: checkbox` | `checkbox` |
| 日期选择 | `datePicker` | — | `_fc_drag_tag: datePicker` | `date` |
| 开关 | `switch` | — | `_fc_drag_tag: switch` | `switch` |
| 分组标题 | `fcTitle` | — | `_fc_drag_tag: fcTitle` | 跳过（非字段） |
| 分割线 | `fcDivider` | — | `_fc_drag_tag: fcDivider` | 跳过 |

### 1.3 normalizeFcRule — 保存前必须执行

```javascript
// fc-designer 保存 layoutConfig 前必须先 normalize：
// 把 type="input" + props.type="number" → 改成 type="inputNumber"
// 把 type="input" + props.type="textarea" → 改成 type="textarea"
// 否则 form-create 渲染时 type="input" 就是 el-input（存字符串）！

const normalizeFcRule = (rule) => {
  const walk = (items) => {
    items.forEach(item => {
      if (item.type === 'input' && item.props?.type) {
        const subType = item.props.type
        if (subType === 'number')    item.type = 'inputNumber'
        else if (subType === 'textarea') item.type = 'textarea'
        // text / password / email 等保持 input
      }
      // 递归嵌套容器
      if (Array.isArray(item.children)) walk(item.children)
      if (Array.isArray(item.cols)) walk(item.cols)
    })
  }
  walk(rule)
  return rule
}
```
### 1.4 为什么必须 normalize？
fc-designer 生成 layoutConfig: type="input" + props.type="number" ↓ 直接给 form-create 渲染: form-create 只认顶层 type → "input" → 渲染 el-input ← 存字符串！ ↓ 用户填 5670000 → 后端收到 variables: {"amount": "5670000"} ← 有引号！ ↓ SpEL "5670000" >= 1000000 → 类型不匹配 → 网关分流炸 💀

normalize 后: type="inputNumber" ↓ form-create 渲染 el-input-number ← v-model 天生是 Number！ ↓ 后端收到 variables: {"amount": 5670000} ← 无引号 ✅ ↓ SpEL 5670000 >= 1000000 = TRUE ✅
---

## 2. form-create v3 API 迁移备忘

| 功能 | v2 API | v3 API | 备注 |
|---|---|---|---|
| 组件引入 | `<form-create :option>` | `<form-create v-model:api="fApi" :rule="rules" />` | v3 用 v-model:api 拿到实例 |
| 取全部数据 | `api.form()` | `api.formData()` 或 `api.form`（属性） | **最常见的坑！** |
| 取部分数据 | `api.form(['a','b'])` | `api.formData(['a','b'])` | |
| 校验 | `api.validate()` | `api.validate()` | 没变 |
| 表单值双向绑定 | `api.form.a = 123` | `api.form.a = 123` | form 是响应式属性 |
| 清空表单 | `api.resetFields()` | `api.resetFields()` | 没变 |

### 兼容写法（start/index.vue 里用）

```javascript
// 同时兼容 v2 和 v3（防御性编程）
const rawData = formApi.formData?.() ?? formApi.form ?? {}

```

---

## 3. form_field 字段元数据同步

### 3.1 为什么需要独立字段表？

layoutConfig JSON 存完整渲染结构，但：

- 工作流网关需要结构化字段做 SpEL 引用

- 字典关联需要知道哪个字段用了哪个 dictTypeCode

- 不能每次都解析整个 JSON 找字段

**所以表单设计器 handleSave 必须同步两件事：**

```javascript
const rule = normalizeFcRule(designerRef.value?.getRule() || [])
const layoutConfig = JSON.stringify(rule)
const fields = extractFormFields(rule)   // 拆出结构化字段

await updateFormDefinition({ layoutConfig })
await batchSaveFormFields(defId, fields)  // 同步入 form_field 表

```

### 3.2 extractFormFields 识别真实 fieldType

```javascript
const extractFormFields = (rule) => {
  const walk = (items, parent) => {
    return items.flatMap(item => {
      if (!item.field) return []  // fcTitle/fcDivider 等无 field 的跳过
      
      // 用 resolveComponentType 识别真实类型
      const realType = resolveComponentType(item)
      // resolveComponentType 内部逻辑同 normalizeFcRule：
      //   item.type === 'input' && item.props?.type === 'number' → 'inputNumber'
      
      return [{
        fieldKey: item.field,
        fieldName: item.title,
        fieldType: realType,
        dictTypeCode: item.props?.dictType || null,
        required: item.$required ? 1 : 0,
        sortOrder: parent ? parent._children.indexOf(item) : items.indexOf(item)
      }]
    })
  }
  return walk(rule)
}

```

### 3.3 一个坑：fc-designer 组件可能没 fieldKey

fc-designer 默认给组件自动生成类似 `Fwgpmvjh7a9fajc` 的随机 key：

- 用这个随机 key 作为 form_field.field_key

- 用户在 SpEL 里也必须用这个随机 key

- **建议改成语义化名字**（`amount` / `money` / `budget`），不然维护时看到 `Fwgpmvjh7a9fajc >= 1000000` 完全不知道在比什么

---

## 4. 踩坑避坑表

| # | 坑根因 | 教训 | 日期 |
|---|--------|------|------|
| 1 | fc-designer inputNumber 顶层 type 是 "input" 不是 "inputNumber" | 保存前必须 normalizeFcRule 改成 "inputNumber"，否则 form-create 渲染 el-input 存字符串 | 2026-10-22 |
| 2 | form-create v3 取数据用 `api.form()` → `is not a function` | v3 用 `api.formData()` 方法或 `api.form` 属性 | 2026-10-22 |
| 3 | 表单设计器只存 layoutConfig → form_field 表永远空 | handleSave() 必须同步调 batchSaveFormFields() | 2026-10-22 |
| 4 | amount 字段选了 input 不是 inputNumber → SpEL 字符串比较炸 | fc-designer 里金额/数量类必须拖"数字输入"组件，不能拖普通输入框 | 2026-10-22 |
| 5 | fc-designer 随机 fieldKey（如 Fwgpmvjh7a9fajc）→ SpEL 不可读 | 建议手动改成语义化名字（amount/money） | 2026-10-22 |
| 6 | 直接改数据库 wf_node.condition_expr 但前端重新保存又被覆盖 | 要么前端改完保存，要么改数据库后不要再用前端保存这个 definition | 2026-10-22 |
