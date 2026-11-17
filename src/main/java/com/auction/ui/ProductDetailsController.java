package com.auction.ui;

import com.auction.item.Art;
import com.auction.item.Electronics;
import com.auction.item.Item;
import com.auction.item.Vehicle;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class ProductDetailsController {

    @FXML private Label lblProductName;
    @FXML private Label lblStartPrice;
    @FXML private Label lblId;
    @FXML private Label lblQuantity;
    @FXML private Label lblArtist;
    @FXML private Label lblMedium;
    @FXML private Label lblYearCreated;
    @FXML private Label lblBrand;
    @FXML private Label lblModel;
    @FXML private Label lblWarrantyPeriod;
    @FXML private Label lblYear;

    @FXML private VBox boxProductName;
    @FXML private VBox boxStartPrice;
    @FXML private VBox boxId;
    @FXML private VBox boxQuantity;
    @FXML private VBox boxArtist;
    @FXML private VBox boxMedium;
    @FXML private VBox boxYearCreated;
    @FXML private VBox boxBrand;
    @FXML private VBox boxModel;
    @FXML private VBox boxWarrantyPeriod;
    @FXML private VBox boxYear;

    private Item currentItem;
    private HomeController mainScreenController;

    public void setHomeController(HomeController homeController) {
        this.mainScreenController = homeController;
    }

    /**
     * Hàm nhận dữ liệu sản phẩm được truyền sang từ thẻ card
     */
    public void initData(Item product) {
        this.currentItem = product;
        if (product == null) return;

        // Điền dữ liệu chung
        if (lblProductName != null) lblProductName.setText(product.getName());
        if (lblStartPrice != null) lblStartPrice.setText(String.format("%,d VNĐ", product.getPrice()));
        if (lblId != null) lblId.setText(String.valueOf(product.getId()));
        if (lblQuantity != null) lblQuantity.setText(String.valueOf(product.getQuantity()));

        // Điền dữ liệu riêng và cập nhật hiển thị
        if (product instanceof Art) {
            Art art = (Art) product;
            if (lblArtist != null) lblArtist.setText(art.getArtist());
            if (lblMedium != null) lblMedium.setText(art.getMedium());
            if (lblYearCreated != null) lblYearCreated.setText(String.valueOf(art.getYearCreated()));
            updateFieldsByCategory("Đồ nghệ thuật");
        } else if (product instanceof Electronics) {
            Electronics elec = (Electronics) product;
            if (lblBrand != null) lblBrand.setText(elec.getBrand());
            if (lblModel != null) lblModel.setText(elec.getModel());
            if (lblWarrantyPeriod != null) lblWarrantyPeriod.setText(elec.getWarrantyPeriod() + " tháng");
            updateFieldsByCategory("Đồ điện");
        } else if (product instanceof Vehicle) {
            Vehicle vehicle = (Vehicle) product;
            if (lblBrand != null) lblBrand.setText(vehicle.getMake());
            if (lblModel != null) lblModel.setText(vehicle.getModel());
            if (lblYear != null) lblYear.setText(String.valueOf(vehicle.getYear()));
            updateFieldsByCategory("Phương tiện giao thông");
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

    @FXML
    private void handleBack() {
        if (mainScreenController != null) {
            mainScreenController.hideOverLay();
        }
    }
}
