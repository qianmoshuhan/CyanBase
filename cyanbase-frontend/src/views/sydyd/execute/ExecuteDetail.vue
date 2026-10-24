<template>
  <div class="execute-detail-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>实施详情 - {{ detail.itemName }}</span>
          <div>
            <el-button @click="goBack">返回列表</el-button>
            <el-button
              v-if="detail.executeStatus === 1"
              type="success" @click="handleStart"
            >开始执行</el-button>
            <el-button
              v-if="detail.executeStatus === 2"
              type="warning" @click="handleFinish"
            >标记完成</el-button>
          </div>
        </div>
      </template>

      <el-descriptions v-if="detail.id" :column="isMobile ? 1 : 2" border>
        <el-descriptions-item label="实施ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="事项名称">{{ detail.itemName }}</el-descriptions-item>
        <el-descriptions-item label="来源会议">{{ detail.meetingName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联会议ID">{{ detail.meetingId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行部门">{{ detail.executeDept || '-' }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ detail.responsible || '-' }}</el-descriptions-item>
        <el-descriptions-item label="计划完成时间">{{ detail.planFinishTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际完成时间">{{ detail.actualFinishTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实施状态">
          <el-tag :type="getStatusType(detail.executeStatus)">
            {{ statusMap[detail.executeStatus] || '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="表决结果">
          <template v-if="detail.voteResult === 1">
            <el-tag type="success">同意</el-tag>
          </template>
          <template v-else-if="detail.voteResult === 2">
            <el-tag type="danger">反对</el-tag>
          </template>
          <template v-else>{{ detail.voteResult ?? '-' }}</template>
        </el-descriptions-item>
        <el-descriptions-item label="执行进度" :span="2">
          <el-progress :percentage="detail.progress || 0" />
        </el-descriptions-item>
        <el-descriptions-item label="进展详情" :span="2">{{ detail.progressDetail || '-' }}</el-descriptions-item>
        <el-descriptions-item label="佐证材料">{{ detail.evidenceFile || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联工作流">
          <template v-if="detail.workflowInstanceId">
            <el-tag type="primary" effect="plain">instance #{{ detail.workflowInstanceId }}</el-tag>
          </template>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detail.updateTime || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getExecuteDetail, startExecute, finishExecute } from '@/api/sydyd'

const route = useRoute()
const router = useRouter()

// ⭐ 响应式检测
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

const detail = ref({})

const statusMap = { 1: '待执行', 2: '执行中', 3: '已完成', 4: '已逾期', 5: '已归档' }
const getStatusType = (s) => ({ 1: 'info', 2: 'warning', 3: 'success', 4: 'danger', 5: 'primary' }[s] || '')

const fetchDetail = async () => {
  const id = route.query.id
  if (!id) return
  const res = await getExecuteDetail(id)
  detail.value = res.data || {}
}

const handleStart = () => ElMessageBox.confirm('确定开始执行？', '提示', { type: 'warning' })
  .then(async () => { await startExecute(detail.value.id); ElMessage.success('已开始执行'); fetchDetail() })
  .catch(() => {})

const handleFinish = () => ElMessageBox.confirm('确定标记为已完成？', '提示', { type: 'warning' })
  .then(async () => { await finishExecute(detail.value.id); ElMessage.success('已完成'); fetchDetail() })
  .catch(() => {})

const goBack = () => router.push('/sydyd/execute')

onMounted(fetchDetail)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.card-header > div { display: flex; gap: 6px; flex-wrap: wrap; }
</style>