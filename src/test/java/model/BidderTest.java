
package model;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BidderTest {

        @Test
        void constructor_shouldInitializeFields() {
                Bidder bidder = new Bidder(
                                "BIDDER-01",
                                "Bob",
                                "password123");

                assertEquals("BIDDER-01", bidder.getId());
                assertEquals("Bob", bidder.getName());
                assertEquals("password123", bidder.getPassword());
        }

        @Test
        void displayInfo_shouldPrintBidderInformation() {
                Bidder bidder = createBidder();

                String output = captureOutput(bidder::displayInfo);

                assertTrue(output.contains("Bidder"));
                assertTrue(output.contains("BIDDER-01"));
                assertTrue(output.contains("Bob"));
        }

        @Test
        void onNewBid_whenBidderPlacedBid_shouldPrintSuccessMessage() {
                Bidder bidder = createBidder();

                Auction auction = createAuction();

                BidTransaction bid = new BidTransaction(
                                "TX-001",
                                auction.getId(),
                                bidder.getId(),
                                15_000_000);

                String output = captureOutput(
                                () -> bidder.onNewBid(auction, bid));

                assertAll(
                                () -> assertTrue(output.contains("Notification")),
                                () -> assertTrue(output.contains("Your bid was successful")),
                                () -> assertTrue(output.contains("AUC-001")));
        }

        @Test
        void onNewBid_whenAnotherBidderPlacedBid_shouldPrintUpdateMessage() {
                Bidder bidder = createBidder();

                Auction auction = createAuction();

                BidTransaction bid = new BidTransaction(
                                "TX-002",
                                auction.getId(),
                                "BIDDER-02",
                                15_000_000);

                String output = captureOutput(
                                () -> bidder.onNewBid(auction, bid));

                assertAll(
                                () -> assertTrue(output.contains("New bid on auction"), output),
                                () -> assertTrue(output.contains("AUC-001"), output),
                                () -> assertTrue(output.contains("BIDDER-02"), output));
        }

        @Test
        void onAuctionClosed_whenBidderIsWinner_shouldPrintCongratulations() {
                Bidder winner = createBidder();

                Auction auction = createFinishedAuction("BIDDER-01");

                String output = captureOutput(
                                () -> winner.onAuctionClosed(auction));

                assertAll(
                                () -> assertTrue(output.contains("CONGRATULATIONS!")),
                                () -> assertTrue(output.contains("You won auction")),
                                () -> assertTrue(output.contains("AUC-001")));
        }

        @Test
        void onAuctionClosed_whenBidderIsNotWinner_shouldPrintWinnerInformation() {
                Bidder bidder = new Bidder(
                                "BIDDER-02",
                                "Charlie",
                                "password123");

                Auction auction = createFinishedAuction("BIDDER-01");

                String output = captureOutput(
                                () -> bidder.onAuctionClosed(auction));

                assertAll(
                                () -> assertTrue(output.contains("Auction")),
                                () -> assertTrue(output.contains("has ended")),
                                () -> assertTrue(output.contains("BIDDER-01")),
                                () -> assertTrue(output.contains("AUC-001")));
        }

        // ==========================================
        // HELPER METHODS
        // ==========================================

        private Bidder createBidder() {
                return new Bidder(
                                "BIDDER-01",
                                "Bob",
                                "password123");
        }

        private Auction createAuction() {
                return new Auction(
                                "AUC-001",
                                "ITEM-001",
                                "SELLER-01",
                                10_000_000,
                                500_000);
        }

        private Auction createFinishedAuction(String winnerId) {
                Auction auction = createAuction();

                auction.startAuction(Duration.ofHours(1));

                BidTransaction bid = new BidTransaction(
                                "TX-CLOSE-001",
                                auction.getId(),
                                winnerId,
                                10_500_000);

                auction.placeBid(bid);
                auction.finishAuction();

                return auction;
        }

        private String captureOutput(Runnable action) {
                PrintStream originalOut = System.out;

                ByteArrayOutputStream output = new ByteArrayOutputStream();

                try (PrintStream testOut = new PrintStream(
                                output,
                                true,
                                StandardCharsets.UTF_8)) {
                        System.setOut(testOut);

                        action.run();

                        testOut.flush();

                        return output.toString(StandardCharsets.UTF_8);

                } finally {
                        System.setOut(originalOut);
                }
        }
}
