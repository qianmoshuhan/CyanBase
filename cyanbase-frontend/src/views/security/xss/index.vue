<template>
  <div class="xss-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>XSS防护配置</span>
          <el-button type="success" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="请输入规则名称/防护字段" clearable />
        </el-form-item>
        <el-form-item>
  

        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="list" border stripe highlight-current-row>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="fieldName" label="防护字段" min-width="150" />
        <el-table-column prop="filterType" label="过滤类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.filterType === 'BLACKLIST' ? 'danger' : 'success'">
              {{ row.filterType === 'BLACKLIST' ? '黑名单' : '白名单' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLE' ? 'success' : 'danger'">
              {{ row.status === 'ENABLE' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
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

      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="80%">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="防护字段" prop="fieldName">
          <el-input v-model="formData.fieldName" placeholder="请输入需要防护的字段名（如content）" />
        </el-form-item>
        <el-form-item label="过滤类型" prop="filterType">
          <el-select v-model="formData.filterType" placeholder="请选择过滤类型">
            <el-option label="黑名单" value="BLACKLIST" />
            <el-option label="白名单" value="WHITELIST" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则内容" prop="ruleContent">
          <el-input v-model="formData.ruleContent" type="textarea" rows="4" placeholder="请输入规则内容（如正则表达式）" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="formData.status" placeholder="请选择状态">
            <el-option label="启用" value="ENABLE" />
            <el-option label="禁用" value="DISABLE" />
          </el-select>
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
const dialogTitle = computed(() => isEdit.value ? '编辑XSS防护配置' : '新增XSS防护配置')

// 表单
const formRef = ref(null)
const formData = reactive({
  id: null,
  fieldName: '',
  filterType: 'BLACKLIST',
  ruleContent: '',
  status: 'ENABLE'
})
const formRules = {
  fieldName: [{ required: true, message: '请输入防护字段', trigger: 'blur' }],
  filterType: [{ required: true, message: '请选择过滤类型', trigger: 'change' }],
  ruleContent: [{ required: true, message: '请输入规则内容', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}



// 新增
const handleAdd = () => {
  isEdit.value = false
  // 重置表单
  formData.id = null
  formData.fieldName = ''
  formData.filterType = 'BLACKLIST'
  formData.ruleContent = ''
  formData.status = 'ENABLE'
  dialogVisible.value = true
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

// 编辑
const handleEdit = (row) => {
  isEdit.value = true
  // 赋值表单
  formData.id = row.id
  formData.fieldName = row.fieldName
  formData.filterType = row.filterType
  formData.ruleContent = row.ruleContent
  formData.status = row.status
  dialogVisible.value = true
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}





</script>

<style scoped>
.xss-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
</style>