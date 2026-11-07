package model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BidAssertionTest {

    @Test
    void constructor_shouldInitializeFields() {
        BidTransaction tx = new BidTransaction(
                "TX-001", "AUC-001", "BIDDER-01", 15000000
        );

        System.out.println(
                "Actual auctionId = [" + tx.getAuctionId() + "]"
        );
        System.out.println(
                "AuctionId length = " + tx.getAuctionId().length()
        );

        assertAll(
                () -> assertEquals("TX-001", tx.getId()),
                () -> assertEquals("AUC-001", tx.getAuctionId()),
                () -> assertEquals("BIDDER-01", tx.getBidderId()),
                () -> assertEquals(15000000.0, tx.getAmount(), 0.001),
                () -> assertNotNull(tx.getTimestamp())
        );
    }
}