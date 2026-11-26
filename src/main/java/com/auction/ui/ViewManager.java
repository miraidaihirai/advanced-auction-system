package com.auction.ui;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.auction.session.ClientSession;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import network.AuctionDetail;
import network.AuctionSummary;
import network.UserProfile;

/**
 * Bộ điều phối màn hình trung tâm của BidX.
 * Bao bọc toàn bộ ứng dụng trong 3 lớp:
 * 1. viewLayer: màn hình nghiệp vụ hiện tại (Login / Register / Bidder Home / Seller Studio / Admin Center / Auction Room)
 * 2. fxLayer: lớp hiệu ứng pháo giấy / chúc mừng (mouse-transparent)
 * 3. toastLayer: lớp thông báo glassmorphism trượt nổi góc trên bên phải
 */
public class ViewManager {

    private static Scene mainScene;
    private static ClientSession clientSession;

    private static StackPane shellRoot;
    private static StackPane viewLayer;
    private static Pane fxLayer;
    private static VBox toastLayer;

    private static final Map<String, ViewEntry> cache = new HashMap<>();

    public static void Initialize(Scene scene) {
        Initialize(scene, null);
    }

    public static void Initialize(Scene scene, ClientSession session) {
        mainScene = scene;
        clientSession = session;

        viewLayer = new StackPane();
        viewLayer.setMinSize(0, 0);
        viewLayer.setAlignment(Pos.TOP_LEFT);
        viewLayer.setStyle("-fx-background-color: #0A0E17;");
        Rectangle viewClip = new Rectangle();
        viewClip.widthProperty().bind(viewLayer.widthProperty());
        viewClip.heightProperty().bind(viewLayer.heightProperty());
        viewLayer.setClip(viewClip);

        fxLayer = new Pane();
        fxLayer.setMinSize(0, 0);
        fxLayer.setMouseTransparent(true);
        fxLayer.setPickOnBounds(false);

        toastLayer = new VBox(10);
        toastLayer.setAlignment(Pos.TOP_RIGHT);
        toastLayer.setPickOnBounds(false);
        toastLayer.setPadding(new Insets(18, 22, 18, 18));
        toastLayer.setMaxWidth(400);
        StackPane.setAlignment(toastLayer, Pos.TOP_RIGHT);

        shellRoot = new StackPane(viewLayer, fxLayer, toastLayer);
        shellRoot.setMinSize(0, 0);
        shellRoot.setStyle("-fx-background-color: #0A0E17;");

        NotificationManager.attachToastContainer(toastLayer);
        mainScene.setRoot(shellRoot);
        ThemeManager.initialize(mainScene, shellRoot);
    }

    public static ClientSession getClientSession() {
        return clientSession;
    }

    public static Object getController(String fxmlName) {
        ViewEntry entry = cache.get(fxmlName);
        return (entry != null) ? entry.controller : null;
    }

    public static void triggerCelebration() {
        Runnable task = () -> BidXAnimations.playCelebrationBurst(fxLayer);
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    public static void triggerBidShockwave(String labelText) {
        Runnable task = () -> BidXAnimations.playBidShockwave(fxLayer, labelText);
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    public static void openAuctionRoom(AuctionSummary auction) {
        if (auction == null) {
            return;
        }
        AuctionDetail cached = clientSession != null ? clientSession.getCachedAuction(auction.auctionId()) : null;
        if (cached != null) {
            openAuctionRoom(cached);
            return;
        }
        SwitchView("AuctionRoom");
        Object controller = getController("AuctionRoom");
        if (controller instanceof AuctionRoomController roomController) {
            roomController.loadAuction(auction);
        }
    }

    public static void openAuctionRoom(AuctionDetail detail) {
        if (detail == null) {
            return;
        }
        SwitchView("AuctionRoom");
        Object controller = getController("AuctionRoom");
        if (controller instanceof AuctionRoomController roomController) {
            roomController.loadAuctionDetail(detail);
        }
    }

    /**
     * Điều hướng tự động theo đúng vai trò tài khoản đăng nhập:
     * - BIDDER -> Sàn đấu giá (Home)
     * - SELLER -> Seller Studio (chỉ bán & gửi yêu cầu mở phòng + nhận tiền về ví)
     * - ADMIN  -> Admin Command Center (duyệt yêu cầu của Seller & giám sát toàn sàn)
     */
    public static void routeByRole(UserProfile profile) {
        if (profile == null) {
            SwitchView("Home");
            return;
        }
        if (profile.isAdmin()) {
            SwitchView("AdminDashboard");
        } else if (profile.isSeller()) {
            SwitchView("SellerDashboard");
        } else {
            SwitchView("Home");
        }
    }

    public static void returnToRoleHome() {
        UserProfile profile = clientSession != null ? clientSession.getCurrentUser() : null;
        routeByRole(profile);
    }

    /**
     * Xóa cache các dashboard khi đăng xuất để tài khoản tiếp theo luôn nạp giao diện sạch mới.
     */
    public static void clearSessionViews() {
        for (String name : java.util.List.of("Home", "SellerDashboard", "AdminDashboard", "AuctionRoom")) {
            ViewEntry entry = cache.remove(name);
            if (entry != null && entry.controller instanceof SessionAware aware) {
                try {
                    aware.detachSession();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void SwitchView(String fxmlName) {
        if (mainScene == null || viewLayer == null) {
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
                if (root instanceof Region region) {
                    region.setMinSize(0, 0);
                    region.prefWidthProperty().bind(viewLayer.widthProperty());
                    region.prefHeightProperty().bind(viewLayer.heightProperty());
                    region.maxWidthProperty().bind(viewLayer.widthProperty());
                    region.maxHeightProperty().bind(viewLayer.heightProperty());
                }

                entry = new ViewEntry(root, loader.getController());
                cache.put(fxmlName, entry);
            }

            if (entry.controller instanceof SessionAware aware && clientSession != null) {
                aware.setClientSession(clientSession);
            }

            viewLayer.getChildren().setAll(entry.root);
            BidXAnimations.fadeSlideIn(entry.root, 0, 12);

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
