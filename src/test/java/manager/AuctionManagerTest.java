package manager;

import exceptions.AuthenticationException;
import exceptions.AuctionClosedException;
import model.Auction;
import model.BidTransaction;
import model.Bidder;
import model.Seller;
import observer.AuctionObserver;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
//Do Singleton và không có phương thức reset dữ liệu
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionManagerTest {
    private final AuctionManager manager = AuctionManager.getInstance();

    @Test
    void getInstance_shouldReturnSameInstance() {
        AuctionManager another = AuctionManager.getInstance();
        assertSame(manager, another);
    }

    @Test
    void addAuction_shouldIgnoreNullAndDuplicateAuction() {
        Auction auction = createAuction();
        manager.addAuction(auction);
        manager.addAuction(null);
        Auction duplicate = new Auction(auction.getId(), "DIFFERENT-ITEM", "SELLER-02", 20_000_000, 1_000_000);
        manager.addAuction(duplicate);
        assertSame(auction, manager.getAuction(auction.getId()));
    }

    @Test
    void getAuction_shouldReturnAuctionOrNull() {
        Auction auction = createAuction();
        manager.addAuction(auction);
        assertAll(
                () -> assertSame(auction, manager.getAuction(auction.getId())),
                () -> assertNull(manager.getAuction(uniqueId("UNKNOWN"))));
    }

    @Test
    void getAllAuction_shouldReturnCopyOfAuctionList() {
        Auction auction = createAuction();
        manager.addAuction(auction);
        List<Auction> auctions = manager.getAllAuctions();
        assertTrue(auctions.contains(auction));
        // Modify the returned list should not change the manager
        auctions.remove(auction);
        assertSame(auction, manager.getAuction(auction.getId()));
    }

    @Test
    void getOpenAuction_shouldReturnOnlyOpenAndRunningAuctions() {
        Auction openAuction = createAuction();
        Auction runningAuction = createAuction();
        Auction finishedAuction = createAuction();

        manager.addAuction(openAuction);
        manager.addAuction(runningAuction);
        manager.addAuction(finishedAuction);
        runningAuction.startAuction(Duration.ofHours(1));
        finishedAuction.startAuction(Duration.ofHours(1));
        finishedAuction.finishAuction();
        try {
            List<Auction> openAuctions = manager.getOpenAuctions();
            assertAll(
                    () -> assertTrue(openAuctions.contains(openAuction)),
                    () -> assertTrue(openAuctions.contains(runningAuction)),
                    () -> assertFalse(openAuctions.contains(finishedAuction)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> openAuctions.add(createAuction())));
        } finally {
            if (runningAuction.getStatus() == Auction.Status.RUNNING) {
                runningAuction.finishAuction();
            }
        }
    }

    @Test
    void registerUser_shouldRejectNullAndDuplicateUsers() {
        assertThrows(IllegalArgumentException.class, () -> manager.registerUser(null));
        String userId = uniqueId("BIDDER");
        Bidder bidder = new Bidder(userId, "Bob", "password123");
        manager.registerUser(bidder);
        assertDoesNotThrow(() -> manager.registerUser(bidder));
        Bidder duplicate = new Bidder(userId, "Charlie", "password456");
        assertThrows(IllegalArgumentException.class, () -> manager.registerUser(duplicate));
        assertSame(bidder, manager.getUser(userId));
    }

    @Test
    void getUser_shouldReturnRegisteredUserOrNull() {
        Bidder bidder = createBidder();
        manager.registerUser(bidder);
        assertAll(
                () -> assertSame(
                        bidder,
                        manager.getUser(bidder.getId())),
                () -> assertNull(
                        manager.getUser(uniqueId("UNKNOWN"))));
    }

    @Test
    void authenticate_shouldHandleValidAndInvalidCredentials() {
        Bidder bidder = createBidder();
        manager.registerUser(bidder);

        assertAll(
                () -> assertSame(
                        bidder,
                        manager.authenticate(
                                bidder.getId(),
                                "password123")),

                () -> assertNull(
                        manager.authenticate(
                                bidder.getId(),
                                "wrongPassword")),

                () -> assertNull(
                        manager.authenticate(
                                bidder.getId(),
                                null)),

                () -> assertNull(
                        manager.authenticate(
                                uniqueId("UNKNOWN"),
                                "password123")));
    }
     @Test
    void registerObserver_shouldRegisterObserverForExistingAuction() {
        Auction auction = createAuction();
        manager.addAuction(auction);

        AtomicInteger bidNotifications = new AtomicInteger(0);
        AtomicInteger closedNotifications = new AtomicInteger(0);

        AuctionObserver observer = new AuctionObserver() {

            @Override
            public void onNewBid(Auction auction, BidTransaction bid) {
                bidNotifications.incrementAndGet();
            }

            @Override
            public void onAuctionClosed(Auction auction) {
                closedNotifications.incrementAndGet();
            }
        };

        manager.registerObserver(auction.getId(), observer);

        auction.startAuction(Duration.ofHours(1));

        try {
            BidTransaction bid = new BidTransaction(
                    uniqueId("TX"),
                    auction.getId(),
                    "BIDDER-01",
                    10_500_000
            );

            assertTrue(auction.placeBid(bid));
            assertEquals(1, bidNotifications.get());

            auction.finishAuction();

            assertEquals(1, closedNotifications.get());

        } finally {
            if (auction.getStatus() == Auction.Status.RUNNING) {
                auction.finishAuction();
            }
        }
    }

    @Test
    void registerObserver_whenAuctionDoesNotExist_shouldNotThrowException() {
        assertDoesNotThrow(
                () -> manager.registerObserver(
                        uniqueId("UNKNOWN"),
                        null
                )
        );
    }

    @Test
    void placeBid_shouldRejectInvalidBidderCredentials() {
        String auctionId = uniqueId("AUC");

        Bidder bidder = createBidder();
        Seller seller = new Seller(
                uniqueId("SELLER"),
                "Alice",
                "password123"
        );

        manager.registerUser(bidder);
        manager.registerUser(seller);

        assertAll(
                // User does not exist.
                () -> assertThrows(
                        AuthenticationException.class,
                        () -> manager.placeBid(
                                auctionId,
                                uniqueId("UNKNOWN"),
                                "password123",
                                10_500_000
                        )
                ),

                // Seller is not a Bidder.
                () -> assertThrows(
                        AuthenticationException.class,
                        () -> manager.placeBid(
                                auctionId,
                                seller.getId(),
                                "password123",
                                10_500_000
                        )
                ),

                // Incorrect password.
                () -> assertThrows(
                        AuthenticationException.class,
                        () -> manager.placeBid(
                                auctionId,
                                bidder.getId(),
                                "wrongPassword",
                                10_500_000
                        )
                ),

                // Null password.
                () -> assertThrows(
                        AuthenticationException.class,
                        () -> manager.placeBid(
                                auctionId,
                                bidder.getId(),
                                null,
                                10_500_000
                        )
                )
        );
    }

    @Test
    void placeBid_whenAuctionDoesNotExist_shouldThrowException() {
        Bidder bidder = createBidder();
        manager.registerUser(bidder);

        assertThrows(
                AuctionClosedException.class,
                () -> manager.placeBid(
                        uniqueId("UNKNOWN"),
                        bidder.getId(),
                        "password123",
                        10_500_000
                )
        );
    }

    @Test
    void placeBid_shouldPlaceBidAndGenerateUniqueTransactionIds() {
        Bidder bidder = createBidder();
        Auction auction = createAuction();

        manager.registerUser(bidder);
        manager.addAuction(auction);

        auction.startAuction(Duration.ofHours(1));

        try {
            boolean firstResult = manager.placeBid(
                    auction.getId(),
                    bidder.getId(),
                    "password123",
                    10_500_000
            );

            boolean secondResult = manager.placeBid(
                    auction.getId(),
                    bidder.getId(),
                    "password123",
                    11_000_000
            );

            List<BidTransaction> history = auction.getBidHistory();

            assertAll(
                    () -> assertTrue(firstResult),
                    () -> assertTrue(secondResult),
                    () -> assertEquals(2, history.size()),

                    () -> assertEquals(
                            bidder.getId(),
                            auction.getHighestBidderId()
                    ),

                    () -> assertEquals(
                            11_000_000.0,
                            auction.getCurrentPrice(),
                            0.001
                    ),

                    () -> assertNotEquals(
                            history.get(0).getId(),
                            history.get(1).getId()
                    )
            );

        } finally {
            if (auction.getStatus() == Auction.Status.RUNNING) {
                auction.finishAuction();
            }
        }
    }
    // Helper method
    private String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private Bidder createBidder() {
        return new Bidder(uniqueId("BIDDER"), "Bob", "password123");
    }

    private Auction createAuction() {
        String token = UUID.randomUUID().toString();
        return new Auction("AUC-" + token, "ITEM-" + token, "SELLER-" + token, 10_000_000, 500_000);
    }
}
