<template>
  <div class="sydyd-execute-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>三重一大 - 实施落地</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 手动新增
          </el-button>
        </div>
      </template>

      <el-form :inline="!isMobile" class="search-form">
        <el-form-item label="事项名称">
          <el-input v-model="query.itemName" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="执行部门">
          <el-input v-model="query.executeDept" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="实施状态">
          <el-select v-model="query.executeStatus" placeholder="全部" clearable style="width: 140px">
            <el-option label="待执行" :value="1" />
            <el-option label="执行中" :value="2" />
            <el-option label="已完成" :value="3" />
            <el-option label="已逾期" :value="4" />
            <el-option label="已归档" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-scroll-wrapper">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="itemName" label="事项名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="meetingName" label="来源会议" min-width="160" show-overflow-tooltip />
        <el-table-column prop="executeDept" label="执行部门" width="120" />
        <el-table-column prop="responsible" label="责任人" width="100" />
        <el-table-column prop="progress" label="进度" width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :stroke-width="12" />
          </template>
        </el-table-column>
        <el-table-column prop="planFinishTime" label="计划完成" width="170" />
        <el-table-column prop="actualFinishTime" label="实际完成" width="170" />
        <el-table-column prop="executeStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.executeStatus)">
              {{ statusMap[row.executeStatus] || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="workflowInstanceId" label="流程实例" width="100">
          <template #default="{ row }">
            <span v-if="row.workflowInstanceId">#{{ row.workflowInstanceId }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleView(row)">详情</el-button>
            <el-button
              v-if="row.executeStatus === 1"
              type="success" link @click="handleStart(row)"
            >开始执行</el-button>
            <el-button
              v-if="row.executeStatus === 2"
              type="warning" link @click="handleFinish(row)"
            >标记完成</el-button>
            <el-button
              v-if="row.executeStatus === 3"
              type="primary" link @click="handleArchive(row)"
            >归档</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="formVisible" title="手动新增实施任务" :width="isMobile ? '92%' : '600px'" @close="handleFormClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="事项名称" prop="itemName">
          <el-input v-model="formData.itemName" placeholder="请输入事项名称" />
        </el-form-item>
        <el-form-item label="执行部门" prop="executeDept">
          <el-input v-model="formData.executeDept" placeholder="如：技术部" />
        </el-form-item>
        <el-form-item label="责任人" prop="responsible">
          <el-input v-model="formData.responsible" placeholder="请输入责任人姓名" />
        </el-form-item>
        <el-form-item label="计划完成时间" prop="planFinishTime">
          <el-date-picker
            v-model="formData.planFinishTime" type="datetime"
            placeholder="选择时间" style="width: 100%"
            format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="关联议题ID">
          <el-input-number v-model="formData.meetingTopicId" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="finishDialogVisible" title="更新执行进度" :width="isMobile ? '92%' : '500px'">
      <el-form :model="finishForm" label-width="100px">
        <el-form-item label="完成情况">
          <el-input v-model="finishForm.progressDetail" type="textarea" :rows="3" placeholder="请描述完成情况" />
        </el-form-item>
        <el-form-item label="佐证材料">
          <el-input v-model="finishForm.evidenceFile" placeholder="附件路径（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="finishDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmFinish">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getExecuteList, createExecute, startExecute, finishExecute, archiveExecute
} from '@/api/sydyd'

const router = useRouter()

// ⭐ 响应式检测
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

const statusMap = { 1: '待执行', 2: '执行中', 3: '已完成', 4: '已逾期', 5: '已归档' }
const getStatusType = (s) => ({ 1: 'info', 2: 'warning', 3: 'success', 4: 'danger', 5: 'primary' }[s] || '')

const query = reactive({ itemName: '', executeDept: '', executeStatus: null })
const loading = ref(false)
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })

const formVisible = ref(false)
const formRef = ref(null)
const formData = reactive({
  itemName: '', executeDept: '', responsible: '',
  planFinishTime: null, meetingTopicId: null
})
const formRules = {
  itemName: [{ required: true, message: '请输入事项名称', trigger: 'blur' }],
  executeDept: [{ required: true, message: '请输入执行部门', trigger: 'blur' }],
  responsible: [{ required: true, message: '请输入责任人', trigger: 'blur' }]
}

const finishDialogVisible = ref(false)
const finishForm = reactive({ id: null, progressDetail: '', evidenceFile: '' })

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getExecuteList({
      pageNum: pagination.current, pageSize: pagination.size, ...query
    })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

const handleAdd = () => {
  Object.assign(formData, {
    itemName: '', executeDept: '', responsible: '',
    planFinishTime: null, meetingTopicId: null
  })
  formVisible.value = true
}

const handleFormClose = () => formRef.value?.resetFields()

const handleFormSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await createExecute(formData)
      ElMessage.success('创建成功，工作流已自动发起')
      formVisible.value = false
      fetchData()
    } catch (e) { console.error(e) }
  })
}

const handleView = (row) => {
  router.push({ path: '/sydyd/execute/detail', query: { id: row.id } })
}

const handleStart = (row) => ElMessageBox.confirm('确定开始执行？', '提示', { type: 'warning' })
  .then(async () => { await startExecute(row.id); ElMessage.success('已开始执行'); fetchData() })
  .catch(() => {})

const handleFinish = (row) => {
  finishForm.id = row.id
  finishForm.progressDetail = ''
  finishForm.evidenceFile = ''
  finishDialogVisible.value = true
}

const confirmFinish = async () => {
  try {
    await finishExecute(finishForm.id, finishForm.progressDetail, finishForm.evidenceFile)
    ElMessage.success('已标记完成')
    finishDialogVisible.value = false
    fetchData()
  } catch (e) { console.error(e) }
}

const handleArchive = (row) => ElMessageBox.confirm('确定归档？归档后不可修改。', '提示', { type: 'warning' })
  .then(async () => { await archiveExecute(row.id); ElMessage.success('已归档'); fetchData() })
  .catch(() => {})

const handleReset = () => {
  query.itemName = ''; query.executeDept = ''; query.executeStatus = null
  pagination.current = 1; fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.sydyd-execute-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
.text-muted { color: #999; }
</style>