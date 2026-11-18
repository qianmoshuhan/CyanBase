<template>
  <div class="bpmn-designer">
    <!-- 顶部工具栏 -->
    <div class="top-toolbar">
      <div class="toolbar-left">
        <el-button text @click="goBack">
          <el-icon><ArrowLeft /></el-icon>返回列表
        </el-button>
        <el-divider direction="vertical" />
        <el-input
          v-model="form.processName"
          placeholder="请输入流程名称"
          style="width: 220px"
          :disabled="loading"
        />
        <el-tag :type="form.status === 1 ? 'success' : 'warning'" class="status-tag">
          {{ form.status === 1 ? '已发布' : '草稿' }}
        </el-tag>
      </div>
      <div class="toolbar-right">
        <el-button @click="handleSave" :loading="saving" type="primary">
          <el-icon><Download /></el-icon>保存
        </el-button>
        <el-button
          v-if="form.status !== 1 && form.id"
          @click="handlePublish"
          :loading="publishing"
          type="success"
        >发布</el-button>
        <el-button @click="handleExportBpmn">
          <el-icon><Upload /></el-icon>导出 .bpmn
        </el-button>
      </div>
    </div>

    <div class="main-body">
      <!-- 中间：bpmn-js 画布 -->
      <div class="canvas-area">
        <div id="bpmn-canvas" class="bpmn-canvas" />
      </div>

      <!-- 右侧：自定义属性面板（Element Plus 手写，不依赖 properties-panel） -->
      <div class="props-panel">
        <div class="panel-section">
          <div class="panel-title">📋 基础信息</div>
          <el-form label-position="top" size="small">
            <el-form-item label="流程描述">
              <el-input v-model="form.description" type="textarea" :rows="2"
                placeholder="流程用途说明（选填）" />
            </el-form-item>
            <el-form-item label="流程分类">
              <el-input v-model="form.category" placeholder="如：行政 / 财务 / 人事" />
            </el-form-item>
            <el-form-item label="版本号">
              <el-input-number v-model="form.version" :min="1" :max="99" />
            </el-form-item>
            <!-- ⭐ 关联表单（流程级，存 cyan:formRef 扩展） -->
            <el-form-item label="关联表单">
              <el-select v-model="form.formDefinitionId" placeholder="选择关联表单（用于字段条件）"
                         clearable style="width:100%" @change="onFormRefChange">
                <el-option v-for="opt in formDefOptions" :key="opt.id"
                           :label="opt.formName + (opt.category ? ' [' + opt.category + ']' : '')"
                           :value="opt.id" />
              </el-select>
              <div class="form-tip" style="font-size:11px; color:#909399; margin-top:2px;">
                选了表单后，分支条件可下拉选表单字段（和自研流程对齐）
              </div>
            </el-form-item>
          </el-form>
        </div>

        <!-- 选中元素的专属属性 -->
        <div v-if="selectedElement" class="panel-section">
          <div class="panel-title">🔧 节点属性</div>
          <el-form label-position="top" size="small">
            <!-- 通用：节点名称 -->
            <el-form-item label="节点名称">
              <el-input v-model="elementProps.name" placeholder="点击画布元素后编辑"
                @change="onElementNameChange" />
            </el-form-item>
            <el-form-item label="节点ID">
              <el-input :model-value="selectedElement.id" disabled />
            </el-form-item>

            <!-- UserTask：审批人配置（核心！） -->
            <template v-if="selectedElement.type === 'bpmn:UserTask'">
              <el-divider content-position="left">审批人范围</el-divider>
              <div class="assignee-list">
                <div
                  v-for="(item, idx) in elementProps.assignees"
                  :key="idx"
                  class="assignee-item"
                >
                  <el-select
                    v-model="item.type"
                    placeholder="类型"
                    style="width: 120px"
                    size="small"
                  >
                    <el-option value="initiator" label="发起人自己" />
                    <el-option value="dept" label="部门" />
                    <el-option value="post" label="岗位" />
                    <el-option value="user" label="指定用户" />
                  </el-select>

                  <!-- 发起人：只显示标签 -->
                  <el-tag v-if="item.type === 'initiator'" type="info" class="flex-1">
                    发起人
                  </el-tag>

                  <!-- 部门：树形选择 -->
                  <el-tree-select
                    v-else-if="item.type === 'dept'"
                    v-model="item.value"
                    :data="deptTree"
                    node-key="nodeId"
                    :props="{ label: 'label', value: 'deptId', children: 'children', disabled: 'nodeDisabled' }"
                    filterable
                    placeholder="选择部门"
                    style="flex: 1"
                    size="small"
                    @change="(v) => { item.name = findDeptName(v) }"
                  />

                  <!-- 岗位：下拉 -->
                  <el-select
                    v-else-if="item.type === 'post'"
                    v-model="item.value"
                    :placeholder="item.value ? '岗位' : '选择岗位'"
                    filterable
                    style="flex: 1"
                    size="small"
                    @change="(v) => { const p = postList.find(x => String(x.id) === String(v)); item.name = p ? p.postName : '' }"
                  >
                    <el-option v-for="p in postList" :key="p.id" :value="String(p.id)"
                      :label="p.postName" />
                  </el-select>

                  <!-- 用户：搜索下拉 -->
                  <el-select
                    v-else-if="item.type === 'user'"
                    v-model="item.value"
                    filterable
                    remote
                    :remote-method="(q) => searchUsers(q)"
                    :loading="userLoading"
                    :placeholder="item.value ? '用户' : '搜索用户'"
                    style="flex: 1"
                    size="small"
                    @change="(v) => { const u = userOptions.find(x => String(x.id) === String(v)); item.name = u ? u.label : '' }"
                  >
                    <el-option v-for="u in userOptions" :key="u.id" :value="String(u.id)"
                      :label="u.label" />
                  </el-select>

                  <el-button link type="danger" size="small"
                    @click="elementProps.assignees.splice(idx, 1)">删除</el-button>
                </div>

                <el-button size="small" @click="addAssignee">+ 添加审批人</el-button>
              </div>

              <el-divider content-position="left">会签类型</el-divider>
              <el-form-item>
                <el-radio-group v-model="elementProps.signType" @change="persistElementProps">
                  <el-radio value="OR">或签（一人通过即可）</el-radio>
                  <el-radio value="AND">会签（所有人审批）</el-radio>
                </el-radio-group>
              </el-form-item>
            </template>

            <!-- SequenceFlow：连线条件（三列可视化，和自研流程对齐） -->
            <template v-if="selectedElement.type === 'bpmn:SequenceFlow' && !isDefaultFlow">
              <el-divider content-position="left">分支条件 (SpEL)</el-divider>
              <!-- 没有关联表单时提示 -->
              <div v-if="!form.formDefinitionId && !formRefLoading" style="color:#999; font-size:12px; padding:6px 0;">
                💡 请先在左侧「关联表单」选一个表单，才能看到字段下拉
              </div>
              <div v-else-if="formRefLoading" style="color:#999; font-size:12px;">加载字段中...</div>

              <!-- 三列条件编辑器 -->
              <div style="display:flex; gap:4px; align-items:center; margin-top:4px;">
                <!-- 字段下拉 -->
                <el-select v-model="elementProps._condField" size="small"
                           style="flex:1; min-width:110px"
                           placeholder="选字段" clearable filterable
                           @change="onCondFieldChange">
                  <el-option-group label="流程内置变量">
                    <el-option v-for="v in BUILTIN_VARS" :key="v.fieldKey"
                               :label="v.fieldName" :value="v.fieldKey" />
                  </el-option-group>
                  <el-option-group label="表单字段">
                    <el-option v-for="f in formFieldList" :key="f.fieldKey"
                               :label="f.fieldName" :value="f.fieldKey" />
                  </el-option-group>
                </el-select>
                <!-- 操作符 -->
                <el-select v-model="elementProps._condOp" size="small"
                           style="width:80px" placeholder="?"
                           @change="syncConditionExpr">
                  <el-option v-for="o in COND_OPS" :key="o.value"
                             :label="o.label" :value="o.value" />
                </el-select>
                <!-- 值列：动态切换 — submitterDeptId 走部门树 / select 走下拉 / 普通走输入框 -->
                <el-tree-select v-if="elementProps._condField === 'submitterDeptId'"
                                v-model="elementProps._condValue" size="small"
                                :data="deptTree" :props="{ label:'label', value:'deptId', children:'children' }"
                                node-key="deptId"
                                style="flex:1; min-width:110px"
                                placeholder="选部门" clearable check-strictly
                                @change="syncConditionExpr" />
                <el-select v-else-if="getSelectedCondField() && HAS_OPT_TYPES.includes(getSelectedCondField().fieldType)"
                           v-model="elementProps._condValue" size="small"
                           style="flex:1; min-width:110px"
                           placeholder="选值" clearable filterable
                           @change="syncConditionExpr">
                  <el-option v-for="opt in getFieldOptions(getSelectedCondField())"
                             :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
                <el-input v-else v-model="elementProps._condValue" size="small"
                          style="flex:1; min-width:110px"
                          placeholder="值" clearable @input="syncConditionExpr" />
              </div>
              <!-- SpEL 预览 + 描述 -->
              <div style="margin-top:6px;">
                <div style="font-size:11px; color:#909399;">
                  SpEL: <code style="color:#e6a23c;">{{ elementProps.conditionExpr || '(未填写)' }}</code>
                </div>
                <el-input v-model="elementProps.desc" size="small" style="margin-top:4px;"
                          placeholder="描述（如：金额 ≥ 10 万）"
                          @change="persistElementProps" />
              </div>
              <!-- 没关联表单时也允许手动输原生 SpEL -->
              <div v-if="!form.formDefinitionId" style="margin-top:6px;">
                <el-input v-model="elementProps.conditionExpr" size="small"
                          placeholder="或直接输入原生 SpEL（如：amount >= 100000）"
                          @change="persistElementProps" />
              </div>
            </template>
          </el-form>
        </div>

        <!-- 没有选中任何元素 -->
        <div v-else class="panel-section empty-hint">
          <el-empty description="点击画布上的节点/连线，在此编辑属性" :image-size="80" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, shallowRef, onMounted, onBeforeUnmount, markRaw, computed, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Download, Upload } from '@element-plus/icons-vue'

// ===== bpmn-js 核心 =====
import Modeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'


// ===== 汉化 translate 函数（内联 key-value，不依赖第三方 i18n 包）=====
// bpmn-js 实际传入的 key 格式 = 小写开头 + 空格分隔（如 "Append end event"）
const CN_MAP = {
  // ------ 画布工具栏（左上角工具条的 tooltip）------
  'Activate hand tool': '手型工具',
  'Activate lasso tool': '套索选择',
  'Activate create/remove space tool': '创建/删除空间',
  'Activate global connect tool': '全局连线工具',
  'Search in diagram': '在图中搜索',
  'Create start event': '创建开始事件',
  'Create intermediate/boundary event': '创建中间/边界事件',
  'Create end event': '创建结束事件',
  'Create gateway': '创建网关',
  'Create task': '创建任务',
  'Create data object reference': '创建数据对象',
  'Create data store reference': '创建数据存储',
  'Create expanded sub-process': '创建展开子流程',
  'Create pool/participant': '创建泳道/参与者',
  'Create group': '创建分组',
  // ------ 画布提示（hover 节点时弹出的气泡菜单）------
  'Append end event': '追加结束事件',
  'Append gateway': '追加网关',
  'Append task': '追加任务',
  'Append intermediate/boundary event': '追加中间/边界事件',
  'Connect to other element': '连接到其他元素',
  'Add text annotation': '添加文本标注',
  'Change element': '替换元素',
  // ------ 任务类型 ------
  'Task': '任务',
  'Service task': '服务任务',
  'Send task': '发送任务',
  'Receive task': '接收任务',
  'Manual task': '人工任务',
  'Business rule task': '业务规则任务',
  'Script task': '脚本任务',
  'Call activity': '调用活动',
  // ------ 子流程 ------
  'Sub-process (collapsed)': '子流程（折叠）',
  'Sub-process (expanded)': '子流程（展开）',
  'Ad-hoc sub-process (collapsed)': '临时子流程（折叠）',
  'Ad-hoc sub-process (expanded)': '临时子流程（展开）',
  // ------ 多实例 ------
  'Parallel multi-instance': '并行多实例',
  'Sequential multi-instance': '串行多实例',
  'Loop': '循环',
  // ------ 通用 ------
  'Delete': '删除',
  // ------ 以下是预扩展 key（可能被其他 bpmn-js 模块用到）------
  'Start event': '开始事件',
  'End event': '结束事件',
  'User task': '用户任务',
  'Exclusive gateway': '排他网关',
  'Parallel gateway': '并行网关',
  'Inclusive gateway': '包容网关',
  'Event-based gateway': '事件网关',
  'Complex gateway': '复杂网关',
  'Append start event': '追加开始事件',
  'Append user task': '追加用户任务',
  'Append service task': '追加服务任务',
  'Append exclusive gateway': '追加排他网关',
  'Append parallel gateway': '追加并行网关',
  'Append inclusive gateway': '追加包容网关',
  'Replace with start event': '替换为开始事件',
  'Replace with end event': '替换为结束事件',
  'Replace with user task': '替换为用户任务',
  'Replace with exclusive gateway': '替换为排他网关',
  'Replace with parallel gateway': '替换为并行网关',
  'Replace with inclusive gateway': '替换为包容网关',
  'Copy': '复制',
  'Paste': '粘贴',
  'Cut': '剪切',
  'Undo': '撤销',
  'Redo': '重做',
  'Align elements': '对齐元素',
  'Align left': '左对齐',
  'Align right': '右对齐',
  'Align top': '上对齐',
  'Align bottom': '下对齐',
  'Align center': '水平居中',
  'Align middle': '垂直居中',
  'Distribute elements horizontally': '水平分布',
  'Distribute elements vertically': '垂直分布',
  'Zoom in': '放大',
  'Zoom out': '缩小',
  'Zoom reset': '重置缩放',
  'Toggle grid': '显示网格',
  'Toggle snap': '开启吸附',
  'Properties panel': '属性面板',
  'BPMN 2.0': 'BPMN 2.0',
  // ------ 元素类型（内部 key）------
  'bpmn:Process': '流程',
  'bpmn:SubProcess': '子流程',
  'bpmn:StartEvent': '开始事件',
  'bpmn:EndEvent': '结束事件',
  'bpmn:UserTask': '用户任务',
  'bpmn:ServiceTask': '服务任务',
  'bpmn:ExclusiveGateway': '排他网关',
  'bpmn:ParallelGateway': '并行网关',
  'bpmn:InclusiveGateway': '包容网关',
  'bpmn:SequenceFlow': '连线',
  'bpmn:BusinessRuleTask': '业务规则任务',
  'bpmn:ScriptTask': '脚本任务',
  'bpmn:ReceiveTask': '接收任务',
  'bpmn:SendTask': '发送任务',
  'bpmn:ManualTask': '人工任务',
  'bpmn:CallActivity': '调用活动',
  'bpmn:EventBasedGateway': '事件网关',
  'bpmn:ComplexGateway': '复杂网关',
  'bpmn:DataObjectReference': '数据对象',
  'bpmn:DataStoreReference': '数据存储',
  'bpmn:TextAnnotation': '文本标注',
  'bpmn:Group': '分组',
  'bpmn:MessageFlow': '消息流',
  'bpmn:Association': '关联',
  'bpmn:IntermediateCatchEvent': '中间捕获事件',
  'bpmn:IntermediateThrowEvent': '中间抛出事件',
  'bpmn:BoundaryEvent': '边界事件'
}

// 收集未翻译 key（localStorage 持久化，便于一次性补齐 CN_MAP）
function collectUntranslated(key) {
  try {
    const raw = localStorage.getItem('bpmn_i18n_missing')
    const set = new Set(raw ? JSON.parse(raw) : [])
    if (!CN_MAP[key] && !set.has(key)) {
      set.add(key)
      localStorage.setItem('bpmn_i18n_missing', JSON.stringify([...set]))
      // eslint-disable-next-line no-console
      console.warn('[bpmn-i18n] ⚠️ 未翻译 key:', JSON.stringify(key), '| 累计未翻译:', set.size)
    }
  } catch (e) { /* ignore */ }
}

function translateCN(key, interpolations) {
  collectUntranslated(key)
  let result = CN_MAP[key] || key
  if (interpolations) {
    result = result.replace(/\{(.*?)\}/g, (_, k) => interpolations[k] ?? `{${k}}`)
  }
  return result
}

// ===== API =====
import {
  addBpmnDef, updateBpmnDef, publishBpmnDef,
  getBpmnDefById, getBpmnDefXml
} from '@/api/bpmnDefinition'
import { getOrgTree } from '@/api/org.js'
import { getAllPost } from '@/api/post.js'
import { searchUsersApi } from '@/api/user.js'
import { getFormDefAllActive, getFormFieldList } from '@/api/form.js'

// ===== 流程基础信息 =====
const route = useRoute()
const router = useRouter()
const saving = ref(false)
const publishing = ref(false)

const form = reactive({
  id: null,
  processName: '',
  processKey: '',
  description: '',
  category: '',
  version: 1,
  status: 0,
  bpmnXml: '',
  formDefinitionId: null,  // ⭐ 关联表单（流程级，存 cyan:formRef）
  formRefFormName: ''      // 回显用，formDefinitionId 对应的表单名
})

// ⭐ 条件编辑器辅助常量（和自研流程对齐）
const BUILTIN_VARS = [
  { fieldKey: 'submitterDeptId', fieldName: '提交人所属部门' }
]
const HAS_OPT_TYPES = ['select', 'radio', 'checkbox']  // 有选项的字段类型
const COND_OPS = [
  { value: '==', label: '等于' },
  { value: '!=', label: '不等于' },
  { value: '>', label: '大于' },
  { value: '>=', label: '大于等于' },
  { value: '<', label: '小于' },
  { value: '<=', label: '小于等于' }
]

// ===== bpmn-js Modeler 实例 =====
let modeler = null
const selectedElement = shallowRef(null)  // shallowRef：存 bpmn-js 原生对象，Vue 不深代理，避免 Proxy 属性类型冲突

// ===== 右侧属性面板：选中元素的 props =====
const elementProps = reactive({
  name: '',
  assignees: [],   // [{type:'dept'|'post'|'user'|'initiator', value:'', name:''}]
  signType: 'OR',  // OR=或签 AND=会签
  desc: '',
  conditionExpr: '',
  // ⭐ 条件编辑器三列辅助（和自研流程对齐）
  _condField: '',  // 选的字段 key（表单 fieldKey 或内置变量名）
  _condOp: '==',   // 操作符
  _condValue: ''   // 值
})

// ⭐ 表单字段下拉数据
const formDefOptions = ref([])   // 表单定义下拉选项
const formFieldList = ref([])    // 当前关联表单的字段元数据

const isDefaultFlow = computed(() => {
  if (!selectedElement.value) return false
  const src = selectedElement.value.source
  if (!src) return false
  const out = src.businessObject && src.businessObject.default
  return out === selectedElement.value.businessObject
})

// ===== 组织架构数据 =====
const deptTree = ref([])
const postList = ref([])
const userOptions = ref([])
const userLoading = ref(false)

// ================= 生命周期 =================

onMounted(async () => {
  await nextTick()
  // 1. 初始化 bpmn-js Modeler
  modeler = markRaw(new Modeler({
    container: '#bpmn-canvas',
    additionalModules: [
      { translate: ['value', translateCN] }  // 汉化（内联 CN_MAP）
    ],
    // 注：cyan:assignee 等自定义扩展类型无需注册 moddle descriptor
    // 因为它们存在于 extensionElements 里，moddle 会自动把未知扩展当作普通 XML 元素处理
    // 手动构建 { $type, ... } JSON 对象即可，保存/导入都会正常工作
  }))

  // 2. 监听选中事件（bpmn-js 原生对象用 markRaw 包，避免被 Vue Proxy 深度代理）
  modeler.on('selection.changed', ({ newSelection }) => {
    const el = newSelection && newSelection[0]
    if (el) {
      selectedElement.value = markRaw(el)
      readElementProps(el)
    } else {
      selectedElement.value = null
    }
  })

  // 3. 加载组织架构数据（属性面板下拉用）
  try {
    const [orgRes, postRes, formRes] = await Promise.all([
      getOrgTree(), getAllPost(), getFormDefAllActive()
    ])
    const raw = Array.isArray(orgRes?.data) ? orgRes.data : []
    // OrgNodeVO：nodeType='dept' 才可选，platform-root / tenant 标记 disabled
    const markDisabled = (nodes) => nodes.map(n => ({
      ...n, nodeDisabled: n.nodeType !== 'dept',
      children: n.children ? markDisabled(n.children) : []
    }))
    deptTree.value = markDisabled(raw)
    postList.value = Array.isArray(postRes?.data) ? postRes.data : []
    // ⭐ 表单定义下拉
    formDefOptions.value = Array.isArray(formRes?.data) ? formRes.data : []
  } catch (e) {
    console.warn('[设计器] 基础数据加载失败', e)
  }

  // 4. 根据路由参数决定：新建 or 编辑
  const { id, action } = route.query
  if (action === 'edit' && id) {
    await loadExistingDef(Number(id))
  } else {
    await newProcess()
  }
})

onBeforeUnmount(() => {
  if (modeler) {
    modeler.destroy()
    modeler = null
  }
})

// ================= bpmn-js 操作 =================

/** 新建空流程（Start → End 模板） */
async function newProcess() {
  const emptyXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xmlns:cyan="http://cyan.org/bpmn-ext"
                  id="Definitions_1">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="UserTask_1" name="审批节点">
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:endEvent id="EndEvent_1">
      <bpmn:incoming>Flow_2</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="UserTask_1" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="UserTask_1" targetRef="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="BPMNShape_StartEvent_1" bpmnElement="StartEvent_1">
        <dc:Bounds x="180" y="160" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_UserTask_1" bpmnElement="UserTask_1">
        <dc:Bounds x="280" y="148" width="100" height="60"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_EndEvent_1" bpmnElement="EndEvent_1">
        <dc:Bounds x="435" y="160" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="BPMNEdge_Flow_1" bpmnElement="Flow_1">
        <di:waypoint x="216" y="178"/>
        <di:waypoint x="280" y="178"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_Flow_2" bpmnElement="Flow_2">
        <di:waypoint x="380" y="178"/>
        <di:waypoint x="435" y="178"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
  await modeler.importXML(emptyXml)
  // 自动调整视角
  const canvas = modeler.get('canvas')
  canvas.zoom('fit-viewport', 'auto')
}

/** 加载已有流程定义（编辑模式） */
async function loadExistingDef(id) {
  try {
    const res = await getBpmnDefById(id)
    if (res.code !== 200 || !res.data) {
      ElMessage.error('流程定义不存在')
      goBack()
      return
    }
    const d = res.data
    form.id = d.id
    form.processName = d.processName
    form.processKey = d.processKey
    form.description = d.description || ''
    form.category = d.category || ''
    form.version = d.version || 1
    form.status = d.status || 0
    form.bpmnXml = d.bpmnXml

    if (!d.bpmnXml) {
      await newProcess()
      return
    }
    await modeler.importXML(d.bpmnXml)
    const canvas = modeler.get('canvas')
    canvas.zoom('fit-viewport', 'auto')
    // ⭐ 流程级：读 cyan:formRef → 回填 formDefinitionId + 异步加载字段
    readFormRefFromDefs()
    ElMessage.success('已加载：' + d.processName)
  } catch (e) {
    console.error('[设计器] 加载失败', e)
    ElMessage.error('加载失败')
  }
}

// ================= extensionElements 读写 =================

/** 从选中元素读取属性 → 填充右侧面板 */
function readElementProps(el) {
  // 通用：名称
  const bo = el.businessObject
  elementProps.name = bo.name || ''

  // 先清
  elementProps.assignees = []
  elementProps.signType = 'OR'
  elementProps.desc = ''
  elementProps.conditionExpr = ''
  elementProps._condField = ''
  elementProps._condOp = ''
  elementProps._condValue = ''

  // ⭐ SequenceFlow：conditionExpression 是 bpmn 标准属性（bo.conditionExpression），
  // 和 extensionElements 无关！必须在 extensionElements 早退之前处理
  if (el.type === 'bpmn:SequenceFlow') {
    const cond = bo.conditionExpression
    console.log('[read] 📐', el.id, '| bo.conditionExpression=', cond, '| cond?.body=', cond?.body)
    if (cond && cond.body) {
      elementProps.conditionExpr = cond.body
      parseConditionToCols(cond.body)
      console.log('[read] 🎯 parseConditionToCols →', { _condField: elementProps._condField, _condOp: elementProps._condOp, _condValue: elementProps._condValue })
    }
  }

  // extensionElements 的扩展节点（cyan:assignees / cyan:desc / cyan:signType）
  if (!bo.extensionElements) return
  const values = bo.extensionElements.values || []

  // UserTask 审批人
  if (el.type === 'bpmn:UserTask') {
    const assigneesNode = values.find(v => v.$type === 'cyan:assignees')
    // 嵌套子元素存在 $children 里（reader handleChild → element.$children.push）
    const childList = assigneesNode && assigneesNode.$children
    if (childList && childList.length > 0) {
      const mapped = childList.map(a => {
        const item = {
          type: a.type || '',
          value: a.value || '',
          name: a.name || ''
        }
        // ★ 类型适配：XML 读回的 value 全是 String，UI 组件期望特定类型
        if (item.type === 'dept' && item.value) {
          // el-tree-select 的 deptTree.id 是 number → 必须转 number 才能 node-key 匹配
          const num = Number(item.value)
          if (!isNaN(num)) item.value = num
        }
        if (item.type === 'post' && item.value) {
          item.value = String(item.value)   // el-select :value="String(p.id)"
        }
        if (item.type === 'user' && item.value) {
          item.value = String(item.value)   // remote el-select :value="String(u.id)"
        }
        return item
      })
      elementProps.assignees = mapped

      // ★ 用户/岗位回显：把已保存的选项注入各自的 options
      // ⚠️ 不要求 a.name 有值！老数据可能 name 为空（之前 @change 缺失导致）
      for (const a of mapped) {
        if (a.type === 'user' && a.value) {
          const id = Number(a.value)
          if (!userOptions.value.find(u => u.id === id)) {
            // 有 name 就用 name，没有就先用 value 占位
            userOptions.value.unshift({ id, label: a.name || String(a.value) })
          }
        }
        if (a.type === 'post' && a.value) {
          // postList 已经全部加载了，直接匹配即可
          const pid = String(a.value)
          const p = postList.value.find(x => String(x.id) === pid)
          if (p && !a.name) a.name = p.postName
        }
      }
      if (initialUserOpts.length > 0) {
        const existingKeys = new Set(userOptions.value.map(u => u.id))
        for (const opt of initialUserOpts) {
          if (!existingKeys.has(opt.id)) userOptions.value.unshift(opt)  // unshift 放最前
        }
      }
    }
    const signNode = values.find(v => v.$type === 'cyan:signType')
    if (signNode && signNode.$body) elementProps.signType = signNode.$body
  }

  // SequenceFlow 连线：desc 是 cyan:desc 扩展，走 extensionElements
  if (el.type === 'bpmn:SequenceFlow') {
    const descNode = values.find(v => v.$type === 'cyan:desc')
    if (descNode && descNode.$body) elementProps.desc = descNode.$body
  }
}



// cyan 扩展命名空间 URI（与 newProcess() 模板 xmlns:cyan="..." 保持一致）
const CYAN_NS_URI = 'http://cyan.org/bpmn-ext'

/**
 * 用 moddle.createAny() 创建 cyan:* 扩展对象
 * 这是 bpmn-moddle 专门为「未注册 descriptor 的未知扩展类型」提供的 API：
 *   moddle.createAny('cyan:assignee', 'http://cyan.org/bpmn-ext', { type, value, name })
 * 它会自动构建带 { isGeneric: true } 的 $descriptor，writer 序列化时走 generic 路径
 * reader 反序列化 XML 里的 <cyan:xxx> 节点也是用同一个 createAny，读写结构对称
 *
 * ⚠️ 不能用 moddle.create('cyan:xxx')  — 只认已注册 descriptor 的类型
 * ⚠️ 不能手动构建 { $type, ... } 纯 JSON — writer 拿不到 $descriptor，崩在 .isGeneric
 */
function makeCyanAny(moddle, localName, props) {
  // 空值保护：undefined / null / 空字符串 的属性不写进 moddle 对象
  // 否则 writer 会序列化成 value="undefined" 这种脏 attribute
  const clean = {}
  for (const [k, v] of Object.entries(props)) {
    if (k === '$children') clean[k] = v  // $children 是数组，总是保留
    else if (k === '$body' && v) clean[k] = String(v)
    else if (v !== undefined && v !== null && v !== '') clean[k] = String(v)
  }
  return moddle.createAny('cyan:' + localName, CYAN_NS_URI, clean)
}

/**
 * 右侧面板修改后，写回 extensionElements（混合策略）
 * ┌──────────────────────────────┬─────────────────────┬───────────────────────┐
 * │ 类型                          │ 策略                 │ 原因                  │
 * ├──────────────────────────────┼─────────────────────┼───────────────────────┤
 * │ bpmn:extensionElements       │ moddle.create()      │ BPMN 内置类型，writer │
 * │ bpmn:tFormalExpression       │ + modeling API       │ 要求它是真 moddle 对象 │
 * ├──────────────────────────────┼─────────────────────┼───────────────────────┤
 * │ cyan:* 自定义扩展             │ 手动 JSON 构建        │ 没注册 descriptor，   │
 * │ (在 extensionElements.values) │ { $type, ... }       │ moddle.create 会抛    │
 * │                              │                      │ unknown type          │
 * └──────────────────────────────┴─────────────────────┴───────────────────────┘
 */
function persistElementProps() {
  if (!selectedElement.value) return
  const modeling = modeler.get('modeling')
  const moddle = modeler.get('moddle')
  const bo = selectedElement.value.businessObject

  // 1. 元素名称
  modeling.updateProperties(selectedElement.value, { name: elementProps.name || '' })

  // 2. 用 moddle.createAny() 构建 cyan:* 扩展对象（带 $descriptor 的 generic 元素）
  const newExts = []

  if (selectedElement.value.type === 'bpmn:UserTask') {
    if (elementProps.assignees && elementProps.assignees.length > 0) {
      const children = elementProps.assignees.map(a =>
        makeCyanAny(moddle, 'assignee', { type: a.type, value: a.value, name: a.name || '' })
      )
      // ⚠️ 嵌套子元素必须放 $children，不是 assignee
      // parseGenericNsAttributes 把所有非 $ 开头的属性当 XML attribute → assignee 数组被转成 [object Object]
      // parseGenericContainments 才会处理 $children 递归序列化子元素
      newExts.push(makeCyanAny(moddle, 'assignees', { $children: children }))
    }
    newExts.push(makeCyanAny(moddle, 'signType', { $body: elementProps.signType }))
  }

  if (selectedElement.value.type === 'bpmn:SequenceFlow') {
    if (elementProps.desc) newExts.push(makeCyanAny(moddle, 'desc', { $body: elementProps.desc }))
    // conditionExpression 是 BPMN 内置类型
    // ⚠️ 标准 bpmn 类型用 body（正式属性），generic 扩展 createAny 才用 $body
    if (elementProps.conditionExpr) {
      console.log('[persist] ✅ 设置 conditionExpression =', elementProps.conditionExpr)
      modeling.updateModdleProperties(selectedElement.value, bo, {
        conditionExpression: moddle.create('bpmn:FormalExpression', {
          body: elementProps.conditionExpr,   // ✅ body，不是 $body
          language: 'spEL'
        })
      })
    } else if (bo.conditionExpression) {
      console.log('[persist] 🗑️ 清除 conditionExpression')
      modeling.updateModdleProperties(selectedElement.value, bo, { conditionExpression: undefined })
    } else {
      console.log('[persist] ⏭️ 无 conditionExpr，跳过')
    }
  }

  // 3. 处理 extensionElements（必须是 moddle 真对象，不能手动 JSON 构建）
  let extEls = bo.extensionElements

  if (!extEls && newExts.length > 0) {
    // bpmn-js 的 moddle 类型名首字母大写：ExtensionElements，不是 XML 小写的 extensionElements
    extEls = moddle.create('bpmn:ExtensionElements')
    modeling.updateModdleProperties(selectedElement.value, bo, { extensionElements: extEls })
  }

  if (extEls) {
    // 清理旧的 cyan:* 节点，splice 原地修改（不要替换 values 数组引用）
    const values = extEls.values
    if (values) {
      const filtered = values.filter(v => !String(v.$type || '').startsWith('cyan:'))
      values.splice(0, values.length, ...filtered, ...newExts)
    } else if (newExts.length > 0) {
      extEls.values = newExts
    }
  }
}

function onElementNameChange() { persistElementProps() }

// ================= ⭐ 关联表单 + 条件编辑器辅助函数 =================

const formRefLoading = ref(false)

/** 关联表单切换 → 加载 form_field 字段元数据 */
async function onFormRefChange(newId) {
  console.log('[BPMN-FormRef] onFormRefChange 触发, newId=', newId, '类型=', typeof newId)
  // 持久化 formRef 到 definitions.rootExtensionElements（流程级）
  persistFormRefToDefs()
  // 清空旧的条件下拉
  formFieldList.value = []
  // 清空当前选中元素的条件三列辅助
  elementProps._condField = ''
  elementProps._condOp = '=='
  elementProps._condValue = ''
  if (!newId) {
    console.log('[BPMN-FormRef] newId 为空，跳过加载')
    return
  }
  formRefLoading.value = true
  try {
    const res = await getFormFieldList(newId)
    console.log('[BPMN-FormRef] getFormFieldList 返回 res=', res)
    formFieldList.value = Array.isArray(res?.data) ? res.data : []
    console.log('[BPMN-FormRef] formFieldList 最终长度=', formFieldList.value.length,
                '内容=', formFieldList.value.map(f => f.fieldKey + ':' + f.fieldName))
  } catch (e) {
    console.warn('[BPMN-FormRef] 加载失败', e)
    formFieldList.value = []
  } finally {
    formRefLoading.value = false
  }
}

// ⭐ watch 兜底：和自研流程对齐，确保任何方式改变 formDefinitionId 都能触发加载
// （el-select @change 只在用户点击时触发，watch 覆盖所有场景：清空/程序赋值/编辑回显等）
watch(() => form.formDefinitionId, (newVal, oldVal) => {
  console.log('[BPMN-FormRef] watch 触发, formDefinitionId:', oldVal, '→', newVal)
  if (newVal === oldVal) return
  onFormRefChange(newVal)
})

/** 从 formFieldList 找当前选中的字段（用于判断类型、选项等） */
function getSelectedCondField() {
  const k = elementProps._condField
  if (!k) return null
  return formFieldList.value.find(f => f.fieldKey === k) || null
}

/** 下拉/单选/多选字段 → 解析 options JSON 或 dict */
function getFieldOptions(field) {
  if (!field) return []
  // 优先 options JSON
  if (field.options) {
    try {
      const opts = typeof field.options === 'string' ? JSON.parse(field.options) : field.options
      return opts.map(o => ({ label: o.label || o.text, value: String(o.value) }))
    } catch (e) { /* 忽略 */ }
  }
  return []
}

/** 字段下拉 change → 重置操作符 + 值，避免类型不匹配 */
function onCondFieldChange() {
  const f = elementProps._condField
  if (!f) {
    elementProps._condOp = ''
    elementProps._condValue = ''
  } else {
    elementProps._condOp = '=='
    elementProps._condValue = ''
  }
  syncConditionExpr()
}

/** 三列 → 自动拼 SpEL 表达式（和自研流程对齐，语法：field op value） */
function syncConditionExpr() {
  const f = elementProps._condField?.trim()
  const op = elementProps._condOp
  const v = elementProps._condValue
  if (!f || !op) {
    elementProps.conditionExpr = ''
    persistElementProps()
    return
  }
  // 数值字段 → 直接拼，不加引号（否则 SpEL 做字符串比较）
  const numField = formFieldList.value.find(x => x.fieldKey === f)?.fieldType === 'number'
  let valStr
  if (numField) {
    valStr = v === '' || v === null || v === undefined ? '0' : String(v)
  } else {
    valStr = `'${String(v ?? '').replace(/'/g, "''")}'`
  }
  elementProps.conditionExpr = `${f} ${op} ${valStr}`
  persistElementProps()
}

/** 反解析 SpEL → 三列辅助（回显用） */
function parseConditionToCols(expr) {
  elementProps._condField = ''
  elementProps._condOp = ''
  elementProps._condValue = ''
  if (!expr) return
  const m = expr.match(/^\s*([\w#.]+)\s*(==|!=|>=|<=|>|<)\s*'?([^']*)'?\s*$/)
  if (!m) return
  elementProps._condField = m[1]
  elementProps._condOp = m[2]
  // submitterDeptId → 部门 ID 要转 Number 才能 node-key 匹配
  if (m[1] === 'submitterDeptId') {
    const num = Number(m[3])
    elementProps._condValue = isNaN(num) ? m[3] : num
  } else {
    elementProps._condValue = m[3]
  }
}

// ================= ⭐ 流程级 cyan:formRef 读写（definitions.rootExtensionElements） =================

/** 流程级：把 cyan:formRef 写入 bpmn:definitions 的 extensionElements */
function persistFormRefToDefs() {
  if (!modeler) return
  const modeling = modeler.get('modeling')
  const moddle = modeler.get('moddle')
  // ⭐ bpmn-js 元素层级：canvas.rootElement = Process图形元素 → .businessObject = Process → .$parent = Definitions
  const canvas = modeler.get('canvas')
  const processEl = canvas.getRootElement()
  const processBo = processEl?.businessObject
  if (!processBo) return
  const definitions = processBo.$parent  // ✅ 这才是 bpmn:Definitions
  if (!definitions || definitions.$type !== 'bpmn:Definitions') return

  const newFormRefs = []
  if (form.formDefinitionId) {
    newFormRefs.push(makeCyanAny(moddle, 'formRef', {
      formDefinitionId: String(form.formDefinitionId),
      formName: form.formRefFormName || (formDefOptions.value.find(o => o.id === form.formDefinitionId)?.formName || '')
    }))
  }

  // bpmn 标准属性名：extensionElements，不是瞎编的 rootExtensionElements
  let extEls = definitions.extensionElements
  if (!extEls && newFormRefs.length > 0) {
    extEls = moddle.create('bpmn:ExtensionElements')
    modeling.updateModdleProperties(processEl, definitions, { extensionElements: extEls })
  }
  if (extEls) {
    const values = extEls.values || []
    const filtered = values.filter(v => v.$type !== 'cyan:formRef')
    if (newFormRefs.length > 0) {
      extEls.values = [...filtered, ...newFormRefs]
    } else if (filtered.length > 0) {
      extEls.values = filtered
    } else {
      modeling.updateModdleProperties(processEl, definitions, { extensionElements: undefined })
    }
  }
}

/** 流程级：从 bpmn:definitions.extensionElements 读 cyan:formRef */
function readFormRefFromDefs() {
  if (!modeler) return
  const canvas = modeler.get('canvas')
  const processEl = canvas.getRootElement()
  const definitions = processEl?.businessObject?.$parent
  if (!definitions || definitions.$type !== 'bpmn:Definitions') return
  const extEls = definitions.extensionElements
  if (!extEls?.values) return
  const formRef = extEls.values.find(v => v.$type === 'cyan:formRef')
  if (formRef && formRef.formDefinitionId) {
    const id = Number(formRef.formDefinitionId)
    form.formDefinitionId = id
    form.formRefFormName = formRef.formName || ''
    console.log('[BPMN-FormRef] ✅ 从 definitions.extensionElements 读到 formRef, id=', id)
    onFormRefChange(id)
  }
}

// ================= 审批人增删 =================

function addAssignee() {
  elementProps.assignees.push({ type: 'dept', value: '', name: '' })
  persistElementProps()
}

function findDeptName(deptId) {
  if (!deptId) return ''
  const walk = (nodes) => {
    for (const n of nodes || []) {
      // OrgNodeVO：dept 节点有 deptId，其他节点没有
      if (n.nodeType === 'dept' && String(n.deptId) === String(deptId)) return n.label || ''
      const r = walk(n.children)
      if (r) return r
    }
    return ''
  }
  return walk(deptTree.value) || ''
}

async function searchUsers(query) {
  if (!query) { userOptions.value = []; return }
  userLoading.value = true
  try {
    const res = await searchUsersApi({ keyword: query, limit: 30 })
    const results = Array.isArray(res?.data) ? res.data : []
    // ★ 不覆盖！保留已选中的 user（从 elementProps.assignees 里拿）
    const pickedIds = new Set()
    for (const a of elementProps.assignees) {
      if (a.type === 'user' && a.value) pickedIds.add(Number(a.value))
    }
    const pickedOpts = userOptions.value.filter(u => pickedIds.has(u.id))
    // 搜索结果放前面，已选中的保留在后面
    userOptions.value = [...results, ...pickedOpts]
  } catch (e) {
    console.warn('[用户搜索] 失败', e)
  } finally {
    userLoading.value = false
  }
}

// ================= 顶部按钮 =================

async function handleSave() {
  if (!form.processName) { ElMessage.warning('请填写流程名称'); return }

  saving.value = true
  try {
    console.log('[保存] 🎯 选中元素:', selectedElement.value?.type, selectedElement.value?.id)
    console.log('[保存] 🎯 form.formDefinitionId =', form.formDefinitionId)

    // ⭐ 流程级：先持久化 cyan:formRef
    persistFormRefToDefs()
    // 元素级：持久化选中节点的属性
    persistElementProps()
    await nextTick()

    // 导出当前 XML
    const { xml } = await modeler.saveXML({ format: true })
    console.log('[保存] 📄 saveXML 生成的 XML 片段（检查点）:')
    // 检查关键节点是否在 XML 中
    const checks = [
      'conditionExpression',
      'cyan:formRef',
      'cyan:desc',
      'cyan:signType'
    ]
    checks.forEach(c => {
      console.log('  ✅ ' + c + ' 存在?', xml.includes(c),
        c === 'conditionExpression' ? '（FormalExpression）' : '',
        c === 'cyan:formRef' ? '（流程级 formRef）' : '')
    })
    // 打印含 conditionExpression 的那段
    const condLines = xml.split('\n').filter(l => l.includes('conditionExpression') || l.includes('FormalExpression'))
    if (condLines.length) console.log('  📐 conditionExpression 行:', condLines.join('\n    '))
    form.bpmnXml = xml

    const payload = {
      id: form.id,
      processName: form.processName,
      processKey: form.processKey,
      description: form.description,
      category: form.category,
      version: form.version,
      status: form.status,
      bpmnXml: xml
    }

    let res
    if (form.id) {
      res = await updateBpmnDef(payload)
    } else {
      res = await addBpmnDef(payload)
      if (res.code === 200 && res.data) {
        form.id = res.data  // 回填新 ID
      }
    }

    if (res.code === 200) {
      ElMessage.success('保存成功')
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    console.error('[保存] 异常', e)
    ElMessage.error('保存失败：' + (e?.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

async function handlePublish() {
  if (!form.id) { ElMessage.warning('请先保存再发布'); return }
  try {
    await ElMessageBox.confirm(
      '发布后流程状态变更为「已发布」。本期只做状态标记，不接入运行引擎。',
      '确认发布', { type: 'warning' }
    )
    publishing.value = true
    const res = await publishBpmnDef(form.id)
    if (res.code === 200) {
      form.status = 1
      ElMessage.success('发布成功')
    } else {
      ElMessage.error(res.msg || '发布失败')
    }
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  } finally {
    publishing.value = false
  }
}

async function handleExportBpmn() {
  try {
    const { xml } = await modeler.saveXML({ format: true })
    const blob = new Blob([xml], { type: 'application/xml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${form.processKey || 'bpmn_process'}_v${form.version || 1}.bpmn`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出 .bpmn 文件')
  } catch (e) {
    console.error('[导出] 失败', e)
    ElMessage.error('导出失败')
  }
}

function goBack() {
  router.push('/workflow/bpmn/definition')
}

// watch：画布元素名称变化时同步到右侧面板显示（可选增强）
</script>

<style>
/* bpmn-js 默认样式（全局引入，确保画布正确渲染） */
@import 'bpmn-js/dist/assets/diagram-js.css';
@import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';

/* 隐藏画布右下角的 bpmn.io 标志（动态 DOM，必须在非 scoped 块里） */
.bjs-powered-by { display: none !important; }
</style>

<style scoped>
.bpmn-designer {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #fff;
  margin: -20px;  /* 抵消 content padding */
}
.top-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #fafafa;
}
.toolbar-left { display: flex; align-items: center; gap: 4px; }
.toolbar-right { display: flex; align-items: center; gap: 8px; }
.status-tag { margin-left: 8px; }

.main-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.canvas-area {
  flex: 1;
  position: relative;
  background: #f5f7fa;
}
.bpmn-canvas {
  width: 100%;
  height: 100%;
}

.props-panel {
  width: 320px;
  border-left: 1px solid #e4e7ed;
  background: #fff;
  overflow-y: auto;
  padding: 12px;
  flex-shrink: 0;
}
.panel-section { margin-bottom: 16px; }
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid #f0f0f0;
}
.assignee-list { display: flex; flex-direction: column; gap: 6px; }
.assignee-item {
  display: flex;
  gap: 6px;
  align-items: center;
}
.flex-1 { flex: 1; }
.empty-hint { padding: 20px 0; }
</style>