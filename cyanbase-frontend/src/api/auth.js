import request from '@/utils/request'

/**
 * 用户登录
 */
export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

/**
 * 获取滑块验证资源（token + 背景图 + 缺口图）
 */
export function getCaptcha() {
  return request({
    url: '/auth/captcha',
    method: 'get'
  })
}

/**
 * 校验滑块轨迹，发放一次性登录凭证 credentialKey
 */
export function checkCaptcha(data) {
  return request({
    url: '/auth/captcha/check',
    method: 'post',
    data
  })
}

/**
 * 获取当前登录用户信息
 */
export function getUserInfo() {
  return request({
    url: '/auth/getUserInfo',
    method: 'get'
  })
}

/**
 * ⭐ 个人中心：获取当前登录人完整信息（user + tenant）
 * <p>平台侧 /auth/profile 和 租户侧 /tenant/user/profile 返回体一致，前端统一调这个就行</p>
 */
export function getCurrentProfile() {
  return request({
    url: '/auth/profile',
    method: 'get'
  })
}

/**
 * ⭐ 平台超管修改密码
 */
export function changePasswordPlatform(data) {
  return request({
    url: '/auth/profile/password',
    method: 'put',
    data
  })
}

/**
 * ⭐ 租户账号修改密码
 */
export function changePasswordTenant(data) {
  return request({
    url: '/tenant/user/profile/password',
    method: 'put',
    data
  })
}

/**
 * 退出登录
 */
export function logout(refreshToken) {
  return request({
    url: '/auth/logout',
    method: 'post',
    data: { refreshToken }
  })
}

/**
 * 刷新 Access Token（双 Token 轮换）
 */
export function refreshToken(refreshToken) {
  return request({
    url: '/auth/refresh',
    method: 'post',
    data: { refreshToken }
  })
}

/**
 * ⭐ 平台主体查询自己允许访问的租户列表
 *  - 平台超管（tenantId=0）→ 返回全部启用租户
 *  - 平台主体（tenantId=NULL）→ 返回 principal_tenant_scope 分配范围
 *  - 普通租户用户（tenantId>0）→ 仅返回所属租户
 */
export function getTenantScopes() {
  return request({
    url: '/auth/tenant-scopes',
    method: 'get'
  })
}

/**
 * ⭐ 平台主体切换活跃租户 —— 换发带 activeTenantId 的新 token
 * 调用成功后前端需立即：
 *   storage.setToken(newAccess) + storage.setRefreshToken(newRefresh)
 *   store.userInfo = null（下次 fetchUserInfo 拉新菜单池）
 */// 平台主体切换活跃租户（已登录状态下切换，需要 Authorization header）
export function switchTenant(tenantId) {
  return request({
    url: '/auth/switch-tenant',
    method: 'post',
    data: { tenantId }
  })
}

// 平台主体首次选租户换 token（登录后还没业务 JWT，靠 credentialKey 安全屏障）
export function switchTenantToken(data) {
  return request({
    url: '/auth/switch-tenant-token',
    method: 'post',
    data  // { userId, selectedTenantId, credentialKey }
  })
}

/**
 * 平台主体右上角下拉：获取当前主体的有效授权租户列表
 *  - 需登录态 JWT，仅 tenantId IS NULL 的主体可调用
 */
export function getMyTenantScopes() {
  return request({
    url: '/auth/my-tenant-scopes',
    method: 'get'
  })
}