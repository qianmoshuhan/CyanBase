<template>
  <div class="workflow-start-container">
    <el-card shadow="hover">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- ========== Tab 1：发起流程 ========== -->
        <el-tab-pane label="发起流程" name="start">
          <div class="process-select">
            <el-form :inline="true" class="search-form">
              <el-form-item label="流程名称">
                <el-input v-model="processQuery.keyword" placeholder="请输入流程名称" clearable />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="fetchProcessList">查询</el-button>
                <el-button @click="resetQuery">重置</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="filteredProcessList" border stripe highlight-current-row style="margin-bottom:20px" empty-text="暂无已发布的流程">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="processName" label="流程名称" min-width="200" />
              <el-table-column prop="processKey" label="流程标识" min-width="200" />
              <el-table-column prop="category" label="分类" min-width="120" />
              <el-table-column prop="version" label="版本" width="70" />
              <el-table-column prop="status" label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">{{ row.status === 1 ? '已发布' : '未发布' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click.stop="selectProcess(row)">选择</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div v-if="selectedProcess" class="form-content">
            <el-divider content-position="left">发起流程：{{ selectedProcess.processName }}</el-divider>
            <el-descriptions :column="2" border style="margin-bottom:16px;">
              <el-descriptions-item label="流程名称">{{ selectedProcess.processName }}</el-descriptions-item>
              <el-descriptions-item label="流程标识">{{ selectedProcess.processKey }}</el-descriptions-item>
              <el-descriptions-item label="业务类型">{{ selectedProcess.businessType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="关联表单">
                <el-tag v-if="formLoading" size="small" type="info">加载中...</el-tag>
                <el-tag v-else-if="formRules.length" size="small" type="success">{{ formDefName }} ({{ formRules.length }}个字段)</el-tag>
                <el-tag v-else size="small" type="warning">未关联表单</el-tag>
              </el-descriptions-item>
            </el-descriptions>

            <!-- ⭐ 关联表单渲染区：form-create 动态渲染 layoutConfig -->
            <div v-if="formLoading" style="text-align:center;padding:30px;">
              <el-icon class="is-loading" :size="28"><Loading /></el-icon>
              <span style="margin-left:8px;color:#909399;">加载关联表单中...</span>
            </div>
            <div v-else-if="formRules.length" style="padding:16px;background:#fafbfc;border-radius:6px;border:1px solid #ebeef5;">
              <form-create v-model:api="formApi" :rule="formRules" />
            </div>
            <div v-else style="padding:12px;color:#909399;font-size:13px;background:#f4f4f5;border-radius:4px;">
              ℹ️ 该流程未关联表单，直接发起即可
            </div>

            <el-form label-width="100px" style="margin-top:16px;">
              <el-form-item label="备注">
                <el-input v-model="remark" type="textarea" :rows="3" placeholder="请输入备注（选填）" />
              </el-form-item>
            </el-form>
            <div class="submit-btn" style="margin-top:20px;text-align:right;">
              <el-button @click="handleCancelSelect">取消选择</el-button>
              <el-button type="primary" :loading="submitting" @click="handleSubmit">发起流程</el-button>
            </div>
          </div>
        </el-tab-pane>

        <!-- ========== Tab 2：我发起的流程 ========== -->
        <el-tab-pane label="我发起的流程" name="my">
          <el-form :inline="true">
            <el-form-item label="流程名称">
              <el-input v-model="myQuery.keyword" placeholder="输入流程名称" clearable style="width:180px"
                        @keyup.enter="fetchMyInstanceList" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="myQuery.status" placeholder="全部" clearable style="width:140px">
                <el-option label="运行中" value="running" />
                <el-option label="已完成" value="completed" />
                <el-option label="已驳回" value="rejected" />
                <el-option label="已撤销" value="cancelled" />
              </el-select>
            </el-form-item>
            <el-form-item label="发起时间">
              <el-date-picker v-model="myQuery.timeRange" type="daterange" range-separator="至"
                              start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width:280px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="fetchMyInstanceList">查询</el-button>
              <el-button @click="resetMyQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="myInstanceList" border stripe>
            <el-table-column type="index" label="序号" width="70"
                             :index="(i) => (myQuery.pageNum - 1) * myQuery.pageSize + i + 1" />
            <el-table-column prop="processName" label="流程名称" min-width="180" />
            <el-table-column prop="itemName" label="业务事项" min-width="160" show-overflow-tooltip />
            <el-table-column prop="currentNodeName" label="当前节点" min-width="140" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="instanceStatusColor(row.status)" size="small">{{ instanceStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" label="发起时间" min-width="160" />
            <el-table-column prop="endTime" label="结束时间" min-width="160" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTrailDialog(row)">查看轨迹</el-button>
                <el-button v-if="row.status==='running'" link type="danger" @click="handleCancel(row)">取消</el-button>
                <el-button v-if="row.status==='cancelled' || row.status==='rejected'"
                           link type="danger" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination style="margin-top:16px;justify-content:flex-end;display:flex;"
            background layout="total, sizes, prev, pager, next"
            :total="myTotal" v-model:page-num="myQuery.pageNum" v-model:page-size="myQuery.pageSize"
            @current-change="handlePageChange" @size-change="handleSizeChange" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- ========== 轨迹详情 Dialog ========== -->
    <el-dialog v-model="trailVisible" :width="isMobile?'92%':'720px'" title="流程轨迹详情" destroy-on-close>
      <div v-if="trailLoading" style="text-align:center;padding:40px;"><el-icon class="is-loading" :size="32"><Loading /></el-icon></div>
      <div v-else-if="trailInstance.id">
        <!-- 基本信息 -->
        <el-descriptions :column="2" border size="small" style="margin-bottom:12px;">
          <el-descriptions-item label="流程名称">{{ trailInstance.processName }}</el-descriptions-item>
          <el-descriptions-item label="实例ID">{{ trailInstance.id }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ trailInstance.initiatorName }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ trailInstance.startTime }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ trailInstance.currentNodeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="instanceStatusColor(trailInstance.status)" size="small">{{ instanceStatusText(trailInstance.status) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="结束时间" :span="2">{{ trailInstance.endTime || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 图例 -->
        <div class="trail-legend">
          <span><i class="dot dot-done"></i>已完成</span>
          <span><i class="dot dot-current"></i>当前</span>
          <span><i class="dot dot-reject"></i>已驳回</span>
          <span><i class="dot dot-skipped"></i>已跳过</span>
          <span><i class="dot dot-pending"></i>待审批</span>
        </div>

        <!-- ① 完整节点流转链（后端 nodeStatusList） -->
        <div class="trail-title">① 节点流转（完整链路）</div>
        <el-timeline v-if="trailNodeStatusList.length">
          <el-timeline-item
            v-for="(n, i) in trailNodeStatusList"
            :key="n.nodeId + '-' + i"
            :color="nodeStatusColor(n.status)"
            :class="{ 'node-pulse': n.status === 'CURRENT' }"
          >
            <div class="node-row">
              <strong>{{ n.nodeName }}</strong>
              <el-tag v-if="n.nodeType === 'start'" type="primary" size="small" style="margin-left:6px;">开始</el-tag>
              <el-tag v-else-if="n.nodeType === 'end'" type="success" size="small" style="margin-left:6px;">结束</el-tag>
              <el-tag v-else-if="n.status === 'CURRENT'" type="warning" size="small" effect="light" style="margin-left:6px;">当前节点</el-tag>
              <el-tag v-else-if="n.status === 'PENDING'" type="info" size="small" effect="plain" style="margin-left:6px;">（待审批）</el-tag>
              <el-tag v-else-if="n.status === 'REJECTED'" type="danger" size="small" effect="light" style="margin-left:6px;">已驳回</el-tag>
              <el-tag v-else-if="n.status === 'SKIPPED'" type="info" size="small" effect="plain" style="margin-left:6px; text-decoration:line-through;">已跳过</el-tag>
              <el-tag v-else-if="n.nodeType === 'userTask'" type="success" size="small" effect="light" style="margin-left:6px;">已完成</el-tag>
            </div>
          </el-timeline-item>
        </el-timeline>
        <div v-else class="text-muted">暂无节点数据</div>

        <!-- ② 审批明细（折叠，按节点分组，含 start/end） -->
        <div v-if="groupedRecords.length" class="trail-title" style="margin-top:16px;">
          ② 审批明细（共 {{ groupedRecords.length }} 个节点）
        </div>
        <el-collapse v-if="groupedRecords.length" accordion>
          <el-collapse-item title="▼ 点击展开审批明细" :name="1">
            <!-- 按节点分组，全局按 sortOrder 排序 -->
            <div v-for="(group, gIdx) in groupedRecords" :key="'g-' + gIdx"
                 class="trail-node-group"
                 :class="{
                   'group-start': group.nodeType === 'start',
                   'group-end': group.nodeType === 'end',
                   'group-status-completed': group.status === 'COMPLETED',
                   'group-status-rejected': group.status === 'REJECTED',
                   'group-status-pending': group.status === 'PENDING',
                   'group-current': group.isCurrent,
                 }">
              <div class="trail-node-header">
                <strong>
                  <span v-if="group.nodeType === 'start'" class="node-icon">🚀</span>
                  <span v-else-if="group.nodeType === 'end'" class="node-icon">🏁</span>
                  <span v-else class="node-icon">📝</span>
                  {{ group.nodeName }}
                </strong>
                <!-- 节点状态标签（按优先级从高到低） -->
                <!-- start/end 用类型标签 -->
                <el-tag v-if="group.nodeType === 'start'" type="primary" size="small" style="margin-left:6px;">开始节点</el-tag>
                <el-tag v-else-if="group.nodeType === 'end'" :type="group.status === 'COMPLETED' ? 'success' : 'info'" size="small" style="margin-left:6px;">
                  {{ group.status === 'COMPLETED' ? '结束节点' : '等待完结' }}
                </el-tag>
                <!-- userTask 按 nodeStatusList.status 判断 -->
                <el-tag v-else-if="group.status === 'CURRENT'" type="warning" size="small" effect="light" style="margin-left:6px;">当前节点</el-tag>
                <el-tag v-else-if="group.status === 'COMPLETED'" type="success" size="small" effect="light" style="margin-left:6px;">已完成</el-tag>
                <el-tag v-else-if="group.status === 'REJECTED'" type="danger" size="small" effect="light" style="margin-left:6px;">已驳回</el-tag>
                <el-tag v-else-if="group.status === 'SKIPPED'" type="info" size="small" effect="plain" style="margin-left:6px; text-decoration:line-through;">已跳过</el-tag>
                <el-tag v-else-if="group.status === 'PENDING'" type="info" size="small" effect="plain" style="margin-left:6px;">（待审批）</el-tag>
                <el-tag v-else type="info" size="small" style="margin-left:6px;">未知</el-tag>
                <!-- 附加标签 -->
                <el-tag v-if="group.nodeType === 'userTask'" size="small" effect="plain" style="margin-left:4px;">
                  {{ group.signTypeLabel }}
                </el-tag>
                <span v-if="group.nodeType === 'userTask'" class="trail-node-count">{{ group.doneCount }}/{{ group.totalCount }} 已处理</span>
              </div>
              <el-timeline>
                <el-timeline-item
                  v-for="(r,i) in group.records"
                  :key="'r-' + gIdx + '-' + i"
                  :timestamp="r.approvalTime"
                  :color="getNodeTimelineColor(group, r)"
                >
                  <div class="trail-approver-row" :class="{ 'todo-record': !r.action, 'virtual-record': r.isVirtual }">
                    <strong>{{ r.approverName || ('用户#' + r.approverId) }}</strong>
                    <span class="trail-action" :class="'act-' + (r.action || 'todo')">{{ getRecordActionText(group, r) }}</span>
                  </div>
                  <div v-if="r.deadline && !r.action" class="trail-deadline">截止 {{ r.deadline.slice(0,16) }}</div>
                  <div v-if="r.comment" class="trail-comment">{{ r.isVirtual ? r.comment : ('意见：' + r.comment) }}</div>
                </el-timeline-item>
              </el-timeline>
            </div>
          </el-collapse-item>
        </el-collapse>
        <div v-else class="text-muted" style="font-size:12px;">暂无审批记录（流程刚发起）</div>
      </div>
      <template #footer>
        <el-button @click="trailVisible=false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getPublishedWorkflowList, startWorkflow, getMyInitiatedList, getInstanceTrail, getInstanceById, cancelWorkflow, deleteInstance } from '@/api/wfDefinition'
import { getFormDefinitionById } from '@/api/form'
import { parseLayoutConfig } from '@/plugins/formCreate'

// ========== 响应式：移动端检测 ==========
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

// ========== Tab 切换 ==========
const activeTab = ref('start')
const onTabChange = (name) => { if (name === 'my') fetchMyInstanceList() }

// ========== Tab 1：发起流程 ==========
const processQuery = reactive({ keyword: '' })
const processList = ref([])
const selectedProcess = ref(null)
const remark = ref('')
// ⭐ 关联表单相关
const formApi = ref(null)
const formRules = ref([])
const formLoading = ref(false)
const formDefName = ref('')
const submitting = ref(false)
const filteredProcessList = computed(() => {
  const kw = processQuery.keyword?.trim().toLowerCase()
  if (!kw) return processList.value
  return processList.value.filter(p =>
    (p.processName || '').toLowerCase().includes(kw) || (p.processKey || '').toLowerCase().includes(kw))
})
const fetchProcessList = async () => {
  try {
    const res = await getPublishedWorkflowList()
    processList.value = Array.isArray(res.data) ? res.data : []
  } catch (e) { ElMessage.error('获取流程定义失败') }
}
const resetQuery = () => { processQuery.keyword = '' }
const selectProcess = async (row) => {
  selectedProcess.value = row
  remark.value = ''
  formRules.value = []
  formApi.value = null

  // 加载关联表单
  if (row.formDefinitionId) {
    formLoading.value = true
    try {
      const res = await getFormDefinitionById(row.formDefinitionId)
      const def = res.data || res
      formDefName.value = def.formName || ''
      formRules.value = parseLayoutConfig(def.layoutConfig)
    } catch (e) {
      console.error('加载关联表单失败:', e)
      ElMessage.warning('关联表单加载失败，将以无表单模式发起')
    } finally {
      formLoading.value = false
    }
  }
}

const handleCancelSelect = () => {
  selectedProcess.value = null
  remark.value = ''
  formRules.value = []
  formApi.value = null
}

const handleSubmit = async () => {
  if (!selectedProcess.value) { ElMessage.warning('请先选择要发起的流程'); return }

  // form-create 校验
  if (formRules.value.length && formApi.value) {
    try {
      await formApi.value.validate()
    } catch (e) {
      ElMessage.warning('请完善表单必填项')
      return
    }
  }

  try {
    await ElMessageBox.confirm(`确定要发起【${selectedProcess.value.processName}】？`, '发起流程',
      { confirmButtonText: '确定发起', cancelButtonText: '取消', type: 'info' })

    // ⭐ 组装 variables：form-create 表单数据 + 备注
    let variables = {}
    if (formApi.value && formRules.value.length) {
      // form-create v3: form 是属性，formData() 是方法，form() 是 v2 旧API
      variables = (formApi.value.formData && formApi.value.formData()) || formApi.value.form || {}
    }
    if (remark.value.trim()) {
      variables._remark = remark.value.trim()
    }

    submitting.value = true
    await startWorkflow({
      definitionId: selectedProcess.value.id,
      businessKey: '',
      variables: JSON.stringify(variables)
    })
    ElMessage.success('流程发起成功')
    handleCancelSelect()
    fetchProcessList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('发起失败：' + (e?.response?.data?.msg || e.message || e))
  } finally {
    submitting.value = false
  }
}

// ========== Tab 2：我发起的流程 ==========
const myQuery = reactive({ pageNum: 1, pageSize: 10, status: '', keyword: '', timeRange: [] })
const myInstanceList = ref([])
const myTotal = ref(0)
const fetchMyInstanceList = async () => {
  try {
    const params = { pageNum: myQuery.pageNum, pageSize: myQuery.pageSize }
    if (myQuery.status) params.status = myQuery.status
    if (myQuery.keyword?.trim()) params.keyword = myQuery.keyword.trim()
    if (myQuery.timeRange?.length === 2) {
      params.startTime = myQuery.timeRange[0] + ' 00:00:00'
      params.endTime   = myQuery.timeRange[1] + ' 23:59:59'
    }
    const res = await getMyInitiatedList(params)
    myInstanceList.value = res.data?.records || []
    myTotal.value = res.data?.total || 0
  } catch (e) { ElMessage.error('获取流程列表失败') }
}
// ⭐ 显式分页回调——先更新 pageNum 再请求，确保时序正确
const handlePageChange = (page) => {
  myQuery.pageNum = page
  fetchMyInstanceList()
}
const handleSizeChange = (size) => {
  myQuery.pageSize = size
  myQuery.pageNum = 1
  fetchMyInstanceList()
}
const resetMyQuery = () => {
  myQuery.keyword = ''; myQuery.status = ''; myQuery.timeRange = []
  myQuery.pageNum = 1; fetchMyInstanceList()
}

// ========== 轨迹 Dialog ==========
const trailVisible = ref(false)
const trailLoading = ref(false)
const trailInstance = ref({})
const trailNodeStatusList = ref([]) // 完整节点状态序列（后端 nodeStatusList）
const trailRecords = ref([])         // 审批明细（approveRecords）
const openTrailDialog = async (row) => {
  trailVisible.value = true
  trailLoading.value = true
  trailInstance.value = {}
  trailNodeStatusList.value = []
  trailRecords.value = []
  try {
    const [instRes, trailRes] = await Promise.all([
      getInstanceById(row.id),
      getInstanceTrail(row.id)
    ])
    trailInstance.value = instRes.data || {}
    trailNodeStatusList.value = trailRes.data?.nodeStatusList || []
    trailRecords.value = trailRes.data?.approveRecords || []
  } catch (e) {
    ElMessage.error('加载轨迹失败：' + (e?.response?.data?.msg || e.message))
  } finally {
    trailLoading.value = false
  }
}

// ========== 取消流程 ==========
const handleCancel = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要撤销流程【${row.processName}】吗？`, '撤销',
      { confirmButtonText: '确定撤销', cancelButtonText: '取消', type: 'warning' })
    await cancelWorkflow(row.id)
    ElMessage.success('已撤销')
    fetchMyInstanceList()
  } catch (e) { if (e !== 'cancel') ElMessage.error('撤销失败') }
}

// ========== ⭐ 删除流程（仅 cancelled/rejected 可删）==========
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除流程【${row.processName}】吗？<br/>` +
      `<span style="color:#f53f3f;font-size:12px;">此操作不可恢复！将同时删除审批记录和节点运行时数据</span>`,
      '删除流程',
      { confirmButtonText: '确定删除', cancelButtonText: '再想想', type: 'error', dangerouslyUseHTMLString: true }
    )
    await deleteInstance(row.id)
    ElMessage.success('已删除')
    fetchMyInstanceList()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.msg || '删除失败')
    }
  }
}

// ========== 辅助函数 ==========
const instanceStatusText = (s) => ({ running: '运行中', completed: '已完成', rejected: '已驳回', cancelled: '已撤销' }[s] || s)
const instanceStatusColor = (s) => ({ running: 'warning', completed: 'success', rejected: 'danger', cancelled: 'info' }[s] || '')
const trailColor = (action) => {
  if (!action) return 'blue'
  if (action === 'approve') return 'green'
  if (action === 'reject') return 'red'
  return 'gray'
}
const trailActionText = (r) => {
  if (!r.action) return (r.signType === 'ADD' ? '待加签人审批' : '待审批')
  const map = { approve: '通过', reject: '驳回', transfer: '转办', skip: '跳过', cancel: '撤回', addSign: '加签中' }
  return map[r.action] || r.action
}
// 节点流转专用：COMPLETED=绿 CURRENT=橙 REJECTED=红 PENDING=灰
const nodeStatusColor = (s) => ({
  COMPLETED: '#1677ff', CURRENT: '#ff7d00', REJECTED: '#f53f3f', SKIPPED: '#909399', PENDING: '#c0c4cc'
}[s] || '#c0c4cc')

/**
 * 审批明细 timeline 节点颜色
 * start 节点 → 永远绿（发起人已提交）
 * end 节点 → 按 node status 着色
 * userTask → 优先按 node status，否则按 record action
 */
const getNodeTimelineColor = (group, record) => {
  if (group.nodeType === 'start') return '#00b42a'           // 绿
  if (group.nodeType === 'end') return nodeStatusColor(group.status)
  // userTask
  if (group.status) return nodeStatusColor(group.status)
  return trailColor(record?.action)
}

/**
 * 审批明细里每条记录的操作文案
 * 虚拟记录（start/end）给更友好的文字
 */
const getRecordActionText = (group, r) => {
  if (r.isVirtual) {
    if (group.nodeType === 'start') return '发起人提交'
    if (group.nodeType === 'end') {
      if (group.status === 'COMPLETED') return '流程完结'
      if (group.status === 'REJECTED') return '流程被驳回'
      return '等待流程完结'
    }
  }
  return trailActionText(r)
}

/**
 * 审批明细按节点分组（含 start/end 虚拟节点）
 * 1. 先把真实 approveRecords（userTask）按 nodeId 分组
 * 2. 再从 trailNodeStatusList 补 start/end（构造虚拟 approve 记录）
 * 3. 所有分组按 sortOrder 排序 → start → userTask(s) → end
 */
const groupedRecords = computed(() => {
  const records = trailRecords.value || []
  const nodeStatusList = trailNodeStatusList.value || []
  const instance = trailInstance.value
  const instanceStatus = instance?.status  // running / completed / rejected / cancelled
  const groups = new Map()

  // ====== step 1：真实 userTask 审批记录分组 ======
  for (const r of records) {
    const key = r.nodeId ?? ('n-' + r.nodeName)
    if (!groups.has(key)) {
      const ns = nodeStatusList.find(n => n.nodeId === r.nodeId)
      // ⭐ 修复：isCurrent 必须同时满足 3 个条件
      //   1) 实例还在运行中（status=running）
      //   2) nodeStatusList 里这个节点被标记为 CURRENT
      //   3) 有 pending 的待办（action 全是 null 或部分 null）
      const nodeStatus = ns?.status
      const isRunning = instanceStatus === 'running'
      groups.set(key, {
        nodeId: r.nodeId,
        nodeName: r.nodeName,
        nodeType: ns?.nodeType || 'userTask',
        sortOrder: ns?.sortOrder ?? 999,
        status: nodeStatus,
        isCurrent: isRunning && nodeStatus === 'CURRENT',
        records: [],
      })
    }
    groups.get(key).records.push(r)
  }

  // ====== step 2：补 start / end 虚拟节点 ======
  for (const ns of nodeStatusList) {
    if (groups.has(ns.nodeId)) continue  // 已被 approveRecords 覆盖
    if (ns.nodeType !== 'start' && ns.nodeType !== 'end') continue

    // 构造虚拟 approve 记录（让 start/end 和 userTask 用同样的 Timeline 渲染）
    const virt = buildVirtualApproveRecord(ns, instance)
    groups.set(ns.nodeId, {
      nodeId: ns.nodeId,
      nodeName: ns.nodeName,
      nodeType: ns.nodeType,
      sortOrder: ns.sortOrder ?? 999,
      status: ns.status,
      isCurrent: false,
      records: [virt],
    })
  }

  // ====== step 3：每组内排序 + 元信息 ======
  for (const g of groups.values()) {
    g.records.sort((a, b) => {
      if (!a.action && b.action) return -1
      if (a.action && !b.action) return 1
      return (a.approvalTime || '').localeCompare(b.approvalTime || '')
    })
    g.totalCount = g.records.length
    g.doneCount = g.records.filter(r => r.action).length
    if (g.nodeType === 'start') {
      g.signTypeLabel = '发起人提交'
    } else if (g.nodeType === 'end') {
      g.signTypeLabel = g.status === 'COMPLETED' ? '流程完结' : '等待完结'
    } else {
      const hasPending = g.records.some(r => !r.action)
      g.signTypeLabel = g.totalCount > 1 && hasPending ? '会签' : (g.totalCount > 1 ? '多人已处理' : '单人审批')
    }
  }

  // ====== step 4：按 sortOrder 全局排序 ======
  return Array.from(groups.values()).sort((a, b) => (a.sortOrder ?? 999) - (b.sortOrder ?? 999))
})

/**
 * 构造 start / end 节点的虚拟 approve 记录
 * 让它们能复用 timeline 的渲染逻辑（action/approverName/comment/approvalTime）
 */
function buildVirtualApproveRecord(ns, instance) {
  if (ns.nodeType === 'start') {
    return {
      nodeId: ns.nodeId, nodeName: ns.nodeName,
      approverId: instance.initiatorId,
      approverName: instance.initiatorName || ('用户#' + instance.initiatorId),
      action: 'approve',
      signType: 'SYSTEM',
      comment: '发起人提交',
      approvalTime: instance.startTime,
      isVirtual: true,
    }
  }
  // end 节点 — 谁发起谁闭环
  const submitterName = instance.initiatorName || ('用户#' + instance.initiatorId)
  if (ns.status === 'COMPLETED') {
    return {
      nodeId: ns.nodeId, nodeName: ns.nodeName,
      approverId: instance.initiatorId, approverName: submitterName,
      action: 'approve', signType: 'SYSTEM',
      comment: '流程完结，提交人确认',
      approvalTime: instance.endTime,
      isVirtual: true,
    }
  }
  if (ns.status === 'REJECTED') {
    return {
      nodeId: ns.nodeId, nodeName: ns.nodeName,
      approverId: instance.initiatorId, approverName: submitterName,
      action: 'reject', signType: 'SYSTEM',
      comment: '流程被驳回，已退回提交人',
      approvalTime: instance.endTime,
      isVirtual: true,
    }
  }
  // PENDING：流程还没到 end
  return {
    nodeId: ns.nodeId, nodeName: ns.nodeName,
    approverId: instance.initiatorId, approverName: submitterName,
    action: null, signType: 'SYSTEM',
    comment: '等待流程走完',
    approvalTime: '',
    isVirtual: true,
  }
}

onMounted(() => { fetchProcessList(); fetchMyInstanceList() })
</script>

<style scoped>
.workflow-start-container { width: 100%; }
.search-form { margin-bottom: 16px; }
.process-select { margin-bottom: 24px; }
.form-content { border-top: 1px solid #eee; padding-top: 20px; }
.submit-btn { margin-top: 20px; text-align: right; }
.trail-title { margin: 14px 0 8px; color: #303133; font-size: 14px; font-weight: 500; }
.trail-action { font-size: 12px; color: #666; }
.trail-comment { font-size: 12px; color: #999; margin-top: 2px; }
.text-muted { color: #909399; }

/* 图例 */
.trail-legend { display: flex; gap: 14px; margin-bottom: 4px; font-size: 12px; color: #606266; }
.trail-legend i.dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 3px; vertical-align: middle; }
.dot-done { background: #1677ff; }
.dot-current { background: #ff7d00; }
.dot-reject { background: #f53f3f; }
.dot-pending { background: #c0c4cc; }

/* 节点行 */
.node-row { display: flex; align-items: center; flex-wrap: wrap; }

/* ====== ① 节点流转：三层波纹脉冲动画 ======
   利用 ::before / ::after 伪元素 + 本体，三层同时向外扩散
   相位错开 → 真正"一圈一圈向外散开"的感觉
*/
.node-pulse :deep(.el-timeline-item__node) {
  position: relative;
  /* 本体：静止的实心橙色圆点 */
  box-shadow: 0 0 0 0 transparent;
}
/* 本体波纹（最内圈，最快）*/
.node-pulse :deep(.el-timeline-item__node) {
  animation: ripple-main 1.4s ease-out infinite;
}
/* ::before 波纹（中圈）*/
.node-pulse :deep(.el-timeline-item__node)::before {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 2px solid #ff7d00;
  animation: ripple-ring 2.4s ease-out infinite;
}
/* ::after 波纹（最外圈，最慢，相位滞后）*/
.node-pulse :deep(.el-timeline-item__node)::after {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 2px solid #e6a23c;
  animation: ripple-ring 2.4s ease-out infinite;
  animation-delay: -0.8s;  /* ⭐ 错开 0.8s，产生相位差 → 一圈圈散开 */
}

/* 本体：从 0px 扩散到 24px */
@keyframes ripple-main {
  0%   { box-shadow: 0 0 0 0  rgba(255, 125, 0, 0.7); }
  70%  { box-shadow: 0 0 0 16px rgba(255, 125, 0, 0); }
  100% { box-shadow: 0 0 0 0  rgba(255, 125, 0, 0); }
}
/* 伪元素环：border 颜色淡出 */
@keyframes ripple-ring {
  0%   { transform: scale(1);   opacity: 0.9; }
  100% { transform: scale(2.2); opacity: 0; }
}

/* 审批明细分组 */
.trail-node-group {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px 6px;
  margin-bottom: 10px;
  background: #fafbfc;
}
/* ====== 节点状态类（和 nodeStatusColor() 严格对齐）====== */
/* ====== ② 审批明细：当前节点边框三层波纹散开
   ⭐ 完全对齐节点流转（上面①）的三件套模式：
     本体    → box-shadow 扩散   1.4s ease-out
     ::before → scale+opacity     2.4s ease-out（立即）
     ::after  → scale+opacity     2.4s ease-out（delay -0.8s）
   border 永远固定 1px → 零抖动
*/
.trail-node-group.group-current,
.trail-node-group.group-status-current {
  border: 1px solid #ff7d00 !important;
  background: #fff7e8 !important;
  position: relative;
  overflow: visible;
  z-index: 1;
  /* ⭐ 本体波纹（最内圈，最快）→ 对齐节点流转的 ripple-main */
  animation: border-ripple-main 1.4s ease-out infinite;
}
/* ::before 边框波纹（中圈）→ 对齐节点流转 ::before */
.trail-node-group.group-current::before,
.trail-node-group.group-status-current::before {
  content: '';
  position: absolute;
  inset: -6px;
  border-radius: 8px;
  border: 2px solid #ff7d00;
  pointer-events: none;
  z-index: -1;
  animation: border-ripple-ring 2.4s ease-out infinite;
}
/* ::after 边框波纹（最外圈，相位滞后 0.8s）→ 对齐节点流转 ::after */
.trail-node-group.group-current::after,
.trail-node-group.group-status-current::after {
  content: '';
  position: absolute;
  inset: -6px;
  border-radius: 8px;
  border: 2px solid #ff7d00;
  pointer-events: none;
  z-index: -1;
  animation: border-ripple-ring 2.4s ease-out infinite;
  animation-delay: -0.8s;
}
/* 本体：box-shadow 从 0 扩散到 20px */
@keyframes border-ripple-main {
  0%   { box-shadow: 0 0 0 0  rgba(255, 125, 0, 0.55); }
  70%  { box-shadow: 0 0 0 14px rgba(255, 125, 0, 0); }
  100% { box-shadow: 0 0 0 0  rgba(255, 125, 0, 0); }
}
/* 伪元素边框环：scale + opacity 渐隐 → 矩形版 ripple-ring */
@keyframes border-ripple-ring {
  0%   { transform: scale(1);    opacity: 0.9; }
  100% { transform: scale(1.08); opacity: 0;   }
}
/* 已完成：绿色边框 + 浅绿背景 */
.trail-node-group.group-status-completed {
  border-color: #00b42a !important;
  background: #e8f8eb !important;
}
/* 已驳回：红色边框 + 浅红背景 */
.trail-node-group.group-status-rejected {
  border-color: #f53f3f !important;
  background: #ffece8 !important;
}
/* 待审批：灰色边框 + 浅灰背景 */
.trail-node-group.group-status-pending {
  border-color: #c0c4cc !important;
  background: #f4f4f5 !important;
  opacity: 0.75;
}
/* 开始节点：蓝色类型标签优先 */
.trail-node-group.group-start {
  border-color: #1677ff !important;
  background: #e6f4ff !important;
}
/* 结束节点：已完成→绿，未完成→灰 */
.trail-node-group.group-end.group-status-completed {
  border-color: #67c23a !important;
  background: #f0f9eb !important;
}
.trail-node-group.group-end:not(.group-status-completed) {
  border-color: #c0c4cc !important;
  background: #f4f4f5 !important;
  opacity: 0.75;
}
.trail-node-header .node-icon {
  margin-right: 4px;
  font-size: 14px;
}
/* 虚拟记录（start/end）斜体 */
.trail-approver-row.virtual-record strong {
  font-style: italic;
  color: #606266;
}
.trail-node-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  font-size: 13px;
  margin-bottom: 6px;
  padding-bottom: 6px;
  border-bottom: 1px dashed #dcdfe6;
}
.trail-node-header strong {
  font-size: 14px;
  color: #303133;
}
.trail-node-count {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}

/* 审批人行 */
.trail-approver-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.trail-approver-row.todo-record {
  padding: 3px 6px;
  border-radius: 4px;
  background: #e6f4ff;
}
.trail-action {
  font-size: 12px;
  color: #606266;
}
.trail-action.act-approve { color: #00b42a; font-weight: 500; }
.trail-action.act-reject { color: #f53f3f; font-weight: 500; }
.trail-action.act-transfer { color: #909399; }
.trail-action.act-skip   { color: #c0c4cc; }
.trail-action.act-todo   { color: #1677ff; font-weight: 500; }
.trail-deadline {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

@media (max-width: 768px) {
  .search-form :deep(.el-form-item) { margin-right: 0; margin-bottom: 8px; width: 100%; }
  .search-form :deep(.el-form-item__content) { width: 100%; }
  .submit-btn { text-align: center; }
}
</style>