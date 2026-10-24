<!-- src/views/form/designer/index.vue -->
<template>
  <div class="designer-page">
    <div class="header-bar">
      <el-button @click="$router.back()">← 返回列表</el-button>
      <span class="title">表单可视化设计器</span>
      <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
      <!-- 在 <div class="header-bar"> 内部添加 -->
        <el-input
        v-model="formName"  
        placeholder="请输入表单名称"
        style="width: 200px; margin-right: 10px;"
        />
    </div>
    <!-- v3 designer 组件 -->
    <fc-designer ref="designerRef" height="80vh" />
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick} from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { parseLayoutConfig } from '@/plugins/formCreate'
import { updateFormDefinition, getFormDefinitionById, addFormDefinition, batchSaveFormFields } from '@/api/form'
// TODO: 替换为您项目中实际的表单API
// import { saveForm, getFormDetail } from '@/api/form'

const route = useRoute()
const designerRef = ref(null)
const saving = ref(false)
const formId = ref(route.query.id);
const formAction = ref(route.query.action);
const formName = ref('');
const formCode = ref('');

onMounted(async () => {
  const id = route.query.id;
  const action = route.query.action;
  const routeName = route.query.name;

  if (id) formId.value = id;

  if (action === "edit") {
    if (!id) return;

    try {
      const res = await getFormDefinitionById(id);
      const data = res.data;

      // 名称回显
      formName.value = routeName || data.formName;

      // 代码回显
      if (data.formCode) {
        formCode.value = data.formCode;
      }

      // 表单配置回显（真正修复）
      if (data.layoutConfig) {
        const cfg = JSON.parse(data.layoutConfig);
        const rules = Array.isArray(cfg) ? cfg : cfg.fields || [];

        setTimeout(() => {
          if (designerRef.value) {
            designerRef.value.setRule(rules);
            console.log("✅ 表单回显成功！");
          }
        }, 260);
      }
    } catch (e) {
      console.error(e);
    }
  } else {
    if (routeName) {
      formName.value = routeName;
    } else {
      ElMessage.warning("未检测到表单名称，请通过列表页新建");
    }
  }
});

/**
 * fc-designer 组件类型 → FormField.fieldType 映射
 */
const COMPONENT_TYPE_MAP = {
  input: 'input', textarea: 'textarea', inputNumber: 'number',
  select: 'select', radio: 'radio', checkbox: 'checkbox',
  datePicker: 'date', timePicker: 'time',
  cascader: 'cascader', switch: 'switch', slider: 'slider',
  rate: 'rate', colorPicker: 'color', upload: 'file',
  elButton: 'button', elAlert: 'alert', elDivider: 'divider'
}

/**
 * 递归解析 fc-designer rule → FormField 列表
 * 跳过纯布局组件（group/tab/row 等没有 field 的容器）
 */
/**
 * ⭐ normalize fc-designer rule：把 fc-designer 生成的 rule 改成 form-create 能正确渲染的格式
 * fc-designer 的怪癖：所有输入类组件顶层 type 都是 "input"，
 *   真实子类型藏在 props.type 里（number / textarea / text / password ...）
 * form-create 只认顶层 type —— type="input" 就渲染 el-input（存字符串）
 *   type="inputNumber" 才渲染 el-input-number（存 Number）
 * 所以必须把 props.type="number" 的改成顶层 type="inputNumber"！
 */
const normalizeFcRule = (rule) => {
  const walk = (items) => {
    if (!Array.isArray(items)) return
    items.forEach(item => {
      const topType = item.type || item.component || 'input'
      
      // fc-designer 特判：顶层是 input 时，看 props.type 决定真实组件
      if (topType === 'input' && item.props?.type) {
        const subType = item.props.type
        if (subType === 'number') {
          item.type = 'inputNumber'
        } else if (subType === 'textarea') {
          item.type = 'textarea'
        }
        // text / password / email / date / textarea 等保持 input
      }
      
      // 递归处理嵌套容器
      if (Array.isArray(item.children)) walk(item.children)
      if (Array.isArray(item.cols)) walk(item.cols)
      if (Array.isArray(item.rows)) walk(item.rows)
    })
  }
  walk(rule)
  return rule
}

/**
 * 根据 fc-designer 的 rule 节点推断真实组件类型（用于 extractFormFields 存数据库）
 */
const resolveComponentType = (item) => {
  const topType = item.type || item.component || 'input'
  
  // fc-designer 特判：顶层是 input 时，看 props.type
  if (topType === 'input' && item.props?.type) {
    const subType = item.props.type
    if (subType === 'number')  return 'inputNumber'
    if (subType === 'textarea') return 'textarea'
  }
  
  return topType
}

const extractFormFields = (rule) => {
  const fields = []
  const walk = (items) => {
    if (!Array.isArray(items)) return
    items.forEach(item => {
      // 有 field + title 才是可业务字段
      if (item.field && item.title && !item.type?.startsWith('el-')) {
        const componentType = resolveComponentType(item)
        const fieldType = COMPONENT_TYPE_MAP[componentType] || componentType
        fields.push({
          fieldKey: item.field,
          fieldName: item.title,
          fieldType: fieldType,
          required: Array.isArray(item.validate) && item.validate.some(v => v.required) ? 1 : 0,
          options: item.options ? JSON.stringify(item.options) : null,
          sortOrder: fields.length + 1
        })
      }
      // 递归处理 children（group/tab/row 容器内的子组件）
      if (Array.isArray(item.children) && item.children.length) {
        walk(item.children)
      }
      // fc-designer 有些嵌套在 col / cell 里
      if (Array.isArray(item.cols) && item.cols.length) walk(item.cols)
      if (Array.isArray(item.rows) && item.rows.length) walk(item.rows)
    })
  }
  walk(rule)
  return fields
}

const handleSave = async () => {
  if (!formName.value.trim()) {
    ElMessage.error('请输入表单名称');
    return;
  }

  saving.value = true
  try {
    // 1. 获取设计器规则
    const rawRule = designerRef.value?.getRule() || []
    
    // ⭐ 先 normalize：把 fc-designer 顶层 type="input" + props.type="number" 改成 type="inputNumber"
    //    这样 form-create 渲染时就是 el-input-number（v-model 天生 Number）
    const rule = normalizeFcRule(rawRule)
    
    const layoutConfig = JSON.stringify(rule)
    const fields = extractFormFields(rule)

    // 2. 保存表单定义（新增需拿返回的 ID）
    let definitionId = formId.value
    if (formId.value) {
      await updateFormDefinition({ id: definitionId, formName: formName.value.trim(), layoutConfig })
    } else {
      const res = await addFormDefinition({ formName: formName.value.trim(), layoutConfig })
      definitionId = res.data   // 后端返回 R.ok(form.getId())
      formId.value = definitionId
    }

    // 3. 同步保存字段到 form_field 表（供工作流设计器拉取）
    if (definitionId) {
      await batchSaveFormFields(definitionId, fields)
      console.log(`✅ 字段元数据同步成功: ${fields.length} 个字段已入库`)
    }

    ElMessage.success(`保存成功，已同步 ${fields.length} 个字段`)
  } catch (err) {
    console.error('保存异常', err)
    ElMessage.error('保存失败: ' + (err?.message || '未知错误'))
  } finally {
    saving.value = false
  }
};
const createNewFormDefinition = async (data) => {
    // 这里需要替换成你实际的创建API调用
    // 例如：return await api.createForm(data);
    return await api.addFormDefinition(data);
    console.log("准备创建新表单:", data);
    //throw new Error(addFormDefinitionError);
};
</script>

<style scoped>
.designer-page { height: 100vh; display: flex; flex-direction: column; }
.header-bar {
  padding: 12px 20px;
  border-bottom: 1px solid #ebeef5;
  display: flex; align-items: center; gap: 16px;
  background: #fff; flex-shrink: 0;
}
.title { font-size: 16px; font-weight: 600; }
.designer-body { flex: 1; overflow: hidden; }
</style>