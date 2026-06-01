CyanBase 后端增补开发任务手册
适用 AI 智能体执行，基于现有完整底座工程，增补 JWT 登录认证、接口权限拦截，完成后具备登录鉴权能力，可对接前端系统
一、基础约束
工程路径：/home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend
运行环境：Java17、Maven3.8.7、SpringBoot3.1.6、MyBatis-Plus3.5.5
数据库配置沿用现有远程库信息，不修改数据源参数
现有 69 个业务 Java 文件、配置类、公共类禁止修改删除，仅新增代码、新增依赖、新增配置
接口统一保留原有 CRUD 功能，新增鉴权逻辑，原有测试接口不受破坏
二、新增技术依赖
pom.xml 追加依赖
在 dependencies 节点内添加以下依赖，其余内容保持不变
xml

<!-- JWT令牌认证 -->

<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
<!-- Spring安全框架 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
三、application.properties 新增配置
文件末尾追加 JWT 自定义配置项
properties
# JWT配置
jwt.secret=cyanbase2026secretkey666888999
jwt.expire=3600000
jwt.tokenHeader=Authorization
jwt.tokenPrefix=Bearer
四、新增文件目录与编码规范
新增分包结构
plaintext
com.cyan
├── security                # 安全认证核心包
│   ├── annotation          # 权限注解
│   ├── config              # 安全配置类
│   ├── filter              # 令牌拦截过滤器
│   ├── util                # JWT工具类
│   └── domain              # 登录封装实体
└── 原有业务包全部保留不变
必新增文件清单
security/util/JwtUtil.java：令牌生成、解析、过期校验工具类
security/domain/LoginUser.java：登录用户信息封装实体
security/annotation/RequiresPermission.java：接口权限校验注解
security/filter/JwtAuthenticationFilter.java：请求令牌拦截解析过滤器
security/config/SecurityConfig.java：SpringSecurity 全局安全配置
common/exception/AuthException.java：自定义认证授权异常
五、核心功能开发要求
1. JWT 工具类功能
根据用户 ID、账号生成有效令牌
解析令牌获取用户信息
判断令牌是否过期、合法性校验
固定密钥与过期时长读取配置文件参数
2. 登录接口改造
在SysUserController新增登录接口 /auth/login
接收账号密码参数，查询数据库校验账号状态
密码比对采用 BCrypt 加密校验逻辑
校验通过生成 JWT 令牌，携带用户基础信息、角色标识返回前端
账号错误、密码错误、账号禁用均返回对应提示码
3. 权限拦截规则
登录、获取验证码类接口放行，无需令牌
其余业务接口请求头必须携带合法 Authorization 令牌
令牌失效、伪造、缺失直接拦截并返回 401 未授权
基于注解实现接口权限控制，不同角色访问对应权限接口
4. 安全框架配置
关闭默认登录页面，自定义认证异常返回格式
配置跨域兼容原有跨域配置，不冲突
配置过滤器执行顺序，先解析令牌再校验权限
禁用不安全请求方式，保障接口访问安全
5. 异常统一处理
新增认证授权异常，接入全局异常处理器
未登录、权限不足、令牌过期统一返回标准 R 格式 JSON
错误提示文案简洁规范，适配前端解析
六、原有代码兼容规则
已完成的用户、部门、角色、菜单等 CRUD 接口逻辑不改动
分页、逻辑删除、数据库查询原有逻辑保留
统一返回类 R、状态码、全局异常类仅适配新增异常，原有方法不变
测试接口、基础健康接口保持可直接访问
七、新增对外接口清单
表格
请求方式    接口地址    功能说明    访问权限
POST    /auth/login    用户登录，返回 JWT 令牌    匿名访问
GET    /auth/getUserInfo    根据令牌获取当前登录用户信息    需令牌
GET    /auth/logout    退出登录，销毁令牌    需令牌
八、编译测试验收标准
代码无语法报错，执行mvn clean compile编译成功
账号admin密码admin123可正常登录获取令牌
无令牌访问业务接口被拦截，返回未授权提示
携带合法令牌可正常调用所有原有 CRUD 接口
过期令牌、非法令牌校验拦截生效
权限注解可控制接口访问权限，角色隔离生效
九、AI 执行指令附加说明
严格按分包新建文件，不打乱现有目录结构
新增代码遵循项目原有命名、注解、缩进编码风格
开发完成后自动打包启动，提供 curl 登录、鉴权测试命令
全部功能完成后，输出可直接对接 Vue 前端的接口文档摘要
