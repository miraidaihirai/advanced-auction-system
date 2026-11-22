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

        default void onProfileReceived(UserProfile profile) {
        }

        default void onAuctionDetailsReceived(List<AuctionDetail> auctions) {
        }

        default void onWalletHistoryReceived(List<WalletTxDTO> transactions) {
        }

        default void onUsersListReceived(List<UserProfile> users) {
        }

        default void onCatalogRefresh() {
        }

        default void onChatMessageReceived(
                String auctionId,
                String senderId,
                String senderName,
                String role,
                String message) {
        }

        default void onChatHistoryReceived(String auctionId, List<ChatMessageDTO> messages) {
        }
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
        List<AuctionDetail> currentFullAuctionBuffer = null;
        List<WalletTxDTO> currentWalletBuffer = null;
        List<UserProfile> currentUsersBuffer = null;
        List<ChatMessageDTO> currentChatHistoryBuffer = null;
        String currentChatHistoryAuctionId = null;
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
                } else if (line.equals(AuctionProtocol.AUCTIONS_FULL_BEGIN)) {
                    currentFullAuctionBuffer = new ArrayList<>();
                } else if (line.startsWith(AuctionProtocol.AUCTION_FULL_PREFIX) && currentFullAuctionBuffer != null) {
                    try {
                        currentFullAuctionBuffer.add(parseAuctionDetailLine(line));
                    } catch (IllegalArgumentException e) {
                        System.err.println("[AuctionClient] Dữ liệu AUCTION_FULL không hợp lệ: " + line);
                    }
                } else if (line.equals(AuctionProtocol.AUCTIONS_FULL_END) && currentFullAuctionBuffer != null) {
                    List<AuctionDetail> detailList = List.copyOf(currentFullAuctionBuffer);
                    for (AuctionEventListener listener : listeners) {
                        try {
                            listener.onAuctionDetailsReceived(detailList);
                        } catch (Exception ignored) {
                        }
                    }
                    currentFullAuctionBuffer = null;
                } else if (line.startsWith(AuctionProtocol.PROFILE_DATA_PREFIX)) {
                    try {
                        UserProfile profile = parseProfileLine(line);
                        for (AuctionEventListener listener : listeners) {
                            try {
                                listener.onProfileReceived(profile);
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        System.err.println("[AuctionClient] Dữ liệu PROFILE_DATA không hợp lệ: " + line);
                    }
                } else if (line.equals(AuctionProtocol.WALLET_BEGIN)) {
                    currentWalletBuffer = new ArrayList<>();
                } else if (line.startsWith(AuctionProtocol.WALLET_TX_PREFIX) && currentWalletBuffer != null) {
                    try {
                        currentWalletBuffer.add(parseWalletTxLine(line));
                    } catch (IllegalArgumentException e) {
                        System.err.println("[AuctionClient] Dữ liệu WALLET_TX không hợp lệ: " + line);
                    }
                } else if (line.equals(AuctionProtocol.WALLET_END) && currentWalletBuffer != null) {
                    List<WalletTxDTO> walletList = List.copyOf(currentWalletBuffer);
                    for (AuctionEventListener listener : listeners) {
                        try {
                            listener.onWalletHistoryReceived(walletList);
                        } catch (Exception ignored) {
                        }
                    }
                    currentWalletBuffer = null;
                } else if (line.equals(AuctionProtocol.USERS_BEGIN)) {
                    currentUsersBuffer = new ArrayList<>();
                } else if (line.startsWith(AuctionProtocol.USER_ROW_PREFIX) && currentUsersBuffer != null) {
                    try {
                        currentUsersBuffer.add(parseUserRowLine(line));
                    } catch (IllegalArgumentException e) {
                        System.err.println("[AuctionClient] Dữ liệu USER_ROW không hợp lệ: " + line);
                    }
                } else if (line.equals(AuctionProtocol.USERS_END) && currentUsersBuffer != null) {
                    List<UserProfile> usersList = List.copyOf(currentUsersBuffer);
                    for (AuctionEventListener listener : listeners) {
                        try {
                            listener.onUsersListReceived(usersList);
                        } catch (Exception ignored) {
                        }
                    }
                    currentUsersBuffer = null;
                } else if (line.equals(AuctionProtocol.NOTIFY_CATALOG_REFRESH)) {
                    for (AuctionEventListener listener : listeners) {
                        try {
                            listener.onCatalogRefresh();
                        } catch (Exception ignored) {
                        }
                    }
                } else if (line.startsWith(AuctionProtocol.CHAT_HISTORY_BEGIN)) {
                    String[] parts = line.split("\\s+", 2);
                    currentChatHistoryAuctionId = parts.length >= 2 ? parts[1].trim() : "";
                    currentChatHistoryBuffer = new ArrayList<>();
                } else if (line.startsWith(AuctionProtocol.CHAT_ROW_PREFIX) && currentChatHistoryBuffer != null) {
                    String payload = line.substring(AuctionProtocol.CHAT_ROW_PREFIX.length());
                    String[] parts = payload.split("\\|", 6);
                    if (parts.length >= 6) {
                        currentChatHistoryBuffer.add(new ChatMessageDTO(
                                parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]));
                    }
                } else if (line.equals(AuctionProtocol.CHAT_HISTORY_END) && currentChatHistoryBuffer != null) {
                    List<ChatMessageDTO> chatList = List.copyOf(currentChatHistoryBuffer);
                    String histAucId = currentChatHistoryAuctionId != null ? currentChatHistoryAuctionId : "";
                    for (AuctionEventListener listener : listeners) {
                        try {
                            listener.onChatHistoryReceived(histAucId, chatList);
                        } catch (Exception ignored) {
                        }
                    }
                    currentChatHistoryBuffer = null;
                    currentChatHistoryAuctionId = null;
                } else if (line.startsWith(AuctionProtocol.NOTIFY_CHAT_PREFIX)) {
                    String payload = line.substring(AuctionProtocol.NOTIFY_CHAT_PREFIX.length());
                    String[] parts = payload.split("\\|", 5);
                    if (parts.length >= 5) {
                        for (AuctionEventListener listener : listeners) {
                            try {
                                listener.onChatMessageReceived(parts[0], parts[1], parts[2], parts[3], parts[4]);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                // AUCTIONS_BIGINS tạo bộ đệm mới
                // AUCTION gọi parseAuctionLine() để chuyển String[] thành AuctionSummary
                // AUCTIONS_END tạo danh sách bất biến bằng List.copyOf() rồi gửi cho listener
                // Cuối cùng reset bộ đệm về null để kết thúc nhận dữ liệu

                // Parse realtime broadcast notifications
                try {
                    if (line.startsWith(AuctionProtocol.NOTIFY_BID_PREFIX)) {
                        // NOTIFY BID <auctionId> <newPrice> <bidderId> <itemId>
                        String[] parts = line.split("\\s+", 6);
                        // Tách giới hạn còn 6 thành phần để lấy cả tên những sản phẩm có dấu cách
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
                } catch (NumberFormatException e) {
                    System.err.println("[AuctionClient] Dữ liệu số không hợp lệ từ Server: " + line);
                }
            }
        } catch (IOException e) {
            if (connected) {
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

    public void requestProfile() {
        sendRaw(AuctionProtocol.CMD_PROFILE);
    }

    public void requestFullList() {
        sendRaw(AuctionProtocol.CMD_LIST_FULL);
    }

    public void requestAuction(
            String itemName,
            String category,
            double startPrice,
            double stepPrice,
            long durationSeconds,
            String imageKey,
            String description) {
        String payload = String.format(Locale.US, "%s|%s|%.2f|%.2f|%d|%s|%s",
                AuctionProtocol.sanitizeField(itemName),
                AuctionProtocol.sanitizeField(category),
                startPrice,
                stepPrice,
                durationSeconds,
                AuctionProtocol.sanitizeField(imageKey),
                AuctionProtocol.sanitizeField(description));
        sendRaw(AuctionProtocol.CMD_REQUEST_AUCTION + " " + payload);
    }

    public void approveAuction(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_APPROVE_AUCTION, auctionId));
    }

    public void approveAuction(String auctionId, long durationSeconds) {
        sendRaw(String.format("%s %s %d", AuctionProtocol.CMD_APPROVE_AUCTION, auctionId, durationSeconds));
    }

    public void rejectAuction(String auctionId, String reason) {
        sendRaw(String.format("%s %s|%s",
                AuctionProtocol.CMD_REJECT_AUCTION,
                AuctionProtocol.sanitizeField(auctionId),
                AuctionProtocol.sanitizeField(reason)));
    }

    public void configureAutoBid(String auctionId, double maxBid, double increment) {
        sendRaw(String.format(Locale.US, "%s %s %.2f %.2f",
                AuctionProtocol.CMD_AUTOBID, auctionId, maxBid, increment));
    }

    public void cancelAutoBid(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_AUTOBID_OFF, auctionId));
    }

    public void requestWalletHistory() {
        sendRaw(AuctionProtocol.CMD_WALLET_HISTORY);
    }

    public void topUpWallet(double amount) {
        sendRaw(String.format(Locale.US, "%s %.2f", AuctionProtocol.CMD_TOPUP, amount));
    }

    public void topUpWallet(double amount, String channel) {
        String safeChannel = AuctionProtocol.sanitizeField(channel);
        if (safeChannel.isEmpty()) {
            topUpWallet(amount);
            return;
        }
        sendRaw(String.format(Locale.US, "%s %.2f %s", AuctionProtocol.CMD_TOPUP, amount, safeChannel));
    }

    public void withdrawWallet(double amount, String destination) {
        String safeDest = AuctionProtocol.sanitizeField(destination);
        if (safeDest.isEmpty()) {
            sendRaw(String.format(Locale.US, "%s %.2f", AuctionProtocol.CMD_WITHDRAW, amount));
            return;
        }
        sendRaw(String.format(Locale.US, "%s %.2f %s", AuctionProtocol.CMD_WITHDRAW, amount, safeDest));
    }

    public void requestUsersList() {
        sendRaw(AuctionProtocol.CMD_LIST_USERS);
    }

    public void sendRoomChat(String auctionId, String message) {
        sendRaw(String.format("%s %s|%s",
                AuctionProtocol.CMD_CHAT,
                AuctionProtocol.sanitizeField(auctionId),
                AuctionProtocol.sanitizeField(message)));
    }

    public void requestChatHistory(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_CHAT_HISTORY, AuctionProtocol.sanitizeField(auctionId)));
    }

    private UserProfile parseProfileLine(String line) {
        String payload = line.substring(AuctionProtocol.PROFILE_DATA_PREFIX.length());
        String[] parts = payload.split("\\|", 4);
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid PROFILE_DATA");
        }
        return new UserProfile(
                parts[0],
                parts[3],
                parts[1],
                Double.parseDouble(parts[2].replace(',', '.')));
    }

    private UserProfile parseUserRowLine(String line) {
        String payload = line.substring(AuctionProtocol.USER_ROW_PREFIX.length());
        String[] parts = payload.split("\\|", 4);
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid USER_ROW");
        }
        return new UserProfile(
                parts[0],
                parts[1],
                parts[2],
                Double.parseDouble(parts[3].replace(',', '.')));
    }

    private AuctionDetail parseAuctionDetailLine(String line) {
        String payload = line.substring(AuctionProtocol.AUCTION_FULL_PREFIX.length());
        String[] parts = payload.split("\\|", 17);
        if (parts.length < 17) {
            throw new IllegalArgumentException("Invalid AUCTION_FULL");
        }
        return new AuctionDetail(
                parts[0],
                parts[1],
                parts[2],
                parts[3],
                parts[4],
                Double.parseDouble(parts[5].replace(',', '.')),
                Double.parseDouble(parts[6].replace(',', '.')),
                Double.parseDouble(parts[7].replace(',', '.')),
                parts[8],
                parts[9],
                Long.parseLong(parts[10]),
                Long.parseLong(parts[11]),
                Integer.parseInt(parts[12]),
                "1".equals(parts[13]),
                parts[14],
                parts[15],
                parts[16]);
    }

    private WalletTxDTO parseWalletTxLine(String line) {
        String payload = line.substring(AuctionProtocol.WALLET_TX_PREFIX.length());
        String[] parts = payload.split("\\|", 9);
        if (parts.length < 9) {
            throw new IllegalArgumentException("Invalid WALLET_TX");
        }
        return new WalletTxDTO(
                parts[0],
                parts[1],
                parts[2],
                parts[3],
                parts[4],
                parts[5],
                Double.parseDouble(parts[6].replace(',', '.')),
                Double.parseDouble(parts[7].replace(',', '.')),
                parts[8]);
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
