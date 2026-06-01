<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>定时任务</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增任务
          </el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="任务名称">
          <el-input v-model="searchForm.jobName" placeholder="请输入任务名称" clearable />
        </el-form-item>
        <el-form-item label="任务分组">
          <el-input v-model="searchForm.jobGroup" placeholder="请输入任务分组" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="正常" value="0" />
            <el-option label="暂停" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="jobId" label="任务ID" width="80" />
        <el-table-column prop="jobName" label="任务名称" min-width="150" />
        <el-table-column prop="jobGroup" label="任务分组" min-width="120" />
        <el-table-column prop="invokeTarget" label="调用目标" min-width="200" show-overflow-tooltip />
        <el-table-column prop="cronExpression" label="Cron" min-width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="'0'" :inactive-value="'1'"  @change="(val) => handleStatusChange(row, val)"/>
          </template>
        </el-table-column>
        <el-table-column prop="concurrent" label="并发" width="80">
          <template #default="{ row }">
            <el-tag :type="row.concurrent === '0' ? 'success' : 'danger'">
              {{ row.concurrent === '0' ? '允许' : '禁止' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="任务名称" prop="jobName">
          <el-input v-model="formData.jobName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务分组" prop="jobGroup">
          <el-input v-model="formData.jobGroup" placeholder="请输入任务分组" />
        </el-form-item>
        <el-form-item label="调用目标" prop="invokeTarget">
          <el-input v-model="formData.invokeTarget" placeholder="请输入调用目标" />
        </el-form-item>
        <el-form-item label="Cron表达式" prop="cronExpression">
          <el-input v-model="formData.cronExpression" placeholder="请输入Cron表达式" />
        </el-form-item>
        <el-form-item label="执行策略">
          <el-select v-model="formData.misfirePolicy" placeholder="请选择执行策略">
            <el-option label="立即执行" value="1" />
            <el-option label="执行一次" value="2" />
            <el-option label="放弃执行" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否并发">
          <el-radio-group v-model="formData.concurrent">
            <el-radio label="0">允许</el-radio>
            <el-radio label="1">禁止</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">暂停</el-radio>
          </el-radio-group>
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
import { getJobList, addJob, updateJob, deleteJob, pauseJob, resumeJob } from '@/api/job'

const searchForm = reactive({ jobName: '', jobGroup: '', status: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })
const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.jobId ? '编辑任务' : '新增任务')
const isEdit = ref(false)
const formRef = ref(null)

const formData = reactive({ jobId: null, jobName: '', jobGroup: 'DEFAULT', invokeTarget: '', cronExpression: '', misfirePolicy: '3', concurrent: '1', status: '0', remark: '' })
const formRules = {
  jobName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  invokeTarget: [{ required: true, message: '请输入调用目标', trigger: 'blur' }],
  cronExpression: [{ required: true, message: '请输入Cron表达式', trigger: 'blur' }]
}

const fetchData = async () => {
  try {
    const res = await getJobList({ current: pagination.current, size: pagination.size, ...searchForm })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error(error) }
}

const handleAdd = () => { isEdit.value = false; Object.assign(formData, { jobId: null, jobName: '', jobGroup: 'DEFAULT', invokeTarget: '', cronExpression: '', misfirePolicy: '3', concurrent: '1', status: '0', remark: '' }); dialogVisible.value = true }
const handleEdit = (row) => { isEdit.value = true; Object.assign(formData, row); dialogVisible.value = true }
const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该任务吗？', '提示', { type: 'warning' })
    .then(async () => { await deleteJob(row.jobId); ElMessage.success('删除成功'); fetchData() }).catch(() => {})
}
const handleSubmit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) { await updateJob(formData); ElMessage.success('更新成功') }
      else { await addJob(formData); ElMessage.success('新增成功') }
      dialogVisible.value = false; fetchData()
    } catch (error) { console.error(error) }
  })
}
const handleDialogClose = () => {}
const handleReset = () => { Object.assign(searchForm, { jobName: '', jobGroup: '', status: '' }); fetchData() }

fetchData()
const handleStatusChange = async (row, newStatus) => {
  try {
    if (!row.jobId) return
    if (newStatus === '0') {
      await resumeJob(row.jobId)
      ElMessage.success('任务已恢复')
    } else {
      await pauseJob(row.jobId)
      ElMessage.success('任务已暂停')
    }
  } catch (e) {
    ElMessage.error('操作失败')
    // 失败后回显
    row.status = newStatus === '0' ? '1' : '0'
  }
}
</script>

<style scoped>
.page-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
