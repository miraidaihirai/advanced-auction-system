
package com.auction.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;

public final class NotificationManager {

    private NotificationManager() {
    }

    public static void showSuccess(String message) {
        showAlert(Alert.AlertType.INFORMATION, "Thành công", message);
    }

    // Đặt giá thành công
    public static void showInfo(String message) {
        showAlert(Alert.AlertType.INFORMATION, "Thông báo", message);
    }

    // Thông tin chung
    public static void showWarning(String message) {
        showAlert(Alert.AlertType.WARNING, "Cảnh báo", message);
    }

    // Cảnh báo phiên đấu giá
    public static void showError(String message) {
        showAlert(Alert.AlertType.ERROR, "Lỗi", message);
    }

    // Mất kết nối, lỗi đặt giá
    private static void showAlert(
            Alert.AlertType type,
            String title,
            String message) {

        Runnable displayAlert = () -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message == null || message.isBlank() ? "Đã xảy ra sự kiện cần thông báo." : message);
            alert.show();
        };
        // Không hiện tiêu đề phụ, hiển thị hộp thoai để người dùng đóng nó
        if (Platform.isFxApplicationThread()) {
            displayAlert.run();
        } else {
            Platform.runLater(displayAlert);
        }
        // Kiểm tra xem có phải ở trên chính xác JavaFX Application Thread không
    }
}
