import request from '@/utils/request'

/**
 * 通用删除请求
 */
export function commonDelete(url) {
  return request({
    url,
    method: 'delete'
  })
}

/**
 * 通用保存请求
 */
export function commonSave(url, data) {
  return request({
    url,
    method: 'post',
    data
  })
}

/**
 * 通用更新请求
 */
export function commonUpdate(url, data) {
  return request({
    url,
    method: 'put',
    data
  })
}

/**
 * 通用获取详情请求
 */
export function commonGet(url) {
  return request({
    url,
    method: 'get'
  })
}