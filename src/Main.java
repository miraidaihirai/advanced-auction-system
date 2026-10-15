import manager.AuctionManager;
import model.Auction;

/**
 * Chương trình chạy thử nghiệm cho các module của Leader:
 * - Auction
 * - BidTransaction
 * - AuctionManager (Singleton)
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" DEMO HỆ THỐNG ĐẤU GIÁ TRỰC TUYẾN - TUẦN 6");
        System.out.println("==================================================");

        // 1. Kiểm tra Singleton Pattern
        System.out.println("\n--- [1] Kiểm tra Singleton: AuctionManager ---");
        AuctionManager manager1 = AuctionManager.getInstance();
        AuctionManager manager2 = AuctionManager.getInstance();
        boolean isSameInstance = (manager1 == manager2);
        System.out.printf("Instance 1: %s%n", manager1);
        System.out.printf("Instance 2: %s%n", manager2);
        System.out.printf("=> Cùng một thể hiện (Singleton): %s%n", isSameInstance ? "THÀNH CÔNG" : "THẤT BẠI");

        // 2. Tạo phiên đấu giá
        System.out.println("\n--- [2] Tạo phiên đấu giá mới ---");
        Auction auction = new Auction("AUC-001", "ITEM-99 (Laptop Gaming)", "Seller_Alice", 10_000_000.0, 500_000.0);
        manager1.addAuction(auction);

        // Thử đặt giá khi chưa mở
        System.out.println("\n--- Thử đặt giá khi phiên chưa mở ---");
        manager1.placeBid("AUC-001", "Bidder_Bob", 10_000_000.0);

        // 3. Mở phiên đấu giá
        System.out.println("\n--- [3] Mở phiên đấu giá ---");
        auction.startAuction();

        // 4. Các lượt đấu giá
        System.out.println("\n--- [4] Đấu giá diễn ra ---");
        // Bob đặt mức giá khởi điểm hợp lệ: 10,000,000
        manager1.placeBid("AUC-001", "Bidder_Bob", 10_000_000.0);

        // Charlie đặt giá không đủ bước giá (+500,000 -> cần >= 10,500,000)
        manager1.placeBid("AUC-001", "Bidder_Charlie", 10_200_000.0);

        // Charlie đặt giá hợp lệ
        manager1.placeBid("AUC-001", "Bidder_Charlie", 11_000_000.0);

        // David trả giá cao hơn
        manager1.placeBid("AUC-001", "Bidder_David", 12_500_000.0);

        // 5. Kết thúc phiên đấu giá
        System.out.println("\n--- [5] Kết thúc phiên và xem kết quả ---");
        auction.closeAuction();

        System.out.println("\nLịch sử các lượt đặt giá:");
        auction.getBidHistory().forEach(bid -> System.out.println("  -> " + bid));

        System.out.println("\n==================================================");
        System.out.println(" DEMO HOÀN TẤT");
        System.out.println("==================================================");
    }
}
