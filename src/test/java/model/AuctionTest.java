
package model;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionTest {

    // ==========================================
    // CONSTRUCTOR TEST
    // ==========================================

    @Test
    void constructor_shouldInitializeFields() {
        Auction auction = new Auction(
                "AUCTION-001",
                "ITEM-001",
                "SELLER-001",
                20_000_000,
                1_000_000
        );

        assertEquals("AUCTION-001", auction.getId());
        assertEquals("ITEM-001", auction.getItemId());
        assertEquals("SELLER-001", auction.getSellerId());

        assertEquals(20_000_000, auction.getStartPrice(), 0.01);
        assertEquals(1_000_000, auction.getStepPrice(), 0.01);

        assertEquals(Auction.Status.OPEN, auction.getStatus());
        assertEquals(20_000_000, auction.getCurrentPrice(), 0.01);
        assertNull(auction.getHighestBidderId());
        assertTrue(auction.getBidHistory().isEmpty());
    }

    // ==========================================
    // A. AUCTION STATE MANAGEMENT TESTS
    // ==========================================

    @Test
    void startAuction_shouldChangeStatusToRunning() {
        Auction auction = createStateTestAuction();

        try {
            auction.startAuction(Duration.ofHours(1));

            assertEquals(
                    Auction.Status.RUNNING,
                    auction.getStatus()
            );
        } finally {
            finishRunningAuctionIfNeeded(auction);
        }
    }

    @Test
    void startAuction_withoutDuration_shouldUseDefaultDuration() {
        Auction auction = createStateTestAuction();

        try {
            auction.startAuction();

            assertEquals(
                    Auction.Status.RUNNING,
                    auction.getStatus()
            );
        } finally {
            finishRunningAuctionIfNeeded(auction);
        }
    }

    @Test
    void startAuction_withNullDuration_shouldThrowException() {
        Auction auction = createStateTestAuction();

        assertThrows(
                IllegalArgumentException.class,
                () -> auction.startAuction(null)
        );

        assertEquals(Auction.Status.OPEN, auction.getStatus());
    }

    @Test
    void startAuction_withZeroDuration_shouldThrowException() {
        Auction auction = createStateTestAuction();

        assertThrows(
                IllegalArgumentException.class,
                () -> auction.startAuction(Duration.ZERO)
        );

        assertEquals(Auction.Status.OPEN, auction.getStatus());
    }

    @Test
    void startAuction_withNegativeDuration_shouldThrowException() {
        Auction auction = createStateTestAuction();

        assertThrows(
                IllegalArgumentException.class,
                () -> auction.startAuction(Duration.ofSeconds(-1))
        );

        assertEquals(Auction.Status.OPEN, auction.getStatus());
    }

    @Test
    void startAuction_whenAlreadyRunning_shouldThrowException() {
        Auction auction = createStateTestAuction();

        try {
            auction.startAuction(Duration.ofHours(1));

            assertThrows(
                    IllegalStateException.class,
                    () -> auction.startAuction(Duration.ofHours(1))
            );

            assertEquals(
                    Auction.Status.RUNNING,
                    auction.getStatus()
            );
        } finally {
            finishRunningAuctionIfNeeded(auction);
        }
    }

    @Test
    void finishAuction_shouldChangeStatusToFinished() {
        Auction auction = createStateTestAuction();

        auction.startAuction(Duration.ofHours(1));
        auction.finishAuction();

        assertEquals(
                Auction.Status.FINISHED,
                auction.getStatus()
        );
    }

    @Test
    void finishAuction_whenNotRunning_shouldThrowException() {
        Auction auction = createStateTestAuction();

        // Auction vẫn đang OPEN, chưa được start
        assertThrows(
                IllegalStateException.class,
                auction::finishAuction
        );

        assertEquals(
                Auction.Status.OPEN,
                auction.getStatus()
        );
    }

    @Test
    void closeAuction_shouldChangeStatusToFinished() {
        Auction auction = createStateTestAuction();

        auction.startAuction(Duration.ofHours(1));
        auction.closeAuction();

        assertEquals(
                Auction.Status.FINISHED,
                auction.getStatus()
        );
    }

    // ==========================================
    // B. PAYMENT TESTS
    // ==========================================

    @Test
    void markPaid_whenAuctionNotFinished_shouldThrowException() {
        Auction auction = createStateTestAuction();

        try {
            auction.startAuction(Duration.ofHours(1));

            assertThrows(
                    IllegalStateException.class,
                    auction::markPaid
            );

            assertEquals(
                    Auction.Status.RUNNING,
                    auction.getStatus()
            );
        } finally {
            finishRunningAuctionIfNeeded(auction);
        }
    }

    @Test
    void markPaid_whenFinishedWithoutWinner_shouldThrowException() {
        Auction auction = createStateTestAuction();

        auction.startAuction(Duration.ofHours(1));
        auction.finishAuction();

        assertThrows(
                IllegalStateException.class,
                auction::markPaid
        );

        assertEquals(
                Auction.Status.FINISHED,
                auction.getStatus()
        );
    }

    @Test
    void markPaid_whenFinishedWithWinner_shouldChangeStatusToPaid() {
        Auction auction = createStateTestAuction();

        try {
            auction.startAuction(Duration.ofHours(1));

            BidTransaction bid = new BidTransaction(
                    "TX-STATE-001",
                    auction.getId(),
                    "BIDDER-01",
                    20_000_000
            );

            assertTrue(auction.placeBid(bid));

            auction.finishAuction();
            auction.markPaid();

            assertEquals(
                    Auction.Status.PAID,
                    auction.getStatus()
            );
        } finally {
            finishRunningAuctionIfNeeded(auction);
        }
    }

    // ==========================================
    // C. CANCELLATION TESTS
    // ==========================================

    @Test
    void cancelAuction_whenNotFinished_shouldThrowException() {
        Auction auction = createStateTestAuction();

        assertThrows(
                IllegalStateException.class,
                auction::cancelAuction
        );

        assertEquals(
                Auction.Status.OPEN,
                auction.getStatus()
        );
    }

    @Test
    void cancelAuction_whenFinished_shouldChangeStatusToCanceled() {
        Auction auction = createStateTestAuction();

        auction.startAuction(Duration.ofHours(1));
        auction.finishAuction();

        auction.cancelAuction();

        assertEquals(
                Auction.Status.CANCELED,
                auction.getStatus()
        );
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private Auction createStateTestAuction() {
        return new Auction(
                "AUC-STATE-TEST",
                "MacBook Pro",
                "SELLER-TEST",
                20_000_000,
                1_000_000
        );
    }

    private void finishRunningAuctionIfNeeded(Auction auction) {
        if (auction.getStatus() == Auction.Status.RUNNING) {
            auction.finishAuction();
        }
    }
}
