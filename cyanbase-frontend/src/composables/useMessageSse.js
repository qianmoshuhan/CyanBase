/**
 * SSE 消息未读数推送 composable
 *
 * 特性：
 *  ✅ 原生 EventSource —— 浏览器自动重连、自动重发未收到的事件
 *  ✅ 多标签页共享 —— 后端按 userId 广播到所有连接，前端无需 BroadcastChannel
 *  ✅ 页面/路由关闭自动 close —— 防止僵尸连接
 *  ✅ 网络断开自动重连 —— EventSource 原生行为 + 心跳撑过 nginx 超时
 *  ✅ 降级兜底 —— SSE 建立超时/报错时自动 fallback 回 HTTP 轮询，保证功能不挂
 *  ✅ 只推数字不碰业务 ——铃铛弹窗、消息列表逻辑原样不动
 *
 * 用法：
 *    const { unreadCount, close } = useMessageSse()
 *    // unreadCount 是响应式 ref，直接绑到 el-badge :value 上
 *    // onBeforeUnmount 调 close()
 */
import { ref, onBeforeUnmount } from 'vue'
import storage from '@/utils/storage'
import { getUnreadCount } from '@/api/message'

const SSE_URL = '/system/message/sse'
const SSE_OPEN_TIMEOUT = 5000    // 5 秒还没连上 → 降级
const POLL_INTERVAL = 30000       // 降级后轮询间隔

export function useMessageSse() {
  const unreadCount = ref(0)
  let eventSource = null
  let pollTimer = null
  let isFallback = false

  // ============== 降级：HTTP 轮询 ==============
  const fallbackToPolling = () => {
    if (isFallback) return        // 已降级过了，别重复启动
    isFallback = true
    console.warn('[SSE] 降级回 HTTP 轮询')

    const poll = async () => {
      try {
        const res = await getUnreadCount()
        unreadCount.value = res.data || 0
      } catch { /* 静默忽略 */ }
    }
    poll()                         // 立即拉一次
    pollTimer = setInterval(poll, POLL_INTERVAL)
  }

  const cleanupPolling = () => {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  // ============== 主流程：建立 SSE ==============
  const initSse = () => {
    const token = storage.getToken()
    if (!token) {
      // 没登录，直接轮询（理论上不会触发，layout 只有登录后才渲染）
      fallbackToPolling()
      return
    }

    // URL 带 token —— EventSource 不支持自定义 header，这是行业标准做法
    const url = `${SSE_URL}?token=${encodeURIComponent(token)}`

    try {
      eventSource = new EventSource(url)
    } catch (e) {
      console.warn('[SSE] EventSource 构造失败', e)
      fallbackToPolling()
      return
    }

    // 连接超时兜底 —— 5 秒内没 open 说明 nginx/Tomcat/后端有问题
    const timeoutId = setTimeout(() => {
      if (eventSource && eventSource.readyState !== EventSource.OPEN) {
        eventSource.close()
        eventSource = null
        fallbackToPolling()
      }
    }, SSE_OPEN_TIMEOUT)

    eventSource.onopen = () => {
      clearTimeout(timeoutId)
      console.log('[SSE] 连接已建立')
    }

    // 业务事件：后端 emitter.send(SseEmitter.event().name('count').data('3'))
    eventSource.addEventListener('count', (e) => {
      const count = parseInt(e.data, 10)
      if (!isNaN(count)) {
        unreadCount.value = count
      }
    })

    // 通用 message 事件（后端没指定 .name() 时的默认事件名）
    eventSource.onmessage = (e) => {
      const count = parseInt(e.data, 10)
      if (!isNaN(count)) {
        unreadCount.value = count
      }
    }


    // ⭐ 后端主动通知 token 过期 → 关掉 EventSource，避免无限重连
    eventSource.addEventListener('error', (e) => {
      const data = e.data
      if (data === 'unauthorized') {
        console.warn('[SSE] token 已过期，主动关闭连接')
        eventSource.close()
        eventSource = null
        // 不要降级轮询了——轮询也会 401，直接静默即可
        // 如果后续有 refreshToken 逻辑，可以在这里触发重新建连
      }
    })

    // 连接关闭或网络错误 —— EventSource 本身会自动重连
    eventSource.onerror = () => {
      clearTimeout(timeoutId)
      if (eventSource) {
        // readyState=2 (CLOSED) 说明已断开且不再重连 → 降级
        if (eventSource.readyState === EventSource.CLOSED) {
          eventSource.close()
          eventSource = null
          fallbackToPolling()
        }
        // readyState=0 (CONNECTING) 说明正在重连中，让它再等等
        // readyState=1 (OPEN) 不应走到 onerror，但保险起见也让它重连
      }
    }
  }

  const close = () => {
    cleanupPolling()
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  // 模块级立即启动（不依赖 onMounted，composable 被调用时就启动）
  initSse()

  // 防漏：即使外部没调，也在组件卸载时清理
  onBeforeUnmount(close)

  return { unreadCount, close, isFallback: () => isFallback }
}
