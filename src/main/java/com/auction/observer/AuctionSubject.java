package com.auction.observer;

import com.auction.model.BidTransaction;

/**
 * Interface Subject trong Observer Pattern.
 */
public interface AuctionSubject {
    void addObserver(AuctionObserver observer);
    void removeObserver(AuctionObserver observer);
    void notifyObservers(BidTransaction bid);
}
