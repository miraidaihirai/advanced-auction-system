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

    @Override
    public void start(Stage primaryStage) {

        clientSession = new ClientSession(SERVER_HOST, SERVER_PORT);

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
            clientSession.disconnect();
        }
    }
}
