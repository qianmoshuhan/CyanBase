<template>
  <div class="workflow-todo-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>我的待办</span>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="流程名称">
          <el-input v-model="query.processName" placeholder="请输入流程名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="list" border stripe highlight-current-row>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="processName" label="流程名称" min-width="150" />
        <el-table-column prop="nodeName" label="当前节点" min-width="120" />
        <el-table-column prop="startUserName" label="发起人" width="120" />
        <el-table-column prop="startTime" label="发起时间" width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusColor(row.status)">
              {{ getStatusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleApprove(row)">审批</el-button>
            <el-button type="warning" link @click="handleView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </el-card>

    <!-- 审批弹窗 -->
    <el-dialog v-model="approveDialogVisible" title="流程审批" width="60%">
      <el-form ref="approveFormRef" :model="approveForm" label-width="100px">
        <el-form-item label="审批意见">
          <el-input v-model="approveForm.comment" type="textarea" :rows="4" placeholder="请输入审批意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button type="success" @click="handleApproveSubmit(true)">通过</el-button>
        <el-button type="danger" @click="handleApproveSubmit(false)">驳回</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="流程详情" width="80%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="流程名称">{{ detail.processName }}</el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ detail.nodeName }}</el-descriptions-item>
        <el-descriptions-item label="发起人">{{ detail.startUserName }}</el-descriptions-item>
        <el-descriptions-item label="发起时间">{{ detail.startTime }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusColor(detail.status)">
            {{ getStatusName(detail.status) }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyTodoList, approveWorkflow } from '@/api/wfDefinition'

const query = reactive({
  processName: ''
})

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const approveDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const currentRow = ref(null)

const approveFormRef = ref(null)
const approveForm = reactive({
  comment: ''
})

const detail = ref({})

const fetchData = async () => {
  try {
    const res = await getMyTodoList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...query
    })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error('获取待办列表失败', error)
  }
}

const handleApprove = (row) => {
  currentRow.value = row
  approveForm.comment = ''
  approveDialogVisible.value = true
}

const handleApproveSubmit = async (approved) => {
  if (!currentRow.value) return
  
  try {
    await approveWorkflow({
      instanceId: currentRow.value.id,
      approved: approved,
      comment: approveForm.comment
    })
    ElMessage.success(approved ? '审批通过' : '已驳回')
    approveDialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error('审批失败', error)
  }
}

const handleView = (row) => {
  detail.value = row
  detailDialogVisible.value = true
}

const getStatusName = (status) => {
  const statuses = {
    0: '待审批',
    1: '已通过',
    2: '已驳回',
    3: '已撤销'
  }
  return statuses[status] || '未知'
}

const getStatusColor = (status) => {
  const colors = {
    0: 'warning',
    1: 'success',
    2: 'danger',
    3: 'info'
  }
  return colors[status] || ''
}

const handleReset = () => {
  query.processName = ''
  pageNum.value = 1
  fetchData()
}

fetchData()
</script>

<style scoped>
.workflow-todo-container {
  width: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}
</style>