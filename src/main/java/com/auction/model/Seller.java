package com.auction.model;

import com.auction.observer.AuctionObserver;

/**
 * Nguoi ban trong phien dau gia.
 * Ke thua User (LSP), implement AuctionObserver (ISP, OCP, DIP).
 */
public class Seller extends User implements AuctionObserver {

    public Seller(String id, String name, String password) {
        super(id, name, password);
    }

    @Override
    public void displayInfo() {
        System.out.printf("Seller [ID: %s, Name: %s]%n", getId(), getName());
    }

    @Override
    public void onNewBid(Auction auction, BidTransaction bid) {
        System.out.printf("  [Thong bao -> Seller %s] San pham [%s] nhan bid moi: %.2f tu [%s]%n",
                getName(), auction.getItemId(), bid.getAmount(), bid.getBidderId());
    }

    @Override
    public void onAuctionClosed(Auction auction) {
        System.out.printf("  [Thong bao -> Seller %s] Phien [%s] da dong. Gia cuoi: %.2f (Nguoi thang: %s)%n",
                getName(), auction.getId(), auction.getCurrentPrice(), auction.getHighestBidderId());
    }
}
