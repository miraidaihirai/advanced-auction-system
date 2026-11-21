package network;

public record AuctionSummary(
    String auctionId,
    String itemId,
    double currentPrice,
    double stepPrice,
    String highestBidderId,
    String status) {}
// Record chỉ lưu trữ dữ liệu không cần thay đổi sau khi khởi tạo
