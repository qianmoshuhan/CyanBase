<template>
  <div class="myform-container">
    <!-- ════════ 卡片列表 ════════ -->
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span><el-icon style="margin-right:6px;"><Document /></el-icon>我的表单（{{ formList.length }}）</span>
          <el-button :icon="Refresh" @click="fetchForms">刷新</el-button>
        </div>
      </template>

      <div v-if="loading" style="text-align:center;padding:40px;">
        <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      </div>

      <el-empty v-else-if="!formList.length" description="当前租户暂无已发布表单" />

      <el-row v-else :gutter="16">
        <el-col
          v-for="f in formList"
          :key="f.id"
          :xs="24" :sm="12" :md="8" :lg="6" :xl="4"
          style="margin-bottom:16px;"
        >
          <el-card
            shadow="hover"
            class="form-card"
            :class="{ 'card-disabled': !f.hasBinding }"
            @click="f.hasBinding && openFillDialog(f)"
          >
            <div class="card-body">
              <div class="form-icon">
                <el-icon :size="28"><Document /></el-icon>
              </div>
              <div class="form-info">
                <div class="form-name" :title="f.formName">{{ f.formName }}</div>
                <div class="form-meta">
                  <el-tag v-if="f.category" size="small" type="info" effect="plain">{{ f.category }}</el-tag>
                  <el-tag size="small" effect="plain">v{{ f.version }}</el-tag>
                </div>
                <div class="form-update">更新：{{ formatTime(f.updateTime) }}</div>
              </div>
            </div>
            <div class="card-footer">
              <el-tag v-if="f.hasBinding" type="success" size="small">可填报</el-tag>
              <el-tag v-else type="warning" size="small" effect="plain">待绑定流程</el-tag>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- ════════ 填报 Dialog ════════ -->
    <el-dialog
      v-model="dialogVisible"
      :title="currentForm ? `填报：${currentForm.formName}` : '填报'"
      :width="isMobile ? '95%' : '720px'"
      top="5vh"
      destroy-on-close
    >
      <!-- ① 加载 match 中 -->
      <div v-if="matching" style="text-align:center;padding:40px;">
        <el-icon class="is-loading" :size="28"><Loading /></el-icon>
        <div style="margin-top:12px;color:#909399;">系统自动匹配流程中...</div>
      </div>

      <!-- ② match 失败 -->
      <div v-else-if="!matchedProcessId" style="text-align:center;padding:30px;">
        <el-icon :size="48" color="#ff7d00"><WarningFilled /></el-icon>
        <div style="margin-top:12px;color:#606266;">未匹配到可用流程，请联系管理员配置表单流程绑定</div>
      </div>

      <!-- ③ 表单渲染 -->
      <div v-else-if="currentForm">
        <div class="match-info">
          <el-alert
            :closable="false"
            type="success"
            show-icon
            :title="`已自动匹配流程 (processDefId: ${matchedProcessId})`"
          />
        </div>

        <div v-if="formLoading" style="text-align:center;padding:40px;">
          <el-icon class="is-loading" :size="28"><Loading /></el-icon>
          <div style="margin-top:8px;color:#909399;">加载表单中...</div>
        </div>

        <div v-else-if="formRules.length" style="padding:16px;background:#fafbfc;border-radius:6px;border:1px solid #ebeef5;">
          <form-create v-model:api="formApi" :rule="formRules" />
        </div>

        <div v-else style="padding:12px;color:#909399;font-size:13px;background:#f4f4f5;border-radius:4px;">
          ⚠️ 该表单布局为空，将以空表单模式发起
        </div>

        <el-form label-width="100px" style="margin-top:16px;">
          <el-form-item label="备注">
            <el-input v-model="remark" type="textarea" :rows="2" placeholder="选填" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit" :disabled="!matchedProcessId">
          提交发起
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Refresh, Loading, WarningFilled } from '@element-plus/icons-vue'
import { getMyFormList } from '@/api/form'
import { getFormDefinitionById } from '@/api/form'
import { matchProcess } from '@/api/binding'
import { startWorkflow } from '@/api/wfDefinition'
import { parseLayoutConfig } from '@/plugins/formCreate'

// ═══════ 响应式 ═══════
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

// 列表
const loading = ref(false)
const formList = ref([])

// Dialog
const dialogVisible = ref(false)
const matching = ref(false)
const submitting = ref(false)
const currentForm = ref(null)
const matchedProcessId = ref(null)
const formApi = ref(null)
const formRules = ref([])
const formLoading = ref(false)
const remark = ref('')

// ═══════ 加载表单列表 ═══════
const fetchForms = async () => {
  loading.value = true
  try {
    const res = await getMyFormList()
    formList.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    console.error('加载我的表单失败', e)
    ElMessage.error('加载表单列表失败')
  } finally {
    loading.value = false
  }
}

// ═══════ 打开填报 Dialog ═══════
const openFillDialog = async (form) => {
  currentForm.value = form
  matchedProcessId.value = null
  formRules.value = []
  formApi.value = null
  remark.value = ''
  dialogVisible.value = true

  // 1️⃣ 自动 match 流程
  matching.value = true
  try {
    const res = await matchProcess(form.id)
    const data = res.data || {}
    if (data.matched && data.processDefinitionId) {
      matchedProcessId.value = data.processDefinitionId
      // 2️⃣ 加载表单布局
      formLoading.value = true
      try {
        const fRes = await getFormDefinitionById(form.id)
        const def = fRes.data || fRes
        if (def.layoutConfig) {
          formRules.value = parseLayoutConfig(def.layoutConfig)
        }
      } catch (e) {
        console.warn('加载表单布局失败', e)
      } finally {
        formLoading.value = false
      }
    } else {
      ElMessage.warning('未匹配到可用流程')
    }
  } catch (e) {
    console.error('匹配流程失败', e)
    ElMessage.error('匹配流程失败')
  } finally {
    matching.value = false
  }
}

// ═══════ 关闭 ═══════
const closeDialog = () => {
  dialogVisible.value = false
  currentForm.value = null
  matchedProcessId.value = null
  formRules.value = []
  formApi.value = null
}

// ═══════ 提交发起 ═══════
const handleSubmit = async () => {
  if (!matchedProcessId.value) return

  // 表单校验
  if (formRules.value.length && formApi.value) {
    try {
      await formApi.value.validate()
    } catch (e) {
      ElMessage.warning('请完善必填项')
      return
    }
  }

  try {
    await ElMessageBox.confirm('确定提交发起该流程？', '发起流程',
      { confirmButtonText: '确定发起', cancelButtonText: '取消', type: 'info' })

    // 组装 variables
    let variables = {}
    if (formApi.value && formRules.value.length) {
      variables = (formApi.value.formData && formApi.value.formData()) || formApi.value.form || {}
    }
    if (remark.value.trim()) {
      variables._remark = remark.value.trim()
    }

    submitting.value = true
    await startWorkflow({
      definitionId: matchedProcessId.value,
      businessKey: '',
      variables: JSON.stringify(variables)
    })
    ElMessage.success('流程发起成功')
    closeDialog()
    fetchForms()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.msg || '发起失败')
    }
  } finally {
    submitting.value = false
  }
}

// 工具：格式化时间
const formatTime = (t) => {
  if (!t) return '-'
  return String(t).slice(0, 16).replace('T', ' ')
}

onMounted(fetchForms)
</script>

<style scoped>
.myform-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }

.form-card {
  cursor: pointer;
  transition: all 0.2s;
  height: 150px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.form-card:hover:not(.card-disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
  border-color: #1677ff;
}
.card-disabled {
  cursor: not-allowed;
  opacity: 0.6;
  border-color: #ebeef5;
}

.card-body {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.form-icon {
  width: 50px;
  height: 50px;
  border-radius: 8px;
  background: linear-gradient(135deg, #1677ff 0%, #4096ff 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.form-info { flex: 1; min-width: 0; }
.form-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.form-meta { margin-top: 6px; display: flex; gap: 6px; flex-wrap: wrap; }
.form-update { margin-top: 6px; font-size: 12px; color: #909399; }
.card-footer { text-align: right; }

.match-info { margin-bottom: 12px; }
</style>