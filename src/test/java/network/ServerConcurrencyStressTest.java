package network;

import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency & Stress Test Suite:
 * - Kiểm thử chịu tải cao khi nhiều Client đặt giá đồng thời (Race Condition & Lost Update protection).
 * - Kiểm thử phát hiện và dọn dẹp tài nguyên khi Client ngắt kết nối đột ngột (Zero Socket/Thread leak).
 * - Kiểm thử cơ chế Heartbeat timeout và tự động thu hồi tài nguyên.
 * - Kiểm thử luồng kết thúc phiên tự động, chốt winner và bắn thông báo riêng cho Seller và Winner.
 */
public class ServerConcurrencyStressTest {

    private AuctionServer server;
    private int testPort;
    private AuctionManager manager;

    @BeforeEach
    void setUp() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            testPort = socket.getLocalPort();
        }
        manager = AuctionManager.getInstance();

        server = new AuctionServer(testPort);
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
    }

    private static class RawClientConnection implements AutoCloseable {
        final Socket socket;
        final BufferedReader reader;
        final PrintWriter writer;

        RawClientConnection(String host, int port) throws IOException {
            this.socket = new Socket(host, port);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            // Consume initial welcome message
            reader.readLine();
        }

        void send(String command) {
            writer.println(command);
        }

        String readLine() throws IOException {
            return reader.readLine();
        }

        String readCommandResponse() throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("SUCCESS") || line.startsWith("ERROR")) {
                    return line;
                }
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            try {
                socket.close();
            } finally {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
                writer.close();
            }
        }
    }

    @Test
    @DisplayName("Stress Test: 10 client cùng đặt giá đồng thời trên 1 phiên (Concurrency / ReentrantLock)")
    void testConcurrentBiddingHighContention() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String auctionId = "AUC-STRESS-" + suffix;
        String sellerId = "SELLER-" + suffix;

        Seller seller = new Seller(sellerId, "Seller", "password123");
        manager.registerUser(seller);

        double startPrice = 1_000_000;
        double stepPrice = 50_000;
        Auction auction = new Auction(auctionId, "Gaming Laptop", sellerId, startPrice, stepPrice);
        auction.startAuction();
        manager.addAuction(auction);

        int clientCount = 10;
        List<String> bidderIds = new ArrayList<>();
        List<RawClientConnection> clients = new ArrayList<>();

        for (int i = 0; i < clientCount; i++) {
            String bidderId = "BIDDER-" + suffix + "-" + i;
            bidderIds.add(bidderId);
            manager.registerUser(new Bidder(bidderId, "Bidder " + i, "password123"));

            RawClientConnection conn = new RawClientConnection("localhost", testPort);
            clients.add(conn);

            conn.send("LOGIN " + bidderId + " password123");
            String loginResp = conn.readLine();
            assertTrue(loginResp.startsWith("SUCCESS LOGIN"), "Client " + i + " should log in");

            conn.send("JOIN " + auctionId);
            String joinResp = conn.readLine();
            assertTrue(joinResp.startsWith("SUCCESS JOIN"), "Client " + i + " should join auction");
        }

        assertEquals(clientCount, server.getConnectedClientCount());

        ExecutorService pool = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(clientCount);
        AtomicInteger successfulBids = new AtomicInteger(0);

        // Mỗi client đặt giá với mức giá riêng tăng dần
        for (int i = 0; i < clientCount; i++) {
            final int index = i;
            final RawClientConnection conn = clients.get(index);
            final double bidAmount = startPrice + (index + 1) * stepPrice;

            pool.submit(() -> {
                try {
                    startGun.await();
                    conn.send(String.format(java.util.Locale.US, "BID %s %.2f", auctionId, bidAmount));
                    String resp = conn.readCommandResponse();
                    if (resp != null && resp.startsWith("SUCCESS BID")) {
                        successfulBids.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Client " + index + " error: " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startGun.countDown(); // Bắn phát súng khởi động đồng thời
        boolean finishedInTime = finishLatch.await(5, TimeUnit.SECONDS);
        assertTrue(finishedInTime, "Tất cả các thread bid phải hoàn thành trong 5 giây");
        pool.shutdown();

        // Kiểm tra tính toàn vẹn Concurrency:
        // Ít nhất 1 lượt bid phải thành công
        assertTrue(successfulBids.get() >= 1, "Ít nhất 1 lượt bid phải thành công");
        assertTrue(auction.getCurrentPrice() >= startPrice + stepPrice, "Giá hiện tại phải lớn hơn giá khởi điểm");
        assertNotNull(auction.getHighestBidderId(), "Phiên phải ghi nhận người giữ giá cao nhất");

        // Dọn dẹp clients
        for (RawClientConnection conn : clients) {
            conn.close();
        }
    }

    @Test
    @DisplayName("Stress Test: 20 client kết nối dồn dập rồi đột ngột ngắt kết nối (Zero socket/thread leak)")
    void testRapidClientConnectionsAndSuddenDisconnections() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String auctionId = "AUC-LEAK-" + suffix;
        String sellerId = "SELLER-" + suffix;

        Seller seller = new Seller(sellerId, "Seller", "password123");
        manager.registerUser(seller);

        Auction auction = new Auction(auctionId, "iPhone 16 Pro", sellerId, 25_000_000, 500_000);
        auction.startAuction();
        manager.addAuction(auction);

        int totalClients = 20;
        List<RawClientConnection> clientList = new ArrayList<>();

        for (int i = 0; i < totalClients; i++) {
            RawClientConnection conn = new RawClientConnection("localhost", testPort);
            clientList.add(conn);

            conn.send("JOIN " + auctionId);
            String resp = conn.readLine();
            assertTrue(resp.startsWith("SUCCESS JOIN"));
        }

        assertEquals(totalClients, server.getConnectedClientCount());
        // Số observers trên auction = 20 client + 1 AuctionServer (global observer)
        assertTrue(auction.getObserverCount() >= totalClients);

        // Giả lập ngắt kết nối đột ngột (abort socket mà KHÔNG gửi QUIT)
        for (RawClientConnection conn : clientList) {
            conn.socket.close();
        }

        // Chờ server phát hiện EOF / reset và kích hoạt ClientHandler.close()
        long start = System.currentTimeMillis();
        while (server.getConnectedClientCount() > 0 && (System.currentTimeMillis() - start < 3000)) {
            Thread.sleep(100);
        }

        assertEquals(0, server.getConnectedClientCount(), "Toàn bộ client bị ngắt đột ngột phải được dọn dẹp khỏi activeClients");
        // Observers cũng phải được tự động gỡ bỏ khỏi Auction
        assertEquals(1, auction.getObserverCount(), "Chỉ còn lại duy nhất AuctionServer global observer, không có observer nào bị rò rỉ");

        for (RawClientConnection conn : clientList) {
            try {
                conn.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Stress Test: Kiểm tra Heartbeat timeout tự động dọn tài nguyên ClientHandler")
    void testHeartbeatTimeoutAutoCleanup() throws Exception {
        // Cấu hình timeout ngắn 300ms để kiểm thử cơ chế timeout tự động dọn dẹp
        server.setDefaultClientTimeoutMs(300);

        try (RawClientConnection conn = new RawClientConnection("localhost", testPort)) {
            assertEquals(1, server.getConnectedClientCount());

            // Không gửi dữ liệu gì để ép kích hoạt SocketTimeoutException
            long start = System.currentTimeMillis();
            while (server.getConnectedClientCount() > 0 && (System.currentTimeMillis() - start < 3000)) {
                Thread.sleep(100);
            }

            assertEquals(0, server.getConnectedClientCount(), "Client không gửi heartbeat phải bị timeout và dọn dẹp sạch sẽ");
        }
    }

    @Test
    @DisplayName("End-to-End: Luồng kết thúc phiên tự động, chốt winner, thông báo Seller/Winner và thanh toán PAID")
    void testAuctionAutoFinishAndWinnerSellerNotifications() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String auctionId = "AUC-FLOW-" + suffix;
        String sellerId = "SELLER-" + suffix;
        String bidderId = "BIDDER-" + suffix;

        Seller seller = new Seller(sellerId, "Seller", "password123");
        Bidder bidder = new Bidder(bidderId, "Bidder", "password123");
        manager.registerUser(seller);
        manager.registerUser(bidder);

        Auction auction = new Auction(auctionId, "Diamond Ring", sellerId, 5_000_000, 200_000);
        // Thiết lập thời gian phiên chỉ kéo dài 400 mili-giây
        auction.startAuction(Duration.ofMillis(400));
        manager.addAuction(auction);

        try (RawClientConnection sellerConn = new RawClientConnection("localhost", testPort);
             RawClientConnection bidderConn = new RawClientConnection("localhost", testPort)) {

            // Đăng nhập Seller
            sellerConn.send("LOGIN " + sellerId + " password123");
            assertTrue(sellerConn.readLine().startsWith("SUCCESS LOGIN"));

            // Đăng nhập Bidder và tham gia phiên
            bidderConn.send("LOGIN " + bidderId + " password123");
            assertTrue(bidderConn.readLine().startsWith("SUCCESS LOGIN"));

            bidderConn.send("JOIN " + auctionId);
            assertTrue(bidderConn.readLine().startsWith("SUCCESS JOIN"));

            // Bidder đặt giá hợp lệ
            bidderConn.send(String.format(java.util.Locale.US, "BID %s %.2f", auctionId, 5200000.0));
            String bidResp = bidderConn.readCommandResponse();
            assertTrue(bidResp.startsWith("SUCCESS BID"), "Bid phải thành công: " + bidResp);

            // Chờ phiên hết giờ (400ms) để Timer tự động kết thúc phiên
            Thread.sleep(600);

            assertEquals(Auction.Status.FINISHED, auction.getStatus(), "Phiên phải tự động chuyển sang FINISHED");
            assertEquals(bidderId, auction.getHighestBidderId(), "Bidder phải là người thắng cuộc");

            // Kiểm tra Winner nhận được thông báo riêng
            String winnerMsg = bidderConn.readLine();
            while (winnerMsg != null && !winnerMsg.startsWith("NOTIFY WINNER") && !winnerMsg.startsWith("NOTIFY CLOSED")) {
                winnerMsg = bidderConn.readLine();
            }
            assertNotNull(winnerMsg, "Winner phải nhận được thông báo kết thúc");
            assertTrue(winnerMsg.startsWith("NOTIFY WINNER") || winnerMsg.startsWith("NOTIFY CLOSED"));

            // Kiểm tra Seller nhận được thông báo riêng
            String sellerMsg = sellerConn.readLine();
            while (sellerMsg != null && !sellerMsg.startsWith("NOTIFY SELLER")) {
                sellerMsg = sellerConn.readLine();
            }
            assertNotNull(sellerMsg, "Seller phải nhận được thông báo riêng NOTIFY SELLER");
            assertTrue(sellerMsg.startsWith("NOTIFY SELLER"), "Seller message: " + sellerMsg);

            // Winner thực hiện thanh toán (PAY)
            bidderConn.send("PAY " + auctionId);
            String payResp = bidderConn.readCommandResponse();
            assertTrue(payResp.startsWith("SUCCESS PAY"), "Thanh toán phải thành công: " + payResp);

            assertEquals(Auction.Status.PAID, auction.getStatus(), "Phiên phải chuyển trạng thái sang PAID");

            // Cả Seller và Bidder đều nhận được thông báo phiên đã thanh toán
            String paidNotification = sellerConn.readLine();
            while (paidNotification != null && !paidNotification.startsWith("NOTIFY PAID")) {
                paidNotification = sellerConn.readLine();
            }
            assertNotNull(paidNotification, "Seller phải nhận thông báo NOTIFY PAID");
            assertTrue(paidNotification.startsWith("NOTIFY PAID"));
        }
    }
}
