package com.auction.ui;

import com.auction.session.ClientSession;
// Ai đang đăng nhập hiện tại
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import network.AuctionClient;

public class AuctionNotificationListener implements AuctionClient.AuctionEventListener {

  // Lưu mã những phiên mà người dùng đã nhận thông báo riêng cho Winner/Seller
  // (Set an toàn khi truy cập từ nhiều thread)
  private final Set<String> personalizedCloseNotification = ConcurrentHashMap.newKeySet();

  @Override
  public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
    // Không hiện Alert cho mỗi lượt đặt giá để tránh làm phiền người dùng.
    // Việc cập nhật giá trên giao diện do Controller phụ trách.
  }

  @Override
  public void onAuctionClosed(String auctionId, double finalPrice, String winnerId) {
    // Đã có thông báo riêng (Winner/Seller) thì bỏ Alert chung để không bị báo đôi
    if (auctionId != null && personalizedCloseNotification.remove(auctionId)) {
      return;
    }

    String winnerMessage;
    if (winnerId == null || winnerId.equalsIgnoreCase("NONE")) {
      winnerMessage = "Không có người thắng cuộc.";
    } else {
      winnerMessage = "Người thắng cuộc: " + winnerId;
    }

    NotificationManager.showInfo(
        "Phiên đấu giá "
            + auctionId
            + " đã kết thúc.\n"
            + "Giá cuối cùng: "
            + formatPrice(finalPrice)
            + "\n"
            + winnerMessage);
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
    } else if (message.startsWith("ERROR ")) {
      handleGeneralError(message.substring("ERROR ".length()).trim());
    }
  }

  private void handleBidSuccess(String message) {
    String[] parts = message.split("\\s+");

    if (parts.length < 4) {
      return;
    }

    String auctionId = parts[2];

    try {
      double amount = Double.parseDouble(parts[3].replace(',', '.'));

      NotificationManager.showSuccess(
          "Bạn đã đặt giá thành công!\n"
              + "Mã phiên: "
              + auctionId
              + "\n"
              + "Giá đặt: "
              + formatPrice(amount));

    } catch (NumberFormatException e) {
      NotificationManager.showSuccess("Bạn đã đặt giá thành công cho phiên " + auctionId + ".");
    }
  }

  @Override
  public void onAuctionPaid(String auctionId, double finalPrice, String winnerId) {
    NotificationManager.showSuccess(
        "Phiên đấu giá "
            + auctionId
            + " đã được thanh toán.\n"
            + "Người thắng: "
            + winnerId
            + "\n"
            + "Giá thanh toán: "
            + formatPrice(finalPrice));
  }

  @Override
  public void onDisconnected() {
    personalizedCloseNotification.clear();

    ClientSession session = ViewManager.getClientSession();
    boolean wasLoggedIn = session != null && session.getCurrentUserId() != null;

    if (!wasLoggedIn) {
      // Đang ở màn hình đăng nhập: LogInController tự hiển thị lỗi kết nối
      return;
    }

    NotificationManager.showError(
        "Mất kết nối với máy chủ.\n" + "Bạn sẽ được đưa về màn hình đăng nhập.");

    // Phiên làm việc đã chết: xóa thông tin tài khoản và quay về Login
    Platform.runLater(
        () -> {
          session.clearCurrentUser();
          ViewManager.clearCache();
          ViewManager.SwitchView("LogIn");
        });
  }

  private String formatPrice(double price) {
    NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));

    formatter.setMaximumFractionDigits(2);

    return formatter.format(price) + " VNĐ";
  }

  @Override
  public void onWinnerNotification(String auctionId, double finalPrice, String itemId) {
    personalizedCloseNotification.add(auctionId);
    NotificationManager.showSuccess(
        "Chúc mừng! Bạn đã thắng phiên đấu giá. \n"
            + "Sản phẩm: "
            + itemId
            + "\n"
            + "Mã phiên: "
            + auctionId
            + "\n"
            + "Giá cuối cùng: "
            + formatPrice(finalPrice));
  }

  @Override
  public void onSellerNotification(
      String auctionId, double finalPrice, String winnerId, String itemId) {
    personalizedCloseNotification.add(auctionId);
    String winnerMessage;
    if (winnerId == null || winnerId.equalsIgnoreCase("NONE")) {
      winnerMessage = "Phiên đấu giá không có người thắng cuộc.";
    } else {
      winnerMessage = "Người thắng cuộc: " + winnerId;
    }
    NotificationManager.showInfo(
        "Phiên đấu giá sản phẩm của bạn đã kết thúc.\n"
            + "Sản phẩm: "
            + itemId
            + "\n"
            + "Mã phiên: "
            + auctionId
            + "\n"
            + "Giá cuối cùng: "
            + formatPrice(finalPrice)
            + "\n"
            + winnerMessage);
  }

  private void handleBidError(String message) {
    String reason = message.substring("ERROR BID ".length()).trim();
    String userMessage = translateBidError(reason);
    System.err.println("[AuctionNotificationListener] BID error: " + reason);
    NotificationManager.showError("Đặt giá không thành công.\n" + userMessage);
  }

  /** Lỗi của các lệnh khác (START, CLOSE, PAY, JOIN...). */
  private void handleGeneralError(String reason) {
    String userMessage = translateGeneralError(reason);
    if (userMessage == null) {
      // Lỗi đăng nhập/đăng ký đã có màn hình tương ứng xử lý; lỗi lạ chỉ ghi log
      System.err.println("[AuctionNotificationListener] ERROR (không hiển thị): " + reason);
      return;
    }
    NotificationManager.showWarning(userMessage);
  }

  private String translateBidError(String reason) {
    String normalized = reason.toLowerCase(Locale.ROOT);

    if (normalized.startsWith("invalid bid amount") || normalized.contains("bid amount must be")) {
      return "Số tiền đặt giá không hợp lệ.";
    }

    if (normalized.contains("must login")) {
      return "Bạn cần đăng nhập trước khi đặt giá.";
    }

    if (normalized.contains("only bidders")) {
      return "Chỉ tài khoản người đấu giá mới được phép đặt giá.";
    }

    if (normalized.contains("not accepting bids") || normalized.contains("auction closed")) {
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

  /**
   * Dịch lỗi của các lệnh không phải BID.
   *
   * @return thông báo tiếng Việt, hoặc null nếu không nên hiển thị Alert
   */
  private String translateGeneralError(String reason) {
    String n = reason.toLowerCase(Locale.ROOT);

    // Lỗi đăng nhập/đăng ký: LogInController, RegisterController tự hiển thị
    if (n.contains("user credentials")
        || n.contains("during login")
        || n.startsWith("usage: login")
        || n.startsWith("usage: register")
        || n.contains("already registered")
        || n.contains("cannot be registered")
        || n.startsWith("invalid id")
        || n.startsWith("invalid name")
        || n.startsWith("invalid password")) {
      return null;
    }

    if (n.contains("can close this auction")) {
      return "Chỉ Người bán sở hữu phiên hoặc Quản trị viên mới được đóng phiên này.";
    }
    if (n.contains("can start this auction")) {
      return "Chỉ Người bán sở hữu phiên hoặc Quản trị viên mới được bắt đầu phiên này.";
    }
    if (n.contains("must login first to pay")) {
      return "Bạn cần đăng nhập trước khi thanh toán.";
    }
    if (n.contains("must login first")) {
      return "Bạn cần đăng nhập để thực hiện thao tác này.";
    }
    if (n.contains("only finished auctions can be paid")) {
      return "Chỉ phiên đã kết thúc mới có thể thanh toán.";
    }
    if (n.contains("only the winning bidder")) {
      return "Chỉ người thắng cuộc mới được thanh toán phiên này.";
    }
    if (n.contains("only an open auction can be started")) {
      return "Phiên này đã bắt đầu hoặc đã kết thúc, không thể bắt đầu lại.";
    }
    if (n.contains("only a running auction can be finished")) {
      return "Phiên này chưa chạy hoặc đã kết thúc, không thể đóng.";
    }
    if (n.contains("only a finished auction with a winner")) {
      return "Phiên chưa kết thúc hoặc không có người thắng, không thể thanh toán.";
    }
    if (n.contains("auction not found")) {
      return "Không tìm thấy phiên đấu giá.";
    }
    if (n.startsWith("usage:")) {
      return "Yêu cầu gửi lên máy chủ không đúng định dạng.";
    }

    // "Not joined in auction", "Unknown command"... chỉ ghi log
    return null;
  }
}
