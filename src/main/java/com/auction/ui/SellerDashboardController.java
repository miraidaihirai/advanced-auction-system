package com.auction.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.auction.item.factory.ArtFactory;
import com.auction.item.factory.ElectronicsFactory;
import com.auction.item.factory.ItemFactory;
import com.auction.item.factory.VehicleFactory;
import com.auction.session.ClientSession;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import network.AuctionClient;
import network.AuctionDetail;
import network.UserProfile;
import network.WalletTxDTO;

public class SellerDashboardController implements SessionAware {

    @FXML
    private StackPane sidebarLogoWrap;
    @FXML
    private ImageView imgSidebarLogo;
    @FXML
    private Button navOverview;
    @FXML
    private Button navCreateRequest;
    @FXML
    private Button navMyRooms;
    @FXML
    private Button navPayouts;
    @FXML
    private Label lblSidebarWallet;
    @FXML
    private Label lblSellerIdentity;
    @FXML
    private Button btnLogout;

    @FXML
    private Label lblTopTitle;
    @FXML
    private Button btnThemeToggle;
    @FXML
    private Button btnRefresh;
    @FXML
    private Button btnTopNewRequest;

    @FXML
    private ScrollPane tabOverview;
    @FXML
    private ScrollPane tabCreate;
    @FXML
    private ScrollPane tabMyRooms;
    @FXML
    private ScrollPane tabPayouts;

    @FXML
    private StackPane heroWalletIcon;
    @FXML
    private Label lblHeroWalletBalance;
    @FXML
    private Label lblKpiPending;
    @FXML
    private Label lblKpiRunning;
    @FXML
    private Label lblKpiSold;
    @FXML
    private VBox overviewRoomsList;

    // Create Request Form
    @FXML
    private TextField txtItemName;
    @FXML
    private ComboBox<String> cbCategory;
    @FXML
    private ComboBox<String> cbDuration;
    @FXML
    private TextField txtStartPrice;
    @FXML
    private TextField txtStepPrice;
    @FXML
    private Label lblSpec1;
    @FXML
    private Label lblSpec2;
    @FXML
    private Label lblSpec3;
    @FXML
    private TextField txtSpec1;
    @FXML
    private TextField txtSpec2;
    @FXML
    private TextField txtSpec3;
    @FXML
    private TextArea txtDescription;
    @FXML
    private Label lblFormError;
    @FXML
    private Button btnSubmitRequest;
    @FXML
    private ImageView imgFormPreview;
    @FXML
    private Label lblImageCount;
    @FXML
    private FlowPane formImageStrip;
    @FXML
    private ComboBox<String> cbImagePreset;

    // My Rooms Filter
    @FXML
    private Button filterMyAll;
    @FXML
    private Button filterMyPending;
    @FXML
    private Button filterMyLive;
    @FXML
    private Button filterMySold;
    @FXML
    private Button filterMyRejected;
    @FXML
    private VBox myRoomsContainer;

    // Payout Ledger & Wallet Hub
    @FXML
    private VBox sellerWalletHubSlot;
    @FXML
    private VBox payoutLedgerContainer;
    private BidXWalletPanel walletPanel;

    private static final int MAX_UPLOAD_IMAGES = 6;
    private static final String DEFAULT_IMAGE_KEY = "item_watch_classic";

    private ClientSession clientSession;
    private AuctionClient.AuctionEventListener listener;
    private final List<AuctionDetail> myAuctions = new ArrayList<>();
    private String myRoomsFilter = "ALL";
    private final List<String> selectedImageKeys = new ArrayList<>();
    private int activePreviewIndex = 0;
    private boolean usingDefaultPreset = true;
    private double lastWalletBalance = -1;
    private String editingAuctionId = null;

    private final Map<String, String> categoryMap = new LinkedHashMap<>();
    private final Map<String, Long> durationMap = new LinkedHashMap<>();
    private final Map<String, String> presetLabelToKey = new LinkedHashMap<>();

    @FXML
    public void initialize() {
        if (imgSidebarLogo != null) {
            imgSidebarLogo.setImage(ProductImageService.getBrandLogo(80));
            Rectangle clip = new Rectangle(38, 38);
            clip.setArcWidth(10);
            clip.setArcHeight(10);
            imgSidebarLogo.setClip(clip);
        }
        if (sidebarLogoWrap != null) {
            BidXAnimations.attachLogoAura(sidebarLogoWrap);
        }

        navOverview.setGraphic(SvgIcons.create(SvgIcons.WALLET, 16, "#34D399"));
        navCreateRequest.setGraphic(SvgIcons.create(SvgIcons.PLUS, 16, "#94A3B8"));
        navMyRooms.setGraphic(SvgIcons.create(SvgIcons.STORE, 16, "#94A3B8"));
        navPayouts.setGraphic(SvgIcons.create(SvgIcons.TRENDING, 16, "#94A3B8"));
        btnLogout.setGraphic(SvgIcons.create(SvgIcons.LOGOUT, 14, "#94A3B8"));
        btnRefresh.setGraphic(SvgIcons.create(SvgIcons.REFRESH, 16, "#CBD5E1"));
        heroWalletIcon.getChildren().setAll(SvgIcons.create(SvgIcons.WALLET, 18, "#34D399"));

        BidXAnimations.applyButtonJuice(btnTopNewRequest);
        BidXAnimations.applyButtonJuice(btnSubmitRequest);
        BidXAnimations.attachButtonGlowPulse(btnSubmitRequest);
        ThemeManager.syncToggleButton(btnThemeToggle);
        ThemeManager.addListener(dark -> ThemeManager.syncToggleButton(btnThemeToggle));

        if (sellerWalletHubSlot != null) {
            walletPanel = new BidXWalletPanel(true, this::submitSellerDeposit, this::submitSellerWithdraw);
            sellerWalletHubSlot.getChildren().setAll(walletPanel);
        }

        categoryMap.put("Siêu Xe & Xe Sang", "VEHICLES");
        categoryMap.put("Đồng hồ cổ & Cao cấp", "WATCHES");
        categoryMap.put("Đồ điện tử & Hi-End", "ELECTRONICS");
        categoryMap.put("Nghệ thuật & Cổ vật", "ART");
        categoryMap.put("Khác (Trang sức, Rượu, Túi, Sưu tầm)", "OTHER");
        cbCategory.getItems().addAll(categoryMap.keySet());
        cbCategory.getSelectionModel().selectFirst();
        cbCategory.setOnAction(e -> updateSpecFieldLabels());
        updateSpecFieldLabels();

        durationMap.put("1 phút (Demo nhanh)", 60L);
        durationMap.put("3 phút", 180L);
        durationMap.put("5 phút", 300L);
        durationMap.put("15 phút", 900L);
        durationMap.put("30 phút", 1800L);
        durationMap.put("1 giờ", 3600L);
        cbDuration.getItems().addAll(durationMap.keySet());
        cbDuration.getSelectionModel().select("5 phút");

        for (Map.Entry<String, String> entry : ProductImageService.STUDIO_PRESETS.entrySet()) {
            presetLabelToKey.put(entry.getValue(), entry.getKey());
            cbImagePreset.getItems().add(entry.getValue());
        }
        cbImagePreset.getSelectionModel().selectFirst();
        selectedImageKeys.clear();
        selectedImageKeys.add(DEFAULT_IMAGE_KEY);
        activePreviewIndex = 0;
        usingDefaultPreset = true;
        updateFormPreview();
    }

    private void updateSpecFieldLabels() {
        if (lblSpec1 == null || cbCategory == null) {
            return;
        }
        String cat = categoryMap.getOrDefault(cbCategory.getValue(), "OTHER");
        switch (cat) {
            case "ART" -> {
                lblSpec1.setText("Tác giả / Nghệ nhân");
                txtSpec1.setPromptText("VD: Họa sĩ / Nghệ nhân...");
                lblSpec2.setText("Chất liệu");
                txtSpec2.setPromptText("VD: Đồng đúc dát vàng 24K");
                lblSpec3.setText("Năm sáng tác");
                txtSpec3.setPromptText("VD: 1998");
            }
            case "ELECTRONICS" -> {
                lblSpec1.setText("Thương hiệu");
                txtSpec1.setPromptText("VD: Leica, Apple, Dell...");
                lblSpec2.setText("Model / Phiên bản");
                txtSpec2.setPromptText("VD: M3 Summicron 50mm");
                lblSpec3.setText("Bảo hành (tháng)");
                txtSpec3.setPromptText("VD: 24");
            }
            case "VEHICLES" -> {
                lblSpec1.setText("Hãng xe");
                txtSpec1.setPromptText("VD: Ferrari, Rolls-Royce...");
                lblSpec2.setText("Dòng xe");
                txtSpec2.setPromptText("VD: SF90 Stradale");
                lblSpec3.setText("Năm sản xuất");
                txtSpec3.setPromptText("VD: 2024");
            }
            default -> {
                lblSpec1.setText("Thương hiệu / Xuất xứ");
                txtSpec1.setPromptText("VD: Rolex, Hermès, Ý...");
                lblSpec2.setText("Chất liệu / Phiên bản");
                txtSpec2.setPromptText("VD: Vàng 18K / Da cá sấu");
                lblSpec3.setText("Năm sản xuất");
                txtSpec3.setPromptText("VD: 2023");
            }
        }
    }

    private void updateFormPreview() {
        if (selectedImageKeys.isEmpty()) {
            selectedImageKeys.add(DEFAULT_IMAGE_KEY);
            usingDefaultPreset = true;
        }
        if (activePreviewIndex < 0 || activePreviewIndex >= selectedImageKeys.size()) {
            activePreviewIndex = 0;
        }
        String previewKey = selectedImageKeys.get(activePreviewIndex);
        ProductImageService.loadIntoImageView(imgFormPreview, "PREVIEW", txtItemName.getText(), previewKey, 256, 152,
                12);

        if (lblImageCount != null) {
            lblImageCount.setText(selectedImageKeys.size() + "/" + MAX_UPLOAD_IMAGES + " ảnh");
        }
        if (formImageStrip != null) {
            formImageStrip.getChildren().clear();
            for (int i = 0; i < selectedImageKeys.size(); i++) {
                final int idx = i;
                String key = selectedImageKeys.get(i);
                ImageView thumb = new ImageView();
                ProductImageService.loadIntoImageView(thumb, "PREVIEW_" + i, txtItemName.getText(), key, 52, 36, 6);

                StackPane wrap = new StackPane(thumb);
                wrap.getStyleClass().add("thumb-frame");
                if (i == activePreviewIndex) {
                    wrap.getStyleClass().add("thumb-frame-active");
                }
                wrap.setOnMouseClicked(e -> {
                    activePreviewIndex = idx;
                    updateFormPreview();
                });

                if (selectedImageKeys.size() > 1) {
                    Button btnRemove = new Button("×");
                    btnRemove.setStyle("-fx-background-color: rgba(244,63,94,0.92); -fx-text-fill: white;"
                            + " -fx-font-size: 9px; -fx-font-weight: bold; -fx-background-radius: 9;"
                            + " -fx-padding: 0 4 0 4; -fx-cursor: hand;");
                    StackPane.setAlignment(btnRemove, Pos.TOP_RIGHT);
                    btnRemove.setOnAction(ev -> {
                        ev.consume();
                        selectedImageKeys.remove(idx);
                        if (activePreviewIndex >= selectedImageKeys.size()) {
                            activePreviewIndex = Math.max(0, selectedImageKeys.size() - 1);
                        }
                        updateFormPreview();
                    });
                    wrap.getChildren().add(btnRemove);
                }
                formImageStrip.getChildren().add(wrap);
            }
        }
    }

    @Override
    public void setClientSession(ClientSession session) {
        if (session == null) {
            return;
        }
        if (this.clientSession != null && listener != null) {
            this.clientSession.getClient().removeListener(listener);
        }
        this.clientSession = session;
        updateProfileUI(session.getCurrentUser());

        listener = new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
                if (clientSession.isConnected()) {
                    clientSession.getClient().requestFullList();
                }
            }

            @Override
            public void onAuctionDetailsReceived(List<AuctionDetail> auctions) {
                Platform.runLater(() -> {
                    clientSession.updateAuctionCache(auctions);
                    UserProfile me = clientSession.getCurrentUser();
                    String myId = me != null ? me.userId() : "";
                    myAuctions.clear();
                    for (AuctionDetail d : auctions) {
                        if (myId.equals(d.sellerId())) {
                            myAuctions.add(d);
                        }
                    }
                    refreshKpisAndLists();
                });
            }

            @Override
            public void onProfileReceived(UserProfile profile) {
                Platform.runLater(() -> {
                    clientSession.setCurrentUser(profile);
                    updateProfileUI(profile);
                });
            }

            @Override
            public void onWalletHistoryReceived(List<WalletTxDTO> transactions) {
                Platform.runLater(() -> renderPayoutLedger(transactions));
            }

            @Override
            public void onCatalogRefresh() {
                requestSync();
            }

            @Override
            public void onSellerNotification(String auctionId, double finalPrice, String winnerId, String itemId) {
                requestSync();
            }

            @Override
            public void onAuctionPaid(String auctionId, double finalPrice, String winnerId) {
                requestSync();
            }

            @Override
            public void onMessageReceived(String rawMessage) {
                if (rawMessage.startsWith("SUCCESS REQUEST_AUCTION ")) {
                    Platform.runLater(() -> {
                        handleResetForm();
                        showMyRoomsTab();
                        requestSync();
                    });
                } else if (rawMessage.startsWith("SUCCESS UPDATE_AUCTION ")) {
                    Platform.runLater(() -> {
                        handleResetForm();
                        showMyRoomsTab();
                        requestSync();
                    });
                } else if (rawMessage.startsWith("SUCCESS DELETE_AUCTION ")) {
                    Platform.runLater(() -> requestSync());
                } else if (rawMessage.startsWith("ERROR ")) {
                    String err = rawMessage.substring("ERROR ".length());
                    Platform.runLater(() -> {
                        lblFormError.setText(err);
                    });
                }
            }
        };

        session.getClient().addListener(listener);
        requestSync();
    }

    private void requestSync() {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().requestProfile();
            clientSession.getClient().requestFullList();
            clientSession.getClient().requestWalletHistory();
        }
    }

    private void updateProfileUI(UserProfile profile) {
        if (profile == null) {
            return;
        }
        lblSellerIdentity.setText(profile.userId() + " • " + profile.name());
        double newBalance = profile.balance();
        if (lastWalletBalance < 0) {
            lblSidebarWallet.setText(FormatUtils.formatVnd(newBalance));
            lblHeroWalletBalance.setText(FormatUtils.formatVnd(newBalance));
        } else if (Math.abs(newBalance - lastWalletBalance) > 0.5) {
            BidXAnimations.animateCurrency(lblSidebarWallet, lastWalletBalance, newBalance);
            BidXAnimations.animateCurrency(lblHeroWalletBalance, lastWalletBalance, newBalance);
            if (newBalance > lastWalletBalance) {
                BidXAnimations.pricePopFlash(lblHeroWalletBalance);
            }
        }
        lastWalletBalance = newBalance;
        if (walletPanel != null) {
            walletPanel.updateProfile(profile);
        }
    }

    private void refreshKpisAndLists() {
        long pending = myAuctions.stream().filter(AuctionDetail::isPendingApproval).count();
        long running = myAuctions.stream().filter(AuctionDetail::isRunning).count();
        long sold = myAuctions.stream().filter(d -> (d.isFinished() || d.isPaid()) && d.hasWinner()).count();

        lblKpiPending.setText(String.valueOf(pending));
        lblKpiRunning.setText(String.valueOf(running));
        lblKpiSold.setText(String.valueOf(sold));

        renderRoomsInto(overviewRoomsList, myAuctions.stream().limit(5).toList());
        renderFilteredMyRooms();
    }

    private void renderFilteredMyRooms() {
        List<AuctionDetail> filtered = myAuctions.stream().filter(d -> switch (myRoomsFilter) {
            case "PENDING" -> d.isPendingApproval();
            case "LIVE" -> d.isRunning();
            case "SOLD" -> (d.isFinished() || d.isPaid()) && d.hasWinner();
            case "REJECTED" -> d.isRejected();
            default -> true;
        }).toList();
        renderRoomsInto(myRoomsContainer, filtered);
    }

    private void renderRoomsInto(VBox target, List<AuctionDetail> list) {
        target.getChildren().clear();
        if (list.isEmpty()) {
            Label empty = new Label(
                    "Chưa có phòng đấu giá nào trong mục này. Nhấn '+ Gửi yêu cầu mở phòng' để tạo sản phẩm đầu tiên!");
            empty.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13px; -fx-padding: 20 0 20 0;");
            target.getChildren().add(empty);
            return;
        }
        int idx = 0;
        for (AuctionDetail d : list) {
            target.getChildren().add(createSellerRoomRow(d, idx++));
        }
    }

    private HBox createSellerRoomRow(AuctionDetail d, int index) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("queue-row-card");

        ImageView thumb = new ImageView();
        ProductImageService.loadIntoImageView(thumb, d.auctionId(), d.itemName(), d.imageKey(), 116, 74, 10);

        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox topLine = new HBox(8);
        topLine.setAlignment(Pos.CENTER_LEFT);
        Label badge = new Label(FormatUtils.statusLabel(d.status()));
        badge.getStyleClass().add(FormatUtils.statusBadgeStyleClass(d.status()));
        Label idTag = new Label(d.auctionId() + " • " + FormatUtils.categoryLabel(d.category()));
        idTag.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11.5px; -fx-font-weight: bold;");
        topLine.getChildren().addAll(badge, idTag);

        Label title = new Label(d.itemName());
        title.getStyleClass().add("text-primary");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15.5px;");

        String metaText;
        if (d.isPendingApproval()) {
            metaText = "⏳ Đang chờ Admin xét duyệt • Giá khởi điểm: " + FormatUtils.formatVnd(d.startPrice())
                    + " • Thời lượng đề xuất: " + FormatUtils.formatDurationLabel(d.requestedDurationSec());
        } else if (d.isRejected()) {
            metaText = "✕ Lý do Admin từ chối: "
                    + (d.rejectionReason().isBlank() ? "Không đạt yêu cầu" : d.rejectionReason());
        } else if (d.isRunning()) {
            metaText = "● Đang diễn ra • Giá hiện tại: " + FormatUtils.formatVnd(d.currentPrice())
                    + " • " + d.bidCount() + " lượt đặt • Còn: "
                    + FormatUtils.formatCountdownCompact(d.getRemainingSeconds());
        } else if (d.hasWinner()) {
            metaText = "💰 Đã bán thành công cho " + d.highestBidderId() + " • Đã nhận +"
                    + FormatUtils.formatVnd(d.currentPrice()) + " về ví Seller!";
        } else {
            metaText = "Phiên đã kết thúc mà chưa có lượt đặt giá.";
        }
        Label meta = new Label(metaText);
        meta.setWrapText(true);
        meta.setStyle(d.isRejected()
                ? "-fx-text-fill: #FB7185; -fx-font-size: 12px; -fx-font-weight: bold;"
                : (d.payoutSettled() ? "-fx-text-fill: #34D399; -fx-font-size: 12.5px; -fx-font-weight: bold;"
                        : "-fx-text-fill: #94A3B8; -fx-font-size: 12px;"));

        info.getChildren().addAll(topLine, title, meta);

        VBox rightCol = new VBox(6);
        rightCol.setAlignment(Pos.CENTER_RIGHT);
        Label price = new Label(FormatUtils.formatVnd(d.currentPrice()));
        price.setStyle("-fx-text-fill: #FF9E44; -fx-font-weight: bold; -fx-font-size: 17px;");
        rightCol.getChildren().add(price);

        if (d.isRunning() || d.isFinished() || d.isPaid()) {
            Button btnWatch = new Button(d.isRunning() ? "Theo dõi Live →" : "Xem chi tiết");
            btnWatch.getStyleClass().add("btn-ghost");
            btnWatch.setOnAction(e -> ViewManager.openAuctionRoom(d));
            rightCol.getChildren().add(btnWatch);
        } else if (d.isPendingApproval() || d.isRejected()) {
            HBox crudBtns = new HBox(8);
            crudBtns.setAlignment(Pos.CENTER_RIGHT);
            if (d.isPendingApproval()) {
                Button btnEdit = new Button("Sửa");
                btnEdit.getStyleClass().add("btn-ghost");
                btnEdit.setStyle("-fx-padding: 6 12 6 12; -fx-font-size: 12px;");
                btnEdit.setOnAction(e -> startEditingAuction(d));
                crudBtns.getChildren().add(btnEdit);
            }
            Button btnDelete = new Button("Xóa");
            btnDelete.getStyleClass().add("btn-crimson-outline");
            btnDelete.setStyle("-fx-padding: 6 12 6 12; -fx-font-size: 12px;");
            btnDelete.setOnAction(e -> handleDeleteAuction(d));
            crudBtns.getChildren().add(btnDelete);
            rightCol.getChildren().add(crudBtns);
        }

        row.getChildren().addAll(thumb, info, rightCol);
        BidXAnimations.fadeSlideIn(row, Math.min(index * 45.0, 260.0), 12);
        return row;
    }

    private void renderPayoutLedger(List<WalletTxDTO> transactions) {
        if (walletPanel != null) {
            walletPanel.updateTransactions(transactions);
        }
        payoutLedgerContainer.getChildren().clear();
        if (transactions == null || transactions.isEmpty()) {
            Label empty = new Label(
                    "Chưa có giao dịch ví nào. Khi phiên kết thúc có người thắng, nạp tiền hoặc rút về ngân hàng, lịch sử sẽ hiển thị tại đây.");
            empty.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13px; -fx-padding: 20 0 0 0;");
            payoutLedgerContainer.getChildren().add(empty);
            return;
        }
        List<WalletTxDTO> rev = new ArrayList<>(transactions);
        Collections.reverse(rev);
        int idx = 0;
        for (WalletTxDTO tx : rev) {
            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("ledger-row");

            boolean isWithdraw = "WITHDRAW".equalsIgnoreCase(tx.type());
            boolean isTopUp = "TOP_UP".equalsIgnoreCase(tx.type());
            boolean positive = tx.amount() >= 0;

            String iconPath = isWithdraw ? SvgIcons.ARROW_UP : (isTopUp ? SvgIcons.ARROW_DOWN : SvgIcons.WALLET);
            String iconColor = isWithdraw ? "#FF9E44" : "#34D399";
            String iconBg = isWithdraw ? "rgba(255,123,36,0.18)" : "rgba(16,185,129,0.18)";

            StackPane icon = new StackPane(SvgIcons.create(iconPath, 17, iconColor));
            icon.setMinSize(38, 38);
            icon.setStyle("-fx-background-color: " + iconBg + "; -fx-background-radius: 10;");

            VBox mid = new VBox(3);
            HBox.setHgrow(mid, Priority.ALWAYS);
            String titleText = (isWithdraw || isTopUp)
                    ? tx.itemName()
                    : "Doanh thu phiên: " + tx.itemName() + " (" + tx.auctionId() + ")";
            String subText = (isWithdraw || isTopUp)
                    ? "Kênh: " + tx.counterpartyId() + " • " + FormatUtils.formatTimestampShort(tx.timestamp())
                    : "Người mua thắng cuộc: " + tx.counterpartyId() + " • "
                            + FormatUtils.formatTimestampShort(tx.timestamp());
            Label title = new Label(titleText);
            title.getStyleClass().add("text-primary");
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 13.5px;");
            Label sub = new Label(subText);
            sub.getStyleClass().add("text-muted");
            sub.setStyle("-fx-font-size: 11.5px;");
            mid.getChildren().addAll(title, sub);

            VBox right = new VBox(2);
            right.setAlignment(Pos.CENTER_RIGHT);
            Label amt = new Label(FormatUtils.formatSignedVnd(tx.amount()));
            amt.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: "
                    + (positive ? "#34D399;" : "#FB7185;"));
            Label bal = new Label("Ví Seller: " + FormatUtils.formatVnd(tx.balanceAfter()));
            bal.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");
            right.getChildren().addAll(amt, bal);

            row.getChildren().addAll(icon, mid, right);
            payoutLedgerContainer.getChildren().add(row);
            BidXAnimations.fadeSlideIn(row, Math.min(idx++ * 40.0, 260.0), 10);
        }
    }

    @FXML
    public void handleUploadImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn nhiều ảnh sản phẩm đấu giá (tối đa " + MAX_UPLOAD_IMAGES + " ảnh)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        Window w = txtItemName.getScene() != null ? txtItemName.getScene().getWindow() : null;
        List<File> chosenList = fc.showOpenMultipleDialog(w);
        if (chosenList != null && !chosenList.isEmpty()) {
            if (usingDefaultPreset) {
                selectedImageKeys.clear();
                usingDefaultPreset = false;
            }
            int added = 0;
            for (File chosen : chosenList) {
                if (selectedImageKeys.size() >= MAX_UPLOAD_IMAGES) {
                    NotificationManager.showInfo("Chỉ hỗ trợ tối đa " + MAX_UPLOAD_IMAGES + " ảnh cho mỗi sản phẩm.");
                    break;
                }
                String key = "SELLER_IMG_" + System.currentTimeMillis() + "_" + added;
                String savedPath = ProductImageService.saveProductImage(key, chosen);
                if (savedPath != null && !savedPath.isBlank() && !selectedImageKeys.contains(savedPath)) {
                    selectedImageKeys.add(savedPath);
                    added++;
                }
            }
            activePreviewIndex = Math.max(0, selectedImageKeys.size() - 1);
            updateFormPreview();
            SoundEffects.playReactionTap();
        }
    }

    @FXML
    public void handleAddPresetImage() {
        String label = cbImagePreset != null ? cbImagePreset.getValue() : null;
        if (label == null || !presetLabelToKey.containsKey(label)) {
            return;
        }
        String presetKey = presetLabelToKey.get(label);
        if (selectedImageKeys.contains(presetKey)) {
            activePreviewIndex = selectedImageKeys.indexOf(presetKey);
            updateFormPreview();
            return;
        }
        if (selectedImageKeys.size() >= MAX_UPLOAD_IMAGES) {
            NotificationManager.showInfo("Đã đạt tối đa " + MAX_UPLOAD_IMAGES + " ảnh cho sản phẩm.");
            return;
        }
        usingDefaultPreset = false;
        selectedImageKeys.add(presetKey);
        activePreviewIndex = selectedImageKeys.size() - 1;
        updateFormPreview();
        SoundEffects.playReactionTap();
    }

    @FXML
    public void handleSubmitAuctionRequest() {
        lblFormError.setText("");
        if (clientSession == null || !clientSession.isConnected()) {
            lblFormError.setText("Chưa kết nối tới máy chủ BidX!");
            return;
        }
        String name = txtItemName.getText() != null ? txtItemName.getText().trim() : "";
        String startStr = txtStartPrice.getText() != null ? txtStartPrice.getText().trim().replaceAll("[,._\\s]", "")
                : "";
        String stepStr = txtStepPrice.getText() != null ? txtStepPrice.getText().trim().replaceAll("[,._\\s]", "") : "";
        String desc = txtDescription.getText() != null ? txtDescription.getText().trim() : "";

        if (name.isEmpty()) {
            lblFormError.setText("Vui lòng nhập tên sản phẩm muốn đấu giá.");
            BidXAnimations.shake(txtItemName);
            return;
        }
        double startPrice;
        double stepPrice;
        try {
            startPrice = Double.parseDouble(startStr);
            stepPrice = Double.parseDouble(stepStr);
            if (startPrice <= 0 || stepPrice <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            lblFormError.setText("Giá khởi điểm và bước giá phải là số dương hợp lệ.");
            BidXAnimations.shake(txtStartPrice);
            return;
        }

        String category = categoryMap.getOrDefault(cbCategory.getValue(), "COLLECTIBLE");
        long durationSec = durationMap.getOrDefault(cbDuration.getValue(), 300L);
        String s1 = txtSpec1 != null && txtSpec1.getText() != null ? txtSpec1.getText().trim() : "";
        String s2 = txtSpec2 != null && txtSpec2.getText() != null ? txtSpec2.getText().trim() : "";
        String s3 = txtSpec3 != null && txtSpec3.getText() != null ? txtSpec3.getText().trim() : "";
        int specYear = parsePositiveIntOrDefault(s3, "ELECTRONICS".equals(category) ? 24 : 2024);

        // Áp dụng Factory Method Pattern để khởi tạo đúng đối tượng OOP theo danh mục
        ItemFactory factory = switch (category) {
            case "ART" -> new ArtFactory(name, (int) (System.currentTimeMillis() % 100000),
                    (int) Math.min(startPrice, Integer.MAX_VALUE), 1,
                    s1.isEmpty() ? "Nghệ nhân BidX" : s1,
                    s2.isEmpty() ? "Thủ công cao cấp" : s2,
                    specYear);
            case "VEHICLES" -> new VehicleFactory(name, (int) (System.currentTimeMillis() % 100000),
                    (int) Math.min(startPrice, Integer.MAX_VALUE), 1,
                    s1.isEmpty() ? "Luxury" : s1,
                    s2.isEmpty() ? name : s2,
                    specYear);
            default -> new ElectronicsFactory(name, (int) (System.currentTimeMillis() % 100000),
                    (int) Math.min(startPrice, Integer.MAX_VALUE), 1,
                    s1.isEmpty() ? "BidX Studio" : s1,
                    s2.isEmpty() ? name : s2,
                    specYear);
        };
        com.auction.item.Item createdItem = factory.createItem();
        String itemSpecs = buildUserItemSpecs(category, s1, s2, s3, createdItem);

        String combinedImageKeys = ProductImageService.joinImageKeys(selectedImageKeys);
        if (editingAuctionId != null) {
            clientSession.getClient().updateAuction(
                    editingAuctionId, name, category, startPrice, stepPrice, durationSec, combinedImageKeys, desc, itemSpecs);
        } else {
            clientSession.getClient().requestAuction(
                    name, category, startPrice, stepPrice, durationSec, combinedImageKeys, desc, itemSpecs);
        }
    }

    private void startEditingAuction(AuctionDetail d) {
        if (d == null) {
            return;
        }
        editingAuctionId = d.auctionId();
        txtItemName.setText(d.itemName());
        txtStartPrice.setText(String.format(java.util.Locale.US, "%.0f", d.startPrice()));
        txtStepPrice.setText(String.format(java.util.Locale.US, "%.0f", d.stepPrice()));
        txtDescription.setText(d.description());
        lblFormError.setText("");

        for (Map.Entry<String, String> entry : categoryMap.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(d.category())) {
                cbCategory.getSelectionModel().select(entry.getKey());
                break;
            }
        }
        for (Map.Entry<String, Long> entry : durationMap.entrySet()) {
            if (entry.getValue() == d.requestedDurationSec()) {
                cbDuration.getSelectionModel().select(entry.getKey());
                break;
            }
        }
        updateSpecFieldLabels();

        if (txtSpec1 != null) txtSpec1.clear();
        if (txtSpec2 != null) txtSpec2.clear();
        if (txtSpec3 != null) txtSpec3.clear();
        if (d.itemSpecs() != null && !d.itemSpecs().isBlank()) {
            String[] specParts = d.itemSpecs().split(";;");
            TextField[] specFields = {txtSpec1, txtSpec2, txtSpec3};
            for (int i = 0; i < Math.min(specParts.length, specFields.length); i++) {
                String part = specParts[i].trim();
                int colon = part.indexOf(':');
                String val = colon >= 0 ? part.substring(colon + 1).trim() : part;
                if (specFields[i] != null) {
                    specFields[i].setText(val.replace(" tháng", ""));
                }
            }
        }

        List<String> parsedImgs = ProductImageService.parseImageKeys(d.imageKey());
        selectedImageKeys.clear();
        if (!parsedImgs.isEmpty()) {
            selectedImageKeys.addAll(parsedImgs);
            usingDefaultPreset = false;
        } else {
            selectedImageKeys.add(DEFAULT_IMAGE_KEY);
            usingDefaultPreset = true;
        }
        activePreviewIndex = 0;
        updateFormPreview();

        btnSubmitRequest.setText("LƯU THAY ĐỔI SẢN PHẨM");
        switchTab(tabCreate, navCreateRequest, "Chỉnh sửa sản phẩm " + d.auctionId());
    }

    private void handleDeleteAuction(AuctionDetail d) {
        if (d == null || clientSession == null || !clientSession.isConnected()) {
            return;
        }
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận xóa sản phẩm");
        confirm.setHeaderText("Xóa sản phẩm " + d.itemName() + " (" + d.auctionId() + ")?");
        confirm.setContentText("Thao tác này sẽ xóa vĩnh viễn yêu cầu / sản phẩm khỏi hệ thống.");
        if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) == javafx.scene.control.ButtonType.OK) {
            clientSession.getClient().deleteAuction(d.auctionId());
        }
    }

    private int parsePositiveIntOrDefault(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int val = Integer.parseInt(raw.replaceAll("[^0-9]", ""));
            return val > 0 ? val : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String buildUserItemSpecs(
            String category, String s1, String s2, String s3, com.auction.item.Item createdItem) {
        if ("ART".equals(category) || "VEHICLES".equals(category) || "ELECTRONICS".equals(category)) {
            if (s1.isEmpty() && s2.isEmpty() && s3.isEmpty()) {
                return "";
            }
            return com.auction.model.Auction.extractSpecsFromItem(createdItem);
        }
        List<String> pairs = new ArrayList<>();
        if (!s1.isEmpty()) {
            pairs.add("Thương hiệu: " + s1);
        }
        if (!s2.isEmpty()) {
            pairs.add("Chất liệu: " + s2);
        }
        if (!s3.isEmpty()) {
            pairs.add("Năm sản xuất: " + s3);
        }
        return String.join(";;", pairs);
    }

    @FXML
    public void handleResetForm() {
        editingAuctionId = null;
        if (btnSubmitRequest != null) {
            btnSubmitRequest.setText("GỬI YÊU CẦU CHO ADMIN DUYỆT");
        }
        txtItemName.clear();
        txtStartPrice.clear();
        txtStepPrice.clear();
        if (txtSpec1 != null) {
            txtSpec1.clear();
        }
        if (txtSpec2 != null) {
            txtSpec2.clear();
        }
        if (txtSpec3 != null) {
            txtSpec3.clear();
        }
        txtDescription.clear();
        lblFormError.setText("");
        selectedImageKeys.clear();
        selectedImageKeys.add(DEFAULT_IMAGE_KEY);
        activePreviewIndex = 0;
        usingDefaultPreset = true;
        if (cbImagePreset != null && !cbImagePreset.getItems().isEmpty()) {
            cbImagePreset.getSelectionModel().selectFirst();
        }
        updateFormPreview();
    }

    private void switchTab(ScrollPane activeTab, Button activeNav, String title) {
        ScrollPane[] tabs = { tabOverview, tabCreate, tabMyRooms, tabPayouts };
        for (ScrollPane sp : tabs) {
            boolean show = (sp == activeTab);
            sp.setVisible(show);
            sp.setManaged(show);
        }
        Button[] navs = { navOverview, navCreateRequest, navMyRooms, navPayouts };
        for (Button b : navs) {
            b.getStyleClass().remove("nav-btn-active-seller");
        }
        if (!activeNav.getStyleClass().contains("nav-btn-active-seller")) {
            activeNav.getStyleClass().add("nav-btn-active-seller");
        }
        lblTopTitle.setText(title);
        BidXAnimations.fadeSlideIn(activeTab, 0, 10);
    }

    @FXML
    public void showOverviewTab() {
        switchTab(tabOverview, navOverview, "BidX Seller Studio • Tổng quan & Ví tiền");
        requestSync();
    }

    @FXML
    public void showCreateTab() {
        if (editingAuctionId != null) {
            handleResetForm();
        }
        switchTab(tabCreate, navCreateRequest, "Gửi yêu cầu mở phòng đấu giá");
    }

    @FXML
    public void showMyRoomsTab() {
        switchTab(tabMyRooms, navMyRooms, "Phòng đấu giá & yêu cầu của tôi");
        requestSync();
    }

    private void submitSellerDeposit(double amount, String bankChannel) {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().topUpWallet(amount, bankChannel);
        }
    }

    private void submitSellerWithdraw(double amount, String bankDestination) {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().withdrawWallet(amount, bankDestination);
        }
    }

    @FXML
    public void showPayoutsTab() {
        switchTab(tabPayouts, navPayouts, "Ví Seller • Nạp & Rút tiền ngân hàng");
        requestSync();
    }

    @FXML
    public void handleOpenSellerDeposit() {
        showPayoutsTab();
        if (walletPanel != null) {
            walletPanel.openDepositMode();
        }
    }

    @FXML
    public void handleOpenSellerWithdraw() {
        showPayoutsTab();
        if (walletPanel != null) {
            walletPanel.openWithdrawMode();
        }
    }

    private void setMyFilter(String filter, Button activeBtn) {
        myRoomsFilter = filter;
        Button[] pills = { filterMyAll, filterMyPending, filterMyLive, filterMySold, filterMyRejected };
        for (Button p : pills) {
            p.getStyleClass().remove("filter-pill-active");
        }
        if (!activeBtn.getStyleClass().contains("filter-pill-active")) {
            activeBtn.getStyleClass().add("filter-pill-active");
        }
        renderFilteredMyRooms();
    }

    @FXML
    public void filterMyRoomsAll() {
        setMyFilter("ALL", filterMyAll);
    }

    @FXML
    public void filterMyRoomsPending() {
        setMyFilter("PENDING", filterMyPending);
    }

    @FXML
    public void filterMyRoomsLive() {
        setMyFilter("LIVE", filterMyLive);
    }

    @FXML
    public void filterMyRoomsSold() {
        setMyFilter("SOLD", filterMySold);
    }

    @FXML
    public void filterMyRoomsRejected() {
        setMyFilter("REJECTED", filterMyRejected);
    }

    @FXML
    public void handleToggleTheme() {
        ThemeManager.toggleTheme();
    }

    @FXML
    public void handleRefresh() {
        requestSync();
        BidXAnimations.popScale(btnRefresh, 1.18);
    }

    @Override
    public void detachSession() {
        if (this.clientSession != null && this.clientSession.getClient() != null && listener != null) {
            this.clientSession.getClient().removeListener(listener);
        }
    }

    @FXML
    public void handleLogout() {
        detachSession();
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.disconnect();
        }
        ViewManager.clearSessionViews();
        ViewManager.SwitchView("LogIn");
    }
}
