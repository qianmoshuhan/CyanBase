﻿﻿﻿<template>
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

      <!-- 搜索表单 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="消息类型">
          <el-select v-model="searchForm.messageType" placeholder="全部类型" clearable style="width: 140px">
            <el-option label="系统公告" :value="1" />
            <el-option label="操作通知" :value="2" />
            <el-option label="预警提醒" :value="3" />
            <el-option label="欢迎消息" :value="4" />
            <el-option label="工作流" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="消息标题">
          <el-input v-model="searchForm.title" placeholder="输入标题关键词" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="发送人">
          <el-input v-model="searchForm.senderName" placeholder="输入发送人" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="list" border stripe highlight-current-row>
        <el-table-column prop="title" label="消息标题" min-width="200" />
        <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
        <el-table-column prop="messageType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getMessageTypeColor(row.messageType)">
              {{ getMessageTypeName(row.messageType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="senderName" label="发送人" width="120" />
        <el-table-column prop="isRead" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isRead === 1 ? 'info' : 'danger'">
              {{ row.isRead === 1 ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="发送时间" min-width="170" />
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
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
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
defineOptions({ name: 'MyMessage' })
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyMessageList, markMessageRead, markAllRead, deleteMessage } from '@/api/message'

const filterType = ref('all')
const searchForm = reactive({ messageType: null, title: '', senderName: '' })
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
    // RadioGroup 快速切换已读状态
    if (filterType.value === 'unread') {
      params.isRead = 0
    } else if (filterType.value === 'read') {
      params.isRead = 1
    }
    // 搜索表单参数（空值不传，避免后端收到空字符串）
    if (searchForm.messageType != null) params.messageType = searchForm.messageType
    if (searchForm.title) params.title = searchForm.title
    if (searchForm.senderName) params.senderName = searchForm.senderName
    
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
  const types = { 1: '系统公告', 2: '操作通知', 3: '预警提醒', 4: '欢迎消息', 5: '工作流' }
  return types[type] || '未知'
}

const getMessageTypeColor = (type) => {
  const colors = { 1: 'success', 2: 'warning', 3: 'danger', 4: 'info', 5: 'primary' }
  return colors[type] || ''
}

const handleSearch = () => { pageNum.value = 1; fetchData() }
const handleReset = () => {
  Object.assign(searchForm, { messageType: null, title: '', senderName: '' })
  filterType.value = 'all'
  pageNum.value = 1
  fetchData()
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