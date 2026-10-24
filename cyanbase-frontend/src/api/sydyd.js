import request from '@/utils/request'

// ========== 采购事项 ==========

export function getItemList(params) {
  return request({
    url: '/tenant/sydyd/item/list',
    method: 'get',
    params
  })
}

export function getItemDetail(id) {
  return request({
    url: `/tenant/sydyd/item/${id}`,
    method: 'get'
  })
}

export function createItem(data) {
  return request({
    url: '/tenant/sydyd/item',
    method: 'post',
    data
  })
}

export function updateItem(data) {
  return request({
    url: '/tenant/sydyd/item',
    method: 'put',
    data
  })
}

export function deleteItem(id) {
  return request({
    url: `/tenant/sydyd/item/${id}`,
    method: 'delete'
  })
}

export function submitItem(id) {
  return request({
    url: `/tenant/sydyd/item/${id}/submit`,
    method: 'put'
  })
}

/** 提交审批 + 指定流程定义 */
export function submitItemWithDefinition(id, definitionId) {
  return request({
    url: `/tenant/sydyd/item/${id}/submit`,
    method: 'put',
    params: { definitionId }
  })
}

export function withdrawItem(id) {
  return request({
    url: `/tenant/sydyd/item/${id}/withdraw`,
    method: 'put'
  })
}

// ========== 会议 ==========

export function getMeetingList(params) {
  return request({
    url: '/tenant/sydyd/meeting/list',
    method: 'get',
    params
  })
}

export function getMeetingDetail(id) {
  return request({
    url: `/tenant/sydyd/meeting/${id}`,
    method: 'get'
  })
}

export function getMeetingTopics(id) {
  return request({
    url: `/tenant/sydyd/meeting/${id}/topics`,
    method: 'get'
  })
}

export function createMeeting(data) {
  return request({
    url: '/tenant/sydyd/meeting',
    method: 'post',
    data
  })
}

export function updateMeeting(data) {
  return request({
    url: '/tenant/sydyd/meeting',
    method: 'put',
    data
  })
}

export function deleteMeeting(id) {
  return request({
    url: `/tenant/sydyd/meeting/${id}`,
    method: 'delete'
  })
}

export function addTopicsToMeeting(id, topicIds) {
  return request({
    url: `/tenant/sydyd/meeting/${id}/topics`,
    method: 'post',
    data: topicIds
  })
}

export function startMeeting(id) {
  return request({
    url: `/tenant/sydyd/meeting/${id}/start`,
    method: 'put'
  })
}

export function finishMeeting(id) {
  return request({
    url: `/tenant/sydyd/meeting/${id}/finish`,
    method: 'put'
  })
}

export function voteMeeting(id, votes) {
  return request({
    url: `/tenant/sydyd/meeting/${id}/vote`,
    method: 'post',
    data: votes
  })
}

export function archiveMeeting(id) {
  return request({
    url: `/tenant/sydyd/meeting/${id}/archive`,
    method: 'put'
  })
}

// ========== 实施 ==========

export function getExecuteList(params) {
  return request({
    url: '/tenant/sydyd/execute/list',
    method: 'get',
    params
  })
}

export function getExecuteDetail(id) {
  return request({
    url: `/tenant/sydyd/execute/${id}`,
    method: 'get'
  })
}

export function createExecute(data) {
  return request({
    url: '/tenant/sydyd/execute',
    method: 'post',
    data
  })
}

export function updateExecute(data) {
  return request({
    url: '/tenant/sydyd/execute',
    method: 'put',
    data
  })
}

export function deleteExecute(id) {
  return request({
    url: `/tenant/sydyd/execute/${id}`,
    method: 'delete'
  })
}

export function startExecute(id) {
  return request({
    url: `/tenant/sydyd/execute/${id}/start`,
    method: 'put'
  })
}

export function updateExecuteProgress(id, progress, progressDetail) {
  return request({
    url: `/tenant/sydyd/execute/${id}/progress`,
    method: 'put',
    params: { progress, progressDetail }
  })
}

export function finishExecute(id, progressDetail, evidenceFile) {
  return request({
    url: `/tenant/sydyd/execute/${id}/finish`,
    method: 'put',
    params: { progressDetail, evidenceFile }
  })
}

export function archiveExecute(id) {
  return request({
    url: `/tenant/sydyd/execute/${id}/archive`,
    method: 'put'
  })
}

// ========== 三重一大清单（事项填报下拉） ==========
export function getThreeBigListAll() {
  return request({
    url: '/tenant/sydyd/list/list',
    method: 'get',
    params: { pageNum: 1, pageSize: 100, status: 0 }
  })
}

// ========== 制度管理 ==========
export function getSystemList(params) {
  return request({ url: '/tenant/sydyd/system/list', method: 'get', params })
}
export function getAllSystems() {
  return request({ url: '/tenant/sydyd/system/all', method: 'get' })
}
export function getSystemDetail(id) {
  return request({ url: `/tenant/sydyd/system/${id}`, method: 'get' })
}
export function createSystem(data) {
  return request({ url: '/tenant/sydyd/system', method: 'post', data })
}
export function updateSystem(data) {
  return request({ url: '/tenant/sydyd/system', method: 'put', data })
}
export function deleteSystem(id) {
  return request({ url: `/tenant/sydyd/system/${id}`, method: 'delete' })
}
export function enableSystem(id) {
  return request({ url: `/tenant/sydyd/system/${id}/enable`, method: 'put' })
}
export function disableSystem(id) {
  return request({ url: `/tenant/sydyd/system/${id}/disable`, method: 'put' })
}

// ========== 清单管理 ==========
export function getListPage(params) {
  return request({ url: '/tenant/sydyd/list/list', method: 'get', params })
}
export function getListBySystem(systemId) {
  return request({ url: `/tenant/sydyd/list/bySystem/${systemId}`, method: 'get' })
}
export function getListDetail(id) {
  return request({ url: `/tenant/sydyd/list/${id}`, method: 'get' })
}
export function createList(data) {
  return request({ url: '/tenant/sydyd/list', method: 'post', data })
}
export function updateList(data) {
  return request({ url: '/tenant/sydyd/list', method: 'put', data })
}
export function deleteList(id) {
  return request({ url: `/tenant/sydyd/list/${id}`, method: 'delete' })
}

// ========== 议题管理 ==========
export function getTopicList(params) {
  return request({ url: '/tenant/sydyd/topic/list', method: 'get', params })
}
export function getTopicDetail(id) {
  return request({ url: `/tenant/sydyd/topic/${id}`, method: 'get' })
}
export function generateTopicFromItem(itemId) {
  return request({ url: `/tenant/sydyd/topic/generate/${itemId}`, method: 'post' })
}
export function batchGenerateTopic(itemIds) {
  return request({ url: '/tenant/sydyd/topic/batchGenerate', method: 'post', data: itemIds })
}
export function updateTopic(data) {
  return request({ url: '/tenant/sydyd/topic', method: 'put', data })
}
export function preCheckPass(id, opinion) {
  return request({ url: `/tenant/sydyd/topic/${id}/preCheckPass`, method: 'put', params: { opinion } })
}
export function preCheckReject(id, opinion) {
  return request({ url: `/tenant/sydyd/topic/${id}/preCheckReject`, method: 'put', params: { opinion } })
}
export function deleteTopic(id) {
  return request({ url: `/tenant/sydyd/topic/${id}`, method: 'delete' })
}