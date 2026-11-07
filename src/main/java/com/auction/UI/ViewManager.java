package com.auction.ui;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

public class ViewManager {
    private static Scene mainScene;
    private static final Map<String, Parent> cache = new HashMap<>();

    public static void Initialize(Scene scene) {
        mainScene = scene;
    }

    public static void SwitchView(String fxmlName) {
        try {
            Parent root;
            if (cache.containsKey(fxmlName)) {
                root = cache.get(fxmlName);
            } else {
                String path = "/com/auction/fxml/"+fxmlName+".fxml";
                root = FXMLLoader.load(ViewManager.class.getResource(path));
                cache.put(fxmlName, root);
            }
            mainScene.setRoot(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
