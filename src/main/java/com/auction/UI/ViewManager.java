package com.auction.ui;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.auction.session.ClientSession;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

public class ViewManager {

    private static Scene mainScene;
    private static ClientSession clientSession;
    // Lưu ClientSession dùng chung, mỗi màn hình có thể nhận cùng một Socket Client thay vì tạo một kết nối riêng
    private static final Map<String, ViewEntry> cache = new HashMap<>();

    public static void Initialize(Scene scene) {
        Initialize(scene, null);
    }

    public static void Initialize(Scene scene, ClientSession session) {
        mainScene = scene;
        clientSession = session;
    }
    // Khởi tạo ViewManager với Session
    public static void SwitchView(String fxmlName) {
        if (mainScene == null) {
            System.err.println("[ViewManager] mainScene chưa được khởi tạo!");
            return;
        }

        try {
            ViewEntry entry = cache.get(fxmlName);

            if (entry == null) {
                String path = "/com/auction/fxml/" + fxmlName + ".fxml";
                URL resource = ViewManager.class.getResource(path);

                if (resource == null) {
                    System.err.println("[ViewManager] Không tìm thấy file FXML: " + path);
                    return;
                }

                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                
                entry = new ViewEntry(root, loader.getController());
                // Lấy controller từ FXMLLoader
                cache.put(fxmlName, entry);
            }

            if (entry.controller instanceof SessionAware aware
                    && clientSession != null) {
                aware.setClientSession(clientSession);
            }
            // Kiểm tra Controller có triển khai SessionAware không, nếu có , ta lấy nó dưới kiểu SessionAware
            // Gọi setClientSession(clientSession) để truyền kết nối Socket dùng chung

            mainScene.setRoot(entry.root);

        } catch (Exception e) {
            System.err.println("[ViewManager] Lỗi khi chuyển sang view '" + fxmlName + "':");
            e.printStackTrace();
        }
    }

    private static class ViewEntry {
        private final Parent root;
        private final Object controller;

        private ViewEntry(Parent root, Object controller) {
            this.root = root;
            this.controller = controller;
        }
    }
}