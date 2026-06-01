<template>
  <div class="oper-log-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>操作日志</span>
          <el-button type="danger" @click="handleClean">
            <el-icon><Delete /></el-icon> 清空日志
          </el-button>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="操作人员">
          <el-input v-model="query.operName" placeholder="请输入操作人员" clearable />
        </el-form-item>
        <el-form-item label="操作模块">
          <el-input v-model="query.title" placeholder="请输入操作模块" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="list" border stripe highlight-current-row>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="操作模块" min-width="150" />
        <el-table-column label="操作类型">
          <template #default="{ row }">
            <el-tag :type="getTagType(row.businessType)">
              {{ getBusinessText(row.businessType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operName" label="操作人员" width="120" />
        <el-table-column prop="operIp" label="操作IP" width="150" />
        <el-table-column prop="operLocation" label="操作地点" width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operTime" label="操作时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleView(row)">详情</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
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

    <!-- 详情弹窗 -->
    <el-dialog v-model="dialogVisible" title="操作日志详情" width="80%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作模块">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ detail.businessType }}</el-descriptions-item>
        <el-descriptions-item label="操作人员">{{ detail.operName }}</el-descriptions-item>
        <el-descriptions-item label="操作IP">{{ detail.operIp }}</el-descriptions-item>
        <el-descriptions-item label="操作地点">{{ detail.operLocation }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detail.operTime }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ detail.requestMethod }}</el-descriptions-item>
        <el-descriptions-item label="请求URL" :span="2">{{ detail.operUrl }}</el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <pre>{{ detail.operParam }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="返回参数" :span="2">
          <pre>{{ detail.jsonResult }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="错误消息" :span="2">
          <pre style="color: red;">{{ detail.errorMsg }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOperLogList, deleteOperLog, cleanOperLog } from '@/api/operLog'

const query = reactive({
  operName: '',
  title: ''
})

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const detail = ref({})

const fetchData = async () => {
  try {
    const res = await getOperLogList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...query
    })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error('获取操作日志列表失败', error)
  }
}

const handleView = (row) => {
  detail.value = row
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该日志吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteOperLog(row.id)
    ElMessage.success('删除成功')
    fetchData()
  }).catch(() => {})
}

const handleClean = () => {
  ElMessageBox.confirm('确定要清空所有操作日志吗？此操作不可恢复！', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await cleanOperLog()
    ElMessage.success('清空成功')
    fetchData()
  }).catch(() => {})
}

const handleReset = () => {
  query.operName = ''
  query.title = ''
  pageNum.value = 1
  fetchData()
}

const getBusinessTypeName = (type) => {
  const map = {
    0: '查询',
    1: '新增',
    2: '修改',
    3: '删除'
  }
  return map[type] || '其他'
}

// 操作类型文字映射
const getBusinessText = (type) => {
  const map = {
    0: '查询',
    1: '新增',
    2: '修改',
    3: '删除'
  }
  return map[type] || '其他'
}

// 标签颜色
const getTagType = (type) => {
  const map = {
    0: '',
    1: 'success',
    2: 'warning',
    3: 'danger'
  }
  return map[type] || ''
}

fetchData()
</script>

<style scoped>
.oper-log-container {
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