package network;

import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.ChatMessage;
import com.auction.model.User;
import com.auction.model.WalletTransaction;

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
    public static final String CMD_PING = "PING";
    public static final String CMD_CLOSE = "CLOSE";
    public static final String CMD_PAY = "PAY";
    public static final String CMD_HISTORY = "HISTORY";

    // BidX Extended Commands (Role Workflow, Wallet, Auto-Bid, Rich Sync)
    public static final String CMD_PROFILE = "PROFILE";
    public static final String CMD_LIST_FULL = "LIST_FULL";
    public static final String CMD_REQUEST_AUCTION = "REQUEST_AUCTION";
    public static final String CMD_APPROVE_AUCTION = "APPROVE_AUCTION";
    public static final String CMD_REJECT_AUCTION = "REJECT_AUCTION";
    public static final String CMD_AUTOBID = "AUTOBID";
    public static final String CMD_AUTOBID_OFF = "AUTOBID_OFF";
    public static final String CMD_WALLET_HISTORY = "WALLET_HISTORY";
    public static final String CMD_TOPUP = "TOPUP";
    public static final String CMD_WITHDRAW = "WITHDRAW";
    public static final String CMD_LIST_USERS = "LIST_USERS";
    public static final String CMD_CHAT = "CHAT";
    public static final String CMD_CHAT_HISTORY = "CHAT_HISTORY";

    // Server -> Client Status Responses
    public static final String RES_SUCCESS = "SUCCESS";
    public static final String RES_ERROR = "ERROR";
    public static final String RES_PONG = "PONG";

    // Server -> Client Realtime Broadcast Notifications (Observer Pattern qua Socket)
    public static final String NOTIFY_BID_PREFIX = "NOTIFY BID";
    public static final String NOTIFY_CLOSED_PREFIX = "NOTIFY CLOSED";
    public static final String NOTIFY_WINNER_PREFIX = "NOTIFY WINNER";
    public static final String NOTIFY_SELLER_PREFIX = "NOTIFY SELLER";
    public static final String NOTIFY_PAID_PREFIX = "NOTIFY PAID";
    public static final String NOTIFY_CATALOG_REFRESH = "NOTIFY CATALOG_REFRESH";
    public static final String NOTIFY_CHAT_PREFIX = "NOTIFY CHAT ";

    public static final String AUCTIONS_BEGIN = "AUCTIONS_BEGIN";
    public static final String AUCTIONS_END = "AUCTIONS_END";

    public static final String AUCTIONS_FULL_BEGIN = "AUCTIONS_FULL_BEGIN";
    public static final String AUCTIONS_FULL_END = "AUCTIONS_FULL_END";

    public static final String HISTORY_BEGIN = "HISTORY_BEGIN";
    public static final String HISTORY_END = "HISTORY_END";

    public static final String WALLET_BEGIN = "WALLET_BEGIN";
    public static final String WALLET_END = "WALLET_END";

    public static final String USERS_BEGIN = "USERS_BEGIN";
    public static final String USERS_END = "USERS_END";

    public static final String CHAT_HISTORY_BEGIN = "CHAT_HISTORY_BEGIN";
    public static final String CHAT_HISTORY_END = "CHAT_HISTORY_END";

    public static final String PROFILE_DATA_PREFIX = "PROFILE_DATA ";
    public static final String AUCTION_FULL_PREFIX = "AUCTION_FULL ";
    public static final String WALLET_TX_PREFIX = "WALLET_TX ";
    public static final String USER_ROW_PREFIX = "USER_ROW ";
    public static final String CHAT_ROW_PREFIX = "CHAT_ROW ";

    public static String sanitizeField(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("|", "/").replace("\r", " ").replace("\n", " ").trim();
    }

    public static String formatProfileData(User user) {
        return String.format(Locale.US, "%s%s|%s|%.2f|%s",
                PROFILE_DATA_PREFIX,
                sanitizeField(user.getId()),
                sanitizeField(user.getRole()),
                user.getBalance(),
                sanitizeField(user.getName()));
    }

    public static String formatUserRow(User user) {
        return String.format(Locale.US, "%s%s|%s|%s|%.2f",
                USER_ROW_PREFIX,
                sanitizeField(user.getId()),
                sanitizeField(user.getName()),
                sanitizeField(user.getRole()),
                user.getBalance());
    }

    public static String formatAuctionFull(Auction a, String sellerName) {
        String winner = a.getHighestBidderId() != null ? a.getHighestBidderId() : "NONE";
        String safeSellerName = (sellerName != null && !sellerName.isBlank()) ? sellerName : a.getSellerId();
        return String.format(Locale.US,
                "%s%s|%s|%s|%s|%s|%.2f|%.2f|%.2f|%s|%s|%d|%d|%d|%s|%s|%s|%s",
                AUCTION_FULL_PREFIX,
                sanitizeField(a.getId()),
                sanitizeField(a.getItemId()),
                sanitizeField(a.getSellerId()),
                sanitizeField(safeSellerName),
                sanitizeField(a.getCategory()),
                a.getStartPrice(),
                a.getCurrentPrice(),
                a.getStepPrice(),
                sanitizeField(winner),
                a.getStatus().name(),
                a.getEndTimeMillis(),
                a.getRequestedDurationSeconds(),
                a.getBidHistory().size(),
                a.isPayoutSettled() ? "1" : "0",
                sanitizeField(a.getImageKey()),
                sanitizeField(a.getRejectionReason()),
                sanitizeField(a.getDescription()));
    }

    public static String formatWalletTransaction(WalletTransaction tx) {
        return String.format(Locale.US, "%s%s|%s|%s|%s|%s|%s|%.2f|%.2f|%s",
                WALLET_TX_PREFIX,
                sanitizeField(tx.getId()),
                sanitizeField(tx.getUserId()),
                sanitizeField(tx.getCounterpartyId()),
                sanitizeField(tx.getAuctionId()),
                sanitizeField(tx.getItemName()),
                tx.getType().name(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getTimestamp().toString());
    }

    public static String formatChatNotification(
            String auctionId,
            String senderId,
            String senderName,
            String role,
            String message) {
        return String.format("%s%s|%s|%s|%s|%s",
                NOTIFY_CHAT_PREFIX,
                sanitizeField(auctionId),
                sanitizeField(senderId),
                sanitizeField(senderName),
                sanitizeField(role),
                sanitizeField(message));
    }

    public static String formatChatHistoryRow(ChatMessage msg) {
        return String.format("%s%s|%s|%s|%s|%s|%s",
                CHAT_ROW_PREFIX,
                sanitizeField(msg.getAuctionId()),
                sanitizeField(msg.getSenderId()),
                sanitizeField(msg.getSenderName()),
                sanitizeField(msg.getRole()),
                msg.getTimestamp().toString(),
                sanitizeField(msg.getMessage()));
    }

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
     * Tạo bản tin thông báo riêng (Private Notification) cho Người thắng phiên (Winner).
     */
    public static String formatWinnerNotification(String auctionId, double finalPrice, String itemId) {
        return String.format(Locale.US, "%s %s %.2f %s", NOTIFY_WINNER_PREFIX, auctionId, finalPrice, itemId);
    }

    /**
     * Tạo bản tin thông báo riêng (Private Notification) cho Người bán (Seller) khi phiên kết thúc.
     */
    public static String formatSellerNotification(String auctionId, double finalPrice, String winnerId, String itemId) {
        return String.format(Locale.US, "%s %s %.2f %s %s", NOTIFY_SELLER_PREFIX, auctionId, finalPrice, winnerId, itemId);
    }

    /**
     * Tạo bản tin thông báo khi phiên đã được thanh toán (PAID).
     */
    public static String formatPaidNotification(String auctionId, double finalPrice, String winnerId) {
        return String.format(Locale.US, "%s %s %.2f %s", NOTIFY_PAID_PREFIX, auctionId, finalPrice, winnerId);
    }

    /**
     * Tạo bản tin định dạng một bản ghi lịch sử đặt giá.
     */
    public static String formatTransactionRecord(BidTransaction tx) {
        if (tx == null) {
            return "";
        }
        return String.format(Locale.US, "TX %s %s %s %.2f %s",
                tx.getId(), tx.getAuctionId(), tx.getBidderId(), tx.getAmount(), tx.getTimestamp());
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
