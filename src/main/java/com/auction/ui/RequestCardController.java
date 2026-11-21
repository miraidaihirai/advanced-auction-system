package com.auction.ui;

import com.auction.model.AuctionRequest;
import com.auction.session.ClientSession;
import com.auction.session.UserRole;
import com.auction.storage.AuctionRequestStorage;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import network.AuctionDetail;

/**
 * Controller cho thẻ yêu cầu đấu giá trong màn hình danh sách. Hiển thị thông tin yêu cầu và 2 nút
 * "Xác nhận" / "Hủy".
 */
public class RequestCardController {

  // -------------------------------------------------------------------------
  // FXML bindings
  // -------------------------------------------------------------------------

  @FXML private Label lblProductName;
  @FXML private Label lblCategory;
  @FXML private Label lblStartPrice;
  @FXML private Label lblStepPrice;
  @FXML private Label lblCreator;
  @FXML private Label lblCreatedAt;
  @FXML private Label lblDescription;
  @FXML private Label lblStatus;
  @FXML private Button btnApprove;
  @FXML private Button btnCancel;
  @FXML private HBox actionRow;

  // -------------------------------------------------------------------------
  // State
  // -------------------------------------------------------------------------

  private AuctionRequest request;
  private AuctionDetail auctionDetail;
  private ClientSession clientSession;
  private AuctionRequestListController listController;
  private UserRole userRole = UserRole.UNKNOWN;

  private final NumberFormat currencyFormat =
      NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
  private final DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  public void setDetailData(
      AuctionDetail detail,
      AuctionRequestListController listCtrl,
      ClientSession session,
      UserRole role) {
    this.auctionDetail = detail;
    this.request = null;
    this.clientSession = session;
    this.listController = listCtrl;
    this.userRole = (role != null) ? role : UserRole.UNKNOWN;
    currencyFormat.setMaximumFractionDigits(0);

    if (lblProductName != null) lblProductName.setText(detail.itemName());
    if (lblCategory != null) lblCategory.setText("Danh mục: " + detail.category());
    if (lblStartPrice != null)
      lblStartPrice.setText("Giá khởi điểm: " + currencyFormat.format(detail.startPrice()) + " đ");
    if (lblStepPrice != null)
      lblStepPrice.setText("Bước giá: " + currencyFormat.format(detail.stepPrice()) + " đ");
    if (lblCreator != null)
      lblCreator.setText("Người gửi: " + detail.sellerName() + " (" + detail.sellerId() + ")");
    if (lblCreatedAt != null)
      lblCreatedAt.setText(
          "Thời lượng đề xuất: " + Math.max(1, detail.requestedDurationSec() / 60) + " phút");
    if (lblDescription != null) {
      String desc = detail.description();
      lblDescription.setText(desc != null && !desc.isEmpty() ? desc : "(Chưa có mô tả)");
    }
    if (lblStatus != null) lblStatus.setText("Trạng thái: Chờ duyệt");

    boolean isOwnerSeller =
        userRole == UserRole.SELLER
            && session != null
            && session.getCurrentUserId() != null
            && session.getCurrentUserId().equals(detail.sellerId());
    boolean canAct = (userRole == UserRole.ADMIN || isOwnerSeller);
    if (actionRow != null) {
      actionRow.setVisible(canAct);
      actionRow.setManaged(canAct);
    }

    boolean canApprove = (userRole == UserRole.ADMIN);
    if (btnApprove != null) {
      btnApprove.setVisible(canApprove);
      btnApprove.setManaged(canApprove);
      btnApprove.setDisable(!canApprove);
    }

    if (btnCancel != null) {
      btnCancel.setVisible(canAct);
      btnCancel.setManaged(canAct);
    }
  }

  /**
   * Điền dữ liệu vào thẻ.
   *
   * @param req yêu cầu đấu giá cần hiển thị
   * @param listCtrl controller danh sách — gọi refreshList() sau hành động
   * @param role vai trò người dùng hiện tại (kiểm soát hiển thị nút)
   */
  public void setData(AuctionRequest req, AuctionRequestListController listCtrl, UserRole role) {
    this.request = req;
    this.listController = listCtrl;
    this.userRole = (role != null) ? role : UserRole.UNKNOWN;
    currencyFormat.setMaximumFractionDigits(0);

    // Điền thông tin
    if (lblProductName != null) lblProductName.setText(req.getProductName());
    if (lblCategory != null) lblCategory.setText("Danh mục: " + req.getCategory());
    if (lblStartPrice != null)
      lblStartPrice.setText("Giá khởi điểm: " + currencyFormat.format(req.getStartPrice()) + " đ");
    if (lblStepPrice != null)
      lblStepPrice.setText("Bước giá: " + currencyFormat.format(req.getStepPrice()) + " đ");
    if (lblCreator != null)
      lblCreator.setText("Người gửi: " + req.getCreatorName() + " (" + req.getCreatorId() + ")");
    if (lblCreatedAt != null)
      lblCreatedAt.setText(
          "Gửi lúc: "
              + (req.getCreatedAt() != null ? req.getCreatedAt().format(dtFormatter) : "—"));
    if (lblDescription != null) {
      String desc = req.getDescription();
      lblDescription.setText(desc != null && !desc.isEmpty() ? desc : "(Chưa có mô tả)");
    }
    if (lblStatus != null) lblStatus.setText("Trạng thái: " + translateStatus(req.getStatus()));

    // --- Phân quyền hiển thị nút ---
    // actionRow (cả 2 nút) chỉ hiện khi ADMIN hoặc SELLER
    boolean canAct = (role == UserRole.ADMIN || role == UserRole.SELLER);
    if (actionRow != null) {
      actionRow.setVisible(canAct);
      actionRow.setManaged(canAct);
    }

    // Nút "Xác nhận" chỉ dành riêng cho ADMIN — Seller KHÔNG được tự duyệt yêu cầu
    boolean canApprove = (role == UserRole.ADMIN);
    if (btnApprove != null) {
      btnApprove.setVisible(canApprove);
      btnApprove.setManaged(canApprove);
      btnApprove.setDisable(!canApprove);
    }

    // Nút "Hủy" hiện cho cả ADMIN và SELLER (Seller có thể hủy yêu cầu của mình)
    boolean canCancel = (role == UserRole.ADMIN || role == UserRole.SELLER);
    if (btnCancel != null) {
      btnCancel.setVisible(canCancel);
      btnCancel.setManaged(canCancel);
    }
  }

  // -------------------------------------------------------------------------
  // FXML handlers
  // -------------------------------------------------------------------------

  /** Xác nhận yêu cầu: chuyển sang APPROVED / RUNNING. Chỉ ADMIN được phép. */
  @FXML
  private void handleApprove() {
    if (userRole != UserRole.ADMIN) {
      NotificationManager.showWarning(
          "Chỉ Quản trị viên mới có quyền xác nhận/duyệt yêu cầu đấu giá!");
      return;
    }
    if (auctionDetail != null) {
      if (clientSession == null || !clientSession.isConnected()) {
        NotificationManager.showError("Chưa kết nối tới máy chủ!");
        return;
      }
      clientSession.getClient().approveAuction(auctionDetail.auctionId());
      NotificationManager.showSuccess(
          "Đã duyệt yêu cầu \""
              + auctionDetail.itemName()
              + "\". Phòng đấu giá đã được mở trên sàn!");
      refreshParentList();
      return;
    }
    if (request == null) return;
    AuctionRequestStorage.getInstance().approveRequest(request.getRequestId());
    NotificationManager.showSuccess(
        "Đã xác nhận yêu cầu đấu giá \""
            + request.getProductName()
            + "\".\n"
            + "Phòng đấu giá sẽ được tạo theo quy trình hệ thống.");
    refreshParentList();
  }

  /** Hủy/từ chối yêu cầu. ADMIN và SELLER chủ phòng đều được phép. */
  @FXML
  private void handleCancel() {
    if (userRole != UserRole.ADMIN && userRole != UserRole.SELLER) {
      NotificationManager.showWarning("Chỉ Quản trị viên hoặc Người bán mới có quyền hủy yêu cầu!");
      return;
    }
    if (auctionDetail != null) {
      if (clientSession == null || !clientSession.isConnected()) {
        NotificationManager.showError("Chưa kết nối tới máy chủ!");
        return;
      }
      String reason =
          (userRole == UserRole.ADMIN) ? "Admin từ chối yêu cầu" : "Người bán tự hủy yêu cầu";
      clientSession.getClient().rejectAuction(auctionDetail.auctionId(), reason);
      NotificationManager.showInfo("Đã hủy yêu cầu đấu giá \"" + auctionDetail.itemName() + "\".");
      refreshParentList();
      return;
    }
    if (request == null) return;
    AuctionRequestStorage.getInstance().cancelRequest(request.getRequestId());
    NotificationManager.showInfo("Đã hủy yêu cầu đấu giá \"" + request.getProductName() + "\".");
    refreshParentList();
  }

  // -------------------------------------------------------------------------
  // Internal
  // -------------------------------------------------------------------------

  private void refreshParentList() {
    if (listController != null) {
      listController.refreshList();
    }
  }

  private String translateStatus(AuctionRequest.Status status) {
    if (status == null) return "Không rõ";
    return switch (status) {
      case PENDING -> "Chờ duyệt";
      case APPROVED -> "Đã xác nhận";
      case CANCELLED -> "Đã hủy";
    };
  }
}
