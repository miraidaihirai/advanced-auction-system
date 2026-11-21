package com.auction.storage;

import com.auction.model.AuctionRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lưu trữ danh sách {@link AuctionRequest} trong bộ nhớ (in-memory).
 * Singleton — sử dụng AuctionRequestStorage.getInstance().
 *
 * <p>Trong phiên bản này dữ liệu được giữ trong RAM; mở rộng sau
 * bằng cách serialize vào file hoặc gửi lên server tương tự FileStorageService.</p>
 */
public class AuctionRequestStorage {

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static final AuctionRequestStorage INSTANCE = new AuctionRequestStorage();

    public static AuctionRequestStorage getInstance() { return INSTANCE; }

    private AuctionRequestStorage() {
        initSampleRequests();
    }

    private void initSampleRequests() {
        addRequest(new AuctionRequest(
                "SELLER-01",
                "Phạm Văn Trường",
                "Đồng hồ cổ Patek Philippe 18k Gold",
                "Đồng hồ cổ & Cao cấp",
                25_000_000.0,
                1_000_000.0,
                "Đồng hồ Thụy Sĩ nguyên bản 1975, chứng nhận kiểm định quốc tế, bảo hành 5 năm.",
                java.time.LocalDateTime.now().plusDays(2)
        ));
        addRequest(new AuctionRequest(
                "SELLER-02",
                "Lê Hồng",
                "Tranh sơn mài Phố Cổ Hà Nội 1985",
                "Nghệ thuật & Cổ vật",
                15_000_000.0,
                500_000.0,
                "Tác phẩm của họa sĩ Bùi Xuân Phái, sơn mài truyền thống trên gỗ quý, kèm giấy giám định.",
                java.time.LocalDateTime.now().plusDays(1)
        ));
    }

    // -------------------------------------------------------------------------
    // Storage
    // -------------------------------------------------------------------------

    /** Danh sách yêu cầu — thread-safe. */
    private final List<AuctionRequest> requests = new CopyOnWriteArrayList<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Thêm một yêu cầu mới vào danh sách.
     *
     * @param request yêu cầu không được null
     */
    public void addRequest(AuctionRequest request) {
        if (request == null) return;
        requests.add(request);
        System.out.printf("[AuctionRequestStorage] Added request: %s%n", request);
    }

    /**
     * Trả về toàn bộ danh sách yêu cầu (snapshot bất biến).
     */
    public List<AuctionRequest> getAllRequests() {
        return Collections.unmodifiableList(new ArrayList<>(requests));
    }

    /**
     * Trả về danh sách yêu cầu đang chờ duyệt (PENDING).
     */
    public List<AuctionRequest> getPendingRequests() {
        List<AuctionRequest> result = new ArrayList<>();
        for (AuctionRequest r : requests) {
            if (r.getStatus() == AuctionRequest.Status.PENDING) {
                result.add(r);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Lấy yêu cầu theo requestId.
     *
     * @return AuctionRequest tương ứng hoặc null nếu không tìm thấy
     */
    public AuctionRequest getById(String requestId) {
        if (requestId == null) return null;
        for (AuctionRequest r : requests) {
            if (requestId.equals(r.getRequestId())) return r;
        }
        return null;
    }

    /**
     * Xác nhận (APPROVE) yêu cầu có requestId cho trước.
     *
     * @return true nếu tìm thấy và chuyển trạng thái thành công
     */
    public boolean approveRequest(String requestId) {
        AuctionRequest r = getById(requestId);
        if (r != null) {
            r.approve();
            System.out.printf("[AuctionRequestStorage] Approved: %s%n", requestId);
            return true;
        }
        return false;
    }

    /**
     * Hủy (CANCEL) yêu cầu có requestId cho trước.
     *
     * @return true nếu tìm thấy và chuyển trạng thái thành công
     */
    public boolean cancelRequest(String requestId) {
        AuctionRequest r = getById(requestId);
        if (r != null) {
            r.cancel();
            System.out.printf("[AuctionRequestStorage] Cancelled: %s%n", requestId);
            return true;
        }
        return false;
    }
}
