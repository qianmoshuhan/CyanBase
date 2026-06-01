import request from '@/utils/request'

export function getOnlineList(params) {
  return request({ url: '/system/online/list', method: 'get', params })
}

export function forceOffline(sessionId) {
  return request({ url: `/system/online/${sessionId}`, method: 'delete' })
}
