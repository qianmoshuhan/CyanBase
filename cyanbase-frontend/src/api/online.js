import request from '@/utils/request'

export function getOnlineList(params) {
  return request({ url: '/monitor/online/list', method: 'get', params })
}

export function forceOffline(sessionId) {
  return request({ url: `/monitor/online/${sessionId}`, method: 'delete' })
}