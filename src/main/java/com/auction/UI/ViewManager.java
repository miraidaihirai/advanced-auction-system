package com.auction.ui;

import java.net.URL;
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
                String path = "/com/auction/fxml/" + fxmlName + ".fxml";
                URL resource = ViewManager.class.getResource(path);
                if (resource == null) {
                    System.err.println("[ViewManager] Không tìm thấy file FXML: " + path);
                    return;
                }
                root = FXMLLoader.load(resource);
                cache.put(fxmlName, root);
            }
            if (mainScene != null && root != null) {
                mainScene.setRoot(root);
            } else {
                System.err.println("[ViewManager] mainScene hoặc root bị null! mainScene=" + mainScene + ", root=" + root);
            }
        } catch (Exception e) {
            System.err.println("[ViewManager] Lỗi khi chuyển sang view '" + fxmlName + "':");
            e.printStackTrace();
        }
    }
}
