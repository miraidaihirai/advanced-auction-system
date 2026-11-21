package com.auction.ui;

import com.auction.session.ClientSession;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import network.AuctionProtocol;

public class App extends Application {

  private static final String SERVER_HOST = AuctionProtocol.DEFAULT_HOST;
  private static final int SERVER_PORT = AuctionProtocol.DEFAULT_PORT;

  private ClientSession clientSession;
  private AuctionNotificationListener notificationListener;

  @Override
  public void start(Stage primaryStage) {
    clientSession = new ClientSession(SERVER_HOST, SERVER_PORT);

    notificationListener = new AuctionNotificationListener();
    clientSession.getClient().addListener(notificationListener);

    // Khởi tạo Scene với kích thước chuẩn
    Scene scene = new Scene(new StackPane(), 1240, 780);

    ViewManager.Initialize(scene, clientSession);
    ViewManager.SwitchView("LogIn");

    primaryStage.setScene(scene);
    primaryStage.setTitle("Hệ thống Đấu giá Trực tuyến - Realtime Auction");
    primaryStage.setMinWidth(1080);
    primaryStage.setMinHeight(680);
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
