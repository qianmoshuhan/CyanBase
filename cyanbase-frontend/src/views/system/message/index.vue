<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>消息管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 发送消息
          </el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="消息类型">
          <el-select v-model="searchForm.msgType" placeholder="请选择类型" clearable>
            <el-option label="通知" value="1" />
            <el-option label="消息" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否已读">
          <el-select v-model="searchForm.isRead" placeholder="请选择状态" clearable>
            <el-option label="未读" value="0" />
            <el-option label="已读" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="msgId" label="消息ID" width="100" />
        <el-table-column prop="msgTitle" label="消息标题" min-width="200" />
        <el-table-column prop="msgType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.msgType === '1' ? 'success' : 'warning'">
              {{ row.msgType === '1' ? '通知' : '消息' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isRead" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isRead === '1' ? 'info' : 'danger'">
              {{ row.isRead === '1' ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createBy" label="发送人" width="120" />
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
          @size-change="fetchData"
          @current-change="fetchData"
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
        <el-descriptions-item label="消息标题">{{ viewData.msgTitle }}</el-descriptions-item>
        <el-descriptions-item label="消息类型">
          <el-tag :type="viewData.msgType === '1' ? 'success' : 'warning'">
            {{ viewData.msgType === '1' ? '通知' : '消息' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发送人">{{ viewData.createBy }}</el-descriptions-item>
        <el-descriptions-item label="发送时间">{{ viewData.createTime }}</el-descriptions-item>
        <el-descriptions-item label="消息内容">{{ viewData.msgContent }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyMessageList, addMessage, getMessageById, markMessageRead, markAllRead, deleteMessage } from '@/api/message'


const searchForm = reactive({ msgType: '', isRead: '' })
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
    const res = await getMyMessageList({ current: pagination.current, size: pagination.size, ...searchForm })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error(error) }
}

const handleAdd = () => { Object.assign(formData, { msgId: null, msgTitle: '', msgType: '1', toUserId: 1, msgContent: '' }); dialogVisible.value = true }
const handleView = async (row) => { viewData.value = row; viewVisible.value = true; if (row.isRead === '0') await markMessageRead(row.msgId); fetchData() }
const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该消息吗？', '提示', { type: 'warning' })
    .then(async () => { await deleteMessage(row.msgId); ElMessage.success('删除成功'); fetchData() }).catch(() => {})
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
const handleReset = () => { Object.assign(searchForm, { msgType: '', isRead: '' }); fetchData() }

fetchData()
</script>

<style scoped>
.page-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
