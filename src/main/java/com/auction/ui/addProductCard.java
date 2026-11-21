package com.auction.ui;

import com.auction.item.Item;
import java.io.IOException;
import java.time.LocalDateTime;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import network.AuctionSummary;

public class addProductCard {
  @FXML
  public static void addNewAuctionCard(
      FlowPane productGridContainer,
      Item product,
      LocalDateTime endTime,
      HomeController homeController) {
    if (product == null || productGridContainer == null) return;

    try {
      // 1. Nạp file FXML của một khung sản phẩm lẻ (ProductCard.fxml)
      FXMLLoader loader =
          new FXMLLoader(addProductCard.class.getResource("/com/auction/fxml/ProductCard.fxml"));
      VBox productCard = loader.load();

      ProductCardController cardController = loader.getController();

      cardController.setData(product, "0", endTime);
      cardController.setHomeController(homeController);
      // 4. Đẩy card mới lên vị trí đầu tiên (index = 0) của FlowPane
      productGridContainer.getChildren().add(0, productCard);
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Không thể khởi tạo thẻ sản phẩm.");
    }
  }

  public static void addNewAuctionCard(
      FlowPane productGridContainer, Item product, LocalDateTime endTime) {
    addNewAuctionCard(productGridContainer, product, endTime, null);
  }

  public static VBox addServerAuctionCard(FlowPane productGridContainer, AuctionSummary auction) {
    if (productGridContainer == null || auction == null) {
      return null;
    }

    try {
      FXMLLoader loader =
          new FXMLLoader(addProductCard.class.getResource("/com/auction/fxml/ProductCard.fxml"));

      VBox productCard = loader.load();

      ProductCardController cardController = loader.getController();

      cardController.setAuctionData(auction);

      productGridContainer.getChildren().add(productCard);

      return productCard;

    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Không thể tạo thẻ đấu giá từ dữ liệu Server.");
      return null;
    }
  }
}
