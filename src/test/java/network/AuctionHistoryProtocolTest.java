package network;

import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionHistoryProtocolTest {

    private AuctionServer server;
    private int port;
    private final String auctionId = "AUC-HIST-SOCKET";

    @BeforeEach
    void setUp() throws IOException {
        AuctionManager manager = AuctionManager.getInstance();
        manager.clear();

        Seller seller = new Seller("SELLER-HIST-SOCK", "Seller Alice", "password123");
        Bidder bidder1 = new Bidder("BIDDER-SOCK-1", "Bob", "password123");
        Bidder bidder2 = new Bidder("BIDDER-SOCK-2", "Charlie", "password123");

        manager.registerUser(seller);
        manager.registerUser(bidder1);
        manager.registerUser(bidder2);

        Auction auction = new Auction(auctionId, "MacBook M3 Max", seller.getId(), 30_000_000, 1_000_000);
        auction.startAuction();
        manager.addAuction(auction);

        manager.placeBid(auctionId, "BIDDER-SOCK-1", "password123", 31_000_000);
        manager.placeBid(auctionId, "BIDDER-SOCK-2", "password123", 33_000_000);

        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }

        server = new AuctionServer(port);
        server.startAsync();
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
        AuctionManager.getInstance().clear();
    }

    @Test
    void testRequestHistoryViaSocketReturnsAllBids() throws Exception {
        AuctionClient client = new AuctionClient("localhost", port);
        CountDownLatch historyLatch = new CountDownLatch(1);
        AtomicReference<List<BidTransaction>> receivedHistory = new AtomicReference<>();

        client.addListener(new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
            }

            @Override
            public void onHistoryReceived(String aucId, List<BidTransaction> history) {
                if (auctionId.equals(aucId)) {
                    receivedHistory.set(history);
                    historyLatch.countDown();
                }
            }
        });

        client.connect();
        Thread.sleep(100);

        client.requestHistory(auctionId);

        assertTrue(historyLatch.await(3, TimeUnit.SECONDS), "Client phải nhận được lịch sử giao dịch qua Socket");

        List<BidTransaction> list = receivedHistory.get();
        assertNotNull(list);
        assertEquals(2, list.size());

        assertEquals("BIDDER-SOCK-1", list.get(0).getBidderId());
        assertEquals(31_000_000.0, list.get(0).getAmount());

        assertEquals("BIDDER-SOCK-2", list.get(1).getBidderId());
        assertEquals(33_000_000.0, list.get(1).getAmount());

        client.disconnect();
    }

    @Test
    void testRequestHistoryNonExistentAuctionReceivesError() throws Exception {
        AuctionClient client = new AuctionClient("localhost", port);
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<String> errorMsg = new AtomicReference<>("");

        client.addListener(new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
            }

            @Override
            public void onMessageReceived(String rawMessage) {
                if (rawMessage.startsWith(AuctionProtocol.RES_ERROR)) {
                    errorMsg.set(rawMessage);
                    errorLatch.countDown();
                }
            }
        });

        client.connect();
        Thread.sleep(100);

        client.requestHistory("AUC-DOES-NOT-EXIST");

        assertTrue(errorLatch.await(3, TimeUnit.SECONDS));
        assertTrue(errorMsg.get().contains("Auction not found"));

        client.disconnect();
    }
}

