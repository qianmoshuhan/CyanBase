import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import router from '@/router'
import storage from './storage'

// 创建axios实例 - 开发环境通过Vite代理访问后端
const service = axios.create({
  baseURL: 'http://172.31.84.55:8090', // 👈 只改这里
  timeout: 30000
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    // 从本地存储获取Token并添加到请求头
    const token = storage.getToken()
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
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
    const res = response.data

    // code为200表示成功
    if (res.code === 200) {
      return res
    }

    // code为401表示未登录或令牌失效
    if (res.code === 401) {
      ElMessageBox.confirm('登录已过期，请重新登录', '提示', {
        confirmButtonText: '重新登录',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        storage.removeToken()
        router.push('/login')
      })
      return Promise.reject(new Error(res.msg || '未登录'))
    }

    // 其他业务异常
    ElMessage.error(res.msg || '操作失败')
    return Promise.reject(new Error(res.msg || '操作失败'))
  },
  error => {
    console.error('响应错误:', error)

    // 处理401未授权
    if (error.response && error.response.status === 401) {
      ElMessageBox.confirm('登录已过期，请重新登录', '提示', {
        confirmButtonText: '重新登录',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        storage.removeToken()
        router.push('/login')
      })
    } else {
      ElMessage.error(error.message || '网络异常，请稍后重试')
    }

    return Promise.reject(error)
  }
)

export default service