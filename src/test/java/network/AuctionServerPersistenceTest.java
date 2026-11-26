package network;

import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionServerPersistenceTest {

    @TempDir
    Path tempDir;

    private File storageFile;
    private AuctionServer server1;
    private AuctionServer server2;
    private int port1;
    private int port2;

    @BeforeEach
    void setUp() throws IOException {
        storageFile = tempDir.resolve("server_restart_test.ser").toFile();
        AuctionManager.getInstance().clear();

        try (ServerSocket s1 = new ServerSocket(0)) {
            port1 = s1.getLocalPort();
        }
        try (ServerSocket s2 = new ServerSocket(0)) {
            port2 = s2.getLocalPort();
        }
    }

    @AfterEach
    void tearDown() {
        if (server1 != null) {
            server1.stop();
        }
        if (server2 != null) {
            server2.stop();
        }
        AuctionManager.getInstance().clear();
        AuctionManager.getInstance().setAutoSave(false);
    }

    @Test
    void testServerRestartPreservesUsersAndBids() throws Exception {
        // 1. Khởi động Server lần 1 với file lưu trữ
        AuctionServer.initData(storageFile.getAbsolutePath());
        server1 = new AuctionServer(port1);
        server1.startAsync();
        Thread.sleep(150);

        // 2. Client kết nối, đăng ký tài khoản mới và đặt giá
        AuctionClient client1 = new AuctionClient("localhost", port1);
        CountDownLatch client1Latch = new CountDownLatch(3); // register, join, bid
        client1.addListener(new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
                client1Latch.countDown();
            }

            @Override
            public void onMessageReceived(String rawMessage) {
                if (rawMessage.startsWith(AuctionProtocol.RES_SUCCESS)) {
                    client1Latch.countDown();
                }
            }
        });

        client1.connect();
        Thread.sleep(100);

        client1.register("RESTART-BIDDER", "RestartUser", "password123", "BIDDER");
        client1.joinAuction("AUC-001");
        client1.placeBid("AUC-001", 11_000_000);

        assertTrue(client1Latch.await(3, TimeUnit.SECONDS), "Client 1 phải nhận phản hồi thành công từ server 1");

        client1.disconnect();
        Thread.sleep(100);

        // 3. Tắt Server lần 1 (lưu toàn bộ dữ liệu ra disk)
        server1.stop();
        assertTrue(storageFile.exists(), "File lưu trữ phải tồn tại sau khi tắt server 1");

        // 4. Giả lập Server Crash / Restart: Xóa sạch RAM
        AuctionManager.getInstance().clear();
        assertNull(AuctionManager.getInstance().getUser("RESTART-BIDDER"));

        // 5. Khởi động Server lần 2 (nạp lại dữ liệu từ file storageFile)
        AuctionServer.initData(storageFile.getAbsolutePath());
        assertNotNull(AuctionManager.getInstance().getUser("RESTART-BIDDER"),
                "Tài khoản RESTART-BIDDER phải được phục hồi từ file");

        Auction restoredAuction = AuctionManager.getInstance().getAuction("AUC-001");
        assertNotNull(restoredAuction);
        assertEquals(11_000_000.0, restoredAuction.getCurrentPrice(),
                "Giá cao nhất phải được bảo toàn sau khi server restart");
        assertEquals("RESTART-BIDDER", restoredAuction.getHighestBidderId(),
                "Người thắng tạm thời phải được bảo toàn sau khi server restart");

        server2 = new AuctionServer(port2);
        server2.startAsync();
        Thread.sleep(150);

        // 6. Client kết nối tới Server lần 2: Đăng nhập trực tiếp không cần đăng ký
        // lại!
        AuctionClient client2 = new AuctionClient("localhost", port2);
        CountDownLatch client2Latch = new CountDownLatch(2); // login, bid
        client2.addListener(new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
                client2Latch.countDown();
            }

            @Override
            public void onMessageReceived(String rawMessage) {
                if (rawMessage.startsWith(AuctionProtocol.RES_SUCCESS)) {
                    client2Latch.countDown();
                }
            }
        });

        client2.connect();
        Thread.sleep(100);

        client2.login("RESTART-BIDDER", "password123");
        client2.placeBid("AUC-001", 12_000_000);

        assertTrue(client2Latch.await(3, TimeUnit.SECONDS),
                "Client 2 phải đăng nhập và đặt giá thành công trên server 2");

        client2.disconnect();
    }
}