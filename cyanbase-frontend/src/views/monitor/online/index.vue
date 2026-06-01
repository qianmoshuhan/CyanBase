<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>在线用户</span>
          <el-button type="danger" @click="handleRefresh">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="登录账号">
          <el-input v-model="searchForm.loginName" placeholder="请输入登录账号" clearable />
        </el-form-item>
        <el-form-item label="IP地址">
          <el-input v-model="searchForm.ipaddr" placeholder="请输入IP地址" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="sessionId" label="会话ID" min-width="200" show-overflow-tooltip />
        <el-table-column prop="loginName" label="登录账号" min-width="120" />
        <el-table-column prop="deptName" label="部门名称" min-width="120" />
        <el-table-column prop="ipaddr" label="登录IP" min-width="140" />
        <el-table-column prop="loginLocation" label="登录地点" min-width="150" />
        <el-table-column prop="browser" label="浏览器" min-width="100" />
        <el-table-column prop="os" label="操作系统" min-width="100" />
        <el-table-column prop="loginTime" label="登录时间" min-width="160" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link @click="handleForceOffline(row)">强制下线</el-button>
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
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOnlineList, forceOffline } from '@/api/online'

const searchForm = reactive({ loginName: '', ipaddr: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })

const fetchData = async () => {
  try {
    const res = await getOnlineList({ current: pagination.current, size: pagination.size, ...searchForm })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error(error) }
}

const handleForceOffline = (row) => {
  ElMessageBox.confirm(`确定要强制下线用户「${row.loginName}」吗？`, '提示', { type: 'warning' })
    .then(async () => { await forceOffline(row.sessionId); ElMessage.success('强制下线成功'); fetchData() }).catch(() => {})
}

const handleRefresh = () => fetchData()
const handleReset = () => { Object.assign(searchForm, { loginName: '', ipaddr: '' }); fetchData() }

fetchData()
</script>

<style scoped>
.page-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
