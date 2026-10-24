<template>
  <div class="topic-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="query">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="议题名称/编号" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="议题状态">
          <el-select v-model="query.topicStatus" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="s in statusOpts" :key="s.val" :label="s.label" :value="s.val" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="handleGenerate">从采购事项生成议题</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="topicName" label="议题名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="topicCode" label="议题编号" width="160" />
        <el-table-column prop="itemId" label="关联事项ID" width="110" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.topicStatus)">{{ statusOpts.find(s => s.val === row.topicStatus)?.label || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="preCheckOpinion" label="前置把关意见" min-width="180" show-overflow-tooltip />
        <el-table-column prop="preCheckBy" label="把关人" width="110" />
        <el-table-column label="关联会议" width="140">
          <template #default="{ row }">{{ row.meetingId ? `meeting#${row.meetingId}` : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.topicStatus === 1"
              link type="success" size="small"
              @click="handlePreCheck(row, 1)"
            >前置通过</el-button>
            <el-button
              v-if="row.topicStatus === 1"
              link type="warning" size="small"
              @click="handlePreCheck(row, 2)"
            >前置打回</el-button>
            <el-button link type="primary" size="small" @click="handleDetail(row)">详情</el-button>
            <el-button
              v-if="row.topicStatus === 1"
              link type="danger" size="small"
              @click="handleDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList"
        @current-change="loadList"
        class="pagination"
      />
    </el-card>

    <!-- 前置把关弹窗 -->
    <el-dialog v-model="opinionVisible" :title="opinionType === 1 ? '前置把关-通过' : '前置把关-打回'" width="450px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="把关意见">
          <el-input v-model="opinion" type="textarea" :rows="4" :placeholder="opinionType === 1 ? '请输入同意意见（可选）' : '请输入打回原因'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="opinionVisible = false">取消</el-button>
        <el-button :type="opinionType === 1 ? 'success' : 'danger'" @click="submitOpinion">确定</el-button>
      </template>
    </el-dialog>

    <!-- 从事项生成 -->
    <el-dialog v-model="genVisible" title="从采购事项生成议题" width="520px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        输入采购事项ID，自动从事项名称/分类/清单生成议题草稿
      </el-alert>
      <el-input-number v-model="genItemId" :min="1" placeholder="事项ID" style="width: 100%" />
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :loading="genLoading" @click="submitGenerate">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTopicList, generateTopicFromItem,
  preCheckPass, preCheckReject, deleteTopic
} from '@/api/sydyd'

const statusOpts = [
  { val: 1, label: '待前置把关' },
  { val: 2, label: '前置把关通过' },
  { val: 3, label: '已入库会议' },
  { val: 4, label: '已表决' },
  { val: 5, label: '已作废' }
]
const statusTag = (s) => {
  const map = { 1: 'warning', 2: 'success', 3: '', 4: 'info', 5: 'danger' }
  return map[s] || ''
}

const loading = ref(false)
const genLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', topicStatus: null })

const opinionVisible = ref(false)
const opinionType = ref(1)
const opinion = ref('')
const opinionRow = ref(null)

const genVisible = ref(false)
const genItemId = ref(null)

const loadList = async () => {
  loading.value = true
  try {
    const res = await getTopicList(query)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
const resetQuery = () => {
  query.keyword = ''; query.topicStatus = null; query.pageNum = 1; loadList()
}

const handleGenerate = () => {
  genItemId.value = null
  genVisible.value = true
}
const submitGenerate = async () => {
  if (!genItemId.value) { ElMessage.warning('请输入事项ID'); return }
  genLoading.value = true
  try {
    const res = await generateTopicFromItem(genItemId.value)
    ElMessage.success(`议题生成成功（topicId=${res.data}）`)
    genVisible.value = false
    loadList()
  } finally { genLoading.value = false }
}

const handlePreCheck = (row, type) => {
  opinionRow.value = row
  opinionType.value = type
  opinion.value = ''
  opinionVisible.value = true
}
const submitOpinion = async () => {
  if (opinionType.value === 2 && !opinion.value.trim()) {
    ElMessage.warning('打回必须填写原因'); return
  }
  if (opinionType.value === 1) {
    await preCheckPass(opinionRow.value.id, opinion.value)
    ElMessage.success('前置把关通过')
  } else {
    await preCheckReject(opinionRow.value.id, opinion.value)
    ElMessage.success('已打回')
  }
  opinionVisible.value = false
  loadList()
}

const handleDetail = (row) => {
  ElMessageBox.alert(
    `<b>议题名称：</b>${row.topicName}<br/>` +
    `<b>议题编号：</b>${row.topicCode || '-'}<br/>` +
    `<b>关联事项ID：</b>${row.itemId || '-'}<br/>` +
    `<b>状态：</b>${statusOpts.find(s => s.val === row.topicStatus)?.label || '-'}<br/>` +
    `<b>前置意见：</b>${row.preCheckOpinion || '-'}<br/>` +
    `<b>关联会议：</b>${row.meetingId ? 'meeting#' + row.meetingId : '-'}`,
    '议题详情',
    { dangerouslyUseHTMLString: true, confirmButtonText: '关闭' }
  )
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`删除议题「${row.topicName}」？（仅待把关状态可删）`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteTopic(row.id); ElMessage.success('删除成功'); loadList()
    }).catch(() => {})
}

onMounted(loadList)
</script>

<style scoped>
.topic-page { padding: 16px; }
.search-card { margin-bottom: 12px; }
.table-card .toolbar { margin-bottom: 12px; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>