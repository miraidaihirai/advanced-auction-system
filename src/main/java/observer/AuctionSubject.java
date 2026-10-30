package observer;

import model.BidTransaction;

/**
 * Interface Subject trong Observer Pattern.
 */
public interface AuctionSubject {
    void addObserver(AuctionObserver observer);
    void removeObserver(AuctionObserver observer);
    void notifyObservers(BidTransaction bid);
}
