/**
 * 本地存储工具类
 */
const STORAGE_PREFIX = 'cyan_'

const storage = {
  /**
   * 设置本地存储
   */
  set(key, value) {
    const storageKey = STORAGE_PREFIX + key
    const storageValue = typeof value === 'object' ? JSON.stringify(value) : value
    localStorage.setItem(storageKey, storageValue)
  },

  /**
   * 获取本地存储
   */
  get(key, defaultValue = null) {
    const storageKey = STORAGE_PREFIX + key
    const value = localStorage.getItem(storageKey)
    if (value === null) {
      return defaultValue
    }
    try {
      return JSON.parse(value)
    } catch {
      return value
    }
  },

  /**
   * 删除本地存储
   */
  remove(key) {
    const storageKey = STORAGE_PREFIX + key
    localStorage.removeItem(storageKey)
  },

  /**
   * 清空所有本地存储
   */
  clear() {
    const keys = Object.keys(localStorage)
    keys.forEach(key => {
      if (key.startsWith(STORAGE_PREFIX)) {
        localStorage.removeItem(key)
      }
    })
  },

  /**
   * 获取Token
   */
  getToken() {
    return this.get('token')
  },

  /**
   * 设置Token
   */
  setToken(token) {
    this.set('token', token)
  },

  /**
   * 删除Token
   */
  removeToken() {
    this.remove('token')
  }
}

export default storage