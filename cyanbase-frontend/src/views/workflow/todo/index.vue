<template>
  <div class="workflow-todo-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>我的待办</span>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="!isMobile" class="search-form">
        <el-form-item label="流程名称">
          <el-input v-model="query.processName" placeholder="请输入流程名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <div class="table-scroll-wrapper">
      <el-table :data="list" border stripe highlight-current-row>
        <el-table-column type="index" label="序号" width="70" align="center" :index="indexMethod" />
        <el-table-column prop="itemName" label="事项名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.itemName">{{ row.itemName }}</span>
            <span v-else-if="row.processName">{{ row.processName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="processName" label="流程名称" min-width="150" />
        <el-table-column prop="currentNodeName" label="当前节点" min-width="120" />
        <el-table-column prop="initiatorName" label="发起人" width="120" />
        <el-table-column prop="startTime" label="发起时间" width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusColor(row.status)">
              {{ getStatusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleApprove(row)">审批</el-button>
            <el-button type="warning" link @click="handleView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- 分页 -->
      <el-pagination
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="流程详情 - 审批进度预览" width="85%" top="5vh" class="todo-detail-dialog">
      <el-row :gutter="12" class="todo-detail-row">
        <el-col :span="15" :xs="24">
          <div id="bpmn-canvas-todo" class="todo-bpmn-canvas"></div>
          <div class="legend" style="margin-top:8px;display:flex;gap:16px;font-size:12px;color:#666;">
            <span><i style="display:inline-block;width:12px;height:12px;border-radius:50%;background:#1677ff;margin-right:4px;"></i>已完成</span>
            <span><i style="display:inline-block;width:12px;height:12px;border-radius:50%;background:#ff7d00;margin-right:4px;"></i>当前待办</span>
            <span><i style="display:inline-block;width:12px;height:12px;border-radius:50%;background:#f53f3f;margin-right:4px;"></i>已驳回</span>
          </div>
        </el-col>
        <el-col :span="9" :xs="24">
          <el-descriptions :column="1" border size="small" style="margin-top:12px;">
            <el-descriptions-item label="流程名称">{{ detail.processName }}</el-descriptions-item>
            <el-descriptions-item label="当前节点">{{ detail.currentNodeName }}</el-descriptions-item>
            <el-descriptions-item label="发起人">{{ detail.initiatorName }}</el-descriptions-item>
            <el-descriptions-item label="发起时间">{{ detail.startTime }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusColor(detail.status)">{{ getStatusName(detail.status) }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">审批轨迹</el-divider>
          <el-timeline style="max-height:320px;overflow:auto;">
            <el-timeline-item v-for="(r,i) in trail.approveRecords" :key="i"
                              :timestamp="r.approvalTime"
                              :color="!r.action ? 'blue' : r.action==='approve' ? 'green' : r.action==='reject' ? 'red' : 'gray'">
              <div><strong>{{ r.nodeName }}</strong> - {{ r.approverName || ('用户#'+r.approverId) }}</div>
              <div style="font-size:12px;color:#666;">{{ !r.action ? '发起' : r.action==='approve' ? '通过' : r.action==='reject' ? '驳回' : r.action }}</div>
              <div v-if="r.comment" style="font-size:12px;color:#999;">意见：{{ r.comment }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-col>
      </el-row>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, markRaw } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getMyTodoList, getInstanceTrail, getBpmnXml } from '@/api/wfDefinition'
const router = useRouter()

// ⭐ 响应式检测
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

import BpmnViewer from 'bpmn-js/lib/Viewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'

const query = reactive({
  processName: ''
})

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const detailDialogVisible = ref(false)
const currentRow = ref(null)

const detail = ref({})
const trail = ref({ completedNodeKeys:[], currentNodeKeys:[], rejectedNodeKeys:[], approveRecords:[] })
let bpmnViewer = null

const fetchData = async () => {
  try {
    const res = await getMyTodoList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...query
    })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error('获取待办列表失败', error)
  }
}

// ⭐ 跳转到独立审批页
const handleApprove = (row) => {
  router.push({ path: '/workflow/approve', query: { id: row.id } })
}

// ⭐ 查看：加载详情 + BPMN 轨迹
const handleView = async (row) => {
  currentRow.value = row
  detail.value = row
  trail.value = { completedNodeKeys:[], currentNodeKeys:[], rejectedNodeKeys:[], approveRecords:[] }
  detailDialogVisible.value = true
  await nextTick()
  try {
    const trailRes = await getInstanceTrail(row.id)
    trail.value = trailRes.data || trail.value
    if (row.definitionId) {
      const xmlRes = await getBpmnXml(row.definitionId)
      // ⭐ 防重复创建：先 destroy 旧的（照搬 designer 的写法）
      if (bpmnViewer) { bpmnViewer.destroy(); bpmnViewer = null }
      bpmnViewer = markRaw(new BpmnViewer({ container: '#bpmn-canvas-todo' }))
      await bpmnViewer.importXML(xmlRes.data)
      bpmnViewer.get('canvas').zoom('fit-viewport')
      applyTodoHighlights()
    }
  } catch (e) { console.warn('加载 BPMN 预览失败', e) }
}
const applyTodoHighlights = () => {
  if (!bpmnViewer) return
  const canvas = bpmnViewer.get('canvas')
  trail.value.completedNodeKeys?.forEach(k => { try { canvas.addMarker(k, 'node-completed'); } catch {} })
  trail.value.currentNodeKeys?.forEach(k => { try { canvas.addMarker(k, 'node-current'); } catch {} })
  trail.value.rejectedNodeKeys?.forEach(k => { try { canvas.addMarker(k, 'node-rejected'); } catch {} })
}



const getStatusName = (status) => ({
  running: '审批中',
  completed: '已完成',
  rejected: '已驳回',
  cancelled: '已撤销'
}[status] || status)

const getStatusColor = (status) => ({
  running: 'warning',
  completed: 'success',
  rejected: 'danger',
  cancelled: 'info'
}[status] || '')

const handleReset = () => {
  query.processName = ''
  pageNum.value = 1
  fetchData()
}

// 序号生成器：根据当前页码和每页大小计算全局序号
const indexMethod = (index) => {
  return (pageNum.value - 1) * pageSize.value + index + 1
}

fetchData()

onBeforeUnmount(() => { if (bpmnViewer) bpmnViewer.destroy() })
</script>

<style scoped>
.workflow-todo-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
</style>
<style>
.node-completed { outline: 3px solid #1677ff; outline-offset: -3px; }
.node-current { outline: 3px solid #ff7d00; outline-offset: -3px; animation: bpulse 1.5s infinite; }
.node-rejected { outline: 3px solid #f53f3f; outline-offset: -3px; }
@keyframes bpulse { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>