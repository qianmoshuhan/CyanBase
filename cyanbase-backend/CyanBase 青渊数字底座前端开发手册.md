CyanBase 青渊数字底座 —— 前端 AI 全自动开发手册
（直接复制发给前端 AI 即可）
一、前端技术栈
Vue 3 + Vite
Element Plus
路由 / 权限 / 菜单
API 接口对接后端
表格 + 查询 + 分页 + 弹窗表单 + 按钮权限
二、生成规则
严格按照 优先级 1 → 优先级 2 → 优先级 3 → 优先级 4 顺序生成页面
每个模块自动生成：
列表页面（index.vue）
API 接口文件（*.js）
增删改查
查询条件
分页
状态开关
按钮权限
目录结构自动对应后端接口
三、模块清单 + 接口路径（AI 自动识别）
🔥 优先级 1 —— 核心必备模块（必须先生成）

1. 岗位管理
   目录：views/system/post
   接口：/system/post/*
   功能：列表、新增、编辑、删除、状态、排序
2. 字典类型管理
   目录：views/system/dict/type
   接口：/system/dict/type/*
3. 字典数据管理
   目录：views/system/dict/data
   接口：/system/dict/data/*
4. 参数配置
   目录：views/system/config
   接口：/system/config/*
5. 公告管理
   目录：views/system/notice
   接口：/system/notice/*
   支持富文本
6. 在线用户
   目录：views/system/online
   接口：/system/online/*
   功能：强退用户
7. 定时任务
   目录：views/system/job
   接口：/system/job/*
   功能：立即执行、暂停、恢复
8. 定时任务日志
   目录：views/system/job/log
   接口：/system/job/log/*
   🔥 优先级 2 —— 业务高频模块
9. 文件管理
   目录：views/system/file
   接口：/system/file/*
10. 存储配置
    目录：views/system/storage
    接口：/system/storage/*
11. 消息管理
    目录：views/system/message
    接口：/system/message/*
12. 代码生成
    目录：views/system/gen
    接口：/system/gen/*
    🔥 优先级 3 —— 运维监控模块
13. 操作日志
    目录：views/system/operlog
    接口：/system/oper/log/*
14. 登录日志
    目录：views/system/loginlog
    接口：/system/login/log/*
15. 接口监控
    目录：views/system/apimonitor
    接口：/system/api/monitor/*
    🔥 优先级 4 —— 可选扩展模块
16. 工作流定义
    目录：views/system/workflow
    接口：/system/wf/definition/*
17. 表单设计
    目录：views/system/form
    接口：/system/form/*
18. 第三方配置
    目录：views/system/thirdconfig
    接口：/system/third/config/*
