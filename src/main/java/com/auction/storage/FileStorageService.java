package com.auction.storage;

import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.User;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Dịch vụ đọc / ghi dữ liệu đối tượng ra tệp tin sử dụng Java Serialization.
 * Áp dụng nguyên lý Single Responsibility (SRP).
 * Cơ chế ghi tệp nguyên tử (Atomic Write qua file tạm .tmp) chống hỏng tệp khi mất nguồn đột ngột.
 */
public class FileStorageService {

    /**
     * Lưu trữ toàn bộ snapshot (người dùng + phiên + lịch sử giao dịch) ra file.
     */
    public void saveSnapshot(AuctionDataSnapshot snapshot, File file) throws IOException {
        writeObjectSafely(snapshot, file);
    }

    /**
     * Tải snapshot (người dùng + phiên + lịch sử giao dịch) từ file.
     */
    public AuctionDataSnapshot loadSnapshot(File file) throws IOException, ClassNotFoundException {
        return readObjectSafely(file, AuctionDataSnapshot.class);
    }

    /**
     * Lưu trữ danh sách tài khoản người dùng riêng lẻ ra file.
     */
    public void saveUsers(Collection<User> users, File file) throws IOException {
        List<User> list = (users != null) ? new ArrayList<>(users) : new ArrayList<>();
        writeObjectSafely(list, file);
    }

    /**
     * Tải danh sách tài khoản người dùng từ file.
     */
    @SuppressWarnings("unchecked")
    public List<User> loadUsers(File file) throws IOException, ClassNotFoundException {
        return readObjectSafely(file, List.class);
    }

    /**
     * Lưu trữ danh sách phiên đấu giá riêng lẻ ra file.
     */
    public void saveAuctions(Collection<Auction> auctions, File file) throws IOException {
        List<Auction> list = (auctions != null) ? new ArrayList<>(auctions) : new ArrayList<>();
        writeObjectSafely(list, file);
    }

    /**
     * Tải danh sách phiên đấu giá từ file.
     */
    @SuppressWarnings("unchecked")
    public List<Auction> loadAuctions(File file) throws IOException, ClassNotFoundException {
        return readObjectSafely(file, List.class);
    }

    /**
     * Lưu trữ danh sách giao dịch đấu giá (BidTransaction) riêng lẻ ra file.
     */
    public void saveTransactions(Collection<BidTransaction> transactions, File file) throws IOException {
        List<BidTransaction> list = (transactions != null) ? new ArrayList<>(transactions) : new ArrayList<>();
        writeObjectSafely(list, file);
    }

    /**
     * Tải danh sách giao dịch đấu giá (BidTransaction) từ file.
     */
    @SuppressWarnings("unchecked")
    public List<BidTransaction> loadTransactions(File file) throws IOException, ClassNotFoundException {
        return readObjectSafely(file, List.class);
    }

    /**
     * Kiểm tra xem tệp dữ liệu có tồn tại và không rỗng hay không.
     */
    public boolean exists(File file) {
        return file != null && file.exists() && file.isFile() && file.length() > 0;
    }

    /**
     * Xóa tệp dữ liệu nếu cần dọn dẹp.
     */
    public boolean delete(File file) {
        if (file != null && file.exists()) {
            return file.delete();
        }
        return false;
    }

    /**
     * Ghi đối tượng ra file an toàn: Ghi ra file .tmp trước, sau đó di chuyển nguyên tử
     * để tránh làm rách file khi quá trình ghi bị ngắt đột ngột.
     */
    private void writeObjectSafely(Object object, File targetFile) throws IOException {
        if (targetFile == null) {
            throw new IllegalArgumentException("Target file must not be null");
        }

        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        File tempFile = new File(targetFile.getAbsolutePath() + ".tmp");
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(tempFile)))) {
            oos.writeObject(object);
            oos.flush();
        }

        Path source = tempFile.toPath();
        Path target = targetFile.toPath();
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Đọc đối tượng từ file với kiểm tra kiểu trả về an toàn.
     */
    private <T> T readObjectSafely(File file, Class<T> clazz) throws IOException, ClassNotFoundException {
        if (file == null || !file.exists()) {
            throw new IOException("File does not exist: " + (file != null ? file.getPath() : "null"));
        }

        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            Object obj = ois.readObject();
            if (clazz.isInstance(obj)) {
                return clazz.cast(obj);
            }
            throw new IOException(String.format("Expected object of type %s but found %s in %s",
                    clazz.getName(), obj != null ? obj.getClass().getName() : "null", file.getName()));
        }
    }
}
