import request from '@/utils/request'

export function getApiMonitorList(params) {
  return request({ url: '/monitor/apiMonitor/list', method: 'get', params })
}

export function getApiMonitorById(id) {
  return request({ url: `/monitor/apiMonitor/${id}`, method: 'get' })
}

export function addApiMonitor(data) {
  return request({ url: '/monitor/apiMonitor', method: 'post', data })
}