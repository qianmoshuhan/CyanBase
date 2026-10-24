import { defineStore } from 'pinia'
import { login, logout, getUserInfo } from '@/api/auth'
import storage from '@/utils/storage'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: storage.getToken() || '',
    userInfo: null,
    roles: [],
    permissions: []
  }),
  getters: {
    isLoggedIn: state => !!state.token,
    getUserName: state => state.userInfo?.userName || '',
    getNickName: state => state.userInfo?.sysUser?.nickName || state.userInfo?.userName || '',
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
    }
  },
  actions: {
    async login(userName, password) {
      const res = await login({ userName, password })
      // 新返回体：{ accessToken, refreshToken, expiresIn }
      this.token = res.data.accessToken
      this.userInfo = null // getUserInfo() 会自己拉
      this.roles = []
      storage.setToken(res.data.accessToken)
      storage.setRefreshToken(res.data.refreshToken)
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
    }
  }
})