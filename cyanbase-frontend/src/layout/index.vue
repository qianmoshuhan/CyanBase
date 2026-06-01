<template>
  <div class="layout-container">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ 'collapsed': isCollapsed }">
      <div class="logo">
        <span class="logo-text">{{ isCollapsed ? 'C' : 'CyanBase' }}</span>
      </div>
      <div class="menu-wrapper">
        <el-menu
          :default-active="activeMenu"
          class="sidebar-menu"
          :collapse="isCollapsed"
          :router="true"
          background-color="#1a1a2e"
          text-color="#a0a5b5"
          active-text-color="#409eff"
        >
          <el-menu-item index="/home">
            <el-icon><HomeFilled /></el-icon>
            <template #title>首页</template>
          </el-menu-item>
          <el-sub-menu index="/system">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>系统管理</span>
            </template>
            <el-menu-item index="/system/user">用户管理</el-menu-item>
            <el-menu-item index="/system/dept">部门管理</el-menu-item>
            <el-menu-item index="/system/role">角色管理</el-menu-item>
            <el-menu-item index="/system/menu">菜单管理</el-menu-item>
            <el-menu-item index="/system/post">岗位管理</el-menu-item>
            <el-sub-menu index="/system/dict">
              <template #title>字典管理</template>
              <el-menu-item index="/system/dict/type">字典类型</el-menu-item>
              <el-menu-item index="/system/dict/data">字典数据</el-menu-item>
            </el-sub-menu>
            <el-menu-item index="/system/config">参数配置</el-menu-item>
            <el-menu-item index="/system/notice">通知公告</el-menu-item>
            <el-menu-item index="/system/file">文件管理</el-menu-item>
            <el-menu-item index="/system/message">消息管理</el-menu-item>
            <el-menu-item index="/system/gen">代码生成</el-menu-item>
            <el-menu-item index="/system/wf/definition">流程定义</el-menu-item>
            <el-menu-item index="/system/form">表单设计</el-menu-item>
            <el-menu-item index="/system/third/config">第三方配置</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="/monitor">
            <template #title>
              <el-icon><Monitor /></el-icon>
              <span>系统监控</span>
            </template>
            <el-menu-item index="/monitor/operLog">操作日志</el-menu-item>
            <el-menu-item index="/monitor/loginLog">登录日志</el-menu-item>
            <el-menu-item index="/monitor/online">在线用户</el-menu-item>
            <el-menu-item index="/monitor/job">定时任务</el-menu-item>
            <el-menu-item index="/monitor/jobLog">任务日志</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main-wrapper">
      <!-- 顶部导航 -->
      <header class="header">
        <div class="header-left">
          <el-button text @click="toggleSidebar">
            <el-icon size="20"><Expand v-if="isCollapsed" /><Fold v-else /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentRouteName">{{ currentRouteName }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><User /></el-icon>
              <span class="username">{{ userStore.getNickName || userStore.getUserName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref(false)

const activeMenu = computed(() => route.path)

const currentRouteName = computed(() => route.meta.title || '')

const toggleSidebar = () => {
  isCollapsed.value = !isCollapsed.value
}

const handleCommand = (command) => {
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      await userStore.logout()
      router.push('/login')
    }).catch(() => {})
  } else if (command === 'profile') {
    ElMessageBox.info('个人中心功能开发中...')
  }
}
</script>

<style scoped>
.layout-container {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 220px;
  height: 100vh;
  background-color: #1a1a2e;
  transition: width 0.3s;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar.collapsed {
  width: 64px;
}

.logo {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #16213e;
  border-bottom: 1px solid #2a2a4a;
}

.logo-text {
  color: #409eff;
  font-size: 20px;
  font-weight: bold;
}

.menu-wrapper {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.menu-wrapper::-webkit-scrollbar {
  width: 6px;
}

.menu-wrapper::-webkit-scrollbar-thumb {
  background-color: #3a3a5a;
  border-radius: 3px;
}

.menu-wrapper::-webkit-scrollbar-track {
  background-color: #1a1a2e;
}

.sidebar-menu {
  border-right: none;
  background-color: #1a1a2e;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 220px;
}

.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: 60px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 5px 10px;
  border-radius: 4px;
}

.user-info:hover {
  background-color: #f5f7fa;
}

.username {
  color: #303133;
  font-size: 14px;
}

.content {
  flex: 1;
  padding: 20px;
  background-color: #f0f2f5;
  overflow-y: auto;
}
</style>