import request from '@/utils/request'

/**
 * 获取流程定义列表
 */
export function getWorkflowDefinitionList(params) {
  return request({
    url: '/workflow/definition/list',
    method: 'get',
    params
  })
}

/**
 * 获取流程定义详情
 */
export function getWorkflowDefinitionById(id) {
  return request({
    url: `/workflow/definition/${id}`,
    method: 'get'
  })
}

/**
 * 新增流程定义
 */
export function addWorkflowDefinition(data) {
  return request({
    url: '/workflow/definition',
    method: 'post',
    data
  })
}

/**
 * 编辑流程定义
 */
export function updateWorkflowDefinition(data) {
  return request({
    url: '/workflow/definition',
    method: 'put',
    data
  })
}

/**
 * 删除流程定义
 */
export function deleteWorkflowDefinition(id) {
  return request({
    url: `/workflow/definition/${id}`,
    method: 'delete'
  })
}

/**
 * 发布流程定义
 */
export function publishWorkflowDefinition(id) {
  return request({
    url: `/workflow/definition/publish/${id}`,
    method: 'put'
  })
}

/**
 * 获取我的待办列表
 */
export function getMyTodoList(params) {
  return request({
    url: '/workflow/instance/todo/list',
    method: 'get',
    params
  })
}

/**
 * 获取我的已办列表
 */
export function getMyDoneList(params) {
  return request({
    url: '/workflow/instance/done/list',
    method: 'get',
    params
  })
}

/**
 * 发起流程
 */
export function startWorkflow(data) {
  return request({
    url: '/workflow/instance/start',
    method: 'post',
    data
  })
}

/**
 * 审批流程
 */
export function approveWorkflow(data) {
  return request({
    url: '/workflow/approve',
    method: 'post',
    data
  })
}

// 获取流程详情（加载XML用）
export function getWorkflowDetail(id) {
  return request({
    url: '/workflow/definition/' + id,
    method: 'get'
  })
}

// 仅保存流程XML（设计器专用）
export function updateWorkflowXml(data) {
  return request({
    url: '/workflow/definition',
    method: 'put',
    data
  })
}