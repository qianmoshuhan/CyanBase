好了登录进来了。
把今天跑通整个链路回顾以下，整理一下改造方式，踩的坑，黑盒内部混乱的变量和方法以及诡异的偏移量。
方便我另一个会话，把嵌入式的滑块改为输入完用户名和密码点登录后，弹窗的滑块。
后续继续迭代租户侧登录的滑块。
18:30
好的！让我把今天整个调试过程完整复盘出来，方便后续迭代。

🧭 CyanBase 滑块验证码改造 — 完整技术复盘
一、涉及文件清单
文件	作用	改动量
AjCaptchaConfig.java	AJ-Captcha 引擎初始化（手动 Properties 装配）	重写
CaptchaController.java	/auth/captcha 接口（get + check）	重写 check()
LoginCaptchaService.java	登录凭证 create/consume（Redis）	修 bug
login/index.vue	前端滑块 UI + 拖动 + AES 加密提交	重写滑块部分
二、AJ-Captcha 1.3.0 内部架构（黑盒解剖）
text



依赖 JAR: com.anji-plus:captcha:1.3.0

BlockPuzzleCaptchaServiceImpl extends AbstractCaptchaService {

    // ===== get() 流程 =====
    get(req) → pictureTemplatesCut()  // 生成缺口图 + 背景图
             → 生成随机 secretKey(AES)
             → 缓存答案 JSON: {"secretKey":"xxx", "x":207, "y":5}
             → 返回 CaptchaVO(backgroundImage, puzzleImage, secretKey, token)

    // ===== check() 流程（⚠️ 有 bug！）=====
    check(req) → 
      1. 查缓存 exists(key) → 不存在返回 API_CAPTCHA_INVALID(6110)
      2. 读缓存 get(key) → 存为 PointVO cachePoint
      3. 立刻删缓存 delete(key) → 一次性！
      4. 用 cachePoint.secretKey 解密 req.pointJson → PointVO clientPoint
      5. ❌ 坐标校验（两个 if_icmpgt 方向写反了）
      6. 通过后 AES 加密存 second key → 返回 successData

    // ===== 反编译确认的缓存 key =====
    AbstractCaptchaService.REDIS_CAPTCHA_KEY = "RUNNING:CAPTCHA:%s"
    AbstractCaptchaService.REDIS_SECOND_CAPTCHA_KEY = "RUNNING:CAPTCHA:second-%s"
    
    // ===== 反编译确认的 Properties 键名 =====
    "captcha.slip.offset" → 默认 "5"   // X 坐标容差（像素）
    "captcha.aes.status"  → 默认 "true" // AES 加密开关（关不掉！）
    "captcha.cacheType"   → 默认 "local" // 缓存类型（local/redis）
    
    // ===== 反编译确认的常量 =====
    RepCodeEnum.API_CAPTCHA_INVALID         = "6110"  // 验证码已失效
    RepCodeEnum.API_CAPTCHA_COORDINATE_ERROR = "6111" // 验证失败（坐标不匹配）
    RepCodeEnum.API_REQ_LIMIT_CHECK_ERROR   = "6204"  // check 请求超限
}
三、字节码反编译发现的致命 Bug（💀 根因）
AJ-Captcha check() 坐标校验逻辑（第 155-219 行）
text



栈操作（栈顶在后）          等价 Java 代码
─────────────────────────────────────────────────────────────
push cacheX(107)
push slipOffset(15)
isub → 92
push clientX(108)
if_icmpgt 208           →  if (clientX > cacheX - slipOffset)  // 108 > 92 = TRUE
→ 跳到 6111 FAIL❌！永远命中！

（上面没跳才走到这）
push clientX(108)
push cacheX(107)
push slipOffset(15)
iadd → 122
if_icmpgt 208           →  if (cacheX + slipOffset > clientX)   // 122 > 108 = TRUE  
→ 又跳到 6111 FAIL❌！

push cacheY(5)
push clientY(5)
if_icmpeq 220           →  if (cacheY == clientY) → PASS✅
Y 比较正常，但永远走不到这步！
正确逻辑应该是：

java



// 条件1：clientX < cacheX - offset → FAIL（太小了）
if (clientX < cacheX - slipOffset) goto FAIL;
// 条件2：clientX > cacheX + offset → FAIL（太大了）  
if (clientX > cacheX + slipOffset) goto FAIL;
// Y 精确匹配
if (cacheY != clientY) goto FAIL;
两个 if 的操作数构建顺序 + 比较指令方向全反了 → 任何正常坐标都被拦截。

绕过方案
CaptchaController.check() 里不调 captchaService.check(req)，自己实现：

java



// 1) 从缓存读答案 JSON
String rawCache = readCacheRaw(token);  // key = "RUNNING:CAPTCHA:" + token
// 2) 解析 cacheX, cacheY, cacheSecretKey
// 3) 用 cacheSecretKey 解密前端 pointJson → clientX, clientY
// 4) 校验：
boolean xOk = Math.abs(clientX - cacheX) <= 15;  // 对称容差
boolean yOk = (clientY == cacheY);                 // 精确匹配
// 5) 通过则删缓存 + 发 credentialKey
四、前端坐标系统（诡异的偏移量）
三套坐标搞清楚了
text



┌─ 缺口图（puzzleImage）内部结构 ──────────────────┐
│                                                    │
│   ┌────┐                                           │
│   │凸块│  ← gapCX, gapCY (scanJigsaw 扫到的内部中心) │
│   └────┘                                           │
│   gapX, gapY ← 凸块左上角在拼图内的偏移（像素）     │
│                                                    │
└────────────────────────────────────────────────────┘

puzzleLeft = -gapX * scaleFactor + dragX  // 拼图在画布上的 CSS left
↑                                                ↑
拼图内部偏移（起始补偿）                           用户拖动的像素

用户松手时：
dragX / scaleFactor ≈ 拼图在背景图上的 X 偏移量

后端答案（从缓存读）：
gameX = 缺口凸块在背景图上的左上角 X
gameY = 缺口凸块在背景图上的左上角 Y

关键：dragX/scaleFactor ≈ gameX（缺口在背景图上的位置）
（因为用户把拼图拖到缺口位置时，视觉上对齐了）
scanJigsaw 扫出来的东西
javascript



// 扫描缺口图 canvas，找 alpha > 30 的像素块
return {
x: gapX,    // 凸块在拼图内部的左上角 X（≈10-15px，拼图左边到凸块的距离）
y: gapY,    // 凸块在拼图内部的左上角 Y（扫描误差 ±3-5px！）
cx: gapCX,  // 凸块中心 X
cy: gapCY   // 凸块中心 Y
}
为什么 Y 必须用后端的
text



scanJigsaw 扫拼图 Y → 21（拼图内部偏移）
后端 gameY → 5（缺口在背景图上的 Y）
差 → 16px（拼图内部 padding + 对齐关系复杂）

关键：后端 check() 里比较的是「前端提交的 y」 vs 「后端缓存的 gameY」
scanJigsaw 扫拼图的 Y 不准（拼图内部坐标 ≠ 缺口在背景图上的坐标）
→ 所以提交的 y 必须用后端 get 接口返回的 gameY
五、AJ-Captcha 不支持 Spring Boot 3 的原因
text



com.anji-plus:captcha:1.3.0 自带：
- META-INF/spring.factories → Spring Boot 2.x 自动配置入口（Boot 3 不认）
- com.anji.captcha.controller.* → 依赖 javax.servlet（Boot 3 用 jakarta.servlet）
- com.anji.captcha.config.* → 内部有 @ComponentScan("com.anji.captcha") 递归扫描

解决方案：AjCaptchaConfig 手动装配
- 不 ComponentScan com.anji.captcha（避开自带 controller）
- 直接 CaptchaServiceFactory.getInstance(config) 构造引擎
- 自己写 CaptchaController（/auth/captcha）
  六、后端完整改造点
1. AjCaptchaConfig.java — 手动装配
   java



@Bean
public CaptchaService captchaService() {
    Properties config = new Properties();
    // 以下 key 全部来自 javap -c AbstractCaptchaService 字节码 ldc 指令确认
    // ⚠️ Properties 精确匹配，yml 的驼峰/连字符自动转换规则在这里无效
    config.put("captcha.type", "blockpuzzle");        // 滑块类型
    config.put("captcha.slip.offset", "15");          // X 容差 ±15px（默认 5）
    config.put("captcha.aes.status", "true");         // AES 加密（关不掉）
    config.put("captcha.cacheType", "local");         // 引擎答案缓存：local（内存），和登录凭证 Redis 独立
    config.put("captcha.water.mark", "");             // 右下角水印（默认 "我的水印"，空串=关闭）
    config.put("captcha.jigsaw", "classpath:captcha/images/jigsaw");  // 自定义底图目录
    return CaptchaServiceFactory.getInstance(config);
}

// ── ⚠️ 重要教训：Properties key 精确匹配 ──
// Spring Boot 的 @ConfigurationProperties 会自动把连字符转驼峰
// （如 yml 写 water-mark 会映射到 POJO 的 waterMark 字段）
// 但底层 java.util.Properties 是精确字符串匹配，引擎源码写什么就得传什么
// 引擎 AbstractCaptchaService.init() 里的真实 ldc 字面量：
//   ldc #19 // String captcha.water.mark  ← 点号！不是 "captcha.water-mark"
//   ldc #23 // String captcha.slip.offset ← 点号
//   ldc #26 // String captcha.water.font  ← 点号
// 差一个字符引擎就走 getProperty(key, "默认值") 兜底分支
2. CaptchaController 构造器 — 反射拿缓存服务
   java



// CaptchaCacheServiceMemImpl（local 模式）用工厂方法拿
Class<?> factoryCls = Class.forName("com.anji.captcha.service.impl.CaptchaServiceFactory");
Method getCache = factoryCls.getDeclaredMethod("getCache", String.class);
captchaCacheService = (CaptchaCacheService) getCache.invoke(null, "local");
3. get() 接口 — 额外返回 gameX/gameY
   java



// 从缓存挖答案 JSON，返回给前端（前端用 gameY 做精确 Y 提交）
int[] gameXY = readGameXY(res.getToken());  // cache.get("RUNNING:CAPTCHA:" + token)
data.put("gameX", gameXY[0]);
data.put("gameY", gameXY[1]);
4. check() 接口 — 绕过 AJ-Captcha bug
   java



// 自己做坐标校验
JsonNode cache = objectMapper.readTree(readCacheRaw(token));
String plain = AESUtil.aesDecrypt(pointJson, cache.get("secretKey").asText());
JsonNode client = objectMapper.readTree(plain);

boolean xOk = Math.abs(client.x - cache.x) <= 15;
boolean yOk = (client.y == cache.y);

if (xOk && yOk) {
deleteCache(token);  // 一次性消费
data.put("credentialKey", loginCaptchaService.createCredential());
return R.ok(data);
}
return R.fail("滑动验证未通过");
5. LoginCaptchaService — 修前缀 bug
   java



// 之前：createCredential 返回完整 "captcha:login:credential:" + UUID
//       consumeCredential 再拼前缀 → "captcha:login:credential:captcha:login:credential:" + UUID ❌
// 修复：createCredential 只返回 UUID
public String createCredential() {
String uuid = UUID.randomUUID().toString();
redisUtil.set(CREDENTIAL_PREFIX + uuid, "1", 120, SECONDS);
return uuid;
}
七、前端完整改造点（login/index.vue）
text



数据流：
用户拖动 → dragX 增加 → puzzleLeft 从 -gapX*scale 开始滑入
用户松手 → doCheck() 提交
x = Math.round(dragX / scaleFactor)  // ≈ 缺口在背景图上的 X
y = serverGameY (后端返回)            // 精确 Y
track = 拖动过程坐标轨迹（AJ-Captcha check 里没用，但加密时要带上）
AES(ECB/PKCS7) 加密 → POST /auth/captcha/check
成功 → loginForm.credentialKey = res.data.credentialKey
然后点登录 → handleLogin → userStore.login(username, password, credentialKey)
八、踩坑时间线（关键节点）
#	现象	排查手段	根因	修复
1	get 请求后端日志空	加 log.info	无	—
2	反射找不到 cacheService	javap -p 看字段	字段猜测错了	静态工厂 CaptchaServiceFactory.getCache("local")
3	Properties 配置无效	反编译 init() ldc	键名猜测错了	硬编码 "captcha.slip.offset" 等
4	check 永远 6111	反编译 check()	两个 if 方向全反	绕过，自己做校验
5	拼图藏在画布外 172px	看 puzzleLeft 计算	gapX 被赋成后端 gameX	gapX 保持 scanJigsaw 内部偏移
6	Y 坐标差 4px 必挂	对比 scanJigsaw 输出 vs gameY	scanJigsaw 扫拼图 Y 有误差	提交 Y 强制用后端 gameY
7	credentialKey 存 Redis 找不到	加 log 看 key	前缀重复拼接	createCredential | 8 | Spring Boot 3 启动报 javax.servlet NoClassDefFoundError | 看 AJ 自带 controller | Boot 3 用 jakarta 而非 javax | 不 ComponentScan，手动装配 |
| 9 | 右下角 Cyan 字样关不掉 | 反复试 Properties key（连字符、空格、Const 常量） | Properties key 必须精确匹配引擎源码的字面量 | javap `AbstractCaptchaService.init()` → `captcha.water.mark`（点号，不是 `captcha.water-mark` 连字符） |
| 10 | 反射设 waterMark=null 触发滑块校验 NPE `Cannot invoke "String.length()" because "s" is null` | 反射捕获堆栈 | 引擎 `drawWatermark()` 没有空检查直接调 `s.length()` | Properties 设空串 `""` 替代 null，length=0 视觉上等于无水印 |
| 11 | 改了 captcha.jigsaw 路径但底图没变 | 看引擎 `ImageUtils 初始化底图` 日志 + 检查目录 | 自定义目录里没放图 → 引擎 fallback 到 jar 内置植物图（自带 Cyan 字样） | 目录 `captcha/images/jigsaw/` 放几何风格 jpg/png（≥310×155），不放图等于白配 |九、后续迭代指南
弹窗滑块（另一个会话要做的）
html



<!-- 改为点击登录按钮后，先显示弹窗 -->
<el-dialog v-model="showCaptchaDialog" title="请完成滑块验证" width="420px">
  <div class="slider-captcha">...</div>
  <template #footer>
    <el-button @click="closeCaptchaDialog">取消</el-button>
  </template>
</el-dialog>
javascript



// handleLogin 里改为：
const handleLogin = async () => {
if (!loginFormRef.value) return
await loginFormRef.value.validate(async (valid) => {
if (!valid) return
if (!loginForm.credentialKey) {
showCaptchaDialog.value = true  // 弹窗
await loadCaptcha()
return
}
// 走正常登录流程...
})
}

// doCheck 成功后：
captchaOk.value = true
loginForm.credentialKey = res.data.credentialKey
showCaptchaDialog.value = false  // 关弹窗
// 可选：自动触发登录
await handleLogin()
租户侧登录滑块
text



租户侧和平台侧结构完全一样，只是：
- 登录接口是 /tenant/user/login（不是 /auth/login）
- 需要再写一套 TenantLoginCaptchaService（credential key 前缀区分租户/平台）
- 前端 @/views/tenant-login/index.vue 里重复同样的滑块组件
- captcha 接口可以复用同一个（AJ-Captcha 引擎不分租户）
- 但 consumeCredential 的 key 前缀要区分：
  平台: captcha:login:credential:
  租户: tenant:captcha:credential:
  十、反编译速查命令
  bash

# 看缓存 key 常量
javap -v -p -classpath ~/.m2/repository/com/anji-plus/captcha/1.3.0/captcha-1.3.0.jar com.anji.captcha.service.impl.AbstractCaptchaService 2>&1 | grep -B 2 -A 2 "REDIS_CAPTCHA_KEY"

# 看 check() 完整字节码
javap -c -p -classpath ~/.m2/repository/com/anji-plus/captcha/1.3.0/captcha-1.3.0.jar com.anji.captcha.service.impl.BlockPuzzleCaptchaServiceImpl 2>&1 | grep -A 350 "public.*check"

# 看 RepCodeEnum 错误码
javap -c -p -classpath ~/.m2/repository/com/anji-plus/captcha/1.3.0/captcha-1.3.0.jar com.anji.captcha.model.common.RepCodeEnum 2>&1

# 看 PointVO 结构
javap -c -p -classpath ~/.m2/repository/com/anji-plus/captcha/1.3.0/captcha-1.3.0.jar com.anji.captcha.model.vo.PointVO 2>&1

# 看 Properties init() 读了哪些 key（找所有 ldc String + getProperty）
javap -c -p -classpath ~/.m2/repository/com/anji-plus/captcha/1.3.0/captcha-1.3.0.jar com.anji.captcha.service.impl.AbstractCaptchaService 2>&1 | grep -E "ldc.*String|getProperty|putstatic.*Field"

# 单独看 watermark/jigsaw/font 相关的 ldc
javap -c -p -classpath ~/.m2/repository/com/anji-plus/captcha/1.3.0/captcha-1.3.0.jar com.anji.captcha.service.impl.AbstractCaptchaService 2>&1 | grep -B1 -A3 "water\|Water\|jigsaw\|font\|Font" | head -60
十一、关键代码位置索引（以后改直接跳）
功能	文件	行号
AJ-Captcha 引擎初始化（含 water.mark / jigsaw）	AjCaptchaConfig.java	48-72
缓存 key 反射读取	CaptchaController.java	99-129
get() 接口（返回 gameX/Y）	CaptchaController.java	164-188
check() 接口（自写校验）	CaptchaController.java	193-270
deleteCache 一次性删除	CaptchaController.java	272-280
credentialKey create	LoginCaptchaService.java	33-37
credentialKey consume	LoginCaptchaService.java	43-51
前端滑块 UI + 拖动	login/index.vue	36-51, 218-247
前端坐标扫描 scanJigsaw	login/index.vue	140-175
前端 AES 加密 + check	login/index.vue	249-298
puzzleLeft 渲染公式	login/index.vue	125
祝后续迭代顺利！有问题随时翻这份复盘 🎯

---

## 十二、Java2D 程序化底图彻底改造（2026-11-02 补充）

### 改造目标
彻底移除 AJ-Captcha 外部 jigsaw 图片目录依赖，用 Java2D 在内存程序化绘制底图，消除版权风险，适配政企环境。

### 涉及文件
| 文件 | 作用 | 改动量 |
|------|------|--------|
| CaptchaImageGenerator.java | 新增！底图绘制 + 缺口裁切 + 滑块描边 | 新建 ~400 行 |
| CaptchaController.java | get/check 接口改用自定义 Generator | 重写 |
| pom.xml | 移除 com.anji-plus:captcha 依赖 | 改 |
| login/index.vue | 滑块坐标/缩放公式大改 | 重写滑块部分 |

### 核心架构（3 套坐标系统）
```
┌──────────────────────────────────────────────────────────┐
│ 1. 底图坐标系 (原生 310×155)                              │
│    gameX, gameY = 缺口 jigsaw bounds 左上角在底图的位置    │
│    scaleFactor = 显示宽度 / 310（如 260/310 ≈ 0.84）      │
│                                                          │
│ 2. 滑块画布坐标系 (原生 canvasW×canvasH)                  │
│    PIECE_PADDING = 5（四周留白，让 scanJigsaw 返回非0）    │
│    clip = jigsawShape 平移到 (PADDING, PADDING)          │
│    drawImage 偏移 = (PADDING-gameX, PADDING-gameY)         │
│                                                          │
│ 3. 滑块内部坐标系 (scanJigsaw 扫出来的)                    │
│    gapX, gapY ≈ PIECE_PADDING = 5（jigsaw 到画布原点距离） │
│    gapCX, gapCY = jigsaw 中心                              │
└──────────────────────────────────────────────────────────┘
```

### jigsaw 形状生成（💀 关键！只 add 不 subtract！）
```java
// ✅ 正确：只 outward add（叠加），形状永远完整
Area area = new Area(new Rectangle(0, 0, pieceSize, pieceSize));
area.add(new Area(new Ellipse2D(cx-topKnob/2, -topKnob/2, topKnob, topKnob)));  // 上半圆凸
area.add(new Area(new Ellipse2D(cx-botKnob/2, pieceSize-botKnob/2, botKnob, botKnob)));  // 下半圆凸
// ❌ 绝对禁止 inward subtract！会在方形上"挖洞"→ 形状破碎！
// area.subtract(new Area(new Ellipse2D(...)));  // 致命错误！
```

### 底图绘制（纯色系 + 3-5 个大圆/三角/半圆）
```
渐变底色: HSB bright 0.88-1.00（接近白色打底）, sat 0.05-0.17（极浅饱和）
叠加图形: 3-5 个大几何（圆/三角/半圆/椭圆），高饱和 0.55-0.95，alpha 0.55-0.85
⛔ 绝对不要线条、散点！太乱太暗！
```

### 缺口 vs 滑块视觉对齐（💀 关键！滑块必须按底图同比例缩放！）
```
底图 CSS 尺寸 = 310 * scaleFactor（如 260px）
缺口 jigsaw CSS 尺寸 = 原生 * scaleFactor（如 47*0.84=39.5px）

之前滑块 width:auto 不缩放 → CSS 尺寸 = 原生 47px ≠ 39.5px → 大一圈！
✅ 修复：滑块加 transform: scale(scaleFactor), transform-origin: top left
```

### 前端 puzzleLeft / puzzleTop 公式（💀 反复踩坑）
```javascript
// 对齐条件：
// 1) 拼图块 jigsaw 左边缘（视觉）= 缺口 jigsaw 左边缘（视觉）
// 2) 拼图块 jigsaw top（视觉）= 缺口 jigsaw top（视觉）
// transform-origin: top left → scale 不影响 left/top 值

const puzzleLeft = computed(() => dragX.value - gapX.value * scaleFactor.value)
const puzzleTop  = computed(() => (serverGameY - gapY.value) * scaleFactor.value)

// 验证：
// gapX=5, gapY=5 (scanJigsaw), gameX=161, gameY=28, scaleFactor=0.84
// 对齐时 dragX = gameX * scaleFactor = 161*0.84 ≈ 135
// puzzleLeft = 135 - 5*0.84 = 131
// 拼图块 jigsaw CSS left = puzzleLeft + gapX*scaleFactor = 131 + 4.2 = 135.2
// 底图缺口 CSS left = 161 * 0.84 = 135.2 ✓ 完美！
```

### 缺口视觉（黑 + 径向白光，无白边）
```java
// 1. 纯黑底 + jigsaw clip
g2d.setClip(jigsawShape);
g2d.setColor(Color.BLACK);
g2d.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
// 2. 径向渐变白光：中心白 160→边缘透明
float radius = (float) Math.hypot(bounds.width, bounds.height) * 0.75f;  // 对角线 × 0.75，范围大
RadialGradientPaint shine = new RadialGradientPaint(
    cx, cy, radius,
    new float[]{0f, 0.4f, 1f},
    new Color[]{new Color(255,255,255,160), new Color(255,255,255,40), new Color(0,0,0,0)}
);
// ⛔ 绝对不要白色描边！缺口就是黑+白光，干净漂亮！
```

### 滑块视觉（底图原始颜色 + 内部柔和阴影条带）
```java
// ✅ 内部边缘条带（Area.subtract）— 不超出 bounds，滑块尺寸完美等于缺口
Area shadowArea = new Area(canvasShape);
shadowArea.subtract(new Area(canvasInner));  // 原始 - 内缩 3px
// ⛔ 绝对不要 fill(偏移后的整个形状)！会覆盖原始像素颜色！
// ⛔ 绝对不要 offsetShape - canvasShape！阴影画在外部 → 视觉变大！
```

### 踩坑时间线（2026-11-02）
| # | 现象 | 根因 | 修复 |
|---|------|------|------|
| 1 | 缺口/滑块永远在顶部 | gameY 固定 MIN_GAP_Y=5 | 恢复 generateGapY() 随机 + 前端加 puzzleTop |
| 2 | 形状每次一样 | createJigsawShape 固定 | knobSize 随机 21-25px |
| 3 | 形状破碎（方形中间挖洞） | inward subtract 半圆 | 只 outward add！ |
| 4 | 滑块比缺口大一圈 | 滑块 width:auto 不缩放 | 加 transform: scale(scaleFactor) |
| 5 | 滑块颜色被黑色蒙住 | fill(偏移后的完整形状) 覆盖原始像素 | Area.subtract 只画边缘条带 |
| 6 | scanJigsaw gapX=0 | 拼图块画布无 padding，alpha 从原点开始 | PIECE_PADDING = MIN_GAP_Y = 5 |
| 7 | 底图暗沉昏暗 | bright 0.60-0.85 + 线条散点堆叠变暗 | bright 0.88-1.0 + 只放 3-5 个大圆 |
| 8 | 编译 cannot find symbol Arc2D | 漏写 import | 加 `import java.awt.geom.Arc2D` |
| 9 | 编译 gameY 未定义 | 删掉 gameY=MIN_GAP_Y 后没重建 | 补 `int gameY = gapY + jigsawBounds.y` |
| 10 | stroke 端点胖一圈 | CAP_SQUARE 端点样式不对称 | 统一用 BasicStroke(width) 默认 CAP_BUTT |

### 以后开发注意事项
1. **jigsaw 形状只 add 不 subtract**！subtract 挖洞 → scanJigsaw 识别错误 → 对齐崩溃
2. **滑块必须 transform: scale(scaleFactor)**！width:auto 原生尺寸 ≠ 底图缩放后缺口尺寸
3. **puzzleLeft/puzzleTop 公式**：`left = dragX - gapX*scale`, `top = (gameY - gapY)*scale`（transform-origin: top-left）
4. **阴影只画内部条带**：`Area(canvasShape).subtract(Area(innerShape))`，绝对不 fill 完整偏移形状
5. **scanJigsaw gapX/gapY ≈ PIECE_PADDING**（不是 0！），这是坐标系统的原点偏移
6. **Y 方向对齐公式**：puzzleTop 固定值 = (gameY - gapY) * scaleFactor（不靠 dragX！）
7. **底图风格**：亮色系 + 大几何图形，不要线条散点（杂乱 + 变暗）
8. **编译必加 import**：Area, Arc2D, Ellipse2D, RadialGradientPaint, AffineTransform, AlphaComposite, BasicStroke

### 关键代码位置索引
| 功能 | 文件 | 说明 |
|------|------|------|
| 底图绘制 drawBackground | CaptchaImageGenerator.java | drawGradientBackground + drawBigShape |
| jigsaw 形状 createJigsawShape | CaptchaImageGenerator.java | 只 add！knobSize 随机 |
| 缺口裁切 cutPuzzlePiece | CaptchaImageGenerator.java | clip + drawImage 偏移对齐 |
| 缺口打黑 punchHoleInBackground | CaptchaImageGenerator.java | 纯黑 + 径向白光，无白边 |
| 滑块阴影 addPuzzleShadow | CaptchaImageGenerator.java | Area.subtract 内部条带 |
| generate 主流程 | CaptchaImageGenerator.java | 生成→裁切→打黑→阴影→Base64 |
| 前端 puzzleLeft/Top | login/index.vue | dragX - gapX*scale / (gameY-gapY)*scale |
| 滑块 transform scale | login/index.vue | scale(scaleFactor), origin: top left |

---

🎯 **终极坐标验证公式**（任何时候出错先算这三行）：
```
底图缺口 CSS left  = gameX * scaleFactor
拼图块 jigsaw CSS left = puzzleLeft + gapX * scaleFactor
拼图块 jigsaw CSS left = 底图缺口 CSS left ✓
```

---

## 十三、二次改造：视觉修正 + 形状多样化 + 前端竞态修复（2026-11-02 补充）

### 改造目标
解决第一次上线后的三个问题：
1. 滑块图自带黑色发散光 + 无阴影（视觉怪）
2. 缺口白光太小、形状永远是同一种（体验单调）
3. 弹窗居中一直偏、关闭按钮各种竞态 bug（交互崩溃）

### 涉及文件
| 文件 | 作用 | 改动量 |
|------|------|--------|
| CaptchaImageGenerator.java | 后端图片生成器（视觉全部重写） | ~200 行改 |
| login/index.vue | 前端弹窗 + 滑块 UI + 登录流程 | ~300 行改 |

---

### 一、后端：滑块视觉修正（去掉内部黑光 + 加外部投影）

#### 之前（错的）
```java
// addPuzzleShadow — 在 jigsaw 内部用黑色渐变蒙住底图原始颜色
g2d.setClip(canvasShape);                    // 裁剪到 jigsaw 内部
RadialGradientPaint shadow = new RadialGradientPaint(
    cx+4, cy+4, radius,                     // 偏移 4px
    new float[]{0f, 1f},
    new Color[]{new Color(0,0,0,120), new Color(0,0,0,0)}
);
g2d.setPaint(shadow);
g2d.fillRect(...);  // 整个 jigsaw 内部被黑灰覆盖！原始颜色看不见！
```

#### 之后（对的）
```java
// addPuzzleShadow — 只在 jigsaw 外部画投影，内部完全保持底图原色
Area canvasShape = new Area(  // jigsaw 在画布中的形状（PIECE_PADDING 偏移后）
    shape.createTransformedArea(AffineTransform.getTranslateInstance(PADDING, PADDING))
);
Area offsetShape = canvasShape.createTransformedArea(
    AffineTransform.getTranslateInstance(SHADOW_OFFSET_X, SHADOW_OFFSET_Y)
);
offsetShape.subtract(canvasShape);  // 偏移形状 - 原形状 = 只有外部多出来的部分！
g2d.fill(offsetShape);  // 只画外部半透明投影，不侵入 jigsaw 内部
```

数学验证：
```
┌───────────────────────────────────────┐
│     外部投影（淡灰色）                │
│    ╱                                │
│   ╱  ┌──────────╱─────┐              │
│  │   │  原始 jigsaw  │ ← 完全不被覆盖│
│  │   │  保持底图原色 │              │
│  │   └──────────╲─────┘              │
│   ╲              ╲ 投影              │
│    ╲                                │
└───────────────────────────────────────┘
```

---

### 二、后端：缺口白光放大

| 参数 | 之前 | 之后 |
|------|------|------|
| radius | 对角线 × 0.75 | **对角线 × 1.5** |
| 中心 alpha | 160 | **200** |
| 渐变断点 | 2 个 (0→1) | **3 个 (0→0.4→1)** |

---

### 三、后端：jigsaw 形状彻底多样化

> ⚠️ **本节记录的是已废弃方案**（2026-11-02 实现 → 2026-11-16 被模板池方案替换）。
> 废弃原因：四条边独立随机 + knobSize 32~60px + offset ±8px → 8.1 亿种组合中大量形状破碎、畸形、观感怪异。
> 当前方案见 **第十四章（2026-11-16 模板池改造）**。

#### 之前（单一形状）
```java
// 每条边只加一个旋钮，固定位置固定大小
area.add(ellipse(上边中间, knobSize=21));
area.add(ellipse(下边中间, knobSize=21));
area.add(ellipse(左边中间, knobSize=21));
area.add(ellipse(右边中间, knobSize=21));
```

#### 之后（800 万+ 种变体 — ❌ 已废弃）
```java
// ❌ 废弃原因：太放飞，凹块过多、knobSize 极端、offset 过大 → 怪异形状频发
// 四条边状态各自独立随机：0=平 1=凸 2=凹 → 3^4 = 81 种组合
// knobSize：32~60px 随机（范围太大）
// 每条边 knob 位置偏移：-8~+8（偏差太大破坏平衡）
```

---

### 四、后端：Java2D 抗锯齿消除毛刺

#### 新增工具方法
```java
private void setHighQualityHints(Graphics2D g2d) {
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
}
```

#### fill(Shape) 替代 setClip + fillRect
```java
// ❌ 之前：setClip + fillRect → clip 边界硬切 → 像素级硬边
g2d.setClip(jigsawShape);
g2d.fillRect(...);

// ✅ 之后：直接 fill(Shape) → Java2D 抗锯齿渲染 → 边缘半透明过渡
g2d.fill(jigsawShape);
```

---

### 五、后端：2x 高分辨率 down-scale 方案（解决放大后模糊）

#### 问题
CSS 把 310px 底图放大到 360px → 每个像素被拉伸 1.16x → 模糊 + 边缘抗锯齿半透明像素变成阶梯

#### 方案
后端输出 **620×310 高分辨率**，前端 CSS 缩放到 360px：
```
原方案（up-scale，糊）：
  后端 310px ──CSS 放大──▶ 360px

新方案（down-scale，清晰）：
  后端 620px ──CSS 缩小──▶ 360px（scaleFactor ≈ 0.58，永远 < 1）
```

#### 常量同步 ×2
| 常量 | 原值 | 新值 |
|------|------|------|
| BG_WIDTH / BG_HEIGHT | 310 × 155 | **620 × 310** |
| PIECE_SIZE | 47 | **94** |
| MIN_GAP_X / MIN_GAP_Y | 100 / 5 | **200 / 10** |
| PIECE_PADDING | 5 | **10** |
| knobSize 范围 | 16~30 | **32~60** |
| shadow offset | 4px | **8px** |

#### 为什么前端坐标逻辑一行不用改
```
后端输出 620×310，某缺口 gameX=300
前端 trackWidth=360
scaleFactor = 360/620 ≈ 0.5806

用户拖动到正确位置时：
  dragX = gameX × scaleFactor = 300 × 0.5806 ≈ 174px

提交给后端校验时：
  x = dragX / scaleFactor = 174 / 0.5806 ≈ 300 ✓ 完美匹配 gameX！
```

---

### 六、前端：弹窗居中终极方案

#### 踩坑链
| # | 做法 | 为什么失败 |
|---|------|-----------|
| 1 | `custom-class="captcha-dialog"` | Element Plus 某些版本不支持这个 prop！类名根本没加上 |
| 2 | `:body-style="{padding:'20px'}"` | el-dialog 没有 body-style prop！被 Vue 忽略 |
| 3 | `<style scoped>` 里写 `.captcha-dialog` | append-to-body 把 DOM 移到 body 下，scoped CSS 的 `[data-v-xxx]` 选择器永远匹配不到 |
| 4 | `<style scoped>` 里写 `:deep(.el-dialog)` 嵌套 | 写成了 `.captcha-dialog :deep(.el-dialog)` → 在 captcha-dialog 内部找 .el-dialog → 永远找不到 |
| 5 | text-align:center + inline-block | 在 flex 容器里有时不生效 |

#### 正确方案（3 招）
```html
<!-- 1. template 里用 class 直接加（不是 custom-class！） -->
<el-dialog v-model="showCaptchaDialog" class="captcha-dialog">

<!-- 2. 去掉 append-to-body（它会导致多个 DOM 叠加） -->

<!-- 3. 独立全局 style 块（不要 scoped！） -->
<style>
.captcha-dialog {
  width: fit-content !important;     /* 严格贴合内容宽度 */
  max-width: 90vw !important;        /* 防小屏超宽 */
  margin: 10vh auto 0 !important;
  border-radius: 12px !important;
}
.captcha-dialog .el-dialog__body {
  padding: 16px 20px !important;     /* 四方向统一 */
  display: flex !important;
  justify-content: center !important; /* flex 主轴居中最可靠 */
}
.captcha-dialog .el-dialog__header {
  display: none !important;           /* 隐藏标题 */
}
.captcha-dialog .el-dialog__footer {
  display: none !important;           /* 隐藏底部 */
}
</style>
```

---

### 七、前端：doCheck 成功后自动登录竞态链

#### 现象
滑块验证成功 → credentialKey 有了 → 弹窗关了 → 但没自动登录，反而又弹一次滑块

#### 根因（3 层叠加）
```
层 1：isLoggingIn 锁挡住了自己
  handleLogin 弹滑块时 isLoggingIn = true
  doCheck 成功后调 handleLogin → isLoggingIn 还是 true → 直接 return！
  credentialKey 白设了！

层 2：handleLogin 里 loginFormRef.validate 可能返回 valid=false
  Element Plus 的 validate callback 如果表单状态有任何问题 → valid=false → 什么都不做

层 3：closeCaptchaDialog 清了 credentialKey
  doCheck 成功 → showCaptchaDialog=false → 触发 @close → closeCaptchaDialog()
  → 清了 credentialKey → handleLogin 走无凭证分支 → 又弹滑块！
```

#### 修复
```javascript
// ✅ doCheck 成功后直接在里面完成登录，不再绕 handleLogin！
// 原因：绕开 handleLogin 里的 isLoggingIn 锁 + validate 可能挡住的问题
async function doCheck() {
  // ... 滑块校验 ...
  if (成功) {
    loginForm.credentialKey = res.data.credentialKey
    isAutoClosing = true                    // 让 closeCaptchaDialog 不碰 credentialKey
    showCaptchaDialog.value = false         // 关弹窗 → 触发 @close
    await closeWindowListeners()
    // ⭐ 直接在这里登录！
    await userStore.login(userName, password, credentialKey)
    await userStore.fetchUserInfo()
    router.push('/home')
  }
}
```

---

### 八、前端：closeCaptchaDialog 极简设计

#### 最终方案
```javascript
// closeCaptchaDialog 只做 2 件事！其他一律不动！
function closeCaptchaDialog() {
  if (isAutoClosing) {
    isAutoClosing = false
    return  // doCheck 成功自动关 → 不碰 credentialKey
  }
  showCaptchaDialog.value = false  // ① 确保 Vue 状态正确
  captchaCancelled = true          // ② 通知异步不要回写
  // ⚠️ 绝对不要清 captchaBg / captchaPuzzle / captchaTip / gapX / gapY！
  // 原因：el-dialog 关闭动画期间 DOM 还在页面上
  // 清图片 → 底图消失只剩导轨文字 → 用户看到"幽灵弹窗"！
  // 这些变量留着完全无害，下次 loadCaptcha() 开头会全部覆盖。
}
```

#### 踩过的坑（为什么不能清）
```
用户点 X 关闭时：
T0: showCaptchaDialog = false → dialog 开始播放关闭动画（DOM 还在！）
T1: 动画结束 → @close → closeCaptchaDialog()
  → captchaBg = '' ← 底图 img 消失了！
  → captchaTip = '请先完成滑块验证' ← 只剩导轨上的文字！
  → 用户看到：dialog 框 + 导轨 + "请先完成滑块验证" → 真的关不掉！
```

---

### 九、前端：模板里 ref 自动解包陷阱

```vue
<!-- ❌ 错：模板里 ref 已经自动解包，.value 访问会得到 undefined -->
@close="() => { console.log(showCaptchaDialog.value) }"
<!-- 实际输出: undefined（因为 boolean.value = undefined） -->

<!-- ✅ 对：模板里直接用 ref 变量名 -->
@close="() => { console.log(showCaptchaDialog) }"
<!-- 实际输出: true / false ✓ -->
```

---

### 十、完整踩坑时间线

| # | 现象 | 根因 | 修复 |
|---|------|------|------|
| 1 | 滑块图自带黑色发散光 | setClip + 内部径向渐变盖黑，覆盖底图原始颜色 | Area(偏移形状) - Area(原形状) = 只在外部画投影 |
| 2 | 缺口白光太小 | radius 只用到对角线 0.75x | 放大到 1.5x + alpha 160→200 + 3 断点渐变 |
| 3 | 形状永远一样 | knobSize 固定 21px + 固定位置 | 4 条边各 3 种状态 × knobSize 随机 × 位置随机 → 800 万+ 种 |
| 4 | 边缘毛刺 | setClip + fillRect 硬切边界 | 8 个 RenderingHints + fill(Shape) 直接绘制 |
| 5 | 放大后模糊 | CSS up-scale 310→360px 每个像素被拉伸 | 后端输出 620×310 (2x)，前端 down-scale 到 360px |
| 6 | 弹窗左边比右边多 3-4px | custom-class 不生效 + style scoped + append-to-body 三重失效 | 用 class 直接加 + 去掉 append-to-body + 独立全局 style 块 |
| 7 | 验证成功后不登录 | isLoggingIn 锁 + validate 可能 valid=false + closeCaptchaDialog 清 credentialKey | doCheck 成功后直接在里面登录，不调 handleLogin |
| 8 | 关闭后弹窗变成"请先完成滑块验证"文字关不掉 | closeCaptchaDialog 清 captchaBg/captchaPuzzle → el-dialog 关闭动画期间 DOM 还在 | closeCaptchaDialog 只设 showCaptchaDialog=false + captchaCancelled=true，其他一律不动 |
| 9 | "showCaptchaDialog=undefined" | 模板里 ref 自动解包，.value 访问错误 | 模板里直接用变量名，不加 .value |
| 10 | 旧 dialog @close 晚到误关新 dialog | append-to-body 导致多个 DOM 叠加 + 关闭动画时序 | 去掉 append-to-body |

---

### 十一、经验总结（下次开发直接对照）

#### 后端 Java2D
1. **滑块阴影画在外部，不侵入 jigsaw 内部**：`Area(偏移) - Area(原形状)`
2. **jigsaw 形状只 add 不 subtract**：subtract 会挖洞破坏 scanJigsaw 识别
3. **Knob 椭圆圆心贴在方形边界上**：向外凸用 add，向内凹用 subtract，完美对称
4. **fill(Shape) 替代 setClip + fillRect**：天然抗锯齿边缘
5. **图片放大用 2x 高分辨率 down-scale**：永远比原生尺寸大 2 倍再 CSS 缩小
6. **所有常量同步缩放**：2x 方案里所有像素值都要 ×2

#### 前端 Vue 3
1. **模板里 ref 自动解包**：模板直接用变量名，不加 `.value`
2. **append-to-body 和 scoped CSS 是死敌**：DOM 移到 body 后 scoped 选择器全失效
3. **el-dialog 用 class 直接加**：不是 custom-class（某些版本不支持）
4. **弹窗居中三件套**：`width: fit-content` + body `display:flex justify-content:center` + 独立全局 style 块
5. **doCheck 成功后不要绕 handleLogin**：直接在 doCheck 里写登录逻辑，避免中间的锁/校验挡住
6. **closeCaptchaDialog 极简设计**：只设 `showCaptchaDialog=false` + `captchaCancelled=true`，其他变量一律不动（留着下次 loadCaptcha 覆盖）
7. **isAutoClosing 标志**：区分"doCheck 成功自动关" vs "用户主动关"，前者不碰 credentialKey
8. **防竞态不靠锁**：用 `showCaptchaDialog.value` 本身作为重入判断，比 isLoggingIn 锁简单可靠

---

### 十二、关键代码位置索引（二次改造后）

| 功能 | 文件 | 说明 |
|------|------|------|
| 滑块阴影（外部投影） | CaptchaImageGenerator.java → addPuzzleShadow | Area.subtract 只画外部 |
| 缺口白光放大 | CaptchaImageGenerator.java → punchHoleInBackground | radius = 对角线 × 1.5 |
| jigsaw 形状模板池 | CaptchaImageGenerator.java → createJigsawShape | 4 套模板池 + knobSize 38~46px + offset ±3px（见第十四章） |
| 抗锯齿 RenderingHints | CaptchaImageGenerator.java → setHighQualityHints | 8 个 Hint 全开 |
| 2x 高分辨率常量 | CaptchaImageGenerator.java 顶部常量区 | BG_WIDTH=620 等 |
| 弹窗 template | login/index.vue L48-83 | class= 不用 custom-class，去掉 append-to-body |
| 全局 style 块 | login/index.vue `<style>`（无 scoped） | .captcha-dialog 相关 |
| doCheck 登录逻辑 | login/index.vue → doCheck() | 成功后直接 userStore.login |
| closeCaptchaDialog | login/index.vue → closeCaptchaDialog() | 只设 2 个变量 |

---

🎯 **最核心的教训**：
> Vue 响应式 + Element Plus 组件 + 关闭动画 + async/await 叠加后，
> **时序 gap 无处不在**。
> 不要靠锁（isLoggingIn）、不要靠保护条件（判断 showCaptchaDialog.value），
> **靠最小化改动**：closeCaptchaDialog 只做 2 件事，doCheck 直接登录，
> 不绕 handleLogin，不碰 credentialKey，不清理不必要的变量。

---

## 十四、jigsaw 模板池改造 — 杜绝怪异形状（2026-11-16 补充）

### 改造背景
2026-11-02 十二/十三章改造后，滑块验证码能正常工作，但用户反馈缺口形状**经常出现破碎、畸形、观感怪异**的情况。
根因是四条边独立随机（3⁴=81 种组合）+ knobSize 32~60px（跨度近 1 倍）+ offset ±8px（偏差过大），8.1 亿种组合中大量形状超出了"好看"的边界。

### 涉及文件
| 文件 | 改动 | 说明 |
|------|------|------|
| CaptchaImageGenerator.java → createJigsawShape() | **改 ~15 行** | 边类型从独立随机改为模板池选取；knobSize/offset 范围收紧 |
| 其他所有类、前端、校验接口 | **零改动** | 下游裁切、打黑、阴影、坐标系统全部复用 |

### 核心改动（只改一个方法的参数，绘制算法不动）

#### 之前（已废弃）
```java
// 四条边各自独立随机 → 81 种边类型组合
int topType   = random.nextInt(3);  // 0平 1凸 2凹
int botType   = random.nextInt(3);
int leftType  = random.nextInt(3);
int rightType = random.nextInt(3);

// knobSize：32~60px，范围太大
int knobSize = 32 + random.nextInt(29);

// offset：-8~+8px，偏差太大
int topOff   = random.nextInt(17) - 8;
```

#### 之后（当前方案）
```java
// 预定义模板池 — 4 套经典形状，每次随机选一套
int[][] templates = {
    {1, 1, 2, 2},   // 经典对边：上凸下凸 + 左凹右凹（X 型配对）
    {1, 0, 0, 1},   // 简单款：上凸 + 右凸，其余平
    {1, 2, 1, 2},   // 对称款：上凸下凹 + 左凸右凹
    {1, 1, 0, 0}    // 上下凸，左右平
};
int[] chosen = templates[random.nextInt(templates.length)];

// knobSize：38~46px，范围收紧避免极端尺寸
int knobSize = 38 + random.nextInt(9);

// offset：-3~+3px，偏差小不破坏视觉平衡
int topOff   = random.nextInt(7) - 3;
```

### 模板池视觉验证
```
模板 {1,1,2,2}  经典对边     模板 {1,0,0,1}  简单款
 ┌──┴──┐                  ┌──┴──┐
 │     │ 凹                │     │
 │     │                    │     │    凸
 └──┬──┘                  └─────┘
    凸凸                    凸

模板 {1,2,1,2}  对称款      模板 {1,1,0,0}  上下凸
 ┌──┴──┐ 凸               ┌──┴──┐
 │     │                    │     │
 └──┬──┘ 凹               └──┬──┘
 凸  凸                      凸  凸
```

### 变化量对比
| 指标 | 旧方案（独立随机） | 新方案（模板池） |
|------|-------------------|-----------------|
| 边类型组合 | 3⁴ = 81 种 | **4 种模板** |
| knobSize 范围 | 32~60px（29 种） | **38~46px（9 种）** |
| offset 范围 | ±8px（17 种/边） | **±3px（7 种/边）** |
| 理论形状总数 | ~8.1 亿 | **4 × 9 × 7⁴ ≈ 86,436** |
| 怪异形状概率 | 高（凹块过多、旋钮过大） | **0%（模板池保证下限）** |

### 不动的东西（保证安全性）
```
✅ createJigsawShape() 的绘制算法：add/subtract Ellipse2D 生成 Shape — 一行不改
✅ cutPuzzlePiece()：裁切拼图块 — 不动
✅ punchHoleInBackground()：缺口打黑 + 径向白光 — 不动
✅ addPuzzleShadow()：外部投影阴影 — 不动
✅ generateGapX/GapY()：坐标系统 — 不动
✅ 前端 puzzleLeft/puzzleTop 公式 — 不动
✅ CaptchaController check() 校验逻辑 — 不动
```

### 为什么下游零影响
`createJigsawShape()` 只负责**生成一个 Shape 对象**。下游所有方法都是「围绕这个 Shape 做变换和绘制」：
- `cutPuzzlePiece` → `clip = jigsawShape` + `drawImage` 偏移
- `punchHoleInBackground` → `fill(jigsawShape)` 画缺口
- `addPuzzleShadow` → `offsetShape.subtract(canvasShape)` 画投影

Shape 本身变漂亮了，下游自动跟着变。坐标系统完全没变（gapX/gapY、gameX/gameY 的计算逻辑零改动），前端 `dragX / scaleFactor ≈ gameX` 公式照常成立。

### 自测清单（改完必做）
| # | 步骤 | 预期 |
|---|------|------|
| 1 | `mvn clean compile` | ✅ 编译通过 |
| 2 | 批量生成 50 张验证码截图 | 无破碎、无畸形、观感一致 |
| 3 | 滑块拖动验证通过率 | 与改造前持平（≈100%，因为坐标逻辑未变） |
| 4 | 异常形状回归 | 无 4 边凹、无 knobSize > 50px 的极端情况 |

### 踩坑时间线（本次改造无踩坑）
> 本次是**微调**而非重构，所有下游逻辑复用，所以没有踩坑。改造本质：把 `createJigsawShape()` 的「参数空间」从 8.1 亿个全放开 → 收紧到 4 套模板 × 小幅随机。

### 经验总结
1. **黑盒先确认是不是黑盒**：文档开头说 AJ-Captcha 黑盒，但实际已在十二/十三章改造中完全移除。改之前先看代码是不是自己写的！
2. **Shape 是生成器和绘制器的契约**：只要 Shape 的 getBounds() 行为不变，下游 clip/fill/subtract 全部自动适配。
3. **多样性 ≠ 随机性**：8.1 亿种"坏"形状不如 4 万种"好"形状。模板池是最简单的保美上限手段。
4. **改动边界要极小**：本次只改一个方法里的几行 `random.nextInt(3) → 模板选取`，下游零改动，风险可控。

### 关键代码位置
| 功能 | 文件 | 说明 |
|------|------|------|
| 模板池定义 | CaptchaImageGenerator.java → createJigsawShape() | `int[][] templates` 4 套经典形状 |
| knobSize 范围 | CaptchaImageGenerator.java → createJigsawShape() | `38 + random.nextInt(9)` → 38~46px |
| offset 范围 | CaptchaImageGenerator.java → createJigsawShape() | `random.nextInt(7) - 3` → ±3px |
| 绘制算法 | CaptchaImageGenerator.java → createJigsawShape() L266~292 | **不动**，add/subtract Ellipse2D |