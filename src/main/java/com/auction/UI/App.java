package com.auction.ui;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    private static Scene scene;

    @Override
    public void start(Stage primaryStage) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("/com/auction/fxml/LogIn.fxml"));

        scene = new Scene(root, 700, 500);

        ViewManager.Initialize(scene);

        primaryStage.setScene(scene);
        primaryStage.setTitle("Hệ thống đấu giá trực tuyến");
        primaryStage.show();
    }
}
