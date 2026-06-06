import request from '@/utils/request'

/**
 * 获取角色列表
 */
export function getRoleList(params) {
  return request({
    url: '/system/role/list',
    method: 'get',
    params
  })
}

/**
 * 获取角色详情
 */
export function getRoleById(id) {
  return request({
    url: `/system/role/${id}`,
    method: 'get'
  })
}

/**
 * 新增角色
 */
export function addRole(data) {
  return request({
    url: '/system/role',
    method: 'post',
    data
  })
}

/**
 * 编辑角色
 */
export function updateRole(data) {
  return request({
    url: '/system/role',
    method: 'put',
    data
  })
}

/**
 * 删除角色
 */
export function deleteRole(id) {
  return request({
    url: `/system/role/${id}`,
    method: 'delete'
  })
}

// 获取菜单树
export function getMenuList() {
  return request({
    url: '/system/menu/all',
    method: 'GET'
  })
}

// 获取角色已勾选菜单ID
export function getRoleMenuIds(roleId) {
  return request({
    url: `/system/role/getMenuIds/${roleId}`,
    method: 'GET'
  })
}

// 保存角色权限
export function saveRoleMenuPerm(roleId, menuIds) {
  return request({
    url: `/system/role/saveMenuPerm/${roleId}`,
    method: 'POST',
    data: menuIds
  })
}