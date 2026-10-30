package network;

import java.util.Locale;

/**
 * Quy định giao thức bản tin (Protocol) trao đổi giữa Client và Server qua Socket.
 * Định dạng text-based dạng dòng: [COMMAND] [ARGS...]
 * Toàn bộ số thực sử dụng Locale.US (dấu chấm thập phân) để tránh lỗi format trên môi trường đa ngôn ngữ.
 */
public final class AuctionProtocol {

    private AuctionProtocol() {
        // Prevent instantiation
    }

    // Client -> Server Commands
    public static final String CMD_LOGIN = "LOGIN";
    public static final String CMD_REGISTER = "REGISTER";
    public static final String CMD_LIST = "LIST";
    public static final String CMD_JOIN = "JOIN";
    public static final String CMD_LEAVE = "LEAVE";
    public static final String CMD_BID = "BID";
    public static final String CMD_START = "START";
    public static final String CMD_INFO = "INFO";
    public static final String CMD_QUIT = "QUIT";

    // Server -> Client Status Responses
    public static final String RES_SUCCESS = "SUCCESS";
    public static final String RES_ERROR = "ERROR";

    // Server -> Client Realtime Broadcast Notifications (Observer Pattern qua Socket)
    public static final String NOTIFY_BID_PREFIX = "NOTIFY BID";
    public static final String NOTIFY_CLOSED_PREFIX = "NOTIFY CLOSED";

    public static final String AUCTIONS_BEGIN = "AUCTIONS_BEGIN";
    public static final String AUCTIONS_END = "AUCTIONS_END";

    /**
     * Tạo bản tin broadcast nhảy giá realtime tới các Client đang xem phiên.
     */
    public static String formatBidNotification(String auctionId, double newPrice, String bidderId, String itemId) {
        return String.format(Locale.US, "%s %s %.2f %s %s", NOTIFY_BID_PREFIX, auctionId, newPrice, bidderId, itemId);
    }

    /**
     * Tạo bản tin broadcast đóng phiên đấu giá tới các Client.
     */
    public static String formatClosedNotification(String auctionId, double finalPrice, String winnerId) {
        return String.format(Locale.US, "%s %s %.2f %s", NOTIFY_CLOSED_PREFIX, auctionId, finalPrice, winnerId);
    }

    /**
     * Tạo bản tin phản hồi thành công.
     */
    public static String formatSuccess(String action, String details) {
        if (details == null || details.trim().isEmpty()) {
            return String.format("%s %s", RES_SUCCESS, action);
        }
        return String.format("%s %s %s", RES_SUCCESS, action, details);
    }

    /**
     * Tạo bản tin phản hồi lỗi.
     */
    public static String formatError(String errorMessage) {
        return String.format("%s %s", RES_ERROR, errorMessage);
    }
}
