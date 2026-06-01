<template>
  <el-card shadow="hover" class="app-container">
    <template #header>
      <div class="card-header">
        <span>工作流管理</span>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增流程
        </el-button>
      </div>
    </template>
    <el-form :inline="true" class="search-form">
      <el-form-item label="流程名称">
        <el-input v-model="queryParams.processName" placeholder="请输入流程名称" clearable />
      </el-form-item>
      <el-form-item label="流程标识">
        <el-input v-model="queryParams.processKey" placeholder="请输入标识" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option label="草稿" value="0" />
          <el-option label="已发布" value="1" />
          <el-option label="已停用" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="tableData" border style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="processName" label="流程名称" min-width="180" />
      <el-table-column prop="processKey" label="流程标识" width="200" />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="version" label="版本" width="100" />
      
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="
            row.status === 1 ? 'success' : 
            row.status === 2 ? 'info' : 'warning'
          ">
            {{ 
              row.status === 0 ? '草稿' : 
              row.status === 1 ? '已发布' : 
              row.status === 2 ? '已停用' : '未知' 
            }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="200" />
      
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button type="success" link size="small" @click="handlePreview(row)">预览</el-button>
          <el-button type="warning" link size="small" @click="handleOpenEdit(row)">编辑</el-button>
          <el-button type="primary" link size="small" @click="handleDesign(row)">设计</el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="queryParams.pageNum"
      v-model:page-size="queryParams.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :total="total"
      layout="total, sizes, prev, pager, jumper"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </el-card>

  <!-- 编辑弹窗 -->
  <el-dialog v-model="editDialogVisible" title="编辑流程信息" width="520px">
    <el-form :model="editForm" label-width="100px">
      <el-form-item label="流程名称">
        <el-input v-model="editForm.processName" placeholder="请输入" />
      </el-form-item>
      <el-form-item label="流程标识">
        <el-input v-model="editForm.processKey" disabled />
      </el-form-item>
      <el-form-item label="分类">
        <el-input v-model="editForm.category" placeholder="请输入" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="editForm.status">
          <el-option label="草稿" value="0" />
          <el-option label="已发布" value="1" />
          <el-option label="已停用" value="2" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSaveEdit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWorkflowDefinitionList, deleteWorkflowDefinition, updateWorkflowDefinition, addWorkflowDefinition } from '@/api/wfDefinition'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  processName: '',
  processKey: '',
  status: ''
})

// 编辑弹窗
const editDialogVisible = ref(false)
const editForm = ref({
  id: '',
  processName: '',
  processKey: '',
  category: '',
  status: 0,
  processXml: ''
})

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await getWorkflowDefinitionList(queryParams)
    tableData.value = res.data.records
    total.value = res.data.total
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

// 打开编辑
const handleOpenEdit = (row) => {
  editForm.value = { ...row }
  editDialogVisible.value = true
}

// 保存编辑
const handleSaveEdit = async () => {
  try {
    await updateWorkflowDefinition(editForm.value)
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    getList()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

// 新增流程
const handleAdd = async () => {
  const { value } = await ElMessageBox.prompt('请填写流程名称', '新增流程', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '流程名称',
    inputPattern: /.{1,}/,
    inputErrorMessage: '流程名称不能为空'
  })

  const params = {
    processName: value,
    description: ''
  }

  try {
    const res = await addWorkflowDefinition(params)
    ElMessage.success('新增成功，进入设计页面')
    router.push({
      path: '/workflow/designer',
      query: {
        id: res.data,
        name: value,
        action: 'edit'
      }
    })
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('新增失败')
      console.error(err)
    }
  }
}

// 进入设计
const handleDesign = (row) => {
  router.push({
    path: '/workflow/designer',
    query: { 
      id: row.id, 
      name: row.processName,
      action: 'edit'
    }
  })
}

// 预览
const handlePreview = (row) => {
  router.push({
    path: '/workflow/designer',
    query: { 
      id: row.id, 
      name: row.processName,
      action: 'preview'
    }
  })
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该流程吗？', '提示')
    await deleteWorkflowDefinition(row.id)
    ElMessage.success('删除成功')
    getList()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('删除失败')
  }
}

// 查询按钮
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 切换每页条数
const handleSizeChange = () => {
  queryParams.pageNum = 1
  getList()
}

// 切换页码（核心：不重置为1）
const handleCurrentChange = () => {
  getList()
}

// 重置
const resetQuery = () => {
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  queryParams.processName = ''
  queryParams.processKey = ''
  queryParams.status = ''
  getList()
}

onMounted(() => getList())
</script>

<style scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end }
</style>