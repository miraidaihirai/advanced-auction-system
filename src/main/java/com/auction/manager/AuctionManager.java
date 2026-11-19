package com.auction.manager;

import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.User;
import com.auction.observer.AuctionObserver;
import com.auction.storage.AuctionDataSnapshot;
import com.auction.storage.FileStorageService;
import com.auction.storage.TransactionStorageService;
import com.auction.exceptions.AuthenticationException;
import com.auction.exceptions.AuctionClosedException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    // Quản lý lưu trữ file và lịch sử giao dịch
    private FileStorageService storageService;
    private TransactionStorageService transactionStorageService;
    private String storagePath = DEFAULT_STORAGE_PATH;
    private volatile boolean autoSave = false;

    // Bộ đếm giao dịch thread-safe tránh trùng ID khi đặt giá đồng thời
    private static final AtomicLong txCounter = new AtomicLong(0);

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
            if (autoSave) {
                saveState();
            }
            return true;
        }
        return false;
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

        if (success && autoSave) {
            saveState();
        }

        return success;
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
                    getAllUsers(), getAllAuctions(), getAllTransactions());
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
