package com.auction.model;

import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminTest {

    @Test
    void testAdminCreationAndValidation() {
        Admin admin = new Admin("ADMIN-01", "Administrator", "password123");
        assertEquals("ADMIN-01", admin.getId());
        assertEquals("Administrator", admin.getName());
        assertEquals("password123", admin.getPassword());

        assertThrows(IllegalArgumentException.class, () -> new Admin("", "Admin", "password123"));
        assertThrows(IllegalArgumentException.class, () -> new Admin("ADMIN-02", "", "password123"));
        assertThrows(IllegalArgumentException.class, () -> new Admin("ADMIN-03", "Admin", "short"));
    }

    @Test
    void testAdminObserverNotifications() {
        Admin admin = new Admin("ADMIN-01", "Administrator", "password123");
        Auction auction = new Auction("AUC-TEST", "Test Item", "SELLER-01", 1_000_000, 100_000);
        BidTransaction bid = new BidTransaction("TX-01", "AUC-TEST", "BIDDER-01", 1_100_000.0);

        assertDoesNotThrow(() -> admin.displayInfo());
        assertDoesNotThrow(() -> admin.onNewBid(auction, bid));
        assertDoesNotThrow(() -> admin.onAuctionClosed(auction));
    }
}

