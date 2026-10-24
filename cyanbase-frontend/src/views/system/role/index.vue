<template>
  <div class="role-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>角色管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增角色
          </el-button>
        </div>
      </template>
      <!-- 数据表格 -->
      <el-table :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="roleName" label="角色名称" min-width="150" />
        <el-table-column prop="roleKey" label="角色标识" min-width="120" />
        <el-table-column prop="tenantName" label="所属租户" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.tenantId && row.tenantId > 0 ? '' : 'info'" effect="plain">
              {{ row.tenantName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dataScope" label="数据范围" width="140">
          <template #default="{ row }">
            <el-tag :type="scopeTagType(row.dataScope)" effect="plain">
              {{ scopeLabel(row.dataScope) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortNum" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="formData.roleName" placeholder="请输入角色名称" />
        </el-form-item>

        <!-- 所属租户下拉（admin 可选，租户禁用） -->
        <el-form-item label="所属租户" prop="tenantId">
          <el-select
            v-model="formData.tenantId"
            :disabled="isTenantLogin"
            placeholder="请选择租户"
            style="width: 100%"
          >
            <el-option label="平台角色" :value="0" />
            <el-option
              v-for="item in tenantOptions"
              :key="item.id"
              :label="item.tenantName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="数据范围" prop="dataScope">
          <el-select v-model="formData.dataScope" style="width: 100%">
            <el-option
              v-for="opt in dataScopeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <!-- 自定义数据范围时：绑定部门 -->
        <el-form-item v-if="formData.dataScope === 5" label="自定义部门">
          <div style="width: calc(100% + 50px); height: 260px; overflow-y: auto; border:1px solid #eee; padding:10px; ">
            <el-tree
              ref="deptTreeRef"
              :data="deptTreeData"
              show-checkbox
              node-key="id"
              :check-strictly="false"
              :props="{ label: 'deptName', children: 'children' }"
            />
          </div>
        </el-form-item>
        <!-- 权限分配（菜单树勾选）-->
<el-form-item label="分配权限">
<div style="width: calc(100% + 50px); height: 300px; overflow-y: auto; border:1px solid #eee; padding:10px; ">
        <el-tree
          ref="treeRef"
          :data="menuTreeData"
          show-checkbox
          node-key="id"
          :default-expand-all="false"
          :check-strictly="false"
          :props="{ label: 'menuName', children: 'children' }"
        />
      </div>
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
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRoleList, addRole, updateRole, deleteRole, getMenuList, getRoleMenuIds, saveRoleMenuPerm, saveRoleDataScope, getRoleDataScope } from '@/api/role'
import { getDeptList } from '@/api/dept'
import { getAllTenant } from '@/api/tenant'
import storage from '@/utils/storage'

// 是否租户登录
const isTenantLogin = computed(() => storage.getLoginType() === 'tenant')

const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.id ? '编辑角色' : '新增角色')
const isEdit = ref(false)
const formRef = ref(null)
// 菜单树数据
const menuTreeData = ref([])
// 树组件引用
const treeRef = ref(null)
// 提交时用的菜单ID数组
const checkedMenuIds = ref([])
// 租户下拉数据源
const tenantOptions = ref([])

// ===== 数据范围 =====
const dataScopeOptions = [
  { value: 1, label: '本租户全部' },
  { value: 2, label: '本部门' },
  { value: 3, label: '本部门及下属' },
  { value: 4, label: '仅本人' },
  { value: 5, label: '自定义' }
]
const scopeLabel = (v) => dataScopeOptions.find(o => o.value === v)?.label || '本租户全部'
const scopeTagType = (v) => {
  if (v === 1) return 'info'
  if (v === 2) return 'warning'
  if (v === 3) return 'warning'
  if (v === 4) return 'success'
  if (v === 5) return 'danger'
  return 'info'
}

// 自定义部门树
const deptTreeRef = ref(null)
const deptTreeData = ref([])

const formData = reactive({
  id: null,
  tenantId: 0,
  roleName: '',
  roleKey: '',
  dataScope: 1,
  sortNum: 0,
  status: 0,
  remark: ''
})

// 只保留角色名称必填
const formRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

onMounted(async () => {
  // admin 需要加载租户下拉选项
  if (!isTenantLogin.value) {
    try {
      const res = await getAllTenant()
      tenantOptions.value = res.data || []
    } catch (e) {
      console.error('加载租户列表失败', e)
    }
  }
})

const fetchData = async () => {
  try {
    const res = await getRoleList()
    tableData.value = res.data.records || []
  } catch (error) {
    console.error('获取角色列表失败', error)
  }
}

const handleAdd = async () => {
  isEdit.value = false
  Object.assign(formData, {
    id: null,
    tenantId: isTenantLogin.value ? 6 : 0,
    roleName: '',
    roleKey: '', dataScope: 1, sortNum: 0, status: 0, remark: ''
  })
  dialogVisible.value = true
  await loadMenuTree()
  await loadDeptTree()
}

const handleEdit = async (row) => {
  isEdit.value = true
  formData.id = row.id
  formData.roleName = row.roleName
  formData.tenantId = row.tenantId ?? 0
  formData.dataScope = row.dataScope || 1
  formData.sortNum = row.sortNum
  formData.status = row.status
  formData.remark = row.remark
  formData.roleKey = row.roleKey
  dialogVisible.value = true
  await loadMenuTree()
  await loadDeptTree()
  await loadRoleCheckedMenus(formData.id)
  // 回显数据范围（含自定义部门勾选）
  const dsRes = await getRoleDataScope(row.id)
  if (dsRes.data) {
    formData.dataScope = dsRes.data.dataScope || 1
    if (formData.dataScope === 5 && dsRes.data.deptIds?.length) {
      nextTick(() => deptTreeRef.value?.setCheckedKeys(dsRes.data.deptIds))
    }
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该角色吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteRole(row.id)
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
        await updateRole(formData)
        ElMessage.success('更新成功')
      } else {
        const res = await addRole(formData)
        formData.id = res?.data ?? formData.id
        ElMessage.success('新增成功')
      }
      const menuIds = treeRef.value?.getCheckedKeys() ?? []
      if (formData.id) {
        await saveRoleMenuPerm(formData.id, menuIds)
        // 数据范围单独保存（自定义时带部门）
        const deptIds = formData.dataScope === 5
          ? (deptTreeRef.value?.getCheckedKeys() ?? [])
          : []
        await saveRoleDataScope(formData.id, formData.dataScope, deptIds)
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
  menuTreeData.value = []  // 清空菜单树，避免上一次数据串到下一次
  checkedMenuIds.value = []
}

const loadMenuTree = async () => {
  const res = await getMenuList()
  menuTreeData.value = res.data
}

const loadDeptTree = async () => {
  try {
    const res = await getDeptList()
    deptTreeData.value = res.data || []
  } catch (e) {
    deptTreeData.value = []
  }
}

const loadRoleCheckedMenus = async (roleId) => {
  const res = await getRoleMenuIds(roleId)
  checkedMenuIds.value = res.data
  nextTick(() => {
    if (treeRef.value) {
      treeRef.value.setCheckedKeys(checkedMenuIds.value)
    }
  })
}

fetchData()
</script>

<style scoped>
.role-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>