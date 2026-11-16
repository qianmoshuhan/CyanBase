<template>
  <div class="principal-scope-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>平台主体 Scope 管理</span>
          <el-tag type="warning" effect="plain">平台超管专属</el-tag>
        </div>
      </template>

      <!-- Tab 切换：主体列表 + 审计日志 -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- ====================== Tab 1：主体列表 ====================== -->
        <el-tab-pane label="主体列表" name="principals">

          <!-- 操作栏 -->
          <div style="margin-bottom:12px">
            <el-button type="primary" @click="openAddDialog" v-hasPermi="['system:principal-scope:assign']">
              新增平台主体
            </el-button>
          </div>
          <!-- 搜索 -->
          <el-form :inline="true" class="search-form">
            <el-form-item label="用户名">
              <el-input v-model="searchForm.userName" placeholder="请输入用户名" clearable />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="searchForm.nickName" placeholder="请输入昵称" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="fetchPrincipals">查询</el-button>
              <el-button @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>

          <!-- 表格 -->
          <el-table :data="principalList" border stripe v-loading="tableLoading">
            <el-table-column type="index" label="序号" width="70" align="center"
              :index="(i) => (pagination.current - 1) * pagination.size + i + 1" />
            <el-table-column prop="userName" label="登录账号" min-width="120" align="center" />
            <el-table-column prop="nickName" label="昵称" min-width="120" align="center" />
            <el-table-column prop="email" label="邮箱" min-width="180" align="center" />
            <el-table-column prop="phone" label="手机号" width="130" align="center" />
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 0 ? 'success' : 'danger'">
                  {{ row.status === 0 ? '正常' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="scopeCount" label="已分配 Scope" width="120" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.scopeCount > 0" type="primary" effect="plain">
                  {{ row.scopeCount }} 个租户
                </el-tag>
                <el-tag v-else type="info" effect="plain">未分配</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleAssignScopes(row)">分配 Scope</el-button>
                <el-button type="danger" link @click="handleRevokeAll(row)" :disabled="row.scopeCount === 0">
                  撤销全部
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <div class="pagination">
            <el-pagination
              v-model:current-page="pagination.current"
              v-model:page-size="pagination.size"
              :total="pagination.total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchPrincipals"
              @size-change="fetchPrincipals"
            />
          </div>
        </el-tab-pane>

        <!-- ====================== Tab 2：审计日志 ====================== -->
        <el-tab-pane label="特权操作审计" name="audit">
          <el-form :inline="true" class="search-form">
            <el-form-item label="操作类型">
              <el-select v-model="auditForm.actionType" placeholder="全部" clearable style="width:180px">
                <el-option label="切换租户" value="SWITCH_TENANT" />
                <el-option label="Scope 分配" value="SCOPE_ASSIGN" />
                <el-option label="Scope 撤销" value="SCOPE_REVOKE" />
                <el-option label="跨租户访问" value="CROSS_TENANT_ACCESS" />
              </el-select>
            </el-form-item>
            <el-form-item label="主体用户ID">
              <el-input v-model.number="auditForm.principalUserId" placeholder="用户ID" clearable style="width:130px" />
            </el-form-item>
            <el-form-item label="目标租户ID">
              <el-input v-model.number="auditForm.targetTenantId" placeholder="租户ID" clearable style="width:130px" />
            </el-form-item>
            <el-form-item label="操作时间">
              <el-date-picker
                v-model="auditForm.timeRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DDTHH:mm:ss"
                style="width:380px"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="fetchAuditLogs">查询</el-button>
              <el-button @click="resetAudit">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="auditList" border stripe v-loading="auditLoading">
            <el-table-column type="index" label="序号" width="70" align="center"
              :index="(i) => (auditPagination.current - 1) * auditPagination.size + i + 1" />
            <el-table-column prop="actionType" label="操作类型" width="140" align="center">
              <template #default="{ row }">
                <el-tag :type="auditTagType(row.actionType)">{{ auditLabel(row.actionType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="principalUserId" label="主体用户ID" width="100" align="center" />
            <el-table-column prop="principalUserName" label="主体用户名" min-width="120" align="center" />
            <el-table-column prop="targetTenantId" label="目标租户ID" width="100" align="center">
              <template #default="{ row }">
                <span v-if="row.targetTenantId">{{ row.targetTenantId }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="targetTenantName" label="目标租户名" min-width="160" align="center">
              <template #default="{ row }">
                <span v-if="row.targetTenantName">{{ row.targetTenantName }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="ipaddr" label="操作IP" width="130" align="center" />
            <el-table-column prop="createTime" label="操作时间" width="180" align="center" />
            <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="auditPagination.current"
              v-model:page-size="auditPagination.size"
              :total="auditPagination.total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchAuditLogs"
              @size-change="fetchAuditLogs"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- ====================== 一体化新增平台主体对话框 ====================== -->
    <el-dialog v-model="addDialogVisible" title="新增平台主体账号" width="860px" :close-on-click-modal="false">
      <div v-if="addLoading" style="text-align:center;padding:40px">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
        <div style="margin-top:12px;color:#909399">加载中...</div>
      </div>
      <div v-else>
        <el-form ref="addFormRef" :model="addForm" :rules="addFormRules" label-width="100px">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="用户名" prop="userName"><el-input v-model="addForm.userName" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="昵称" prop="nickName"><el-input v-model="addForm.nickName" /></el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="addForm.phone" @blur="validatePhoneAsync" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="密码" prop="password">
                <el-input v-model="addForm.password" type="password" show-password />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-radio-group v-model="addForm.status">
                  <el-radio :value="0">正常</el-radio>
                  <el-radio :value="1">停用</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="邮箱" prop="email"><el-input v-model="addForm.email" /></el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-divider content-position="left">Scope 租户角色分配</el-divider>

        <div style="margin-bottom:8px;color:#606266;font-size:13px">
          <el-icon><InfoFilled /></el-icon>
          勾选下方租户作为该主体的访问范围，每行必须独立选择角色。
        </div>
        <el-table :data="scopeOptions" border height="340" @selection-change="handleScopeSelection">
          <el-table-column type="selection" width="50" />
          <el-table-column prop="id" label="租户ID" width="80" align="center" />
          <el-table-column prop="tenantName" label="租户名称" min-width="140" />
          <el-table-column prop="tenantCode" label="租户编码" width="120" align="center" />
          <el-table-column prop="status" label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '启用' : '冻结' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="200">
            <template #default="{ row }">
              <el-select v-model="row.roleId" placeholder="请选择角色" size="small" style="width:100%"
                :disabled="!isRowSelected(row)">
                <el-option v-for="role in getRolesByTenant(row.id)" :key="role.id"
                  :label="role.roleName" :value="role.id" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSaving" @click="handleAddSubmit">
          确定并创建（Scope: 已选 {{ selectedTenantIds.length }} 个）
        </el-button>
      </template>
    </el-dialog>

    <!-- ====================== Scope 分配对话框 ====================== -->
    <el-dialog v-model="scopeDialogVisible" :title="`为 ${currentPrincipal?.userName} 分配 Scope`" width="720px"
      :close-on-click-modal="false">
      <div v-if="scopeLoading" style="text-align:center;padding:40px">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
        <div style="margin-top:12px;color:#909399">加载中...</div>
      </div>

      <div v-else>
        <div style="margin-bottom:12px;color:#606266;font-size:13px">
          <el-icon><InfoFilled /></el-icon>
          勾选下方租户作为该主体的访问范围。全量覆盖模式：保存后会先清空旧关联再插入新关联。
        </div>
        <el-table :data="scopeOptions" border height="380" @selection-change="handleScopeSelection">
          <el-table-column type="selection" width="50" />
          <el-table-column prop="id" label="租户ID" width="80" align="center" />
          <el-table-column prop="tenantName" label="租户名称" min-width="140" />
          <el-table-column prop="tenantCode" label="租户编码" width="120" align="center" />
          <el-table-column prop="status" label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '启用' : '冻结' }}</el-tag>
            </template>
          </el-table-column>
          <!-- 角色下拉：每行独立，只展示归属该行 tenantId 的角色 -->
          <el-table-column label="角色" min-width="200">
            <template #default="{ row }">
              <el-select
                v-model="row.roleId"
                placeholder="请选择角色"
                size="small"
                style="width:100%"
                :disabled="!isRowSelected(row)"
                clearable
              >
                <el-option
                  v-for="role in getRolesByTenant(row.id)"
                  :key="role.id"
                  :label="role.roleName"
                  :value="role.id"
                >
                  <span>{{ role.roleName }}</span>
                  <span style="float:right;color:#8492a6;font-size:12px">{{ role.roleKey }}</span>
                </el-option>
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="scopeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="scopeSaving" @click="handleSaveScopes">
          保存（选中 {{ selectedTenantIds.length }} 个）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { Loading, InfoFilled } from '@element-plus/icons-vue'
import {
  listPrincipals,
  getPrincipalScopes,
  assignPrincipalScopes,
  revokeAllPrincipalScopes,
  listAuditLogs,
  getAllAvailableRoles,
  addPrincipal
} from '@/api/principalScope'
import { getAllTenant } from '@/api/tenant'

// ==================== Tab 切换 ====================
const activeTab = ref('principals')
function handleTabChange(tab) {
  if (tab === 'audit') fetchAuditLogs()
  else fetchPrincipals()
}

// ==================== 主体列表 ====================
const principalList = ref([])
const tableLoading = ref(false)
const searchForm = reactive({ userName: '', nickName: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

async function fetchPrincipals() {
  tableLoading.value = true
  try {
    const res = await listPrincipals({
      pageNum: pagination.current,
      pageSize: pagination.size,
      userName: searchForm.userName || undefined,
      nickName: searchForm.nickName || undefined
    })
    principalList.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (e) {
    ElMessage.error('查询失败：' + (e.message || ''))
  } finally {
    tableLoading.value = false
  }
}

function handleReset() {
  searchForm.userName = ''
  searchForm.nickName = ''
  pagination.current = 1
  fetchPrincipals()
}

// ==================== Scope 分配 ====================
const scopeDialogVisible = ref(false)
const scopeLoading = ref(false)
const scopeSaving = ref(false)
const currentPrincipal = ref(null)
const scopeOptions = ref([])        // 所有可选租户（每条约含 id, tenantName, tenantCode, status, roleId）
const selectedTenantIds = ref([])   // 当前已勾选的 tenantIds
const roleGrouped = ref({})         // 角色缓存：{ tenantId: [ { id, roleName, roleKey, tenantId } ] }

/** 根据 tenantId 获取该租户下的可用角色列表 */
function getRolesByTenant(tenantId) {
  return roleGrouped.value[tenantId] || []
}

/** 判断某行是否被勾选（控制角色下拉 disabled） */
function isRowSelected(row) {
  return selectedTenantIds.value.includes(row.id)
}

async function handleAssignScopes(row) {
  currentPrincipal.value = row
  scopeDialogVisible.value = true
  scopeLoading.value = true
  selectedTenantIds.value = []
  roleGrouped.value = {}
  try {
    // 并行加载 3 个接口：已分配 scope + 全部可选租户 + 全部可用角色
    const [scopesRes, allRes, rolesRes] = await Promise.all([
      getPrincipalScopes(row.userId),
      getAllTenant(),
      getAllAvailableRoles()
    ])

    // 1. 已分配 scope 列表 (VO: { tenantId, roleId, tenantName, tenantCode, status })
    const scopes = scopesRes.data || []
    const scopedMap = {}
    for (const s of scopes) {
      scopedMap[s.tenantId] = s.roleId
    }
    selectedTenantIds.value = Object.keys(scopedMap).map(Number)

    // 2. 全部可用租户（过滤停用），每条加 roleId 字段
    const allTenants = (allRes.data || []).filter(t => t.status === 0)
    scopeOptions.value = allTenants.map(t => ({
      ...t,
      roleId: scopedMap[t.id] ?? null   // 回显已绑定的 roleId（存量历史数据可能为 null）
    }))

    // 3. 角色缓存：按 tenantId 分组
    const allRoles = rolesRes.data || []
    const grouped = {}
    for (const r of allRoles) {
      const tid = r.tenantId
      if (!grouped[tid]) grouped[tid] = []
      grouped[tid].push(r)
    }
    roleGrouped.value = grouped
  } catch (e) {
    ElMessage.error('加载失败：' + (e.message || ''))
  } finally {
    scopeLoading.value = false
  }
}

function handleScopeSelection(selection) {
  selectedTenantIds.value = selection.map(s => s.id)
  // 取消勾选时自动清空该行 roleId（避免保存脏数据）
  const selectedIds = new Set(selectedTenantIds.value)
  for (const opt of scopeOptions.value) {
    if (!selectedIds.has(opt.id)) {
      opt.roleId = null
    }
  }
}

async function handleSaveScopes() {
  // 校验：勾选的租户必须选角色
  const unassigned = scopeOptions.value.filter(
    o => selectedTenantIds.value.includes(o.id) && !o.roleId
  )
  if (unassigned.length > 0) {
    ElMessage.error(`以下租户未选择角色：${unassigned.map(o => o.tenantName).join('、')}`)
    return
  }

  const assignments = scopeOptions.value
    .filter(o => selectedTenantIds.value.includes(o.id))
    .map(o => ({ tenantId: o.id, roleId: o.roleId }))

  scopeSaving.value = true
  try {
    await assignPrincipalScopes(currentPrincipal.value.userId, assignments)
    ElMessage.success(`Scope 保存成功，共分配 ${assignments.length} 个租户`)
    scopeDialogVisible.value = false
    fetchPrincipals()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || ''))
  } finally {
    scopeSaving.value = false
  }
}

async function handleRevokeAll(row) {
  await ElMessageBox.confirm(
    `确认撤销用户「${row.userName}」的全部 Scope 权限？`,
    '操作确认',
    { confirmButtonText: '确认撤销', cancelButtonText: '取消', type: 'warning' }
  )
  await revokeAllPrincipalScopes(row.userId)
  ElMessage.success('已撤销全部 Scope')
  fetchPrincipals()
}

// ==================== 一体化新增平台主体 ====================
const addDialogVisible = ref(false)
const addLoading = ref(false)
const addSaving = ref(false)
const addFormRef = ref(null)
const addForm = reactive({
  userName: '', nickName: '', phone: '', password: '',
  status: 0, email: ''
})
const addFormRules = {
  userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickName: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码不少于6位', trigger: 'blur' }
  ]
}

/** 手机号异步唯一校验（复用全局 checkPhone 接口） */
const validatePhoneAsync = async () => {
  if (!addForm.phone || !/^1[3-9]\d{9}$/.test(addForm.phone)) return
  try {
    const res = await request({ url: '/system/user/checkPhone', method: 'get', params: { phone: addForm.phone } })
    if (res.data === false) ElMessage.error('该手机号已被使用')
  } catch (e) { /* 静默 */ }
}

/** 打开一体化新增弹窗：并行加载全部可用租户 + 全部可用角色 */
async function openAddDialog() {
  addDialogVisible.value = true
  Object.assign(addForm, { userName: '', nickName: '', phone: '', password: '', status: 0, email: '' })
  addFormRef.value?.resetFields()
  selectedTenantIds.value = []
  roleGrouped.value = {}

  addLoading.value = true
  try {
    const [allRes, rolesRes] = await Promise.all([getAllTenant(), getAllAvailableRoles()])
    scopeOptions.value = (allRes.data || []).filter(t => t.status === 0).map(t => ({ ...t, roleId: null }))
    const grouped = {}
    for (const r of (rolesRes.data || [])) {
      if (!grouped[r.tenantId]) grouped[r.tenantId] = []
      grouped[r.tenantId].push(r)
    }
    roleGrouped.value = grouped
  } catch (e) {
    ElMessage.error('加载失败：' + (e.message || ''))
  } finally {
    addLoading.value = false
  }
}

/** 提交一体化新增：基础表单校验 + Scope 勾选校验 + API */
async function handleAddSubmit() {
  await addFormRef.value.validate()
  const unassigned = scopeOptions.value.filter(
    o => selectedTenantIds.value.includes(o.id) && !o.roleId
  )
  if (unassigned.length > 0) {
    ElMessage.error(`以下租户未选择角色：${unassigned.map(o => o.tenantName).join('、')}`)
    return
  }
  const payload = {
    userName: addForm.userName,
    nickName: addForm.nickName,
    phone: addForm.phone,
    password: addForm.password,
    status: addForm.status,
    email: addForm.email,
    assignments: scopeOptions.value
      .filter(o => selectedTenantIds.value.includes(o.id))
      .map(o => ({ tenantId: o.id, roleId: o.roleId }))
  }
  addSaving.value = true
  try {
    await addPrincipal(payload)
    ElMessage.success('平台主体创建成功')
    addDialogVisible.value = false
    fetchPrincipals()
  } catch (e) {
    ElMessage.error('创建失败：' + (e.message || ''))
  } finally {
    addSaving.value = false
  }
}

// ==================== 审计日志 ====================
const auditList = ref([])
const auditLoading = ref(false)
const auditForm = reactive({
  actionType: '',
  principalUserId: undefined,
  targetTenantId: undefined,
  timeRange: null
})
const auditPagination = reactive({ current: 1, size: 10, total: 0 })

async function fetchAuditLogs() {
  auditLoading.value = true
  try {
    const params = {
      pageNum: auditPagination.current,
      pageSize: auditPagination.size,
      actionType: auditForm.actionType || undefined,
      principalUserId: auditForm.principalUserId || undefined,
      targetTenantId: auditForm.targetTenantId || undefined
    }
    // 时间范围：后端用 LocalDateTime，传 startTime / endTime 两个参数
    if (auditForm.timeRange && auditForm.timeRange.length === 2) {
      params.startTime = auditForm.timeRange[0]
      params.endTime = auditForm.timeRange[1]
    }
    const res = await listAuditLogs(params)
    auditList.value = res.data?.records || []
    auditPagination.total = res.data?.total || 0
  } catch (e) {
    ElMessage.error('查询审计日志失败：' + (e.message || ''))
  } finally {
    auditLoading.value = false
  }
}

function resetAudit() {
  auditForm.actionType = ''
  auditForm.principalUserId = undefined
  auditForm.targetTenantId = undefined
  auditForm.timeRange = null
  auditPagination.current = 1
  fetchAuditLogs()
}

function auditLabel(t) {
  return { SWITCH_TENANT: '切换活跃租户', SCOPE_ASSIGN: 'Scope 分配', SCOPE_REVOKE: 'Scope 撤销' }[t] || t
}
function auditTagType(t) {
  return { SWITCH_TENANT: 'success', SCOPE_ASSIGN: '', SCOPE_REVOKE: 'danger' }[t] || 'info'
}

onMounted(() => {
  fetchPrincipals()
})
</script>

<style scoped>
.principal-scope-container {
  padding: 16px;
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
.search-form {
  margin-bottom: 16px;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.text-muted {
  color: #c0c4cc;
}
</style>