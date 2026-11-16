﻿﻿﻿﻿﻿﻿﻿<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>岗位管理</span>
          <el-button type="primary" @click="handleAdd" v-hasPermi="['system:post:add']">
            <el-icon><Plus /></el-icon> 新增岗位
          </el-button>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" class="search-form">
        <el-form-item v-if="!isTenantLogin" label="所属租户">
          <el-select v-model="searchForm.tenantId" placeholder="请选择租户" clearable style="width: 180px">
            <el-option label="平台岗位" :value="0" />
            <el-option v-for="t in tenantOptions" :key="t.id" :label="t.tenantName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位名称">
          <el-input v-model="searchForm.postName" placeholder="请输入岗位名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 140px">
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="70" align="center" :index="(i) => (pagination.current - 1) * pagination.size + i + 1" />
        <el-table-column prop="postName" label="岗位名称" min-width="150" />
        <el-table-column v-if="!isTenantLogin" prop="tenantName" label="所属租户" min-width="140">
          <template #default="{ row }">
            <el-tag :type="row.tenantId && row.tenantId > 0 ? '' : 'info'" effect="plain">
              {{ row.tenantName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="所属部门" min-width="140">
          <template #default="{ row }">
            <el-tag :type="row.deptId && row.deptId > 0 ? '' : 'info'" effect="plain">
              {{ row.deptName || '未分配' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="postSort" label="显示顺序" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)" v-hasPermi="['system:post:edit']">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)" v-hasPermi="['system:post:delete']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="岗位名称" prop="postName">
          <el-input v-model="formData.postName" placeholder="请输入岗位名称" />
        </el-form-item>
        <el-form-item v-if="!isTenantLogin" label="所属租户">
          <el-select
            v-model="formData.tenantId"
            placeholder="请选择租户"
            clearable
            style="width: 100%"
            @change="formData.deptId = null"
          >
            <el-option label="平台岗位" :value="0" />
            <el-option v-for="t in tenantOptions" :key="t.id" :label="t.tenantName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属部门">
          <el-tree-select
            v-model="formData.deptId"
            :data="deptOptions"
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
            placeholder="请选择所属部门（可不选）"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="显示顺序" prop="postSort">
          <el-input-number v-model="formData.postSort" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" rows="3" placeholder="请输入备注" />
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
defineOptions({ name: 'Post' })
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPostList, addPost, updatePost, deletePost } from '@/api/post'
import { getAllTenant } from '@/api/tenant'
import { getAllDept } from '@/api/dept'
import storage from '@/utils/storage'

const isTenantLogin = computed(() => storage.getLoginType() === 'tenant')
const tenantOptions = ref([])
const allDeptFlat = ref([])

onMounted(async () => {
  if (!isTenantLogin.value) {
    try {
      const res = await getAllTenant()
      tenantOptions.value = res.data || []
    } catch (e) { console.error('加载租户列表失败', e) }
  }
  try {
    const res = await getAllDept()
    allDeptFlat.value = res.data || []
  } catch (e) { console.error('加载部门列表失败', e) }
})

// 根据 formData.tenantId 过滤部门树：平台侧按租户过滤，租户侧全部显示
const deptOptions = computed(() => {
  let filtered = allDeptFlat.value
  if (!isTenantLogin.value && formData.tenantId != null) {
    filtered = allDeptFlat.value.filter(d => d.tenantId === formData.tenantId)
  }
  return buildTree(filtered)
})

// 扁平列表 → children 嵌套树
const buildTree = (flatList) => {
  const map = {}
  flatList.forEach(n => { map[n.id] = { ...n, children: [] } })
  const roots = []
  flatList.forEach(n => {
    const node = map[n.id]
    if (n.parentId === 0 || !map[n.parentId]) {
      roots.push(node)
    } else {
      map[n.parentId].children.push(node)
    }
  })
  const sortRecursive = (list) => {
    list.sort((a, b) => (a.sortNum || 0) - (b.sortNum || 0))
    list.forEach(n => sortRecursive(n.children))
  }
  sortRecursive(roots)
  return roots
}

const searchForm = reactive({ tenantId: null, postName: '', status: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })

const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.postId ? '编辑岗位' : '新增岗位')
const isEdit = ref(false)
const formRef = ref(null)

const formData = reactive({
  postId: null, tenantId: 0, deptId: null, postCode: '', postName: '', postSort: 0, status: '0', remark: ''
})

const formRules = {
  postName: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }]
}

const fetchData = async () => {
  try {
    const res = await getPostList({ current: pagination.current, size: pagination.size, ...searchForm })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error(error) }
}

const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, { postId: null, tenantId: null, deptId: null, postCode: '', postName: '', postSort: 0, status: '0', remark: '' })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(formData, row)
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该岗位吗？', '提示', { type: 'warning' })
    .then(async () => { await deletePost(row.postId); ElMessage.success('删除成功'); fetchData() })
    .catch(() => {})
}

const handleSubmit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) { await updatePost(formData); ElMessage.success('更新成功') }
      else { await addPost(formData); ElMessage.success('新增成功') }
      dialogVisible.value = false
      fetchData()
    } catch (error) { console.error(error) }
  })
}

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

const handleDialogClose = () => formRef.value?.resetFields()
const handleReset = () => {
  Object.assign(searchForm, { tenantId: null, postName: '', status: '' })
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