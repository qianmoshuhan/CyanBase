<template>
  <div class="biz-unit-scope-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>经营单元授权管理</span>
          <div>
            <!-- 租户选择器：始终显示，admin 可跨租户切换，租户用户置灰锁定当前租户 -->
            <el-select
              v-model="selectedTenantId"
              :disabled="!isAdminActor"
              placeholder="请选择租户"
              style="width: 240px"
              @change="handleTenantChange"
            >
              <el-option
                v-for="t in tenantList"
                :key="t.id"
                :label="t.tenantName"
                :value="t.id"
              />
            </el-select>
          </div>
        </div>
      </template>

      <div class="main-layout">
        <!-- ============ 左栏：经营单元树 ============ -->
        <div class="left-panel">
          <div class="panel-header">
            <el-input
              v-model="treeFilter"
              placeholder="搜索经营单元"
              clearable
              size="small"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
          </div>
          <div class="tree-scroll">
            <el-tree
              ref="bizUnitTreeRef"
              :data="bizUnitTree"
              node-key="id"
              :props="{ label: 'deptName', children: 'children' }"
              :filter-node-method="filterTreeNode"
              :expand-on-click-node="false"
              highlight-current
              default-expand-all
              @node-click="handleNodeClick"
            >
              <template #default="{ data }">
                <span class="tree-node" :class="{ 'is-biz-unit': data.isBizUnit === 1 }">
                  <el-icon v-if="data.isBizUnit === 1"><OfficeBuilding /></el-icon>
                  <el-icon v-else><FolderOpened /></el-icon>
                  <span class="node-label">{{ data.deptName }}</span>
                  <el-tag v-if="data.isBizUnit === 1" type="warning" size="small" effect="plain">经营单元</el-tag>
                </span>
              </template>
            </el-tree>
          </div>
        </div>

        <!-- ============ 右栏：绑定用户列表 ============ -->
        <div class="right-panel">
          <div v-if="!selectedBizUnitId" class="empty-hint">
            <el-empty description="请在左侧选择一个经营单元" />
          </div>
          <div v-else>
            <div class="panel-header">
              <span class="current-label">
                当前经营单元：<strong>{{ currentBizUnitName }}</strong>
                <el-tag type="info" effect="plain" style="margin-left:8px">已绑定 {{ bindUsers.length }} 人</el-tag>
              </span>
              <div>
                <el-button type="primary" :disabled="loading || !canAssign" @click="openBatchAssignDialog">
                  <el-icon><Plus /></el-icon> 批量绑定
                </el-button>
                <el-button @click="fetchBindUsers">
                  <el-icon><Refresh /></el-icon> 刷新
                </el-button>
              </div>
            </div>

            <el-table :data="bindUsers" border stripe v-loading="loading" height="calc(100% - 60px)">
              <el-table-column type="index" label="#" width="55" align="center"
                :index="(i) => i + 1" />
              <el-table-column prop="userName" label="登录账号" width="130" />
              <el-table-column prop="nickName" label="姓名" width="120" />
              <el-table-column prop="phone" label="手机号" width="130" />
              <el-table-column prop="deptName" label="所属部门" width="140" show-overflow-tooltip />
              <el-table-column prop="roleName" label="绑定角色" min-width="140">
                <template #default="{ row }">
                  <el-select
                    v-if="canAssign"
                    v-model="row.roleId"
                    placeholder="请选择角色"
                    size="small"
                    style="width: 100%"
                    clearable
                    @change="(val) => handleChangeRole(row, val)"
                  >
                    <el-option
                      v-for="role in roleOptions"
                      :key="role.id"
                      :label="role.roleName"
                      :value="role.id"
                    >
                      <span>{{ role.roleName }}</span>
                      <span style="float:right;color:#8492a6;font-size:12px">{{ role.roleKey }}</span>
                    </el-option>
                  </el-select>
                  <el-tag v-else type="info">{{ row.roleName || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
                    {{ row.status === 0 ? '正常' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="bindTime" label="绑定时间" width="170" />
              <el-table-column label="操作" width="100" fixed="right" v-if="canAssign">
                <template #default="{ row }">
                  <el-button type="danger" link :loading="row._unbinding" @click="handleUnbind(row)">解绑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>
    </el-card>

    <!-- ============ 批量绑定用户 Dialog ============ -->
    <el-dialog
      v-model="batchAssignVisible"
      title="批量绑定用户 —— " + currentBizUnitName
      width="800px"
      :close-on-click-modal="false"
    >
      <div class="assign-header">
        <el-input v-model="userSearch.keyword" placeholder="搜索登录账号/昵称" clearable style="width: 260px" @keyup.enter="searchUsers">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="searchUsers">搜索</el-button>
        <el-tag type="warning" effect="plain" style="margin-left:auto">
          将用角色「<strong>{{ batchRoleOption?.roleName || '未选择' }}</strong>」批量绑定给选中用户
        </el-tag>
      </div>

      <div class="assign-body">
        <div class="assign-role-bar">
          <span>角色：</span>
          <el-select v-model="batchRoleOption" placeholder="请选择角色" style="width: 240px" filterable>
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.roleName + ' (' + role.roleKey + ')'"
              :value="role"
            />
          </el-select>
          <el-tag type="info" effect="plain" style="margin-left:12px">
            已选 {{ batchSelectedUsers.length }} 人 / {{ userList.length }} 人
          </el-tag>
        </div>

        <el-table
          :data="userList"
          border
          height="360"
          v-loading="userLoading"
          @selection-change="handleBatchSelection"
        >
          <el-table-column type="selection" width="50" />
          <el-table-column prop="userName" label="登录账号" width="130" />
          <el-table-column prop="nickName" label="姓名" width="120" />
          <el-table-column prop="phone" label="手机号" width="130" />
          <el-table-column prop="deptName" label="所属部门" min-width="140" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
                {{ row.status === 0 ? '正常' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="batchAssignVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="batchSelectedUsers.length === 0 || !batchRoleOption"
          :loading="batchSaving"
          @click="confirmBatchAssign"
        >
          确认绑定（{{ batchSelectedUsers.length }} 人 × 角色「{{ batchRoleOption?.roleName || '' }}」）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, OfficeBuilding, FolderOpened, Plus, Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import request from '@/utils/request'
import {
  getBizUnitTree,
  listBizUnitScopeUsers,
  listAvailableRoles,
  assignBizUnitScope
} from '@/api/bizUnitScope'

const userStore = useUserStore()

// admin 跨租户时可选租户列表
const tenantList = ref([])
const selectedTenantId = ref(null)

// 左栏：经营单元树
const bizUnitTree = ref([])
const bizUnitTreeRef = ref(null)
const treeFilter = ref('')

// 右栏：绑定用户
const selectedBizUnitId = ref(null)
const currentBizUnitName = ref('')
const bindUsers = ref([])
const loading = ref(false)
const roleOptions = ref([])

// 批量绑定 Dialog
const batchAssignVisible = ref(false)
const userSearch = reactive({ keyword: '' })
const userList = ref([])
const userLoading = ref(false)
const batchSelectedUsers = ref([])
const batchRoleOption = ref(null)
const batchSaving = ref(false)

// 权限门卡
const canAssign = computed(() => userStore.hasPerm('system:user:bizUnitAssign'))

// admin 判定：getTenantId 是 0 或 null → 平台侧
const isAdminActor = computed(() => {
  const tid = userStore.getTenantId
  return tid === 0 || tid === null || tid === undefined
})

const effectiveTenantId = computed(() => {
  if (isAdminActor.value) return selectedTenantId.value
  return userStore.getTenantId
})

// ============ 树过滤 ============
watch(treeFilter, (val) => {
  bizUnitTreeRef.value?.filter(val)
})
function filterTreeNode(value, data) {
  if (!value) return true
  return (data.deptName || '').toLowerCase().includes(value.toLowerCase())
}

// ============ 初始化 ============
onMounted(async () => {
  const tid = userStore.getTenantId

  // 统一数据源：都调 /system/tenant/all 拿全量租户列表
  // （sys_tenant 是租户元表，TenantLine 不对它做租户隔离，两边都能拿到全量）
  try {
    const res = await request({ url: '/system/tenant/all', method: 'get' })
    const allTenants = res.data || []
    if (isAdminActor.value) {
      // admin：全量展示，支持跨租户切换
      tenantList.value = allTenants
    } else {
      // 租户用户：只展示自己那条，置灰锁定
      const self = allTenants.find(t => t.id === tid)
      tenantList.value = self ? [self] : [{ id: tid, tenantName: '未知租户 #' + tid }]
      selectedTenantId.value = tid
    }
  } catch (e) {
    console.warn('加载租户列表失败：', e.message)
    // 兜底：至少塞一条占位
    tenantList.value = [{ id: tid, tenantName: '未知租户' }]
    selectedTenantId.value = tid
  }
  // 拉经营单元树（TenantLine 自动按 effectiveTenantId 过滤）
  await fetchTree()
  // 拉角色下拉
  await fetchRoles()
})

async function fetchTree() {
  if (!effectiveTenantId.value) return
  try {
    const res = await getBizUnitTree({ tenantId: effectiveTenantId.value })
    bizUnitTree.value = res.data || []
  } catch (e) {
    ElMessage.error('加载经营单元树失败：' + (e.message || ''))
  }
}

async function fetchRoles() {
  if (!effectiveTenantId.value) return
  try {
    const res = await listAvailableRoles(effectiveTenantId.value)
    roleOptions.value = res.data || []
  } catch (e) {
    console.warn('加载角色失败：', e.message)
  }
}

function handleTenantChange() {
  selectedBizUnitId.value = null
  currentBizUnitName.value = ''
  bindUsers.value = []
  fetchTree()
  fetchRoles()
}

// ============ 树节点点击 ============
function handleNodeClick(data) {
  // 只允许 isBizUnit=1 的节点作为经营单元查看绑定用户
  if (data.isBizUnit !== 1) {
    ElMessage.info('只有「经营单元」类型的部门可查看绑定用户（带 🏢 图标的节点）')
    return
  }
  selectedBizUnitId.value = data.id
  currentBizUnitName.value = data.deptName
  fetchBindUsers()
}

async function fetchBindUsers() {
  if (!selectedBizUnitId.value) return
  loading.value = true
  try {
    const res = await listBizUnitScopeUsers(selectedBizUnitId.value)
    bindUsers.value = res.data || []
  } catch (e) {
    ElMessage.error('加载绑定用户失败：' + (e.message || ''))
  } finally {
    loading.value = false
  }
}

// ============ 改角色（行内 select change） ============
async function handleChangeRole(row, newRoleId) {
  const oldRoleId = row.roleId
  try {
    await assignBizUnitScope({
      userId: row.userId,
      bizUnitId: selectedBizUnitId.value,
      roleId: newRoleId || null
    })
    row.roleId = newRoleId || null
    row.roleName = newRoleId
      ? (roleOptions.value.find(r => r.id === newRoleId)?.roleName || '未知角色')
      : null
    ElMessage.success(newRoleId ? '角色更新成功' : '角色已清除')
  } catch (e) {
    row.roleId = oldRoleId
    ElMessage.error('保存失败：' + (e.message || ''))
  }
}

// ============ 解绑 ============
async function handleUnbind(row) {
  await ElMessageBox.confirm(
    `确认解除用户「${row.nickName || row.userName}」在经营单元「${currentBizUnitName.value}」的绑定？`,
    '解绑确认',
    { confirmButtonText: '确认解绑', cancelButtonText: '取消', type: 'warning' }
  )
  row._unbinding = true
  try {
    await assignBizUnitScope({
      userId: row.userId,
      bizUnitId: selectedBizUnitId.value,
      roleId: null
    })
    ElMessage.success('解绑成功')
    fetchBindUsers()
  } catch (e) {
    ElMessage.error('解绑失败：' + (e.message || ''))
  } finally {
    row._unbinding = false
  }
}

// ============ 批量绑定 ============
function openBatchAssignDialog() {
  batchAssignVisible.value = true
  batchSelectedUsers.value = []
  batchRoleOption.value = null
  userSearch.keyword = ''
  userList.value = []
  searchUsers()
}

async function searchUsers() {
  userLoading.value = true
  try {
    const params = {
      pageNum: 1,
      pageSize: 500,
      userName: userSearch.keyword || undefined,
      nickName: userSearch.keyword || undefined
    }
    // admin 跨租户时需要跳过 TenantLine，但 TenantUserController 默认不支持外部传 tenantId
    // 这里先不传，后端 TenantLine 自动按当前登录用户的 tenantId 过滤
    // 如果 admin 跨租户场景不够，后续加一个 skip TenantLine 的专用接口
    const res = await request({ url: '/tenant/user/list', method: 'get', params })
    userList.value = res.data?.records || []
  } catch (e) {
    ElMessage.error('搜索用户失败：' + (e.message || ''))
  } finally {
    userLoading.value = false
  }
}

function handleBatchSelection(selection) {
  batchSelectedUsers.value = selection
}

async function confirmBatchAssign() {
  if (batchSelectedUsers.value.length === 0 || !batchRoleOption.value) {
    ElMessage.warning('请先选择用户和角色')
    return
  }
  await ElMessageBox.confirm(
    `确认将经营单元「${currentBizUnitName.value}」+ 角色「${batchRoleOption.value.roleName}」批量绑定给 ${batchSelectedUsers.value.length} 个用户？`,
    '批量绑定确认',
    { confirmButtonText: '确认绑定', cancelButtonText: '取消', type: 'warning' }
  )

  batchSaving.value = true
  let successCount = 0
  let failCount = 0
  try {
    for (const u of batchSelectedUsers.value) {
      try {
        await assignBizUnitScope({
          userId: u.userId,
          bizUnitId: selectedBizUnitId.value,
          roleId: batchRoleOption.value.id
        })
        successCount++
      } catch (e) {
        failCount++
        console.warn('批量绑定失败 userId=' + u.userId, e.message)
      }
    }
    const msg = successCount > 0
      ? `批量绑定完成：成功 ${successCount} 人${failCount > 0 ? '，失败 ' + failCount + ' 人' : ''}`
      : `全部绑定失败`
    if (failCount === 0) ElMessage.success(msg)
    else ElMessage.warning(msg)
    batchAssignVisible.value = false
    fetchBindUsers()
  } finally {
    batchSaving.value = false
  }
}
</script>

<style scoped>
.biz-unit-scope-container {
  padding: 16px;
  height: calc(100vh - 120px);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header span {
  font-size: 16px;
  font-weight: 600;
}
.main-layout {
  display: flex;
  gap: 16px;
  height: calc(100vh - 200px);
}
.left-panel {
  width: 320px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.panel-header {
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.tree-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  font-size: 13px;
}
.tree-node.is-biz-unit .node-label {
  color: #e6a23c;
  font-weight: 500;
}
.right-panel {
  flex: 1;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.right-panel .panel-header {
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.current-label strong {
  font-size: 15px;
  color: #303133;
}
.empty-hint {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.assign-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.assign-body {
  border-top: 1px solid var(--el-border-color-lighter);
}
.assign-role-bar {
  padding: 10px 0;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
</style>