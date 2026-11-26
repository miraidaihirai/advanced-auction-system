package com.auction.ui;

import com.auction.session.ClientSession;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class App extends Application {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;

    private ClientSession clientSession;
    private AuctionNotificationListener notificationListener;

    @Override
    public void start(Stage primaryStage) {
        clientSession = new ClientSession(SERVER_HOST, SERVER_PORT);

        notificationListener = new AuctionNotificationListener();
        clientSession.getClient().addListener(notificationListener);

        Scene scene = new Scene(new StackPane(), 1360, 820, Color.web("#0A0E17"));

        ViewManager.Initialize(scene, clientSession);
        ViewManager.SwitchView("LogIn");

        Image appIcon = ProductImageService.getBrandLogo(64);
        if (appIcon != null) {
            primaryStage.getIcons().add(appIcon);
        }

        primaryStage.setScene(scene);
        primaryStage.setTitle("BidX — Sàn Đấu Giá Tài Sản Cao Cấp Trực Tuyến");
        primaryStage.setMinWidth(1140);
        primaryStage.setMinHeight(720);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (clientSession != null) {
            if (notificationListener != null) {
                clientSession.getClient().removeListener(notificationListener);
            }
            clientSession.disconnect();
        }
    }
}
