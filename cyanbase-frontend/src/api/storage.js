import request from '@/utils/request'

export function getStorageList(params) {
  return request({ url: '/system/storage/list', method: 'get', params })
}

export function getStorageById(id) {
  return request({ url: `/system/storage/${id}`, method: 'get' })
}

export function addStorage(data) {
  return request({ url: '/system/storage', method: 'post', data })
}

export function updateStorage(data) {
  return request({ url: '/system/storage', method: 'put', data })
}

export function deleteStorage(id) {
  return request({ url: `/system/storage/${id}`, method: 'delete' })
}
