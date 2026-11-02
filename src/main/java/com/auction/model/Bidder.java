package com.auction.model;

import com.auction.observer.AuctionObserver;

/**
 * Nguoi tham gia dau gia.
 * Ke thua User (LSP), implement AuctionObserver (ISP, OCP, DIP).
 */
public class Bidder extends User implements AuctionObserver {

    public Bidder(String id, String name, String password) {
        super(id, name, password);
    }

    @Override
    public void displayInfo() {
        System.out.printf("Bidder [ID: %s, Name: %s]%n", getId(), getName());
    }

    @Override
    public void onNewBid(Auction auction, BidTransaction bid) {
        if (bid.getBidderId().equals(this.getId())) {
            System.out.printf("  [Thong bao -> %s] Dat gia thanh cong: %.2f tren phien [%s]%n",
                    getName(), bid.getAmount(), auction.getId());
        } else {
            System.out.printf("  [Thong bao -> %s] Gia moi tren phien [%s]: %.2f boi [%s]%n",
                    getName(), auction.getId(), bid.getAmount(), bid.getBidderId());
        }
    }

    @Override
    public void onAuctionClosed(Auction auction) {
        if (getId().equals(auction.getHighestBidderId())) {
            System.out.printf("  [Thong bao -> %s] CHUC MUNG! Ban da thang phien [%s] voi gia %.2f%n",
                    getName(), auction.getId(), auction.getCurrentPrice());
        } else {
            System.out.printf("  [Thong bao -> %s] Phien [%s] da ket thuc. Winner: %s (%.2f)%n",
                    getName(), auction.getId(), auction.getHighestBidderId(), auction.getCurrentPrice());
        }
    }
}
