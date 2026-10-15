package manager;

import model.Auction;
import model.BidTransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quản lý các phiên đấu giá trong toàn hệ thống.
 * Áp dụng Singleton Pattern đảm bảo chỉ có 1 bộ quản trị duy nhất.
 * Phụ trách bởi: Bạn (Leader)
 */
public class AuctionManager {
    // 1. Biến static duy nhất của Singleton
    private static AuctionManager instance;

    // Danh sách lưu trữ các phiên đấu giá theo ID
    private final Map<String, Auction> auctionMap;

    // 2. Private constructor ngăn tạo đối tượng từ bên ngoài
    private AuctionManager() {
        this.auctionMap = new HashMap<>();
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
            System.out.printf("[AuctionManager] Phiên đấu giá không hợp lệ hoặc đã tồn tại: %s%n",
                    auction != null ? auction.getId() : "null");
            return;
        }
        auctionMap.put(auction.getId(), auction);
        System.out.printf("[AuctionManager] Đã đăng ký phiên đấu giá ID: %s%n", auction.getId());
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

    public boolean placeBid(String auctionId, String bidderId, double amount) {
        Auction auction = auctionMap.get(auctionId);
        if (auction == null) {
            System.out.printf("[AuctionManager] Không tìm thấy phiên đấu giá: %s%n", auctionId);
            return false;
        }

        String transactionId = "TX-" + System.currentTimeMillis();
        BidTransaction tx = new BidTransaction(transactionId, auctionId, bidderId, amount);
        return auction.placeBid(tx);
    }
}
