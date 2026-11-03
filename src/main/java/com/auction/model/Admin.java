package com.auction.model;

import com.auction.observer.AuctionObserver;

/**
 * Quản trị viên hệ thống (Admin).
 * Kế thừa User (LSP), implement AuctionObserver (ISP, OCP, DIP) để giám sát các phiên đấu giá.
 */
public class Admin extends User implements AuctionObserver {

    public Admin(String id, String name, String password) {
        super(id, name, password);
    }

    @Override
    public void displayInfo() {
        System.out.printf("Admin [ID: %s, Name: %s]%n", getId(), getName());
    }

    @Override
    public void onNewBid(Auction auction, BidTransaction bid) {
        System.out.printf("  [Giam sat Admin] Phien [%s] nhan bid: %.2f tu [%s]%n",
                auction.getId(), bid.getAmount(), bid.getBidderId());
    }

    @Override
    public void onAuctionClosed(Auction auction) {
        System.out.printf("  [Giam sat Admin] Phien [%s] da dong. Gia cuoi: %.2f (Winner: %s)%n",
                auction.getId(), auction.getCurrentPrice(), auction.getHighestBidderId());
    }
}
