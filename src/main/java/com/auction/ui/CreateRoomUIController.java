package com.auction.ui;

import com.auction.item.Item;
import com.auction.item.factory.ArtFactory;
import com.auction.item.factory.ElectronicsFactory;
import com.auction.item.factory.ItemFactory;
import com.auction.item.factory.VehicleFactory;
import com.auction.session.ClientSession;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Controller cho form "Tạo phòng đấu giá" (CreateAuctionRoom.fxml).
 *
 * <p>Luồng hoạt động:
 *
 * <ol>
 *   <li>Người dùng (Seller / Admin) bấm nút "+ Tạo phòng đấu giá" ở Home.
 *   <li>HomeController mở overlay CreateAuctionRoom (gọi showCreateRoomOverlay()).
 *   <li>Người dùng điền thông tin, bấm "Tạo phòng" → handleCreate() thực thi:
 *   <li>Tạo Item từ dữ liệu nhập, lưu vào Home card list (tương thích code cũ).
 *   <li>Đồng thời tạo AuctionRequest lưu vào AuctionRequestStorage (trạng thái PENDING).
 *   <li>Đóng overlay → điều hướng sang màn hình "Yêu cầu đấu giá" và làm mới danh sách.
 * </ol>
 */
public class CreateRoomUIController implements Initializable {
  @FXML private VBox createRoom;

  @FXML private ComboBox<String> selectItem;

  @FXML private Label lblImageFileName;

  @FXML private ImageView imgPreview;

  private File selectedImageFile;

  // Các trường chung
  @FXML private TextField txtProductName;

  @FXML private TextField txtStartPrice;

  @FXML private TextField txtId;

  @FXML private TextField txtQuantity;

  // Các VBox bọc trường riêng
  @FXML private VBox boxArtist;

  @FXML private TextField txtArtist;

  @FXML private VBox boxMedium;

  @FXML private TextField txtMedium;

  @FXML private VBox boxYearCreated;

  @FXML private TextField txtYearCreated;

  @FXML private VBox boxBrand;

  @FXML private TextField txtBrand;

  @FXML private VBox boxModel;

  @FXML private TextField txtModel;

  @FXML private VBox boxWarrantyPeriod;

  @FXML private TextField txtWarrantyPeriod;

  @FXML private VBox boxYear;

  @FXML private TextField txtYear;

  private Item item;
  private HomeController mainScreenController;

  /** Session người dùng hiện tại — để lấy creatorId/creatorName khi tạo AuctionRequest. */
  private ClientSession clientSession;

  public void setMainScreenController(HomeController mainScreenController) {
    this.mainScreenController = mainScreenController;
  }

  /** Nhận ClientSession từ HomeController để biết thông tin người tạo yêu cầu. */
  public void setClientSession(ClientSession session) {
    this.clientSession = session;
  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    selectItem.getItems().addAll("Đồ nghệ thuật", "Đồ điện", "Phương tiện giao thông");
    updateFieldsByCategory(null);

    selectItem.setOnAction(
        event -> {
          String selectValue = selectItem.getValue();
          updateFieldsByCategory(selectValue);
        });
  }

  @FXML
  private void handleChooseImage() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Chọn ảnh sản phẩm đấu giá");
    fileChooser
        .getExtensionFilters()
        .addAll(
            new FileChooser.ExtensionFilter(
                "Hình ảnh (*.png, *.jpg, *.jpeg, *.webp)",
                "*.png",
                "*.jpg",
                "*.jpeg",
                "*.webp",
                "*.PNG",
                "*.JPG",
                "*.JPEG"),
            new FileChooser.ExtensionFilter("Tất cả tệp", "*.*"));

    Window window =
        createRoom != null && createRoom.getScene() != null
            ? createRoom.getScene().getWindow()
            : null;
    File file = fileChooser.showOpenDialog(window);
    if (file != null) {
      selectedImageFile = file;
      if (lblImageFileName != null) {
        lblImageFileName.setText(file.getName());
      }
      try {
        Image img = new Image(file.toURI().toString(), 150, 85, true, true);
        if (imgPreview != null) {
          imgPreview.setImage(img);
        }
      } catch (Exception e) {
        System.err.println("Không thể nạp preview ảnh: " + e.getMessage());
      }
    }
  }

  private void setFieldVisible(VBox box, boolean visible) {
    if (box != null) {
      box.setVisible(visible);
      box.setManaged(visible);
    }
  }

  private void updateFieldsByCategory(String category) {
    if (category == null) {
      setFieldVisible(boxArtist, false);
      setFieldVisible(boxMedium, false);
      setFieldVisible(boxYearCreated, false);
      setFieldVisible(boxBrand, false);
      setFieldVisible(boxModel, false);
      setFieldVisible(boxWarrantyPeriod, false);
      setFieldVisible(boxYear, false);
      return;
    }

    boolean isArt = "Đồ nghệ thuật".equals(category);
    boolean isElectronics = "Đồ điện".equals(category);
    boolean isVehicle = "Phương tiện giao thông".equals(category);

    setFieldVisible(boxArtist, isArt);
    setFieldVisible(boxMedium, isArt);
    setFieldVisible(boxYearCreated, isArt);

    setFieldVisible(boxBrand, isElectronics || isVehicle);
    setFieldVisible(boxModel, isElectronics || isVehicle);

    setFieldVisible(boxWarrantyPeriod, isElectronics);
    setFieldVisible(boxYear, isVehicle);
  }

  private Item initializeItem(String selectValue) {
    if (selectValue == null) {
      NotificationManager.showWarning("Vui lòng chọn loại sản phẩm!");
      return null;
    }

    try {
      String name = txtProductName.getText() != null ? txtProductName.getText().trim() : "";
      if (name.isEmpty()) {
        NotificationManager.showWarning("Vui lòng nhập tên sản phẩm!");
        return null;
      }
      int id = Integer.parseInt(cleanNumeric(txtId.getText()));
      int price = Integer.parseInt(cleanNumeric(txtStartPrice.getText()));
      int quantity = Integer.parseInt(cleanNumeric(txtQuantity.getText()));
      if (price <= 0 || quantity <= 0) {
        NotificationManager.showWarning("Giá khởi điểm và số lượng phải lớn hơn 0!");
        return null;
      }

      if (selectValue.equals("Đồ nghệ thuật")) {
        String artist = txtArtist.getText() != null ? txtArtist.getText().trim() : "";
        String medium = txtMedium.getText() != null ? txtMedium.getText().trim() : "";
        int yearCreated = Integer.parseInt(cleanNumeric(txtYearCreated.getText()));
        ItemFactory factory =
            new ArtFactory(name, id, price, quantity, artist, medium, yearCreated);
        return factory.createItem();
      } else if (selectValue.equals("Đồ điện")) {
        String brand = txtBrand.getText() != null ? txtBrand.getText().trim() : "";
        String model = txtModel.getText() != null ? txtModel.getText().trim() : "";
        int warrantyPeriod = Integer.parseInt(cleanNumeric(txtWarrantyPeriod.getText()));
        ItemFactory factory =
            new ElectronicsFactory(name, id, price, quantity, brand, model, warrantyPeriod);
        return factory.createItem();
      } else if (selectValue.equals("Phương tiện giao thông")) {
        String make = txtBrand.getText() != null ? txtBrand.getText().trim() : "";
        String model = txtModel.getText() != null ? txtModel.getText().trim() : "";
        int year = Integer.parseInt(cleanNumeric(txtYear.getText()));
        ItemFactory factory = new VehicleFactory(name, id, price, quantity, make, model, year);
        return factory.createItem();
      }
    } catch (NumberFormatException e) {
      NotificationManager.showWarning(
          "Một hoặc nhiều trường số (ID, Giá khởi điểm, Số lượng, Năm, Bảo hành) chưa hợp lệ!");
    } catch (Exception e) {
      NotificationManager.showWarning("Lỗi khởi tạo sản phẩm: " + e.getMessage());
    }
    return null;
  }

  private static String cleanNumeric(String raw) {
    return raw == null ? "" : raw.trim().replaceAll("[,._\\s]", "");
  }

  /**
   * Xử lý nút "Tạo phòng":
   *
   * <ol>
   *   <li>Validate và tạo Item từ dữ liệu form.
   *   <li>Lưu ảnh nếu có.
   *   <li>Gửi lệnh REQUEST_AUCTION lên AuctionServer để Admin xét duyệt.
   *   <li>Đóng overlay CreateAuctionRoom và điều hướng sang màn hình "Yêu cầu đấu giá".
   * </ol>
   */
  @FXML
  private void handleCreate() {
    String selectValue = selectItem.getValue();
    item = initializeItem(selectValue);

    if (item == null) {
      return;
    }

    if (clientSession == null || !clientSession.isConnected()) {
      NotificationManager.showError("Chưa kết nối tới máy chủ để gửi yêu cầu tạo phòng!");
      return;
    }

    if (mainScreenController == null) {
      System.err.println(
          "[CreateRoomUIController] Không thể tạo phòng: mainScreenController là null.");
      return;
    }

    // 1. Lưu ảnh sản phẩm nếu người dùng đã chọn
    String imageKey = "";
    if (selectedImageFile != null) {
      String savedPath =
          ProductImageService.saveProductImage("ITEM-" + item.getId(), selectedImageFile);
      item.setImagePath(savedPath);
      ProductImageService.registerImage(item.getName(), savedPath);
      ProductImageService.registerImage(String.valueOf(item.getId()), savedPath);
      imageKey = savedPath != null ? savedPath : "";
    }

    // 2. Gửi yêu cầu mở phòng đấu giá qua Socket lên AuctionServer
    String categoryCode =
        switch (selectValue) {
          case "Đồ nghệ thuật" -> "ART";
          case "Đồ điện" -> "ELECTRONICS";
          case "Phương tiện giao thông" -> "VEHICLES";
          default -> "COLLECTIBLE";
        };
    String description =
        "Sản phẩm " + selectValue + ": " + item.getName() + " (SL: " + item.getQuantity() + ")";
    clientSession
        .getClient()
        .requestAuction(
            item.getName(), categoryCode, item.getPrice(), 500_000.0, 1800L, imageKey, description);

    NotificationManager.showSuccess(
        "Đã gửi yêu cầu mở phòng \"" + item.getName() + "\" lên máy chủ chờ Admin duyệt!");

    // 3. Reset trạng thái form
    selectedImageFile = null;
    if (lblImageFileName != null) {
      lblImageFileName.setText("Chưa chọn ảnh (sử dụng ảnh mẫu theo danh mục)");
    }
    if (imgPreview != null) {
      imgPreview.setImage(null);
    }

    // 4. Đóng overlay CreateAuctionRoom
    mainScreenController.hideOverLay();

    // 5. Điều hướng sang màn hình "Yêu cầu đấu giá" và làm mới danh sách từ Server
    mainScreenController.handleNavAuctionRequests();
  }

  @FXML
  private void handleClose() {
    if (mainScreenController != null) {
      mainScreenController.hideOverLay();
    }
  }
}
