<template>
  <div class="workflow-designer-container">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button @click="handleBack"><el-icon><Back /></el-icon>返回列表</el-button>
        <el-tag v-if="definition.status === 0" type="warning" size="large">草稿</el-tag>
        <el-tag v-else-if="definition.status === 1" type="success" size="large">已发布</el-tag>
        <el-tag v-else type="info" size="large">已停用</el-tag>
        <span class="version-tag">v{{ definition.version || 1 }}</span>
      </div>
      <div class="toolbar-right">
        <el-button @click="openPreview" :disabled="!definition.id">
          <el-icon><View /></el-icon>BPMN 预览
        </el-button>
        <el-button type="primary" @click="handleSave" v-if="action !== 'preview'">
          <el-icon><Check /></el-icon>保存配置
        </el-button>
        <el-button type="success" @click="handlePublish" v-if="action !== 'preview' && definition.status !== 1">
          <el-icon><Upload /></el-icon>发布流程
        </el-button>
        <el-button type="warning" @click="openTenantPublish" v-if="action !== 'preview' && definition.status === 1 && userStore.getIsPlatform">
          <el-icon><Promotion /></el-icon>发布到租户
        </el-button>
        <el-button @click="openTenantList" v-if="action !== 'preview' && definition.status === 1 && userStore.getIsPlatform">
          <el-icon><Share /></el-icon>租户分发
        </el-button>
      </div>
    </div>

    <div class="content-wrap">
      <!-- 左：定义信息表单 -->
      <el-card class="panel left-panel" shadow="never">
        <template #header><strong>流程基本信息</strong></template>
        <el-form :model="definition" label-width="90px" :disabled="action === 'preview'">
          <el-form-item label="流程名称"><el-input v-model="definition.processName" placeholder="如：三重一大审批" /></el-form-item>
          <el-form-item label="流程标识">
            <el-input v-model="definition.processKey" placeholder="自动生成，可自定义" />
            <div class="form-tip">唯一标识，建议英文+下划线</div>
          </el-form-item>
          <el-form-item label="业务类型" required>
            <el-select v-model="definition.businessType" placeholder="选择本流程服务于哪个业务" style="width: 100%">
              <el-option v-for="opt in businessTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <div class="form-tip">⚠️ 必填，业务侧 submit 时按此字段匹配流程</div>
          </el-form-item>
          <el-form-item label="关联表单">
            <el-select v-model="definition.formDefinitionId" placeholder="选择关联的表单（用于条件下拉）"
                       clearable style="width: 100%" :disabled="action === 'preview'">
              <el-option v-for="opt in formDefOptions" :key="opt.id"
                         :label="opt.formName + (opt.category ? ' [' + opt.category + ']' : '')"
                         :value="opt.id" />
            </el-select>
            <div class="form-tip">🔀 选了表单后，条件网关可下拉选表单字段 + 字典选项</div>
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="definition.category" filterable allow-create default-first-option
                       placeholder="可下拉选也可手动输入" style="width: 100%">
              <el-option v-for="opt in categoryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="描述"><el-input v-model="definition.description" type="textarea" :rows="3" placeholder="选填" /></el-form-item>
        </el-form>
      </el-card>

      <!-- 右：节点配置表格 -->
      <el-card class="panel right-panel" shadow="never">
        <template #header>
          <div class="panel-header">
            <strong>审批节点配置</strong>
            <el-button type="primary" link @click="addNode" v-if="action !== 'preview'">
              <el-icon><Plus /></el-icon>新增节点
            </el-button>
          </div>
        </template>

        <el-table :data="nodes" border size="small" :disabled="action === 'preview'" empty-text="请先新增一个 start 节点">
          <el-table-column label="#" width="50" type="index" />

          <el-table-column label="节点类型" width="110">
            <template #default="{ row }">
              <el-select v-model="row.nodeType" :disabled="action === 'preview'" size="small">
                <el-option label="开始节点" value="start" />
                <el-option label="审批节点" value="userTask" />
                <el-option label="网关" value="gateway" />
                <el-option label="结束节点" value="end" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="节点名称" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.nodeName" :disabled="action === 'preview'" size="small" placeholder="如：部门领导审批" />
            </template>
          </el-table-column>

          <el-table-column label="审批人类型" width="120">
            <template #default="{ row }">
              <el-select v-model="row.assigneeType" :disabled="action === 'preview' || row.nodeType !== 'userTask'" size="small">
                <el-option label="指定用户" value="user" />
                <el-option label="指定角色" value="role" />
                <el-option label="指定部门" value="dept" />
                <el-option label="发起人自己" value="initiator" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="审批人值" min-width="260">
            <template #default="{ row }">
              <!-- 未选 assigneeType 或 非 userTask：隐藏 -->
              <template v-if="row.nodeType !== 'userTask'">
                <span class="muted">—</span>
              </template>
              <!-- initiator：固定标签 -->
              <template v-else-if="row.assigneeType === 'initiator'">
                <el-tag type="info" size="small">发起人自己</el-tag>
              </template>
              <!-- user / role / dept：远程搜索下拉 -->
              <template v-else>
                <el-select
                  v-model="row._assigneeValueList"
                  multiple collapse-tags collapse-tags-tooltip
                  :remote="true"
                  :remote-method="(kw) => remoteSearch(kw, row)"
                  :filterable="true"
                  :loading="row._searchLoading"
                  :disabled="action === 'preview'"
                  size="small"
                  placeholder="输入关键字搜索" style="width: 100%">
                  <el-option
                    v-for="opt in row._assigneeOptions"
                    :key="opt.id"
                    :label="opt.label"
                    :value="opt.id" />
                </el-select>
              </template>
            </template>
          </el-table-column>

          <el-table-column label="审批类型" width="110">
            <template #default="{ row }">
              <el-select v-model="row.approvalType" :disabled="action === 'preview' || row.nodeType !== 'userTask'" size="small">
                <el-option label="或签(任一人通过)" value="OR" />
                <el-option label="会签(全部通过)" value="AND" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="签字时效(h)" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.timeoutHours" :min="0" :disabled="action === 'preview' || row.nodeType !== 'userTask'"
                               size="small" style="width:100%" />
            </template>
          </el-table-column>

          <!-- 网关类型列（仅网关节点可编辑） -->
          <el-table-column label="网关类型" width="120">
            <template #default="{ row }">
              <el-select v-if="row.nodeType === 'gateway'"
                         v-model="row.gatewayType" :disabled="action === 'preview'"
                         size="small" style="width:100%">
                <el-option label="排他(条件分支)" value="exclusive" />
                <el-option label="并行(全部分支)" value="parallel" />
              </el-select>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>

          <!-- 分支条件列：配在网关出边指向的「目标节点」上，不是网关自身 -->
          <el-table-column label="分支条件(SpEL)" min-width="240">
            <template #default="{ row }">
              <!-- gateway / start / end 节点不需要条件 -->
              <template v-if="row.nodeType === 'gateway' || row.nodeType === 'start' || row.nodeType === 'end'">
                <span class="muted">—</span>
              </template>
              <!-- userTask / 其他节点：如果它被某个网关指向下游，可以填条件 -->
              <template v-else>
                <div style="display: flex; gap: 4px; align-items: center;">
                  <el-input v-model="row.conditionExpr" :disabled="action === 'preview'"
                            size="small" style="flex:1"
                            placeholder="amount > 10000 或 #variables.amount > 10000" />
                  <el-button type="primary" link size="small"
                             :disabled="action === 'preview'"
                             @click="openFieldSelector(row)">
                    字段📋
                  </el-button>
                </div>
              </template>
            </template>
          </el-table-column>

          <el-table-column label="下一节点" width="200">
            <template #default="{ row }">
              <el-select v-model="row.nextNodeIds" :disabled="action === 'preview' || row.nodeType === 'end'"
                         multiple collapse-tags collapse-tags-tooltip size="small" placeholder="选下一节点">
                <el-option v-for="n in otherNodes(row.id)" :key="n.id" :label="n.nodeName + ' (' + typeLabel(n.nodeType) + ')'"
                           :value="String(n.id)" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="排序" width="80">
            <template #default="{ row, $index }">
              <div class="sort-btns">
                <el-button link size="small" :disabled="action === 'preview' || $index === 0" @click="moveUp($index)">↑</el-button>
                <el-button link size="small" :disabled="action === 'preview' || $index === nodes.length - 1" @click="moveDown($index)">↓</el-button>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" :disabled="action === 'preview'" @click="removeNode($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="hint">
          <div><strong>💡 基础链路</strong>：<code>start → userTask(部门领导) → userTask(分管领导) → end</code>。start/end 不需要配置审批人。</div>
          <div style="margin-top:8px"><strong>🔀 网关分支条件（SpEL）</strong>：</div>
          <ul style="margin:4px 0 0 20px;padding:0;line-height:1.8;">
            <li>⭐ <strong>条件配在「网关出边指向的目标节点」上</strong>，不是网关自身。例如网关 nextNodeIds 选了「审批A」和「审批B」，条件就填在审批A/审批B那两行</li>
            <li>留空 = 默认分支（所有有条件分支都不满足时走这里）</li>
            <li><strong>可用变量</strong>：业务 submit 时传的 JSON Map key（如 amount/dept）+ 内置 initiatorId / processKey</li>
            <li><strong>写法示例</strong>：<code>amount > 10000</code> 或 <code>#variables.amount > 10000</code>、<code>dept == 'finance'</code>、<code>amount > 10000 and initiatorId != 1</code></li>
            <li>并行网关（parallel）忽略所有条件，所有分支同时激活</li>
          </ul>
        </div>
      </el-card>
    </div>

    <!-- BPMN 只读预览弹窗 -->
    <el-dialog v-model="previewVisible" title="BPMN 只读预览" width="90%" top="3vh" :close-on-click-modal="true"
               @close="handlePreviewClose">
      <div id="bpmn-canvas" style="height: 60vh; border: 1px solid #eee; background:#fafafa;"></div>
    </el-dialog>

    <!-- 发布到租户弹框 -->
    <el-dialog v-model="tenantPublishVisible" title="发布到指定租户" width="500px">
      <el-form label-width="120px">
        <el-form-item label="目标租户">
          <el-select v-model="tenantPublishForm.tenantIds" multiple collapse-tags
                     :remote="true" :remote-method="searchTenant" :loading="tenantSearchLoading"
                     filterable style="width: 100%" placeholder="搜索并选择租户">
            <el-option v-for="t in tenantOptions" :key="t.id" :label="t.tenantName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="允许租户修改">
          <el-switch v-model="tenantPublishForm.allowEdit" />
          <span style="margin-left:10px;color:#999;font-size:12px">{{ tenantPublishForm.allowEdit ? '租户可复制到自己租户后自由编辑' : '租户只能使用不能修改' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tenantPublishVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPublishToTenants" :disabled="!tenantPublishForm.tenantIds?.length">确定发布</el-button>
      </template>
    </el-dialog>

    <!-- ⭐ 字段选择弹窗（Workflow Designer 条件网关用） -->
    <el-dialog v-model="formFieldSelectorVisible" title="从表单字段生成条件" width="600px" :close-on-click-modal="true">
      <div v-if="!definition.formDefinitionId" style="color:#999; text-align:center; padding:20px;">
        请先在左侧「关联表单」选择一个表单
      </div>
      <div v-else>
        <div style="margin-bottom:10px;">
          <el-tag type="info" size="small">当前表单共 {{ formFieldList.length }} 个字段</el-tag>
          <el-button link size="small" @click="loadFormFields" style="margin-left:10px;">🔄 重新加载</el-button>
        </div>
        <el-table :data="formFieldList" border stripe size="small" max-height="380">
          <el-table-column prop="fieldKey" label="字段标识" width="120" />
          <el-table-column prop="fieldName" label="字段名称" width="140" />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="fieldTypeTagType(row.fieldType)">{{ row.fieldType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="字典/选项">
            <template #default="{ row }">
              <el-tag v-if="row.dictTypeCode" size="small" type="warning">
                字典: {{ row.dictTypeCode }}
              </el-tag>
              <el-tag v-else-if="row.options" size="small" type="success">本地选项</el-tag>
              <span v-else style="color:#bbb">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" @click="insertFieldSpEL(row)">插入</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 租户分发记录弹框 -->
    <el-dialog v-model="tenantListVisible" title="租户分发记录" width="700px">
      <el-table :data="tenantDistList" border stripe>
        <el-table-column prop="tenantId" label="租户ID" width="80" />
        <el-table-column prop="definitionName" label="流程名称" min-width="150" />
        <el-table-column label="编辑权限" width="120">
          <template #default="{ row }">
            <el-tag :type="row.allowEdit ? 'success' : 'info'">{{ row.allowEdit ? '可复制编辑' : '仅使用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '有效' : '已撤回' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="receiveTime" label="接收时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" type="danger" link size="small" @click="handleRevoke(row)">撤回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, markRaw, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getWorkflowDetail, saveWorkflowAll, publishWorkflowDefinition, getBpmnXml, publishToTenants, getTenantDistribution, revokeTenantDistribution } from '@/api/wfDefinition'
import { getFormDefAllActive, getFormFieldList } from '@/api/form'
import { getDictDataByType } from '@/api/dictData'
import request from '@/utils/request'
import BpmnViewer from 'bpmn-js/lib/Viewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import { Promotion, Share } from '@element-plus/icons-vue'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const id = route.query.id || null
const action = route.query.action || 'edit'

const definition = reactive({
  id: null, processKey: '', processName: '', description: '', category: '',
  businessType: '', version: 1, status: 0, startNodeId: null, processXml: '',
  formDefinitionId: null
})

// ⭐ 关联表单的字段列表（Workflow Designer 条件下拉用）
const formDefOptions = ref([])       // 表单定义下拉选项
const formFieldList = ref([])        // 当前关联表单的字段元数据
const formFieldLoading = ref(false)
const formFieldSelectorVisible = ref(false)  // 字段选择弹窗可见性

// ⭐ 业务类型下拉选项（与后端 ThreeBigItemWorkflowBinder.PREFIX 对应）
const businessTypeOptions = [
  { label: '三重一大事项', value: 'threeBigItem' },
  { label: '三重一大会议', value: 'threeBigMeeting' },
  { label: '三重一大合同', value: 'threeBigContract' },
  { label: '通用审批', value: 'system' },
]

// ⭐ 分类下拉选项（预设常见值，允许自定义输入）
const categoryOptions = [
  { label: '三重一大审批', value: '三重一大审批' },
  { label: '公文审批', value: '公文审批' },
  { label: '请假出差', value: '请假出差' },
  { label: '采购报销', value: '采购报销' },
]
const nodes = ref([])
const previewVisible = ref(false)
let bpmnViewer = null

// ⭐ 租户分发相关状态
const tenantPublishVisible = ref(false)
const tenantListVisible = ref(false)
const tenantPublishForm = reactive({ tenantIds: [], allowEdit: false })
const tenantDistList = ref([])
const tenantOptions = ref([])
const tenantSearchLoading = ref(false)

// ========== 远程搜索（根据 assigneeType 切不同接口） ==========
const remoteSearch = async (keyword, row) => {
  if (row.nodeType !== 'userTask' || !row.assigneeType || row.assigneeType === 'initiator') return
  row._searchLoading = true
  try {
    let url = ''
    if (row.assigneeType === 'user') url = '/system/user/search'
    else if (row.assigneeType === 'role') url = '/system/role/search'
    else if (row.assigneeType === 'dept') url = '/system/dept/search'
    const res = await request({ url, method: 'get', params: { keyword: keyword || '', limit: 50 } })
    row._assigneeOptions = res.data || []
  } catch (e) {
    console.error('远程搜索失败', e)
  } finally {
    row._searchLoading = false
  }
}

// ========== 给每个节点初始化临时字段 ==========
const initNodeTempFields = (node) => {
  // ⭐ 新字段默认值 — 按节点类型区分
  if (!node.approvalType) node.approvalType = 'OR'
  if (node.timeoutHours == null) node.timeoutHours = 72
  // conditionExpr 只对 userTask 有意义（配在网关指向的目标节点上）
  if (!node.conditionExpr) node.conditionExpr = ''
  // gatewayType 只对 gateway 有意义
  if (node.nodeType === 'gateway') {
    if (!node.gatewayType) node.gatewayType = 'exclusive'
  } else {
    node.gatewayType = '' // 非网关节点清空，避免脏数据
  }

  // 1. assigneeValue: 逗号串 → 数字数组
  let list = []
  if (node.assigneeValue && node.assigneeType !== 'initiator') {
    list = node.assigneeValue.split(',').filter(s => s.trim() !== '').map(s => Number(s.trim()))
  }
  node._assigneeValueList = list
  node._assigneeOptions = []
  node._searchLoading = false

  // 2. ⭐ nextNodeIds: 逗号串 → 字符串数组（el-select multiple 期望数组，值是 String 类型）
  if (typeof node.nextNodeIds === 'string') {
    node.nextNodeIds = node.nextNodeIds.split(',').filter(s => s.trim() !== '').map(s => String(s.trim()))
  } else if (!Array.isArray(node.nextNodeIds)) {
    node.nextNodeIds = []
  }

  // 3. 监听 assigneeValueList 变化 → 同步回 assigneeValue 逗号串
  watch(() => node._assigneeValueList, (val) => {
    node.assigneeValue = (val || []).join(',')
  })

  // 4. 监听 assigneeType 变化 → 清空旧选择、清空选项
  watch(() => node.assigneeType, () => {
    node._assigneeValueList = []
    node._assigneeOptions = []
    node.assigneeValue = ''
  })

  return node
}

// ========== ⭐ 预加载已有审批人的 label（让 el-select 能显示文字而不是裸数字） ==========
const preloadAssigneeLabels = async () => {
  // 对每个 userTask 节点，如果已经有 assigneeValue（逗号串），自动调一次远程搜索把 label 拉回来
  for (const node of nodes.value) {
    if (node.nodeType !== 'userTask') continue
    if (!node.assigneeType || node.assigneeType === 'initiator') continue
    if (!node.assigneeValue && (!node._assigneeValueList || node._assigneeValueList.length === 0)) continue
    // 已经有 options 了就不重复拉
    if (node._assigneeOptions && node._assigneeOptions.length > 0) continue
    // 用一个空关键字搜索，后端 limit=50 足够覆盖常见场景
    try {
      await remoteSearch('', node)
    } catch (e) {
      console.warn('预加载审批人 label 失败', e)
    }
  }
}

// ========== 加载已有数据 ==========
const loadData = async () => {
  if (!id) return
  try {
    const res = await getWorkflowDetail(id)
    const d = res.data.definition
    Object.assign(definition, d)
    nodes.value = (res.data.nodes || []).map(n => initNodeTempFields(n))
    // ⭐ 节点加载完后，异步预拉取审批人 label（不阻塞页面渲染）
    preloadAssigneeLabels()
  } catch (e) {
    ElMessage.error('加载失败')
    console.error(e)
  }
}

// ========== 节点操作 ==========
const otherNodes = (curId) => nodes.value.filter(n => n.id !== curId)
const typeLabel = (t) => ({ start: '开始', userTask: '审批', gateway: '网关', end: '结束' }[t] || t)

const addNode = () => {
  const newId = Date.now() + Math.floor(Math.random() * 1000)
  const nodeTypes = ['start', 'userTask', 'gateway', 'end']
  const defaultType = nodeTypes[nodes.value.length === 0 ? 0 : (nodes.value.length === 1 ? 1 : Math.min(1, nodeTypes.length - 2))] // 第一个默认 start
  const newNode = {
    id: -newId, nodeKey: 'node_' + newId,
    nodeName: '', nodeType: defaultType,
    assigneeType: defaultType === 'userTask' ? 'initiator' : '',
    assigneeValue: '',
    nextNodeIds: [], sortOrder: nodes.value.length + 1,
    approvalType: 'OR', timeoutHours: 72,
    conditionExpr: '',
    // ⭐ gatewayType 只有 gateway 类型才需要
    gatewayType: defaultType === 'gateway' ? 'exclusive' : ''
  }
  nodes.value.push(initNodeTempFields(newNode))
}

const removeNode = (index) => {
  const removed = nodes.value.splice(index, 1)[0]
  // 连带把其他节点的 nextNodeIds 里对该节点的引用清掉
  for (const n of nodes.value) {
    if (!n.nextNodeIds) continue
    n.nextNodeIds = n.nextNodeIds.filter(nid => String(nid) !== String(removed.id))
  }
}

const moveUp = (i) => {
  if (i === 0) return
  const tmp = nodes.value[i - 1]
  nodes.value[i - 1] = nodes.value[i]
  nodes.value[i] = tmp
}
const moveDown = (i) => {
  if (i >= nodes.value.length - 1) return
  const tmp = nodes.value[i + 1]
  nodes.value[i + 1] = nodes.value[i]
  nodes.value[i] = tmp
}

// ========== 校验 ==========
const validate = () => {
  if (!definition.processName) { ElMessage.warning('请填写流程名称'); return false }
  if (definition.status === 1) {
    // 发布状态节点校验
    const userTaskCount = nodes.value.filter(n => n.nodeType === 'userTask').length
    if (nodes.value.length === 0) { ElMessage.warning('请先配置节点'); return false }
    if (userTaskCount === 0) { ElMessage.warning('至少要有一个审批节点(userTask)'); return false }
  }
  for (const n of nodes.value) {
    if (!n.nodeName) { ElMessage.warning('节点名称不能为空'); return false }
    if (n.nodeType === 'end') n.nextNodeIds = [] // end 节点不应有下一节点
  }
  return true
}

// ========== 保存 ==========
const handleSave = async () => {
  if (!validate()) return
  try {
    // ⭐ 显式组装要传给后端的节点对象（去掉 _ 开头的临时字段，显式同步 assigneeValue）
    const cleanNodes = nodes.value.map(n => {
      // 1. assigneeValue: 如果有 _assigneeValueList 就用它 join，否则直接用 assigneeValue
      let assigneeValue = n.assigneeValue || ''
      if (n.assigneeType && n.assigneeType !== 'initiator' && n._assigneeValueList) {
        assigneeValue = n._assigneeValueList.join(',')
      }
      // 2. nextNodeIds: el-select multiple 存的是数组，join 成逗号串
      let nextNodeIds = Array.isArray(n.nextNodeIds) ? n.nextNodeIds.join(',') : (n.nextNodeIds || '')

      // ⭐ conditionExpr 只给 userTask（网关指向的目标节点配条件）
      //    gateway 自身的 conditionExpr 清空（引擎不读网关自身条件）
      //    gatewayType 只给 gateway 节点
      const isGateway = n.nodeType === 'gateway'
      return {
        id: n.id,
        nodeKey: n.nodeKey,
        nodeName: n.nodeName,
        nodeType: n.nodeType,
        assigneeType: isGateway ? '' : n.assigneeType,       // 网关不是审批节点，清空 assignee
        assigneeValue: isGateway ? '' : assigneeValue,
        nextNodeIds: nextNodeIds,
        approvalType: n.approvalType,
        timeoutHours: n.timeoutHours,
        sortOrder: n.sortOrder,
        conditionExpr: isGateway ? '' : (n.conditionExpr || ''),  // ⭐ 网关自身清空条件
        gatewayType: isGateway ? (n.gatewayType || 'exclusive') : ''  // ⭐ 只有网关才发 gatewayType
      }
    })

    console.log('[Workflow Designer] save-all payload:', JSON.stringify({ nodes: cleanNodes }, null, 2))

    const payload = {
      definition: { ...definition },
      nodes: cleanNodes
    }
    const res = await saveWorkflowAll(payload)
    const newId = res.data
    if (definition.id == null) definition.id = newId
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
    console.error(e)
  }
}

// ========== 发布 ==========
const handlePublish = async () => {
  await handleSave()
  try {
    await publishWorkflowDefinition(definition.id)
    definition.status = 1
    ElMessage.success('发布成功')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '发布失败')
  }
}

// ========== BPMN 只读预览（⭐ 防重复创建 + 动态 destroy） ==========
const openPreview = async () => {
  if (!definition.id) { ElMessage.warning('请先保存后再预览'); return }
  previewVisible.value = true
  await nextTick()
  try {
    // ⭐ 先销毁旧实例 + 清空 DOM 里残留的 svg
    if (bpmnViewer) { bpmnViewer.destroy(); bpmnViewer = null }
    const container = document.getElementById('bpmn-canvas')
    if (container) container.innerHTML = ''

    const res = await getBpmnXml(definition.id)
    const xml = res.data
    bpmnViewer = markRaw(new BpmnViewer({ container: '#bpmn-canvas' }))
    await bpmnViewer.importXML(xml)
    bpmnViewer.get('canvas').zoom('fit-viewport')
  } catch (e) { console.error(e); ElMessage.error('BPMN 渲染失败: ' + (e?.response?.data?.msg || e.message)) }
}
// ⭐ 弹窗关闭时也销毁，避免叠加
const handlePreviewClose = () => {
  if (bpmnViewer) { bpmnViewer.destroy(); bpmnViewer = null }
  const container = document.getElementById('bpmn-canvas')
  if (container) container.innerHTML = ''
}

// ========== ⭐ 租户分发相关函数 ==========
const searchTenant = async (kw) => {
  tenantSearchLoading.value = true
  try {
    // ⭐ 实际后端接口：/tenant-auth/tenant/list
    const res = await request({ url: '/tenant-auth/tenant/list', method: 'get', params: { keyword: kw || '', pageSize: 50 } })
    const raw = res.data?.records || res.data || []
    tenantOptions.value = raw.map(t => ({ id: t.id, tenantName: t.tenantName || t.name || t.label }))
  } catch (e) { console.warn('搜索租户失败', e) } finally { tenantSearchLoading.value = false }
}

const openTenantPublish = async () => {
  tenantPublishForm.tenantIds = []
  tenantPublishForm.allowEdit = false
  tenantPublishVisible.value = true
  await searchTenant('')
}

const confirmPublishToTenants = async () => {
  try {
    await publishToTenants({
      definitionId: definition.id,
      tenantIds: tenantPublishForm.tenantIds,
      allowEdit: tenantPublishForm.allowEdit
    })
    ElMessage.success('已发布到选中租户')
    tenantPublishVisible.value = false
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '发布失败') }
}

const openTenantList = async () => {
  tenantListVisible.value = true
  try {
    const res = await getTenantDistribution(definition.id)
    tenantDistList.value = res.data || []
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '查询失败') }
}

const handleRevoke = async (row) => {
  try {
    await ElMessageBox.confirm(`确定撤回租户 #${row.tenantId} 的分发？`, '提示', { type: 'warning' })
    await revokeTenantDistribution({ definitionId: definition.id, tenantId: row.tenantId })
    ElMessage.success('已撤回')
    openTenantList() // 刷新
  } catch (e) { if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || '撤回失败') }
}
// ========== ⭐ 关联表单 + 字段元数据加载（Workflow Designer 条件下拉用） ==========
const loadFormDefOptions = async () => {
  try {
    const res = await getFormDefAllActive()
    formDefOptions.value = res.data || []
  } catch (e) { console.warn('加载表单定义下拉失败', e) }
}

const loadFormFields = async () => {
  if (!definition.formDefinitionId) { formFieldList.value = []; return }
  formFieldLoading.value = true
  try {
    const res = await getFormFieldList(definition.formDefinitionId)
    formFieldList.value = res.data || []
  } catch (e) {
    console.error('加载表单字段失败', e)
    ElMessage.error('加载表单字段失败')
  } finally {
    formFieldLoading.value = false
  }
}

// definition.formDefinitionId 变化 → 自动加载字段
watch(() => definition.formDefinitionId, () => {
  if (action === 'preview') return
  loadFormFields()
})

const openFieldSelector = async (row) => {
  // 前置校验：必须先选关联表单
  if (!definition.formDefinitionId) {
    ElMessage.warning('请先在左侧「关联表单」选择一个表单')
    return
  }
  // 每次点开都重新拉一次最新字段（防止用户改了表设计器后没刷新）
  await loadFormFields()
  formFieldSelectorVisible.value = true
  // 记录当前操作的是哪一行（conditionExpr 所在节点）
  formFieldSelectorVisible._targetRow = row
}

const fieldTypeTagType = (ft) => ({
  text: '', textarea: '', number: 'success', date: 'warning',
  select: 'warning', radio: 'warning', checkbox: 'warning', file: 'info'
}[ft] || '')

const insertFieldSpEL = async (field) => {
  const row = formFieldSelectorVisible._targetRow
  if (!row) return

  // 生成 SpEL 片段：优先用 fieldKey（业务 submit 时传的就是 key）
  // ⭐ 注意：不再加 // 中文注释 —— SpEL 解析器对非 ASCII 注释字符有 bug
  let spel = field.fieldKey

  // 如果字段关联了字典，把字典选项也打印出来方便用户写条件（直接显示在弹窗里，不写进 SpEL）
  if (field.dictTypeCode) {
    try {
      const dictRes = await getDictDataByType(field.dictTypeCode)
      const opts = (dictRes.data || []).map(d => `${d.dictLabel}(${d.dictValue})`).join(', ')
      ElMessage.info(`${field.fieldName} 字典选项: ${opts}`)
    } catch (e) { /* ignore */ }
    spel = `${field.fieldKey} == '值'`
  } else if (field.fieldType === 'number') {
    spel = `${field.fieldKey} > 0`
  } else if (field.fieldType === 'date') {
    spel = `${field.fieldKey} != null`
  } else {
    spel = `${field.fieldKey} == '值'`
  }

  row.conditionExpr = spel
  formFieldSelectorVisible.value = false
  ElMessage.success('已插入到分支条件')
}

const handleBack = () => router.push('/system/wf/definition')

onMounted(async () => {
  await loadFormDefOptions()
  await loadData()
})
onBeforeUnmount(() => { if (bpmnViewer) bpmnViewer.destroy() })
</script>

<style scoped>
.workflow-designer-container { height: 100vh; display: flex; flex-direction: column; background: #f5f7fa; }
.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; flex-shrink: 0;
}
.toolbar-left { display: flex; gap: 10px; align-items: center; }
.toolbar-right { display: flex; gap: 8px; }
.version-tag { color: #999; font-size: 13px; }
.content-wrap { flex: 1; display: flex; gap: 12px; padding: 12px; overflow: auto; }
.panel { background: #fff; }
.left-panel { width: 320px; flex-shrink: 0; height: fit-content; position: sticky; top: 12px; }
.right-panel { flex: 1; }
.panel-header { display: flex; justify-content: space-between; align-items: center; }
.form-tip { color: #999; font-size: 12px; margin-top: 4px; }
.sort-btns { display: flex; flex-direction: column; gap: 0; }
.hint { margin-top: 12px; padding: 10px 12px; background: #fdf6ec; border-radius: 4px; color: #8a6d3b; font-size: 13px; }
.hint code { background: #eee; padding: 0 4px; border-radius: 2px; }
.muted { color: #c0c4cc; }
</style>
<style>.bjs-container { height: 100% !important; }</style>