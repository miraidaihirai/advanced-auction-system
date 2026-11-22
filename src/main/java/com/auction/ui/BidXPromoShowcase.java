package com.auction.ui;

import java.util.Random;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Trình chiếu Video Quảng Cáo Motion-Graphics 60 FPS trực tiếp cho BidX (dùng trên Login & Register).
 * Hiệu ứng Ken Burns trên ảnh studio thật + sóng cực quang neon đa sắc + lưới cyber 3D
 * + tia sáng lăng kính + đường giá realtime sống động + thẻ bid bay nổi bật.
 * Click vào khung video để chuyển nhanh sang cảnh tiếp theo!
 */
public class BidXPromoShowcase extends StackPane {

    private static final double W = 560;
    private static final double H = 640;
    private static final double SCENE_DURATION = 4.6;

    private record PromoScene(
            String imageKey,
            String badge,
            String headline,
            String subline,
            String priceText,
            String accentHex,
            String accentHex2,
            String[] floatingBubbles
    ) {
    }

    private static final PromoScene[] SCENES = {
            new PromoScene(
                    "item_watch_skeleton",
                    "LIVE • TOURBILLON ROYAL",
                    "ĐẤU GIÁ ĐỈNH CAO\nTHỜI GIAN THỰC",
                    "Nhảy giá tức thì mili-giây • Đường giá trực tuyến siêu mượt",
                    "34.500.000 đ",
                    "#FF7B24",
                    "#EC4899",
                    new String[]{"Xuân Thắng  +5.000.000 đ", "Huy Hưng  +10.000.000 đ", "⚡ Dẫn đầu mới: 34.5M đ"}
            ),
            new PromoScene(
                    "item_camera_leica",
                    "⚡ ANTI-SNIPING +30S",
                    "CÔNG NGHỆ CHỐNG\nCƯỚP GIÂY CUỐI",
                    "Đặt giá trong 30 giây cuối tự động gia hạn thêm thời gian công bằng",
                    "18.500.000 đ",
                    "#38BDF8",
                    "#A855F7",
                    new String[]{"⏱ 00:00:08 → +30s GIA HẠN!", "Phạm Trường • Leica M3", "⚡ Auto-Bid đang dẫn đầu"}
            ),
            new PromoScene(
                    "item_vehicle_porsche",
                    "★ SÀN CỔ VẬT & SIÊU XE",
                    "BỘ SƯU TẬP ĐỘC BẢN\nĐÃ QUA KIỂM ĐỊNH",
                    "Đồng hồ Thụy Sĩ, máy ảnh cổ, cổ vật nghệ thuật và xe sưu tầm đỉnh cao",
                    "195.000.000 đ",
                    "#FBBF24",
                    "#F43F5E",
                    new String[]{"★ 18 người đang theo dõi", "♦ Đã xác minh chính hãng", "▲ Huy Hưng vừa nâng +15M"}
            ),
            new PromoScene(
                    "item_watch_classic",
                    "💰 QUYẾT TOÁN TỨC THÌ",
                    "CHỐT PHIÊN THÀNH CÔNG\nTIỀN CHẢY VỀ VÍ NGAY",
                    "Hệ thống quyết toán minh bạch tự động chuyển doanh thu trực tiếp cho Seller",
                    "+42.000.000 đ",
                    "#10B981",
                    "#38BDF8",
                    new String[]{"✓ +42.000.000 đ → Ví Tuấn Đại", "✓ +31.500.000 đ → Ví Phạm Trường", "★ 100% An toàn & Minh bạch"}
            )
    };

    private static final class SparkParticle {
        double x, y, vx, vy, size, alpha, hueShift;
    }

    private final Canvas canvas;
    private final Image[] sceneImages;
    private final Image brandLogo;
    private final SparkParticle[] particles = new SparkParticle[48];
    private final Random rng = new Random(42);

    private AnimationTimer timer;
    private long startNanos = -1;
    private int manualOffset = 0;
    private double flashAlpha = 0.0;

    public BidXPromoShowcase() {
        setPrefSize(W, H);
        setMinSize(500, 580);
        setMaxSize(W, H);

        canvas = new Canvas(W, H);
        getChildren().add(canvas);

        Rectangle clip = new Rectangle(W, H);
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        setClip(clip);

        setStyle("-fx-background-color: #0B1020; -fx-background-radius: 16; -fx-cursor: hand;");
        setEffect(new DropShadow(34, Color.rgb(255, 107, 43, 0.28)));

        sceneImages = new Image[SCENES.length];
        for (int i = 0; i < SCENES.length; i++) {
            sceneImages[i] = ProductImageService.getImage("PROMO-" + i, SCENES[i].headline(), SCENES[i].imageKey(), W, H);
        }
        brandLogo = ProductImageService.getBrandLogo(96);

        for (int i = 0; i < particles.length; i++) {
            particles[i] = spawnParticle(true);
        }

        // Click để chuyển cảnh promo ngay với hiệu ứng chớp sáng neon
        setOnMouseClicked(e -> {
            manualOffset++;
            flashAlpha = 0.55;
            SoundEffects.playReactionTap();
        });

        startLoop();
    }

    private SparkParticle spawnParticle(boolean randomY) {
        SparkParticle p = new SparkParticle();
        p.x = rng.nextDouble() * W;
        p.y = randomY ? rng.nextDouble() * H : H + 10 + rng.nextDouble() * 40;
        p.vx = (rng.nextDouble() - 0.5) * 26;
        p.vy = -(22 + rng.nextDouble() * 48);
        p.size = 1.8 + rng.nextDouble() * 3.6;
        p.alpha = 0.35 + rng.nextDouble() * 0.6;
        p.hueShift = rng.nextDouble() * 360.0;
        return p;
    }

    public void startLoop() {
        if (timer != null) {
            return;
        }
        timer = new AnimationTimer() {
            private long lastFrame = -1;

            @Override
            public void handle(long now) {
                if (startNanos < 0) {
                    startNanos = now;
                    lastFrame = now;
                }
                double dt = Math.min(0.05, (now - lastFrame) / 1_000_000_000.0);
                lastFrame = now;
                double elapsed = (now - startNanos) / 1_000_000_000.0;
                renderFrame(elapsed, dt);
            }
        };
        timer.start();
    }

    public void stopLoop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    private void renderFrame(double t, double dt) {
        GraphicsContext g = canvas.getGraphicsContext2D();

        double totalSlot = t / SCENE_DURATION + manualOffset;
        int sceneIdx = ((int) Math.floor(totalSlot)) % SCENES.length;
        if (sceneIdx < 0) {
            sceneIdx += SCENES.length;
        }
        int nextIdx = (sceneIdx + 1) % SCENES.length;
        double progress = totalSlot - Math.floor(totalSlot); // 0.0 -> 1.0

        PromoScene current = SCENES[sceneIdx];
        Color accent1 = Color.web(current.accentHex());
        Color accent2 = Color.web(current.accentHex2());

        // 1. Nền obsidian sâu
        g.setFill(Color.web("#070B14"));
        g.fillRect(0, 0, W, H);

        // 2. Vẽ ảnh Studio với chuyển động Ken Burns zoom + pan mượt & crossfade cuối cảnh
        drawKenBurnsImage(g, sceneImages[sceneIdx], progress, 1.0);
        if (progress > 0.84) {
            double fade = (progress - 0.84) / 0.16;
            drawKenBurnsImage(g, sceneImages[nextIdx], 0.0, fade);
        }

        // 3. Lớp phủ cực quang màu mè (Aurora Cinema Color Grade)
        RadialGradient auroraTop = new RadialGradient(
                0, 0,
                0.22 + 0.12 * Math.sin(t * 0.9),
                0.18 + 0.08 * Math.cos(t * 0.7),
                0.75, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(accent1.getRed(), accent1.getGreen(), accent1.getBlue(), 0.34)),
                new Stop(0.55, Color.color(accent2.getRed(), accent2.getGreen(), accent2.getBlue(), 0.16)),
                new Stop(1, Color.TRANSPARENT)
        );
        g.setFill(auroraTop);
        g.fillRect(0, 0, W, H);

        // Gradient tối điện ảnh phía trên & dưới để chữ nổi bật tuyệt đối
        LinearGradient vignette = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(6, 10, 20, 0.82)),
                new Stop(0.24, Color.rgb(6, 10, 20, 0.18)),
                new Stop(0.55, Color.rgb(7, 11, 22, 0.42)),
                new Stop(0.78, Color.rgb(7, 11, 22, 0.90)),
                new Stop(1.0, Color.rgb(6, 9, 18, 0.98))
        );
        g.setFill(vignette);
        g.fillRect(0, 0, W, H);

        // 4. Lưới Cyber 3D chuyển động phía dưới khung hình
        drawCyberGrid(g, t, accent1, accent2);

        // 5. Tia lăng kính (Prism Light Sweep) quét chéo sang trọng
        double sweepX = ((t * 170) % (W + 360)) - 180;
        g.save();
        g.setGlobalAlpha(0.14);
        LinearGradient prism = new LinearGradient(sweepX, 0, sweepX + 120, H * 0.55, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.35, accent1),
                new Stop(0.65, accent2),
                new Stop(1, Color.TRANSPARENT));
        g.setFill(prism);
        g.fillPolygon(
                new double[]{sweepX, sweepX + 115, sweepX - 70, sweepX - 185},
                new double[]{0, 0, H, H},
                4);
        g.restore();

        // 6. Hạt ánh sáng neon bay lơ lửng (Bokeh & Sparks)
        updateAndDrawParticles(g, t, dt, accent1, accent2);

        // 7. Sóng biểu đồ giá realtime động phát sáng
        drawLivePriceWave(g, t, accent1, accent2);

        // 8. Thanh tiến trình 4 cảnh kiểu Story/Reel ở trên cùng
        drawTopProgressBars(g, sceneIdx, progress, accent1, accent2);

        // 9. Brand Header với Logo 3D mới + Badge LIVE REEL
        drawBrandHeader(g, t, current, accent1, accent2);

        // 10. Thẻ thông báo đấu giá nổi động bay nhẹ bên phải
        drawFloatingBidBubbles(g, t, progress, current, accent1, accent2);

        // 11. Khối tiêu đề điện ảnh + Giá động + Dấu ấn nhóm phát triển
        drawBottomTypography(g, t, progress, current, accent1, accent2);

        // 12. Viền neon xoay màu quanh toàn khung video
        drawAnimatedBorder(g, t, accent1, accent2);

        // 13. Hiệu ứng chớp sáng khi người dùng click chuyển cảnh
        if (flashAlpha > 0.01) {
            g.setFill(Color.color(1, 1, 1, flashAlpha));
            g.fillRect(0, 0, W, H);
            flashAlpha = Math.max(0, flashAlpha - dt * 2.4);
        }
    }

    private void drawKenBurnsImage(GraphicsContext g, Image img, double progress, double alpha) {
        if (img == null) {
            return;
        }
        g.save();
        g.setGlobalAlpha(alpha);
        double zoom = 1.04 + progress * 0.14;
        double drawW = W * zoom;
        double drawH = H * 0.72 * zoom;
        double offsetX = -(drawW - W) * (0.25 + progress * 0.5);
        double offsetY = 24 - (drawH - H * 0.68) * 0.35;
        g.drawImage(img, offsetX, offsetY, drawW, drawH);
        g.restore();
    }

    private void drawCyberGrid(GraphicsContext g, double t, Color c1, Color c2) {
        g.save();
        double horizonY = H * 0.66;
        g.setLineWidth(1.0);
        // Đường ngang trôi dần về phía người xem
        for (int i = 0; i < 8; i++) {
            double phase = ((i / 8.0) + (t * 0.22)) % 1.0;
            double y = horizonY + Math.pow(phase, 1.9) * (H - horizonY);
            g.setStroke(Color.color(c1.getRed(), c1.getGreen(), c1.getBlue(), phase * 0.24));
            g.strokeLine(0, y, W, y);
        }
        // Đường phối cảnh hội tụ
        double vanishX = W * 0.5;
        for (int i = -6; i <= 6; i++) {
            double bottomX = vanishX + i * 92;
            g.setStroke(Color.color(c2.getRed(), c2.getGreen(), c2.getBlue(), 0.15));
            g.strokeLine(vanishX + i * 16, horizonY, bottomX, H);
        }
        g.restore();
    }

    private void updateAndDrawParticles(GraphicsContext g, double t, double dt, Color c1, Color c2) {
        g.save();
        for (int i = 0; i < particles.length; i++) {
            SparkParticle p = particles[i];
            p.x += p.vx * dt + Math.sin(t * 2.0 + i) * 0.25;
            p.y += p.vy * dt;
            if (p.y < -12 || p.x < -15 || p.x > W + 15) {
                particles[i] = spawnParticle(false);
                p = particles[i];
            }
            Color col = (i % 3 == 0) ? c1 : ((i % 3 == 1) ? c2 : Color.web("#FBBF24"));
            double pulse = 0.55 + 0.45 * Math.sin(t * 4.0 + i);
            g.setFill(Color.color(col.getRed(), col.getGreen(), col.getBlue(), p.alpha * pulse * 0.45));
            g.fillOval(p.x - p.size * 1.4, p.y - p.size * 1.4, p.size * 2.8, p.size * 2.8);
            g.setFill(Color.color(1, 1, 1, p.alpha * pulse * 0.85));
            g.fillOval(p.x - p.size * 0.45, p.y - p.size * 0.45, p.size * 0.9, p.size * 0.9);
        }
        g.restore();
    }

    private void drawLivePriceWave(GraphicsContext g, double t, Color c1, Color c2) {
        g.save();
        double baseY = H * 0.65;
        double startX = 28;
        double endX = W - 28;
        int pts = 34;
        double[] xs = new double[pts];
        double[] ys = new double[pts];

        for (int i = 0; i < pts; i++) {
            double u = (double) i / (pts - 1);
            xs[i] = startX + u * (endX - startX);
            double climb = -u * 34.0;
            double ripple = Math.sin(u * 9.0 - t * 3.8) * 7.5 + Math.cos(u * 15.0 + t * 2.5) * 3.5;
            ys[i] = baseY + climb + ripple;
        }

        // Glow stroke dày mờ phía sau
        g.setStroke(Color.color(c1.getRed(), c1.getGreen(), c1.getBlue(), 0.28));
        g.setLineWidth(7.0);
        g.strokePolyline(xs, ys, pts);

        // Đường neon sắc nét
        LinearGradient lineGrad = new LinearGradient(startX, 0, endX, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0, c2),
                new Stop(0.5, c1),
                new Stop(1, Color.web("#FBBF24")));
        g.setStroke(lineGrad);
        g.setLineWidth(2.6);
        g.strokePolyline(xs, ys, pts);

        // Điểm sáng dẫn đầu ở cuối đường giá
        double tipX = xs[pts - 1];
        double tipY = ys[pts - 1];
        double tipPulse = 6.0 + 3.0 * Math.sin(t * 7.0);
        g.setFill(Color.color(1.0, 0.78, 0.22, 0.35));
        g.fillOval(tipX - tipPulse, tipY - tipPulse, tipPulse * 2, tipPulse * 2);
        g.setFill(Color.WHITE);
        g.fillOval(tipX - 3.5, tipY - 3.5, 7, 7);
        g.restore();
    }

    private void drawTopProgressBars(GraphicsContext g, int activeIdx, double progress, Color c1, Color c2) {
        double padX = 24;
        double gap = 8;
        double barW = (W - padX * 2 - gap * (SCENES.length - 1)) / SCENES.length;
        double y = 18;
        for (int i = 0; i < SCENES.length; i++) {
            double x = padX + i * (barW + gap);
            g.setFill(Color.rgb(255, 255, 255, 0.22));
            g.fillRoundRect(x, y, barW, 3.5, 4, 4);

            double fill = (i < activeIdx) ? 1.0 : (i == activeIdx ? progress : 0.0);
            if (fill > 0) {
                LinearGradient barGrad = new LinearGradient(x, 0, x + barW, 0, false, CycleMethod.NO_CYCLE,
                        new Stop(0, c1), new Stop(1, c2));
                g.setFill(barGrad);
                g.fillRoundRect(x, y, barW * fill, 3.5, 4, 4);
            }
        }
    }

    private void drawBrandHeader(GraphicsContext g, double t, PromoScene scene, Color c1, Color c2) {
        double y = 38;
        // Vòng hào quang sau Logo 3D
        double glowR = 30 + 3 * Math.sin(t * 3.2);
        g.setFill(Color.color(c1.getRed(), c1.getGreen(), c1.getBlue(), 0.35));
        g.fillOval(26 + 24 - glowR, y + 24 - glowR, glowR * 2, glowR * 2);

        if (brandLogo != null) {
            g.save();
            g.beginPath();
            g.moveTo(38, y);
            g.arcTo(74, y, 74, y + 48, 12);
            g.arcTo(74, y + 48, 26, y + 48, 12);
            g.arcTo(26, y + 48, 26, y, 12);
            g.arcTo(26, y, 74, y, 12);
            g.closePath();
            g.clip();
            g.drawImage(brandLogo, 26, y, 48, 48);
            g.restore();
        }
        g.setStroke(Color.color(1, 0.78, 0.35, 0.85));
        g.setLineWidth(1.5);
        g.strokeRoundRect(26, y, 48, 48, 13, 13);

        g.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 28));
        g.setFill(Color.WHITE);
        g.fillText("Bid", 84, y + 29);
        g.setFill(c1);
        g.fillText("X", 129, y + 29);

        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        g.setFill(Color.rgb(226, 232, 240, 0.85));
        g.fillText("LUXURY LIVE AUCTION", 85, y + 44);

        // Live Promo Pill góc phải trên
        double pillW = 166;
        double pillX = W - pillW - 24;
        g.setFill(Color.rgb(12, 18, 32, 0.78));
        g.fillRoundRect(pillX, y + 8, pillW, 28, 20, 20);
        g.setStroke(Color.color(c1.getRed(), c1.getGreen(), c1.getBlue(), 0.75));
        g.setLineWidth(1.2);
        g.strokeRoundRect(pillX, y + 8, pillW, 28, 20, 20);

        double dotAlpha = 0.45 + 0.55 * Math.abs(Math.sin(t * 4.5));
        g.setFill(Color.color(1.0, 0.28, 0.42, dotAlpha));
        g.fillOval(pillX + 12, y + 18, 8, 8);

        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        g.setFill(Color.WHITE);
        g.fillText(scene.badge(), pillX + 25, y + 26);
    }

    private void drawFloatingBidBubbles(GraphicsContext g, double t, double progress, PromoScene scene, Color c1, Color c2) {
        String[] bubbles = scene.floatingBubbles();
        for (int i = 0; i < bubbles.length; i++) {
            double appear = Math.max(0.0, Math.min(1.0, (progress * 3.8) - i * 0.45));
            if (appear <= 0.01) {
                continue;
            }
            double ease = 1.0 - Math.pow(1.0 - appear, 3.0);
            double floatY = Math.sin(t * 2.8 + i * 1.4) * 4.0;
            double bx = W - 246 + (1.0 - ease) * 48;
            double by = 114 + i * 44 + floatY;

            g.save();
            g.setGlobalAlpha(ease * 0.94);
            g.setFill(Color.rgb(13, 19, 34, 0.84));
            g.fillRoundRect(bx, by, 222, 34, 17, 17);
            Color stroke = (i == 0) ? c1 : (i == 1 ? c2 : Color.web("#FBBF24"));
            g.setStroke(Color.color(stroke.getRed(), stroke.getGreen(), stroke.getBlue(), 0.7));
            g.setLineWidth(1.2);
            g.strokeRoundRect(bx, by, 222, 34, 17, 17);

            g.setFill(stroke);
            g.fillOval(bx + 11, by + 13, 8, 8);

            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.5));
            g.setFill(Color.web("#F8FAFC"));
            g.fillText(bubbles[i], bx + 25, by + 21);
            g.restore();
        }
    }

    private void drawBottomTypography(GraphicsContext g, double t, double progress, PromoScene scene, Color c1, Color c2) {
        double enter = Math.min(1.0, progress * 5.0);
        double slideY = (1.0 - (1.0 - Math.pow(1.0 - enter, 3.0))) * 18.0;

        g.save();
        g.setGlobalAlpha(enter);

        // Thẻ giá nổi bật
        double cardY = H - 208 + slideY;
        LinearGradient priceGrad = new LinearGradient(28, 0, 235, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0, c1), new Stop(1, c2));
        g.setFill(priceGrad);
        g.fillRoundRect(28, cardY, 188, 32, 16, 16);
        g.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 14.5));
        g.setFill(Color.WHITE);
        g.fillText(scene.priceText(), 42, cardY + 21);

        // Headline 2 dòng đậm điện ảnh
        String[] lines = scene.headline().split("\n");
        g.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 29));
        g.setFill(Color.WHITE);
        double hy = cardY + 66;
        for (String line : lines) {
            g.fillText(line, 28, hy);
            hy += 35;
        }

        // Subline
        g.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
        g.setFill(Color.web("#CBD5E1"));
        g.fillText(scene.subline(), 28, hy + 2);

        // Dải footer thương hiệu nhóm phát triển
        g.setFill(Color.rgb(255, 255, 255, 0.06));
        g.fillRoundRect(24, H - 50, W - 48, 30, 12, 12);
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        g.setFill(Color.web("#94A3B8"));
        g.fillText("BidX Team  •  Tuấn Đại  •  Phạm Trường  •  Xuân Thắng  •  Huy Hưng", 82, H - 31);

        g.restore();
    }

    private void drawAnimatedBorder(GraphicsContext g, double t, Color c1, Color c2) {
        double angle = t * 0.9;
        double sx = 0.5 + 0.5 * Math.cos(angle);
        double sy = 0.5 + 0.5 * Math.sin(angle);
        LinearGradient borderGrad = new LinearGradient(sx, sy, 1 - sx, 1 - sy, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, c1),
                new Stop(0.35, Color.web("#FBBF24")),
                new Stop(0.7, c2),
                new Stop(1.0, Color.web("#38BDF8")));
        g.setStroke(borderGrad);
        g.setLineWidth(2.4);
        g.strokeRoundRect(1.5, 1.5, W - 3, H - 3, 30, 30);
    }
}
