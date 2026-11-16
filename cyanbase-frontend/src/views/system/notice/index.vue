﻿﻿﻿﻿﻿<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>通知公告</span>
          <el-button type="primary" @click="handleAdd" v-hasPermi="['system:notice:add']">
            <el-icon><Plus /></el-icon> 新增公告
          </el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="公告标题">
          <el-input v-model="searchForm.noticeTitle" placeholder="请输入公告标题" clearable />
        </el-form-item>
        <el-form-item label="公告类型">
          <el-select v-model="searchForm.noticeType" placeholder="请选择类型" clearable style="width: 140px">
            <el-option label="通知" value="1" />
            <el-option label="公告" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="70" align="center" :index="(i) => (pagination.current - 1) * pagination.size + i + 1" />
        <el-table-column prop="noticeTitle" label="公告标题" min-width="200" />
        <el-table-column prop="noticeType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.noticeType === '1' ? 'success' : 'warning'">
              {{ row.noticeType === '1' ? '通知' : '公告' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'">
              {{ row.status === '0' ? '正常' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="info" link @click="handleView(row)">查看详情</el-button>
            <el-button type="primary" link @click="handleEdit(row)" v-hasPermi="['system:notice:edit']">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)" v-hasPermi="['system:notice:delete']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="900px" top="5vh" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="公告标题" prop="noticeTitle">
          <el-input v-model="formData.noticeTitle" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="公告类型" prop="noticeType">
          <el-radio-group v-model="formData.noticeType">
            <el-radio label="1">通知</el-radio>
            <el-radio label="2">公告</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="公告状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">关闭</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="公告内容">
          <div style="border:1px solid #dcdfe6; border-radius:4px; width:100%">
            <Toolbar style="border-bottom:1px solid #dcdfe6" :editor="editorRef" :defaultConfig="toolbarConfig" mode="default" />
            <Editor style="height:400px; overflow-y:auto;" v-model="formData.noticeContent" :defaultConfig="editorConfig" mode="default" @onCreated="handleEditorCreated" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="公告详情" width="800px" top="5vh">
      <div class="detail-header">
        <h2 style="margin:0 0 8px 0">{{ detailData.noticeTitle }}</h2>
        <div class="detail-meta">
          <el-tag :type="detailData.noticeType === '1' ? 'success' : 'warning'" effect="plain">
            {{ detailData.noticeType === '1' ? '通知' : '公告' }}
          </el-tag>
          <span style="margin-left:12px; color:#909399; font-size:13px">{{ detailData.createTime }}</span>
        </div>
      </div>
      <el-divider />
      <div class="rich-content" v-html="detailData.noticeContent"></div>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'Notice' })
import { ref, reactive, computed, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { getNoticeList, addNotice, updateNotice, deleteNotice } from '@/api/notice'

const searchForm = reactive({ noticeTitle: '', noticeType: '' })
const tableData = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })
const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.noticeId ? '编辑公告' : '新增公告')
const isEdit = ref(false)
const formRef = ref(null)

// WangEditor 实例 & 配置
const editorRef = ref(null)
const toolbarConfig = {
  excludeKeys: []
}
const editorConfig = {
  placeholder: '请输入公告内容...',
  MENU_CONF: {}
}
const handleEditorCreated = (editor) => { editorRef.value = editor }

// 详情弹窗
const detailVisible = ref(false)
const detailData = reactive({ noticeTitle: '', noticeType: '', noticeContent: '', createTime: '' })
const handleView = (row) => {
  Object.assign(detailData, row)
  detailVisible.value = true
}

const formData = reactive({ noticeId: null, noticeTitle: '', noticeType: '1', noticeContent: '', status: '0', remark: '' })
const formRules = {
  noticeTitle: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  noticeType: [{ required: true, message: '请选择公告类型', trigger: 'change' }]
}

const fetchData = async () => {
  try {
    const res = await getNoticeList({ current: pagination.current, size: pagination.size, ...searchForm })
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (error) { console.error(error) }
}

const handleAdd = () => { isEdit.value = false; Object.assign(formData, { noticeId: null, noticeTitle: '', noticeType: '1', noticeContent: '', status: '0', remark: '' }); dialogVisible.value = true }
const handleEdit = (row) => { isEdit.value = true; Object.assign(formData, row); dialogVisible.value = true }
const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该公告吗？', '提示', { type: 'warning' })
    .then(async () => { await deleteNotice(row.noticeId); ElMessage.success('删除成功'); fetchData() }).catch(() => {})
}
const handleSubmit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) { await updateNotice(formData); ElMessage.success('更新成功') }
      else { await addNotice(formData); ElMessage.success('新增成功') }
      dialogVisible.value = false; fetchData()
    } catch (error) { console.error(error) }
  })
}
const handleDialogClose = () => {
  formRef.value?.resetFields()
  // 销毁 WangEditor 实例
  if (editorRef.value) {
    editorRef.value.destroy()
    editorRef.value = null
  }
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
  Object.assign(searchForm, { noticeTitle: '', noticeType: '' })
  pagination.current = 1
  fetchData()
}

// 兜底销毁
onBeforeUnmount(() => {
  if (editorRef.value) { editorRef.value.destroy(); editorRef.value = null }
})

fetchData()
</script>

<style scoped>
.page-container { width: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 20px; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
.detail-header h2 { font-size: 20px; font-weight: 600; }
.detail-meta { display: flex; align-items: center; }
/* 富文本内容渲染样式 —— 详情页 */
.rich-content :deep(p) { margin: 8px 0; line-height: 1.8; }
.rich-content :deep(h1), .rich-content :deep(h2), .rich-content :deep(h3) { margin: 16px 0 8px; font-weight: 600; }
.rich-content :deep(h1) { font-size: 20px; }
.rich-content :deep(h2) { font-size: 18px; }
.rich-content :deep(h3) { font-size: 16px; }
.rich-content :deep(ul), .rich-content :deep(ol) { padding-left: 24px; margin: 8px 0; }
.rich-content :deep(li) { margin: 4px 0; line-height: 1.8; }
.rich-content :deep(a) { color: #409eff; text-decoration: none; }
.rich-content :deep(a:hover) { text-decoration: underline; }
.rich-content :deep(img) { max-width: 100%; height: auto; margin: 8px 0; border-radius: 4px; }
.rich-content :deep(blockquote) { border-left: 4px solid #dcdfe6; padding: 8px 16px; margin: 8px 0; background: #f5f7fa; color: #606266; }
.rich-content :deep(table) { border-collapse: collapse; margin: 8px 0; width: 100%; }
.rich-content :deep(td), .rich-content :deep(th) { border: 1px solid #dcdfe6; padding: 6px 12px; }
</style>