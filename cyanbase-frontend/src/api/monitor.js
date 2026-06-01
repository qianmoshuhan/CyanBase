import request from '@/utils/request'

/**
 * 获取服务健康状态
 */
export function getServerHealth() {
  return request({
    url: '/actuator/health',
    method: 'get'
  })
}

/**
 * 获取服务指标
 */
export function getServerMetrics() {
  return request({
    url: '/actuator/metrics',
    method: 'get'
  })
}

/**
 * 获取服务信息
 */
export function getServerInfo() {
  return request({
    url: '/actuator/info',
    method: 'get'
  })
}