<template>
  <div class="sydyd-item-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>三重一大 - 采购事项</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增事项
          </el-button>
        </div>
      </template>

      <el-form :inline="!isMobile" class="search-form">
        <el-form-item label="事项名称">
          <el-input v-model="query.itemName" placeholder="请输入事项名称" clearable />
        </el-form-item>
        <el-form-item label="事项类型">
          <el-select v-model="query.itemType" placeholder="全部" clearable style="width: 140px">
            <el-option label="大额资金" :value="1" />
            <el-option label="重大项目" :value="2" />
            <el-option label="人事任免" :value="3" />
            <el-option label="其他" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.itemStatus" placeholder="全部" clearable style="width: 140px">
            <el-option label="草稿" :value="1" />
            <el-option label="待酝酿" :value="2" />
            <el-option label="已生成议题" :value="3" />
            <el-option label="已上会" :value="4" />
            <el-option label="已办结" :value="5" />
            <el-option label="已驳回" :value="6" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-scroll-wrapper">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="itemName" label="事项名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="itemType" label="类型" width="100">
          <template #default="{ row }">
            {{ itemTypeMap[row.itemType] || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="fundAmount" label="资金(万元)" width="110" align="right">
          <template #default="{ row }">
            {{ row.fundAmount ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="hostDept" label="主办部门" width="130" />
        <el-table-column prop="applicant" label="申请人" width="100" />
        <el-table-column prop="itemStatus" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.itemStatus)">
              {{ statusMap[row.itemStatus] || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="workflowInstanceId" label="流程实例" width="100">
          <template #default="{ row }">
            <span v-if="row.workflowInstanceId">#{{ row.workflowInstanceId }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleView(row)">详情</el-button>
            <el-button
              v-if="row.itemStatus === 1"
              type="warning" link
              @click="handleEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="row.itemStatus === 1"
              type="success" link
              @click="handleSubmit(row)"
            >提交</el-button>
            <el-button
              v-if="row.itemStatus === 2 || row.itemStatus === 6"
              type="info" link
              @click="handleWithdraw(row)"
            >回退</el-button>
            <el-button
              v-if="row.itemStatus === 1"
              type="danger" link
              @click="handleDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="formVisible" :title="formTitle" :width="isMobile ? '92%' : '680px'" @close="handleFormClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="事项清单" prop="listId">
          <el-select v-model="formData.listId" placeholder="请选择所属清单" style="width: 100%">
            <el-option
              v-for="opt in listOptions"
              :key="opt.id"
              :label="opt.listName + (opt.amountThreshold ? '（门槛≥' + opt.amountThreshold + '万元）' : '')"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="事项名称" prop="itemName">
          <el-input v-model="formData.itemName" placeholder="请输入事项名称" />
        </el-form-item>
        <el-form-item label="事项类型" prop="itemType">
          <el-select v-model="formData.itemType" placeholder="请选择" style="width: 100%">
            <el-option label="大额资金" :value="1" />
            <el-option label="重大项目" :value="2" />
            <el-option label="人事任免" :value="3" />
            <el-option label="其他" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="资金(万元)" prop="fundAmount">
          <el-input-number v-model="formData.fundAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="主办部门" prop="hostDept">
          <el-input v-model="formData.hostDept" placeholder="如：技术部" />
        </el-form-item>
        <el-form-item label="现状说明">
          <el-input v-model="formData.currentStatus" type="textarea" :rows="3" placeholder="请描述当前现状" />
        </el-form-item>
        <el-form-item label="论证材料">
          <el-input v-model="formData.proofMaterial" placeholder="附件路径（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 选择工作流弹框 -->
    <el-dialog v-model="workflowDialogVisible" title="选择审批流程" :width="isMobile ? '92%' : '480px'">
      <div style="margin-bottom: 12px; color: #606266;">
        找到 {{ workflowOptions.length }} 个「三重一大事项」类型的已发布流程，请选择一个：
      </div>
      <el-radio-group v-model="selectedWorkflowId" style="width: 100%; display: flex; flex-direction: column; gap: 8px;">
        <el-radio
          v-for="w in workflowOptions"
          :key="w.id"
          :value="w.id"
          border
          style="margin-right: 0; width: 100%;"
        >
          <span style="font-weight: 500;">{{ w.processName }}</span>
          <span style="color: #909399; font-size: 12px; margin-left: 8px;">v{{ w.version }}</span>
        </el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="workflowDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmWorkflowSubmit">确定提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getItemList, createItem, updateItem, deleteItem, submitItem, withdrawItem,
  getThreeBigListAll, submitItemWithDefinition
} from '@/api/sydyd'
import { getPublishedWorkflowList } from '@/api/wfDefinition'

const router = useRouter()

// ⭐ 响应式检测
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

const itemTypeMap = { 1: '大额资金', 2: '重大项目', 3: '人事任免', 4: '其他' }
const statusMap = { 1: '草稿', 2: '待酝酿', 3: '已生成议题', 4: '已上会', 5: '已办结', 6: '已驳回' }
const BUSINESS_TYPE = 'threeBigItem'

/** 选流程弹框 */
const workflowDialogVisible = ref(false)
const workflowOptions = ref([])
const selectedWorkflowId = ref(null)
const submittingRow = ref(null)

const getStatusType = (s) => ({ 1: 'info', 2: 'warning', 3: '', 4: '', 5: 'success', 6: 'danger' }[s] || '')

const query = reactive({ itemName: '', itemType: null, itemStatus: null })
const loading = ref(false)
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })

const formVisible = ref(false)
const formIsEdit = ref(false)
const formRef = ref(null)
const formData = reactive({
  id: null, listId: null, itemName: '', itemType: null, fundAmount: null,
  hostDept: '', currentStatus: '', proofMaterial: ''
})
const listOptions = ref([])
const loadListOptions = async () => {
  try {
    const res = await getThreeBigListAll()
    listOptions.value = res.data?.records || []
  } catch (e) {
    console.error(e)
    listOptions.value = []
  }
}
const formTitle = computed(() => formIsEdit.value ? '编辑事项' : '新增事项')
const formRules = {
  listId: [{ required: true, message: '请选择事项清单', trigger: 'change' }],
  itemName: [{ required: true, message: '请输入事项名称', trigger: 'blur' }],
  itemType: [{ required: true, message: '请选择事项类型', trigger: 'change' }]
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getItemList({
      pageNum: pagination.current,
      pageSize: pagination.size,
      ...query
    })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleAdd = async () => {
  formIsEdit.value = false
  Object.assign(formData, {
    id: null, listId: null, itemName: '', itemType: null, fundAmount: null,
    hostDept: '', currentStatus: '', proofMaterial: ''
  })
  await loadListOptions()
  if (!listOptions.value.length) {
    ElMessage.warning('当前租户没有启用的清单，请先在清单管理中创建')
  }
  formVisible.value = true
}

const handleEdit = async (row) => {
  formIsEdit.value = true
  await loadListOptions()
  Object.assign(formData, { ...row })
  formVisible.value = true
}

const handleView = (row) => {
  router.push({ path: '/sydyd/item/detail', query: { id: row.id } })
}

const handleFormClose = () => formRef.value?.resetFields()

const handleFormSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (formIsEdit.value) {
        await updateItem(formData)
        ElMessage.success('更新成功')
      } else {
        await createItem(formData)
        ElMessage.success('创建成功')
      }
      formVisible.value = false
      fetchData()
    } catch (e) {
      console.error(e)
    }
  })
}

const handleSubmit = async (row) => {
  if (row.itemStatus !== 1) {
    ElMessage.warning('只有草稿状态的事项才能提交审批')
    return
  }
  try {
    const res = await getPublishedWorkflowList({ businessType: BUSINESS_TYPE })
    const list = res.data || []
    if (list.length === 0) {
      ElMessageBox.alert(
        `未找到「三重一大事项」类型的已发布流程，请先在【工作流定义】中配置并发布。`,
        '无法提交', { type: 'warning', confirmButtonText: '知道了' }
      )
      return
    }
    if (list.length === 1) {
      submitWithDefinition(row, list[0].id)
      return
    }
    // 多个已发布流程 → 弹框选
    submittingRow.value = row
    workflowOptions.value = list
    selectedWorkflowId.value = list[0].id
    workflowDialogVisible.value = true
  } catch (e) {
    ElMessage.error('查询流程失败：' + (e.message || e))
  }
}

const submitWithDefinition = async (row, definitionId) => {
  try {
    await submitItemWithDefinition(row.id, definitionId)
    ElMessage.success('提交成功，工作流已发起')
    workflowDialogVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error('提交失败：' + (e.message || e))
  }
}

const confirmWorkflowSubmit = () => {
  if (!selectedWorkflowId.value) {
    ElMessage.warning('请选择一个流程')
    return
  }
  submitWithDefinition(submittingRow.value, selectedWorkflowId.value)
}

const handleWithdraw = (row) => {
  ElMessageBox.confirm('确定回退为草稿？', '提示', { type: 'warning' })
    .then(async () => {
      await withdrawItem(row.id)
      ElMessage.success('已回退')
      fetchData()
    }).catch(() => {})
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定删除该草稿？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteItem(row.id)
      ElMessage.success('删除成功')
      fetchData()
    }).catch(() => {})
}

const handleReset = () => {
  query.itemName = ''
  query.itemType = null
  query.itemStatus = null
  pagination.current = 1
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.sydyd-item-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
.text-muted { color: #999; }
</style>