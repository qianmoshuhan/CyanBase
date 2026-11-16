<template>
  <div class="login-container">
    <!-- 背景装饰光斑 -->
    <div class="bg-blob bg-blob-1"></div>
    <div class="bg-blob bg-blob-2"></div>
    <div class="bg-blob bg-blob-3"></div>

    <div class="login-box">
      <div class="login-header">
        <div class="logo-mark">
          <span class="logo-dot"></span>
        </div>
        <h1 class="title">CyanBase</h1>
        <p class="subtitle">青渊数字底座 · 平台管理</p>
      </div>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <!-- 登录模式 Tab 切换 -->
        <div class="login-mode-tabs">
          <div
            class="mode-tab"
            :class="{ active: loginForm.loginMode === 'account' }"
            @click="switchLoginMode('account')"
          >账号登录</div>
          <div
            class="mode-tab"
            :class="{ active: loginForm.loginMode === 'phone' }"
            @click="switchLoginMode('phone')"
          >手机登录</div>
        </div>

        <el-form-item v-if="loginForm.loginMode === 'account'" prop="userName">
          <el-input
            v-model="loginForm.userName"
            placeholder="请输入用户名"
            size="large"
            prefix-icon="User"
            autocomplete="off"
          />
        </el-form-item>
        <el-form-item v-else prop="phone">
          <el-input
            v-model="loginForm.phone"
            placeholder="请输入手机号"
            size="large"
            prefix-icon="Iphone"
            autocomplete="off"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            prefix-icon="Lock"
            show-password
            autocomplete="off"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-button"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 滑块验证码弹窗 — class 属性直接命中根元素 -->
      <!-- 滑块验证码弹窗
           ⚠️ 不用 append-to-body！原因：
           append-to-body 会把 dialog DOM 移到 body 下，关闭动画没完成时又开新 dialog
           会导致 body 下多个 .captcha-dialog DOM 叠加，旧 dialog 动画完成触发的 @close
           会把新 dialog 也关了！同时多个 DOM 也会导致 document.querySelector 选错元素。
           去掉 append-to-body 后 dialog 在 Vue 组件内部，Vue 的虚拟 DOM 会正确处理。
           全局无 scoped style 块（L650）仍然能命中 .captcha-dialog 类名。 -->
      <el-dialog
        v-model="showCaptchaDialog"
        top="10vh"
        :close-on-click-modal="false"
        :close-on-press-escape="false"
        :show-close="false"
        class="captcha-dialog"
      >
        <div class="slider-captcha" :class="{ 'is-ok': captchaOk }">
          <div class="sc-img-wrap" :class="{ dragging }" :style="{ width: trackWidth + 'px' }">
            <!-- v-if 关键！closeCaptchaDialog 清空 captchaBg 时 img 直接从 DOM 移除，
                 不会出现 src="" 浏览器加载当前页面当图片 → broken icon 的问题 -->
            <img v-if="captchaBg" ref="bgRef" :src="captchaBg" class="sc-img" alt="滑块背景" @load="onImgLoad" />
            <img v-if="captchaPuzzle" :src="captchaPuzzle" class="sc-puzzle" :style="{ left: puzzleLeft + 'px', top: puzzleTop + 'px', transform: `scale(${scaleFactor})`, transformOrigin: 'top left' }" alt="缺口" />
            <span class="sc-refresh" title="刷新验证码" @click.stop="loadCaptcha()">↻</span>
          </div>
          <div class="sc-track" :style="{ width: trackWidth + 'px' }">
            <div class="sc-track-bar">
              <span class="sc-tip">{{ captchaTip }}</span>
              <div class="sc-fill" :style="{ width: dragX + 'px' }"></div>
              <div class="sc-btn" :class="{ dragging }" :style="{ left: dragX + 'px' }" @pointerdown="startDrag">
                <span class="sc-icon">{{ captchaOk ? '✔' : '→' }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-dialog>

      <!-- ⭐ 平台主体选活跃租户弹窗（和 tenantLogin 同款 el-dialog + v-for） -->
      <el-dialog
        v-model="tenantDialogVisible"
        title="选择活跃租户"
        width="460px"
        :close-on-click-modal="false"
        :close-on-press-escape="true"
        center
      >
        <p class="dialog-tip">
          当前账号是平台主体，可访问 {{ scopeTenants.length }} 个租户，请选择审计目标：
        </p>
        <div class="tenant-list">
          <div
            v-for="item in scopeTenants"
            :key="item.id"
            class="tenant-item"
            :class="{ active: selectedTenantId === item.id }"
            @click="selectedTenantId = item.id"
          >
            <el-radio
              :model-value="selectedTenantId"
              :label="item.id"
              @change="selectedTenantId = item.id"
            >
              <div class="tenant-info">
                <div class="tenant-name">{{ item.tenantName }}</div>
                <div class="tenant-nick">{{ item.tenantCode }} · ID:{{ item.id }}</div>
              </div>
            </el-radio>
          </div>
        </div>
        <template #footer>
          <el-button @click="handleCancelTenantSelect">取消</el-button>
          <el-button type="primary" :loading="tenantSwitchLoading" @click="handleConfirmTenant">
            确认进入
          </el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getCaptcha, checkCaptcha, getTenantScopes } from '@/api/auth'
import { ElMessage } from 'element-plus'
import storage from '@/utils/storage'
import { getConfigByKey } from '@/api/config'
import CryptoJS from 'crypto-js'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)
const showCaptchaDialog = ref(false)

const loginForm = reactive({
  loginMode: 'account',   // 'account' | 'phone'
  userName: '',
  phone: '',
  password: '',
  credentialKey: ''
})

// 动态校验规则：根据登录模式切换必填字段
const loginRules = computed(() => ({
  userName: [
    { required: loginForm.loginMode === 'account', message: '请输入用户名', trigger: 'blur' }
  ],
  phone: [
    { required: loginForm.loginMode === 'phone', message: '请输入手机号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}))

// 切换登录模式：清空另一模式的输入值
function switchLoginMode(mode) {
  if (loginForm.loginMode === mode) return
  loginForm.loginMode = mode
  if (mode === 'account') loginForm.phone = ''
  else loginForm.userName = ''
}

// Console 打印的兜底账号（后端配置 key 缺失或接口异常时使用，与 loginForm 默认值解耦）
const DEMO_FALLBACK = { username: 'admin', password: '654123' }

// ==================== 滑块验证码（加载 → 拖拽 → 校验拿凭证 → 随登录提交） ====================
const captchaToken = ref('')
const captchaSecret = ref('') // get 返回的 AES secretKey，坐标加密用
const captchaBg = ref('')
const captchaPuzzle = ref('')
const bgRef = ref(null)
const captchaOk = ref(false)
const captchaTip = ref('请先完成滑块验证')
const dragging = ref(false)
const dragX = ref(0)          // 拖拽位移（显示像素）
const trackWidth = ref(360)   // 滑块轨道宽度（弹窗 400px - 左右各 15px 边距）
const scaleFactor = ref(1)    // 显示尺寸 / 原生图像尺寸 的比例
let dragStartX = 0
let dragTrack = []
const gapX = ref(0)   // 缺口在图像原生像素中的 X
const gapY = ref(0)   // 缺口在图像原生像素中的 Y
const gapCX = ref(0)  // 缺口凸块中心 X
const gapCY = ref(0)  // 缺口凸块中心 Y（引擎校验 y 常用）
let serverGameY = null  // 后端返回的精确 gameY（scanJigsaw 扫拼图 Y 有误差 3-5px，Y 校验无容差！）
let captchaCancelled = false  // 用户主动关闭弹窗的标志，防止 loadCaptcha 异步返回后仍更新状态

// 滑块用 transform: scale(scaleFactor) 和底图同比例缩放 → 消除"大一圈"
// 缩放后 jigsaw CSS 尺寸 = 原生 * scaleFactor = 缺口 jigsaw CSS 尺寸 ✓
// 滑块左边缘（视觉）= left + gapX * scaleFactor = dragX → left = dragX - gapX * scaleFactor
const puzzleLeft = computed(() => dragX.value - gapX.value * scaleFactor.value)
const puzzleTop = computed(() => (serverGameY - gapY.value) * scaleFactor.value)  // 固定 Y 对齐缺口

// 兼容 AJ-Captcha 返回的 base64（可能带/不带 data: 前缀）
const toDataUrl = (b64) => b64 && (b64.startsWith('data:') ? b64 : 'data:image/png;base64,' + b64)

// anji-plus 校验端要求 pointJson 先做 Base64 编码（UTF-8 安全）
const encodeBase64 = (str) => {
  try {
    return btoa(unescape(encodeURIComponent(str)))
  } catch {
    return btoa(str)
  }
}

// 扫描缺口图，找出缺口形状左上角坐标（原生像素），用于对齐与校验坐标
function scanJigsaw(src) {
  return new Promise((resolve) => {
    if (!src) return resolve(null)
    const img = new Image()
    img.onload = () => {
      try {
        const c = document.createElement('canvas')
        c.width = img.naturalWidth
        c.height = img.naturalHeight
        const ctx = c.getContext('2d')
        ctx.drawImage(img, 0, 0)
        const d = ctx.getImageData(0, 0, c.width, c.height).data
        let minX = c.width
        let minY = c.height
        let maxX = 0
        let maxY = 0
        for (let y = 0; y < c.height; y++) {
          for (let x = 0; x < c.width; x++) {
            if (d[(y * c.width + x) * 4 + 3] > 30) {
              if (x < minX) minX = x
              if (y < minY) minY = y
              if (x > maxX) maxX = x
              if (y > maxY) maxY = y
            }
          }
        }
        resolve({
          x: minX === c.width ? 0 : minX,
          y: minY === c.height ? 0 : minY,
          // 缺口凸块中心（引擎校验 y 常用中心点），避免左上角与中心差半个缺口高导致超差
          cx: minX + Math.round((maxX - minX) / 2),
          cy: minY + Math.round((maxY - minY) / 2)
        })
      } catch {
        resolve(null)
      }
    }
    img.onerror = () => resolve(null)
    img.src = src
  })
}

async function loadCaptcha() {
  captchaCancelled = false  // 新一轮开始，重置取消标志
  captchaOk.value = false
  captchaTip.value = '加载中...'
  dragX.value = 0
  dragTrack = []
  loginForm.credentialKey = ''
  try {
    const res = await getCaptcha()
    // ⚠️ 关键：await 之后检测是否已被用户关闭！
    // 如果用户在 await 期间点了 X，captchaCancelled 为 true → 直接 return，
    // 不再更新 captchaBg 等，避免 "关闭后又被异步刷新" 的幽灵弹窗 bug
    if (captchaCancelled) return
    captchaToken.value = res.data.token
    captchaSecret.value = res.data.secretKey
    captchaBg.value = toDataUrl(res.data.backgroundImage)
    captchaPuzzle.value = toDataUrl(res.data.puzzleImage)
    const pos = await scanJigsaw(captchaPuzzle.value)
    if (captchaCancelled) return  // scanJigsaw 也是异步，再检测一次
    gapX.value = pos ? pos.x : 0
    gapY.value = pos ? pos.y : 0
    gapCX.value = pos ? pos.cx : 0
    gapCY.value = pos ? pos.cy : 0
    serverGameY = res.data.gameY
    console.log('[滑块] scanJigsaw gapX=', gapX.value, 'gapY=', gapY.value,
      '| 后端 gameX=', res.data.gameX, 'gameY=', serverGameY)
    captchaTip.value = '请拖动滑块完成拼图'
  } catch {
    if (!captchaCancelled) captchaTip.value = '验证码加载失败，点击重试'
  }
}

function onImgLoad() {
  const img = bgRef.value
  if (!img || !img.naturalWidth) return
  trackWidth.value = img.clientWidth
  scaleFactor.value = img.clientWidth / img.naturalWidth
}

function startDrag(e) {
  if (captchaOk.value || !captchaToken.value) return
  e.preventDefault()
  dragging.value = true
  dragX.value = 0
  dragTrack = []
  dragStartX = e.clientX
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', endDrag)
  window.addEventListener('pointercancel', endDrag)
}

function onDragMove(e) {
  if (!dragging.value) return
  const max = trackWidth.value - 40
  let x = e.clientX - dragStartX
  x = Math.max(0, Math.min(max, x))
  dragX.value = x
  // 坐标换算回图像原生像素，供服务端偏移/轨迹校验
  // 注意：y 方向用 gapY（缺口在原图上的 Y），因为拼图块对齐后 y 应该等于 gameY
  dragTrack.push({ x: Math.round(x / scaleFactor.value), y: gapY.value, t: Date.now() })
}

async function endDrag() {
  if (!dragging.value) return
  dragging.value = false
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', endDrag)
  window.removeEventListener('pointercancel', endDrag)
  await doCheck()
}

async function doCheck() {
  // AJ-Captcha blockPuzzle 后端校验逻辑：把 pointJson.x 与 get 阶段保存的 gameX
  // （缺口在背景图上的左上角 X 坐标）做差值比较，容差 = slip-offset。
  // 前端只需提交「滑动偏移量」= dragX/scaleFactor，不需要加 gapCX（缺口自身偏移）。
  // 之前多加了 gapCX ≈ 23px，即使后端 slip-offset 放宽到 15 也必然超差 → repCode=6111。
  const x = Math.round(dragX.value / scaleFactor.value)
  // y 优先用后端精确 gameY（scanJigsaw 扫拼图 Y 有 ±3-5px 误差，后端 Y 校验无容差！）
  const y = serverGameY != null ? serverGameY : gapY.value
  const track = dragTrack.slice()
  // 诊断用：打印本次提交坐标与 secretKey 明文，便于定位偏差      
  try {
    console.log('[滑块诊断] 提交 x =', x, 'y =', y, 'gapX =', gapX.value, 'gapY =', gapY.value, 'gapCX =', gapCX.value, 'gapCY =', gapCY.value,
      'scaleFactor =', scaleFactor.value, 'track点数 =', track.length)
    console.log('[滑块诊断] secretKey =', captchaSecret.value, '| base64解码 =',
      atob(captchaSecret.value.replace(/-/g, '+').replace(/_/g, '/').split('=')[0]))
  } catch (e) {
    console.log('[滑块诊断] secretKey 非标准base64，跳过解码：', e && e.message)
  }
  // 轨迹过短会导致服务端校验失败，补足采样点
  while (track.length < 10) track.push({ x, y, t: Date.now() + track.length })
  captchaTip.value = '验证中...'
  try {
    // AJ 1.3.0 校验端强制用 secretKey 做 AES 解密坐标（aes-status 关不掉），
    // 故前端用同一 secretKey 对坐标 JSON 做 AES(ECB/PKCS7) 加密后回传
    const plain = JSON.stringify({ x, y, track })
    // ====== 调试：打印加密前的明文（F12 直接看到提交的真实 x/y/track）======
    console.log('[滑块明文] plain =', plain)
    console.log('[滑块明文] 末点 x=', x, ' y=', y, ' track末点=', track[track.length - 1])
    const encrypted = CryptoJS.AES.encrypt(
      CryptoJS.enc.Utf8.parse(plain),
      CryptoJS.enc.Utf8.parse(captchaSecret.value),
      { mode: CryptoJS.mode.ECB, padding: CryptoJS.pad.Pkcs7 }
    ).toString()
    const res = await checkCaptcha({
      token: captchaToken.value,
      pointJson: encrypted,
      secretKey: captchaSecret.value
    })
    console.log('[滑块校验] check 返回 res =', res)
    console.log('[滑块校验] res.data =', res?.data)
    loginForm.credentialKey = res?.data?.credentialKey || ''
    console.log('[滑块校验] credentialKey =', loginForm.credentialKey)
    captchaOk.value = true
    captchaTip.value = '✓ 验证通过'
    isAutoClosing = true
    showCaptchaDialog.value = false
    window.removeEventListener('pointermove', onDragMove)
    window.removeEventListener('pointerup', endDrag)
    window.removeEventListener('pointercancel', endDrag)
    // ⭐ 直接在 doCheck 里登录，不再调 handleLogin！
    // 原因：handleLogin 里有 loginFormRef.value.validate 校验，如果表单状态有问题
    // 可能返回 valid=false 导致什么都不做，credentialKey 白设了
    console.log('[滑块校验] 验证成功！开始登录...')
    loading.value = true
    try {
      const loginBody = {
        password: loginForm.password,
        credentialKey: loginForm.credentialKey
      }
      if (loginForm.loginMode === 'phone') loginBody.phone = loginForm.phone
      else loginBody.userName = loginForm.userName
      const loginRes = await userStore.login(loginBody)
      const isMobile = window.innerWidth <= 768
      const redirect = route.query.redirect || (isMobile ? '/mobile/home' : '/home')
      await handlePostLogin(redirect, loginRes)
    } catch (error) {
      console.error('[滑块校验] 登录失败：', error)
      loginForm.credentialKey = ''
      ElMessage.error('登录失败：' + (error.message || '未知错误'))
    } finally {
      loading.value = false
    }
  } catch (err) {
    // 透出后端失败原因，便于定位
    console.error('[滑块校验] 失败原因：', err && err.message)
    loginForm.credentialKey = ''
    captchaTip.value = (err && err.message) || '验证失败，请重试'
    await loadCaptcha()
  }
}
// ==================== 滑块验证码 ====================

let isAutoClosing = false  // doCheck 成功后自动关弹窗的标志

// ==================== ⭐ 平台主体选租户 dialog 状态 ====================
const tenantDialogVisible = ref(false)
const scopeTenants = ref([])          // 登录响应里直接带的 tenants 列表（后端 step='select-tenant' 分支返回）
const selectedTenantId = ref(null)
const tenantSwitchLoading = ref(false)
let pendingRedirect = ''
let pendingUserId = null               // 登录响应返回的 userId，用于 switchTenantToken 接口

// ==================== ⭐ 平台主体选租户流程 ====================
/**
 * 登录成功后的统一处理：
 *   1. 后端响应里 step='select-tenant' → 平台主体，弹窗等用户选租户换 token
 *   2. 否则 → fetchUserInfo + 正常跳转
 */
async function handlePostLogin(redirect, loginRes) {
  storage.setLoginType('admin')

  // ⭐ 后端 step='select-tenant' → 平台主体，直接用响应里的 tenants 弹窗
  if (loginRes?.data?.step === 'select-tenant') {
    const tenants = loginRes.data.tenants || []
    if (tenants.length === 0) {
      ElMessage.warning('当前平台主体未分配任何有效租户，请联系管理员配置后重试')
      userStore.clearAuth()
      return
    }
    pendingUserId = loginRes.data.userId
    scopeTenants.value = tenants
    selectedTenantId.value = tenants[0]?.id ?? null
    pendingRedirect = redirect
    tenantDialogVisible.value = true
    return  // 等用户在 dialog 里选
  }

  // 超管 / 租户用户 —— fetchUserInfo + 直接跳
  await userStore.fetchUserInfo()
  ElMessage.success('登录成功')
  router.push(redirect)
}

/** 用户点取消 → 退出登录 */
function handleCancelTenantSelect() {
  tenantDialogVisible.value = false
  ElMessage.info('已取消，退出登录')
  userStore.clearAuth()
}

/** 用户点确认 → switchTenantToken 换双 token + 重新拉菜单池 → 跳转 */
async function handleConfirmTenant() {
  if (!selectedTenantId.value) {
    ElMessage.warning('请选择一个租户')
    return
  }
  tenantSwitchLoading.value = true
  try {
    // 用登录响应里的 userId + 滑块凭证走首次换 token 接口
    await userStore.switchTenantTokenAndUpdateToken(
      pendingUserId,
      selectedTenantId.value,
      loginForm.credentialKey
    )
    loginForm.credentialKey = ''  // 凭证已被后端 consume，清空
    await userStore.fetchUserInfo()
    tenantDialogVisible.value = false
    const tenantName = scopeTenants.value.find(t => t.id === selectedTenantId.value)?.tenantName || ''
    ElMessage.success(`进入租户 ${tenantName} 成功`)
    router.push(pendingRedirect)
  } catch (err) {
    ElMessage.error('切换租户失败：' + (err.message || ''))
    userStore.clearAuth()
    tenantDialogVisible.value = false
  } finally {
    tenantSwitchLoading.value = false
  }
}

/**
 * 弹窗关闭时的清理函数 — @close 触发就说明 dialog 肯定要关了！
 *
 * ⚠️ 极其精简：只做两件事！
 *   1. showCaptchaDialog.value = false  ← 确保 Vue 状态正确
 *   2. captchaCancelled = true          ← 通知还在跑的异步（loadCaptcha/doCheck）不要回写
 *
 * ⚠️ 绝对不要清 captchaBg/captchaPuzzle/captchaTip/gapX 等变量！
 * 原因：el-dialog 的关闭动画期间 DOM 还在页面上，
 * 清图片 → 底图消失只剩导轨文字 = 用户看到的"弹窗没了但还留一个文字"！
 * 这些变量留着完全无害——下次 loadCaptcha() 开头会全部覆盖。
 */
function closeCaptchaDialog() {
  console.log('[滑块] closeCaptchaDialog, isAutoClosing=', isAutoClosing)

  // 场景①：doCheck 成功后自动关，credentialKey 刚设好，什么都不要动！
  if (isAutoClosing) {
    isAutoClosing = false
    return
  }

  // 场景②：用户主动点 X 关闭
  // 只设这两个！其他变量一律保留！
  showCaptchaDialog.value = false
  captchaCancelled = true
}

onMounted(async () => {
  let username = DEMO_FALLBACK.username
  let password = DEMO_FALLBACK.password
  try {
    const [u, p] = await Promise.all([
      getConfigByKey('demo.username'),
      getConfigByKey('demo.password')
    ])
    if (u && u.data) username = u.data
    if (p && p.data) password = p.data
  } catch {
    // 接口异常（如后端未启动等），静默兜底，不影响登录页
  }
  console.group('演示项目登录账号')
  console.log('账号：' + username)
  console.log('密码：' + password)
  console.log('提示：本账号仅用于项目简历演示')
  console.groupEnd()
  // 滑块改为点击登录后弹窗按需加载，不再自动初始化
})

/**
 * 登录入口 — 两条路径：
 *   ① 有 credentialKey → 直接调后端登录
 *   ② 无 credentialKey → 弹滑块弹窗，用户完成后 doCheck 会自动回调 handleLogin
 *
 * ⚠️ 不再用 isLoggingIn 锁！原因：
 *   isLoggingIn 在 "弹滑块 → 等用户拖 → doCheck 成功 → 调 handleLogin" 这条链中
 *   会导致 handleLogin 被自己的锁挡住 → 验证成功了但不登录！
 *   防重入改用更简单的方式：
 *   - 点登录按钮时检查 showCaptchaDialog 是否已开
 *   - doCheck 成功后直接调 handleLogin，不需要解锁任何东西
 */
const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    // === 路径 1：有凭证 → 直接登录 ===
    if (loginForm.credentialKey) {
      loading.value = true
      try {
        const loginBody = {
          password: loginForm.password,
          credentialKey: loginForm.credentialKey
        }
        if (loginForm.loginMode === 'phone') loginBody.phone = loginForm.phone
        else loginBody.userName = loginForm.userName
        const loginRes = await userStore.login(loginBody)
        const isMobile = window.innerWidth <= 768
        const redirect = route.query.redirect || (isMobile ? '/mobile/home' : '/home')
        await handlePostLogin(redirect, loginRes)
      } catch (error) {
        loginForm.credentialKey = ''  // 凭证失效，清了让用户重新验证
      } finally {
        loading.value = false
      }
      return
    }

    // === 路径 2：无凭证 → 弹滑块 ===
    if (showCaptchaDialog.value) {
      console.log('[登录] 滑块弹窗已打开，忽略')
      return
    }
    console.log('[登录] 无凭证，弹滑块弹窗')
    showCaptchaDialog.value = true
    await nextTick()
    const closeBtn = document.querySelector('.captcha-dialog .el-dialog__headerbtn')
    if (closeBtn) closeBtn.title = '关闭'
    await loadCaptcha()
    if (!showCaptchaDialog.value) {
      console.log('[滑块] 用户已主动关闭弹窗，本次登录中断')
      return
    }
    // 等用户拖滑块 → doCheck 成功后会自动调 handleLogin（此时 credentialKey 已有了，走路径 1）
  })
}
</script>

<style scoped>
.login-container {
  position: relative;
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0958d9 0%, #1677ff 40%, #69b1ff 100%);
  overflow: hidden;
}

/* 背景装饰光斑 — 纯 CSS，不引入任何图片 */
.bg-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
  pointer-events: none;
}
.bg-blob-1 {
  width: 420px;
  height: 420px;
  background: #69b1ff;
  top: -120px;
  left: -100px;
  animation: blobFloat 12s ease-in-out infinite;
}
.bg-blob-2 {
  width: 360px;
  height: 360px;
  background: #91caff;
  bottom: -100px;
  right: -80px;
  animation: blobFloat 15s ease-in-out infinite reverse;
}
.bg-blob-3 {
  width: 280px;
  height: 280px;
  background: rgba(255,255,255,0.35);
  top: 40%;
  left: 55%;
  animation: blobFloat 18s ease-in-out infinite;
}
@keyframes blobFloat {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(30px, -40px) scale(1.08); }
}

.login-box {
  position: relative;
  width: 420px;
  max-width: 92%;
  padding: 44px 40px 36px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 16px;
  box-shadow:
    0 20px 60px rgba(9, 88, 217, 0.35),
    0 4px 16px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.5);
  animation: boxFadeIn 0.6s ease-out;
}
@keyframes boxFadeIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.logo-mark {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}
.logo-dot {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, #1677ff, #69b1ff);
  box-shadow: 0 6px 20px rgba(22, 119, 255, 0.45);
  position: relative;
}
.logo-dot::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 20px;
  height: 4px;
  border-radius: 2px;
  background: #fff;
  transform: translate(-50%, -50%);
  box-shadow: 0 -8px 0 #fff, 0 8px 0 #fff;
}

.title {
  color: #1a1a2e;
  font-size: 26px;
  font-weight: 700;
  margin: 0;
  letter-spacing: 1px;
}

.subtitle {
  color: #8c8c8c;
  font-size: 13px;
  margin: 8px 0 0 0;
  letter-spacing: 0.5px;
}

.login-form {
  margin-top: 16px;
}

/* ===== 登录模式 Tab 切换 —— 原来完全缺失！===== */
.login-mode-tabs {
  display: flex;
  background: #f5f7fa;
  border-radius: 10px;
  padding: 4px;
  margin-bottom: 24px;
  gap: 4px;
}
.mode-tab {
  flex: 1;
  height: 40px;
  line-height: 40px;
  text-align: center;
  font-size: 14px;
  color: #606266;
  border-radius: 7px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  font-weight: 500;
  user-select: none;
}
.mode-tab:hover {
  color: #1677ff;
}
.mode-tab.active {
  background: #fff;
  color: #1677ff;
  font-weight: 600;
  box-shadow:
    0 2px 8px rgba(22, 119, 255, 0.15),
    0 1px 2px rgba(0, 0, 0, 0.06);
  transform: translateY(-1px);
}

/* ===== 输入框聚焦美化 ===== */
.login-form :deep(.el-input__wrapper) {
  border-radius: 10px;
  transition: all 0.3s;
  box-shadow: 0 0 0 1px #e4e7ed inset;
}
.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #4096ff inset;
}
.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px #1677ff inset,
    0 0 0 3px rgba(22, 119, 255, 0.12);
}

/* ===== 登录按钮美化 ===== */
.login-button {
  width: 100%;
  height: 46px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 10px;
  letter-spacing: 4px;
  background: linear-gradient(135deg, #1677ff 0%, #4096ff 100%);
  border: none;
  box-shadow: 0 6px 20px rgba(22, 119, 255, 0.35);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.login-button:hover {
  background: linear-gradient(135deg, #4096ff 0%, #69b1ff 100%) !important;
  box-shadow: 0 8px 28px rgba(22, 119, 255, 0.45);
  transform: translateY(-2px);
}
.login-button:active {
  transform: translateY(0);
  box-shadow: 0 4px 14px rgba(22, 119, 255, 0.3);
}

/* 注：captcha-dialog 样式移到文件末尾独立的无 scoped style 块 */
.slider-captcha {
  flex-shrink: 0;
}
.sc-img-wrap {
  position: relative;
  overflow: hidden;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  background: #f5f7fa;
}
.sc-img {
  display: block;
  width: 100%;
  height: auto;
}
.sc-puzzle {
  position: absolute;
  left: 0;
  top: 0;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  transition: left 0.05s linear;
}
.sc-img-wrap.dragging .sc-puzzle {
  transition: none;
}
.slider-captcha.is-ok .sc-puzzle {
  opacity: 0.55;
}
.sc-refresh {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 4px;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
}
.sc-track {
  height: 48px;
  margin-top: 12px;
  position: relative;
}
.sc-track-bar {
  position: absolute;
  inset: 0;
  background: #eef1f6;
  border-radius: 24px;
  overflow: hidden;
}
.sc-tip {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #909399;
}
.sc-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: rgba(22, 119, 255, 0.22);
}
.sc-btn {
  position: absolute;
  left: 0;
  top: 0;
  width: 48px;
  height: 48px;
  background: #fff;
  border: 1px solid #d0d5dd;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
  user-select: none;
  -webkit-user-select: none;
  touch-action: none;
  will-change: left;
}
.sc-btn:active {
  cursor: grabbing;
}
.sc-icon {
  color: #1677ff;
  font-weight: 700;
  font-size: 18px;
}
.slider-captcha.is-ok .sc-fill {
  background: rgba(103, 194, 58, 0.4);
}
.slider-captcha.is-ok .sc-icon {
  color: #67c23a;
}
.slider-captcha.is-ok .sc-tip {
  color: #67c23a;
}

/* ===== 平台主体选租户 dialog 样式（和 tenantLogin 同款） ===== */
.dialog-tip {
  color: #606266;
  font-size: 14px;
  margin: 0 0 16px;
}
.tenant-list {
  max-height: 320px;
  overflow-y: auto;
}
.tenant-item {
  padding: 12px 16px;
  margin-bottom: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.tenant-item:hover {
  border-color: #409eff;
  background-color: #f0f7ff;
}
.tenant-item.active {
  border-color: #409eff;
  background-color: #ecf5ff;
}
.tenant-info {
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
}
.tenant-name {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}
.tenant-nick {
  font-size: 13px;
  color: #909399;
}

/* ===== 手机端响应式美化 ===== */
@media (max-width: 768px) {
  .bg-blob-1 { width: 280px; height: 280px; top: -80px; left: -60px; }
  .bg-blob-2 { width: 240px; height: 240px; bottom: -60px; right: -40px; }
  .bg-blob-3 { display: none; }

  .login-box {
    width: 100%;
    max-width: 88%;
    padding: 32px 24px 28px;
    border-radius: 20px;
    box-shadow: 0 16px 48px rgba(9, 88, 217, 0.3);
  }

  .login-header {
    margin-bottom: 24px;
  }
  .logo-dot {
    width: 42px;
    height: 42px;
    border-radius: 12px;
  }
  .logo-dot::after {
    width: 16px;
    height: 3px;
    box-shadow: 0 -7px 0 #fff, 0 7px 0 #fff;
  }
  .login-header .title {
    font-size: 22px;
    letter-spacing: 2px;
  }
  .login-header .subtitle {
    font-size: 12px;
    color: #909399;
    margin-top: 6px;
  }

  .login-mode-tabs {
    margin-bottom: 20px;
  }
  .mode-tab {
    height: 38px;
    line-height: 38px;
    font-size: 13px;
  }

  .login-form :deep(.el-input__wrapper) {
    border-radius: 10px;
  }

  .login-button {
    height: 46px;
    font-size: 15px;
    font-weight: 600;
    border-radius: 12px;
    letter-spacing: 3px;
  }
}
</style>

<!--
  =========================================================
  弹窗全局样式（不加 scoped！）
  append-to-body 把 dialog 丢到 <body> 下，脱离 Vue scoped 作用域，
  所以必须用独立的无 scoped style 块。
  =========================================================
-->
<style>
/* 外层 overlay — 也要覆盖防止干扰 */
.el-overlay:has(.captcha-dialog) {
  display: flex !important;
  justify-content: center !important;
  align-items: flex-start !important;
}
/* dialog 本体 */
.captcha-dialog,
body .el-dialog.captcha-dialog {
  position: relative !important; /* 给 X 按钮的绝对定位做基准 */
  margin: 10vh auto 0 !important;
  padding: 0 !important;
  border: none !important;
  border-radius: 12px !important;
  box-sizing: border-box !important;
  width: fit-content !important;
  max-width: 90vw !important;
}
/* header：不用 display:none（会把 X 按钮一起藏了！）
   改用 height:0 让标题消失，overflow:visible 让 X 按钮溢出显示 */
.captcha-dialog > .el-dialog__header,
.captcha-dialog .el-dialog__header {
  padding: 0 !important;
  margin: 0 !important;
  height: 0 !important;
  min-height: 0 !important;
  overflow: visible !important;
  border-bottom: none !important;
}
/* 隐藏 header 内的标题文字 */
.captcha-dialog .el-dialog__title,
body .captcha-dialog .el-dialog__title {
  display: none !important;
}
/* 隐藏 footer */
.captcha-dialog > .el-dialog__footer,
.captcha-dialog .el-dialog__footer {
  display: none !important;
  padding: 0 !important;
  margin: 0 !important;
  height: 0 !important;
}
/* body — flex 绝对居中 */
.captcha-dialog > .el-dialog__body,
.captcha-dialog .el-dialog__body {
  padding: 20px !important;
  margin: 0 !important;
  box-sizing: border-box !important;
  display: flex !important;
  justify-content: center !important;
  align-items: flex-start !important;
  width: auto !important;
}
/* 右上角关闭按钮：绝对定位到弹窗右上角，样式优化
   tooltip "关闭"：需在 JS 中给按钮添加 title="关闭" 属性（CSS 无法设置原生 tooltip） */
.captcha-dialog .el-dialog__headerbtn,
.captcha-dialog > .el-dialog__headerbtn {
  position: absolute !important;
  top: 6px !important;
  right: 6px !important;
  width: 24px !important;
  height: 24px !important;
  line-height: 24px !important;
  font-size: 16px !important;
  border-radius: 4px !important;
  background: rgba(255, 255, 255, 0.85) !important;
  color: #909399 !important;
  transition: all 0.2s !important;
}
.captcha-dialog .el-dialog__headerbtn:hover,
.captcha-dialog > .el-dialog__headerbtn:hover {
  color: #409eff !important;
  background: #fff !important;
}
/* 内部滑块容器 — flex-shrink 防止被压缩 */
.captcha-dialog .slider-captcha {
  flex-shrink: 0;
}
</style>