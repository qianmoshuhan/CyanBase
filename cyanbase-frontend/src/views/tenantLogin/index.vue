<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1 class="title">CyanBase</h1>
        <p class="subtitle">租户登录</p>
      </div>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="phone">
          <el-input
            v-model="loginForm.phone"
            placeholder="请输入手机号"
            size="large"
            prefix-icon="Cellphone"
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

      <!-- 滑块验证码弹窗 — 与平台侧 login/index.vue 完全同款
           ⚠️ 不用 append-to-body！原因同平台侧：
           append-to-body 会把 dialog DOM 移到 body 下，关闭动画没完成时又开新 dialog
           会导致 body 下多个 .captcha-dialog DOM 叠加，旧 dialog 动画完成触发的 @close
           会把新 dialog 也关了！同时多个 DOM 也会导致 document.querySelector 选错元素。
           去掉 append-to-body 后 dialog 在 Vue 组件内部，Vue 的虚拟 DOM 会正确处理。
           全局无 scoped style 块（文件末尾）仍然能命中 .captcha-dialog 类名。 -->
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
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tenantLogin } from '@/api/tenantAuth'
import { useUserStore } from '@/store/user'
import storage from '@/utils/storage'
import { getCaptcha, checkCaptcha } from '@/api/auth'
import CryptoJS from 'crypto-js'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)

// 滑块验证码弹窗状态
const showCaptchaDialog = ref(false)

const loginForm = reactive({
  phone: '',
  password: '',
  credentialKey: ''  // 滑块验证成功后拿到的一次性凭证
})

// ==================== 滑块验证码 state（与平台侧 login/index.vue 完全同款） ====================
const captchaToken = ref('')
const captchaSecret = ref('') // get 返回的 AES secretKey，坐标加密用
const captchaBg = ref('')
const captchaPuzzle = ref('')
const bgRef = ref(null)
const captchaOk = ref(false)
const captchaTip = ref('请先完成滑块验证')
const dragging = ref(false)
const dragX = ref(0)          // 拖拽位移（显示像素）
const trackWidth = ref(360)   // 滑块轨道宽度（与平台侧一致：弹窗 400px - 左右各 15px 边距）
const scaleFactor = ref(1)    // 显示尺寸 / 原生图像尺寸 的比例
let dragStartX = 0
let dragTrack = []
const gapX = ref(0)   // 缺口在图像原生像素中的 X
const gapY = ref(0)   // 缺口在图像原生像素中的 Y
const gapCX = ref(0)  // 缺口凸块中心 X
const gapCY = ref(0)  // 缺口凸块中心 Y（引擎校验 y 常用）
let serverGameY = null  // 后端返回的精确 gameY（scanJigsaw 扫拼图 Y 有误差 3-5px，Y 校验无容差！）
let captchaCancelled = false  // 用户主动关闭弹窗的标志，防止 loadCaptcha 异步返回后仍更新状态
let isAutoClosing = false     // doCheck 成功后自动关弹窗的标志，区分自动/手动关闭

// 滑块用 transform: scale(scaleFactor) 和底图同比例缩放 → 消除"大一圈"
// 缩放后 jigsaw CSS 尺寸 = 原生 * scaleFactor = 缺口 jigsaw CSS 尺寸 ✓
// 滑块左边缘（视觉）= left + gapX * scaleFactor = dragX → left = dragX - gapX * scaleFactor
const puzzleLeft = computed(() => dragX.value - gapX.value * scaleFactor.value)
const puzzleTop = computed(() => (serverGameY - gapY.value) * scaleFactor.value)  // 固定 Y 对齐缺口
const toDataUrl = (b64) => b64 && (b64.startsWith('data:') ? b64 : 'data:image/png;base64,' + b64)

const loginRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

// ==================== 滑块验证码完整逻辑（和平台侧零差异） ====================
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
  e.preventDefault(); dragging.value = true; dragX.value = 0; dragTrack = []; dragStartX = e.clientX
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', endDrag); window.addEventListener('pointercancel', endDrag)
}
function onDragMove(e) {
  if (!dragging.value) return
  const max = trackWidth.value - 40
  let x = e.clientX - dragStartX; x = Math.max(0, Math.min(max, x)); dragX.value = x
  dragTrack.push({ x: Math.round(x / scaleFactor.value), y: gapY.value, t: Date.now() })
}
async function endDrag() {
  if (!dragging.value) return
  dragging.value = false
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', endDrag); window.removeEventListener('pointercancel', endDrag)
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
      secretKey: captchaSecret.value,
      scope: 'tenant'  // ⭐ 租户侧发 tenant 前缀的凭证
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

    // ⭐ 手机号全局唯一后，tenantLogin 永远命中一个账号，直接登录成功
    console.log('[滑块校验] 验证成功！开始租户登录...')
    loading.value = true
    try {
      const loginRes = await tenantLogin({
        phone: loginForm.phone,
        password: loginForm.password,
        credentialKey: loginForm.credentialKey
      })
      handleLoginSuccess(loginRes.data)  // 直接成功，后端已 consume 凭证
    } catch (error) {
      console.error('[滑块校验] 租户登录失败：', error)
      loginForm.credentialKey = ''  // 凭证被 consume / 密码错 → 清了让用户重新弹滑块
      ElMessage.error(error?.message || '登录失败，请稍后重试')
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

/**
 * 弹窗关闭时的清理函数 — @close 触发就说明 dialog 肯定要关了！
 *
 * ⚠️ 极其精简：只做两件事！
 *   1. showCaptchaDialog.value = false  ← 确保 Vue 状态正确
 *   2. captchaCancelled = true          ← 通知还在跑的异步（loadCaptcha/doCheck）不要回写
 *
 * ⚠️ 绝对不要清 captchaBg/captchaPuzzle/captchaTip/gapX/credentialKey 等变量！
 * 原因：el-dialog 的关闭动画期间 DOM 还在页面上，
 * 清图片 → 底图消失只剩导轨文字 = 用户看到的"弹窗没了但还留一个文字"！
 * 清 credentialKey → doCheck 刚设好凭证，close 事件触发就清 → 无限刷新循环！
 * 这些变量留着完全无害——下次 loadCaptcha() 开头会全部覆盖。
 */
function closeCaptchaDialog() {
  console.log('[滑块] closeCaptchaDialog, isAutoClosing=', isAutoClosing)

  // 场景①：doCheck 成功后自动关，credentialKey 刚设好，什么都不要动！
  if (isAutoClosing) {
    isAutoClosing = false
    return
  }

  // 场景②：用户主动点关闭（如果后续恢复按钮的话）
  // 只设这两个！其他变量一律保留！
  showCaptchaDialog.value = false
  captchaCancelled = true
}

/** 登录成功后的公共处理逻辑 */
const handleLoginSuccess = async (data) => {
  const { accessToken, refreshToken } = data
  // ⭐ 先同步 userStore（fetchUserInfo 在跳转前完成，避免右上角昵称短暂缺失）
  userStore.token = accessToken
  storage.setToken(accessToken)
  storage.setRefreshToken(refreshToken)
  storage.setLoginType('tenant')
  // ⭐ 立即拉完整 userInfo
  try {
    await userStore.fetchUserInfo()
  } catch (e) {
    console.error('拉取用户信息失败', e)
  }
  ElMessage.success('登录成功')
  const isMobile = window.innerWidth <= 768
  const redirect = route.query.redirect || (isMobile ? '/mobile/home' : '/home')
  router.push(redirect)
}

/**
 * 登录入口 — 两条路径：
 *   ① 有 credentialKey → 直接调 tenantLogin
 *   ② 无 credentialKey → 弹滑块弹窗，用户完成后 doCheck 会自动调 tenantLogin
 *
 * ⚠️ 防重入：弹窗已打开时忽略重复点击
 */
const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    // === 路径 1：有凭证 → 直接登录（手机号全局唯一，永远命中一个账号） ===
    if (loginForm.credentialKey) {
      loading.value = true
      try {
        const res = await tenantLogin({
          phone: loginForm.phone,
          password: loginForm.password,
          credentialKey: loginForm.credentialKey
        })
        handleLoginSuccess(res.data)
      } catch (error) {
        loginForm.credentialKey = ''  // 凭证失效，清了让用户重新验证
        ElMessage.error(error?.message || '登录失败，请稍后重试')
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
    await loadCaptcha()
    if (!showCaptchaDialog.value) {
      console.log('[滑块] 用户已主动关闭弹窗，本次登录中断')
      return
    }
    // 等用户拖滑块 → doCheck 成功后会自动走 tenantLogin（此时 credentialKey 已有了）
  })
}


</script>

<style scoped>
.login-container {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1677ff 0%, #4096ff 50%, #e6f4ff 100%);
}

.login-box {
  width: 420px;
  max-width: 92%;
  padding: 40px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

/* ===== 手机端响应式美化 ===== */
@media (max-width: 768px) {
  .login-container {
    background: linear-gradient(160deg, #4096ff 0%, #1677ff 60%, #0958d9 100%);
  }
  .login-box {
    width: 100%;
    max-width: 88%;
    padding: 32px 24px 24px;
    border-radius: 20px;
    box-shadow: 0 12px 48px rgba(0,0,0,0.25);
  }
  .login-header {
    margin-bottom: 28px;
  }
  .login-header .title {
    font-size: 24px;
    letter-spacing: 2px;
  }
  .login-header .subtitle {
    font-size: 13px;
    color: #909399;
    margin-top: 6px;
  }
  .login-form :deep(.el-input__wrapper) {
    border-radius: 10px;
    box-shadow: 0 0 0 1px #dcdfe6 inset;
  }
  .login-button {
    width: 100%;
    height: 46px;
    font-size: 16px;
    font-weight: 600;
    border-radius: 12px;
    letter-spacing: 2px;
  }
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.title {
  color: #303133;
  font-size: 28px;
  font-weight: bold;
  margin: 0;
}

.subtitle {
  color: #606266;
  font-size: 14px;
  margin: 8px 0 0 0;
}

.login-form {
  margin-top: 20px;
}

.full-width {
  width: 100%;
}

.login-button {
  width: 100%;
  font-size: 16px;
}

/* ===== 多租户选择弹窗样式 ===== */
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

/* ===== 滑块验证码样式（与平台侧 login/index.vue 完全同款） ===== */
/* 注：captcha-dialog 样式在文件末尾独立的无 scoped style 块 */
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