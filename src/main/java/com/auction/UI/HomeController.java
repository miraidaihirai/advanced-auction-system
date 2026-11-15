package com.auction.ui;

import java.io.IOException;
import java.time.LocalDateTime;

import com.auction.item.Item;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;

public class HomeController {
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
