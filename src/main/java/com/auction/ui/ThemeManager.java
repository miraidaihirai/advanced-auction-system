package com.auction.ui;

import java.net.URL;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javafx.animation.FadeTransition;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Quản lý chuyển đổi giao diện Dark Luxury <-> Light Ivory toàn hệ thống BidX
 * với hiệu ứng chuyển cảnh mềm mượt và tự động cập nhật mọi nút bấm đang mở.
 */
public final class ThemeManager {

    private static final String LIGHT_CSS = "/com/auction/css/bidx-light.css";

    private static Scene boundScene;
    private static StackPane boundShell;
    private static boolean darkMode = true;
    private static final List<Consumer<Boolean>> listeners = new CopyOnWriteArrayList<>();

    private ThemeManager() {
    }

    public static void initialize(Scene scene, StackPane shellRoot) {
        boundScene = scene;
        boundShell = shellRoot;
        applyTheme(false);
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void addListener(Consumer<Boolean> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Consumer<Boolean> listener) {
        listeners.remove(listener);
    }

    public static void toggleTheme() {
        darkMode = !darkMode;
        SoundEffects.playReactionTap();
        applyTheme(true);
        for (Consumer<Boolean> l : listeners) {
            try {
                l.accept(darkMode);
            } catch (Exception ignored) {
            }
        }
    }

    public static void syncToggleButton(Button btn) {
        if (btn == null) {
            return;
        }
        if (darkMode) {
            btn.setText("Light");
            btn.setGraphic(SvgIcons.create(SvgIcons.SUN, 14, "#FBBF24"));
        } else {
            btn.setText("Dark");
            btn.setGraphic(SvgIcons.create(SvgIcons.MOON, 14, "#4F46E5"));
        }
    }

    public static void applyTheme(boolean animate) {
        if (boundScene == null || boundShell == null) {
            return;
        }
        URL lightUrl = ThemeManager.class.getResource(LIGHT_CSS);
        String lightExternal = lightUrl != null ? lightUrl.toExternalForm() : null;

        if (darkMode) {
            if (lightExternal != null) {
                boundScene.getStylesheets().remove(lightExternal);
            }
            boundShell.getStyleClass().remove("light-mode");
            boundShell.setStyle("-fx-background-color: #0A0E17;");
            boundScene.setFill(Color.web("#0A0E17"));
        } else {
            if (lightExternal != null && !boundScene.getStylesheets().contains(lightExternal)) {
                boundScene.getStylesheets().add(lightExternal);
            }
            if (!boundShell.getStyleClass().contains("light-mode")) {
                boundShell.getStyleClass().add("light-mode");
            }
            boundShell.setStyle("-fx-background-color: #F1F5F9;");
            boundScene.setFill(Color.web("#F1F5F9"));
        }

        if (animate) {
            FadeTransition ft = new FadeTransition(Duration.millis(240), boundShell);
            ft.setFromValue(0.84);
            ft.setToValue(1.0);
            ft.play();
        }
    }
}
