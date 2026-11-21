package com.auction.ui;

import com.auction.item.Item;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import network.AuctionDetail;
import network.AuctionSummary;

public class ProductCardController {
  @FXML private Label lblCategory;

  @FXML private Label lblId;

  @FXML private ImageView imgProduct;

  @FXML private Label lblName;

  @FXML private Label lblPrice;

  @FXML private Button btnBid;

  @FXML private Label lblTime;

  @FXML private Label lblBids;

  @FXML private VBox card;

  private Item Product;
  private AuctionSummary auctionSummary;
  private AuctionDetail auctionDetail;
  private HomeController homeController;

  private final TimelineCard timeline = new TimelineCard();
  private final NumberFormat currencyFormat =
      NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));

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

    if (imgProduct != null) {
      ProductImageService.loadIntoImageView(
          imgProduct,
          "ITEM-" + product.getId(),
          product.getName(),
          product.getImagePath(),
          232,
          135);
    }

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
      if ("RUNNING".equalsIgnoreCase(auction.status())
          || "ACTIVE".equalsIgnoreCase(auction.status())) {
        lblTime.setText("Đang diễn ra");
      } else {
        lblTime.setText("Đã kết thúc");
        lblTime.setStyle("-fx-text-fill: #9ca3af;");
      }
    }

    if (imgProduct != null) {
      ProductImageService.loadIntoImageView(
          imgProduct, auction.auctionId(), auction.itemId(), null, 232, 135);
    }

    if (btnBid != null) {
      btnBid.setDisable(false);
    }
  }

  public void setAuctionDetail(AuctionDetail detail) {
    if (detail == null) return;
    this.auctionDetail = detail;
    this.auctionSummary = detail.toSummary();
    currencyFormat.setMaximumFractionDigits(0);

    if (lblId != null) lblId.setText(detail.auctionId());
    if (lblName != null) lblName.setText(detail.itemName());
    if (lblCategory != null) {
      lblCategory.setText(detectCategoryFromMeta(detail.category(), detail.itemName()));
    }
    if (lblPrice != null) lblPrice.setText(currencyFormat.format(detail.currentPrice()) + " đ");

    String highestBidder = detail.highestBidderId();
    if (lblBids != null) {
      if (highestBidder == null || highestBidder.equalsIgnoreCase("NONE")) {
        lblBids.setText(detail.bidCount() + " lượt • Chưa có giá");
      } else {
        lblBids.setText("Dẫn đầu: " + highestBidder);
      }
    }

    if (lblTime != null) {
      timeline.stop();
      if (detail.isRunning() && detail.endTimeMillis() > System.currentTimeMillis()) {
        LocalDateTime endTime =
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(detail.endTimeMillis()), ZoneId.systemDefault());
        timeline.startCountdown(endTime, lblTime);
      } else if (detail.isPaid()) {
        lblTime.setText("Đã thanh toán");
        lblTime.setStyle("-fx-text-fill: #10B981;");
      } else {
        lblTime.setText("Đã kết thúc");
        lblTime.setStyle("-fx-text-fill: #9ca3af;");
      }
    }

    if (imgProduct != null) {
      ProductImageService.loadIntoImageView(
          imgProduct, detail.auctionId(), detail.itemName(), detail.imageKey(), 232, 135);
    }

    if (btnBid != null) {
      btnBid.setDisable(false);
      btnBid.setText(detail.isRunning() ? "VÀO ĐẤU GIÁ" : "XEM KẾT QUẢ");
    }
  }

  private String detectCategoryFromMeta(String category, String name) {
    if (category != null) {
      switch (category.toUpperCase(Locale.ROOT)) {
        case "WATCHES" -> {
          return "[ ĐỒNG HỒ CỔ ]";
        }
        case "ELECTRONICS" -> {
          return "[ ĐỒ ĐIỆN TỬ ]";
        }
        case "ART" -> {
          return "[ NGHỆ THUẬT ]";
        }
        case "VEHICLES" -> {
          return "[ XE CỘ ]";
        }
        default -> {}
      }
    }
    return detectCategory(name);
  }

  private String detectCategory(String name) {
    if (name == null) return "[ ĐỒNG HỒ CỔ ]";
    String lower = name.toLowerCase();
    if (lower.contains("đồng hồ") || lower.contains("watch")) {
      return "[ ĐỒNG HỒ CỔ ]";
    } else if (lower.contains("dell")
        || lower.contains("laptop")
        || lower.contains("máy ảnh")
        || lower.contains("camera")) {
      return "[ ĐỒ ĐIỆN TỬ ]";
    } else if (lower.contains("bình")
        || lower.contains("vase")
        || lower.contains("tranh")
        || lower.contains("art")) {
      return "[ NGHỆ THUẬT ]";
    } else if (lower.contains("xe") || lower.contains("car") || lower.contains("auto")) {
      return "[ XE CỘ ]";
    }
    return "[ SƯU TẦM ]";
  }

  @FXML
  public void handleOpenRoom() {
    if (auctionDetail != null) {
      ViewManager.openAuctionRoom(auctionDetail);
    } else if (auctionSummary != null) {
      ViewManager.openAuctionRoom(auctionSummary);
    } else if (Product != null) {
      AuctionSummary summary =
          new AuctionSummary(
              "AUC-" + Product.getId(),
              Product.getName(),
              Product.getPrice(),
              500_000.0,
              "NONE",
              "ACTIVE");
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
