package com.auction.storage;

import com.auction.model.BidTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionStorageServiceTest {

    @TempDir
    Path tempDir;

    private TransactionStorageService service;

    @BeforeEach
    void setUp() {
        service = new TransactionStorageService();
    }

    @Test
    void testSaveAndLoadTransactionsBinary() throws Exception {
        File binaryFile = tempDir.resolve("transactions.ser").toFile();

        BidTransaction tx1 = new BidTransaction("TX-1", "AUC-01", "BIDDER-A", 10_000_000);
        BidTransaction tx2 = new BidTransaction("TX-2", "AUC-01", "BIDDER-B", 11_000_000);
        List<BidTransaction> original = Arrays.asList(tx1, tx2);

        service.saveTransactions(original, binaryFile);
        assertTrue(binaryFile.exists());

        List<BidTransaction> loaded = service.loadTransactions(binaryFile);
        assertEquals(2, loaded.size());
        assertEquals("TX-1", loaded.get(0).getId());
        assertEquals("AUC-01", loaded.get(0).getAuctionId());
        assertEquals("BIDDER-A", loaded.get(0).getBidderId());
        assertEquals(10_000_000.0, loaded.get(0).getAmount());
        assertNotNull(loaded.get(0).getTimestamp());

        assertEquals("TX-2", loaded.get(1).getId());
        assertEquals(11_000_000.0, loaded.get(1).getAmount());
    }

    @Test
    void testExportAndImportCsv() throws IOException {
        File csvFile = tempDir.resolve("nested/dir/transactions.csv").toFile();

        LocalDateTime fixedTime = LocalDateTime.of(2026, 11, 7, 10, 30, 0);
        BidTransaction tx1 = new BidTransaction("TX-101", "AUC-01", "BIDDER-01", 15_500_000.50);
        tx1.setTimestamp(fixedTime);

        BidTransaction tx2 = new BidTransaction("TX-102", "AUC-02", "BIDDER-02", 25_000_000.00);
        tx2.setTimestamp(fixedTime.plusMinutes(5));

        service.exportToCsv(Arrays.asList(tx1, tx2), csvFile);
        assertTrue(csvFile.exists());

        List<BidTransaction> imported = service.importFromCsv(csvFile);
        assertEquals(2, imported.size());

        BidTransaction imp1 = imported.get(0);
        assertEquals("TX-101", imp1.getId());
        assertEquals("AUC-01", imp1.getAuctionId());
        assertEquals("BIDDER-01", imp1.getBidderId());
        assertEquals(15_500_000.50, imp1.getAmount());
        assertEquals(fixedTime, imp1.getTimestamp());

        BidTransaction imp2 = imported.get(1);
        assertEquals("TX-102", imp2.getId());
        assertEquals(25_000_000.00, imp2.getAmount());
        assertEquals(fixedTime.plusMinutes(5), imp2.getTimestamp());
    }

    @Test
    void testFilterByAuctionAndBidder() {
        BidTransaction tx1 = new BidTransaction("TX-1", "AUC-01", "BIDDER-A", 10_000_000);
        BidTransaction tx2 = new BidTransaction("TX-2", "AUC-01", "BIDDER-B", 11_000_000);
        BidTransaction tx3 = new BidTransaction("TX-3", "AUC-02", "BIDDER-A", 20_000_000);
        List<BidTransaction> list = Arrays.asList(tx1, tx2, tx3);

        List<BidTransaction> auc1 = service.filterByAuction(list, "AUC-01");
        assertEquals(2, auc1.size());

        List<BidTransaction> bidderA = service.filterByBidder(list, "BIDDER-A");
        assertEquals(2, bidderA.size());

        List<BidTransaction> emptyFilter = service.filterByAuction(list, "NON-EXISTENT");
        assertTrue(emptyFilter.isEmpty());
    }

    @Test
    void testTotalVolumeAndHighestTransaction() {
        BidTransaction tx1 = new BidTransaction("TX-1", "AUC-01", "BIDDER-A", 10_000_000);
        BidTransaction tx2 = new BidTransaction("TX-2", "AUC-01", "BIDDER-B", 25_000_000);
        BidTransaction tx3 = new BidTransaction("TX-3", "AUC-02", "BIDDER-A", 15_000_000);
        List<BidTransaction> list = Arrays.asList(tx1, tx2, tx3);

        double totalVolume = service.calculateTotalVolume(list);
        assertEquals(50_000_000.0, totalVolume);

        Optional<BidTransaction> highest = service.findHighestTransaction(list);
        assertTrue(highest.isPresent());
        assertEquals("TX-2", highest.get().getId());
        assertEquals(25_000_000.0, highest.get().getAmount());
    }

    @Test
    void testImportFromNonExistentFileThrowsException() {
        File missing = tempDir.resolve("missing.csv").toFile();
        assertThrows(IOException.class, () -> service.importFromCsv(missing));
    }

    @Test
    void testExportEmptyTransactionsCreatesHeaderOnly() throws IOException {
        File csvFile = tempDir.resolve("empty.csv").toFile();
        service.exportToCsv(Collections.emptyList(), csvFile);
        assertTrue(csvFile.exists());

        List<BidTransaction> imported = service.importFromCsv(csvFile);
        assertTrue(imported.isEmpty());
    }
}

