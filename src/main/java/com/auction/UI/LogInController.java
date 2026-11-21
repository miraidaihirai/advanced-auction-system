package com.auction.ui;

import com.auction.session.ClientSession;
import com.auction.session.UserRole;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import network.AuctionClient;

public class LogInController implements SessionAware {
  // Cho ViewManager truyền ClientSession vào Controller
  // ClientSession lưu kết nối dùng chung của ứng dụng
  @FXML private AnchorPane LoginPane;

  @FXML private TextField usernameField;

  @FXML private PasswordField passwordField;

  @FXML private Label errorLabel;

  private ClientSession clientSession;

  private volatile boolean loginInProgress = false;

  private final AuctionClient.AuctionEventListener loginListener =
      new AuctionClient.AuctionEventListener() {
        // Lắng nghe phản hồi của AuctionServer
        @Override
        public void onBidUpdate(
            String auctionId, double newPrice, String bidderId, String itemId) {}

        @Override
        public void onMessageReceived(String rawMessage) {
          System.out.println("[LogInController] onMessageReceived: " + rawMessage);

          if (!loginInProgress) {
            return;
          }

          try {
            if (rawMessage.startsWith("SUCCESS LOGIN ")) {
              if (clientSession != null && clientSession.isConnected()) {
                clientSession.getClient().requestProfile();
              }
            } else if (rawMessage.startsWith("ERROR ")) {

              String serverError = rawMessage.substring("ERROR ".length());

              Platform.runLater(
                  () -> {
                    loginInProgress = false;
                    errorLabel.setText(translateLoginError(serverError));
                  });
            }
          } catch (Throwable t) {
            System.err.println(
                "[LogInController] Error handling onMessageReceived: " + t.getMessage());
            t.printStackTrace();
            Platform.runLater(
                () -> {
                  loginInProgress = false;
                  errorLabel.setText("Lỗi xử lý phản hồi đăng nhập: " + t.getMessage());
                });
          }
        }

        @Override
        public void onProfileReceived(String userId, String roleStr, String userName) {
          if (!loginInProgress) {
            return;
          }
          UserRole role = UserRole.fromString(roleStr);
          if (role == UserRole.UNKNOWN) {
            role = UserRole.BIDDER;
          }
          final UserRole finalRole = role;
          Platform.runLater(
              () -> {
                loginInProgress = false;
                errorLabel.setText("");
                if (clientSession != null) {
                  clientSession.setCurrentUser(userId, userName, finalRole);
                }
                ViewManager.SwitchView("Home");
              });
        }

        @Override
        public void onDisconnected() {

          if (!loginInProgress) {
            return;
          }

          Platform.runLater(
              () -> {
                loginInProgress = false;
                errorLabel.setText("Mất kết nối đến máy chủ. Vui lòng thử lại.");
              });
        }
        // Nhận sự kiện mất kết nối từ AuctionClient trên Socket listener thread
        // Platform.runLater() cập nhật errorLabel qua JavaFX Application Thread
      };

  @Override
  public void setClientSession(ClientSession session) {
    if (this.clientSession != null && this.clientSession != session) {
      this.clientSession.getClient().removeListener(loginListener);
    }

    this.clientSession = session;

    if (this.clientSession != null) {
      this.clientSession.getClient().addListener(loginListener);
    }

    resetForm();
  }

  @Override
  public void dispose() {
    if (this.clientSession != null) {
      this.clientSession.getClient().removeListener(loginListener);
    }
  }

  /** Xóa sạch các trường nhập liệu và thông báo lỗi trên form đăng nhập. */
  public void resetForm() {
    loginInProgress = false;
    if (usernameField != null) {
      usernameField.clear();
    }
    if (passwordField != null) {
      passwordField.clear();
    }
    if (errorLabel != null) {
      errorLabel.setText("");
    }
  }

  // Đăng kí listener với Auction Client

  @FXML
  public void SwitchToHome() {

    if (loginInProgress) {
      return;
    }

    String userId = usernameField.getText().trim();
    String password = passwordField.getText();

    if (userId.isEmpty() || password.isEmpty()) {
      errorLabel.setText("Vui lòng nhập tên đăng nhập và mật khẩu.");
      return;
    }

    if (clientSession == null) {
      errorLabel.setText("Phiên kết nối chưa được khởi tạo.");
      return;
    }

    loginInProgress = true;
    errorLabel.setText("Đang kết nối đến máy chủ...");

    // Watchdog timeout 8s: tự động mở khóa nếu Server không phản hồi, tránh treo giao diện
    Thread watchdogThread =
        new Thread(
            () -> {
              try {
                Thread.sleep(8000);
                if (loginInProgress) {
                  Platform.runLater(
                      () -> {
                        if (loginInProgress) {
                          loginInProgress = false;
                          errorLabel.setText("Máy chủ không phản hồi yêu cầu. Vui lòng thử lại.");
                        }
                      });
                }
              } catch (InterruptedException ignored) {
              }
            },
            "login-watchdog");
    watchdogThread.setDaemon(true);
    watchdogThread.start();

    Thread loginThread =
        new Thread(
            () -> {
              try {
                System.out.println("[LogInController] Connecting to server...");
                clientSession.connect();
                System.out.println(
                    "[LogInController] Sending LOGIN command for userId='" + userId + "'...");
                clientSession.getClient().login(userId, password);
              } catch (Exception e) {
                System.err.println("[LogInController] Connect failed: " + e.getMessage());
                Platform.runLater(
                    () -> {
                      loginInProgress = false;
                      errorLabel.setText(
                          "Không thể kết nối đến máy chủ. Vui lòng kiểm tra AuctionServer đã được bật!");
                    });
              }
            },
            "login-worker");

    loginThread.setDaemon(true);
    loginThread.start();
  }

  // Kiểm tra dữ liệu, kết nối Server và gửi yêu cầu LOGIN
  @FXML
  public void SwitchToRegister() {
    ViewManager.SwitchView("Register");
  }

  private String translateLoginError(String message) {

    return switch (message.trim()) {
      case "Invalid user credentials" -> "Tên đăng nhập hoặc mật khẩu không chính xác.";

      case "Usage: LOGIN <userId> <password>" -> "Thông tin đăng nhập không hợp lệ.";

      default -> "Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.";
    };
  }
}
