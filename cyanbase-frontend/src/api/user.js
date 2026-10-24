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