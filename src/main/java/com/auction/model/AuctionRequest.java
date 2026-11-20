package com.auction.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bản ghi yêu cầu đấu giá sản phẩm do Seller / Admin tạo.
 * Lưu ở trạng thái PENDING cho đến khi Admin xác nhận (APPROVED) hoặc hủy (CANCELLED).
 */
public class AuctionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    // -------------------------------------------------------------------------
    // Trạng thái yêu cầu
    // -------------------------------------------------------------------------

    public enum Status {
        PENDING,    // Chờ duyệt
        APPROVED,   // Đã xác nhận — phòng đấu giá sẽ được tạo
        CANCELLED   // Đã hủy
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final String requestId;    // UUID định danh yêu cầu
    private final String creatorId;    // ID người tạo yêu cầu (Seller / Admin)
    private final String creatorName;  // Tên người tạo
    private final String productName;  // Tên sản phẩm
    private final String category;     // Danh mục (Nghệ thuật / Điện tử / Xe cộ / Khác)
    private final double startPrice;   // Giá khởi điểm
    private final double stepPrice;    // Bước giá tối thiểu
    private final String description;  // Mô tả / thông số kỹ thuật
    private final LocalDateTime createdAt;  // Thời điểm tạo yêu cầu
    private LocalDateTime expectedStartAt;  // Thời gian dự kiến khai mạc (nullable)
    private volatile Status status;         // Trạng thái hiện tại

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public AuctionRequest(
            String creatorId,
            String creatorName,
            String productName,
            String category,
            double startPrice,
            double stepPrice,
            String description,
            LocalDateTime expectedStartAt) {

        this.requestId       = UUID.randomUUID().toString();
        this.creatorId       = creatorId;
        this.creatorName     = creatorName;
        this.productName     = (productName != null) ? productName.trim() : "";
        this.category        = (category != null) ? category.trim() : "Khác";
        this.startPrice      = Math.max(0, startPrice);
        this.stepPrice       = (stepPrice > 0) ? stepPrice : 500_000.0;
        this.description     = (description != null) ? description.trim() : "";
        this.createdAt       = LocalDateTime.now();
        this.expectedStartAt = expectedStartAt;
        this.status          = Status.PENDING;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getRequestId()       { return requestId; }
    public String getCreatorId()       { return creatorId; }
    public String getCreatorName()     { return creatorName; }
    public String getProductName()     { return productName; }
    public String getCategory()        { return category; }
    public double getStartPrice()      { return startPrice; }
    public double getStepPrice()       { return stepPrice; }
    public String getDescription()     { return description; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
    public LocalDateTime getExpectedStartAt() { return expectedStartAt; }
    public Status getStatus()          { return status; }

    // -------------------------------------------------------------------------
    // State transitions
    // -------------------------------------------------------------------------

    /** Chuyển trạng thái sang APPROVED. */
    public synchronized void approve() {
        if (status == Status.PENDING) {
            status = Status.APPROVED;
        }
    }

    /** Chuyển trạng thái sang CANCELLED. */
    public synchronized void cancel() {
        if (status == Status.PENDING) {
            status = Status.CANCELLED;
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format("[AuctionRequest id=%s product='%s' status=%s creator=%s]",
                requestId, productName, status, creatorId);
    }
}
