<template>
  <div class="workflow-approve-container">
    <!-- 顶部操作栏 -->
    <div class="top-bar">
      <el-button @click="goBack"><el-icon><ArrowLeft /></el-icon>返回待办</el-button>
      <div class="top-bar-right" v-if="instance.status === 'running'">
        <el-button @click="addSignerVisible = true"><el-icon><User /></el-icon>加签</el-button>
        <el-button @click="transferVisible = true"><el-icon><Share /></el-icon>转办</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="approve-row">
      <!-- 左：BPMN 流程图（带高亮） -->
      <el-col :span="14" :xs="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span><strong>审批进度</strong> - {{ instance.processName }}</span>
              <div class="legend">
                <span class="legend-item"><i class="dot dot-completed"></i>已完成</span>
                <span class="legend-item"><i class="dot dot-current"></i>当前待办</span>
                <span class="legend-item"><i class="dot dot-rejected"></i>已驳回</span>
              </div>
            </div>
          </template>
          <div id="bpmn-canvas-approve" class="approve-bpmn-canvas"></div>
        </el-card>

        <!-- 审批轨迹 Timeline -->
        <el-card shadow="hover" style="margin-top: 16px;">
          <template #header><strong>审批轨迹</strong></template>
          <el-timeline>
            <el-timeline-item
              v-for="(r, i) in trail.approveRecords"
              :key="i"
              :timestamp="r.approvalTime"
              :color="trailColor(r.action)"
            >
              <div><strong>{{ r.nodeName }}</strong> - {{ r.approverName || ('用户#' + r.approverId) }}</div>
              <div class="trail-action">{{ trailActionText(r) }}</div>
              <div v-if="r.comment" class="trail-comment">意见：{{ r.comment }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>

      <!-- 右：流程信息 + 审批操作 -->
      <el-col :span="10" :xs="24">
        <el-card shadow="hover">
          <template #header><strong>流程信息</strong></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="流程名称">{{ instance.processName }}</el-descriptions-item>
            <el-descriptions-item label="流程实例ID">{{ instance.id }}</el-descriptions-item>
            <el-descriptions-item label="发起人">{{ instance.initiatorName }}</el-descriptions-item>
            <el-descriptions-item label="发起时间">{{ instance.startTime }}</el-descriptions-item>
            <el-descriptions-item label="当前节点">{{ instance.currentNodeName }}</el-descriptions-item>
            <el-descriptions-item label="审批截止">
              <span v-if="instance.currentNodeDeadline" :class="isOverdue ? 'text-danger' : ''">
                {{ instance.currentNodeDeadline }}
                <el-tag v-if="isOverdue" type="danger" size="small" style="margin-left:6px">已超时</el-tag>
              </span>
              <span v-else class="text-muted">未设置</span>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusColor">{{ statusText }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- ⭐ 审批表单（只有 running 状态才显示） -->
        <el-card v-if="instance.status === 'running'" shadow="hover" style="margin-top: 16px;">
          <template #header><strong>审批操作</strong></template>
          <el-form :model="approveForm" label-width="80px">
            <el-form-item label="审批意见">
              <el-input v-model="approveForm.comment" type="textarea" :rows="4" placeholder="选填" />
            </el-form-item>
          </el-form>
          <div style="display:flex; gap:10px; justify-content:flex-end;">
            <el-button type="danger" @click="handleSubmit('reject')">驳回</el-button>
            <el-button type="success" @click="handleSubmit('approve')">通过</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 加签弹框 -->
    <el-dialog v-model="addSignerVisible" title="动态加签" :width="isMobile ? '92%' : '420px'">
      <el-form label-width="80px">
        <el-form-item label="加签人">
          <el-select v-model="addSignerForm.addUserIds" multiple filterable collapse-tags
                     :remote="true" :remote-method="(kw) => searchUser(kw, 'add')"
                     style="width:100%" placeholder="搜索并选择用户">
            <el-option v-for="u in addSignerForm._userOpts" :key="u.id" :label="u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addSignerForm.comment" placeholder="为什么要加签" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addSignerVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAddSigner" :disabled="!addSignerForm.addUserIds?.length">确定</el-button>
      </template>
    </el-dialog>

    <!-- 转办弹框 -->
    <el-dialog v-model="transferVisible" title="转办给他人" :width="isMobile ? '92%' : '420px'">
      <el-form label-width="80px">
        <el-form-item label="转给">
          <el-select v-model="transferForm.userId" filterable :remote="true"
                     :remote-method="(kw) => searchUser(kw, 'trans')"
                     style="width:100%" placeholder="搜索并选择用户">
            <el-option v-for="u in transferForm._userOpts" :key="u.id" :label="u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="transferForm.comment" placeholder="转办原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmTransfer" :disabled="!transferForm.userId">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, markRaw, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, User, Share } from '@element-plus/icons-vue'
import { approveWorkflow, getInstanceById, getInstanceTrail, getBpmnXml, addSigner, transferApprover } from '@/api/wfDefinition'
import request from '@/utils/request'
import BpmnViewer from 'bpmn-js/lib/Viewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'

const route = useRoute()
const router = useRouter()

// ⭐ 响应式检测
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

const instanceId = Number(route.query.id)

const instance = ref({})
const trail = ref({ completedNodeKeys: [], currentNodeKeys: [], rejectedNodeKeys: [], approveRecords: [] })
const approveForm = reactive({ comment: '' })

let bpmnViewer = null

// ⭐ 是否超时
const isOverdue = computed(() => {
  if (!instance.value.currentNodeDeadline) return false
  return new Date(instance.value.currentNodeDeadline) < new Date()
})
const statusColor = computed(() => ({ running: 'warning', completed: 'success', rejected: 'danger', cancelled: 'info' }[instance.value.status] || ''))
const statusText = computed(() => ({ running: '运行中', completed: '已完成', rejected: '已驳回', cancelled: '已撤销' }[instance.value.status] || instance.value.status))

const trailColor = (action) => {
  if (!action) return 'blue'
  if (action === 'approve') return 'green'
  if (action === 'reject') return 'red'
  if (action === 'skip') return 'gray'
  return 'blue'
}
const trailActionText = (r) => {
  if (!r.action) return '发起'
  const map = { approve: '通过', reject: '驳回', skip: '跳过', cancel: '撤回', addSign: '加签中', transfer: '转办' }
  return map[r.action] || r.action
}

// ⭐ 加载数据 + 渲染 BPMN
const loadAll = async () => {
  try {
    const [instRes, trailRes] = await Promise.all([
      getInstanceById(instanceId),
      getInstanceTrail(instanceId)
    ])
    instance.value = instRes.data || {}
    trail.value = trailRes.data || { completedNodeKeys: [], currentNodeKeys: [], rejectedNodeKeys: [], approveRecords: [] }

    // 有了 definitionId 才能拿 BPMN
    if (instance.value.definitionId) {
      await renderBpmn(instance.value.definitionId)
    }
  } catch (e) { ElMessage.error('加载失败: ' + (e?.response?.data?.msg || e.message)) }
}

const renderBpmn = async (definitionId) => {
  await nextTick()
  try {
    const xmlRes = await getBpmnXml(definitionId)
    bpmnViewer = markRaw(new BpmnViewer({ container: '#bpmn-canvas-approve' }))
    await bpmnViewer.importXML(xmlRes.data)
    bpmnViewer.get('canvas').zoom('fit-viewport')
    applyHighlights()
  } catch (e) { console.error('BPMN 渲染失败', e) }
}

const applyHighlights = () => {
  if (!bpmnViewer) return
  const overlays = bpmnViewer.get('overlays')
  const canvas = bpmnViewer.get('canvas')
  const highlight = (keys, className) => {
    keys?.forEach(k => {
      try {
        canvas.addMarker(k, className)
      } catch { /* bpmn-js 找不到节点忽略 */ }
    })
  }
  highlight(trail.value.completedNodeKeys, 'node-completed')
  highlight(trail.value.currentNodeKeys, 'node-current')
  highlight(trail.value.rejectedNodeKeys, 'node-rejected')
}

// ========== 审批提交 ==========
const handleSubmit = async (action) => {
  try {
    await ElMessageBox.confirm(`确定要${action === 'approve' ? '通过' : '驳回'}该流程吗？`, '提示', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: action === 'approve' ? 'success' : 'warning'
    })
    await approveWorkflow({ instanceId, action, comment: approveForm.comment })
    ElMessage.success('提交成功')
    router.push('/workflow/todo')
  } catch (e) { if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || '提交失败') }
}

// ========== 加签 ==========
const addSignerVisible = ref(false)
const addSignerForm = reactive({ addUserIds: [], comment: '', _userOpts: [] })
const searchUser = async (kw, scope) => {
  try {
    const res = await request({ url: '/system/user/search', method: 'get', params: { keyword: kw || '', limit: 50 } })
    const opts = res.data || []
    if (scope === 'add') addSignerForm._userOpts = opts
    else transferForm._userOpts = opts
  } catch (e) { console.warn(e) }
}
const confirmAddSigner = async () => {
  try {
    const nodeId = instance.value.currentNodeId
    await addSigner({ instanceId, nodeId, addUserIds: addSignerForm.addUserIds, comment: addSignerForm.comment })
    ElMessage.success('加签成功')
    addSignerVisible.value = false
    addSignerForm.addUserIds = []
    addSignerForm.comment = ''
    loadAll()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '加签失败') }
}

// ========== 转办 ==========
const transferVisible = ref(false)
const transferForm = reactive({ userId: null, comment: '', _userOpts: [] })
const confirmTransfer = async () => {
  try {
    await transferApprover({ instanceId, userId: transferForm.userId, comment: transferForm.comment })
    ElMessage.success('转办成功')
    transferVisible.value = false
    router.push('/workflow/todo')
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '转办失败') }
}

const goBack = () => router.push('/workflow/todo')

onMounted(loadAll)
onBeforeUnmount(() => { if (bpmnViewer) bpmnViewer.destroy() })
</script>

<style scoped>
.workflow-approve-container { padding: 12px; }
.top-bar { display: flex; justify-content: space-between; margin-bottom: 12px; }
.top-bar-right { display: flex; gap: 8px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.legend { display: flex; gap: 12px; font-size: 12px; color: #666; }
.legend-item { display: flex; align-items: center; gap: 4px; }
.dot { display: inline-block; width: 12px; height: 12px; border-radius: 50%; }
.dot-completed { background: #1677ff; opacity: 0.6; }
.dot-current { background: #ff7d00; box-shadow: 0 0 6px #ff7d00; animation: pulse 1.5s infinite; }
.dot-rejected { background: #f53f3f; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }
.trail-action { font-size: 12px; color: #666; }
.trail-comment { font-size: 12px; color: #999; margin-top: 2px; }
.text-danger { color: #f53f3f; font-weight: bold; }
.text-muted { color: #c0c4cc; }
.approve-bpmn-canvas { height: 500px; border: 1px solid #eee; border-radius: 4px; }

@media (max-width: 768px) {
  .approve-bpmn-canvas { height: 280px; }
  .approve-row { flex-direction: column; }
  .top-bar { flex-wrap: wrap; gap: 8px; }
  .top-bar-right { flex-wrap: wrap; }
  .legend { flex-wrap: wrap; gap: 6px; }
}
</style>

<!-- ⭐ BPMN 高亮样式（必须全局，因为 bpmn-js 用 canvas marker） -->
<style>
.node-completed { outline: 3px solid #1677ff; outline-offset: -3px; }
.node-current { outline: 3px solid #ff7d00; outline-offset: -3px; animation: bpmn-pulse 1.5s infinite; }
.node-rejected { outline: 3px solid #f53f3f; outline-offset: -3px; }
@keyframes bpmn-pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>