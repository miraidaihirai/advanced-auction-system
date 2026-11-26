
package network;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
//Gỉa lập server qua IO và socket
import java.nio.charset.StandardCharsets;
//Đảm bảo đọc ghi Tiếng Việt
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
// Bộ công cụ cho Multithreading
// AtomicReference cho phép lưu giữ cập nhạt một reference an toàn khi multithreading. 
// Trong bài test có MainThread thực hiện JUnit Test và ListenerThread nhận thông báo từ AuctionClient
import static org.junit.jupiter.api.Assertions.*;

class AuctionClientTest {

        @Test
        void sendRaw_whenNotConnected_shouldThrowException() {
                AuctionClient client = new AuctionClient("localhost", 8888);

                assertThrows(
                                IllegalStateException.class,
                                () -> client.sendRaw("LIST"));
        }

        // Gửi message khi chưa kết nối
        @Test
        void commandMethods_shouldSendCorrectMessages() throws Exception {
                try (ServerSocket serverSocket = new ServerSocket(0)) {
                        // Khởi tạo server socket ngẫu nhiên bằng try-with-resources đảm bảo chúng được
                        // đóng sau test
                        BlockingQueue<String> receivedCommands = new LinkedBlockingQueue<>();
                        // Hàng đợi thread-safe chờ các command server nhận được cho main thread kiểm
                        // tra
                        CountDownLatch receivedLatch = new CountDownLatch(7);

                        Thread serverThread = new Thread(() -> {
                                try (Socket socket = serverSocket.accept();
                                                BufferedReader reader = new BufferedReader(
                                                                new InputStreamReader(
                                                                                socket.getInputStream(),
                                                                                StandardCharsets.UTF_8))) {

                                        for (int i = 0; i < 7; i++) {
                                                String command = reader.readLine();

                                                if (command == null) {
                                                        break;
                                                }
                                                // readLine() trả về null khi đóng kết nối, thoát an toàn
                                                receivedCommands.add(command);
                                                receivedLatch.countDown();
                                                // Nhận đủ command bộ đếm giảm về 0
                                        }

                                } catch (IOException ignored) {
                                }
                        });

                        serverThread.setDaemon(true);
                        // Thread tự chết theo JVM nếu test kết thúc sớm, tránh treo process
                        serverThread.start();

                        AuctionClient client = new AuctionClient(
                                        "127.0.0.1",
                                        serverSocket.getLocalPort());
                        // Trỏ client đến cổng thực của server vừa tạo (getLocalPort())
                        try {
                                client.connect();

                                assertTrue(client.isConnected());

                                client.login("BIDDER-01", "password123");
                                client.register(
                                                "BIDDER-02",
                                                "Bob",
                                                "password123",
                                                "BIDDER");
                                client.joinAuction("AUC-001");
                                client.leaveAuction("AUC-001");
                                client.placeBid("AUC-001", 123.45);
                                client.requestList();
                                client.requestInfo("AUC-001");
                                // 7 method nghiệp vụ
                                assertTrue(
                                                receivedLatch.await(3, TimeUnit.SECONDS),
                                                "Server should receive all commands");
                                // Chờ tối đa 3s để server nhận đủ 7 lệnh, nếu timeout thì test fail và in thông
                                // báo

                                assertEquals(
                                                "LOGIN BIDDER-01 password123",
                                                receivedCommands.poll(1, TimeUnit.SECONDS));
                                // poll(1, SECONDS) lất phần tử đầu hàng đợi, chờ tối đa 1s
                                assertEquals(
                                                "REGISTER BIDDER-02 Bob password123 BIDDER",
                                                receivedCommands.poll(1, TimeUnit.SECONDS));

                                assertEquals(
                                                "JOIN AUC-001",
                                                receivedCommands.poll(1, TimeUnit.SECONDS));

                                assertEquals(
                                                "LEAVE AUC-001",
                                                receivedCommands.poll(1, TimeUnit.SECONDS));

                                assertEquals(
                                                "BID AUC-001 123.45",
                                                receivedCommands.poll(1, TimeUnit.SECONDS));

                                assertEquals(
                                                "LIST",
                                                receivedCommands.poll(1, TimeUnit.SECONDS));

                                assertEquals(
                                                "INFO AUC-001",
                                                receivedCommands.poll(1, TimeUnit.SECONDS));

                        } finally {
                                client.disconnect();
                                serverSocket.close();
                                serverThread.join(1000);
                                // Đóng client, server, chờ thread server kết thúc tối đa 1s
                        }

                        assertFalse(client.isConnected());
                }
        }

        @Test
        void shouldNotifyListenerWhenBidUpdateReceived() throws Exception {
                try (ServerSocket serverSocket = new ServerSocket(0)) {

                        CountDownLatch bidLatch = new CountDownLatch(1);

                        AtomicReference<String> receivedAuctionId = new AtomicReference<>();

                        AtomicReference<Double> receivedPrice = new AtomicReference<>();

                        AtomicReference<String> receivedBidder = new AtomicReference<>();

                        AtomicReference<String> receivedRawMessage = new AtomicReference<>();
                        // Hộp chứa an toàn để listener thread ghi, main thread đọc, không cần
                        // volatile/synchronize
                        AuctionClient client = new AuctionClient(
                                        "127.0.0.1",
                                        serverSocket.getLocalPort());

                        client.addListener(new AuctionClient.AuctionEventListener() {

                                @Override
                                public void onBidUpdate(
                                                String auctionId,
                                                double newPrice,
                                                String bidderId,
                                                String itemId) {
                                        receivedAuctionId.set(auctionId);
                                        receivedPrice.set(newPrice);
                                        receivedBidder.set(bidderId);

                                        bidLatch.countDown();
                                }

                                @Override
                                public void onMessageReceived(String message) {
                                        receivedRawMessage.set(message);
                                }
                                // Ẩn danh class implement AuctionEventListener
                        });

                        Thread serverThread = startMessageServer(
                                        serverSocket,
                                        "NOTIFY BID AUC-001 15000000.00 BIDDER-01 ITEM-01");

                        try {
                                client.connect();

                                assertTrue(
                                                bidLatch.await(3, TimeUnit.SECONDS),
                                                "Listener should receive bid update");
                                // Chờ listener được gọi (tối đa 3s)
                                assertEquals("AUC-001", receivedAuctionId.get());
                                assertEquals(15000000.0, receivedPrice.get(), 0.01);
                                // Tham số delta tránh lỗi dấu phẩy động
                                assertEquals("BIDDER-01", receivedBidder.get());

                                assertEquals(
                                                "NOTIFY BID AUC-001 15000000.00 BIDDER-01 ITEM-01",
                                                receivedRawMessage.get());

                        } finally {
                                client.disconnect();
                                serverSocket.close();
                                serverThread.join(1000);
                        }
                }
        }

        @Test
        void shouldNotifyListenerWhenAuctionClosed() throws Exception {
                try (ServerSocket serverSocket = new ServerSocket(0)) {

                        CountDownLatch closedLatch = new CountDownLatch(1);

                        AtomicReference<String> receivedAuctionId = new AtomicReference<>();

                        AtomicReference<Double> receivedFinalPrice = new AtomicReference<>();

                        AtomicReference<String> receivedWinner = new AtomicReference<>();

                        AuctionClient client = new AuctionClient(
                                        "127.0.0.1",
                                        serverSocket.getLocalPort());

                        client.addListener(new AuctionClient.AuctionEventListener() {

                                @Override
                                public void onBidUpdate(
                                                String auctionId,
                                                double newPrice,
                                                String bidderId,
                                                String itemId) {
                                }

                                @Override
                                public void onAuctionClosed(
                                                String auctionId,
                                                double finalPrice,
                                                String winnerId) {
                                        receivedAuctionId.set(auctionId);
                                        receivedFinalPrice.set(finalPrice);
                                        receivedWinner.set(winnerId);

                                        closedLatch.countDown();
                                }
                                // Chỉ test sự kiện đóng auction
                        });

                        Thread serverThread = startMessageServer(
                                        serverSocket,
                                        "NOTIFY CLOSED AUC-001 15500000.00 BIDDER-02");

                        try {
                                client.connect();

                                assertTrue(
                                                closedLatch.await(3, TimeUnit.SECONDS),
                                                "Listener should receive auction closed notification");

                                assertEquals("AUC-001", receivedAuctionId.get());
                                assertEquals(15500000.0, receivedFinalPrice.get(), 0.01);
                                assertEquals("BIDDER-02", receivedWinner.get());

                        } finally {
                                client.disconnect();
                                serverSocket.close();
                                serverThread.join(1000);
                        }
                }
        }

        private Thread startMessageServer(
                        ServerSocket serverSocket,
                        String message) {
                Thread serverThread = new Thread(() -> {
                        try (Socket socket = serverSocket.accept();
                                        PrintWriter writer = new PrintWriter(
                                                        socket.getOutputStream(),
                                                        true,
                                                        StandardCharsets.UTF_8
                        // Luôn true nên auto-flush sau mỗi println, đảm bảo mesage được gửi đi ngay
                        )) {

                                writer.println(message);

                        } catch (IOException ignored) {
                        }
                });

                serverThread.setDaemon(true);
                serverThread.start();
                // Thực sự chạy thread

                return serverThread;
                // Để thread có thể join() chờ kết thúc
        }

        @Test
        void addListener_shouldIgnoreDuplicateAndNullListeners() throws Exception {
                try (ServerSocket serverSocket = new ServerSocket(0)) {

                        CountDownLatch messageLatch = new CountDownLatch(1);
                        CountDownLatch duplicateLatch = new CountDownLatch(2);

                        AuctionClient client = new AuctionClient(
                                        "127.0.0.1",
                                        serverSocket.getLocalPort());

                        AuctionClient.AuctionEventListener listener = new AuctionClient.AuctionEventListener() {

                                @Override
                                public void onBidUpdate(
                                                String auctionId,
                                                double newPrice,
                                                String bidderId,
                                                String itemId) {
                                }

                                @Override
                                public void onMessageReceived(String message) {
                                        messageLatch.countDown();
                                        duplicateLatch.countDown();
                                }
                        };

                        client.addListener(null);
                        client.addListener(listener);
                        client.addListener(listener);

                        Thread serverThread = startMessageServer(
                                        serverSocket,
                                        "TEST MESSAGE");

                        try {
                                client.connect();

                                assertTrue(
                                                messageLatch.await(3, TimeUnit.SECONDS));

                                assertFalse(
                                                duplicateLatch.await(300, TimeUnit.MILLISECONDS),
                                                "The same listener should not be registered twice");

                        } finally {
                                client.disconnect();
                                serverSocket.close();
                                serverThread.join(1000);
                        }
                }
        }

        @Test
        void removeListener_shouldStopNotifications() throws Exception {
                try (ServerSocket serverSocket = new ServerSocket(0)) {

                        AtomicInteger removedCallbackCount = new AtomicInteger(0);
                        CountDownLatch activeListenerLatch = new CountDownLatch(1);

                        AuctionClient client = new AuctionClient(
                                        "127.0.0.1",
                                        serverSocket.getLocalPort());

                        AuctionClient.AuctionEventListener removedListener = new AuctionClient.AuctionEventListener() {

                                @Override
                                public void onBidUpdate(
                                                String auctionId,
                                                double newPrice,
                                                String bidderId,
                                                String itemId) {
                                }

                                @Override
                                public void onMessageReceived(String message) {
                                        removedCallbackCount.incrementAndGet();
                                }
                        };

                        AuctionClient.AuctionEventListener activeListener = new AuctionClient.AuctionEventListener() {

                                @Override
                                public void onBidUpdate(
                                                String auctionId,
                                                double newPrice,
                                                String bidderId,
                                                String itemId) {
                                }

                                @Override
                                public void onMessageReceived(String message) {
                                        activeListenerLatch.countDown();
                                }
                        };

                        client.addListener(removedListener);
                        client.removeListener(removedListener);
                        client.addListener(activeListener);

                        Thread serverThread = startMessageServer(
                                        serverSocket,
                                        "TEST MESSAGE");

                        try {
                                client.connect();

                                assertTrue(
                                                activeListenerLatch.await(3, TimeUnit.SECONDS));

                                assertEquals(
                                                0,
                                                removedCallbackCount.get(),
                                                "Removed listener should not receive messages");

                        } finally {
                                client.disconnect();
                                serverSocket.close();
                                serverThread.join(1000);
                        }
                }
        }

        @Test
        void disconnect_whenNotConnected_shouldRemainDisconnected() {
                AuctionClient client = new AuctionClient("localhost", 8888);
                assertDoesNotThrow(client::disconnect);
                assertFalse(client.isConnected());
                assertDoesNotThrow(client::disconnect);
        }

        @Test
        void remoteDisconnect_shouldUpdateConnectionState() throws Exception {
                try (ServerSocket serverSocket = new ServerSocket(0)) {

                        Thread serverThread = new Thread(() -> {
                                try (Socket socket = serverSocket.accept()) {
                                        // Server closes the connection after accepting
                                } catch (IOException ignored) {
                                }
                        });

                        serverThread.setDaemon(true);
                        serverThread.start();

                        AuctionClient client = new AuctionClient(
                                        "127.0.0.1",
                                        serverSocket.getLocalPort());

                        client.connect();

                        try {
                                assertTrue(client.isConnected());

                                long deadline = System.nanoTime()
                                                + TimeUnit.SECONDS.toNanos(3);

                                while (client.isConnected()
                                                && System.nanoTime() < deadline) {

                                        Thread.sleep(10);
                                }

                                assertFalse(
                                                client.isConnected(),
                                                "Client should detect remote disconnection");

                        } finally {
                                client.disconnect();
                                serverSocket.close();
                                serverThread.join(1000);
                        }
                }
        }

        @Test
        void listenerException_shouldNotAffectOtherListeners() throws Exception {
                try (ServerSocket serverSocket = new ServerSocket(0)) {

                        CountDownLatch goodListenerLatch = new CountDownLatch(1);

                        AuctionClient client = new AuctionClient(
                                        "127.0.0.1",
                                        serverSocket.getLocalPort());

                        AuctionClient.AuctionEventListener badListener = new AuctionClient.AuctionEventListener() {

                                @Override
                                public void onBidUpdate(
                                                String auctionId,
                                                double newPrice,
                                                String bidderId,
                                                String itemId) {
                                }

                                @Override
                                public void onMessageReceived(String message) {
                                        throw new IllegalStateException("Test exception");
                                }
                        };

                        AuctionClient.AuctionEventListener goodListener = new AuctionClient.AuctionEventListener() {

                                @Override
                                public void onBidUpdate(
                                                String auctionId,
                                                double newPrice,
                                                String bidderId,
                                                String itemId) {
                                }

                                @Override
                                public void onMessageReceived(String message) {
                                        goodListenerLatch.countDown();
                                }
                        };

                        client.addListener(badListener);
                        client.addListener(goodListener);

                        Thread serverThread = startMessageServer(
                                        serverSocket,
                                        "TEST MESSAGE");

                        try {
                                client.connect();

                                assertTrue(
                                                goodListenerLatch.await(3, TimeUnit.SECONDS),
                                                "Exception in one listener should not affect other listeners");

                        } finally {
                                client.disconnect();
                                serverSocket.close();
                                serverThread.join(1000);
                        }
                }
        }
}
