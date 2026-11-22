package network;

import com.auction.exceptions.AuthenticationException;
import com.auction.manager.AuctionManager;
import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BidXRoleWorkflowTest {

    private AuctionServer server;
    private int port;
    private AuctionManager manager;

    @BeforeEach
    void setUp() throws Exception {
        manager = AuctionManager.getInstance();
        manager.clear();
        manager.setAutoSave(false);

        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }
        server = new AuctionServer(port);
        server.startAsync();

        long deadline = System.currentTimeMillis() + 3000;
        while (!server.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertTrue(server.isRunning(), "BidX server must start");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        manager.clear();
    }

    @Test
    @DisplayName("End-to-End BidX: Seller gửi yêu cầu -> Admin duyệt -> Bidder đặt giá -> Đóng phiên tiền chảy về ví Seller")
    void fullRoleWorkflow_sellerRequestAdminApproveBidderBidAndPayoutToSeller() throws Exception {
        String suf = UUID.randomUUID().toString().substring(0, 6);
        Seller seller = new Seller("SELL-" + suf, "Alice", "password123");
        Bidder bidder = new Bidder("BID-" + suf, "Bob", "password123");
        Admin admin = new Admin("ADM-" + suf, "Admin", "password123");
        manager.registerUser(seller);
        manager.registerUser(bidder);
        manager.registerUser(admin);

        assertEquals(0.0, seller.getBalance(), 0.01);
        double initialBidderBalance = bidder.getBalance();

        AuctionClient sellerClient = new AuctionClient("localhost", port);
        AuctionClient adminClient = new AuctionClient("localhost", port);
        AuctionClient bidderClient = new AuctionClient("localhost", port);

        AtomicReference<String> createdAuctionId = new AtomicReference<>();
        CountDownLatch requestLatch = new CountDownLatch(1);
        CountDownLatch approveLatch = new CountDownLatch(1);
        CountDownLatch sellerErrorOnBidLatch = new CountDownLatch(1);
        CountDownLatch bidderErrorOnRequestLatch = new CountDownLatch(1);
        CountDownLatch bidSuccessLatch = new CountDownLatch(1);
        CountDownLatch closeSuccessLatch = new CountDownLatch(1);
        AtomicReference<UserProfile> sellerProfileAfterClose = new AtomicReference<>();
        CountDownLatch sellerProfileLatch = new CountDownLatch(1);

        sellerClient.addListener(new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String id, double p, String b, String item) {
            }

            @Override
            public void onMessageReceived(String raw) {
                if (raw.startsWith("SUCCESS REQUEST_AUCTION ")) {
                    createdAuctionId.set(raw.substring("SUCCESS REQUEST_AUCTION ".length()).trim());
                    requestLatch.countDown();
                } else if (raw.startsWith("ERROR BID Only Bidders")) {
                    sellerErrorOnBidLatch.countDown();
                }
            }

            @Override
            public void onProfileReceived(UserProfile profile) {
                if (profile.balance() > 0) {
                    sellerProfileAfterClose.set(profile);
                    sellerProfileLatch.countDown();
                }
            }
        });

        adminClient.addListener(new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String id, double p, String b, String item) {
            }

            @Override
            public void onMessageReceived(String raw) {
                if (raw.startsWith("SUCCESS APPROVE_AUCTION ")) {
                    approveLatch.countDown();
                } else if (raw.startsWith("SUCCESS CLOSE ")) {
                    closeSuccessLatch.countDown();
                }
            }
        });

        bidderClient.addListener(new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String id, double p, String b, String item) {
            }

            @Override
            public void onMessageReceived(String raw) {
                if (raw.startsWith("ERROR Only Sellers")) {
                    bidderErrorOnRequestLatch.countDown();
                } else if (raw.startsWith("SUCCESS BID ")) {
                    bidSuccessLatch.countDown();
                }
            }
        });

        try {
            sellerClient.connect();
            adminClient.connect();
            bidderClient.connect();

            sellerClient.login(seller.getId(), "password123");
            adminClient.login(admin.getId(), "password123");
            bidderClient.login(bidder.getId(), "password123");
            Thread.sleep(150);

            // 1. Bidder KHÔNG được phép gửi yêu cầu mở phòng bán
            bidderClient.requestAuction("Fake Item", "WATCHES", 10_000_000, 500_000, 300, "item_watch_classic", "desc");
            assertTrue(bidderErrorOnRequestLatch.await(3, TimeUnit.SECONDS), "Bidder must be blocked from creating auction requests");

            // 2. Seller gửi yêu cầu mở phòng -> PENDING_APPROVAL
            sellerClient.requestAuction("Rolex Vintage 1970", "WATCHES", 20_000_000, 1_000_000, 300, "item_watch_classic", "Authentic watch");
            assertTrue(requestLatch.await(3, TimeUnit.SECONDS), "Seller request must succeed");
            String aucId = createdAuctionId.get();
            assertNotNull(aucId);
            assertEquals(Auction.Status.PENDING_APPROVAL, manager.getAuction(aucId).getStatus());

            // 3. Seller KHÔNG thể tự duyệt phòng của mình
            assertThrows(AuthenticationException.class,
                    () -> manager.approveAuctionRequest(seller.getId(), aucId, null));

            // 4. Admin duyệt phòng -> RUNNING
            adminClient.approveAuction(aucId);
            assertTrue(approveLatch.await(3, TimeUnit.SECONDS), "Admin approval must succeed");
            assertEquals(Auction.Status.RUNNING, manager.getAuction(aucId).getStatus());

            // 5. Seller KHÔNG thể đặt bid
            sellerClient.placeBid(aucId, 21_000_000);
            assertTrue(sellerErrorOnBidLatch.await(3, TimeUnit.SECONDS), "Seller must be blocked from bidding");

            // 6. Bidder đặt giá hợp lệ
            bidderClient.placeBid(aucId, 25_000_000);
            assertTrue(bidSuccessLatch.await(3, TimeUnit.SECONDS), "Bidder bid must succeed");
            assertEquals(25_000_000.0, manager.getAuction(aucId).getCurrentPrice(), 0.01);

            // 7. Admin chốt phiên -> tiền tự động chảy về túi Seller (không bị cộng đôi nếu PAY sau đó)
            adminClient.closeAuction(aucId);
            assertTrue(closeSuccessLatch.await(3, TimeUnit.SECONDS));

            assertEquals(25_000_000.0, seller.getBalance(), 0.01, "Tiền đấu giá thành công phải chảy về ví Seller");
            assertEquals(initialBidderBalance - 25_000_000.0, bidder.getBalance(), 0.01, "Ví Bidder phải bị trừ đúng số tiền thắng");

            // 8. Seller đồng bộ PROFILE nhận đúng số dư mới
            sellerClient.requestProfile();
            assertTrue(sellerProfileLatch.await(3, TimeUnit.SECONDS));
            assertEquals(25_000_000.0, sellerProfileAfterClose.get().balance(), 0.01);

            // 9. Xác nhận PAY sau đó chuyển PAID nhưng không cộng tiền lần 2 cho Seller
            manager.markAuctionPaid(aucId);
            assertEquals(Auction.Status.PAID, manager.getAuction(aucId).getStatus());
            assertEquals(25_000_000.0, seller.getBalance(), 0.01, "Seller balance must not be double-credited on PAY");
            assertEquals(1, manager.getWalletTransactionsForUser(seller.getId()).size());
        } finally {
            sellerClient.disconnect();
            adminClient.disconnect();
            bidderClient.disconnect();
        }
    }

    @Test
    @DisplayName("Auto-Bidding PriorityQueue & Anti-Sniping extension hoạt động chính xác")
    void autoBiddingAndAntiSniping_shouldWorkCorrectly() {
        Seller seller = new Seller("S-AUTO", "Seller", "password123");
        Bidder b1 = new Bidder("B-AUTO-1", "Bidder One", "password123");
        Bidder b2 = new Bidder("B-AUTO-2", "Bidder Two", "password123");
        manager.registerUser(seller);
        manager.registerUser(b1);
        manager.registerUser(b2);

        Auction auction = new Auction("AUC-AUTO-1", "Leica M3", seller.getId(), 10_000_000, 500_000);
        auction.startAuction(Duration.ofSeconds(15));
        auction.setAntiSniping(true, 30, 30);
        manager.addAuction(auction);

        long originalEnd = auction.getEndTimeMillis();

        // B1 đặt Auto-Bid tối đa 15M, B2 đặt tối đa 18M -> B2 phải thắng ở mức 15.5M (không vượt maxBid)
        manager.configureAutoBid("AUC-AUTO-1", b1.getId(), "password123", 15_000_000, 500_000);
        manager.configureAutoBid("AUC-AUTO-1", b2.getId(), "password123", 18_000_000, 500_000);

        assertEquals(b2.getId(), auction.getHighestBidderId());
        assertTrue(auction.getCurrentPrice() >= 15_500_000 && auction.getCurrentPrice() <= 18_000_000);
        assertTrue(auction.getEndTimeMillis() > originalEnd, "Anti-sniping must extend endTimeMillis");
        assertTrue(auction.getAntiSnipingExtensionsCount() >= 1);

        // Admin có thể từ chối yêu cầu pending của Seller
        Admin admin = new Admin("ADM-REJ", "Admin", "password123");
        manager.registerUser(admin);
        Auction req = manager.submitAuctionRequest(seller.getId(), "Unverified Watch", "WATCHES", "No box", 5_000_000, 200_000, 300, "");
        manager.rejectAuctionRequest(admin.getId(), req.getId(), "Thiếu giấy kiểm định");
        assertEquals(Auction.Status.REJECTED, req.getStatus());
        assertEquals("Thiếu giấy kiểm định", req.getRejectionReason());
        assertTrue(List.of(req).get(0).getRejectionReason().contains("kiểm định"));
    }

    @Test
    @DisplayName("Live Room Chat & Reactions broadcast realtime tới mọi người đang trong phòng")
    void roomChatAndReactions_shouldBroadcastToSubscribedClients() throws Exception {
        Seller seller = new Seller("S-CHAT", "Tuấn Đại", "password123");
        Bidder bidder = new Bidder("B-CHAT", "Xuân Thắng", "password123");
        manager.registerUser(seller);
        manager.registerUser(bidder);

        Auction auction = new Auction("AUC-CHAT-1", "Leica M3", seller.getId(), 10_000_000, 500_000);
        auction.startAuction(Duration.ofMinutes(5));
        manager.addAuction(auction);

        AuctionClient c1 = new AuctionClient("localhost", port);
        AuctionClient c2 = new AuctionClient("localhost", port);
        CountDownLatch chatLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMsg = new AtomicReference<>();

        c2.addListener(new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String a, double p, String b, String i) {
            }

            @Override
            public void onChatMessageReceived(String auctionId, String senderId, String senderName, String role, String message) {
                if ("AUC-CHAT-1".equals(auctionId)) {
                    receivedMsg.set(senderName + ":" + message);
                    chatLatch.countDown();
                }
            }
        });

        try {
            c1.connect();
            c2.connect();
            c1.login(bidder.getId(), "password123");
            c2.login(seller.getId(), "password123");
            Thread.sleep(120);
            c1.joinAuction("AUC-CHAT-1");
            c2.joinAuction("AUC-CHAT-1");
            Thread.sleep(120);

            c1.sendRoomChat("AUC-CHAT-1", "Sản phẩm đẹp quá! 🔥😍");
            assertTrue(chatLatch.await(3, TimeUnit.SECONDS));
            assertEquals("Xuân Thắng:Sản phẩm đẹp quá! 🔥😍", receivedMsg.get());
        } finally {
            c1.disconnect();
            c2.disconnect();
        }

        // Người dùng khác đăng nhập sau khi Xuân Thắng đã đăng xuất vẫn phải thấy lịch sử tin nhắn + emoji!
        Bidder huyHung = new Bidder("B-CHAT-2", "Huy Hưng", "password123");
        manager.registerUser(huyHung);
        AuctionClient lateJoiner = new AuctionClient("localhost", port);
        CountDownLatch chatHistoryLatch = new CountDownLatch(1);
        AtomicReference<List<ChatMessageDTO>> loadedHistory = new AtomicReference<>();
        lateJoiner.addListener(new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String a, double p, String b, String i) {
            }

            @Override
            public void onChatHistoryReceived(String auctionId, List<ChatMessageDTO> messages) {
                if ("AUC-CHAT-1".equals(auctionId)) {
                    loadedHistory.set(messages);
                    chatHistoryLatch.countDown();
                }
            }
        });
        try {
            lateJoiner.connect();
            lateJoiner.login(huyHung.getId(), "password123");
            Thread.sleep(120);
            lateJoiner.requestChatHistory("AUC-CHAT-1");
            assertTrue(chatHistoryLatch.await(3, TimeUnit.SECONDS), "Late-joining user must receive saved chat history");
            assertNotNull(loadedHistory.get());
            assertEquals(1, loadedHistory.get().size());
            assertEquals("Xuân Thắng", loadedHistory.get().get(0).senderName());
            assertEquals("Sản phẩm đẹp quá! 🔥😍", loadedHistory.get().get(0).message());
        } finally {
            lateJoiner.disconnect();
        }
    }

    @Test
    @DisplayName("Nạp tiền (TOPUP) & Rút tiền (WITHDRAW) qua Socket: cập nhật số dư, ghi lịch sử và chặn rút vượt số dư")
    void walletDepositAndWithdraw_shouldUpdateBalanceRecordLedgerAndPreventOverdraft() throws Exception {
        Bidder bidder = new Bidder("B-WALLET", "Xuân Thắng", "password123", 100_000_000);
        Seller seller = new Seller("S-WALLET", "Tuấn Đại", "password123", 50_000_000);
        manager.registerUser(bidder);
        manager.registerUser(seller);

        AuctionClient bidderClient = new AuctionClient("localhost", port);
        AuctionClient sellerClient = new AuctionClient("localhost", port);

        CountDownLatch topUpLatch = new CountDownLatch(1);
        CountDownLatch withdrawLatch = new CountDownLatch(1);
        CountDownLatch overdraftErrorLatch = new CountDownLatch(1);
        CountDownLatch sellerWithdrawLatch = new CountDownLatch(1);
        CountDownLatch historyLatch = new CountDownLatch(1);
        AtomicReference<List<WalletTxDTO>> bidderHistory = new AtomicReference<>();

        bidderClient.addListener(new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String a, double p, String b, String i) {
            }

            @Override
            public void onMessageReceived(String raw) {
                if (raw.startsWith("SUCCESS TOPUP ")) {
                    topUpLatch.countDown();
                } else if (raw.startsWith("SUCCESS WITHDRAW ")) {
                    withdrawLatch.countDown();
                } else if (raw.startsWith("ERROR ") && raw.contains("không đủ")) {
                    overdraftErrorLatch.countDown();
                }
            }

            @Override
            public void onWalletHistoryReceived(List<WalletTxDTO> transactions) {
                if (transactions.size() >= 2) {
                    bidderHistory.set(transactions);
                    historyLatch.countDown();
                }
            }
        });

        sellerClient.addListener(new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String a, double p, String b, String i) {
            }

            @Override
            public void onMessageReceived(String raw) {
                if (raw.startsWith("SUCCESS WITHDRAW ")) {
                    sellerWithdrawLatch.countDown();
                }
            }
        });

        try {
            bidderClient.connect();
            sellerClient.connect();
            bidderClient.login(bidder.getId(), "password123");
            sellerClient.login(seller.getId(), "password123");
            Thread.sleep(150);

            // 1. Bidder nạp +50M qua Vietcombank -> 150M
            bidderClient.topUpWallet(50_000_000, "Vietcombank");
            assertTrue(topUpLatch.await(3, TimeUnit.SECONDS), "Bidder top-up must succeed");
            assertEquals(150_000_000.0, bidder.getBalance(), 0.01);

            // 2. Bidder rút 30M về Techcombank -> 120M
            bidderClient.withdrawWallet(30_000_000, "Techcombank ••9999 (XUÂN THẮNG)");
            assertTrue(withdrawLatch.await(3, TimeUnit.SECONDS), "Bidder withdraw must succeed");
            assertEquals(120_000_000.0, bidder.getBalance(), 0.01);

            // 3. Bidder cố rút 500M (> 120M) -> bị chặn & số dư KHÔNG bị trừ về 0
            bidderClient.withdrawWallet(500_000_000, "MB Bank ••1111");
            assertTrue(overdraftErrorLatch.await(3, TimeUnit.SECONDS), "Overdraft withdraw must be rejected");
            assertEquals(120_000_000.0, bidder.getBalance(), 0.01, "Balance must remain intact after failed withdraw");

            // 4. Seller rút 20M doanh thu về ngân hàng -> 30M
            sellerClient.withdrawWallet(20_000_000, "Vietcombank ••8888 (TUẤN ĐẠI)");
            assertTrue(sellerWithdrawLatch.await(3, TimeUnit.SECONDS), "Seller withdraw must succeed");
            assertEquals(30_000_000.0, seller.getBalance(), 0.01);

            // 5. Đồng bộ lịch sử giao dịch ví qua Socket phải có đủ TOP_UP & WITHDRAW
            bidderClient.requestWalletHistory();
            assertTrue(historyLatch.await(3, TimeUnit.SECONDS), "Wallet history must include deposit & withdraw");
            List<WalletTxDTO> txs = bidderHistory.get();
            assertTrue(txs.stream().anyMatch(t -> "TOP_UP".equals(t.type()) && t.amount() == 50_000_000.0));
            assertTrue(txs.stream().anyMatch(t -> "WITHDRAW".equals(t.type()) && t.amount() == -30_000_000.0));
        } finally {
            bidderClient.disconnect();
            sellerClient.disconnect();
        }
    }

    @Test
    @DisplayName("ClientSession tách biệt hoàn toàn thông báo & yêu thích khi đăng xuất chuyển tài khoản")
    void clientSession_shouldIsolateNotificationsAndFavoritesAcrossAccounts() {
        com.auction.session.ClientSession session = new com.auction.session.ClientSession("localhost", port);
        session.setCurrentUser(new UserProfile("BIDDER-01", "Xuân Thắng", "BIDDER", 500_000_000));
        session.addNotificationLog("Thành công: Chào mừng Xuân Thắng đến với BidX!");
        session.toggleFavorite("AUC-004");
        assertEquals(1, session.getRecentNotifications().size());
        assertTrue(session.isFavorite("AUC-004"));

        // Đăng xuất -> xóa sạch trạng thái phiên của tài khoản cũ
        session.disconnect();
        assertTrue(session.getRecentNotifications().isEmpty());
        assertFalse(session.isFavorite("AUC-004"));

        // Đăng nhập tài khoản khác -> chỉ thấy thông báo của Huy Hưng
        session.setCurrentUser(new UserProfile("BIDDER-02", "Huy Hưng", "BIDDER", 500_000_000));
        session.addNotificationLog("Thành công: Chào mừng Huy Hưng đến với BidX!");
        assertEquals(1, session.getRecentNotifications().size());
        assertTrue(session.getRecentNotifications().get(0).contains("Huy Hưng"));
        assertFalse(session.getRecentNotifications().get(0).contains("Xuân Thắng"));
    }
}
