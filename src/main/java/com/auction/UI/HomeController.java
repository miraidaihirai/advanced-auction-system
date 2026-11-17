package com.auction.ui;

import java.io.IOException;
import java.time.LocalDateTime;

import com.auction.item.Item;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import java.util.List;
import com.auction.session.ClientSession;
import network.AuctionClient;
import network.AuctionSummary;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;

import javafx.scene.layout.VBox;

public class HomeController implements SessionAware {
    private ClientSession clientSession;
    private AuctionClient.AuctionEventListener auctionListListener;

    private final ObservableList<AuctionSummary> auctionSummaries = FXCollections.observableArrayList();
    private final List<VBox> serverAuctionCards = new ArrayList<>();
    // Quản lí riêng các ProductCard tạo từ dự liệu server

    // ClientSession giữ kết nối dùng chung
    // auctionListListener quản lý listener
    // auctionSummaries: lưu danh sách đấu giá nhận từ server
    // ObservableList có thể thông báo khi dữ liệu thay đổi, rất phù hợp để liên kết
    @Override
    public void setClientSession(ClientSession session) {
        if (session == null) {
            return;
        }
        if (this.clientSession == session && auctionListListener != null) {
            return;
        }
        //Không làm gì nếu Session không tồn tại, nếu cùng session thì không đăng kí lại
        if (this.clientSession != null && auctionListListener != null) {
            this.clientSession.getClient().removeListener(auctionListListener);
        }
        this.clientSession = session;
        AuctionClient client = session.getClient();
         // Gỡ listener khỏi kết nối cũ
        auctionListListener = new AuctionClient.AuctionEventListener() {
            // Đăng kí listener
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

                            renderServerAuctionCards();

                            System.out.println(
                                    "Đã cập nhật giá phiên "
                                            + auctionId + ": " + newPrice);

                            break;
                        }
                    }
                });
            }
            //Xử lý thông báo đấu giá từ Server
            // Ví dụ onBidUpdate("AUC-001", 10500000, "BIDDER-01", ...);
            //1. Tìm phiên đấu giá có auctionId như kia, tạo một AuctionSummary mới
            // giữ nguyên tên sản phẩm, bước giá và trạng thái
            //Cập nhật giá và người dẫn đầu
            // Gọi renderServerAuctionCards() để cập nhật giao diện

            @Override
            public void onAuctionListReceived(List<AuctionSummary> auctions) {
                Platform.runLater(() -> {
                    auctionSummaries.setAll(auctions);

                    renderServerAuctionCards();

                    System.out.println(
                            "Đã hiển thị "
                                    + serverAuctionCards.size()
                                    + " phiên đấu giá từ Server.");
                });
            }
        };
        // Gọi khi AuctionClient đã nhận đủ danh sác và xử lí AUCTIONS_END
        client.addListener(auctionListListener);
        // Đăng kí home để nhận dữ liệu từ Socket

        if (client.isConnected()) {
            client.requestList();
        }
    }

    private void renderServerAuctionCards() {
        if (productGridContainer == null) {
            return;
        }

        for (VBox card : serverAuctionCards) {
            productGridContainer.getChildren().remove(card);
        }

        serverAuctionCards.clear();

        for (AuctionSummary auction : auctionSummaries) {
            VBox card = addProductCard.addServerAuctionCard(
                    productGridContainer,
                    auction);

            if (card != null) {
                serverAuctionCards.add(card);
            }
        }
    }
    // Hiển thị các thẻ từ auctionSummaries. Tách riêng để sử dụng khi nhận LIST mới
    // Xoá thẻ cũ do server trước tạo
    // serverAuctionCards.clear() xoá danh sách tham chiếu
    // Tạo thẻ mới

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
    public void SwitchToLogin() throws IOException {
        ViewManager.SwitchView("LogIn");
    }

    @FXML
    public void initialize() {
        // Truyền ngược HomeController sang controller con tạo phòng
        if (createRoomSubViewController != null) {
            createRoomSubViewController.setMainScreenController(this);
        }
        // Truyền tham chiếu của HomeController vào cho bảng chi tiết để nút "Quay lại"
        // tắt overlay
        if (productDetailsViewController != null) {
            productDetailsViewController.setHomeController(this);
        }
    }

    @FXML
    public void handleCreateAuctionRoom() {
        // Hiển thị lớp phủ mờ kèm bảng con lên
        overlayPane.setVisible(true);
        overlayPane.setManaged(true);

        // Hiện bảng tạo phòng, ẩn bảng chi tiết đi
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
            productDetailsViewController.initData(product); // Đổ dữ liệu sản phẩm
        }

        overlayPane.setVisible(true);
        overlayPane.setManaged(true);

        // Hiện bảng chi tiết sản phẩm, ẩn bảng tạo phòng đi
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
