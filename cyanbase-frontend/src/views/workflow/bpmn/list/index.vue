<template>
  <div class="bpmn-list-container">
    <!-- 顶部操作栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm" class="filter-form">
        <el-form-item label="流程名称">
          <el-input v-model="queryForm.processName" placeholder="请输入" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="流程Key">
          <el-input v-model="queryForm.processKey" placeholder="请输入" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option :value="0" label="草稿" />
            <el-option :value="1" label="已发布" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item class="right-actions">
          <el-button type="success" @click="goCreate">
            <el-icon><Plus /></el-icon>新建 BPMN 流程
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="processName" label="流程名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="processKey" label="流程Key" min-width="160" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="80" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createBy" label="创建人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goEdit(row.id)">编辑</el-button>
            <el-button
              v-if="row.status !== 1"
              link type="success"
              @click="handlePublish(row.id)"
            >发布</el-button>
            <el-button link type="info" @click="handleExport(row)">导出</el-button>
            <el-popconfirm title="确定删除该流程定义？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getBpmnDefList, publishBpmnDef, deleteBpmnDef, getBpmnDefXml
} from '@/api/bpmnDefinition'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryForm = reactive({
  pageNum: 1,
  pageSize: 10,
  processName: '',
  processKey: '',
  status: null
})

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getBpmnDefList(queryForm)
    if (res.code === 200 && res.data) {
      tableData.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    console.error('[BPMN列表] 加载失败', e)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryForm.pageNum = 1; fetchList() }
const handleReset = () => {
  queryForm.processName = ''
  queryForm.processKey = ''
  queryForm.status = null
  queryForm.pageNum = 1
  fetchList()
}

const goCreate = () => {
  router.push({ path: '/workflow/bpmn/designer', query: { action: 'create' } })
}
const goEdit = (id) => {
  router.push({ path: '/workflow/bpmn/designer', query: { id, action: 'edit' } })
}

const handlePublish = async (id) => {
  try {
    const res = await publishBpmnDef(id)
    if (res.code === 200) {
      ElMessage.success('发布成功')
      fetchList()
    } else {
      ElMessage.error(res.msg || '发布失败')
    }
  } catch (e) {
    console.error(e)
  }
}

const handleDelete = async (id) => {
  try {
    const res = await deleteBpmnDef(id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchList()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (e) {
    console.error(e)
  }
}

const handleExport = async (row) => {
  try {
    const res = await getBpmnDefXml(row.id)
    const xml = res.data || res
    if (!xml) { ElMessage.error('无法获取 XML'); return }
    // Blob 下载为 .bpmn 文件
    const blob = new Blob([xml], { type: 'application/xml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.processKey || 'bpmn'}_v${row.version || 1}.bpmn`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    console.error('[导出] 失败', e)
    ElMessage.error('导出失败')
  }
}

onMounted(fetchList)
</script>

<style scoped>
.bpmn-list-container { padding: 0; }
.filter-card { margin-bottom: 12px; }
.filter-form { display: flex; flex-wrap: wrap; align-items: center; gap: 0; }
.right-actions { margin-left: auto; }
.table-card { padding: 0; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>