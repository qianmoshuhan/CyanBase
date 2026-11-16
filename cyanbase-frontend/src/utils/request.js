import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import router from '@/router'
import storage from './storage'
import { refreshToken as refreshTokenApi } from '@/api/auth'
import { useUserStore } from '@/store/user'

// ===== 自动续期核心状态 =====
let isRefreshing = false               // 是否正在刷新（单飞标志）
let pendingRequests = []               // 等待刷新完成后重放的请求队列

// 创建axios实例 - 开发环境通过Vite代理访问后端
const service = axios.create({
  baseURL: '',
  timeout: 30000
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    // ⭐ refreshToken 接口不要自动注入 Authorization header
    // 业务上 refresh 只靠 refreshToken（body 里），不应该用 Access Token
    // 如果注入了旧 Access Token，被踢下线后会导致 refresh 接口也被 Filter 拦截 → 死循环
    const isRefreshApi = config.url?.includes('/auth/refresh')
    if (!isRefreshApi) {
      const token = storage.getToken()
      if (token) {
        config.headers['Authorization'] = `Bearer ${token}`
      }
    }
    return config
  },
  error => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    // ⭐ 旁路：responseType=blob / arraybuffer 的下载请求 —— 跳过 JSON code 判断
    //   直接返回完整 response（业务方自己用 response.data 拿 Blob）
    //   根因：Blob 对象没有 .code 属性，走下面 res.code===200 会永远判失败
    const ct = response.headers?.['content-type'] || ''
    if (response.config?.responseType === 'blob'
        || response.config?.responseType === 'arraybuffer'
        || ct.includes('application/octet-stream')
        || ct.includes('application/vnd.openxmlformats')
        || ct.includes('application/pdf')) {
      return response
    }

    const res = response.data

    // code为200表示成功
    if (res.code === 200) {
      return res
    }

    // code为401表示Access Token失效 —— 尝试自动续期
    if (res.code === 401) {
      return handle401(response)
    }

    // code为403 —— 后端权限拦截返回
    if (res.code === 403) {
      ElMessage.error(res.msg || '权限不足，请联系管理员')
      return Promise.reject(new Error(res.msg || '权限不足'))
    }

    // 其他业务异常
    ElMessage.error(res.msg || '操作失败')
    return Promise.reject(new Error(res.msg || '操作失败'))
  },
  error => {
    console.error('响应错误:', error)

    // HTTP 401 也走自动续期
    if (error.response && error.response.status === 401) {
      return handle401(error.response)
    }
    // HTTP 403 权限拦截
    if (error.response && error.response.status === 403) {
      const msg = error.response.data?.msg || '权限不足，请联系管理员'
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    ElMessage.error(error.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

/**
 * Access Token 失效处理 —— 核心！
 * 刷新成功 → 重放原请求 → 返回给调用方（无感）
 * 刷新失败 → 清 token → 强制跳登录
 */
function handle401(response) {
  const config = response.config
  const originalRequest = config
  // ⭐ 提取后端返回的 msg（比如 "您已被管理员强制下线"）
  const backendMsg = response.data?.msg

  // ⭐ 墙 1：如果后端说"被强制下线"，直接 forceLogin，不尝试 refreshToken
  // 因为 refreshToken 接口虽然现在不带 Access Token 了（墙 2），
  // 但业务语义上：被管理员强制下线 = 不让续期 = 必须跳登录页
  if (backendMsg && backendMsg.includes('强制下线')) {
    forceLogin(backendMsg)
    return Promise.reject(new Error(backendMsg))
  }

  // ① 正在刷新中 → 把当前请求加入队列，等刷新完再重放
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      pendingRequests.push({
        resolve: newToken => {
          originalRequest.headers['Authorization'] = `Bearer ${newToken}`
          resolve(service(originalRequest))
        },
        reject: err => reject(err)
      })
    })
  }

  // ② 没在刷新 → 开始刷新
  const refreshTokenValue = storage.getRefreshToken()
  if (!refreshTokenValue) {
    // 连 Refresh Token 都没有 → 强制跳登录（用后端 msg）
    forceLogin(backendMsg)
    return Promise.reject(new Error('无 Refresh Token'))
  }

  isRefreshing = true

  return new Promise((resolve, reject) => {
    refreshTokenApi(refreshTokenValue)
      .then(res => {
        const { accessToken, refreshToken: newRefreshToken } = res.data
        // 存新 Token
        storage.setToken(accessToken)
        storage.setRefreshToken(newRefreshToken)
        // 重放队列里所有排队的请求
        pendingRequests.forEach(p => p.resolve(accessToken))
        pendingRequests = []
        // 重放当前请求
        originalRequest.headers['Authorization'] = `Bearer ${accessToken}`
        resolve(service(originalRequest))
      })
      .catch(err => {
        // Refresh Token 也过期了 → 强制登出（用后端 msg）
        pendingRequests.forEach(p => p.reject(err))
        pendingRequests = []
        forceLogin(backendMsg)
        reject(err)
      })
      .finally(() => {
        isRefreshing = false
      })
  })
}

/**
 * 强制跳登录页
 * @param {string} msg 后端返回的提示信息（可选），不传则用默认 "登录已过期"
 */
function forceLogin(msg) {
  // ⭐ 先读 loginType —— 必须在 clearAuth 之前！
  const loginType = storage.getLoginType()
  const loginPath = loginType === 'tenant' ? '/tenant-login' : '/login'

  // ⭐ 只有当前不在登录页时才弹窗
  if (router.currentRoute.value.path !== '/login' && router.currentRoute.value.path !== '/tenant-login') {
    ElMessageBox.confirm(msg || '登录已过期，请重新登录', '提示', {
      confirmButtonText: '重新登录',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      // ⭐ 用户确认后再清 auth —— 避免点"取消"后页面还在但 userInfo 被清空
      try {
        const userStore = useUserStore()
        userStore.clearAuth()
      } catch (e) {
        storage.clearAuth()
      }
      storage.clearAuth()
      router.push(loginPath)
    })
  }
}

export default service