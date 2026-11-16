﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿<template>
  <div class="user-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary" @click="handleAdd" v-hasPermi="['system:user:add']">
            <el-icon><Plus /></el-icon> 新增用户
          </el-button>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="用户名">
          <el-input v-model="searchForm.userName" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item v-if="!isTenantLogin" label="所属租户">
          <el-select v-model="searchForm.tenantId" placeholder="全部租户" clearable style="width: 140px">
            <el-option label="平台用户" :value="0" />
            <el-option v-for="t in tenantOptions" :key="t.id" :label="t.tenantName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.roleId" placeholder="全部角色" clearable filterable style="width: 140px">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-tree-select v-model="searchForm.deptId" :data="deptTree"
                          :props="{ label: 'deptName', value: 'id', children: 'children' }"
                          check-strictly clearable filterable placeholder="全部部门" style="width: 160px" />
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="searchForm.postId" placeholder="全部岗位" clearable filterable style="width: 140px">
            <el-option v-for="p in postOptions" :key="p.postId" :label="p.postName" :value="p.postId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 110px">
            <el-option label="正常" :value="0" />
            <el-option label="禁用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="70" align="center" :index="(i) => (pagination.current - 1) * pagination.size + i + 1" />
        <el-table-column prop="phone" label="手机号" width="130" align="center" />
        <el-table-column prop="userName" label="用户名" min-width="120" align="center" />
        <el-table-column v-if="!isTenantLogin" prop="tenantName" label="所属租户" min-width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.tenantId && row.tenantId > 0 ? '' : 'info'" effect="plain">
              {{ row.tenantName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="roleNames" label="角色" min-width="180" align="center">
          <template #default="{ row }">
            <template v-if="row.roleNames && row.roleNames !== '未分配'">
              <el-tag v-for="n in row.roleNames.split(', ')" :key="n" size="small" style="margin-right: 4px;">
                {{ n }}
              </el-tag>
            </template>
            <el-tag v-else type="info" size="small">未分配</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" min-width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.deptName && row.deptName !== '未分配' && row.deptName !== '未知部门'"
                    size="small">
              {{ row.deptName }}
            </el-tag>
            <el-tag v-else type="info" size="small">{{ row.deptName || '未分配' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="postName" label="岗位" min-width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.postName && row.postName !== '未分配' && row.postName !== '未知岗位'"
                    size="small">
              {{ row.postName }}
            </el-tag>
            <el-tag v-else type="info" size="small">{{ row.postName || '未分配' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <el-button type="info" link @click="handleDetail(row)" v-if="!isTenantLogin">详情</el-button>
            <el-button type="primary" link @click="handleEdit(row)" v-hasPermi="['system:user:edit']">编辑</el-button>
            <el-button
              type="success" link @click="handleBizUnitScope(row)"
              v-hasPermi="['system:user:bizUnitAssign']"
              v-if="row.tenantId && row.tenantId > 0"
            >经营单元</el-button>
            <el-button type="warning" link @click="handleResetPwd(row)" v-hasPermi="['system:user:resetPassword']">重置密码</el-button>
            <el-button type="danger" link @click="handleDelete(row)" v-hasPermi="['system:user:delete']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetPwdVisible" title="重置密码" width="480px" :close-on-click-modal="false">
      <div style="margin-bottom: 16px;">
        <el-alert type="warning" :closable="false">
          确定重置用户 <b>{{ resetPwdTarget?.userName }}</b> 的密码？重置后密码将改为下方显示的初始密码，用户下次登录必须修改密码。
        </el-alert>
      </div>
      <div v-if="resetPwdNewPassword" style="background: #f5f7fa; border-radius: 6px; padding: 16px;">
        <div style="color: #909399; font-size: 12px; margin-bottom: 6px;">初始密码（请复制保存给用户）</div>
        <div style="display: flex; align-items: center; gap: 8px;">
          <el-input :model-value="resetPwdNewPassword" readonly>
            <template #append>
              <el-button :icon="CopyDocument" @click="copyPwd">复制</el-button>
            </template>
          </el-input>
        </div>
        <div style="color: #909399; font-size: 12px; margin-top: 8px;">⚠️ 密码只显示一次，关闭后无法再查看</div>
      </div>
      <div v-else style="color: #606266; font-size: 14px;">点击"确认重置"按钮生成新密码...</div>
      <template #footer>
        <el-button @click="resetPwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetPwdLoading" @click="confirmResetPwd">确认重置</el-button>
        <el-button type="success" @click="resetPwdVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ========== 用户详情弹窗（只读） ========== -->
    <el-dialog
      v-model="detailVisible"
      title="用户详情"
      width="960px"
      destroy-on-close
    >
      <!-- 区块 1：基础信息 -->
      <el-descriptions title="一、基础信息" :column="3" border size="small">
        <el-descriptions-item label="用户ID">{{ detailData.id }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ detailData.userName }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detailData.nickName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailData.phone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detailData.email || '—' }}</el-descriptions-item>
        <el-descriptions-item label="账号状态">
          <el-tag :type="detailData.status === 0 ? 'success' : 'danger'" size="small">
            {{ detailData.status === 0 ? '正常' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left" style="margin: 16px 0;">二、归属信息</el-divider>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="所属租户">{{ detailData.tenantName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="所属部门">{{ detailData.deptName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ detailData.postName || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left" style="margin: 16px 0;">三、权限信息</el-divider>
      <el-descriptions title="当前生效数据范围" :column="1" border size="small">
        <el-descriptions-item label="DataScope">
          <el-tag type="warning" effect="plain">{{ detailData.effectiveDataScope || '全部数据' }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <el-table :data="detailData.roles || []" border stripe size="small" empty-text="暂无分配角色" style="margin-top: 8px;">
        <el-table-column label="角色归属" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="roleScopeTagType(row.roleScope)" size="small" effect="plain">
              {{ roleScopeText(row.roleScope) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleKey" label="权限字符" width="120" />
        <el-table-column label="数据范围" width="120" align="center">
          <template #default="{ row }">
            {{ dataScopeText(row.dataScope) }}
          </template>
        </el-table-column>
        <el-table-column v-if="detailData.roles?.some(r => r.roleScope === 'TENANT')" prop="tenantName" label="所属租户" width="120" />
      </el-table>

      <el-divider content-position="left" style="margin: 16px 0;">四、运维信息</el-divider>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="创建时间">{{ detailData.createTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="最后登录时间">{{ detailData.loginTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="最后登录IP">{{ detailData.loginIp || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailData.createBy || '—' }}</el-descriptions-item>
        <el-descriptions-item label="密码修改时间">未记录</el-descriptions-item>
        <el-descriptions-item label="permVer（权限版本）">
          <span v-if="detailData.permVer != null">{{ detailData.permVer }}</span>
          <span v-else class="text-muted">未初始化</span>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 五、预留扩展区域（后续挂工号/职级等自定义字段） -->
      <el-divider content-position="left" style="margin: 16px 0;">五、预留扩展</el-divider>
      <el-empty description="后续可挂载工号、职级等自定义扩展字段" :image-size="60" />

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ========== 经营单元绑定抽屉 ========== -->
    <el-drawer
      v-model="bizUnitDrawerVisible"
      :title="bizUnitDrawerTitle"
      size="520px"
      destroy-on-close
    >
      <!-- 抽屉主体 -->
      <div v-if="bizUnitScopeLoading" style="text-align:center; padding:40px;">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
        <div style="color:#909399; margin-top:8px;">加载中...</div>
      </div>

      <div v-else>
        <!-- 已绑定列表 -->
        <div style="margin-bottom:16px;">
          <div style="font-weight:600; margin-bottom:8px;">已绑定经营单元</div>
          <el-table :data="scopeBindings" border size="small" empty-text="暂无绑定">
            <el-table-column label="经营单元" prop="bizUnitName" min-width="140" />
            <el-table-column label="角色" min-width="180">
              <template #default="{ row }">
                <el-select
                  :model-value="row.roleId"
                  @update:model-value="(val) => handleScopeRoleChange(row, val)"
                  placeholder="请选择角色"
                  clearable
                  style="width:100%"
                >
                  <el-option
                    v-for="r in availableRoleOptions"
                    :key="r.id"
                    :label="r.roleName"
                    :value="r.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="handleRemoveScope(row)">解除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 新增绑定 -->
        <el-divider content-position="left" style="margin:16px 0;">新增绑定</el-divider>
        <div style="display:flex; gap:8px; align-items:center;">
          <el-tree-select
            ref="bizUnitTreeSelectRef"
            v-model="newBindRow.bizUnitId"
            :data="bizUnitTree"
            :props="{ label: 'deptName', value: 'id', children: 'children', disabled: (d) => !d.isBizUnit }"
            filterable check-strictly clearable
            placeholder="请选择经营单元（标 ✦ 的节点）"
            style="flex:1"
          />
          <el-select
            v-model="newBindRow.roleId"
            placeholder="请选择角色"
            clearable filterable
            style="width:180px"
          >
            <el-option
              v-for="r in availableRoleOptions"
              :key="r.id"
              :label="r.roleName"
              :value="r.id"
            />
          </el-select>
          <el-button type="primary" @click="handleAddScopeRow" :disabled="!newBindRow.bizUnitId">添加</el-button>
        </div>
        <div style="color:#909399; font-size:12px; margin-top:6px;">
          提示：只有 <b>isBizUnit=1</b> 的部门节点可选。一个用户 × 一个经营单元 只能绑一个角色。
        </div>
      </div>

      <!-- 抽屉底部 -->
      <template #footer>
        <el-button @click="bizUnitDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="bizUnitScopeSaving" @click="saveBizUnitScopes">保存</el-button>
      </template>
    </el-drawer>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="用户名" prop="userName">
          <el-input v-model="formData.userName" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickName">
          <el-input v-model="formData.nickName" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="formData.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <!-- 所属租户：租户登录/编辑场景一律置灰；下拉数据源已由后端按授权范围过滤 -->
        <el-form-item label="所属租户" prop="tenantId">
          <el-select
            v-model="formData.tenantId"
            :disabled="isTenantLogin || isEdit"
            placeholder="请选择租户"
            style="width: 100%"
          >
            <el-option
              v-for="item in tenantOptions"
              :key="item.id"
              :label="item.tenantName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="部门" prop="deptId">
          <el-tree-select v-model="formData.deptId" :props="{ label: 'deptName', value: 'id', children: 'children' }"
                          :data="deptTree" check-strictly clearable filterable
                          placeholder="请选择部门" style="width: 100%" />
        </el-form-item>
        <el-form-item label="岗位" prop="postId">
          <el-select v-model="formData.postId" clearable filterable placeholder="请选择岗位" style="width: 100%">
            <el-option v-for="p in postOptions" :key="p.postId" :label="p.postName" :value="p.postId" />
          </el-select>
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formData.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="0">正常</el-radio>
            <el-radio :label="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分配角色">
  <el-select v-model="formData.roleIds" multiple placeholder="请选择角色">
    <el-option v-for="item in roleOptions" :key="item.id" :label="item.roleName" :value="item.id"/>
  </el-select>
</el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'User' })
import { ref, reactive, computed ,onMounted} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, addUser, updateUser, deleteUser, getAllRole, getUserSelectedRoleIds, resetUserPwd, checkUserPhone, getUserDetail } from '@/api/user'
import { CopyDocument, Loading } from '@element-plus/icons-vue'
import { getAllTenant, getAuthorizedTenants } from '@/api/tenant'
import { getAllDept } from '@/api/dept'
import { getAllPost } from '@/api/post'
import { getBizUnitTree, getUserBizUnitScopes, assignBizUnitScope } from '@/api/bizUnitScope'
import storage from '@/utils/storage'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

// 是否租户登录
const isTenantLogin = computed(() => storage.getLoginType() === 'tenant')
// 当前登录用户真实 tenantId（从 userStore.getTenantId getter 取，底层读 userInfo.sysUser.tenantId）
const currentTenantId = computed(() => userStore.getTenantId)

const searchForm = reactive({
  userName: '',
  tenantId: null,  // null=全部，0=平台用户，>0=指定租户
  roleId: null,
  deptId: null,
  postId: null,
  status: null
})

const tableData = ref([])

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.id ? '编辑用户' : '新增用户')
const isEdit = ref(false)

// ⭐ 部门树形下拉（扁平列表 → 前端 buildTree）
const deptTree = ref([])
const buildTree = (flatList) => {
  const map = {}
  flatList.forEach(n => { map[n.id] = { ...n, children: [] } })
  const roots = []
  flatList.forEach(n => {
    const node = map[n.id]
    if (n.parentId === 0 || !map[n.parentId]) roots.push(node)
    else map[n.parentId].children.push(node)
  })
  return roots
}
const fetchDeptTree = async () => {
  try {
    const res = await getAllDept()
    deptTree.value = buildTree(res.data || [])
  } catch (e) { deptTree.value = [] }
}
const fetchPostOptions = async () => {
  try {
    const res = await getAllPost()
    postOptions.value = res.data || []
  } catch (e) { postOptions.value = [] }
}

const formRef = ref(null)
const formData = reactive({
  id: null,
  tenantId: null,
  userName: '',
  password: '',
  nickName: '',
  deptId: 0,
  postId: null,
  phone: '',
  email: '',
  sex: 0,
  status: 0,
  remark: '',
  roleIds: []
})

//定义角色下拉数据源
const roleOptions = ref([])
// 岗位下拉数据源
const postOptions = ref([])
// 租户下拉数据源
const tenantOptions = ref([])

// 自定义 validator：校验手机号在当前租户唯一性
const validatePhoneUnique = async (rule, value, callback) => {
  if (!value) return callback()            // 必填规则会拦截空值
  if (!/^1[3-9]\d{9}$/.test(value)) return callback()  // 正则规则会拦截格式

  // ⚠️ 平台侧新增 + 还没选租户 → 跳过唯一性校验
  // （后端 checkPhone 接口没 tenantId 也会返回 true 兜底，但前端直接跳过更省请求、也避免 axios 拦截器的全局错误提示）
  if (!isTenantLogin.value && !isEdit.value && formData.tenantId == null) {
    return callback()
  }

  try {
    // 平台侧传 tenantId（前端可选租户）；租户侧不传（后端从 token 取 activeTenantId）
    const params = {}
    if (!isTenantLogin.value && formData.tenantId != null) {
      params.tenantId = formData.tenantId
    }
    // 编辑场景排除自己
    if (isEdit.value && formData.id) {
      params.excludeId = formData.id
    }
    const res = await checkUserPhone(value, params)
    if (res.data) {
      callback()  // 唯一，通过
    } else {
      callback(new Error('该手机号已在当前租户使用'))
    }
  } catch (e) {
    // 校验接口异常不阻塞提交（后端 Controller 还有兜底校验）
    callback()
  }
}

// 校验规则：编辑场景不校验密码（密码重置是独立功能）；所属租户在新增场景必填
const formRules = computed(() => ({
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确手机号', trigger: 'blur' },
    { validator: validatePhoneUnique, trigger: 'blur' }
  ],
  userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  tenantId: [{ required: true, message: '请选择所属租户', trigger: 'change' }],
  ...(!isEdit.value ? { password: [{ required: true, message: '请输入密码', trigger: 'blur' }] } : {})
}))

onMounted(async()=>{
  // 部门树形下拉（公司+部门）
  fetchDeptTree()
  // 岗位下拉
  fetchPostOptions()

  // 角色下拉
  const res = await getAllRole()
  roleOptions.value = res.data

  // 租户下拉 —— 平台侧加载「可授权租户」（后端按身份过滤，不含平台主体项）；
  // 租户侧虽然也加载了但 disabled，tenantOptions 实际不影响展示
  if (!isTenantLogin.value) {
    try {
      const tenantRes = await getAuthorizedTenants()
      tenantOptions.value = tenantRes.data || []
    } catch (e) {
      console.error('加载可授权租户列表失败', e)
    }
  }
})

const fetchData = async () => {
  try {
    const params = {
      pageNum: pagination.current,
      pageSize: pagination.size
    }
    if (searchForm.userName) params.userName = searchForm.userName
    if (searchForm.tenantId != null) params.tenantId = searchForm.tenantId
    if (searchForm.roleId != null) params.roleId = searchForm.roleId
    if (searchForm.deptId != null) params.deptId = searchForm.deptId
    if (searchForm.postId != null) params.postId = searchForm.postId
    if (searchForm.status != null) params.status = searchForm.status
    const res = await getUserList(params)
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) {
    console.error('获取用户列表失败', error)
  }
}

const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, {
    id: null,
    // 租户登录 → 锁定当前租户（从 userStore.getTenantId 取真实值，不再用硬编码 || 6）；平台侧 → 置空，必填校验拦截
    tenantId: isTenantLogin.value ? (currentTenantId.value ?? null) : null,
    userName: '',
    password: '',
    nickName: '',
    deptId: 0,
    postId: null,
    phone: '',
    email: '',
    sex: 0,
    status: 0,
    remark: '',
    roleIds:[]
  })
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  isEdit.value = true
  Object.assign(formData, row)
  formData.password = ''
  //回显已绑定角色
  const res = await getUserSelectedRoleIds(row.id)
  formData.roleIds = res.data
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该用户吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchData()
  }).catch(() => {})
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      if (isEdit.value) {
        await updateUser(formData)
        ElMessage.success('更新成功')
      } else {
        await addUser(formData)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      console.error('保存失败', error)
    }
  })
}

// ===== 重置密码弹窗 =====
const resetPwdVisible = ref(false)
const resetPwdLoading = ref(false)
const resetPwdTarget = ref(null)
const resetPwdNewPassword = ref('')

const handleResetPwd = (row) => {
  resetPwdTarget.value = row
  resetPwdNewPassword.value = ''
  resetPwdVisible.value = true
}

const confirmResetPwd = async () => {
  if (!resetPwdTarget.value) return
  resetPwdLoading.value = true
  try {
    const res = await resetUserPwd(resetPwdTarget.value.id)
    resetPwdNewPassword.value = res.data.password
    ElMessage.success('密码重置成功，请复制新密码')
  } catch (e) {
    console.error(e)
  } finally {
    resetPwdLoading.value = false
  }
}

const copyPwd = async () => {
  if (!resetPwdNewPassword.value) return
  const text = resetPwdNewPassword.value
  try {
    // 优先 Clipboard API（HTTPS / localhost 才支持）
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      ElMessage.success('已复制到剪贴板')
      return
    }
    // fallback：document.execCommand('copy') 兼容 http + IP 环境
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    ta.setSelectionRange(0, text.length)
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    if (ok) {
      ElMessage.success('已复制到剪贴板')
    } else {
      ElMessage.warning('复制失败，请手动选择文本复制')
    }
  } catch {
    ElMessage.warning('复制失败，请手动选择文本复制')
  }
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

// 分页回调：显式先赋值再请求，@current-change 时序早于 v-model update
const handlePageChange = (page) => {
  pagination.current = page
  fetchData()
}
const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  fetchData()
}

const handleReset = () => {
  searchForm.userName = ''
  searchForm.tenantId = null
  searchForm.roleId = null
  searchForm.deptId = null
  searchForm.postId = null
  searchForm.status = null
  pagination.current = 1
  fetchData()
}

// ===== 详情弹窗 =====
const detailVisible = ref(false)
const detailData = reactive({
  id: null,
  userName: '',
  nickName: '',
  phone: '',
  email: '',
  status: null,
  tenantId: null,
  tenantName: '',
  deptId: null,
  deptName: '',
  postId: null,
  postName: '',
  effectiveDataScope: '',
  roles: [],
  createTime: '',
  loginTime: '',
  loginIp: '',
  pwdUpdateTime: null,
  permVer: null,
  createBy: '',
  extJson: ''
})

const handleDetail = async (row) => {
  try {
    const res = await getUserDetail(row.id)
    Object.assign(detailData, res.data || {})
    detailVisible.value = true
  } catch (e) {
    console.error('加载用户详情失败', e)
  }
}

// ===== 详情辅助函数 =====
const roleScopeText = (scope) => ({ PLATFORM: '平台角色', TENANT: '租户角色', DEPT: '部门私有' }[scope] || '未知')
const roleScopeTagType = (scope) => ({ PLATFORM: '', TENANT: 'warning', DEPT: 'success' }[scope] || 'info')
const dataScopeText = (scope) => ({ 1: '全部数据', 2: '本部门', 3: '本部门及下属', 4: '仅本人', 5: '自定义' }[scope] || '未知')

// ════════════════════════════════════════════════════════════════
// 经营单元绑定抽屉逻辑
// ════════════════════════════════════════════════════════════════

const bizUnitDrawerVisible = ref(false)
const bizUnitScopeLoading = ref(false)
const bizUnitScopeSaving = ref(false)
const scopeTargetUser = ref(null)       // 当前抽屉操作的目标用户
const scopeBindings = ref([])           // 已绑定列表：[{ bizUnitId, bizUnitName, roleId, roleName }]
const bizUnitTree = ref([])             // 经营单元树（含所有部门，disabled 非 isBizUnit 节点）

// 新增绑定行（临时，点「添加」后合入 scopeBindings）
const newBindRow = reactive({ bizUnitId: null, bizUnitName: '', roleId: null })

// 抽屉标题
const bizUnitDrawerTitle = computed(() => {
  const u = scopeTargetUser.value
  return u ? `绑定经营单元 —— ${u.userName || u.nickName || ''}` : '绑定经营单元'
})

// 可分配角色（过滤掉 admin 超管角色，后端也会校验）
const availableRoleOptions = computed(() => {
  return (roleOptions.value || []).filter(r => r.roleKey !== 'admin')
})

/** 打开抽屉：拉树 + 拉已绑定列表 */
const handleBizUnitScope = async (row) => {
  scopeTargetUser.value = row
  bizUnitDrawerVisible.value = true
  bizUnitScopeLoading.value = true
  // 重置新增行
  newBindRow.bizUnitId = null
  newBindRow.bizUnitName = ''
  newBindRow.roleId = null

  try {
    // 并行拉经营单元树 + 已绑定列表
    // ⭐ 必须传 row.tenantId：admin(tenantId=0) 跨租户管理时后端需要按目标租户查树
    const [treeRes, scopesRes] = await Promise.all([
      getBizUnitTree({ tenantId: row.tenantId }).catch(() => ({ data: [] })),
      getUserBizUnitScopes(row.id).catch(() => ({ data: [] }))
    ])
    bizUnitTree.value = treeRes.data || []

    // 拉到的 scopes 格式是 [{ bizUnitId, bizUnitName, roles: [{roleId, roleName}] }]
    // 扁平化成每行一个 bizUnit + 一个 role（后端一个 bizUnit 只绑一个 role）
    const scopes = scopesRes.data || []
    scopeBindings.value = scopes.map(s => ({
      bizUnitId: s.bizUnitId,
      bizUnitName: s.bizUnitName,
      roleId: (s.roles && s.roles.length > 0) ? s.roles[0].roleId : null
    }))
  } catch (e) {
    ElMessage.error('加载数据失败')
  } finally {
    bizUnitScopeLoading.value = false
  }
}

/** 新增绑定行：把 newBindRow 合入 scopeBindings */
const handleAddScopeRow = () => {
  if (!newBindRow.bizUnitId) {
    ElMessage.warning('请先选择经营单元')
    return
  }
  // 检查是否已在列表中（同一 bizUnitId 不允许重复）
  if (scopeBindings.value.some(b => b.bizUnitId === newBindRow.bizUnitId)) {
    ElMessage.warning('该经营单元已在列表中')
    return
  }
  // 从树里取名称
  const findName = (nodes, id) => {
    for (const n of nodes) {
      if (n.id === id) return n.deptName
      if (n.children?.length) {
        const found = findName(n.children, id)
        if (found) return found
      }
    }
    return ''
  }
  const bizUnitName = findName(bizUnitTree.value, newBindRow.bizUnitId)

  scopeBindings.value.push({
    bizUnitId: newBindRow.bizUnitId,
    bizUnitName,
    roleId: newBindRow.roleId
  })
  // 重置
  newBindRow.bizUnitId = null
  newBindRow.bizUnitName = ''
  newBindRow.roleId = null
}

/** 角色下拉变更（直接改绑定项的 roleId） */
const handleScopeRoleChange = (row, newRoleId) => {
  row.roleId = newRoleId
}

/** 解除某条绑定（从列表里移除） */
const handleRemoveScope = (row) => {
  scopeBindings.value = scopeBindings.value.filter(b => b.bizUnitId !== row.bizUnitId)
}

/** 保存：逐个调 assignBizUnitScope（后端是单条先删后插语义） */
const saveBizUnitScopes = async () => {
  if (!scopeTargetUser.value) return
  bizUnitScopeSaving.value = true
  try {
    // 后端接口已支持 userId 参数给他人绑
    const targetUserId = scopeTargetUser.value.id
    // 并行调用（全部成功才算成功，任一失败 catch 单条跳过）
    await Promise.all(
      scopeBindings.value.map(b => {
        return assignBizUnitScope({
          userId: targetUserId,
          bizUnitId: b.bizUnitId,
          roleId: b.roleId   // null 表示解除
        })
      })
    )
    ElMessage.success('经营单元绑定已保存')
    bizUnitDrawerVisible.value = false
  } catch (e) {
    ElMessage.error('保存失败，请检查经营单元/角色是否属于当前租户')
  } finally {
    bizUnitScopeSaving.value = false
  }
}

fetchData()
</script>

<style scoped>
.user-container {
  width: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.text-muted {
  color: #909399;
}
</style>