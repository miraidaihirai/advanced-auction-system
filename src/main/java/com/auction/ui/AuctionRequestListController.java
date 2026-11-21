package com.auction.ui;

import com.auction.model.AuctionRequest;
import com.auction.session.ClientSession;
import com.auction.session.UserRole;
import com.auction.storage.AuctionRequestStorage;
import java.util.List;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * Controller cho màn hình danh sách yêu cầu đấu giá ("Yêu cầu đấu giá"). Hiển thị tất cả yêu cầu
 * (PENDING) và cho phép ADMIN/SELLER xác nhận hoặc hủy.
 *
 * <p>Layout tương đồng màn hình Home: có tiêu đề, FlowPane chứa các thẻ yêu cầu.
 */
public class AuctionRequestListController implements SessionAware {

  // -------------------------------------------------------------------------
  // FXML bindings
  // -------------------------------------------------------------------------

  @FXML private Label lblSectionTitle;
  @FXML private FlowPane requestCardContainer;
  @FXML private Label lblEmptyHint;

  // -------------------------------------------------------------------------
  // State
  // -------------------------------------------------------------------------

  private ClientSession clientSession;
  private UserRole currentRole = UserRole.UNKNOWN;

  // -------------------------------------------------------------------------
  // SessionAware
  // -------------------------------------------------------------------------

  @Override
  public void setClientSession(ClientSession session) {
    this.clientSession = session;
    if (session != null) {
      this.currentRole = session.getCurrentRole();
    }
    refreshList();
  }

  // -------------------------------------------------------------------------
  // FXML Initialize
  // -------------------------------------------------------------------------

  @FXML
  public void initialize() {
    refreshList();
  }

  // -------------------------------------------------------------------------
  // Public API (called from HomeController on navigate)
  // -------------------------------------------------------------------------

  /** Làm mới danh sách yêu cầu — gọi mỗi khi mở màn hình này. */
  public void refreshList() {
    if (requestCardContainer == null) return;
    requestCardContainer.getChildren().clear();

    List<AuctionRequest> requests = AuctionRequestStorage.getInstance().getPendingRequests();

    if (requests.isEmpty()) {
      if (lblEmptyHint != null) {
        lblEmptyHint.setVisible(true);
        lblEmptyHint.setManaged(true);
      }
      return;
    }

    if (lblEmptyHint != null) {
      lblEmptyHint.setVisible(false);
      lblEmptyHint.setManaged(false);
    }

    for (AuctionRequest req : requests) {
      addRequestCard(req);
    }
  }

  // -------------------------------------------------------------------------
  // Internal
  // -------------------------------------------------------------------------

  private void addRequestCard(AuctionRequest request) {
    try {
      FXMLLoader loader =
          new FXMLLoader(getClass().getResource("/com/auction/fxml/RequestCard.fxml"));
      VBox card = loader.load();

      RequestCardController cardCtrl = loader.getController();
      cardCtrl.setData(request, this, currentRole);

      requestCardContainer.getChildren().add(card);

    } catch (Exception e) {
      System.err.println("[AuctionRequestListController] Lỗi tải RequestCard: " + e.getMessage());
    }
  }

  // -------------------------------------------------------------------------
  // Navigation
  // -------------------------------------------------------------------------

  @FXML
  public void handleBackToHome() {
    ViewManager.SwitchView("Home");
  }
}
