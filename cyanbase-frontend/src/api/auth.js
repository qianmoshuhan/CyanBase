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