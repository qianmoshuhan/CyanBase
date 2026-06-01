<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>任务执行日志</span>
          <el-button @click="handleRefresh">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="任务名称">
          <el-input v-model="searchForm.jobName" placeholder="请输入任务名称" clearable />
        </el-form-item>
        <el-form-item label="执行状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="正常" value="0" />
            <el-option label="失败" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="jobLogId" label="日志ID" width="100" />
        <el-table-column prop="jobName" label="任务名称" min-width="150" />
        <el-table-column prop="jobGroup" label="任务分组" min-width="120" />
        <el-table-column prop="invokeTarget" label="调用目标" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '正常' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="jobMessage" label="日志信息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="执行时间" min-width="160" />
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

    <el-dialog v-model="detailVisible" title="日志详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务名称">{{ detailData.jobName }}</el-descriptions-item>
        <el-descriptions-item label="任务分组">{{ detailData.jobGroup }}</el-descriptions-item>
        <el-descriptions-item label="调用目标" :span="2">{{ detailData.invokeTarget }}</el-descriptions-item>
        <el-descriptions-item label="执行状态">
          <el-tag :type="detailData.status === '0' ? 'success' : 'danger'">
            {{ detailData.status === '0' ? '正常' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行时间">{{ detailData.createTime }}</el-descriptions-item>
        <el-descriptions-item label="日志信息" :span="2">{{ detailData.jobMessage }}</el-descriptions-item>
        <el-descriptions-item label="异常信息" :span="2">
          <pre v-if="detailData.exceptionInfo" style="color: #f56c6c; white-space: pre-wrap;">{{ detailData.exceptionInfo }}</pre>
          <span v-else>无</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { getJobLogList } from '@/api/jobLog'

const searchForm = reactive({ jobName: '', jobGroup: '', status: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })
const detailVisible = ref(false)
const detailData = ref({})

const fetchData = async () => {
  try {
    const res = await getJobLogList({ current: pagination.current, size: pagination.size, ...searchForm })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error(error) }
}

const handleViewDetail = (row) => { detailData.value = row; detailVisible.value = true }
const handleRefresh = () => fetchData()
const handleReset = () => { Object.assign(searchForm, { jobName: '', jobGroup: '', status: '' }); fetchData() }

fetchData()
</script>

<style scoped>
.page-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
