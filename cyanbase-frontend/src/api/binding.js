import request from '@/utils/request'

/** 分页查询表单↔流程绑定列表 */
export function getBindingList(params) {
  return request({
    url: '/workflow/binding/list',
    method: 'get',
    params
  })
}

/** 查询某个表单的所有启用绑定 */
export function getBindingsByForm(definitionId) {
  return request({
    url: `/workflow/binding/by-form/${definitionId}`,
    method: 'get'
  })
}

/** 绑定详情 */
export function getBindingById(id) {
  return request({
    url: `/workflow/binding/${id}`,
    method: 'get'
  })
}

/** 新增绑定 */
export function addBinding(data) {
  return request({
    url: '/workflow/binding',
    method: 'post',
    data
  })
}

/** 更新绑定 */
export function updateBinding(data) {
  return request({
    url: '/workflow/binding',
    method: 'put',
    data
  })
}

/** 删除绑定 */
export function deleteBinding(id) {
  return request({
    url: `/workflow/binding/${id}`,
    method: 'delete'
  })
}

/** ⭐ 路由匹配（表单填报前自动调） */
export function matchProcess(definitionId, userId) {
  return request({
    url: '/workflow/binding/match',
    method: 'get',
    params: { definitionId, userId }
  })
}
