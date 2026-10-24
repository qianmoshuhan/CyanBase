import request from '@/utils/request'

/**
 * 获取字典类型列表
 */
export function getDictTypeList(params) {
  return request({
    url: '/system/dict/type/list',
    method: 'get',
    params
  })
}

/**
 * 获取字典类型详情
 */
export function getDictTypeById(id) {
  return request({
    url: `/system/dict/type/${id}`,
    method: 'get'
  })
}

/**
 * 新增字典类型
 */
export function addDictType(data) {
  return request({
    url: '/system/dict/type',
    method: 'post',
    data
  })
}

/**
 * 编辑字典类型
 */
export function updateDictType(data) {
  return request({
    url: '/system/dict/type',
    method: 'put',
    data
  })
}

/**
 * 删除字典类型
 */
export function deleteDictType(id) {
  return request({
    url: `/system/dict/type/${id}`,
    method: 'delete'
  })
}

export function getDictTypeAllActive() {
  return request({
    url: '/system/dict/type/all-active',
    method: 'get'
  })
}