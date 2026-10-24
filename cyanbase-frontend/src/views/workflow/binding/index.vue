<template>
  <div class="binding-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span><el-icon style="margin-right:6px;"><Connection /></el-icon>表单 ↔ 流程绑定配置</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增绑定
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :inline="!isMobile" class="search-form">
        <el-form-item label="表单名称">
          <el-input v-model="query.formKeyword" placeholder="输入表单名模糊搜索" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px;">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="list" border stripe highlight-current-row>
        <el-table-column type="index" label="序号" width="60" align="center" :index="(i) => (pageNum-1)*pageSize + i + 1" />
        <el-table-column label="表单" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag type="info" effect="plain" size="small">ID:{{ row.definitionId }}</el-tag>
            <span style="margin-left:6px;">{{ row.formName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="流程" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag type="warning" effect="plain" size="small">ID:{{ row.processDefinitionId }}</el-tag>
            <span style="margin-left:6px;">{{ row.processName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deptId" label="匹配部门ID" width="110">
          <template #default="{ row }">{{ row.deptId || '—' }}</template>
        </el-table-column>
        <el-table-column prop="deptPath" label="部门路径" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptPath || '—' }}</template>
        </el-table-column>
        <el-table-column prop="businessType" label="业务类型" width="120">
          <template #default="{ row }">{{ row.businessType || '—' }}</template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除这条绑定吗？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        @size-change="fetchData"
        @current-change="fetchData"
        style="margin-top:16px;justify-content:flex-end;"
      />
    </el-card>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑绑定' : '新增绑定'" width="640px" top="8vh">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="表单" prop="definitionId">
          <el-select v-model="form.definitionId" placeholder="请选择表单" filterable style="width:100%;" :disabled="isEdit">
            <el-option
              v-for="f in formOptions" :key="f.id"
              :label="`${f.formName} (ID:${f.id})`"
              :value="f.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="流程" prop="processDefinitionId">
          <el-select v-model="form.processDefinitionId" placeholder="请选择已发布流程" filterable style="width:100%;">
            <el-option
              v-for="p in processOptions" :key="p.id"
              :label="`${p.processName} (${p.processKey})`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">路由规则（均空则为兜底默认）</el-divider>

        <el-form-item label="匹配部门ID">
          <el-select v-model="form.deptId" placeholder="可选：精确匹配部门" clearable filterable style="width:100%;">
            <el-option
              v-for="d in deptOptions" :key="d.id"
              :label="`${d.deptName} (ID:${d.id})`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="部门路径">
          <el-input v-model="form.deptPath" placeholder='可选：逗号分隔，如 "100,101,102"；含子部门时精确匹配父部门即可' clearable />
          <div style="font-size:12px;color:#909399;margin-top:4px;">
            多部门逗号分隔，匹配时检查用户部门是否在此路径集合中（含子部门）
          </div>
        </el-form-item>

        <el-form-item label="业务类型">
          <el-input v-model="form.businessType" placeholder="可选，如 Meeting / ThreeBigItem" clearable />
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="form.priority" :min="0" :max="9999" controls-position="right" style="width:100%;" />
              <div style="font-size:12px;color:#909399;margin-top:4px;">数字越大越优先（多个绑定都命中时取高）</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" style="margin-top:8px;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Plus } from '@element-plus/icons-vue'
import { getBindingList, addBinding, updateBinding, deleteBinding } from '@/api/binding'
import { getFormDefAllActive } from '@/api/form'
import { getPublishedWorkflowList } from '@/api/wfDefinition'
import { getDeptList } from '@/api/dept'

// ⭐ 响应式
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

// 查询 + 列表
const query = reactive({ formKeyword: '', status: null })
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

// 弹窗
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null, definitionId: null, processDefinitionId: null,
  deptId: null, deptPath: '', businessType: '',
  priority: 0, status: 1, remark: ''
})
const rules = {
  definitionId:        [{ required: true, message: '请选择表单', trigger: 'change' }],
  processDefinitionId: [{ required: true, message: '请选择流程', trigger: 'change' }]
}

// 下拉选项
const formOptions = ref([])
const processOptions = ref([])
const deptOptions = ref([])

// 加载下拉
const loadOptions = async () => {
  try {
    const [fRes, pRes, dRes] = await Promise.all([
      getFormDefAllActive(),
      getPublishedWorkflowList(),
      getDeptList()
    ])
    formOptions.value = fRes.data || []
    processOptions.value = pRes.data || []
    deptOptions.value = Array.isArray(dRes.data) ? dRes.data : (dRes.data?.records || [])
  } catch (e) {
    console.warn('加载下拉选项失败', e)
  }
}

// 列表查询
const fetchData = async () => {
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value, status: query.status }
    const res = await getBindingList(params)
    // 前端过滤（后端没做 formKeyword，一期简单处理）
    let records = res.data?.records || []
    if (query.formKeyword) {
      const kw = query.formKeyword.toLowerCase()
      records = records.filter(r =>
        (r.formName && r.formName.toLowerCase().includes(kw)) ||
        (r.processName && r.processName.toLowerCase().includes(kw))
      )
    }
    list.value = records
    total.value = res.data?.total || 0
  } catch (e) {
    console.error('获取绑定列表失败', e)
  }
}

const handleReset = () => { query.formKeyword = ''; query.status = null; pageNum.value = 1; fetchData() }

// 新增
const handleAdd = () => {
  isEdit.value = false
  Object.assign(form, {
    id: null, definitionId: null, processDefinitionId: null,
    deptId: null, deptPath: '', businessType: '',
    priority: 0, status: 1, remark: ''
  })
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(form, {
    id: row.id, definitionId: row.definitionId, processDefinitionId: row.processDefinitionId,
    deptId: row.deptId, deptPath: row.deptPath || '', businessType: row.businessType || '',
    priority: row.priority ?? 0, status: row.status ?? 1, remark: row.remark || ''
  })
  dialogVisible.value = true
}

// 提交
const handleSubmit = async () => {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateBinding({ ...form })
      ElMessage.success('更新成功')
    } else {
      await addBinding({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    // 后端 BusinessException 会返回统一错误消息，request 拦截器已弹
    console.warn('提交失败', e)
  } finally {
    submitting.value = false
  }
}

// 删除
const handleDelete = async (row) => {
  try {
    await deleteBinding(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e) { console.warn('删除失败', e) }
}

// 初始化
loadOptions()
fetchData()
</script>

<style scoped>
.binding-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; font-size: 15px; }
.search-form { margin-bottom: 16px; }
</style>