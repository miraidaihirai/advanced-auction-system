package com.auction.manager;

import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionHistoryManagerTest {

    @TempDir
    Path tempDir;

    private AuctionManager manager;
    private File storageFile;

    @BeforeEach
    void setUp() {
        manager = AuctionManager.getInstance();
        manager.clear();
        storageFile = tempDir.resolve("history_test_data.ser").toFile();
        manager.setStoragePath(storageFile.getAbsolutePath());
        manager.setAutoSave(false);
    }

    @AfterEach
    void tearDown() {
        manager.clear();
        manager.setAutoSave(false);
    }

    @Test
    void testQueryTransactionHistoryByAuctionAndBidder() {
        Seller seller = new Seller("SELLER-HIST", "Alice", "password123");
        Bidder bob = new Bidder("BOB-HIST", "Bob", "password123");
        Bidder charlie = new Bidder("CHARLIE-HIST", "Charlie", "password123");

        manager.registerUser(seller);
        manager.registerUser(bob);
        manager.registerUser(charlie);

        Auction auc1 = new Auction("AUC-HIST-1", "MacBook Pro", seller.getId(), 20_000_000, 1_000_000);
        auc1.startAuction();
        manager.addAuction(auc1);

        Auction auc2 = new Auction("AUC-HIST-2", "iPad Pro", seller.getId(), 10_000_000, 500_000);
        auc2.startAuction();
        manager.addAuction(auc2);

        manager.placeBid("AUC-HIST-1", bob.getId(), "password123", 21_000_000);
        manager.placeBid("AUC-HIST-1", charlie.getId(), "password123", 22_000_000);
        manager.placeBid("AUC-HIST-2", bob.getId(), "password123", 10_500_000);

        // 1. Kiểm tra toàn bộ giao dịch trong hệ thống
        List<BidTransaction> allTxs = manager.getAllTransactions();
        assertEquals(3, allTxs.size());

        // 2. Lọc theo phiên
        List<BidTransaction> auc1Txs = manager.getTransactionsByAuction("AUC-HIST-1");
        assertEquals(2, auc1Txs.size());

        List<BidTransaction> auc2Txs = manager.getTransactionsByAuction("AUC-HIST-2");
        assertEquals(1, auc2Txs.size());

        // 3. Lọc theo người đặt giá
        List<BidTransaction> bobTxs = manager.getTransactionsByBidder("BOB-HIST");
        assertEquals(2, bobTxs.size());

        List<BidTransaction> charlieTxs = manager.getTransactionsByBidder("CHARLIE-HIST");
        assertEquals(1, charlieTxs.size());
    }

    @Test
    void testExportTransactionsToCsv() throws IOException {
        Seller seller = new Seller("S-CSV", "Seller", "password123");
        Bidder bidder = new Bidder("B-CSV", "Bidder", "password123");
        manager.registerUser(seller);
        manager.registerUser(bidder);

        Auction auc = new Auction("AUC-CSV", "Laptop", seller.getId(), 15_000_000, 500_000);
        auc.startAuction();
        manager.addAuction(auc);

        manager.placeBid("AUC-CSV", bidder.getId(), "password123", 16_000_000);

        File allCsv = tempDir.resolve("all_transactions.csv").toFile();
        manager.exportTransactionsToCsv(allCsv);
        assertTrue(allCsv.exists());

        List<String> lines = Files.readAllLines(allCsv.toPath());
        assertTrue(lines.size() >= 2);
        assertTrue(lines.get(0).contains("TransactionID,AuctionID,BidderID,Amount,Timestamp"));
        assertTrue(lines.get(1).contains("AUC-CSV"));
        assertTrue(lines.get(1).contains("B-CSV"));
        assertTrue(lines.get(1).contains("16000000.00"));

        File singleAucCsv = tempDir.resolve("single_auc.csv").toFile();
        manager.exportAuctionTransactionsToCsv("AUC-CSV", singleAucCsv);
        assertTrue(singleAucCsv.exists());
    }

    @Test
    void testTransactionHistoryPreservedInSnapshotAcrossRestart() {
        Seller seller = new Seller("S-SNAP", "Seller", "password123");
        Bidder bidder = new Bidder("B-SNAP", "Bidder", "password123");
        manager.registerUser(seller);
        manager.registerUser(bidder);

        Auction auc = new Auction("AUC-SNAP", "Camera", seller.getId(), 5_000_000, 200_000);
        auc.startAuction();
        manager.addAuction(auc);

        manager.placeBid("AUC-SNAP", bidder.getId(), "password123", 5_500_000);

        manager.saveState(storageFile);
        assertTrue(storageFile.exists());

        manager.clear();
        assertEquals(0, manager.getAllTransactions().size());

        boolean restored = manager.loadState(storageFile);
        assertTrue(restored);

        List<BidTransaction> restoredTxs = manager.getAllTransactions();
        assertEquals(1, restoredTxs.size());
        assertEquals("AUC-SNAP", restoredTxs.get(0).getAuctionId());
        assertEquals("B-SNAP", restoredTxs.get(0).getBidderId());
        assertEquals(5_500_000.0, restoredTxs.get(0).getAmount());
    }
}

