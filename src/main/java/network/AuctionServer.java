package network;

import com.auction.manager.AuctionManager;
import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
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
 * Socket Multi-threaded Server cho hệ thống đấu giá trực tuyến. - Lắng nghe kết nối từ các Client
 * và cấp phát mỗi Client một Thread riêng (ClientHandler). - Quản lý vòng đời Server và danh sách
 * Client kết nối đồng thời. - Tự động nạp và lưu trữ dữ liệu (Serialization / Deserialization)
 * tránh mất dữ liệu khi khởi động lại server.
 */
public class AuctionServer implements AuctionObserver {

  public static final int DEFAULT_PORT = AuctionProtocol.DEFAULT_PORT;
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
    this.clientThreadPool =
        Executors.newCachedThreadPool(
            task -> {
              Thread thread =
                  new Thread(task, "socket-client-worker-" + clientThreadCounter.incrementAndGet());
              thread.setDaemon(true);
              return thread;
            });
  }

  /** Khởi động server (đồng bộ - lặp accept kết nối). */
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
        System.out.printf(
            "[AuctionServer] New connection from: %s%n", clientSocket.getRemoteSocketAddress());

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

  /** Khởi động server bất đồng bộ (chạy trên thread riêng, hữu ích cho testing / nhúng). */
  public void startAsync() {
    Thread serverThread =
        new Thread(
            () -> {
              try {
                start();
              } catch (IOException e) {
                if (running) {
                  System.err.printf(
                      "[AuctionServer] Server stopped with error: %s%n", e.getMessage());
                }
              }
            },
            "auction-server-main");
    serverThread.setDaemon(true);
    serverThread.start();
  }

  /**
   * Dừng server và giải phóng toàn bộ kết nối. Tự động lưu trạng thái dữ liệu ra file nếu autoSave
   * đang bật.
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
    notifyAuctionFinished(auction);
  }

  @Override
  public void onAuctionPaid(Auction auction) {
    notifyAuctionPaid(auction);
  }

  /**
   * Bắn thông báo riêng đến Seller và Winner khi phiên đóng nếu họ đang kết nối socket nhưng chưa
   * kịp / không trực tiếp JOIN phòng đấu giá.
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
          client.sendMessage(
              AuctionProtocol.formatWinnerNotification(
                  auction.getId(), auction.getCurrentPrice(), auction.getItemId()));
        }
        if (sellerId != null && sellerId.equals(user.getId())) {
          client.sendMessage(
              AuctionProtocol.formatSellerNotification(
                  auction.getId(), auction.getCurrentPrice(), winner, auction.getItemId()));
        }
      }
    }
  }

  /** Bắn thông báo riêng tới Seller và Winner khi phiên hoàn tất thanh toán (PAID). */
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
        if ((auction.getHighestBidderId() != null
                && auction.getHighestBidderId().equals(user.getId()))
            || (auction.getSellerId() != null && auction.getSellerId().equals(user.getId()))) {
          client.sendMessage(
              AuctionProtocol.formatPaidNotification(
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

  /** Broadcast bản tin tới toàn bộ Client đang kết nối. */
  public void broadcastToAll(String message) {
    for (ClientHandler client : activeClients) {
      client.sendMessage(message);
    }
  }

  /**
   * Khởi tạo dữ liệu hệ thống: Ưu tiên nạp dữ liệu đã lưu từ file (Deserialization). Nếu chưa có dữ
   * liệu lưu trữ, khởi tạo dữ liệu mẫu và lưu lại thành file ban đầu.
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
      System.out.printf(
          "[AuctionServer] Restored persistent data: %d users, %d auctions from %s%n",
          manager.getAllUsers().size(), manager.getAllAuctions().size(), dataFilePath);
      ensureDefaultUsers(manager);
      printLoadedUsers(manager);
      return;
    }

    initSampleData();
    manager.saveState(dataFilePath);
    printLoadedUsers(manager);
  }

  /** Đảm bảo các tài khoản mẫu bắt buộc luôn luôn tồn tại trong hệ thống. */
  public static void ensureDefaultUsers(AuctionManager manager) {
    if (manager.getUser("SELLER-01") == null) {
      manager.registerUser(new Seller("SELLER-01", "Alice", "password123"));
    }
    if (manager.getUser("BIDDER-01") == null) {
      manager.registerUser(new Bidder("BIDDER-01", "Bob", "password123"));
    }
    if (manager.getUser("BIDDER-02") == null) {
      manager.registerUser(new Bidder("BIDDER-02", "Charlie", "password123"));
    }
    if (manager.getUser("BIDDER-03") == null) {
      manager.registerUser(new Bidder("BIDDER-03", "David", "password123"));
    }
    if (manager.getUser("ADMIN-01") == null) {
      manager.registerUser(new Admin("ADMIN-01", "Administrator", "password123"));
    }
  }

  private static void printLoadedUsers(AuctionManager manager) {
    System.out.println("--------------------------------------------------");
    System.out.println(" [AuctionServer] DANH SÁCH TÀI KHOẢN KHẢ DỤNG:");
    for (User u : manager.getAllUsers()) {
      System.out.printf(
          "   - ID: %-12s | Tên: %-15s | Vai trò: %-8s | MK: password123%n",
          u.getId(), u.getName(), u.getRoleName());
    }
    System.out.println("--------------------------------------------------");
  }

  /** Khởi tạo dữ liệu mẫu nếu AuctionManager đang trống. */
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

    Auction auction1 =
        new Auction("AUC-001", "Laptop Dell XPS 15", alice.getId(), 10_000_000, 500_000);
    auction1.setCategory("ELECTRONICS");
    auction1.setDescription("Ultrabook vỏ nhôm CNC cao cấp, màn hình OLED, RAM 32GB, SSD 1TB.");
    auction1.setRequestedDurationSeconds(2700);
    auction1.startAuction(Duration.ofMinutes(45));
    manager.addAuction(auction1);

    Auction auction2 =
        new Auction("AUC-002", "Vintage Antique Vase", alice.getId(), 2_000_000, 100_000);
    auction2.setCategory("ART");
    auction2.setDescription("Bình gốm sứ cổ vẽ tay viền vàng, có giấy chứng nhận giám định.");
    auction2.setRequestedDurationSeconds(2100);
    auction2.startAuction(Duration.ofMinutes(35));
    manager.addAuction(auction2);

    Auction auction3 =
        new Auction("AUC-003", "Đồng Hồ Cơ Thụy Sĩ Vintage", alice.getId(), 19_500_000, 500_000);
    auction3.setCategory("WATCHES");
    auction3.setDescription("Đồng hồ cơ lên cót tay vàng hồng 18K nguyên bản, dây da bê thủ công.");
    auction3.setRequestedDurationSeconds(3000);
    auction3.startAuction(Duration.ofMinutes(50));
    manager.addAuction(auction3);

    Auction auction4 =
        new Auction("AUC-004", "Porsche 911 Classic Silver", alice.getId(), 185_000_000, 5_000_000);
    auction4.setCategory("VEHICLES");
    auction4.setDescription(
        "Xe thể thao cổ điển màu bạc ánh kim, động cơ nguyên bản đầy đủ hồ sơ.");
    auction4.setRequestedDurationSeconds(3600);
    auction4.startAuction(Duration.ofMinutes(60));
    manager.addAuction(auction4);

    Auction pending1 =
        Auction.createPendingRequest(
            "AUC-005",
            "Đồng Hồ Patek Calatrava Gold",
            alice.getId(),
            42_000_000,
            2_000_000,
            "WATCHES",
            "Phiên bản sưu tầm giới hạn vỏ vàng hồng 39mm, đầy đủ hộp sổ chính hãng.",
            900,
            "");
    manager.addAuction(pending1);

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
