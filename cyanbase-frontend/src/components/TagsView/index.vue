<template>
  <div class="tags-view-wrapper">
    <!-- 左滚动箭头 -->
    <el-button
      v-if="scrollable"
      text
      class="scroll-btn scroll-left"
      @click="scrollLeft"
    >
      <el-icon><ArrowLeft /></el-icon>
    </el-button>

    <!-- 标签轨道 -->
    <div
      ref="scrollContainer"
      class="tags-scroll-container"
      @contextmenu.prevent="openContextMenu"
    >
      <div class="tags-track" ref="tagsTrack">
        <div
          v-for="tag in visitedViews"
          :key="tag.fullPath || tag.path"
          class="tag-item"
          :class="{ 'is-active': isActive(tag), 'is-affix': tag.meta?.affix }"
          @click="goTo(tag)"
          @contextmenu.prevent.stop="openContextMenu($event, tag)"
        >
          <el-icon v-if="tag.meta?.affix" class="affix-icon"><Lock /></el-icon>
          <span class="tag-title">{{ tag.meta?.title || '未命名' }}</span>
          <el-icon
            v-if="!tag.meta?.affix"
            class="close-icon"
            @click.stop="closeTag(tag)"
          >
            <Close />
          </el-icon>
        </div>
      </div>
    </div>

    <!-- 右滚动箭头 -->
    <el-button
      v-if="scrollable"
      text
      class="scroll-btn scroll-right"
      @click="scrollRight"
    >
      <el-icon><ArrowRight /></el-icon>
    </el-button>

    <!-- 右键菜单 -->
    <teleport to="body">
      <ul
        v-show="contextMenu.visible"
        class="context-menu"
        :style="contextMenuStyle"
      >
        <li @click="refreshSelectedTag">
          <el-icon><Refresh /></el-icon>
          <span>刷新标签</span>
        </li>
        <li
          :class="{ 'is-disabled': contextMenu.tag?.meta?.affix }"
          @click="closeSelectedTag"
        >
          <el-icon><Close /></el-icon>
          <span>关闭当前</span>
        </li>
        <li @click="closeOtherTags">
          <el-icon><CircleClose /></el-icon>
          <span>关闭其他</span>
        </li>
        <li @click="closeAllTags">
          <el-icon><FolderDelete /></el-icon>
          <span>关闭所有</span>
        </li>
      </ul>
    </teleport>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTagsViewStore } from '@/store/tagsView'
import {
  ArrowLeft, ArrowRight, Close, Lock, Refresh,
  CircleClose, FolderDelete
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsViewStore()

// ⭐ 用 computed 包装，比解构更安全，彻底避免响应式断裂
const visitedViews = computed(() => tagsStore.visitedViews)

// ===== 滚动相关 =====
const scrollContainer = ref(null)
const tagsTrack = ref(null)
const scrollable = ref(false)

const checkScrollable = () => {
  if (!scrollContainer.value || !tagsTrack.value) return
  scrollable.value = tagsTrack.value.scrollWidth > scrollContainer.value.clientWidth
}

const scrollLeft = () => {
  scrollContainer.value?.scrollBy({ left: -200, behavior: 'smooth' })
}
const scrollRight = () => {
  scrollContainer.value?.scrollBy({ left: 200, behavior: 'smooth' })
}

// ===== 右键菜单 =====
const contextMenu = ref({
  visible: false,
  x: 0,
  y: 0,
  tag: null
})

const contextMenuStyle = computed(() => ({
  left: contextMenu.value.x + 'px',
  top: contextMenu.value.y + 'px'
}))

const openContextMenu = (e, tag = null) => {
  // 没有 tag 时（在空白处右键）不显示
  if (!tag) return

  // 防止菜单超出可视区域
  const menuW = 160
  const menuH = 180
  let x = e.clientX
  let y = e.clientY
  if (x + menuW > window.innerWidth) x = window.innerWidth - menuW - 8
  if (y + menuH > window.innerHeight) y = window.innerHeight - menuH - 8

  contextMenu.value = { visible: true, x, y, tag }
}

const closeContextMenu = () => {
  contextMenu.value.visible = false
}

// 点击空白处关闭
const handleClickDocument = () => closeContextMenu()
const handleScroll = () => closeContextMenu()

// ===== 标签操作 =====
const isActive = (tag) => tag.path === route.path

const goTo = (tag) => {
  if (tag.path !== route.path) {
    router.push(tag.fullPath || tag.path)
  }
}

const closeTag = (tag) => {
  // ⭐ 在 store splice 之前先算好跳转目标（数据还没变，最准）
  let nextTarget = null
  if (tag.path === route.path) {
    const list = tagsStore.visitedViews
    const idx = list.findIndex(v => v.path === tag.path)
    if (idx >= 0) {
      // 优先右边，没有右边则左边，都没有则跳 home
      nextTarget = list[idx + 1] || list[idx - 1] || null
    }
  }

  if (!tagsStore.closeView(tag)) return

  if (tag.path === route.path) {
    if (nextTarget) {
      router.push(nextTarget.fullPath || nextTarget.path)
    } else {
      // 标签全关了（只剩 affix 被保护住的情况理论上走不到这里）
      router.push('/home')
    }
  }
}

const refreshSelectedTag = () => {
  const tag = contextMenu.value.tag
  closeContextMenu()
  if (!tag) return
  // 先移除缓存，再重新进入 → 触发组件重新加载
  tagsStore.removeCachedView(tag.name)
  // 用 replace + query 强制重新加载
  router.replace({ path: '/redirect' + tag.fullPath })
}

const closeSelectedTag = () => {
  const tag = contextMenu.value.tag
  closeContextMenu()
  if (!tag || tag.meta?.affix) return
  closeTag(tag)
}

const closeOtherTags = () => {
  const tag = contextMenu.value.tag
  closeContextMenu()
  if (!tag) return
  tagsStore.closeOthers(tag)
  if (tag.path !== route.path) {
    router.push(tag.fullPath || tag.path)
  }
}

const closeAllTags = () => {
  closeContextMenu()
  tagsStore.closeAll()
  // 跳到首页（affix 标签）
  const home = tagsStore.visitedViews.find(v => v.meta?.affix)
  if (home && home.path !== route.path) {
    router.push(home.fullPath || home.path)
  } else if (!home) {
    router.push('/home')
  }
}

// ===== 路由切换时自动添加标签 =====
watch(
  () => route.path,
  () => {
    tagsStore.addView(route)
    nextTick(checkScrollable)
  },
  { immediate: true }
)

watch(
  visitedViews,
  () => nextTick(checkScrollable),
  { deep: true }
)

onMounted(() => {
  document.addEventListener('click', handleClickDocument)
  document.addEventListener('scroll', handleScroll, true)
  window.addEventListener('resize', checkScrollable)
  nextTick(checkScrollable)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickDocument)
  document.removeEventListener('scroll', handleScroll, true)
  window.removeEventListener('resize', checkScrollable)
})
</script>

<style scoped>
.tags-view-wrapper {
  display: flex;
  align-items: center;
  height: 38px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 8px;
  position: relative;
}

.scroll-btn {
  flex-shrink: 0;
  padding: 0 4px;
  height: 100%;
  color: #606266;
}

.tags-scroll-container {
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
  height: 100%;
}
.tags-scroll-container::-webkit-scrollbar { display: none; }

.tags-track {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 100%;
  padding: 0 4px;
  white-space: nowrap;
}

.tag-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
  border: 1px solid #d8dce5;
  border-radius: 2px;
  background: #fff;
  color: #495060;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
  user-select: none;
}

.tag-item:hover {
  color: var(--el-color-primary);
}

.tag-item.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.tag-item.is-affix {
  border-style: dashed;
  background: #f5f7fa;
}

.tag-title {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.close-icon {
  font-size: 12px;
  border-radius: 50%;
  padding: 2px;
  transition: background 0.2s;
}
.close-icon:hover {
  background: rgba(0, 0, 0, 0.1);
}

.affix-icon {
  font-size: 12px;
  color: #909399;
}

/* 右键菜单 */
.context-menu {
  position: fixed;
  z-index: 99999;
  min-width: 140px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  margin: 0;
  list-style: none;
}

.context-menu li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 13px;
  color: #303133;
  cursor: pointer;
  transition: background 0.15s;
}

.context-menu li:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.context-menu li.is-disabled {
  color: #c0c4cc;
  cursor: not-allowed;
}
.context-menu li.is-disabled:hover {
  background: transparent;
  color: #c0c4cc;
}
</style>