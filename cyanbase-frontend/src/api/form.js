import request from '@/utils/request'

/**
 * 获取表单定义列表
 */
export function getFormDefinitionList(params) {
  return request({
    url: '/form/definition/list',
    method: 'get',
    params
  })
}

/**
 * 获取表单定义详情
 */
export function getFormDefinitionById(id) {
  return request({
    url: `/form/definition/${id}`,
    method: 'get'
  })
}

/**
 * 新增表单定义
 */
export function addFormDefinition(data) {
  console.log('API call addFormDefinition with data:', data) 
  return request({
    url: '/form/definition',
    method: 'post',
    data
  })
}

/**
 * 编辑表单定义
 */
export function updateFormDefinition(data) {
  console.log('API call updateFormDefinition with data:', data)
  return request({
    url: '/form/definition',
    method: 'put',
    data
  })
}

/**
 * 删除表单定义
 */
export function deleteFormDefinition(id) {
  return request({
    url: `/form/definition/${id}`,
    method: 'delete'
  })
}

/**
 * 发布表单定义
 */
export function publishFormDefinition(id) {
  return request({
    url: `/form/definition/publish/${id}`,
    method: 'put'
  })
}

/**
 * 提交表单数据
 */
export function submitFormData(data) {
  return request({
    url: '/form/data/submit',
    method: 'post',
    data
  })
}

/**
 * 获取表单数据列表
 */
export function getFormDataList(params) {
  return request({
    url: '/form/data/list',
    method: 'get',
    params
  })
}