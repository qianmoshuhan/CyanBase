<template>
  <div class="rate-limit-container">

      <!-- 搜索表单 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="请输入规则名称/接口地址" clearable />
        </el-form-item>
        <el-form-item>

          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="list" border stripe highlight-current-row>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="apiPath" label="接口地址" min-width="200" />
        <el-table-column prop="limitCount" label="限流次数" width="100" />
        <el-table-column prop="timeWindow" label="时间窗口(秒)" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="warning" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>



    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="80%">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="接口地址" prop="apiPath">
          <el-input v-model="formData.apiPath" placeholder="请输入接口地址（如/api/user）" />
        </el-form-item>
        <el-form-item label="限流次数" prop="limitCount">
          <el-input-number v-model="formData.limitCount" min="1" max="1000" placeholder="请输入限流次数" />
        </el-form-item>
        <el-form-item label="时间窗口" prop="timeWindow">
          <el-input-number v-model="formData.timeWindow" min="1" max="3600" placeholder="请输入时间窗口（秒）" />
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
import { Plus } from '@element-plus/icons-vue'


// 查询条件
const query = reactive({ keyword: '' })

// 列表数据
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

// 弹窗
const dialogVisible = ref(false)
const isEdit = ref(false)
const dialogTitle = computed(() => isEdit.value ? '编辑限流规则' : '新增限流规则')

// 表单
const formRef = ref(null)
const formData = reactive({
  id: null,
  apiPath: '',
  limitCount: 10,
  timeWindow: 60
})
const formRules = {
  apiPath: [{ required: true, message: '请输入接口地址', trigger: 'blur' }],
  limitCount: [{ required: true, message: '请输入限流次数', trigger: 'blur' }],
  timeWindow: [{ required: true, message: '请输入时间窗口', trigger: 'blur' }]
}


// 新增
const handleAdd = () => {
  isEdit.value = false
  // 重置表单
  formData.id = null
  formData.apiPath = ''
  formData.limitCount = 10
  formData.timeWindow = 60
  dialogVisible.value = true
  // 重置表单验证
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

// 编辑
const handleEdit = (row) => {
  isEdit.value = true
  // 赋值表单
  formData.id = row.id
  formData.apiPath = row.apiPath
  formData.limitCount = row.limitCount
  formData.timeWindow = row.timeWindow
  dialogVisible.value = true
  // 重置表单验证
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}







</script>

<style scoped>
.rate-limit-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
</style>