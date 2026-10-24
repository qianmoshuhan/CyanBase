import request from '@/utils/request'
import storage from '@/utils/storage'

function getUrl(path) {
  const prefix = storage.getLoginType() === 'tenant' ? '/tenant' : '/system'
  return prefix + path
}

/**
 * 获取角色列表
 */
export function getRoleList(params) {
  return request({
    url: getUrl('/role/list'),
    method: 'get',
    params
  })
}

/**
 * 获取角色详情
 */
export function getRoleById(id) {
  return request({
    url: getUrl(`/role/${id}`),
    method: 'get'
  })
}

/**
 * 新增角色
 */
export function addRole(data) {
  return request({
    url: getUrl('/role'),
    method: 'post',
    data
  })
}

/**
 * 编辑角色
 */
export function updateRole(data) {
  return request({
    url: getUrl('/role'),
    method: 'put',
    data
  })
}

/**
 * 删除角色
 */
export function deleteRole(id) {
  return request({
    url: getUrl(`/role/${id}`),
    method: 'delete'
  })
}

/**
 * 获取菜单树 — 根据身份走不同接口
 * - admin → /system/menu/all（全局菜单）
 * - tenant → /tenant/role/availableMenus（只有平台分配给本租户的菜单）
 */
export function getMenuList() {
  const isTenant = storage.getLoginType() === 'tenant'
  return request({
    url: isTenant ? '/tenant/role/availableMenus' : '/system/menu/all',
    method: 'GET'
  })
}

/**
 * 获取角色已勾选菜单ID
 */
export function getRoleMenuIds(roleId) {
  return request({
    url: getUrl(`/role/getMenuIds/${roleId}`),
    method: 'GET'
  })
}

/**
 * 保存角色权限
 */
export function saveRoleMenuPerm(roleId, menuIds) {
  return request({
    url: getUrl(`/role/saveMenuPerm/${roleId}`),
    method: 'POST',
    data: menuIds
  })
}

/** 保存角色数据范围配置（自定义时需传 deptIds） */
export function saveRoleDataScope(roleId, dataScope, deptIds) {
  return request({
    url: getUrl(`/role/saveDataScope/${roleId}`),
    method: 'POST',
    data: { dataScope, deptIds }
  })
}

/** 获取角色数据范围配置（编辑回显） */
export function getRoleDataScope(roleId) {
  return request({
    url: getUrl(`/role/getDataScope/${roleId}`),
    method: 'GET'
  })
}