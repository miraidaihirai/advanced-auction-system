package model;

import observer.AuctionObserver;

/**
 * A participant in an auction.
 * Extends User (LSP), implements AuctionObserver (ISP, OCP, DIP).
 */
public class Bidder extends User implements AuctionObserver {

    public Bidder(String id, String name, String password) {
        super(id, name, password);
    }

    @Override
    public void displayInfo() {
        System.out.printf("Bidder [ID: %s, Name: %s]%n",
                getId(), getName());
    }

    @Override
    public void onNewBid(Auction auction, BidTransaction bid) {
        if (bid.getBidderId().equals(this.getId())) {
            System.out.printf(
                    "  [Notification -> %s] Your bid was successful: %.2f on auction [%s]%n",
                    getName(), bid.getAmount(), auction.getId()
            );
        } else {
            System.out.printf(
                    "  [Notification -> %s] New bid on auction [%s]: %.2f by [%s]%n",
                    getName(), auction.getId(), bid.getAmount(), bid.getBidderId()
            );
        }
    }

    @Override
    public void onAuctionClosed(Auction auction) {
        if (getId().equals(auction.getHighestBidderId())) {
            System.out.printf(
                    "  [Notification -> %s] CONGRATULATIONS! You won auction [%s] with a bid of %.2f%n",
                    getName(), auction.getId(), auction.getCurrentPrice()
            );
        } else {
            System.out.printf(
                    "  [Notification -> %s] Auction [%s] has ended. Winner: %s (%.2f)%n",
                    getName(), auction.getId(),
                    auction.getHighestBidderId(), auction.getCurrentPrice()
            );
        }
    }
}