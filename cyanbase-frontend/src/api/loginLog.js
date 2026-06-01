import request from '@/utils/request'

/**
 * 获取登录日志列表
 */
export function getLoginLogList(params) {
  return request({
    url: '/system/login/log/list',
    method: 'get',
    params
  })
}

/**
 * 删除登录日志
 */
export function deleteLoginLog(id) {
  return request({
    url: `/system/login/log/${id}`,
    method: 'delete'
  })
}

/**
 * 清空登录日志
 */
export function cleanLoginLog() {
  return request({
    url: '/system/login/log/clean',
    method: 'delete'
  })
}