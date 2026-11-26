package com.auction.ui;

import com.auction.session.ClientSession;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import network.AuctionClient;
import network.UserProfile;

public class RegisterController implements SessionAware {

    @FXML
    private Pane ambientPane;
    @FXML
    private StackPane promoHost;
    @FXML
    private StackPane logoWrap;
    @FXML
    private ImageView imgLogo;
    @FXML
    private VBox registerCard;
    @FXML
    private VBox roleBidderCard;
    @FXML
    private VBox roleSellerCard;
    @FXML
    private StackPane iconBidder;
    @FXML
    private StackPane iconSeller;
    @FXML
    private TextField txtUserId;
    @FXML
    private TextField txtDisplayName;
    @FXML
    private Label lblIdValid;
    @FXML
    private Label lblNameValid;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private PasswordField txtConfirmPassword;
    @FXML
    private ProgressBar pwdStrengthBar;
    @FXML
    private Label lblPwdStrength;
    @FXML
    private Label errorRegister;
    @FXML
    private Button btnRegister;
    @FXML
    private Button btnThemeToggle;

    private ClientSession clientSession;
    private BidXPromoShowcase promoShowcase;
    private String selectedRole = "BIDDER";
    private volatile boolean registerInProgress = false;

    private final AuctionClient.AuctionEventListener registerListener = new AuctionClient.AuctionEventListener() {
        @Override
        public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
        }

        @Override
        public void onMessageReceived(String rawMessage) {
            if (!registerInProgress) {
                return;
            }
            if (rawMessage.startsWith("SUCCESS REGISTER ")) {
                try {
                    clientSession.getClient().requestProfile();
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        registerInProgress = false;
                        btnRegister.setDisable(false);
                        ViewManager.SwitchView("LogIn");
                    });
                }
            } else if (rawMessage.startsWith("ERROR ")) {
                String err = rawMessage.substring("ERROR ".length());
                Platform.runLater(() -> {
                    registerInProgress = false;
                    btnRegister.setDisable(false);
                    errorRegister.setStyle("-fx-text-fill: #FB7185; -fx-font-size: 12.5px; -fx-font-weight: bold;");
                    errorRegister.setText("Đăng ký thất bại: " + err);
                    BidXAnimations.shake(registerCard);
                });
            }
        }

        @Override
        public void onProfileReceived(UserProfile profile) {
            if (!registerInProgress) {
                return;
            }
            Platform.runLater(() -> {
                registerInProgress = false;
                btnRegister.setDisable(false);
                errorRegister.setText("");
                if (clientSession != null) {
                    clientSession.setCurrentUser(profile);
                }
                ViewManager.clearSessionViews();
                ViewManager.routeByRole(profile);
                NotificationManager.showPayout("ĐĂNG KÝ THÀNH CÔNG!",
                        "Chào mừng " + profile.name() + " gia nhập cộng đồng BidX!");
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
        if (iconBidder != null) {
            iconBidder.getChildren().setAll(SvgIcons.create(SvgIcons.GAVEL, 16, "#FF9E44"));
        }
        if (iconSeller != null) {
            iconSeller.getChildren().setAll(SvgIcons.create(SvgIcons.STORE, 16, "#34D399"));
        }

        txtUserId.textProperty().addListener((obs, o, n) ->
                lblIdValid.setText(n != null && n.trim().length() >= 3 ? "✓ Hợp lệ" : ""));
        txtDisplayName.textProperty().addListener((obs, o, n) ->
                lblNameValid.setText(n != null && n.trim().length() >= 2 ? "✓ Hợp lệ" : ""));
        txtPassword.textProperty().addListener((obs, o, n) -> updatePasswordStrength(n));

        BidXAnimations.attachAmbientOrbs(ambientPane);
        BidXAnimations.applyButtonJuice(btnRegister);
        BidXAnimations.attachButtonGlowPulse(btnRegister);
        BidXAnimations.fadeSlideIn(promoHost, 40, 16);
        BidXAnimations.fadeSlideIn(registerCard, 130, 20);
        ThemeManager.syncToggleButton(btnThemeToggle);
        ThemeManager.addListener(dark -> ThemeManager.syncToggleButton(btnThemeToggle));
    }

    @FXML
    public void handleToggleTheme() {
        ThemeManager.toggleTheme();
    }

    private void updatePasswordStrength(String pwd) {
        if (pwd == null || pwd.isEmpty()) {
            pwdStrengthBar.setProgress(0.0);
            lblPwdStrength.setText("");
            return;
        }
        int score = 0;
        if (pwd.length() >= 8) {
            score++;
        }
        if (pwd.length() >= 11) {
            score++;
        }
        if (pwd.matches(".*[A-Z].*") && pwd.matches(".*[a-z].*")) {
            score++;
        }
        if (pwd.matches(".*\\d.*") || pwd.matches(".*[^a-zA-Z0-9].*")) {
            score++;
        }

        double progress = Math.max(0.2, score / 4.0);
        pwdStrengthBar.setProgress(progress);
        if (pwd.length() < 8) {
            lblPwdStrength.setText("Cần tối thiểu 8 ký tự");
            lblPwdStrength.setStyle("-fx-text-fill: #FB7185; -fx-font-size: 11px; -fx-font-weight: bold;");
            pwdStrengthBar.setStyle("-fx-accent: #F43F5E; -fx-control-inner-background: #1C2640;");
        } else if (score <= 2) {
            lblPwdStrength.setText("Trung bình");
            lblPwdStrength.setStyle("-fx-text-fill: #FBBF24; -fx-font-size: 11px; -fx-font-weight: bold;");
            pwdStrengthBar.setStyle("-fx-accent: #F59E0B; -fx-control-inner-background: #1C2640;");
        } else if (score == 3) {
            lblPwdStrength.setText("Mạnh");
            lblPwdStrength.setStyle("-fx-text-fill: #34D399; -fx-font-size: 11px; -fx-font-weight: bold;");
            pwdStrengthBar.setStyle("-fx-accent: #10B981; -fx-control-inner-background: #1C2640;");
        } else {
            lblPwdStrength.setText("Siêu bảo mật");
            lblPwdStrength.setStyle("-fx-text-fill: #38BDF8; -fx-font-size: 11px; -fx-font-weight: bold;");
            pwdStrengthBar.setStyle("-fx-accent: #38BDF8; -fx-control-inner-background: #1C2640;");
        }
    }

    @Override
    public void setClientSession(ClientSession session) {
        if (this.clientSession != null && this.clientSession.getClient() != null) {
            this.clientSession.getClient().removeListener(registerListener);
        }
        this.clientSession = session;
        if (this.clientSession != null && this.clientSession.getClient() != null) {
            this.clientSession.getClient().addListener(registerListener);
        }
        if (promoShowcase != null) {
            promoShowcase.startLoop();
        }
    }

    @FXML
    public void selectBidderRole() {
        selectedRole = "BIDDER";
        if (!roleBidderCard.getStyleClass().contains("role-choice-selected")) {
            roleBidderCard.getStyleClass().add("role-choice-selected");
        }
        roleSellerCard.getStyleClass().remove("role-choice-selected");
        BidXAnimations.popScale(roleBidderCard, 1.04);
        SoundEffects.playReactionTap();
    }

    @FXML
    public void selectSellerRole() {
        selectedRole = "SELLER";
        if (!roleSellerCard.getStyleClass().contains("role-choice-selected")) {
            roleSellerCard.getStyleClass().add("role-choice-selected");
        }
        roleBidderCard.getStyleClass().remove("role-choice-selected");
        BidXAnimations.popScale(roleSellerCard, 1.04);
        SoundEffects.playReactionTap();
    }

    @FXML
    private void Register() {
        if (registerInProgress) {
            return;
        }
        String userId = txtUserId.getText() != null ? txtUserId.getText().trim().replaceAll("\\s+", "_") : "";
        String displayName = txtDisplayName.getText() != null ? txtDisplayName.getText().trim().replaceAll("\\s+", "_") : "";
        String password = txtPassword.getText() != null ? txtPassword.getText() : "";
        String confirm = txtConfirmPassword != null && txtConfirmPassword.getText() != null ? txtConfirmPassword.getText() : "";

        if (userId.isEmpty() || displayName.isEmpty()) {
            errorRegister.setText("Vui lòng nhập mã tài khoản và tên hiển thị.");
            BidXAnimations.shake(registerCard);
            return;
        }
        if (password.length() < 8) {
            errorRegister.setText("Mật khẩu phải chứa ít nhất 8 ký tự.");
            BidXAnimations.shake(registerCard);
            return;
        }
        if (txtConfirmPassword != null && !password.equals(confirm)) {
            errorRegister.setText("Mật khẩu xác nhận chưa trùng khớp.");
            BidXAnimations.shake(txtConfirmPassword);
            return;
        }
        if (clientSession == null) {
            errorRegister.setText("Phiên kết nối chưa sẵn sàng.");
            return;
        }

        registerInProgress = true;
        btnRegister.setDisable(true);
        errorRegister.setStyle("-fx-text-fill: #38BDF8; -fx-font-size: 12.5px; -fx-font-weight: bold;");
        errorRegister.setText("Đang khởi tạo tài khoản " + selectedRole + " trên BidX Server...");

        Thread worker = new Thread(() -> {
            try {
                clientSession.connect();
                clientSession.getClient().addListener(registerListener);
                clientSession.getClient().register(userId, displayName, password, selectedRole);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    registerInProgress = false;
                    btnRegister.setDisable(false);
                    errorRegister.setStyle("-fx-text-fill: #FB7185; -fx-font-size: 12.5px; -fx-font-weight: bold;");
                    errorRegister.setText("Không thể kết nối máy chủ BidX. Hãy chạy start-server.bat trước!");
                    BidXAnimations.shake(registerCard);
                });
            }
        }, "bidx-register-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void backToLogin() {
        ViewManager.SwitchView("LogIn");
    }
}
