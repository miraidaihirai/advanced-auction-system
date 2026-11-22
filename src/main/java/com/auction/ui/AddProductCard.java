package com.auction.ui;

import java.io.IOException;
import java.time.LocalDateTime;

import com.auction.item.Item;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import network.AuctionDetail;
import network.AuctionSummary;

public class AddProductCard {

    public static void addNewAuctionCard(FlowPane productGridContainer, Item product, LocalDateTime endTime,
            HomeController homeController) {
        if (product == null || productGridContainer == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(AddProductCard.class.getResource("/com/auction/fxml/ProductCard.fxml"));
            VBox productCard = loader.load();
            ProductCardController cardController = loader.getController();
            cardController.setData(product, "0", endTime);
            cardController.setHomeController(homeController);
            productGridContainer.getChildren().add(0, productCard);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addNewAuctionCard(FlowPane productGridContainer, Item product, LocalDateTime endTime) {
        addNewAuctionCard(productGridContainer, product, endTime, null);
    }

    public static VBox addServerAuctionCard(FlowPane productGridContainer, AuctionSummary auction) {
        if (productGridContainer == null || auction == null) {
            return null;
        }
        try {
            FXMLLoader loader = new FXMLLoader(AddProductCard.class.getResource("/com/auction/fxml/ProductCard.fxml"));
            VBox productCard = loader.load();
            ProductCardController cardController = loader.getController();
            cardController.setAuctionData(auction);
            productGridContainer.getChildren().add(productCard);
            return productCard;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ProductCardController addDetailAuctionCard(
            FlowPane productGridContainer,
            AuctionDetail detail,
            boolean isFavorite,
            boolean isBidderRole,
            HomeController homeController,
            int index) {
        if (productGridContainer == null || detail == null) {
            return null;
        }
        try {
            FXMLLoader loader = new FXMLLoader(AddProductCard.class.getResource("/com/auction/fxml/ProductCard.fxml"));
            VBox productCard = loader.load();
            ProductCardController cardController = loader.getController();
            cardController.setHomeController(homeController);
            cardController.setAuctionDetail(detail, isFavorite, isBidderRole);
            productGridContainer.getChildren().add(productCard);
            BidXAnimations.fadeSlideIn(productCard, Math.min(index * 45.0, 360.0), 16);
            return cardController;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
