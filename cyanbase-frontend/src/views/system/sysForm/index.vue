<template>
  <el-card shadow="hover" class="app-container">
    <template #header>
      <div class="card-header">
        <span>表单管理</span>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增表单
        </el-button>
      </div>
    </template>

    <el-form :inline="true" class="search-form">
      <el-form-item label="表单名称">
        <el-input v-model="queryParams.name" placeholder="请输入表单名称" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option label="正常" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableUp" border style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="formName" label="表单名称" min-width="150" />
      <el-table-column prop="code" label="表单编码" width="150" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'info'">
            {{ row.status === '0' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      
      <!-- 操作列：预览 编辑 设计 删除 -->
      <el-table-column label="操作" width="350" fixed="right">
        <template #default="{ row }">
          <el-button type="success" link size="small" @click="handlePreview(row)">预览</el-button>
          <!-- 新增编辑按钮 -->
          <el-button type="warning" link size="small" @click="handleOpenEdit(row)">编辑</el-button>
          <el-button type="primary" link size="small" @click="handleDesign(row)">设计</el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </el-card>

  <!-- 表单预览弹窗 -->
  <el-dialog v-model="previewVisible" title="表单预览" width="900px">
    <div v-if="!currentLayoutConfig" class="text-center p-4">
      暂无表单配置或配置格式无法识别
    </div>
    <div v-else style="padding: 16px;">
      <form-create
        :rule="JSON.parse(currentLayoutConfig)"
        v-model="previewFormData"
        disabled
      ></form-create>
    </div>
  </el-dialog>

  <!-- 新增：编辑表单名称+状态弹窗 -->
  <el-dialog v-model="editDialogVisible" title="编辑表单信息" width="500px">
    <el-form :model="editForm" label-width="80px">
      <el-form-item label="表单名称">
        <el-input v-model="editForm.formName" placeholder="请输入表单名称" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="editForm.status" placeholder="请选择状态">
          <el-option label="启用" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSaveEdit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addFormDefinition, getFormDefinitionList, deleteFormDefinition, updateFormDefinition } from '@/api/form'

const router = useRouter()
const loading = ref(false)
const tableUp = ref([])
const total = ref(0)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  name: '',
  status: ''
})

// 预览相关
const previewFormData = ref({})
const currentLayoutConfig = ref(""); 
const currentFormName = ref('')
const previewVisible = ref(false)

// 编辑弹窗相关
const editDialogVisible = ref(false)
// 编辑表单临时对象
const editForm = ref({
  id: '',
  formName: '',
  status: ''
})

// 新增表单
const handleAdd = async () => {
  try {
    const { value: formName } = await ElMessageBox.prompt('请输入表单名称', '新建表单', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /.{1,}/,
      inputErrorMessage: '表单名称不能为空'
    })
    const res = await addFormDefinition({ formName })
    router.push({
      path: '/form/designer',
      query: {
        action: 'new',
        name: formName,
        id: res.data
      }
    })
    ElMessage.success('新建成功，正在跳转...')
  } catch (err) {
    if (err !== 'cancel' && !ElMessageBox.isClose(err)) {
      ElMessage.error('新建失败')
      console.error(err)
    }
  }
}

// 设计
const handleDesign = (row) => {
  router.push({
    path: '/form/designer',
    query: { id: row.id, name: row.formName, action: 'edit' }
  })
}

// 预览
const handlePreview = (row) => {
  console.log("预览行数据:", row);
  currentLayoutConfig.value = row.layoutConfig || "";
  currentFormName.value = row.formName || "表单预览";
  previewVisible.value = true;
};
const handleOpenEdit = (row) => {
  editForm.value.id = row.id
  editForm.value.formName = row.formName
  editForm.value.status = row.status ?? '0'
  // ✅ 关键：把原来的 layoutConfig 存起来，编辑时原样传回后端
  editForm.value.layoutConfig = row.layoutConfig  
  editDialogVisible.value = true
}

const handleSaveEdit = async () => {
  if (!editForm.value.formName.trim()) {
    ElMessage.error('表单名称不能为空')
    return
  }
  try {
    await updateFormDefinition({
      id: editForm.value.id,
      formName: editForm.value.formName,
      status: editForm.value.status,
      // ✅ 关键：把原来的 layoutConfig 原样传回去，不修改
      layoutConfig: editForm.value.layoutConfig  
    })
    ElMessage.success('修改成功')
    editDialogVisible.value = false
    getList()
  } catch (err) {
    ElMessage.error('修改失败')
    console.error(err)
  }
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除表单 "${row.formName}" 吗？`,
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
    await deleteFormDefinition(row.id);
    ElMessage.success('删除成功');
    getList();
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error('删除失败，请稍后再试');
    }
  }
};

// 查询分页
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}
const resetQuery = () => {
  queryParams.name = ''
  queryParams.status = ''
  handleQuery()
}
const handleSizeChange = (size) => {
  queryParams.pageSize = size
  getList()
}
const handleCurrentChange = (page) => {
  queryParams.pageNum = page
  getList()
}

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await getFormDefinitionList(queryParams)
    let responseData = null;
    if (Array.isArray(res)) {
      responseData = { records: res, total: res.length };
    } else if (res && res.data && Array.isArray(res.data.records)) {
      responseData = res.data;
    } else if (res && res.data && res.data.data && Array.isArray(res.data.data.records)) {
      responseData = res.data.data;
    } else {
      responseData = res || {};
    }
    tableUp.value = responseData.records || []
    total.value = responseData.total || 0
  } catch (error) {
    console.error('获取列表失败:', error)
    ElMessage.error('获取表单列表失败')
    tableUp.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.search-form {
  margin-bottom: 20px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.text-center {
  text-align: center;
}
.p-4 {
  padding: 16px;
}
</style>