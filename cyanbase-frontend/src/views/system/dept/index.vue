﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿<template>
  <div class="dept-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>部门管理</span>
          <div>
            <el-button-group>
              <el-button @click="expandAll">全部展开</el-button>
              <el-button @click="collapseAll">全部收起</el-button>
            </el-button-group>
            <el-button type="primary" @click="handleAdd" style="margin-left: 12px;" v-hasPermi="['system:dept:add']">
              <el-icon><Plus /></el-icon> 新增部门
            </el-button>
            <el-button @click="handleExport" style="margin-left: 8px;" v-hasPermi="['system:dept:list']" :loading="exportLoading">
              <el-icon><Download /></el-icon> 导出
            </el-button>
            <el-button @click="handleImportClick" style="margin-left: 8px;" v-hasPermi="['system:dept:list']">
              <el-icon><Upload /></el-icon> 导入
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索表单（样式对齐 tenant/index.vue） -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="部门名称">
          <el-input v-model="searchForm.deptName" placeholder="请输入部门名称" clearable @keyup.enter="fetchData" />
        </el-form-item>
        <!-- ⭐ 租户下拉仅平台 admin 可见（租户侧 CustomTenantHandler 自动拦截，传了也不认） -->
        <el-form-item v-if="isPlatform" label="所属租户">
          <el-select v-model="searchForm.tenantId" placeholder="全部租户" clearable style="width: 180px">
            <el-option v-for="t in tenantList" :key="t.id" :label="t.tenantName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="禁用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="tableRef"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :default-expand-all="true"
        border
        stripe
      >
        <el-table-column prop="deptName" label="部门名称" min-width="200">
          <template #default="{ row }">
            <el-icon style="color: #1677ff;"><Folder /></el-icon>
            <span style="margin-left: 6px;">{{ row.deptName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortNum" label="排序" width="80" />
        <!-- ⭐ 所属租户列：仅平台 admin 可见（租户侧 CustomTenantHandler 自动拦截，无需展示）
             样式对齐 user/index.vue：el-tag + effect="plain" -->
        <el-table-column v-if="isPlatform" label="所属租户" min-width="120">
          <template #default="{ row }">
            <el-tag v-if="row.tenantId == null || row.tenantId === 0" type="info" effect="plain">平台</el-tag>
            <el-tag v-else-if="tenantMap[row.tenantId]" effect="plain">{{ tenantMap[row.tenantId] }}</el-tag>
            <el-tag v-else type="warning" effect="plain">未分配</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- ⭐ 经营单元标记列：type=warning + effect=plain + size=small 对齐用户管理页角色/部门 tag 样式 -->
        <el-table-column label="经营单元" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isBizUnit === 1" type="warning" size="small" effect="plain">经营单元</el-tag>
            <span v-else style="color: #c0c4cc; font-size: 12px;">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleAddChild(row)" v-hasPermi="['system:dept:add']">新增子部门</el-button>
            <el-button type="primary" link @click="handleEdit(row)" v-hasPermi="['system:dept:edit']">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)" v-hasPermi="['system:dept:delete']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="父部门" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            :data="parentTree"
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
            placeholder="请选择父部门（0 表示顶级）"
            check-strictly
            clearable
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="formData.deptName" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="排序" prop="sortNum">
          <el-input-number v-model="formData.sortNum" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="0">正常</el-radio>
            <el-radio :label="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <!-- ⭐ 是否独立法人经营单元：对应 sys_dept.is_biz_unit -->
        <el-form-item label="经营单元" prop="isBizUnit">
          <el-checkbox v-model="formData.isBizUnit" :value="1" :true-value="1" :false-value="0">
            是否为独立法人经营单元
          </el-checkbox>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入部门数据弹窗：模板下载 + 文件选择 + 确认导入 -->
    <el-dialog v-model="importDialogVisible" title="导入部门数据" width="680px" :close-on-click-modal="false">
      <!-- 顶部：下载模板按钮 -->
      <div style="margin-bottom: 16px;">
        <el-button type="primary" @click="handleTemplateDownload" :loading="templateLoading">
          <el-icon><Download /></el-icon> 下载导入模板
        </el-button>
        <span style="margin-left: 12px; color: #909399; font-size: 13px;">先下载模板，按格式填写后上传</span>
      </div>

      <!-- 说明文字 -->
      <el-alert type="warning" :closable="false" style="margin-bottom: 20px;">
        <template #title>
          <span style="font-weight: 600;">⚠️ 重要说明</span>
        </template>
        <div style="line-height: 2; font-size: 13px; color: #606266;">
          <div>1. 本导入后续仅支持新增部门，修改已有部门请在页面编辑</div>
          <div>2. <b>部门编码列禁止填写任何内容</b>，由系统自动生成</div>
          <div>3. 状态仅允许填写：<b>正常、停用</b>；是否经营单元仅允许填写：<b>是、否</b></div>
          <div>4. 父子层级填写格式示例：集团 / 事业部 / 部门</div>
          <div>5. 最大支持导入 <b>5000 行</b>；仅支持 <b>.xlsx</b> 文件，不支持 xls 旧格式</div>
          <div v-if="!isPlatform">6. 租户模式下表格内【所属租户】列会被系统忽略</div>
        </div>
      </el-alert>

      <!-- 文件上传组件：auto-upload=false 手动提交，Element Plus 自己维护文件列表显示 -->
      <div>
        <el-upload
          ref="deptUploadRef"
          :auto-upload="false"
          :limit="1"
          accept=".xlsx"
          :show-file-list="true"
          :on-change="handleFileSelect"
          drag
        >
          <el-icon style="font-size: 28px; color: #c0c4cc;"><Upload /></el-icon>
          <div style="color: #909399; font-size: 13px; margin-top: 4px;">点击或拖拽 .xlsx 文件到此处</div>
        </el-upload>
      </div>

      <!-- 底部：关闭 + 确认导入 -->
      <template #footer>
        <el-button @click="handleImportDialogClose">取消</el-button>
        <el-button
          type="primary"
          :loading="importLoading"
          :disabled="!selectedFile"
          @click="handleConfirmImport"
        >
          确认导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'Dept' })
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Folder, Download, Upload } from '@element-plus/icons-vue'
import { getAllDept, addDept, updateDept, deleteDept, exportDept, importTemplate, importDept } from '@/api/dept'
import { getAllTenant } from '@/api/tenant'
import storage from '@/utils/storage'

// ===== 搜索表单 =====
const searchForm = reactive({
  deptName: '',
  tenantId: null,
  status: null
})

// 是否平台 admin（决定是否显示租户下拉）
const isPlatform = computed(() => storage.getLoginType() !== 'tenant')

// 租户下拉数据源
const tenantList = ref([])

// ⭐ tenantId → tenantName 字典（加速表格映射）
const tenantMap = computed(() => {
  const map = {}
  tenantList.value.forEach(t => { map[t.id] = t.tenantName })
  return map
})

const treeData = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const tableRef = ref(null)
const editingRow = ref(null) // 当前编辑/新增子部门的父行
const exportLoading = ref(false) // 导出 loading 态
const importDialogVisible = ref(false) // 导入弹窗可见性
const templateLoading = ref(false) // 下载模板 loading 态
const importLoading = ref(false) // 导入 loading 态（确认导入按钮用）
const selectedFile = ref(null) // 当前选中的原生 File 对象
const deptUploadRef = ref(null) // el-upload 组件 ref（用于 clearFiles 清空）

const defaultForm = () => ({
  id: null,
  parentId: 0,
  deptName: '',
  sortNum: 0,
  status: 0,
  isBizUnit: 0,
  remark: ''
})
const formData = reactive(defaultForm())

const formRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  parentId: [{ required: true, message: '请选择父部门', trigger: 'change' }],
  sortNum: [{ required: true, message: '请输入排序号', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const dialogTitle = computed(() => {
  if (isEdit.value) return '编辑部门'
  return editingRow.value?.id ? '新增子部门' : '新增顶级部门'
})

// ===== 父部门下拉树（禁用当前编辑节点+其子孙）=====
const parentTree = computed(() => {
  const disableIds = new Set()
  if (editingRow.value?.id) {
    collectDescendantIds(editingRow.value.id, treeData.value, disableIds)
  }
  const markDisabled = (nodes) => nodes.map(n => ({
    ...n,
    disabled: disableIds.has(n.id),
    children: n.children ? markDisabled(n.children) : []
  }))
  return [{ id: 0, deptName: '顶级部门', children: markDisabled(treeData.value) }]
})

const collectDescendantIds = (targetId, nodes, set) => {
  for (const n of nodes) {
    if (n.id === targetId) {
      set.add(n.id)
      const walk = (children) => children.forEach(c => { set.add(c.id); walk(c.children) })
      walk(n.children)
      return true
    }
    if (collectDescendantIds(targetId, n.children, set)) return true
  }
  return false
}

// ===== 获取数据 + 前端建树 =====
const fetchData = async () => {
  try {
    // 构造查询参数（空值不传，让后端用默认行为）
    const params = {}
    if (searchForm.deptName) params.deptName = searchForm.deptName.trim()
    if (searchForm.status != null) params.status = searchForm.status
    if (isPlatform.value && searchForm.tenantId != null) params.tenantId = searchForm.tenantId

    const res = await getAllDept(Object.keys(params).length ? params : undefined)
    const flatList = res.data || []
    treeData.value = buildTree(flatList)
  } catch (error) {
    console.error('获取部门列表失败', error)
  }
}

// ===== 重置搜索 =====
const handleReset = () => {
  searchForm.deptName = ''
  searchForm.tenantId = null
  searchForm.status = null
  fetchData()
}

// ===== 加载租户下拉（仅平台侧需要）=====
const loadTenants = async () => {
  if (!isPlatform.value) return
  try {
    const res = await getAllTenant()
    tenantList.value = res.data || []
  } catch (e) {
    console.error('加载租户列表失败', e)
  }
}

// 前端建树（扁平 → children 嵌套）
const buildTree = (flatList) => {
  const map = {}
  flatList.forEach(n => { map[n.id] = { ...n, children: [] } })
  const roots = []
  flatList.forEach(n => {
    const node = map[n.id]
    if (n.parentId === 0 || !map[n.parentId]) {
      roots.push(node)
    } else {
      map[n.parentId].children.push(node)
    }
  })
  // 递归排序
  const sortRecursive = (list) => {
    list.sort((a, b) => (a.sortNum || 0) - (b.sortNum || 0))
    list.forEach(n => sortRecursive(n.children))
  }
  sortRecursive(roots)
  return roots
}

const expandAll = () => {
  nextTick(() => {
    // el-table 树形展开：遍历 store 里的所有行，逐层展开
    const store = tableRef.value?.store
    if (!store) return
    // 获取所有行（包括子行）
    const rows = store.getFlattenRows ? store.getFlattenRows() : []
    // 如果没有 getFlattenRows，递归展开 root 行
    const expandRecursive = (row) => {
      if (row && row.children && row.children.length) {
        tableRef.value.toggleRowExpansion(row, true)
        row.children.forEach(expandRecursive)
      }
    }
    treeData.value.forEach(r => expandRecursive(r))
  })
}

const collapseAll = () => {
  nextTick(() => {
    const store = tableRef.value?.store
    if (!store) return
    const collapseRecursive = (row) => {
      if (row && row.children && row.children.length) {
        row.children.forEach(collapseRecursive)
        tableRef.value.toggleRowExpansion(row, false)
      }
    }
    treeData.value.forEach(r => collapseRecursive(r))
  })
}

const resetForm = () => { Object.assign(formData, defaultForm()) }

const handleAdd = () => {
  isEdit.value = false
  editingRow.value = null
  resetForm()
  dialogVisible.value = true
}

const handleAddChild = (row) => {
  isEdit.value = false
  editingRow.value = row
  resetForm()
  formData.parentId = row.id
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  editingRow.value = row
  Object.assign(formData, { ...defaultForm(), ...row })
  dialogVisible.value = true
}

const handleDelete = (row) => {
  const hasChildren = row.children && row.children.length
  const msg = hasChildren
    ? `部门"${row.deptName}"含有 ${row.children.length} 个子部门，确定删除？子部门将一同删除！`
    : `确定删除部门"${row.deptName}"？`
  ElMessageBox.confirm(msg, '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    await deleteDept(row.id)
    ElMessage.success('删除成功')
    fetchData()
  }).catch(() => {})
}

const handleSubmit = async () => {
  await formRef.value?.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) {
        await updateDept(formData)
        ElMessage.success('更新成功')
      } else {
        await addDept(formData)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (e) {
      console.error(e)
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
  editingRow.value = null
}

// ===== 导出部门数据 =====
const handleExport = async () => {
  const params = {}
  if (searchForm.deptName) params.deptName = searchForm.deptName.trim()
  if (searchForm.status != null) params.status = searchForm.status
  // 平台侧可选 tenantId 精确过滤；租户侧传了也被后端 CustomTenantHandler 丢弃（MP 自动隔离）
  if (isPlatform.value && searchForm.tenantId != null) params.tenantId = searchForm.tenantId

  exportLoading.value = true
  try {
    // ⭐ request.js 响应拦截器已旁路 responseType=blob 的请求
    //   这里拿到的是完整 axios response（含 .data Blob + .headers）
    const response = await exportDept(Object.keys(params).length ? params : {})

    // ⭐ 业务异常兜底：后端抛异常时 HTTP 200 + Content-Type: application/json
    //   因为 responseType=blob，即使后端返回 JSON 也会被包成 Blob
    //   检测到 application/json 就解析错误信息弹给用户
    const contentType = response.headers?.['content-type'] || ''
    if (contentType.includes('application/json')) {
      const errText = await response.data.text()
      const errJson = JSON.parse(errText)
      ElMessage.error(errJson.msg || '导出失败')
      return
    }

    // —— 正常导出分支 —— response.data 已经是 xlsx Blob，不需要再 new Blob 包一层
    const blob = response.data

    // 从 Content-Disposition 取真实文件名（对齐后端下载头模板）
    const disposition = response.headers?.['content-disposition'] || ''
    let fileName = '部门数据_' + new Date().toISOString().slice(0, 10) + '.xlsx'
    const match = disposition.match(/filename\*?=([^;]+)/i)
    if (match) {
      let raw = decodeURIComponent(match[1].trim().replace(/['"]/g, ''))
      raw = raw.replace(/^UTF-8''/i, '')
      fileName = raw || fileName
    }

    // 动态 a 标签触发下载
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)

    ElMessage.success('导出成功')
  } catch (err) {
    console.error('导出异常', err)
    ElMessage.error('导出失败，请稍后重试')
  } finally {
    // ⭐ 无论成功/异常都关闭 loading，避免按钮锁死
    exportLoading.value = false
  }
}

// ===== 导入部门（模板下载 + 文件选择 + 确认导入）=====
const handleImportClick = () => {
  importDialogVisible.value = true
}

// 下载导入模板（后端生成带说明 + 下拉的空模板）
const handleTemplateDownload = async () => {
  templateLoading.value = true
  try {
    const response = await importTemplate()
    // response.data 已经是 Blob（request.js 旁路 blob 响应）
    const blob = response.data
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '部门导入模板.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success('模板下载成功')
  } catch (err) {
    console.error('模板下载失败', err)
    ElMessage.error('模板下载失败，请稍后重试')
  } finally {
    templateLoading.value = false
  }
}

// 文件选择回调：Element Plus 的 on-change 签名是 (uploadFile, uploadFiles)
// uploadFile 是 UploadUserFile，.raw 才是浏览器原生 File 对象
const handleFileSelect = (uploadFile) => {
  if (!uploadFile?.raw) {
    selectedFile.value = null
    return
  }
  const name = (uploadFile.name || '').toLowerCase()
  if (!name.endsWith('.xlsx')) {
    ElMessage.warning('仅支持 .xlsx 格式的文件，请重新选择')
    selectedFile.value = null
    // 清空 el-upload 内部文件列表
    deptUploadRef.value?.clearFiles?.()
    return
  }
  selectedFile.value = uploadFile.raw // 存浏览器原生 File 对象
}

// 弹窗关闭：清空已选文件 + 重置 el-upload 内部列表
const handleImportDialogClose = () => {
  selectedFile.value = null
  importLoading.value = false
  deptUploadRef.value?.clearFiles?.()
  importDialogVisible.value = false
}

// 真正执行导入 —— 用户点"确认导入"时触发
const handleConfirmImport = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择要导入的 .xlsx 文件')
    return
  }

  importLoading.value = true
  try {
    const res = await importDept(selectedFile.value)
    const result = res.data || res

    if (result.failRows === 0) {
      ElMessage.success(`导入成功！共 ${result.totalRows} 行，全部入库`)
      handleImportDialogClose()
      fetchData()
    } else {
      // 存在失败：弹窗展示明细（弹窗保持打开，用户可以改完 Excel 重试）
      const failItems = result.failList || []
      const detailHtml = failItems
        .map(item => `<div style="margin:4px 0;">⚠️ 第 <b>${item.rowNum}</b> 行：${item.errorMsg}</div>`)
        .join('')
      ElMessageBox.alert(
        `<div style="max-height:360px;overflow-y:auto;">${detailHtml}</div>`,
        `导入完成 — 成功 ${result.successRows} 行，失败 ${result.failRows} 行`,
        {
          confirmButtonText: '知道了',
          dangerouslyUseHTMLString: true,
          type: 'warning'
        }
      )
    }
  } catch (err) {
    const msg = err?.response?.data?.msg || err?.message || '导入失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    importLoading.value = false
  }
}

// ===== 初始化 =====
onMounted(async () => {
  loadTenants()
  fetchData()
})
</script>

<style scoped>
.dept-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
</style>