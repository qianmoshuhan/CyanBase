import request from '@/utils/request'

export function getNoticeList(params) {
  return request({ url: '/system/notice/list', method: 'get', params })
}

export function getNoticeById(id) {
  return request({ url: `/system/notice/${id}`, method: 'get' })
}

export function addNotice(data) {
  return request({ url: '/system/notice', method: 'post', data })
}

export function updateNotice(data) {
  return request({ url: '/system/notice', method: 'put', data })
}

export function deleteNotice(id) {
  return request({ url: `/system/notice/${id}`, method: 'delete' })
}
