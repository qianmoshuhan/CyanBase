import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './assets/styles/index.css'
import formCreatePlugin from './plugins/formCreate'
import { setupPermissionDirective } from './directives/permission'

const app = createApp(App)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.use(formCreatePlugin)
setupPermissionDirective(app)

app.mount('#app')

// 移除原生全屏 loading：正常路径，mount 完成立即移除
const _loading = document.getElementById('__app_loading__')
if (_loading) _loading.remove()

// 8 秒超时兜底：防止 JS 异常导致 loading 永久停留（首页加载慢 6~7 秒，不要设 5 秒）
setTimeout(() => {
  const _fallback = document.getElementById('__app_loading__')
  if (_fallback) _fallback.remove()
}, 8000)