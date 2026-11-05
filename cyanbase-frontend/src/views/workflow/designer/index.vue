﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿<template>
  <div class="workflow-designer-container">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button @click="handleBack"><el-icon><Back /></el-icon>返回列表</el-button>
        <el-tag v-if="definition.status === 0" type="warning" size="large">草稿</el-tag>
        <el-tag v-else-if="definition.status === 1" type="success" size="large">已发布</el-tag>
        <el-tag v-else type="info" size="large">已停用</el-tag>
        <span class="version-tag">v{{ definition.version || 1 }}</span>
        <!-- 版本模式下额外显示版本号+只读标签 -->
        <template v-if="isVersionMode">
          <el-tag v-if="action === 'version-view'" type="info" size="small" style="margin-left:8px">只读查看</el-tag>
          <el-tag v-else-if="action === 'version-edit'" type="primary" size="small" style="margin-left:8px">版本编辑中</el-tag>
          <span style="margin-left:6px;color:#909399;font-size:12px">
            版本快照 v{{ queryVersionNo }}<template v-if="versionName"> · {{ versionName }}</template>
          </span>
        </template>
      </div>
      <div class="toolbar-right">
        <el-button @click="openPreview" :disabled="!definition.id">
          <el-icon><View /></el-icon>BPMN 预览
        </el-button>
        <!-- 保存: preview 和 version-view 都不显示 -->
        <el-button type="primary" @click="handleSave" v-if="action !== 'preview' && action !== 'version-view'">
          <el-icon><Check /></el-icon>{{ action === 'version-edit' ? '保存版本快照' : '保存配置' }}
        </el-button>
        <!-- 发布/租户: 版本模式下暂时隐藏(后续做版本启用时再加) -->
        <el-button type="success" @click="handlePublish" v-if="action !== 'preview' && action !== 'version-view' && action !== 'version-edit' && definition.status !== 1">
          <el-icon><Upload /></el-icon>发布流程
        </el-button>
        <el-button type="warning" @click="openTenantPublish" v-if="action !== 'preview' && action !== 'version-view' && action !== 'version-edit' && definition.status === 1 && userStore.getIsPlatform">
          <el-icon><Promotion /></el-icon>发布到租户
        </el-button>
        <el-button @click="openTenantList" v-if="action !== 'preview' && action !== 'version-view' && action !== 'version-edit' && definition.status === 1 && userStore.getIsPlatform">
          <el-icon><Share /></el-icon>租户分发
        </el-button>
      </div>
    </div>

    <div class="content-wrap">
      <!-- 左：定义信息表单 -->
      <el-card class="panel left-panel" shadow="never">
        <template #header><strong>流程基本信息</strong></template>
        <el-form :model="definition" label-width="90px" :disabled="isReadonly">
          <el-form-item label="流程名称"><el-input v-model="definition.processName" placeholder="如：三重一大审批" /></el-form-item>
          <!-- ⭐ 流程标识(processKey)隐藏：非开发人员无需看到，后台自动生成/校验逻辑不动 -->
          <el-form-item label="流程标识" v-if="false">
            <el-input v-model="definition.processKey" placeholder="自动生成，可自定义" />
            <div class="form-tip">唯一标识，建议英文+下划线</div>
          </el-form-item>
          <el-form-item label="业务类型" required>
            <el-select v-model="definition.businessType" placeholder="选择本流程服务于哪个业务" style="width: 100%">
              <el-option v-for="opt in businessTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <div class="form-tip">⚠️ 必填，业务侧 submit 时按此字段匹配流程</div>
          </el-form-item>
          <el-form-item label="关联表单">
            <el-select v-model="definition.formDefinitionId" placeholder="选择关联的表单（用于条件下拉）"
                       clearable style="width: 100%" :disabled="isReadonly">
              <el-option v-for="opt in formDefOptions" :key="opt.id"
                         :label="opt.formName + (opt.category ? ' [' + opt.category + ']' : '')"
                         :value="opt.id" />
            </el-select>
            <div class="form-tip">🔀 选了表单后，条件网关可下拉选表单字段 + 字典选项</div>
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="definition.category" filterable allow-create default-first-option
                       placeholder="可下拉选也可手动输入" style="width: 100%">
              <el-option v-for="opt in categoryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="描述"><el-input v-model="definition.description" type="textarea" :rows="3" placeholder="选填" /></el-form-item>
        </el-form>
      </el-card>

      <!-- 右：节点配置表格 -->
      <el-card ref="rightPanelRef" class="panel right-panel" :class="{ 'is-fullscreen': isPanelFullscreen }" shadow="never">
        <template #header>
          <div class="panel-header">
            <strong>审批节点配置</strong>
            <div class="panel-header-actions">
              <!-- 全屏按钮：不支持时隐藏 -->
              <el-button link size="small" class="btn-fullscreen"
                         :title="isPanelFullscreen ? '恢复弹窗大小' : '放大编辑区域'"
                         @click="togglePanelFullscreen">
                <span class="fs-icon">{{ isPanelFullscreen ? '▢' : '⛶' }}</span>
              </el-button>
              <el-button type="primary" link @click="addNode" v-if="!isReadonly">
                <el-icon><Plus /></el-icon>新增节点
              </el-button>
            </div>
          </div>
        </template>

        <el-table :data="nodes" border size="small" :disabled="isReadonly" empty-text="请先新增一个 start 节点">
          <el-table-column label="#" width="50" type="index" />

          <el-table-column label="节点类型" width="110" class-name="col-node-type">
            <template #default="{ row, $index }">
              <el-select v-model="row.nodeType" :disabled="isReadonly" size="small">
                <el-option label="开始节点" value="start" />
                <el-option label="审批节点" value="userTask" />
                <el-option label="网关" value="gateway" />
                <el-option label="结束节点" value="end" />
              </el-select>
              <!-- ⭐ 用一个固定位置的容器统一 + 和 − 的 left 对齐 -->
              <div v-if="!isReadonly" class="col-edge-btns">
                <el-button title="增加节点" class="btn-insert-row" link type="primary" size="small"
                           @click="addNode($index + 1)">+</el-button>
                <el-button title="删除节点" class="btn-delete-row" link type="danger" size="small"
                           :disabled="nodes.length <= 1" @click="removeNode($index)">−</el-button>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="节点名称" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.nodeName" :disabled="isReadonly" size="small" placeholder="如：部门领导审批" />
            </template>
          </el-table-column>

          <el-table-column label="审批人类型" width="120">
            <template #default="{ row }">
              <el-select v-model="row.assigneeType" :disabled="isReadonly || row.nodeType !== 'userTask'" size="small">
                <el-option label="指定用户" value="user" />
                <el-option label="指定角色" value="role" />
                <el-option label="指定部门" value="dept" />
                <el-option label="指定岗位" value="post" />
                <el-option label="发起人自己" value="initiator" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="审批人值" min-width="260">
            <template #default="{ row }">
              <!-- 未选 assigneeType 或 非 userTask：隐藏 -->
              <template v-if="row.nodeType !== 'userTask'">
                <span class="muted">—</span>
              </template>
              <!-- initiator：固定标签 -->
              <template v-else-if="row.assigneeType === 'initiator'">
                <el-tag type="info" size="small">发起人自己</el-tag>
              </template>
              <!-- post：本地全量岗位下拉（不走远程） -->
              <template v-else-if="row.assigneeType === 'post'">
                <el-select
                  v-model="row._assigneeValueList"
                  multiple collapse-tags collapse-tags-tooltip
                  :filterable="true"
                  :disabled="isReadonly"
                  size="small"
                  placeholder="选岗位" style="width: 100%">
                  <el-option
                    v-for="opt in postOptions"
                    :key="opt.postId"
                    :label="opt.postName"
                    :value="opt.postId" />
                </el-select>
              </template>
              <!-- user / role / dept：远程搜索下拉 -->
              <template v-else>
                <el-select
                  v-model="row._assigneeValueList"
                  multiple collapse-tags collapse-tags-tooltip
                  :remote="true"
                  :remote-method="(kw) => remoteSearch(kw, row)"
                  :filterable="true"
                  :loading="row._searchLoading"
                  :disabled="isReadonly"
                  size="small"
                  placeholder="输入关键字搜索" style="width: 100%">
                  <el-option
                    v-for="opt in row._assigneeOptions"
                    :key="opt.id"
                    :label="opt.label"
                    :value="opt.id" />
                </el-select>
              </template>
            </template>
          </el-table-column>

          <el-table-column label="审批类型" width="110">
            <template #default="{ row }">
              <el-select v-model="row.approvalType" :disabled="isReadonly || row.nodeType !== 'userTask'" size="small">
                <el-option label="或签(任一人通过)" value="OR" />
                <el-option label="会签(全部通过)" value="AND" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="签字时效(h)" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.timeoutHours" :min="0" :disabled="isReadonly || row.nodeType !== 'userTask'"
                               size="small" style="width:100%" />
            </template>
          </el-table-column>

          <!-- 网关类型列（仅网关节点可编辑） -->
          <el-table-column label="网关类型" width="120">
            <template #default="{ row }">
              <el-select v-if="row.nodeType === 'gateway'"
                         v-model="row.gatewayType" :disabled="isReadonly"
                         size="small" style="width:100%">
                <el-option label="排他(条件分支)" value="exclusive" />
                <el-option label="并行(全部分支)" value="parallel" />
                <el-option label="排他汇聚(任意到达)" value="exclusiveJoin" />
              </el-select>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>

          <!-- 分支条件列：配在网关出边指向的「目标节点」上，不是网关自身 -->
          <el-table-column label="分支条件(SpEL)" min-width="380">
            <template #default="{ row }">
              <!-- gateway / start / end 节点不需要条件 -->
              <template v-if="row.nodeType === 'gateway' || row.nodeType === 'start' || row.nodeType === 'end'">
                <span class="muted">—</span>
              </template>
              <!-- userTask / 其他节点：三列拼成 SpEL -->
              <template v-else>
                <div style="display: flex; gap: 4px; align-items: center;">
                  <!-- ⭐ 字段下拉 -->
                  <el-select v-model="row._condField" :disabled="isReadonly"
                             size="small" style="flex: 1; min-width: 120px"
                             placeholder="选字段" clearable filterable
                             @change="syncConditionExpr(row)">
                    <el-option-group label="流程内置变量">
                      <el-option v-for="v in BUILTIN_VARS" :key="v.fieldKey"
                                 :label="v.fieldName"
                                 :value="v.fieldKey" />
                    </el-option-group>
                    <el-option-group label="表单字段">
                      <el-option v-for="f in formFieldList" :key="f.fieldKey"
                                 :label="f.fieldName"
                                 :value="f.fieldKey" />
                    </el-option-group>
                  </el-select>
                  <!-- ⭐ 操作符下拉 -->
                  <el-select v-model="row._condOp" :disabled="isReadonly"
                             size="small" style="width:80px"
                             placeholder="?" @change="syncConditionExpr(row)">
                    <el-option v-for="o in availableOps(row)" :key="o.value"
                               :label="o.label"
                               :value="o.value" />
                  </el-select>
                  <!-- ⭐ 值列：根据字段类型动态切换 —— 内置变量 submitterDeptId 走部门树 / 有选项字段走下拉 / 普通字段走输入框 -->
                  <el-tree-select v-if="row._condField === 'submitterDeptId'"
                                  v-model="row._condValue" :disabled="isReadonly"
                                  :data="deptTree"
                                  :props="{ label: 'deptName', value: 'id', children: 'children' }"
                                  size="small" style="flex: 1; min-width: 120px"
                                  placeholder="选部门" clearable check-strictly
                                  @change="syncConditionExpr(row)" />
                  <el-select v-else-if="getConditionField(row)?.fieldType && HAS_OPT_TYPES.includes(getConditionField(row).fieldType)"
                             v-model="row._condValue" :disabled="isReadonly"
                             size="small" style="flex: 1; min-width: 120px"
                             placeholder="选值" clearable filterable
                             @change="syncConditionExpr(row)">
                    <el-option v-for="opt in getFieldOptions(getConditionField(row))"
                               :key="opt.value"
                               :label="opt.label"
                               :value="opt.value" />
                  </el-select>
                  <!-- 普通字段 → 输入框 + 金额字段警告标识 -->
                  <div v-else style="display: flex; align-items: center; flex: 1; gap: 4px; min-width: 120px;">
                    <el-input
                              v-model="row._condValue" :disabled="isReadonly"
                              size="small" style="flex: 1"
                              placeholder="值" clearable
                              @input="syncConditionExpr(row)" />
                    <!-- ⭐ 金额（number型）字段：非数字值实时橙色警告标识 -->
                    <el-tooltip
                      v-if="isAmountField(row) && row._condValue !== '' && row._condValue != null && !isValidNumber(row._condValue)"
                      content="金额字段，请输入合法数字"
                      placement="top">
                      <el-icon class="amount-warn-icon" :size="16"><WarningFilled /></el-icon>
                    </el-tooltip>
                  </div>
                </div>
                <!-- ⭐ 中文描述（BPMN连线name属性）—— 可手动编辑 -->
                <div style="margin-top: 4px; display: flex; gap: 2px; align-items: center;">
                  <span style="font-size: 11px; color: #909399;">描述:</span>
                  <el-input v-model="row.showDesc" :disabled="isReadonly"
                            size="small" style="flex: 1"
                            :placeholder="row._condField && row._condOp ? autoBuildShowDesc(row) : '选条件后自动生成，可手动改'"
                            clearable
                            @focus="markShowDescManual(row)" />
                </div>
              </template>
            </template>
          </el-table-column>

          <el-table-column label="下一节点" width="200">
            <template #default="{ row }">
              <el-select v-model="row.nextNodeIds" :disabled="isReadonly || row.nodeType === 'end'"
                         multiple collapse-tags collapse-tags-tooltip size="small" placeholder="选下一节点">
                <el-option v-for="n in otherNodes(row.id)" :key="n.id" :label="n.nodeName + ' (' + typeLabel(n.nodeType) + ')'"
                           :value="String(n.id)" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="排序" width="80">
            <template #default="{ row, $index }">
              <div class="sort-btns">
                <el-button link size="small" :disabled="isReadonly || $index === 0" @click="moveUp($index)">↑</el-button>
                <el-button link size="small" :disabled="isReadonly || $index === nodes.length - 1" @click="moveDown($index)">↓</el-button>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" :disabled="isReadonly" @click="removeNode($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- BPMN 只读预览弹窗 -->
    <el-dialog v-model="previewVisible" title="BPMN 只读预览" width="90%" top="3vh" :close-on-click-modal="true"
               @close="handlePreviewClose">
      <div class="preview-wrapper">
        <!-- ⭐ 右上角工具栏 -->
        <div class="preview-toolbar">
          <el-tooltip content="缩小 (-)" placement="top">
            <el-button :icon="ZoomOut" circle size="small" @click="bpmnZoom(-0.2)" />
          </el-tooltip>
          <el-tooltip content="放大 (+)" placement="top">
            <el-button :icon="ZoomIn" circle size="small" @click="bpmnZoom(0.2)" />
          </el-tooltip>
          <!-- ⭐ 手型工具：放大后拖动画布 -->
          <el-tooltip :content="isPanMode ? '退出手型 (Esc)' : '手型拖动画布 (H)'" placement="top">
            <el-button circle size="small" :class="{ 'is-active': isPanMode }" @click="togglePanMode">
              <HandIcon />
            </el-button>
          </el-tooltip>
          <el-tooltip content="适屏 (F)" placement="top">
            <el-button :icon="FullScreen" circle size="small" @click="bpmnFit()" />
          </el-tooltip>
          <el-divider direction="vertical" />
          <el-tooltip content="导出 SVG" placement="top">
            <el-button :icon="Picture" circle size="small" @click="exportBpmn('svg')" />
          </el-tooltip>
          <el-tooltip content="导出 JPG" placement="top">
            <el-button :icon="Camera" circle size="small" @click="exportBpmn('jpg')" />
          </el-tooltip>
          <el-tooltip content="导出 PDF（浏览器打印）" placement="top">
            <el-button :icon="Document" circle size="small" @click="exportBpmn('pdf')" />
          </el-tooltip>
        </div>
        <div id="bpmn-canvas" class="bpmn-canvas-wrap" :class="{ 'pan-mode': isPanMode }"
             style="height: 60vh; border: 1px solid #eee; background:#fafafa; position:relative; overflow:hidden;"></div>
      </div>
    </el-dialog>

    <!-- 发布到租户弹框 -->
    <el-dialog v-model="tenantPublishVisible" title="发布到指定租户" width="500px">
      <el-form label-width="120px">
        <el-form-item label="目标租户">
          <el-select v-model="tenantPublishForm.tenantIds" multiple collapse-tags
                     :remote="true" :remote-method="searchTenant" :loading="tenantSearchLoading"
                     filterable style="width: 100%" placeholder="搜索并选择租户">
            <el-option v-for="t in tenantOptions" :key="t.id" :label="t.tenantName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="允许租户修改">
          <el-switch v-model="tenantPublishForm.allowEdit" />
          <span style="margin-left:10px;color:#999;font-size:12px">{{ tenantPublishForm.allowEdit ? '租户可复制到自己租户后自由编辑' : '租户只能使用不能修改' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tenantPublishVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPublishToTenants" :disabled="!tenantPublishForm.tenantIds?.length">确定发布</el-button>
      </template>
    </el-dialog>

    <!-- ⭐ 字段选择弹窗（Workflow Designer 条件网关用） -->
    <el-dialog v-model="formFieldSelectorVisible" title="从表单字段生成条件" width="600px" :close-on-click-modal="true">
      <div v-if="!definition.formDefinitionId" style="color:#999; text-align:center; padding:20px;">
        请先在左侧「关联表单」选择一个表单
      </div>
      <div v-else>
        <div style="margin-bottom:10px;">
          <el-tag type="info" size="small">当前表单共 {{ formFieldList.length }} 个字段</el-tag>
          <el-button link size="small" @click="loadFormFields" style="margin-left:10px;">🔄 重新加载</el-button>
        </div>
        <el-table :data="formFieldList" border stripe size="small" max-height="380">
          <el-table-column prop="fieldKey" label="字段标识" width="120" />
          <el-table-column prop="fieldName" label="字段名称" width="140" />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="fieldTypeTagType(row.fieldType)">{{ row.fieldType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="字典/选项">
            <template #default="{ row }">
              <el-tag v-if="row.dictTypeCode" size="small" type="warning">
                字典: {{ row.dictTypeCode }}
              </el-tag>
              <el-tag v-else-if="row.options" size="small" type="success">本地选项</el-tag>
              <span v-else style="color:#bbb">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" @click="insertFieldSpEL(row)">插入</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 租户分发记录弹框 -->
    <el-dialog v-model="tenantListVisible" title="租户分发记录" width="700px">
      <el-table :data="tenantDistList" border stripe>
        <!-- 租户ID → 映射显示租户名称 -->
        <el-table-column label="租户名称" min-width="160">
          <template #default="{ row }">
            {{ tenantNameMap[row.tenantId] || ('租户 #' + row.tenantId) }}
          </template>
        </el-table-column>
        <el-table-column prop="definitionName" label="流程名称" min-width="150" />
        <el-table-column label="编辑权限" width="120">
          <template #default="{ row }">
            <el-tag :type="row.allowEdit ? 'success' : 'info'">{{ row.allowEdit ? '可复制编辑' : '仅使用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '有效' : '已撤回' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="receiveTime" label="接收时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" type="danger" link size="small" @click="handleRevoke(row)">撤回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'WorkflowDesigner' })
import { ref, reactive, computed, onMounted, nextTick, markRaw, onBeforeUnmount, watch, defineComponent, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getWorkflowDetail, saveWorkflowAll, publishWorkflowDefinition, getBpmnXml, publishToTenants, getTenantDistribution, revokeTenantDistribution, getVersionDetail, saveVersionSnapshot, getVersionBpmn } from '@/api/wfDefinition'
import { getFormDefAllActive, getFormFieldList } from '@/api/form'
import { getDictDataByType } from '@/api/dictData'
import { getAllDept } from '@/api/dept'
import { getAllPost } from '@/api/post'
import request from '@/utils/request'
import BpmnViewer from 'bpmn-js/lib/Viewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import { Promotion, Share, ZoomIn, ZoomOut, FullScreen, Picture, Camera, Document, Delete, WarningFilled } from '@element-plus/icons-vue'

// ⭐ 手型图标（Element Plus 没有现成的，内联 SVG）
const HandIcon = defineComponent({
  setup() {
    return () => h('svg', {
      width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
      stroke: 'currentColor', 'stroke-width': 2, 'stroke-linecap': 'round', 'stroke-linejoin': 'round'
    }, [
      h('path', { d: 'M18 11V6a2 2 0 0 0-4 0v5' }),
      h('path', { d: 'M14 10V4a2 2 0 0 0-4 0v6' }),
      h('path', { d: 'M10 10.5V6a2 2 0 0 0-4 0v8' }),
      h('path', { d: 'M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15' })
    ])
  }
})

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const id = route.query.id || null
const action = route.query.action || 'edit'

// ========== 版本模式变量 ==========
const versionId = route.query.versionId || null
const queryVersionNo = route.query.versionNo || null
const versionName = ref('')  // ⭐ 当前版本名称

/** 是否版本模式（script 里用 .value，模板里自动解包） */
const isVersionMode = computed(() => action === 'version-view' || action === 'version-edit')

/** 只读模式（预览 或 版本查看） */
const isReadonly = computed(() => action === 'preview' || action === 'version-view')

const definition = reactive({
  id: null, processKey: '', processName: '', description: '', category: '',
  businessType: '', version: 1, status: 0, startNodeId: null, processXml: '',
  formDefinitionId: null
})

// ⭐ 关联表单的字段列表（Workflow Designer 条件下拉用）
const formDefOptions = ref([])       // 表单定义下拉选项
const formFieldList = ref([])        // 当前关联表单的字段元数据
const formFieldLoading = ref(false)
const formFieldSelectorVisible = ref(false)  // 字段选择弹窗可见性
const dictCache = ref({})  // { dictTypeCode: [{label, value}] } ⭐ 字典缓存

// ⭐ 审批节点配置面板 CSS 模拟全屏
const rightPanelRef = ref(null)
const isPanelFullscreen = ref(false)

/** ⭐ ESC 退出模拟全屏 */
onMounted(async () => {
  await loadFormDefOptions()
  await loadData()
  loadDeptTree().catch(() => {})  // ⭐ 预加载部门树（失败不阻塞主流程）
  loadPostOptions().catch(() => {})  // ⭐ 预加载岗位下拉（失败不阻塞主流程）
  document.addEventListener('keydown', onEscExitFullscreen)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onEscExitFullscreen)
})

// ⭐ 业务类型下拉选项（与后端 ThreeBigItemWorkflowBinder.PREFIX 对应）
const businessTypeOptions = [
  { label: '三重一大事项', value: 'threeBigItem' },
  { label: '三重一大会议', value: 'threeBigMeeting' },
  { label: '三重一大合同', value: 'threeBigContract' },
  { label: '通用审批', value: 'system' },
]

// ⭐ 分类下拉选项（预设常见值，允许自定义输入）
const categoryOptions = [
  { label: '三重一大审批', value: '三重一大审批' },
  { label: '公文审批', value: '公文审批' },
  { label: '请假出差', value: '请假出差' },
  { label: '采购报销', value: '采购报销' },
]
const nodes = ref([])
const previewVisible = ref(false)
let bpmnViewer = null

// ⭐ 租户分发相关状态
const tenantPublishVisible = ref(false)
const tenantListVisible = ref(false)
const tenantPublishForm = reactive({ tenantIds: [], allowEdit: false })
const tenantDistList = ref([])
const tenantOptions = ref([])
const tenantSearchLoading = ref(false)
// 租户ID → 租户名称映射（给「租户分发记录」弹窗用）
const tenantNameMap = ref({})

// ========== 远程搜索（根据 assigneeType 切不同接口） ==========
const remoteSearch = async (keyword, row) => {
  if (row.nodeType !== 'userTask' || !row.assigneeType || row.assigneeType === 'initiator' || row.assigneeType === 'post') return
  row._searchLoading = true
  try {
    let url = ''
    if (row.assigneeType === 'user') url = '/system/user/search'
    else if (row.assigneeType === 'role') url = '/system/role/search'
    else if (row.assigneeType === 'dept') url = '/system/dept/search'
    const res = await request({ url, method: 'get', params: { keyword: keyword || '', limit: 50 } })
    row._assigneeOptions = res.data || []
  } catch (e) {
    console.error('远程搜索失败', e)
  } finally {
    row._searchLoading = false
  }
}

// ========== 给每个节点初始化临时字段 ==========
const initNodeTempFields = (node) => {
  // ⭐ 新字段默认值 — 按节点类型区分
  if (!node.approvalType) node.approvalType = 'OR'
  if (node.timeoutHours == null) node.timeoutHours = 72
  // conditionExpr 只对 userTask 有意义（配在网关指向的目标节点上）
  if (!node.conditionExpr) node.conditionExpr = ''
  // gatewayType 只对 gateway 有意义
  if (node.nodeType === 'gateway') {
    if (!node.gatewayType) node.gatewayType = 'exclusive'
  } else {
    node.gatewayType = '' // 非网关节点清空，避免脏数据
  }

  // 1. assigneeValue: 逗号串 → 数字数组
  let list = []
  if (node.assigneeValue && node.assigneeType !== 'initiator') {
    list = node.assigneeValue.split(',').filter(s => s.trim() !== '').map(s => Number(s.trim()))
  }
  node._assigneeValueList = list
  node._assigneeOptions = []
  node._searchLoading = false

  // 2. ⭐ nextNodeIds: 逗号串 → 字符串数组
  if (typeof node.nextNodeIds === 'string') {
    node.nextNodeIds = node.nextNodeIds.split(',').filter(s => s.trim() !== '').map(s => String(s.trim()))
  } else if (!Array.isArray(node.nextNodeIds)) {
    node.nextNodeIds = []
  }

  // 3. ⭐ conditionExpr → 三个辅助字段（反向解析，用于 3 个下拉/输入框展示）
  node._condField = ''
  node._condOp = ''
  node._condValue = ''
  node.showDesc = node.showDesc || ''  // ⭐ showDesc 中文描述，从后端带回；没有则空
  node._showDescManual = !!node.showDesc  // 有值认为是已手动设置
  if (node.conditionExpr) {
    const m = node.conditionExpr.match(/^\s*([\w#.]+)\s*(==|!=|>=|<=|>|<)\s*'?([^']*)'?\s*$/)
    if (m) {
      node._condField = m[1]
      node._condOp = m[2]
      // ⭐ submitterDeptId 反解析时把字符串数字转成 Number
      //    el-tree-select 用 === 严格匹配 deptTree.id（数字），字符串 "16" !== 数字 16
      if (m[1] === 'submitterDeptId' && m[3] !== '' && !isNaN(Number(m[3]))) {
        node._condValue = Number(m[3])
      } else {
        node._condValue = m[3]
      }
      // 如果后端没给 showDesc，反解析成功的话自动生成一个
      if (!node.showDesc) {
        node.showDesc = autoBuildShowDesc(node)
      }
    }
  }

  // 3. 监听 assigneeValueList 变化 → 同步回 assigneeValue 逗号串
  watch(() => node._assigneeValueList, (val) => {
    node.assigneeValue = (val || []).join(',')
  })

  // 4. 监听 assigneeType 变化 → 清空旧选择、清空选项
  watch(() => node.assigneeType, () => {
    node._assigneeValueList = []
    node._assigneeOptions = []
    node.assigneeValue = ''
  })

  return node
}

// ========== ⭐ 预加载已有审批人的 label（让 el-select 能显示文字而不是裸数字） ==========
const preloadAssigneeLabels = async () => {
  // 对每个 userTask 节点，如果已经有 assigneeValue（逗号串），自动调一次远程搜索把 label 拉回来
  for (const node of nodes.value) {
    if (node.nodeType !== 'userTask') continue
    if (!node.assigneeType || node.assigneeType === 'initiator') continue
    if (!node.assigneeValue && (!node._assigneeValueList || node._assigneeValueList.length === 0)) continue
    // 已经有 options 了就不重复拉
    if (node._assigneeOptions && node._assigneeOptions.length > 0) continue
    // 用一个空关键字搜索，后端 limit=50 足够覆盖常见场景
    try {
      await remoteSearch('', node)
    } catch (e) {
      console.warn('预加载审批人 label 失败', e)
    }
  }
}

// ========== 加载已有数据 ==========
const loadData = async () => {
  if (!id) return
  try {
    let res
    if (versionId) {
      // 版本模式：从版本快照加载
      res = await getVersionDetail(versionId)
      const d = res.data.definition
      Object.assign(definition, d)
      // ⭐ 补：版本快照里的 BPMN XML
      if (res.data.processXml) definition.processXml = res.data.processXml
      // 版本号优先用 URL 参数
      if (queryVersionNo) definition.version = Number(queryVersionNo)
      else if (res.data.versionNo) definition.version = res.data.versionNo
      // ⭐ 版本名称
      versionName.value = res.data.versionName || ''
      nodes.value = (res.data.nodes || []).map(n => initNodeTempFields(n))
    } else {
      // 原逻辑：从主表加载
      res = await getWorkflowDetail(id)
      const d = res.data.definition
      Object.assign(definition, d)
      nodes.value = (res.data.nodes || []).map(n => initNodeTempFields(n))
    }
    preloadAssigneeLabels()
  } catch (e) {
    ElMessage.error('加载失败')
    console.error(e)
  }
}

// ========== 节点操作 ==========
// ⭐ 用 id 做匹配（后端 parseFlowJson 已把 nextNodeIds 转成 finalId 数字格式）
const otherNodes = (curId) => nodes.value.filter(n => String(n.id) !== String(curId))
const typeLabel = (t) => ({ start: '开始', userTask: '审批', gateway: '网关', end: '结束' }[t] || t)

// ⭐ 辅助：根据 _condField / _condOp / _condValue 拼出完整 SpEL
const buildConditionString = (row) => {
  const f = row._condField?.trim()
  const op = row._condOp
  const v = row._condValue
  if (!f || !op) return ''
  // 值是纯数字不加引号，其他加单引号
  if (v === undefined || v === '' || v === null) return `${f} ${op} null`
  return /^-?\d+(\.\d+)?$/.test(v) ? `${f} ${op} ${v}` : `${f} ${op} '${v}'`
}

/**
 * ⭐ 自动生成中文描述（showDesc）
 * 把 fieldKey 换成友好的 fieldName，操作符换成中文，值做人性化处理
 * 例：fieldKey=amount, op=>, v=10000 → "金额 > 10000"
 *     fieldKey=dept, op==, v=finance → "部门 == finance"
 */
const OP_LABEL = { '==': '等于', '!=': '不等于', '>': '大于', '>=': '大于等于', '<': '小于', '<=': '小于等于' }
const autoBuildShowDesc = (row) => {
  const f = row._condField?.trim()
  const op = row._condOp
  const v = row._condValue
  if (!f || !op) return ''
  // 从 formFieldList 找友好名称
  const fieldObj = getConditionField(row)
  const fieldLabel = fieldObj?.fieldName || f  // 找不到就退回 fieldKey
  const opLabel = OP_LABEL[op] || op
  const val = (v === undefined || v === '' || v === null) ? '空' : v
  return `${fieldLabel} ${opLabel} ${val}`
}

// ⭐ 三个子控件 change/input 时同步回 row.conditionExpr + showDesc
const syncConditionExpr = (row, fieldChanged = false) => {
  // 字段切换 → 自动兜底：内置变量强制 op 为 ==、清空值；普通字段保留现有 op（如果合法）
  if (fieldChanged) {
    const f = row._condField
    if (!f) { row._condOp = ''; row._condValue = '' }
    else if (builtinFieldKeys.includes(f)) {
      row._condOp = '=='
      row._condValue = ''
    } else {
      // 普通字段：如果当前 op 对该字段类型不合法，重置为 ==
      const validOps = availableOps(row).map(o => o.value)
      if (!validOps.includes(row._condOp)) row._condOp = '=='
      row._condValue = ''
    }
  }
  row.conditionExpr = buildConditionString(row)
  if (!row._showDescManual) row.showDesc = autoBuildShowDesc(row)
}

// ⭐ 用户手动编辑 showDesc 时打标记，之后不再自动覆盖
const markShowDescManual = (row) => {
  row._showDescManual = true
}

// ═══════════════════════════════════════════════════════════
// ⭐ 根据字段类型动态切换「值列」为下拉还是输入框
// ═══════════════════════════════════════════════════════════

/** 有选项的字段类型 → 值列用下拉 */
const HAS_OPT_TYPES = ['select', 'radio', 'checkbox', 'cascader']

// ═══════════════════════════════════════════════════════════
// ⭐ 流程内置变量定义（submitterDeptId 等）
// ═══════════════════════════════════════════════════════════
const BUILTIN_VARS = [
  { fieldKey: 'submitterDeptId', fieldName: '提交人所属部门ID', fieldType: 'dept' },
]
const builtinFieldKeys = BUILTIN_VARS.map(v => v.fieldKey)

/** 部门树数据（el-tree-select 用） */
const deptTree = ref([])
/** ⭐ 岗位下拉数据源（指定岗位审批人用） */
const postOptions = ref([])
const loadPostOptions = async () => {
  try {
    const res = await getAllPost()
    postOptions.value = res.data || []
  } catch (e) { postOptions.value = [] }
}
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
const loadDeptTree = async () => {
  try {
    const res = await getAllDept()
    deptTree.value = buildTree(res.data || [])
  } catch (e) { deptTree.value = [] }
}

/** 解析条件字段：优先内置变量 → 再查 formFieldList */
const resolveConditionField = (row) => {
  if (!row._condField) return null
  const bi = BUILTIN_VARS.find(v => v.fieldKey === row._condField)
  if (bi) return bi
  return formFieldList.value.find(f => f.fieldKey === row._condField) || null
}

/** 当前字段可用的运算符（内置变量 submitterDeptId 仅允许 ==、!=） */
const availableOps = (row) => {
  const f = row._condField
  if (builtinFieldKeys.includes(f)) return [{ label: '=', value: '==' }, { label: '!=', value: '!=' }]
  return [{ label: '=', value: '==' }, { label: '!=', value: '!=' }, { label: '>', value: '>' }, { label: '>=', value: '>=' }, { label: '<', value: '<' }, { label: '<=', value: '<=' }]
}

/** 根据 row._condField 找到字段对象（已兼容内置变量 + 表单字段） */
const getConditionField = (row) => resolveConditionField(row)

// ⭐ 金额字段实时校验 —— 仅提示，不拦截
/** 判断当前条件字段是否为金额（number型） */
const isAmountField = (row) => getConditionField(row)?.fieldType === 'number'
/** 校验字符串是否为合法数字（整数/负数/小数均可） */
const isValidNumber = (val) => {
  if (val === undefined || val === null || val === '') return true
  return /^-?\d+(\.\d+)?$/.test(val)
}

// ⭐ insertAt: 可选，指定插入到哪个 index 之后；不传则 push 到末尾
const addNode = (insertAt) => {
  const newId = Date.now() + Math.floor(Math.random() * 1000)
  const nodeTypes = ['start', 'userTask', 'gateway', 'end']
  const defaultType = nodeTypes[nodes.value.length === 0 ? 0 : (nodes.value.length === 1 ? 1 : Math.min(1, nodeTypes.length - 2))]
  const newNode = {
    id: -newId, nodeKey: 'node_' + newId,
    nodeName: '', nodeType: defaultType,
    assigneeType: defaultType === 'userTask' ? 'initiator' : '',
    assigneeValue: '',
    nextNodeIds: [], sortOrder: nodes.value.length + 1,
    approvalType: 'OR', timeoutHours: 72,
    conditionExpr: '', showDesc: '', _showDescManual: false,
    gatewayType: defaultType === 'gateway' ? 'exclusive' : ''
  }
  const initNode = initNodeTempFields(newNode)
  if (typeof insertAt === 'number' && insertAt >= 0 && insertAt <= nodes.value.length) {
    // 在中间/指定位置插入
    nodes.value.splice(insertAt, 0, initNode)

    // ⭐ 自动接管前后节点的 nextNodeIds 链路（网关分支不动，让用户手动配）
    const prev = nodes.value[insertAt - 1]  // 插入位置的前一个节点
    const next = nodes.value[insertAt + 1]  // 插入位置的后一个节点
    if (prev && prev.nodeType !== 'gateway' && !['start'].includes(prev.nodeType)) {
      // 普通审批节点/start：把 prev 的 nextNodeIds 里「原目标节点」替换成新节点
      if (prev.nextNodeIds && prev.nextNodeIds.length > 0) {
        // 先把 prev 原来的 next 存下来
        const oldNextIds = [...prev.nextNodeIds]
        // prev → 新节点
        prev.nextNodeIds = [String(initNode.id)]
        // 新节点继承 prev 原来指向的目标
        initNode.nextNodeIds = oldNextIds
      } else if (next && next.nodeType !== 'end') {
        // prev 没配 next，但下一个节点存在 → 默认串起来
        prev.nextNodeIds = [String(initNode.id)]
        initNode.nextNodeIds = [String(next.id)]
      } else if (next && next.nodeType === 'end') {
        prev.nextNodeIds = [String(initNode.id)]
        initNode.nextNodeIds = [String(next.id)]
      }
    }
  } else {
    nodes.value.push(initNode)
  }
}

const removeNode = (index) => {
  const removed = nodes.value.splice(index, 1)[0]
  // 连带把其他节点的 nextNodeIds 里对该节点的引用清掉（用 id 匹配）
  for (const n of nodes.value) {
    if (!n.nextNodeIds) continue
    n.nextNodeIds = n.nextNodeIds.filter(nid => String(nid) !== String(removed.id))
  }
}

const moveUp = (i) => {
  if (i === 0) return
  const tmp = nodes.value[i - 1]
  nodes.value[i - 1] = nodes.value[i]
  nodes.value[i] = tmp
}
const moveDown = (i) => {
  if (i >= nodes.value.length - 1) return
  const tmp = nodes.value[i + 1]
  nodes.value[i + 1] = nodes.value[i]
  nodes.value[i] = tmp
}

// ========== 校验 ==========
const validate = () => {
  if (!definition.processName) { ElMessage.warning('请填写流程名称'); return false }
  if (definition.status === 1) {
    const userTaskCount = nodes.value.filter(n => n.nodeType === 'userTask').length
    if (nodes.value.length === 0) { ElMessage.warning('请先配置节点'); return false }
    if (userTaskCount === 0) { ElMessage.warning('至少要有一个审批节点(userTask)'); return false }
  }
  for (const n of nodes.value) {
    if (!n.nodeName) { ElMessage.warning('节点名称不能为空'); return false }
    if (n.nodeType === 'end') { n.nextNodeIds = []; continue }
    // ⭐ 网关类型专属 nextNodeIds 约束（防止 BPMN 预览混乱）
    if (n.nodeType === 'gateway') {
      const gt = n.gatewayType || 'exclusive'
      const nextCount = Array.isArray(n.nextNodeIds) ? n.nextNodeIds.length : 0
      if (gt === 'exclusiveJoin') {
        // 排他汇聚网关：必须且只能有 1 条出边（汇聚后直通下游）
        if (nextCount === 0) { ElMessage.warning(`网关「${n.nodeName}」(排他汇聚) 必须配置 1 个下一节点`); return false }
        if (nextCount > 1)  { ElMessage.warning(`网关「${n.nodeName}」(排他汇聚) 只能有 1 个下一节点，当前配了 ${nextCount} 个`); return false }
      } else {
        // 排他分流 / 并行网关：至少 1 条出边
        if (nextCount === 0) { ElMessage.warning(`网关「${n.nodeName}」必须至少配置 1 个下一节点`); return false }
      }
    }
  }
  return true
}

// ========== 保存 ==========
const handleSave = async () => {
  if (!validate()) return
  try {
    // ⭐ 显式组装要传给后端的节点对象（去掉 _ 开头的临时字段，显式同步 assigneeValue）
    // ⭐⭐ 关键：sortOrder 按数组实际位置重新编号，不再信任旧值（新增/删除/移动后 sortOrder 会跳号）
    const cleanNodes = nodes.value.map((n, i) => {
      let assigneeValue = n.assigneeValue || ''
      if (n.assigneeType && n.assigneeType !== 'initiator' && n._assigneeValueList) {
        assigneeValue = n._assigneeValueList.join(',')
      }
      let nextNodeIds = Array.isArray(n.nextNodeIds) ? n.nextNodeIds.join(',') : (n.nextNodeIds || '')
      const isGateway = n.nodeType === 'gateway'
      return {
        id: n.id, nodeKey: n.nodeKey, nodeName: n.nodeName, nodeType: n.nodeType,
        assigneeType: isGateway ? '' : n.assigneeType,
        assigneeValue: isGateway ? '' : assigneeValue,
        nextNodeIds, approvalType: n.approvalType, timeoutHours: n.timeoutHours,
        sortOrder: i + 1,
        conditionExpr: isGateway ? '' : (n.conditionExpr || ''),
        showDesc: isGateway ? '' : (n.showDesc || ''),
        gatewayType: isGateway ? (n.gatewayType || 'exclusive') : ''
      }
    })

    // ⭐⭐ 版本编辑模式：只保存到版本快照，不碰主表/其他版本
    if (action === 'version-edit' && versionId) {
      // 组装 flow_json（和后端 buildFlowJson 一致的结构）
      const snapshot = {
        definition: {
          processName: definition.processName,
          processKey: definition.processKey,
          category: definition.category,
          formDefinitionId: definition.formDefinitionId,
          businessType: definition.businessType,
          description: definition.description
        },
        processXml: definition.processXml,
        nodes: cleanNodes
      }
      await saveVersionSnapshot(versionId, JSON.stringify(snapshot))
      ElMessage.success('版本快照保存成功')
      return
    }

    console.log('[Workflow Designer] save-all payload:', JSON.stringify({ nodes: cleanNodes }, null, 2))

    const payload = {
      definition: { ...definition },
      nodes: cleanNodes
    }
    const res = await saveWorkflowAll(payload)
    const newId = res.data
    if (definition.id == null) definition.id = newId
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
    console.error(e)
  }
}

// ========== 发布 ==========
const handlePublish = async () => {
  await handleSave()
  try {
    await publishWorkflowDefinition(definition.id)
    definition.status = 1
    ElMessage.success('发布成功')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '发布失败')
  }
}

// ========== BPMN 只读预览（⭐ 防重复创建 + 动态 destroy） ==========
const openPreview = async () => {
  if (!definition.id) { ElMessage.warning('请先保存后再预览'); return }
  previewVisible.value = true
  await nextTick()
  try {
    // ⭐ 先销毁旧实例 + 清空 DOM 里残留的 svg
    if (bpmnViewer) { bpmnViewer.destroy(); bpmnViewer = null }
    const container = document.getElementById('bpmn-canvas')
    if (container) container.innerHTML = ''

    // ⭐⭐ 版本模式：从版本快照实时生成 BPMN（保证是当前版本的节点结构）
    // 非版本模式：走原后端 getBpmnXml 从主表生成
    let xml = null
    if (isVersionMode.value && versionId) {
      const bpmnRes = await getVersionBpmn(versionId)
      xml = bpmnRes.data
    } else {
      const res = await getBpmnXml(definition.id)
      xml = res.data
    }
    if (!xml) { ElMessage.error('BPMN XML 为空，无法预览'); previewVisible.value = false; return }

    bpmnViewer = markRaw(new BpmnViewer({ container: '#bpmn-canvas' }))
    await bpmnViewer.importXML(xml)
    await nextTick()          // ⭐ 确保 DOM 渲染完
    paintBpmnNodes()
    // 缩放可能触发重绘，放最后
    bpmnViewer.get('canvas').zoom('fit-viewport')
    // 缩放后再补色（fit-viewport 有时会重绘）
    paintBpmnNodes()
    // offsetFlowLabels() 暂时注释，以后精细调整再启用
    _bindCanvasPanDown()  // ⭐ 绑定手型拖动画布
  } catch (e) { console.error(e); ElMessage.error('BPMN 渲染失败: ' + (e?.response?.data?.msg || e.message)) }
}
// ⭐ 弹窗关闭时也销毁，避免叠加
const handlePreviewClose = () => {
  togglePanMode(false)  // ⭐ 退出 pan 模式 + 移除快捷键监听
  _unbindCanvasPanDown()
  if (bpmnViewer) { bpmnViewer.destroy(); bpmnViewer = null }
  const container = document.getElementById('bpmn-canvas')
  if (container) container.innerHTML = ''
}

// ========== ⭐ BPMN 缩放 / 适屏 ==========
// ⭐ 根据节点类型给 BPMN 形状上色（用 elementRegistry 官方 API，v18 稳定）
const paintBpmnNodes = () => {
  if (!bpmnViewer) return
  const registry = bpmnViewer.get('elementRegistry')
  if (!registry) return

  // ⭐ 只改 stroke（边框线条），不碰 fill（保留 bpmn-js 默认白底 + 图标）
  const COLOR_MAP = {
    'bpmn:StartEvent':        '#3b82f6',   // 🔵 开始 - 蓝线条
    'bpmn:EndEvent':          '#22c55e',   // 🟢 结束 - 绿线条
    'bpmn:ExclusiveGateway':  '#f97316',   // 🟠 网关 - 橙线条
    'bpmn:ParallelGateway':   '#f97316',
    'bpmn:InclusiveGateway':  '#f97316',
    'bpmn:EventBasedGateway': '#f97316',
    'bpmn:UserTask':          '#3b82f6',
    'bpmn:ServiceTask':       '#22c55e',
    'bpmn:CallActivity':      '#f59e0b',
    'bpmn:SubProcess':        '#a855f7',
    'bpmn:AdHocSubProcess':   '#a855f7',
  }

  // 🔍 诊断：打印 registry 里所有 element 的 type
  const allTypes = []
  registry.forEach(el => allTypes.push({ id: el.id, type: el.type, isShape: !!el.businessObject }))
  console.log('[paintBpmnNodes] registry elements:', allTypes)

  let painted = 0
  registry.forEach(el => {
    // 跳过连线（flow）、label、group（bpmn-js 内部容器）
    if (el.type === 'label') return
    if (el.type && el.type.includes('Connection')) return
    // 只上色我们有颜色映射的节点
    const strokeColor = COLOR_MAP[el.type]
    if (!strokeColor) return

    const svgGroup = registry.getGraphics(el)
    if (!svgGroup) return

    const shapes = svgGroup.querySelectorAll('circle, polygon, rect, path, ellipse')
    if (shapes.length === 0) return
    shapes.forEach(s => {
      const tag = s.tagName.toLowerCase()
      if (tag === 'text' || tag === 'tspan') return
      // ⭐ 只改 stroke（边框），不碰 fill！保留原白底 + bpmn-js 图标
      s.setAttribute('stroke', strokeColor)
      s.setAttribute('stroke-width', '2.5')
      s.style.stroke = strokeColor
      s.style.strokeWidth = '2.5'
    })
    painted++
  })
  console.log('[paintBpmnNodes] ✅ 已上色节点数:', painted)
}

/**
 * ⭐ 给 sequenceFlow 的 label（连线文字）加偏移，避开线条本身
 * bpmn-js 默认 label 居中沿线条方向，容易覆盖线条或交叉线
 * 策略：
 *  - 水平线 (waypoint 差不多 y 相同) → label 向下偏移 14px
 *  - 垂直线 (waypoint 差不多 x 相同) → label 向右偏移 14px
 *  - 折线/斜线 → 找到最长方向，按那个方向偏移
 */
const offsetFlowLabels = () => {
  if (!bpmnViewer) return
  const registry = bpmnViewer.get('elementRegistry')
  if (!registry) return

  let adjusted = 0
  registry.forEach(el => {
    // 只处理连线的 label
    if (el.type !== 'label') return
    if (el.businessObject?.$type !== 'bpmn:SequenceFlow') return

    const labelGroup = registry.getGraphics(el)
    if (!labelGroup) return

    // 直接找 text/tspan 元素，加 dy 属性（SVG 标准，bpmn-js 不会覆盖）
    const textEls = labelGroup.querySelectorAll('text, tspan')
    textEls.forEach(t => {
      // dy 控制文字相对基线的偏移：正值 = 向下
      const existingDy = t.getAttribute('dy') || '0'
      t.setAttribute('dy', `${parseFloat(existingDy) + 14}`)
      // 同时加个白色背景矩形？不用，先看偏移效果
    })

    // 再给 label group 也加个整体 translate（双保险）
    const origTransform = labelGroup.getAttribute('transform') || ''
    if (!origTransform.includes('translate(0, 14)')) {
      labelGroup.setAttribute('transform', origTransform + ' translate(0, 14)')
    }

    adjusted++
  })
  console.log('[offsetFlowLabels] ✅ 已调整连线文字数:', adjusted)
}

const bpmnZoom = (delta) => {
  if (!bpmnViewer) return
  const canvas = bpmnViewer.get('canvas')
  const current = canvas.zoom()
  const target = Math.max(0.1, Math.min(5, current + delta))
  canvas.zoom(target, 'center')
}
const bpmnFit = () => {
  if (!bpmnViewer) return
  bpmnViewer.get('canvas').zoom('fit-viewport', 'center')
}

// ========== ⭐ BPMN 手型拖动画布 ==========
const isPanMode = ref(false)
let _panStart = null // { screenX, screenY, vbX, vbY }

const togglePanMode = (force) => {
  const next = typeof force === 'boolean' ? force : !isPanMode.value
  isPanMode.value = next
  // Esc / H 全局监听（只在弹窗打开时生效）
  if (next) {
    document.addEventListener('keydown', _panKeyHandler)
  } else {
    document.removeEventListener('keydown', _panKeyHandler)
  }
}
const _panKeyHandler = (e) => {
  if (e.key === 'Escape' || e.key === 'h' || e.key === 'H') {
    togglePanMode(false)
  }
}

// 挂在 window 上（因为 bpmn-js 的 SVG 内部拦截了事件，window 监听更稳）
onMounted(() => {
  window.addEventListener('mousemove', _onPanMove)
  window.addEventListener('mouseup', _onPanUp)
})
onBeforeUnmount(() => {
  window.removeEventListener('mousemove', _onPanMove)
  window.removeEventListener('mouseup', _onPanUp)
  document.removeEventListener('keydown', _panKeyHandler)
})

const _onPanDown = (e) => {
  if (!isPanMode.value || !bpmnViewer) return
  const canvas = bpmnViewer.get('canvas')
  const vb = canvas.viewbox()
  if (!vb) return
  _panStart = { screenX: e.clientX, screenY: e.clientY, vbX: vb.x, vbY: vb.y }
  e.preventDefault()
}
const _onPanMove = (e) => {
  if (!_panStart || !bpmnViewer) return
  const canvas = bpmnViewer.get('canvas')
  const container = document.getElementById('bpmn-canvas')
  if (!container) return

  // ⭐ 屏幕像素位移 → viewBox 坐标位移（要除以当前 zoom 比例）
  const rect = container.getBoundingClientRect()
  const vb = canvas.viewbox()
  const zoom = vb.width / rect.width  // viewBox 宽 / DOM 宽 = 1 屏幕像素对应多少 viewBox 单位

  const dx = (e.clientX - _panStart.screenX) * zoom
  const dy = (e.clientY - _panStart.screenY) * zoom

  canvas.viewbox({ x: _panStart.vbX - dx, y: _panStart.vbY - dy, width: vb.width, height: vb.height })
}
const _onPanUp = () => {
  _panStart = null
}

// 在 canvas 上绑定 mousedown（pan 模式下才触发）
const _bindCanvasPanDown = () => {
  const el = document.getElementById('bpmn-canvas')
  if (!el) return
  el.addEventListener('mousedown', _onPanDown)
}
const _unbindCanvasPanDown = () => {
  const el = document.getElementById('bpmn-canvas')
  if (!el) return
  el.removeEventListener('mousedown', _onPanDown)
}

// ========== ⭐ BPMN 导出（SVG / JPG / PDF） ==========
const exportBpmn = async (format) => {
  if (!bpmnViewer) { ElMessage.warning('请先加载 BPMN'); return }
  const container = document.getElementById('bpmn-canvas')
  const svgEl = container?.querySelector('svg')
  if (!svgEl) { ElMessage.error('BPMN SVG 未找到'); return }

  const fileName = (definition.processName || 'workflow') + '_bpmn'

  // ⭐ 关键：从 bpmn-js canvas 拿到「真实画布尺寸」(viewBox 坐标系)
  // bpmn-js fit-viewport 后的 viewBox 就是完整流程图的边界框
  let vbW = 1200, vbH = 800
  try {
    const vb = bpmnViewer.get('canvas').viewbox()
    // vb = { x, y, width, height }
    if (vb && vb.width > 0 && vb.height > 0) {
      vbW = Math.ceil(vb.width)
      vbH = Math.ceil(vb.height)
    }
  } catch (e) { console.warn('无法获取 canvas viewbox，用默认尺寸', e) }

  // ⭐ 克隆 SVG → 强制 width/height = viewBox 真实尺寸（修导出残缺的核心！）
  const serializer = new XMLSerializer()
  const clone = svgEl.cloneNode(true)
  if (!clone.getAttribute('xmlns')) clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
  if (!clone.getAttribute('xmlns:xlink')) clone.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink')
  // 清空可能影响渲染的宽高 → 写死为画布真实尺寸
  clone.setAttribute('width', String(vbW))
  clone.setAttribute('height', String(vbH))
  // 确保 viewBox 也是同样范围（bpmn-js 的 viewBox 是 {x,y,w,h} 格式，要转成 "x y w h" 字符串）
  try {
    const vb = bpmnViewer.get('canvas').viewbox()
    clone.setAttribute('viewBox', `${vb.x} ${vb.y} ${vb.width} ${vb.height}`)
  } catch (e) {}
  const svgString = serializer.serializeToString(clone)

  if (format === 'svg') {
    const blob = new Blob([svgString], { type: 'image/svg+xml;charset=utf-8' })
    downloadBlob(blob, fileName + '.svg')
    ElMessage.success('SVG 导出成功')
    return
  }

  // JPG / PDF：SVG → Image → Canvas
  const blob = new Blob([svgString], { type: 'image/svg+xml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  try {
    const img = await loadImage(url)
    // ⭐ 用 viewBox 真实尺寸（不是 img.naturalWidth！）
    const scale = 2
    const canvas = document.createElement('canvas')
    canvas.width = vbW * scale
    canvas.height = vbH * scale
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    // ⭐ drawImage：用 viewBox 覆盖坐标（从 vb.x/vb.y 开始），完整绘制
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height)

    if (format === 'jpg') {
      canvas.toBlob(b => {
        downloadBlob(b, fileName + '.jpg')
        ElMessage.success('JPG 导出成功')
      }, 'image/jpeg', 0.95)
    } else if (format === 'pdf') {
      const dataUrl = canvas.toDataURL('image/jpeg', 0.95)
      const w = window.open('', '_blank')
      if (!w) { ElMessage.error('弹窗被浏览器拦截，请允许弹窗权限'); return }
      w.document.write(`<html><head><title>${fileName}</title><style>
        @page { size: ${vbW}px ${vbH}px; margin: 0; }
        body { margin: 0; display: flex; justify-content: center; align-items: center; }
        img { width: ${vbW}px; height: ${vbH}px; }
      </style></head><body><img src="${dataUrl}" onload="setTimeout(()=>window.print(),300)" /></body></html>`)
      w.document.close()
      ElMessage.info('请在弹出窗口中按 Ctrl+P → 选择「另存为 PDF」')
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('导出失败：' + e.message)
  } finally {
    URL.revokeObjectURL(url)
  }
}

const loadImage = (src) => new Promise((resolve, reject) => {
  const img = new Image()
  img.crossOrigin = 'anonymous'
  img.onload = () => resolve(img)
  img.onerror = (e) => reject(e)
  img.src = src
})

const downloadBlob = (blob, name) => {
  const a = document.createElement('a')
  const url = URL.createObjectURL(blob)
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

// ========== ⭐ 租户分发相关函数 ==========
const searchTenant = async (kw) => {
  tenantSearchLoading.value = true
  try {
    // ✅ 修复：后端真实接口是 SysTenantController 的 /system/tenant/list
    // 参数名 tenantName（后端用 like 模糊匹配），keyword 是之前写错的
    const res = await request({ url: '/system/tenant/list', method: 'get', params: { pageNum: 1, pageSize: 50, tenantName: kw || '' } })
    const raw = res.data?.records || res.data || []
    tenantOptions.value = raw.map(t => ({ id: t.id, tenantName: t.tenantName || t.name || t.label }))
  } catch (e) { console.warn('搜索租户失败', e) } finally { tenantSearchLoading.value = false }
}

const openTenantPublish = async () => {
  tenantPublishForm.tenantIds = []
  tenantPublishForm.allowEdit = false
  tenantPublishVisible.value = true
  await searchTenant('')
}

const confirmPublishToTenants = async () => {
  try {
    await publishToTenants({
      definitionId: definition.id,
      tenantIds: tenantPublishForm.tenantIds,
      allowEdit: tenantPublishForm.allowEdit
    })
    ElMessage.success('已发布到选中租户')
    tenantPublishVisible.value = false
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '发布失败') }
}

const openTenantList = async () => {
  tenantListVisible.value = true
  try {
    // 1. 拉分发记录
    const distRes = await getTenantDistribution(definition.id)
    tenantDistList.value = distRes.data || []
    // 2. 同时拉租户全量列表，建立 id → name 映射
    try {
      const tenantRes = await request({ url: '/system/tenant/all', method: 'get' })
      const map = {}
      ;(tenantRes.data || []).forEach(t => { map[t.id] = t.tenantName })
      tenantNameMap.value = map
    } catch (e) {
      console.warn('加载租户名称映射失败', e)
    }
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '查询失败') }
}

const handleRevoke = async (row) => {
  try {
    await ElMessageBox.confirm(`确定撤回租户 #${row.tenantId} 的分发？`, '提示', { type: 'warning' })
    await revokeTenantDistribution({ definitionId: definition.id, tenantId: row.tenantId })
    ElMessage.success('已撤回')
    openTenantList() // 刷新
  } catch (e) { if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || '撤回失败') }
}
// ========== ⭐ 关联表单 + 字段元数据加载（Workflow Designer 条件下拉用） ==========
const loadFormDefOptions = async () => {
  try {
    const res = await getFormDefAllActive()
    formDefOptions.value = res.data || []
  } catch (e) { console.warn('加载表单定义下拉失败', e) }
}

/**
 * ⭐ 拿字段的下拉选项数组 [{label, value}] —— 同步函数，模板里可用
 * 来源优先级：① options 本地选项 JSON ② dictTypeCode 字典数据（已预加载缓存）
 */
const getFieldOptions = (field) => {
  if (!field) return []

  // ① 本地 options 优先
  if (field.options) {
    try {
      let opts = typeof field.options === 'string' ? JSON.parse(field.options) : field.options
      if (Array.isArray(opts)) {
        return opts.map(o => ({
          label: o.label ?? o.text ?? String(o.value ?? ''),
          value: String(o.value ?? o.id ?? o.key ?? '')
        })).filter(x => x.value !== '')
      } else if (typeof opts === 'object') {
        return Object.entries(opts).map(([k, v]) => ({ label: String(v), value: String(k) }))
      }
    } catch (e) { /* ignore */ }
  }

  // ② 字典数据（已预加载到 dictCache）
  if (field.dictTypeCode && dictCache.value[field.dictTypeCode]) {
    return dictCache.value[field.dictTypeCode]
  }

  return []
}

/**
 * 预加载所有字典数据（formFieldList 加载完后调用）
 */
const preloadDictOptions = async () => {
  const dictCodes = [...new Set(formFieldList.value.map(f => f.dictTypeCode).filter(Boolean))]
  if (dictCodes.length === 0) return
  for (const code of dictCodes) {
    if (dictCache.value[code]) continue  // 已缓存
    try {
      const res = await getDictDataByType(code)
      const list = (res?.data || []).map(d => ({
        label: d.dictLabel ?? d.label ?? String(d.dictValue),
        value: String(d.dictValue ?? d.value)
      }))
      dictCache.value[code] = list
    } catch (e) { /* ignore */ }
  }
}

const loadFormFields = async () => {
  if (!definition.formDefinitionId) { formFieldList.value = []; dictCache.value = {}; return }
  formFieldLoading.value = true
  try {
    const res = await getFormFieldList(definition.formDefinitionId)
    formFieldList.value = res.data || []
    // ⭐ 字段加载完后预加载字典选项缓存
    await preloadDictOptions()
  } catch (e) {
    console.error('加载表单字段失败', e)
    ElMessage.error('加载表单字段失败')
  } finally {
    formFieldLoading.value = false
  }
}

// definition.formDefinitionId 变化 → 自动加载字段
watch(() => definition.formDefinitionId, () => {
  if (isReadonly.value) return
  loadFormFields()
})

const openFieldSelector = async (row) => {
  // 前置校验：必须先选关联表单
  if (!definition.formDefinitionId) {
    ElMessage.warning('请先在左侧「关联表单」选择一个表单')
    return
  }
  // 每次点开都重新拉一次最新字段（防止用户改了表设计器后没刷新）
  await loadFormFields()
  formFieldSelectorVisible.value = true
  // 记录当前操作的是哪一行（conditionExpr 所在节点）
  formFieldSelectorVisible._targetRow = row
}

const fieldTypeTagType = (ft) => ({
  text: '', textarea: '', number: 'success', date: 'warning',
  select: 'warning', radio: 'warning', checkbox: 'warning', file: 'info'
}[ft] || '')

const insertFieldSpEL = async (field) => {
  const row = formFieldSelectorVisible._targetRow
  if (!row) return

  // 生成 SpEL 片段：优先用 fieldKey（业务 submit 时传的就是 key）
  // ⭐ 注意：不再加 // 中文注释 —— SpEL 解析器对非 ASCII 注释字符有 bug
  let spel = field.fieldKey

  // 如果字段关联了字典，把字典选项也打印出来方便用户写条件（直接显示在弹窗里，不写进 SpEL）
  if (field.dictTypeCode) {
    try {
      const dictRes = await getDictDataByType(field.dictTypeCode)
      const opts = (dictRes.data || []).map(d => `${d.dictLabel}(${d.dictValue})`).join(', ')
      ElMessage.info(`${field.fieldName} 字典选项: ${opts}`)
    } catch (e) { /* ignore */ }
    spel = `${field.fieldKey} == '值'`
  } else if (field.fieldType === 'number') {
    spel = `${field.fieldKey} > 0`
  } else if (field.fieldType === 'date') {
    spel = `${field.fieldKey} != null`
  } else {
    spel = `${field.fieldKey} == '值'`
  }

  row.conditionExpr = spel
  formFieldSelectorVisible.value = false
  ElMessage.success('已插入到分支条件')
}

const handleBack = () => router.push('/system/wf/definition')

/** ⭐ 面板 CSS 模拟全屏切换 */
const togglePanelFullscreen = () => {
  isPanelFullscreen.value = !isPanelFullscreen.value
}

/** ⭐ ESC 退出模拟全屏 */
const onEscExitFullscreen = (e) => {
  if (e.key === 'Escape' && isPanelFullscreen.value) {
    isPanelFullscreen.value = false
  }
}

onBeforeUnmount(() => {
  if (bpmnViewer) bpmnViewer.destroy()
})
</script>

<style scoped>
.workflow-designer-container { height: 100vh; display: flex; flex-direction: column; background: #f5f7fa; }
.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; flex-shrink: 0;
}
.toolbar-left { display: flex; gap: 10px; align-items: center; }
.toolbar-right { display: flex; gap: 8px; }
.version-tag { color: #999; font-size: 13px; }
.content-wrap { flex: 1; display: flex; gap: 12px; padding: 12px; overflow: auto; }
.panel { background: #fff; }
.left-panel { width: 320px; flex-shrink: 0; height: fit-content; position: sticky; top: 12px; }
.right-panel { flex: 1; }
.panel-header { display: flex; justify-content: space-between; align-items: center; }

/* ⭐ 全屏按钮 & header actions */
.panel-header-actions { display: flex; align-items: center; gap: 4px; }
.btn-fullscreen { padding: 4px 8px; margin-right: 4px; }
.fs-icon { font-size: 16px; font-weight: bold; line-height: 1; }

/* ⭐ CSS 模拟全屏：fixed + inset:0 铺满视口 */
.right-panel.is-fullscreen {
  position: fixed !important;
  top: 0; left: 0; right: 0; bottom: 0;
  width: auto !important;
  height: auto !important;
  margin: 0;
  border-radius: 0;
  z-index: 100;  /* ⭐ 低于全局 Element Plus 弹出层 z-index，下拉能正常弹出 */
  background: #fff;
  display: flex !important;
  flex-direction: column;
}
.right-panel.is-fullscreen :deep(.el-card__body) {
  flex: 1;
  overflow: auto;
}
.form-tip { color: #999; font-size: 12px; margin-top: 4px; }
.sort-btns { display: flex; flex-direction: column; gap: 0; }
.hint { margin-top: 12px; padding: 10px 12px; background: #fdf6ec; border-radius: 4px; color: #8a6d3b; font-size: 13px; }
.hint code { background: #eee; padding: 0 4px; border-radius: 2px; }
.muted { color: #c0c4cc; }
/* ⭐ 让节点类型列的 cell 支持 absolute 定位 */
.el-table .col-node-type .cell { position: relative; }
/* ⭐ 用一个容器统一 left 位置，里面的 + 和 − 自然垂直对齐 */
.col-edge-btns {
  position: absolute;
  left: 6px;          /* ← 统一在这里调 left，+ 和 − 一起动 */
  top: 0;
  bottom: 0;
  width: 16px;
  pointer-events: none; /* 容器不阻挡点击，只让按钮点 */
}
.col-edge-btns .el-button { pointer-events: auto; }
.btn-insert-row {
  position: absolute;
  left: 0;
  bottom: -8px;
  z-index: 5;
  padding: 0 4px !important;
  font-weight: bold;
}
.btn-delete-row {
  position: absolute;
  left: 0;
  top: -8px;
  z-index: 5;
  padding: 0 4px !important;
  font-weight: bold;
}
.preview-wrapper { position: relative; z-index: 1; }
/* ⭐ 金额字段警告图标：橙色，跟随输入框显示 */
.amount-warn-icon {
  color: #e6a23c;
  flex-shrink: 0;
  cursor: default;
}
.preview-toolbar {
  position: absolute; top: 8px; right: 12px; z-index: 10;
  display: flex; gap: 4px; align-items: center;
  background: #fff; padding: 4px 6px; border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.12);
}
/* ⭐ 手型模式：光标变抓握 */
.bpmn-canvas-wrap.pan-mode,
.bpmn-canvas-wrap.pan-mode * {
  cursor: grab !important;
}
.bpmn-canvas-wrap.pan-mode:active,
.bpmn-canvas-wrap.pan-mode:active * {
  cursor: grabbing !important;
}
/* 手型按钮激活态 */
.preview-toolbar .el-button.is-active {
  background: #409eff !important;
  border-color: #409eff !important;
  color: #fff !important;
}
</style>
<style>.bjs-container { height: 100% !important; }</style>

<!-- ⭐ 全局：所有 Element Plus 弹出层强制 z-index 到顶 -->
<style>
.el-popper,
.el-select-dropdown,
.el-cascader-panel,
.el-picker-panel,
.el-date-table,
.el-time-panel {
  z-index: 100000 !important;
}
</style>

<!-- ⭐ 全局：所有 Element Plus 弹出层强制 z-index 到顶 -->
<style>
.el-popper,
.el-select-dropdown,
.el-cascader-panel,
.el-picker-panel,
.el-date-table,
.el-time-panel {
  z-index: 100000 !important;
}
</style>