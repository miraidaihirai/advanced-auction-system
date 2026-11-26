package com.auction.model;

import com.auction.item.Art;
import com.auction.item.Electronics;
import com.auction.item.Item;
import com.auction.item.Vehicle;
import com.auction.observer.AuctionObserver;
import com.auction.observer.AuctionSubject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.auction.exceptions.AuctionClosedException;
import com.auction.exceptions.InvalidBidException;

/**
 * Đại diện cho một phiên đấu giá.
 * - Triển khai AuctionSubject (Observer Pattern) để phát thông báo khi có bid mới.
 * - Triển khai Serializable để hỗ trợ lưu trữ dữ liệu phiên ra file.
 * - Sử dụng ReentrantLock để kiểm soát đồng thời (Concurrency), tránh Lost Update / Race Condition.
 * - Hỗ trợ quy trình kiểm duyệt Seller -> Admin (OPEN -> RUNNING / CANCELED),
 *   Anti-Sniping và cấu hình Auto-Bidding.
 */
public class Auction implements AuctionSubject, Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status {
        OPEN, RUNNING, FINISHED, PAID, CANCELED
    }

    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(1);
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "auction-timeout");
        thread.setDaemon(true);
        return thread;
    });

    private final String id;
    private String itemId;
    private final String sellerId;
    private double startPrice;
    private double currentPrice;
    private double stepPrice;
    private Status status;
    private String highestBidderId;
    private final List<BidTransaction> bidHistory;
    private long endTimeMillis;

    // Metadata cho BidX & quy trình Seller -> Admin -> Payout
    private Item item;
    private String category = "COLLECTIBLE";
    private String description = "";
    private String itemSpecs = "";
    private String imageKey = "";
    private long requestedDurationSeconds = 300;
    private String rejectionReason = "";
    private boolean payoutSettled = false;

    // Anti-Sniping (tự động gia hạn khi có bid ở giây cuối)
    private boolean antiSnipingEnabled = false;
    private long antiSnipingThresholdSeconds = 30;
    private long antiSnipingExtensionSeconds = 30;
    private int antiSnipingExtensionsCount = 0;

    // Auto-Bidding configs theo bidderId
    private Map<String, AutoBidConfig> autoBids = new ConcurrentHashMap<>();

    // Lịch sử tin nhắn thảo luận trong phòng đấu giá (lưu bền vững qua đăng xuất / restart)
    private List<ChatMessage> chatHistory = new ArrayList<>();

    // Concurrency control: ReentrantLock (fair lock đảm bảo FIFO cho các lượt bid)
    private transient ReentrantLock lock = new ReentrantLock(true);
    private transient ScheduledFuture<?> finishTask;

    // Observer list: CopyOnWriteArrayList bảo đảm thread-safe khi thêm/xóa/duyệt thông báo
    private transient List<AuctionObserver> observers = new CopyOnWriteArrayList<>();

    public Auction(String id, String itemId, String sellerId, double startPrice, double stepPrice) {
        this.id = id;
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startPrice = startPrice;
        this.currentPrice = startPrice;
        this.stepPrice = stepPrice;
        this.status = Status.OPEN;
        this.highestBidderId = null;
        this.bidHistory = new ArrayList<>();
    }

    public Auction(String id, Item item, String sellerId, double startPrice, double stepPrice) {
        this(id, item != null ? item.getName() : "Unknown Item", sellerId, startPrice, stepPrice);
        setItem(item);
        if (item != null && item.getImagePath() != null) {
            this.imageKey = item.getImagePath();
        }
    }

    /**
     * Tạo yêu cầu mở phòng đấu giá từ Seller ở trạng thái chờ Admin phê duyệt.
     */
    public static Auction createPendingRequest(
            String id,
            String itemName,
            String sellerId,
            double startPrice,
            double stepPrice,
            String category,
            String description,
            long requestedDurationSeconds,
            String imageKey) {
        Auction auction = new Auction(id, itemName, sellerId, startPrice, stepPrice);
        auction.status = Status.OPEN;
        auction.category = (category != null && !category.isBlank()) ? category.trim().toUpperCase() : "OTHER";
        auction.description = description != null ? description.trim() : "";
        auction.requestedDurationSeconds = Math.max(30, requestedDurationSeconds);
        auction.imageKey = imageKey != null ? imageKey.trim() : "";
        return auction;
    }

    public void startAuction() {
        startAuction(DEFAULT_DURATION);
    }

    public void startAuction(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Auction duration must be positive");
        }

        lock.lock();
        try {
            if (status != Status.OPEN) {
                throw new IllegalStateException("Only an OPEN auction can be started");
            }
            beginRunningLocked(duration);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Admin phê duyệt yêu cầu mở phòng của Seller và kích hoạt chạy ngay.
     */
    public void approveAndStart(Duration duration) {
        Duration effectiveDuration = (duration != null && !duration.isZero() && !duration.isNegative())
                ? duration
                : Duration.ofSeconds(Math.max(30, requestedDurationSeconds));
        lock.lock();
        try {
            if (status != Status.OPEN) {
                throw new IllegalStateException("Only an OPEN auction can be approved");
            }
            this.rejectionReason = "";
            beginRunningLocked(effectiveDuration);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Admin từ chối yêu cầu mở phòng của Seller kèm lý do (chuyển sang CANCELED).
     */
    public void rejectRequest(String reason) {
        lock.lock();
        try {
            if (status != Status.OPEN) {
                throw new IllegalStateException("Only an OPEN auction can be rejected");
            }
            this.status = Status.CANCELED;
            this.rejectionReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Không đạt tiêu chuẩn kiểm duyệt";
            System.out.printf("[Auction %s] Rejected/Canceled by Admin. Reason: %s%n", id, rejectionReason);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cập nhật thông tin sản phẩm khi phiên đang ở trạng thái chờ duyệt (OPEN).
     */
    public void updatePendingDetails(
            String itemName,
            String category,
            double startPrice,
            double stepPrice,
            long durationSeconds,
            String imageKey,
            String description,
            String itemSpecs) {
        lock.lock();
        try {
            if (status != Status.OPEN) {
                throw new IllegalStateException("Chỉ có thể sửa sản phẩm khi đang chờ duyệt (OPEN)");
            }
            if (itemName == null || itemName.trim().isEmpty()) {
                throw new IllegalArgumentException("Tên sản phẩm không được để trống");
            }
            if (!Double.isFinite(startPrice) || startPrice <= 0 || !Double.isFinite(stepPrice) || stepPrice <= 0) {
                throw new IllegalArgumentException("Giá khởi điểm và bước giá phải là số dương");
            }
            this.itemId = itemName.trim();
            this.category = (category != null && !category.isBlank()) ? category.trim().toUpperCase() : "OTHER";
            this.startPrice = startPrice;
            this.currentPrice = startPrice;
            this.stepPrice = stepPrice;
            this.requestedDurationSeconds = Math.max(30, durationSeconds);
            this.imageKey = imageKey != null ? imageKey.trim() : "";
            this.description = description != null ? description.trim() : "";
            this.itemSpecs = itemSpecs != null ? itemSpecs.trim() : "";
        } finally {
            lock.unlock();
        }
    }

    private void beginRunningLocked(Duration duration) {
        this.status = Status.RUNNING;
        this.endTimeMillis = System.currentTimeMillis() + duration.toMillis();
        if (duration.getSeconds() >= 60 && !this.antiSnipingEnabled) {
            this.antiSnipingEnabled = true;
            this.antiSnipingThresholdSeconds = 30;
            this.antiSnipingExtensionSeconds = 30;
        }
        this.finishTask = TIMER.schedule(this::finishIfRunning, duration.toMillis(), TimeUnit.MILLISECONDS);
        System.out.printf("[Auction %s] Started auction for item: %s%n", id, itemId);
    }

    public void closeAuction() {
        finishAuction();
    }

    public void finishAuction() {
        transitionToFinished(true);
    }

    private void finishIfRunning() {
        transitionToFinished(false);
    }

    private void transitionToFinished(boolean requireRunning) {
        List<AuctionObserver> observersToNotify;
        lock.lock();
        try {
            if (status != Status.RUNNING) {
                if (requireRunning) {
                    throw new IllegalStateException("Only a RUNNING auction can be finished");
                }
                return;
            }
            status = Status.FINISHED;
            if (finishTask != null) {
                finishTask.cancel(false);
            }
            observersToNotify = new ArrayList<>(observers);
        } finally {
            lock.unlock();
        }

        System.out.printf("[Auction %s] Auction finished. Winner: %s with price: %.2f%n",
                id, (highestBidderId != null ? highestBidderId : "None"), currentPrice);
        for (AuctionObserver observer : observersToNotify) {
            try {
                observer.onAuctionClosed(this);
            } catch (Exception e) {
                System.err.printf("[Observer Error] %s: %s%n", observer.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    public void markPaid() {
        List<AuctionObserver> observersToNotify;
        lock.lock();
        try {
            if (status != Status.FINISHED || highestBidderId == null) {
                throw new IllegalStateException("Only a finished auction with a winner can be marked PAID");
            }
            status = Status.PAID;
            observersToNotify = new ArrayList<>(observers);
        } finally {
            lock.unlock();
        }

        System.out.printf("[Auction %s] Auction marked PAID for winner: %s (%.2f)%n",
                id, highestBidderId, currentPrice);
        for (AuctionObserver observer : observersToNotify) {
            try {
                observer.onAuctionPaid(this);
            } catch (Exception e) {
                System.err.printf("[Observer Error] %s: %s%n", observer.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    public void finishAndPay() {
        finishAuction();
        if (getHighestBidderId() != null) {
            markPaid();
        }
    }

    public void cancelAuction() {
        lock.lock();
        try {
            if (status != Status.FINISHED) {
                throw new IllegalStateException("Only a FINISHED auction can be canceled");
            }
            status = Status.CANCELED;
        } finally {
            lock.unlock();
        }
    }

    /*
     * Hủy bỏ tác vụ đếm ngược kết thúc phiên (nếu đang chạy), dùng khi reset/dọn
     * dẹp phiên.
     */
    public void cancelTimer() {
        lock.lock();
        try {
            if (finishTask != null && !finishTask.isDone()) {
                finishTask.cancel(false);
            }
        } finally {
            lock.unlock();
        }
    }

    /*
     * Xử lý đặt giá đồng thời (Concurrency) bằng ReentrantLock.
     * Tránh Lost Update và Race Condition khi nhiều thread đặt giá cùng một thời
     * điểm.
     */
    public boolean placeBid(BidTransaction bid) {
        lock.lock();
        try {
            if (status != Status.RUNNING) {
                throw new AuctionClosedException("Auction [" + id + "] is not accepting bids (status: " + status + ")");
            }

            if (!Double.isFinite(bid.getAmount()) || bid.getAmount() <= 0) {
                throw new InvalidBidException("Bid amount must be a positive finite number");
            }

            // Lượt đầu tiên chỉ cần >= startPrice, các lượt sau cần >= currentPrice +
            // stepPrice
            double minRequired = (bidHistory.isEmpty()) ? startPrice : (currentPrice + stepPrice);
            if (bid.getAmount() < minRequired) {
                throw new InvalidBidException(String.format(
                        "Bid amount %.2f is below the minimum required %.2f", bid.getAmount(), minRequired));
            }

            this.currentPrice = bid.getAmount();
            this.highestBidderId = bid.getBidderId();
            this.bidHistory.add(bid);
            System.out.printf("[Success] %s placed bid %.2f for auction [%s]%n",
                    bid.getBidderId(), bid.getAmount(), id);

            // Anti-Sniping: nếu đặt giá trong X giây cuối -> tự động gia hạn thêm Y giây
            if (antiSnipingEnabled && endTimeMillis > 0) {
                long now = System.currentTimeMillis();
                long remainingMs = endTimeMillis - now;
                if (remainingMs > 0 && remainingMs <= antiSnipingThresholdSeconds * 1000L) {
                    long extensionMs = antiSnipingExtensionSeconds * 1000L;
                    endTimeMillis += extensionMs;
                    antiSnipingExtensionsCount++;
                    if (finishTask != null) {
                        finishTask.cancel(false);
                    }
                    long newDelay = Math.max(100L, endTimeMillis - System.currentTimeMillis());
                    finishTask = TIMER.schedule(this::finishIfRunning, newDelay, TimeUnit.MILLISECONDS);
                    System.out.printf("[Anti-Sniping %s] Extended by %ds (count=%d)%n",
                            id, antiSnipingExtensionSeconds, antiSnipingExtensionsCount);
                }
            }

            // Tự động bắn thông báo cho toàn bộ Observers
            notifyObservers(bid);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void addObserver(AuctionObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    @Override
    public void removeObserver(AuctionObserver observer) {
        if (observer != null) {
            observers.remove(observer);
        }
    }

    @Override
    public void notifyObservers(BidTransaction bid) {
        for (AuctionObserver observer : observers) {
            try {
                observer.onNewBid(this, bid);
            } catch (Exception e) {
                System.err.printf("[Observer Error] %s: %s%n", observer.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    public List<AuctionObserver> getObservers() {
        return Collections.unmodifiableList(observers);
    }

    public int getObserverCount() {
        return observers.size();
    }

    public String getId() {
        return id;
    }

    public String getItemId() {
        lock.lock();
        try {
            return itemId;
        } finally {
            lock.unlock();
        }
    }

    public String getSellerId() {
        return sellerId;
    }

    public double getStartPrice() {
        lock.lock();
        try {
            return startPrice;
        } finally {
            lock.unlock();
        }
    }

    public double getCurrentPrice() {
        lock.lock();
        try {
            return currentPrice;
        } finally {
            lock.unlock();
        }
    }

    public double getStepPrice() {
        lock.lock();
        try {
            return stepPrice;
        } finally {
            lock.unlock();
        }
    }

    public Status getStatus() {
        lock.lock();
        try {
            return status;
        } finally {
            lock.unlock();
        }
    }

    public String getHighestBidderId() {
        lock.lock();
        try {
            return highestBidderId;
        } finally {
            lock.unlock();
        }
    }

    public List<BidTransaction> getBidHistory() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(bidHistory));
        } finally {
            lock.unlock();
        }
    }

    public long getEndTimeMillis() {
        lock.lock();
        try {
            return endTimeMillis;
        } finally {
            lock.unlock();
        }
    }

    public String getCategory() {
        return category != null ? category : "COLLECTIBLE";
    }

    public void setCategory(String category) {
        if (category != null && !category.isBlank()) {
            this.category = category.trim().toUpperCase();
        }
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description != null ? description.trim() : "";
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
        if (item != null && (itemSpecs == null || itemSpecs.isBlank())) {
            this.itemSpecs = extractSpecsFromItem(item);
        }
    }

    public String getItemSpecs() {
        return itemSpecs != null ? itemSpecs : "";
    }

    public void setItemSpecs(String itemSpecs) {
        this.itemSpecs = itemSpecs != null ? itemSpecs.trim() : "";
    }

    public static String extractSpecsFromItem(Item item) {
        if (item instanceof Art art) {
            return formatSpecPairs(
                    "Tác giả", art.getArtist(),
                    "Chất liệu", art.getMedium(),
                    "Năm sáng tác", art.getYearCreated() > 0 ? String.valueOf(art.getYearCreated()) : "");
        }
        if (item instanceof Electronics elec) {
            return formatSpecPairs(
                    "Thương hiệu", elec.getBrand(),
                    "Model", elec.getModel(),
                    "Bảo hành", elec.getWarrantyPeriod() > 0 ? elec.getWarrantyPeriod() + " tháng" : "Chính hãng");
        }
        if (item instanceof Vehicle vehicle) {
            return formatSpecPairs(
                    "Hãng xe", vehicle.getMake(),
                    "Dòng xe", vehicle.getModel(),
                    "Năm sản xuất", vehicle.getYear() > 0 ? String.valueOf(vehicle.getYear()) : "");
        }
        return "";
    }

    private static String formatSpecPairs(String k1, String v1, String k2, String v2, String k3, String v3) {
        StringBuilder sb = new StringBuilder();
        appendSpec(sb, k1, v1);
        appendSpec(sb, k2, v2);
        appendSpec(sb, k3, v3);
        return sb.toString();
    }

    private static void appendSpec(StringBuilder sb, String key, String val) {
        if (val == null || val.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(";;");
        }
        sb.append(key).append(": ").append(val.replace(";;", ", ").replace("|", "/").trim());
    }

    public String getImageKey() {
        return imageKey != null ? imageKey : "";
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey != null ? imageKey.trim() : "";
    }

    public long getRequestedDurationSeconds() {
        return requestedDurationSeconds > 0 ? requestedDurationSeconds : 300;
    }

    public void setRequestedDurationSeconds(long requestedDurationSeconds) {
        if (requestedDurationSeconds > 0) {
            this.requestedDurationSeconds = requestedDurationSeconds;
        }
    }

    public String getRejectionReason() {
        return rejectionReason != null ? rejectionReason : "";
    }

    public boolean isPayoutSettled() {
        lock.lock();
        try {
            return payoutSettled;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Đánh dấu đã chuyển tiền về ví Seller đúng một lần duy nhất (idempotent, thread-safe).
     */
    public boolean claimPayoutSettlement() {
        lock.lock();
        try {
            if (payoutSettled) {
                return false;
            }
            if ((status == Status.FINISHED || status == Status.PAID) && highestBidderId != null) {
                payoutSettled = true;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void setAntiSniping(boolean enabled, long thresholdSeconds, long extensionSeconds) {
        lock.lock();
        try {
            this.antiSnipingEnabled = enabled;
            if (thresholdSeconds > 0) {
                this.antiSnipingThresholdSeconds = thresholdSeconds;
            }
            if (extensionSeconds > 0) {
                this.antiSnipingExtensionSeconds = extensionSeconds;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isAntiSnipingEnabled() {
        return antiSnipingEnabled;
    }

    public int getAntiSnipingExtensionsCount() {
        lock.lock();
        try {
            return antiSnipingExtensionsCount;
        } finally {
            lock.unlock();
        }
    }

    public void registerAutoBid(AutoBidConfig config) {
        if (config == null) {
            return;
        }
        if (autoBids == null) {
            autoBids = new ConcurrentHashMap<>();
        }
        autoBids.put(config.getBidderId(), config);
    }

    public void cancelAutoBid(String bidderId) {
        if (bidderId != null && autoBids != null) {
            autoBids.remove(bidderId);
        }
    }

    public Map<String, AutoBidConfig> getAutoBids() {
        if (autoBids == null) {
            autoBids = new ConcurrentHashMap<>();
        }
        return Collections.unmodifiableMap(autoBids);
    }

    public void addChatMessage(ChatMessage msg) {
        if (msg == null) {
            return;
        }
        lock.lock();
        try {
            if (chatHistory == null) {
                chatHistory = new ArrayList<>();
            }
            chatHistory.add(msg);
            if (chatHistory.size() > 200) {
                chatHistory.remove(0);
            }
        } finally {
            lock.unlock();
        }
    }

    public List<ChatMessage> getChatHistory() {
        lock.lock();
        try {
            if (chatHistory == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList<>(chatHistory));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Khôi phục các trường transient sau khi Deserialization từ file.
     * Đảm bảo ReentrantLock và danh sách Observers được khởi tạo mới an toàn.
     * Tự động xử lý thời gian còn lại của phiên RUNNING nếu có.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.lock = new ReentrantLock(true);
        this.observers = new CopyOnWriteArrayList<>();
        if (this.autoBids == null) {
            this.autoBids = new ConcurrentHashMap<>();
        }
        if (this.chatHistory == null) {
            this.chatHistory = new ArrayList<>();
        }
        if (this.category == null) {
            this.category = "COLLECTIBLE";
        }
        if (this.description == null) {
            this.description = "";
        }
        if (this.itemSpecs == null) {
            this.itemSpecs = "";
        }
        if (this.itemSpecs.isBlank() && this.item != null) {
            this.itemSpecs = extractSpecsFromItem(this.item);
        }
        if (this.imageKey == null) {
            this.imageKey = "";
        }
        if (this.rejectionReason == null) {
            this.rejectionReason = "";
        }

        if (this.status == Status.RUNNING) {
            long remaining = endTimeMillis - System.currentTimeMillis();
            if (remaining <= 0 && endTimeMillis > 0) {
                this.status = Status.FINISHED;
            } else if (remaining > 0) {
                this.finishTask = TIMER.schedule(this::finishIfRunning, remaining, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public String toString() {
        lock.lock();
        try {
            return String.format("Auction[id=%s, itemId=%s, seller=%s, currentPrice=%.2f, winner=%s, status=%s]",
                    id, itemId, sellerId, currentPrice, (highestBidderId != null ? highestBidderId : "None"), status);
        } finally {
            lock.unlock();
        }
    }
}
