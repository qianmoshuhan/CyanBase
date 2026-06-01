import request from '@/utils/request'

export function getFileList(params) {
  return request({ url: '/system/file/list', method: 'get', params })
}

export function getFileById(id) {
  return request({ url: `/system/file/${id}`, method: 'get' })
}

export function addFile(data) {
  return request({ url: '/system/file', method: 'post', data })
}

export function deleteFile(id) {
  return request({ url: `/system/file/${id}`, method: 'delete' })
}
