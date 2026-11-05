import { defineStore } from 'pinia'

const SESSION_KEY = 'tagsView:visitedViews'

const loadFromSession = () => {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
}

export const useTagsViewStore = defineStore('tagsView', {
  state: () => ({
    visitedViews: loadFromSession(),
    /**
     * KeepAlive include 的组件 name 列表
     * 存 meta.componentName（优先）或 route.name
     * 多路由复用同一组件时，用 meta.componentName 统一 key
     */
    cachedViews: []
  }),
  getters: {
    affixTags: (state) => state.visitedViews.filter(v => v.meta?.affix)
  },
  actions: {
    persist() {
      const slim = this.visitedViews.map(v => ({
        path: v.path,
        fullPath: v.fullPath,
        name: v.name,
        meta: { ...v.meta },
        params: v.params,
        query: v.query
      }))
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(slim))
    },

    addView(route) {
      // ⭐ /redirect/xxx 是刷新中转路由，也要进白名单（防止被当普通标签添加）
      const whiteList = ['/login', '/tenant-login', '/mobile/home']
      if (whiteList.includes(route.path)) return
      if (route.path.startsWith('/redirect')) return
      if (this.visitedViews.some(v => v.path === route.path)) return

      this.visitedViews.push({
        path: route.path,
        fullPath: route.fullPath,
        name: route.name,
        meta: { ...route.meta },
        params: route.params,
        query: route.query
      })

      // ⭐ key 规则：meta.componentName 优先，否则 route.name
      const cacheKey = route.meta?.componentName || route.name
      if (cacheKey && !route.meta?.noCache) {
        if (!this.cachedViews.includes(cacheKey)) {
          this.cachedViews.push(cacheKey)
        }
      }
      this.persist()
    },

    closeView(view) {
      if (!view) return false
      if (view.meta?.affix) return false
      if (!Array.isArray(this.visitedViews)) return false

      const idx = this.visitedViews.findIndex(v => v && v.path === view.path)
      if (idx === -1) return false
      this.visitedViews.splice(idx, 1)

      // ⭐ 和 addView 用同样的 key 规则
      const cacheKey = view.meta?.componentName || view.name
      if (cacheKey) {
        const ci = this.cachedViews.indexOf(cacheKey)
        if (ci > -1) this.cachedViews.splice(ci, 1)
      }
      this.persist()
      return true
    },

    closeOthers(view) {
      if (!view) return
      // ⭐ 原地从后往前删，不能重新赋值 this.visitedViews（否则组件解构引用断了）
      for (let i = this.visitedViews.length - 1; i >= 0; i--) {
        const v = this.visitedViews[i]
        if (!v.meta?.affix && v.path !== view.path) {
          this.visitedViews.splice(i, 1)
        }
      }
      // 同步 cachedViews（原地删）
      const aliveKeys = new Set(
        this.visitedViews.map(v => v.meta?.componentName || v.name).filter(Boolean)
      )
      for (let i = this.cachedViews.length - 1; i >= 0; i--) {
        if (!aliveKeys.has(this.cachedViews[i])) {
          this.cachedViews.splice(i, 1)
        }
      }
      this.persist()
    },

    closeAll() {
      // ⭐ 原地从后往前删，只留 affix
      for (let i = this.visitedViews.length - 1; i >= 0; i--) {
        if (!this.visitedViews[i].meta?.affix) {
          this.visitedViews.splice(i, 1)
        }
      }
      // 同步 cachedViews
      const aliveKeys = new Set(
        this.visitedViews.map(v => v.meta?.componentName || v.name).filter(Boolean)
      )
      for (let i = this.cachedViews.length - 1; i >= 0; i--) {
        if (!aliveKeys.has(this.cachedViews[i])) {
          this.cachedViews.splice(i, 1)
        }
      }
      this.persist()
    },

    removeCachedView(key) {
      if (!key) return
      const idx = this.cachedViews.indexOf(key)
      if (idx > -1) this.cachedViews.splice(idx, 1)
    },

    reset() {
      // ⭐ 原地清空，不能重新赋值
      this.visitedViews.splice(0, this.visitedViews.length)
      this.cachedViews.splice(0, this.cachedViews.length)
      sessionStorage.removeItem(SESSION_KEY)
    }
  }
})