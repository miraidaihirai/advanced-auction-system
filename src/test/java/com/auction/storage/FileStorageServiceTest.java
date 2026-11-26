package com.auction.storage;

import com.auction.item.Art;
import com.auction.item.Electronics;
import com.auction.item.Item;
import com.auction.item.Vehicle;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new FileStorageService();
    }

    @Test
    void testSaveAndLoadUsers() throws Exception {
        File userFile = tempDir.resolve("users.ser").toFile();

        Seller seller = new Seller("SELLER-1", "Seller Alice", "pass123456");
        Bidder bidder = new Bidder("BIDDER-1", "Bidder Bob", "pass123456");
        List<User> originalUsers = Arrays.asList(seller, bidder);

        storageService.saveUsers(originalUsers, userFile);
        assertTrue(storageService.exists(userFile));

        List<User> loadedUsers = storageService.loadUsers(userFile);
        assertEquals(2, loadedUsers.size());

        User loadedSeller = loadedUsers.stream().filter(u -> u.getId().equals("SELLER-1")).findFirst().orElse(null);
        assertNotNull(loadedSeller);
        assertTrue(loadedSeller instanceof Seller);
        assertEquals("Seller Alice", loadedSeller.getName());
        assertEquals("pass123456", loadedSeller.getPassword());

        User loadedBidder = loadedUsers.stream().filter(u -> u.getId().equals("BIDDER-1")).findFirst().orElse(null);
        assertNotNull(loadedBidder);
        assertTrue(loadedBidder instanceof Bidder);
        assertEquals("Bidder Bob", loadedBidder.getName());
    }

    @Test
    void testSaveAndLoadAuctionsWithItemsAndBids() throws Exception {
        File auctionFile = tempDir.resolve("auctions.ser").toFile();

        Item laptop = new Electronics("Laptop", 101, 15_000_000, 1, "Dell", "XPS", 12);
        Auction auction = new Auction("AUC-100", laptop, "SELLER-1", 15_000_000, 500_000);
        auction.startAuction();

        BidTransaction bid = new BidTransaction("TX-1", "AUC-100", "BIDDER-1", 16_000_000);
        auction.placeBid(bid);

        storageService.saveAuctions(List.of(auction), auctionFile);
        assertTrue(storageService.exists(auctionFile));

        List<Auction> loadedList = storageService.loadAuctions(auctionFile);
        assertEquals(1, loadedList.size());

        Auction loadedAuction = loadedList.get(0);
        assertEquals("AUC-100", loadedAuction.getId());
        assertEquals("Laptop", loadedAuction.getItemId());
        assertEquals(16_000_000.0, loadedAuction.getCurrentPrice());
        assertEquals("BIDDER-1", loadedAuction.getHighestBidderId());
        assertEquals(Auction.Status.RUNNING, loadedAuction.getStatus());
        assertEquals(1, loadedAuction.getBidHistory().size());
        assertEquals("TX-1", loadedAuction.getBidHistory().get(0).getId());
    }

    @Test
    void testSaveAndLoadSnapshot() throws Exception {
        File snapshotFile = tempDir.resolve("nested/dir/snapshot.ser").toFile();

        Seller seller = new Seller("SELLER-2", "Seller Charlie", "pass123456");
        Bidder bidder = new Bidder("BIDDER-2", "Bidder Dave", "pass123456");
        Auction auction = new Auction("AUC-200", "Artwork", "SELLER-2", 5_000_000, 200_000);

        AuctionDataSnapshot snapshot = new AuctionDataSnapshot(List.of(seller, bidder), List.of(auction));
        storageService.saveSnapshot(snapshot, snapshotFile);

        assertTrue(storageService.exists(snapshotFile));

        AuctionDataSnapshot loadedSnapshot = storageService.loadSnapshot(snapshotFile);
        assertNotNull(loadedSnapshot);
        assertEquals(2, loadedSnapshot.getUsers().size());
        assertEquals(1, loadedSnapshot.getAuctions().size());
        assertTrue(loadedSnapshot.getSavedAt() > 0);
    }

    @Test
    void testLoadNonExistentFileThrowsException() {
        File nonExistent = tempDir.resolve("does_not_exist.ser").toFile();
        assertFalse(storageService.exists(nonExistent));
        assertThrows(IOException.class, () -> storageService.loadSnapshot(nonExistent));
    }

    @Test
    void testDeleteFile() throws IOException {
        File file = tempDir.resolve("to_delete.ser").toFile();
        storageService.saveUsers(List.of(new Bidder("B-1", "Bob", "pass123456")), file);
        assertTrue(storageService.exists(file));

        assertTrue(storageService.delete(file));
        assertFalse(storageService.exists(file));
        assertFalse(storageService.delete(file));
    }
}

