import request from '@/utils/request'

/**
 * BPMN 可视化流程定义 API
 * 路径: /workflow/bpmn/definition（归属 workflow 子域，与 /workflow/definition 区分）
 */

// 分页列表
export function getBpmnDefList(params) {
  return request({ url: '/workflow/bpmn/definition/list', method: 'get', params })
}

// 详情（含完整 bpmn_xml）
export function getBpmnDefById(id) {
  return request({ url: `/workflow/bpmn/definition/${id}`, method: 'get' })
}

// 仅拉取 BPMN XML（画布恢复专用）
export function getBpmnDefXml(id) {
  return request({ url: `/workflow/bpmn/definition/${id}/xml`, method: 'get', responseType: 'text' })
}

// 新增
export function addBpmnDef(data) {
  return request({ url: '/workflow/bpmn/definition', method: 'post', data })
}

// 更新（含 bpmn_xml）
export function updateBpmnDef(data) {
  return request({ url: '/workflow/bpmn/definition', method: 'put', data })
}

// 逻辑删除
export function deleteBpmnDef(id) {
  return request({ url: `/workflow/bpmn/definition/${id}`, method: 'delete' })
}

// 发布（0→1，本期只标记状态）
export function publishBpmnDef(id) {
  return request({ url: `/workflow/bpmn/definition/publish/${id}`, method: 'put' })
}