import request from '@/utils/request'

export function getApiMonitorList(params) {
  return request({ url: '/system/api/monitor/list', method: 'get', params })
}

export function getApiMonitorById(id) {
  return request({ url: `/system/api/monitor/${id}`, method: 'get' })
}

export function addApiMonitor(data) {
  return request({ url: '/system/api/monitor', method: 'post', data })
}
