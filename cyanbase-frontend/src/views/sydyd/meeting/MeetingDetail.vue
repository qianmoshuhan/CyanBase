<template>
  <div class="meeting-detail-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>会议详情 - {{ detail.meetingName }}</span>
          <div>
            <el-button @click="goBack">返回列表</el-button>
            <el-button
              v-if="detail.meetingStatus === 1"
              type="success" @click="handleStart"
            >开始会议</el-button>
            <el-button
              v-if="detail.meetingStatus === 2"
              type="warning" @click="handleFinish"
            >结束会议</el-button>
            <el-button
              v-if="detail.meetingStatus === 3"
              type="primary" @click="handleArchive"
            >归档</el-button>
          </div>
        </div>
      </template>

      <el-descriptions v-if="detail.id" :column="isMobile ? 1 : 2" border>
        <el-descriptions-item label="会议ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="会议名称">{{ detail.meetingName }}</el-descriptions-item>
        <el-descriptions-item label="会议类型">{{ meetingTypeMap[detail.meetingType] || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主持人">{{ detail.host || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开会时间">{{ detail.meetingTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="会议地点">{{ detail.meetingPlace || '-' }}</el-descriptions-item>
        <el-descriptions-item label="参会人员">{{ detail.attendees || '-' }}</el-descriptions-item>
        <el-descriptions-item label="列席人员">{{ detail.observers || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(detail.meetingStatus)">
            {{ statusMap[detail.meetingStatus] || '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="关联工作流">
          <template v-if="detail.workflowInstanceId">
            <el-tag type="primary" effect="plain">instance #{{ detail.workflowInstanceId }}</el-tag>
          </template>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="hover" style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>会议议题（{{ topics.length }} 条）</span>
          <div>
            <el-tag v-if="detail.meetingStatus === 2" type="warning" effect="plain" style="margin-right:8px">
              会议进行中 · 可边讨论边表决
            </el-tag>
            <el-button
              v-if="detail.meetingStatus === 1"
              type="primary" size="small" @click="handleAddTopic"
            >添加议题</el-button>
            <el-button
              v-if="detail.meetingStatus === 2 || detail.meetingStatus === 3"
              type="success" size="small" @click="handleVoteAll"
            >批量表决</el-button>
          </div>
        </div>
      </template>

      <div class="table-scroll-wrapper">
      <el-table :data="topics" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="topicTitle" label="议题名称" min-width="200" />
        <el-table-column prop="topicOrder" label="序号" width="80" />
        <el-table-column prop="voteResult" label="表决结果" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.voteResult === 1" type="success">同意</el-tag>
            <el-tag v-else-if="row.voteResult === 2" type="danger">反对</el-tag>
            <el-tag v-else-if="row.voteResult === 3" type="warning">弃权</el-tag>
            <span v-else class="text-muted">待表决</span>
          </template>
        </el-table-column>
        <el-table-column prop="voteDetail" label="表决意见" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="detail.meetingStatus === 2 || detail.meetingStatus === 3">
              <el-button
                v-if="row.voteResult !== 1"
                type="success" link size="small"
                @click="openVoteDialog(row, 1)"
              >同意</el-button>
              <el-button
                v-if="row.voteResult !== 2"
                type="danger" link size="small"
                @click="openVoteDialog(row, 2)"
              >反对</el-button>
              <el-button
                v-if="row.voteResult !== 3"
                type="warning" link size="small"
                @click="openVoteDialog(row, 3)"
              >弃权</el-button>
              <el-button
                v-if="row.voteResult != null"
                type="primary" link size="small"
                @click="openVoteDialog(row)"
              >修改</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      </div>
      <el-empty v-if="!topics.length" description="暂无议题" />
    </el-card>

    <!-- 添加议题 dialog -->
    <el-dialog v-model="topicDialogVisible" title="添加议题到本会议" :width="isMobile ? '92%' : '560px'">
      <el-form label-width="100px">
        <el-form-item label="可选议题">
          <el-select
            v-model="selectedTopicIds"
            multiple collapse-tags collapse-tags-tooltip
            placeholder="选择前置把关已通过的议题"
            style="width: 100%"
            :loading="topicOptionsLoading"
          >
            <el-option
              v-for="t in topicOptions"
              :key="t.id"
              :label="`[#${t.id}] ${t.topicTitle}`"
              :value="t.id"
              :disabled="t.alreadyInMeeting"
            >
              <span>{{ t.topicTitle }}</span>
              <span v-if="t.alreadyInMeeting" style="color:#999;margin-left:8px">已在本会议</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-alert
            type="info" :closable="false" show-icon
            title="只显示前置把关已通过（status=2）的议题，其他请先到议题管理页处理"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="topicDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAddTopics">确定添加</el-button>
      </template>
    </el-dialog>

    <!-- 单个议题表决/修改 dialog -->
    <el-dialog v-model="voteDialogVisible" :title="voteRow && voteRow.voteResult != null ? '修改表决' : '录入表决'" :width="isMobile ? '92%' : '480px'">
      <el-form v-if="voteRow" label-width="90px">
        <el-form-item label="议题">
          <el-tag>{{ voteRow.topicTitle }}</el-tag>
        </el-form-item>
        <el-form-item label="表决结果">
          <el-radio-group v-model="voteForm.voteResult">
            <el-radio :label="1">同意</el-radio>
            <el-radio :label="2">反对</el-radio>
            <el-radio :label="3">弃权</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="表决意见">
          <el-input v-model="voteForm.voteDetail" type="textarea" :rows="3" placeholder="可选，如同意请输入意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="voteDialogVisible = false">取消</el-button>
        <el-button type="success" :loading="voteSubmitting" @click="confirmVoteOne">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量表决 dialog -->
    <el-dialog v-model="voteAllDialogVisible" title="批量表决" :width="isMobile ? '92%' : '600px'">
      <el-alert type="warning" show-icon :closable="false" title="对所有未表决议题统一表决" style="margin-bottom: 16px" />
      <el-form :model="voteAllForm" label-width="100px">
        <el-form-item label="表决结果">
          <el-radio-group v-model="voteAllForm.voteResult">
            <el-radio :label="1">同意</el-radio>
            <el-radio :label="2">反对</el-radio>
            <el-radio :label="3">弃权</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="表决意见">
          <el-input v-model="voteAllForm.voteDetail" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="voteAllDialogVisible = false">取消</el-button>
        <el-button type="success" @click="confirmVoteAll">确认批量表决</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMeetingDetail, getMeetingTopics, startMeeting, finishMeeting,
  archiveMeeting, addTopicsToMeeting, voteMeeting, getTopicList
} from '@/api/sydyd'

const route = useRoute()
const router = useRouter()

// ⭐ 响应式检测
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

const meetingId = ref(null)
const detail = ref({})
const topics = ref([])

const meetingTypeMap = { 1: '党委会', 2: '总经理办公会', 3: '董事会' }
const statusMap = { 1: '待召开', 2: '进行中', 3: '已结束', 4: '已归档' }
const getStatusType = (s) => ({ 1: 'info', 2: 'warning', 3: 'success', 4: 'primary' }[s] || '')

// === 添加议题相关 ===
const topicDialogVisible = ref(false)
const topicOptions = ref([])
const topicOptionsLoading = ref(false)
const selectedTopicIds = ref([])
const existingTopicIdsInMeeting = ref(new Set())

// === 单个议题表决 dialog ===
const voteDialogVisible = ref(false)
const voteSubmitting = ref(false)
const voteRow = ref(null)
const voteForm = reactive({ voteResult: 1, voteDetail: '' })

// === 批量表决 dialog ===
const voteAllDialogVisible = ref(false)
const voteAllForm = reactive({ voteResult: 1, voteDetail: '' })

// ========= 数据加载 =========
const fetchAll = async () => {
  meetingId.value = route.query.id
  if (!meetingId.value) return
  const [detailRes, topicsRes] = await Promise.all([
    getMeetingDetail(meetingId.value),
    getMeetingTopics(meetingId.value)
  ])
  detail.value = detailRes.data || {}
  topics.value = topicsRes.data || []
  existingTopicIdsInMeeting.value = new Set(topics.value.map(t => t.id))
}

// ========= 顶部按钮 =========
const goBack = () => router.push('/sydyd/meeting')

const handleStart = () => ElMessageBox.confirm('确定开始会议？', '提示', { type: 'warning' })
  .then(async () => { await startMeeting(meetingId.value); ElMessage.success('会议已开始'); fetchAll() })
  .catch(() => {})

const handleFinish = () => ElMessageBox.confirm('确定结束会议？结束后可进行最终表决。', '提示', { type: 'warning' })
  .then(async () => { await finishMeeting(meetingId.value); ElMessage.success('会议已结束'); fetchAll() })
  .catch(() => {})

const handleArchive = () => ElMessageBox.confirm('确定归档？归档后不可修改。', '提示', { type: 'warning' })
  .then(async () => { await archiveMeeting(meetingId.value); ElMessage.success('已归档'); fetchAll() })
  .catch(() => {})

// ========= 添加议题 =========
const handleAddTopic = async () => {
  selectedTopicIds.value = []
  topicOptionsLoading.value = true
  try {
    const res = await getTopicList({ pageNum: 1, pageSize: 100, topicStatus: 2 })
    const all = res.data?.records || []
    topicOptions.value = all.map(t => ({
      ...t,
      alreadyInMeeting: existingTopicIdsInMeeting.value.has(t.id)
    }))
    if (!topicOptions.value.length) {
      ElMessage.warning('没有可用议题，请先在议题管理页完成前置把关')
    }
  } finally {
    topicOptionsLoading.value = false
    topicDialogVisible.value = true
  }
}
const confirmAddTopics = async () => {
  const ids = selectedTopicIds.value
  if (!ids.length) { ElMessage.warning('请选择要添加的议题'); return }
  try {
    await addTopicsToMeeting(meetingId.value, ids)
    ElMessage.success(`添加请求已发送（共${ids.length}个）`)
    topicDialogVisible.value = false
    fetchAll()
  } catch (e) { console.error(e) }
}

// ========= 单个议题表决/修改 =========
const openVoteDialog = (row, quickResult) => {
  voteRow.value = row
  voteForm.voteResult = quickResult || row.voteResult || 1
  voteForm.voteDetail = row.voteDetail || ''
  voteDialogVisible.value = true
}
const confirmVoteOne = async () => {
  voteSubmitting.value = true
  try {
    await voteMeeting(meetingId.value, [{
      id: voteRow.value.id,
      voteResult: voteForm.voteResult,
      voteDetail: voteForm.voteDetail
    }])
    ElMessage.success('表决已保存')
    voteDialogVisible.value = false
    fetchAll()
  } finally { voteSubmitting.value = false }
}

// ========= 批量表决 =========
const handleVoteAll = () => {
  voteAllForm.voteResult = 1
  voteAllForm.voteDetail = ''
  voteAllDialogVisible.value = true
}
const confirmVoteAll = async () => {
  const pending = topics.value.filter(t => t.voteResult == null)
  if (!pending.length) { ElMessage.warning('没有待表决的议题'); voteAllDialogVisible.value = false; return }
  try {
    const votes = pending.map(t => ({
      id: t.id, voteResult: voteAllForm.voteResult, voteDetail: voteAllForm.voteDetail
    }))
    await voteMeeting(meetingId.value, votes)
    ElMessage.success(`已对 ${votes.length} 个议题完成表决`)
    voteAllDialogVisible.value = false
    fetchAll()
  } catch (e) { console.error(e) }
}

onMounted(fetchAll)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.card-header > div { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
.text-muted { color: #999; }
</style>