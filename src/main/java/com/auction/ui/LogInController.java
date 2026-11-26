package com.auction.ui;

import com.auction.session.ClientSession;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import network.AuctionClient;
import network.UserProfile;

public class LogInController implements SessionAware {

    @FXML
    private StackPane loginRoot;
    @FXML
    private Pane ambientPane;
    @FXML
    private StackPane promoHost;
    @FXML
    private VBox loginCard;
    @FXML
    private StackPane logoWrap;
    @FXML
    private ImageView imgLogo;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button btnLogin;
    @FXML
    private Button btnThemeToggle;

    private ClientSession clientSession;
    private BidXPromoShowcase promoShowcase;
    private volatile boolean loginInProgress = false;

    private final AuctionClient.AuctionEventListener loginListener = new AuctionClient.AuctionEventListener() {
        @Override
        public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
        }

        @Override
        public void onMessageReceived(String rawMessage) {
            if (!loginInProgress) {
                return;
            }
            if (rawMessage.startsWith("SUCCESS LOGIN ")) {
                try {
                    clientSession.getClient().requestProfile();
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        loginInProgress = false;
                        btnLogin.setDisable(false);
                        ViewManager.SwitchView("Home");
                    });
                }
            } else if (rawMessage.startsWith("ERROR ")) {
                String serverError = rawMessage.substring("ERROR ".length());
                Platform.runLater(() -> {
                    loginInProgress = false;
                    btnLogin.setDisable(false);
                    errorLabel.setText(translateLoginError(serverError));
                    BidXAnimations.shake(loginCard);
                });
            }
        }

        @Override
        public void onProfileReceived(UserProfile profile) {
            if (!loginInProgress) {
                return;
            }
            Platform.runLater(() -> {
                loginInProgress = false;
                btnLogin.setDisable(false);
                errorLabel.setText("");
                if (clientSession != null) {
                    clientSession.setCurrentUser(profile);
                }
                SoundEffects.playApproveChime();
                ViewManager.clearSessionViews();
                ViewManager.routeByRole(profile);
                NotificationManager.showSuccess("Chào mừng " + profile.name() + " đến với BidX!");
            });
        }

        @Override
        public void onDisconnected() {
            if (!loginInProgress) {
                return;
            }
            Platform.runLater(() -> {
                loginInProgress = false;
                btnLogin.setDisable(false);
                errorLabel.setText("Mất kết nối đến máy chủ BidX. Hãy kiểm tra Server đã chạy chưa.");
                BidXAnimations.shake(loginCard);
            });
        }
    };

    @FXML
    public void initialize() {
        if (imgLogo != null) {
            imgLogo.setImage(ProductImageService.getBrandLogo(96));
            Rectangle clip = new Rectangle(46, 46);
            clip.setArcWidth(12);
            clip.setArcHeight(12);
            imgLogo.setClip(clip);
        }
        if (logoWrap != null) {
            BidXAnimations.attachLogoAura(logoWrap);
        }
        if (promoHost != null) {
            promoShowcase = new BidXPromoShowcase();
            promoHost.getChildren().setAll(promoShowcase);
        }
        BidXAnimations.attachAmbientOrbs(ambientPane);
        BidXAnimations.applyButtonJuice(btnLogin);
        BidXAnimations.attachButtonGlowPulse(btnLogin);
        BidXAnimations.fadeSlideIn(promoHost, 40, 16);
        BidXAnimations.fadeSlideIn(loginCard, 130, 20);
        ThemeManager.syncToggleButton(btnThemeToggle);
        ThemeManager.addListener(dark -> ThemeManager.syncToggleButton(btnThemeToggle));
    }

    @FXML
    public void handleToggleTheme() {
        ThemeManager.toggleTheme();
    }

    @Override
    public void setClientSession(ClientSession session) {
        if (this.clientSession != null && this.clientSession.getClient() != null) {
            this.clientSession.getClient().removeListener(loginListener);
        }
        this.clientSession = session;
        if (this.clientSession != null && this.clientSession.getClient() != null) {
            this.clientSession.getClient().addListener(loginListener);
            if (hostField != null) {
                hostField.setText(session.getHost());
            }
            if (portField != null) {
                portField.setText(String.valueOf(session.getPort()));
            }
        }
        if (promoShowcase != null) {
            promoShowcase.startLoop();
        }
    }

    private void quickLogin(String userId) {
        usernameField.setText(userId);
        passwordField.setText("password123");
        SwitchToHome();
    }

    @FXML
    public void handleQuickXuanThang() {
        quickLogin("BIDDER-01");
    }

    @FXML
    public void handleQuickHuyHung() {
        quickLogin("BIDDER-02");
    }

    @FXML
    public void handleQuickTuanDai() {
        quickLogin("SELLER-01");
    }

    @FXML
    public void handleQuickPhamTruong() {
        quickLogin("SELLER-02");
    }

    @FXML
    public void handleQuickAdmin() {
        quickLogin("ADMIN-01");
    }

    @FXML
    public void SwitchToHome() {
        if (loginInProgress) {
            return;
        }
        String userId = usernameField.getText() != null ? usernameField.getText().trim() : "";
        String password = passwordField.getText() != null ? passwordField.getText() : "";

        if (userId.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Vui lòng nhập đầy đủ mã tài khoản và mật khẩu.");
            BidXAnimations.shake(loginCard);
            return;
        }
        if (clientSession == null) {
            errorLabel.setText("Phiên kết nối chưa được khởi tạo.");
            return;
        }

        String host = (hostField != null && !hostField.getText().isBlank()) ? hostField.getText().trim() : "localhost";
        int port = 8888;
        try {
            if (portField != null && !portField.getText().isBlank()) {
                port = Integer.parseInt(portField.getText().trim());
            }
        } catch (NumberFormatException e) {
            errorLabel.setText("Cổng kết nối không hợp lệ.");
            BidXAnimations.shake(loginCard);
            return;
        }

        final String finalHost = host;
        final int finalPort = port;

        loginInProgress = true;
        btnLogin.setDisable(true);
        errorLabel.setStyle("-fx-text-fill: #38BDF8; -fx-font-size: 12.5px; -fx-font-weight: bold;");
        errorLabel.setText("Đang kết nối tới BidX Server (" + finalHost + ":" + finalPort + ")...");

        Thread loginThread = new Thread(() -> {
            try {
                boolean endpointChanged = !finalHost.equals(clientSession.getHost()) || finalPort != clientSession.getPort();
                if (endpointChanged) {
                    clientSession.getClient().removeListener(loginListener);
                    clientSession.reconnectEndpoint(finalHost, finalPort);
                    clientSession.getClient().addListener(loginListener);
                } else {
                    clientSession.connect();
                    clientSession.getClient().addListener(loginListener);
                }
                clientSession.getClient().login(userId, password);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    loginInProgress = false;
                    btnLogin.setDisable(false);
                    errorLabel.setStyle("-fx-text-fill: #FB7185; -fx-font-size: 12.5px; -fx-font-weight: bold;");
                    errorLabel.setText("Không thể kết nối máy chủ " + finalHost + ":" + finalPort + ". Hãy chạy start-server.bat trước!");
                    BidXAnimations.shake(loginCard);
                });
            }
        }, "bidx-login-worker");
        loginThread.setDaemon(true);
        loginThread.start();
    }

    @FXML
    public void SwitchToRegister() {
        ViewManager.SwitchView("Register");
    }

    private String translateLoginError(String message) {
        errorLabel.setStyle("-fx-text-fill: #FB7185; -fx-font-size: 12.5px; -fx-font-weight: bold;");
        return switch (message.trim()) {
            case "Invalid user credentials" -> "Mã tài khoản hoặc mật khẩu không chính xác.";
            case "Usage: LOGIN <userId> <password>" -> "Thông tin đăng nhập không hợp lệ.";
            default -> "Đăng nhập thất bại: " + message;
        };
    }
}
