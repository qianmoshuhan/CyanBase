<template>
  <div class="list-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="!isMobile" :model="query">
        <el-form-item label="清单名称">
          <el-input v-model="query.listName" placeholder="请输入" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="所属制度">
          <el-select v-model="query.systemId" placeholder="全部" clearable style="width: 200px">
            <el-option v-for="s in systemOptions" :key="s.id" :label="s.systemName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="事项分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="c in categoryOpts" :key="c.val" :label="c.label" :value="c.val" />
          </el-select>
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

    <el-card class="table-card" shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="handleAdd">新增清单</el-button>
      </div>
      <div class="table-scroll-wrapper">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="listName" label="清单名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="所属制度" width="180">
          <template #default="{ row }">{{ systemMap[row.systemId] || '-' }}</template>
        </el-table-column>
        <el-table-column label="分类" width="120">
          <template #default="{ row }">{{ categoryMap[row.category] || '-' }}</template>
        </el-table-column>
        <el-table-column prop="amountThreshold" label="金额门槛(万)" width="130">
          <template #default="{ row }">{{ row.amountThreshold != null ? row.amountThreshold : '—' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        @size-change="loadList"
        @current-change="loadList"
        class="pagination"
      />
    </el-card>

    <!-- 表单 -->
    <el-dialog v-model="formVisible" :title="formIsEdit ? '编辑清单' : '新增清单'" :width="isMobile ? '92%' : '550px'" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="所属制度" prop="systemId">
          <el-select v-model="formData.systemId" placeholder="请选择制度" style="width: 100%">
            <el-option v-for="s in systemOptions" :key="s.id" :label="s.systemName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="清单名称" prop="listName">
          <el-input v-model="formData.listName" placeholder="如：大额资金支出事项" />
        </el-form-item>
        <el-form-item label="事项分类" prop="category">
          <el-select v-model="formData.category" placeholder="请选择" style="width: 100%">
            <el-option v-for="c in categoryOpts" :key="c.val" :label="c.label" :value="c.val" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额门槛(万)">
          <el-input-number v-model="formData.amountThreshold" :min="0" :precision="2" placeholder="可选" style="width: 100%" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="formData.sortNum" :min="0" style="width: 100%" />
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
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getListPage, createList, updateList, deleteList, getAllSystems } from '@/api/sydyd'

// ⭐ 响应式检测
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

const categoryOpts = [
  { val: 1, label: '大额资金支出' },
  { val: 2, label: '重大项目投资' },
  { val: 3, label: '重要人事任免' },
  { val: 4, label: '其他重大决策' }
]
const categoryMap = Object.fromEntries(categoryOpts.map(c => [c.val, c.label]))

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const systemOptions = ref([])
const systemMap = reactive({})
const query = reactive({ pageNum: 1, pageSize: 10, listName: '', systemId: null, category: null, status: null })
const formVisible = ref(false)
const formIsEdit = ref(false)
const formRef = ref(null)
const formData = reactive({
  id: null, systemId: null, listName: '', category: null,
  amountThreshold: null, sortNum: 1, status: 0
})
const formRules = {
  systemId: [{ required: true, message: '请选择所属制度', trigger: 'change' }],
  listName: [{ required: true, message: '请输入清单名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择事项分类', trigger: 'change' }]
}

const formatDate = (t) => t ? (t.includes('T') ? t.replace('T', ' ').substring(0, 19) : t.substring(0, 19)) : '-'

const loadSystems = async () => {
  try {
    const res = await getAllSystems()
    systemOptions.value = res.data || []
    Object.keys(systemMap).forEach(k => delete systemMap[k])
    systemOptions.value.forEach(s => { systemMap[s.id] = s.systemName })
  } catch (e) { /* 忽略 */ }
}
const loadList = async () => {
  loading.value = true
  try {
    const res = await getListPage(query)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
const resetQuery = () => {
  query.listName = ''; query.systemId = null; query.category = null; query.status = null
  query.pageNum = 1; loadList()
}
const handleAdd = async () => {
  await loadSystems()
  formIsEdit.value = false
  Object.assign(formData, {
    id: null, systemId: null, listName: '', category: null,
    amountThreshold: null, sortNum: 1, status: 0
  })
  formVisible.value = true
}
const handleEdit = async (row) => {
  await loadSystems()
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
      await updateList(formData); ElMessage.success('更新成功')
    } else {
      await createList(formData); ElMessage.success('新增成功')
    }
    formVisible.value = false; loadList()
  } finally { submitLoading.value = false }
}
const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除清单「${row.listName}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteList(row.id); ElMessage.success('删除成功'); loadList()
    }).catch(() => {})
}

onMounted(async () => { await loadSystems(); loadList() })
</script>

<style scoped>
.list-page { padding: 16px; }
.search-card { margin-bottom: 12px; }
.table-card .toolbar { margin-bottom: 12px; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>