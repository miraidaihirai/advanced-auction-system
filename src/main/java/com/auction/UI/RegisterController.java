package com.auction.ui;

import com.auction.session.ClientSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import network.AuctionClient;

public class RegisterController implements SessionAware {
  @FXML private AnchorPane RegisterPane;
  @FXML private TextField username;
  @FXML private TextField txtName;
  @FXML private ComboBox<String> cbRole;
  @FXML private PasswordField password;
  @FXML private Label errorRegister;

  private ClientSession clientSession;
  private volatile boolean registerInProgress = false;

  private final AuctionClient.AuctionEventListener registerListener =
      new AuctionClient.AuctionEventListener() {
        @Override
        public void onBidUpdate(
            String auctionId, double newPrice, String bidderId, String itemId) {}

        @Override
        public void onMessageReceived(String rawMessage) {
          if (!registerInProgress) {
            return;
          }
          if (rawMessage.startsWith("SUCCESS REGISTER ")) {
            Platform.runLater(
                () -> {
                  registerInProgress = false;
                  errorRegister.setText("");
                  if (clientSession != null && clientSession.isConnected()) {
                    clientSession.disconnect();
                  }
                  NotificationManager.showSuccess(
                      "Đăng ký tài khoản thành công! Vui lòng đăng nhập.");
                  clearForm();
                  ViewManager.SwitchView("LogIn");
                });
          } else if (rawMessage.startsWith("ERROR ")) {
            String err = rawMessage.substring("ERROR ".length());
            Platform.runLater(
                () -> {
                  registerInProgress = false;
                  errorRegister.setText(translateRegisterError(err));
                });
          }
        }

        @Override
        public void onDisconnected() {
          if (!registerInProgress) {
            return;
          }
          Platform.runLater(
              () -> {
                registerInProgress = false;
                errorRegister.setText("Mất kết nối đến máy chủ. Vui lòng thử lại.");
              });
        }
      };

  @FXML
  public void initialize() {
    if (cbRole != null) {
      cbRole.getItems().setAll("Người đặt giá (BIDDER)", "Người bán (SELLER)");
      cbRole.getSelectionModel().selectFirst();
    }
  }

  @Override
  public void setClientSession(ClientSession session) {
    if (this.clientSession != null && this.clientSession != session) {
      this.clientSession.getClient().removeListener(registerListener);
    }
    this.clientSession = session;
    if (this.clientSession != null) {
      this.clientSession.getClient().addListener(registerListener);
    }
    clearForm();
  }

  @Override
  public void dispose() {
    if (this.clientSession != null) {
      this.clientSession.getClient().removeListener(registerListener);
    }
  }

  private void clearForm() {
    registerInProgress = false;
    if (username != null) username.clear();
    if (txtName != null) txtName.clear();
    if (password != null) password.clear();
    if (errorRegister != null) errorRegister.setText("");
  }

  @FXML
  private void Register() {
    if (registerInProgress) {
      return;
    }
    String userId = username != null && username.getText() != null ? username.getText().trim() : "";
    String name = txtName != null && txtName.getText() != null ? txtName.getText().trim() : "";
    String pwd = password != null && password.getText() != null ? password.getText() : "";
    String roleChoice = cbRole != null ? cbRole.getValue() : "BIDDER";
    String role = (roleChoice != null && roleChoice.contains("SELLER")) ? "SELLER" : "BIDDER";

    if (userId.isEmpty() || name.isEmpty() || pwd.isEmpty()) {
      errorRegister.setText("Vui lòng nhập đầy đủ mã tài khoản, tên hiển thị và mật khẩu.");
      return;
    }
    if (userId.contains(" ") || pwd.contains(" ")) {
      errorRegister.setText("Mã tài khoản và mật khẩu không được chứa khoảng trắng.");
      return;
    }
    if (pwd.length() < 8) {
      errorRegister.setText("Mật khẩu phải có ít nhất 8 ký tự.");
      return;
    }
    if (clientSession == null) {
      errorRegister.setText("Phiên kết nối chưa được khởi tạo.");
      return;
    }

    String safeName = name.replaceAll("\\s+", "_");
    registerInProgress = true;
    errorRegister.setText("Đang gửi yêu cầu đăng ký...");

    Thread worker =
        new Thread(
            () -> {
              try {
                clientSession.connect();
                clientSession.getClient().register(userId, safeName, pwd, role);
              } catch (Exception e) {
                Platform.runLater(
                    () -> {
                      registerInProgress = false;
                      errorRegister.setText(
                          "Không thể kết nối đến máy chủ. Vui lòng bật AuctionServer trước!");
                    });
              }
            },
            "register-worker");
    worker.setDaemon(true);
    worker.start();
  }

  @FXML
  private void SwitchToLogin() {
    ViewManager.SwitchView("LogIn");
  }

  private String translateRegisterError(String err) {
    String lower = err.toLowerCase();
    if (lower.contains("already registered")) {
      return "Mã tài khoản này đã tồn tại trên hệ thống.";
    }
    if (lower.contains("password")) {
      return "Mật khẩu không hợp lệ (tối thiểu 8 ký tự).";
    }
    return "Đăng ký thất bại: " + err;
  }
}
