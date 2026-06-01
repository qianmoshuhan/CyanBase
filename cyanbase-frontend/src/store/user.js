import { defineStore } from 'pinia'
import { login, logout, getUserInfo } from '@/api/auth'
import storage from '@/utils/storage'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: storage.getToken() || '',
    userInfo: null,
    roles: []
  }),

  getters: {
    isLoggedIn: state => !!state.token,
    getUserName: state => state.userInfo?.userName || '',
    getNickName: state => state.userInfo?.sysUser?.nickName || state.userInfo?.userName || ''
  },

  actions: {
    /**
     * 用户登录
     */
    async login(userName, password) {
      const res = await login({ userName, password })
      this.token = res.data.token
      this.userInfo = res.data.userInfo
      this.roles = res.data.userInfo.roles ? res.data.userInfo.roles.split(',') : []
      storage.setToken(this.token)
      return res
    },

    /**
     * 获取用户信息
     */
    async fetchUserInfo() {
      try {
        const res = await getUserInfo()
        this.userInfo = res.data
        this.roles = res.data.roles ? res.data.roles.split(',') : []
        return res
      } catch (error) {
        this.logout()
        throw error
      }
    },

    /**
     * 退出登录
     */
    async logout() {
      try {
        await logout()
      } catch (e) {
        // 忽略退出接口错误
      } finally {
        this.clearAuth()
      }
    },

    /**
     * 清除认证信息
     */
    clearAuth() {
      this.token = ''
      this.userInfo = null
      this.roles = []
      storage.removeToken()
    }
  }
})