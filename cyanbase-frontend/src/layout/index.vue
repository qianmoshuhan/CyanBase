<template>
  <div class="layout-container">
    <!-- 侧边栏（PC 端保持原样） -->
    <aside v-if="!isMobile" class="sidebar" :class="{ 'collapsed': isCollapsed }">
      <div class="logo-box" :class="{ 'is-collapsed': isCollapsed }">
        <img v-if="systemLogo && !isCollapsed" :src="systemLogo" class="logo-img" />
        <span v-if="!isCollapsed" class="logo-text">{{ systemName }}</span>
      </div>
      <div class="menu-wrapper">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapsed"
          :collapse-transition="false"
          router
          background-color="var(--cyan-sidebar-bg)"
          text-color="var(--cyan-sidebar-text)"
          active-text-color="var(--el-color-primary)"
        >
          <el-menu-item index="/home"><el-icon><HomeFilled /></el-icon><span>首页</span></el-menu-item>
          <template v-for="item in menuList" :key="item.id">
            <el-sub-menu v-if="item.children && item.children.length" :index="`/${item.routePath}`">
              <template #title>
                <el-icon><component :is="getIconComponent(item.icon)" /></el-icon>
                <span>{{ item.menuName }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.id"
                :index="getPath(child.routePath,item.routePath)">
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

    <!-- ⭐ 手机端：抽屉式侧边栏 -->
    <el-drawer
      v-if="isMobile"
      v-model="drawerVisible"
      direction="ltr"
      :size="260"
      :show-close="false"
      :with-header="false"
      class="mobile-sidebar-drawer"
      destroy-on-close
    >
      <div class="sidebar drawer-sidebar">
        <div class="logo-box">
          <img v-if="systemLogo" :src="systemLogo" class="logo-img" />
          <span class="logo-text">{{ systemName }}</span>
        </div>
        <div class="menu-wrapper">
          <el-menu
            :default-active="activeMenu"
            router
            background-color="var(--cyan-sidebar-bg)"
            text-color="var(--cyan-sidebar-text)"
            active-text-color="var(--el-color-primary)"
          >
            <el-menu-item index="/home"><el-icon><HomeFilled /></el-icon><span>首页</span></el-menu-item>
            <template v-for="item in menuList" :key="item.id">
              <el-sub-menu v-if="item.children && item.children.length" :index="`/${item.routePath}`">
                <template #title>
                  <el-icon><component :is="getIconComponent(item.icon)" /></el-icon>
                  <span>{{ item.menuName }}</span>
                </template>
                <el-menu-item v-for="child in item.children" :key="child.id"
                  :index="getPath(child.routePath,item.routePath)">
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
      </div>
    </el-drawer>

    <!-- 主内容区 -->
    <div class="main-wrapper">
      <!-- 顶部导航 -->
      <header class="header">
        <div class="header-left" style="display:flex;align-items:center;gap:12px;height:100%;">
          <!-- 折叠按钮 -->
          <el-button text @click="toggleSidebar">
            <el-icon size="20"><Expand v-if="isCollapsed" /><Fold v-else /></el-icon>
          </el-button>

          <!-- ⭐ 面包屑（已移除冗余 LOGO，侧边栏顶部已有） -->
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentRouteName">{{ currentRouteName }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <!-- ⭐ 消息铃铛：PC 显示数字 badge，手机只显示小红点 -->
          <el-badge
            :value="isMobile ? 0 : unreadCount"
            :is-dot="isMobile && unreadCount > 0"
            :max="99"
            class="message-badge"
          >
            <el-button text class="bell-btn" @click="goMessage">
              <el-icon size="20"><Bell /></el-icon>
            </el-button>
          </el-badge>

          <!-- ⭐ 平台主体租户切换下拉：仅 tenantId IS NULL 且授权租户 > 1 时渲染 -->
          <el-dropdown
            v-if="userStore.getIsPlatformPrincipal && tenantScopes.length > 1"
            @command="handleTenantSwitch"
          >
            <span class="user-info tenant-switch-btn">
              <el-icon><OfficeBuilding /></el-icon>
              <span class="username">{{ currentTenantName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="t in tenantScopes"
                  :key="t.id"
                  :command="t.id"
                  :class="{ 'is-active': t.id === activeTenantId }"
                >
                  {{ t.tenantName }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <!-- ⭐ 租户内经营单元切换下拉：仅「非平台主体 + tenantId>0 + 已绑定经营单元」时渲染 -->
          <el-dropdown
            v-if="showBizUnitSwitch"
            :disabled="bizUnitSwitching"
            @command="handleBizUnitSwitch"
          >
            <span class="user-info biz-unit-switch-btn">
              <el-icon><OfficeBuilding /></el-icon>
              <span class="username">{{ currentBizUnitName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="b in availableBizUnits"
                  :key="b.bizUnitId"
                  :command="b.bizUnitId"
                  :class="{ 'is-active': Number(b.bizUnitId) === Number(activeBizUnitId) }"
                >
                  {{ b.bizUnitName }}
                </el-dropdown-item>
                <!-- 已激活时才提供清空入口；用字符串哨兵，避免 command=null 被下拉组件吞掉 -->
                <el-dropdown-item
                  v-if="activeBizUnitId != null"
                  divided
                  :command="CLEAR_BIZ_UNIT"
                >
                  恢复原始范围
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><User /></el-icon>
              <span class="username">{{ userStore.getNickName || userStore.getUserName || '用户' }}</span>
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

      <!-- ⭐ TagsView 标签栏 -->
      <TagsView />

      <!-- 内容区（KeepAlive 缓存） -->
      <main class="content">
        <router-view v-slot="{ Component, route: r }">
          <KeepAlive :include="tagsViewStore.cachedViews">
            <component :is="Component" :key="r.fullPath" />
          </KeepAlive>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useTagsViewStore } from '@/store/tagsView'
import { ElMessageBox, ElMessage } from 'element-plus'
import storage from '@/utils/storage'
import { getConfigByKey, getUserMenu } from "@/api/config.js";
import { getMyTenantScopes } from '@/api/auth'
import { getAvailableBizUnits } from '@/api/bizUnitScope'
import { useMessageSse } from '@/composables/useMessageSse'
import TagsView from '@/components/TagsView/index.vue'

// 导入需要的图标（修复：移除不存在的Post，替换为Element Plus真实存在的图标，其余保留）
import { 
  HomeFilled, Setting, Monitor, Expand, Fold, User, ArrowDown, Bell, OfficeBuilding,
  Menu as MenuIcon, Files as FileIcon, Tools as ToolIcon
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const tagsViewStore = useTagsViewStore()

const isCollapsed = ref(false)

// ⭐ 消息未读数 —— SSE 实时推送（内部自带 5s 超时降级 HTTP 轮询兜底）
const { unreadCount, close: closeSse } = useMessageSse()
const goMessage = () => {
  router.push('/message/myMessage')
}

// ⭐ 响应式检测
const isMobile = ref(false)
const drawerVisible = ref(false)
const checkMobile = () => { isMobile.value = window.innerWidth <= 768 }
const handleResize = () => { checkMobile(); if (!isMobile.value) drawerVisible.value = false }

const activeMenu = computed(() => route.path)

const currentRouteName = computed(() => route.meta.title || '')
// ⭐ 平台主体租户切换下拉
const tenantScopes = ref([])     // 当前主体的有效授权租户列表
// activeTenantId 在 LoginUser 根层级（不是 sysUser 里）：
// LoginUser { userId, userName, tenantId, activeTenantId, sysUser: {...} }
const activeTenantId = computed(() => {
  return userStore.userInfo?.activeTenantId ?? null
})
const currentTenantName = computed(() => {
  const t = tenantScopes.value.find(x => x.id === activeTenantId.value)
  return t?.tenantName || '选择租户'
})

// ══════════ ⭐ 租户内经营单元（BizUnit）切换 —— 与平台主体 Scope 两套链路完全隔离 ══════════
const CLEAR_BIZ_UNIT = '__CLEAR_BIZ_UNIT__'  // 下拉 command 哨兵：表示传 bizUnitId=null
const availableBizUnits = ref([])             // 当前用户可切换的经营单元扁平列表
const bizUnitSwitching = ref(false)           // 切换中防重复点击

// F4：activeBizUnitId 在 getUserInfo 返回的 LoginUser 根层级，不是 sysUser 里
const activeBizUnitId = computed(() => userStore.userInfo?.activeBizUnitId ?? null)

const currentBizUnitName = computed(() => {
  if (activeBizUnitId.value == null) return '选择经营单元'
  const hit = availableBizUnits.value.find(b => Number(b.bizUnitId) === Number(activeBizUnitId.value))
  return hit?.bizUnitName || '选择经营单元'
})

// F3 + F5：平台主体调 /tenant/biz-unit/* 会被后端 403；admin 超管(tenantId=0)也不需要下拉
// getIsPlatformPrincipal 已在 store 里改为 JWT claim 优先（auditor01 的 tenantId 是 6，不能只看 tenantId）
const isTenantUser = computed(() => {
  if (!userStore.isLoggedIn) return false
  if (userStore.getIsPlatformPrincipal) return false
  return Number(userStore.getTenantId) > 0
})

// 切换中 userInfo 会被短暂清空，用 bizUnitSwitching 兜住，避免下拉闪烁消失
const showBizUnitSwitch = computed(() =>
  (isTenantUser.value || bizUnitSwitching.value) && availableBizUnits.value.length > 0
)

// ⚠️ 不能用 showBizUnitSwitch 判断是否去拉列表：初始 0 条 → false → 永远不拉 → 下拉永远不出现
const fetchAvailableBizUnits = async () => {
  if (!isTenantUser.value && !bizUnitSwitching.value) {
    availableBizUnits.value = []
    return
  }
  try {
    const res = await getAvailableBizUnits()
    availableBizUnits.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    // 403 / 业务错误的精准 msg 已由 request.js 拦截器原样弹出，这里只兜底清空
    console.warn('[经营单元切换] 获取可切换列表失败:', e?.message || e)
    availableBizUnits.value = []
  }
}

// userInfo 可能晚于布局挂载到达；身份就绪后一定拉一次，身份不变时不会重复请求
watch(isTenantUser, (ok) => {
  if (bizUnitSwitching.value) return  // 切换中 userInfo 抖动，忽略
  if (ok) fetchAvailableBizUnits()
  else availableBizUnits.value = []
}, { immediate: true })

const handleBizUnitSwitch = async (command) => {
  if (bizUnitSwitching.value) return
  const targetId = command === CLEAR_BIZ_UNIT ? null : command
  // 重复点当前项 → 不请求；清空项只在已激活时才存在
  if (targetId !== null && Number(targetId) === Number(activeBizUnitId.value)) return
  if (targetId === null && activeBizUnitId.value == null) return

  // 名称必须按目标 ID 从列表查，不能用切换前的 currentBizUnitName（那是旧值）
  const targetName = targetId === null
    ? null
    : availableBizUnits.value.find(b => Number(b.bizUnitId) === Number(targetId))?.bizUnitName || '经营单元'

  bizUnitSwitching.value = true
  try {
    // store action 内部：覆盖双 Token + 清 userInfo/roles/permissions + reset tagsView
    await userStore.switchBizUnitAndUpdateToken(targetId)
    await userStore.fetchUserInfo()   // F2：从新 token 重拉 activeBizUnitId + 合并后的权限
    await fetchUserMenu()             // 角色合并后可见菜单可能变化
    await fetchAvailableBizUnits()    // 同步下拉数据
    ElMessage({
      message: targetId === null ? '已恢复原始权限范围' : `已切换到「${targetName}」`,
      type: 'success',
      customClass: 'tenant-switch-success'
    })
    // 跳首页，避免当前页继续用旧权限/旧数据渲染
    if (route.path !== '/home') router.push('/home')
  } catch (e) {
    // F6：后端精准消息已由 request.js 拦截器原样弹出，这里不重复弹窗、不重写文案
    console.warn('[经营单元切换] 切换失败:', e?.message || e)
  } finally {
    bizUnitSwitching.value = false
  }
}
// ════════════════════════════════════════════════════════════════════════════════════

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
    //====业务工具【全部强制抛弃tool父前缀，固定/system或直接根路径】
    file:{root:'system',path:'file'},
    message:{root:'system',path:'message'},
    gen:{root:'system',path:'gen'},
    form:{root:'system',path:'form'},
    thirdConfig:{root:'system',path:'third/config'},
    //====工作流页面【直接走静态路由根路径 /workflow/xxx 或 /system/wf/xxx】
    definition:{root:'system',path:'wf/definition'},
    wfDefinition:{root:'system',path:'wf/definition'},
    todo:{root:'',path:'workflow/todo'},
    done:{root:'',path:'workflow/done'},
    start:{root:'',path:'workflow/start'}
  }

  const item = routeMap[childPath]
  // 如果配置是对象 → 抛弃原parentPath，使用自定义根路径
  if(typeof item === 'object'){
    return item.root ? `/${item.root}/${item.path}` : `/${item.path}`
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

// ⭐ 平台主体：拉取授权租户列表（用于右上角切换下拉）
const fetchMyTenantScopes = async () => {
  if (!userStore.getIsPlatformPrincipal) return  // 非主体不拉
  try {
    const res = await getMyTenantScopes()
    tenantScopes.value = res.data || []
  } catch (e) {
    console.warn('[租户切换] 获取授权租户列表失败:', e?.message)
    tenantScopes.value = []
  }
}

// ⭐ 平台主体：切换活跃租户
const handleTenantSwitch = async (tenantId) => {
  if (!tenantId || tenantId === activeTenantId.value) return
  try {
    await userStore.switchTenantAndUpdateToken(tenantId)
    await userStore.fetchUserInfo()
    await fetchUserMenu()
    await fetchMyTenantScopes()  // ⭐ 重新拉 scopes，确保 activeTenantId + tenantScopes 同步
    tagsViewStore.reset()
    ElMessage({ message: '切换租户成功', type: 'success', customClass: 'tenant-switch-success' })
    router.push('/home')
  } catch (e) {
    ElMessage.error('切换租户失败：' + (e?.message || ''))
  }
}

// 侧边栏折叠/展开（PC）或抽屉开关（手机）
const toggleSidebar = () => {
  if (isMobile.value) {
    drawerVisible.value = !drawerVisible.value
  } else {
    isCollapsed.value = !isCollapsed.value
  }
}

// 路由切换时手机自动关抽屉
watch(() => route.path, () => {
  if (isMobile.value) drawerVisible.value = false
})

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
      const loginType = storage.getLoginType()
      await userStore.logout()
      router.push(loginType === 'tenant' ? '/tenant-login' : '/login')
    }).catch(() => {})
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

onMounted(async () => {
  checkMobile()
  window.addEventListener('resize', handleResize)
  // ⭐ SSE 已在 composable 构造时自动 init，这里只做 UI 初始化
  try {
    let nameRes = await getConfigByKey("system_name");
    if (nameRes?.data) { systemName.value = nameRes.data }
    let logoRes = await getConfigByKey("system_logo");
    if (logoRes?.data) { systemLogo.value = logoRes.data }
    await fetchUserMenu()
    await fetchMyTenantScopes()
  } catch (e) {
    console.log("加载配置失败", e);
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  // ⭐ 关闭 SSE 连接（或降级时清理轮询定时器）
  closeSse()
})
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
  background-color: var(--cyan-sidebar-bg);
  transition: width 0.3s;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}

.sidebar.collapsed {
  width: 64px;
}

.logo-box {
  height: 100px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border-bottom: 1px solid var(--cyan-sidebar-border);
  transition: all 0.3s;
}

.logo-box.is-collapsed {
  height: 60px;
}

.logo-img {
  height: 48px;
  width: auto;
  max-width: 120px;
  object-fit: contain;
}

.logo-box.is-collapsed .logo-img {
  height: 32px;
  max-width: 40px;
}

.menu-wrapper {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.menu-wrapper .el-menu {
  border-right: none;
  background-color: var(--cyan-sidebar-bg);
  width: 100%;
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
  gap: 8px;
}

.bell-btn {
  padding: 6px;
}
.message-badge {
  margin-right: 4px;
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

/* 经营单元名称可能较长，避免撑破 header */
.biz-unit-switch-btn .username {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content {
  flex: 1;
  padding: 20px;
  background-color: #f0f2f5;
  overflow-y: auto;
}

/* ===== 手机端响应式覆盖 ===== */
.mobile-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.drawer-sidebar {
  width: 100% !important;
  height: 100%;
  min-height: 100vh;
}

@media (max-width: 768px) {
  .layout-container { overflow-x: hidden; }
  .header { height: 52px; padding: 0 12px; }
  .username { display: none; }
  .user-info { padding: 4px 8px; }
  .content { padding: 12px; }
}

.mobile-sidebar-drawer .logo-box {
  height: 70px;
}
.mobile-sidebar-drawer .logo-img {
  height: 36px;
}
</style>