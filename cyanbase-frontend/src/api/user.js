import request from '@/utils/request'
import storage from '@/utils/storage'

function getUrl(path) {
  const prefix = storage.getLoginType() === 'tenant' ? '/tenant' : '/system'
  return prefix + path
}

/**
 * 获取用户分页列表
 */
export function getUserList(params) {
  return request({
    url: getUrl('/user/list'),
    method: 'get',
    params
  })
}

/**
 * 获取用户详情
 */
export function getUserById(id) {
  return request({
    url: getUrl(`/user/${id}`),
    method: 'get'
  })
}

/**
 * 新增用户
 */
export function addUser(data) {
  return request({
    url: getUrl('/user'),
    method: 'post',
    data
  })
}

/**
 * 编辑用户
 */
export function updateUser(data) {
  return request({
    url: getUrl('/user'),
    method: 'put',
    data
  })
}

/**
 * 删除用户
 */
export function deleteUser(id) {
  return request({
    url: getUrl(`/user/${id}`),
    method: 'delete'
  })
}

/**
 * 获取角色下拉列表 — 租户场景自动只返回本租户的角色
 */
export function getAllRole() {
  return request({
    url: getUrl('/user/allRole'),
    method: 'get'
  })
}

/**
 * 根据用户ID获取已勾选角色ID数组
 */
export function getUserSelectedRoleIds(uid) {
  return request({
    url: getUrl(`/user/getRoleIds/${uid}`),
    method: 'get'
  })
}

/**
 * 重置用户密码 —— 返回新生成的随机明文密码
 */
export function resetUserPwd(userId) {
  return request({
    url: getUrl(`/user/resetPwd/${userId}`),
    method: 'post'
  })
}

/**
 * 校验手机号在当前（或指定）租户内是否唯一
 * @param {string} phone 手机号
 * @param {object} params { tenantId?: number, excludeId?: number } 平台侧需传 tenantId，编辑场景传 excludeId
 * @returns {Promise<boolean>} true=可用，false=已存在
 */
export function checkUserPhone(phone, params = {}) {
  return request({
    url: getUrl('/user/checkPhone'),
    method: 'get',
    params: { phone, ...params }
  })
}