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
    getNickName: state => state.userInfo?.sysUser?.nickName || state.userInfo?.userName || '',
    // 新增获取用户ID
    getUserId: state => state.userInfo?.userId
  },
  actions: {
    async login(userName, password) {
      const res = await login({ userName, password })
      this.token = res.data.token
      this.userInfo = res.data.userInfo
      this.roles = res.data.userInfo.roles ? res.data.userInfo.roles.split(',') : []
      storage.setToken(this.token)
      return res
    },
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
    async logout() {
      try {
        await logout()
      } catch (e) {
      } finally {
        this.clearAuth()
      }
    },
    clearAuth() {
      this.token = ''
      this.userInfo = null
      this.roles = []
      storage.removeToken()
    }
  }
})