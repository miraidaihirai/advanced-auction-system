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
    private Label lblCategory;

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
    private AuctionSummary auctionSummary;
    private HomeController homeController;

    private final TimelineCard timeline = new TimelineCard();
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));

    public void setHomeController(HomeController homeController) {
        this.homeController = homeController;
    }

    public void setData(Item product, String bids, LocalDateTime endTime) {
        this.Product = product;
        currencyFormat.setMaximumFractionDigits(0);

        String id = String.valueOf(product.getId());
        if (lblId != null) lblId.setText("ID: " + id);
        if (lblName != null) lblName.setText(product.getName());
        if (lblPrice != null) lblPrice.setText(currencyFormat.format(product.getPrice()) + " đ");
        if (lblBids != null) lblBids.setText(bids + " Bids");
        if (lblCategory != null) lblCategory.setText(detectCategory(product.getName()));

        if (endTime != null && lblTime != null) {
            timeline.startCountdown(endTime, lblTime);
        }
    }

    public void setAuctionData(AuctionSummary auction) {
        if (auction == null) return;
        this.auctionSummary = auction;
        currencyFormat.setMaximumFractionDigits(0);

        if (lblId != null) lblId.setText(auction.auctionId());
        if (lblName != null) lblName.setText(auction.itemId());
        if (lblCategory != null) lblCategory.setText(detectCategory(auction.itemId()));
        if (lblPrice != null) lblPrice.setText(currencyFormat.format(auction.currentPrice()) + " đ");

        String highestBidder = auction.highestBidderId();
        if (lblBids != null) {
            if (highestBidder == null || highestBidder.equalsIgnoreCase("NONE")) {
                lblBids.setText("Chưa có lượt đặt");
            } else {
                lblBids.setText("Dẫn đầu: " + highestBidder);
            }
        }

        if (lblTime != null) {
            lblTime.setText("03h 10m 15s");
        }

        if (btnBid != null) {
            btnBid.setDisable(false);
        }
    }

    private String detectCategory(String name) {
        if (name == null) return "[ ĐỒNG HỒ CỔ ]";
        String lower = name.toLowerCase();
        if (lower.contains("đồng hồ") || lower.contains("watch")) {
            return "[ ĐỒNG HỒ CỔ ]";
        } else if (lower.contains("dell") || lower.contains("laptop") || lower.contains("máy ảnh") || lower.contains("camera")) {
            return "[ ĐỒ ĐIỆN TỬ ]";
        } else if (lower.contains("bình") || lower.contains("vase") || lower.contains("tranh") || lower.contains("art")) {
            return "[ NGHỆ THUẬT ]";
        } else if (lower.contains("xe") || lower.contains("car") || lower.contains("auto")) {
            return "[ XE CỘ ]";
        }
        return "[ SƯU TẦM ]";
    }

    @FXML
    public void handleOpenRoom() {
        if (auctionSummary != null) {
            ViewManager.openAuctionRoom(auctionSummary);
        } else if (Product != null) {
            AuctionSummary summary = new AuctionSummary(
                    "AUC-" + Product.getId(),
                    Product.getName(),
                    Product.getPrice(),
                    500_000.0,
                    "NONE",
                    "ACTIVE"
            );
            ViewManager.openAuctionRoom(summary);
        }
    }

    @FXML
    private void handleViewDetails() {
        if (Product == null) {
            if (auctionSummary != null) {
                handleOpenRoom();
            }
            return;
        }

        if (homeController != null) {
            homeController.showProductDetailsOverlay(Product);
        }
    }
}
