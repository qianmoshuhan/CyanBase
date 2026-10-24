import { useUserStore } from '@/store/user'

/**
 * v-hasPermi="'system:user:add'"  —— 单权限（或）
 * v-hasPermi="['system:user:add','system:user:edit']" —— 多权限（任一即可）
 *
 * 没有权限时：按钮会被隐藏（display:none），不会向 DOM 提交任何点击事件。
 * 后端权限码从 LoginUser.permissions 下发，user store 统一维护。
 * 平台超管自动拥有 '*:*:*' 通配。
 */
function check(el, binding) {
  const store = useUserStore()
  const value = binding.value
  if (!value) return

  const permList = Array.isArray(value) ? value : [value]
  const hasAny = permList.some(p => store.hasPerm(p))
  if (!hasAny) {
    // 隐藏但不移除元素（避免 VDOM diff 混乱）
    el.style.display = 'none'
  } else {
    // 确保有权限时不被残留样式隐藏
    el.style.display = ''
  }
}

export const hasPermi = {
  mounted(el, binding) { check(el, binding) },
  updated(el, binding) { check(el, binding) }
}

/** 全局注册入口：在 main.js 里 import 并 app.use(permissions) */
export function setupPermissionDirective(app) {
  app.directive('hasPermi', hasPermi)
}
