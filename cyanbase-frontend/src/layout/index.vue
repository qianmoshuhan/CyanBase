<template>
  <div class="layout-container">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ 'collapsed': isCollapsed }">
      <div class="logo-box" style="display:flex;justify-content:center;align-items:center;padding:10px 0;">
        <img
          v-if="systemLogo"
          :src="systemLogo"
          style="height:100px;width:auto;max-width:120px;object-fit:contain;margin-left:-50px;"
        />
        <span v-else class="logo-text">{{ systemName }}</span>
      </div>
      <div class="menu-wrapper">
        <!-- 修改主组件中的 el-menu 渲染部分 -->
<el-menu
  :default-active="activeMenu"
  router
  background-color="#002140"
  text-color="#fff"
  active-text-color="#ffd04b"
>
  <el-menu-item index="/home">
    <el-icon><HomeFilled /></el-icon>
    <span>首页</span>
  </el-menu-item>
  <template v-for="item in menuList" :key="item.id">
    <el-sub-menu v-if="item.children && item.children.length" :index="`/${item.routePath}`">
      <template #title>
        <el-icon><component :is="getIconComponent(item.icon)" /></el-icon>
        <span>{{ item.menuName }}</span>
      </template>
      <el-menu-item
        v-for="child in item.children"
        :key="child.id"
        :index="getPath(child.routePath,item.routePath)"
      >
        <span>{{ child.menuName }}</span>
      </el-menu-item>
    </el-sub-menu>
    <el-menu-item v-else :index="`/${item.routePath}`">
      <el-icon><component :is="getIconComponent(item.icon)" /></el-icon>
      <span>{{ item.menuName }}</span>
    </el-menu-item>
  </template>
</el-menu>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main-wrapper">
      <!-- 顶部导航 -->
      <header class="header">
        <div class="header-left" style="display:flex;align-items:center;gap:12px;height:100%;">
          <!-- 折叠按钮 -->
          <el-button text @click="toggleSidebar">
            <el-icon size="20"><Expand v-if="isCollapsed" /><Fold v-else /></el-icon>
          </el-button>

          <!-- Logo + 系统名称，侧边栏收缩时自动隐藏logo只留文字 -->
          <div style="display:flex;align-items:center;gap:8px;">
            <img
              v-if="systemLogo && !isCollapsed"
              :src="systemLogo"
              style="height:34px;width:auto;max-width:130px;object-fit:contain;"
            />
            <span v-if="!isCollapsed" style="font-size:17px;font-weight:500;">{{ systemName }}</span>
          </div>

          <!-- 面包屑 -->
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
// 1. 补充导入 onMounted
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessageBox } from 'element-plus'
import { getConfigByKey, getUserMenu } from "@/api/config.js";

// 导入需要的图标（修复：移除不存在的Post，替换为Element Plus真实存在的图标，其余保留）
import { 
  HomeFilled, Setting, Monitor, Expand, Fold, User, ArrowDown, 
  Menu as MenuIcon, Files as FileIcon, Tools as ToolIcon // 关键修正
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref(false)

const activeMenu = computed(() => route.path)

const currentRouteName = computed(() => route.meta.title || '')
const systemName = ref("CyanBase");
const systemLogo = ref("");
// 菜单列表
const menuList = ref([])

const getPath = (childPath, parentPath) => {
  const routeMap = {
    //====系统管理
    post:'post',
    dict:'dict/type',
    config:'config',
    notice:'notice',
    //====系统监控
    online:'online',
    job:'job',
    operlog:'operLog',
    logininfor:'loginLog',
    apimonitor:'apiMonitor',
    //====业务工具【全部强制抛弃tool父前缀，固定/system】
    file:{root:'system',path:'file'},
    message:{root:'system',path:'message'},
    gen:{root:'system',path:'gen'},
    wfDefinition:{root:'system',path:'wf/definition'},
    form:{root:'system',path:'form'},
    thirdConfig:{root:'system',path:'third/config'}
  }

  const item = routeMap[childPath]
  // 如果配置是对象 → 抛弃原parentPath，使用自定义根路径
  if(typeof item === 'object'){
    return `/${item.root}/${item.path}`
  }
  // 普通菜单沿用原有父路径拼接
  const realPath = item || childPath
  return `/${parentPath}/${realPath}`
}

// 递归组件：渲染子菜单
const MenuRecursive = {
  props: {
    menu: {
      type: Object,
      required: true
    },
    isCollapsed: {
      type: Boolean,
      default: false
    }
  },
  components: {
    MenuRecursive: () => MenuRecursive
  },
  setup(props) {
    // 补充：递归组件内也要导入userStore（作用域隔离）
    const userStore = useUserStore()
    const hasPermission = (perms) => {
      if (!perms) return true
      return userStore.permissions?.includes(perms) || false
    }

    // 图标映射（修复：移除post对应的错误图标，其余保留）
const getIconComponent = (icon) => {
      const iconMap = {
        system: Setting,
        monitor: Monitor,
        file: FileIcon,
        tool: ToolIcon,
        menu: MenuIcon
      }
      return iconMap[icon] || MenuIcon
    }

    return { hasPermission, getIconComponent }
  },
  template: `
    <template v-for="child in menu.children" :key="child.id">
      <!-- 修复：子菜单index路径拼接规则 -->
      <el-sub-menu 
        v-if="child.children && child.children.length > 0 && child.menuType === 1" 
        :index="child.routePath ? \`/\${child.routePath}\` : \`/\${child.id}\`"
      >
        <template #title>
          <el-icon><component :is="getIconComponent(child.icon)" /></el-icon>
          <span>{{ child.menuName }}</span>
        </template>
        <MenuRecursive :menu="child" :isCollapsed="isCollapsed" />
      </el-sub-menu>
      <el-menu-item 
        v-else-if="child.menuType === 2" 
        :index="child.parentRoutePath 
          ? \`/\${child.parentRoutePath}/\${child.routePath}\` 
          : \`/\${child.routePath}\`"
      >
        <el-icon><component :is="getIconComponent(child.icon)" /></el-icon>
        <template #title>{{ child.menuName }}</template>
      </el-menu-item>
    </template>
  `
}

const handleMenuData = (menus, parentRoutePath = '') => {
  return menus.map(menu => {
    menu.parentRoutePath = parentRoutePath
    // 修复：先过滤type3，再处理子菜单（避免空数组覆盖原有children）
    if (menu.children && menu.children.length > 0) {
      const validChildren = menu.children.filter(item => item.menuType !== 3)
      if (validChildren.length > 0) {
        menu.children = handleMenuData(validChildren, menu.routePath)
      } else {
        menu.children = [] // 无有效子菜单则置空
      }
    } else {
      menu.children = [] // 兜底：避免children为undefined
    }
    return menu
  })
}

// 获取用户菜单
const fetchUserMenu = async () => {
  try {
    // 删掉 || 4！！！
    const userId = userStore.getUserId
    const res = await getUserMenu(userId)
    if (res.code === 200 && res.data) {
      menuList.value = handleMenuData(res.data)
    }
  } catch (e) {
    console.error('获取用户菜单失败', e)
  }
}
// 权限判断（兼容原有角色判断 + 新的权限码判断）
const hasPermission = (perms) => {
  if (!perms) return true
  return userStore.permissions?.includes(perms) || false
}

// 图标映射（主组件中也需要，修复：移除post对应的错误图标）
const getIconComponent = (icon) => {
  const iconMap = {
    system: Setting,
    monitor: Monitor,
    file: FileIcon,
    tool: ToolIcon,
    menu: MenuIcon
  }
  return iconMap[icon] || MenuIcon
}

// 侧边栏折叠/展开
const toggleSidebar = () => {
  isCollapsed.value = !isCollapsed.value
}

// 角色判断
function hasRole(roleId) {
  const r = userStore.roles || ''
  return String(r).includes(roleId)
}

// 下拉菜单指令处理
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

// 挂载时加载配置和菜单
onMounted(async () => {
  try {
    // 加载系统配置
    let nameRes = await getConfigByKey("system_name");
    if (nameRes?.data) {
      systemName.value = nameRes.data;
    }
    let logoRes = await getConfigByKey("system_logo");
    if (logoRes?.data) {
      systemLogo.value = logoRes.data;
    }
    // 加载用户菜单
    await fetchUserMenu()
  } catch (e) {
    console.log("加载配置失败", e);
  }
});
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