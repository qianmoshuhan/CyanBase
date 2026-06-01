import request from '@/utils/request'

export function getThirdConfigList(params) {
  return request({ url: '/system/third/config/list', method: 'get', params })
}

export function getThirdConfigById(id) {
  return request({ url: `/system/third/config/${id}`, method: 'get' })
}

export function addThirdConfig(data) {
  return request({ url: '/system/third/config', method: 'post', data })
}

export function updateThirdConfig(data) {
  return request({ url: '/system/third/config', method: 'put', data })
}

export function deleteThirdConfig(id) {
  return request({ url: `/system/third/config/${id}`, method: 'delete' })
}
