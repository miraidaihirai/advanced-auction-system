package com.auction.manager;

import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionManagerPersistenceTest {

    @TempDir
    Path tempDir;

    private File testStorageFile;
    private AuctionManager manager;

    @BeforeEach
    void setUp() {
        manager = AuctionManager.getInstance();
        manager.clear();
        testStorageFile = tempDir.resolve("test_auction_data.ser").toFile();
        manager.setStoragePath(testStorageFile.getAbsolutePath());
        manager.setAutoSave(false);
    }

    @AfterEach
    void tearDown() {
        manager.setAutoSave(false);
        manager.clear();
        if (testStorageFile != null && testStorageFile.exists()) {
            testStorageFile.delete();
        }
    }

    @Test
    void testSaveAndLoadStateManual() {
        Seller seller = new Seller("SELLER-PERSIST", "Persist Seller", "password123");
        Bidder bidder = new Bidder("BIDDER-PERSIST", "Persist Bidder", "password123");
        manager.registerUser(seller);
        manager.registerUser(bidder);

        Auction auction = new Auction("AUC-PERSIST", "Vintage Watch", seller.getId(), 5_000_000, 200_000);
        auction.startAuction();
        manager.addAuction(auction);

        manager.placeBid("AUC-PERSIST", bidder.getId(), "password123", 5_200_000);

        // Lưu trạng thái ra file
        manager.saveState(testStorageFile);
        assertTrue(testStorageFile.exists());

        // Xóa sạch bộ nhớ RAM
        manager.clear();
        assertNull(manager.getUser("SELLER-PERSIST"));
        assertNull(manager.getUser("BIDDER-PERSIST"));
        assertNull(manager.getAuction("AUC-PERSIST"));

        // Nạp lại trạng thái từ file
        boolean loaded = manager.loadState(testStorageFile);
        assertTrue(loaded);

        assertNotNull(manager.getUser("SELLER-PERSIST"));
        assertNotNull(manager.getUser("BIDDER-PERSIST"));
        Auction restored = manager.getAuction("AUC-PERSIST");
        assertNotNull(restored);
        assertEquals(5_200_000.0, restored.getCurrentPrice());
        assertEquals("BIDDER-PERSIST", restored.getHighestBidderId());
        assertEquals(1, restored.getBidHistory().size());
    }

    @Test
    void testAutoSaveOnUserRegistrationAndBid() {
        manager.setAutoSave(true);

        Seller seller = new Seller("AUTO-SELLER", "Auto Seller", "password123");
        manager.registerUser(seller);
        // File phải được tạo ngay lập tức do autoSave = true
        assertTrue(testStorageFile.exists());

        Bidder bidder = new Bidder("AUTO-BIDDER", "Auto Bidder", "password123");
        manager.registerUser(bidder);

        Auction auction = new Auction("AUTO-AUC", "Tesla Model 3", seller.getId(), 800_000_000, 10_000_000);
        auction.startAuction();
        manager.addAuction(auction);

        // Đặt giá
        boolean bidSuccess = manager.placeBid("AUTO-AUC", bidder.getId(), "password123", 810_000_000);
        assertTrue(bidSuccess);

        // Giả lập server crash: xóa sạch RAM và nạp lại từ disk
        manager.clear();
        assertEquals(0, manager.getAllUsers().size());
        assertEquals(0, manager.getAllAuctions().size());

        boolean restored = manager.loadState();
        assertTrue(restored);

        assertEquals(2, manager.getAllUsers().size());
        Auction restoredAuction = manager.getAuction("AUTO-AUC");
        assertNotNull(restoredAuction);
        assertEquals(810_000_000.0, restoredAuction.getCurrentPrice());
        assertEquals("AUTO-BIDDER", restoredAuction.getHighestBidderId());
    }
}
