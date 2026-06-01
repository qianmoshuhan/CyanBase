<template>
  <div class="my-message-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>我的消息</span>
          <div>
            <el-button type="success" @click="handleMarkAllRead">全部已读</el-button>
            <el-radio-group v-model="filterType" size="small" style="margin-left: 10px;">
              <el-radio-button label="all">全部</el-radio-button>
              <el-radio-button label="unread">未读</el-radio-button>
              <el-radio-button label="read">已读</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table :data="list" border stripe highlight-current-row>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
        <el-table-column prop="messageType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getMessageTypeColor(row.messageType)">
              {{ getMessageTypeName(row.messageType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isRead" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isRead === 1 ? 'info' : 'success'">
              {{ row.isRead === 1 ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleView(row)">查看</el-button>
            <el-button v-if="row.isRead === 0" type="warning" link @click="handleMarkRead(row)">标为已读</el-button>
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

    <!-- 消息详情弹窗 -->
    <el-dialog v-model="dialogVisible" title="消息详情" width="60%">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="getMessageTypeColor(detail.messageType)">
            {{ getMessageTypeName(detail.messageType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发送时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="内容">
          <div style="white-space: pre-wrap;">{{ detail.content }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyMessageList, markMessageRead, markAllRead, deleteMessage } from '@/api/message'

const filterType = ref('all')
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const detail = ref({})

const fetchData = async () => {
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    
    if (filterType.value === 'unread') {
      params.isRead = 0
    } else if (filterType.value === 'read') {
      params.isRead = 1
    }
    
    const res = await getMyMessageList(params)
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error('获取消息列表失败', error)
  }
}

const handleView = (row) => {
  detail.value = row
  dialogVisible.value = true
  
  // 如果未读，自动标记为已读
  if (row.isRead === 0) {
    markMessageRead(row.id).then(() => {
      row.isRead = 1
      fetchData()
    })
  }
}

const handleMarkRead = async (row) => {
  await markMessageRead(row.id)
  ElMessage.success('已标记为已读')
  fetchData()
}

const handleMarkAllRead = () => {
  ElMessageBox.confirm('确定要将所有消息标记为已读吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info'
  }).then(async () => {
    await markAllRead()
    ElMessage.success('已全部标记为已读')
    fetchData()
  }).catch(() => {})
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该消息吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteMessage(row.id)
    ElMessage.success('删除成功')
    fetchData()
  }).catch(() => {})
}

const getMessageTypeName = (type) => {
  const types = {
    0: '系统通知',
    1: '审批通知',
    2: '任务提醒',
    3: '其他'
  }
  return types[type] || '未知'
}

const getMessageTypeColor = (type) => {
  const colors = {
    0: '',
    1: 'warning',
    2: 'success',
    3: 'info'
  }
  return colors[type] || ''
}

watch(filterType, () => {
  pageNum.value = 1
  fetchData()
})

fetchData()
</script>

<style scoped>
.my-message-container {
  width: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>