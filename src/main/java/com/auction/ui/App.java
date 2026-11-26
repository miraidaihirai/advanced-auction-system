package com.auction.ui;

import com.auction.session.ClientSession;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
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

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double maxSceneW = Math.max(920, visualBounds.getWidth() - 32);
        double maxSceneH = Math.max(560, visualBounds.getHeight() - 62);
        double initW = Math.min(1320, maxSceneW);
        double initH = Math.min(800, maxSceneH);

        Scene scene = new Scene(new StackPane(), initW, initH, Color.web("#0A0E17"));

        ViewManager.Initialize(scene, clientSession);
        ViewManager.SwitchView("LogIn");

        Image appIcon = ProductImageService.getBrandLogo(64);
        if (appIcon != null) {
            primaryStage.getIcons().add(appIcon);
        }

        primaryStage.setScene(scene);
        primaryStage.setTitle("BidX — Sàn Đấu Giá Tài Sản Cao Cấp Trực Tuyến");
        primaryStage.setMinWidth(Math.min(960, visualBounds.getWidth()));
        primaryStage.setMinHeight(Math.min(580, visualBounds.getHeight()));
        primaryStage.show();

        // Đảm bảo cửa sổ luôn nằm gọn 100% trong vùng hiển thị màn hình (không tràn 4 cạnh trên laptop 125%-200% DPI)
        if (primaryStage.getWidth() > visualBounds.getWidth()) {
            primaryStage.setWidth(visualBounds.getWidth());
        }
        if (primaryStage.getHeight() > visualBounds.getHeight()) {
            primaryStage.setHeight(visualBounds.getHeight());
        }
        double targetX = visualBounds.getMinX() + (visualBounds.getWidth() - primaryStage.getWidth()) / 2.0;
        double targetY = visualBounds.getMinY() + (visualBounds.getHeight() - primaryStage.getHeight()) / 2.0;
        primaryStage.setX(Math.max(visualBounds.getMinX(), targetX));
        primaryStage.setY(Math.max(visualBounds.getMinY(), targetY));
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
