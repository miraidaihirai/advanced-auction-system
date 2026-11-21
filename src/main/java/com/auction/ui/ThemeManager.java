package com.auction.ui;

import java.net.URL;
import javafx.scene.Scene;

/**
 * Quản lý giao diện Sáng / Tối (Light / Dark Theme) cho toàn bộ ứng dụng. Singleton — sử dụng
 * ThemeManager.getInstance() để truy cập.
 */
public final class ThemeManager {

  private static final ThemeManager INSTANCE = new ThemeManager();

  public static ThemeManager getInstance() {
    return INSTANCE;
  }

  private ThemeManager() {}

  private boolean darkMode = true; // Mặc định là Dark
  private Scene managedScene;

  /**
   * Lấy URL stylesheet của chế độ Sáng một cách an toàn (tránh NullPointerException nếu chưa nạp
   * được resource).
   */
  public static String getLightCss() {
    URL url = ThemeManager.class.getResource("/com/auction/css/styleLight.css");
    return url != null ? url.toExternalForm() : null;
  }

  public void setScene(Scene scene) {
    this.managedScene = scene;
    applyCurrentTheme();
  }

  public boolean isDarkMode() {
    return darkMode;
  }

  public void toggleTheme() {
    darkMode = !darkMode;
    applyCurrentTheme();
  }

  public void applyTo(Scene scene) {
    if (scene == null) return;
    String lightCss = getLightCss();
    if (lightCss == null) return;

    if (darkMode) {
      scene.getStylesheets().remove(lightCss);
    } else {
      if (!scene.getStylesheets().contains(lightCss)) {
        scene.getStylesheets().add(lightCss);
      }
    }
  }

  private void applyCurrentTheme() {
    if (managedScene == null) return;
    applyTo(managedScene);
  }
}
