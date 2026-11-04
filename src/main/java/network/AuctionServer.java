package network;

import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.Admin;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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
public class AuctionServer {

    public static final int DEFAULT_PORT = 8888;
    public static final String DEFAULT_DATA_FILE = AuctionManager.DEFAULT_STORAGE_PATH;

    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService clientThreadPool;
    private final Set<ClientHandler> activeClients = ConcurrentHashMap.newKeySet();
    private volatile boolean running = false;

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
        System.out.println("==================================================");
        System.out.printf(" AUCTION SOCKET SERVER STARTED ON PORT %d%n", port);
        System.out.println("==================================================");

        try {
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.printf("[AuctionServer] New connection from: %s%n", clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(clientSocket, this);
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
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        System.out.println("[AuctionServer] Stopping server...");

        // Lưu trạng thái trước khi tắt server
        AuctionManager manager = AuctionManager.getInstance();
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

        // Đóng toàn bộ ClientHandlers
        for (ClientHandler client : activeClients) {
            client.close();
        }
        activeClients.clear();

        clientThreadPool.shutdownNow();
        System.out.println("[AuctionServer] Server stopped successfully.");
    }

    public void removeClient(ClientHandler handler) {
        activeClients.remove(handler);
    }

    public int getConnectedClientCount() {
        return activeClients.size();
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
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

        Seller alice = new Seller("SELLER-01", "Alice", "password123");
        Bidder bob = new Bidder("BIDDER-01", "Bob", "password123");
        Bidder charlie = new Bidder("BIDDER-02", "Charlie", "password123");
        Bidder david = new Bidder("BIDDER-03", "David", "password123");
        Admin admin = new Admin("ADMIN-01", "Administrator", "password123");

        manager.registerUser(alice);
        manager.registerUser(bob);
        manager.registerUser(charlie);
        manager.registerUser(david);
        manager.registerUser(admin);

        Auction auction1 = new Auction("AUC-001", "Laptop Dell XPS 15", alice.getId(), 10_000_000, 500_000);
        auction1.startAuction();
        manager.addAuction(auction1);

        Auction auction2 = new Auction("AUC-002", "Vintage Antique Vase", alice.getId(), 2_000_000, 100_000);
        auction2.startAuction();
        manager.addAuction(auction2);

        System.out.println("[AuctionServer] Sample auctions and users initialized.");
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

