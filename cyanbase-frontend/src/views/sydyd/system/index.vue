<template>
  <div class="system-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="query">
        <el-form-item label="制度名称">
          <el-input v-model="query.systemName" placeholder="请输入" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card" shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="handleAdd">新增制度</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="systemName" label="制度名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="systemCode" label="制度编号" width="160" />
        <el-table-column prop="effectiveDate" label="生效日期" width="170">
          <template #default="{ row }">{{ formatDate(row.effectiveDate) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              link
              size="small"
              :type="row.status === 0 ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >{{ row.status === 0 ? '停用' : '启用' }}</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <!-- 分页 -->
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList"
        @current-change="loadList"
        class="pagination"
      />
    </el-card>

    <!-- 表单弹窗 -->
    <el-dialog v-model="formVisible" :title="formIsEdit ? '编辑制度' : '新增制度'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="制度名称" prop="systemName">
          <el-input v-model="formData.systemName" placeholder="如：三重一大决策制度" />
        </el-form-item>
        <el-form-item label="制度编号" prop="systemCode">
          <el-input v-model="formData.systemCode" placeholder="如：SYDYD_POLICY_01" />
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker
            v-model="formData.effectiveDate"
            type="datetime"
            placeholder="选择生效日期"
            style="width: 100%"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSystemList, createSystem, updateSystem, deleteSystem,
  enableSystem, disableSystem
} from '@/api/sydyd'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, systemName: '', status: null })
const formVisible = ref(false)
const formIsEdit = ref(false)
const formRef = ref(null)
const formData = reactive({
  id: null, systemName: '', systemCode: '',
  effectiveDate: null, remark: ''
})
const formRules = {
  systemName: [{ required: true, message: '请输入制度名称', trigger: 'blur' }],
  systemCode: [{ required: true, message: '请输入制度编号', trigger: 'blur' }]
}

const formatDate = (t) => t ? (t.includes('T') ? t.replace('T', ' ').substring(0, 19) : t.substring(0, 19)) : '-'

const loadList = async () => {
  loading.value = true
  try {
    const res = await getSystemList(query)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
const resetQuery = () => {
  query.systemName = ''; query.status = null; query.pageNum = 1; loadList()
}
const handleAdd = () => {
  formIsEdit.value = false
  Object.assign(formData, { id: null, systemName: '', systemCode: '', effectiveDate: null, remark: '' })
  formVisible.value = true
}
const handleEdit = (row) => {
  formIsEdit.value = true
  Object.assign(formData, { ...row })
  formVisible.value = true
}
const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (formIsEdit.value) {
      await updateSystem(formData)
      ElMessage.success('更新成功')
    } else {
      await createSystem(formData)
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    loadList()
  } finally { submitLoading.value = false }
}
const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除制度「${row.systemName}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteSystem(row.id)
      ElMessage.success('删除成功')
      loadList()
    }).catch(() => {})
}
const toggleStatus = async (row) => {
  if (row.status === 0) await disableSystem(row.id)
  else await enableSystem(row.id)
  ElMessage.success('操作成功')
  loadList()
}

onMounted(loadList)
</script>

<style scoped>
.system-page { padding: 16px; }
.search-card { margin-bottom: 12px; }
.table-card .toolbar { margin-bottom: 12px; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>