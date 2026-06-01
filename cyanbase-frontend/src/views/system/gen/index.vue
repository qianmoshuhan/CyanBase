<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>代码生成</span>
          <el-button type="primary" @click="handleAdd"><el-icon><Plus /></el-icon> 新增表</el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="表名称">
          <el-input v-model="searchForm.tableName" placeholder="请输入表名称" clearable />
        </el-form-item>
        <el-form-item label="表描述">
          <el-input v-model="searchForm.tableComment" placeholder="请输入表描述" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="tableId" label="编号" width="80" />
        <el-table-column prop="tableName" label="表名称" min-width="150" />
        <el-table-column prop="tableComment" label="表描述" min-width="180" />
        <el-table-column prop="entityName" label="实体类名" min-width="150" />
        <el-table-column prop="moduleName" label="生成包名" min-width="150" />
        <el-table-column prop="functionName" label="功能名称" min-width="120" />
        <el-table-column prop="functionAuthor" label="作者" width="100" />
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link @click="handleGenerate(row)">生成</el-button>
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
        <el-form-item label="表名称" prop="tableName">
          <el-input v-model="formData.tableName" placeholder="请输入表名称" />
        </el-form-item>
        <el-form-item label="表描述" prop="tableComment">
          <el-input v-model="formData.tableComment" placeholder="请输入表描述" />
        </el-form-item>
        <el-form-item label="实体类名" prop="entityName">
          <el-input v-model="formData.entityName" placeholder="请输入实体类名" />
        </el-form-item>
        <el-form-item label="生成包名" prop="moduleName">
          <el-input v-model="formData.moduleName" placeholder="请输入生成包名" />
        </el-form-item>
        <el-form-item label="功能名称" prop="functionName">
          <el-input v-model="formData.functionName" placeholder="请输入功能名称" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="formData.functionAuthor" placeholder="请输入作者" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getGenTableList, addGenTable, updateGenTable, deleteGenTable } from '@/api/gen'

const searchForm = reactive({ tableName: '', tableComment: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })
const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.tableId ? '编辑表' : '新增表')
const isEdit = ref(false)
const formRef = ref(null)

const formData = reactive({ tableId: null, tableName: '', tableComment: '', entityName: '', moduleName: '', functionName: '', functionAuthor: '' })
const formRules = {
  tableName: [{ required: true, message: '请输入表名称', trigger: 'blur' }],
  tableComment: [{ required: true, message: '请输入表描述', trigger: 'blur' }],
  entityName: [{ required: true, message: '请输入实体类名', trigger: 'blur' }]
}

const fetchData = async () => {
  try {
    const res = await getGenTableList({ current: pagination.current, size: pagination.size, ...searchForm })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error(error) }
}

const handleAdd = () => { isEdit.value = false; Object.assign(formData, { tableId: null, tableName: '', tableComment: '', entityName: '', moduleName: '', functionName: '', functionAuthor: '' }); dialogVisible.value = true }
const handleEdit = (row) => { isEdit.value = true; Object.assign(formData, row); dialogVisible.value = true }
const handleGenerate = (row) => { ElMessage.info(`正在生成表 ${row.tableName} 的代码...`) }
const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该表吗？', '提示', { type: 'warning' })
    .then(async () => { await deleteGenTable(row.tableId); ElMessage.success('删除成功'); fetchData() }).catch(() => {})
}
const handleSubmit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) { await updateGenTable(formData); ElMessage.success('更新成功') }
      else { await addGenTable(formData); ElMessage.success('新增成功') }
      dialogVisible.value = false; fetchData()
    } catch (error) { console.error(error) }
  })
}
const handleDialogClose = () => formRef.value?.resetFields()
const handleReset = () => { Object.assign(searchForm, { tableName: '', tableComment: '' }); fetchData() }

fetchData()
</script>

<style scoped>
.page-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
