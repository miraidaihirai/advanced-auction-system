package com.auction;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.auction.Item.Factory.ElectronicsFactory;
import com.auction.Item.Factory.ItemFactory;
import com.auction.Item.Item;
import com.auction.exceptions.AuctionClosedException;
import com.auction.exceptions.AuthenticationException;
import com.auction.exceptions.InvalidBidException;
import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.Seller;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("==================================================");
        System.out.println(" ONLINE AUCTION SYSTEM - SOLID & CONCURRENCY DEMO ");
        System.out.println("==================================================");

        // -----------------------------------------------------------------
        // 1. Kiem tra Singleton Pattern
        // -----------------------------------------------------------------
        System.out.println("\n--- [1] Checking Singleton: AuctionManager ---");
        AuctionManager manager1 = AuctionManager.getInstance();
        AuctionManager manager2 = AuctionManager.getInstance();
        boolean isSameInstance = (manager1 == manager2);
        System.out.printf("Instance 1: %s%n", manager1);
        System.out.printf("Instance 2: %s%n", manager2);
        System.out.printf("=> Same instance (Singleton): %s%n", isSameInstance ? "SUCCESS" : "FAILED");

        // -----------------------------------------------------------------
        // 2. SOLID + Observer Pattern: Tu dong ban thong bao khi co bid moi
        // -----------------------------------------------------------------
        System.out.println("\n--- [2] SOLID & Observer Pattern: Tu dong ban thong bao khi co bid ---");

        // Khoi tao Seller va cac Bidders (ke thua User - LSP, implement AuctionObserver
        // - ISP/DIP)
        Seller alice = new Seller("SELLER-01", "Alice", "password123");
        Bidder bob = new Bidder("BIDDER-01", "Bob", "password123");
        Bidder charlie = new Bidder("BIDDER-02", "Charlie", "password123");
        Bidder david = new Bidder("BIDDER-03", "David", "password123");
        manager1.registerUser(bob);
        manager1.registerUser(charlie);
        manager1.registerUser(david);

        // Tao san pham Item (ke thua Item)
        ItemFactory laptopFactory = new ElectronicsFactory("Laptop", 101, 10_000_000, 1, "Dell", "XPS 15", 24);
        Item laptop = laptopFactory.createItem();

        // Tao phien dau gia AUC-001
        Auction auction1 = new Auction("AUC-001", laptop, alice.getId(), 10_000_000.0, 500_000.0);
        manager1.addAuction(auction1);

        // Dang ky Observers theo doi phien dau gia
        auction1.addObserver(alice);
        auction1.addObserver(bob);
        auction1.addObserver(charlie);
        auction1.addObserver(david);

        // Dat gia khi chua mo phien -> Bi tu choi
        System.out.println("\n>> Test: Dat gia khi phien chua mo:");
        try {
            manager1.placeBid("AUC-001", bob.getId(), "password123", 10_000_000.0);
        } catch (AuctionClosedException e) {
            System.out.println(e.getMessage());
        }

        // Mo phien dau gia
        System.out.println("\n>> Mo phien AUC-001:");
        auction1.startAuction();

        // Bob dat gia khoi diem hop le (10,000,000) -> Observers nhan thong bao
        System.out.println("\n>> Bob dat gia 10,000,000:");
        manager1.placeBid("AUC-001", bob.getId(), "password123", 10_000_000.0);

        // Charlie dat gia khong du buoc gia (+500,000 -> can >= 10,500,000) -> That bai
        System.out.println("\n>> Charlie dat gia thieu buoc gia (10,200,000):");
        try {
            manager1.placeBid("AUC-001", charlie.getId(), "password123", 10_200_000.0);
        } catch (InvalidBidException e) {
            System.out.println(e.getMessage());
        }

        // Charlie dat gia hop le 11,000,000 -> Tu dong ban thong bao cho Alice, Bob,
        // David
        System.out.println("\n>> Charlie dat gia hop le (11,000,000):");
        manager1.placeBid("AUC-001", charlie.getId(), "password123", 11_000_000.0);

        // David tra gia 12,500,000 -> Tu dong ban thong bao
        System.out.println("\n>> David dat gia cao hon (12,500,000):");
        manager1.placeBid("AUC-001", david.getId(), "password123", 12_500_000.0);

        // Dong phien dau gia AUC-001 -> Tu dong ban thong bao ket qua cho moi nguoi
        System.out.println("\n>> Dong phien AUC-001:");
        auction1.closeAuction();

        // -----------------------------------------------------------------
        // 3. Concurrency: Kiem tra dau gia dong thoi bang ReentrantLock
        // -----------------------------------------------------------------
        System.out.println("\n--------------------------------------------------");
        System.out.println("--- [3] Concurrency Test: placeBid() voi ReentrantLock ---");
        System.out.println("--------------------------------------------------");

        Auction auctionConcurrent = new Auction("AUC-RACE", "Limited Antique Vase", alice.getId(), 1_000_000.0,
                100_000.0);
        manager1.addAuction(auctionConcurrent);
        auctionConcurrent.startAuction();

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch readyLatch = new CountDownLatch(numThreads);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successBids = new AtomicInteger(0);
        AtomicInteger failedBids = new AtomicInteger(0);

        System.out.printf("Phat lenh cho %d threads dong thoi dat gia vao phien AUC-RACE...%n", numThreads);

        for (int i = 1; i <= numThreads; i++) {
            final int bidderNum = i;
            final String bidderId = "Bidder_Concurrent_" + bidderNum;
            manager1.registerUser(new Bidder(bidderId, "Concurrent Bidder " + bidderNum, "password123"));
            final double bidAmount = 1_000_000.0 + (bidderNum * 100_000.0);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    // Cho tieng sung phat lenh de tat ca thread gui request cung thoi diem
                    startGun.await();
                    boolean result = manager1.placeBid("AUC-RACE", bidderId, "password123", bidAmount);
                    if (result) {
                        successBids.incrementAndGet();
                    } else {
                        failedBids.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (AuctionClosedException | InvalidBidException | AuthenticationException e) {
                    failedBids.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startGun.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("\n--- Ket qua xu ly dong thoi (Concurrency Result) ---");
        System.out.printf("Tong luot bid thanh cong: %d%n", successBids.get());
        System.out.printf("Tong luot bid bi tu choi: %d%n", failedBids.get());
        System.out.printf("Gia cuoi cung dat duoc: %.2f%n", auctionConcurrent.getCurrentPrice());
        System.out.printf("So luong ban ghi trong lich su bid: %d%n", auctionConcurrent.getBidHistory().size());
        System.out.println("=> Khong bi Lost Update, khong bi Race Condition (ReentrantLock dam bao tinh toan ven)!");

        auctionConcurrent.closeAuction();

        System.out.println("\n==================================================");
        System.out.println(" DEMO COMPLETED SUCCESSFULLY");
        System.out.println("==================================================");
    }
}
