<template>
  <div class="workflow-done-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>我的已办</span>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="!isMobile" class="search-form">
        <el-form-item label="流程名称">
          <el-input v-model="query.processName" placeholder="请输入流程名称" clearable />
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select v-model="query.status" placeholder="请选择审批状态" clearable>
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <div class="table-scroll-wrapper">
      <el-table :data="list" border stripe highlight-current-row>
        <el-table-column type="index" label="序号" width="70" align="center" :index="indexMethod" />
        <el-table-column prop="itemName" label="事项名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.itemName">{{ row.itemName }}</span>
            <span v-else-if="row.processName">{{ row.processName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="processName" label="流程名称" min-width="150" />
        <el-table-column prop="currentNodeName" label="最后审批节点" width="140" />
        <el-table-column prop="initiatorName" label="发起人" width="120" />
        <el-table-column prop="startTime" label="发起时间" width="180" />
        <el-table-column prop="endTime" label="结束时间" width="180" />
        <el-table-column prop="status" label="流程状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusColor(row.status)">
              {{ getStatusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- 分页 -->
      <el-pagination
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="流程详情" width="70%">
      <div v-if="currentRow" class="detail-content">
        <el-descriptions :column="isMobile ? 1 : 2" border>
          <el-descriptions-item label="流程名称">{{ currentRow.processName }}</el-descriptions-item>
          <el-descriptions-item label="流程ID">{{ currentRow.id }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ currentRow.initiatorName }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ currentRow.startTime }}</el-descriptions-item>
          <el-descriptions-item label="业务键">{{ currentRow.businessKey }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusColor(currentRow.status)">
              {{ getStatusName(currentRow.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
// 导入工作流API（已完成）
import { getMyDoneList } from '@/api/wfDefinition'

// ⭐ 响应式检测
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

// 查询条件
const query = reactive({
  processName: '',
  status: '',
  dateRange: [],
  keyword: ''
})

// 列表数据
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

// 详情弹窗
const detailVisible = ref(false)
const currentRow = ref(null)

// 获取列表数据
const fetchData = async () => {
  try {
    const res = await getMyDoneList({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    ElMessage.error('获取数据失败')
  }
}

const getStatusName = (status) => ({
  running: '审批中',
  completed: '已完成',
  rejected: '已驳回',
  cancelled: '已撤销'
}[status] || status)

const getStatusColor = (status) => ({
  running: 'warning',
  completed: 'success',
  rejected: 'danger',
  cancelled: 'info'
}[status] || '')

// 查看详情
const handleView = (row) => {
  currentRow.value = row
  detailVisible.value = true
}

// 重置搜索
const handleReset = () => {
  query.processName = ''
  query.status = ''
  query.dateRange = []
  pageNum.value = 1
  fetchData()
}

// 序号生成器：根据当前页码和每页大小计算全局序号
const indexMethod = (index) => {
  return (pageNum.value - 1) * pageSize.value + index + 1
}

// 初始化加载
fetchData()
</script>

<style scoped>
.workflow-done-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.detail-content { line-height: 1.6; }
.form-data h4 { margin: 0 0 10px 0; font-size: 14px; color: #333; }
</style>