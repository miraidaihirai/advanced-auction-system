package com.auction.storage;

import com.auction.model.BidTransaction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Module chuyên trách lưu trữ, quản lý và truy xuất lịch sử giao dịch đấu giá (Data Persistence).
 * Áp dụng nguyên tắc Single Responsibility (SRP): Tách biệt logic quản lý lịch sử giao dịch.
 * Hỗ trợ lưu trữ dạng nhị phân (Serialization) và định dạng bảng tính CSV (phục vụ đối soát, kiểm toán).
 */
public class TransactionStorageService {

    public static final String CSV_HEADER = "TransactionID,AuctionID,BidderID,Amount,Timestamp";

    private final FileStorageService fileStorageService;

    public TransactionStorageService() {
        this(new FileStorageService());
    }

    public TransactionStorageService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService != null ? fileStorageService : new FileStorageService();
    }

    /**
     * Lưu trữ danh sách giao dịch ra tệp tin nhị phân bằng Java Serialization.
     */
    public void saveTransactions(Collection<BidTransaction> transactions, File file) throws IOException {
        fileStorageService.saveTransactions(transactions, file);
    }

    /**
     * Nạp danh sách giao dịch từ tệp tin nhị phân.
     */
    public List<BidTransaction> loadTransactions(File file) throws IOException, ClassNotFoundException {
        return fileStorageService.loadTransactions(file);
    }

    /**
     * Xuất toàn bộ lịch sử giao dịch ra tệp tin CSV chuẩn UTF-8.
     * Số thực Amount được định dạng theo Locale.US (dấu chấm thập phân) để tương thích chuẩn CSV.
     */
    public void exportToCsv(Collection<BidTransaction> transactions, File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Target CSV file must not be null");
        }

        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(CSV_HEADER);
            writer.newLine();

            if (transactions != null) {
                for (BidTransaction tx : transactions) {
                    if (tx == null) {
                        continue;
                    }
                    String line = String.format(Locale.US, "%s,%s,%s,%.2f,%s",
                            escapeCsv(tx.getId()),
                            escapeCsv(tx.getAuctionId()),
                            escapeCsv(tx.getBidderId()),
                            tx.getAmount(),
                            tx.getTimestamp() != null ? tx.getTimestamp().toString() : "");
                    writer.write(line);
                    writer.newLine();
                }
            }
            writer.flush();
        }
    }

    /**
     * Đọc và nhập lại danh sách giao dịch từ tệp tin CSV.
     */
    public List<BidTransaction> importFromCsv(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("CSV file does not exist: " + (file != null ? file.getPath() : "null"));
        }

        List<BidTransaction> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // Bỏ qua header
            if (line == null) {
                return result;
            }

            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",", -1);
                if (parts.length < 5) {
                    System.err.printf("[TransactionStorageService] Skipping malformed line %d: %s%n", lineNum, line);
                    continue;
                }

                try {
                    String id = parts[0].trim();
                    String auctionId = parts[1].trim();
                    String bidderId = parts[2].trim();
                    double amount = Double.parseDouble(parts[3].trim());
                    LocalDateTime timestamp = LocalDateTime.parse(parts[4].trim());

                    BidTransaction tx = new BidTransaction(id, auctionId, bidderId, amount);
                    tx.setTimestamp(timestamp);
                    result.add(tx);
                } catch (NumberFormatException | DateTimeParseException e) {
                    System.err.printf("[TransactionStorageService] Error parsing line %d: %s (%s)%n",
                            lineNum, line, e.getMessage());
                }
            }
        }
        return result;
    }

    /**
     * Lọc danh sách giao dịch theo phiên đấu giá chỉ định.
     */
    public List<BidTransaction> filterByAuction(Collection<BidTransaction> transactions, String auctionId) {
        if (transactions == null || auctionId == null) {
            return Collections.emptyList();
        }
        return transactions.stream()
                .filter(tx -> tx != null && auctionId.equals(tx.getAuctionId()))
                .collect(Collectors.toList());
    }

    /**
     * Lọc danh sách giao dịch theo người đặt giá (Bidder) chỉ định.
     */
    public List<BidTransaction> filterByBidder(Collection<BidTransaction> transactions, String bidderId) {
        if (transactions == null || bidderId == null) {
            return Collections.emptyList();
        }
        return transactions.stream()
                .filter(tx -> tx != null && bidderId.equals(tx.getBidderId()))
                .collect(Collectors.toList());
    }

    /**
     * Tính tổng giá trị giao dịch (Tổng tiền đặt giá).
     */
    public double calculateTotalVolume(Collection<BidTransaction> transactions) {
        if (transactions == null) {
            return 0.0;
        }
        return transactions.stream()
                .filter(tx -> tx != null)
                .mapToDouble(BidTransaction::getAmount)
                .sum();
    }

    /**
     * Tìm giao dịch có mức giá đặt cao nhất trong tập dữ liệu.
     */
    public Optional<BidTransaction> findHighestTransaction(Collection<BidTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return Optional.empty();
        }
        return transactions.stream()
                .filter(tx -> tx != null)
                .max(Comparator.comparingDouble(BidTransaction::getAmount));
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

