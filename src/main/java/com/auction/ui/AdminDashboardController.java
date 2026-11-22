package com.auction.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.auction.session.ClientSession;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import network.AuctionClient;
import network.AuctionDetail;
import network.UserProfile;
import network.WalletTxDTO;

public class AdminDashboardController implements SessionAware {

    @FXML
    private StackPane sidebarLogoWrap;
    @FXML
    private ImageView imgSidebarLogo;
    @FXML
    private Button navQueue;
    @FXML
    private Label lblPendingNavBadge;
    @FXML
    private Button navMonitor;
    @FXML
    private Button navUsers;
    @FXML
    private Button navMoneyFlow;
    @FXML
    private Label lblAdminIdentity;
    @FXML
    private Button btnLogout;

    @FXML
    private Label lblTopTitle;
    @FXML
    private Button btnThemeToggle;
    @FXML
    private Button btnRefresh;

    @FXML
    private Label lblKpiPending;
    @FXML
    private Label lblKpiLive;
    @FXML
    private Label lblKpiSellerPayout;
    @FXML
    private Label lblKpiUsers;
    @FXML
    private Label lblKpiUsersSub;

    @FXML
    private ScrollPane tabQueue;
    @FXML
    private Label lblQueueSummary;
    @FXML
    private VBox approvalQueueContainer;

    @FXML
    private ScrollPane tabMonitor;
    @FXML
    private Button monAll;
    @FXML
    private Button monLive;
    @FXML
    private Button monFinished;
    @FXML
    private Button monRejected;
    @FXML
    private VBox monitorListContainer;

    @FXML
    private ScrollPane tabUsers;
    @FXML
    private VBox usersListContainer;

    @FXML
    private ScrollPane tabMoneyFlow;
    @FXML
    private VBox moneyFlowContainer;

    @FXML
    private StackPane rejectModal;
    @FXML
    private Label lblRejectTarget;
    @FXML
    private TextArea txtRejectReason;

    private ClientSession clientSession;
    private AuctionClient.AuctionEventListener listener;
    private final List<AuctionDetail> allAuctions = new ArrayList<>();
    private final List<UserProfile> allUsers = new ArrayList<>();
    private String monitorFilter = "ALL";
    private AuctionDetail rejectTargetAuction;
    private double lastTotalSellerPayout = -1;

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

        navQueue.setGraphic(SvgIcons.create(SvgIcons.SHIELD, 16, "#38BDF8"));
        navMonitor.setGraphic(SvgIcons.create(SvgIcons.COMPASS, 16, "#94A3B8"));
        navUsers.setGraphic(SvgIcons.create(SvgIcons.USERS, 16, "#94A3B8"));
        navMoneyFlow.setGraphic(SvgIcons.create(SvgIcons.WALLET, 16, "#94A3B8"));
        btnLogout.setGraphic(SvgIcons.create(SvgIcons.LOGOUT, 14, "#94A3B8"));
        btnRefresh.setGraphic(SvgIcons.create(SvgIcons.REFRESH, 16, "#CBD5E1"));

        BidXAnimations.startPulse(lblPendingNavBadge, 0.72, 1.08, 1600);
        ThemeManager.syncToggleButton(btnThemeToggle);
        ThemeManager.addListener(dark -> ThemeManager.syncToggleButton(btnThemeToggle));
    }

    @FXML
    public void handleToggleTheme() {
        ThemeManager.toggleTheme();
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
        updateIdentity(session.getCurrentUser());

        listener = new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
                requestSync();
            }

            @Override
            public void onAuctionDetailsReceived(List<AuctionDetail> auctions) {
                Platform.runLater(() -> {
                    clientSession.updateAuctionCache(auctions);
                    allAuctions.clear();
                    allAuctions.addAll(auctions);
                    refreshAuctionViews();
                });
            }

            @Override
            public void onUsersListReceived(List<UserProfile> users) {
                Platform.runLater(() -> {
                    allUsers.clear();
                    allUsers.addAll(users);
                    renderUsersAndStats();
                });
            }

            @Override
            public void onWalletHistoryReceived(List<WalletTxDTO> transactions) {
                Platform.runLater(() -> renderMoneyFlow(transactions));
            }

            @Override
            public void onProfileReceived(UserProfile profile) {
                Platform.runLater(() -> {
                    clientSession.setCurrentUser(profile);
                    updateIdentity(profile);
                });
            }

            @Override
            public void onCatalogRefresh() {
                requestSync();
            }

            @Override
            public void onAuctionClosed(String auctionId, double finalPrice, String winnerId) {
                requestSync();
            }

            @Override
            public void onAuctionPaid(String auctionId, double finalPrice, String winnerId) {
                requestSync();
            }
        };

        session.getClient().addListener(listener);
        requestSync();
    }

    private void requestSync() {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().requestFullList();
            clientSession.getClient().requestUsersList();
            clientSession.getClient().requestWalletHistory();
        }
    }

    private void updateIdentity(UserProfile profile) {
        if (profile != null) {
            lblAdminIdentity.setText(profile.userId() + " • " + profile.name());
        }
    }

    private void refreshAuctionViews() {
        List<AuctionDetail> pending = allAuctions.stream().filter(AuctionDetail::isPendingApproval).toList();
        long liveCount = allAuctions.stream().filter(AuctionDetail::isRunning).count();

        lblKpiPending.setText(String.valueOf(pending.size()));
        lblPendingNavBadge.setText(String.valueOf(pending.size()));
        lblPendingNavBadge.setVisible(!pending.isEmpty());
        lblPendingNavBadge.setManaged(!pending.isEmpty());
        lblQueueSummary.setText(pending.size() + " yêu cầu đang chờ duyệt");
        lblKpiLive.setText(String.valueOf(liveCount));

        renderApprovalQueue(pending);
        renderMonitorList();
    }

    private void renderApprovalQueue(List<AuctionDetail> pendingList) {
        approvalQueueContainer.getChildren().clear();
        if (pendingList.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER);
            empty.setStyle("-fx-padding: 50 20 50 20;");
            Label t = new Label("Tất cả yêu cầu mở phòng của Seller đã được xử lý!");
            t.setStyle("-fx-text-fill: #34D399; -fx-font-size: 16px; -fx-font-weight: bold;");
            Label s = new Label("Khi Seller gửi yêu cầu mở phòng đấu giá mới, thẻ kiểm duyệt sẽ tự động xuất hiện ngay tại đây.");
            s.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13px;");
            empty.getChildren().addAll(t, s);
            approvalQueueContainer.getChildren().add(empty);
            return;
        }

        int idx = 0;
        for (AuctionDetail d : pendingList) {
            HBox card = new HBox(18);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("queue-row-card");

            ImageView thumb = new ImageView();
            ProductImageService.loadIntoImageView(thumb, d.auctionId(), d.itemName(), d.imageKey(), 146, 94, 12);

            VBox info = new VBox(5);
            HBox.setHgrow(info, Priority.ALWAYS);

            HBox tags = new HBox(8);
            tags.setAlignment(Pos.CENTER_LEFT);
            Label pendingBadge = new Label("⏳ CHỜ ADMIN DUYỆT");
            pendingBadge.getStyleClass().add("badge-pending");
            Label catBadge = new Label(FormatUtils.categoryLabel(d.category()));
            catBadge.getStyleClass().add("badge-muted");
            Label idLabel = new Label(d.auctionId());
            idLabel.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11.5px; -fx-font-weight: bold;");
            tags.getChildren().addAll(pendingBadge, catBadge, idLabel);

            Label title = new Label(d.itemName());
            title.getStyleClass().add("text-primary");
            title.setStyle("-fx-font-size: 16.5px; -fx-font-weight: bold;");

            Label sellerLine = new Label("Người gửi yêu cầu: " + d.sellerName() + " (" + d.sellerId() + ")"
                    + "  •  Khởi điểm: " + FormatUtils.formatVnd(d.startPrice())
                    + "  •  Bước giá: +" + FormatUtils.formatVnd(d.stepPrice())
                    + "  •  Thời lượng: " + FormatUtils.formatDurationLabel(d.requestedDurationSec()));
            sellerLine.getStyleClass().add("text-accent-orange");
            sellerLine.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
            sellerLine.setWrapText(true);

            Label desc = new Label(d.description().isBlank() ? "Chưa có mô tả bổ sung." : d.description());
            desc.getStyleClass().add("text-muted");
            desc.setStyle("-fx-font-size: 12px;");
            desc.setWrapText(true);

            info.getChildren().addAll(tags, title, sellerLine, desc);

            VBox actions = new VBox(8);
            actions.setAlignment(Pos.CENTER_RIGHT);
            actions.setMinWidth(195);

            Button btnApprove = new Button("✓ DUYỆT & MỞ PHÒNG");
            btnApprove.getStyleClass().add("btn-emerald");
            btnApprove.setMaxWidth(Double.MAX_VALUE);
            BidXAnimations.applyButtonJuice(btnApprove);
            btnApprove.setOnAction(e -> {
                if (clientSession != null && clientSession.isConnected()) {
                    SoundEffects.playApproveChime();
                    clientSession.getClient().approveAuction(d.auctionId());
                }
            });

            Button btnReject = new Button("✕ TỪ CHỐI");
            btnReject.getStyleClass().add("btn-crimson-outline");
            btnReject.setMaxWidth(Double.MAX_VALUE);
            BidXAnimations.applyButtonJuice(btnReject);
            btnReject.setOnAction(e -> openRejectModal(d));

            actions.getChildren().addAll(btnApprove, btnReject);
            card.getChildren().addAll(thumb, info, actions);
            approvalQueueContainer.getChildren().add(card);
            BidXAnimations.fadeSlideIn(card, Math.min(idx++ * 55.0, 300.0), 14);
        }
    }

    private void renderMonitorList() {
        monitorListContainer.getChildren().clear();
        List<AuctionDetail> list = allAuctions.stream().filter(d -> switch (monitorFilter) {
            case "LIVE" -> d.isRunning();
            case "ENDED" -> d.isFinished() || d.isPaid();
            case "REJECTED" -> d.isRejected();
            default -> !d.isPendingApproval();
        }).toList();

        if (list.isEmpty()) {
            Label empty = new Label("Không có phiên đấu giá nào trong bộ lọc này.");
            empty.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13px; -fx-padding: 20 0 0 0;");
            monitorListContainer.getChildren().add(empty);
            return;
        }

        int idx = 0;
        for (AuctionDetail d : list) {
            HBox row = new HBox(16);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("queue-row-card");

            ImageView thumb = new ImageView();
            ProductImageService.loadIntoImageView(thumb, d.auctionId(), d.itemName(), d.imageKey(), 108, 68, 10);

            VBox info = new VBox(4);
            HBox.setHgrow(info, Priority.ALWAYS);

            HBox top = new HBox(8);
            top.setAlignment(Pos.CENTER_LEFT);
            Label st = new Label(FormatUtils.statusLabel(d.status()));
            st.getStyleClass().add(FormatUtils.statusBadgeStyleClass(d.status()));
            Label metaId = new Label(d.auctionId() + " • Seller: " + d.sellerId());
            metaId.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11.5px; -fx-font-weight: bold;");
            top.getChildren().addAll(st, metaId);

            Label name = new Label(d.itemName());
            name.getStyleClass().add("text-primary");
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

            String detailStr = "Giá hiện tại: " + FormatUtils.formatVnd(d.currentPrice())
                    + " • " + d.bidCount() + " lượt bid"
                    + (d.hasWinner() ? " • Dẫn đầu/Thắng: " + d.highestBidderId() : " • Chưa có bidder")
                    + (d.payoutSettled() ? " • 💰 ĐÃ CHUYỂN TIỀN VỀ SELLER" : "");
            Label sub = new Label(detailStr);
            sub.setStyle(d.payoutSettled()
                    ? "-fx-text-fill: #34D399; -fx-font-size: 12px; -fx-font-weight: bold;"
                    : "-fx-text-fill: #94A3B8; -fx-font-size: 12px;");
            info.getChildren().addAll(top, name, sub);

            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_RIGHT);

            Button btnView = new Button("Giám sát");
            btnView.getStyleClass().add("btn-ghost");
            btnView.setOnAction(e -> ViewManager.openAuctionRoom(d));
            actions.getChildren().add(btnView);

            if (d.isRunning()) {
                Button btnClose = new Button("Chốt phiên & Trả Seller");
                btnClose.getStyleClass().add("btn-crimson-outline");
                btnClose.setStyle("-fx-padding: 8 12 8 12; -fx-font-size: 12px;");
                btnClose.setOnAction(e -> {
                    if (clientSession != null && clientSession.isConnected()) {
                        clientSession.getClient().closeAuction(d.auctionId());
                    }
                });
                actions.getChildren().add(btnClose);
            }

            row.getChildren().addAll(thumb, info, actions);
            monitorListContainer.getChildren().add(row);
            BidXAnimations.fadeSlideIn(row, Math.min(idx++ * 38.0, 260.0), 10);
        }
    }

    private void renderUsersAndStats() {
        long bidders = allUsers.stream().filter(UserProfile::isBidder).count();
        long sellers = allUsers.stream().filter(UserProfile::isSeller).count();
        long admins = allUsers.stream().filter(UserProfile::isAdmin).count();
        double totalSellerPocket = allUsers.stream()
                .filter(UserProfile::isSeller)
                .mapToDouble(UserProfile::balance)
                .sum();

        lblKpiUsers.setText(String.valueOf(allUsers.size()));
        lblKpiUsersSub.setText(bidders + " Bidder • " + sellers + " Seller • " + admins + " Admin");

        if (lastTotalSellerPayout < 0) {
            lblKpiSellerPayout.setText(FormatUtils.formatVnd(totalSellerPocket));
        } else if (Math.abs(totalSellerPocket - lastTotalSellerPayout) > 0.5) {
            BidXAnimations.animateCurrency(lblKpiSellerPayout, lastTotalSellerPayout, totalSellerPocket);
        }
        lastTotalSellerPayout = totalSellerPocket;

        usersListContainer.getChildren().clear();
        int idx = 0;
        for (UserProfile u : allUsers) {
            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("ledger-row");

            String roleChip = u.isAdmin() ? "role-chip-admin" : (u.isSeller() ? "role-chip-seller" : "role-chip-bidder");
            Label roleLbl = new Label(u.role());
            roleLbl.getStyleClass().add(roleChip);
            roleLbl.setMinWidth(74);
            roleLbl.setAlignment(Pos.CENTER);

            VBox mid = new VBox(2);
            HBox.setHgrow(mid, Priority.ALWAYS);
            Label name = new Label(u.name() + "  (" + u.userId() + ")");
            name.getStyleClass().add("text-primary");
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 13.5px;");
            String perm = u.isAdmin()
                    ? "Ban Quản Trị • BidX Control"
                    : (u.isSeller() ? "Đối tác bán hàng • Đã xác minh"
                            : "Thành viên đấu giá VIP • Đã xác minh");
            Label sub = new Label(perm);
            sub.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11.5px;");
            mid.getChildren().addAll(name, sub);

            VBox right = new VBox(2);
            right.setAlignment(Pos.CENTER_RIGHT);
            Label balCaption = new Label(u.isSeller() ? "Tiền trong túi Seller" : (u.isBidder() ? "Số dư ví Bidder" : "Tài khoản Admin"));
            balCaption.setStyle("-fx-text-fill: #64748B; -fx-font-size: 10.5px;");
            Label balVal = new Label(FormatUtils.formatVnd(u.balance()));
            balVal.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: "
                    + (u.isSeller() ? "#34D399;" : (u.isBidder() ? "#FBBF24;" : "#94A3B8;")));
            right.getChildren().addAll(balCaption, balVal);

            row.getChildren().addAll(roleLbl, mid, right);
            usersListContainer.getChildren().add(row);
            BidXAnimations.fadeSlideIn(row, Math.min(idx++ * 30.0, 220.0), 8);
        }
    }

    private void renderMoneyFlow(List<WalletTxDTO> transactions) {
        moneyFlowContainer.getChildren().clear();
        if (transactions == null || transactions.isEmpty()) {
            Label empty = new Label("Chưa có giao dịch quyết toán nào. Khi một phiên đấu giá kết thúc có người thắng, dòng tiền từ Bidder → Seller sẽ ghi nhận tại đây.");
            empty.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13px; -fx-padding: 20 0 0 0;");
            moneyFlowContainer.getChildren().add(empty);
            return;
        }
        List<WalletTxDTO> rev = new ArrayList<>(transactions);
        Collections.reverse(rev);
        for (WalletTxDTO tx : rev) {
            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("ledger-row");

            String badgeText;
            String badgeClass;
            if ("PAYOUT_RECEIVED".equalsIgnoreCase(tx.type())) {
                badgeText = "TIỀN VỀ SELLER";
                badgeClass = "badge-paid";
            } else if ("TOP_UP".equalsIgnoreCase(tx.type())) {
                badgeText = "NẠP TIỀN";
                badgeClass = "badge-live";
            } else if ("WITHDRAW".equalsIgnoreCase(tx.type())) {
                badgeText = "RÚT TIỀN";
                badgeClass = "badge-pending";
            } else if ("AUCTION_PAYMENT".equalsIgnoreCase(tx.type())) {
                badgeText = "THANH TOÁN";
                badgeClass = "badge-finished";
            } else {
                badgeText = tx.type();
                badgeClass = "badge-muted";
            }
            Label typeBadge = new Label(badgeText);
            typeBadge.getStyleClass().add(badgeClass);

            VBox mid = new VBox(2);
            HBox.setHgrow(mid, Priority.ALWAYS);
            Label title = new Label(tx.userId() + " • " + (tx.itemName().isBlank() ? tx.auctionId() : tx.itemName()));
            title.getStyleClass().add("text-primary");
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
            Label sub = new Label("Đối ứng: " + tx.counterpartyId() + " • Phiên: " + tx.auctionId()
                    + " • " + FormatUtils.formatTimestampShort(tx.timestamp()));
            sub.getStyleClass().add("text-muted");
            sub.setStyle("-fx-font-size: 11.5px;");
            mid.getChildren().addAll(title, sub);

            Label amt = new Label(FormatUtils.formatSignedVnd(tx.amount()));
            amt.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: "
                    + (tx.amount() >= 0 ? "#34D399;" : "#FB7185;"));

            row.getChildren().addAll(typeBadge, mid, amt);
            moneyFlowContainer.getChildren().add(row);
        }
    }

    private void openRejectModal(AuctionDetail d) {
        this.rejectTargetAuction = d;
        lblRejectTarget.setText("Từ chối phiên: " + d.itemName() + " (" + d.auctionId() + ") của " + d.sellerId());
        txtRejectReason.clear();
        rejectModal.setVisible(true);
        rejectModal.setManaged(true);
    }

    @FXML
    public void handleCancelReject() {
        rejectTargetAuction = null;
        rejectModal.setVisible(false);
        rejectModal.setManaged(false);
    }

    @FXML
    public void handleConfirmReject() {
        if (rejectTargetAuction != null && clientSession != null && clientSession.isConnected()) {
            String reason = txtRejectReason.getText() != null && !txtRejectReason.getText().isBlank()
                    ? txtRejectReason.getText().trim()
                    : "Chưa đủ thông tin kiểm định sản phẩm";
            clientSession.getClient().rejectAuction(rejectTargetAuction.auctionId(), reason);
        }
        handleCancelReject();
    }

    private void switchTab(ScrollPane active, Button activeNav, String title) {
        ScrollPane[] tabs = {tabQueue, tabMonitor, tabUsers, tabMoneyFlow};
        for (ScrollPane sp : tabs) {
            boolean show = (sp == active);
            sp.setVisible(show);
            sp.setManaged(show);
        }
        Button[] navs = {navQueue, navMonitor, navUsers, navMoneyFlow};
        for (Button b : navs) {
            b.getStyleClass().remove("nav-btn-active-admin");
        }
        if (!activeNav.getStyleClass().contains("nav-btn-active-admin")) {
            activeNav.getStyleClass().add("nav-btn-active-admin");
        }
        lblTopTitle.setText(title);
        BidXAnimations.fadeSlideIn(active, 0, 10);
    }

    @FXML
    public void showQueueTab() {
        switchTab(tabQueue, navQueue, "Hàng đợi kiểm duyệt phòng đấu giá");
        requestSync();
    }

    @FXML
    public void showMonitorTab() {
        switchTab(tabMonitor, navMonitor, "Giám sát toàn bộ phiên đấu giá");
        requestSync();
    }

    @FXML
    public void showUsersTab() {
        switchTab(tabUsers, navUsers, "Quản lý Người dùng & Số dư ví");
        requestSync();
    }

    @FXML
    public void showMoneyFlowTab() {
        switchTab(tabMoneyFlow, navMoneyFlow, "Nhật ký Dòng tiền Hệ thống");
        requestSync();
    }

    private void setMonFilter(String filter, Button active) {
        monitorFilter = filter;
        Button[] pills = {monAll, monLive, monFinished, monRejected};
        for (Button p : pills) {
            p.getStyleClass().remove("filter-pill-active");
        }
        if (!active.getStyleClass().contains("filter-pill-active")) {
            active.getStyleClass().add("filter-pill-active");
        }
        renderMonitorList();
    }

    @FXML
    public void filterMonAll() {
        setMonFilter("ALL", monAll);
    }

    @FXML
    public void filterMonLive() {
        setMonFilter("LIVE", monLive);
    }

    @FXML
    public void filterMonEnded() {
        setMonFilter("ENDED", monFinished);
    }

    @FXML
    public void filterMonRejected() {
        setMonFilter("REJECTED", monRejected);
    }

    @FXML
    public void handleRefresh() {
        requestSync();
        BidXAnimations.popScale(btnRefresh, 1.18);
    }

    @FXML
    public void handleLogout() {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.disconnect();
        }
        ViewManager.clearSessionViews();
        ViewManager.SwitchView("LogIn");
    }
}
