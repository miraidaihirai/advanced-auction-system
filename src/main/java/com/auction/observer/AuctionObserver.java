package com.auction.observer;

import com.auction.model.Auction;
import com.auction.model.BidTransaction;

/**
 * Interface Observer nhan thong bao khi co thay doi tren phien dau gia.
 * Ap dung Observer Pattern (DIP & OCP trong SOLID).
 */
public interface AuctionObserver {
    void onNewBid(Auction auction, BidTransaction bid);
    default void onAuctionClosed(Auction auction) {}
}
