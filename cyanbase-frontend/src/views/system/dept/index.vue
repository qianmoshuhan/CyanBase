﻿﻿﻿﻿﻿﻿﻿<template>
  <div class="dept-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>部门管理</span>
          <div>
            <el-button-group>
              <el-button @click="expandAll">全部展开</el-button>
              <el-button @click="collapseAll">全部收起</el-button>
            </el-button-group>
            <el-button type="primary" @click="handleAdd" style="margin-left: 12px;" v-hasPermi="['system:dept:add']">
              <el-icon><Plus /></el-icon> 新增部门
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索表单（样式对齐 tenant/index.vue） -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="部门名称">
          <el-input v-model="searchForm.deptName" placeholder="请输入部门名称" clearable @keyup.enter="fetchData" />
        </el-form-item>
        <!-- ⭐ 租户下拉仅平台 admin 可见（租户侧 CustomTenantHandler 自动拦截，传了也不认） -->
        <el-form-item v-if="isPlatform" label="所属租户">
          <el-select v-model="searchForm.tenantId" placeholder="全部租户" clearable style="width: 180px">
            <el-option v-for="t in tenantList" :key="t.id" :label="t.tenantName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="禁用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="tableRef"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :default-expand-all="true"
        border
        stripe
      >
        <el-table-column prop="deptName" label="部门名称" min-width="200">
          <template #default="{ row }">
            <el-icon style="color: #1677ff;"><Folder /></el-icon>
            <span style="margin-left: 6px;">{{ row.deptName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortNum" label="排序" width="80" />
        <!-- ⭐ 所属租户列：仅平台 admin 可见（租户侧 CustomTenantHandler 自动拦截，无需展示）
             样式对齐 user/index.vue：el-tag + effect="plain" -->
        <el-table-column v-if="isPlatform" label="所属租户" min-width="120">
          <template #default="{ row }">
            <el-tag v-if="row.tenantId == null || row.tenantId === 0" type="info" effect="plain">平台</el-tag>
            <el-tag v-else-if="tenantMap[row.tenantId]" effect="plain">{{ tenantMap[row.tenantId] }}</el-tag>
            <el-tag v-else type="warning" effect="plain">未分配</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleAddChild(row)" v-hasPermi="['system:dept:add']">新增子部门</el-button>
            <el-button type="primary" link @click="handleEdit(row)" v-hasPermi="['system:dept:edit']">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)" v-hasPermi="['system:dept:delete']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="父部门" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            :data="parentTree"
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
            placeholder="请选择父部门（0 表示顶级）"
            check-strictly
            clearable
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="formData.deptName" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="排序" prop="sortNum">
          <el-input-number v-model="formData.sortNum" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="0">正常</el-radio>
            <el-radio :label="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
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
defineOptions({ name: 'Dept' })
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Folder } from '@element-plus/icons-vue'
import { getAllDept, addDept, updateDept, deleteDept } from '@/api/dept'
import { getAllTenant } from '@/api/tenant'
import storage from '@/utils/storage'

// ===== 搜索表单 =====
const searchForm = reactive({
  deptName: '',
  tenantId: null,
  status: null
})

// 是否平台 admin（决定是否显示租户下拉）
const isPlatform = computed(() => storage.getLoginType() !== 'tenant')

// 租户下拉数据源
const tenantList = ref([])

// ⭐ tenantId → tenantName 字典（加速表格映射）
const tenantMap = computed(() => {
  const map = {}
  tenantList.value.forEach(t => { map[t.id] = t.tenantName })
  return map
})

const treeData = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const tableRef = ref(null)
const editingRow = ref(null) // 当前编辑/新增子部门的父行

const defaultForm = () => ({
  id: null,
  parentId: 0,
  deptName: '',
  sortNum: 0,
  status: 0,
  remark: ''
})
const formData = reactive(defaultForm())

const formRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  parentId: [{ required: true, message: '请选择父部门', trigger: 'change' }],
  sortNum: [{ required: true, message: '请输入排序号', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const dialogTitle = computed(() => {
  if (isEdit.value) return '编辑部门'
  return editingRow.value?.id ? '新增子部门' : '新增顶级部门'
})

// ===== 父部门下拉树（禁用当前编辑节点+其子孙）=====
const parentTree = computed(() => {
  const disableIds = new Set()
  if (editingRow.value?.id) {
    collectDescendantIds(editingRow.value.id, treeData.value, disableIds)
  }
  const markDisabled = (nodes) => nodes.map(n => ({
    ...n,
    disabled: disableIds.has(n.id),
    children: n.children ? markDisabled(n.children) : []
  }))
  return [{ id: 0, deptName: '顶级部门', children: markDisabled(treeData.value) }]
})

const collectDescendantIds = (targetId, nodes, set) => {
  for (const n of nodes) {
    if (n.id === targetId) {
      set.add(n.id)
      const walk = (children) => children.forEach(c => { set.add(c.id); walk(c.children) })
      walk(n.children)
      return true
    }
    if (collectDescendantIds(targetId, n.children, set)) return true
  }
  return false
}

// ===== 获取数据 + 前端建树 =====
const fetchData = async () => {
  try {
    // 构造查询参数（空值不传，让后端用默认行为）
    const params = {}
    if (searchForm.deptName) params.deptName = searchForm.deptName.trim()
    if (searchForm.status != null) params.status = searchForm.status
    if (isPlatform.value && searchForm.tenantId != null) params.tenantId = searchForm.tenantId

    const res = await getAllDept(Object.keys(params).length ? params : undefined)
    const flatList = res.data || []
    treeData.value = buildTree(flatList)
  } catch (error) {
    console.error('获取部门列表失败', error)
  }
}

// ===== 重置搜索 =====
const handleReset = () => {
  searchForm.deptName = ''
  searchForm.tenantId = null
  searchForm.status = null
  fetchData()
}

// ===== 加载租户下拉（仅平台侧需要）=====
const loadTenants = async () => {
  if (!isPlatform.value) return
  try {
    const res = await getAllTenant()
    tenantList.value = res.data || []
  } catch (e) {
    console.error('加载租户列表失败', e)
  }
}

// 前端建树（扁平 → children 嵌套）
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
  // 递归排序
  const sortRecursive = (list) => {
    list.sort((a, b) => (a.sortNum || 0) - (b.sortNum || 0))
    list.forEach(n => sortRecursive(n.children))
  }
  sortRecursive(roots)
  return roots
}

const expandAll = () => {
  nextTick(() => {
    // el-table 树形展开：遍历 store 里的所有行，逐层展开
    const store = tableRef.value?.store
    if (!store) return
    // 获取所有行（包括子行）
    const rows = store.getFlattenRows ? store.getFlattenRows() : []
    // 如果没有 getFlattenRows，递归展开 root 行
    const expandRecursive = (row) => {
      if (row && row.children && row.children.length) {
        tableRef.value.toggleRowExpansion(row, true)
        row.children.forEach(expandRecursive)
      }
    }
    treeData.value.forEach(r => expandRecursive(r))
  })
}

const collapseAll = () => {
  nextTick(() => {
    const store = tableRef.value?.store
    if (!store) return
    const collapseRecursive = (row) => {
      if (row && row.children && row.children.length) {
        row.children.forEach(collapseRecursive)
        tableRef.value.toggleRowExpansion(row, false)
      }
    }
    treeData.value.forEach(r => collapseRecursive(r))
  })
}

const resetForm = () => { Object.assign(formData, defaultForm()) }

const handleAdd = () => {
  isEdit.value = false
  editingRow.value = null
  resetForm()
  dialogVisible.value = true
}

const handleAddChild = (row) => {
  isEdit.value = false
  editingRow.value = row
  resetForm()
  formData.parentId = row.id
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  editingRow.value = row
  Object.assign(formData, { ...defaultForm(), ...row })
  dialogVisible.value = true
}

const handleDelete = (row) => {
  const hasChildren = row.children && row.children.length
  const msg = hasChildren
    ? `部门"${row.deptName}"含有 ${row.children.length} 个子部门，确定删除？子部门将一同删除！`
    : `确定删除部门"${row.deptName}"？`
  ElMessageBox.confirm(msg, '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    await deleteDept(row.id)
    ElMessage.success('删除成功')
    fetchData()
  }).catch(() => {})
}

const handleSubmit = async () => {
  await formRef.value?.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) {
        await updateDept(formData)
        ElMessage.success('更新成功')
      } else {
        await addDept(formData)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (e) {
      console.error(e)
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
  editingRow.value = null
}

// ===== 初始化 =====
onMounted(async () => {
  loadTenants()
  fetchData()
})
</script>

<style scoped>
.dept-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
</style>