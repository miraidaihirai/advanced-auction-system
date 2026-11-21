package network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
// Tầng mạng, tách biệt với UI và model
// BufferedReader/InputStreamReader đọc text từ socket
// PrintWriter ghi text ra socket
// InetSocketAddress hiện địa chỉ host:port để kết nối có timeout
// Socket: Kết nối TCP
// StandardCharsets tránh lỗi encoding khi có tiếng Việt
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
// LocalDateTime parse timestamp của transaction
// Locale.US đảm bảo số thực dùng "." chứ khônh phải ",", tránh lỗi khi máy chạy locale VN
// Executors + ScheduledExecutorService + TimeUnit: lập lịch gửi heartbeat
// Consumer là functional interface để duyệt listener
import com.auction.model.BidTransaction;


/**
 * Socket Client kết nối tới AuctionServer.
 * - Gửi các bản tin yêu cầu (Login, Register, Join, Bid, Leave).
 * - Nhận và lắng nghe các thông báo realtime từ Server (Observer Pattern qua
 * Socket).
 */
public class AuctionClient {

    public interface AuctionEventListener {
        void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId);
        // Mọi listener đều phải cài đặt 
        default void onAuctionClosed(String auctionId, double finalPrice, String winnerId) {
        }

        default void onWinnerNotification(String auctionId, double finalPrice, String itemId) {
        }

        default void onSellerNotification(String auctionId, double finalPrice, String winnerId, String itemId) {
        }

        default void onAuctionPaid(String auctionId, double finalPrice, String winnerId) {
        }

        default void onHistoryReceived(String auctionId, List<BidTransaction> history) {
        }

        default void onMessageReceived(String rawMessage) {
        }

        default void onDisconnected() {
        }

        default void onAuctionListReceived(List<AuctionSummary> auctions) {
        }

        // Được gọi khi nhận dòng AUCTION_INFO (phản hồi của lệnh INFO)
        default void onAuctionInfoReceived(String auctionId, String itemId, String sellerId,
                double currentPrice, double stepPrice, String highestBidderId, String status) {
        }
        // default + {} tránh bắt buộc implement hết
    }

    private static final long HEARTBEAT_INTERVAL_SECONDS = 20;
    // Đủ để giữ NAT/Firewall không đóng kết nối
    private static final int CONNECT_TIMEOUT_MS = 3000;
    // Nếu server không phản hồi trong 3s thì báo lỗi

    private final String host;
    private final int port;
    private volatile Socket socket;
    // Đọc từ listener và ghi từ thread gọi disconnect/connect
    private PrintWriter out;
    // Không volatile vì luôn được truy cập trong synchronized
    private volatile boolean connected = false;
    private ScheduledExecutorService heartbeatScheduler;
    // Scheduler gửi PING định kỳ

    private final List<AuctionEventListener> listeners = new CopyOnWriteArrayList<>();
    //Mỗi lần add/remove, mảng nội bộ được copy, nên duyệt không cần lock

    public AuctionClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    // Chỉ lưu cấu hình, không kết nối ngay

    private synchronized void startHeartbeat() {
        // Nếu đã có một schedule đang chạy thì không tạo thêm
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            return;
        }
        // Thread riêng gửi PING định kỳ, không chạy trên JavaFX nên không làm treo giao diện
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "auction-client-heartbeat");
            thread.setDaemon(true);
            // App đóng thread tự chết
            return thread;
        });

        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!isConnected()) {
                return;
            }
            try {
                ping();
            } catch (IllegalStateException e) {
                System.err.println("[AuctionClient] Heartbeat failed: " + e.getMessage());
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    // Chạy task mỗi 20 lần, bắt exception do sendRaw ném để không làm chết Scheduler

    public void addListener(AuctionEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(AuctionEventListener listener) {
        listeners.remove(listener);
    }

    public synchronized void connect() throws IOException {
        if (connected && socket != null && !socket.isClosed()) {
            return;
        }

        // Dọn kết nối cũ (nếu còn) trước khi mở kết nối mới
        disconnect();

        Socket newSocket = new Socket();
        try {
            newSocket.setTcpNoDelay(true);
            // Tắt Nagle algorithm, giảm delay cho message nhỏ (đặt giá, notify real time), phù hợp cho đấu giá cần phản hồi nhanh
            newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            //Kết nối với timeout 3s, tránh treo vô hạn nếu server không phản hồi

            // Khởi tạo OutputStream trước và flush() ngay để handshake đúng chuẩn Socket
            PrintWriter newOut = new PrintWriter(newSocket.getOutputStream(), true, StandardCharsets.UTF_8);
            newOut.flush();
            BufferedReader newIn = new BufferedReader(
                    new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));

            socket = newSocket;
            out = newOut;
            connected = true;

            Thread listenerThread = new Thread(() -> listenLoop(newSocket, newIn), "auction-client-listener");
            // Truyền newSocket và newIn như tham số, vì nếu đọc field thì reconnect có thể làm thread cũ đọc nhầm socket mới
            listenerThread.setDaemon(true);
            listenerThread.start();

            startHeartbeat();
        } catch (IOException e) {
            // Luôn đóng socket vừa tạo, tránh rò rỉ khi kết nối thất bại
            closeQuietly(newSocket);
            throw e;
        }
    }

    /** Gọi toàn bộ listener; lỗi của một listener không ảnh hưởng listener khác. */
    private void fire(Consumer<AuctionEventListener> action) {
        for (AuctionEventListener listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                System.err.printf("[AuctionClient] Exception in listener %s: %s%n",
                        listener.getClass().getName(), e.getMessage());
            }
        }
    }

    private static double parsePrice(String raw) {
        return Double.parseDouble(raw.replace(',', '.'));
    }
    // Chuẩn hoá giá nếu server gửi dạng 250000,00

    private void listenLoop(Socket activeSocket, BufferedReader activeIn) {
        List<BidTransaction> historyBuffer = null;
        String historyAuctionId = null;
        // Server gửi danh sách đấu giá theo từng dòng riêng biệt
        List<AuctionSummary> auctionBuffer = null;

        try {
            String line;
            while (connected && socket == activeSocket && (line = activeIn.readLine()) != null) {
                // Đóng khi disconnected, user reconnect thì thread cũ thấy socket khác, thoát
                final String msg = line.trim();
                if (msg.isEmpty()) {
                    continue;
                }

                fire(l -> l.onMessageReceived(msg));
                // Luôn báo raw message trước

                try {
                // Một dòng lỗi không chết cả vòng lặp
                    if (msg.equals(AuctionProtocol.AUCTIONS_BEGIN)) {
                        // Tạo bộ đệm mới
                        auctionBuffer = new ArrayList<>();

                    } else if (msg.startsWith("AUCTION ") && auctionBuffer != null) {
                        // AUCTION <id> <item...> <price> <step> <winner> <status>
                        try {
                            auctionBuffer.add(parseAuctionLine(msg));
                        } catch (IllegalArgumentException e) {
                            System.err.println("[AuctionClient] Dữ liệu phiên đấu giá không hợp lệ: " + msg);
                        }

                    } else if (msg.equals(AuctionProtocol.AUCTIONS_END) && auctionBuffer != null) {
                        // Tạo danh sách bất biến rồi gửi cho listener, sau đó reset bộ đệm
                        final List<AuctionSummary> auctionList = List.copyOf(auctionBuffer);
                        auctionBuffer = null;
                        fire(l -> l.onAuctionListReceived(auctionList));

                    } else if (msg.startsWith("AUCTION_INFO ")) {
                        parseAndFireAuctionInfo(msg);

                    } else if (msg.startsWith(AuctionProtocol.NOTIFY_BID_PREFIX)) {
                        // NOTIFY BID <auctionId> <newPrice> <bidderId> <itemId...>
                        // Giới hạn 6 phần để giữ nguyên tên sản phẩm có dấu cách
                        String[] parts = msg.split("\\s+", 6);
                        if (parts.length >= 6) {
                            final String auctionId = parts[2];
                            final double newPrice = parsePrice(parts[3]);
                            final String bidderId = parts[4];
                            final String itemId = parts[5];
                            fire(l -> l.onBidUpdate(auctionId, newPrice, bidderId, itemId));
                        }

                    } else if (msg.startsWith(AuctionProtocol.NOTIFY_CLOSED_PREFIX)) {
                        // NOTIFY CLOSED <auctionId> <finalPrice> <winnerId>
                        String[] parts = msg.split("\\s+");
                        if (parts.length >= 5) {
                            final String auctionId = parts[2];
                            final double finalPrice = parsePrice(parts[3]);
                            final String winnerId = parts[4];
                            fire(l -> l.onAuctionClosed(auctionId, finalPrice, winnerId));
                        }

                    } else if (msg.startsWith(AuctionProtocol.NOTIFY_WINNER_PREFIX)) {
                        // NOTIFY WINNER <auctionId> <finalPrice> <itemId...>
                        String[] parts = msg.split("\\s+", 5);
                        if (parts.length >= 5) {
                            final String auctionId = parts[2];
                            final double finalPrice = parsePrice(parts[3]);
                            final String itemId = parts[4];
                            fire(l -> l.onWinnerNotification(auctionId, finalPrice, itemId));
                        }

                    } else if (msg.startsWith(AuctionProtocol.NOTIFY_SELLER_PREFIX)) {
                        // NOTIFY SELLER <auctionId> <finalPrice> <winnerId> <itemId...>
                        String[] parts = msg.split("\\s+", 6);
                        if (parts.length >= 6) {
                            final String auctionId = parts[2];
                            final double finalPrice = parsePrice(parts[3]);
                            final String winnerId = parts[4];
                            final String itemId = parts[5];
                            fire(l -> l.onSellerNotification(auctionId, finalPrice, winnerId, itemId));
                        }

                    } else if (msg.startsWith(AuctionProtocol.NOTIFY_PAID_PREFIX)) {
                        // NOTIFY PAID <auctionId> <finalPrice> <winnerId>
                        String[] parts = msg.split("\\s+");
                        if (parts.length >= 5) {
                            final String auctionId = parts[2];
                            final double finalPrice = parsePrice(parts[3]);
                            final String winnerId = parts[4];
                            fire(l -> l.onAuctionPaid(auctionId, finalPrice, winnerId));
                        }

                    } else if (msg.startsWith(AuctionProtocol.HISTORY_BEGIN)) {
                        // HISTORY_BEGIN <auctionId> <count>
                        String[] parts = msg.split("\\s+");
                        if (parts.length >= 2) {
                            historyAuctionId = parts[1];
                            historyBuffer = new ArrayList<>();
                        }

                    } else if (msg.startsWith("TX ") && historyBuffer != null) {
                        // TX <id> <auctionId> <bidderId> <amount> <timestamp>
                        String[] parts = msg.split("\\s+");
                        if (parts.length >= 5) {
                            try {
                                BidTransaction tx = new BidTransaction(
                                        parts[1], parts[2], parts[3], parsePrice(parts[4]));
                                if (parts.length >= 6) {
                                    tx.setTimestamp(LocalDateTime.parse(parts[5]));
                                }
                                historyBuffer.add(tx);
                            } catch (RuntimeException e) {
                                System.err.println("[AuctionClient] Bản ghi lịch sử không hợp lệ: " + msg);
                            }
                        }

                    } else if (msg.startsWith(AuctionProtocol.HISTORY_END)) {
                        // HISTORY_END <auctionId>
                        String[] parts = msg.split("\\s+");
                        final String aucId = parts.length >= 2 ? parts[1] : historyAuctionId;
                        final List<BidTransaction> history = (historyBuffer != null)
                                ? historyBuffer
                                : Collections.emptyList();
                        historyBuffer = null;
                        historyAuctionId = null;
                        fire(l -> l.onHistoryReceived(aucId, history));
                    }
                } catch (NumberFormatException e) {
                    System.err.println("[AuctionClient] Dữ liệu số không hợp lệ từ Server: " + msg);
                } catch (RuntimeException e) {
                    // Một dòng lỗi không được làm chết luồng nghe
                    System.err.printf("[AuctionClient] Không xử lý được dòng '%s': %s%n", msg, e.getMessage());
                }
            }
        } catch (IOException e) {
            if (connected && socket == activeSocket) {
                System.out.printf("[AuctionClient] Connection lost: %s%n", e.getMessage());
            }
        } finally {
            // Khi Server đóng kết nối hoặc mạng gián đoạn, vòng lặp kết thúc.
            // Nếu vẫn còn connected tức là mất kết nối bất ngờ: báo cho listener rồi giải phóng Socket
            if (socket == activeSocket) {
                if (connected) {
                    fire(AuctionEventListener::onDisconnected);
                }
                disconnect();
            }
        }
    }

    private AuctionSummary parseAuctionLine(String line) {

        String[] parts = line.trim().split("\\s+");

        if (parts.length < 7 || !parts[0].equals("AUCTION")) {
            throw new IllegalArgumentException("Sai định dạng AUCTION");
        }
        // E.g AUCTION AUC-001 Laptop Dell XPS 15 25000000.00 500000.00 BIDDER-01 RUNNING
        // 4 phần tử cuối cố định, phần giữa là tên sản phẩm (có thể có dấu cách)
        int currentPriceIndex = parts.length - 4;

        String auctionId = parts[1];

        String itemId = String.join(" ", Arrays.copyOfRange(parts, 2, currentPriceIndex));

        try {
            double currentPrice = parsePrice(parts[currentPriceIndex]);
            double stepPrice = parsePrice(parts[currentPriceIndex + 1]);
            String highestBidderId = parts[currentPriceIndex + 2];
            String status = parts[currentPriceIndex + 3];

            return new AuctionSummary(auctionId, itemId, currentPrice, stepPrice, highestBidderId, status);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Giá đấu không đúng định dạng", e);
        }
    }

    /**
     * AUCTION_INFO <auctionId> <item...> <sellerId> <price> <step> <winner> <status>
     * 5 phần tử cuối cố định, phần giữa là tên sản phẩm (có thể có dấu cách).
     */
    private void parseAndFireAuctionInfo(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 8) {
            System.err.println("[AuctionClient] Dữ liệu AUCTION_INFO không hợp lệ: " + line);
            return;
        }

        int tail = parts.length - 5;
        final String auctionId = parts[1];
        final String itemId = String.join(" ", Arrays.copyOfRange(parts, 2, tail));
        final String sellerId = parts[tail];
        final double currentPrice = parsePrice(parts[tail + 1]);
        final double stepPrice = parsePrice(parts[tail + 2]);
        final String winnerId = parts[tail + 3];
        final String status = parts[tail + 4];

        fire(l -> l.onAuctionInfoReceived(
                auctionId, itemId, sellerId, currentPrice, stepPrice, winnerId, status));
    }

    public synchronized void sendRaw(String command) {
        if (!connected || out == null) {
            throw new IllegalStateException("Client is not connected to server");
        }
        out.println(command);
        out.flush();
    }

    public void login(String userId, String password) {
        sendRaw(String.format("%s %s %s", AuctionProtocol.CMD_LOGIN, userId, password));
    }

    public void register(String userId, String name, String password, String role) {
        sendRaw(String.format("%s %s %s %s %s", AuctionProtocol.CMD_REGISTER, userId, name, password, role));
    }

    public void joinAuction(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_JOIN, auctionId));
    }

    public void leaveAuction(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_LEAVE, auctionId));
    }

    public void placeBid(String auctionId, double amount) {
        sendRaw(String.format(Locale.US, "%s %s %.2f", AuctionProtocol.CMD_BID, auctionId, amount));
    }

    public void requestList() {
        sendRaw(AuctionProtocol.CMD_LIST);
    }

    public void requestInfo(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_INFO, auctionId));
    }

    public void requestHistory(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_HISTORY, auctionId));
    }

    public void ping() {
        sendRaw(AuctionProtocol.CMD_PING);
    }

    public void startAuction(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_START, auctionId));
    }

    public void startAuction(String auctionId, int durationSeconds) {
        sendRaw(String.format("%s %s %d", AuctionProtocol.CMD_START, auctionId, durationSeconds));
    }

    public void closeAuction(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_CLOSE, auctionId));
    }

    public void payAuction(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_PAY, auctionId));
    }

    public synchronized void disconnect() {
        boolean wasConnected = connected;
        connected = false;

        // Dừng heartbeat scheduler
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
            heartbeatScheduler = null;
        }

        // Chỉ báo QUIT khi đang có kết nối thật
        if (wasConnected && out != null) {
            out.println(AuctionProtocol.CMD_QUIT);
            out.flush();
        }

        // Luôn đóng socket (nếu còn), kể cả khi chưa kịp đánh dấu connected
        closeQuietly(socket);
    }

    private static void closeQuietly(Socket target) {
        if (target == null) {
            return;
        }
        try {
            if (!target.isClosed()) {
                target.close();
            }
        } catch (IOException ignored) {
            // Đang dọn dẹp: không còn gì để xử lý thêm
        }
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
}