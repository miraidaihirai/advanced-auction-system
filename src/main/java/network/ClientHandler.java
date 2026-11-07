package network;

import com.auction.exceptions.AuctionClosedException;
import com.auction.exceptions.AuthenticationException;
import com.auction.exceptions.InvalidBidException;
import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.Admin;
import com.auction.model.User;
import com.auction.observer.AuctionObserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Xử lý kết nối từ một Client trong luồng riêng biệt (Multi-threaded).
 * Đồng thời là một AuctionObserver: nhận thông báo từ Auction và broadcast
 * realtime qua Socket.
 */
public class ClientHandler implements Runnable, AuctionObserver {

    public static final int DEFAULT_SO_TIMEOUT_MS = 60_000;

    private final Socket socket;
    private final AuctionServer server;
    private int soTimeoutMillis = DEFAULT_SO_TIMEOUT_MS;
    private BufferedReader in;
    private PrintWriter out;

    private User currentUser;
    private final Set<String> subscribedAuctions = ConcurrentHashMap.newKeySet();
    private volatile boolean running = true;

    public ClientHandler(Socket socket, AuctionServer server) {
        this(socket, server, DEFAULT_SO_TIMEOUT_MS);
    }

    public ClientHandler(Socket socket, AuctionServer server, int soTimeoutMillis) {
        this.socket = socket;
        this.server = server;
        this.soTimeoutMillis = soTimeoutMillis;
    }

    @Override
    public void run() {
        try {
            if (soTimeoutMillis > 0) {
                socket.setSoTimeout(soTimeoutMillis);
            }
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);

            sendMessage(AuctionProtocol.formatSuccess("CONNECTED", "Welcome to Online Auction Server!"));

            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                handleCommand(line);
            }
        } catch (SocketTimeoutException e) {
            if (running) {
                System.out.printf("[ClientHandler] Client %s timed out (heartbeat failure): %s%n",
                        getClientInfo(), e.getMessage());
            }
        } catch (IOException e) {
            if (running) {
                System.out.printf("[ClientHandler] Client %s disconnected unexpectedly: %s%n",
                        getClientInfo(), e.getMessage());
            }
        } finally {
            close();
        }
    }

    /**
     * Phân tích và điều hướng xử lý các lệnh theo AuctionProtocol.
     */
    private void handleCommand(String line) {
        String[] parts = line.split("\\s+");
        String command = parts[0].toUpperCase();

        try {
            switch (command) {
                case AuctionProtocol.CMD_LOGIN:
                    handleLogin(parts);
                    break;
                case AuctionProtocol.CMD_REGISTER:
                    handleRegister(parts);
                    break;
                case AuctionProtocol.CMD_LIST:
                    handleList();
                    break;
                case AuctionProtocol.CMD_JOIN:
                    handleJoin(parts);
                    break;
                case AuctionProtocol.CMD_LEAVE:
                    handleLeave(parts);
                    break;
                case AuctionProtocol.CMD_BID:
                    handleBid(parts);
                    break;
                case AuctionProtocol.CMD_START:
                    handleStart(parts);
                    break;
                case AuctionProtocol.CMD_CLOSE:
                    handleClose(parts);
                    break;
                case AuctionProtocol.CMD_PAY:
                    handlePay(parts);
                    break;
                case AuctionProtocol.CMD_PING:
                    sendMessage(AuctionProtocol.RES_PONG);
                    break;
                case AuctionProtocol.CMD_INFO:
                    handleInfo(parts);
                    break;
                case AuctionProtocol.CMD_HISTORY:
                    handleHistory(parts);
                    break;
                case AuctionProtocol.CMD_QUIT:
                    sendMessage(AuctionProtocol.formatSuccess("BYE", "Goodbye!"));
                    running = false;
                    break;
                default:
                    sendMessage(AuctionProtocol.formatError("Unknown command: " + command));
            }
        } catch (Exception e) {
            sendMessage(AuctionProtocol.formatError(e.getMessage()));
        }
    }

    private void handleLogin(String[] parts) {
        if (parts.length < 3) {
            sendMessage(AuctionProtocol.formatError("Usage: LOGIN <userId> <password>"));
            return;
        }
        String userId = parts[1];
        String password = parts[2];

        User user = AuctionManager.getInstance().authenticate(userId, password);
        if (user != null) {
            this.currentUser = user;
            sendMessage(AuctionProtocol.formatSuccess(AuctionProtocol.CMD_LOGIN,
                    user.getId() + " " + user.getName()));
            System.out.printf("[ClientHandler] Client %s logged in as %s%n", getClientInfo(), userId);
        } else {
            sendMessage(AuctionProtocol.formatError("Invalid user credentials"));
        }
    }

    private void handleRegister(String[] parts) {
        if (parts.length < 5) {
            sendMessage(AuctionProtocol.formatError("Usage: REGISTER <userId> <name> <password> <BIDDER|SELLER|ADMIN>"));
            return;
        }
        String userId = parts[1];
        String name = parts[2];
        String password = parts[3];
        String role = parts[4].toUpperCase();

        User newUser;
        if ("SELLER".equals(role)) {
            newUser = new Seller(userId, name, password);
        } else if ("ADMIN".equals(role)) {
            newUser = new Admin(userId, name, password);
        } else {
            newUser = new Bidder(userId, name, password);
        }

        try {
            AuctionManager.getInstance().registerUser(newUser);
            this.currentUser = newUser;
            sendMessage(AuctionProtocol.formatSuccess(AuctionProtocol.CMD_REGISTER, userId));
            System.out.printf("[ClientHandler] Registered new user: %s (%s)%n", userId, role);
        } catch (IllegalArgumentException e) {
            sendMessage(AuctionProtocol.formatError(e.getMessage()));
        }
    }

    private void handleList() {
        sendMessage(AuctionProtocol.AUCTIONS_BEGIN);
        for (Auction a : AuctionManager.getInstance().getAllAuctions()) {
            String winner = a.getHighestBidderId() != null ? a.getHighestBidderId() : "NONE";
            sendMessage(String.format(Locale.US, "AUCTION %s %s %.2f %.2f %s %s",
                    a.getId(), a.getItemId(), a.getCurrentPrice(), a.getStepPrice(), winner, a.getStatus()));
        }
        sendMessage(AuctionProtocol.AUCTIONS_END);
    }

    private void handleJoin(String[] parts) {
        if (parts.length < 2) {
            sendMessage(AuctionProtocol.formatError("Usage: JOIN <auctionId>"));
            return;
        }
        String auctionId = parts[1];
        Auction auction = AuctionManager.getInstance().getAuction(auctionId);
        if (auction == null) {
            sendMessage(AuctionProtocol.formatError("Auction not found: " + auctionId));
            return;
        }

        subscribedAuctions.add(auctionId);
        auction.addObserver(this);
        sendMessage(AuctionProtocol.formatSuccess(AuctionProtocol.CMD_JOIN,
                String.format(Locale.US, "%s %s %.2f %.2f %s",
                        auction.getId(),
                        auction.getItemId(),
                        auction.getCurrentPrice(),
                        auction.getStepPrice(),
                        auction.getStatus())));
        System.out.printf("[ClientHandler] Client %s joined auction %s%n", getClientInfo(), auctionId);
    }

    private void handleLeave(String[] parts) {
        if (parts.length < 2) {
            sendMessage(AuctionProtocol.formatError("Usage: LEAVE <auctionId>"));
            return;
        }
        String auctionId = parts[1];
        if (subscribedAuctions.remove(auctionId)) {
            Auction auction = AuctionManager.getInstance().getAuction(auctionId);
            if (auction != null) {
                auction.removeObserver(this);
            }
            sendMessage(AuctionProtocol.formatSuccess(AuctionProtocol.CMD_LEAVE, auctionId));
            System.out.printf("[ClientHandler] Client %s left auction %s%n", getClientInfo(), auctionId);
        } else {
            sendMessage(AuctionProtocol.formatError("Not joined in auction: " + auctionId));
        }
    }

    private void handleBid(String[] parts) {
        if (parts.length < 3) {
            sendMessage(AuctionProtocol.formatError("Usage: BID <auctionId> <amount>"));
            return;
        }
        if (currentUser == null) {
            sendMessage(AuctionProtocol.formatError("You must LOGIN first to place a bid"));
            return;
        }
        if (!(currentUser instanceof Bidder)) {
            sendMessage(AuctionProtocol.formatError("Only Bidders can place bids"));
            return;
        }

        String auctionId = parts[1];
        double amount;
        try {
            amount = Double.parseDouble(parts[2].replace(',', '.'));
        } catch (NumberFormatException e) {
            sendMessage(AuctionProtocol.formatError("Invalid bid amount: " + parts[2]));
            return;
        }

        try {
            boolean success = AuctionManager.getInstance().placeBid(
                    auctionId, currentUser.getId(), currentUser.getPassword(), amount);
            if (success) {
                if (subscribedAuctions.add(auctionId)) {
                    Auction auction = AuctionManager.getInstance().getAuction(auctionId);
                    if (auction != null) {
                        auction.addObserver(this);
                    }
                }
                sendMessage(AuctionProtocol.formatSuccess(AuctionProtocol.CMD_BID,
                        String.format(Locale.US, "%s %.2f", auctionId, amount)));
            } else {
                sendMessage(AuctionProtocol.formatError("Bid was not accepted"));
            }
        } catch (InvalidBidException | AuctionClosedException | AuthenticationException e) {
            sendMessage(AuctionProtocol.formatError(e.getMessage()));
        }
    }

    private void handleStart(String[] parts) {
        if (parts.length < 2) {
            sendMessage(AuctionProtocol.formatError("Usage: START <auctionId> [durationSeconds]"));
            return;
        }
        String auctionId = parts[1];
        Auction auction = AuctionManager.getInstance().getAuction(auctionId);
        if (auction == null) {
            sendMessage(AuctionProtocol.formatError("Auction not found: " + auctionId));
            return;
        }

        try {
            if (parts.length >= 3) {
                int seconds = Integer.parseInt(parts[2]);
                auction.startAuction(Duration.ofSeconds(seconds));
            } else {
                auction.startAuction();
            }
            if (subscribedAuctions.add(auctionId)) {
                auction.addObserver(this);
            }
            sendMessage(AuctionProtocol.formatSuccess(AuctionProtocol.CMD_START, auctionId));
        } catch (Exception e) {
            sendMessage(AuctionProtocol.formatError(e.getMessage()));
        }
    }

    private void handleClose(String[] parts) {
        if (parts.length < 2) {
            sendMessage(AuctionProtocol.formatError("Usage: CLOSE <auctionId>"));
            return;
        }
        if (currentUser == null) {
            sendMessage(AuctionProtocol.formatError("You must LOGIN first"));
            return;
        }
        String auctionId = parts[1];
        Auction auction = AuctionManager.getInstance().getAuction(auctionId);
        if (auction == null) {
            sendMessage(AuctionProtocol.formatError("Auction not found: " + auctionId));
            return;
        }
        boolean isSeller = currentUser instanceof Seller && currentUser.getId().equals(auction.getSellerId());
        boolean isAdmin = currentUser instanceof Admin;
        if (!isSeller && !isAdmin) {
            sendMessage(AuctionProtocol.formatError("Only the Seller or Admin can close this auction"));
            return;
        }

        try {
            auction.finishAuction();
            if (AuctionManager.getInstance().isAutoSave()) {
                AuctionManager.getInstance().saveState();
            }
            sendMessage(AuctionProtocol.formatSuccess(AuctionProtocol.CMD_CLOSE, auctionId));
        } catch (Exception e) {
            sendMessage(AuctionProtocol.formatError(e.getMessage()));
        }
    }

    private void handlePay(String[] parts) {
        if (parts.length < 2) {
            sendMessage(AuctionProtocol.formatError("Usage: PAY <auctionId>"));
            return;
        }
        if (currentUser == null) {
            sendMessage(AuctionProtocol.formatError("You must LOGIN first to pay"));
            return;
        }
        String auctionId = parts[1];
        Auction auction = AuctionManager.getInstance().getAuction(auctionId);
        if (auction == null) {
            sendMessage(AuctionProtocol.formatError("Auction not found: " + auctionId));
            return;
        }
        if (auction.getStatus() != Auction.Status.FINISHED) {
            sendMessage(AuctionProtocol.formatError("Only FINISHED auctions can be paid (current: " + auction.getStatus() + ")"));
            return;
        }
        boolean isWinner = currentUser.getId().equals(auction.getHighestBidderId());
        boolean isAdmin = currentUser instanceof Admin;
        if (!isWinner && !isAdmin) {
            sendMessage(AuctionProtocol.formatError("Only the winning bidder can make payment for this auction"));
            return;
        }

        try {
            sendMessage(AuctionProtocol.formatSuccess(AuctionProtocol.CMD_PAY,
                    String.format(Locale.US, "%s %.2f", auctionId, auction.getCurrentPrice())));
            auction.markPaid();
            if (AuctionManager.getInstance().isAutoSave()) {
                AuctionManager.getInstance().saveState();
            }
        } catch (Exception e) {
            sendMessage(AuctionProtocol.formatError(e.getMessage()));
        }
    }

    private void handleInfo(String[] parts) {
        if (parts.length < 2) {
            sendMessage(AuctionProtocol.formatError("Usage: INFO <auctionId>"));
            return;
        }
        String auctionId = parts[1];
        Auction a = AuctionManager.getInstance().getAuction(auctionId);
        if (a == null) {
            sendMessage(AuctionProtocol.formatError("Auction not found: " + auctionId));
            return;
        }
        String winner = a.getHighestBidderId() != null ? a.getHighestBidderId() : "NONE";
        sendMessage(String.format(Locale.US, "AUCTION_INFO %s %s %s %.2f %.2f %s %s",
                a.getId(), a.getItemId(), a.getSellerId(), a.getCurrentPrice(), a.getStepPrice(), winner,
                a.getStatus()));
    }

    private void handleHistory(String[] parts) {
        if (parts.length < 2) {
            sendMessage(AuctionProtocol.formatError("Usage: HISTORY <auctionId>"));
            return;
        }
        String auctionId = parts[1];
        Auction auction = AuctionManager.getInstance().getAuction(auctionId);
        if (auction == null) {
            sendMessage(AuctionProtocol.formatError("Auction not found: " + auctionId));
            return;
        }

        List<BidTransaction> history = auction.getBidHistory();
        sendMessage(String.format("%s %s %d", AuctionProtocol.HISTORY_BEGIN, auctionId, history.size()));
        for (BidTransaction tx : history) {
            sendMessage(AuctionProtocol.formatTransactionRecord(tx));
        }
        sendMessage(String.format("%s %s", AuctionProtocol.HISTORY_END, auctionId));
    }

    /**
     * Observer Pattern Callback: Tự động bắn thông báo realtime qua Socket khi có
     * lượt bid mới.
     */
    @Override
    public void onNewBid(Auction auction, BidTransaction bid) {
        if (!running) {
            return;
        }
        if (subscribedAuctions.contains(auction.getId())) {
            String notification = AuctionProtocol.formatBidNotification(
                    auction.getId(),
                    bid.getAmount(),
                    bid.getBidderId(),
                    auction.getItemId());
            sendMessage(notification);
        }
    }

    /**
     * Observer Pattern Callback: Tự động bắn thông báo realtime khi phiên đấu giá
     * kết thúc.
     * Gửi thông báo riêng đến Seller và Winner, kèm thông báo phòng chung cho các client đang xem.
     */
    @Override
    public void onAuctionClosed(Auction auction) {
        if (!running) {
            return;
        }
        String winner = auction.getHighestBidderId() != null ? auction.getHighestBidderId() : "NONE";

        // 1. Thông báo riêng cho Winner
        if (currentUser != null && auction.getHighestBidderId() != null
                && currentUser.getId().equals(auction.getHighestBidderId())) {
            sendMessage(AuctionProtocol.formatWinnerNotification(
                    auction.getId(),
                    auction.getCurrentPrice(),
                    auction.getItemId()));
        }

        // 2. Thông báo riêng cho Seller
        if (currentUser != null && auction.getSellerId() != null
                && currentUser.getId().equals(auction.getSellerId())) {
            sendMessage(AuctionProtocol.formatSellerNotification(
                    auction.getId(),
                    auction.getCurrentPrice(),
                    winner,
                    auction.getItemId()));
        }

        // 3. Thông báo phòng chung cho các Client đang theo dõi phiên
        if (subscribedAuctions.contains(auction.getId())) {
            String notification = AuctionProtocol.formatClosedNotification(
                    auction.getId(),
                    auction.getCurrentPrice(),
                    winner);
            sendMessage(notification);
        }
    }

    /**
     * Observer Pattern Callback: Tự động bắn thông báo khi phiên đã thanh toán (PAID).
     */
    @Override
    public void onAuctionPaid(Auction auction) {
        if (!running) {
            return;
        }
        String winner = auction.getHighestBidderId() != null ? auction.getHighestBidderId() : "NONE";
        if (subscribedAuctions.contains(auction.getId())
                || (currentUser != null && (currentUser.getId().equals(auction.getHighestBidderId())
                        || currentUser.getId().equals(auction.getSellerId())))) {
            sendMessage(AuctionProtocol.formatPaidNotification(
                    auction.getId(),
                    auction.getCurrentPrice(),
                    winner));
        }
    }

    /**
     * Gửi bản tin tới Socket client đảm bảo thread-safe.
     */
    public synchronized void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * Dọn dẹp tài nguyên khi client ngắt kết nối.
     * Tự động gỡ đăng ký Observers, dọn Session trên Server và đóng Socket/Stream.
     */
    public void close() {
        if (!running) {
            return;
        }
        running = false;

        // Tự động hủy đăng ký observer tại tất cả các phiên client đã tham gia
        for (String auctionId : subscribedAuctions) {
            Auction auction = AuctionManager.getInstance().getAuction(auctionId);
            if (auction != null) {
                auction.removeObserver(this);
            }
        }
        subscribedAuctions.clear();

        if (server != null) {
            server.removeClient(this);
        }

        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }
        if (out != null) {
            out.close();
        }
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        System.out.printf("[ClientHandler] Client %s closed and cleaned up%n", getClientInfo());
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public Set<String> getSubscribedAuctions() {
        return Collections.unmodifiableSet(subscribedAuctions);
    }

    public boolean isSubscribed(String auctionId) {
        return subscribedAuctions.contains(auctionId);
    }

    public boolean isRunning() {
        return running;
    }

    public int getSoTimeout() {
        return soTimeoutMillis;
    }

    public void setSoTimeout(int timeoutMillis) {
        this.soTimeoutMillis = timeoutMillis;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.setSoTimeout(Math.max(0, timeoutMillis));
            } catch (IOException ignored) {
            }
        }
    }

    public String getClientInfo() {
        return currentUser != null ? currentUser.getId() : socket.getRemoteSocketAddress().toString();
    }
}

