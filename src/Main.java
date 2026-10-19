import manager.AuctionManager;
import model.Auction;

public class Main {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" ONLINE AUCTION SYSTEM DEMO - WEEK 6");
        System.out.println("==================================================");

        // 1. Kiểm tra Singleton Pattern
        System.out.println("\n--- [1] Checking Singleton: AuctionManager ---");
        AuctionManager manager1 = AuctionManager.getInstance();
        AuctionManager manager2 = AuctionManager.getInstance();
        boolean isSameInstance = (manager1 == manager2);
        System.out.printf("Instance 1: %s%n", manager1);
        System.out.printf("Instance 2: %s%n", manager2);
        System.out.printf("=> Same instance (Singleton): %s%n", isSameInstance ? "SUCCESS" : "FAILED");

        // 2. Tạo phiên đấu giá
        System.out.println("\n--- [2] Create new auction ---");
        Auction auction = new Auction("AUC-001", "ITEM-99 (Gaming Laptop)", "Seller_Alice", 10_000_000.0, 500_000.0);
        manager1.addAuction(auction);

        // Thử đặt giá khi chưa mở
        System.out.println("\n--- Try placing bid when auction is not open ---");
        manager1.placeBid("AUC-001", "Bidder_Bob", 10_000_000.0);

        // 3. Mở phiên đấu giá
        System.out.println("\n--- [3] Start auction ---");
        auction.startAuction();

        // 4. Các lượt đấu giá
        System.out.println("\n--- [4] Bidding in progress ---");
        // Bob đặt mức giá khởi điểm hợp lệ: 10,000,000
        manager1.placeBid("AUC-001", "Bidder_Bob", 10_000_000.0);

        // Charlie đặt giá không đủ bước giá (+500,000 -> cần >= 10,500,000)
        manager1.placeBid("AUC-001", "Bidder_Charlie", 10_200_000.0);

        // Charlie đặt giá hợp lệ
        manager1.placeBid("AUC-001", "Bidder_Charlie", 11_000_000.0);

        // David trả giá cao hơn
        manager1.placeBid("AUC-001", "Bidder_David", 12_500_000.0);

        // 5. Kết thúc phiên đấu giá
        System.out.println("\n--- [5] Close auction and display result ---");
        auction.closeAuction();

        System.out.println("\nBid history:");
        auction.getBidHistory().forEach(bid -> System.out.println("  -> " + bid));

        System.out.println("\n==================================================");
        System.out.println(" DEMO COMPLETED");
        System.out.println("==================================================");
    }
}
