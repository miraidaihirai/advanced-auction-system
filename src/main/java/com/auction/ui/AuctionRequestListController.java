package com.auction.ui;

import com.auction.session.ClientSession;
import com.auction.session.UserRole;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import network.AuctionClient;
import network.AuctionDetail;

/**
 * Controller cho màn hình danh sách yêu cầu đấu giá ("Yêu cầu đấu giá"). Đồng bộ danh sách yêu cầu
 * PENDING_APPROVAL từ Server qua Socket và cho phép ADMIN xác nhận hoặc ADMIN/SELLER hủy.
 */
public class AuctionRequestListController
    implements SessionAware, AuctionClient.AuctionEventListener {

  @FXML private Label lblSectionTitle;
  @FXML private FlowPane requestCardContainer;
  @FXML private Label lblEmptyHint;

  private ClientSession clientSession;
  private UserRole currentRole = UserRole.UNKNOWN;

  @Override
  public void setClientSession(ClientSession session) {
    if (this.clientSession != null && this.clientSession != session) {
      this.clientSession.getClient().removeListener(this);
    }
    this.clientSession = session;
    if (session != null) {
      this.currentRole = session.getCurrentRole();
      session.getClient().addListener(this);
    }
    refreshList();
  }

  @Override
  public void dispose() {
    if (this.clientSession != null) {
      this.clientSession.getClient().removeListener(this);
    }
  }

  @FXML
  public void initialize() {
    refreshList();
  }

  /** Làm mới danh sách yêu cầu từ AuctionServer. */
  @FXML
  public void refreshList() {
    if (clientSession != null && clientSession.isConnected()) {
      clientSession.getClient().requestFullList();
    }
  }

  @Override
  public void onBidUpdate(String auctionId, double newPrice, String bidderId, String itemId) {}

  @Override
  public void onAuctionDetailsReceived(List<AuctionDetail> auctions) {
    List<AuctionDetail> pending = new ArrayList<>();
    if (auctions != null) {
      for (AuctionDetail d : auctions) {
        if (d.isPendingApproval()) {
          pending.add(d);
        }
      }
    }
    Platform.runLater(() -> renderRequests(pending));
  }

  @Override
  public void onCatalogRefresh() {
    refreshList();
  }

  private void renderRequests(List<AuctionDetail> requests) {
    if (requestCardContainer == null) return;
    requestCardContainer.getChildren().clear();

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

    for (AuctionDetail req : requests) {
      addRequestCard(req);
    }
  }

  private void addRequestCard(AuctionDetail detail) {
    try {
      FXMLLoader loader =
          new FXMLLoader(getClass().getResource("/com/auction/fxml/RequestCard.fxml"));
      VBox card = loader.load();

      RequestCardController cardCtrl = loader.getController();
      cardCtrl.setDetailData(detail, this, clientSession, currentRole);

      requestCardContainer.getChildren().add(card);
    } catch (Exception e) {
      System.err.println("[AuctionRequestListController] Lỗi tải RequestCard: " + e.getMessage());
    }
  }

  @FXML
  public void handleBackToHome() {
    ViewManager.SwitchView("Home");
  }
}
