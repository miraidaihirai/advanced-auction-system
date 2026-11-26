package com.auction.ui;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.auction.session.ClientSession;

import network.AuctionClient;

/**
 * Bộ lắng nghe sự kiện realtime toàn cục cho BidX (Observer Pattern qua Socket).
 * Hiển thị Glassmorphism Toast mượt mà và đồng bộ lại số dư ví khi có thay đổi.
 */
public class AuctionNotificationListener implements AuctionClient.AuctionEventListener {

    private final Set<String> personalizedCloseNotification = ConcurrentHashMap.newKeySet();

    @Override
    public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
        // Cập nhật giá realtime đã được hiển thị sinh động ngay trong phòng & thẻ sản phẩm.
    }

    @Override
    public void onAuctionClosed(String auctionId, double finalPrice, String winnerId) {
        if (auctionId != null && personalizedCloseNotification.remove(auctionId)) {
            return;
        }
        String winnerMessage = (winnerId == null || winnerId.equalsIgnoreCase("NONE"))
                ? "Không có người thắng cuộc."
                : "Người thắng: " + winnerId;

        NotificationManager.showInfo(
                "Phiên " + auctionId + " đã kết thúc • "
                        + FormatUtils.formatVnd(finalPrice) + "\n" + winnerMessage);
        requestProfileAndCatalogRefresh();
    }

    @Override
    public void onWinnerNotification(String auctionId, double finalPrice, String itemId) {
        personalizedCloseNotification.add(auctionId);
        NotificationManager.showPayout(
                "CHÚC MỪNG BẠN ĐÃ THẮNG PHIÊN!",
                itemId + " (" + auctionId + ")\nGiá chốt: " + FormatUtils.formatVnd(finalPrice)
                        + " • Vui lòng kiểm tra ví và hoàn tất xác nhận thanh toán.");
        requestProfileAndCatalogRefresh();
    }

    @Override
    public void onSellerNotification(String auctionId, double finalPrice, String winnerId, String itemId) {
        personalizedCloseNotification.add(auctionId);
        if (winnerId == null || winnerId.equalsIgnoreCase("NONE")) {
            NotificationManager.showWarning(
                    "Phiên " + itemId + " (" + auctionId + ") đã đóng mà chưa có lượt đặt giá.");
        } else {
            NotificationManager.showPayout(
                    "PHIÊN ĐẤU GIÁ ĐÃ CHỐT NGƯỜI THẮNG!",
                    itemId + " (" + auctionId + ")\nGiá chốt: " + FormatUtils.formatVnd(finalPrice)
                            + " • Người thắng: " + winnerId);
        }
        requestProfileAndCatalogRefresh();
    }

    @Override
    public void onAuctionPaid(String auctionId, double finalPrice, String winnerId) {
        NotificationManager.showSuccess(
                "Phiên " + auctionId + " đã xác nhận thanh toán " + FormatUtils.formatVnd(finalPrice)
                        + " (Người thắng: " + winnerId + ").");
        requestProfileAndCatalogRefresh();
    }

    @Override
    public void onMessageReceived(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        String message = rawMessage.trim();
        if (message.startsWith("SUCCESS BID ")) {
            String[] parts = message.split("\\s+");
            if (parts.length >= 4) {
                try {
                    double amount = Double.parseDouble(parts[3].replace(',', '.'));
                    NotificationManager.showSuccess("Đặt giá thành công " + FormatUtils.formatVnd(amount)
                            + " cho phiên " + parts[2] + "!");
                } catch (NumberFormatException ignored) {
                    NotificationManager.showSuccess("Đặt giá thành công cho phiên " + parts[2] + "!");
                }
            }
        } else if (message.startsWith("ERROR BID ")) {
            String reason = message.substring("ERROR BID ".length()).trim();
            NotificationManager.showError(translateBidError(reason));
        } else if (message.startsWith("SUCCESS REQUEST_AUCTION ")) {
            String aucId = message.substring("SUCCESS REQUEST_AUCTION ".length()).trim();
            NotificationManager.showSuccess("Đã gửi yêu cầu mở phòng " + aucId
                    + "!\nVui lòng chờ Admin kiểm duyệt.");
        } else if (message.startsWith("SUCCESS APPROVE_AUCTION ")) {
            String aucId = message.substring("SUCCESS APPROVE_AUCTION ".length()).trim();
            NotificationManager.showPayout("ĐÃ DUYỆT & MỞ PHÒNG!",
                    "Phòng đấu giá " + aucId + " đã chính thức lên sàn LIVE.");
        } else if (message.startsWith("SUCCESS REJECT_AUCTION ")) {
            String aucId = message.substring("SUCCESS REJECT_AUCTION ".length()).trim();
            NotificationManager.showWarning("Đã từ chối yêu cầu mở phòng " + aucId + ".");
        } else if (message.startsWith("SUCCESS AUTOBID ")) {
            NotificationManager.showSuccess("Đã kích hoạt Đấu giá tự động (Auto-Bid) thành công!");
        } else if (message.startsWith("SUCCESS AUTOBID_OFF ")) {
            NotificationManager.showInfo("Đã tắt Đấu giá tự động.");
        } else if (message.startsWith("SUCCESS TOPUP ")) {
            String balStr = message.substring("SUCCESS TOPUP ".length()).trim();
            try {
                double bal = Double.parseDouble(balStr.replace(',', '.'));
                NotificationManager.showPayout("NẠP TIỀN THÀNH CÔNG!",
                        "Số dư ví BidX hiện tại: " + FormatUtils.formatVnd(bal));
            } catch (NumberFormatException ignored) {
                NotificationManager.showSuccess("Nạp tiền vào ví BidX thành công!");
            }
            requestProfileAndCatalogRefresh();
        } else if (message.startsWith("SUCCESS WITHDRAW ")) {
            String balStr = message.substring("SUCCESS WITHDRAW ".length()).trim();
            try {
                double bal = Double.parseDouble(balStr.replace(',', '.'));
                NotificationManager.showPayout("RÚT TIỀN THÀNH CÔNG!",
                        "Lệnh chuyển về ngân hàng đã hoàn tất.\nSố dư ví còn lại: " + FormatUtils.formatVnd(bal));
            } catch (NumberFormatException ignored) {
                NotificationManager.showSuccess("Rút tiền về ngân hàng thành công!");
            }
            requestProfileAndCatalogRefresh();
        } else if (message.startsWith("ERROR ") && !message.startsWith("ERROR BID ")) {
            String reason = message.substring("ERROR ".length()).trim();
            if (reason.contains("Số dư ví")
                    || reason.contains("Thanh toán thất bại")
                    || reason.toLowerCase(Locale.ROOT).contains("withdraw")
                    || reason.toLowerCase(Locale.ROOT).contains("top-up")) {
                NotificationManager.showError(reason);
                requestProfileAndCatalogRefresh();
            }
        }
    }

    @Override
    public void onDisconnected() {
        NotificationManager.showError("Mất kết nối tới máy chủ BidX. Vui lòng đăng nhập lại.");
    }

    private void requestProfileAndCatalogRefresh() {
        ClientSession session = ViewManager.getClientSession();
        if (session != null && session.isConnected() && session.getCurrentUser() != null) {
            try {
                session.getClient().requestProfile();
                session.getClient().requestFullList();
                session.getClient().requestWalletHistory();
            } catch (Exception ignored) {
            }
        }
    }

    private String translateBidError(String reason) {
        String normalized = reason.toLowerCase(Locale.ROOT);
        if (normalized.contains("only bidders")) {
            return "Chỉ tài khoản Bidder mới được phép đặt giá!";
        }
        if (normalized.contains("below the minimum required")) {
            return "Mức giá chưa đạt bước nhảy tối thiểu theo quy định.";
        }
        if (normalized.contains("not accepting bids") || normalized.contains("closed")) {
            return "Phiên đấu giá đã kết thúc hoặc chưa mở.";
        }
        if (normalized.contains("invalid bid amount")) {
            return "Số tiền đặt giá không hợp lệ.";
        }
        if (normalized.contains("must login")) {
            return "Bạn cần đăng nhập để đặt giá.";
        }
        return "Đặt giá thất bại: " + reason;
    }
}
