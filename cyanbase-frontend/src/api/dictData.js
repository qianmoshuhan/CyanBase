import request from '@/utils/request'

export function getDictDataList(params) {
  return request({ url: '/system/dict/data/list', method: 'get', params })
}

export function getDictDataByType(dictTypeCode) {
  return request({ url: `/system/dict/data/byType/${dictTypeCode}`, method: 'get' })
}

export function getDictDataById(id) {
  return request({ url: `/system/dict/data/${id}`, method: 'get' })
}

export function addDictData(data) {
  return request({ url: '/system/dict/data', method: 'post', data })
}

export function updateDictData(data) {
  return request({ url: '/system/dict/data', method: 'put', data })
}

export function deleteDictData(id) {
  return request({ url: `/system/dict/data/${id}`, method: 'delete' })
}