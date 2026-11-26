package com.auction.session;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import network.AuctionClient;
import network.AuctionDetail;
import network.UserProfile;

/**
 * Quản lý phiên kết nối Socket duy nhất của BidX Client:
 * - Lưu hồ sơ người dùng hiện tại (UserProfile: ID, tên, vai trò, số dư ví).
 * - Duy trì heartbeat PING tự động (mỗi 20s) để không bao giờ bị Server ngắt kết nối khi nhàn rỗi.
 * - Lưu bộ nhớ đệm danh sách phiên đấu giá & danh sách yêu thích của người dùng.
 */
public class ClientSession {

    private String host;
    private int port;
    private AuctionClient auctionClient;

    private volatile UserProfile currentUser;
    private volatile String activeUserId;
    private final Map<String, AuctionDetail> auctionCache = new ConcurrentHashMap<>();
    private final Set<String> favoriteAuctionIds = ConcurrentHashMap.newKeySet();
    private final List<String> recentNotifications = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "bidx-heartbeat");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> heartbeatTask;

    public ClientSession(String host, int port) {
        this.host = host;
        this.port = port;
        this.auctionClient = new AuctionClient(host, port);
    }

    public synchronized void connect() throws IOException {
        if (!auctionClient.isConnected()) {
            auctionClient.connect();
            startHeartbeat();
        }
    }

    public synchronized void reconnectEndpoint(String newHost, int newPort) throws IOException {
        boolean endpointChanged = !this.host.equals(newHost) || this.port != newPort;
        if (endpointChanged) {
            stopHeartbeat();
            if (auctionClient.isConnected()) {
                auctionClient.disconnect();
            }
            this.host = newHost;
            this.port = newPort;
            this.auctionClient = new AuctionClient(newHost, newPort);
        }
        connect();
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (auctionClient != null && auctionClient.isConnected()) {
                    auctionClient.ping();
                }
            } catch (Exception ignored) {
            }
        }, 20, 20, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    public AuctionClient getClient() {
        return auctionClient;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isConnected() {
        return auctionClient != null && auctionClient.isConnected();
    }

    public synchronized void disconnect() {
        stopHeartbeat();
        currentUser = null;
        activeUserId = null;
        recentNotifications.clear();
        favoriteAuctionIds.clear();
        auctionCache.clear();
        if (auctionClient != null) {
            auctionClient.disconnect();
        }
    }

    public UserProfile getCurrentUser() {
        return currentUser;
    }

    public synchronized void setCurrentUser(UserProfile currentUser) {
        String nextId = currentUser != null ? currentUser.userId() : null;
        if (nextId == null || !nextId.equals(this.activeUserId)) {
            recentNotifications.clear();
            favoriteAuctionIds.clear();
        }
        this.currentUser = currentUser;
        this.activeUserId = nextId;
    }

    public void updateAuctionCache(List<AuctionDetail> list) {
        auctionCache.clear();
        if (list != null) {
            for (AuctionDetail d : list) {
                auctionCache.put(d.auctionId(), d);
            }
        }
    }

    public AuctionDetail getCachedAuction(String auctionId) {
        return auctionId != null ? auctionCache.get(auctionId) : null;
    }

    public boolean toggleFavorite(String auctionId) {
        if (auctionId == null) {
            return false;
        }
        if (favoriteAuctionIds.contains(auctionId)) {
            favoriteAuctionIds.remove(auctionId);
            return false;
        }
        favoriteAuctionIds.add(auctionId);
        return true;
    }

    public boolean isFavorite(String auctionId) {
        return auctionId != null && favoriteAuctionIds.contains(auctionId);
    }

    public Set<String> getFavoriteAuctionIds() {
        return Collections.unmodifiableSet(favoriteAuctionIds);
    }

    public void addNotificationLog(String entry) {
        if (currentUser != null && entry != null && !entry.isBlank()) {
            recentNotifications.add(0, entry);
            if (recentNotifications.size() > 30) {
                recentNotifications.remove(recentNotifications.size() - 1);
            }
        }
    }

    public List<String> getRecentNotifications() {
        return Collections.unmodifiableList(recentNotifications);
    }
}
