package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Đại diện cho một phiên đấu giá.
 */
public class Auction {
    public enum Status {
        PENDING, OPEN, CLOSED
    }

    private String id;
    private String itemId;
    private String sellerId;
    private double startPrice;
    private double currentPrice;
    private double stepPrice;
    private Status status;
    private String highestBidderId;
    private List<BidTransaction> bidHistory;

    public Auction(String id, String itemId, String sellerId, double startPrice, double stepPrice) {
        this.id = id;
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startPrice = startPrice;
        this.currentPrice = startPrice;
        this.stepPrice = stepPrice;
        this.status = Status.PENDING;
        this.highestBidderId = null;
        this.bidHistory = new ArrayList<>();
    }

    public void startAuction() {
        this.status = Status.OPEN;
        System.out.printf("[Auction %s] Started auction for item: %s%n", id, itemId);
    }

    public void closeAuction() {
        this.status = Status.CLOSED;
        System.out.printf("[Auction %s] Auction closed. Winner: %s with price: %.2f%n",
                id, (highestBidderId != null ? highestBidderId : "None"), currentPrice);
    }

    public synchronized boolean placeBid(BidTransaction bid) {
        if (status != Status.OPEN) {
            System.out.printf("[Error] Auction [%s] is not open (status: %s)%n", id, status);
            return false;
        }

        // Lượt đầu tiên chỉ cần >= startPrice, các lượt sau cần >= currentPrice + stepPrice
        double minRequired = (bidHistory.isEmpty()) ? startPrice : (currentPrice + stepPrice);
        if (bid.getAmount() < minRequired) {
            System.out.printf("[Error] Bid amount %.2f is invalid (Minimum required: %.2f)%n",
                    bid.getAmount(), minRequired);
            return false;
        }

        this.currentPrice = bid.getAmount();
        this.highestBidderId = bid.getBidderId();
        this.bidHistory.add(bid);
        System.out.printf("[Success] %s placed bid %.2f for auction [%s]%n",
                bid.getBidderId(), bid.getAmount(), id);
        return true;
    }

    public String getId() {
        return id;
    }

    public String getItemId() {
        return itemId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public double getStartPrice() {
        return startPrice;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getStepPrice() {
        return stepPrice;
    }

    public Status getStatus() {
        return status;
    }

    public String getHighestBidderId() {
        return highestBidderId;
    }

    public List<BidTransaction> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
    }

    @Override
    public String toString() {
        return String.format("Auction[id=%s, itemId=%s, seller=%s, currentPrice=%.2f, winner=%s, status=%s]",
                id, itemId, sellerId, currentPrice, (highestBidderId != null ? highestBidderId : "None"), status);
    }
}
