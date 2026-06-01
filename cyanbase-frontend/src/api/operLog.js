import request from '@/utils/request'

/**
 * 获取操作日志列表
 */
export function getOperLogList(params) {
  return request({
    url: '/system/oper/log/list',
    method: 'get',
    params
  })
}

/**
 * 删除操作日志
 */
export function deleteOperLog(id) {
  return request({
    url: `/system/oper/log/${id}`,
    method: 'delete'
  })
}

/**
 * 清空操作日志
 */
export function cleanOperLog() {
  return request({
    url: '/system/oper/log/clean',
    method: 'delete'
  })
}