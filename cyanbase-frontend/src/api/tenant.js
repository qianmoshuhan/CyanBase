import request from '@/utils/request'

// 分页查询租户列表
export function getTenantList(params) {
    return request({
        url: '/system/tenant/list',
        method: 'get',
        params
    })
}

// 根据ID查询租户
export function getTenantById(id) {
    return request({
        url: `/system/tenant/${id}`,
        method: 'get'
    })
}

// 新增租户
export function addTenant(data) {
    return request({
        url: '/system/tenant',
        method: 'post',
        data
    })
}

// 更新租户
export function updateTenant(data) {
    return request({
        url: '/system/tenant',
        method: 'put',
        data
    })
}

// 删除租户
export function deleteTenant(id) {
    return request({
        url: `/system/tenant/${id}`,
        method: 'delete'
    })
}

// 获取所有菜单树（复用菜单管理接口）
export function getAllMenuTree() {
    return request({
        url: '/system/menu/all',
        method: 'get'
    })
}

// 查询指定租户已分配的菜单ID列表
export function getTenantMenuIds(tenantId) {
    return request({
        url: `/system/tenant/${tenantId}/menu-ids`,
        method: 'get'
    })
}

// 保存租户菜单分配（全量覆盖）
export function saveTenantMenus(tenantId, menuIds) {
    return request({
        url: `/system/tenant/${tenantId}/menus`,
        method: 'put',
        data: menuIds
    })
}

/**
 * 获取全部启用租户（下拉选项用）
 */
export function getAllTenant() {
    return request({
        url: '/system/tenant/all',
        method: 'get'
    })
}