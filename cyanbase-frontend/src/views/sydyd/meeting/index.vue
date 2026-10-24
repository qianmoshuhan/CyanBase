<template>
  <div class="sydyd-meeting-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>三重一大 - 会议管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新建会议
          </el-button>
        </div>
      </template>

      <el-form :inline="!isMobile" class="search-form">
        <el-form-item label="会议名称">
          <el-input v-model="query.meetingName" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="会议类型">
          <el-select v-model="query.meetingType" placeholder="全部" clearable style="width: 140px">
            <el-option label="党委会" :value="1" />
            <el-option label="总经理办公会" :value="2" />
            <el-option label="董事会" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.meetingStatus" placeholder="全部" clearable style="width: 140px">
            <el-option label="待召开" :value="1" />
            <el-option label="进行中" :value="2" />
            <el-option label="已结束" :value="3" />
            <el-option label="已归档" :value="4" />
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
        <el-table-column prop="meetingName" label="会议名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="meetingType" label="类型" width="130">
          <template #default="{ row }">{{ meetingTypeMap[row.meetingType] || '-' }}</template>
        </el-table-column>
        <el-table-column prop="host" label="主持人" width="100" />
        <el-table-column prop="meetingPlace" label="地点" width="140" show-overflow-tooltip />
        <el-table-column prop="meetingTime" label="开会时间" width="170" />
        <el-table-column prop="meetingStatus" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.meetingStatus)">
              {{ statusMap[row.meetingStatus] || '-' }}
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
              v-if="row.meetingStatus === 1"
              type="warning" link @click="handleEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="row.meetingStatus === 1"
              type="success" link @click="handleDelete(row)"
            >删除</el-button>
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

    <el-dialog v-model="formVisible" :title="formTitle" :width="isMobile ? '92%' : '680px'" @close="handleFormClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="会议名称" prop="meetingName">
          <el-input v-model="formData.meetingName" placeholder="如：2026年第3次党委会" />
        </el-form-item>
        <el-form-item label="会议类型" prop="meetingType">
          <el-select v-model="formData.meetingType" placeholder="请选择" style="width: 100%">
            <el-option label="党委会" :value="1" />
            <el-option label="总经理办公会" :value="2" />
            <el-option label="董事会" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="开会时间" prop="meetingTime">
          <el-date-picker
            v-model="formData.meetingTime" type="datetime"
            placeholder="选择开会时间" style="width: 100%"
            format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="主持人" prop="host">
          <el-input v-model="formData.host" placeholder="请输入主持人姓名" />
        </el-form-item>
        <el-form-item label="会议地点">
          <el-input v-model="formData.meetingPlace" placeholder="如：3楼会议室" />
        </el-form-item>
        <el-form-item label="参会人员">
          <el-input v-model="formData.attendees" placeholder="多人用逗号分隔" />
        </el-form-item>
        <el-form-item label="列席人员">
          <el-input v-model="formData.observers" placeholder="多人用逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMeetingList, createMeeting, updateMeeting, deleteMeeting
} from '@/api/sydyd'

const router = useRouter()

// ⭐ 响应式检测
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

const meetingTypeMap = { 1: '党委会', 2: '总经理办公会', 3: '董事会' }
const statusMap = { 1: '待召开', 2: '进行中', 3: '已结束', 4: '已归档' }
const getStatusType = (s) => ({ 1: 'info', 2: 'warning', 3: 'success', 4: 'primary' }[s] || '')

const query = reactive({ meetingName: '', meetingType: null, meetingStatus: null })
const loading = ref(false)
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })

const formVisible = ref(false)
const formIsEdit = ref(false)
const formRef = ref(null)
const formData = reactive({
  id: null, meetingName: '', meetingType: null, meetingTime: null,
  host: '', meetingPlace: '', attendees: '', observers: ''
})
const formTitle = computed(() => formIsEdit.value ? '编辑会议' : '新建会议')
const formRules = {
  meetingName: [{ required: true, message: '请输入会议名称', trigger: 'blur' }],
  meetingType: [{ required: true, message: '请选择会议类型', trigger: 'change' }]
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getMeetingList({
      pageNum: pagination.current,
      pageSize: pagination.size, ...query
    })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

const handleAdd = () => {
  formIsEdit.value = false
  Object.assign(formData, {
    id: null, meetingName: '', meetingType: null, meetingTime: null,
    host: '', meetingPlace: '', attendees: '', observers: ''
  })
  formVisible.value = true
}

const handleEdit = (row) => {
  formIsEdit.value = true
  Object.assign(formData, { ...row })
  formVisible.value = true
}

const handleView = (row) => {
  router.push({ path: '/sydyd/meeting/detail', query: { id: row.id } })
}

const handleFormClose = () => formRef.value?.resetFields()

const handleFormSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (formIsEdit.value) {
        await updateMeeting(formData)
        ElMessage.success('更新成功')
      } else {
        await createMeeting(formData)
        ElMessage.success('创建成功，工作流已自动发起')
      }
      formVisible.value = false
      fetchData()
    } catch (e) { console.error(e) }
  })
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定删除该会议？仅待召开状态允许删除。', '提示', { type: 'warning' })
    .then(async () => {
      await deleteMeeting(row.id)
      ElMessage.success('删除成功')
      fetchData()
    }).catch(() => {})
}

const handleReset = () => {
  query.meetingName = ''; query.meetingType = null; query.meetingStatus = null
  pagination.current = 1; fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.sydyd-meeting-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
.text-muted { color: #999; }
</style>