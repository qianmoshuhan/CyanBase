<template>
  <div class="item-detail-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>事项详情 - {{ detail.itemName }}</span>
          <div>
            <el-button @click="goBack">返回列表</el-button>
            <el-button
              v-if="detail.itemStatus === 1"
              type="success"
              @click="handleSubmit"
            >提交审批</el-button>
            <el-button
              v-if="detail.itemStatus === 2 || detail.itemStatus === 6"
              type="info"
              @click="handleWithdraw"
            >回退草稿</el-button>
          </div>
        </div>
      </template>

      <el-descriptions v-if="detail.id" :column="isMobile ? 1 : 2" border>
        <el-descriptions-item label="事项ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="事项名称">{{ detail.itemName }}</el-descriptions-item>
        <el-descriptions-item label="事项类型">{{ itemTypeMap[detail.itemType] || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资金额度(万元)">{{ detail.fundAmount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="主办部门">{{ detail.hostDept || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicant || '-' }}</el-descriptions-item>
        <el-descriptions-item label="填报部门">{{ detail.submitDeptId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(detail.itemStatus)">
            {{ statusMap[detail.itemStatus] || '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="现状说明" :span="2">{{ detail.currentStatus || '-' }}</el-descriptions-item>
        <el-descriptions-item label="论证材料">{{ detail.proofMaterial || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ detail.applyTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联工作流">
          <template v-if="detail.workflowInstanceId">
            <el-tag type="primary" effect="plain">instance #{{ detail.workflowInstanceId }}</el-tag>
          </template>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- ⭐ 已提交审批 → BPMN 审批轨迹预览 -->
    <el-card v-if="detail.workflowInstanceId" shadow="hover" style="margin-top:16px;">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <strong>审批轨迹预览</strong>
          <el-tag type="primary" effect="plain">实例 #{{ detail.workflowInstanceId }}</el-tag>
        </div>
      </template>
      <el-row :gutter="12" class="bpmn-row">
        <el-col :span="16" :xs="24">
          <div id="bpmn-canvas-item" class="bpmn-canvas-item"></div>
        </el-col>
        <el-col :span="8" :xs="24">
          <el-divider content-position="left">审批历史</el-divider>
          <el-timeline v-if="trail.approveRecords?.length">
            <el-timeline-item v-for="(r,i) in trail.approveRecords" :key="i"
                              :timestamp="r.approvalTime"
                              :color="!r.action?'blue':r.action==='approve'?'green':r.action==='reject'?'red':'gray'">
              <div><strong>{{ r.nodeName }}</strong> - {{ r.approverName||('用户#'+r.approverId) }}</div>
              <div style="font-size:12px;color:#666;">{{ !r.action?'发起':r.action==='approve'?'通过':r.action==='reject'?'驳回':r.action }}</div>
              <div v-if="r.comment" style="font-size:12px;color:#999;">意见：{{ r.comment }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无审批记录" />
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getItemDetail, withdrawItem } from '@/api/sydyd'
import { getPublishedWorkflowList, getInstanceTrail, getBpmnXml } from '@/api/wfDefinition'
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

const detail = ref({})
const trail = ref({ completedNodeKeys:[], currentNodeKeys:[], rejectedNodeKeys:[], approveRecords:[] })
let bpmnViewer = null

const itemTypeMap = { 1: '大额资金', 2: '重大项目', 3: '人事任免', 4: '其他' }
const statusMap = { 1: '草稿', 2: '待酝酿', 3: '已生成议题', 4: '已上会', 5: '已办结', 6: '已驳回' }
const getStatusType = (s) => ({ 1: 'info', 2: 'warning', 3: '', 4: '', 5: 'success', 6: 'danger' }[s] || '')

const fetchDetail = async () => {
  const id = route.query.id
  if (!id) return
  const res = await getItemDetail(id)
  detail.value = res.data || {}

  // ⭐ 如果已提交审批，渲染 BPMN 轨迹
  if (detail.value.workflowInstanceId) {
    await nextTick()
    try {
      const [trailRes, xmlRes] = await Promise.all([
        getInstanceTrail(detail.value.workflowInstanceId),
        detail.value.definitionId ? getBpmnXml(detail.value.definitionId) : Promise.resolve(null)
      ])
      trail.value = trailRes.data || trail.value
      if (xmlRes && xmlRes.data) {
        bpmnViewer = markRaw(new BpmnViewer({ container: '#bpmn-canvas-item' }))
        await bpmnViewer.importXML(xmlRes.data)
        bpmnViewer.get('canvas').zoom('fit-viewport')
        applyItemHighlights()
      }
    } catch (e) { console.warn('BPMN 轨迹加载失败', e) }
  }
}
const applyItemHighlights = () => {
  if (!bpmnViewer) return
  const canvas = bpmnViewer.get('canvas')
  trail.value.completedNodeKeys?.forEach(k => { try { canvas.addMarker(k, 'node-completed'); } catch {} })
  trail.value.currentNodeKeys?.forEach(k => { try { canvas.addMarker(k, 'node-current'); } catch {} })
  trail.value.rejectedNodeKeys?.forEach(k => { try { canvas.addMarker(k, 'node-rejected'); } catch {} })
}

const BUSINESS_TYPE = 'threeBigItem'

/**
 * 提交审批：先查 businessType=threeBigItem 的已发布流程
 *   0 个 → 提示去配置
 *   1 个 → 直接用（不弹框）
 *   多个 → 弹框让用户选
 */
const handleSubmit = async () => {
  let publishedList = []
  try {
    const res = await getPublishedWorkflowList({ businessType: BUSINESS_TYPE })
    publishedList = res.data || []
  } catch (e) {
    console.warn('查询可用流程失败', e)
  }

  if (publishedList.length === 0) {
    ElMessageBox.alert(
      '当前没有可用的审批流程，请先到【工作流定义】中创建并发布一个业务类型为「三重一大事项」的流程。',
      '提示', { type: 'warning', confirmButtonText: '知道了' }
    )
    return
  }

  let definitionId = publishedList[0].id

  if (publishedList.length > 1) {
    // 多个 → 弹框让用户选
    try {
      const { value } = await ElMessageBox.prompt(
        '当前有多个可用的审批流程，请选择一个：',
        '选择审批流程',
        {
          confirmButtonText: '确定提交',
          cancelButtonText: '取消',
          inputType: 'select',
          inputPlaceholder: '请选择流程定义',
          inputOptions: publishedList.map(d => ({ value: d.id, label: d.processName + ' (v' + (d.version || 1) + ')' }))
        }
      )
      definitionId = Number(value)
    } catch { return }
  } else {
    // 只有 1 个 → 直接二次确认
    try {
      await ElMessageBox.confirm(
        `将使用审批流程【${publishedList[0].processName}】提交，确定继续？`,
        '提交确认', { type: 'warning', confirmButtonText: '确定提交' }
      )
    } catch { return }
  }

  try {
    // ⭐ 调用后端 submit 接口，传 definitionId
    await request({
      url: `/tenant/sydyd/item/${detail.value.id}/submit`,
      method: 'put',
      params: { definitionId }
    })
    ElMessage.success('提交成功，工作流已发起')
    fetchDetail()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '提交失败')
  }
}

const handleWithdraw = () => {
  ElMessageBox.confirm('确定回退为草稿？', '提示', { type: 'warning' })
    .then(async () => {
      await withdrawItem(detail.value.id)
      ElMessage.success('已回退')
      fetchDetail()
    }).catch(() => {})
}

const goBack = () => router.push('/sydyd/item')

onMounted(fetchDetail)
onBeforeUnmount(() => { if (bpmnViewer) bpmnViewer.destroy() })
</script>
<style>
.node-completed { outline: 3px solid #1677ff; outline-offset: -3px; }
.node-current { outline: 3px solid #ff7d00; outline-offset: -3px; animation: bpulse 1.5s infinite; }
.node-rejected { outline: 3px solid #f53f3f; outline-offset: -3px; }
@keyframes bpulse { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.bpmn-canvas-item { height: 420px; border: 1px solid #eee; border-radius: 4px; }

@media (max-width: 768px) {
  .bpmn-canvas-item { height: 280px; }
  .bpmn-row { flex-direction: column; }
}
</style>