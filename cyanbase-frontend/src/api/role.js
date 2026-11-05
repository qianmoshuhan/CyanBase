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
 * 获取菜单树 — admin 按 tenantId 动态裁剪，租户自动走本租户 availableMenus
 * @param {number} tenantId 所属租户ID（admin 传，租户侧忽略）
 */
export function getMenuList(tenantId) {
  const isTenant = storage.getLoginType() === 'tenant'
  if (isTenant) {
    return request({ url: '/tenant/role/availableMenus', method: 'GET' })
  }
  // admin：按 tenantId 加载（0 或 null 表示平台级角色 → 全量菜单）
  return request({
    url: '/system/role/availableMenus',
    method: 'GET',
    params: { tenantId: tenantId ?? 0 }
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