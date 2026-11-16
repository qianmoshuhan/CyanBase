import { defineStore } from 'pinia'
import { login, logout, getUserInfo, switchTenant, switchTenantToken } from '@/api/auth'
import { switchBizUnit } from '@/api/bizUnitScope'
import storage from '@/utils/storage'
import { useTagsViewStore } from './tagsView'

/**
 * 从当前 Access Token 的 JWT payload 读取指定 claim。
 * <p>后端 getUserInfo() 构造 LoginUser 时没有 set 平台主体标记（字段存在但值为 null），
 * 且文档 §15.1 明确平台主体 sys_user.tenant_id 可能是 NULL 也可能 >0（auditor01 是 6），
 * 因此「是否平台主体」不能只看 tenantId。JWT claim 与后端
 * TenantBizUnitScopeController.authenticateTenantUser() 的 403 判定同源，最可靠。</p>
 */
function readTokenClaim(key) {
  const token = storage.getToken()
  if (!token || typeof token !== 'string') return undefined
  try {
    const parts = token.split('.')
    if (parts.length < 2) return undefined
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(base64).split('').map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join('')
    )
    return JSON.parse(json)?.[key]
  } catch (e) {
    return undefined
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: storage.getToken() || '',
    userInfo: null,
    roles: [],
    permissions: []
  }),
  getters: {
    isLoggedIn: state => !!state.token,
    getUserName: state => state.userInfo?.userName || state.userInfo?.sysUser?.userName || '',
    getNickName: state => state.userInfo?.sysUser?.nickName || state.userInfo?.nickName || state.userInfo?.userName || '',
    getTenantId: state => state.userInfo?.sysUser?.tenantId || state.userInfo?.tenantId || null,
    // 从 sysUser 对象里取到所属租户名（后端 getUserInfo 没直接塞，个人中心接口 profile 才给）
    // 首页/banner 的 tenantName 从 tenant store 或 profile 接口拿最准，这里保底用 tenantId 区分平台/租户
    getIsPlatform: state => {
      const tid = state.userInfo?.sysUser?.tenantId || state.userInfo?.tenantId
      return !tid || tid === 0
    },
    // 新增获取用户ID
    getUserId: state => state.userInfo?.userId,
    // 权限码集合（后端 LoginUser.permissions 是 Set，JSON 序列化后变数组）
    getPermissions: state => state.userInfo?.permissions || [],
    /** 判断是否拥有指定权限码（支持通配符 *:*:*） */
    hasPerm: state => (code) => {
      if (!code) return false
      const perms = state.userInfo?.permissions || []
      if (perms.includes('*:*:*')) return true
      return perms.includes(code)
    },
    /** ⭐ 平台主体判定：JWT claim 优先，userInfo 兜底（见文件头 readTokenClaim 说明） */
    getIsPlatformPrincipal: state => {
      const claim = readTokenClaim('platformPrincipal')
      if (claim === true) return true
      if (claim === false) return false
      // 兼容后端未来回填 LoginUser.platformPrincipal 的情况
      if (state.userInfo?.platformPrincipal === true) return true
      // userInfo 尚未拉到（切换中会短暂置 null）时不妄断，避免下拉闪烁
      if (!state.userInfo) return false
      const tid = state.userInfo?.sysUser?.tenantId ?? state.userInfo?.tenantId
      return tid === null || tid === undefined
    }
  },
  actions: {
    /**
     * 登录入口：兼容账号密码 + 手机号密码双模式
     * @param {Object} loginBody  { userName | phone, password, credentialKey }
     * @returns 后端响应对象；平台主体返回 step='select-tenant' 时 data 里没有 accessToken
     */
    async login(loginBody) {
      const res = await login(loginBody)
      // ⚠️ 平台主体 step='select-tenant' 分支后端不下发 accessToken，跳过存储
      if (res.data.accessToken) {
        this.token = res.data.accessToken
        storage.setToken(res.data.accessToken)
        storage.setRefreshToken(res.data.refreshToken)
      }
      this.userInfo = null
      this.roles = []
      this.permissions = []
      return res
    },
    async fetchUserInfo() {
      try {
        const res = await getUserInfo()
        this.userInfo = res.data
        this.roles = res.data.roles ? res.data.roles.split(',') : []
        this.permissions = res.data.permissions || []
        return res
      } catch (error) {
        this.logout()
        throw error
      }
    },
    async logout() {
      const refreshToken = storage.getRefreshToken()
      try {
        await logout(refreshToken)
      } catch (e) {
        // 后端可能已拉黑，忽略
      } finally {
        this.clearAuth()
      }
    },
    clearAuth() {
      this.token = ''
      this.userInfo = null
      this.roles = []
      this.permissions = []
      storage.clearAuth() // Access + Refresh 一起清
      // ⭐ 同步清理标签页和 KeepAlive 缓存（退出登录 → 新身份不能继承旧标签）
      try { useTagsViewStore().reset() } catch (e) { /* store 可能还没注册，忽略 */ }
    },

    /**
     * ⭐ 平台主体切换活跃租户（已登录状态下切换，走 /auth/switch-tenant）
     */
    async switchTenantAndUpdateToken(tenantId) {
      const res = await switchTenant(tenantId)
      const { accessToken, refreshToken } = res.data
      this.token = accessToken
      storage.setToken(accessToken)
      storage.setRefreshToken(refreshToken)
      this.userInfo = null
      this.roles = []
      this.permissions = []
      return res.data
    },

    /**
     * ⭐ 平台主体首次选租户换 token（走 /auth/switch-tenant-token，无 Authorization header）
     */
    async switchTenantTokenAndUpdateToken(userId, selectedTenantId, credentialKey) {
      const res = await switchTenantToken({ userId, selectedTenantId, credentialKey })
      const { accessToken, refreshToken } = res.data
      this.token = accessToken
      storage.setToken(accessToken)
      storage.setRefreshToken(refreshToken)
      this.userInfo = null
      this.roles = []
      this.permissions = []
      return res.data
    },

    /**
     * ⭐ 切换租户内经营单元并立即更新双 Token（走 PUT /tenant/biz-unit/switch）
     * 严格对齐 switchTenantAndUpdateToken 模板：后端同时轮换 Access + Refresh，
     * 并把 activeBizUnitId 写进 Refresh 的 Redis 上下文，保证自动续期后不丢经营单元。
     * @param {number|null} bizUnitId  目标经营单元ID；null = 清空，恢复原始 DataScope
     * @returns {{ accessToken: string, refreshToken: string, activeBizUnitId: number|null }}
     */
    async switchBizUnitAndUpdateToken(bizUnitId) {
      const res = await switchBizUnit(bizUnitId, storage.getRefreshToken())
      const { accessToken, refreshToken, activeBizUnitId } = res.data
      // F1：新 token 必须立即覆盖内存 + localStorage，下一个请求才会带 activeBizUnitId
      this.token = accessToken
      storage.setToken(accessToken)
      // ⭐ 后端已作废旧 Refresh（单设备模式），不覆盖新值 → 下次 401 续期必然失败被踢登录
      if (refreshToken) storage.setRefreshToken(refreshToken)
      // F2：旧 userInfo 是旧 token 解析出来的，不含新的 activeBizUnitId，
      //     清空后调用方 fetchUserInfo() 才会从新 token 重新解析
      this.userInfo = null
      this.roles = []
      this.permissions = []
      // 角色合并后菜单/权限可能变化，标签页里的页面可能已用旧权限渲染过
      try { useTagsViewStore().reset() } catch (e) { /* store 可能还没注册，忽略 */ }
      return { accessToken, refreshToken, activeBizUnitId }
    }
  }
})