<template>
  <div class="workflow-designer-container">
    <div class="toolbar">
      <!-- 编辑模式：显示保存按钮 -->
      <el-button 
          type="primary" 
          @click="handleSave"
          v-if="action !== 'preview'"
      >
        <el-icon><Check /></el-icon>
        保存流程配置
      </el-button>
      <!-- 所有模式都显示：返回列表 -->
      <el-button @click="handleBack">
        <el-icon><Back /></el-icon>
        返回列表
      </el-button>
    </div>
    <div id="bpmn-canvas" class="bpmn-canvas"></div>
  </div>
</template>
<script setup>
import { onMounted, onBeforeUnmount, nextTick, markRaw } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getWorkflowDetail, updateWorkflowXml } from '@/api/wfDefinition'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'

const router = useRouter()
const route = useRoute()
const id = route.query.id
const action = route.query.action
let bpmnModeler = null

// 修复：正确的空BPMN模板（删掉omn多余n）
const emptyBpmnXml = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
  id="Definitions_1"
  targetNamespace="http://bpmn.io/bpmn">
  <process id="Process_1" name="业务流程" isExecutable="true">
    <startEvent id="StartEvent_1"/>
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="BPMNShape_StartEvent_1" bpmnElement="StartEvent_1">
        <omgdc:Bounds x="150" y="150" width="36" height="36"/>
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`

const initAndLoad = async () => {
  // 关键：markRaw 禁止Vue代理实例，解决拖拽失效
  bpmnModeler = markRaw(new BpmnModeler({
    container: '#bpmn-canvas',
    keyboard: { bindTo: document }
  }))

  try {
    let realXml = ''
    if (id) {
      const res = await getWorkflowDetail(id)
      realXml = res.data.processXml || ''
    }

    // 有真实XML加载真实，没有加载正确空模板
    if (realXml) {
      await bpmnModeler.importXML(realXml)
    } else {
      await bpmnModeler.importXML(emptyBpmnXml)
    }

    // 编辑模式强制开启所有编辑、拖拽
    const modeling = bpmnModeler.get('modeling')
    const dragging = bpmnModeler.get('dragging')
    modeling.setReadOnly(false)
    dragging.setOptions({ enabled: true })

    // 自适应画布
    bpmnModeler.get('canvas').zoom('fit-viewport')

    // 仅预览模式禁用拖拽
    if (action === 'preview') {
      dragging.setOptions({ enabled: false })
    }

  } catch (e) {
    console.error('加载失败：', e)
  }
}

const handleSave = async () => {
  try {
    const { xml } = await bpmnModeler.saveXML({ format: true })
    await updateWorkflowXml({ id, processXml: xml })
    ElMessage.success('保存成功！')
  } catch (err) {
    ElMessage.error('保存失败')
    console.error(err)
  }
}

const handleBack = () => {
  router.push('/system/wf/definition')
}

onMounted(async () => {
  await nextTick()
  initAndLoad()
})

onBeforeUnmount(() => {
  if (bpmnModeler) {
    bpmnModeler.destroy()
  }
})
</script>
<style scoped>
.workflow-designer-container {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
}
.toolbar {
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #eee;
}
.bpmn-canvas {
  flex: 1;
  height: calc(100vh - 60px);
}
</style>
<style>
.bjs-container {
  height: 100% !important;
}
</style>