<!-- src/views/form/dataList/FormPreviewDialog.vue -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="表单预览"
    width="700px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="rules.length" class="preview-container">
      <!-- v3 API: v-model:api 绑定实例，:rule 传入规则 -->
      <form-create v-model:api="fApi" :rule="rules" :option="options" />
    </div>
    <el-empty v-else description="暂无表单配置或配置格式无法识别" />
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { parseLayoutConfig } from '@/plugins/formCreate'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  configJson: { type: String, default: '' }
})

defineEmits(['update:modelValue'])

const fApi = ref(null)
const rules = ref([])
const options = {
  submitBtn: false,
  resetBtn: false
}

watch(() => props.configJson, (val) => {
  rules.value = parseLayoutConfig(val)
}, { immediate: true })
</script>

<style scoped>
.preview-container {
  max-height: 60vh;
  overflow-y: auto;
  padding: 10px 0;
}
</style>