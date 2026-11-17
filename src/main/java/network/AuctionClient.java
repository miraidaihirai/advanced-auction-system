package network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
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
        // Được gọi khi Client nhận đủ danh sách đấu giá
        // auctions chứa toàn bộ các phiên đấu giá đã xong
    }

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread listenerThread;
    private volatile boolean connected = false;

    private final List<AuctionEventListener> listeners = new CopyOnWriteArrayList<>();

    public AuctionClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void addListener(AuctionEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(AuctionEventListener listener) {
        listeners.remove(listener);
    }

    public synchronized void connect() throws IOException {
        if (connected) {
            return;
        }

        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        connected = true;

        listenerThread = new Thread(this::listenLoop, "auction-client-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenLoop() {
        List<BidTransaction> currentHistoryBuffer = null;
        String currentHistoryAuctionId = null;

        List<AuctionSummary> currentAuctionBuffer = null;
        // Server gửi danh sách đấu giá theo từng dòng riêng biệt
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Notify raw message
                for (AuctionEventListener l : listeners) {
                    try {
                        l.onMessageReceived(line);
                    } catch (Exception ignored) {
                    }
                }
                if (line.equals(AuctionProtocol.AUCTIONS_BEGIN)) {

                    currentAuctionBuffer = new ArrayList<>();

                } else if (line.startsWith("AUCTION ")
                        && currentAuctionBuffer != null) {

                    try {
                        currentAuctionBuffer.add(parseAuctionLine(line));
                    } catch (IllegalArgumentException e) {
                        System.err.println(
                                "[AuctionClient] Dữ liệu phiên đấu giá không hợp lệ: " + line
                        );
                    }

                } else if (line.equals(AuctionProtocol.AUCTIONS_END)
                        && currentAuctionBuffer != null) {

                    List<AuctionSummary> auctionList =
                            List.copyOf(currentAuctionBuffer);

                    for (AuctionEventListener listener : listeners) {
                        try {
                            listener.onAuctionListReceived(auctionList);
                        } catch (Exception ignored) {
                        }
                    }

                    currentAuctionBuffer = null;
                }
                                
                // AUCTIONS_BIGINS tạo bộ đệm mới
                // AUCTION gọi parseAuctionLine() để chuyển String[] thành AuctionSummary
                // AUCTIONS_END tạo danh sách bất biến bằng List.copyOf() rồi gửi cho listener
                // Cuối cùng reset bộ đệm về null để kết thúc nhận dữ liệu


                // Parse realtime broadcast notifications
                if (line.startsWith(AuctionProtocol.NOTIFY_BID_PREFIX)) {
                    // NOTIFY BID <auctionId> <newPrice> <bidderId> <itemId>
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 6) {
                        String auctionId = parts[2];
                        double newPrice = Double.parseDouble(parts[3].replace(',', '.'));
                        String bidderId = parts[4];
                        String itemId = parts[5];
                        for (AuctionEventListener l : listeners) {
                            try {
                                l.onBidUpdate(auctionId, newPrice, bidderId, itemId);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } else if (line.startsWith(AuctionProtocol.NOTIFY_CLOSED_PREFIX)) {
                    // NOTIFY CLOSED <auctionId> <finalPrice> <winnerId>
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 5) {
                        String auctionId = parts[2];
                        double finalPrice = Double.parseDouble(parts[3].replace(',', '.'));
                        String winnerId = parts[4];
                        for (AuctionEventListener l : listeners) {
                            try {
                                l.onAuctionClosed(auctionId, finalPrice, winnerId);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } else if (line.startsWith(AuctionProtocol.NOTIFY_WINNER_PREFIX)) {
                    // NOTIFY WINNER <auctionId> <finalPrice> <itemId>
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 5) {
                        String auctionId = parts[2];
                        double finalPrice = Double.parseDouble(parts[3].replace(',', '.'));
                        String itemId = line.substring(line.indexOf(parts[4]));
                        for (AuctionEventListener l : listeners) {
                            try {
                                l.onWinnerNotification(auctionId, finalPrice, itemId);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } else if (line.startsWith(AuctionProtocol.NOTIFY_SELLER_PREFIX)) {
                    // NOTIFY SELLER <auctionId> <finalPrice> <winnerId> <itemId>
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 6) {
                        String auctionId = parts[2];
                        double finalPrice = Double.parseDouble(parts[3].replace(',', '.'));
                        String winnerId = parts[4];
                        String itemId = line.substring(line.indexOf(parts[5]));
                        for (AuctionEventListener l : listeners) {
                            try {
                                l.onSellerNotification(auctionId, finalPrice, winnerId, itemId);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } else if (line.startsWith(AuctionProtocol.NOTIFY_PAID_PREFIX)) {
                    // NOTIFY PAID <auctionId> <finalPrice> <winnerId>
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 5) {
                        String auctionId = parts[2];
                        double finalPrice = Double.parseDouble(parts[3].replace(',', '.'));
                        String winnerId = parts[4];
                        for (AuctionEventListener l : listeners) {
                            try {
                                l.onAuctionPaid(auctionId, finalPrice, winnerId);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } else if (line.startsWith(AuctionProtocol.HISTORY_BEGIN)) {
                    // HISTORY_BEGIN <auctionId> <count>
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        currentHistoryAuctionId = parts[1];
                        currentHistoryBuffer = new ArrayList<>();
                    }
                } else if (line.startsWith("TX ") && currentHistoryBuffer != null) {
                    // TX <id> <auctionId> <bidderId> <amount> <timestamp>
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 5) {
                        try {
                            String txId = parts[1];
                            String aucId = parts[2];
                            String bidderId = parts[3];
                            double amt = Double.parseDouble(parts[4].replace(',', '.'));
                            BidTransaction tx = new BidTransaction(txId, aucId, bidderId, amt);
                            if (parts.length >= 6) {
                                tx.setTimestamp(java.time.LocalDateTime.parse(parts[5]));
                            }
                            currentHistoryBuffer.add(tx);
                        } catch (Exception ignored) {
                        }
                    }
                } else if (line.startsWith(AuctionProtocol.HISTORY_END)) {
                    // HISTORY_END <auctionId>
                    String[] parts = line.split("\\s+");
                    String aucId = parts.length >= 2 ? parts[1] : currentHistoryAuctionId;
                    List<BidTransaction> history = (currentHistoryBuffer != null) ? currentHistoryBuffer
                            : Collections.emptyList();
                    for (AuctionEventListener l : listeners) {
                        try {
                            l.onHistoryReceived(aucId, history);
                        } catch (Exception ignored) {
                        }
                    }
                    currentHistoryBuffer = null;
                    currentHistoryAuctionId = null;
                }
            }
        } catch (IOException e) {
            if (connected) {
                disconnectedReason = "Connection lost: " + e.getMessage();
                System.out.printf("[AuctionClient] Connection lost: %s%n", e.getMessage());
            }
        } finally {
            if (connected) {
                for (AuctionEventListener listener : listeners) {
                    try {
                        listener.onDisconnected();
                    } catch (Exception ignored) {
                    }
                }
            }
            disconnect();
            if (unexpectedDisconnect) {
                for (AuctionEventListener listener : listeners) {
                    try {
                        listener.onDisconnected(disconnectedReason);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
    private AuctionSummary parseAuctionLine(String line) {

        String[] parts = line.trim().split("\\s+");

        if (parts.length < 7 || !parts[0].equals("AUCTION")) {
            throw new IllegalArgumentException("Sai định dạng AUCTION");
        }
        // E.g AUCTION AUC-001 Laptop Dell XPS 15 25000000.00 500000.00 BIDDER-01 ACTIVE
        // Tránh sản phẩm có dấu cách ở giữa
        int currentPriceIndex = parts.length - 4;

        String auctionId = parts[1];

        String itemId = String.join(
                " ",
                java.util.Arrays.copyOfRange(
                        parts,
                        2,
                        currentPriceIndex
                )
        );

        try {
            double currentPrice = Double.parseDouble(
                    parts[currentPriceIndex].replace(',', '.')
            );

            double stepPrice = Double.parseDouble(
                    parts[currentPriceIndex + 1].replace(',', '.')
            );

            String highestBidderId = parts[currentPriceIndex + 2];
            String status = parts[currentPriceIndex + 3];

            return new AuctionSummary(
                    auctionId,
                    itemId,
                    currentPrice,
                    stepPrice,
                    highestBidderId,
                    status
            );

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Giá đấu không đúng định dạng", e
            );
        }
    }

    // Khi sever đóng kết nối hoặc mạng bị gián đoạn, vòng lặp nhận dữ liệu sẽ kết
    // thúc
    // finally sẽ luôn thực thi, nếu còn connected, tức kết thúc bất ngờ
    // Client gọi onDisconnected cho tất cả listener vag disconnect() để giải phóng
    // Socket
    public synchronized void sendRaw(String command) {
        if (!connected || out == null) {
            throw new IllegalStateException("Client is not connected to server");
        }
        out.println(command);
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
        if (!connected) {
            return;
        }
        connected = false;

        try {
            if (out != null) {
                out.println(AuctionProtocol.CMD_QUIT);
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
}
