package manager;

import model.Auction;
import model.BidTransaction;
import observer.AuctionObserver;

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

    // Bộ đếm giao dịch thread-safe tránh trùng ID khi đặt giá đồng thời
    private static final AtomicLong txCounter = new AtomicLong(0);

    // 2. Private constructor ngăn tạo đối tượng từ bên ngoài
    private AuctionManager() {
        this.auctionMap = new ConcurrentHashMap<>();
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
            if (a.getStatus() == Auction.Status.OPEN) {
                openList.add(a);
            }
        }
        return Collections.unmodifiableList(openList);
    }

    public void registerObserver(String auctionId, AuctionObserver observer) {
        Auction auction = auctionMap.get(auctionId);
        if (auction != null) {
            auction.addObserver(observer);
        } else {
            System.out.printf("[AuctionManager] Auction not found for observer: %s%n", auctionId);
        }
    }

    public boolean placeBid(String auctionId, String bidderId, double amount) {
        Auction auction = auctionMap.get(auctionId);
        if (auction == null) {
            System.out.printf("[AuctionManager] Auction not found: %s%n", auctionId);
            return false;
        }

        String transactionId = "TX-" + System.currentTimeMillis() + "-" + txCounter.incrementAndGet();
        BidTransaction tx = new BidTransaction(transactionId, auctionId, bidderId, amount);
        return auction.placeBid(tx);
    }
}
