package com.auction.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Bản ghi giao dịch ví tiền (nhận tiền bán đấu giá về ví Seller, thanh toán của Bidder, nạp tiền).
 */
public class WalletTransaction implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        PAYOUT_RECEIVED,
        AUCTION_PAYMENT,
        TOP_UP,
        WITHDRAW,
        REFUND
    }

    private final String id;
    private final String userId;
    private final String counterpartyId;
    private final String auctionId;
    private final String itemName;
    private final Type type;
    private final double amount;
    private final double balanceAfter;
    private final LocalDateTime timestamp;

    public WalletTransaction(
            String id,
            String userId,
            String counterpartyId,
            String auctionId,
            String itemName,
            Type type,
            double amount,
            double balanceAfter) {
        this(id, userId, counterpartyId, auctionId, itemName, type, amount, balanceAfter, LocalDateTime.now());
    }

    public WalletTransaction(
            String id,
            String userId,
            String counterpartyId,
            String auctionId,
            String itemName,
            Type type,
            double amount,
            double balanceAfter,
            LocalDateTime timestamp) {
        this.id = id;
        this.userId = userId;
        this.counterpartyId = counterpartyId != null ? counterpartyId : "SYSTEM";
        this.auctionId = auctionId != null ? auctionId : "-";
        this.itemName = itemName != null ? itemName : "";
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getCounterpartyId() {
        return counterpartyId;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getItemName() {
        return itemName;
    }

    public Type getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
