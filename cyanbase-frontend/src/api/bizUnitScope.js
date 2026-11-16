import request from '@/utils/request'

/**
 * 接口 1：经营单元树形（管理端用，全部门树 + isBizUnit 标记）
 * GET /tenant/biz-unit/tree?tenantId=xxx
 * @param {Object} params 可选：{ tenantId }
 *   - admin(tenantId=0) 必须显式传 tenantId；租户用户不传 → 后端自动用自己的
 * 返回: [{ id, parentId, deptName, sortNum, status, isBizUnit(0|1), children }, ...]
 */
export function getBizUnitTree(params = {}) {
  return request({
    url: '/tenant/biz-unit/tree',
    method: 'get',
    params
  })
}

/**
 * 接口 2：当前登录用户已绑定的经营单元 + 角色列表
 * GET /tenant/biz-unit/my-scopes
 * 返回: [{ bizUnitId, bizUnitName, roles: [{ roleId, roleName }, ...] }, ...]
 * 注意：角色是嵌套的 roles 数组，不是扁平的 roleId/roleName 字段。
 */
export function getMyBizUnitScopes() {
  return request({
    url: '/tenant/biz-unit/my-scopes',
    method: 'get'
  })
}

/**
 * 接口 3：分配/解除经营单元角色绑定（先删后插，roleId=null 表示解除绑定）
 * POST /tenant/biz-unit/assign
 * @param {Object} data  { bizUnitId, roleId, userId? }
 *   - 没传 userId：给自己绑（所有人可用）
 *   - 传了 userId 且 != 当前用户：给他人绑（需要 system:user:bizUnitAssign 权限）
 * 失败时后端返回精准消息（「指定部门不是经营单元」「部门不属于当前租户」
 * 「角色不属于当前租户」「目标用户不存在或不属于当前租户」等），
 * request.js 拦截器会原样弹出，调用方不要重写。
 */
export function assignBizUnitScope(data) {
  return request({
    url: '/tenant/biz-unit/assign',
    method: 'post',
    data
  })
}

/**
 * 接口 6：管理端 —— 查指定用户已绑定的经营单元 + 角色（抽屉回显用）
 * GET /tenant/biz-unit/user-scopes
 * @param {number} userId  目标用户ID
 * 需要 system:user:bizUnitAssign 权限。
 * 返回: [{ bizUnitId, bizUnitName, roles: [{ roleId, roleName }, ...] }, ...]
 */
export function getUserBizUnitScopes(userId) {
  return request({
    url: '/tenant/biz-unit/user-scopes',
    method: 'get',
    params: { userId }
  })
}

/**
 * 接口 4：切换当前活跃经营单元，换发带 activeBizUnitId 的新 token
 * PUT /tenant/biz-unit/switch
 * @param {number|null} bizUnitId  目标经营单元ID；传 null 清空 → 恢复原始 DataScope
 * @param {string} refreshToken  当前 Refresh Token。后端要同时轮换它，把 activeBizUnitId
 *   写进 Redis 上下文，否则 Access Token 过期自动续期后新 JWT 会丢失经营单元 claim。
 * 返回: { accessToken, refreshToken, activeBizUnitId }
 * ⚠️ 调用方必须立即用返回的 accessToken + refreshToken 覆盖 storage（见 store action），
 *    否则单设备模式下旧 Refresh 已失效，下一次 401 续期会直接把用户踢回登录页。
 */
export function switchBizUnit(bizUnitId, refreshToken) {
  return request({
    url: '/tenant/biz-unit/switch',
    method: 'put',
    data: { bizUnitId, refreshToken }
  })
}

/**
 * 接口 5：当前用户可切换的经营单元扁平列表（header 下拉用）
 * GET /tenant/biz-unit/get-available-biz-units
 * 返回: [{ bizUnitId, bizUnitName }, ...]（只含当前用户已绑定的经营单元）
 */
export function getAvailableBizUnits() {
  return request({
    url: '/tenant/biz-unit/get-available-biz-units',
    method: 'get'
  })
}

/**
 * 接口 8：A 阶段 —— 获取指定租户下可用角色列表（排除 admin 超管角色）
 * GET /tenant/biz-unit/available-roles?tenantId=xxx
 * @param {number} tenantId  可选；admin(tenantId=0) 必须传；租户用户不传 → 后端自动用自己的
 * 返回: [{ id, roleName, roleKey }, ...]
 */
export function listAvailableRoles(tenantId) {
  return request({
    url: '/tenant/biz-unit/available-roles',
    method: 'get',
    params: tenantId ? { tenantId } : {}
  })
}

/**
 * 接口 7：A 阶段独立授权管理页 —— 反向查询：指定经营单元下所有已绑定的用户
 * GET /tenant/biz-unit/biz-unit-users?bizUnitId=xxx
 * 需要 system:user:bizUnitAssign 权限。
 * 返回: [{ userId, userName, nickName, phone, email, status, deptName, roleId, roleName, bindTime }, ...]
 */
export function listBizUnitScopeUsers(bizUnitId) {
  return request({
    url: '/tenant/biz-unit/biz-unit-users',
    method: 'get',
    params: { bizUnitId }
  })
}