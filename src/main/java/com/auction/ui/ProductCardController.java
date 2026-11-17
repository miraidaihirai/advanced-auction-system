package com.auction.ui;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Locale;

import com.auction.item.Item;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import network.AuctionSummary;

public class ProductCardController {
    @FXML
    private Label lblId;

    @FXML
    private Label lblName;

    @FXML
    private Label lblPrice;

    @FXML
    private Button btnBid;

    @FXML
    private Label lblTime;

    @FXML
    private Label lblBids;

    @FXML
    private VBox card;

    private Item Product;
    private HomeController homeController;

    private final TimelineCard timeline = new TimelineCard();

    public void setHomeController(HomeController homeController) {
        this.homeController = homeController;
    }

    public void setData(Item product, String bids, LocalDateTime endTime){
        //In miêu tả cho thẻ sản phẩm
        String id = String.valueOf(product.getId());
        lblId.setText(id);
        lblName.setText(product.getName());
        lblPrice.setText(String.valueOf(product.getPrice()));
        lblBids.setText(bids);
        this.Product = product;
        
        //thiết lập thời gian đếm ngược
        timeline.startCountdown(endTime, lblTime);
    }

    public void setAuctionData(AuctionSummary auction) {
        if (auction == null) return;

        lblId.setText(auction.auctionId());
        lblName.setText(auction.itemId());

        NumberFormat priceFormat =
                NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));

        priceFormat.setMaximumFractionDigits(2);
        priceFormat.setMinimumFractionDigits(0);

        lblPrice.setText(priceFormat.format(auction.currentPrice()));

        String highestBidder = auction.highestBidderId();

        if (highestBidder == null || highestBidder.equalsIgnoreCase("NONE")) {
            lblBids.setText("Chưa có người đặt giá");
        } else {
            lblBids.setText("Người dẫn đầu: " + highestBidder);
        }

        lblTime.setText("Trạng thái: " + translateStatus(auction.status()));

        btnBid.setDisable(true);
    }

    private String translateStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Không xác định";
        }

        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> "Đang diễn ra";
            case "CLOSED", "ENDED", "FINISHED" -> "Đã kết thúc";
            case "PENDING", "WAITING" -> "Chưa bắt đầu";
            default -> "Không xác định (" + status + ")";
        };
    }

    public void handleCardClick() {
        card.setOnMouseClicked(event -> {
            navigateToAuctionRoom("NameAuctionRoom");
        });
    }

    @FXML
    private void navigateToAuctionRoom(String idAuctionRoom) {
        System.out.println("Đang mở phiên đấu giá của sản phẩm ID:" + idAuctionRoom);

        try {
            ViewManager.SwitchView(idAuctionRoom);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @FXML
    private void handleViewDetails() {
        if (Product == null) {
            System.err.println("Lỗi: Card này chưa có dữ liệu sản phẩm!");
            return;
        }

        // Gọi hàm bật overlay hiển thị chi tiết sản phẩm ngay trên màn hình Home
        if (homeController != null) {
            homeController.showProductDetailsOverlay(Product);
        } else {
            System.err.println("Lỗi: Chưa liên kết HomeController với ProductCardController!");
        }
    }
}
