package com.auction.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Hệ thống thông báo Toast nổi dạng Glassmorphism với hoạt ảnh trượt mượt mà cho BidX.
 * Thay thế hộp thoại Alert mặc định cứng nhắc của hệ điều hành.
 */
public final class NotificationManager {

    private static VBox toastContainer;

    private NotificationManager() {
    }

    public static void attachToastContainer(VBox container) {
        toastContainer = container;
    }

    public static void showSuccess(String message) {
        showToast("Thành công", message, "#10B981", SvgIcons.CHECK, 4200);
    }

    public static void showPayout(String title, String message) {
        showToast(title, message, "#FBBF24", SvgIcons.WALLET, 6200);
        ViewManager.triggerCelebration();
        SoundEffects.playWinFanfare();
    }

    public static void showInfo(String message) {
        showToast("Thông báo BidX", message, "#38BDF8", SvgIcons.BELL, 4500);
    }

    public static void showWarning(String message) {
        showToast("Cảnh báo", message, "#F59E0B", SvgIcons.ZAP, 4800);
    }

    public static void showError(String message) {
        showToast("Không thành công", message, "#F43F5E", SvgIcons.CLOSE, 5200);
    }

    public static void showToast(String title, String message, String accentHex, String iconPath, long durationMs) {
        String safeMsg = (message == null || message.isBlank()) ? "Đã xảy ra sự kiện mới." : message.trim();
        if (ViewManager.getClientSession() != null) {
            ViewManager.getClientSession().addNotificationLog(title + ": " + safeMsg.replace("\n", " • "));
        }

        Runnable render = () -> {
            if (toastContainer == null) {
                System.out.printf("[BidX Toast][%s] %s%n", title, safeMsg);
                return;
            }

            boolean dark = ThemeManager.isDarkMode();
            VBox card = new VBox(0);
            card.setMaxWidth(370);
            card.setMinWidth(320);
            card.setStyle(
                    "-fx-background-color: " + (dark ? "rgba(17, 23, 38, 0.96);" : "rgba(255, 255, 255, 0.97);")
                    + "-fx-background-radius: 14;"
                    + "-fx-border-color: " + accentHex + (dark ? "66;" : "88;")
                    + "-fx-border-width: 1.2;"
                    + "-fx-border-radius: 14;"
                    + "-fx-cursor: hand;");
            card.setEffect(new DropShadow(24, dark ? Color.rgb(0, 0, 0, 0.65) : Color.rgb(15, 23, 42, 0.18)));

            HBox body = new HBox(12);
            body.setAlignment(Pos.CENTER_LEFT);
            body.setPadding(new Insets(13, 15, 11, 14));

            Region accentBar = new Region();
            accentBar.setMinWidth(4);
            accentBar.setMaxWidth(4);
            accentBar.setMinHeight(38);
            accentBar.setStyle("-fx-background-color: " + accentHex + "; -fx-background-radius: 4;");

            Node iconNode = SvgIcons.create(iconPath, 18, accentHex);
            VBox iconWrap = new VBox(iconNode);
            iconWrap.setAlignment(Pos.CENTER);
            iconWrap.setMinSize(34, 34);
            iconWrap.setMaxSize(34, 34);
            iconWrap.setStyle(
                    "-fx-background-color: " + accentHex + "22;"
                    + "-fx-background-radius: 10;");

            VBox textCol = new VBox(3);
            HBox.setHgrow(textCol, Priority.ALWAYS);

            Label lblTitle = new Label(title);
            lblTitle.setStyle("-fx-text-fill: " + (dark ? "#F8FAFC" : "#0F172A") + "; -fx-font-size: 13px; -fx-font-weight: bold;");

            Label lblMsg = new Label(safeMsg);
            lblMsg.setWrapText(true);
            lblMsg.setStyle("-fx-text-fill: " + (dark ? "#CBD5E1" : "#475569") + "; -fx-font-size: 12px;");

            textCol.getChildren().addAll(lblTitle, lblMsg);
            body.getChildren().addAll(accentBar, iconWrap, textCol);

            Rectangle progressBar = new Rectangle(350, 3);
            progressBar.setFill(Color.web(accentHex));
            progressBar.setArcWidth(3);
            progressBar.setArcHeight(3);
            HBox progressWrap = new HBox(progressBar);
            progressWrap.setPadding(new Insets(0, 10, 4, 10));

            card.getChildren().addAll(body, progressWrap);

            // Giới hạn tối đa 4 toast cùng lúc để màn hình luôn gọn gàng
            while (toastContainer.getChildren().size() >= 4) {
                toastContainer.getChildren().remove(toastContainer.getChildren().size() - 1);
            }
            toastContainer.getChildren().add(0, card);

            // Hoạt ảnh trượt từ phải vào
            card.setOpacity(0);
            card.setTranslateX(80);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(260), card);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            TranslateTransition slideIn = new TranslateTransition(Duration.millis(280), card);
            slideIn.setFromX(80);
            slideIn.setToX(0);
            slideIn.setInterpolator(Interpolator.SPLINE(0.22, 1.0, 0.36, 1.0));
            new ParallelTransition(fadeIn, slideIn).play();

            // Thanh thời gian thu hẹp mượt
            Timeline progressTl = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(progressBar.widthProperty(), 340)),
                    new KeyFrame(Duration.millis(durationMs), new KeyValue(progressBar.widthProperty(), 0))
            );
            progressTl.play();

            Runnable dismiss = () -> {
                if (!toastContainer.getChildren().contains(card)) {
                    return;
                }
                FadeTransition fadeOut = new FadeTransition(Duration.millis(220), card);
                fadeOut.setToValue(0);
                TranslateTransition slideOut = new TranslateTransition(Duration.millis(220), card);
                slideOut.setToX(70);
                ParallelTransition exit = new ParallelTransition(fadeOut, slideOut);
                exit.setOnFinished(ev -> toastContainer.getChildren().remove(card));
                exit.play();
            };

            card.setOnMouseClicked(e -> dismiss.run());
            PauseTransition autoDismiss = new PauseTransition(Duration.millis(durationMs));
            autoDismiss.setOnFinished(e -> dismiss.run());
            autoDismiss.play();
        };

        if (Platform.isFxApplicationThread()) {
            render.run();
        } else {
            Platform.runLater(render);
        }
    }
}
