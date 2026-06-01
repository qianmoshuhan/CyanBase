🔥 CyanBase 青渊底座 —— 前端开发终极任务手册（阶段 1~8 完整版）
（前端 AI 直接照做，100% 对接、风格统一、无猜测、无浪费 Credits）
一、项目基础信息（固定不变，禁止修改）
前端技术栈：Vue3 + Element Plus
后端地址：http://localhost:8090
请求方式：所有接口统一兼容 GET/POST，前端可任意使用，后端无报错
认证方式：请求头自动携带
js
headers: {
  Authorization: 'Bearer ' + token
}
统一返回格式
json
{ "code": 200, "msg": "成功", "data": 任何数据 }
分页规则：后端完全处理分页，前端只做展示
后端返回：records、total、pageNum、pageSize
前端只渲染列表 + 展示分页组件，不计算、不处理分页逻辑
前端路径：/home/xjgh2025XZD/Cyan/CyanBase/cyanbase-frontend
后端路径：/home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend
二、强制风格统一规范（必须 100% 遵守）

1. 页面结构（固定模板）
   vue
   
   <template>
   <!-- 顶部搜索区域 -->
   <div class="search-form">
    <el-form :model="query" inline label-width="100px">
      <el-form-item label="关键字">
        <el-input v-model="query.keyword" placeholder="请输入" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary">查询</el-button>
        <el-button>重置</el-button>
      </el-form-item>
    </el-form>
   </div>
   
   <!-- 顶部按钮区域 -->
   
   <div class="table-toolbar">
    <el-button type="success">新增</el-button>
    <el-button type="warning">编辑</el-button>
    <el-button type="danger">删除</el-button>
   </div>
   
   <!-- 表格区域 -->
   
   <el-table :data="list" border stripe highlight-current-row>
    <el-table-column label="ID" prop="id" />
    <el-table-column label="名称" prop="name" />
    <el-table-column label="时间" prop="createTime" />
   </el-table>
   
   <!-- 分页区域（只展示） -->
   
   <el-pagination
    :total="total"
    :current-page="pageNum"
    :page-size="pageSize"
    layout="total, sizes, prev, pager, next, jumper"
   />
   </template>

2. 按钮颜色规范（禁止自定义）
   查询：type="primary"
   新增：type="success"
   编辑：type="warning"
   删除：type="danger"
   重置 / 其他：默认

3. 弹窗规范
   组件：el-dialog
   宽度：80% ~ 90%
   表单项：label-width="100px"

4. 禁止行为
   不自定义 CSS
   不新增主题色
   不新增图标库
   不修改原有项目代码
   只新增文件，不覆盖、不删除、不改动
   三、阶段 1~8 必须开发的前端页面（全部一次性生成）
   模块 1：系统基础管理
   用户管理页面
   角色管理页面
   菜单权限管理页面
   字典管理页面
   模块 2：日志管理
   操作日志页面
   登录日志页面
   模块 3：文件服务
   文件管理列表（上传、预览、删除）
   模块 4：安全防护
   限流规则页面
   XSS 防护配置页
   模块 5：定时任务
   定时任务列表（新增、编辑、暂停、执行、日志）
   模块 6：站内消息
   我的消息列表
   全部已读、未读、详情
   模块 7：工作流
   流程定义管理
   我的待办
   我的已办
   发起流程
   审批页面（通过 / 驳回）
   模块 8：自定义表单
   表单设计管理
   表单提交页面
   表单数据列表
   模块 9：运维监控（阶段八）
   服务监控页面
   在线接口文档页面（Swagger/springdoc）
   四、API 对接规则（禁止猜测，照做即可）
   使用项目已封装好的 axios
   自动携带 token
   统一错误提示
   分页只展示，不处理逻辑
   所有请求既可 GET 也可 POST
   五、AI 开发行为规则（必须遵守）
   只新增文件，不修改原有任何代码
   一次性生成所有页面、API、路由、菜单
   过程中不问问题、不等待、不确认
   不清楚的地方只单独问我一个问题，我回答后再继续
   达到 95% 把握后再开始写代码
   输出结构清晰：api 文件 → vue 页面 → 路由 → 菜单
