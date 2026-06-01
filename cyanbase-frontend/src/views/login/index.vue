<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1 class="title">CyanBase</h1>
        <p class="subtitle">青渊数字底座</p>
      </div>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="userName">
          <el-input
            v-model="loginForm.userName"
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
      <div class="login-tips">
        <span>默认账号：admin</span>
        <span>默认密码：admin123</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  userName: 'admin',
  password: 'admin123'
})

const loginRules = {
  userName: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      await userStore.login(loginForm.userName, loginForm.password)
      ElMessage.success('登录成功')

      // 获取跳转地址
      const redirect = route.query.redirect || '/home'
      router.push(redirect)
    } catch (error) {
      // 错误已由request拦截器处理
    } finally {
      loading.value = false
    }
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
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
}

.login-box {
  width: 420px;
  padding: 40px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.title {
  color: #1a1a2e;
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

.login-button {
  width: 100%;
  font-size: 16px;
}

.login-tips {
  margin-top: 20px;
  text-align: center;
  color: #909399;
  font-size: 12px;
}

.login-tips span {
  margin: 0 8px;
}
</style>