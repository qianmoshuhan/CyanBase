<template>
  <div class="form-submit">
    <el-card shadow="hover">
      <template #header>
        <span>表单提交</span>
      </template>

      <el-form
        ref="formRef"
        :model="formData"
        label-width="100px"
        class="submit-form"
      >
        <el-form-item label="表单名称" required>
          <el-select v-model="formData.formId" placeholder="请选择表单" clearable>
            <el-option
              v-for="item in formList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="formData.remark"
            type="textarea"
            rows="4"
            placeholder="请输入备注信息"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="onSubmit">提交表单</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getFormDefinitionList, submitFormData } from '@/api/form'

const formRef = ref(null)

// 下拉列表：表单列表
const formList = ref([])

// 提交表单数据
const formData = reactive({
  formId: '',
  remark: ''
})

// 加载表单列表
const loadFormList = async () => {
  try {
    const res = await getFormDefinitionList({ status: 'PUBLISHED' })
    if (res.code === 200) {
      formList.value = res.data.records || res.data.list || []
    }
  } catch (err) {
    console.error('加载表单失败', err)
  }
}

// 提交
const onSubmit = async () => {
  if (!formData.formId) {
    ElMessage.warning('请选择表单')
    return
  }

  try {
    const res = await submitFormData(formData)
    if (res.code === 200) {
      ElMessage.success('表单提交成功！')
      formRef.value?.resetFields()
    } else {
      ElMessage.error(res.msg || '提交失败')
    }
  } catch (err) {
    ElMessage.error('提交异常，请稍后重试')
    console.error(err)
  }
}

// 重置
const onReset = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  loadFormList()
})
</script>

<style scoped>
.form-submit {
  width: 100%;
}

.submit-form {
  margin-top: 20px;
  max-width: 700px;
}
</style>