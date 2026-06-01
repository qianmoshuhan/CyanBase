<template>
  <div class="dept-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>部门管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增部门
          </el-button>
        </div>
      </template>

      <el-table 
        :data="tableData" 
        border stripe
        default-sort="{ prop: 'sortNum', order: 'ascending' }"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="deptName" label="部门名称" min-width="150" :formatter="formatDeptName" />
        <el-table-column prop="parentId" label="父部门ID" width="100" />
        <el-table-column prop="sortNum" label="排序" width="80" sortable />
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
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="formData.deptName" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="父部门ID" prop="parentId">
          <el-input-number v-model="formData.parentId" :min="0" />
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
import { getDeptList, addDept, updateDept, deleteDept } from '@/api/dept'

const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.id ? '编辑部门' : '新增部门')
const isEdit = ref(false)
const formRef = ref(null)

const formData = reactive({
  id: null,
  tenantId: 1,
  parentId: 0,
  deptName: '',
  sortNum: 0,
  status: 0,
  remark: ''
})

// 校验规则
const formRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  parentId: [{ required: true, message: '请输入父部门ID', trigger: 'change' }],
  sortNum: [{ required: true, message: '请输入排序号', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

// 获取数据 + 排序 + 生成层级 level
const fetchData = async () => {
  try {
    const res = await getDeptList()
    let list = res.data.records || []

    // 按 sort 升序
    list.sort((a, b) => a.sortNum - b.sortNum)

    // 计算层级
    const map = new Map()
    list.forEach(item => map.set(item.id, item))
    list.forEach(item => {
      let level = 0
      let p = map.get(item.parentId)
      while (p) {
        level++
        p = map.get(p.parentId)
      }
      item.level = level
    })

    tableData.value = list
  } catch (error) {
    console.error(error)
  }
}

// 部门名称缩进格式化
const formatDeptName = (row) => {
  return '　　'.repeat(row.level || 0) + row.deptName
}

const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, {
    id: null, tenantId: 1, parentId: 0,
    deptName: '', sortNum: 0, status: 0, remark: ''
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(formData, row)
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该部门吗？', '提示', {
    type: 'warning'
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
}

fetchData()
</script>

<style scoped>
.dept-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>