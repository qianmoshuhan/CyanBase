<template>
  <div class="tenant-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>租户管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增租户
          </el-button>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="租户名称">
          <el-input v-model="searchForm.tenantName" placeholder="请输入租户名称" clearable />
        </el-form-item>
        <el-form-item label="租户编码">
          <el-input v-model="searchForm.tenantCode" placeholder="请输入租户编码" clearable />
        </el-form-item>
        <el-form-item label="状态">
            <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 120px">
              <el-option label="正常"   :value="0" />
              <el-option label="冻结"   :value="1" />
              <el-option label="已过期" :value="2" />
            </el-select>
          </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="tenantName" label="租户名称" min-width="140" />
        <el-table-column prop="tenantCode" label="租户编码" min-width="120" />
        <el-table-column prop="contactUser" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expireDate" label="到期时间" width="180">
          <template #default="{ row }">
            <span v-if="row.expireDate">{{ row.expireDate }}</span>
            <span v-else class="text-muted">永不过期</span>
          </template>
        </el-table-column>
        <el-table-column prop="maxUserNum" label="用户配额" width="100">
          <template #default="{ row }">
            <span v-if="row.maxUserNum">{{ row.maxUserNum }}</span>
            <span v-else class="text-muted">不限</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
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
            @size-change="fetchData"
            @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
        width="850px"
        @close="handleDialogClose"
    >
      <el-tabs v-model="activeTab">
        <!-- ========== Tab1: 基础信息 ========== -->
        <el-tab-pane label="基础信息" name="basic">
          <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
            <el-form-item label="租户名称" prop="tenantName">
              <el-input v-model="formData.tenantName" placeholder="请输入租户名称" />
            </el-form-item>
            <el-form-item label="租户编码" prop="tenantCode">
              <el-input v-model="formData.tenantCode" placeholder="请输入租户编码（唯一标识）" />
            </el-form-item>
            <el-form-item label="联系人" prop="contactUser">
              <el-input v-model="formData.contactUser" placeholder="请输入联系人" />
            </el-form-item>
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" />
            </el-form-item>
            <el-form-item label="地址" prop="address">
              <el-input v-model="formData.address" type="textarea" :rows="2" placeholder="请输入地址" />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="formData.status">
                <el-radio :label="0">正常</el-radio>
                <el-radio :label="1">冻结</el-radio>
              </el-radio-group>
              <div class="form-tip">已过期(status=2)由定时任务自动维护，不可手动选择</div>
            </el-form-item>
            <el-form-item label="到期时间" prop="expireDate">
              <el-date-picker
                v-model="formData.expireDate"
                type="datetime"
                placeholder="留空 = 永不过期"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
                clearable
                style="width:100%"
              />
            </el-form-item>
            <el-form-item label="用户配额" prop="maxUserNum">
              <el-input-number
                v-model="formData.maxUserNum"
                :min="1"
                :controls="true"
                placeholder="留空 = 不限制"
                style="width:100%"
              />
              <div class="form-tip">租户用户数达到配额后将无法新增</div>
            </el-form-item>
            <el-form-item label="备注" prop="remark">
              <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ========== Tab2: 菜单权限 ========== -->
        <el-tab-pane label="菜单权限" name="menu">
          <div class="menu-permission-wrapper">
            <div class="menu-toolbar">
              <el-button size="small" @click="handleCheckAll">全选</el-button>
              <el-button size="small" @click="handleUncheckAll">清空</el-button>
              <el-button size="small" @click="handleExpandAll">展开</el-button>
              <el-button size="small" @click="handleCollapseAll">折叠</el-button>
              <span class="menu-tip">已选 {{ checkedMenuIds.length }} 项</span>
            </div>
            <el-tree
              ref="menuTreeRef"
              :data="menuTreeData"
              show-checkbox
              node-key="id"
              :props="treeProps"
              :default-expand-all="true"
              :check-strictly="false"
              empty-text="暂无菜单数据"
              @check="handleCheckChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTenantList, addTenant, updateTenant, deleteTenant, getAllMenuTree, getTenantMenuIds, saveTenantMenus } from '@/api/tenant'

// ===== 菜单权限相关 =====
const activeTab = ref('basic')
const menuTreeData = ref([])
const checkedMenuIds = ref([])
const menuTreeRef = ref(null)
// 标记菜单树是否已经完成 setCheckedKeys（避免 watch 重复调用）
const menuCheckedApplied = ref(false)
const treeProps = {
  label: 'menuName',
  children: 'children'
}

const searchForm = reactive({
  tenantName: '',
  tenantCode: '',
  status: null
})

const tableData = ref([])

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.id ? '编辑租户' : '新增租户')
const isEdit = ref(false)

const formRef = ref(null)
const formData = reactive({
  id: null,
  tenantName: '',
  tenantCode: '',
  contactUser: '',
  contactPhone: '',
  address: '',
  status: 0,
  expireDate: null,
  maxUserNum: null,
  remark: ''
})

// ===== status 辅助映射 =====
const statusText = (s) => ({ 0: '正常', 1: '冻结', 2: '已过期' }[s] ?? '未知')
const statusTagType = (s) => ({ 0: 'success', 1: 'warning', 2: 'info' }[s] ?? 'danger')

const formRules = {
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  tenantCode: [{ required: true, message: '请输入租户编码', trigger: 'blur' }]
}

const fetchData = async () => {
  try {
    const res = await getTenantList({
      pageNum: pagination.current,
      pageSize: pagination.size,
      tenantName: searchForm.tenantName || undefined,
      tenantCode: searchForm.tenantCode || undefined,
      status: searchForm.status
    })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) {
    console.error('获取租户列表失败', error)
  }
}

const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, {
    id: null,
    tenantName: null,
    tenantCode: '',
    contactUser: '',
    contactPhone: '',
    address: '',
    status: 0,
    expireDate: null,
    maxUserNum: null,
    remark: ''
  })
  activeTab.value = 'basic'
  checkedMenuIds.value = []
  menuCheckedApplied.value = false
  dialogVisible.value = true
  // 加载所有菜单树
  loadMenuTree()
}

// 加载菜单树 + 回填已分配菜单
const loadMenuTree = async () => {
  try {
    const res = await getAllMenuTree()
    menuTreeData.value = res.data || []
    menuCheckedApplied.value = false
    // 编辑时回填已选菜单
    if (isEdit.value && formData.id) {
      const idsRes = await getTenantMenuIds(formData.id)
      checkedMenuIds.value = idsRes.data || []
    } else {
      checkedMenuIds.value = []
    }
    // 注意：el-tree 在第二个 tab 里，此时 tab='basic'，el-tree 还没渲染
    // setCheckedKeys 放到 watch activeTab 里执行
  } catch (e) {
    console.error('加载菜单树失败', e)
  }
}

// 监听 tab 切换：切到 menu tab 时 el-tree 才渲染好，再 setCheckedKeys
watch(activeTab, async (newVal) => {
  if (newVal !== 'menu') return
  await nextTick()
  if (menuTreeRef.value && !menuCheckedApplied.value) {
    menuTreeRef.value.setCheckedKeys(checkedMenuIds.value, false)
    menuCheckedApplied.value = true
  }
})

// 树选中变化，同步到 checkedMenuIds（取所有完全选中的节点，包括父节点）
const handleCheckChange = () => {
  if (menuTreeRef.value) {
    checkedMenuIds.value = menuTreeRef.value.getCheckedKeys(false)
  }
}

// 递归收集所有节点 ID
const collectAllIds = (nodes = []) => {
  const ids = []
  const walk = (list) => {
    list.forEach(item => {
      ids.push(item.id)
      if (item.children && item.children.length) walk(item.children)
    })
  }
  walk(nodes)
  return ids
}

const handleCheckAll = () => {
  if (menuTreeRef.value) {
    // 传 false 让所有节点（包括父节点）都被设置为选中，这样父子关联后所有叶子也会被完全选中
    menuTreeRef.value.setCheckedKeys(collectAllIds(menuTreeData.value), false)
  }
}
const handleUncheckAll = () => {
  if (menuTreeRef.value) {
    menuTreeRef.value.setCheckedKeys([])
  }
}
const handleExpandAll = () => {
  const nodes = menuTreeRef.value?.store._getAllNodes() || []
  nodes.forEach(n => (n.expanded = true))
}
const handleCollapseAll = () => {
  const nodes = menuTreeRef.value?.store._getAllNodes() || []
  nodes.forEach(n => (n.expanded = false))
}

const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(formData, row)
  activeTab.value = 'basic'
  dialogVisible.value = true
  loadMenuTree()
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除租户"${row.tenantName}"吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteTenant(row.id)
    ElMessage.success('删除成功')
    fetchData()
  }).catch(() => {})
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      // 防御：空值统一转 null，避免后端接收到 '' 或 undefined
      const payload = { ...formData }
      if (!payload.expireDate) payload.expireDate = null
      if (!payload.maxUserNum) payload.maxUserNum = null

      if (isEdit.value) {
        await updateTenant(payload)
        // 保存菜单权限
        await saveTenantMenus(formData.id, checkedMenuIds.value)
        ElMessage.success('更新成功')
      } else {
        const res = await addTenant(payload)
        // 新增成功后保存菜单权限（后端 add 接口返回带自增ID的 SysTenant 对象）
        const newId = res?.data?.id
        if (newId) {
          await saveTenantMenus(newId, checkedMenuIds.value)
        }
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      console.error('保存失败', error)
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  activeTab.value = 'basic'
  checkedMenuIds.value = []
  menuTreeData.value = []
  menuCheckedApplied.value = false
}

const handleReset = () => {
  searchForm.tenantName = ''
  searchForm.tenantCode = ''
  searchForm.status = null
  pagination.current = 1
  fetchData()
}

fetchData()
</script>

<style scoped>
.tenant-container {
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

.menu-permission-wrapper {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  max-height: 420px;
  overflow-y: auto;
}

.menu-toolbar {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.menu-tip {
  margin-left: auto;
  color: #909399;
  font-size: 13px;
}

.text-muted {
  color: #909399;
}

.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}
</style>