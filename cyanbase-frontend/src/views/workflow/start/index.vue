<template>
  <div class="workflow-start-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>发起流程</span>
        </div>
      </template>

      <!-- 流程选择 -->
      <div class="process-select">
        <el-form :inline="true" class="search-form">
          <el-form-item label="流程名称">
            <el-input v-model="processQuery.keyword" placeholder="请输入流程名称" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="fetchProcessList">查询</el-button>
          </el-form-item>
        </el-form>

        <el-table 
          :data="processList" 
          border 
          stripe 
          highlight-current-row
          @row-click="selectProcess"
          style="margin-bottom: 20px;"
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="流程名称" min-width="200" />
          <el-table-column prop="key" label="流程标识" min-width="180" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'warning'">
                {{ row.status === 'PUBLISHED' ? '已发布' : '未发布' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 表单内容（选中流程后显示） -->
      <div v-if="selectedProcess" class="form-content">
        <el-divider content-position="left">填写【{{ selectedProcess.name }}】表单</el-divider>
        
        <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
          <!-- 动态渲染表单字段（根据流程定义的表单配置） -->
          <el-form-item 
            v-for="field in selectedProcess.formFields" 
            :key="field.id"
            :label="field.label"
            :prop="field.prop"
          >
            <component 
              :is="getComponent(field.type)" 
              v-model="formData[field.prop]"
              :placeholder="`请输入${field.label}`"
              :options="field.options || []"
              clearable
            />
          </el-form-item>

          <el-form-item label="备注">
            <el-input v-model="formData.remark" type="textarea" rows="4" placeholder="请输入备注（选填）" />
          </el-form-item>
        </el-form>

        <div class="submit-btn" style="margin-top: 20px; text-align: right;">
          <el-button @click="resetForm">重置</el-button>
          <el-button type="primary" @click="handleSubmit">提交流程</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
// 导入工作流API（已完成）
import { getWorkflowDefinitionList, startWorkflow } from '@/api/wfDefinition'

// 流程查询条件
const processQuery = reactive({ keyword: '' })

// 流程列表
const processList = ref([])
// 选中的流程
const selectedProcess = ref(null)

// 表单数据
const formRef = ref(null)
const formData = reactive({
  remark: ''
})
// 表单规则（动态生成）
const formRules = ref({})

// 获取流程列表
const fetchProcessList = async () => {
  try {
    const res = await getWorkflowDefinitionList({
      keyword: processQuery.keyword,
      status: 'PUBLISHED' // 只显示已发布的流程
    })
    if (res.code === 200) {
      processList.value = res.data.list
    } else {
      ElMessage.error(res.msg || '获取流程列表失败')
    }
  } catch (error) {
    ElMessage.error('网络异常，请稍后重试')
    console.error('获取流程列表失败：', error)
  }
}

// 选择流程
const selectProcess = (row) => {
  if (row.status !== 'PUBLISHED') {
    ElMessage.warning('该流程未发布，无法发起')
    return
  }
  selectedProcess.value = row
  
  // 初始化表单数据和规则
  formData.remark = ''
  formRules.value = {}
  
  // 动态生成表单数据和验证规则
  row.formFields.forEach(field => {
    // 初始化字段值
    formData[field.prop] = ''
    // 生成验证规则
    if (field.required) {
      formRules.value[field.prop] = [{ 
        required: true, 
        message: `请输入${field.label}`, 
        trigger: field.type === 'select' ? 'change' : 'blur' 
      }]
    }
  })
}

// 根据字段类型获取对应组件
const getComponent = (type) => {
  switch (type) {
    case 'input': return 'el-input'
    case 'select': return 'el-select'
    case 'radio': return 'el-radio-group'
    case 'checkbox': return 'el-checkbox-group'
    case 'date': return 'el-date-picker'
    case 'number': return 'el-input-number'
    default: return 'el-input'
  }
}

// 重置表单
const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  formData.remark = ''
}

// 提交流程
const handleSubmit = async () => {
  if (!selectedProcess.value) {
    ElMessage.warning('请先选择要发起的流程')
    return
  }
  if (!formRef.value) return
  
  try {
    // 表单验证
    await formRef.value.validate()
    
    // 构建提交参数
    const params = {
      processDefinitionId: selectedProcess.value.id,
      processKey: selectedProcess.value.key,
      formData: formData,
      remark: formData.remark
    }
    
    // 确认提交
    await ElMessageBox.confirm(
      '确定要发起该流程吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    
    // 调用发起流程API
    const res = await startWorkflow(params)
    if (res.code === 200) {
      ElMessage.success('流程发起成功')
      // 重置状态
      selectedProcess.value = null
      resetForm()
    } else {
      ElMessage.error(res.msg || '流程发起失败')
    }
  } catch (error) {
    if (error.name !== 'ValidationError' && error !== 'cancel') {
      ElMessage.error('网络异常，请稍后重试')
      console.error('发起流程失败：', error)
    }
  }
}

// 初始化加载流程列表
fetchProcessList()
</script>

<style scoped>
.workflow-start-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.process-select { margin-bottom: 30px; }
.form-content { border-top: 1px solid #eee; padding-top: 20px; }
.submit-btn { margin-top: 20px; text-align: right; }
</style>