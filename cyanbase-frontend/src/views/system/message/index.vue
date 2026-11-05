﻿﻿﻿﻿﻿﻿<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>消息管理</span>
          <el-button type="primary" @click="handleAdd" v-hasPermi="['system:message:send']">
            <el-icon><Plus /></el-icon> 发送消息
          </el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="消息类型">
          <el-select v-model="searchForm.messageType" placeholder="请选择类型" clearable style="width: 160px">
            <el-option label="系统公告" :value="1" />
            <el-option label="操作通知" :value="2" />
            <el-option label="预警提醒" :value="3" />
            <el-option label="欢迎消息" :value="4" />
            <el-option label="工作流" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否已读">
          <el-select v-model="searchForm.isRead" placeholder="请选择状态" clearable style="width: 140px">
            <el-option label="未读" :value="0" />
            <el-option label="已读" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="消息标题">
          <el-input v-model="searchForm.title" placeholder="输入标题关键词" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="发送人">
          <el-input v-model="searchForm.senderName" placeholder="输入发送人" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="70" align="center" :index="(i) => (pagination.current - 1) * pagination.size + i + 1" />
        <el-table-column prop="title" label="消息标题" min-width="200" />
        <el-table-column prop="messageType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.messageType === 1 ? 'success' : row.messageType === 2 ? 'warning' : row.messageType === 3 ? 'danger' : row.messageType === 5 ? 'primary' : 'info'">
              {{ row.messageType === 1 ? '系统公告' : row.messageType === 2 ? '操作通知' : row.messageType === 3 ? '预警提醒' : row.messageType === 5 ? '工作流' : '欢迎消息' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isRead" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isRead === 1 ? 'info' : 'danger'">
              {{ row.isRead === 1 ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="senderName" label="发送人" width="120" />
        <el-table-column prop="createTime" label="发送时间" min-width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleView(row)">查看</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="消息标题" prop="msgTitle">
          <el-input v-model="formData.msgTitle" placeholder="请输入消息标题" />
        </el-form-item>
        <el-form-item label="消息类型">
          <el-radio-group v-model="formData.msgType">
            <el-radio label="1">通知</el-radio>
            <el-radio label="2">消息</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="接收用户ID" prop="toUserId">
          <el-input-number v-model="formData.toUserId" :min="1" />
        </el-form-item>
        <el-form-item label="消息内容">
          <el-input v-model="formData.msgContent" type="textarea" rows="4" placeholder="请输入消息内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewVisible" title="消息详情" width="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="消息标题">{{ viewData.title }}</el-descriptions-item>
        <el-descriptions-item label="消息类型">
          <el-tag :type="viewData.messageType === 1 ? 'success' : viewData.messageType === 2 ? 'warning' : viewData.messageType === 3 ? 'danger' : viewData.messageType === 5 ? 'primary' : 'info'">
            {{ viewData.messageType === 1 ? '系统公告' : viewData.messageType === 2 ? '操作通知' : viewData.messageType === 3 ? '预警提醒' : viewData.messageType === 5 ? '工作流' : '欢迎消息' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发送人">{{ viewData.senderName }}</el-descriptions-item>
        <el-descriptions-item label="发送时间">{{ viewData.createTime }}</el-descriptions-item>
        <el-descriptions-item label="消息内容">{{ viewData.content }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'Message' })
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyMessageList, addMessage, getMessageById, markMessageRead, markAllRead, deleteMessage } from '@/api/message'


const searchForm = reactive({ messageType: null, isRead: null, title: '', senderName: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })
const dialogVisible = ref(false)
const viewVisible = ref(false)
const dialogTitle = computed(() => '发送消息')
const isEdit = ref(false)
const formRef = ref(null)
const viewData = ref({})

const formData = reactive({ msgId: null, msgTitle: '', msgType: '1', toUserId: 1, msgContent: '' })
const formRules = {
  msgTitle: [{ required: true, message: '请输入消息标题', trigger: 'blur' }],
  toUserId: [{ required: true, message: '请输入接收用户ID', trigger: 'blur' }]
}

const fetchData = async () => {
  try {
    // 后端 Controller 用 pageNum / pageSize，不是 current / size
    const params = {
      pageNum: pagination.current,
      pageSize: pagination.size,
      messageType: searchForm.messageType || null,
      isRead: searchForm.isRead != null ? searchForm.isRead : null,
      title: searchForm.title || null,
      senderName: searchForm.senderName || null
    }
    const res = await getMyMessageList(params)
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error(error) }
}

const handleAdd = () => { Object.assign(formData, { msgId: null, msgTitle: '', msgType: '1', toUserId: 1, msgContent: '' }); dialogVisible.value = true }
const handleView = async (row) => {
  viewData.value = row
  viewVisible.value = true
  // row.id 现在是正确的后端主键
  if (row.isRead !== 1) {
    await markMessageRead(row.id)
    row.isRead = 1   // 前端乐观更新，不用等 fetchData
  }
}
const handleDelete = async (row) => {
  ElMessageBox.confirm('确定要删除该消息吗？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteMessage(row.id)
      ElMessage.success('删除成功')
      fetchData()
    }).catch(() => {})
}
const handleSubmit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      // 拼接后端需要的参数格式
      const params = {
        title: formData.msgTitle,
        content: formData.msgContent,
        messageType: formData.msgType,
        userIds: [formData.toUserId] // 单个ID包装成数组
      }
      await addMessage(params)
      ElMessage.success('发送成功')
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      console.error(error)
      ElMessage.error('发送失败：' + error.message)
    }
  })
}
const handleDialogClose = () => formRef.value?.resetFields()
// 分页回调：显式先赋值再请求，@current-change 时序早于 v-model update
const handlePageChange = (page) => {
  pagination.current = page
  fetchData()
}
const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  fetchData()
}

const handleReset = () => {
  Object.assign(searchForm, { messageType: null, isRead: null, title: '', senderName: '' })
  pagination.current = 1
  fetchData()
}

fetchData()
</script>

<style scoped>
.page-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>