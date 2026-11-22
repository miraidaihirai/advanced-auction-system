package com.auction.ui;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.auction.item.Item;
import com.auction.session.ClientSession;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import network.AuctionClient;
import network.AuctionDetail;
import network.AuctionSummary;
import network.UserProfile;
import network.WalletTxDTO;

public class HomeController implements SessionAware {

    @FXML
    private StackPane sidebarLogoWrap;
    @FXML
    private ImageView imgSidebarLogo;
    @FXML
    private Button navHome;
    @FXML
    private Button navLive;
    @FXML
    private Button navMyBids;
    @FXML
    private Button navFavorites;
    @FXML
    private Button navLeaderboard;
    @FXML
    private Button navWallet;
    @FXML
    private Label lblWalletBalance;
    @FXML
    private Label lblCurrentUser;
    @FXML
    private Button btnLogout;

    @FXML
    private StackPane searchIconWrap;
    @FXML
    private TextField searchField;
    @FXML
    private Label lblLivePill;
    @FXML
    private Button btnThemeToggle;
    @FXML
    private Button btnSoundToggle;
    @FXML
    private Button btnRefresh;
    @FXML
    private Button btnBell;
    @FXML
    private Label lblNotifCount;
    @FXML
    private HBox filterBar;
    @FXML
    private Button pillAll;
    @FXML
    private Button pillActive;
    @FXML
    private Button pillWatches;
    @FXML
    private Button pillElectronics;
    @FXML
    private Button pillArt;
    @FXML
    private Button pillVehicles;
    @FXML
    private Button pillOther;

    @FXML
    private ScrollPane marketplaceScroll;
    @FXML
    private ScrollPane walletScroll;
    @FXML
    private ScrollPane leaderboardScroll;
    @FXML
    private StackPane tickerViewport;
    @FXML
    private Label lblTickerText;
    @FXML
    private HBox podiumBox;
    @FXML
    private VBox leaderboardListContainer;
    @FXML
    private HBox heroBanner;
    @FXML
    private ImageView imgHeroSpotlight;
    @FXML
    private Label lblHeroStats;
    @FXML
    private Label lblHeroTitle;
    @FXML
    private Label lblHeroSub;
    @FXML
    private Button btnHeroAction;
    @FXML
    private Label lblSectionTitle;
    @FXML
    private Label lblResultCount;
    @FXML
    private FlowPane productGridContainer;
    @FXML
    private VBox emptyStateBox;
    @FXML
    private VBox walletHubSlot;
    @FXML
    private VBox walletLedgerContainer;
    private BidXWalletPanel walletPanel;

    @FXML
    private StackPane notifOverlay;
    @FXML
    private VBox notifListContainer;

    private ClientSession clientSession;
    private AuctionClient.AuctionEventListener eventListener;
    private final List<AuctionDetail> allAuctions = new ArrayList<>();
    private final List<ProductCardController> activeCardControllers = new ArrayList<>();

    private String currentNavMode = "HOME";
    private String currentCategoryFilter = "ALL";
    private AuctionDetail spotlightAuction;
    private double lastDisplayedBalance = -1;
    private Timeline countdownTicker;
    private TranslateTransition tickerTransition;

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

        navHome.setGraphic(SvgIcons.create(SvgIcons.HOME, 16, "#FF9E44"));
        navLive.setGraphic(SvgIcons.create(SvgIcons.FLAME, 16, "#94A3B8"));
        navMyBids.setGraphic(SvgIcons.create(SvgIcons.GAVEL, 16, "#94A3B8"));
        navFavorites.setGraphic(SvgIcons.create(SvgIcons.HEART, 16, "#94A3B8"));
        if (navLeaderboard != null) {
            navLeaderboard.setGraphic(SvgIcons.create(SvgIcons.TROPHY, 16, "#94A3B8"));
        }
        navWallet.setGraphic(SvgIcons.create(SvgIcons.WALLET, 16, "#94A3B8"));
        btnLogout.setGraphic(SvgIcons.create(SvgIcons.LOGOUT, 14, "#94A3B8"));

        searchIconWrap.getChildren().setAll(SvgIcons.create(SvgIcons.SEARCH, 15, "#64748B"));
        btnRefresh.setGraphic(SvgIcons.create(SvgIcons.REFRESH, 16, "#CBD5E1"));
        btnBell.setGraphic(SvgIcons.create(SvgIcons.BELL, 16, "#CBD5E1"));

        if (tickerViewport != null) {
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(tickerViewport.widthProperty());
            clip.heightProperty().bind(tickerViewport.heightProperty());
            tickerViewport.setClip(clip);
        }

        BidXAnimations.startPulse(lblLivePill, 0.72, 1.04, 1800);
        BidXAnimations.applyButtonJuice(btnHeroAction);
        BidXAnimations.attachButtonGlowPulse(btnHeroAction);
        ThemeManager.syncToggleButton(btnThemeToggle);
        ThemeManager.addListener(dark -> ThemeManager.syncToggleButton(btnThemeToggle));

        if (walletHubSlot != null) {
            walletPanel = new BidXWalletPanel(false, this::submitDeposit, this::submitWithdraw);
            walletHubSlot.getChildren().setAll(walletPanel);
        }

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> renderCards());
        }

        countdownTicker = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            for (ProductCardController c : activeCardControllers) {
                c.refreshCountdown();
            }
        }));
        countdownTicker.setCycleCount(Timeline.INDEFINITE);
        countdownTicker.play();
    }

    @Override
    public void setClientSession(ClientSession session) {
        if (session == null) {
            return;
        }
        if (this.clientSession != null && eventListener != null) {
            this.clientSession.getClient().removeListener(eventListener);
        }
        this.clientSession = session;
        updateUserHeader(session.getCurrentUser());

        eventListener = new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
                Platform.runLater(() -> {
                    for (int i = 0; i < allAuctions.size(); i++) {
                        AuctionDetail d = allAuctions.get(i);
                        if (d.auctionId().equals(auctionId)) {
                            AuctionDetail updated = new AuctionDetail(
                                    d.auctionId(), d.itemName(), d.sellerId(), d.sellerName(), d.category(),
                                    d.startPrice(), newPrice, d.stepPrice(), bidderId, d.status(),
                                    d.endTimeMillis(), d.requestedDurationSec(), d.bidCount() + 1,
                                    d.payoutSettled(), d.imageKey(), d.rejectionReason(), d.description());
                            allAuctions.set(i, updated);
                            for (ProductCardController c : activeCardControllers) {
                                if (c.getAuctionId().equals(auctionId)) {
                                    c.flashNewPrice(newPrice, updated.bidCount());
                                    break;
                                }
                            }
                            break;
                        }
                    }
                });
            }

            @Override
            public void onAuctionDetailsReceived(List<AuctionDetail> auctions) {
                Platform.runLater(() -> {
                    allAuctions.clear();
                    allAuctions.addAll(auctions);
                    clientSession.updateAuctionCache(auctions);
                    updateSpotlightBanner();
                    renderCards();
                });
            }

            @Override
            public void onAuctionListReceived(List<AuctionSummary> auctions) {
                // Giữ tương thích nếu chỉ có LIST cơ bản
                if (allAuctions.isEmpty() && auctions != null && !auctions.isEmpty()) {
                    Platform.runLater(() -> {
                        for (AuctionSummary s : auctions) {
                            allAuctions.add(new AuctionDetail(
                                    s.auctionId(), s.itemId(), "SELLER-01", "Seller", "COLLECTIBLE",
                                    s.currentPrice(), s.currentPrice(), s.stepPrice(), s.highestBidderId(),
                                    s.status(), System.currentTimeMillis() + 1800_000L, 1800, 0, false, "", "", ""));
                        }
                        updateSpotlightBanner();
                        renderCards();
                    });
                }
            }

            @Override
            public void onProfileReceived(UserProfile profile) {
                Platform.runLater(() -> {
                    clientSession.setCurrentUser(profile);
                    updateUserHeader(profile);
                });
            }

            @Override
            public void onWalletHistoryReceived(List<WalletTxDTO> transactions) {
                Platform.runLater(() -> renderWalletLedger(transactions));
            }

            @Override
            public void onCatalogRefresh() {
                if (clientSession.isConnected()) {
                    clientSession.getClient().requestFullList();
                    clientSession.getClient().requestProfile();
                }
            }

            @Override
            public void onAuctionClosed(String auctionId, double finalPrice, String winnerId) {
                if (clientSession.isConnected()) {
                    clientSession.getClient().requestFullList();
                    clientSession.getClient().requestProfile();
                }
            }
        };

        session.getClient().addListener(eventListener);

        if (session.isConnected()) {
            session.getClient().requestProfile();
            session.getClient().requestFullList();
            session.getClient().requestWalletHistory();
        }
    }

    private void updateUserHeader(UserProfile profile) {
        if (profile == null) {
            return;
        }
        lblCurrentUser.setText(profile.userId() + " • " + profile.name());
        double newBalance = profile.balance();
        if (lastDisplayedBalance < 0) {
            lblWalletBalance.setText(FormatUtils.formatVnd(newBalance));
        } else if (Math.abs(newBalance - lastDisplayedBalance) > 0.5) {
            BidXAnimations.animateCurrency(lblWalletBalance, lastDisplayedBalance, newBalance);
        }
        lastDisplayedBalance = newBalance;
        if (walletPanel != null) {
            walletPanel.updateProfile(profile);
        }

        int notifCount = clientSession != null ? clientSession.getRecentNotifications().size() : 0;
        if (lblNotifCount != null) {
            lblNotifCount.setText(String.valueOf(notifCount));
            lblNotifCount.setVisible(notifCount > 0);
            lblNotifCount.setManaged(notifCount > 0);
        }
    }

    private void updateSpotlightBanner() {
        List<AuctionDetail> liveList = allAuctions.stream().filter(AuctionDetail::isRunning).toList();
        int totalBids = allAuctions.stream().mapToInt(AuctionDetail::bidCount).sum();
        lblHeroStats.setText(liveList.size() + " phiên đang diễn ra • " + totalBids + " lượt đặt giá toàn sàn");

        spotlightAuction = liveList.stream()
                .max((a, b) -> Double.compare(a.currentPrice(), b.currentPrice()))
                .orElse(liveList.isEmpty() ? null : liveList.get(0));

        if (spotlightAuction != null) {
            lblHeroTitle.setText(spotlightAuction.itemName());
            lblHeroSub.setText("Giá dẫn đầu: " + FormatUtils.formatVnd(spotlightAuction.currentPrice())
                    + "  •  Người bán: " + spotlightAuction.sellerName()
                    + "  •  Còn lại: " + FormatUtils.formatCountdownCompact(spotlightAuction.getRemainingSeconds()));
            if (imgHeroSpotlight != null) {
                ProductImageService.loadIntoImageView(
                        imgHeroSpotlight, spotlightAuction.auctionId(), spotlightAuction.itemName(),
                        spotlightAuction.imageKey(), 172, 104, 11);
            }
            btnHeroAction.setDisable(false);
        } else {
            lblHeroTitle.setText("Sàn BidX sẵn sàng đón các phiên đấu giá mới");
            lblHeroSub.setText("Các phiên đấu giá mới sẽ hiển thị tức thời ngay khi mở sàn.");
            btnHeroAction.setDisable(true);
        }

        updateLiveTicker(liveList);
        renderLeaderboard();
    }

    private void updateLiveTicker(List<AuctionDetail> liveList) {
        if (lblTickerText == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (AuctionDetail d : liveList) {
            sb.append("★ ").append(d.itemName()).append(": ")
              .append(FormatUtils.formatVnd(d.currentPrice()))
              .append(" (").append(d.sellerName()).append(")     •     ");
        }
        if (sb.length() == 0) {
            sb.append("BidX Live • Sàn đấu giá tài sản cao cấp thời gian thực • Anti-Sniping +30s");
        }
        lblTickerText.setText(sb.toString());

        if (tickerTransition != null) {
            tickerTransition.stop();
        }
        lblTickerText.setTranslateX(140);
        tickerTransition = new TranslateTransition(Duration.seconds(24), lblTickerText);
        tickerTransition.setFromX(220);
        tickerTransition.setToX(-Math.max(820, sb.length() * 6.4));
        tickerTransition.setCycleCount(Timeline.INDEFINITE);
        tickerTransition.setInterpolator(Interpolator.LINEAR);
        tickerTransition.play();
    }

    private void renderLeaderboard() {
        if (podiumBox == null || leaderboardListContainer == null) {
            return;
        }
        podiumBox.getChildren().clear();
        leaderboardListContainer.getChildren().clear();

        List<AuctionDetail> ranked = allAuctions.stream()
                .filter(d -> !d.isPendingApproval() && !d.isRejected())
                .sorted((a, b) -> Double.compare(b.currentPrice(), a.currentPrice()))
                .toList();

        String[] podiumStyles = {"podium-card-gold", "podium-card-silver", "podium-card-bronze"};
        String[] medalTags = {"#1  TOP GIÁ TRỊ", "#2  HẠNG NHÌ", "#3  HẠNG BA"};
        String[] accentColors = {"#FBBF24", "#CBD5E1", "#FB923C"};

        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            AuctionDetail d = ranked.get(i);
            VBox card = new VBox(8);
            HBox.setHgrow(card, Priority.ALWAYS);
            card.getStyleClass().add(podiumStyles[i]);
            card.setStyle(card.getStyle() + "; -fx-cursor: hand;");

            Label rankLbl = new Label(medalTags[i]);
            rankLbl.setStyle("-fx-text-fill: " + accentColors[i] + "; -fx-font-weight: bold; -fx-font-size: 11.5px;");
            Label nameLbl = new Label(d.itemName());
            nameLbl.setWrapText(true);
            nameLbl.getStyleClass().add("text-primary");
            nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 15.5px;");
            Label priceLbl = new Label(FormatUtils.formatVnd(d.currentPrice()));
            priceLbl.setStyle("-fx-text-fill: " + accentColors[i] + "; -fx-font-weight: bold; -fx-font-size: 20px;");
            Label sellerLbl = new Label("Người bán: " + d.sellerName() + " • " + d.bidCount() + " lượt đặt");
            sellerLbl.getStyleClass().add("text-muted");
            sellerLbl.setStyle("-fx-font-size: 11.5px;");

            card.getChildren().addAll(rankLbl, nameLbl, priceLbl, sellerLbl);
            card.setOnMouseClicked(e -> ViewManager.openAuctionRoom(d));
            BidXAnimations.applyCardHoverLift(card, javafx.scene.paint.Color.web(accentColors[i], 0.4));
            podiumBox.getChildren().add(card);
        }

        int pos = 1;
        for (AuctionDetail d : ranked) {
            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("ledger-row");
            row.setStyle("-fx-cursor: hand;");

            Label num = new Label("#" + pos++);
            num.setMinWidth(34);
            num.getStyleClass().add("text-accent-orange");
            num.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

            ImageView thumb = new ImageView();
            ProductImageService.loadIntoImageView(thumb, d.auctionId(), d.itemName(), d.imageKey(), 74, 48, 8);

            VBox mid = new VBox(2);
            HBox.setHgrow(mid, Priority.ALWAYS);
            Label title = new Label(d.itemName());
            title.getStyleClass().add("text-primary");
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            Label sub = new Label("Người bán: " + d.sellerName() + " • " + d.bidCount() + " lượt đặt • "
                    + (d.hasWinner() ? "Dẫn đầu: " + d.highestBidderId() : "Mở giá khởi điểm"));
            sub.getStyleClass().add("text-muted");
            sub.setStyle("-fx-font-size: 11.5px;");
            mid.getChildren().addAll(title, sub);

            Label price = new Label(FormatUtils.formatVnd(d.currentPrice()));
            price.getStyleClass().add("text-accent-gold");
            price.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

            row.getChildren().addAll(num, thumb, mid, price);
            row.setOnMouseClicked(e -> ViewManager.openAuctionRoom(d));
            leaderboardListContainer.getChildren().add(row);
        }
    }

    private void renderCards() {
        productGridContainer.getChildren().clear();
        activeCardControllers.clear();

        String keyword = searchField != null && searchField.getText() != null
                ? searchField.getText().trim().toLowerCase(Locale.ROOT)
                : "";
        UserProfile me = clientSession != null ? clientSession.getCurrentUser() : null;
        String myId = me != null ? me.userId() : "";
        boolean isBidder = me == null || me.isBidder();

        int idx = 0;
        for (AuctionDetail d : allAuctions) {
            // Bidder chỉ thấy các phiên đã được Admin duyệt (KHÔNG thấy PENDING_APPROVAL hay REJECTED)
            if (d.isPendingApproval() || d.isRejected()) {
                continue;
            }
            if (!matchesNavAndFilter(d, keyword, myId)) {
                continue;
            }
            boolean fav = clientSession != null && clientSession.isFavorite(d.auctionId());
            ProductCardController ctrl = AddProductCard.addDetailAuctionCard(
                    productGridContainer, d, fav, isBidder, this, idx++);
            if (ctrl != null) {
                activeCardControllers.add(ctrl);
            }
        }

        lblResultCount.setText(idx + " phiên đấu giá");
        boolean empty = idx == 0;
        emptyStateBox.setVisible(empty);
        emptyStateBox.setManaged(empty);
    }

    private boolean matchesNavAndFilter(AuctionDetail d, String keyword, String myId) {
        String name = d.itemName().toLowerCase(Locale.ROOT);
        String id = d.auctionId().toLowerCase(Locale.ROOT);
        if (!keyword.isEmpty() && !name.contains(keyword) && !id.contains(keyword)) {
            return false;
        }
        if ("LIVE".equals(currentNavMode) && !d.isRunning()) {
            return false;
        }
        if ("MY_BIDS".equals(currentNavMode)) {
            if (myId.isEmpty() || !myId.equals(d.highestBidderId())) {
                return false;
            }
        }
        if ("FAVORITES".equals(currentNavMode)) {
            if (clientSession == null || !clientSession.isFavorite(d.auctionId())) {
                return false;
            }
        }

        if ("ALL".equals(currentCategoryFilter)) {
            return true;
        }
        if ("ACTIVE".equals(currentCategoryFilter)) {
            return d.isRunning();
        }
        String cat = FormatUtils.detectCategory(d.category(), d.itemName());
        return cat.equalsIgnoreCase(currentCategoryFilter);
    }

    private void renderWalletLedger(List<WalletTxDTO> transactions) {
        if (walletPanel != null) {
            walletPanel.updateTransactions(transactions);
        }
        walletLedgerContainer.getChildren().clear();
        if (transactions == null || transactions.isEmpty()) {
            Label empty = new Label("Chưa có giao dịch ví nào. Khi bạn nạp tiền, rút tiền hoặc thắng phiên, lịch sử sẽ hiển thị tại đây.");
            empty.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13px; -fx-padding: 18 0 0 0;");
            walletLedgerContainer.getChildren().add(empty);
            return;
        }
        List<WalletTxDTO> reversed = new ArrayList<>(transactions);
        java.util.Collections.reverse(reversed);
        int idx = 0;
        for (WalletTxDTO tx : reversed) {
            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("ledger-row");

            boolean isTopUp = "TOP_UP".equalsIgnoreCase(tx.type());
            boolean isWithdraw = "WITHDRAW".equalsIgnoreCase(tx.type());
            boolean positive = tx.amount() >= 0;
            String iconPath = isTopUp ? SvgIcons.ARROW_DOWN : (isWithdraw ? SvgIcons.ARROW_UP : SvgIcons.GAVEL);
            String iconColor = positive ? "#34D399" : (isWithdraw ? "#FF9E44" : "#FB7185");
            String iconBg = positive ? "rgba(16,185,129,0.16)"
                    : (isWithdraw ? "rgba(255,123,36,0.16)" : "rgba(244,63,94,0.16)");

            StackPane icon = new StackPane(SvgIcons.create(iconPath, 16, iconColor));
            icon.setMinSize(36, 36);
            icon.setStyle("-fx-background-color: " + iconBg + "; -fx-background-radius: 10;");

            VBox info = new VBox(3);
            HBox.setHgrow(info, Priority.ALWAYS);
            Label title = new Label(tx.itemName().isBlank() ? tx.type() : tx.itemName());
            title.getStyleClass().add("text-primary");
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 13.5px;");
            String meta = ("-".equals(tx.auctionId()) || tx.auctionId().isBlank())
                    ? "Kênh: " + tx.counterpartyId() + " • " + FormatUtils.formatTimestampShort(tx.timestamp())
                    : "Mã phiên: " + tx.auctionId() + " • Đối tác: " + tx.counterpartyId()
                            + " • " + FormatUtils.formatTimestampShort(tx.timestamp());
            Label sub = new Label(meta);
            sub.getStyleClass().add("text-muted");
            sub.setStyle("-fx-font-size: 11.5px;");
            info.getChildren().addAll(title, sub);

            VBox right = new VBox(2);
            right.setAlignment(Pos.CENTER_RIGHT);
            Label amt = new Label(FormatUtils.formatSignedVnd(tx.amount()));
            amt.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: "
                    + (positive ? "#34D399;" : "#FB7185;"));
            Label bal = new Label("Số dư: " + FormatUtils.formatVnd(tx.balanceAfter()));
            bal.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");
            right.getChildren().addAll(amt, bal);

            row.getChildren().addAll(icon, info, right);
            walletLedgerContainer.getChildren().add(row);
            BidXAnimations.fadeSlideIn(row, Math.min(idx++ * 35.0, 250.0), 10);
        }
    }

    private void switchMainPane(String pane) {
        boolean isMarket = "MARKET".equals(pane);
        boolean isWallet = "WALLET".equals(pane);
        boolean isLeader = "LEADERBOARD".equals(pane);

        marketplaceScroll.setVisible(isMarket);
        marketplaceScroll.setManaged(isMarket);
        filterBar.setVisible(isMarket);
        filterBar.setManaged(isMarket);

        walletScroll.setVisible(isWallet);
        walletScroll.setManaged(isWallet);

        if (leaderboardScroll != null) {
            leaderboardScroll.setVisible(isLeader);
            leaderboardScroll.setManaged(isLeader);
        }
    }

    private void setActiveNav(Button active) {
        Button[] all = {navHome, navLive, navMyBids, navFavorites, navLeaderboard, navWallet};
        for (Button b : all) {
            if (b != null) {
                b.getStyleClass().remove("nav-btn-active");
            }
        }
        if (active != null && !active.getStyleClass().contains("nav-btn-active")) {
            active.getStyleClass().add("nav-btn-active");
        }
    }

    @FXML
    public void handleToggleTheme() {
        ThemeManager.toggleTheme();
    }

    @FXML
    public void handleToggleSound() {
        boolean on = SoundEffects.toggleEnabled();
        if (btnSoundToggle != null) {
            btnSoundToggle.setText(on ? "SFX ON" : "SFX OFF");
        }
    }

    private void setActivePill(Button active) {
        Button[] all = {pillAll, pillActive, pillWatches, pillElectronics, pillArt, pillVehicles, pillOther};
        for (Button b : all) {
            if (b != null) {
                b.getStyleClass().remove("filter-pill-active");
            }
        }
        if (active != null && !active.getStyleClass().contains("filter-pill-active")) {
            active.getStyleClass().add("filter-pill-active");
        }
    }

    public void onFavoriteToggled() {
        if ("FAVORITES".equals(currentNavMode)) {
            renderCards();
        }
    }

    @FXML
    public void handleRefreshList() {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().requestFullList();
            clientSession.getClient().requestProfile();
            clientSession.getClient().requestWalletHistory();
            BidXAnimations.popScale(btnRefresh, 1.18);
        }
    }

    private void submitDeposit(double amount, String bankChannel) {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().topUpWallet(amount, bankChannel);
        }
    }

    private void submitWithdraw(double amount, String bankDestination) {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().withdrawWallet(amount, bankDestination);
        }
    }

    @FXML
    public void handleQuickTopUp() {
        handleOpenDeposit();
    }

    @FXML
    public void handleOpenDeposit() {
        handleNavWallet();
        if (walletPanel != null) {
            walletPanel.openDepositMode();
        }
    }

    @FXML
    public void handleOpenWithdraw() {
        handleNavWallet();
        if (walletPanel != null) {
            walletPanel.openWithdrawMode();
        }
    }

    @FXML
    public void handleOpenSpotlight() {
        if (spotlightAuction != null) {
            ViewManager.openAuctionRoom(spotlightAuction);
        }
    }

    @FXML
    public void handleFilterAll() {
        currentCategoryFilter = "ALL";
        setActivePill(pillAll);
        lblSectionTitle.setText("Tất Cả Phiên Đấu Giá Đã Duyệt");
        renderCards();
    }

    @FXML
    public void handleFilterActive() {
        currentCategoryFilter = "ACTIVE";
        setActivePill(pillActive);
        lblSectionTitle.setText("Phiên Đang Diễn Ra (Live)");
        renderCards();
    }

    @FXML
    public void handleFilterWatches() {
        currentCategoryFilter = "WATCHES";
        setActivePill(pillWatches);
        lblSectionTitle.setText("Danh Mục: Đồng Hồ Cổ & Luxury");
        renderCards();
    }

    @FXML
    public void handleFilterElectronics() {
        currentCategoryFilter = "ELECTRONICS";
        setActivePill(pillElectronics);
        lblSectionTitle.setText("Danh Mục: Đồ Điện Tử & Máy Ảnh");
        renderCards();
    }

    @FXML
    public void handleFilterArt() {
        currentCategoryFilter = "ART";
        setActivePill(pillArt);
        lblSectionTitle.setText("Danh Mục: Nghệ Thuật & Cổ Vật");
        renderCards();
    }

    @FXML
    public void handleFilterVehicles() {
        currentCategoryFilter = "VEHICLES";
        setActivePill(pillVehicles);
        lblSectionTitle.setText("Danh Mục: Siêu Xe Đẳng Cấp");
        renderCards();
    }

    @FXML
    public void handleFilterOther() {
        currentCategoryFilter = "OTHER";
        setActivePill(pillOther);
        lblSectionTitle.setText("Danh Mục: Khác • Vật Phẩm Sưu Tầm Độc Bản");
        renderCards();
    }

    @FXML
    public void handleNavHome() {
        currentNavMode = "HOME";
        setActiveNav(navHome);
        switchMainPane("MARKET");
        lblSectionTitle.setText("Phiên Đấu Giá Đã Kiểm Duyệt");
        renderCards();
    }

    @FXML
    public void handleNavExplore() {
        currentNavMode = "LIVE";
        setActiveNav(navLive);
        switchMainPane("MARKET");
        lblSectionTitle.setText("Phiên Đang Diễn Ra (Live)");
        renderCards();
    }

    @FXML
    public void handleNavMyAuctions() {
        currentNavMode = "MY_BIDS";
        setActiveNav(navMyBids);
        switchMainPane("MARKET");
        lblSectionTitle.setText("Phiên Bạn Đang Dẫn Đầu & Đã Thắng");
        renderCards();
    }

    @FXML
    public void handleNavFavorites() {
        currentNavMode = "FAVORITES";
        setActiveNav(navFavorites);
        switchMainPane("MARKET");
        lblSectionTitle.setText("Danh Sách Yêu Thích Của Bạn");
        renderCards();
    }

    @FXML
    public void handleNavLeaderboard() {
        setActiveNav(navLeaderboard);
        switchMainPane("LEADERBOARD");
        renderLeaderboard();
    }

    @FXML
    public void handleNavWallet() {
        setActiveNav(navWallet);
        switchMainPane("WALLET");
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().requestProfile();
            clientSession.getClient().requestWalletHistory();
        }
    }

    @FXML
    public void handleToggleNotifications() {
        boolean show = !notifOverlay.isVisible();
        if (show) {
            notifListContainer.getChildren().clear();
            List<String> logs = clientSession != null ? clientSession.getRecentNotifications() : List.of();
            if (logs.isEmpty()) {
                Label empty = new Label("Chưa có thông báo mới.");
                empty.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13px;");
                notifListContainer.getChildren().add(empty);
            } else {
                for (String item : logs) {
                    Label row = new Label(item);
                    row.setWrapText(true);
                    row.setMaxWidth(Double.MAX_VALUE);
                    row.getStyleClass().addAll("ledger-row", "text-secondary");
                    row.setStyle("-fx-font-size: 12px;");
                    notifListContainer.getChildren().add(row);
                }
            }
        }
        notifOverlay.setVisible(show);
        notifOverlay.setManaged(show);
    }

    @FXML
    public void handleCloseNotifications() {
        notifOverlay.setVisible(false);
        notifOverlay.setManaged(false);
    }

    @FXML
    public void consumeClick(MouseEvent event) {
        event.consume();
    }

    @FXML
    public void SwitchToLogin() throws IOException {
        if (countdownTicker != null) {
            countdownTicker.stop();
        }
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.disconnect();
        }
        ViewManager.clearSessionViews();
        ViewManager.SwitchView("LogIn");
    }

    // Giữ các phương thức cũ để tương thích ngược nếu class phụ còn gọi
    public void hideOverLay() {
    }

    public void showProductDetailsOverlay(Item product) {
    }

    public void addNewAuctionCard(Item product, LocalDateTime endTime) {
        AddProductCard.addNewAuctionCard(productGridContainer, product, endTime, this);
    }
}
