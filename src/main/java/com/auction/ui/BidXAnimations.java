package com.auction.ui;

import java.util.Random;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Bộ động cơ hiệu ứng hoạt ảnh GPU mượt mà cho BidX:
 * - Staggered entrance fade + slide
 * - Hover lift & neon rim glow cho product cards
 * - Price pop shockwave khi nhảy giá realtime
 * - Counting roll-up cho ví tiền & KPI
 * - Pháo giấy / ánh sáng chúc mừng (Confetti Burst) khi thắng phiên hoặc tiền chảy về ví Seller
 * - Ambient floating glow orbs cho màn hình Login/Register
 */
public final class BidXAnimations {

    private static final Random RNG = new Random();

    private BidXAnimations() {
    }

    public static void fadeSlideIn(Node node, double delayMs, double fromOffsetY) {
        if (node == null) {
            return;
        }
        node.setOpacity(0);
        node.setTranslateY(fromOffsetY);

        FadeTransition fade = new FadeTransition(Duration.millis(340), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(360), node);
        slide.setFromY(fromOffsetY);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.SPLINE(0.22, 1.0, 0.36, 1.0));

        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setDelay(Duration.millis(Math.max(0, delayMs)));
        pt.play();
    }

    public static void applyCardHoverLift(Node card, Color glowColor) {
        if (card == null) {
            return;
        }
        DropShadow hoverGlow = new DropShadow();
        hoverGlow.setColor(glowColor != null ? glowColor : Color.rgb(255, 107, 43, 0.42));
        hoverGlow.setRadius(24);
        hoverGlow.setSpread(0.12);
        hoverGlow.setOffsetY(8);

        DropShadow restShadow = new DropShadow();
        restShadow.setColor(Color.rgb(0, 0, 0, 0.45));
        restShadow.setRadius(14);
        restShadow.setOffsetY(6);
        card.setEffect(restShadow);

        card.setOnMouseEntered(e -> {
            TranslateTransition lift = new TranslateTransition(Duration.millis(180), card);
            lift.setToY(-6);
            lift.setInterpolator(Interpolator.EASE_OUT);

            ScaleTransition scale = new ScaleTransition(Duration.millis(180), card);
            scale.setToX(1.018);
            scale.setToY(1.018);
            scale.setInterpolator(Interpolator.EASE_OUT);

            card.setEffect(hoverGlow);
            new ParallelTransition(lift, scale).play();
        });

        card.setOnMouseExited(e -> {
            TranslateTransition drop = new TranslateTransition(Duration.millis(200), card);
            drop.setToY(0);
            drop.setInterpolator(Interpolator.EASE_OUT);

            ScaleTransition scale = new ScaleTransition(Duration.millis(200), card);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.setInterpolator(Interpolator.EASE_OUT);

            card.setEffect(restShadow);
            new ParallelTransition(drop, scale).play();
        });
    }

    public static void applyButtonJuice(Node button) {
        if (button == null) {
            return;
        }
        button.setOnMousePressed(e -> {
            ScaleTransition down = new ScaleTransition(Duration.millis(80), button);
            down.setToX(0.95);
            down.setToY(0.95);
            down.play();
        });
        button.setOnMouseReleased(e -> {
            ScaleTransition up = new ScaleTransition(Duration.millis(140), button);
            up.setToX(1.0);
            up.setToY(1.0);
            up.setInterpolator(Interpolator.SPLINE(0.22, 1.0, 0.36, 1.0));
            up.play();
        });
    }

    public static void popScale(Node node, double peakScale) {
        if (node == null) {
            return;
        }
        ScaleTransition up = new ScaleTransition(Duration.millis(120), node);
        up.setToX(peakScale);
        up.setToY(peakScale);
        up.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition down = new ScaleTransition(Duration.millis(180), node);
        down.setToX(1.0);
        down.setToY(1.0);
        down.setInterpolator(Interpolator.SPLINE(0.22, 1.0, 0.36, 1.0));

        new SequentialTransition(up, down).play();
    }

    public static void pricePopFlash(Node priceNode) {
        if (priceNode == null) {
            return;
        }
        DropShadow flash = new DropShadow(26, Color.web("#FF7B24"));
        flash.setSpread(0.35);
        priceNode.setEffect(flash);

        ScaleTransition up = new ScaleTransition(Duration.millis(130), priceNode);
        up.setToX(1.12);
        up.setToY(1.12);
        up.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition down = new ScaleTransition(Duration.millis(260), priceNode);
        down.setToX(1.0);
        down.setToY(1.0);
        down.setInterpolator(Interpolator.SPLINE(0.22, 1.0, 0.36, 1.0));

        SequentialTransition seq = new SequentialTransition(up, down);
        seq.setOnFinished(e -> priceNode.setEffect(null));
        seq.play();
    }

    public static Timeline startPulse(Node node, double minOpacity, double maxScale, double periodMs) {
        if (node == null) {
            return null;
        }
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(node.opacityProperty(), 1.0),
                        new KeyValue(node.scaleXProperty(), 1.0),
                        new KeyValue(node.scaleYProperty(), 1.0)),
                new KeyFrame(Duration.millis(periodMs / 2.0),
                        new KeyValue(node.opacityProperty(), minOpacity, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleXProperty(), maxScale, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleYProperty(), maxScale, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(periodMs),
                        new KeyValue(node.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        return timeline;
    }

    public static void shake(Node node) {
        if (node == null) {
            return;
        }
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(0), new KeyValue(node.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(50), new KeyValue(node.translateXProperty(), -8)),
                new KeyFrame(Duration.millis(100), new KeyValue(node.translateXProperty(), 8)),
                new KeyFrame(Duration.millis(150), new KeyValue(node.translateXProperty(), -6)),
                new KeyFrame(Duration.millis(200), new KeyValue(node.translateXProperty(), 6)),
                new KeyFrame(Duration.millis(250), new KeyValue(node.translateXProperty(), -3)),
                new KeyFrame(Duration.millis(300), new KeyValue(node.translateXProperty(), 0))
        );
        timeline.play();
    }

    public static void animateCurrency(Label label, double fromVal, double toVal) {
        if (label == null) {
            return;
        }
        if (Math.abs(toVal - fromVal) < 1.0) {
            label.setText(FormatUtils.formatVnd(toVal));
            return;
        }
        DoubleProperty valProp = new SimpleDoubleProperty(fromVal);
        valProp.addListener((obs, oldV, newV) -> label.setText(FormatUtils.formatVnd(newV.doubleValue())));

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(valProp, fromVal)),
                new KeyFrame(Duration.millis(680), new KeyValue(valProp, toVal, Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0)))
        );
        tl.play();
    }

    /**
     * Pháo giấy neon chúc mừng khi thắng phiên đấu giá, duyệt phòng hoặc nhận tiền về ví Seller.
     */
    public static void playCelebrationBurst(Pane fxOverlay) {
        if (fxOverlay == null) {
            return;
        }
        double width = fxOverlay.getWidth() > 0 ? fxOverlay.getWidth() : 1280;
        double height = fxOverlay.getHeight() > 0 ? fxOverlay.getHeight() : 800;
        double originX = width * 0.5;
        double originY = height * 0.32;

        Color[] palette = {
                Color.web("#FF7B24"),
                Color.web("#FBBF24"),
                Color.web("#10B981"),
                Color.web("#38BDF8"),
                Color.web("#F43F5E"),
                Color.web("#A855F7")
        };

        for (int i = 0; i < 42; i++) {
            boolean isCircle = i % 3 == 0;
            Node particle;
            Color color = palette[i % palette.length];
            if (isCircle) {
                Circle c = new Circle(3 + RNG.nextDouble() * 4, color);
                particle = c;
            } else {
                Rectangle r = new Rectangle(6 + RNG.nextDouble() * 6, 4 + RNG.nextDouble() * 5, color);
                r.setArcWidth(2);
                r.setArcHeight(2);
                particle = r;
            }
            particle.setMouseTransparent(true);
            particle.setManaged(false);
            particle.setLayoutX(originX);
            particle.setLayoutY(originY);
            fxOverlay.getChildren().add(particle);

            double angle = RNG.nextDouble() * Math.PI * 2;
            double distance = 120 + RNG.nextDouble() * 340;
            double targetX = Math.cos(angle) * distance;
            double targetY = Math.sin(angle) * distance + 130;
            double durationMs = 750 + RNG.nextDouble() * 650;

            TranslateTransition move = new TranslateTransition(Duration.millis(durationMs), particle);
            move.setByX(targetX);
            move.setByY(targetY);
            move.setInterpolator(Interpolator.SPLINE(0.15, 0.85, 0.3, 1.0));

            FadeTransition fade = new FadeTransition(Duration.millis(durationMs), particle);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setDelay(Duration.millis(durationMs * 0.35));

            Timeline spin = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(particle.rotateProperty(), 0)),
                    new KeyFrame(Duration.millis(durationMs), new KeyValue(particle.rotateProperty(), (RNG.nextBoolean() ? 1 : -1) * (240 + RNG.nextInt(360))))
            );

            ParallelTransition pt = new ParallelTransition(move, fade, spin);
            pt.setOnFinished(ev -> fxOverlay.getChildren().remove(particle));
            pt.play();
        }
    }

    /**
     * Thêm các quầng sáng neon trôi nhẹ phía nền màn hình Auth để tạo chiều sâu hiện đại.
     */
    public static void attachAmbientOrbs(Pane bgPane) {
        if (bgPane == null) {
            return;
        }
        bgPane.getChildren().clear();

        Circle orb1 = new Circle(170, Color.rgb(255, 107, 43, 0.22));
        orb1.setEffect(new GaussianBlur(75));
        orb1.setLayoutX(160);
        orb1.setLayoutY(180);

        Circle orb2 = new Circle(210, Color.rgb(225, 29, 72, 0.16));
        orb2.setEffect(new GaussianBlur(90));
        orb2.setLayoutX(460);
        orb2.setLayoutY(560);

        Circle orb3 = new Circle(150, Color.rgb(56, 189, 248, 0.14));
        orb3.setEffect(new GaussianBlur(75));
        orb3.setLayoutX(980);
        orb3.setLayoutY(160);

        bgPane.getChildren().addAll(orb1, orb2, orb3);

        animateOrbFloat(orb1, 35, 28, 5200);
        animateOrbFloat(orb2, -40, -25, 6400);
        animateOrbFloat(orb3, -30, 35, 5800);
    }

    /**
     * Hào quang thở nhẹ & nhịp nổi cho Logo BidX 3D mới.
     */
    public static void attachLogoAura(Node logoNode) {
        if (logoNode == null) {
            return;
        }
        DropShadow aura = new DropShadow(18, Color.rgb(255, 140, 43, 0.55));
        aura.setSpread(0.16);
        logoNode.setEffect(aura);

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(aura.radiusProperty(), 14),
                        new KeyValue(logoNode.scaleXProperty(), 1.0),
                        new KeyValue(logoNode.scaleYProperty(), 1.0)),
                new KeyFrame(Duration.millis(1500),
                        new KeyValue(aura.radiusProperty(), 24, Interpolator.EASE_BOTH),
                        new KeyValue(logoNode.scaleXProperty(), 1.04, Interpolator.EASE_BOTH),
                        new KeyValue(logoNode.scaleYProperty(), 1.04, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(3000),
                        new KeyValue(aura.radiusProperty(), 14, Interpolator.EASE_BOTH),
                        new KeyValue(logoNode.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(logoNode.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
        );
        tl.setCycleCount(Timeline.INDEFINITE);
        tl.play();
    }

    /**
     * Hiệu ứng thả Reaction bay lượn sóng mềm mượt lên khung trưng bày phòng đấu giá.
     */
    public static void spawnFloatingReaction(Pane hostPane, String text, String accentHex) {
        if (hostPane == null) {
            return;
        }
        double hostW = hostPane.getWidth() > 0 ? hostPane.getWidth() : 620;
        double hostH = hostPane.getHeight() > 0 ? hostPane.getHeight() : 300;

        Label pill = new Label(text);
        pill.setMouseTransparent(true);
        pill.setManaged(false);
        pill.setStyle(
                "-fx-background-color: rgba(11, 16, 32, 0.88);"
                + "-fx-text-fill: #FFFFFF;"
                + "-fx-font-size: 12.5px;"
                + "-fx-font-weight: bold;"
                + "-fx-padding: 6 13 6 13;"
                + "-fx-background-radius: 20;"
                + "-fx-border-color: " + accentHex + ";"
                + "-fx-border-width: 1.4;"
                + "-fx-border-radius: 20;");
        pill.setEffect(new DropShadow(16, Color.web(accentHex)));

        double startX = hostW * (0.28 + RNG.nextDouble() * 0.44);
        double startY = hostH - 42;
        pill.setLayoutX(startX);
        pill.setLayoutY(startY);
        pill.setScaleX(0.4);
        pill.setScaleY(0.4);
        hostPane.getChildren().add(pill);

        ScaleTransition pop = new ScaleTransition(Duration.millis(220), pill);
        pop.setToX(1.08);
        pop.setToY(1.08);
        pop.setInterpolator(Interpolator.SPLINE(0.22, 1.0, 0.36, 1.0));

        TranslateTransition rise = new TranslateTransition(Duration.millis(2100), pill);
        rise.setByY(-(hostH * 0.72));
        rise.setByX((RNG.nextDouble() - 0.5) * 90);
        rise.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition fade = new FadeTransition(Duration.millis(1200), pill);
        fade.setDelay(Duration.millis(900));
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

        ParallelTransition all = new ParallelTransition(pop, rise, fade);
        all.setOnFinished(e -> hostPane.getChildren().remove(pill));
        all.play();
    }

    /**
     * Sóng xung kích neon lan tỏa + số tiền tăng giá bay nổi khi có lượt bid mới!
     */
    public static void playBidShockwave(Pane fxLayer, String labelText) {
        if (fxLayer == null) {
            return;
        }
        double w = fxLayer.getWidth() > 0 ? fxLayer.getWidth() : 1360;
        double h = fxLayer.getHeight() > 0 ? fxLayer.getHeight() : 820;
        double cx = w * 0.78;
        double cy = h * 0.34;

        Circle ring = new Circle(22, Color.TRANSPARENT);
        ring.setStroke(Color.web("#FF8C2B"));
        ring.setStrokeWidth(3.2);
        ring.setMouseTransparent(true);
        ring.setManaged(false);
        ring.setLayoutX(cx);
        ring.setLayoutY(cy);
        fxLayer.getChildren().add(ring);

        ScaleTransition expand = new ScaleTransition(Duration.millis(560), ring);
        expand.setToX(5.2);
        expand.setToY(5.2);
        expand.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition ringFade = new FadeTransition(Duration.millis(560), ring);
        ringFade.setFromValue(0.9);
        ringFade.setToValue(0.0);

        ParallelTransition ringAnim = new ParallelTransition(expand, ringFade);
        ringAnim.setOnFinished(e -> fxLayer.getChildren().remove(ring));
        ringAnim.play();

        if (labelText != null && !labelText.isBlank()) {
            Label badge = new Label(labelText);
            badge.setMouseTransparent(true);
            badge.setManaged(false);
            badge.setStyle(
                    "-fx-background-color: linear-gradient(to right, #FF8C2B, #E11D48);"
                    + "-fx-text-fill: #FFFFFF;"
                    + "-fx-font-size: 13.5px;"
                    + "-fx-font-weight: bold;"
                    + "-fx-padding: 6 14 6 14;"
                    + "-fx-background-radius: 20;");
            badge.setEffect(new DropShadow(18, Color.web("#FF7B24")));
            badge.setLayoutX(cx - 70);
            badge.setLayoutY(cy - 18);
            fxLayer.getChildren().add(badge);

            TranslateTransition floatUp = new TranslateTransition(Duration.millis(1100), badge);
            floatUp.setByY(-72);
            floatUp.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition fadeText = new FadeTransition(Duration.millis(650), badge);
            fadeText.setDelay(Duration.millis(450));
            fadeText.setFromValue(1.0);
            fadeText.setToValue(0.0);

            ParallelTransition pt = new ParallelTransition(floatUp, fadeText);
            pt.setOnFinished(e -> fxLayer.getChildren().remove(badge));
            pt.play();
        }
    }

    /**
     * Hào quang thở nhẹ cho các nút CTA nổi bật tạo chiều sâu sống động.
     */
    public static void attachButtonGlowPulse(Node node) {
        if (node == null) {
            return;
        }
        DropShadow glow = new DropShadow(16, Color.rgb(255, 107, 43, 0.44));
        glow.setOffsetY(4);
        node.setEffect(glow);

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 15)),
                new KeyFrame(Duration.millis(1400), new KeyValue(glow.radiusProperty(), 26, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(2800), new KeyValue(glow.radiusProperty(), 15, Interpolator.EASE_BOTH))
        );
        tl.setCycleCount(Timeline.INDEFINITE);
        tl.play();
    }

    private static void animateOrbFloat(Node orb, double dx, double dy, double durationMs) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(durationMs), orb);
        tt.setByX(dx);
        tt.setByY(dy);
        tt.setAutoReverse(true);
        tt.setCycleCount(Timeline.INDEFINITE);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.play();
    }
}
