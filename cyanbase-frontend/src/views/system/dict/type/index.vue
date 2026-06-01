<template>
  <div class="dict-type-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>字典类型管理</span>
          <el-button type="success" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增字典类型
          </el-button>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="字典名称">
          <el-input v-model="query.dictName" placeholder="请输入字典名称" clearable />
        </el-form-item>
        <el-form-item label="字典类型编码">
          <el-input v-model="query.dictTypeCode" placeholder="请输入字典类型编码" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="list" border stripe highlight-current-row>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="dictName" label="字典名称" min-width="150" />
        <el-table-column prop="dictTypeCode" label="字典类型编码" min-width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="warning" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="formData.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="字典类型编码" prop="dictTypeCode">
          <el-input v-model="formData.dictTypeCode" placeholder="请输入字典类型编码" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
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
import { getDictTypeList, addDictType, updateDictType, deleteDictType } from '@/api/dictType'

const query = reactive({
  dictName: '',
  dictTypeCode: ''
})

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const isEdit = ref(false)
const dialogTitle = computed(() => isEdit.value ? '编辑字典类型' : '新增字典类型')

const formRef = ref(null)
const formData = reactive({
  id: null,
  dictName: '',
  dictTypeCode: '',
  status: '0',
  remark: ''
})

const formRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictTypeCode: [{ required: true, message: '请输入字典类型编码', trigger: 'blur' }]
}

const fetchData = async () => {
  try {
    const res = await getDictTypeList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...query
    })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error('获取字典类型列表失败', error)
  }
}

const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, {
    id: null,
    dictName: '',
    dictTypeCode: '',
    status: '0',
    remark: ''
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(formData, row)
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该字典类型吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteDictType(row.id)
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
        await updateDictType(formData)
        ElMessage.success('更新成功')
      } else {
        await addDictType(formData)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      console.error('保存失败', error)
    }
  })
}

const handleReset = () => {
  query.dictName = ''
  query.dictTypeCode = ''
  pageNum.value = 1
  fetchData()
}

fetchData()
</script>

<style scoped>
.dict-type-container {
  width: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}
</style>
