package com.auction.ui;

import java.io.IOException;
import java.time.LocalDateTime;

import com.auction.item.Item;

import javafx.fxml.FXML;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;

public class HomeController {
    @FXML
    private FlowPane productGridContainer;

    @FXML
    private StackPane overlayPane;

    // Tên biến phải khớp fx:id="createRoomSubView" + "Controller" =
    // createRoomSubViewController
    @FXML
    private CreateRoomUIController createRoomSubViewController;

    @FXML
    public void SwitchToLogin() throws IOException {
        ViewManager.SwitchView("LogIn");
    }

    @FXML
    public void initialize() {
        // Truyền ngược MainScreenController sang controller con để nó có thể gọi lệnh
        // ẩn bảng
        if (createRoomSubViewController != null) {
            createRoomSubViewController.setMainScreenController(this);
        }
    }

    @FXML
    public void handleCreateAuctionRoom() {
        // Hiển thị lớp phủ mờ kèm bảng con lên
        overlayPane.setVisible(true);
        overlayPane.setManaged(true);
    }

    @FXML
    public void hideOverLay() {
        overlayPane.setVisible(false);
        overlayPane.setManaged(false);
    }

    @FXML
    public void addNewAuctionCard(Item product, LocalDateTime endTime) {
        addProductCard.addNewAuctionCard(productGridContainer, product, endTime);
    }
}
