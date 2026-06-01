<template>
  <div class="workflow-done-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>我的已办</span>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" class="search-form">
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
      <el-table :data="list" border stripe highlight-current-row>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="processName" label="流程名称" min-width="180" />
        <el-table-column prop="processInstanceId" label="流程实例ID" min-width="200" />
        <el-table-column prop="applyUserName" label="申请人" width="120" />
        <el-table-column prop="approveTime" label="审批时间" width="180" />
        <el-table-column prop="status" label="审批状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : 'danger'">
              {{ row.status === 'APPROVED' ? '已通过' : '已驳回' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleView(row)">查看详情</el-button>
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
    <el-dialog v-model="detailVisible" title="流程详情" width="90%">
      <div v-if="currentRow" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="流程名称">{{ currentRow.processName }}</el-descriptions-item>
          <el-descriptions-item label="流程实例ID">{{ currentRow.processInstanceId }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ currentRow.applyUserName }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ currentRow.applyTime }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ currentRow.approveUserName }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ currentRow.approveTime }}</el-descriptions-item>
          <el-descriptions-item label="审批状态">
            <el-tag :type="currentRow.status === 'APPROVED' ? 'success' : 'danger'">
              {{ currentRow.status === 'APPROVED' ? '已通过' : '已驳回' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="审批意见">{{ currentRow.approveRemark || '无' }}</el-descriptions-item>
        </el-descriptions>

        <div class="form-data" style="margin-top: 20px;">
          <h4>表单数据：</h4>
          <pre style="background: #f5f5f5; padding: 10px; border-radius: 4px; max-height: 300px; overflow: auto;">
{{ JSON.stringify(currentRow.formData, null, 2) }}
          </pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
// 导入工作流API（已完成）
import { getMyDoneList } from '@/api/wfDefinition'

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
    // 处理时间范围参数
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      processName: query.processName,
      status: query.status,
      startTime: query.dateRange[0] ? query.dateRange[0].format('YYYY-MM-DD') : '',
      endTime: query.dateRange[1] ? query.dateRange[1].format('YYYY-MM-DD') : ''
    }
    const res = await getMyDoneList(params)
    if (res.code === 200) {
      list.value = res.data.list
      total.value = res.data.total
    } else {
      ElMessage.error(res.msg || '获取数据失败')
    }
  } catch (error) {
    ElMessage.error('网络异常，请稍后重试')
    console.error('获取我的已办列表失败：', error)
  }
}

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