package com.auction.ui;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.auction.item.Item;
import com.auction.session.ClientSession;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import network.AuctionClient;
import network.AuctionSummary;

public class HomeController implements SessionAware {
    private ClientSession clientSession;
    private AuctionClient.AuctionEventListener auctionListListener;

    private final ObservableList<AuctionSummary> auctionSummaries = FXCollections.observableArrayList();
    private final List<VBox> serverAuctionCards = new ArrayList<>();
    private String currentCategoryFilter = "ALL";

    @FXML
    private FlowPane productGridContainer;

    @FXML
    private StackPane overlayPane;

    @FXML
    private Node createRoomSubView;

    @FXML
    private Node productDetailsView;

    @FXML
    private CreateRoomUIController createRoomSubViewController;

    @FXML
    private ProductDetailsController productDetailsViewController;

    @FXML
    private TextField searchField;

    @FXML
    private Button btnCreateAuctionRoom;

    @FXML
    private Label lblCurrentUser;

    @FXML
    private Label lblSectionTitle;

    @Override
    public void setClientSession(ClientSession session) {
        if (session == null) {
            return;
        }
        if (this.clientSession == session && auctionListListener != null) {
            return;
        }

        if (this.clientSession != null && auctionListListener != null) {
            this.clientSession.getClient().removeListener(auctionListListener);
        }
        this.clientSession = session;
        AuctionClient client = session.getClient();

        auctionListListener = new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(
                    String auctionId,
                    double newPrice,
                    String bidderId,
                    String itemId) {
                Platform.runLater(() -> {
                    for (int i = 0; i < auctionSummaries.size(); i++) {
                        AuctionSummary current = auctionSummaries.get(i);
                        if (current.auctionId().equals(auctionId)) {
                            AuctionSummary updated = new AuctionSummary(
                                    current.auctionId(),
                                    current.itemId(),
                                    newPrice,
                                    current.stepPrice(),
                                    bidderId,
                                    current.status());

                            auctionSummaries.set(i, updated);
                            renderFilteredCards();
                            break;
                        }
                    }
                });
            }

            @Override
            public void onAuctionListReceived(List<AuctionSummary> auctions) {
                Platform.runLater(() -> {
                    auctionSummaries.setAll(auctions);
                    renderFilteredCards();
                });
            }
        };

        client.addListener(auctionListListener);

        if (client.isConnected()) {
            client.requestList();
        }
    }

    @FXML
    public void initialize() {
        if (createRoomSubViewController != null) {
            createRoomSubViewController.setMainScreenController(this);
        }
        if (productDetailsViewController != null) {
            productDetailsViewController.setHomeController(this);
        }

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> renderFilteredCards());
        }
    }

    private void renderFilteredCards() {
        if (productGridContainer == null) {
            return;
        }

        for (VBox card : serverAuctionCards) {
            productGridContainer.getChildren().remove(card);
        }
        serverAuctionCards.clear();

        String keyword = searchField != null && searchField.getText() != null
                ? searchField.getText().trim().toLowerCase() : "";

        for (AuctionSummary auction : auctionSummaries) {
            if (!matchesFilter(auction, keyword, currentCategoryFilter)) {
                continue;
            }

            VBox card = addProductCard.addServerAuctionCard(
                    productGridContainer,
                    auction);

            if (card != null) {
                serverAuctionCards.add(card);
            }
        }
    }

    private boolean matchesFilter(AuctionSummary auction, String keyword, String category) {
        String name = auction.itemId().toLowerCase();
        String id = auction.auctionId().toLowerCase();

        if (!keyword.isEmpty() && !name.contains(keyword) && !id.contains(keyword)) {
            return false;
        }

        if (category == null || category.equals("ALL")) {
            return true;
        }

        return switch (category) {
            case "ACTIVE" -> auction.status() != null && auction.status().equalsIgnoreCase("ACTIVE");
            case "WATCHES" -> name.contains("đồng hồ") || name.contains("watch");
            case "ELECTRONICS" -> name.contains("dell") || name.contains("laptop") || name.contains("máy ảnh") || name.contains("camera");
            case "ART" -> name.contains("bình") || name.contains("vase") || name.contains("tranh") || name.contains("art");
            case "VEHICLES" -> name.contains("xe") || name.contains("car") || name.contains("auto");
            default -> true;
        };
    }

    @FXML
    public void handleRefreshList() {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.getClient().requestList();
        }
    }

    @FXML
    public void handleFilterAll() {
        currentCategoryFilter = "ALL";
        updateSectionTitle("Tất Cả Phiên Đấu Giá");
        renderFilteredCards();
    }

    @FXML
    public void handleFilterActive() {
        currentCategoryFilter = "ACTIVE";
        updateSectionTitle("Phiên Đang Diễn Ra (Live)");
        renderFilteredCards();
    }

    @FXML
    public void handleFilterWatches() {
        currentCategoryFilter = "WATCHES";
        updateSectionTitle("Danh Mục: Đồng Hồ Cổ & Cao Cấp");
        renderFilteredCards();
    }

    @FXML
    public void handleFilterElectronics() {
        currentCategoryFilter = "ELECTRONICS";
        updateSectionTitle("Danh Mục: Đồ Điện Tử & Công Nghệ");
        renderFilteredCards();
    }

    @FXML
    public void handleFilterArt() {
        currentCategoryFilter = "ART";
        updateSectionTitle("Danh Mục: Nghệ Thuật & Cổ Vật");
        renderFilteredCards();
    }

    @FXML
    public void handleFilterVehicles() {
        currentCategoryFilter = "VEHICLES";
        updateSectionTitle("Danh Mục: Xe Cộ & Phương Tiện");
        renderFilteredCards();
    }

    private void updateSectionTitle(String title) {
        if (lblSectionTitle != null) {
            lblSectionTitle.setText(title);
        }
    }

    @FXML
    public void handleNavHome() {
        handleFilterAll();
    }

    @FXML
    public void handleNavExplore() {
        handleFilterActive();
    }

    @FXML
    public void handleNavMyAuctions() {
        updateSectionTitle("Các Phiên Bạn Đang Tham Gia");
        renderFilteredCards();
    }

    @FXML
    public void handleNavHistory() {
        updateSectionTitle("Lịch Sử Đấu Giá Của Hệ Thống");
        renderFilteredCards();
    }

    @FXML
    public void handleNavFavorites() {
        updateSectionTitle("Danh Sách Phiên Đấu Giá Yêu Thích");
        renderFilteredCards();
    }

    @FXML
    public void handleNavSettings() {
        updateSectionTitle("Cài Đặt Hệ Thống & Tài Khoản");
    }

    @FXML
    public void SwitchToLogin() throws IOException {
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.disconnect();
        }
        ViewManager.SwitchView("LogIn");
    }

    @FXML
    public void handleCreateAuctionRoom() {
        overlayPane.setVisible(true);
        overlayPane.setManaged(true);

        if (createRoomSubView != null) {
            createRoomSubView.setVisible(true);
            createRoomSubView.setManaged(true);
        }
        if (productDetailsView != null) {
            productDetailsView.setVisible(false);
            productDetailsView.setManaged(false);
        }
    }

    public void showProductDetailsOverlay(Item product) {
        if (productDetailsViewController != null) {
            productDetailsViewController.initData(product);
        }

        overlayPane.setVisible(true);
        overlayPane.setManaged(true);

        if (productDetailsView != null) {
            productDetailsView.setVisible(true);
            productDetailsView.setManaged(true);
        }
        if (createRoomSubView != null) {
            createRoomSubView.setVisible(false);
            createRoomSubView.setManaged(false);
        }
    }

    @FXML
    public void hideOverLay() {
        overlayPane.setVisible(false);
        overlayPane.setManaged(false);
    }

    @FXML
    public void addNewAuctionCard(Item product, LocalDateTime endTime) {
        addProductCard.addNewAuctionCard(productGridContainer, product, endTime, this);
    }
}
