<template>
  <div class="home-container">
    <div class="welcome-card">
      <h2>欢迎使用 CyanBase 青渊数字底座</h2>
      <p>当前登录用户：{{ userStore.getNickName || userStore.getUserName }} <span v-if="tenantName" class="tenant-tag">{{ tenantName }}</span></p>
      <p>登录时间：{{ currentTime }}</p>
    </div>

    <div class="stats-grid">
      <el-card class="stat-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <el-icon size="24" :color="'var(--el-color-primary)'"><User /></el-icon>
            <span>用户总数</span>
          </div>
        </template>
        <div class="stat-value">{{ stats.userCount }}</div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <el-icon size="24" :color="'var(--el-color-success)'"><OfficeBuilding /></el-icon>
            <span>部门数量</span>
          </div>
        </template>
        <div class="stat-value">{{ stats.deptCount }}</div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <el-icon size="24" :color="'var(--el-color-warning)'"><Key /></el-icon>
            <span>角色数量</span>
          </div>
        </template>
        <div class="stat-value">{{ stats.roleCount }}</div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <el-icon size="24" :color="'var(--el-color-danger)'"><Monitor /></el-icon>
            <span>系统状态</span>
          </div>
        </template>
        <div class="stat-value status-ok">正常</div>
      </el-card>
    </div>

    <el-card class="quick-actions" shadow="hover">
      <template #header>
        <span>快捷操作</span>
      </template>
      <div class="actions">
        <el-button type="primary" @click="$router.push('/system/user')">用户管理</el-button>
        <el-button type="success" @click="$router.push('/system/dept')">部门管理</el-button>
        <el-button type="warning" @click="$router.push('/system/role')">角色管理</el-button>
        <el-button type="info" @click="$router.push('/system/menu')">菜单管理</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { OfficeBuilding } from '@element-plus/icons-vue'
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { getUserList } from '@/api/user'
import { getDeptList } from '@/api/dept'
import { getRoleList } from '@/api/role'
import { getCurrentProfile } from '@/api/auth'
import storage from '@/utils/storage'

const userStore = useUserStore()

const currentTime = ref('')
const tenantName = ref('')
const stats = reactive({
  userCount: 0,
  deptCount: 0,
  roleCount: 0
})

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const fetchStats = async () => {
  try {
    const [userRes, deptRes, roleRes] = await Promise.all([
      getUserList(),
      getDeptList(),
      getRoleList()
    ])
    stats.userCount = userRes.data.total || 0
    stats.deptCount = deptRes.data.total || 0
    stats.roleCount = roleRes.data.total || 0
  } catch (error) {
    console.error('获取统计数据失败', error)
  }
}

const fetchTenantName = async () => {
  try {
    // 先确保 userInfo 有值（如果路由守卫没提前调 fetchUserInfo）
    if (!userStore.userInfo) {
      await userStore.fetchUserInfo()
    }
    // 平台超管 tenantId 为 null/0
    const isPlatform = userStore.getIsPlatform
    if (isPlatform) {
      tenantName.value = '平台用户'
    } else {
      // 租户账号拉 profile 拿租户名
      const res = await getCurrentProfile()
      tenantName.value = res.data?.tenant?.tenantName || ''
    }
  } catch (e) {
    console.error('获取租户信息失败', e)
  }
}

onMounted(() => {
  updateTime()
  setInterval(updateTime, 1000)
  fetchStats()
  fetchTenantName()
})
</script>

<style scoped>
.home-container {
  padding: 20px;
}

.welcome-card {
  background: linear-gradient(135deg, #1677ff 0%, #0958d9 100%);
  color: #fff;
  padding: 30px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.welcome-card h2 {
  margin: 0 0 15px 0;
  font-size: 24px;
}

.welcome-card p {
  margin: 8px 0;
  opacity: 0.9;
}

.tenant-tag {
  display: inline-block;
  margin-left: 10px;
  padding: 2px 10px;
  background: rgba(255,255,255,0.2);
  border-radius: 12px;
  font-size: 12px;
  color: #ffec3d;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #303133;
  padding: 10px 0;
}

.status-ok {
  color: var(--el-color-success);
}

.quick-actions {
  margin-top: 20px;
}

.actions {
  display: flex;
  gap: 15px;
  flex-wrap: wrap;
}
</style>