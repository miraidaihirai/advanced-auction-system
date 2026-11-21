package network;

import com.auction.exceptions.AuctionClosedException;
import com.auction.exceptions.AuthenticationException;
import com.auction.exceptions.InvalidBidException;
import com.auction.manager.AuctionManager;
import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Xử lý kết nối từ một Client trong luồng riêng biệt (Multi-threaded). Đồng thời là một
 * AuctionObserver: nhận thông báo từ Auction và broadcast realtime qua Socket.
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
  private final AtomicBoolean closed = new AtomicBoolean(false);

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
      socket.setTcpNoDelay(true);
      // Khởi tạo OutputStream trước và flush() để hoàn tất handshake với Client
      out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
      out.flush();
      in =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

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
        System.out.printf(
            "[ClientHandler] Client %s timed out (heartbeat failure): %s%n",
            getClientInfo(), e.getMessage());
      }
    } catch (IOException e) {
      if (running) {
        System.out.printf(
            "[ClientHandler] Client %s disconnected unexpectedly: %s%n",
            getClientInfo(), e.getMessage());
      }
    } finally {
      close();
    }
  }

  /** Phân tích và điều hướng xử lý các lệnh theo AuctionProtocol. */
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
        case AuctionProtocol.CMD_PROFILE:
          handleProfile();
          break;
        case AuctionProtocol.CMD_LIST_FULL:
          handleListFull();
          break;
        case AuctionProtocol.CMD_REQUEST_AUCTION:
          handleRequestAuction(line);
          break;
        case AuctionProtocol.CMD_APPROVE_AUCTION:
          handleApproveAuction(parts);
          break;
        case AuctionProtocol.CMD_REJECT_AUCTION:
          handleRejectAuction(line);
          break;
        case AuctionProtocol.CMD_QUIT:
          sendMessage(AuctionProtocol.formatSuccess("BYE", "Goodbye!"));
          running = false;
          break;
        default:
          sendMessage(AuctionProtocol.formatError("Unknown command: " + command));
      }
    } catch (Exception e) {
      if (AuctionProtocol.CMD_BID.equals(command)) {
        sendBidError(e.getMessage());
      } else {
        sendMessage(AuctionProtocol.formatError(e.getMessage()));
      }
    }
  }

  private void handleLogin(String[] parts) {
    try {
      if (parts.length < 3) {
        System.out.printf(
            "[ClientHandler] LOGIN FAILED: Missing parameters from %s%n", getClientInfo());
        sendMessage(AuctionProtocol.formatError("Usage: LOGIN <userId> <password>"));
        return;
      }
      String userId = parts[1];
      String password = parts[2];

      System.out.printf(
          "[ClientHandler] Received LOGIN request for userId='%s' from %s%n",
          userId, getClientInfo());
      User user = AuctionManager.getInstance().authenticate(userId, password);
      if (user != null) {
        this.currentUser = user;
        String successMsg =
            AuctionProtocol.formatSuccess(
                AuctionProtocol.CMD_LOGIN, user.getId() + " " + user.getName());
        sendMessage(successMsg);
        System.out.printf(
            "[ClientHandler] LOGIN SUCCESS: User '%s' (%s) authenticated successfully.%n",
            user.getId(), detectRole(user));
      } else {
        System.out.printf(
            "[ClientHandler] LOGIN FAILED: Invalid credentials for userId='%s'%n", userId);
        sendMessage(AuctionProtocol.formatError("Invalid user credentials"));
      }
    } catch (Throwable t) {
      System.err.printf("[ClientHandler] LOGIN ERROR exception: %s%n", t.getMessage());
      t.printStackTrace();
      sendMessage(AuctionProtocol.formatError("Server error during login: " + t.getMessage()));
    }
  }

  private void handleRegister(String[] parts) {
    if (parts.length < 5) {
      sendMessage(
          AuctionProtocol.formatError(
              "Usage: REGISTER <userId> <name> <password> <BIDDER|SELLER|ADMIN>"));
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
      sendMessage(
          String.format(
              Locale.US,
              "AUCTION %s %s %.2f %.2f %s %s",
              a.getId(),
              a.getItemId(),
              a.getCurrentPrice(),
              a.getStepPrice(),
              winner,
              a.getStatus()));
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
    sendMessage(
        AuctionProtocol.formatSuccess(
            AuctionProtocol.CMD_JOIN,
            String.format(
                Locale.US,
                "%s %s %.2f %.2f %s",
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
      sendBidError("Usage: BID <auctionId> <amount>");
      return;
    }

    if (currentUser == null) {
      sendBidError("You must LOGIN first to place a bid");
      return;
    }

    if (!(currentUser instanceof Bidder)) {
      sendBidError("Only Bidders can place bids");
      return;
    }

    String auctionId = parts[1];
    double amount;

    try {
      amount = Double.parseDouble(parts[2].replace(',', '.'));
    } catch (NumberFormatException e) {
      sendBidError("Invalid bid amount: " + parts[2]);
      return;
    }

    try {
      boolean success =
          AuctionManager.getInstance()
              .placeBid(auctionId, currentUser.getId(), currentUser.getPassword(), amount);

      if (success) {
        if (subscribedAuctions.add(auctionId)) {
          Auction auction = AuctionManager.getInstance().getAuction(auctionId);

          if (auction != null) {
            auction.addObserver(this);
          }
        }

        sendMessage(
            AuctionProtocol.formatSuccess(
                AuctionProtocol.CMD_BID, String.format(Locale.US, "%s %.2f", auctionId, amount)));

      } else {
        sendBidError("Bid was not accepted");
      }

    } catch (InvalidBidException | AuctionClosedException | AuthenticationException e) {
      sendBidError(e.getMessage());
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
    boolean isSeller =
        currentUser instanceof Seller && currentUser.getId().equals(auction.getSellerId());
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
      sendMessage(
          AuctionProtocol.formatError(
              "Only FINISHED auctions can be paid (current: " + auction.getStatus() + ")"));
      return;
    }
    boolean isWinner = currentUser.getId().equals(auction.getHighestBidderId());
    boolean isAdmin = currentUser instanceof Admin;
    if (!isWinner && !isAdmin) {
      sendMessage(
          AuctionProtocol.formatError("Only the winning bidder can make payment for this auction"));
      return;
    }

    try {
      sendMessage(
          AuctionProtocol.formatSuccess(
              AuctionProtocol.CMD_PAY,
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
    sendMessage(
        String.format(
            Locale.US,
            "AUCTION_INFO %s %s %s %.2f %.2f %s %s",
            a.getId(),
            a.getItemId(),
            a.getSellerId(),
            a.getCurrentPrice(),
            a.getStepPrice(),
            winner,
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
    sendMessage(
        String.format("%s %s %d", AuctionProtocol.HISTORY_BEGIN, auctionId, history.size()));
    for (BidTransaction tx : history) {
      sendMessage(AuctionProtocol.formatTransactionRecord(tx));
    }
    sendMessage(String.format("%s %s", AuctionProtocol.HISTORY_END, auctionId));
  }

  private void handleProfile() {
    if (currentUser == null) {
      sendMessage(AuctionProtocol.formatError("You must LOGIN first"));
      return;
    }
    User fresh = AuctionManager.getInstance().getUser(currentUser.getId());
    if (fresh != null) {
      this.currentUser = fresh;
    }
    sendMessage(AuctionProtocol.formatProfileData(this.currentUser));
  }

  private void handleListFull() {
    AuctionManager manager = AuctionManager.getInstance();
    sendMessage(AuctionProtocol.AUCTIONS_FULL_BEGIN);
    for (Auction a : manager.getAllAuctions()) {
      User seller = manager.getUser(a.getSellerId());
      String sellerName = seller != null ? seller.getName() : a.getSellerId();
      sendMessage(AuctionProtocol.formatAuctionFull(a, sellerName));
    }
    sendMessage(AuctionProtocol.AUCTIONS_FULL_END);
  }

  private void handleRequestAuction(String rawLine) {
    if (currentUser == null) {
      sendMessage(AuctionProtocol.formatError("You must LOGIN first"));
      return;
    }
    if (!(currentUser instanceof Seller) && !(currentUser instanceof Admin)) {
      sendMessage(
          AuctionProtocol.formatError("Only Sellers or Admins can submit auction room requests"));
      return;
    }

    int firstSpace = rawLine.indexOf(' ');
    if (firstSpace < 0 || firstSpace >= rawLine.length() - 1) {
      sendMessage(
          AuctionProtocol.formatError(
              "Usage: REQUEST_AUCTION <itemName>|<category>|<startPrice>|<stepPrice>|<durationSec>|<imageKey>|<description>"));
      return;
    }

    String payload = rawLine.substring(firstSpace + 1).trim();
    String[] fields = payload.split("\\|", -1);
    if (fields.length < 5) {
      sendMessage(AuctionProtocol.formatError("Invalid auction request payload"));
      return;
    }

    try {
      String itemName = fields[0].trim();
      String category = fields[1].trim();
      double startPrice = Double.parseDouble(fields[2].trim().replace(',', '.'));
      double stepPrice = Double.parseDouble(fields[3].trim().replace(',', '.'));
      long durationSec = Long.parseLong(fields[4].trim());
      String imageKey = fields.length > 5 ? fields[5].trim() : "";
      String description = fields.length > 6 ? fields[6].trim() : "";

      Auction created =
          AuctionManager.getInstance()
              .submitAuctionRequest(
                  currentUser.getId(),
                  itemName,
                  category,
                  description,
                  startPrice,
                  stepPrice,
                  durationSec,
                  imageKey);

      sendMessage(
          AuctionProtocol.formatSuccess(AuctionProtocol.CMD_REQUEST_AUCTION, created.getId()));
      if (server != null) {
        server.broadcastToAll(AuctionProtocol.NOTIFY_CATALOG_REFRESH);
      }
    } catch (NumberFormatException e) {
      sendMessage(AuctionProtocol.formatError("Invalid numeric field in auction request"));
    } catch (Exception e) {
      sendMessage(AuctionProtocol.formatError(e.getMessage()));
    }
  }

  private void handleApproveAuction(String[] parts) {
    if (currentUser == null) {
      sendMessage(AuctionProtocol.formatError("You must LOGIN first"));
      return;
    }
    if (!(currentUser instanceof Admin)) {
      sendMessage(AuctionProtocol.formatError("Only Admins can approve auction requests"));
      return;
    }
    if (parts.length < 2) {
      sendMessage(
          AuctionProtocol.formatError("Usage: APPROVE_AUCTION <auctionId> [durationSeconds]"));
      return;
    }
    String auctionId = parts[1];
    Long overrideDuration = null;
    if (parts.length >= 3) {
      try {
        overrideDuration = Long.parseLong(parts[2]);
      } catch (NumberFormatException e) {
        sendMessage(AuctionProtocol.formatError("Invalid duration seconds: " + parts[2]));
        return;
      }
    }

    try {
      AuctionManager.getInstance()
          .approveAuctionRequest(currentUser.getId(), auctionId, overrideDuration);
      sendMessage(AuctionProtocol.formatSuccess(AuctionProtocol.CMD_APPROVE_AUCTION, auctionId));
      if (server != null) {
        server.broadcastToAll(AuctionProtocol.NOTIFY_CATALOG_REFRESH);
      }
    } catch (Exception e) {
      sendMessage(AuctionProtocol.formatError(e.getMessage()));
    }
  }

  private void handleRejectAuction(String rawLine) {
    if (currentUser == null) {
      sendMessage(AuctionProtocol.formatError("You must LOGIN first"));
      return;
    }
    if (!(currentUser instanceof Admin) && !(currentUser instanceof Seller)) {
      sendMessage(
          AuctionProtocol.formatError("Only Admins or Sellers can reject/cancel auction requests"));
      return;
    }
    int firstSpace = rawLine.indexOf(' ');
    if (firstSpace < 0 || firstSpace >= rawLine.length() - 1) {
      sendMessage(AuctionProtocol.formatError("Usage: REJECT_AUCTION <auctionId>|<reason>"));
      return;
    }
    String payload = rawLine.substring(firstSpace + 1).trim();
    String[] fields = payload.split("\\|", 2);
    String auctionId = fields[0].trim();
    String reason = fields.length > 1 ? fields[1].trim() : "Không đạt tiêu chuẩn kiểm duyệt";

    try {
      AuctionManager.getInstance().rejectAuctionRequest(currentUser.getId(), auctionId, reason);
      sendMessage(AuctionProtocol.formatSuccess(AuctionProtocol.CMD_REJECT_AUCTION, auctionId));
      if (server != null) {
        server.broadcastToAll(AuctionProtocol.NOTIFY_CATALOG_REFRESH);
      }
    } catch (Exception e) {
      sendMessage(AuctionProtocol.formatError(e.getMessage()));
    }
  }

  /** Observer Pattern Callback: Tự động bắn thông báo realtime qua Socket khi có lượt bid mới. */
  @Override
  public void onNewBid(Auction auction, BidTransaction bid) {
    if (!running) {
      return;
    }
    if (subscribedAuctions.contains(auction.getId())) {
      String notification =
          AuctionProtocol.formatBidNotification(
              auction.getId(), bid.getAmount(), bid.getBidderId(), auction.getItemId());
      sendMessage(notification);
    }
  }

  /**
   * Observer Pattern Callback: Tự động bắn thông báo realtime khi phiên đấu giá kết thúc. Gửi thông
   * báo riêng đến Seller và Winner, kèm thông báo phòng chung cho các client đang xem.
   */
  @Override
  public void onAuctionClosed(Auction auction) {
    if (!running) {
      return;
    }
    String winner = auction.getHighestBidderId() != null ? auction.getHighestBidderId() : "NONE";

    // 1. Thông báo riêng cho Winner
    if (currentUser != null
        && auction.getHighestBidderId() != null
        && currentUser.getId().equals(auction.getHighestBidderId())) {
      sendMessage(
          AuctionProtocol.formatWinnerNotification(
              auction.getId(), auction.getCurrentPrice(), auction.getItemId()));
    }

    // 2. Thông báo riêng cho Seller
    if (currentUser != null
        && auction.getSellerId() != null
        && currentUser.getId().equals(auction.getSellerId())) {
      sendMessage(
          AuctionProtocol.formatSellerNotification(
              auction.getId(), auction.getCurrentPrice(), winner, auction.getItemId()));
    }

    // 3. Thông báo phòng chung cho các Client đang theo dõi phiên
    if (subscribedAuctions.contains(auction.getId())) {
      String notification =
          AuctionProtocol.formatClosedNotification(
              auction.getId(), auction.getCurrentPrice(), winner);
      sendMessage(notification);
    }
  }

  /** Observer Pattern Callback: Tự động bắn thông báo khi phiên đã thanh toán (PAID). */
  @Override
  public void onAuctionPaid(Auction auction) {
    if (!running) {
      return;
    }
    String winner = auction.getHighestBidderId() != null ? auction.getHighestBidderId() : "NONE";
    if (subscribedAuctions.contains(auction.getId())
        || (currentUser != null
            && (currentUser.getId().equals(auction.getHighestBidderId())
                || currentUser.getId().equals(auction.getSellerId())))) {
      sendMessage(
          AuctionProtocol.formatPaidNotification(
              auction.getId(), auction.getCurrentPrice(), winner));
    }
  }

  /** Gửi bản tin tới Socket client đảm bảo thread-safe. */
  public synchronized void sendMessage(String message) {
    if (out != null && !socket.isClosed()) {
      out.println(message);
      out.flush();
    }
  }

  /**
   * Dọn dẹp tài nguyên khi client ngắt kết nối. Tự động gỡ đăng ký Observers, dọn Session trên
   * Server và đóng Socket/Stream.
   */
  public void close() {
    if (!closed.compareAndSet(false, true)) {
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
      if (!socket.isClosed()) {
        socket.close();
      }
    } catch (IOException ignored) {
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

  private void sendBidError(String reason) {
    String safeReason = (reason == null || reason.isBlank() ? "Unknown bid error" : reason);
    sendMessage(AuctionProtocol.formatError("BID " + safeReason));
  }

  /**
   * Xác định role của User dựa trên kiểu thực tế (instanceof).
   *
   * @param user người dùng đã xác thực
   * @return chuỗi "ADMIN", "SELLER" hoặc "BIDDER"
   */
  private String detectRole(User user) {
    if (user instanceof Admin) return "ADMIN";
    if (user instanceof Seller) return "SELLER";
    return "BIDDER";
  }
}
