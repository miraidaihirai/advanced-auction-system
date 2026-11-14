package com.auction.UI;

import com.auction.sessions.ClientSession;
import network.AuctionClient;
import java.io.IOException;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.concurrent.Task;

public class LogInController implements SessionAware, ViewLifecycle {
    private ClientSession clientSession;
    private boolean listenerRegistered = false;
    private volatile boolean loginPending = false;
    // Xác định yêu càu đăng nhập có đang chờ phản hồi hay không
    @FXML
    private AnchorPane LoginPane;
    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField usernameField;

    @FXML
    private Label errorLabel;

    @FXML
    private void SwitchToHome() throws IOException {
        if (clientSession == null) {
            errorLabel.setText("Client session is not available.");
            return;
        }
        String userId = usernameField.getText().trim();
        String password = passwordField.getText();
        if (userId.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please enter username and password.");
            return;
        }
        if (loginPending) {
            return;
        }
        loginPending = true;
        errorLabel.setText("Connecting to server...");
        Task<Void> loginTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (!clientSession.isConnected()) {
                    clientSession.connect();
                }
                clientSession.getClient().login(userId, password);
                return null;
            }
            // Task.call() chạy trên background thread. Do việc kết nối Socket mất thời gian
            // nên không được thực hiện trực tiếp trên JavaFX Application Thread
        };
        loginTask.setOnFailed(event -> {
            loginPending = false;
            Throwable exception = loginTask.getException();
            String message = exception != null ? exception.getMessage() : "Unable to connect to server.";
            errorLabel.setText("Connection failed: " + message);
        });
        Thread loginThread = new Thread(loginTask, "auction-login-task");
        loginThread.setDaemon(true);
        loginThread.start();
    }
    // Người dùng đăng nhập sẽ dùng SwitchToHome() để lấy username và account
    // Kết nối trên sever nếu chưa kết nối
    // AuctionClient.login(userId, password) đảm nhiệm gửi LOGIN đến server

    @FXML
    private void SwitchToRegister() throws IOException {
        ViewManager.SwitchView("Register");
    }

    private final AuctionClient.AuctionEventListener loginListener = new AuctionClient.AuctionEventListener() {
        @Override
        public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
        }

        @Override
        public void onMessageReceived(String rawMessage) {
            handleServerMessage(rawMessage);
        }
    };

    // loginListener lắng nghe sự kiện từ AuctionClient
    // onMessageReceived nhận từng phản hồi của server
    @Override
    public void setClientSession(ClientSession session) {
        this.clientSession = session;
    }

    @Override
    public void onViewShown() {
        if (clientSession == null || listenerRegistered) {
            return;
        }
        clientSession.getClient().addListener(loginListener);
        listenerRegistered = true;
    }

    @Override
    public void onViewHidden() {
        if (clientSession != null && listenerRegistered) {
            clientSession.getClient().removeListener(loginListener);
            listenerRegistered = false;
        }
    }

    @Override
    public void dispose() {
        onViewHidden();
    }

    private void handleServerMessage(String rawMessage) {
        if (!loginPending) {
            return;
        }
        if (rawMessage.startsWith("SUCCESS LOGIN ")) {
            loginPending = false;
            Platform.runLater(() -> {
                errorLabel.setText("");
                ViewManager.SwitchView("Home");
            });
        } else if (rawMessage.startsWith("ERROR ")) {
            loginPending = false;
            String errorMessage = rawMessage.substring("ERROR ".length());
            Platform.runLater(() -> {
                errorLabel.setText(errorMessage);
            });
        }
    }
}
