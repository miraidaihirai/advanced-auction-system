package network;

/**
 * Bản ghi chi tiết đầy đủ của một phiên đấu giá đồng bộ giữa Server và BidX Client.
 */
public record AuctionDetail(
        String auctionId,
        String itemName,
        String sellerId,
        String sellerName,
        String category,
        double startPrice,
        double currentPrice,
        double stepPrice,
        String highestBidderId,
        String status,
        long endTimeMillis,
        long requestedDurationSec,
        int bidCount,
        boolean payoutSettled,
        String imageKey,
        String rejectionReason,
        String description,
        String itemSpecs
) {
    public AuctionDetail(
            String auctionId,
            String itemName,
            String sellerId,
            String sellerName,
            String category,
            double startPrice,
            double currentPrice,
            double stepPrice,
            String highestBidderId,
            String status,
            long endTimeMillis,
            long requestedDurationSec,
            int bidCount,
            boolean payoutSettled,
            String imageKey,
            String rejectionReason,
            String description) {
        this(auctionId, itemName, sellerId, sellerName, category, startPrice, currentPrice, stepPrice,
                highestBidderId, status, endTimeMillis, requestedDurationSec, bidCount, payoutSettled,
                imageKey, rejectionReason, description, "");
    }
    public AuctionSummary toSummary() {
        return new AuctionSummary(auctionId, itemName, currentPrice, stepPrice, highestBidderId, status);
    }

    public boolean isRunning() {
        return "RUNNING".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean isPendingApproval() {
        return "OPEN".equalsIgnoreCase(status);
    }

    public boolean isFinished() {
        return "FINISHED".equalsIgnoreCase(status);
    }

    public boolean isPaid() {
        return "PAID".equalsIgnoreCase(status);
    }

    public boolean isRejected() {
        return "CANCELED".equalsIgnoreCase(status);
    }

    public boolean hasWinner() {
        return highestBidderId != null
                && !highestBidderId.isBlank()
                && !"NONE".equalsIgnoreCase(highestBidderId);
    }

    public long getRemainingSeconds() {
        if (!isRunning() || endTimeMillis <= 0) {
            return 0;
        }
        return Math.max(0L, (endTimeMillis - System.currentTimeMillis()) / 1000L);
    }
}
