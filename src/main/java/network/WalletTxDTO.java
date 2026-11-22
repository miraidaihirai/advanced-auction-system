package network;

/**
 * Bản ghi dòng tiền ví (nhận tiền bán đấu giá về Seller, thanh toán Bidder, nạp tiền).
 */
public record WalletTxDTO(
        String id,
        String userId,
        String counterpartyId,
        String auctionId,
        String itemName,
        String type,
        double amount,
        double balanceAfter,
        String timestamp
) {
}
