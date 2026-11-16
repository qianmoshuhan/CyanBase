<template>
  <div class="org-tree-panel" :class="{ collapsed: isCollapsed }" :style="panelStyle">
    <!-- 折叠窄条 -->
    <template v-if="isCollapsed">
      <div class="collapsed-bar">
        <el-tooltip placement="right" content="展开组织树" :show-after="200">
          <el-button circle size="small" :icon="Expand" @click="toggleCollapse" />
        </el-tooltip>
      </div>
    </template>

    <!-- 展开面板 -->
    <template v-else>
      <div class="panel-header">
        <span class="panel-title">组织架构</span>
        <div class="panel-actions">
          <el-tooltip placement="top" content="刷新">
            <el-icon class="action-icon" @click="$emit('refresh')"><Refresh /></el-icon>
          </el-tooltip>
          <el-tooltip placement="top" content="折叠面板">
            <el-icon class="action-icon" @click="toggleCollapse"><Fold /></el-icon>
          </el-tooltip>
        </div>
      </div>

      <div v-if="showSearch" class="panel-search">
        <el-input
          v-model="filterText"
          placeholder="搜索组织名称"
          clearable
          :prefix-icon="Search"
          size="small"
        />
      </div>

      <el-scrollbar class="panel-scroll" :horizontal="true">
        <el-tree-v2
          ref="treeRef"
          class="org-tree"
          :data="displayTreeData"
          :props="treeProps"
          :highlight-current="true"
          :expand-on-click-node="false"
          :node-class="getTreeNodeClass"
          :height="treeHeight"
          :default-expanded-keys="effectiveExpandKeys"
          @node-click="handleNodeClick"
          @node-expand="onNodeExpand"
          @node-collapse="onNodeCollapse"
        >
          <template #default="{ data }">
            <span class="custom-tree-node" :title="data.label">
              <el-icon class="node-icon" :class="getIconClass(data)">
                <component :is="getIconComponent(data)" />
              </el-icon>
              <span class="node-label"
                    :class="{ 'frozen-node': isFrozenNode(data), 'biz-unit-label': data.nodeType === 'dept' && data.isBizUnit === 1 }">
                {{ data.label }}
              </span>
              <el-tag v-if="data.nodeType === 'dept' && data.isBizUnit === 1"
                size="small" type="success" effect="plain" class="biz-unit-tag">经营</el-tag>
            </span>
          </template>
        </el-tree-v2>
      </el-scrollbar>
    </template>

    <!-- 拖拽分割线 -->
    <div v-if="!isCollapsed" class="resize-handle" @mousedown="startResize" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import {
  Search, Refresh, Fold, Expand,
  OfficeBuilding, Collection, FolderOpened, Folder
} from '@element-plus/icons-vue'

const props = defineProps({
  treeData:          { type: Array,  default: () => [] },
  loading:           { type: Boolean, default: false },
  defaultCollapsed:  { type: Boolean, default: false },
  defaultWidth:      { type: Number, default: 280 },
  minWidth:          { type: Number, default: 180 },
  maxWidth:          { type: Number, default: 500 },
  showSearch:        { type: Boolean, default: true }
})
const emit = defineEmits(['node-click', 'refresh'])

// --------- 内部状态 ---------
const isCollapsed = ref(props.defaultCollapsed)
const currentWidth = ref(props.defaultWidth)
const filterText = ref('')
const treeRef = ref(null)
const treeHeight = ref(300)

const treeProps = computed(() => ({
  value: 'nodeId',
  label: 'label',
  children: 'children'
}))

const panelStyle = computed(() => ({
  width: isCollapsed.value ? '36px' : currentWidth.value + 'px',
  minWidth: isCollapsed.value ? '36px' : props.minWidth + 'px',
  maxWidth: props.maxWidth + 'px'
}))

// --------- 核心：前端过滤，保留命中节点 + 全部祖先 ---------
// 避开 el-tree-v2 暴露方法有限的问题，直接重建展示树
const displayTreeData = computed(() => {
  const kw = filterText.value.trim().toLowerCase()
  if (!kw) return props.treeData

  function prune(nodes) {
    if (!nodes?.length) return []
    const result = []
    for (const node of nodes) {
      const children = prune(node.children)
      const selfHit = node.label?.toLowerCase().includes(kw)
      if (selfHit || children.length) {
        result.push({ ...node, children })
      }
    }
    return result
  }
  return prune(props.treeData)
})

// ⭐⭐⭐ el-tree-v2 展开状态管理 —— 基于源码行为的正确实现
//
// el-tree-v2 源码关键行为（useTree.mjs）：
//   1) 只认 default-expanded-keys prop（不认 expand-keys！）
//   2) 每次 data 变化强制用 default-expanded-keys 重置内部展开状态
//   3) setExpandedKeys 自动补齐祖先链（传任意后代 key，祖先自动展开）
//   4) node-expand / node-collapse 事件只传 (nodeData, nodeInstance)，没有完整 expandKeys
//
// 方案：
//   a) effectiveExpandKeys = 全展开中间节点 - 用户手动折叠的节点
//   b) node-expand / node-collapse 维护 userCollapsedKeys
//   c) 搜索时清空 userCollapsedKeys → prune 后全展开 → 祖先链自动可见

function collectAllExpandKeys(nodes) {
  const keys = []
  function walk(list) {
    if (!list?.length) return
    for (const n of list) {
      if (n.children?.length) {
        keys.push(n.nodeId)
        walk(n.children)
      }
    }
  }
  walk(nodes)
  return keys
}

// ⭐ 给定一个节点，收集它所有子孙中间节点的 nodeId
// 用于：用户折叠一个节点时，也要把它的子孙中间节点从 effectiveExpandKeys 里移除
// 否则 el-tree-v2 的 setExpandedKeys 会自动补齐祖先链，把折叠的父节点又展开回去
function collectDescendantExpandKeys(node, map) {
  const keys = []
  function walk(n) {
    if (!n?.children?.length) return
    for (const c of n.children) {
      if (c.children?.length) {
        keys.push(c.nodeId)
        walk(c)
      }
    }
  }
  walk(node)
  return keys
}

// 用户手动折叠的节点 key 数组（每次修改都替换新引用，确保 Vue 响应式正确触发）
const userCollapsedKeys = ref([])

// ⭐⭐⭐ 传给 el-tree-v2 的 default-expanded-keys
// 关键：必须把折叠节点及其所有子孙中间节点一起移除
// 否则 setExpandedKeys 遍历子孙节点时会自动补齐祖先链 → 折叠的父节点被重新展开
const effectiveExpandKeys = computed(() => {
  const all = collectAllExpandKeys(displayTreeData.value)
  if (userCollapsedKeys.value.length === 0) return all
  // 把用户折叠的 key 都映射到对应的"自身+子孙中间节点"集合
  const removeSet = new Set()
  // 建立 nodeId → node 的快速查找（只含中间节点）
  const nodeMap = buildNodeMap(displayTreeData.value)
  for (const key of userCollapsedKeys.value) {
    removeSet.add(key)
    const node = nodeMap.get(key)
    if (node) {
      for (const desc of collectDescendantExpandKeys(node, nodeMap)) {
        removeSet.add(desc)
      }
    }
  }
  return all.filter(k => !removeSet.has(k))
})

// 建立 nodeId → 节点对象 的 Map（从 displayTreeData 构建）
function buildNodeMap(nodes) {
  const map = new Map()
  function walk(list) {
    if (!list?.length) return
    for (const n of list) {
      map.set(n.nodeId, n)
      walk(n.children)
    }
  }
  walk(nodes)
  return map
}

// 用户手动展开节点 → 从折叠集合移除
// 同时也要移除该节点的所有子孙（因为用户手动展开了，说明想看到完整子树）
function onNodeExpand(data, node) {
  if (!node?.key) return
  const nodeMap = buildNodeMap(displayTreeData.value)
  const removeKeys = [node.key, ...collectDescendantExpandKeys(nodeMap.get(node.key), nodeMap)]
  userCollapsedKeys.value = userCollapsedKeys.value.filter(k => !removeKeys.includes(k))
}
// 用户手动折叠节点 → 加入折叠集合
function onNodeCollapse(data, node) {
  if (!node?.key) return
  if (!userCollapsedKeys.value.includes(node.key)) {
    userCollapsedKeys.value = [...userCollapsedKeys.value, node.key]
  }
}

// 搜索时清空用户折叠记录 → 确保 prune 后的树全展开
// 清除搜索时也清空 → 回到默认全展开状态
watch(filterText, () => {
  userCollapsedKeys.value = []
})

// 搜索场景自动选中第一个命中节点（视觉高亮）
watch(displayTreeData, (data) => {
  const kw = filterText.value.trim().toLowerCase()
  if (kw && data?.length) {
    nextTick(() => {
      const firstHit = findFirstHit(data, kw)
      if (firstHit && treeRef.value?.setCurrentKey) {
        treeRef.value.setCurrentKey(firstHit.nodeId, true)
      }
    })
  }
}, { immediate: true })

function findFirstHit(nodes, kw) {
  if (!nodes?.length) return null
  for (const n of nodes) {
    if (n.label?.toLowerCase().includes(kw)) return n
    const deeper = findFirstHit(n.children, kw)
    if (deeper) return deeper
  }
  return null
}

// --------- 图标 & 样式辅助 ---------
function getIconComponent(data) {
  if (data.nodeType === 'platform-root') return OfficeBuilding
  if (data.nodeType === 'tenant')       return Collection
  if (data.isBizUnit === 1)             return FolderOpened
  return Folder
}
function getIconClass(data)    { return `icon-${data.nodeType}` }
function isFrozenNode(data)    { return data.nodeType === 'tenant' && data.status !== 0 }
function getTreeNodeClass(data) { return isFrozenNode(data) ? 'tree-node-frozen' : '' }

// --------- 事件 ---------
function handleNodeClick(data) { emit('node-click', data) }
function toggleCollapse()      { isCollapsed.value = !isCollapsed.value }

// --------- 拖拽（增量计算：避免 clientX 绝对坐标被系统菜单偏移污染） ---------
let isResizing = false
let resizeStartX = 0
let resizeStartWidth = 0
function startResize(e) {
  isResizing = true
  resizeStartX = e.clientX
  resizeStartWidth = currentWidth.value
  document.addEventListener('mousemove', onResize)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  e.preventDefault()
}
function onResize(e) {
  if (!isResizing) return
  const delta = e.clientX - resizeStartX
  const w = Math.min(props.maxWidth, Math.max(props.minWidth, resizeStartWidth + delta))
  currentWidth.value = w
}
function stopResize() {
  isResizing = false
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

// --------- 树高度（虚拟滚动需要固定 height） ---------
function calcHeight() {
  const headerH = isCollapsed.value ? 0 : 44
  const searchH  = (isCollapsed.value || !props.showSearch) ? 0 : 40
  treeHeight.value = Math.max(200, window.innerHeight - 180 - headerH - searchH)
}
onMounted(() => { calcHeight(); window.addEventListener('resize', calcHeight) })
onBeforeUnmount(() => {
  window.removeEventListener('resize', calcHeight)
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
})

// --------- 暴露给父组件的方法 ---------
defineExpose({
  /**
   * 重置树状态：清空搜索框、取消选中高亮、恢复默认全展开
   * 父页面 "重置" 按钮调用，保持左右面板状态一致
   */
  reset() {
    filterText.value = ''
    userCollapsedKeys.value = []
    nextTick(() => treeRef.value?.setCurrentKey(null))
  }
})
</script>

<style scoped>
.org-tree-panel {
  position: relative;
  height: 100%;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.2s ease;
  flex-shrink: 0;
}
.org-tree-panel.collapsed {
  border-right: none;
  align-items: center;
  padding-top: 12px;
}
.collapsed-bar {
  display: flex;
  justify-content: center;
}

.panel-header {
  height: 44px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.panel-actions {
  display: flex;
  gap: 8px;
}
.action-icon {
  font-size: 16px;
  cursor: pointer;
  color: var(--el-text-color-secondary);
  transition: color 0.2s;
}
.action-icon:hover { color: var(--el-color-primary); }

.panel-search {
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}
.panel-scroll {
  flex: 1;
  overflow: hidden;
}

/* 自定义节点 —— 不截断文字 + 横向滚动 */
.custom-tree-node {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  padding-right: 8px;
}
.node-icon {
  font-size: 14px;
  flex-shrink: 0;
}
.icon-platform-root { color: var(--el-color-primary); }
.icon-tenant        { color: #67c23a; }
.icon-dept          { color: var(--el-text-color-secondary); }
.node-label {
  font-size: 13px;
}
.frozen-node {
  color: var(--el-text-color-placeholder);
  text-decoration: line-through;
}
.biz-unit-label {
  color: var(--el-color-success);
}
.biz-unit-tag {
  margin-left: 2px;
  padding: 0 4px;
  height: 18px;
  line-height: 16px;
  font-size: 11px;
}

/* 租户冻结置灰 */
:deep(.tree-node-frozen) {
  opacity: 0.5;
}

/* 分割线 */
.resize-handle {
  position: absolute;
  top: 0;
  right: -3px;
  width: 6px;
  height: 100%;
  cursor: col-resize;
  z-index: 10;
}
.resize-handle:hover {
  background: var(--el-color-primary-light-5);
}
</style>