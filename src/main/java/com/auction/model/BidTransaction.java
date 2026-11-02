package com.auction.model;

import java.time.LocalDateTime;

/**
 * Đại diện cho một giao dịch đặt giá trong phiên đấu giá.
 */
public class BidTransaction {
    private String id;
    private String auctionId;
    private String bidderId;
    private double amount;
    private LocalDateTime timestamp;

    public BidTransaction(String id, String auctionId, String bidderId, double amount) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.timestamp = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public void setBidderId(String bidderId) {
        this.bidderId = bidderId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return String.format("BidTransaction[id=%s, auctionId=%s, bidderId=%s, amount=%.2f, time=%s]",
                id, auctionId, bidderId, amount, timestamp);
    }
}
