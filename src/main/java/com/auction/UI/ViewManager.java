package com.auction.ui;

import com.auction.session.ClientSession;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/**
 * Quản lý việc chuyển đổi giữa các màn hình (FXML Views). Lưu trữ ClientSession dùng chung và tích
 * hợp ThemeManager để áp dụng theme mỗi lần chuyển view.
 */
public class ViewManager {

  private static Scene mainScene;
  private static ClientSession clientSession;
  // Lưu ClientSession dùng chung, mỗi màn hình có thể nhận cùng một Socket Client
  private static final Map<String, ViewEntry> cache = new HashMap<>();

  public static void Initialize(Scene scene) {
    Initialize(scene, null);
  }

  public static void Initialize(Scene scene, ClientSession session) {
    mainScene = scene;
    clientSession = session;
    // Đăng ký scene với ThemeManager
    ThemeManager.getInstance().setScene(scene);
  }

  public static ClientSession getClientSession() {
    return clientSession;
  }

  /**
   * Xóa sạch cache các màn hình FXML. Sử dụng khi đăng xuất để đảm bảo khi đăng nhập tài khoản mới,
   * mọi màn hình (Home, AuctionRoom, AuctionRequestList) được nạp lại hoàn toàn mới, không bị lưu
   * vết trạng thái của tài khoản trước đó.
   */
  public static void clearCache() {
    cache.clear();
  }

  public static Object getController(String fxmlName) {
    ViewEntry entry = cache.get(fxmlName);
    return (entry != null) ? entry.controller : null;
  }

  public static void openAuctionRoom(network.AuctionSummary auction) {
    SwitchView("AuctionRoom");
    Object controller = getController("AuctionRoom");
    if (controller instanceof AuctionRoomController roomController) {
      roomController.loadAuction(auction);
    }
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

      if (entry.controller instanceof SessionAware aware && clientSession != null) {
        aware.setClientSession(clientSession);
      }
      // Kiểm tra Controller có triển khai SessionAware không, nếu có, ta lấy nó dưới kiểu
      // SessionAware
      // Gọi setClientSession(clientSession) để truyền kết nối Socket dùng chung

      mainScene.setRoot(entry.root);

      // Áp dụng theme hiện tại sau mỗi lần chuyển view
      ThemeManager.getInstance().applyTo(mainScene);

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
