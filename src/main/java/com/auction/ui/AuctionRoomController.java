package com.auction.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.auction.model.BidTransaction;
import com.auction.session.ClientSession;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import javafx.util.Duration;
import network.AuctionClient;
import network.AuctionDetail;
import network.AuctionSummary;
import network.ChatMessageDTO;
import network.UserProfile;

public class AuctionRoomController implements SessionAware, AuctionClient.AuctionEventListener {

    private record BidFeedItem(String bidderId, double amount, String timeText, boolean isLeader) {
    }

    @FXML
    private Button btnBack;
    @FXML
    private Button btnThemeToggle;
    @FXML
    private Label lblRoomTitle;
    @FXML
    private Label lblAuctionId;
    @FXML
    private Label lblAntiSnipingBadge;
    @FXML
    private Label lblLiveBadge;
    @FXML
    private StackPane walletPillIcon;
    @FXML
    private Label lblRoomWallet;

    @FXML
    private ImageView imgShowcase;
    @FXML
    private Pane reactionFxLayer;
    @FXML
    private HBox thumbnailGallery;
    @FXML
    private ListView<String> lvRoomChat;
    @FXML
    private TextField txtChatInput;
    @FXML
    private Button btnEmojiPicker;
    private Popup activeEmojiPopup;
    @FXML
    private Label lblCategoryTag;
    @FXML
    private Label lblAuctionStatus;
    @FXML
    private Label lblItemName;
    @FXML
    private Label lblDescription;
    @FXML
    private Label lblSellerAvatar;
    @FXML
    private Label lblSeller;
    @FXML
    private Label lblStartPrice;
    @FXML
    private Label lblMinStep;
    @FXML
    private StackPane chartIconWrap;
    @FXML
    private LineChart<String, Number> priceChart;
    @FXML
    private CategoryAxis xAxis;
    @FXML
    private NumberAxis yAxis;

    @FXML
    private VBox timerCardBox;
    @FXML
    private Label lblTimerCaption;
    @FXML
    private Label lblCountdownTimer;
    @FXML
    private Label lblCurrentPrice;
    @FXML
    private Label lblHighestBidder;
    @FXML
    private Label lblStepInfo;
    @FXML
    private Label lblTotalBidBadge;
    @FXML
    private ListView<BidFeedItem> lvBidHistory;

    @FXML
    private VBox bidderControlsBox;
    @FXML
    private Button btnQuick1;
    @FXML
    private Button btnQuick2;
    @FXML
    private Button btnQuick3;
    @FXML
    private Button btnQuick4;
    @FXML
    private TextField txtBidAmount;
    @FXML
    private Button btnPlaceBid;

    private final double[] quickIncrements = {2_000_000.0, 5_000_000.0, 10_000_000.0, 20_000_000.0};

    @FXML
    private StackPane autoBidIconWrap;
    @FXML
    private StackPane autoBidTrack;
    @FXML
    private Circle autoBidThumb;
    @FXML
    private VBox autoBidConfigBox;
    @FXML
    private TextField txtAutoMaxBid;
    @FXML
    private TextField txtAutoIncrement;

    @FXML
    private VBox winnerPayBox;
    @FXML
    private Label lblWinnerTitle;
    @FXML
    private Label lblWinnerSub;
    @FXML
    private Button btnConfirmPay;

    @FXML
    private VBox sellerLockBox;
    @FXML
    private StackPane sellerLockIcon;
    @FXML
    private Label lblSellerPayoutInfo;

    @FXML
    private VBox adminLockBox;
    @FXML
    private StackPane adminLockIcon;
    @FXML
    private Button btnAdminCloseRoom;

    @FXML
    private VBox endedNoticeBox;
    @FXML
    private Label lblEndedSummary;
    @FXML
    private Label lblStatusMessage;

    private ClientSession clientSession;
    private AuctionDetail currentDetail;
    private String currentAuctionId = "";
    private String itemName = "";
    private double currentPrice = 0.0;
    private double stepPrice = 500_000.0;
    private String highestBidderId = "NONE";
    private String currentStatus = "RUNNING";
    private long endTimeMillis = 0L;
    private boolean autoBidExpanded = false;

    private XYChart.Series<String, Number> priceSeries;
    private Timeline countdownTimeline;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    public void initialize() {
        btnBack.setGraphic(SvgIcons.create(SvgIcons.ARROW_LEFT, 14, "#CBD5E1"));
        walletPillIcon.getChildren().setAll(SvgIcons.create(SvgIcons.WALLET, 15, "#FBBF24"));
        chartIconWrap.getChildren().setAll(SvgIcons.create(SvgIcons.TRENDING, 18, "#FF8C2B"));
        autoBidIconWrap.getChildren().setAll(SvgIcons.create(SvgIcons.ZAP, 15, "#FF9E44"));
        sellerLockIcon.getChildren().setAll(SvgIcons.create(SvgIcons.STORE, 16, "#34D399"));
        adminLockIcon.getChildren().setAll(SvgIcons.create(SvgIcons.SHIELD, 16, "#38BDF8"));

        setupChatCellFactory();
        if (btnEmojiPicker != null) {
            btnEmojiPicker.setGraphic(BidXEmojiPicker.createEmojiView("😀", 20));
            btnEmojiPicker.setTooltip(new Tooltip("Chọn biểu tượng cảm xúc"));
        }

        BidXAnimations.applyButtonJuice(btnPlaceBid);
        BidXAnimations.attachButtonGlowPulse(btnPlaceBid);
        BidXAnimations.applyButtonJuice(btnQuick1);
        BidXAnimations.applyButtonJuice(btnQuick2);
        BidXAnimations.applyButtonJuice(btnQuick3);
        BidXAnimations.applyButtonJuice(btnQuick4);
        BidXAnimations.startPulse(lblLiveBadge, 0.72, 1.04, 1700);
        ThemeManager.syncToggleButton(btnThemeToggle);
        ThemeManager.addListener(dark -> ThemeManager.syncToggleButton(btnThemeToggle));

        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Giá đấu");
        priceChart.getData().clear();
        priceChart.getData().add(priceSeries);
        priceChart.setLegendVisible(false);
        priceChart.setAnimated(false);

        setupBidFeedCellFactory();
        startCountdownTicker();
    }

    private void setupBidFeedCellFactory() {
        lvBidHistory.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(BidFeedItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add(item.isLeader() ? "bid-feed-row-leader" : "bid-feed-row");

                String initial = item.bidderId() != null && !item.bidderId().isBlank()
                        ? item.bidderId().substring(0, 1).toUpperCase(Locale.ROOT) : "B";
                StackPane avatar = new StackPane(new Label(initial));
                avatar.setMinSize(26, 26);
                avatar.setMaxSize(26, 26);
                avatar.setStyle(item.isLeader()
                        ? "-fx-background-color: #FF7B24; -fx-background-radius: 13;"
                        : "-fx-background-color: #475569; -fx-background-radius: 13;");
                ((Label) avatar.getChildren().get(0)).setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");

                VBox mid = new VBox(1);
                HBox.setHgrow(mid, Priority.ALWAYS);
                Label lblUser = new Label(item.bidderId() + (item.isLeader() ? "  ★ Dẫn đầu" : ""));
                lblUser.getStyleClass().add(item.isLeader() ? "text-accent-orange" : "text-primary");
                lblUser.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                Label lblTime = new Label(item.timeText());
                lblTime.getStyleClass().add("text-subtle");
                lblTime.setStyle("-fx-font-size: 10.5px;");
                mid.getChildren().addAll(lblUser, lblTime);

                Label lblAmt = new Label(FormatUtils.formatVnd(item.amount()));
                lblAmt.getStyleClass().add(item.isLeader() ? "text-primary" : "text-secondary");
                lblAmt.setStyle("-fx-font-weight: bold; -fx-font-size: " + (item.isLeader() ? "13px;" : "12px;"));

                row.getChildren().addAll(avatar, mid, lblAmt);
                setGraphic(row);
                setText(null);
            }
        });
    }

    private void startCountdownTicker() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateCountdownDisplay()));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateCountdownDisplay() {
        if (lblCountdownTimer == null) {
            return;
        }
        if (!isStatusRunning()) {
            lblTimerCaption.setText("TRẠNG THÁI PHIÊN");
            lblCountdownTimer.setText("00:00:00");
            return;
        }
        long rem = Math.max(0L, (endTimeMillis - System.currentTimeMillis()) / 1000L);
        lblTimerCaption.setText(rem <= 30 ? "⚡ SẮP KẾT THÚC (ANTI-SNIPING)" : "THỜI GIAN CÒN LẠI");
        lblCountdownTimer.setText(FormatUtils.formatCountdownHMS(rem));
        if (rem > 0 && rem <= 30) {
            BidXAnimations.popScale(lblCountdownTimer, 1.07);
        }
    }

    @Override
    public void setClientSession(ClientSession session) {
        if (this.clientSession != null && this.clientSession.getClient() != null) {
            this.clientSession.getClient().removeListener(this);
        }
        this.clientSession = session;
        if (this.clientSession != null && this.clientSession.getClient() != null) {
            this.clientSession.getClient().addListener(this);
            updateWalletBadge(session.getCurrentUser());
        }
    }

    public void loadAuction(AuctionSummary summary) {
        if (summary == null) {
            return;
        }
        AuctionDetail synthesized = new AuctionDetail(
                summary.auctionId(),
                summary.itemId(),
                "SELLER-01",
                "Alice Luxury",
                FormatUtils.detectCategory(null, summary.itemId()),
                summary.currentPrice(),
                summary.currentPrice(),
                summary.stepPrice() > 0 ? summary.stepPrice() : 500_000.0,
                summary.highestBidderId(),
                summary.status(),
                System.currentTimeMillis() + 300_000L,
                300,
                0,
                false,
                "",
                "",
                "Sản phẩm đấu giá cao cấp được kiểm duyệt trực tiếp bởi hệ thống BidX.");
        loadAuctionDetail(synthesized);
    }

    public void loadAuctionDetail(AuctionDetail detail) {
        if (detail == null) {
            return;
        }
        this.currentDetail = detail;
        this.currentAuctionId = detail.auctionId();
        this.itemName = detail.itemName();
        this.currentPrice = detail.currentPrice();
        this.stepPrice = detail.stepPrice() > 0 ? detail.stepPrice() : 500_000.0;
        this.highestBidderId = detail.highestBidderId() != null ? detail.highestBidderId() : "NONE";
        this.currentStatus = detail.status() != null ? detail.status() : "RUNNING";
        this.endTimeMillis = detail.endTimeMillis() > 0 ? detail.endTimeMillis() : (System.currentTimeMillis() + 300_000L);

        Platform.runLater(() -> {
            lblRoomTitle.setText(itemName);
            lblAuctionId.setText(currentAuctionId);
            lblItemName.setText(itemName);
            lblCategoryTag.setText(FormatUtils.categoryLabel(FormatUtils.detectCategory(detail.category(), itemName)).toUpperCase(Locale.ROOT));
            lblDescription.setText(detail.description() != null && !detail.description().isBlank()
                    ? detail.description()
                    : "Sản phẩm đấu giá chính hãng đã qua kiểm định bởi Admin BidX.");

            String sellerDisplay = detail.sellerName() + " (" + detail.sellerId() + ")";
            lblSeller.setText(sellerDisplay);
            lblSellerAvatar.setText(detail.sellerName() != null && !detail.sellerName().isBlank()
                    ? detail.sellerName().substring(0, 1).toUpperCase(Locale.ROOT) : "S");
            lblStartPrice.setText(FormatUtils.formatVnd(detail.startPrice()));
            lblMinStep.setText("+" + FormatUtils.formatVnd(stepPrice));

            ProductImageService.loadIntoImageView(
                    imgShowcase, currentAuctionId, itemName, detail.imageKey(), 790, 310, 14);
            buildThumbnailGallery(detail.imageKey(), itemName);

            updateStatusBadges();
            updatePriceDisplay(false);
            updateSmartQuickBidChips();
            updateRoleControls();
            updateCountdownDisplay();
            updateWalletBadge(clientSession != null ? clientSession.getCurrentUser() : null);

            priceSeries.getData().clear();
            priceSeries.getData().add(new XYChart.Data<>("Khởi điểm", detail.startPrice()));

            lvBidHistory.getItems().clear();
            lblTotalBidBadge.setText(detail.bidCount() + " lượt");
            if (lvRoomChat != null) {
                lvRoomChat.getItems().clear();
                lvRoomChat.getItems().add("SYSTEM|Chào mừng đến với phòng đấu giá " + itemName + "! Gửi tin nhắn hoặc thả reaction ngay.");
            }

            setDefaultBidInputs();
            setStatus("Đã kết nối phòng đấu giá " + currentAuctionId + " (Realtime)", "#10B981");
        });

        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().joinAuction(currentAuctionId);
            clientSession.getClient().requestHistory(currentAuctionId);
            clientSession.getClient().requestChatHistory(currentAuctionId);
            clientSession.getClient().requestProfile();
        }
    }

    private void buildThumbnailGallery(String primaryKey, String name) {
        thumbnailGallery.getChildren().clear();
        String resolvedKey = (primaryKey != null && !primaryKey.isBlank())
                ? primaryKey
                : ProductImageService.resolveResourceKey(primaryKey, name);
        List<ProductImageService.GalleryAngle> angles = ProductImageService.GALLERY_ANGLES;
        for (int i = 0; i < angles.size(); i++) {
            final ProductImageService.GalleryAngle angle = angles.get(i);
            ImageView thumb = new ImageView();
            ProductImageService.loadIntoImageViewWithAngle(thumb, currentAuctionId, name, resolvedKey, 108, 66, 8, angle);

            Label angleTag = new Label(angle.label());
            angleTag.setStyle("-fx-background-color: rgba(8,12,24,0.78); -fx-text-fill: #F8FAFC;"
                    + " -fx-font-size: 9.5px; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 1 6 1 6;");
            StackPane.setAlignment(angleTag, Pos.BOTTOM_LEFT);
            StackPane.setMargin(angleTag, new javafx.geometry.Insets(0, 0, 4, 4));

            StackPane frame = new StackPane(thumb, angleTag);
            frame.getStyleClass().add("thumb-frame");
            if (i == 0) {
                frame.getStyleClass().add("thumb-frame-active");
            }
            frame.setOnMouseClicked(e -> {
                for (var child : thumbnailGallery.getChildren()) {
                    child.getStyleClass().remove("thumb-frame-active");
                }
                frame.getStyleClass().add("thumb-frame-active");
                ProductImageService.loadIntoImageViewWithAngle(imgShowcase, currentAuctionId, name, resolvedKey, 790, 310, 14, angle);
                FadeTransition ft = new FadeTransition(Duration.millis(200), imgShowcase);
                ft.setFromValue(0.45);
                ft.setToValue(1.0);
                ft.play();
            });
            thumbnailGallery.getChildren().add(frame);
        }
    }

    private void updateStatusBadges() {
        String badgeClass = FormatUtils.statusBadgeStyleClass(currentStatus);
        lblAuctionStatus.getStyleClass().removeAll(
                "badge-live", "badge-pending", "badge-finished", "badge-paid", "badge-rejected", "badge-muted");
        lblAuctionStatus.getStyleClass().add(badgeClass);
        lblAuctionStatus.setText(FormatUtils.statusLabel(currentStatus));

        lblLiveBadge.getStyleClass().removeAll(
                "badge-live", "badge-pending", "badge-finished", "badge-paid", "badge-rejected", "badge-muted");
        lblLiveBadge.getStyleClass().add(badgeClass);
        lblLiveBadge.setText(isStatusRunning() ? "● ĐANG TRỰC TIẾP" : FormatUtils.statusLabel(currentStatus));
    }

    private void updatePriceDisplay(boolean animate) {
        lblCurrentPrice.setText(FormatUtils.formatVnd(currentPrice));
        if (animate) {
            BidXAnimations.pricePopFlash(lblCurrentPrice);
        }
        boolean hasLeader = highestBidderId != null && !highestBidderId.isBlank() && !"NONE".equalsIgnoreCase(highestBidderId);
        lblHighestBidder.setText(hasLeader ? "★ Dẫn đầu: " + highestBidderId : "Chưa có lượt đặt giá");
        lblStepInfo.setText("Bước tối thiểu: +" + FormatUtils.formatVnd(stepPrice));
    }

    private void setDefaultBidInputs() {
        double nextMin = lvBidHistory.getItems().isEmpty() ? currentPrice : (currentPrice + stepPrice);
        if (highestBidderId != null && !"NONE".equalsIgnoreCase(highestBidderId)) {
            nextMin = currentPrice + stepPrice;
        }
        txtBidAmount.setText(String.format(Locale.US, "%.0f", nextMin));
        txtAutoMaxBid.setText(String.format(Locale.US, "%.0f", nextMin + stepPrice * 4));
        txtAutoIncrement.setText(String.format(Locale.US, "%.0f", stepPrice));
    }

    private void updateWalletBadge(UserProfile profile) {
        if (profile != null && lblRoomWallet != null) {
            lblRoomWallet.setText(FormatUtils.formatVnd(profile.balance()));
        }
    }

    /**
     * Phân quyền giao diện tuyệt đối theo đúng vai trò:
     * - BIDDER: chỉ đặt bid / bật Auto-Bid (hoặc thanh toán nếu thắng)
     * - SELLER: KHÔNG được đặt bid, chỉ theo dõi phiên & xem tiền chảy về ví
     * - ADMIN:  KHÔNG được đặt bid, chỉ giám sát & chốt phiên nếu cần
     */
    private void updateRoleControls() {
        UserProfile me = clientSession != null ? clientSession.getCurrentUser() : null;
        boolean isSeller = me != null && me.isSeller();
        boolean isAdmin = me != null && me.isAdmin();
        boolean isBidder = !isSeller && !isAdmin;
        boolean running = isStatusRunning();
        boolean iAmWinner = me != null && me.userId().equals(highestBidderId);
        boolean hasWinner = highestBidderId != null && !highestBidderId.isBlank() && !"NONE".equalsIgnoreCase(highestBidderId);

        setVisibleManaged(bidderControlsBox, isBidder && running);
        setVisibleManaged(sellerLockBox, isSeller);
        setVisibleManaged(adminLockBox, isAdmin);
        setVisibleManaged(winnerPayBox, isBidder && !running && iAmWinner);
        setVisibleManaged(endedNoticeBox, isBidder && !running && !iAmWinner);

        boolean settled = "PAID".equalsIgnoreCase(currentStatus)
                || (currentDetail != null && currentDetail.payoutSettled());

        if (isSeller) {
            if (running) {
                lblSellerPayoutInfo.setText(hasWinner
                        ? "Doanh thu dự kiến về ví Seller: +" + FormatUtils.formatVnd(currentPrice) + " (Người giữ giá: " + highestBidderId + ")"
                        : "Đang chờ Bidder đầu tiên đặt giá...");
            } else if (hasWinner) {
                if (settled) {
                    lblSellerPayoutInfo.setText("💰 TIỀN ĐÃ CHẢY VỀ VÍ SELLER: +" + FormatUtils.formatVnd(currentPrice)
                            + " từ người thắng " + highestBidderId + "!");
                    lblSellerPayoutInfo.setStyle("-fx-text-fill: #34D399; -fx-font-weight: bold; -fx-font-size: 13.5px;");
                } else {
                    lblSellerPayoutInfo.setText("⏳ CHỜ THANH TOÁN: Người thắng " + highestBidderId
                            + " chưa đủ số dư ví (" + FormatUtils.formatVnd(currentPrice) + "). Chưa giao hàng!");
                    lblSellerPayoutInfo.setStyle("-fx-text-fill: #FBBF24; -fx-font-weight: bold; -fx-font-size: 13px;");
                }
            } else {
                lblSellerPayoutInfo.setText("Phiên đã đóng mà chưa có lượt đặt giá.");
            }
        }

        if (isAdmin) {
            setVisibleManaged(btnAdminCloseRoom, running);
        }

        if (isBidder && !running && iAmWinner) {
            if ("PAID".equalsIgnoreCase(currentStatus)) {
                lblWinnerTitle.setText("✓ ĐÃ HOÀN TẤT THANH TOÁN & QUYẾT TOÁN");
                lblWinnerSub.setText("Bạn đã sở hữu " + itemName + " với giá " + FormatUtils.formatVnd(currentPrice)
                        + ". Tiền đã được chuyển đầy đủ vào ví Seller.");
                setVisibleManaged(btnConfirmPay, false);
            } else if (!settled && me.balance() < currentPrice) {
                lblWinnerTitle.setText("⚠ SỐ DƯ VÍ KHÔNG ĐỦ ĐỂ THANH TOÁN");
                lblWinnerSub.setText("Giá chốt: " + FormatUtils.formatVnd(currentPrice)
                        + " • Ví hiện có: " + FormatUtils.formatVnd(me.balance())
                        + ". Vui lòng nạp thêm tiền vào ví trước khi xác nhận thanh toán!");
                setVisibleManaged(btnConfirmPay, true);
            } else {
                lblWinnerTitle.setText("CHÚC MỪNG! BẠN ĐÃ THẮNG PHIÊN NÀY");
                lblWinnerSub.setText("Giá chốt: " + FormatUtils.formatVnd(currentPrice)
                        + " • Nhấn xác nhận thanh toán & nhận bàn giao sản phẩm.");
                setVisibleManaged(btnConfirmPay, true);
            }
        }

        if (isBidder && !running && !iAmWinner) {
            lblEndedSummary.setText(hasWinner
                    ? "Phiên đã kết thúc • Người thắng: " + highestBidderId + " (" + FormatUtils.formatVnd(currentPrice) + ")"
                    : "Phiên đã kết thúc mà chưa có người thắng.");
        }
    }

    private void setVisibleManaged(javafx.scene.Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private boolean isStatusRunning() {
        return "RUNNING".equalsIgnoreCase(currentStatus) || "ACTIVE".equalsIgnoreCase(currentStatus);
    }

    private void setStatus(String msg, String hexColor) {
        lblStatusMessage.setText(msg);
        lblStatusMessage.setStyle("-fx-text-fill: " + hexColor + "; -fx-font-size: 12px; -fx-font-weight: bold;");
    }

    /**
     * Tính toán 4 nút tăng giá nhanh tỷ lệ thông minh theo giá trị sản phẩm
     * (không dùng +500k / +1M quá nhỏ so với tài sản hàng chục - hàng trăm triệu).
     */
    private void updateSmartQuickBidChips() {
        double[] increments;
        if (currentPrice >= 100_000_000.0) {
            increments = new double[]{
                    Math.max(stepPrice, 10_000_000.0),
                    Math.max(stepPrice * 2, 20_000_000.0),
                    Math.max(stepPrice * 4, 35_000_000.0),
                    Math.max(stepPrice * 8, 50_000_000.0)
            };
        } else if (currentPrice >= 25_000_000.0) {
            increments = new double[]{
                    Math.max(stepPrice, 3_000_000.0),
                    Math.max(stepPrice * 2, 5_000_000.0),
                    Math.max(stepPrice * 4, 10_000_000.0),
                    Math.max(stepPrice * 8, 20_000_000.0)
            };
        } else {
            increments = new double[]{
                    Math.max(stepPrice, 2_000_000.0),
                    Math.max(stepPrice * 2, 5_000_000.0),
                    Math.max(stepPrice * 4, 10_000_000.0),
                    Math.max(stepPrice * 8, 15_000_000.0)
            };
        }
        System.arraycopy(increments, 0, quickIncrements, 0, 4);
        Button[] btns = {btnQuick1, btnQuick2, btnQuick3, btnQuick4};
        for (int i = 0; i < 4; i++) {
            if (btns[i] != null) {
                btns[i].setText(formatShortIncrement(quickIncrements[i]));
            }
        }
    }

    private String formatShortIncrement(double amount) {
        if (amount >= 1_000_000_000.0) {
            double b = amount / 1_000_000_000.0;
            return (b == Math.floor(b)) ? String.format("+%.0f Tỷ", b) : String.format(Locale.US, "+%.1f Tỷ", b);
        }
        double m = amount / 1_000_000.0;
        return (m == Math.floor(m)) ? String.format("+%.0fM", m) : String.format(Locale.US, "+%.1fM", m);
    }

    @FXML
    public void handleToggleTheme() {
        ThemeManager.toggleTheme();
    }

    @FXML
    public void handleQuickBid1() {
        applyIncrement(quickIncrements[0]);
    }

    @FXML
    public void handleQuickBid2() {
        applyIncrement(quickIncrements[1]);
    }

    @FXML
    public void handleQuickBid3() {
        applyIncrement(quickIncrements[2]);
    }

    @FXML
    public void handleQuickBid4() {
        applyIncrement(quickIncrements[3]);
    }

    private void applyIncrement(double delta) {
        double next = currentPrice + Math.max(delta, stepPrice);
        txtBidAmount.setText(String.format(Locale.US, "%.0f", next));
        BidXAnimations.popScale(txtBidAmount, 1.03);
    }

    @FXML
    public void handlePlaceBid() {
        if (clientSession == null || !clientSession.isConnected()) {
            setStatus("Chưa kết nối tới máy chủ BidX!", "#F43F5E");
            return;
        }
        UserProfile me = clientSession.getCurrentUser();
        if (me != null && !me.isBidder()) {
            NotificationManager.showError("Chỉ tài khoản Bidder mới được phép đặt giá!");
            return;
        }
        String raw = txtBidAmount.getText() != null ? txtBidAmount.getText().trim().replaceAll("[,._\\s]", "") : "";
        if (raw.isEmpty()) {
            setStatus("Vui lòng nhập số tiền đặt giá!", "#F43F5E");
            BidXAnimations.shake(txtBidAmount);
            return;
        }
        try {
            double amount = Double.parseDouble(raw);
            clientSession.getClient().placeBid(currentAuctionId, amount);
            setStatus("Đang gửi lệnh đặt giá " + FormatUtils.formatVnd(amount) + "...", "#38BDF8");
        } catch (NumberFormatException e) {
            setStatus("Số tiền nhập vào không hợp lệ!", "#F43F5E");
            BidXAnimations.shake(txtBidAmount);
        }
    }

    @FXML
    public void handleToggleAutoBidPanel() {
        autoBidExpanded = !autoBidExpanded;
        setVisibleManaged(autoBidConfigBox, autoBidExpanded);
        autoBidTrack.getStyleClass().removeAll("toggle-track-on", "toggle-track-off");
        autoBidTrack.getStyleClass().add(autoBidExpanded ? "toggle-track-on" : "toggle-track-off");

        TranslateTransition tt = new TranslateTransition(Duration.millis(180), autoBidThumb);
        tt.setToX(autoBidExpanded ? 10 : -10);
        tt.play();
    }

    @FXML
    public void handleSaveAutoBid() {
        if (clientSession == null || !clientSession.isConnected()) {
            return;
        }
        try {
            double maxBid = Double.parseDouble(txtAutoMaxBid.getText().trim().replaceAll("[,._\\s]", ""));
            double inc = Double.parseDouble(txtAutoIncrement.getText().trim().replaceAll("[,._\\s]", ""));
            clientSession.getClient().configureAutoBid(currentAuctionId, maxBid, inc);
            setStatus("Đã gửi cấu hình Auto-Bid tối đa " + FormatUtils.formatVnd(maxBid), "#10B981");
        } catch (Exception e) {
            setStatus("Giá tối đa hoặc bước tăng Auto-Bid không hợp lệ!", "#F43F5E");
            BidXAnimations.shake(autoBidConfigBox);
        }
    }

    @FXML
    public void handleDisableAutoBid() {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().cancelAutoBid(currentAuctionId);
        }
        if (autoBidExpanded) {
            handleToggleAutoBidPanel();
        }
    }

    @FXML
    public void handleConfirmPay() {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().payAuction(currentAuctionId);
        }
    }

    @FXML
    public void handleAdminCloseAuction() {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().closeAuction(currentAuctionId);
            setStatus("Admin đang chốt phiên và kích hoạt quyết toán về ví Seller...", "#38BDF8");
        }
    }

    private void setupChatCellFactory() {
        if (lvRoomChat == null) {
            return;
        }
        lvRoomChat.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String raw, boolean empty) {
                super.updateItem(raw, empty);
                if (empty || raw == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                String[] parts = raw.split("\\|", 3);
                HBox box = new HBox(8);
                box.setAlignment(Pos.CENTER_LEFT);
                box.getStyleClass().add("chat-bubble");

                if ("SYSTEM".equals(parts[0])) {
                    Label sys = new Label("✦ " + (parts.length > 1 ? parts[1] : raw));
                    sys.setWrapText(true);
                    sys.getStyleClass().add("text-accent-cyan");
                    sys.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold;");
                    box.getChildren().add(sys);
                } else {
                    String senderText = parts[0];
                    String timeText = parts.length >= 3 ? parts[1] : "";
                    String bodyText = parts.length >= 3 ? parts[2] : (parts.length == 2 ? parts[1] : "");

                    Label sender = new Label(senderText + ":");
                    sender.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                    sender.getStyleClass().add(senderText.contains("ADMIN") ? "text-accent-cyan"
                            : (senderText.contains("SELLER") ? "text-accent-emerald" : "text-accent-orange"));
                    sender.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

                    TextFlow bodyFlow = BidXEmojiPicker.buildRichChatFlow(bodyText, "chat-text");
                    HBox.setHgrow(bodyFlow, Priority.ALWAYS);

                    box.getChildren().addAll(sender, bodyFlow);
                    if (!timeText.isBlank()) {
                        Label lblTime = new Label(timeText);
                        lblTime.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                        lblTime.getStyleClass().add("text-subtle");
                        lblTime.setStyle("-fx-font-size: 10.5px;");
                        box.getChildren().add(lblTime);
                    }
                }
                setGraphic(box);
                setText(null);
            }
        });
    }

    @FXML
    public void handleToggleEmojiPicker() {
        if (activeEmojiPopup != null && activeEmojiPopup.isShowing()) {
            activeEmojiPopup.hide();
            return;
        }
        activeEmojiPopup = BidXEmojiPicker.createPickerPopup(emojiCode -> {
            if (txtChatInput != null) {
                int caret = txtChatInput.getCaretPosition();
                String current = txtChatInput.getText() != null ? txtChatInput.getText() : "";
                String updated = current.substring(0, caret) + emojiCode + current.substring(caret);
                txtChatInput.setText(updated);
                txtChatInput.positionCaret(caret + emojiCode.length());
                txtChatInput.requestFocus();
            }
        });
        BidXEmojiPicker.showAboveNode(activeEmojiPopup, btnEmojiPicker);
    }

    private void sendReaction(String label, String colorHex) {
        SoundEffects.playReactionTap();
        BidXAnimations.spawnFloatingReaction(reactionFxLayer, label, colorHex);
        if (clientSession != null && clientSession.isConnected() && !currentAuctionId.isEmpty()) {
            clientSession.getClient().sendRoomChat(currentAuctionId, "REACT:" + label + ":" + colorHex);
        }
    }

    @FXML
    public void handleReactHot() {
        sendReaction("★ QUÁ HOT!", "#FF7B24");
    }

    @FXML
    public void handleReactGem() {
        sendReaction("♦ ĐỈNH CAO!", "#38BDF8");
    }

    @FXML
    public void handleReactZap() {
        sendReaction("⚡ CHỐT NGAY!", "#FBBF24");
    }

    @FXML
    public void handleReactRocket() {
        sendReaction("▲ BAY GIÁ!", "#EC4899");
    }

    @FXML
    public void handleSendChat() {
        if (txtChatInput == null || clientSession == null || !clientSession.isConnected()) {
            return;
        }
        String text = txtChatInput.getText() != null ? txtChatInput.getText().trim() : "";
        if (text.isEmpty()) {
            return;
        }
        clientSession.getClient().sendRoomChat(currentAuctionId, text);
        txtChatInput.clear();
        SoundEffects.playReactionTap();
    }

    @FXML
    public void handleBackToHome() {
        if (clientSession != null && clientSession.isConnected() && !currentAuctionId.isEmpty()) {
            try {
                clientSession.getClient().leaveAuction(currentAuctionId);
            } catch (Exception ignored) {
            }
        }
        ViewManager.returnToRoleHome();
    }

    // ---------- Realtime Observer Callbacks ----------

    @Override
    public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
        if (!auctionId.equals(currentAuctionId)) {
            return;
        }
        Platform.runLater(() -> {
            double delta = Math.max(0, newPrice - this.currentPrice);
            this.currentPrice = newPrice;
            this.highestBidderId = bidderId;

            SoundEffects.playBidPop();
            ViewManager.triggerBidShockwave(delta > 0
                    ? "+" + FormatUtils.formatVnd(delta) + "  •  " + bidderId
                    : FormatUtils.formatVnd(newPrice));
            BidXAnimations.spawnFloatingReaction(reactionFxLayer, "⚡ " + bidderId + " vừa nâng giá!", "#FF8C2B");

            // Đồng bộ Anti-Sniping nếu bid rơi vào 30s cuối
            long remSec = (endTimeMillis - System.currentTimeMillis()) / 1000L;
            if (isStatusRunning() && remSec > 0 && remSec <= 30) {
                endTimeMillis += 30_000L;
                NotificationManager.showWarning("⚡ Anti-Sniping kích hoạt! Phiên được tự động gia hạn thêm +30 giây.");
                BidXAnimations.pricePopFlash(timerCardBox);
            }

            updatePriceDisplay(true);
            updateSmartQuickBidChips();
            setDefaultBidInputs();
            updateRoleControls();

            String timeStr = LocalDateTime.now().format(timeFormatter);
            priceSeries.getData().add(new XYChart.Data<>(timeStr, newPrice));
            if (priceSeries.getData().size() > 18) {
                priceSeries.getData().remove(0);
            }

            // Hạ flag isLeader cho các item cũ rồi chèn item mới lên đầu
            for (int i = 0; i < lvBidHistory.getItems().size(); i++) {
                BidFeedItem old = lvBidHistory.getItems().get(i);
                if (old.isLeader()) {
                    lvBidHistory.getItems().set(i, new BidFeedItem(old.bidderId(), old.amount(), old.timeText(), false));
                }
            }
            lvBidHistory.getItems().add(0, new BidFeedItem(bidderId, newPrice, timeStr, true));
            lblTotalBidBadge.setText(lvBidHistory.getItems().size() + " lượt");
            setStatus("⚡ " + bidderId + " vừa đặt " + FormatUtils.formatVnd(newPrice), "#10B981");
        });
    }

    @Override
    public void onHistoryReceived(String auctionId, List<BidTransaction> history) {
        if (!auctionId.equals(currentAuctionId) || history == null) {
            return;
        }
        Platform.runLater(() -> {
            priceSeries.getData().clear();
            lvBidHistory.getItems().clear();
            if (currentDetail != null) {
                priceSeries.getData().add(new XYChart.Data<>("Khởi điểm", currentDetail.startPrice()));
            }
            for (int i = 0; i < history.size(); i++) {
                BidTransaction tx = history.get(i);
                String t = tx.getTimestamp() != null ? tx.getTimestamp().format(timeFormatter) : "#" + (i + 1);
                priceSeries.getData().add(new XYChart.Data<>(t, tx.getAmount()));
                boolean isLast = (i == history.size() - 1);
                lvBidHistory.getItems().add(0, new BidFeedItem(tx.getBidderId(), tx.getAmount(), t, isLast));
            }
            lblTotalBidBadge.setText(history.size() + " lượt");
            if (!history.isEmpty()) {
                BidTransaction latest = history.get(history.size() - 1);
                this.currentPrice = latest.getAmount();
                this.highestBidderId = latest.getBidderId();
                updatePriceDisplay(false);
                setDefaultBidInputs();
                updateRoleControls();
            }
        });
    }

    @Override
    public void onAuctionClosed(String auctionId, double finalPrice, String winnerId) {
        if (!auctionId.equals(currentAuctionId)) {
            return;
        }
        Platform.runLater(() -> {
            this.currentStatus = "FINISHED";
            this.currentPrice = finalPrice;
            this.highestBidderId = winnerId;
            updateStatusBadges();
            updatePriceDisplay(true);
            updateRoleControls();
            updateCountdownDisplay();
            setStatus("Phiên đấu giá đã kết thúc!", "#FBBF24");
            if (clientSession != null && clientSession.isConnected()) {
                clientSession.getClient().requestProfile();
                clientSession.getClient().requestFullList();
            }
        });
    }

    @Override
    public void onCatalogRefresh() {
        if (clientSession != null && clientSession.isConnected() && !currentAuctionId.isEmpty()) {
            clientSession.getClient().requestFullList();
        }
    }

    @Override
    public void onAuctionDetailsReceived(List<AuctionDetail> auctions) {
        if (auctions == null || currentAuctionId.isEmpty()) {
            return;
        }
        for (AuctionDetail d : auctions) {
            if (currentAuctionId.equals(d.auctionId())) {
                Platform.runLater(() -> {
                    boolean reclaimed = d.isPendingApproval() && !"PENDING_APPROVAL".equalsIgnoreCase(this.currentStatus);
                    this.currentDetail = d;
                    this.currentStatus = d.status();
                    if (reclaimed) {
                        this.currentPrice = d.startPrice();
                        this.highestBidderId = "NONE";
                        priceSeries.getData().clear();
                        priceSeries.getData().add(new XYChart.Data<>("Khởi điểm", d.startPrice()));
                        lvBidHistory.getItems().clear();
                        lblTotalBidBadge.setText("0 lượt");
                        if (lvRoomChat != null) {
                            lvRoomChat.getItems().clear();
                        }
                        updatePriceDisplay(false);
                        updateCountdownDisplay();
                        setStatus("Vật phẩm đã bị thu hồi về hàng chờ Admin duyệt lại do thanh toán thất bại quá 3 lần.", "#F43F5E");
                    }
                    updateStatusBadges();
                    updateRoleControls();
                });
                break;
            }
        }
    }

    @Override
    public void onAuctionPaid(String auctionId, double finalPrice, String winnerId) {
        if (!auctionId.equals(currentAuctionId)) {
            return;
        }
        Platform.runLater(() -> {
            this.currentStatus = "PAID";
            updateStatusBadges();
            updateRoleControls();
            setStatus("✓ Đã hoàn tất xác nhận thanh toán " + FormatUtils.formatVnd(finalPrice), "#34D399");
        });
    }

    @Override
    public void onProfileReceived(UserProfile profile) {
        Platform.runLater(() -> {
            if (clientSession != null) {
                clientSession.setCurrentUser(profile);
            }
            updateWalletBadge(profile);
            updateRoleControls();
        });
    }

    @Override
    public void onChatMessageReceived(String auctionId, String senderId, String senderName, String role, String message) {
        if (!auctionId.equals(currentAuctionId) || message == null) {
            return;
        }
        Platform.runLater(() -> {
            if (message.startsWith("REACT:")) {
                String[] parts = message.split(":", 3);
                String label = parts.length > 1 ? parts[1] : "HOT!";
                String color = parts.length > 2 ? parts[2] : "#FF8C2B";
                UserProfile me = clientSession != null ? clientSession.getCurrentUser() : null;
                if (me == null || !me.userId().equals(senderId)) {
                    BidXAnimations.spawnFloatingReaction(reactionFxLayer, senderName + ": " + label, color);
                    SoundEffects.playReactionTap();
                }
                return;
            }
            if (lvRoomChat != null) {
                String nowShort = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                lvRoomChat.getItems().add(senderName + " (" + role + ")|" + nowShort + "|" + message);
                lvRoomChat.scrollTo(lvRoomChat.getItems().size() - 1);
            }
        });
    }

    @Override
    public void onChatHistoryReceived(String auctionId, List<ChatMessageDTO> messages) {
        if (!auctionId.equals(currentAuctionId) || messages == null) {
            return;
        }
        Platform.runLater(() -> {
            if (lvRoomChat == null) {
                return;
            }
            lvRoomChat.getItems().clear();
            lvRoomChat.getItems().add("SYSTEM|Chào mừng đến với phòng đấu giá " + itemName + "! Gửi tin nhắn hoặc chèn emoji giao lưu ngay.");
            for (ChatMessageDTO msg : messages) {
                String shortTime = FormatUtils.formatTimestampShort(msg.timestamp());
                lvRoomChat.getItems().add(msg.senderName() + " (" + msg.role() + ")|" + shortTime + "|" + msg.message());
            }
            lvRoomChat.scrollTo(lvRoomChat.getItems().size() - 1);
        });
    }

    @Override
    public void onMessageReceived(String rawMessage) {
        if (rawMessage.startsWith("ERROR ")) {
            String err = rawMessage.substring("ERROR ".length());
            Platform.runLater(() -> {
                setStatus("Lỗi: " + err, "#F43F5E");
                BidXAnimations.shake(txtBidAmount);
            });
        }
    }
}
