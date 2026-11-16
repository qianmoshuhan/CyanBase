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
 * 获取部门列表（分页）
 */
export function getDeptList(params) {
  return request({
    url: getUrl('/dept/list'),
    method: 'get',
    params
  })
}

/**
 * 获取全量部门（扁平列表，前端 buildTree 成 children 嵌套）
 * 树形表格展示需要全量数据
 * @param {Object} params 可选过滤条件：{ deptName, status, tenantId }
 */
export function getAllDept(params) {
  return request({
    url: getUrl('/dept/all'),
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

/**
 * 导出部门数据为 Excel（.xlsx）
 * <p>
 * 传参：deptName（模糊）、status（精确）。
 *   平台侧可额外传 tenantId 精确过滤租户。
 *   租户侧即使传了 tenantId 也被后端 CustomTenantHandler 拦截（MP 自动隔离）。
 * responseType: blob —— 前端收到后构造 Blob + 动态 a 标签触发下载。
 * </p>
 */
export function exportDept(params) {
  return request({
    url: getUrl('/dept/export'),
    method: 'get',
    params,
    responseType: 'blob'
  })
}

/**
 * 下载部门导入模板（.xlsx）
 * <p>
 * 模板内容：表头与导出 Excel 一致 + 表头上方灰色说明行 + 状态/是否经营单元列下拉。
 * 本接口仅返回空模板，不处理实际导入业务逻辑（下一阶段实现）。
 * </p>
 */
export function importTemplate() {
  return request({
    url: getUrl('/dept/importTemplate'),
    method: 'get',
    responseType: 'blob'
  })
}

/**
 * 执行部门导入（上传 .xlsx 文件）
 * <p>
 * 后端逻辑：读取 Excel → 逐行校验（带 Excel 真实行号）→ 全部通过才入库。
 * 租户侧忽略 Excel 的租户列，数据归属当前登录租户。
 * 返回结构：{ totalRows, successRows, failRows, failList: [{ rowNum, errorMsg }] }
 * </p>
 */
export function importDept(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: getUrl('/dept/import'),
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}