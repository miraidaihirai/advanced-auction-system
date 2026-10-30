package model;

import Item.Item;
import observer.AuctionObserver;
import observer.AuctionSubject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import exceptions.AuctionClosedException;
import exceptions.InvalidBidException;

/**
 * Đại diện cho một phiên đấu giá.
 * - Triển khai AuctionSubject (Observer Pattern) để phát thông báo khi có bid mới.
 * - Sử dụng ReentrantLock để kiểm soát đồng thời (Concurrency), tránh Lost Update / Race Condition.
 */
public class Auction implements AuctionSubject {
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
    private final String itemId;
    private final String sellerId;
    private final double startPrice;
    private double currentPrice;
    private final double stepPrice;
    private Status status;
    private String highestBidderId;
    private final List<BidTransaction> bidHistory;

    // Concurrency control: ReentrantLock (fair lock đảm bảo FIFO cho các lượt bid)
    private final ReentrantLock lock = new ReentrantLock(true);
    private ScheduledFuture<?> finishTask;

    // Observer list: CopyOnWriteArrayList bảo đảm thread-safe khi thêm/xóa/duyệt thông báo
    private final List<AuctionObserver> observers = new CopyOnWriteArrayList<>();
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Đại diện cho một phiên đấu giá.
 */
public class Auction {
    public enum Status {
        PENDING, OPEN, CLOSED
    }

    private String id;
    private String itemId;
    private String sellerId;
    private double startPrice;
    private double currentPrice;
    private double stepPrice;
    private Status status;
    private String highestBidderId;
    private List<BidTransaction> bidHistory;

    public Auction(String id, String itemId, String sellerId, double startPrice, double stepPrice) {
        this.id = id;
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startPrice = startPrice;
        this.currentPrice = startPrice;
        this.stepPrice = stepPrice;
        this.status = Status.OPEN;
        this.status = Status.PENDING;
        this.highestBidderId = null;
        this.bidHistory = new ArrayList<>();
    }

    public Auction(String id, Item item, String sellerId, double startPrice, double stepPrice) {
        this(id, item != null ? item.getName() : "Unknown Item", sellerId, startPrice, stepPrice);
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
            this.status = Status.RUNNING;
            this.finishTask = TIMER.schedule(this::finishIfRunning, duration.toMillis(), TimeUnit.MILLISECONDS);
            System.out.printf("[Auction %s] Started auction for item: %s%n", id, itemId);
        } finally {
            lock.unlock();
        }
    }

    public void finishAuction() {
        transitionToFinished(true);
    }

    public void closeAuction() {
        finishAuction();
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
        lock.lock();
        try {
            if (status != Status.FINISHED || highestBidderId == null) {
                throw new IllegalStateException("Only a finished auction with a winner can be marked PAID");
            }
            status = Status.PAID;
        } finally {
            lock.unlock();
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

    /**
     * Xử lý đặt giá đồng thời (Concurrency) bằng ReentrantLock.
     * Tránh Lost Update và Race Condition khi nhiều thread đặt giá cùng một thời điểm.
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

            // Lượt đầu tiên chỉ cần >= startPrice, các lượt sau cần >= currentPrice + stepPrice
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
    public void startAuction() {
        this.status = Status.OPEN;
        System.out.printf("[Auction %s] Started auction for item: %s%n", id, itemId);
    }

    public void closeAuction() {
        this.status = Status.CLOSED;
        System.out.printf("[Auction %s] Auction closed. Winner: %s with price: %.2f%n",
                id, (highestBidderId != null ? highestBidderId : "None"), currentPrice);
    }

    public synchronized boolean placeBid(BidTransaction bid) {
        if (status != Status.OPEN) {
            System.out.printf("[Error] Auction [%s] is not open (status: %s)%n", id, status);
            return false;
        }

        // Lượt đầu tiên chỉ cần >= startPrice, các lượt sau cần >= currentPrice + stepPrice
        double minRequired = (bidHistory.isEmpty()) ? startPrice : (currentPrice + stepPrice);
        if (bid.getAmount() < minRequired) {
            System.out.printf("[Error] Bid amount %.2f is invalid (Minimum required: %.2f)%n",
                    bid.getAmount(), minRequired);
            return false;
        }

        this.currentPrice = bid.getAmount();
        this.highestBidderId = bid.getBidderId();
        this.bidHistory.add(bid);
        System.out.printf("[Success] %s placed bid %.2f for auction [%s]%n",
                bid.getBidderId(), bid.getAmount(), id);
        return true;
    }

    public String getId() {
        return id;
    }

    public String getItemId() {
        return itemId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public double getStartPrice() {
        return startPrice;
    }

    public double getCurrentPrice() {
        lock.lock();
        try {
            return currentPrice;
        } finally {
            lock.unlock();
        }
        return currentPrice;
    }

    public double getStepPrice() {
        return stepPrice;
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
        return status;
    }

    public String getHighestBidderId() {
        return highestBidderId;
    }

    public List<BidTransaction> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
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
        return String.format("Auction[id=%s, itemId=%s, seller=%s, currentPrice=%.2f, winner=%s, status=%s]",
                id, itemId, sellerId, currentPrice, (highestBidderId != null ? highestBidderId : "None"), status);
    }
}
