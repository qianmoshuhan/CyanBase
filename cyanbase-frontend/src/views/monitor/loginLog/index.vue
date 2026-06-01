<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>登录日志</span>
          <div>
            <el-button type="danger" @click="handleClean">清空日志</el-button>
            <el-button @click="handleRefresh"><el-icon><Refresh /></el-icon> 刷新</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="登录账号">
          <el-input v-model="searchForm.loginName" placeholder="请输入登录账号" clearable />
        </el-form-item>
        <el-form-item label="登录状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="成功" value="0" />
            <el-option label="失败" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="infoId" label="访问ID" width="100" />
        <el-table-column prop="loginName" label="登录账号" min-width="120" />
        <el-table-column prop="ipaddr" label="登录IP" min-width="140" />
        <el-table-column prop="loginLocation" label="登录地点" min-width="150" />
        <el-table-column prop="browser" label="浏览器" min-width="120" />
        <el-table-column prop="os" label="操作系统" min-width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="msg" label="提示消息" min-width="150" show-overflow-tooltip />
        <el-table-column prop="loginTime" label="访问时间" min-width="160" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewDetail(row)">详情</el-button>
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

    <el-dialog v-model="detailVisible" title="登录详情" width="600px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="登录账号">{{ detailData.loginName }}</el-descriptions-item>
        <el-descriptions-item label="登录IP">{{ detailData.ipaddr }}</el-descriptions-item>
        <el-descriptions-item label="登录地点" :span="2">{{ detailData.loginLocation }}</el-descriptions-item>
        <el-descriptions-item label="浏览器">{{ detailData.browser }}</el-descriptions-item>
        <el-descriptions-item label="操作系统">{{ detailData.os }}</el-descriptions-item>
        <el-descriptions-item label="登录状态">
          <el-tag :type="detailData.status === '0' ? 'success' : 'danger'">{{ detailData.status === '0' ? '成功' : '失败' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="提示消息">{{ detailData.msg }}</el-descriptions-item>
        <el-descriptions-item label="登录时间">{{ detailData.loginTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLoginLogList, cleanLoginLog } from '@/api/loginLog'

const searchForm = reactive({ loginName: '', ipaddr: '', status: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })
const detailVisible = ref(false)
const detailData = ref({})

const fetchData = async () => {
  try {
    const res = await getLoginLogList({ current: pagination.current, size: pagination.size, ...searchForm })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error(error) }
}

const handleViewDetail = (row) => { detailData.value = row; detailVisible.value = true }
const handleClean = () => {
  ElMessageBox.confirm('确定要清空所有登录日志吗？', '提示', { type: 'warning' })
    .then(async () => { await cleanLoginLog(); ElMessage.success('清空成功'); fetchData() }).catch(() => {})
}
const handleRefresh = () => fetchData()
const handleReset = () => { Object.assign(searchForm, { loginName: '', ipaddr: '', status: '' }); fetchData() }

fetchData()
</script>

<style scoped>
.page-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
