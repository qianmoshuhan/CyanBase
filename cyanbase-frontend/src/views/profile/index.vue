<template>
  <div class="profile-container">
    <!-- 数据加载前显示空状态 -->
    <div v-if="!profile" class="profile-loading">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>加载中...</p>
    </div>

    <template v-else>
    <!-- 顶部用户概览卡 -->
    <el-card class="overview-card" shadow="hover">
      <div class="overview-inner">
        <el-avatar :size="72" class="avatar">
          {{ profile?.user?.nickName?.charAt(0)?.toUpperCase() || 'U' }}
        </el-avatar>
        <div class="user-main">
          <div class="user-name">
            {{ profile?.user?.nickName || profile?.user?.userName }}
            <el-tag v-if="isPlatform" type="primary" size="small" effect="plain">平台超管</el-tag>
            <el-tag v-else type="success" size="small" effect="plain">租户账号</el-tag>
          </div>
          <div class="user-sub">
            账号 {{ profile?.user?.userName }} · 创建于 {{ profile?.user?.createTime }}
          </div>
        </div>
      </div>
    </el-card>

    <!-- 主体三栏 -->
    <div class="content-grid">
      <!-- 左侧：基础信息 -->
      <el-card shadow="hover">
        <template #header>
          <span class="card-title">基础信息</span>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="账号">{{ profile?.user?.userName }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ profile?.user?.nickName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ profile?.user?.phonenumber || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ profile?.user?.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ deptName }}</el-descriptions-item>
          <el-descriptions-item label="账号类型">
            <el-tag v-if="isPlatform" type="primary">平台超管</el-tag>
            <el-tag v-else type="success">租户账号</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="profile?.user?.status === 0 ? 'success' : 'danger'">
              {{ profile?.user?.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ profile?.user?.createTime }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 中间：租户信息（平台超管隐藏） -->
      <el-card v-if="!isPlatform" shadow="hover">
        <template #header>
          <span class="card-title">所属租户</span>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="租户名称">{{ profile?.tenant?.tenantName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="租户编码">{{ profile?.tenant?.tenantCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="到期时间">
            <span v-if="profile?.tenant?.expireDate">{{ profile?.tenant?.expireDate }}</span>
            <span v-else class="text-muted">永不过期</span>
          </el-descriptions-item>
          <el-descriptions-item label="租户状态">
            <el-tag :type="tenantStatusTag" size="small">{{ tenantStatusText }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 右侧：修改密码 -->
      <el-card shadow="hover">
        <template #header>
          <span class="card-title">修改密码</span>
        </template>
        <el-form
          :model="pwdForm"
          :rules="pwdRules"
          ref="pwdFormRef"
          :label-width="isMobile ? 'auto' : '90px'"
          :label-position="isMobile ? 'top' : 'right'"
        >
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少6位" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitChangePwd" :loading="pwdLoading">确认修改</el-button>
            <el-button @click="resetPwdForm">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getCurrentProfile, changePasswordPlatform, changePasswordTenant } from '@/api/auth'
import storage from '@/utils/storage'

const profile = ref(null)
const deptName = ref('')
const isMobile = ref(window.innerWidth <= 768)
const onResize = () => { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))
const pwdFormRef = ref()
const pwdLoading = ref(false)

const pwdForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_, val) => {
        if (val !== pwdForm.value.newPassword) return new Error('两次密码不一致')
        return true
      },
      trigger: 'blur'
    }
  ]
}

// 是平台用户还是租户用户
const isPlatform = computed(() => {
  const tid = profile.value?.user?.tenantId
  return !tid || tid === 0
})

const tenantStatusText = computed(() => {
  const s = profile.value?.tenant?.status
  return s === 0 ? '正常' : s === 1 ? '冻结' : s === 2 ? '已过期' : '未知'
})
const tenantStatusTag = computed(() => {
  const s = profile.value?.tenant?.status
  return s === 0 ? 'success' : s === 1 ? 'warning' : s === 2 ? 'info' : 'danger'
})

const fetchProfile = async () => {
  try {
    const res = await getCurrentProfile()
    profile.value = res.data
    deptName.value = res.data?.deptName || '暂未关联部门'
  } catch (e) {
    ElMessage.error('加载个人信息失败')
  }
}

const submitChangePwd = async () => {
  if (!pwdFormRef.value) return
  try {
    // ⭐ Promise 风格：不传 callback，validate() 返回 Promise，校验不通过会 reject
    await pwdFormRef.value.validate()
  } catch (_) {
    return
  }
  pwdLoading.value = true
  try {
    const body = { oldPassword: pwdForm.value.oldPassword, newPassword: pwdForm.value.newPassword }
    const loginType = storage.getLoginType()
    if (loginType === 'tenant') {
      await changePasswordTenant(body)
    } else {
      await changePasswordPlatform(body)
    }
    ElMessage.success('密码修改成功，请重新登录')
    resetPwdForm()
  } catch (e) {
    // 后端会返回具体错误（原密码错误等），已经被全局 request 拦截器统一 toast 了
  } finally {
    pwdLoading.value = false
  }
}

const resetPwdForm = () => {
  pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  pwdFormRef.value?.clearValidate()
}

onMounted(fetchProfile)
</script>

<style scoped>
.profile-container {
  padding: 20px;
}

.overview-card {
  margin-bottom: 20px;
  background: linear-gradient(135deg, #1677ff 0%, #4096ff 100%);
  border: none;
}
.overview-card :deep(.el-card__body) { padding: 24px; }

.overview-inner {
  display: flex;
  align-items: center;
  gap: 20px;
  color: #fff;
}
.avatar {
  background: rgba(255,255,255,0.25);
  font-size: 28px;
  font-weight: bold;
}
.user-main .user-name {
  font-size: 22px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-main .user-name .el-tag {
  color: #1677ff;
}
.user-main .user-sub {
  font-size: 13px;
  opacity: 0.85;
  margin-top: 6px;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 20px;
}

.card-title {
  font-weight: bold;
}

.text-muted {
  color: #909399;
}

/* 响应式：窄屏自动折成两列 */
@media (max-width: 1200px) {
  .content-grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 768px) {
  .content-grid { grid-template-columns: 1fr; }
}

/* ===== 手机端额外优化 ===== */
@media (max-width: 768px) {
  .profile-container { padding: 0; }
  .overview-card { margin-bottom: 12px; border-radius: 8px; }
  .overview-card :deep(.el-card__body) { padding: 20px 16px; }

  /* 概览卡：头像和用户信息纵向堆叠 */
  .overview-inner {
    flex-direction: column;
    text-align: center;
    gap: 12px;
  }
  .user-main .user-name {
    justify-content: center;
    font-size: 18px;
  }
  .user-main .user-sub {
    font-size: 12px;
    margin-top: 4px;
  }
  .avatar { --el-avatar-size: 56px; font-size: 22px; }

  /* 三栏间距缩小 */
  .content-grid { gap: 12px; }

  /* 修改密码表单：已通过 template 动态 label-position="top" 解决 */
  .profile-container :deep(.el-form-item) {
    margin-bottom: 14px;
  }
}

/* 加载中状态 */
.profile-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: #909399;
  gap: 12px;
  font-size: 14px;
}
</style>