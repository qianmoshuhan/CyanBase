import request from '@/utils/request'

/**
 * 平台主体列表（平台超管专属，分页）
 */
export function listPrincipals(params) {
  return request({
    url: '/system/principal-scope/list',
    method: 'get',
    params
  })
}

/**
 * 查询指定平台主体已分配的 scope 租户
 */
export function getPrincipalScopes(userId) {
  return request({
    url: `/system/principal-scope/${userId}/scopes`,
    method: 'get'
  })
}

/**
 * 平台主体 scope + 角色分配 API
 * POST /system/principal-scope/{userId}/scopes
 * @param {number} userId  平台主体用户ID
 * @param {Array}  assignments  [{ tenantId, roleId }, ...]  全量覆盖，传空列表即撤销全部
 */
export function assignPrincipalScopes(userId, assignments) {
  return request({
    url: `/system/principal-scope/${userId}/scopes`,
    method: 'post',
    data: { assignments }
  })
}

/**
 * 获取全部可用角色（roleKey != admin、status=0），按 tenantId 分组后前端消费。
 * skip 后端租户拦截，一次性返回所有租户的可用角色。
 */
export function getAllAvailableRoles() {
  return request({
    url: '/system/principal-scope/all-available-roles',
    method: 'get'
  })
}

/**
 * 撤销指定主体的全部 scope
 */
export function revokeAllPrincipalScopes(userId) {
  return request({
    url: `/system/principal-scope/${userId}/scopes`,
    method: 'delete'
  })
}

/**
 * 一体化新增平台主体（基础账号信息 + Scope 租户角色分配）
 * POST /system/principal-scope/principal
 * @param {Object} data  { userName, nickName, phone, password, status, email, assignments: [{ tenantId, roleId }] }
 */
export function addPrincipal(data) {
  return request({
    url: '/system/principal-scope/principal',
    method: 'post',
    data
  })
}

/**
 * 特权操作审计日志（分页）
 */
export function listAuditLogs(params) {
  return request({
    url: '/system/principal-scope/audit-log',
    method: 'get',
    params
  })
}