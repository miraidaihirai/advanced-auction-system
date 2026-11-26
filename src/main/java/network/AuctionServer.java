package network;

import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.Admin;
import com.auction.model.User;
import com.auction.observer.AuctionObserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Socket Multi-threaded Server cho hệ thống đấu giá trực tuyến.
 * - Lắng nghe kết nối từ các Client và cấp phát mỗi Client một Thread riêng (ClientHandler).
 * - Quản lý vòng đời Server và danh sách Client kết nối đồng thời.
 * - Tự động nạp và lưu trữ dữ liệu (Serialization / Deserialization) tránh mất dữ liệu khi khởi động lại server.
 */
public class AuctionServer implements AuctionObserver {

    public static final int DEFAULT_PORT = 8888;
    public static final String DEFAULT_DATA_FILE = AuctionManager.DEFAULT_STORAGE_PATH;

    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService clientThreadPool;
    private final Set<ClientHandler> activeClients = ConcurrentHashMap.newKeySet();
    private volatile boolean running = false;
    private int defaultClientTimeoutMs = ClientHandler.DEFAULT_SO_TIMEOUT_MS;

    private static final AtomicInteger clientThreadCounter = new AtomicInteger(0);

    public AuctionServer() {
        this(DEFAULT_PORT);
    }

    public AuctionServer(int port) {
        this.port = port;
        this.clientThreadPool = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "socket-client-worker-" + clientThreadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Khởi động server (đồng bộ - lặp accept kết nối).
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        AuctionManager.getInstance().addGlobalObserver(this);
        System.out.println("==================================================");
        System.out.printf(" AUCTION SOCKET SERVER STARTED ON PORT %d%n", port);
        System.out.println("==================================================");

        try {
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.printf("[AuctionServer] New connection from: %s%n", clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(clientSocket, this, defaultClientTimeoutMs);
                activeClients.add(handler);
                clientThreadPool.submit(handler);
            }
        } catch (IOException e) {
            if (running) {
                System.err.printf("[AuctionServer] Error accepting connection: %s%n", e.getMessage());
            }
        } finally {
            stop();
        }
    }

    /**
     * Khởi động server bất đồng bộ (chạy trên thread riêng, hữu ích cho testing / nhúng).
     */
    public void startAsync() {
        Thread serverThread = new Thread(() -> {
            try {
                start();
            } catch (IOException e) {
                if (running) {
                    System.err.printf("[AuctionServer] Server stopped with error: %s%n", e.getMessage());
                }
            }
        }, "auction-server-main");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /**
     * Dừng server và giải phóng toàn bộ kết nối.
     * Tự động lưu trạng thái dữ liệu ra file nếu autoSave đang bật.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        System.out.println("[AuctionServer] Stopping server...");

        AuctionManager manager = AuctionManager.getInstance();
        manager.removeGlobalObserver(this);
        if (manager.isAutoSave()) {
            manager.saveState();
        }

        // Đóng server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }

        // Đóng toàn bộ ClientHandlers (snapshot để tránh ConcurrentModification)
        for (ClientHandler client : new java.util.ArrayList<>(activeClients)) {
            client.close();
        }
        activeClients.clear();

        clientThreadPool.shutdownNow();
        try {
            clientThreadPool.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[AuctionServer] Server stopped successfully.");
    }

    public void removeClient(ClientHandler handler) {
        activeClients.remove(handler);
    }

    public int getConnectedClientCount() {
        return activeClients.size();
    }

    public Set<ClientHandler> getActiveClients() {
        return Collections.unmodifiableSet(activeClients);
    }

    public ClientHandler findClientByUserId(String userId) {
        if (userId == null) {
            return null;
        }
        for (ClientHandler client : activeClients) {
            if (client.getCurrentUser() != null && userId.equals(client.getCurrentUser().getId())) {
                return client;
            }
        }
        return null;
    }

    @Override
    public void onNewBid(Auction auction, BidTransaction bid) {
        // Có thể bổ sung logging giám sát cấp Server nếu cần
    }

    @Override
    public void onAuctionClosed(Auction auction) {
        AuctionManager.getInstance().settleAuctionFunds(auction);
        notifyAuctionFinished(auction);
    }

    @Override
    public void onAuctionPaid(Auction auction) {
        AuctionManager.getInstance().settleAuctionFunds(auction);
        notifyAuctionPaid(auction);
    }

    /**
     * Bắn thông báo riêng đến Seller và Winner khi phiên đóng nếu họ đang kết nối socket
     * nhưng chưa kịp / không trực tiếp JOIN phòng đấu giá.
     */
    public void notifyAuctionFinished(Auction auction) {
        if (auction == null) {
            return;
        }
        String winnerId = auction.getHighestBidderId();
        String sellerId = auction.getSellerId();
        String winner = winnerId != null ? winnerId : "NONE";

        for (ClientHandler client : activeClients) {
            User user = client.getCurrentUser();
            if (user == null) {
                continue;
            }
            if (!client.isSubscribed(auction.getId())) {
                if (winnerId != null && winnerId.equals(user.getId())) {
                    client.sendMessage(AuctionProtocol.formatWinnerNotification(
                            auction.getId(), auction.getCurrentPrice(), auction.getItemId()));
                }
                if (sellerId != null && sellerId.equals(user.getId())) {
                    client.sendMessage(AuctionProtocol.formatSellerNotification(
                            auction.getId(), auction.getCurrentPrice(), winner, auction.getItemId()));
                }
            }
        }
    }

    /**
     * Bắn thông báo riêng tới Seller và Winner khi phiên hoàn tất thanh toán (PAID).
     */
    public void notifyAuctionPaid(Auction auction) {
        if (auction == null) {
            return;
        }
        String winner = auction.getHighestBidderId() != null ? auction.getHighestBidderId() : "NONE";
        for (ClientHandler client : activeClients) {
            User user = client.getCurrentUser();
            if (user == null) {
                continue;
            }
            if (!client.isSubscribed(auction.getId())) {
                if ((auction.getHighestBidderId() != null && auction.getHighestBidderId().equals(user.getId()))
                        || (auction.getSellerId() != null && auction.getSellerId().equals(user.getId()))) {
                    client.sendMessage(AuctionProtocol.formatPaidNotification(
                            auction.getId(), auction.getCurrentPrice(), winner));
                }
            }
        }
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }

    public int getDefaultClientTimeoutMs() {
        return defaultClientTimeoutMs;
    }

    public void setDefaultClientTimeoutMs(int defaultClientTimeoutMs) {
        this.defaultClientTimeoutMs = defaultClientTimeoutMs;
    }

    /**
     * Broadcast bản tin tới toàn bộ Client đang kết nối.
     */
    public void broadcastToAll(String message) {
        for (ClientHandler client : activeClients) {
            client.sendMessage(message);
        }
    }

    /**
     * Khởi tạo dữ liệu hệ thống: Ưu tiên nạp dữ liệu đã lưu từ file (Deserialization).
     * Nếu chưa có dữ liệu lưu trữ, khởi tạo dữ liệu mẫu và lưu lại thành file ban đầu.
     */
    public static void initData() {
        initData(DEFAULT_DATA_FILE);
    }

    public static void initData(String dataFilePath) {
        AuctionManager manager = AuctionManager.getInstance();
        manager.setStoragePath(dataFilePath);
        manager.setAutoSave(true);

        boolean loaded = manager.loadState(dataFilePath);
        if (loaded && (!manager.getAllUsers().isEmpty() || !manager.getAllAuctions().isEmpty())) {
            System.out.printf("[AuctionServer] Restored persistent data: %d users, %d auctions from %s%n",
                    manager.getAllUsers().size(), manager.getAllAuctions().size(), dataFilePath);
            return;
        }

        initSampleData();
        manager.saveState(dataFilePath);
    }

    /**
     * Khởi tạo dữ liệu mẫu nếu AuctionManager đang trống.
     */
    public static void initSampleData() {
        AuctionManager manager = AuctionManager.getInstance();
        if (!manager.getAllAuctions().isEmpty()) {
            return;
        }

        Seller tuanDai = new Seller("SELLER-01", "Tuấn Đại", "password123");
        Seller phamTruong = new Seller("SELLER-02", "Phạm Trường", "password123");
        Bidder xuanThang = new Bidder("BIDDER-01", "Xuân Thắng", "password123");
        Bidder huyHung = new Bidder("BIDDER-02", "Huy Hưng", "password123");
        Admin admin = new Admin("ADMIN-01", "BidX Admin", "password123");

        manager.registerUser(tuanDai);
        manager.registerUser(phamTruong);
        manager.registerUser(xuanThang);
        manager.registerUser(huyHung);
        manager.registerUser(admin);

        // ================= ĐỒ ĐIỆN TỬ (ELECTRONICS) =================
        // 1. Laptop Dell XPS 15 (giữ nguyên AUC-001, start=10M, step=500k cho AuctionServerPersistenceTest)
        addRunningSample(manager, "AUC-001", "Laptop Dell XPS 15 OLED", tuanDai.getId(),
                10_000_000, 500_000, "ELECTRONICS", "item_laptop_xps", 45,
                "Ultrabook vỏ nhôm CNC cao cấp, màn hình OLED 3.5K cảm ứng, RAM 32GB, SSD 1TB, bảo hành chính hãng 24 tháng.");

        // 2. Máy ảnh Leica M3
        addRunningSample(manager, "AUC-004", "Máy Ảnh Film Cổ Leica M3", phamTruong.getId(),
                15_500_000, 500_000, "ELECTRONICS", "item_camera_leica", 22,
                "Huyền thoại máy ảnh rangefinder Đức thân bạc cổ điển, ống kính Summicron 50mm f/2 quang học hoàn hảo.");

        // 3. MacBook Pro M4 Max
        addRunningSample(manager, "AUC-010", "MacBook Pro 16 M4 Max Space Black", tuanDai.getId(),
                68_000_000, 2_000_000, "ELECTRONICS", "item_macbook_pro", 50,
                "Cấu hình đỉnh cao chip M4 Max 16-Core, RAM 64GB, SSD 2TB, màn hình Liquid Retina XDR Nano-Texture nguyên seal.");

        // 4. Tai Nghe Hi-End Focal Utopia
        addRunningSample(manager, "AUC-011", "Tai Nghe Hi-End Focal Utopia Carbon", phamTruong.getId(),
                74_000_000, 2_000_000, "ELECTRONICS", "item_headphone_hifi", 36,
                "Tai nghe tham chiếu audiophile thủ công từ Pháp, màng loa Beryllium nguyên chất, khung carbon & da cừu cao cấp.");

        // ================= SIÊU XE (VEHICLES) =================
        // 5. Porsche 911 Classic Silver
        addRunningSample(manager, "AUC-007", "Porsche 911 Classic Silver", phamTruong.getId(),
                185_000_000, 5_000_000, "VEHICLES", "item_vehicle_porsche", 60,
                "Biểu tượng xe thể thao cổ điển châu Âu màu bạc ánh kim, động cơ nguyên bản bảo dưỡng chính hãng đầy đủ hồ sơ.");

        // 6. Lamborghini Aventador SVJ
        addRunningSample(manager, "AUC-012", "Lamborghini Aventador SVJ Giallo", tuanDai.getId(),
                420_000_000, 10_000_000, "VEHICLES", "item_car_lambo", 75,
                "Siêu bò V12 hút khí tự nhiên 770 mã lực phiên bản giới hạn màu vàng Giallo Orion, gói khí động học ALA 2.0 carbon.");

        // 7. Ferrari SF90 Stradale
        addRunningSample(manager, "AUC-013", "Ferrari SF90 Stradale Rosso Corsa", phamTruong.getId(),
                390_000_000, 10_000_000, "VEHICLES", "item_car_ferrari", 70,
                "Siêu xe hybrid 1.000 mã lực màu đỏ Rosso Corsa biểu tượng Maranello, nội thất da Alcantara khâu chỉ đỏ thủ công.");

        // 8. Mercedes-AMG G63 Edition 1
        addRunningSample(manager, "AUC-014", "Mercedes-AMG G63 Matte Black Edition", tuanDai.getId(),
                265_000_000, 8_000_000, "VEHICLES", "item_car_g63", 58,
                "Ông vua địa hình hạng sang động cơ V8 Biturbo 4.0L sơn đen nhám Designo Magno, kẹp phanh đồng đặc trưng.");

        // 9. Rolls-Royce Phantom VIII
        addRunningSample(manager, "AUC-015", "Rolls-Royce Phantom VIII Bespoke", phamTruong.getId(),
                460_000_000, 15_000_000, "VEHICLES", "item_car_rollsroyce", 85,
                "Đỉnh cao siêu sang hai tông màu đen Obsidian & bạc ánh kim, bầu trời sao Starlight 1.344 điểm đèn sợi quang.");

        // ================= ĐỒNG HỒ CỔ (WATCHES) =================
        // 10. Đồng hồ cơ cổ
        addRunningSample(manager, "AUC-003", "Đồng Hồ Cơ Thụy Sĩ Vintage", tuanDai.getId(),
                19_500_000, 500_000, "WATCHES", "item_watch_classic", 55,
                "Đồng hồ cơ lên cót tay vàng hồng 18K nguyên bản, mặt số champagne cổ điển, dây da bê thủ công Ý.");

        // 11. Đồng hồ Tourbillon Skeleton
        addRunningSample(manager, "AUC-005", "Đồng Hồ Skeleton Tourbillon", phamTruong.getId(),
                28_900_000, 1_000_000, "WATCHES", "item_watch_skeleton", 32,
                "Kiệt tác cơ khí lộ máy Tourbillon mặt xanh hoàng gia kết hợp vỏ vàng hồng, kính sapphire chống lóa hai mặt.");

        // 12. Rolex Daytona 18K Gold
        addRunningSample(manager, "AUC-016", "Rolex Cosmograph Daytona 18K Gold", tuanDai.getId(),
                145_000_000, 5_000_000, "WATCHES", "item_watch_rolex", 64,
                "Phiên bản bấm giờ thể thao vỏ vàng nguyên khối 18K vành gốm Cerachrom đen, máy Caliber 4131 chính hãng Thụy Sĩ.");

        // 13. Audemars Piguet Royal Oak Blue Dial
        addRunningSample(manager, "AUC-017", "Audemars Piguet Royal Oak Blue Dial", phamTruong.getId(),
                168_000_000, 5_000_000, "WATCHES", "item_watch_royaloak", 68,
                "Thiết kế vành bát giác 8 ốc vít huyền thoại, mặt số Grande Tapisserie xanh navy sâu thẳm, thép hoàn thiện satin.");

        // ================= NGHỆ THUẬT (ART) =================
        // 14. Bình Sứ Cổ
        addRunningSample(manager, "AUC-002", "Bình Sứ Thanh Hoa Thế Kỷ 19", tuanDai.getId(),
                15_300_000, 500_000, "ART", "item_vase_antique", 38,
                "Cổ vật gốm sứ men lam họa tiết hoa sen vẽ tay viền vàng kim, có giấy chứng nhận giám định bảo tàng.");

        // 15. Tranh Sơn Dầu
        addRunningSample(manager, "AUC-006", "Tranh Sơn Dầu Hoàng Hôn Biển", tuanDai.getId(),
                22_000_000, 1_000_000, "ART", "item_art_painting", 28,
                "Tác phẩm sơn dầu trên toan phong cách ấn tượng thế kỷ 20, khung gỗ chạm trổ dát vàng thủ công nguyên bản.");

        // 16. Tượng Đồng Cổ Khổng Tước
        addRunningSample(manager, "AUC-018", "Tượng Đồng Mạ Vàng Khổng Tước Khai Phú", phamTruong.getId(),
                54_000_000, 2_000_000, "ART", "item_art_sculpture", 48,
                "Tác phẩm điêu khắc đồng cổ đúc liền khối dát vàng 24K trên đế đá cẩm thạch đen vân trắng, tinh xảo từng lông vũ.");

        // 17. Đại Dương Cầm Steinway
        addRunningSample(manager, "AUC-019", "Đàn Grand Piano Steinway & Sons Model D", tuanDai.getId(),
                240_000_000, 10_000_000, "ART", "item_art_piano", 72,
                "Đại dương cầm hòa nhạc chuẩn phòng thu màu đen bóng Ebony Polish, bảng cộng hưởng gỗ vân sam Sitka thủ công.");

        // ================= KHÁC (OTHER - SƯU TẦM XA XỈ ĐỘC BẢN) =================
        // 18. Túi Hermès Birkin 30
        addRunningSample(manager, "AUC-020", "Túi Hermès Birkin 30 Da Cá Sấu Cognac", phamTruong.getId(),
                195_000_000, 5_000_000, "OTHER", "item_other_birkin", 52,
                "Tuyệt tác thủ công Paris chế tác từ da cá sấu Niloticus màu nâu Cognac, khóa kim loại mạ vàng 18K đầy đủ bill hộp.");

        // 19. Nước Hoa Hoàng Gia Oud Amber Extrait
        addRunningSample(manager, "AUC-021", "Nước Hoa Hoàng Gia Oud Amber Extrait", tuanDai.getId(),
                38_000_000, 1_500_000, "OTHER", "item_other_perfume", 42,
                "Tinh chất nước hoa Niche cao cấp chiết xuất trầm hương & hổ phách hoàng gia, chai pha lê cắt giác thủ công kèm hộp gỗ sồi lót nhung.");

        // 20. Nhẫn Kim Cương 3.5 Carat GIA
        addRunningSample(manager, "AUC-022", "Nhẫn Kim Cương Tự Nhiên 3.5 Carat GIA", phamTruong.getId(),
                310_000_000, 10_000_000, "OTHER", "item_other_diamond", 66,
                "Kim cương tự nhiên giác cắt Round Brilliant nước D độ tinh khiết VVS1 chuẩn chứng thư GIA quốc tế, ổ nhẫn Platinum PT950.");

        // 21. Bộ Cờ Vua Hoàng Gia Đá Cẩm Thạch
        addRunningSample(manager, "AUC-023", "Bộ Cờ Vua Hoàng Gia Đá Cẩm Thạch Ý", tuanDai.getId(),
                34_000_000, 1_500_000, "OTHER", "item_other_chess", 44,
                "Bộ cờ thủ công điêu khắc từ đá cẩm thạch Carrara trắng & xanh ngọc lục bảo kết hợp chân đồng mạ vàng tinh xảo.");

        // 22. Rượu Whisky Chivas Regal Collector Edition
        addRunningSample(manager, "AUC-024", "Rượu Whisky Chivas Regal Collector Edition", phamTruong.getId(),
                45_000_000, 2_000_000, "OTHER", "item_other_whisky", 54,
                "Blended Scotch Whisky thượng hạng Scotland phiên bản sưu tầm giới hạn, ủ lâu năm trong thùng gỗ sồi tuyển chọn, hương mật ong & gỗ sồi tinh tế.");

        // ================= CHỜ ADMIN DUYỆT (PENDING_APPROVAL) =================
        Auction pending1 = Auction.createPendingRequest(
                "AUC-008",
                "Đồng Hồ Patek Calatrava Gold",
                tuanDai.getId(),
                42_000_000,
                2_000_000,
                "WATCHES",
                "Phiên bản sưu tầm giới hạn vỏ vàng hồng 39mm, đầy đủ hộp sổ giấy khai sinh chính hãng từ Geneva.",
                600,
                "item_watch_classic");
        manager.addAuction(pending1);

        Auction pending2 = Auction.createPendingRequest(
                "AUC-009",
                "Leica Summilux Collector Set",
                phamTruong.getId(),
                31_500_000,
                1_000_000,
                "ELECTRONICS",
                "Bộ sưu tập máy ảnh film phiên bản giới hạn kèm dây đeo da thủ công và hộp gỗ mun nguyên bản.",
                900,
                "item_camera_leica");
        manager.addAuction(pending2);

        System.out.println("[BidX Server] Sample luxury auctions (21 live + 2 pending approvals) and 4 team members initialized.");
    }

    private static void addRunningSample(
            AuctionManager manager,
            String id,
            String name,
            String sellerId,
            double startPrice,
            double stepPrice,
            String category,
            String imageKey,
            long durationMinutes,
            String description) {
        Auction a = new Auction(id, name, sellerId, startPrice, stepPrice);
        a.setCategory(category);
        a.setImageKey(imageKey);
        a.setDescription(description);
        a.setRequestedDurationSeconds(durationMinutes * 60L);
        a.startAuction(Duration.ofMinutes(durationMinutes));
        manager.addAuction(a);
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.printf("Invalid port '%s', using default port %d%n", args[0], DEFAULT_PORT);
            }
        }

        initData();

        AuctionServer server = new AuctionServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        try {
            server.start();
        } catch (IOException e) {
            System.err.printf("Failed to start server: %s%n", e.getMessage());
        }
    }
}

