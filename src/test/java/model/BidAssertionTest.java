package model;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BidAssertionTest {
    @Test
    void constructor_shouldInitializeFields(){
        BidTransaction tx = new BidTransaction("TX-001", "AUC-001 ", "BIDDER-01", 15000000);
        assertEquals("TX-001", tx.getId());
        assertEquals("AUC-001", tx.getAuctionId());
        assertEquals("BIDDER-01", tx.getBidderId());
        assertEquals(15000000, tx.getAmount());
        assertNotNull(tx.getTimestamp());
    }
}
