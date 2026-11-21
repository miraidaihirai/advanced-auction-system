package com.auction.ui;

import com.auction.item.Item;
import com.auction.session.ClientSession;
import com.auction.session.UserRole;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import network.AuctionClient;
import network.AuctionSummary;

/**
 * Controller cho màn hình chính (Home). Tích hợp: - Phân quyền hiển thị theo vai trò (BIDDER /
 * SELLER / ADMIN). - Nút "+ Tạo phòng đấu giá" chỉ hiện cho SELLER và ADMIN. - Khi SELLER / ADMIN
 * bấm "+ Tạo phòng đấu giá", điều hướng sang màn hình "Yêu cầu đấu giá". - Nút "Yêu cầu đấu giá"
 * trên thanh điều hướng sidebar. - Nút chuyển đổi Sáng / Tối (ThemeManager). - Giữ nguyên overlay
 * phòng đấu giá (createRoomSubView) và chi tiết sản phẩm (productDetailsView).
 */
public class HomeController implements SessionAware {

  // -------------------------------------------------------------------------
  // FXML fields — hiển thị danh sách phiên
  // -------------------------------------------------------------------------

  @FXML private FlowPane productGridContainer;
  @FXML private StackPane overlayPane;
  @FXML private Node createRoomSubView;
  @FXML private Node productDetailsView;

  @FXML private CreateRoomUIController createRoomSubViewController;
  @FXML private ProductDetailsController productDetailsViewController;

  @FXML private TextField searchField;
  @FXML private Label lblCurrentUser;
  @FXML private Label lblSectionTitle;

  // -------------------------------------------------------------------------
  // FXML fields — phân quyền & hành động trên header / sidebar
  // -------------------------------------------------------------------------

  /** Nút Tạo phòng đấu giá — chỉ hiện cho SELLER và ADMIN. */
  @FXML private Button btnCreateAuctionRoom;

  /** Nút Yêu cầu đấu giá trên sidebar. */
  @FXML private Button btnAuctionRequests;

  /** Nút chuyển đổi Sáng / Tối. */
  @FXML private Button btnToggleTheme;

  // -------------------------------------------------------------------------
  // State
  // -------------------------------------------------------------------------

  private ClientSession clientSession;
  private AuctionClient.AuctionEventListener auctionListListener;

  private final ObservableList<AuctionSummary> auctionSummaries =
      FXCollections.observableArrayList();
  private final List<VBox> serverAuctionCards = new ArrayList<>();
  private String currentCategoryFilter = "ALL";

  // -------------------------------------------------------------------------
  // SessionAware — nhận session và cập nhật UI theo role
  // -------------------------------------------------------------------------

  @Override
  public void setClientSession(ClientSession session) {
    if (session == null) return;

    // Lưu session hiện tại
    this.clientSession = session;

    // Luôn áp dụng phân quyền và cập nhật UI theo user hiện tại trong session
    applyRoleBasedVisibility(session.getCurrentRole(), session);

    // Truyền session xuống CreateRoomUIController để lấy creatorId/Name khi tạo AuctionRequest
    if (createRoomSubViewController != null) {
      createRoomSubViewController.setClientSession(session);
    }

    AuctionClient client = session.getClient();

    // Đảm bảo listener không bị add trùng lặp nhiều lần
    if (auctionListListener == null) {
      auctionListListener =
          new AuctionClient.AuctionEventListener() {
            @Override
            public void onBidUpdate(
                String auctionId, double newPrice, String bidderId, String itemId) {
              Platform.runLater(
                  () -> {
                    for (int i = 0; i < auctionSummaries.size(); i++) {
                      AuctionSummary current = auctionSummaries.get(i);
                      if (current.auctionId().equals(auctionId)) {
                        AuctionSummary updated =
                            new AuctionSummary(
                                current.auctionId(),
                                current.itemId(),
                                newPrice,
                                current.stepPrice(),
                                bidderId,
                                current.status());
                        auctionSummaries.set(i, updated);
                        renderFilteredCards();
                        break;
                      }
                    }
                  });
            }

            @Override
            public void onAuctionListReceived(List<AuctionSummary> auctions) {
              Platform.runLater(
                  () -> {
                    auctionSummaries.setAll(auctions);
                    renderFilteredCards();
                  });
            }
          };
      client.addListener(auctionListListener);
    }

    if (client.isConnected()) {
      client.requestList();
    }
  }

  // -------------------------------------------------------------------------
  // Initialize
  // -------------------------------------------------------------------------

  @FXML
  public void initialize() {
    // Nối controller con đã có từ trước (CreateRoom, ProductDetails)
    if (createRoomSubViewController != null) {
      createRoomSubViewController.setMainScreenController(this);
    }
    if (productDetailsViewController != null) {
      productDetailsViewController.setHomeController(this);
    }

    // Lắng nghe sự thay đổi ô tìm kiếm
    if (searchField != null) {
      searchField.textProperty().addListener((obs, oldVal, newVal) -> renderFilteredCards());
    }

    // Cập nhật label nút theme
    updateThemeButtonLabel();
  }

  // -------------------------------------------------------------------------
  // Role-based visibility
  // -------------------------------------------------------------------------

  /**
   * Áp dụng quy tắc hiển thị/ẩn theo vai trò người dùng: - SELLER hoặc ADMIN: Nút "+ Tạo phòng đấu
   * giá" hiển thị đầy đủ. - BIDDER: Nút "+ Tạo phòng đấu giá" ẩn hoàn toàn (visible=false,
   * managed=false).
   */
  private void applyRoleBasedVisibility(UserRole role, ClientSession session) {
    boolean canManage =
        (session != null && session.canManageAuction())
            || (role == UserRole.SELLER || role == UserRole.ADMIN);

    // Nút "+ Tạo phòng đấu giá" chỉ hiện cho SELLER / ADMIN; ẩn hoàn toàn với BIDDER
    if (btnCreateAuctionRoom != null) {
      btnCreateAuctionRoom.setVisible(canManage);
      btnCreateAuctionRoom.setManaged(canManage);
    }

    // Cập nhật label người dùng ở góc dưới sidebar
    if (lblCurrentUser != null && session != null) {
      String roleName =
          switch (role) {
            case SELLER -> "Người bán";
            case ADMIN -> "Quản trị viên";
            case BIDDER -> "Người đặt giá";
            default -> "Chưa xác định";
          };
      String displayName =
          session.getCurrentUserName() != null
              ? session.getCurrentUserName()
              : (session.getCurrentUserId() != null
                  ? session.getCurrentUserId()
                  : "Chưa đăng nhập");
      lblCurrentUser.setText("Tài khoản: " + displayName + " (" + roleName + ")");
    }
  }

  // -------------------------------------------------------------------------
  // Rendering & Filters
  // -------------------------------------------------------------------------

  private void renderFilteredCards() {
    if (productGridContainer == null) return;

    for (VBox card : serverAuctionCards) {
      productGridContainer.getChildren().remove(card);
    }
    serverAuctionCards.clear();

    String keyword =
        searchField != null && searchField.getText() != null
            ? searchField.getText().trim().toLowerCase()
            : "";

    for (AuctionSummary auction : auctionSummaries) {
      if (!matchesFilter(auction, keyword, currentCategoryFilter)) continue;

      VBox card = addProductCard.addServerAuctionCard(productGridContainer, auction);
      if (card != null) {
        serverAuctionCards.add(card);
      }
    }
  }

  private boolean matchesFilter(AuctionSummary auction, String keyword, String category) {
    String name = auction.itemId().toLowerCase();
    String id = auction.auctionId().toLowerCase();

    if (!keyword.isEmpty() && !name.contains(keyword) && !id.contains(keyword)) return false;

    if (category == null || category.equals("ALL")) return true;

    return switch (category) {
      case "ACTIVE" -> auction.status() != null && auction.status().equalsIgnoreCase("ACTIVE");
      case "WATCHES" -> name.contains("đồng hồ") || name.contains("watch");
      case "ELECTRONICS" ->
          name.contains("dell")
              || name.contains("laptop")
              || name.contains("máy ảnh")
              || name.contains("camera");
      case "ART" ->
          name.contains("bình")
              || name.contains("vase")
              || name.contains("tranh")
              || name.contains("art");
      case "VEHICLES" -> name.contains("xe") || name.contains("car") || name.contains("auto");
      default -> true;
    };
  }

  @FXML
  public void handleRefreshList() {
    if (clientSession != null && clientSession.isConnected()) {
      clientSession.getClient().requestList();
    }
  }

  @FXML
  public void handleFilterAll() {
    setFilter("ALL", "Tất Cả Phiên Đấu Giá");
  }

  @FXML
  public void handleFilterActive() {
    setFilter("ACTIVE", "Phiên Đang Diễn Ra (Live)");
  }

  @FXML
  public void handleFilterWatches() {
    setFilter("WATCHES", "Danh Mục: Đồng Hồ Cổ & Cao Cấp");
  }

  @FXML
  public void handleFilterElectronics() {
    setFilter("ELECTRONICS", "Danh Mục: Đồ Điện Tử & Công Nghệ");
  }

  @FXML
  public void handleFilterArt() {
    setFilter("ART", "Danh Mục: Nghệ Thuật & Cổ Vật");
  }

  @FXML
  public void handleFilterVehicles() {
    setFilter("VEHICLES", "Danh Mục: Xe Cộ & Phương Tiện");
  }

  private void setFilter(String category, String title) {
    currentCategoryFilter = category;
    updateSectionTitle(title);
    renderFilteredCards();
  }

  private void updateSectionTitle(String title) {
    if (lblSectionTitle != null) lblSectionTitle.setText(title);
  }

  // -------------------------------------------------------------------------
  // Navigation sidebar & header
  // -------------------------------------------------------------------------

  @FXML
  public void handleNavHome() {
    handleFilterAll();
  }

  @FXML
  public void handleNavExplore() {
    handleFilterActive();
  }

  @FXML
  public void handleNavMyAuctions() {
    updateSectionTitle("Các Phiên Bạn Đang Tham Gia");
    renderFilteredCards();
  }

  @FXML
  public void handleNavHistory() {
    updateSectionTitle("Lịch Sử Đấu Giá Của Hệ Thống");
    renderFilteredCards();
  }

  @FXML
  public void handleNavFavorites() {
    updateSectionTitle("Danh Sách Phiên Đấu Giá Yêu Thích");
    renderFilteredCards();
  }

  @FXML
  public void handleNavSettings() {
    updateSectionTitle("Cài Đặt Hệ Thống & Tài Khoản");
  }

  /** Mở màn hình danh sách "Yêu cầu đấu giá". */
  @FXML
  public void handleNavAuctionRequests() {
    ViewManager.SwitchView("AuctionRequestList");
    Object ctrl = ViewManager.getController("AuctionRequestList");
    if (ctrl instanceof AuctionRequestListController listCtrl) {
      listCtrl.refreshList();
    }
  }

  /**
   * Nút "Tạo phòng đấu giá" (chỉ Seller & Admin thấy): Mở overlay form CreateAuctionRoom để người
   * dùng nhập thông tin sản phẩm. Sau khi điền đầy đủ và bấm "Tạo phòng", CreateRoomUIController sẽ
   * lưu yêu cầu vào storage rồi điều hướng sang màn hình "Yêu cầu đấu giá".
   */
  @FXML
  public void handleCreateAuctionRoom() {
    if (clientSession != null && !clientSession.canManageAuction()) {
      NotificationManager.showWarning(
          "Chỉ Người bán và Quản trị viên mới có quyền tạo phòng đấu giá.");
      return;
    }
    // Mở overlay form tạo phòng (CreateAuctionRoom.fxml / createRoomSubView)
    showCreateRoomOverlay();
  }

  // -------------------------------------------------------------------------
  // Logout
  // -------------------------------------------------------------------------

  @FXML
  public void SwitchToLogin() throws IOException {
    if (clientSession != null && clientSession.isConnected()) {
      clientSession.disconnect();
    }
    if (clientSession != null) {
      clientSession.clearCurrentUser();
    }
    // Xóa sạch cache các view cũ để tài khoản tiếp theo có view mới hoàn toàn
    ViewManager.clearCache();
    ViewManager.SwitchView("LogIn");
  }

  // -------------------------------------------------------------------------
  // Overlay — Chi tiết sản phẩm & Tạo phòng nội bộ (giữ nguyên cho code cũ)
  // -------------------------------------------------------------------------

  public void showProductDetailsOverlay(Item product) {
    if (productDetailsViewController != null) {
      productDetailsViewController.initData(product);
    }
    showOverlay(productDetailsView);
  }

  public void showCreateRoomOverlay() {
    showOverlay(createRoomSubView);
  }

  private void showOverlay(Node targetView) {
    overlayPane.setVisible(true);
    overlayPane.setManaged(true);

    setNodeVisible(createRoomSubView, targetView == createRoomSubView);
    setNodeVisible(productDetailsView, targetView == productDetailsView);
  }

  private void setNodeVisible(Node node, boolean visible) {
    if (node != null) {
      node.setVisible(visible);
      node.setManaged(visible);
    }
  }

  @FXML
  public void hideOverLay() {
    overlayPane.setVisible(false);
    overlayPane.setManaged(false);
  }

  // -------------------------------------------------------------------------
  // Theme toggle
  // -------------------------------------------------------------------------

  @FXML
  public void handleToggleTheme() {
    ThemeManager.getInstance().toggleTheme();
    updateThemeButtonLabel();
  }

  private void updateThemeButtonLabel() {
    if (btnToggleTheme == null) return;
    boolean dark = ThemeManager.getInstance().isDarkMode();
    btnToggleTheme.setText(dark ? "☀ Chế độ sáng" : "🌙 Chế độ tối");
  }

  // -------------------------------------------------------------------------
  // Add auction card (giữ nguyên cho CreateRoomUIController / code cũ)
  // -------------------------------------------------------------------------

  @FXML
  public void addNewAuctionCard(Item product, LocalDateTime endTime) {
    addProductCard.addNewAuctionCard(productGridContainer, product, endTime, this);
  }
}
