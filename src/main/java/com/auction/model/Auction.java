package com.auction.model;

import com.auction.item.Item;
import com.auction.observer.AuctionObserver;
import com.auction.observer.AuctionSubject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.time.Duration;
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

import com.auction.exceptions.AuctionClosedException;
import com.auction.exceptions.InvalidBidException;

/**
 * Đại diện cho một phiên đấu giá.
 * - Triển khai AuctionSubject (Observer Pattern) để phát thông báo khi có bid
 * mới.
 * - Triển khai Serializable để hỗ trợ lưu trữ dữ liệu phiên ra file.
 * - Sử dụng ReentrantLock để kiểm soát đồng thời (Concurrency), tránh Lost
 * Update / Race Condition.
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
    private final String itemId;
    private final String sellerId;
    private final double startPrice;
    private double currentPrice;
    private final double stepPrice;
    private Status status;
    private String highestBidderId;
    private final List<BidTransaction> bidHistory;
    private long endTimeMillis;

    // Concurrency control: ReentrantLock (fair lock đảm bảo FIFO cho các lượt bid)
    private transient ReentrantLock lock = new ReentrantLock(true);
    private transient ScheduledFuture<?> finishTask;

    // Observer list: CopyOnWriteArrayList bảo đảm thread-safe khi thêm/xóa/duyệt
    // thông báo
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
            this.endTimeMillis = System.currentTimeMillis() + duration.toMillis();
            this.finishTask = TIMER.schedule(this::finishIfRunning, duration.toMillis(), TimeUnit.MILLISECONDS);
            System.out.printf("[Auction %s] Started auction for item: %s%n", id, itemId);
        } finally {
            lock.unlock();
        }
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
    }

    public long getEndTimeMillis() {
        return endTimeMillis;
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
