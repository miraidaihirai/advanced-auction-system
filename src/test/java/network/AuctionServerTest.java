package network;

import manager.AuctionManager;
import model.Auction;
import model.Bidder;
import model.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionServerTest {

    private AuctionServer server;
    private int testPort;
    private String auctionId;
    private String sellerId;
    private String bobId;
    private String charlieId;

    @BeforeEach
    void setUp() throws IOException {
        // Find a random available port
        try (ServerSocket socket = new ServerSocket(0)) {
            testPort = socket.getLocalPort();
        }

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        auctionId = "AUC-" + suffix;
        sellerId = "SELLER-" + suffix;
        bobId = "BOB-" + suffix;
        charlieId = "CHARLIE-" + suffix;

        // Initialize test data with unique IDs
        AuctionManager manager = AuctionManager.getInstance();
        Seller alice = new Seller(sellerId, "Alice", "password123");
        Bidder bob = new Bidder(bobId, "Bob", "password123");
        Bidder charlie = new Bidder(charlieId, "Charlie", "password123");

        manager.registerUser(alice);
        manager.registerUser(bob);
        manager.registerUser(charlie);

        Auction testAuction = new Auction(auctionId, "MacBook Pro", sellerId, 20_000_000, 1_000_000);
        testAuction.startAuction();
        manager.addAuction(testAuction);

        server = new AuctionServer(testPort);
        server.startAsync();

        // Brief delay to allow server socket to bind
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testServerStartsAndAcceptsMultipleClients() throws IOException, InterruptedException {
        AuctionClient client1 = new AuctionClient("localhost", testPort);
        AuctionClient client2 = new AuctionClient("localhost", testPort);

        try {
            client1.connect();
            client2.connect();

            Thread.sleep(150);
            assertEquals(2, server.getConnectedClientCount());
            assertTrue(client1.isConnected());
            assertTrue(client2.isConnected());
        } finally {
            client1.disconnect();
            client2.disconnect();
        }
    }

    @Test
    void testRealtimeBroadcastViaObserverPattern() throws Exception {
        AuctionClient bobClient = new AuctionClient("localhost", testPort);
        AuctionClient charlieClient = new AuctionClient("localhost", testPort);

        CountDownLatch charlieReceivedBidLatch = new CountDownLatch(1);
        AtomicReference<Double> receivedPrice = new AtomicReference<>(0.0);
        AtomicReference<String> receivedBidder = new AtomicReference<>("");

        charlieClient.addListener(new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String id, double newPrice, String bidderId, String itemId) {
                if (auctionId.equals(id)) {
                    receivedPrice.set(newPrice);
                    receivedBidder.set(bidderId);
                    charlieReceivedBidLatch.countDown();
                }
            }
        });

        try {
            bobClient.connect();
            charlieClient.connect();

            // Bob logs in
            bobClient.login(bobId, "password123");

            // Charlie logs in and subscribes as Observer to auction
            charlieClient.login(charlieId, "password123");
            charlieClient.joinAuction(auctionId);

            Thread.sleep(150);

            // Bob places a bid: 21,000,000 (valid bid: start 20m + step 1m)
            bobClient.placeBid(auctionId, 21_000_000);

            // Charlie should receive realtime broadcast notification via Observer Pattern over socket
            boolean received = charlieReceivedBidLatch.await(3, TimeUnit.SECONDS);
            assertTrue(received, "Charlie should receive realtime price update via Observer socket broadcast");
            assertEquals(21_000_000.0, receivedPrice.get(), 0.01);
            assertEquals(bobId, receivedBidder.get());
        } finally {
            bobClient.disconnect();
            charlieClient.disconnect();
        }
    }
}
