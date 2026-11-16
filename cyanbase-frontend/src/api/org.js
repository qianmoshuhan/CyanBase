import request from '@/utils/request'

/**
 * 获取聚合的组织架构树（租户 + 部门混合层级）。
 * ⭐ 统一走 /system/org/tree —— 后端通过 SecurityUtils.getPrincipalTenantId()
 *   自动判断当前登录身份并裁剪权限范围：
 *   - root 超管    → platform-root + 全部租户 + 全部部门
 *   - 平台主体     → platform-root + PrincipalTenantScope 授权租户 + 其下部门
 *   - 租户账号     → 当前租户的部门树（无租户层级）
 * 返回节点结构（后端 OrgNodeVO）：
 * { nodeId, nodeType('platform-root'|'tenant'|'dept'), label,
 *   tenantId, deptId?, status, isBizUnit?, children? }
 */
export function getOrgTree() {
  return request({
    url: '/system/org/tree',
    method: 'get'
  })
}