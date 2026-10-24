<template>
  <div class="mobile-home">
    <!-- 顶部 Banner -->
    <div class="banner">
      <div class="banner-content">
        <div class="banner-top">
          <div class="user-info">
            <el-avatar :size="44" class="user-avatar">
              {{ userInfo.nickName?.charAt(0)?.toUpperCase() || 'U' }}
            </el-avatar>
            <div class="user-text">
              <div class="welcome">{{ greeting }}，{{ userInfo.nickName || userInfo.userName }}</div>
              <div class="sub">{{ tenantName }}</div>
            </div>
          </div>
          <el-button circle size="small" class="logout-btn" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
        <div class="banner-title">CyanBase · 三重一大管理平台</div>
      </div>
    </div>

    <!-- 待办卡片 -->
    <div class="todo-card" @click="go('/workflow/todo')">
      <div class="todo-item">
        <div class="todo-num">{{ todoCount }}</div>
        <div class="todo-label">待办审批</div>
      </div>
      <div class="todo-arrow">
        <el-icon :size="20"><ArrowRight /></el-icon>
      </div>
    </div>

    <!-- 宫格导航 -->
    <div class="grid-nav">
      <div
        v-for="item in navItems"
        :key="item.path"
        class="nav-item"
        @click="go(item.path)"
      >
        <div class="nav-icon" :style="{ background: item.color }">
          <el-icon :size="26">
            <component :is="item.icon" />
          </el-icon>
        </div>
        <div class="nav-label">{{ item.label }}</div>
        <el-badge
          v-if="item.badge"
          :value="item.badge"
          class="nav-badge"
        />
      </div>
    </div>

    <!-- 最近事项 -->
    <div class="section">
      <div class="section-header">
        <span class="section-title">最近事项</span>
        <span class="section-more" @click="go('/sydyd/item')">查看全部 ›</span>
      </div>
      <div class="recent-list">
        <div
          v-for="row in recentItems"
          :key="row.id"
          class="recent-item"
          @click="go('/sydyd/item/detail', { id: row.id })"
        >
          <div class="recent-main">
            <div class="recent-name">{{ row.itemName }}</div>
            <div class="recent-sub">{{ row.createTime }}</div>
          </div>
          <el-tag :type="statusColor(row.itemStatus)" size="small">
            {{ statusText(row.itemStatus) }}
          </el-tag>
        </div>
        <div v-if="!recentItems.length" class="empty-tip">暂无事项</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  List, FolderOpened, Calendar, Connection, User,
  Tickets, Document, SwitchButton, ArrowRight, Bell
} from '@element-plus/icons-vue'
import { getCurrentProfile } from '@/api/auth'
import { getMyTodoList } from '@/api/wfDefinition'
import { getItemList } from '@/api/sydyd'
import { getUnreadCount } from '@/api/message'
import storage from '@/utils/storage'

const router = useRouter()

// 用户信息
const userInfo = ref({})
const tenantName = ref('')

// 待办数量
const todoCount = ref(0)
// ⭐ 消息未读数
const messageCount = ref(0)

// 最近事项
const recentItems = ref([])

// 导航项
const navItems = computed(() => [
  { label: '待办审批', path: '/workflow/todo', icon: Tickets, color: '#1677ff', badge: todoCount.value || null },
  { label: '我的消息', path: '/message/myMessage', icon: Bell, color: '#f53f3f', badge: messageCount.value || null },
  { label: '我的已办', path: '/workflow/done', icon: Document, color: '#00b42a' },
  { label: '三重一大', path: '/sydyd/item', icon: FolderOpened, color: '#ff7d00' },
  { label: '会议管理', path: '/sydyd/meeting', icon: Calendar, color: '#f53f3f' },
  { label: '实施落地', path: '/sydyd/execute', icon: Connection, color: '#909399' },
  { label: '发起流程', path: '/workflow/start', icon: List, color: '#722ED1' },
  { label: '个人中心', path: '/profile', icon: User, color: '#0958d9' }
])

// 问候语
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

// 状态映射
const statusMap = { 1: '草稿', 2: '待酝酿', 3: '已生成议题', 4: '已上会', 5: '已办结', 6: '已驳回' }
const statusColor = (s) => ({ 1: 'info', 2: 'warning', 3: '', 4: '', 5: 'success', 6: 'danger' }[s] || '')
const statusText = (s) => statusMap[s] || '-'

// 跳转（普通 push，mobile 路由不经过 layout）
const go = (path, query) => {
  router.push({ path, query })
}

const handleLogout = () => {
  ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    .then(() => {
      // 先记下来登录类型，再清 auth（清了就读不到了）
      const loginType = storage.getLoginType()
      storage.clearAuth()  // 清 token / refreshToken / loginType
      storage.clear()      // 清所有用户数据
      // 租户跳租户登录页，平台跳平台登录页
      router.push(loginType === 'tenant' ? '/tenant-login' : '/login')
    }).catch(() => {})
}

// 加载数据
const loadAll = async () => {
  try {
    const res = await getCurrentProfile()
    userInfo.value = res.data?.user || {}
    tenantName.value = res.data?.tenant?.tenantName || ''
  } catch (e) { /* 忽略 */ }

  // 待办数量（从 getMyTodoList 的 total 取，只查 1 条）
  try {
    const todoRes = await getMyTodoList({ pageNum: 1, pageSize: 1 })
    todoCount.value = todoRes.data?.total || 0
  } catch (e) { /* 忽略 */ }

  try {
    const itemRes = await getItemList({ pageNum: 1, pageSize: 5 })
    recentItems.value = itemRes.data?.records || []
  } catch (e) { /* 忽略 */ }

  // ⭐ 消息未读数
  try {
    const msgRes = await getUnreadCount()
    messageCount.value = msgRes.data || 0
  } catch (e) { /* 忽略 */ }
}

onMounted(loadAll)
</script>

<style scoped>
.mobile-home {
  min-height: 100vh;
  background: #f5f6fa;
  padding-bottom: 24px;
}

/* Banner */
.banner {
  background: linear-gradient(135deg, #1677ff 0%, #4096ff 50%, #0958d9 100%);
  padding: 48px 20px 32px;
  color: #fff;
  border-radius: 0 0 24px 24px;
}
.banner-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-avatar {
  background: rgba(255,255,255,0.2);
  border: 2px solid rgba(255,255,255,0.4);
}
.welcome {
  font-size: 17px;
  font-weight: 600;
}
.sub {
  font-size: 12px;
  opacity: 0.7;
  margin-top: 2px;
}
.logout-btn {
  background: rgba(255,255,255,0.15);
  border: none;
  color: #fff;
}
.banner-title {
  margin-top: 20px;
  font-size: 14px;
  opacity: 0.8;
}

/* 待办卡片 */
.todo-card {
  margin: -20px 16px 0;
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
}
.todo-item {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.todo-num {
  font-size: 32px;
  font-weight: 700;
  color: #ff7d00;
  line-height: 1;
}
.todo-label {
  font-size: 15px;
  color: #303133;
  font-weight: 500;
}
.todo-arrow {
  color: #c0c4cc;
}

/* 宫格导航 */
.grid-nav {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px 8px;
  padding: 20px 16px;
}
.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  position: relative;
}
.nav-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}
.nav-label {
  font-size: 12px;
  color: #303133;
}
.nav-badge {
  position: absolute;
  top: 0;
  right: calc(50% - 24px);
}

/* 区块 */
.section {
  margin: 8px 16px 0;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 4px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.section-more {
  font-size: 13px;
  color: #909399;
}

.recent-list {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}
.recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.recent-item:last-child {
  border-bottom: none;
}
.recent-name {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}
.recent-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.empty-tip {
  padding: 30px;
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
}
</style>