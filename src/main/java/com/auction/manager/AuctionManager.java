package com.auction.manager;

import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.AutoBidConfig;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.ChatMessage;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.model.WalletTransaction;
import com.auction.observer.AuctionObserver;
import com.auction.storage.AuctionDataSnapshot;
import com.auction.storage.FileStorageService;
import com.auction.storage.TransactionStorageService;
import com.auction.exceptions.AuthenticationException;
import com.auction.exceptions.AuctionClosedException;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Quản lý các phiên đấu giá trong toàn hệ thống.
 * Áp dụng Singleton Pattern đảm bảo chỉ có 1 bộ quản trị duy nhất.
 * Quản lý đồng thời bằng ConcurrentHashMap và AtomicLong sinh Transaction ID duy nhất.
 * Tích hợp Serialization / Deserialization và TransactionStorageService để lưu trữ và phục hồi trạng thái hệ thống.
 */
public class AuctionManager {
    public static final String DEFAULT_STORAGE_PATH = "data/auction_data.ser";

    // 1. Biến static duy nhất của Singleton
    private static AuctionManager instance;

    // Danh sách lưu trữ các phiên đấu giá theo ID (Thread-safe)
    private final Map<String, Auction> auctionMap;
    private final Map<String, User> userMap;
    private final List<AuctionObserver> globalObservers = new CopyOnWriteArrayList<>();
    private final List<WalletTransaction> walletTransactions = new CopyOnWriteArrayList<>();

    // Quản lý lưu trữ file và lịch sử giao dịch
    private FileStorageService storageService;
    private TransactionStorageService transactionStorageService;
    private String storagePath = DEFAULT_STORAGE_PATH;
    private volatile boolean autoSave = false;

    // Bộ đếm giao dịch thread-safe tránh trùng ID khi đặt giá đồng thời
    private static final AtomicLong txCounter = new AtomicLong(0);
    private static final AtomicLong walletTxCounter = new AtomicLong(0);
    private static final AtomicLong auctionRequestCounter = new AtomicLong(100);

    // 2. Private constructor ngăn tạo đối tượng từ bên ngoài
    private AuctionManager() {
        this.auctionMap = new ConcurrentHashMap<>();
        this.userMap = new ConcurrentHashMap<>();
        this.storageService = new FileStorageService();
        this.transactionStorageService = new TransactionStorageService(this.storageService);
    }

    // 3. Phương thức tĩnh truy cập instance duy nhất (Thread-safe)
    public static synchronized AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }

    public void addAuction(Auction auction) {
        if (auction == null || auctionMap.containsKey(auction.getId())) {
            System.out.printf("[AuctionManager] Invalid or duplicate auction: %s%n",
                    auction != null ? auction.getId() : "null");
            return;
        }
        for (AuctionObserver observer : globalObservers) {
            auction.addObserver(observer);
        }
        auctionMap.put(auction.getId(), auction);
        System.out.printf("[AuctionManager] Registered auction ID: %s%n", auction.getId());

        if (autoSave) {
            saveState();
        }
    }

    public void addGlobalObserver(AuctionObserver observer) {
        if (observer != null && !globalObservers.contains(observer)) {
            globalObservers.add(observer);
            for (Auction a : auctionMap.values()) {
                a.addObserver(observer);
            }
        }
    }

    public void removeGlobalObserver(AuctionObserver observer) {
        if (observer != null) {
            globalObservers.remove(observer);
            for (Auction a : auctionMap.values()) {
                a.removeObserver(observer);
            }
        }
    }

    public boolean finishAuction(String auctionId) {
        Auction auction = auctionMap.get(auctionId);
        if (auction != null) {
            auction.finishAuction();
            settleAuctionFunds(auction);
            if (autoSave) {
                saveState();
            }
            return true;
        }
        return false;
    }

    public boolean markAuctionPaid(String auctionId) {
        Auction auction = auctionMap.get(auctionId);
        if (auction != null) {
            auction.markPaid();
            settleAuctionFunds(auction);
            if (autoSave) {
                saveState();
            }
            return true;
        }
        return false;
    }

    /**
     * Thanh toán tự động sau khi phiên đấu giá thành công: tiền chảy từ người thắng (Bidder)
     * về trực tiếp ví người bán (Seller). Đảm bảo idempotent (không bao giờ cộng trùng).
     */
    public synchronized boolean settleAuctionFunds(Auction auction) {
        if (auction == null || !auction.claimPayoutSettlement()) {
            return false;
        }

        double finalAmount = auction.getCurrentPrice();
        String winnerId = auction.getHighestBidderId();
        String sellerId = auction.getSellerId();

        User winner = winnerId != null ? userMap.get(winnerId) : null;
        User seller = sellerId != null ? userMap.get(sellerId) : null;

        if (winner != null && finalAmount > 0) {
            winner.deductBalance(finalAmount);
            walletTransactions.add(new WalletTransaction(
                    "WTX-" + System.currentTimeMillis() + "-" + walletTxCounter.incrementAndGet(),
                    winner.getId(),
                    sellerId != null ? sellerId : "SELLER",
                    auction.getId(),
                    auction.getItemId(),
                    WalletTransaction.Type.AUCTION_PAYMENT,
                    -finalAmount,
                    winner.getBalance()));
        }

        if (seller != null && finalAmount > 0) {
            seller.addBalance(finalAmount);
            walletTransactions.add(new WalletTransaction(
                    "WTX-" + System.currentTimeMillis() + "-" + walletTxCounter.incrementAndGet(),
                    seller.getId(),
                    winnerId != null ? winnerId : "BIDDER",
                    auction.getId(),
                    auction.getItemId(),
                    WalletTransaction.Type.PAYOUT_RECEIVED,
                    finalAmount,
                    seller.getBalance()));
            System.out.printf("[BidX Settlement] +%.2f VND -> Seller [%s] (new balance: %.2f) for auction [%s]%n",
                    finalAmount, seller.getId(), seller.getBalance(), auction.getId());
        }

        if (autoSave) {
            saveState();
        }
        return true;
    }

    /**
     * Seller gửi yêu cầu mở phòng đấu giá mới (trạng thái PENDING_APPROVAL chờ Admin duyệt).
     */
    public Auction submitAuctionRequest(
            String sellerId,
            String itemName,
            String category,
            String description,
            double startPrice,
            double stepPrice,
            long durationSeconds,
            String imageKey) {
        User user = userMap.get(sellerId);
        if (!(user instanceof Seller)) {
            throw new AuthenticationException("Only Sellers can submit auction room requests");
        }
        if (itemName == null || itemName.trim().isEmpty()) {
            throw new IllegalArgumentException("Item name must not be empty");
        }
        if (!Double.isFinite(startPrice) || startPrice <= 0) {
            throw new IllegalArgumentException("Start price must be positive");
        }
        if (!Double.isFinite(stepPrice) || stepPrice <= 0) {
            throw new IllegalArgumentException("Step price must be positive");
        }

        String auctionId;
        do {
            auctionId = "AUC-" + auctionRequestCounter.incrementAndGet();
        } while (auctionMap.containsKey(auctionId));

        Auction pending = Auction.createPendingRequest(
                auctionId,
                itemName.trim(),
                sellerId,
                startPrice,
                stepPrice,
                category,
                description,
                Math.max(30, durationSeconds),
                imageKey);
        addAuction(pending);
        return pending;
    }

    /**
     * Admin phê duyệt yêu cầu mở phòng đấu giá của Seller -> chuyển sang RUNNING và bắt đầu đếm ngược.
     */
    public Auction approveAuctionRequest(String adminId, String auctionId, Long overrideDurationSeconds) {
        User user = userMap.get(adminId);
        if (!(user instanceof Admin)) {
            throw new AuthenticationException("Only Admins can approve auction requests");
        }
        Auction auction = auctionMap.get(auctionId);
        if (auction == null) {
            throw new AuctionClosedException("Auction not found: " + auctionId);
        }
        long durationSec = (overrideDurationSeconds != null && overrideDurationSeconds > 0)
                ? overrideDurationSeconds
                : auction.getRequestedDurationSeconds();
        auction.approveAndStart(Duration.ofSeconds(durationSec));
        if (autoSave) {
            saveState();
        }
        return auction;
    }

    /**
     * Admin từ chối yêu cầu mở phòng đấu giá của Seller kèm lý do.
     */
    public Auction rejectAuctionRequest(String adminId, String auctionId, String reason) {
        User user = userMap.get(adminId);
        if (!(user instanceof Admin)) {
            throw new AuthenticationException("Only Admins can reject auction requests");
        }
        Auction auction = auctionMap.get(auctionId);
        if (auction == null) {
            throw new AuctionClosedException("Auction not found: " + auctionId);
        }
        auction.rejectRequest(reason);
        if (autoSave) {
            saveState();
        }
        return auction;
    }

    public List<Auction> getPendingApprovalAuctions() {
        List<Auction> pending = new ArrayList<>();
        for (Auction a : auctionMap.values()) {
            if (a.getStatus() == Auction.Status.PENDING_APPROVAL) {
                pending.add(a);
            }
        }
        return Collections.unmodifiableList(pending);
    }

    public List<Auction> getAuctionsBySeller(String sellerId) {
        if (sellerId == null) {
            return Collections.emptyList();
        }
        List<Auction> list = new ArrayList<>();
        for (Auction a : auctionMap.values()) {
            if (sellerId.equals(a.getSellerId())) {
                list.add(a);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public double topUpWallet(String userId, double amount) {
        return topUpWallet(userId, amount, "BIDX-BANK");
    }

    public double topUpWallet(String userId, double amount, String channel) {
        User user = userMap.get(userId);
        if (user == null) {
            throw new AuthenticationException("User not found: " + userId);
        }
        if (user instanceof Admin) {
            throw new AuthenticationException("Admin account does not use personal top-up wallet");
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Top-up amount must be positive");
        }
        String safeChannel = (channel != null && !channel.trim().isEmpty()) ? channel.trim() : "BIDX-BANK";
        double updatedBalance;
        synchronized (user) {
            user.addBalance(amount);
            updatedBalance = user.getBalance();
        }
        walletTransactions.add(new WalletTransaction(
                "WTX-" + System.currentTimeMillis() + "-" + walletTxCounter.incrementAndGet(),
                user.getId(),
                safeChannel,
                "-",
                "Nạp tiền vào ví (" + safeChannel + ")",
                WalletTransaction.Type.TOP_UP,
                amount,
                updatedBalance));
        if (autoSave) {
            saveState();
        }
        return updatedBalance;
    }

    public double withdrawWallet(String userId, double amount, String destination) {
        User user = userMap.get(userId);
        if (user == null) {
            throw new AuthenticationException("User not found: " + userId);
        }
        if (user instanceof Admin) {
            throw new AuthenticationException("Admin account does not use personal withdraw wallet");
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Withdraw amount must be positive");
        }
        String safeDest = (destination != null && !destination.trim().isEmpty())
                ? destination.trim()
                : "Ngân hàng liên kết";
        double updatedBalance;
        synchronized (user) {
            if (user.getBalance() < amount) {
                throw new IllegalArgumentException("Số dư ví không đủ để rút " + String.format("%,.0f", amount) + " đ");
            }
            user.deductBalance(amount);
            updatedBalance = user.getBalance();
        }
        walletTransactions.add(new WalletTransaction(
                "WTX-" + System.currentTimeMillis() + "-" + walletTxCounter.incrementAndGet(),
                user.getId(),
                safeDest,
                "-",
                "Rút tiền về " + safeDest,
                WalletTransaction.Type.WITHDRAW,
                -amount,
                updatedBalance));
        if (autoSave) {
            saveState();
        }
        return updatedBalance;
    }

    public List<WalletTransaction> getWalletTransactionsForUser(String userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<WalletTransaction> result = new ArrayList<>();
        for (WalletTransaction tx : walletTransactions) {
            if (userId.equals(tx.getUserId())) {
                result.add(tx);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<WalletTransaction> getAllWalletTransactions() {
        return Collections.unmodifiableList(new ArrayList<>(walletTransactions));
    }

    public ChatMessage recordChatMessage(
            String auctionId,
            String senderId,
            String senderName,
            String role,
            String message) {
        if (auctionId == null || message == null || message.trim().isEmpty()) {
            return null;
        }
        Auction auction = auctionMap.get(auctionId);
        if (auction == null) {
            return null;
        }
        ChatMessage chatMsg = new ChatMessage(auctionId, senderId, senderName, role, message.trim());
        auction.addChatMessage(chatMsg);
        if (autoSave) {
            saveState();
        }
        return chatMsg;
    }

    public List<ChatMessage> getChatHistory(String auctionId) {
        if (auctionId == null) {
            return Collections.emptyList();
        }
        Auction auction = auctionMap.get(auctionId);
        return auction != null ? auction.getChatHistory() : Collections.emptyList();
    }

    public Auction getAuction(String auctionId) {
        return auctionMap.get(auctionId);
    }

    public List<Auction> getAllAuctions() {
        return new ArrayList<>(auctionMap.values());
    }

    public List<Auction> getOpenAuctions() {
        List<Auction> openList = new ArrayList<>();
        for (Auction a : auctionMap.values()) {
            if (a.getStatus() == Auction.Status.OPEN || a.getStatus() == Auction.Status.RUNNING) {
                openList.add(a);
            }
        }
        return Collections.unmodifiableList(openList);
    }

    public void registerUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        User existingUser = userMap.putIfAbsent(user.getId(), user);
        if (existingUser != null && existingUser != user) {
            throw new IllegalArgumentException("User ID is already registered: " + user.getId());
        }

        if (autoSave) {
            saveState();
        }
    }

    public User getUser(String userId) {
        return userMap.get(userId);
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(userMap.values());
    }

    public User authenticate(String userId, String password) {
        User user = userMap.get(userId);
        if (user != null && password != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }

    public void registerObserver(String auctionId, AuctionObserver observer) {
        Auction auction = auctionMap.get(auctionId);
        if (auction != null) {
            auction.addObserver(observer);
        } else {
            System.out.printf("[AuctionManager] Auction not found for observer: %s%n", auctionId);
        }
    }

    public boolean placeBid(String auctionId, String bidderId, String password, double amount) {
        User user = userMap.get(bidderId);
        if (!(user instanceof Bidder) || password == null || !user.getPassword().equals(password)) {
            throw new AuthenticationException("Invalid bidder credentials");
        }

        Auction auction = auctionMap.get(auctionId);
        if (auction == null) {
            throw new AuctionClosedException("Auction not found: " + auctionId);
        }

        String transactionId = "TX-" + System.currentTimeMillis() + "-" + txCounter.incrementAndGet();
        BidTransaction tx = new BidTransaction(transactionId, auctionId, bidderId, amount);
        boolean success = auction.placeBid(tx);

        if (success) {
            processAutoBids(auction);
            if (autoSave) {
                saveState();
            }
        }

        return success;
    }

    /**
     * Đăng ký Đấu giá tự động (Auto-Bidding) cho Bidder với giá trần và bước tăng.
     */
    public void configureAutoBid(String auctionId, String bidderId, String password, double maxBid, double increment) {
        User user = userMap.get(bidderId);
        if (!(user instanceof Bidder) || password == null || !user.getPassword().equals(password)) {
            throw new AuthenticationException("Only authenticated Bidders can configure Auto-Bid");
        }
        Auction auction = auctionMap.get(auctionId);
        if (auction == null) {
            throw new AuctionClosedException("Auction not found: " + auctionId);
        }
        if (auction.getStatus() != Auction.Status.RUNNING) {
            throw new AuctionClosedException("Auction is not RUNNING");
        }
        double minStep = Math.max(auction.getStepPrice(), increment);
        if (maxBid < auction.getCurrentPrice() + auction.getStepPrice() && !bidderId.equals(auction.getHighestBidderId())) {
            throw new IllegalArgumentException("Max bid must be at least current price + step price");
        }

        auction.registerAutoBid(new AutoBidConfig(bidderId, maxBid, minStep));
        processAutoBids(auction);
        if (autoSave) {
            saveState();
        }
    }

    public void cancelAutoBid(String auctionId, String bidderId) {
        Auction auction = auctionMap.get(auctionId);
        if (auction != null && bidderId != null) {
            auction.cancelAutoBid(bidderId);
        }
    }

    /**
     * Xử lý tự động trả giá bằng PriorityQueue khi có đối thủ vượt giá.
     * Đảm bảo không vượt quá maxBid, ưu tiên người đăng ký sớm khi cùng maxBid, và chống vòng lặp vô hạn.
     */
    private void processAutoBids(Auction auction) {
        if (auction == null || auction.getStatus() != Auction.Status.RUNNING) {
            return;
        }
        Map<String, AutoBidConfig> configs = auction.getAutoBids();
        if (configs.isEmpty()) {
            return;
        }

        int safetyLimit = 100;
        while (safetyLimit-- > 0 && auction.getStatus() == Auction.Status.RUNNING) {
            PriorityQueue<AutoBidConfig> queue = new PriorityQueue<>(configs.values());
            boolean placedAny = false;
            String currentLeader = auction.getHighestBidderId();
            double minNext = auction.getBidHistory().isEmpty()
                    ? auction.getStartPrice()
                    : auction.getCurrentPrice() + auction.getStepPrice();

            while (!queue.isEmpty()) {
                AutoBidConfig candidate = queue.poll();
                if (candidate.getBidderId().equals(currentLeader)) {
                    continue;
                }
                if (candidate.getMaxBid() < minNext) {
                    continue;
                }
                double desired = auction.getBidHistory().isEmpty()
                        ? auction.getStartPrice()
                        : auction.getCurrentPrice() + Math.max(auction.getStepPrice(), candidate.getIncrement());
                double bidAmount = Math.min(candidate.getMaxBid(), Math.max(minNext, desired));
                String txId = "AUTO-TX-" + System.currentTimeMillis() + "-" + txCounter.incrementAndGet();
                try {
                    auction.placeBid(new BidTransaction(txId, auction.getId(), candidate.getBidderId(), bidAmount));
                    placedAny = true;
                    break;
                } catch (Exception ignored) {
                    break;
                }
            }

            if (!placedAny) {
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Quản lý & truy vấn lịch sử giao dịch đấu giá (Transaction History)
    // -------------------------------------------------------------------------

    /**
     * Lấy toàn bộ danh sách lịch sử giao dịch đặt giá từ tất cả các phiên đấu giá.
     */
    public List<BidTransaction> getAllTransactions() {
        List<BidTransaction> all = new ArrayList<>();
        for (Auction auction : auctionMap.values()) {
            all.addAll(auction.getBidHistory());
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * Lấy danh sách lịch sử giao dịch đặt giá theo một phiên đấu giá cụ thể.
     */
    public List<BidTransaction> getTransactionsByAuction(String auctionId) {
        Auction auction = auctionMap.get(auctionId);
        if (auction != null) {
            return auction.getBidHistory();
        }
        return Collections.emptyList();
    }

    /**
     * Lấy danh sách lịch sử giao dịch đặt giá theo người tham gia (Bidder).
     */
    public List<BidTransaction> getTransactionsByBidder(String bidderId) {
        if (bidderId == null) {
            return Collections.emptyList();
        }
        List<BidTransaction> list = new ArrayList<>();
        for (Auction auction : auctionMap.values()) {
            for (BidTransaction tx : auction.getBidHistory()) {
                if (bidderId.equals(tx.getBidderId())) {
                    list.add(tx);
                }
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Xuất toàn bộ lịch sử giao dịch ra tệp CSV.
     */
    public void exportTransactionsToCsv(File file) throws IOException {
        transactionStorageService.exportToCsv(getAllTransactions(), file);
    }

    /**
     * Xuất toàn bộ lịch sử giao dịch ra đường dẫn tệp CSV.
     */
    public void exportTransactionsToCsv(String filePath) throws IOException {
        exportTransactionsToCsv(new File(filePath));
    }

    /**
     * Xuất lịch sử giao dịch của một phiên đấu giá cụ thể ra tệp CSV.
     */
    public void exportAuctionTransactionsToCsv(String auctionId, File file) throws IOException {
        transactionStorageService.exportToCsv(getTransactionsByAuction(auctionId), file);
    }

    /**
     * Xuất lịch sử giao dịch của một phiên đấu giá cụ thể ra đường dẫn tệp CSV.
     */
    public void exportAuctionTransactionsToCsv(String auctionId, String filePath) throws IOException {
        exportAuctionTransactionsToCsv(auctionId, new File(filePath));
    }

    // -------------------------------------------------------------------------
    // Persistence & Snapshot
    // -------------------------------------------------------------------------

    /**
     * Dọn sạch toàn bộ dữ liệu người dùng và phiên đấu giá trong bộ nhớ RAM, đồng thời hủy bỏ các timer đang chạy.
     */
    public synchronized void clear() {
        for (Auction a : auctionMap.values()) {
            if (a != null) {
                a.cancelTimer();
            }
        }
        auctionMap.clear();
        userMap.clear();
        walletTransactions.clear();
    }

    /**
     * Lưu trạng thái hệ thống ra tệp tin mặc định.
     */
    public synchronized void saveState() {
        saveState(new File(storagePath));
    }

    /**
     * Lưu trạng thái hệ thống ra đường dẫn tệp chỉ định.
     */
    public synchronized void saveState(String filePath) {
        saveState(new File(filePath));
    }

    /**
     * Lưu trạng thái hệ thống (tất cả User, Auction và Transaction) ra file qua FileStorageService.
     */
    public synchronized void saveState(File file) {
        try {
            AuctionDataSnapshot snapshot = new AuctionDataSnapshot(
                    getAllUsers(), getAllAuctions(), getAllTransactions(), getAllWalletTransactions());
            storageService.saveSnapshot(snapshot, file);
            System.out.printf("[AuctionManager] Successfully saved state (%d users, %d auctions, %d txs) to: %s%n",
                    snapshot.getUsers().size(), snapshot.getAuctions().size(),
                    snapshot.getTransactions().size(), file.getPath());
        } catch (IOException e) {
            System.err.printf("[AuctionManager] Error saving state to %s: %s%n", file.getPath(), e.getMessage());
        }
    }

    /**
     * Nạp trạng thái hệ thống từ tệp tin mặc định.
     * @return true nếu đọc file thành công và nạp dữ liệu vào bộ nhớ, false nếu không tìm thấy file hoặc lỗi
     */
    public synchronized boolean loadState() {
        return loadState(new File(storagePath));
    }

    /**
     * Nạp trạng thái hệ thống từ đường dẫn tệp chỉ định.
     */
    public synchronized boolean loadState(String filePath) {
        return loadState(new File(filePath));
    }

    /**
     * Nạp trạng thái hệ thống từ tệp tin chỉ định.
     */
    public synchronized boolean loadState(File file) {
        if (!storageService.exists(file)) {
            return false;
        }

        try {
            AuctionDataSnapshot snapshot = storageService.loadSnapshot(file);
            if (snapshot == null) {
                return false;
            }

            for (User user : snapshot.getUsers()) {
                this.userMap.put(user.getId(), user);
            }
            for (Auction auction : snapshot.getAuctions()) {
                this.auctionMap.put(auction.getId(), auction);
            }
            this.walletTransactions.clear();
            this.walletTransactions.addAll(snapshot.getWalletTransactions());

            System.out.printf("[AuctionManager] Successfully restored state (%d users, %d auctions, %d txs) from: %s%n",
                    snapshot.getUsers().size(), snapshot.getAuctions().size(),
                    snapshot.getTransactions().size(), file.getPath());
            return true;
        } catch (Exception e) {
            System.err.printf("[AuctionManager] Error restoring state from %s: %s%n", file.getPath(), e.getMessage());
            return false;
        }
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        if (storagePath != null && !storagePath.trim().isEmpty()) {
            this.storagePath = storagePath;
        }
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public FileStorageService getStorageService() {
        return storageService;
    }

    public void setStorageService(FileStorageService storageService) {
        if (storageService != null) {
            this.storageService = storageService;
            this.transactionStorageService = new TransactionStorageService(this.storageService);
        }
    }

    public TransactionStorageService getTransactionStorageService() {
        return transactionStorageService;
    }

    public void setTransactionStorageService(TransactionStorageService transactionStorageService) {
        if (transactionStorageService != null) {
            this.transactionStorageService = transactionStorageService;
        }
    }
}
