CyanBase 青渊数字底座 - Vue3 前端开发任务手册
（专供 VSCode AI 智能体执行，严格限定路径、技术栈、接口规范，禁止随意创建文件目录）
一、基础项目信息
项目名称：cyanbase-frontend
固定绝对路径：/home/xjgh2025XZD/Cyan/CyanBase/cyanbase-frontend
工作区根目录：VSCode 打开目录为 /home/xjgh2025XZD/Cyan
技术栈固定：Vue3 + Vite5 + Element Plus + Axios + Pinia + Vue Router4
后端服务基础地址：http://localhost:8090
编码格式：UTF-8，代码风格统一 ES6 规范
页面配色规范：整体采用稳重、严谨商务格调配色，主色调选用深蓝系，辅助色搭配浅灰、深灰，禁用鲜艳浮夸色彩，贴合政企管理系统视觉气质
二、目录结构强制规范（严格按此创建，不得增减层级）
plaintext
cyanbase-frontend
├── public
├── src
│   ├── api                # 接口请求分层
│   │   ├── auth.js        # 登录、鉴权、退出接口
│   │   ├── user.js        # 用户管理接口
│   │   ├── dept.js        # 部门管理接口
│   │   ├── role.js        # 角色管理接口
│   │   ├── menu.js        # 菜单管理接口
│   │   └── common.js      # 公共通用接口
│   ├── assets             # 静态资源、样式、图片
│   ├── components         # 全局公共组件
│   ├── layout             # 后台主布局框架
│   ├── router             # 路由配置
│   ├── store              # Pinia状态管理
│   ├── utils
│   │   ├── request.js     # Axios全局拦截封装
│   │   └── storage.js     # 本地存储工具
│   ├── views              # 页面视图文件
│   │   ├── login          # 登录页面
│   │   ├── system
│   │   │   ├── user       # 用户管理页
│   │   │   ├── dept       # 部门管理页
│   │   │   ├── role       # 角色管理页
│   │   │   └── menu       # 菜单管理页
│   │   └── monitor        # 日志监控页面
│   ├── App.vue
│   └── main.js
├── .env.development
├── package.json
└── vite.config.js
三、依赖版本固定要求（禁止 AI 自行升级修改版本）
json
{
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.2.0",
    "pinia": "^2.1.0",
    "axios": "^1.6.0",
    "element-plus": "^2.4.0",
    "@element-plus/icons-vue": "^2.3.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.0",
    "vite": "^5.0.0"
  }
}
四、全局核心配置规则

1. 请求头规范
   所有需鉴权接口请求头必须携带 Token
   plaintext
   Authorization: Bearer ${token值}
   Token 存储位置：浏览器 localStorage，键名cyan_token
2. 响应统一格式
   后端固定返回结构，前端统一解析
   json
   {
   "code":200,
   "msg":"提示文案",
   "data":{}
   }
   code=200：请求成功
   code=401：未登录 / 令牌失效，自动跳转登录页
   code=500：业务异常，弹窗提示错误信息
3. 路由守卫规则
   访问非登录页面，无 Token 强制跳转 /login
   登录页面已存在有效 Token，自动跳转首页
   路由白名单：仅 /login 页面无需鉴权
4. Axios 拦截器要求
   请求拦截：自动从本地读取 Token 并挂载请求头
   响应拦截：统一状态码判断、异常弹窗、401 登录跳转
   统一拼接后端基础请求地址
   五、接口对接明细（严格匹配后端接口路径与请求方式）
5. 鉴权模块
   表格
   请求方式    接口地址    功能    权限
   POST    /auth/login    账号密码登录，获取 Token 与用户信息    公开访问
   GET    /auth/getUserInfo    获取当前登录人信息    需 Token
   POST    /auth/logout    退出登录，清空本地 Token    需 Token
6. 系统管理模块
   表格
   请求方式    接口地址    功能
   GET    /system/user/list    用户分页列表
   GET    /system/user/{id}    用户详情查询
   POST    /system/user    新增用户
   PUT    /system/user    编辑用户
   DELETE    /system/user/{id}    删除用户
   GET    /system/dept/list    部门列表
   GET    /system/role/list    角色列表
   GET    /system/menu/list    菜单列表
   六、页面功能开发要求
   登录页
   输入框：用户名、密码，默认测试账号 admin/admin123
   登录按钮点击调用登录接口，成功存储 Token 并跳转首页
   账号密码错误后端提示文案前端弹窗展示
   页面色调契合稳重严谨商务风格
   后台主布局
   左侧固定侧边菜单栏、顶部导航栏、主体内容区
   侧边菜单根据后端返回菜单数据渲染
   右上角展示当前登录用户名，提供退出登录按钮
   整体色调沉稳大气，界面布局规整简洁
   用户管理页面
   分页表格展示用户数据，匹配后端分页返回字段
   支持新增、编辑、删除弹窗表单操作
   表单字段与数据库实体字段一一对应
   部门、角色、菜单页面
   列表查询、新增、修改、删除全套页面功能
   页面交互风格统一 Element Plus 默认样式
   色彩搭配保持统一严谨商务调性
   七、状态管理规范
   Pinia 创建用户仓库，存储登录用户信息、Token
   退出登录时清空仓库数据与本地存储 Token
   全局用户信息统一从仓库读取使用
   八、运行与编译命令
   安装依赖
   bash
   运行
   cd /home/xjgh2025XZD/Cyan/CyanBase/cyanbase-frontend
   npm install
   本地启动项目
   bash
   运行
   npm run dev
   打包部署
   bash
   运行
   npm run build
   九、AI 生成强制约束
   所有文件仅生成在固定前端路径内，禁止跨目录、禁止创建上级 / 无关文件夹
   严格遵循给定目录层级，包名、文件名不得随意命名修改
   接口地址、请求方式、请求头完全匹配后端规范，不得私自改动接口路径
   沿用后端统一返回逻辑，状态码判断逻辑固定不变
   不删减基础功能、不新增无关业务代码，保证前后端接口完全互通
   生成完成后可直接执行命令启动，无语法报错、无路径引用错误
   全程遵循稳重严谨商务配色要求，界面视觉风格统一协调
   十、交付要求
   完整生成目录下所有基础配置文件、页面、接口、工具类
   项目启动后可正常打开登录页，成功登录并跳转后台主页
   用户、部门、角色基础增删改查页面可正常调用后端接口
   路由守卫、Token 携带、异常跳转功能全部生效
   整体页面配色符合稳重严谨商务格调标准
