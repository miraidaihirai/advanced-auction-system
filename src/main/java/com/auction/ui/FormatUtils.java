package com.auction.ui;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Tiện ích định dạng tiền tệ VNĐ, đếm ngược, danh mục và trạng thái phiên đấu giá BidX.
 */
public final class FormatUtils {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    private FormatUtils() {
    }

    public static String formatVnd(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.round(amount)) + " đ";
    }

    public static String formatNumber(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.round(amount));
    }

    public static String formatSignedVnd(double amount) {
        String sign = amount >= 0 ? "+" : "-";
        return sign + formatVnd(Math.abs(amount));
    }

    public static String formatCompactVnd(double amount) {
        double abs = Math.abs(amount);
        if (abs >= 1_000_000_000) {
            return String.format(Locale.US, "%.2f tỷ", abs / 1_000_000_000.0).replace(".00", "");
        }
        if (abs >= 1_000_000) {
            return String.format(Locale.US, "%.1fM đ", abs / 1_000_000.0).replace(".0M", "M");
        }
        if (abs >= 1_000) {
            return String.format(Locale.US, "%.0fk đ", abs / 1_000.0);
        }
        return formatVnd(abs);
    }

    public static String formatCountdownHMS(long totalSeconds) {
        long clamped = Math.max(0L, totalSeconds);
        long h = clamped / 3600;
        long m = (clamped % 3600) / 60;
        long s = clamped % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    public static String formatCountdownCompact(long totalSeconds) {
        long clamped = Math.max(0L, totalSeconds);
        long h = clamped / 3600;
        long m = (clamped % 3600) / 60;
        long s = clamped % 60;
        return String.format("%02dh %02dm %02ds", h, m, s);
    }

    public static String formatDurationLabel(long seconds) {
        if (seconds < 60) {
            return seconds + " giây";
        }
        long mins = seconds / 60;
        if (mins < 60) {
            return mins + " phút";
        }
        long hours = mins / 60;
        long rem = mins % 60;
        return rem > 0 ? hours + "h " + rem + "m" : hours + " giờ";
    }

    public static String formatTimestampShort(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return LocalDateTime.now().format(TIME_FMT);
        }
        try {
            return LocalDateTime.parse(isoTimestamp).format(DATE_TIME_FMT);
        } catch (Exception e) {
            return isoTimestamp.length() > 16 ? isoTimestamp.substring(0, 16) : isoTimestamp;
        }
    }

    public static String categoryLabel(String category) {
        if (category == null) {
            return "Khác";
        }
        return switch (category.toUpperCase(Locale.ROOT)) {
            case "WATCHES" -> "Đồng hồ cổ";
            case "ELECTRONICS" -> "Đồ điện tử";
            case "ART" -> "Nghệ thuật";
            case "VEHICLES" -> "Siêu Xe";
            case "OTHER", "COLLECTIBLE" -> "Khác";
            default -> "Khác";
        };
    }

    public static String detectCategory(String categoryField, String itemName) {
        if (categoryField != null && !categoryField.isBlank() && !"COLLECTIBLE".equalsIgnoreCase(categoryField)) {
            return categoryField.toUpperCase(Locale.ROOT);
        }
        String lower = itemName != null ? itemName.toLowerCase(Locale.ROOT) : "";
        if (lower.contains("đồng hồ") || lower.contains("dong ho") || lower.contains("watch")
                || lower.contains("patek") || lower.contains("rolex") || lower.contains("audemars") || lower.contains("royal oak")) {
            return "WATCHES";
        }
        if (lower.contains("dell") || lower.contains("laptop") || lower.contains("leica") || lower.contains("máy ảnh")
                || lower.contains("may anh") || lower.contains("camera") || lower.contains("macbook")
                || lower.contains("iphone") || lower.contains("tai nghe") || lower.contains("focal")) {
            return "ELECTRONICS";
        }
        if (lower.contains("bình") || lower.contains("binh") || lower.contains("vase") || lower.contains("tranh")
                || lower.contains("art") || lower.contains("sứ") || lower.contains("tượng") || lower.contains("piano")) {
            return "ART";
        }
        if (lower.contains("xe") || lower.contains("porsche") || lower.contains("lamborghini") || lower.contains("ferrari")
                || lower.contains("mercedes") || lower.contains("g63") || lower.contains("rolls")
                || lower.contains("car") || lower.contains("coupe") || lower.contains("tesla") || lower.contains("bmw")) {
            return "VEHICLES";
        }
        return "OTHER";
    }

    public static String statusLabel(String status) {
        if (status == null) {
            return "KHÔNG RÕ";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "RUNNING", "ACTIVE" -> "ĐANG DIỄN RA";
            case "OPEN" -> "CHỜ ADMIN DUYỆT";
            case "FINISHED" -> "ĐÃ KẾT THÚC";
            case "PAID" -> "HOÀN TẤT & ĐÃ NHẬN TIỀN";
            case "CANCELED" -> "ĐÃ HỦY / TỪ CHỐI";
            default -> status;
        };
    }

    public static String statusBadgeStyleClass(String status) {
        if (status == null) {
            return "badge-muted";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "RUNNING", "ACTIVE" -> "badge-live";
            case "OPEN" -> "badge-pending";
            case "FINISHED" -> "badge-finished";
            case "PAID" -> "badge-paid";
            case "CANCELED" -> "badge-rejected";
            default -> "badge-muted";
        };
    }

    public static String roleLabel(String role) {
        if (role == null) {
            return "USER";
        }
        return switch (role.toUpperCase(Locale.ROOT)) {
            case "BIDDER" -> "BIDDER • NGƯỜI ĐẤU GIÁ";
            case "SELLER" -> "SELLER • NGƯỜI BÁN";
            case "ADMIN" -> "ADMIN • KIỂM DUYỆT VIÊN";
            default -> role;
        };
    }
}
