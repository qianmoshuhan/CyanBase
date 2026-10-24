import request from '@/utils/request'
import storage from '@/utils/storage'

/**
 * 根据登录身份动态决定 API 前缀
 * - admin（平台管理员） → /system/dept/*
 * - tenant（租户用户）  → /tenant/dept/*
 */
function getUrl(path) {
  const prefix = storage.getLoginType() === 'tenant' ? '/tenant' : '/system'
  return prefix + path
}

/**
 * 获取部门列表
 */
export function getDeptList(params) {
  return request({
    url: getUrl('/dept/list'),
    method: 'get',
    params
  })
}

/**
 * 获取部门详情
 */
export function getDeptById(id) {
  return request({
    url: getUrl(`/dept/${id}`),
    method: 'get'
  })
}

/**
 * 新增部门
 */
export function addDept(data) {
  return request({
    url: getUrl('/dept'),
    method: 'post',
    data
  })
}

/**
 * 编辑部门
 */
export function updateDept(data) {
  return request({
    url: getUrl('/dept'),
    method: 'put',
    data
  })
}

/**
 * 删除部门
 */
export function deleteDept(id) {
  return request({
    url: getUrl(`/dept/${id}`),
    method: 'delete'
  })
}