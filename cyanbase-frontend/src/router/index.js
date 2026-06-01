import { createRouter, createWebHistory } from 'vue-router'
import storage from '@/utils/storage'


// 路由白名单
const whiteList = ['/login']

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/home',
    children: [
      // 首页
      {
        path: '/home',
        name: 'Home',
        component: () => import('@/views/home/index.vue'),
        meta: { title: '首页' }
      },
      // ===== 系统管理 =====
      // 用户管理
      {
        path: '/system/user',
        name: 'User',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: '用户管理' }
      },
      // 部门管理
      {
        path: '/system/dept',
        name: 'Dept',
        component: () => import('@/views/system/dept/index.vue'),
        meta: { title: '部门管理' }
      },
      // 角色管理
      {
        path: '/system/role',
        name: 'Role',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: '角色管理' }
      },
      // 菜单管理
      {
        path: '/system/menu',
        name: 'Menu',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { title: '菜单管理' }
      },
      // 岗位管理
      {
        path: '/system/post',
        name: 'Post',
        component: () => import('@/views/system/post/index.vue'),
        meta: { title: '岗位管理' }
      },
      // 字典类型
      {
        path: '/system/dict/type',
        name: 'DictType',
        component: () => import('@/views/system/dict/type/index.vue'),
        meta: { title: '字典类型' }
      },
      // 字典数据
      {
        path: '/system/dict/data',
        name: 'DictData',
        component: () => import('@/views/system/dict/data/index.vue'),
        meta: { title: '字典数据' }
      },
      // 参数配置
      {
        path: '/system/config',
        name: 'Config',
        component: () => import('@/views/system/config/index.vue'),
        meta: { title: '参数配置' }
      },
      // 通知公告
      {
        path: '/system/notice',
        name: 'Notice',
        component: () => import('@/views/system/notice/index.vue'),
        meta: { title: '通知公告' }
      },
      // 文件管理
      {
        path: '/system/file',
        name: 'File',
        component: () => import('@/views/system/file/index.vue'),
        meta: { title: '文件管理' }
      },
      // 消息管理
      {
        path: '/system/message',
        name: 'Message',
        component: () => import('@/views/system/message/index.vue'),
        meta: { title: '消息管理' }
      },
      // 代码生成
      {
        path: '/system/gen',
        name: 'GenTable',
        component: () => import('@/views/system/gen/index.vue'),
        meta: { title: '代码生成' }
      },
      // 流程定义
      {
        path: '/system/wf/definition',
        name: 'WfDefinition',
        component: () => import('@/views/system/wfDefinition/index.vue'),
        meta: { title: '流程定义' }
      },
      // 表单设计
      {
        path: '/system/form',
        name: 'SysForm',
        component: () => import('@/views/system/sysForm/index.vue'),
        meta: { title: '表单设计' }
      },
      // 第三方配置
      {
        path: '/system/third/config',
        name: 'ThirdConfig',
        component: () => import('@/views/system/thirdConfig/index.vue'),
        meta: { title: '第三方配置' }
      },
      // ===== 系统监控 =====
      // 操作日志
      {
        path: '/monitor/operLog',
        name: 'OperLog',
        component: () => import('@/views/monitor/operLog/index.vue'),
        meta: { title: '操作日志' }
      },
      // 登录日志
      {
        path: '/monitor/loginLog',
        name: 'LoginLog',
        component: () => import('@/views/monitor/loginLog/index.vue'),
        meta: { title: '登录日志' }
      },
      // 在线用户
      {
        path: '/monitor/online',
        name: 'Online',
        component: () => import('@/views/monitor/online/index.vue'),
        meta: { title: '在线用户' }
      },
      // 定时任务
      {
        path: '/monitor/job',
        name: 'Job',
        component: () => import('@/views/monitor/job/index.vue'),
        meta: { title: '定时任务' }
      },
      // 任务日志
      {
        path: '/monitor/jobLog',
        name: 'JobLog',
        component: () => import('@/views/monitor/jobLog/index.vue'),
        meta: { title: '任务日志' }
      },
      // 服务监控（阶段八）
      {
        path: '/monitor/server',
        name: 'ServerMonitor',
        component: () => import('@/views/monitor/server/index.vue'),
        meta: { title: '服务监控' }
      },
      // ===== 安全防护 =====
      // 限流规则
      {
        path: '/security/rateLimit',
        name: 'RateLimit',
        component: () => import('@/views/security/rateLimit/index.vue'),
        meta: { title: '限流规则' }
      },
      // XSS防护
      {
        path: '/security/xss',
        name: 'XssConfig',
        component: () => import('@/views/security/xss/index.vue'),
        meta: { title: 'XSS防护' }
      },
      // ===== 站内消息 =====
      // 我的消息
      {
        path: '/message/myMessage',
        name: 'MyMessage',
        component: () => import('@/views/message/myMessage/index.vue'),
        meta: { title: '我的消息' }
      },
      // ===== 工作流 =====
      {
        path: '/workflow/designer',
        name: 'WorkflowDesigner',
        component: () => import('@/views/workflow/designer/index.vue'),
        meta: { title: '流程设计器' }
      },
      // 我的待办
      {
        path: '/workflow/todo',
        name: 'WorkflowTodo',
        component: () => import('@/views/workflow/todo/index.vue'),
        meta: { title: '我的待办' }
      },
      // 我的已办
      {
        path: '/workflow/done',
        name: 'WorkflowDone',
        component: () => import('@/views/workflow/done/index.vue'),
        meta: { title: '我的已办' }
      },
      // 发起流程
      {
        path: '/workflow/start',
        name: 'WorkflowStart',
        component: () => import('@/views/workflow/start/index.vue'),
        meta: { title: '发起流程' }
      },
      // 审批页面
      {
        path: '/workflow/approve',
        name: 'WorkflowApprove',
        component: () => import('@/views/workflow/approve/index.vue'),
        meta: { title: '审批页面' }
      },
      // ===== 自定义表单 =====
      // 表单提交
      {
        path: '/form/submit',
        name: 'FormSubmit',
        component: () => import('@/views/form/submit/index.vue'),
        meta: { title: '表单提交' }
      },
      // 表单数据列表
      {
        path: '/form/dataList',
        name: 'FormDataList',
        component: () => import('@/views/form/dataList/index.vue'),
        meta: { title: '表单数据' }
      },
      {
        path: '/form/designer',
        name: 'FormDesigner',
        component: () => import('@/views/form/designer/index.vue'),
        meta: { title: '表单可视化设计器' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const hasToken = !!storage.getToken()

  // 白名单路由直接放行
  if (whiteList.includes(to.path)) {
    // 已登录则跳转首页
    if (hasToken && to.path === '/login') {
      next('/home')
    } else {
      next()
    }
    return
  }

  // 需要认证的路由
  if (hasToken) {
    next()
  } else {
    next(`/login?redirect=${to.path}`)
  }
})

export default router