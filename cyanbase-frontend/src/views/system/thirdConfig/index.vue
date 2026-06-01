<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>第三方配置</span>
          <el-button type="primary" @click="handleAdd"><el-icon><Plus /></el-icon> 新增配置</el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="配置类型">
          <el-select v-model="searchForm.configType" placeholder="请选择类型" clearable>
            <el-option label="短信" value="sms" />
            <el-option label="邮件" value="email" />
            <el-option label="OSS" value="oss" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置名称">
          <el-input v-model="searchForm.configName" placeholder="请输入配置名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="configType" label="配置类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ { sms: '短信', email: '邮件', oss: 'OSS' }[row.configType] || row.configType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="configName" label="配置名称" min-width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination v-model:current-page="pagination.current" v-model:page-size="pagination.size" :total="pagination.total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" @size-change="fetchData" @current-change="fetchData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="配置类型" prop="configType">
          <el-select v-model="formData.configType" placeholder="请选择配置类型">
            <el-option label="短信" value="sms" />
            <el-option label="邮件" value="email" />
            <el-option label="OSS" value="oss" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="formData.configName" placeholder="请输入配置名称" />
        </el-form-item>
        <el-form-item label="配置内容">
          <el-input v-model="formData.config" type="textarea" rows="4" placeholder="请输入配置JSON" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio label="0">启用</el-radio>
            <el-radio label="1">禁用</el-radio>
          </el-radio-group>
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
import { getThirdConfigList, addThirdConfig, updateThirdConfig, deleteThirdConfig } from '@/api/thirdConfig'

const searchForm = reactive({ configType: '', configName: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })
const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.id ? '编辑配置' : '新增配置')
const isEdit = ref(false)
const formRef = ref(null)

const formData = reactive({ id: null, configType: '', configName: '', config: '', status: '0' })
const formRules = { configType: [{ required: true, message: '请选择配置类型', trigger: 'change' }], configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }] }

const fetchData = async () => {
  try {
    const res = await getThirdConfigList({ current: pagination.current, size: pagination.size, ...searchForm })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error(error) }
}

const handleAdd = () => { isEdit.value = false; Object.assign(formData, { id: null, configType: '', configName: '', config: '', status: '0' }); dialogVisible.value = true }
const handleEdit = (row) => { isEdit.value = true; Object.assign(formData, row); dialogVisible.value = true }
const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该配置吗？', '提示', { type: 'warning' })
    .then(async () => { await deleteThirdConfig(row.id); ElMessage.success('删除成功'); fetchData() }).catch(() => {})
}
const handleSubmit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) { await updateThirdConfig(formData); ElMessage.success('更新成功') }
      else { await addThirdConfig(formData); ElMessage.success('新增成功') }
      dialogVisible.value = false; fetchData()
    } catch (error) { console.error(error) }
  })
}
const handleDialogClose = () => formRef.value?.resetFields()
const handleReset = () => { Object.assign(searchForm, { configType: '', configName: '' }); fetchData() }

fetchData()
</script>

<style scoped>
.page-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
