import request from '@/utils/request'

/**
 * 获取用户分页列表
 */
export function getUserList(params) {
  return request({
    url: '/system/user/list',
    method: 'get',
    params
  })
}

/**
 * 获取用户详情
 */
export function getUserById(id) {
  return request({
    url: `/system/user/${id}`,
    method: 'get'
  })
}

/**
 * 新增用户
 */
export function addUser(data) {
  return request({
    url: '/system/user',
    method: 'post',
    data
  })
}

/**
 * 编辑用户
 */
export function updateUser(data) {
  return request({
    url: '/system/user',
    method: 'put',
    data
  })
}

/**
 * 删除用户
 */
export function deleteUser(id) {
  return request({
    url: `/system/user/${id}`,
    method: 'delete'
  })
}

//获取所有角色下拉列表
export function getAllRole() {
  return request({
    url: '/system/user/allRole',
    method: 'get'
  })
}
//根据用户ID获取已勾选角色ID数组
export function getUserSelectedRoleIds(uid) {
  return request({
    url: `/system/user/getRoleIds/${uid}`,
    method: 'get'
  })
}