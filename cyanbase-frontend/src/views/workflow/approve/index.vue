<template>
  <div class="workflow-approve-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>流程审批</span>
        </div>
      </template>

      <!-- 流程基本信息 -->
      <el-descriptions :column="2" border style="margin-bottom: 20px;">
        <el-descriptions-item label="流程名称">{{ processInfo.processName }}</el-descriptions-item>
        <el-descriptions-item label="流程实例ID">{{ processInfo.processInstanceId }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ processInfo.applyUserName }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ processInfo.applyTime }}</el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ processInfo.currentNode }}</el-descriptions-item>
        <el-descriptions-item label="流程状态">
          <el-tag :type="processInfo.status === 'RUNNING' ? 'warning' : 'success'">
            {{ processInfo.status === 'RUNNING' ? '运行中' : '已完成' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 表单数据 -->
      <div class="form-data" style="margin-bottom: 20px;">
        <h4>表单数据：</h4>
        <el-table :data="formDataList" border stripe>
          <el-table-column prop="fieldName" label="字段名称" width="150" />
          <el-table-column prop="fieldValue" label="字段值" min-width="200" />
          <el-table-column prop="fieldDesc" label="说明" />
        </el-table>
      </div>

      <!-- 审批操作 -->
      <div class="approve-form">
        <el-form ref="approveFormRef" :model="approveForm" :rules="approveRules" label-width="100px">
          <el-form-item label="审批结果" prop="result">
            <el-radio-group v-model="approveForm.result">
              <el-radio label="APPROVED">通过</el-radio>
              <el-radio label="REJECTED">驳回</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="审批意见" prop="remark">
            <el-input v-model="approveForm.remark" type="textarea" rows="4" placeholder="请输入审批意见" />
          </el-form-item>
        </el-form>

        <div class="approve-btn" style="margin-top: 20px; text-align: right;">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" @click="handleApprove">提交审批</el-button>
        </div>
      </div>

      <!-- 审批历史 -->
      <div class="approve-history" style="margin-top: 30px;">
        <el-divider content-position="left">审批历史</el-divider>
        <el-timeline>
          <el-timeline-item
            v-for="(item, index) in approveHistory"
            :key="index"
            :timestamp="item.approveTime"
            :color="item.result === 'APPROVED' ? 'green' : item.result === 'REJECTED' ? 'red' : 'blue'"
          >
            <div>
              <strong>{{ item.nodeName }}</strong> - {{ item.approveUserName }}
            </div>
            <div>
              {{ item.result === 'APPROVED' ? '通过' : item.result === 'REJECTED' ? '驳回' : '发起' }}
            </div>
            <div v-if="item.remark">
              意见：{{ item.remark }}
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, useRoute, useRouter } from 'element-plus'
// 导入工作流API（已完成）
import { getProcessInstanceDetail, approveWorkflow } from '@/api/wfDefinition'

// 路由和导航
const route = useRoute()
const router = useRouter()

// 流程信息
const processInfo = ref({})
// 表单数据列表
const formDataList = ref([])
// 审批历史
const approveHistory = ref([])

// 审批表单
const approveFormRef = ref(null)
const approveForm = reactive({
  result: '', // APPROVED:通过，REJECTED:驳回
  remark: ''
})
const approveRules = {
  result: [{ required: true, message: '请选择审批结果', trigger: 'change' }],
  remark: [{ required: true, message: '请输入审批意见', trigger: 'blur' }]
}

// 获取流程详情
const getProcessDetail = async () => {
  const processInstanceId = route.query.processInstanceId
  if (!processInstanceId) {
    ElMessage.error('缺少流程实例ID')
    router.push('/workflow/todo')
    return
  }
  
  try {
    const res = await getProcessInstanceDetail(processInstanceId)
    if (res.code === 200) {
      processInfo.value = res.data.processInfo
      formDataList.value = res.data.formDataList
      approveHistory.value = res.data.approveHistory
    } else {
      ElMessage.error(res.msg || '获取流程详情失败')
      router.push('/workflow/todo')
    }
  } catch (error) {
    ElMessage.error('网络异常，请稍后重试')
    console.error('获取流程详情失败：', error)
    router.push('/workflow/todo')
  }
}

// 提交审批
const handleApprove = async () => {
  if (!approveFormRef.value) return
  
  try {
    // 表单验证
    await approveFormRef.value.validate()
    
    // 确认提交
    await ElMessageBox.confirm(
      `确定要${approveForm.result === 'APPROVED' ? '通过' : '驳回'}该流程吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: approveForm.result === 'APPROVED' ? 'success' : 'warning'
      }
    )
    
    // 调用审批API
    const params = {
      processInstanceId: processInfo.value.processInstanceId,
      taskId: processInfo.value.taskId,
      result: approveForm.result,
      remark: approveForm.remark
    }
    
    const res = await approveWorkflow(params)
    if (res.code === 200) {
      ElMessage.success(`流程${approveForm.result === 'APPROVED' ? '审批通过' : '审批驳回'}成功`)
      router.push('/workflow/todo')
    } else {
      ElMessage.error(res.msg || '审批失败')
    }
  } catch (error) {
    if (error.name !== 'ValidationError' && error !== 'cancel') {
      ElMessage.error('网络异常，请稍后重试')
      console.error('审批流程失败：', error)
    }
  }
}

// 返回
const goBack = () => {
  router.push('/workflow/todo')
}

// 初始化
onMounted(() => {
  getProcessDetail()
})
</script>

<style scoped>
.workflow-approve-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.form-data h4 { margin: 0 0 10px 0; font-size: 14px; color: #333; }
.approve-history { margin-top: 30px; }
</style>