package com.auction.ui;

import com.auction.model.BidTransaction;
import com.auction.session.ClientSession;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
import network.AuctionClient;
import network.AuctionSummary;

public class AuctionRoomController implements SessionAware, AuctionClient.AuctionEventListener {

  @FXML private Label lblRoomTitle;

  @FXML private Label lblCategoryTag;

  @FXML private Label lblItemName;

  @FXML private Label lblAuctionId;

  @FXML private ImageView imgShowcase;

  @FXML private Label lblSeller;

  @FXML private Label lblDescription;

  @FXML private Label lblStartPrice;

  @FXML private Label lblMinStep;

  @FXML private Label lblAuctionStatus;

  @FXML private LineChart<String, Number> priceChart;

  @FXML private CategoryAxis xAxis;

  @FXML private NumberAxis yAxis;

  @FXML private Label lblCountdownTimer;

  @FXML private Label lblCurrentPrice;

  @FXML private Label lblHighestBidder;

  @FXML private Label lblStepInfo;

  @FXML private ListView<String> lvBidHistory;

  @FXML private TextField txtBidAmount;

  @FXML private Button btnPlaceBid;

  @FXML private Label lblStatusMessage;

  private ClientSession clientSession;
  private String currentAuctionId = "";
  private double currentPrice = 0.0;
  private double stepPrice = 500_000.0;
  private String itemName = "";

  private XYChart.Series<String, Number> priceSeries;
  private Timeline countdownTimeline;
  private int remainingSeconds = 272; // ~04:32 countdown like in design

  private final NumberFormat currencyFormat =
      NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
  private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

  @FXML
  public void initialize() {
    currencyFormat.setMaximumFractionDigits(0);

    priceSeries = new XYChart.Series<>();
    priceSeries.setName("Giá đấu");
    if (priceChart != null) {
      priceChart.getData().clear();
      priceChart.getData().add(priceSeries);
      priceChart.setLegendVisible(false);
      priceChart.setAnimated(false);
    }

    startCountdownTimer();
  }

  private void startCountdownTimer() {
    if (countdownTimeline != null) {
      countdownTimeline.stop();
    }

    countdownTimeline =
        new Timeline(
            new KeyFrame(
                Duration.seconds(1),
                e -> {
                  if (remainingSeconds > 0) {
                    remainingSeconds--;
                    updateCountdownDisplay();
                  } else {
                    if (lblCountdownTimer != null) {
                      lblCountdownTimer.setText("00:00:00");
                    }
                  }
                }));
    countdownTimeline.setCycleCount(Timeline.INDEFINITE);
    countdownTimeline.play();
    updateCountdownDisplay();
  }

  private void updateCountdownDisplay() {
    if (lblCountdownTimer == null) {
      return;
    }
    int hours = remainingSeconds / 3600;
    int minutes = (remainingSeconds % 3600) / 60;
    int seconds = remainingSeconds % 60;
    lblCountdownTimer.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
  }

  @Override
  public void setClientSession(ClientSession session) {
    if (this.clientSession != null) {
      this.clientSession.getClient().removeListener(this);
    }
    this.clientSession = session;
    if (this.clientSession != null) {
      this.clientSession.getClient().addListener(this);
    }
    applyRoleMode();
  }

  private void applyRoleMode() {
    if (clientSession == null) return;
    Platform.runLater(
        () -> {
          if (clientSession.isSeller()) {
            if (txtBidAmount != null) txtBidAmount.setDisable(true);
            if (btnPlaceBid != null) {
              btnPlaceBid.setDisable(true);
              btnPlaceBid.setText("CHẾ ĐỘ NGƯỜI BÁN (CHỈ THEO DÕI)");
            }
            setStatus("Chế độ Người bán: Theo dõi diễn biến phiên đấu giá", true);
          } else if (clientSession.isAdmin()) {
            if (txtBidAmount != null) txtBidAmount.setDisable(true);
            if (btnPlaceBid != null) {
              btnPlaceBid.setDisable(true);
              btnPlaceBid.setText("CHẾ ĐỘ QUẢN TRỊ VIÊN (GIÁM SÁT)");
            }
            setStatus("Chế độ Quản trị viên: Giám sát toàn quyền phiên đấu giá", true);
          } else {
            if (txtBidAmount != null) txtBidAmount.setDisable(false);
            if (btnPlaceBid != null) {
              btnPlaceBid.setDisable(false);
              btnPlaceBid.setText("ĐẶT GIÁ NGAY");
            }
          }
        });
  }

  public void loadAuction(AuctionSummary auction) {
    if (auction == null) {
      return;
    }

    this.currentAuctionId = auction.auctionId();
    this.itemName = auction.itemId();
    this.currentPrice = auction.currentPrice();
    this.stepPrice = (auction.stepPrice() > 0) ? auction.stepPrice() : 500_000.0;
    this.remainingSeconds = 272;

    Platform.runLater(
        () -> {
          if (lblRoomTitle != null) {
            lblRoomTitle.setText("PHÒNG ĐẤU GIÁ: " + currentAuctionId + " - " + itemName);
          }
          if (lblItemName != null) {
            lblItemName.setText(itemName);
          }
          if (imgShowcase != null) {
            ProductImageService.loadIntoImageView(
                imgShowcase, currentAuctionId, itemName, 520, 220);
          }
          if (lblAuctionId != null) {
            lblAuctionId.setText("Mã phiên: " + currentAuctionId);
          }
          if (lblCategoryTag != null) {
            lblCategoryTag.setText(detectCategory(itemName));
          }
          if (lblSeller != null) {
            String sellerDisplay = "Người bán (Đã xác minh)";
            try {
              com.auction.model.Auction a =
                  com.auction.manager.AuctionManager.getInstance().getAuction(currentAuctionId);
              if (a != null && a.getSellerId() != null && !a.getSellerId().isBlank()) {
                sellerDisplay = a.getSellerId() + " (Đã xác minh)";
              }
            } catch (Exception ignored) {
            }
            lblSeller.setText(sellerDisplay);
          }
          if (lblDescription != null) {
            lblDescription.setText(
                "Sản phẩm đấu giá chính hãng, kèm giấy tờ kiểm định và bảo hành toàn diện.");
          }
          if (lblStartPrice != null) {
            lblStartPrice.setText(formatCurrency(currentPrice) + " đ");
          }
          if (lblMinStep != null) {
            lblMinStep.setText(formatCurrency(stepPrice) + " đ");
          }
          if (lblAuctionStatus != null) {
            lblAuctionStatus.setText(auction.status() != null ? auction.status() : "ACTIVE");
          }

          updatePriceDisplay(auction.highestBidderId());

          if (priceSeries != null) {
            priceSeries.getData().clear();
            String now = LocalDateTime.now().format(timeFormatter);
            priceSeries.getData().add(new XYChart.Data<>(now, currentPrice));
          }

          if (lvBidHistory != null) {
            lvBidHistory.getItems().clear();
            String leader =
                (auction.highestBidderId() != null
                        && !auction.highestBidderId().equalsIgnoreCase("NONE"))
                    ? auction.highestBidderId()
                    : "Chưa có";
            lvBidHistory
                .getItems()
                .add(
                    String.format(
                        "[%s] Khởi điểm: %s đ (%s)",
                        LocalDateTime.now().format(timeFormatter),
                        formatCurrency(currentPrice),
                        leader));
          }

          if (lblStatusMessage != null) {
            lblStatusMessage.setText("Đã tham gia phòng đấu giá " + currentAuctionId);
            lblStatusMessage.setStyle("-fx-text-fill: #10B981;");
          }

          setDefaultBidInput();
          applyRoleMode();
        });

    if (clientSession != null && clientSession.isConnected()) {
      clientSession.getClient().joinAuction(currentAuctionId);
      clientSession.getClient().requestHistory(currentAuctionId);
    }
  }

  private String detectCategory(String name) {
    if (name == null) {
      return "[ ĐỒNG HỒ CAO CẤP ]";
    }
    String lower = name.toLowerCase();
    if (lower.contains("đồng hồ") || lower.contains("watch")) {
      return "[ ĐỒNG HỒ CAO CẤP ]";
    } else if (lower.contains("dell")
        || lower.contains("laptop")
        || lower.contains("máy ảnh")
        || lower.contains("camera")) {
      return "[ ĐỒ ĐIỆN TỬ ]";
    } else if (lower.contains("vase")
        || lower.contains("bình")
        || lower.contains("tranh")
        || lower.contains("art")) {
      return "[ NGHỆ THUẬT & CỔ VẬT ]";
    } else if (lower.contains("xe") || lower.contains("car") || lower.contains("auto")) {
      return "[ XE CỘ & PHƯƠNG TIỆN ]";
    }
    return "[ SẢN PHẨM ĐẤU GIÁ ]";
  }

  private void updatePriceDisplay(String leader) {
    if (lblCurrentPrice != null) {
      lblCurrentPrice.setText(formatCurrency(currentPrice) + " đ");
    }
    if (lblHighestBidder != null) {
      String leaderName = (leader != null && !leader.equalsIgnoreCase("NONE")) ? leader : "Chưa có";
      lblHighestBidder.setText("Người giữ giá cao nhất: " + leaderName);
    }
    if (lblStepInfo != null) {
      lblStepInfo.setText("Bước giá tối thiểu: +" + formatCurrency(stepPrice) + " đ");
    }
  }

  private void setDefaultBidInput() {
    if (txtBidAmount != null) {
      txtBidAmount.setText(String.format(Locale.US, "%.0f", currentPrice + stepPrice));
    }
  }

  private String formatCurrency(double amount) {
    return currencyFormat.format(amount);
  }

  @FXML
  public void handleQuickBid500k() {
    applyBidIncrement(500_000.0);
  }

  @FXML
  public void handleQuickBid1M() {
    applyBidIncrement(1_000_000.0);
  }

  @FXML
  public void handleQuickBid2M() {
    applyBidIncrement(2_000_000.0);
  }

  @FXML
  public void handleQuickBid5M() {
    applyBidIncrement(5_000_000.0);
  }

  private void applyBidIncrement(double increment) {
    if (clientSession != null && (clientSession.isSeller() || clientSession.isAdmin())) {
      setStatus("Chỉ Người đặt giá (Bidder) mới có thể đặt giá hoặc tăng giá!", false);
      return;
    }
    if (txtBidAmount == null) {
      return;
    }
    double baseAmount;
    String currentInput = txtBidAmount.getText();

    if (currentInput == null || currentInput.trim().isEmpty()) {
      baseAmount = currentPrice;
    } else {
      try {
        baseAmount = Double.parseDouble(currentInput.trim());
      } catch (NumberFormatException e) {
        setStatus("Giá đặt hiện tại không hợp lệ", false);
        return;
      }
    }
    double newBid = baseAmount + increment;
    if (!Double.isFinite(newBid)) {
      setStatus("Giá đặt vượt quá giới hạn", false);
      return;
    }
    txtBidAmount.setText(String.format(Locale.US, "%.0f", newBid));
  }

  @FXML
  public void handlePlaceBid() {
    if (clientSession != null && (clientSession.isSeller() || clientSession.isAdmin())) {
      setStatus("Chỉ Người đặt giá (Bidder) mới có thể đặt giá trong phiên!", false);
      return;
    }

    if (clientSession == null || !clientSession.isConnected()) {
      setStatus("Chưa kết nối đến máy chủ!", false);
      return;
    }

    String bidText = txtBidAmount != null ? txtBidAmount.getText().trim() : "";
    if (bidText.isEmpty()) {
      setStatus("Vui lòng nhập số tiền muốn đặt!", false);
      return;
    }

    try {
      double amount = Double.parseDouble(bidText);
      if (amount <= currentPrice) {
        setStatus(
            "Giá đặt phải lớn hơn giá hiện tại (" + formatCurrency(currentPrice) + " đ)!", false);
        return;
      }

      clientSession.getClient().placeBid(currentAuctionId, amount);
      setStatus("Đang gửi yêu cầu đặt giá " + formatCurrency(amount) + " đ...", true);

    } catch (NumberFormatException e) {
      setStatus("Số tiền nhập vào không hợp lệ!", false);
    }
  }

  @FXML
  public void handleBackToHome() {
    if (clientSession != null && clientSession.isConnected() && !currentAuctionId.isEmpty()) {
      try {
        clientSession.getClient().leaveAuction(currentAuctionId);
      } catch (Exception ignored) {
      }
    }
    ViewManager.SwitchView("Home");
  }

  private void setStatus(String message, boolean isSuccess) {
    if (lblStatusMessage != null) {
      lblStatusMessage.setText(message);
      lblStatusMessage.setStyle(isSuccess ? "-fx-text-fill: #10B981;" : "-fx-text-fill: #EF4444;");
    }
  }

  @Override
  public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {
    if (!auctionId.equals(currentAuctionId)) {
      return;
    }

    Platform.runLater(
        () -> {
          this.currentPrice = newPrice;
          updatePriceDisplay(bidderId);
          setDefaultBidInput();

          String timeStr = LocalDateTime.now().format(timeFormatter);

          if (priceSeries != null) {
            priceSeries.getData().add(new XYChart.Data<>(timeStr, newPrice));
            if (priceSeries.getData().size() > 15) {
              priceSeries.getData().remove(0);
            }
          }

          if (lvBidHistory != null) {
            String historyEntry =
                String.format("[%s] %s: %s đ", timeStr, bidderId, formatCurrency(newPrice));
            lvBidHistory.getItems().add(0, historyEntry);
          }

          setStatus("CẬP NHẬT: " + bidderId + " vừa đặt " + formatCurrency(newPrice) + " đ!", true);
        });
  }

  @Override
  public void onHistoryReceived(String auctionId, List<BidTransaction> history) {
    if (!auctionId.equals(currentAuctionId) || history == null || history.isEmpty()) {
      return;
    }

    Platform.runLater(
        () -> {
          if (priceSeries != null) {
            priceSeries.getData().clear();
          }
          if (lvBidHistory != null) {
            lvBidHistory.getItems().clear();
          }

          for (BidTransaction tx : history) {
            String timeStr =
                tx.getTimestamp() != null ? tx.getTimestamp().format(timeFormatter) : "Lịch sử";
            if (priceSeries != null) {
              priceSeries.getData().add(new XYChart.Data<>(timeStr, tx.getAmount()));
            }
            if (lvBidHistory != null) {
              String historyEntry =
                  String.format(
                      "[%s] %s: %s đ", timeStr, tx.getBidderId(), formatCurrency(tx.getAmount()));
              lvBidHistory.getItems().add(0, historyEntry);
            }
          }

          BidTransaction latest = history.get(history.size() - 1);
          this.currentPrice = latest.getAmount();
          updatePriceDisplay(latest.getBidderId());
          setDefaultBidInput();
        });
  }

  @Override
  public void onMessageReceived(String rawMessage) {
    if (rawMessage.startsWith("SUCCESS BID ")) {
      Platform.runLater(() -> setStatus("Chúc mừng! Bạn đã đặt giá thành công!", true));
    } else if (rawMessage.startsWith("ERROR ")) {
      String err = rawMessage.substring("ERROR ".length());
      Platform.runLater(() -> setStatus("Đặt giá thất bại: " + err, false));
    }
  }
}
