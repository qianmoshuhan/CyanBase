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

/**
 * 获取所有启用的表单定义（下拉选用）
 */
export function getFormDefAllActive() {
  return request({
    url: '/form/definition/all-active',
    method: 'get'
  })
}

/**
 * 获取指定表单定义下的字段列表
 */
export function getFormFieldList(definitionId) {
  return request({
    url: `/form/field/list/${definitionId}`,
    method: 'get'
  })
}

/**
 * ⭐ Phase 3.2 我的表单列表（当前租户已发布表单，含 hasBinding 标记）
 */
export function getMyFormList() {
  return request({
    url: '/form/definition/my-list',
    method: 'get'
  })
}

/**
 * 批量保存表单字段（表单设计器保存时同步拆字段入库）
 */
export function batchSaveFormFields(definitionId, fields) {
  return request({
    url: `/form/field/batch/${definitionId}`,
    method: 'post',
    data: fields
  })
}

// ==================== 表单版本 API ====================

/** 查询某表单定义的全部版本列表 */
export function getFormVersionList(definitionId) {
  return request({
    url: `/form/definition/${definitionId}/versions`,
    method: 'get'
  })
}

/** 查询某版本详情（返回 form_schema） */
export function getFormVersionDetail(versionId) {
  return request({
    url: `/form/definition/version/${versionId}`,
    method: 'get'
  })
}

/** 从指定版本创建新版本 */
export function createFormVersionFrom(sourceVersionId, versionName) {
  return request({
    url: `/form/definition/version/${sourceVersionId}/create-from`,
    method: 'post',
    data: { versionName }
  })
}

/** 保存表单版本快照（设计器版本模式保存） */
export function saveFormVersionSnapshot(versionId, formSchema) {
  return request({
    url: `/form/definition/version/${versionId}/save-snapshot`,
    method: 'post',
    data: { formSchema }
  })
}