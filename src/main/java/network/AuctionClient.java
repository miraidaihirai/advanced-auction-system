package network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Socket Client kết nối tới AuctionServer.
 * - Gửi các bản tin yêu cầu (Login, Register, Join, Bid, Leave).
 * - Nhận và lắng nghe các thông báo realtime từ Server (Observer Pattern qua Socket).
 */
public class AuctionClient {

    public interface AuctionEventListener {
        void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId);
        default void onAuctionClosed(String auctionId, double finalPrice, String winnerId) {}
        default void onMessageReceived(String rawMessage) {}
    }

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread listenerThread;
    private volatile boolean connected = false;

    private final List<AuctionEventListener> listeners = new CopyOnWriteArrayList<>();

    public AuctionClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void addListener(AuctionEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(AuctionEventListener listener) {
        listeners.remove(listener);
    }

    public synchronized void connect() throws IOException {
        if (connected) {
            return;
        }

        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        connected = true;

        listenerThread = new Thread(this::listenLoop, "auction-client-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenLoop() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Notify raw message
                for (AuctionEventListener l : listeners) {
                    try {
                        l.onMessageReceived(line);
                    } catch (Exception ignored) {
                    }
                }

                // Parse realtime broadcast notifications
                if (line.startsWith(AuctionProtocol.NOTIFY_BID_PREFIX)) {
                    // NOTIFY BID <auctionId> <newPrice> <bidderId> <itemId>
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 6) {
                        String auctionId = parts[2];
                        double newPrice = Double.parseDouble(parts[3].replace(',', '.'));
                        String bidderId = parts[4];
                        String itemId = parts[5];
                        for (AuctionEventListener l : listeners) {
                            try {
                                l.onBidUpdate(auctionId, newPrice, bidderId, itemId);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } else if (line.startsWith(AuctionProtocol.NOTIFY_CLOSED_PREFIX)) {
                    // NOTIFY CLOSED <auctionId> <finalPrice> <winnerId>
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 5) {
                        String auctionId = parts[2];
                        double finalPrice = Double.parseDouble(parts[3].replace(',', '.'));
                        String winnerId = parts[4];
                        for (AuctionEventListener l : listeners) {
                            try {
                                l.onAuctionClosed(auctionId, finalPrice, winnerId);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (connected) {
                System.out.printf("[AuctionClient] Connection lost: %s%n", e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    public synchronized void sendRaw(String command) {
        if (!connected || out == null) {
            throw new IllegalStateException("Client is not connected to server");
        }
        out.println(command);
    }

    public void login(String userId, String password) {
        sendRaw(String.format("%s %s %s", AuctionProtocol.CMD_LOGIN, userId, password));
    }

    public void register(String userId, String name, String password, String role) {
        sendRaw(String.format("%s %s %s %s %s", AuctionProtocol.CMD_REGISTER, userId, name, password, role));
    }

    public void joinAuction(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_JOIN, auctionId));
    }

    public void leaveAuction(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_LEAVE, auctionId));
    }

    public void placeBid(String auctionId, double amount) {
        sendRaw(String.format(Locale.US, "%s %s %.2f", AuctionProtocol.CMD_BID, auctionId, amount));
    }

    public void requestList() {
        sendRaw(AuctionProtocol.CMD_LIST);
    }

    public void requestInfo(String auctionId) {
        sendRaw(String.format("%s %s", AuctionProtocol.CMD_INFO, auctionId));
    }

    public synchronized void disconnect() {
        if (!connected) {
            return;
        }
        connected = false;

        try {
            if (out != null) {
                out.println(AuctionProtocol.CMD_QUIT);
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
}
