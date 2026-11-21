package com.auction.ui;

import java.util.HashSet;
import java.util.Set;
import javafx.application.Platform;
// Cung cấp isFxApplicationThread() và runLater() đảm bảo code chạy trên
// JavaFX Application Thread (thread duy nhất cập nhật UI)
import javafx.scene.control.Alert;
// Hộp thoại chuẩn của JavaFX
import javafx.scene.control.DialogPane;
// Root node của Alert, dùng để phân biệt cửa số chính với hộp thoại khi tìm Owner
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

// Modality mức độ chặn của của sổ (NONE/WINDOW_MODAL/APPLICATION_MODAL)
// Stage, cửa sổ lớp cao nhất
// Window, cha của Stage, dùng để lấy các cửa sổ đang mở

public final class NotificationManager {

  // Các Alert đang mở (chỉ truy cập trên JavaFX Application Thread)
  private static final Set<String> openAlerts = new HashSet<>();

  private NotificationManager() {}

  public static void showSuccess(String message) {
    showAlert(Alert.AlertType.INFORMATION, "Thành công", message);
  }

  // Thông tin chung
  public static void showInfo(String message) {
    showAlert(Alert.AlertType.INFORMATION, "Thông báo", message);
  }

  // Cảnh báo (không đủ quyền, thao tác không hợp lệ)
  public static void showWarning(String message) {
    showAlert(Alert.AlertType.WARNING, "Cảnh báo", message);
  }

  // Mất kết nối, lỗi đặt giá
  public static void showError(String message) {
    showAlert(Alert.AlertType.ERROR, "Lỗi", message);
  }

  private static void showAlert(Alert.AlertType type, String title, String message) {

    final String content =
        (message == null || message.isBlank()) ? "Đã xảy ra sự kiện cần thông báo." : message;

    Runnable displayAlert =
        () -> {
          // Cùng nội dung đang hiển thị thì không mở thêm cái nữa
          String key = title + "\n" + content;
          if (!openAlerts.add(key)) {
            return;
            // Set.add() trả về false nếu phần tử đã tồn tại, nếu Alert cùng nội dung thì thoát
            // ngay, không mở thêm cửa sổ
          }

          try {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);

            // Không khóa cửa sổ chính: người dùng vẫn thao tác được khi còn thông báo
            alert.initModality(Modality.NONE);

            // Gắn vào cửa sổ chính để thông báo luôn nổi phía trên ứng dụng
            Window owner = findMainWindow();
            if (owner != null) {
              alert.initOwner(owner);
            }

            alert.setOnHidden(event -> openAlerts.remove(key));
            alert.show();
            // Không treo luồng gọi (như showAndWait()) để tránh lỗi "Nested event loop"
          } catch (RuntimeException e) {
            openAlerts.remove(key);
            // Tạo alert thất bại thì xoá key để không giữ khoá ma, thứ sẽ chặn thông báo sau
            throw e;
          }
        };

    if (Platform.isFxApplicationThread()) {
      displayAlert.run();
      // Chạy ngay nếu đang ở FX thread để tránh delay
    } else {
      Platform.runLater(displayAlert);
    }
  }

  /** Tìm cửa sổ chính: Stage đang hiển thị và không phải hộp thoại Alert. */
  private static Window findMainWindow() {
    for (Window window : Window.getWindows()) {
      // Window.getWindows() trả về các cửa sổ hiện có của ứng dụng
      if (window instanceof Stage
          // Cử sổ loại Popup, Tooltip(? window)
          && window.isShowing()
          // Phải đang thực sự hiển thị
          && window.getScene() != null
          // Có Scene gắn vào
          && !(window.getScene().getRoot() instanceof DialogPane)) {
        return window;
        // Loại trừ các Alert/dialog vì Alert cũng là Stage với scene root là DialogPane
        // Nếu không lọc, có thể chọn nhầm một Alert khác làm owner gây vòng lặp owner
      }
    }
    return null;
    // Không tìm thấy thì trả null
  }
}
