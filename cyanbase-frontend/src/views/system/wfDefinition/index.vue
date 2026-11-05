﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿<template>
  <el-card shadow="hover" class="app-container">
    <template #header>
      <div class="card-header">
        <span>工作流管理</span>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增流程
        </el-button>
      </div>
    </template>
    <el-form :inline="true" class="search-form">
      <el-form-item label="流程名称">
        <el-input v-model="queryParams.processName" placeholder="请输入流程名称" clearable />
      </el-form-item>
      <el-form-item label="流程标识">
        <el-input v-model="queryParams.processKey" placeholder="请输入标识" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option label="草稿" value="0" />
          <el-option label="已发布" value="1" />
          <el-option label="已停用" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="tableData" border style="width: 100%">
      <el-table-column type="index" label="序号" width="70" align="center" :index="(i) => (queryParams.pageNum - 1) * queryParams.pageSize + i + 1" />
      <el-table-column prop="processName" label="流程名称" min-width="180" />
      <el-table-column prop="processKey" label="流程标识" width="200" />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="version" label="版本" width="80" />

      <!-- ⭐ 新增：数据来源列 -->
      <el-table-column label="来源" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.source === 'OWN'" type="primary">本租户</el-tag>
          <el-tag v-else-if="row.source === 'DISTRIBUTED'" type="warning">分发给我</el-tag>
          <el-tag v-else type="info">未知</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="
            row.status === 1 ? 'success' : 
            row.status === 2 ? 'info' : 'warning'
          ">
            {{ 
              row.status === 0 ? '草稿' : 
              row.status === 1 ? '已发布' : 
              row.status === 2 ? '已停用' : '未知' 
            }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="200" />
      
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <!-- 需求4：新增 preview/designer 权限，保留现有分发给租户不可编辑的禁用逻辑 -->
          <el-button type="success" link size="small" @click="handlePreview(row)" v-hasPermi="['workflow:definition:preview']">预览</el-button>
          <!-- ⭐ 分发给我且不允许编辑 → 置灰 -->
          <el-tooltip v-if="isDistributedNoEdit(row)" content="分发给您的流程，仅可使用不可修改" placement="top">
            <span>
              <el-button type="warning" link size="small" disabled>编辑</el-button>
            </span>
          </el-tooltip>
          <el-button v-else type="warning" link size="small" @click="handleOpenEdit(row)" v-hasPermi="['workflow:definition:save']">编辑</el-button>

          <el-tooltip v-if="isDistributedNoEdit(row)" content="分发给您的流程，仅可使用不可修改" placement="top">
            <span>
              <el-button type="primary" link size="small" disabled>设计</el-button>
            </span>
          </el-tooltip>
          <el-button v-else type="primary" link size="small" @click="handleDesign(row)" v-hasPermi="['workflow:definition:designer']">设计</el-button>

          <el-tooltip v-if="isDistributedNoEdit(row)" content="分发给您的流程，仅可使用不可修改" placement="top">
            <span>
              <el-button type="danger" link size="small" disabled>删除</el-button>
            </span>
          </el-tooltip>
          <el-button v-else type="danger" link size="small" @click="handleDelete(row)" v-hasPermi="['workflow:definition:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination">
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </el-card>

  <!-- ============ 版本选择弹窗(新增) ============ -->
  <el-dialog v-model="versionDialogVisible" title="选择流程版本" width="720px" :close-on-click-modal="true">
    <div v-loading="versionLoading">
      <el-table
        :data="versionList"
        border
        highlight-current-row
        :row-class-name="tableRowClassName"
        @current-change="handleVersionRowChange"
        style="width: 100%">
        <el-table-column label="版本号" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isLatest === 1 ? 'success' : 'info'">v{{ row.versionNo }}</el-tag>
            <span v-if="row.isLatest === 1" style="margin-left:6px;font-size:11px;color:#67c23a">最新</span>
          </template>
        </el-table-column>
        <el-table-column prop="versionName" label="版本名称" min-width="160">
          <template #default="{ row }">
            <span v-if="row.versionName">{{ row.versionName }}</span>
            <span v-else style="color:#bbb">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="保存时间" width="200" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <!-- 需求5：最新版本→正常编辑模式（可发布/分发）；非最新→只读查看 -->
            <el-button
              v-if="row.isLatest === 1"
              type="primary" link size="small"
              @click="openDesignerFromVersion(row, 'latest-edit')">设计</el-button>
            <el-button
              v-else
              type="info" link size="small"
              @click="openDesignerFromVersion(row, 'view')">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <template #footer>
      <el-button @click="versionDialogVisible = false">取消</el-button>
      <el-button
        type="primary"
        :disabled="!selectedVersionRow"
        :loading="creatingVersion"
        @click="handleCreateNewVersion">
        新建版本
      </el-button>
    </template>
  </el-dialog>

  <!-- ========== 新建版本确认弹窗(新增) ========== -->
  <el-dialog v-model="confirmCreateVersionDialogVisible" title="新建版本" width="420px"
             :close-on-click-modal="false" append-to-body>
    <el-form label-width="90px">
      <el-form-item label="源版本">
        <el-tag type="info">v{{ selectedVersionRow?.versionNo }}</el-tag>
        <span v-if="selectedVersionRow?.versionName" style="margin-left:8px;color:#606266">{{ selectedVersionRow.versionName }}</span>
        <span v-else style="margin-left:8px;color:#bbb">—</span>
      </el-form-item>
      <el-form-item label="新版本号">
        <el-tag type="success">v{{ newVersionNoPreview }}</el-tag>
        <span style="margin-left:6px;color:#909399;font-size:12px">(自动计算)</span>
      </el-form-item>
      <el-form-item label="版本名称">
        <el-input v-model="newVersionNameInput" placeholder="如：简化审批流程、测试版" maxlength="50" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="confirmCreateVersionDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="creatingVersion" @click="confirmCreateVersion">确认创建</el-button>
    </template>
  </el-dialog>

  <!-- 编辑弹窗 -->
  <el-dialog v-model="editDialogVisible" title="编辑流程信息" width="520px">
    <el-form :model="editForm" label-width="100px">
      <el-form-item label="流程名称">
        <el-input v-model="editForm.processName" placeholder="请输入" />
      </el-form-item>
      <el-form-item label="流程标识">
        <el-input v-model="editForm.processKey" disabled />
      </el-form-item>
      <el-form-item label="分类">
        <el-input v-model="editForm.category" placeholder="请输入" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="editForm.status">
          <el-option label="草稿" value="0" />
          <el-option label="已发布" value="1" />
          <el-option label="已停用" value="2" />
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
defineOptions({ name: 'WfDefinition' })
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWorkflowDefinitionList, deleteWorkflowDefinition, updateWorkflowDefinition, addWorkflowDefinition, getVersionList, createVersionFrom } from '@/api/wfDefinition'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  processName: '',
  processKey: '',
  status: ''
})

// 编辑弹窗
const editDialogVisible = ref(false)
const editForm = ref({
  id: '',
  processName: '',
  processKey: '',
  category: '',
  status: 0,
  processXml: ''
})

// 判断：分发给我 且 不允许编辑 → 禁用编辑/设计/删除
const isDistributedNoEdit = (row) => {
  return row.source === 'DISTRIBUTED' && row.allowEdit !== true
}

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await getWorkflowDefinitionList(queryParams)
    tableData.value = res.data.records
    total.value = res.data.total
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

// 打开编辑
const handleOpenEdit = (row) => {
  editForm.value = { ...row }
  editDialogVisible.value = true
}

// 保存编辑
const handleSaveEdit = async () => {
  try {
    await updateWorkflowDefinition(editForm.value)
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    getList()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

// 新增流程
const handleAdd = async () => {
  const { value } = await ElMessageBox.prompt('请填写流程名称', '新增流程', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '流程名称',
    inputPattern: /.{1,}/,
    inputErrorMessage: '流程名称不能为空'
  })

  const params = {
    processName: value,
    description: ''
  }

  try {
    const res = await addWorkflowDefinition(params)
    ElMessage.success('新增成功，进入设计页面')
    router.push({
      path: '/workflow/designer',
      query: {
        id: res.data,
        name: value,
        action: 'edit'
      }
    })
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('新增失败')
      console.error(err)
    }
  }
}

// ========== 版本弹窗相关 ==========
const versionDialogVisible = ref(false)
const versionLoading = ref(false)
const versionList = ref([])
const selectedVersionRow = ref(null)
const creatingVersion = ref(false)
const currentDesignRow = ref(null)  // 当前操作的流程定义行

// ⭐ 新建版本二次确认弹窗
const confirmCreateVersionDialogVisible = ref(false)
const newVersionNameInput = ref('')
const newVersionNoPreview = computed(() => {
  // 从当前版本列表算 max(versionNo) + 1
  if (!versionList.value?.length) return 1
  const max = Math.max(...versionList.value.map(v => v.versionNo || 0))
  return max + 1
})

/** 行高亮 */
const tableRowClassName = ({ row }) => {
  return selectedVersionRow.value && selectedVersionRow.value.id === row.id ? 'version-row-active' : ''
}

/** 行点击 → 单选 */
const handleVersionRowChange = (row) => {
  selectedVersionRow.value = row
}

/** 打开设计器(从版本)
 *  mode: 'view'=只读查看（非最新版本）  'edit'=可编辑新建版本  'latest-edit'=最新版本→正常编辑模式
 */
const openDesignerFromVersion = (versionRow, mode) => {
  versionDialogVisible.value = false
  const baseQuery = {
    id: currentDesignRow.value.id,
    name: currentDesignRow.value.processName
  }
  if (mode === 'latest-edit') {
    // 需求5：最新版本 → 正常编辑模式，不传 versionId，action 默认为 'edit'
    // designer 的 loadData 会从主表加载 definition（status 正确），三个管理按钮全部可见
    router.push({ path: '/workflow/designer', query: baseQuery })
  } else if (mode === 'edit') {
    // 非最新版本 → 版本快照编辑模式（保留现有逻辑）
    router.push({
      path: '/workflow/designer',
      query: { ...baseQuery, versionId: versionRow.id, versionNo: versionRow.versionNo, action: 'version-edit' }
    })
  } else {
    // 非最新版本 → 只读查看（保留现有逻辑）
    router.push({
      path: '/workflow/designer',
      query: { ...baseQuery, versionId: versionRow.id, versionNo: versionRow.versionNo, action: 'version-view' }
    })
  }
}

/** 点击「新建版本」按钮 → 弹出二次确认弹窗 */
const handleCreateNewVersion = () => {
  if (!selectedVersionRow.value) return
  newVersionNameInput.value = ''  // 重置输入
  confirmCreateVersionDialogVisible.value = true
}

/** 二次确认弹窗 → 真正调后端创建 */
const confirmCreateVersion = async () => {
  creatingVersion.value = true
  try {
    // ⭐ API 层 createVersionFrom 签名：第二个参数是 versionName 字符串
    // 内部 data: { versionName } 会自动包一层 { versionName: "简化版01" }
    const res = await createVersionFrom(
      selectedVersionRow.value.id,
      newVersionNameInput.value?.trim() || null
    )
    const newVersionId = res.data
    ElMessage.success('新版本 v' + newVersionNoPreview.value + ' 已创建')
    confirmCreateVersionDialogVisible.value = false
    versionDialogVisible.value = false
    router.push({
      path: '/workflow/designer',
      query: {
        id: currentDesignRow.value.id,
        name: currentDesignRow.value.processName,
        versionId: newVersionId,
        versionNo: newVersionNoPreview.value,
        action: 'version-edit'
      }
    })
  } catch (e) {
    ElMessage.error('创建新版本失败')
  } finally {
    creatingVersion.value = false
  }
}

// 进入设计 → 改为弹出版本选择弹窗
const handleDesign = async (row) => {
  currentDesignRow.value = row
  selectedVersionRow.value = null
  versionLoading.value = true
  versionDialogVisible.value = true
  try {
    const res = await getVersionList(row.id)
    versionList.value = res.data || []
  } catch (e) {
    ElMessage.error('加载版本列表失败')
    versionDialogVisible.value = false
  } finally {
    versionLoading.value = false
  }
}

// 预览
const handlePreview = (row) => {
  router.push({
    path: '/workflow/designer',
    query: { 
      id: row.id, 
      name: row.processName,
      action: 'preview'
    }
  })
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该流程吗？', '提示')
    await deleteWorkflowDefinition(row.id)
    ElMessage.success('删除成功')
    getList()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('删除失败')
  }
}

// 查询按钮
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 分页回调：显式先赋值再请求，@current-change 时序早于 v-model update
const handleSizeChange = (size) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  getList()
}
const handleCurrentChange = (page) => {
  queryParams.pageNum = page
  getList()
}

// 重置
const resetQuery = () => {
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  queryParams.processName = ''
  queryParams.processKey = ''
  queryParams.status = ''
  getList()
}

onMounted(() => getList())
</script>

<style scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end }

/* 版本弹窗行高亮 */
:deep(.version-row-active) {
  background-color: #ecf5ff !important;
}
</style>