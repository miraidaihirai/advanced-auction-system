package com.auction.model;

import java.io.Serializable;

/**
 * Cấu hình Đấu giá tự động (Auto-Bidding) cho một Bidder trong một phiên đấu giá.
 * Implement Comparable để dùng với PriorityQueue:
 * - Giá tối đa (maxBid) cao hơn được ưu tiên cao hơn.
 * - Nếu cùng maxBid, người đăng ký trước (registeredAtNanos nhỏ hơn) được ưu tiên.
 */
public class AutoBidConfig implements Serializable, Comparable<AutoBidConfig> {
    private static final long serialVersionUID = 1L;

    private final String bidderId;
    private final double maxBid;
    private final double increment;
    private final long registeredAtNanos;

    public AutoBidConfig(String bidderId, double maxBid, double increment) {
        this(bidderId, maxBid, increment, System.nanoTime());
    }

    public AutoBidConfig(String bidderId, double maxBid, double increment, long registeredAtNanos) {
        if (bidderId == null || bidderId.isBlank()) {
            throw new IllegalArgumentException("Bidder ID must not be empty");
        }
        if (!Double.isFinite(maxBid) || maxBid <= 0) {
            throw new IllegalArgumentException("Max bid must be positive");
        }
        if (!Double.isFinite(increment) || increment <= 0) {
            throw new IllegalArgumentException("Increment must be positive");
        }
        this.bidderId = bidderId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.registeredAtNanos = registeredAtNanos;
    }

    public String getBidderId() {
        return bidderId;
    }

    public double getMaxBid() {
        return maxBid;
    }

    public double getIncrement() {
        return increment;
    }

    public long getRegisteredAtNanos() {
        return registeredAtNanos;
    }

    @Override
    public int compareTo(AutoBidConfig other) {
        int byMax = Double.compare(other.maxBid, this.maxBid);
        if (byMax != 0) {
            return byMax;
        }
        return Long.compare(this.registeredAtNanos, other.registeredAtNanos);
    }
}
