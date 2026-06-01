import request from '@/utils/request'

export function getConfigList(params) {
  return request({ url: '/system/config/list', method: 'get', params })
}

export function getConfigById(id) {
  return request({ url: `/system/config/${id}`, method: 'get' })
}

export function addConfig(data) {
  return request({ url: '/system/config', method: 'post', data })
}

export function updateConfig(data) {
  return request({ url: '/system/config', method: 'put', data })
}

export function deleteConfig(id) {
  return request({ url: `/system/config/${id}`, method: 'delete' })
}
