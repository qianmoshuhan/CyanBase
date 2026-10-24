import request from '@/utils/request'

export function getTenantList() {
  return request({
    url: '/tenant-auth/tenant/list',
    method: 'get'
  })
}

export function tenantLogin(data) {
  return request({
    url: '/tenant-auth/login',
    method: 'post',
    data
  })
}