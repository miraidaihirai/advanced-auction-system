package com.auction.model;

import com.auction.observer.AuctionObserver;

/**
 * Seller in an auction.
 * Extends User (LSP), implements AuctionObserver (ISP, OCP, DIP).
 */
public class Seller extends User implements AuctionObserver {
    private static final long serialVersionUID = 1L;

    public Seller(String id, String name, String password) {
        super(id, name, password, 0.0);
    }

    public Seller(String id, String name, String password, double initialBalance) {
        super(id, name, password, initialBalance);
    }

    @Override
    public String getRole() {
        return "SELLER";
    }

    @Override
    public void displayInfo() {
        System.out.printf("Seller [ID: %s, Name: %s]%n",
                getId(), getName());
    }

    @Override
    public void onNewBid(Auction auction, BidTransaction bid) {
        System.out.printf(
                "  [Notification -> Seller %s] Your item [%s] has received a new bid: %.2f from [%s]%n",
                getName(),
                auction.getItemId(),
                bid.getAmount(),
                bid.getBidderId());
    }

    @Override
    public void onAuctionClosed(Auction auction) {
        System.out.printf(
                "  [Notification -> Seller %s] Auction [%s] has closed. Final price: %.2f (Winner: %s)%n",
                getName(),
                auction.getId(),
                auction.getCurrentPrice(),
                auction.getHighestBidderId());
    }
}