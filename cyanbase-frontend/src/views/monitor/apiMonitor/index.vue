﻿﻿﻿<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>API 监控</span>
          <el-button type="primary" @click="handleRefresh">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="接口名称">
          <el-input v-model="searchForm.apiName" placeholder="请输入接口名称" clearable />
        </el-form-item>
        <el-form-item label="请求路径">
          <el-input v-model="searchForm.apiUrl" placeholder="请输入请求路径" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="70" align="center" :index="(i) => (pagination.current - 1) * pagination.size + i + 1" />
        <el-table-column prop="apiName" label="接口名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="apiUrl" label="请求路径" min-width="220" show-overflow-tooltip />
        <el-table-column prop="reqCount" label="请求次数" width="100" sortable />
        <el-table-column prop="avgTime" label="平均耗时(ms)" width="130" sortable />
        <el-table-column prop="maxTime" label="最大耗时(ms)" width="130" sortable />
        <el-table-column prop="errorCount" label="错误次数" width="100" sortable>
          <template #default="{ row }">
            <span :style="{ color: row.errorCount > 0 ? '#f53f3f' : '#00b42a' }">
              {{ row.errorCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createDate" label="统计日期" width="120" />
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
  </div>
</template>

<script setup>
defineOptions({ name: 'ApiMonitor' })
import { ref, reactive } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getApiMonitorList } from '@/api/apiMonitor'

const searchForm = reactive({ apiName: '', apiUrl: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })

const fetchData = async () => {
  try {
    const res = await getApiMonitorList({ current: pagination.current, size: pagination.size, ...searchForm })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) {
    console.error('获取API监控数据失败:', error)
  }
}

const handleRefresh = () => fetchData()
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
  Object.assign(searchForm, { apiName: '', apiUrl: '' })
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