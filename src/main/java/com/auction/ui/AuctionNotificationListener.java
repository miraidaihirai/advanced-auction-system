package com.auction.ui;

import java.text.NumberFormat;
import java.util.Locale;

import network.AuctionClient;
import java.util.Set;
// Lưu các mã phiên có thông báo riêng
import java.util.concurrent.ConcurrentHashMap;

// Tạo một Set an toàn khi được truy cập từ nhiều thread
public class AuctionNotificationListener
        implements AuctionClient.AuctionEventListener {
    private final Set<String> personalizedCloseNotification = ConcurrentHashMap.newKeySet();

    // Lưu mã những phiên mà người dùng đã nhận thông báo dành riêng cho
    // Winner/Seller
    @Override
    public void onBidUpdate(
            String auctionId,
            double newPrice,
            String bidderId,
            String itemId) {
        // Không hiện Alert cho mỗi lượt đặt giá để tránh làm phiền người dùng.
        // Việc cập nhật giá trên giao diện sẽ do Controller phụ trách.
    }

    @Override
    public void onAuctionClosed(
            String auctionId,
            double finalPrice,
            String winnerId) {
        String winnerMessage;
        if (auctionId != null && personalizedCloseNotification.remove(auctionId)) {
            return;
        }
        // Nếu mã phiên tồn tại trong Set, xoá nó và không hiện Alert chung
        // Nếu không tồn tại, người dùng chỉ nhận thông báo kết thúc chung, nên Alert
        // vẫn hiển thị
        if (winnerId == null || winnerId.equalsIgnoreCase("NONE")) {
            winnerMessage = "Không có người thắng cuộc.";
        } else {
            winnerMessage = "Người thắng cuộc: " + winnerId;
        }

        NotificationManager.showInfo(
                "Phiên đấu giá " + auctionId
                        + " đã kết thúc.\n"
                        + "Giá cuối cùng: " + formatPrice(finalPrice)
                        + "\n" + winnerMessage);
    }

    @Override
    public void onMessageReceived(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }

        String message = rawMessage.trim();

        if (message.startsWith("SUCCESS BID ")) {
            handleBidSuccess(message);
        } else if (message.startsWith("ERROR BID ")) {
            handleBidError(message);
        }
    }

    private void handleBidSuccess(String message) {
        String[] parts = message.split("\\s+");

        if (parts.length < 4) {
            return;
        }

        String auctionId = parts[2];

        try {
            double amount = Double.parseDouble(
                    parts[3].replace(',', '.'));

            NotificationManager.showSuccess(
                    "Bạn đã đặt giá thành công!\n"
                            + "Mã phiên: " + auctionId + "\n"
                            + "Giá đặt: " + formatPrice(amount));

        } catch (NumberFormatException e) {
            NotificationManager.showSuccess(
                    "Bạn đã đặt giá thành công cho phiên "
                            + auctionId + ".");
        }
    }

    @Override
    public void onAuctionPaid(
            String auctionId,
            double finalPrice,
            String winnerId) {
        NotificationManager.showSuccess(
                "Phiên đấu giá " + auctionId
                        + " đã được thanh toán.\n"
                        + "Người thắng: " + winnerId + "\n"
                        + "Giá thanh toán: " + formatPrice(finalPrice));
    }

    @Override
    public void onDisconnected() {
        NotificationManager.showError(
                "Mất kết nối với máy chủ.\n"
                        + "Vui lòng kiểm tra kết nối mạng và đăng nhập lại.");
    }

    private String formatPrice(double price) {
        NumberFormat formatter = NumberFormat.getNumberInstance(
                Locale.forLanguageTag("vi-VN"));

        formatter.setMaximumFractionDigits(2);

        return formatter.format(price) + " VNĐ";
    }

    @Override
    public void onWinnerNotification(
            String auctionId,
            double finalPrice,
            String itemId) {
        personalizedCloseNotification.add(auctionId);
        NotificationManager.showSuccess(
                "Chúc mừng! Bạn đã thắng phiên đấu giá. \n"
                        + "Sản phẩm: " + itemId + "\n"
                        + "Mã phiên: " + auctionId + "\n"
                        + "Giá cuối cùng: " + formatPrice(finalPrice));
    }

    @Override
    public void onSellerNotification(
            String auctionId,
            double finalPrice,
            String winnerId,
            String itemId) {
        personalizedCloseNotification.add(auctionId);
        String winnerMessage;
        if (winnerId == null || winnerId.equalsIgnoreCase("NONE")) {
            winnerMessage = "Phiên đấu giá không có người thắng cuộc.";
        } else {
            winnerMessage = "Người thắng cuộc: " + winnerId;
        }
        NotificationManager.showInfo(
                "Phiên đấu giá sản phẩm của bạn đã kết thúc.\n"
                        + "Sản phẩm: " + itemId + "\n"
                        + "Mã phiên: " + auctionId + "\n"
                        + "Giá cuối cùng: " + formatPrice(finalPrice) + "\n"
                        + winnerMessage);
    }

    private void handleBidError(String message) {
        String reason = message.substring("ERROR BID ".length()).trim();
        String userMessage = translateBidError(reason);
        System.err.println("[AuctionNotificationListener] BID error: " + reason);
        NotificationManager.showError("Đặt giá không thành công.\n" + userMessage);
    }

    private String translateBidError(String reason) {
        String normalized = reason.toLowerCase(Locale.ROOT);

        if (normalized.startsWith("invalid bid amount")
                || normalized.contains("bid amount must be")) {
            return "Số tiền đặt giá không hợp lệ.";
        }

        if (normalized.contains("must login")) {
            return "Bạn cần đăng nhập trước khi đặt giá.";
        }

        if (normalized.contains("only bidders")) {
            return "Chỉ tài khoản người đấu giá mới được phép đặt giá.";
        }

        if (normalized.contains("not accepting bids")
                || normalized.contains("auction closed")) {
            return "Phiên đấu giá đã kết thúc hoặc không còn nhận giá.";
        }

        if (normalized.contains("below the minimum required")) {
            return "Giá đặt thấp hơn mức giá tối thiểu cho phép.";
        }

        if (normalized.contains("invalid bidder credentials")) {
            return "Thông tin tài khoản người đấu giá không hợp lệ.";
        }

        if (normalized.contains("auction not found")) {
            return "Không tìm thấy phiên đấu giá.";
        }

        if (normalized.contains("bid was not accepted")) {
            return "Máy chủ không chấp nhận mức giá này.";
        }

        if (normalized.startsWith("usage:")) {
            return "Thông tin đặt giá không đúng định dạng.";
        }

        return "Vui lòng kiểm tra mức giá và trạng thái phiên đấu giá.";
    }

}
// Observer Pattern
// Listener có thể đăng kí với Client và nhận được thông báo mỗi khi server gửi
// sự kiện tương ứng
// AuctionServer gửi thông báo qua Socket -> AuctionClient nhận và phân tích dữ
// liệu
// AuctionNotificationListener xác định sự kiện cần thông báo ->
// NotificationManager hiển thị JavaFX Alert

// onBidUpdate không hiện thông báo do trong một Auction bid có thể thay đổi rất
// nhiều, làm cho người dùng thấy khó chịu khi đóng tab
