<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>参数配置</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增配置
          </el-button>
        </div>
      </template>
      <el-form :inline="true" class="search-form">
        <el-form-item label="参数名称">
          <el-input v-model="searchForm.configName" placeholder="请输入参数名称" clearable />
        </el-form-item>
        <el-form-item label="参数键名">
          <el-input v-model="searchForm.configKey" placeholder="请输入参数键名" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="tableData" border stripe>
        <el-table-column prop="configId" label="参数ID" width="100" />
        <el-table-column prop="configName" label="参数名称" min-width="150" />
        <el-table-column prop="configKey" label="参数键名" min-width="150" />
        <el-table-column prop="configValue" label="参数键值" min-width="200" show-overflow-tooltip />
        <el-table-column prop="configType" label="系统内置" width="100">
          <template #default="{ row }">
            <el-tag :type="row.configType === 1 ? 'success' : 'info'">
              {{ row.configType === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <!--改这里：record→row-->
            <el-button @click="openEdit(row)">编辑</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="formData.configName" placeholder="请输入参数名称" />
        </el-form-item>
        <el-form-item label="参数键名" prop="configKey">
          <el-input v-model="formData.configKey" placeholder="请输入参数键名" />
        </el-form-item>
        <el-form-item label="参数键值" prop="configValue">
          <!-- 只有已经选好system_logo才展示预览，新增空白时永远保留输入框 -->
          <div v-if="formData.configKey === 'system_logo' && formData.configValue" style="display: flex; align-items: center; gap: 10px;">
            <img
              :src="formData.configValue"
              class="logo-preview"
            />
            <span style="color: #999; font-size: 12px;">支持 PNG/JPG/SVG，手动填写图片地址</span>
          </div>
          <!-- 无论什么key，输入框永远存在 -->
          <el-input
            v-model="formData.configValue"
            placeholder="请填写图片地址，例：/cyanbase.png"
            :rows="4"
            type="textarea"
          />
        </el-form-item>
        <el-form-item label="系统内置" prop="configType">
          <el-radio-group v-model="formData.configType">
            <!-- :label数字，1=是，0=否 -->
            <el-radio :label="1">是</el-radio>
            <el-radio :label="0">否</el-radio>
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
import { ref, reactive, computed ,nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getConfigList, addConfig, updateConfig, deleteConfig ,getConfigByKey} from '@/api/config'
const searchForm = reactive({ configName: '', configKey: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })
const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.configId ? '编辑配置' : '新增配置')
const isEdit = ref(false)
const formRef = ref(null)
const sysName = ref('CyanBase')
//删掉无用const form = ref(...)
const formData = reactive({
  configId: null,
  configName: '',
  configKey: '',
  configValue: '',
  configType: 0, // 原来：'N' → 改成数字0
  remark: ''
})
const formRules = {
  configName: [{ required: true, message: '请输入参数名称', trigger: 'blur' }],
  configKey: [{ required: true, message: '请输入参数键名', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入参数键值', trigger: 'blur' }]
}
//重构编辑
const openEdit = async(row)=>{
  if(!row){
    Object.assign(formData,{configId:null,configName:'',configKey:'',configValue:'',configType:'N',remark:''})
  }else{
    Object.assign(formData,row)
  }
  await nextTick()
  dialogVisible.value = true
  isEdit.value = true
}
const fetchData = async () => {
  try {
    const res = await getConfigList({ current: pagination.current, size: pagination.size, ...searchForm })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error(error) }
}
const handleAdd = () => {
  isEdit.value = false;
  Object.assign(formData, { configId: null, configName: '', configKey: '', configValue: '', configType: 'N', remark: '' });
  dialogVisible.value = true
}
//删除多余handleEdit
const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该配置吗？', '提示', { type: 'warning' })
    .then(async () => { await deleteConfig(row.configId); ElMessage.success('删除成功'); fetchData() }).catch(() => {})
}
const handleSubmit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) { await updateConfig(formData); ElMessage.success('更新成功') }
      else { await addConfig(formData); ElMessage.success('新增成功') }
      dialogVisible.value = false; fetchData()
    } catch (error) { console.error(error) }
  })
}
const handleDialogClose = () => formRef.value?.resetFields()
const handleReset = () => { Object.assign(searchForm, { configName: '', configKey: '' }); fetchData() }
const logoUrl = ref('')
fetchData()
</script>
<style scoped>
.page-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
.logo-preview {
  width: 40px;
  height: 40px;
  object-fit: contain;
  border: 1px solid #eee;
  border-radius: 4px;
}
</style>