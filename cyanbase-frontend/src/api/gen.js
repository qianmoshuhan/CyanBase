import request from '@/utils/request'

export function getGenTableList(params) {
  return request({ url: '/system/gen/list', method: 'get', params })
}

export function getGenTableById(id) {
  return request({ url: `/system/gen/${id}`, method: 'get' })
}

export function addGenTable(data) {
  return request({ url: '/system/gen', method: 'post', data })
}

export function updateGenTable(data) {
  return request({ url: '/system/gen', method: 'put', data })
}

export function deleteGenTable(id) {
  return request({ url: `/system/gen/${id}`, method: 'delete' })
}
