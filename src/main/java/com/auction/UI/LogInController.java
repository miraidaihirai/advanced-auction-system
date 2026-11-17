
package com.auction.ui;

import com.auction.session.ClientSession;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.concurrent.Task;

import network.AuctionClient;

public class LogInController implements SessionAware {
    // Cho ViewManager truyền ClientSession vào Controller
    // ClientSession lưu kết nối dùng chung của ứng dụng
    @FXML
    private AnchorPane LoginPane;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    private ClientSession clientSession;

    private volatile boolean loginInProgress = false;

    private final AuctionClient.AuctionEventListener loginListener = new AuctionClient.AuctionEventListener() {
        // Lắng nghe phản hồi của AuctionServer
        @Override
        public void onBidUpdate(String auctionId, double newPrice,
                String bidderId, String itemId) {
        }

        @Override
        public void onMessageReceived(String rawMessage) {

            if (!loginInProgress) {
                return;
            }

            if (rawMessage.startsWith("SUCCESS LOGIN ")) {

                Platform.runLater(() -> {
                    loginInProgress = false;
                    errorLabel.setText("");
                    ViewManager.SwitchView("Home");
                });

            } else if (rawMessage.startsWith("ERROR ")) {

                String serverError = rawMessage.substring("ERROR ".length());

                Platform.runLater(() -> {
                    loginInProgress = false;
                    errorLabel.setText(translateLoginError(serverError));
                });
            }
        }

        @Override
        public void onDisconnected() {

            if (!loginInProgress) {
                return;
            }

            Platform.runLater(() -> {
                loginInProgress = false;
                errorLabel.setText(
                        "Mất kết nối đến máy chủ. Vui lòng thử lại.");
            });
        }
        // Nhận sự kiện mất kết nối từ AuctionClient trên Socket listener thread
        // Platform.runLater() cập nhật errorLabel qua JavaFX Application Thread
    };

    @Override
    public void setClientSession(ClientSession session) {

        if (this.clientSession == session) {
            return;
        }

        if (this.clientSession != null) {
            this.clientSession.getClient().removeListener(loginListener);
        }

        this.clientSession = session;

        if (this.clientSession != null) {
            this.clientSession.getClient().addListener(loginListener);
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
            errorLabel.setText(
                    "Vui lòng nhập tên đăng nhập và mật khẩu.");
            return;
        }

        if (clientSession == null) {
            errorLabel.setText(
                    "Phiên kết nối chưa được khởi tạo.");
            return;
        }

        loginInProgress = true;
        errorLabel.setText("Đang kết nối đến máy chủ...");

        Thread loginThread = new Thread(() -> {

            try {
                clientSession.connect();

                clientSession.getClient().login(userId, password);

            } catch (Exception e) {

                Platform.runLater(() -> {
                    loginInProgress = false;

                    errorLabel.setText(
                            "Không thể kết nối đến máy chủ. Vui lòng thử lại.");
                });
            }

        }, "login-worker");

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

            case "Invalid user credentials" ->
                "Tên đăng nhập hoặc mật khẩu không chính xác.";

            case "Usage: LOGIN <userId> <password>" ->
                "Thông tin đăng nhập không hợp lệ.";

            default ->
                "Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.";
        };
    }

}
