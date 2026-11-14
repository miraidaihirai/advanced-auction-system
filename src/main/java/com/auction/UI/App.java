package com.auction.UI;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.layout.StackPane;
import java.io.IOException;

import com.auction.sessions.ClientSession;

public class App extends Application {
    private ClientSession clientSession;
    private static final String SEVER_HOST = "localhost";
    private static final int SEVER_PORT = 8888;

    @Override
    public void start(Stage primaryStage) throws IOException {
        clientSession = new ClientSession(SEVER_HOST, SEVER_PORT);
        // Tạo một AuctionClient trong ClientSession, Socket chưa kết nối với Server
        Scene scene = new Scene(new StackPane(), 700, 500);
        // Do JavaFX cần một Root Node để tạo Scene
        // Ta tạo một StackPane trống, sau đó để ViewManager thay thế nó bằng Login.fxml
        ViewManager.Initialize(scene, clientSession);
        // ViewManager nhận thêm đối tượng ClientSesion cho phép cung cấp cho các Controller sử dụng Socket
        primaryStage.setScene(scene);
        primaryStage.setTitle("Online Auction System");
        ViewManager.SwitchView("LogIn");
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (clientSession != null) {
            clientSession.disconnect();
        }
    }
    // Đóng Socket khi yngws dụng kết thúc
}
