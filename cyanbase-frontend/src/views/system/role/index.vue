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
        <el-table-column prop="dataScope" label="数据范围" width="100" />
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="formData.roleName" placeholder="请输入角色名称" />
        </el-form-item>

        <!-- 【已删除角色标识输入框，后端自动生成】 -->

        <el-form-item label="数据范围" prop="dataScope">
          <el-input-number v-model="formData.dataScope" :min="1" :max="10" />
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
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRoleList, addRole, updateRole, deleteRole } from '@/api/role'

const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.id ? '编辑角色' : '新增角色')
const isEdit = ref(false)
const formRef = ref(null)

const formData = reactive({
  id: null,
  tenantId: 1,
  roleName: '',
  roleKey: '',
  dataScope: 1,
  sortNum: 0,
  status: 0,
  remark: ''
})

// 只保留角色名称必填，删掉 roleKey 校验
const formRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const fetchData = async () => {
  try {
    const res = await getRoleList()
    tableData.value = res.data.records || []
  } catch (error) {
    console.error('获取角色列表失败', error)
  }
}

const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, { 
    id: null, tenantId: 1, roleName: '', 
    roleKey: '', dataScope: 1, sortNum: 0, status: 0, remark: '' 
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  // 编辑回显roleKey，但页面不展示、不可改
  Object.assign(formData, row)
  dialogVisible.value = true
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
        // 编辑：后端不修改roleKey，只改其他字段
        await updateRole(formData)
        ElMessage.success('更新成功')
      } else {
        // 新增：后端自动生成roleKey，前端不用传值
        await addRole(formData)
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
}

fetchData()
</script>

<style scoped>
.role-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>