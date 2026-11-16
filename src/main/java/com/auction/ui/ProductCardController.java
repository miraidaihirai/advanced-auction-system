package com.auction.ui;

import java.time.LocalDateTime;

import com.auction.item.Item;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

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
        String price = String.valueOf(product.getPrice());
        lblPrice.setText(price);
        lblBids.setText(bids);
        this.Product = product;
        
        //thiết lập thời gian đếm ngược
        timeline.startCountdown(endTime, lblTime);

    }
    //Hàm xử lý khi click chuột vào thẻ sản phẩm
    public void handleCardClick(){
        card.setOnMouseClicked(event->{
            //truyền vào tên của phòng đấu giá
            navigateToAuctionRoom("NameAuctionRoom");
        });
    }
    
    @FXML 
    //Chuyển hướng tới phòng đấu giá
    private void navigateToAuctionRoom(String idAuctionRoom){
        System.out.println("Đang mở phiên đấu giá của sản phẩm ID:"+idAuctionRoom);
        try{
            ViewManager.SwitchView(idAuctionRoom);
        }catch(Exception e){
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
