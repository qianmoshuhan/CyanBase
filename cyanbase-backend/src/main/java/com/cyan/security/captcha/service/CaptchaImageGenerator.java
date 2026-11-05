package com.cyan.security.captcha.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Java2D 程序化滑块验证码图片生成器。
 * <p>完全替代 AJ-Captcha 的外部 jigsaw 目录图片加载逻辑，零外部资源依赖。</p>
 * <p>尺寸规格（字节码确认）：</p>
 * <ul>
 *   <li>底图: 310×155 — 与 AJ jar 内置 defaultImages/jigsaw/original/*.png 一致</li>
 *   <li>拼图块: 47×47 正方形 — 修复原 47×155 细长模板导致的 gapY 恒为 5 的 bug</li>
 * </ul>
 * <p>绘图风格：低饱和渐变底色 + 随机矩形/圆形/三角形/线条 — 纯几何，无实拍内容。</p>
 */
@Slf4j
@Component
public class CaptchaImageGenerator {

    /**
     * 底图尺寸 — 2x 高分辨率（原生 310×155 × 2 = 620×310）。
     * <p>前端 CSS 缩放到 360×180 显示，down-scale 永远清晰无模糊无毛刺。</p>
     */
    public static final int BG_WIDTH = 620;
    public static final int BG_HEIGHT = 310;

    /** 拼图块尺寸 — 94×94（47 × 2） */
    public static final int PIECE_SIZE = 94;

    /** gapX 左侧最小边距 — 200（100 × 2） */
    public static final int MIN_GAP_X = 200;

    /** gapY 顶部最小边距 — 10（5 × 2） */
    public static final int MIN_GAP_Y = 10;

    /** 拼图块画布 padding = MIN_GAP_Y = 10，scanJigsaw gapX ≈ 10 */
    public static final int PIECE_PADDING = MIN_GAP_Y;

    /** 答案缓存 TTL — AbstractCaptchaService.EXPIRESIN_SECONDS = 120 */
    public static final long CACHE_TTL_SECONDS = 120L;

    private final SecureRandom random = new SecureRandom();

    @PostConstruct
    public void init() {
        log.info("🎨 CaptchaImageGenerator 初始化完成: bg={}x{}, piece={}x{} (2x 高清模式)",
                     BG_WIDTH, BG_HEIGHT, PIECE_SIZE, PIECE_SIZE);
    }

    /**
     * 滑块验证码结果
     */
    public record CaptchaResult(
        String token,           // UUID 去横线
        String secretKey,       // 16 字符 AES key [a-zA-Z0-9]
        String backgroundImage, // 带缺口的底图 Base64 (PNG)
        String puzzleImage,     // 拼图块 Base64 (PNG)
        int gameX,              // 缺口 X（底图坐标系）
        int gameY               // 缺口 Y（底图坐标系）
    ) {}

    /**
     * 生成一次完整的滑块验证码。
     */
    public CaptchaResult generate(CaptchaCryptoService crypto) {
        // 1. 生成 token（UUID 去横线）— 与 AJ RandomUtils.getUUID() 行为一致
        String token = UUID.randomUUID().toString().replace("-", "");

        // 2. 生成 secretKey（16 字符 [a-zA-Z0-9]）
        String secretKey = crypto.generateKey();

        // 3. 先生成 jigsaw 形状（为了拿到 bounds）
        Shape jigsawShape = createJigsawShape(PIECE_SIZE);
        Rectangle jigsawBounds = jigsawShape.getBounds();
        
        // 4. 生成答案坐标 — gapX/gapY 都随机，缺口位置多样化
        int gapX = generateGapX(jigsawBounds);
        int gapY = generateGapY(jigsawBounds);
        log.debug("CAPTCHA jigsaw bounds={} gapX={} gapY={}", jigsawBounds, gapX, gapY);

        // 5. 程序化绘制底图
        BufferedImage bgImage = drawBackground();

        // 坐标语义统一：gapX, gapY = 方形主体在底图的位置
        // jigsawShape 方形主体永远在 (0,0)→(PIECE_SIZE,PIECE_SIZE)
        // jigsawBounds 可能有负坐标（凸起超出左/上边界）

        // 6. 裁切拼图块
        BufferedImage puzzlePiece = cutPuzzlePiece(bgImage, jigsawShape, jigsawBounds, gapX, gapY);

        // 7. 底图缺口位置打黑 + 白光 + 白色描边（视觉标记）
        punchHoleInBackground(bgImage, jigsawShape, jigsawBounds, gapX, gapY);

        // 8. 滑块阴影（增强立体感）— 只有半透明阴影，不额外加颜色
        addPuzzleShadow(puzzlePiece, jigsawShape, jigsawBounds);

        // 9. Base64 编码
        String bgBase64 = toBase64Png(bgImage);
        String puzzleBase64 = toBase64Png(puzzlePiece);

        // 返回给前端的 gameX/gameY = bounds 左上角在底图的 X/Y（都随机）
        int gameX = gapX + jigsawBounds.x;
        int gameY = gapY + jigsawBounds.y;
        log.debug("CAPTCHA answer for frontend: gameX={} gameY={} (bounds={})", gameX, gameY, jigsawBounds);
        return new CaptchaResult(token, secretKey, bgBase64, puzzleBase64, gameX, gameY);
    }

    // ============== 答案坐标生成 ==============

    /**
     * gapX ∈ [MIN_GAP_X, BG_WIDTH - jigsawBounds.width - safety]
     * gapX 是拼图块 bounds 左上角在底图的位置，不是方形主体位置。
     */
    private int generateGapX(Rectangle jigsawBounds) {
        // bounds.x 可能为负（left outward 凸起），gapX 要保证整个 bounds 都在底图内
        int maxGapX = BG_WIDTH - jigsawBounds.width - 1;
        int minGapX = Math.max(MIN_GAP_X, -jigsawBounds.x + 5);  // 保证凸起不超出左边
        int range = maxGapX - minGapX;
        if (range <= 0) return MIN_GAP_X;
        return minGapX + random.nextInt(range + 1);
    }

    /**
     * gapY ∈ [MIN_GAP_Y, BG_HEIGHT - jigsawBounds.height - safety]
     * gapY 是拼图块 bounds 左上角在底图的位置。
     */
    private int generateGapY(Rectangle jigsawBounds) {
        int maxGapY = BG_HEIGHT - jigsawBounds.height - 1;
        int minGapY = Math.max(MIN_GAP_Y, -jigsawBounds.y + 5);
        int range = maxGapY - minGapY;
        if (range <= 0) return MIN_GAP_Y;
        return minGapY + random.nextInt(range + 1);
    }

    // ============== 底图绘制 ==============

    /**
     * 亮色渐变底 + 3-5 个大几何图形（圆/三角/半圆），干净清爽。
     */
    private BufferedImage drawBackground() {
        BufferedImage image = new BufferedImage(BG_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        try {
            setHighQualityHints(g2d);

            drawGradientBackground(g2d);

            // 3-5 个大几何图形，不同颜色填充，干净不杂乱
            int shapeCount = 3 + random.nextInt(3);
            for (int i = 0; i < shapeCount; i++) {
                drawBigShape(g2d);
            }

        } finally {
            g2d.dispose();
        }
        return image;
    }

    /**
     * 亮色渐变 — bright 0.85-1.0，几乎纯白系打底，再叠彩色图形。
     */
    private void drawGradientBackground(Graphics2D g2d) {
        float hue1 = random.nextFloat();
        float hue2 = (hue1 + 0.15f + random.nextFloat() * 0.25f) % 1.0f;
        float sat1 = 0.05f + random.nextFloat() * 0.12f;   // 0.05-0.17 极浅饱和
        float sat2 = 0.05f + random.nextFloat() * 0.12f;
        float bright1 = 0.88f + random.nextFloat() * 0.12f; // 0.88-1.00 亮白
        float bright2 = 0.88f + random.nextFloat() * 0.12f;

        Color color1 = Color.getHSBColor(hue1, sat1, bright1);
        Color color2 = Color.getHSBColor(hue2, sat2, bright2);

        GradientPaint gradient = new GradientPaint(0, 0, color1, BG_WIDTH, BG_HEIGHT, color2);
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, BG_WIDTH, BG_HEIGHT);
    }

    /**
     * 大几何图形（圆形、三角形、半圆、椭圆），高饱和鲜艳色，alpha 叠加。
     */
    private void drawBigShape(Graphics2D g2d) {
        int type = random.nextInt(4);  // 0圆 1三角 2半圆 3椭圆
        float hue = random.nextFloat();
        float sat = 0.55f + random.nextFloat() * 0.40f;   // 0.55-0.95 鲜艳
        float bright = 0.55f + random.nextFloat() * 0.35f; // 0.55-0.90 中亮
        Color color = Color.getHSBColor(hue, sat, bright);

        int size = 40 + random.nextInt(60);  // 40-100px
        int x = random.nextInt(BG_WIDTH - size);
        int y = random.nextInt(BG_HEIGHT - size);

        g2d.setColor(color);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f + random.nextFloat() * 0.30f));

        switch (type) {
            case 0 -> g2d.fillOval(x, y, size, size);  // 圆
            case 1 -> {  // 三角形
                int[] xs = {x + size / 2, x, x + size};
                int[] ys = {y, y + size, y + size};
                g2d.fillPolygon(xs, ys, 3);
            }
            case 2 -> {  // 半圆（上半）
                Arc2D arc = new Arc2D.Double(x, y, size, size, 0, 180, Arc2D.CHORD);
                g2d.fill(arc);
            }
            case 3 -> g2d.fillOval(x, y, size, size / 2);  // 椭圆
        }
        g2d.setComposite(AlphaComposite.SrcOver);
    }

    // ============== Jigsaw 形状 & 裁切 ==============

    /**
     * 创建多样化 jigsaw 形状 — 四条边（上下左右）各自独立随机。
     * <p>每条边 3 种状态：0=平、1=凸(knob, add)、2=凹(hole, subtract)</p>
     * <p>knobSize 大范围随机（16-30px），位置带 ±4px 偏移，不总在正中。</p>
     * <p>subtract 安全性保证：椭圆圆心正好在方形边界上，只会挖掉边上的半圆坑，不会穿透！</p>
     * <p>变化量：3^4(边状态) × 15(大小) × 9^4(位置偏移) ≈ 100万+ 种形状</p>
     */
    private Shape createJigsawShape(int pieceSize) {
        int cx = pieceSize / 2;
        int cy = pieceSize / 2;

        Area area = new Area(new Rectangle(0, 0, pieceSize, pieceSize));

        // 四条边独立随机状态
        int topType   = random.nextInt(3);  // 0平 1凸 2凹
        int botType   = random.nextInt(3);
        int leftType  = random.nextInt(3);
        int rightType = random.nextInt(3);

        // 全平太无聊 → 保底强制一条边凸
        if (topType == 0 && botType == 0 && leftType == 0 && rightType == 0) {
            int force = random.nextInt(4);
            if (force == 0) topType = 1;
            else if (force == 1) botType = 1;
            else if (force == 2) leftType = 1;
            else rightType = 1;
        }

        // knob 大小：32-60px（16~30 × 2），大范围变化
        int knobSize = 32 + random.nextInt(29);
        // 每条边独立位置偏移：-8 到 +8（×2）
        int topOff   = random.nextInt(17) - 8;
        int botOff   = random.nextInt(17) - 8;
        int leftOff  = random.nextInt(17) - 8;
        int rightOff = random.nextInt(17) - 8;

        // ===== 上边：椭圆圆心正好在 y=0 边界 =====
        if (topType == 1) {
            area.add(new Area(new Ellipse2D.Double(cx + topOff - knobSize/2.0, -knobSize/2.0, knobSize, knobSize)));
        } else if (topType == 2) {
            area.subtract(new Area(new Ellipse2D.Double(cx + topOff - knobSize/2.0, -knobSize/2.0, knobSize, knobSize)));
        }

        // ===== 下边：椭圆圆心正好在 y=pieceSize 边界 =====
        if (botType == 1) {
            area.add(new Area(new Ellipse2D.Double(cx + botOff - knobSize/2.0, pieceSize - knobSize/2.0, knobSize, knobSize)));
        } else if (botType == 2) {
            area.subtract(new Area(new Ellipse2D.Double(cx + botOff - knobSize/2.0, pieceSize - knobSize/2.0, knobSize, knobSize)));
        }

        // ===== 左边：椭圆圆心正好在 x=0 边界 =====
        if (leftType == 1) {
            area.add(new Area(new Ellipse2D.Double(-knobSize/2.0, cy + leftOff - knobSize/2.0, knobSize, knobSize)));
        } else if (leftType == 2) {
            area.subtract(new Area(new Ellipse2D.Double(-knobSize/2.0, cy + leftOff - knobSize/2.0, knobSize, knobSize)));
        }

        // ===== 右边：椭圆圆心正好在 x=pieceSize 边界 =====
        if (rightType == 1) {
            area.add(new Area(new Ellipse2D.Double(pieceSize - knobSize/2.0, cy + rightOff - knobSize/2.0, knobSize, knobSize)));
        } else if (rightType == 2) {
            area.subtract(new Area(new Ellipse2D.Double(pieceSize - knobSize/2.0, cy + rightOff - knobSize/2.0, knobSize, knobSize)));
        }

        return area;
    }

    /**
     * 用 jigsaw 形状从底图裁切拼图块（透明背景 + PADDING）。
     * <p>画布 = bounds + 2*PADDING，clip 让 jigsaw 对齐画布 (PADDING, PADDING)。</p>
     * <p>drawImage 偏移 = (PADDING - gameX, PADDING - gameY)，让画布 clip 区域 = 底图缺口区域。</p>
     */
    private BufferedImage cutPuzzlePiece(BufferedImage bgImage, Shape jigsawShape,
                                          Rectangle bounds, int gapX, int gapY) {
        int gameX = gapX + bounds.x;
        int gameY = gapY + bounds.y;
        int canvasW = bounds.width + 2 * PIECE_PADDING;
        int canvasH = bounds.height + 2 * PIECE_PADDING;
        BufferedImage puzzlePiece = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = puzzlePiece.createGraphics();

        try {
            setHighQualityHints(g2d);
            // clip: jigsaw bounds 对齐画布 (PADDING, PADDING)
            Shape canvasClip = AffineTransform.getTranslateInstance(PIECE_PADDING - bounds.x,
                                                                     PIECE_PADDING - bounds.y)
                    .createTransformedShape(jigsawShape);
            g2d.setClip(canvasClip);
            // drawImage: dx = PADDING - gameX, dy = PADDING - gameY（drawImage 必须用 clip 限制范围）
            g2d.drawImage(bgImage, PIECE_PADDING - gameX, PIECE_PADDING - gameY, null);

            // 诊断日志
            log.info("🎨 CUT: canvas={}x{} clipBounds=({},{})-({},{}) drawOffset=({},{}) → 对应底图缺口=({},{})-({},{})",
                    canvasW, canvasH,
                    PIECE_PADDING, PIECE_PADDING, PIECE_PADDING + bounds.width, PIECE_PADDING + bounds.height,
                    PIECE_PADDING - gameX, PIECE_PADDING - gameY,
                    gameX, gameY, gameX + bounds.width, gameY + bounds.height);
        } finally {
            g2d.dispose();
        }

        return puzzlePiece;
    }

    /**
     * 在底图缺口位置绘制深色区域 + jigsaw 描边。
     * <p>必须用 jigsawShape clip 让缺口形状和拼图块匹配！</p>
     * <p>translate(gapX, gapY): jigsawShape(jx, jy) → 底图(gapX+jx, gapY+jy)</p>
     */
    private void punchHoleInBackground(BufferedImage bgImage, Shape jigsawShape,
                                         Rectangle bounds, int gapX, int gapY) {
        int gameX = gapX + bounds.x;
        int gameY = gapY + bounds.y;
        Graphics2D g2d = bgImage.createGraphics();

        try {
            setHighQualityHints(g2d);
            g2d.translate(gapX, gapY);

            log.info("🎯 PUNCH: translate=({},{}) jigsawBounds=({},{})-({},{}) → 缺口底图位置=({},{})-({},{}) gameX={} gameY={}",
                    gapX, gapY, bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height,
                    gameX, gameY, gameX + bounds.width, gameY + bounds.height, gameX, gameY);

            // 关键改进：直接 fill(jigsawShape) 替代 setClip + fillRect
            // Shape 边缘天然抗锯齿，不需要 clip 二次裁切！

            // 1. 纯黑底 — 直接填充 jigsaw 形状
            g2d.setColor(Color.BLACK);
            g2d.fill(jigsawShape);

            // 2. 径向渐变白光 — 放大到 1.5 倍对角线，更亮更柔和
            int cx = bounds.x + PIECE_SIZE / 2;
            int cy = bounds.y + PIECE_SIZE / 2;
            float radius = (float) Math.hypot(bounds.width, bounds.height) * 1.5f;
            RadialGradientPaint shine = new RadialGradientPaint(
                    cx, cy, radius,
                    new float[]{0f, 0.3f, 0.7f, 1f},
                    new Color[]{new Color(255, 255, 255, 200), new Color(255, 255, 255, 120), new Color(255, 255, 255, 30), new Color(0, 0, 0, 0)}
            );
            g2d.setPaint(shine);
            g2d.fill(jigsawShape);  // 直接 fill 形状，边缘平滑无毛刺！
        } finally {
            g2d.dispose();
        }
    }

    /**
     * 滑块外部投影阴影 — 只在 jigsaw 形状外部画右下偏移的柔和阴影，
     * jigsaw 内部完全保持底图原始颜色，不被任何东西覆盖！
     * <p>实现：偏移后的 jigsaw 形状 减去 原始 jigsaw 形状 = 外部阴影区</p>
     */
    private void addPuzzleShadow(BufferedImage puzzlePiece, Shape jigsawShape, Rectangle bounds) {
        Graphics2D g2d = puzzlePiece.createGraphics();
        try {
            setHighQualityHints(g2d);
            AffineTransform toCanvas = AffineTransform.getTranslateInstance(PIECE_PADDING - bounds.x,
                                                                            PIECE_PADDING - bounds.y);
            Shape canvasShape = toCanvas.createTransformedShape(jigsawShape);

            int offsetX = 8;  // 4 × 2（高分辨率阴影偏移）
            int offsetY = 8;

            // 阴影区域 = 偏移后的 jigsaw 形状 - 原始 jigsaw 形状
            Area shadowRegion = new Area(
                    AffineTransform.getTranslateInstance(offsetX, offsetY).createTransformedShape(canvasShape)
            );
            shadowRegion.subtract(new Area(canvasShape));

            // 柔和径向渐变阴影
            Rectangle sb = canvasShape.getBounds();
            int cx = sb.x + sb.width / 2 + offsetX;
            int cy = sb.y + sb.height / 2 + offsetY;
            float radius = Math.max(sb.width, sb.height) * 0.9f;

            RadialGradientPaint shadowGrad = new RadialGradientPaint(
                    cx, cy, radius,
                    new float[]{0.3f, 1f},
                    new Color[]{new Color(0, 0, 0, 85), new Color(0, 0, 0, 0)}
            );
            g2d.setPaint(shadowGrad);
            // 直接 fill(shadowRegion) 替代 setClip + fillRect — 边缘天然平滑！
            g2d.fill(shadowRegion);
        } finally {
            g2d.dispose();
        }
    }

    /**
     * 为 Graphics2D 设置完整的高质量渲染 hints — 消除形状边缘锯齿/毛刺。
     */
    private void setHighQualityHints(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /**
     * 生成向中心内缩 shrinkPx 像素的 jigsaw 形状 — 用于做纯内部描边。
     */
    private Shape createShrunkenJigsaw(Shape jigsaw, int shrinkPx) {
        Area outer = new Area(jigsaw);
        Area inner = new Area(jigsaw);
        // 用 Rectangle2D 收缩 bounds，然后 clip
        Rectangle b = jigsaw.getBounds();
        int ix = b.x + shrinkPx;
        int iy = b.y + shrinkPx;
        int iw = Math.max(1, b.width - 2 * shrinkPx);
        int ih = Math.max(1, b.height - 2 * shrinkPx);
        inner.intersect(new Area(new java.awt.Rectangle(ix, iy, iw, ih)));
        return inner;
    }

    // ============== 编码 ==============

    /**
     * BufferedImage → PNG → Base64（去 \r\n — 与 AJ 字节码 .replaceAll("\r|\n", "") 一致）。
     */
    private String toBase64Png(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return base64.replaceAll("[\r\n]", "");
        } catch (IOException e) {
            throw new RuntimeException("PNG 编码失败", e);
        }
    }
}