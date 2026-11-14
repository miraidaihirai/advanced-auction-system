package com.auction.UI;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.auction.sessions.ClientSession;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

public class ViewManager {
    private static Scene mainScene;
    private static final Map<String, ViewEntry> cache = new HashMap<>();
    private static String currentView;
    private static ClientSession clientSession;

    // Xác định màn hình hiện tại
    private static class ViewEntry {
        private final Parent root;
        // Giao diện gốc tải từ FXML
        private final Object controller;

        // Controller điều chỉnh giao diện đó
        // Không phải Controller nào cũng triển khai ViewLifecycle
        public ViewEntry(Parent root, Object controller) {
            this.root = root;
            this.controller = controller;
        }

        public Parent getRoot() {
            return root;
        }

        public Object getController() {
            return controller;
        }
    }

    public static void Initialize(Scene scene, ClientSession session) {
        mainScene = scene;
        clientSession = session;
    }

    public static void SwitchView(String fxmlName) {
        try {
            if (mainScene == null) {
                throw new IllegalStateException("ViewManager has not been initialized");
            }
            if (fxmlName.equals(currentView)) {
                return;
            }
            // Tránh chuyển Home -> Home

            ViewEntry newEntry = cache.get(fxmlName);
            // Nếu màn hình được tải trước đó, chúng ta lấy lại ViewEntry cũ
            if (newEntry == null) {
                String path = "/com/auction/fxml/" + fxmlName + ".fxml";
                var resource = ViewManager.class.getResource(path);
                if (resource == null) {
                    throw new IOException("FXML file not found: " + path);
                }
                FXMLLoader loader = new FXMLLoader(resource);
                // giữ lại loader để truy cập Controller
                Parent root = loader.load();
                // JavaFX đọc FXML, tạo các thành phần giao diện và Controller tương ứng
                Object controller = loader.getController();
                // Lấy controller tham chiếu đến đối tượng HomeController được tạo
                if (controller instanceof SessionAware aware) {
                    aware.setClientSession(clientSession);
                }
                // Kiểm tra xem có cần nhận Session không
                newEntry = new ViewEntry(root, controller);
                // Đóng gói vào ViewEntry

                cache.put(fxmlName, newEntry);
            }
            ViewEntry oldEntry = cache.get(currentView);
            // Lấy controller của màn hình cũ
            if (oldEntry != null && oldEntry.getController() instanceof ViewLifecycle lifecycle) {
                // Cần Controller có trong cache và có triển khai ViewLifecycle
                // Sử dụng pattern matching nên không cần "instanceof Class"
                lifecycle.onViewHidden();
            }
            mainScene.setRoot(newEntry.getRoot());
            currentView = fxmlName;
            // Cập nhật giao diện mới
            if (newEntry.getController() instanceof ViewLifecycle lifecycle) {
                lifecycle.onViewShown();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
