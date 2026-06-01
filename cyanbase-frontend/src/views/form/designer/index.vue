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
import { updateFormDefinition, getFormDefinitionById ,addFormDefinition} from '@/api/form'
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

const handleSave = async () => {
  if (!formName.value.trim()) {
    ElMessage.error('请输入表单名称');
    return;
  }

  // 获取设计器配置
  const rule = designerRef.value?.getRule() || [];
  const layoutConfig = JSON.stringify(rule);

  // 构建提交数据
  const saveData = {
    id: formId.value || null,
    formName: formName.value.trim(),
    layoutConfig: layoutConfig,
  };

  try {
    if (formId.value) {
      // 编辑更新
      await updateFormDefinition(saveData);
    } else {
      // 新增
      await addFormDefinition(saveData);
    }

    ElMessage.success('保存成功');
    //router.push('/form/list');
  } catch (err) {
    console.error('保存异常', err);
    ElMessage.error('保存失败');
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