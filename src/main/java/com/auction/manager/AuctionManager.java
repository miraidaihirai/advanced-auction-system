package com.auction.manager;

import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.User;
import com.auction.observer.AuctionObserver;
import com.auction.exceptions.AuthenticationException;
import com.auction.exceptions.AuctionClosedException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Quản lý các phiên đấu giá trong toàn hệ thống.
 * Áp dụng Singleton Pattern đảm bảo chỉ có 1 bộ quản trị duy nhất.
 * Quản lý đồng thời bằng ConcurrentHashMap và AtomicLong sinh Transaction ID duy nhất.
 */
public class AuctionManager {
    // 1. Biến static duy nhất của Singleton
    private static AuctionManager instance;

    // Danh sách lưu trữ các phiên đấu giá theo ID (Thread-safe)
    private final Map<String, Auction> auctionMap;
    private final Map<String, User> userMap;

    // Bộ đếm giao dịch thread-safe tránh trùng ID khi đặt giá đồng thời
    private static final AtomicLong txCounter = new AtomicLong(0);

    // 2. Private constructor ngăn tạo đối tượng từ bên ngoài
    private AuctionManager() {
        this.auctionMap = new ConcurrentHashMap<>();
        this.userMap = new ConcurrentHashMap<>();
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
        auctionMap.put(auction.getId(), auction);
        System.out.printf("[AuctionManager] Registered auction ID: %s%n", auction.getId());
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
    }

    public User getUser(String userId) {
        return userMap.get(userId);
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
        return auction.placeBid(tx);
    }
}
