<template>
  <div class="form-data-list-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>表单数据列表</span>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="表单名称">
          <el-select v-model="query.formId" placeholder="请选择表单" clearable>
            <el-option 
              v-for="form in formList" 
              :key="form.id" 
              :label="form.name" 
              :value="form.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="提交人">
          <el-input v-model="query.createBy" placeholder="请输入提交人" clearable />
        </el-form-item>
        <el-form-item label="提交时间">
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
        <el-table-column prop="formName" label="表单名称" min-width="180" />
        <el-table-column prop="createBy" label="提交人" width="120" />
        <el-table-column prop="createTime" label="提交时间" width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUBMITTED' ? 'success' : 'warning'">
              {{ row.status === 'SUBMITTED' ? '已提交' : '已撤回' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleView(row)">查看详情</el-button>
            <el-button 
              v-if="row.status === 'SUBMITTED'"
              type="danger" 
              link 
              @click="handleRecall(row)"
            >
              撤回
            </el-button>
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
    <el-dialog v-model="detailVisible" title="表单数据详情" width="90%">
      <div v-if="currentRow" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="表单名称">{{ currentRow.formName }}</el-descriptions-item>
          <el-descriptions-item label="提交人">{{ currentRow.createBy }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ currentRow.createTime }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="currentRow.status === 'SUBMITTED' ? 'success' : 'danger'">
              {{ currentRow.status === 'SUBMITTED' ? '已提交' : '已撤回' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="form-data" style="margin-top: 20px;">
          <h4>表单内容：</h4>
          <el-table :data="currentRow.formDataList" border stripe>
            <el-table-column prop="fieldName" label="字段名称" width="150" />
            <el-table-column prop="fieldValue" label="字段值" min-width="200" />
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
// 导入表单API（已完成）
import { getFormDefinitionList, getFormDataList, recallFormData } from '@/api/form'
import FormPreviewDialog from './FormPreviewDialog.vue'
import { ref } from 'vue'

// 表单列表（用于筛选）
const formList = ref([])

// 查询条件
const query = reactive({
  formId: '',
  createBy: '',
  dateRange: []
})

// 列表数据
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

// 详情弹窗
const detailVisible = ref(false)
const currentRow = ref(null)

const previewVisible = ref(false)
const previewConfig = ref('')

const handlePreview = (row) => {
  previewConfig.value = row.layoutConfig || ''
  previewVisible.value = true
}

// 获取表单列表（用于筛选）
const fetchFormList = async () => {
  try {
    const res = await getFormDefinitionList({ status: 'PUBLISHED' })
    if (res.code === 200) {
      formList.value = res.data.list
    }
  } catch (error) {
    console.error('获取表单列表失败：', error)
  }
}

// 获取表单数据列表
const fetchData = async () => {
  try {
    // 处理时间范围参数
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      formId: query.formId,
      createBy: query.createBy,
      startTime: query.dateRange[0] ? query.dateRange[0].format('YYYY-MM-DD') : '',
      endTime: query.dateRange[1] ? query.dateRange[1].format('YYYY-MM-DD') : ''
    }
    const res = await getFormDataList(params)
    if (res.code === 200) {
      list.value = res.data.list
      total.value = res.data.total
    } else {
      ElMessage.error(res.msg || '获取数据失败')
    }
  } catch (error) {
    ElMessage.error('网络异常，请稍后重试')
    console.error('获取表单数据列表失败：', error)
  }
}

// 查看详情
const handleView = (row) => {
  currentRow.value = row
  detailVisible.value = true
}

// 撤回表单
const handleRecall = (row) => {
  ElMessageBox.confirm(
    '确定要撤回该表单数据吗？',
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      const res = await recallFormData(row.id)
      if (res.code === 200) {
        ElMessage.success('撤回成功')
        fetchData()
      } else {
        ElMessage.error(res.msg || '撤回失败')
      }
    } catch (error) {
      ElMessage.error('网络异常，请稍后重试')
      console.error('撤回表单数据失败：', error)
    }
  })
}

// 重置搜索
const handleReset = () => {
  query.formId = ''
  query.createBy = ''
  query.dateRange = []
  pageNum.value = 1
  fetchData()
}

// 初始化
onMounted(() => {
  fetchFormList()
  fetchData()
})
</script>

<style scoped>
.form-data-list-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.detail-content { line-height: 1.6; }
.form-data h4 { margin: 0 0 10px 0; font-size: 14px; color: #333; }
</style>