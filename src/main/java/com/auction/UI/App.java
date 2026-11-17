package com.auction.ui;

import com.auction.session.ClientSession;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;
// Cần đổi "localhost" thành IP LAN của máy chạy Server trong demo
    private ClientSession clientSession;
    private AuctionNotificationListener notificationListener;

    @Override
    public void start(Stage primaryStage) {

        clientSession = new ClientSession(SERVER_HOST, SERVER_PORT);

        notificationListener = new AuctionNotificationListener();
        clientSession.getClient().addListener(notificationListener);
        // Khởi tạo và đăng ký listener vào SocketClient để dùng chung
        Scene scene = new Scene(new StackPane(), 700, 500);

        ViewManager.Initialize(scene, clientSession);
        ViewManager.SwitchView("LogIn");

        primaryStage.setScene(scene);
        primaryStage.setTitle("Hệ thống đấu giá trực tuyến");
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (clientSession != null) {
            if (notificationListener != null){
                clientSession.getClient().removeListener(notificationListener);
            }
            clientSession.disconnect();
        }
    }
    // Khi người dùng chủ động đóng ứng dụng, sẽ không xuất hiện alert mất kết nối
}