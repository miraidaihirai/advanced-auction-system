
package model;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SellerTest {

    @Test
    void constructor_shouldInitializeFields() {
        Seller seller = createSeller();

        assertEquals("SELLER-01", seller.getId());
        assertEquals("Alice", seller.getName());
        assertEquals("password123", seller.getPassword());
    }

    @Test
    void displayInfo_shouldPrintSellerInformation() {
        Seller seller = createSeller();

        String output = captureOutput(seller::displayInfo);

        assertTrue(output.contains("Seller"));
        assertTrue(output.contains("SELLER-01"));
        assertTrue(output.contains("Alice"));
    }

    @Test
    void onNewBid_shouldPrintNewBidInformation() {
        Seller seller = createSeller();

        Auction auction = createAuction();

        BidTransaction bid = new BidTransaction(
                "TX-001",
                auction.getId(),
                "BIDDER-01",
                15_000_000
        );

        String output = captureOutput(
                () -> seller.onNewBid(auction, bid)
        );

        assertTrue(output.contains("Seller Alice"));
        assertTrue(output.contains("ITEM-001"));
        assertTrue(output.contains("BIDDER-01"));
        assertTrue(output.contains("nhan bid moi"));
    }

    @Test
    void onAuctionClosed_shouldPrintFinalAuctionInformation() {
        Seller seller = createSeller();

        Auction auction = createFinishedAuction();

        String output = captureOutput(
                () -> seller.onAuctionClosed(auction)
        );

        assertTrue(output.contains("Seller Alice"));
        assertTrue(output.contains("AUC-001"));
        assertTrue(output.contains("BIDDER-01"));
        assertTrue(output.contains("da dong"));
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private Seller createSeller() {
        return new Seller(
                "SELLER-01",
                "Alice",
                "password123"
        );
    }

    private Auction createAuction() {
        return new Auction(
                "AUC-001",
                "ITEM-001",
                "SELLER-01",
                10_000_000,
                500_000
        );
    }

    private Auction createFinishedAuction() {
        Auction auction = createAuction();

        auction.startAuction(Duration.ofHours(1));

        BidTransaction bid = new BidTransaction(
                "TX-001",
                auction.getId(),
                "BIDDER-01",
                10_500_000
        );

        auction.placeBid(bid);
        auction.finishAuction();

        return auction;
    }

    private String captureOutput(Runnable action) {
        PrintStream originalOut = System.out;

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try (PrintStream testOut = new PrintStream(
                output,
                true,
                StandardCharsets.UTF_8
        )) {
            System.setOut(testOut);

            action.run();

            testOut.flush();

            return output.toString(StandardCharsets.UTF_8);

        } finally {
            System.setOut(originalOut);
        }
    }
}

