package com.auction.UI;

import javafx.fxml.FXML;
import java.io.IOException;
import com.auction.sessions.ClientSession;
import javafx.application.Platform;
import java.util.Locale;
import network.AuctionClient;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;

public class HomeController implements ViewLifecycle, SessionAware, AuctionClient.AuctionEventListener {
    // ViewLifecycle cho biết khi nào Home xuất hiện hoặc bị ẩn
    // SessionAware nhận ClientSession từ ViewManager
    // AuctionEventListener nhận sự kiện từ SocketClient
    @FXML
    private Label eventLabel;
    // Hiển thị thông báo trên giao diện
    private ClientSession clientSession;
    // Giữ ClientSession được ViewManager truyền vào
    private boolean listenerRegistered = false;
    // Tránh đăng kí Listener nhiều lần
    private volatile boolean viewActive = false;

    // Check Home có đang hiển thị hay không. "volatile" giúp thread thấy giá trị
    // cập nhật của biến này
    @FXML
    private void SwitchToAuction(){
        ViewManager.SwitchView("Auction");
    }
    @Override
    public void setClientSession(ClientSession session) {
        this.clientSession = session;
    }

    @Override
    public void onViewShown() {
        if (clientSession == null || listenerRegistered) {
            return;
        }
        clientSession.getClient().addListener(this);
        // Thêm HomeController vào danh sách listener của AuctionClient
        listenerRegistered = true;
        viewActive = true;
    }

    @Override
    public void onViewHidden() {
        viewActive = false;
        if (clientSession != null && listenerRegistered) {
            clientSession.getClient().removeListener(this);
            listenerRegistered = false;
        }
    }

    @Override
    public void dispose() {
        onViewHidden();
        // Giải phóng Listener, không đóng Socket dùng chung
    }

    @Override
    public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
        Platform.runLater(() -> {
            if (!viewActive || eventLabel == null) {
                return;
            }
            eventLabel.setText(String.format(Locale.US,
                    "New bid on %s | Item: %s | Price: %,.2f | Bidder: %s",
                    auctionId,
                    itemId,
                    newPrice,
                    bidderId));
        });
        // AuctionClient listener thread nhận thông báo -> HomeController gọi
        // onBidUpdate để chạy Platform.runLater() -> JavaFX Application Thread cập nhật
        // event Label
    }

    @FXML
    public void SwitchToLogin() throws IOException {
        ViewManager.SwitchView("LogIn");
    }

    @Override
    public void onDisconnected(String reason) {
        Platform.runLater(() -> {
            if (!viewActive) {
                return;
            }
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Connection lost");
            alert.setHeaderText("Server connection was lost");
            alert.setContentText(reason + "\nPlease check your connection and restart the application");
            alert.showAndWait();
        });
    }
    // Sever bị tắt sẽ có AuctionClient.listenLoop() phát hiện kết nổi
    // Auction Client gọi onDisconnected(reason)
    // HomeController nhận callback
    // Platform.runLater() đưa các thao tác hiển thị Alert về JavaFX Application Thread để in ra màn hình
    private void showAuctionAlert(String title, String header, String message){
        Platform.runLater(() -> {
            if (!viewActive){
                return;
            }
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(message);
            alert.show();
        });
    }
    @Override
    public void onAuctionClosed(String auctionId, double finalPrice, String winnerId){
        String message = String.format(Locale.US,
            "Auction ID: %s\nFinal price: %,.2f\nWinner: %s",
            auctionId,
            finalPrice,
            winnerId
        );
        showAuctionAlert("Auction Ended", "The auction has ended", message);

    }
    @Override
    public void onWinnerNotification(String auctionId, double finalPrice, String itemId){
        String message = String.format(Locale.US,
            "Congratulations!\nYou won the auction for: %s\nFinal price: %,.2f",
            itemId,
            finalPrice
        );
        showAuctionAlert("Auction Won", "Congratulations! You are the winner", message);
    }
    @Override
    public void onSellerNotification(String auctionId, double finalPrice, String winnerId, String itemId){
        String message = String.format(Locale.US,
            "Item: %s\nAuction ID: %s\nFinal price: %,.2f\nWinner: %s",
            itemId,
            auctionId,
            finalPrice,
            winnerId
        );
        showAuctionAlert("Auction Complete", "Your auction has ended", message);
    }
}
