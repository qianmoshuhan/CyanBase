<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>岗位管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增岗位
          </el-button>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="岗位编码">
          <el-input v-model="searchForm.postCode" placeholder="请输入岗位编码" clearable />
        </el-form-item>
        <el-form-item label="岗位名称">
          <el-input v-model="searchForm.postName" placeholder="请输入岗位名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
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
        <el-table-column prop="postId" label="岗位ID" width="100" />
        <el-table-column prop="postCode" label="岗位编码" min-width="120" />
        <el-table-column prop="postName" label="岗位名称" min-width="150" />
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
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
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
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="岗位编码" prop="postCode">
          <el-input v-model="formData.postCode" placeholder="请输入岗位编码" />
        </el-form-item>
        <el-form-item label="岗位名称" prop="postName">
          <el-input v-model="formData.postName" placeholder="请输入岗位名称" />
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
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPostList, addPost, updatePost, deletePost } from '@/api/post'

const searchForm = reactive({ postCode: '', postName: '', status: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })

const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.postId ? '编辑岗位' : '新增岗位')
const isEdit = ref(false)
const formRef = ref(null)

const formData = reactive({
  postId: null, postCode: '', postName: '', postSort: 0, status: '0', remark: ''
})

const formRules = {
  postCode: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }],
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
  Object.assign(formData, { postId: null, postCode: '', postName: '', postSort: 0, status: '0', remark: '' })
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

const handleDialogClose = () => formRef.value?.resetFields()
const handleReset = () => { Object.assign(searchForm, { postCode: '', postName: '', status: '' }); fetchData() }

fetchData()
</script>

<style scoped>
.page-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
