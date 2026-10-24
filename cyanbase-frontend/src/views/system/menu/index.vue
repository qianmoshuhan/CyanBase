<template>
  <div class="menu-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">菜单管理</span>
          <div>
            <el-input
              v-model="searchKeyword"
              placeholder="搜索菜单名称/路径"
              clearable
              style="width: 220px; margin-right: 12px;"
              @clear="handleSearch"
              @keyup.enter="handleSearch"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button-group>
              <el-button @click="expandAll">全部展开</el-button>
              <el-button @click="collapseAll">全部收起</el-button>
            </el-button-group>
            <el-button type="primary" @click="handleAdd" style="margin-left: 12px;">
              <el-icon><Plus /></el-icon> 新增菜单
            </el-button>
          </div>
        </div>
      </template>

      <!-- 树形表格 -->
      <el-table
        :data="filteredData"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :default-expand-all="true"
        border
        stripe
        height="calc(100vh - 280px)"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="200">
          <template #default="{ row }">
            <el-icon v-if="row.menuType === 0" style="color: #1677ff;"><Folder /></el-icon>
            <el-icon v-else-if="row.menuType === 1" style="color: #00b42a;"><Document /></el-icon>
            <el-icon v-else style="color: #ff7d00;"><Key /></el-icon>
            <span style="margin-left: 6px;">{{ row.menuName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="menuType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.menuType === 0" type="primary" size="small">目录</el-tag>
            <el-tag v-else-if="row.menuType === 1" type="success" size="small">菜单</el-tag>
            <el-tag v-else type="warning" size="small">按钮</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderNum" label="排序" width="70" />
        <el-table-column prop="path" label="路由路径" min-width="160" show-overflow-tooltip />
        <el-table-column prop="component" label="组件路径" min-width="180" show-overflow-tooltip />
        <el-table-column prop="perms" label="权限标识" min-width="140" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
              {{ row.status === 0 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleAddChild(row)">新增子菜单</el-button>
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="父菜单" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            :data="parentTree"
            :props="{ label: 'menuName', value: 'id', children: 'children' }"
            placeholder="请选择父菜单（0 表示顶级）"
            check-strictly
            clearable
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group v-model="formData.menuType">
            <el-radio :label="0">目录</el-radio>
            <el-radio :label="1">菜单</el-radio>
            <el-radio :label="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="formData.menuName" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="排序" prop="orderNum">
          <el-input-number v-model="formData.orderNum" :min="0" :max="999" />
        </el-form-item>
        <el-form-item v-if="formData.menuType !== 2" label="路由路径" prop="path">
          <el-input v-model="formData.path" placeholder="如: /system/menu" />
        </el-form-item>
        <el-form-item v-if="formData.menuType === 1" label="组件路径" prop="component">
          <el-input v-model="formData.component" placeholder="如: system/menu/index" />
        </el-form-item>
        <el-form-item v-if="formData.menuType === 2" label="权限标识" prop="perms">
          <el-input v-model="formData.perms" placeholder="如: system:menu:add" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="0">正常</el-radio>
            <el-radio :label="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" rows="2" />
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
import { ref, reactive, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Folder, Document, Key } from '@element-plus/icons-vue'
import { getMenuList, addMenu, updateMenu, deleteMenu } from '@/api/menu'

// ===== 数据 =====
const treeData = ref([])
const searchKeyword = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const tableRef = ref(null)
const editingRow = ref(null) // 记录当前正在编辑/新增子菜单的父行

const defaultForm = () => ({
  id: null,
  parentId: 0,
  menuType: 1,
  menuName: '',
  orderNum: 0,
  path: '',
  component: '',
  perms: '',
  status: 0,
  remark: ''
})
const formData = reactive(defaultForm())

const formRules = {
  parentId: [{ required: true, message: '请选择父菜单', trigger: 'change' }],
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }]
}

// ===== 过滤后的树形数据 =====
const filteredData = computed(() => {
  if (!searchKeyword.value.trim()) return treeData.value
  const kw = searchKeyword.value.trim().toLowerCase()
  return filterTree(treeData.value, kw)
})

// 递归过滤：节点或其子孙匹配 → 保留该节点（带匹配的子树）
const filterTree = (nodes, kw) => {
  if (!nodes || !nodes.length) return []
  return nodes
    .map(n => {
      const matchSelf = (n.menuName || '').toLowerCase().includes(kw)
        || (n.path || '').toLowerCase().includes(kw)
        || (n.perms || '').toLowerCase().includes(kw)
      const children = filterTree(n.children, kw)
      if (matchSelf || children.length) {
        return { ...n, children }
      }
      return null
    })
    .filter(Boolean)
}

// ===== 父菜单下拉树 =====
const parentTree = computed(() => {
  // 给每个节点加 disabled（防止选自己或自己的子孙作为父节点）
  const disableIds = new Set()
  if (editingRow.value?.id) {
    collectDescendantIds(editingRow.value.id, treeData.value, disableIds)
  }
  const markDisabled = (nodes) => nodes.map(n => ({
    ...n,
    disabled: disableIds.has(n.id),
    children: n.children ? markDisabled(n.children) : []
  }))
  // 根节点加一条 "顶级菜单"
  return [{ id: 0, menuName: '顶级菜单', children: markDisabled(treeData.value) }]
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

const dialogTitle = computed(() => {
  if (isEdit.value) return '编辑菜单'
  return editingRow.value?.id ? '新增子菜单' : '新增顶级菜单'
})

// ===== 获取数据 =====
const fetchData = async () => {
  try {
    // includeDisabled=true：菜单管理要看到全部含禁用的
    const res = await getMenuList({ pageNum: 1, pageSize: 9999 })
    // 后端 /system/menu/list 返回的是扁平 records，前端自己建树
    const flatList = res.data.records || []
    treeData.value = buildTree(flatList)
  } catch (error) {
    console.error('获取菜单列表失败', error)
  }
}

// 前端建树（后端 list 接口返回扁平数据）
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
  // 排序
  const sortRecursive = (list) => {
    list.sort((a, b) => (a.orderNum || 0) - (b.orderNum || 0))
    list.forEach(n => sortRecursive(n.children))
  }
  sortRecursive(roots)
  return roots
}

// ===== 操作 =====
const handleSearch = () => { /* computed 自动响应，这里保留入口 */ }

const expandAll = () => {
  nextTick(() => {
    // el-table 的树形展开：手动触发所有行展开
    const rows = tableRef.value?.store?.watcher?.getter?.value
    if (Array.isArray(rows)) rows.forEach((_, i) => tableRef.value.toggleRowExpansion(tableRef.value.store.getCellsByRow(rows[i])?.[0]?.row, true))
  })
}

const collapseAll = () => {
  nextTick(() => {
    // el-table 没有 collapseAll 原生方法，手动 toggle
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
    ? `菜单"${row.menuName}"含有 ${row.children.length} 个子菜单，确定删除？子菜单将一同删除！`
    : `确定删除菜单"${row.menuName}"？`
  ElMessageBox.confirm(msg, '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    fetchData()
  }).catch(() => {})
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) {
        await updateMenu(formData)
        ElMessage.success('更新成功')
      } else {
        await addMenu(formData)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      console.error('保存失败', error)
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
  editingRow.value = null
}

fetchData()
</script>

<style scoped>
.menu-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header .title { font-size: 16px; font-weight: 600; }
</style>