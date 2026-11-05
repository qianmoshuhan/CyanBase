import request from '@/utils/request'

/**
 * 租户登录（手机号模式）
 * @param {Object} data - { phone, password, tenantId? }
 *   首次请求不传 tenantId → 后端统一验密
 *   多租户命中时后端返回 { step: 'select-tenant', list: [...] }
 *   选租户后二次请求带 tenantId → 真正登录返回 LoginResponse
 */
export function tenantLogin(data) {
  return request({
    url: '/tenant-auth/login',
    method: 'post',
    data
  })
}