package com.auction.ui;
import java.io.IOException;
import java.time.LocalDateTime;

import com.auction.item.Item;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class addProductCard{
    @FXML
    public static void addNewAuctionCard(FlowPane productGridContainer, Item product, LocalDateTime endTime){
        if (product == null || productGridContainer == null) return;

        try{
            // 1. Nạp file FXML của một khung sản phẩm lẻ (product_card.fxml)
            FXMLLoader loader = new FXMLLoader(addProductCard.class.getResource("/com/auction/fxml/ProductCard.fxml"));
            VBox productCard = loader.load();
            // 2. Lấy Controller của thẻ sản phẩm vừa nạp
            ProductCardController cardController = loader.getController();
            // 3. Truyền dữ liệu vào card
            cardController.setData(product, "0", endTime);
            // 4. Đẩy card mới lên vị trí đầu tiên (index = 0) của FlowPane
            productGridContainer.getChildren().add(0,productCard);
        }catch(IOException e){
            e.printStackTrace();
            System.err.println("Không thể khởi tạo thẻ sản phẩm.");
        }
    }
}
