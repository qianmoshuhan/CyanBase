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

/** 我的待办 */
export function getMyTodoList(params) {
  return request({
    url: '/workflow/instance/my-todo',
    method: 'get',
    params
  })
}

/** 我的已办 */
export function getMyDoneList(params) {
  return request({
    url: '/workflow/instance/my-done',
    method: 'get',
    params
  })
}

/** 我发起的流程 */
export function getMyInitiatedList(params) {
  return request({
    url: '/workflow/instance/my-initiated',
    method: 'get',
    params
  })
}

/** 发起流程 */
export function startWorkflow(data) {
  return request({
    url: '/workflow/instance/start',
    method: 'post',
    data
  })
}

/**
 * 审批流程（通过/驳回）
 * @param {Object} data - { instanceId, action: 'approve'|'reject', comment }
 */
export function approveWorkflow(data) {
  return request({
    url: '/workflow/approve/process',
    method: 'post',
    data
  })
}

/** 获取流程实例详情 */
export function getInstanceById(id) {
  return request({
    url: `/workflow/instance/${id}`,
    method: 'get'
  })
}

/** 获取审批历史 */
export function getApproveList(instanceId) {
  return request({
    url: `/workflow/approve/list/${instanceId}`,
    method: 'get'
  })
}

/** 取消流程 */
export function cancelWorkflow(id) {
  return request({
    url: `/workflow/instance/cancel/${id}`,
    method: 'put'
  })
}

/** ⭐ 手动删除已撤销/已驳回的流程实例 */
export function deleteInstance(id) {
  return request({
    url: `/workflow/instance/${id}`,
    method: 'delete'
  })
}

/** 查询所有已发布流程（发起流程时下拉选择） */
export function getPublishedWorkflowList(params) {
  return request({
    url: '/workflow/definition/published',
    method: 'get',
    params
  })
}

/** 获取定义详情（definition + nodes） */
export function getWorkflowDetail(id) {
  return request({
    url: '/workflow/definition/' + id,
    method: 'get'
  })
}

/** 保存流程定义 + 节点配置（核心接口，事务性一起存） */
export function saveWorkflowAll(data) {
  return request({
    url: '/workflow/definition/save-all',
    method: 'post',
    data
  })
}

/** ⭐ 获取 BPMN 2.0 XML（只读预览用，后端动态生成） */
export function getBpmnXml(definitionId) {
  return request({
    url: `/workflow/definition/${definitionId}/bpmn`,
    method: 'get'
  })
}

/** ⭐ 获取流程实例轨迹（高亮用：已走/当前/驳回节点 keys + 审批记录） */
export function getInstanceTrail(instanceId) {
  return request({
    url: `/workflow/instance/${instanceId}/trail`,
    method: 'get'
  })
}

/** ⭐ 动态加签 */
export function addSigner(data) {
  return request({
    url: '/workflow/approve/add-signer',
    method: 'post',
    data
  })
}

/** ⭐ 转办 */
export function transferApprover(data) {
  return request({
    url: '/workflow/approve/transfer',
    method: 'post',
    data
  })
}

/** ⭐ 发布流程到指定租户 */
export function publishToTenants(data) {
  return request({
    url: '/workflow/definition/publish-to-tenants',
    method: 'post',
    data
  })
}

/** ⭐ 查询定义的租户分发记录 */
export function getTenantDistribution(definitionId) {
  return request({
    url: `/workflow/definition/${definitionId}/tenant-distribution`,
    method: 'get'
  })
}

/** ⭐ 撤回某租户的分发 */
export function revokeTenantDistribution(data) {
  return request({
    url: '/workflow/definition/revoke-tenant',
    method: 'post',
    data
  })
}