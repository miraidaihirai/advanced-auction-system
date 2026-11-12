
package network;

import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
// Start auction với thời lượng 10p
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientHandlerTest {

        private static final AuctionManager manager = AuctionManager.getInstance();

        @Test
        void connection_shouldSendWelcomeMessageAndHandleUnknownCommand()
                        throws Exception {
                // Connect + lệnh không xác định
                try (TestConnection connection = openConnection()) {

                        assertEquals(
                                        "SUCCESS CONNECTED Welcome to Online Auction Server!",
                                        connection.welcomeMessage);

                        assertEquals(
                                        "ERROR Unknown command: UNKNOWN",
                                        connection.sendCommand("UNKNOWN"));
                }
        }

        @Test
        void login_shouldAcceptValidCredentialsAndRejectInvalidCredentials()
                        throws Exception {
                // Test login đúng sai
                Bidder bidder = registerBidder();
                // Tạo sẵn Bidder làm dữ liệu mồi để test login
                try (TestConnection connection = openConnection()) {

                        assertEquals(
                                        "ERROR Invalid user credentials",
                                        connection.sendCommand(
                                                        "LOGIN " + bidder.getId() + " wrongPassword"));

                        assertEquals(
                                        "SUCCESS LOGIN " + bidder.getId() + " " + bidder.getName(),
                                        connection.sendCommand(
                                                        "LOGIN " + bidder.getId() + " password123"));
                }
        }

        @Test
        void login_withoutRequiredArguments_shouldReturnError()
                        throws Exception {
                // Login thiếu argument
                try (TestConnection connection = openConnection()) {

                        assertEquals(
                                        "ERROR Usage: LOGIN <userId> <password>",
                                        connection.sendCommand("LOGIN"));
                }
        }

        @Test
        void register_shouldCreateBidderAndSeller() throws Exception {
                // Register cả 2 role
                String bidderId = uniqueId("BIDDER");
                String sellerId = uniqueId("SELLER");
                // Sinh ID ngẫu nhiên tránh trùng lặp
                try (TestConnection connection = openConnection()) {

                        assertEquals(
                                        "SUCCESS REGISTER " + bidderId,
                                        connection.sendCommand(
                                                        "REGISTER " + bidderId + " Bob password123 BIDDER"));
                        // Kiểm tra User thực sự được tạo và đúng loại
                        assertTrue(manager.getUser(bidderId) instanceof Bidder);

                        assertEquals(
                                        "SUCCESS REGISTER " + sellerId,
                                        connection.sendCommand(
                                                        "REGISTER " + sellerId + " Alice password123 SELLER"));

                        assertTrue(manager.getUser(sellerId) instanceof Seller);
                }
        }

        @Test
        void register_duplicateUserId_shouldReturnError() throws Exception {
                // Test register trùng ID
                String userId = uniqueId("BIDDER");

                manager.registerUser(
                                new Bidder(userId, "Bob", "password123"));

                try (TestConnection connection = openConnection()) {

                        assertEquals(
                                        "ERROR User ID is already registered: " + userId,
                                        connection.sendCommand(
                                                        "REGISTER " + userId + " Bob password123 BIDDER"));
                }
        }

        @Test
        void joinAndLeave_shouldWorkForExistingAuction() throws Exception {
                // Test join và leave
                Auction auction = createAuction();

                String expectedJoin = String.format(
                                Locale.US,
                                "SUCCESS JOIN %s %s %.2f %.2f %s",
                                auction.getId(),
                                auction.getItemId(),
                                auction.getCurrentPrice(),
                                auction.getStepPrice(),
                                auction.getStatus());
                // Ghép chuỗi kỳ vọng bằng chính dữ liệu của auction
                try (TestConnection connection = openConnection()) {

                        assertEquals(
                                        expectedJoin,
                                        connection.sendCommand(
                                                        "JOIN " + auction.getId()));

                        assertEquals(
                                        "SUCCESS LEAVE " + auction.getId(),
                                        connection.sendCommand(
                                                        "LEAVE " + auction.getId()));
                }
        }

        @Test
        void joinUnknownAuctionAndLeaveWithoutJoining_shouldReturnErrors()
                        throws Exception {
                // join auction không tồn tại, leave khi chưa join
                String auctionId = uniqueId("AUC");
                // ID auction chắc chắn không tồn tại
                try (TestConnection connection = openConnection()) {

                        assertEquals(
                                        "ERROR Auction not found: " + auctionId,
                                        connection.sendCommand("JOIN " + auctionId));

                        assertEquals(
                                        "ERROR Not joined in auction: " + auctionId,
                                        connection.sendCommand("LEAVE " + auctionId));
                }
        }

        // Look up thành công nhưng auction client chưa join và look up thất bại
        @Test
        void listAndInfo_shouldReturnAuctionInformation() throws Exception {

                Auction auction = createAuction();

                String expectedInfo = String.format(
                                Locale.US,
                                "AUCTION_INFO %s %s %s %.2f %.2f NONE %s",
                                auction.getId(),
                                auction.getItemId(),
                                auction.getSellerId(),
                                auction.getCurrentPrice(),
                                auction.getStepPrice(),
                                auction.getStatus());

                String expectedListItem = String.format(
                                Locale.US,
                                "AUCTION %s %s %.2f %.2f NONE %s",
                                auction.getId(),
                                auction.getItemId(),
                                auction.getCurrentPrice(),
                                auction.getStepPrice(),
                                auction.getStatus());

                try (TestConnection connection = openConnection()) {

                        assertEquals(
                                        expectedInfo,
                                        connection.sendCommand(
                                                        "INFO " + auction.getId()));

                        List<String> response = connection.requestAuctionList();

                        assertEquals(
                                        AuctionProtocol.AUCTIONS_BEGIN,
                                        response.get(0));

                        assertEquals(
                                        AuctionProtocol.AUCTIONS_END,
                                        response.get(response.size() - 1));

                        assertTrue(response.contains(expectedListItem));
                }
        }

        @Test
        void bid_shouldRequireLoginAndBidderRole() throws Exception {
                // BID cần login + đúng role
                Seller seller = registerSeller();

                try (TestConnection connection = openConnection()) {

                        assertEquals(
                                        "ERROR You must LOGIN first to place a bid",
                                        connection.sendCommand(
                                                        "BID " + uniqueId("AUC") + " 21000000"));
                        // Chưa login, báo lỗi auth trước, chưa cần biết auction có tồn tại hay không
                        assertEquals(
                                        "SUCCESS LOGIN " + seller.getId() + " " + seller.getName(),
                                        connection.sendCommand(
                                                        "LOGIN " + seller.getId() + " password123"));
                        assertEquals(
                                        "ERROR Only Bidders can place bids",
                                        connection.sendCommand(
                                                        "BID " + uniqueId("AUC") + " 21000000"));
                        // Login sai role
                }
        }

        @Test
        void bid_withInvalidAmount_shouldReturnError() throws Exception {
                // BID với invalid amount
                Bidder bidder = registerBidder();

                try (TestConnection connection = openConnection()) {

                        connection.sendCommand(
                                        "LOGIN " + bidder.getId() + " password123");

                        assertEquals(
                                        "ERROR Invalid bid amount: invalid",
                                        connection.sendCommand(
                                                        "BID " + uniqueId("AUC") + " invalid"));
                }
        }

        @Test
        void bid_withValidAmount_shouldUpdateAuctionPrice() throws Exception {

                Bidder bidder = registerBidder();
                Auction auction = createAuction();

                auction.startAuction(Duration.ofMinutes(10));
                // Start auction với 10p để chuyển sang RUNNING
                try (TestConnection connection = openConnection()) {

                        assertEquals(
                                        "SUCCESS LOGIN " + bidder.getId() + " " + bidder.getName(),
                                        connection.sendCommand(
                                                        "LOGIN " + bidder.getId() + " password123"));

                        assertEquals(
                                        String.format(
                                                        Locale.US,
                                                        "SUCCESS BID %s %.2f",
                                                        auction.getId(),
                                                        21_000_000.0),
                                        connection.sendCommand(
                                                        "BID " + auction.getId() + " 21000000"));

                        assertEquals(21_000_000.0, auction.getCurrentPrice(), 0.01);

                        assertEquals(
                                        bidder.getId(),
                                        auction.getHighestBidderId());

                        assertEquals(1, auction.getBidHistory().size());
                        // Đúng 1 lượt bid được ghi lại
                } finally {
                        finishIfRunning(auction);
                }
        }

        @Test
        void start_shouldStartAnOpenAuction() throws Exception {

                Auction auction = createAuction();

                try (TestConnection connection = openConnection()) {

                        assertEquals(
                                        "SUCCESS START " + auction.getId(),
                                        connection.sendCommand(
                                                        "START " + auction.getId()));

                        assertEquals(
                                        Auction.Status.RUNNING,
                                        auction.getStatus());

                } finally {
                        finishIfRunning(auction);
                }
        }

        @Test
        void quit_shouldReturnGoodbyeMessage() throws Exception {

                try (TestConnection connection = openConnection()) {

                        assertEquals(
                                        "SUCCESS BYE Goodbye!",
                                        connection.sendCommand("QUIT"));
                }
        }

        @Test
        void ping_shouldReturnPong() throws Exception {
                try (TestConnection connection = openConnection()) {
                        assertEquals("PONG", connection.sendCommand("PING"));
                }
        }

        @Test
        void heartbeatTimeout_shouldCloseClientAndTerminateThread() throws Exception {
                // Khởi tạo connection với heartbeat timeout cực ngắn (250ms)
                TestConnection connection = openConnection(250);
                // Client không gửi bất kỳ bản tin nào; đợi 400ms để server kích hoạt timeout
                Thread.sleep(400);

                // Luồng handler phải tự động kết thúc và giải phóng tài nguyên
                connection.handlerThread.join(1000);
                assertFalse(connection.handlerThread.isAlive(),
                                "Handler thread phai tu giai phong sau heartbeat timeout");
                assertFalse(connection.handler.isRunning(), "Handler khong duoc o trang thai running sau timeout");
                connection.close();
        }

        @Test
        void suddenDisconnect_shouldCleanUpSubscriptionsAndActiveClients() throws Exception {
                Auction auction = createAuction();
                TestConnection connection = openConnection();

                // Join phòng đấu giá
                connection.sendCommand("JOIN " + auction.getId());
                assertTrue(connection.handler.isSubscribed(auction.getId()));

                // Đột ngột ngắt kết nối phía client (không gửi QUIT)
                connection.clientSocket.close();

                // Luồng handler phải phát hiện EOF/connection closed và dọn dẹp
                connection.handlerThread.join(1000);
                assertFalse(connection.handlerThread.isAlive(),
                                "Handler thread phai ket thuc khi client disconnect dot ngot");
                assertFalse(connection.handler.isSubscribed(auction.getId()),
                                "Subscription phai duoc don dep hoan toan");
                connection.close();
        }

        @Test
        void auctionFinish_shouldDeliverTargetedWinnerAndSellerNotifications() throws Exception {
                Seller seller = registerSeller();
                Bidder winner = registerBidder();
                Bidder spectator = registerBidder();

                Auction auction = new Auction(uniqueId("AUC"), "MacBook M3 Pro", seller.getId(), 20_000_000, 1_000_000);
                manager.addAuction(auction);

                try (TestConnection sellerConn = openConnection();
                                TestConnection winnerConn = openConnection();
                                TestConnection spectatorConn = openConnection()) {

                        sellerConn.sendCommand("LOGIN " + seller.getId() + " password123");
                        sellerConn.sendCommand("JOIN " + auction.getId());

                        winnerConn.sendCommand("LOGIN " + winner.getId() + " password123");
                        winnerConn.sendCommand("JOIN " + auction.getId());

                        spectatorConn.sendCommand("LOGIN " + spectator.getId() + " password123");
                        spectatorConn.sendCommand("JOIN " + auction.getId());

                        // Start auction & place bid
                        sellerConn.sendCommand("START " + auction.getId());
                        winnerConn.writer.println("BID " + auction.getId() + " 21000000");

                        // Winner nhận được 2 dòng: NOTIFY BID và SUCCESS BID
                        String wLine1 = winnerConn.reader.readLine();
                        String wLine2 = winnerConn.reader.readLine();
                        assertTrue(wLine1.startsWith("NOTIFY BID") || wLine2.startsWith("NOTIFY BID"));
                        assertTrue(wLine1.startsWith("SUCCESS BID") || wLine2.startsWith("SUCCESS BID"));

                        // Seller và Spectator nhận NOTIFY BID
                        String bidNotifySeller = sellerConn.reader.readLine();
                        assertTrue(bidNotifySeller.startsWith("NOTIFY BID"));
                        String bidNotifySpectator = spectatorConn.reader.readLine();
                        assertTrue(bidNotifySpectator.startsWith("NOTIFY BID"));

                        // Kết thúc phiên đấu giá
                        auction.finishAuction();
                        assertEquals(Auction.Status.FINISHED, auction.getStatus());
                        assertEquals(winner.getId(), auction.getHighestBidderId());

                        // Winner nhận thông báo riêng NOTIFY WINNER và thông báo phòng NOTIFY CLOSED
                        String winnerFirst = winnerConn.reader.readLine();
                        assertEquals("NOTIFY WINNER " + auction.getId() + " 21000000.00 " + auction.getItemId(),
                                        winnerFirst);
                        String winnerSecond = winnerConn.reader.readLine();
                        assertEquals("NOTIFY CLOSED " + auction.getId() + " 21000000.00 " + winner.getId(),
                                        winnerSecond);

                        // Seller nhận thông báo riêng NOTIFY SELLER và thông báo phòng NOTIFY CLOSED
                        String sellerFirst = sellerConn.reader.readLine();
                        assertEquals("NOTIFY SELLER " + auction.getId() + " 21000000.00 " + winner.getId() + " "
                                        + auction.getItemId(), sellerFirst);
                        String sellerSecond = sellerConn.reader.readLine();
                        assertEquals("NOTIFY CLOSED " + auction.getId() + " 21000000.00 " + winner.getId(),
                                        sellerSecond);

                        // Spectator chỉ nhận thông báo phòng NOTIFY CLOSED
                        String spectatorMsg = spectatorConn.reader.readLine();
                        assertEquals("NOTIFY CLOSED " + auction.getId() + " 21000000.00 " + winner.getId(),
                                        spectatorMsg);
                }
        }

        @Test
        void auctionPay_shouldTransitionStatusToPaidAndNotify() throws Exception {
                Seller seller = registerSeller();
                Bidder winner = registerBidder();

                Auction auction = new Auction(uniqueId("AUC"), "Rolex Submariner", seller.getId(), 50_000_000,
                                2_000_000);
                manager.addAuction(auction);

                try (TestConnection sellerConn = openConnection();
                                TestConnection winnerConn = openConnection()) {

                        sellerConn.sendCommand("LOGIN " + seller.getId() + " password123");
                        sellerConn.sendCommand("JOIN " + auction.getId());

                        winnerConn.sendCommand("LOGIN " + winner.getId() + " password123");
                        winnerConn.sendCommand("JOIN " + auction.getId());

                        sellerConn.sendCommand("START " + auction.getId());
                        winnerConn.writer.println("BID " + auction.getId() + " 52000000");

                        // Xả lượt bid realtime của winner và seller
                        winnerConn.reader.readLine();
                        winnerConn.reader.readLine();
                        sellerConn.reader.readLine();

                        // Kết thúc phiên
                        auction.finishAuction();
                        assertEquals(Auction.Status.FINISHED, auction.getStatus());

                        // Xả các thông báo close
                        winnerConn.reader.readLine(); // NOTIFY WINNER
                        winnerConn.reader.readLine(); // NOTIFY CLOSED
                        sellerConn.reader.readLine(); // NOTIFY SELLER
                        sellerConn.reader.readLine(); // NOTIFY CLOSED

                        // Winner gửi lệnh PAY
                        String payRes = winnerConn.sendCommand("PAY " + auction.getId());
                        assertEquals("SUCCESS PAY " + auction.getId() + " 52000000.00", payRes);
                        assertEquals(Auction.Status.PAID, auction.getStatus());

                        // Cả Winner và Seller đều nhận được bản tin NOTIFY PAID
                        String paidWinnerNotify = winnerConn.reader.readLine();
                        assertEquals("NOTIFY PAID " + auction.getId() + " 52000000.00 " + winner.getId(),
                                        paidWinnerNotify);
                        String paidSellerNotify = sellerConn.reader.readLine();
                        assertEquals("NOTIFY PAID " + auction.getId() + " 52000000.00 " + winner.getId(),
                                        paidSellerNotify);
                }
        }

        @Test
        void auctionFinish_noBids_sellerNotifiedNoneAndCannotPay() throws Exception {
                Seller seller = registerSeller();
                Bidder bidder = registerBidder();

                Auction auction = new Auction(uniqueId("AUC"), "Rare Antique", seller.getId(), 10_000_000, 500_000);
                manager.addAuction(auction);

                try (TestConnection sellerConn = openConnection();
                                TestConnection bidderConn = openConnection()) {

                        sellerConn.sendCommand("LOGIN " + seller.getId() + " password123");
                        sellerConn.sendCommand("JOIN " + auction.getId());

                        bidderConn.sendCommand("LOGIN " + bidder.getId() + " password123");
                        bidderConn.sendCommand("JOIN " + auction.getId());

                        sellerConn.sendCommand("START " + auction.getId());

                        // Đóng phiên khi không có ai bid
                        auction.finishAuction();
                        assertEquals(Auction.Status.FINISHED, auction.getStatus());
                        assertNull(auction.getHighestBidderId());

                        // Seller nhận thông báo NOTIFY SELLER với người thắng là NONE
                        String sellerNotice = sellerConn.reader.readLine();
                        assertEquals("NOTIFY SELLER " + auction.getId() + " 10000000.00 NONE " + auction.getItemId(),
                                        sellerNotice);
                        String sellerClosed = sellerConn.reader.readLine();
                        assertEquals("NOTIFY CLOSED " + auction.getId() + " 10000000.00 NONE", sellerClosed);

                        // BidderConn đã tham gia nên nhận NOTIFY CLOSED
                        String bidderClosed = bidderConn.reader.readLine();
                        assertEquals("NOTIFY CLOSED " + auction.getId() + " 10000000.00 NONE", bidderClosed);

                        // Bidder không thể PAY vì không phải người thắng
                        String payError = bidderConn.sendCommand("PAY " + auction.getId());
                        assertEquals("ERROR Only the winning bidder can make payment for this auction", payError);
                }
        }

        // -------------------- Helper Methods --------------------

        private TestConnection openConnection() throws IOException {
                return openConnection(ClientHandler.DEFAULT_SO_TIMEOUT_MS);
        }

        private TestConnection openConnection(int soTimeoutMillis) throws IOException {

                ServerSocket listenerSocket = new ServerSocket(0);

                Socket clientSocket = new Socket(
                                "127.0.0.1",
                                listenerSocket.getLocalPort());

                Socket handlerSocket = listenerSocket.accept();
                // Blocking tới khi client kết nối xong, trả socket phía server
                AuctionServer server = new AuctionServer(0);

                ClientHandler handler = new ClientHandler(
                                handlerSocket,
                                server,
                                soTimeoutMillis);
                // Bọc socket + server vào handler
                Thread handlerThread = new Thread(
                                handler,
                                "client-handler-test");

                handlerThread.setDaemon(true);
                handlerThread.start();
                // ClientHandler chạy trong thread riêng có tên "..", start() để thread bắt đầu
                // phục vụ
                clientSocket.setSoTimeout(3000);

                BufferedReader reader = new BufferedReader(
                                new InputStreamReader(
                                                clientSocket.getInputStream(),
                                                StandardCharsets.UTF_8));

                PrintWriter writer = new PrintWriter(
                                clientSocket.getOutputStream(),
                                true,
                                StandardCharsets.UTF_8);

                String welcomeMessage = reader.readLine();

                return new TestConnection(
                                listenerSocket,
                                clientSocket,
                                handlerSocket,
                                handler,
                                handlerThread,
                                reader,
                                writer,
                                welcomeMessage);
                // Đóng gói vào TestConnection để try-with-resources quản lý
        }

        private Bidder registerBidder() {

                String id = uniqueId("BIDDER");

                Bidder bidder = new Bidder(
                                id,
                                "Bob",
                                "password123");

                manager.registerUser(bidder);

                return bidder;
        }

        private Seller registerSeller() {

                String id = uniqueId("SELLER");

                Seller seller = new Seller(
                                id,
                                "Alice",
                                "password123");

                manager.registerUser(seller);

                return seller;
        }

        private Auction createAuction() {

                Auction auction = new Auction(
                                uniqueId("AUC"),
                                "MacBook Pro",
                                uniqueId("SELLER"),
                                20_000_000,
                                1_000_000);

                manager.addAuction(auction);

                return auction;
        }

        private String uniqueId(String prefix) {

                return prefix + "-"
                                + UUID.randomUUID().toString().substring(0, 8);
        }

        private void finishIfRunning(Auction auction) {

                if (auction.getStatus() == Auction.Status.RUNNING) {
                        auction.finishAuction();
                }
        }

        // -------------------- Test Connection --------------------

        private static class TestConnection implements AutoCloseable {

                private final ServerSocket listenerSocket;
                private final Socket clientSocket;
                private final Socket handlerSocket;
                private final ClientHandler handler;
                private final Thread handlerThread;

                private final BufferedReader reader;
                private final PrintWriter writer;

                private final String welcomeMessage;

                TestConnection(
                                ServerSocket listenerSocket,
                                Socket clientSocket,
                                Socket handlerSocket,
                                ClientHandler handler,
                                Thread handlerThread,
                                BufferedReader reader,
                                PrintWriter writer,
                                String welcomeMessage) {
                        this.listenerSocket = listenerSocket;
                        this.clientSocket = clientSocket;
                        this.handlerSocket = handlerSocket;
                        this.handler = handler;
                        this.handlerThread = handlerThread;
                        this.reader = reader;
                        this.writer = writer;
                        this.welcomeMessage = welcomeMessage;
                }

                String sendCommand(String command) throws IOException {

                        writer.println(command);

                        if (writer.checkError()) {
                                throw new IOException("Failed to send command");
                        }

                        return reader.readLine();
                }

                List<String> requestAuctionList() throws IOException {

                        writer.println(AuctionProtocol.CMD_LIST);

                        if (writer.checkError()) {
                                throw new IOException("Failed to send LIST command");
                        }

                        List<String> response = new ArrayList<>();

                        String line;

                        while ((line = reader.readLine()) != null) {

                                response.add(line);

                                if (AuctionProtocol.AUCTIONS_END.equals(line)) {
                                        return response;
                                }
                        }

                        throw new IOException("Server closed connection before AUCTIONS_END");
                }

                @Override
                public void close() {
                        try {
                                if (!clientSocket.isClosed()) {
                                        clientSocket.shutdownOutput();
                                }
                        } catch (Exception ignored) {
                        }

                        handler.close();

                        try {
                                clientSocket.close();
                                handlerSocket.close();
                                listenerSocket.close();

                                handlerThread.join(500);

                        } catch (IOException e) {
                                throw new RuntimeException("Failed to close test sockets", e);

                        } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Interrupted while closing test connection", e);
                        }
                }
        }
}
