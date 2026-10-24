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
        <el-form-item prop="tenantId">
          <el-select
            v-model="loginForm.tenantId"
            placeholder="请选择租户"
            size="large"
            class="full-width"
          >
            <el-option
              v-for="item in tenantList"
              :key="item.id"
              :label="item.tenantName"
              :value="item.id"
              :disabled="item.status != 0"
            />
          </el-select>
        </el-form-item>
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            size="large"
            prefix-icon="User"
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
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTenantList, tenantLogin } from '@/api/tenantAuth'
import storage from '@/utils/storage'

const router = useRouter()
const route = useRoute()

const loginFormRef = ref(null)
const loading = ref(false)
const tenantList = ref([])

const loginForm = reactive({
  tenantId: '',
  username: '',
  password: ''
})

const loginRules = {
  tenantId: [
    { required: true, message: '请选择租户', trigger: 'change' }
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const loadTenantList = async () => {
  try {
    const res = await getTenantList()
    tenantList.value = res.data || []
  } catch (error) {
    console.error('获取租户列表失败:', error)
  }
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res = await tenantLogin({
        tenantId: loginForm.tenantId,
        username: loginForm.username,
        password: loginForm.password
      })
      const { accessToken, refreshToken } = res.data
      storage.setToken(accessToken)
      storage.setRefreshToken(refreshToken)
      storage.setLoginType('tenant')
      ElMessage.success('登录成功')
      const isMobile = window.innerWidth <= 768
      const redirect = route.query.redirect || (isMobile ? '/mobile/home' : '/home')
      router.push(redirect)
    } catch (error) {
      ElMessage.error(error?.message || '登录失败，请稍后重试')
    } finally {
      loading.value = false
    }
  })
}

onMounted(() => {
  loadTenantList()
})
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
</style>