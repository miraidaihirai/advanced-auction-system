package com.auction.ui;

import java.time.LocalDateTime;

import com.auction.item.Item;
import com.auction.session.ClientSession;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import network.AuctionDetail;
import network.AuctionSummary;

public class ProductCardController {

    @FXML
    private VBox card;
    @FXML
    private ImageView imgProduct;
    @FXML
    private Label lblStatusBadge;
    @FXML
    private Button btnFavorite;
    @FXML
    private Label lblCategory;
    @FXML
    private Label lblId;
    @FXML
    private Label lblName;
    @FXML
    private Label lblPrice;
    @FXML
    private Label lblTime;
    @FXML
    private Label lblBids;
    @FXML
    private Button btnBid;

    private Item product;
    private AuctionSummary auctionSummary;
    private AuctionDetail auctionDetail;
    private HomeController homeController;

    private final TimelineCard timeline = new TimelineCard();

    @FXML
    public void initialize() {
        BidXAnimations.applyCardHoverLift(card, Color.rgb(255, 123, 36, 0.38));
        BidXAnimations.applyButtonJuice(btnBid);
        updateFavoriteIcon(false);
    }

    public void setHomeController(HomeController homeController) {
        this.homeController = homeController;
    }

    public void setAuctionDetail(AuctionDetail detail, boolean isFavorite, boolean isBidderRole) {
        if (detail == null) {
            return;
        }
        this.auctionDetail = detail;
        this.auctionSummary = detail.toSummary();

        lblId.setText(detail.auctionId());
        lblName.setText(detail.itemName());
        lblCategory.setText(FormatUtils.categoryLabel(FormatUtils.detectCategory(detail.category(), detail.itemName())));
        lblPrice.setText(FormatUtils.formatVnd(detail.currentPrice()));
        lblBids.setText(detail.bidCount() + " Bids");

        ProductImageService.loadIntoImageView(
                imgProduct, detail.auctionId(), detail.itemName(), detail.imageKey(), 224, 142, 12);

        applyStatusBadge(detail.status());
        refreshCountdown();
        updateFavoriteIcon(isFavorite);

        if (detail.isRunning()) {
            btnBid.setText(isBidderRole ? "ĐẶT GIÁ" : "XEM PHÒNG LIVE");
        } else if (detail.isFinished() || detail.isPaid()) {
            btnBid.setText("XEM KẾT QUẢ");
        } else {
            btnBid.setText("CHI TIẾT");
        }
    }

    public void setData(Item product, String bids, LocalDateTime endTime) {
        this.product = product;
        lblId.setText("ITEM-" + product.getId());
        lblName.setText(product.getName());
        lblPrice.setText(FormatUtils.formatVnd(product.getPrice()));
        lblBids.setText(bids + " Bids");
        lblCategory.setText(FormatUtils.categoryLabel(FormatUtils.detectCategory(null, product.getName())));
        ProductImageService.loadIntoImageView(
                imgProduct, "ITEM-" + product.getId(), product.getName(), product.getImagePath(), 238, 152, 12);
        applyStatusBadge("RUNNING");
        if (endTime != null) {
            timeline.startCountdown(endTime, lblTime);
        }
    }

    public void setAuctionData(AuctionSummary auction) {
        if (auction == null) {
            return;
        }
        this.auctionSummary = auction;
        lblId.setText(auction.auctionId());
        lblName.setText(auction.itemId());
        lblCategory.setText(FormatUtils.categoryLabel(FormatUtils.detectCategory(null, auction.itemId())));
        lblPrice.setText(FormatUtils.formatVnd(auction.currentPrice()));
        String leader = auction.highestBidderId();
        lblBids.setText((leader == null || "NONE".equalsIgnoreCase(leader)) ? "0 Bids" : "Top: " + leader);
        lblTime.setText("Đang diễn ra");
        ProductImageService.loadIntoImageView(imgProduct, auction.auctionId(), auction.itemId(), null, 238, 152, 12);
        applyStatusBadge(auction.status());
    }

    public void refreshCountdown() {
        if (auctionDetail == null || lblTime == null) {
            return;
        }
        if (auctionDetail.isRunning()) {
            long rem = auctionDetail.getRemainingSeconds();
            lblTime.setText(rem > 0 ? FormatUtils.formatCountdownCompact(rem) : "00h 00m 00s");
            lblTime.setStyle(rem <= 30
                    ? "-fx-text-fill: #FF4D6D; -fx-font-weight: bold; -fx-font-size: 12px;"
                    : "-fx-text-fill: #FB7185; -fx-font-weight: bold; -fx-font-size: 12px;");
        } else if (auctionDetail.isPaid()) {
            lblTime.setText("Đã quyết toán");
            lblTime.setStyle("-fx-text-fill: #34D399; -fx-font-weight: bold; -fx-font-size: 12px;");
        } else if (auctionDetail.isFinished()) {
            lblTime.setText("Đã đóng phiên");
            lblTime.setStyle("-fx-text-fill: #A5B4FC; -fx-font-weight: bold; -fx-font-size: 12px;");
        } else {
            lblTime.setText(FormatUtils.statusLabel(auctionDetail.status()));
        }
    }

    public void flashNewPrice(double newPrice, int bidCount) {
        lblPrice.setText(FormatUtils.formatVnd(newPrice));
        lblBids.setText(bidCount + " Bids");
        BidXAnimations.pricePopFlash(lblPrice);
    }

    public String getAuctionId() {
        if (auctionDetail != null) {
            return auctionDetail.auctionId();
        }
        return auctionSummary != null ? auctionSummary.auctionId() : "";
    }

    private void applyStatusBadge(String status) {
        lblStatusBadge.getStyleClass().removeAll(
                "badge-live", "badge-pending", "badge-finished", "badge-paid", "badge-rejected", "badge-muted");
        lblStatusBadge.getStyleClass().add(FormatUtils.statusBadgeStyleClass(status));
        if ("RUNNING".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status)) {
            lblStatusBadge.setText("● LIVE");
        } else {
            lblStatusBadge.setText(FormatUtils.statusLabel(status));
        }
    }

    private void updateFavoriteIcon(boolean fav) {
        if (btnFavorite != null) {
            btnFavorite.setGraphic(SvgIcons.create(SvgIcons.HEART, 14, fav ? "#F43F5E" : "#94A3B8"));
        }
    }

    @FXML
    public void handleToggleFavorite(ActionEvent event) {
        event.consume();
        ClientSession session = ViewManager.getClientSession();
        String id = getAuctionId();
        if (session != null && !id.isEmpty()) {
            boolean nowFav = session.toggleFavorite(id);
            updateFavoriteIcon(nowFav);
            BidXAnimations.popScale(btnFavorite, 1.32);
            if (homeController != null) {
                homeController.onFavoriteToggled();
            }
        }
    }

    @FXML
    public void handleCardClick(MouseEvent event) {
        if (event.getTarget() == btnFavorite) {
            return;
        }
        handleOpenRoom();
    }

    @FXML
    public void handleOpenRoom() {
        if (auctionDetail != null) {
            ViewManager.openAuctionRoom(auctionDetail);
        } else if (auctionSummary != null) {
            ViewManager.openAuctionRoom(auctionSummary);
        } else if (product != null) {
            AuctionSummary summary = new AuctionSummary(
                    "AUC-" + product.getId(),
                    product.getName(),
                    product.getPrice(),
                    500_000.0,
                    "NONE",
                    "RUNNING");
            ViewManager.openAuctionRoom(summary);
        }
    }
}
