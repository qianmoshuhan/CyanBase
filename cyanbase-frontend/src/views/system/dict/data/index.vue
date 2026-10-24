<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>字典数据管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增数据
          </el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="字典类型">
          <el-input v-model="searchForm.dictTypeCode" placeholder="请输入字典类型编码" clearable />
        </el-form-item>
        <el-form-item label="字典标签">
          <el-input v-model="searchForm.dictLabel" placeholder="请输入字典标签" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="dictLabel" label="字典标签" min-width="150" />
        <el-table-column prop="dictValue" label="字典键值" min-width="150" />
        <el-table-column prop="dictTypeCode" label="字典类型编码" min-width="120" />
        <el-table-column prop="sortNum" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '正常' : '停用' }}
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
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="字典类型编码" prop="dictTypeCode">
          <el-select v-model="formData.dictTypeCode" placeholder="请选择字典类型" filterable style="width:100%">
            <el-option v-for="t in dictTypeOptions" :key="t.dictTypeCode"
                       :label="t.dictName + ' (' + t.dictTypeCode + ')'"
                       :value="t.dictTypeCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="formData.dictLabel" placeholder="请输入字典标签" />
        </el-form-item>
        <el-form-item label="字典键值" prop="dictValue">
          <el-input v-model="formData.dictValue" placeholder="请输入字典键值" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="formData.sortNum" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" rows="3" />
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
import { getDictDataList, addDictData, updateDictData, deleteDictData } from '@/api/dictData'
import { getDictTypeAllActive } from '@/api/dictType'

const searchForm = reactive({ dictTypeCode: '', dictLabel: '', status: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })
const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.id ? '编辑字典数据' : '新增字典数据')
const isEdit = ref(false)
const formRef = ref(null)
const dictTypeOptions = ref([])

const formData = reactive({ id: null, dictTypeCode: '', dictLabel: '', dictValue: '', sortNum: 0, status: '0', remark: '' })
const formRules = {
  dictTypeCode: [{ required: true, message: '请选择字典类型', trigger: 'change' }],
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典键值', trigger: 'blur' }]
}

const fetchData = async () => {
  try {
    const res = await getDictDataList({ pageNum: pagination.current, pageSize: pagination.size, ...searchForm })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error(error) }
}

const loadDictTypeOptions = async () => {
  try {
    const res = await getDictTypeAllActive()
    dictTypeOptions.value = res.data || []
  } catch (error) {
    console.error('加载字典类型下拉失败', error)
  }
}

const handleAdd = () => { isEdit.value = false; Object.assign(formData, { id: null, dictTypeCode: '', dictLabel: '', dictValue: '', sortNum: 0, status: '0', remark: '' }); dialogVisible.value = true }
const handleEdit = (row) => { isEdit.value = true; Object.assign(formData, row); dialogVisible.value = true }
const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该字典数据吗？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteDictData(row.id)
      ElMessage.success('删除成功')
      fetchData()
    }).catch(() => {})
}
const handleSubmit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) { await updateDictData(formData); ElMessage.success('更新成功') }
      else { await addDictData(formData); ElMessage.success('新增成功') }
      dialogVisible.value = false; fetchData()
    } catch (error) { console.error(error) }
  })
}
const handleDialogClose = () => formRef.value?.resetFields()
const handleReset = () => { Object.assign(searchForm, { dictTypeCode: '', dictLabel: '', status: '' }); fetchData() }

// 页面初始化：加载字典数据列表 + 加载字典类型下拉选项
fetchData()
loadDictTypeOptions()
</script>

<style scoped>
.page-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>