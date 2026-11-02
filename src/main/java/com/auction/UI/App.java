package com.auction.UI;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {
    private static Scene scene;

    @Override
    public void start(Stage primaryStage) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("/com/auction/LogIn.fxml"));

        scene = new Scene(root, 700, 500);

        ViewManager.Initialize(scene);

        primaryStage.setScene(scene);
        primaryStage.setTitle("Online auction system");
        primaryStage.show();
    }
}

