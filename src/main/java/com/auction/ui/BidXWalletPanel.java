package com.auction.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import network.UserProfile;
import network.WalletTxDTO;

/**
 * Giao diện Ví BidX chuẩn Fintech (Nạp tiền & Rút tiền về ngân hàng),
 * tái sử dụng cho cả Bidder và Seller, hỗ trợ đầy đủ Dark / Light Mode.
 */
public class BidXWalletPanel extends HBox {

    private static final String[] DEPOSIT_BANKS = {
            "Vietcombank", "Techcombank", "MB Bank", "BIDV", "MoMo / VietQR"
    };
    private static final String[] WITHDRAW_BANKS = {
            "Vietcombank", "Techcombank", "MB Bank", "BIDV", "ACB", "VPBank"
    };

    private final boolean sellerStyle;
    private final BiConsumer<Double, String> onDeposit;
    private final BiConsumer<Double, String> onWithdraw;

    // Platinum Card & Stats
    private final Label lblCardBalance = new Label("0 đ");
    private final Label lblCardHolder = new Label("BIDX MEMBER");
    private final Label lblCardAccountId = new Label("ACCOUNT");
    private final Label lblStatIn = new Label("+0 đ");
    private final Label lblStatOut = new Label("-0 đ");
    private final Label lblStatCount = new Label("0 lệnh");

    // Mode Toggle
    private final Button btnModeDeposit = new Button("Nạp tiền vào ví");
    private final Button btnModeWithdraw = new Button("Rút tiền về ngân hàng");
    private final VBox depositPane = new VBox(14);
    private final VBox withdrawPane = new VBox(14);

    // Deposit Controls
    private final List<Button> depositBankBtns = new ArrayList<>();
    private String selectedDepositBank = DEPOSIT_BANKS[0];
    private final TextField txtDepositAmount = new TextField();
    private final Label lblDepositPreview = new Label("Số dư sau nạp: 0 đ");
    private final Label lblDepositRef = new Label("Nội dung CK: BIDX NAP • Xử lý tức thì 24/7");
    private final Label lblDepositError = new Label();
    private final Button btnConfirmDeposit = new Button("XÁC NHẬN NẠP TIỀN NGAY");

    // Withdraw Controls
    private final List<Button> withdrawBankBtns = new ArrayList<>();
    private String selectedWithdrawBank = WITHDRAW_BANKS[0];
    private final TextField txtBankAccount = new TextField();
    private final TextField txtAccountHolder = new TextField();
    private final TextField txtWithdrawAmount = new TextField();
    private final Label lblWithdrawPreview = new Label("Còn lại sau rút: 0 đ • Miễn phí 0 đ");
    private final Label lblWithdrawError = new Label();
    private final Button btnConfirmWithdraw = new Button("XÁC NHẬN RÚT TIỀN VỀ NGÂN HÀNG");

    private double currentBalance = 0;
    private double lastAnimatedBalance = -1;
    private String currentUserId = "USER";
    private String currentUserName = "";
    private boolean withdrawMode = false;

    public BidXWalletPanel(
            boolean sellerStyle,
            BiConsumer<Double, String> onDeposit,
            BiConsumer<Double, String> onWithdraw) {
        this.sellerStyle = sellerStyle;
        this.onDeposit = onDeposit;
        this.onWithdraw = onWithdraw;

        setSpacing(16);
        setAlignment(Pos.TOP_LEFT);

        VBox leftCol = buildLeftVaultColumn();
        VBox rightCol = buildRightTransferConsole();
        HBox.setHgrow(rightCol, Priority.ALWAYS);

        getChildren().addAll(leftCol, rightCol);
        setMode(sellerStyle, false);
    }

    private VBox buildLeftVaultColumn() {
        VBox col = new VBox(14);
        col.setPrefWidth(332);
        col.setMinWidth(292);
        col.setMaxWidth(368);

        // Metallic Platinum Vault Card
        VBox card = new VBox(16);
        card.getStyleClass().add(sellerStyle ? "wallet-platinum-card-seller" : "wallet-platinum-card");

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        StackPane bankIconWrap = new StackPane(
                SvgIcons.create(SvgIcons.WALLET, 16, sellerStyle ? "#34D399" : "#FBBF24"));
        bankIconWrap.setMinSize(32, 32);
        bankIconWrap.setStyle("-fx-background-color: rgba(255,255,255,0.10); -fx-background-radius: 10;");
        Label brandTag = new Label(sellerStyle ? "BIDX SELLER VAULT" : "BIDX PLATINUM WALLET");
        brandTag.setStyle(
                "-fx-text-fill: #E2E8F0; -fx-font-weight: bold; -fx-font-size: 11.5px; -fx-letter-spacing: 0.8px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label instantBadge = new Label("● 24/7");
        instantBadge.setMinWidth(Region.USE_PREF_SIZE);
        instantBadge.setStyle("-fx-background-color: rgba(16,185,129,0.22); -fx-text-fill: #6EE7B7;"
                + " -fx-font-size: 10px; -fx-font-weight: bold; -fx-background-radius: 14; -fx-padding: 3 9 3 9;");
        topRow.getChildren().addAll(bankIconWrap, brandTag, spacer, instantBadge);

        VBox balBox = new VBox(4);
        Label balCaption = new Label(sellerStyle ? "DOANH THU KHẢ DỤNG TRONG TÚI" : "SỐ DƯ KHẢ DỤNG");
        balCaption.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 10.5px; -fx-font-weight: bold;");
        lblCardBalance.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 26px; -fx-font-weight: bold;");
        balBox.getChildren().addAll(balCaption, lblCardBalance);

        HBox bottomRow = new HBox(12);
        bottomRow.setAlignment(Pos.BOTTOM_LEFT);
        VBox holderBox = new VBox(2);
        Label holderCap = new Label("CHỦ TÀI KHOẢN");
        holderCap.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 9.5px; -fx-font-weight: bold;");
        lblCardHolder.setStyle("-fx-text-fill: #F8FAFC; -fx-font-size: 13.5px; -fx-font-weight: bold;");
        holderBox.getChildren().addAll(holderCap, lblCardHolder);

        VBox idBox = new VBox(2);
        Label idCap = new Label("MÃ VÍ");
        idCap.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 9.5px; -fx-font-weight: bold;");
        lblCardAccountId.setStyle("-fx-text-fill: #FBBF24; -fx-font-size: 13px; -fx-font-weight: bold;");
        idBox.getChildren().addAll(idCap, lblCardAccountId);

        Region bSpacer = new Region();
        HBox.setHgrow(bSpacer, Priority.ALWAYS);

        StackPane emvChip = new StackPane();
        emvChip.setMinSize(38, 26);
        emvChip.setMaxSize(38, 26);
        emvChip.getStyleClass().add("wallet-emv-chip");

        bottomRow.getChildren().addAll(holderBox, idBox, bSpacer, emvChip);
        card.getChildren().addAll(topRow, balBox, bottomRow);

        // 3 Mini Cashflow Summary Cards
        HBox statsRow = new HBox(10);
        statsRow.getChildren().addAll(
                buildMiniStatCard(sellerStyle ? "TỔNG THU / NẠP" : "TỔNG NẠP", lblStatIn, "text-accent-emerald"),
                buildMiniStatCard("TỔNG RÚT", lblStatOut, "text-accent-crimson"),
                buildMiniStatCard("GIAO DỊCH", lblStatCount, "text-primary"));

        // Bank Guarantee Info Strip
        HBox infoStrip = new HBox(10);
        infoStrip.setAlignment(Pos.CENTER_LEFT);
        infoStrip.getStyleClass().add("sub-surface");
        infoStrip.setPadding(new Insets(12, 14, 12, 14));
        StackPane bankIco = new StackPane(SvgIcons.create(SvgIcons.BANK, 16, "#38BDF8"));
        bankIco.setMinSize(32, 32);
        bankIco.setStyle("-fx-background-color: rgba(56,189,248,0.14); -fx-background-radius: 9;");
        VBox infoTexts = new VBox(2);
        Label infoTitle = new Label("Chuyển khoản nhanh Napas 24/7");
        infoTitle.getStyleClass().add("text-primary");
        infoTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label infoSub = new Label("Miễn phí nạp & rút 0 đ • Đồng bộ tức thời tới máy chủ BidX");
        infoSub.getStyleClass().add("text-subtle");
        infoSub.setWrapText(true);
        infoSub.setStyle("-fx-font-size: 11px;");
        infoTexts.getChildren().addAll(infoTitle, infoSub);
        infoStrip.getChildren().addAll(bankIco, infoTexts);

        col.getChildren().addAll(card, statsRow, infoStrip);
        return col;
    }

    private VBox buildMiniStatCard(String caption, Label valueLabel, String accentClass) {
        VBox box = new VBox(4);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.getStyleClass().add("sub-surface");
        box.setPadding(new Insets(11, 12, 11, 12));
        Label cap = new Label(caption);
        cap.getStyleClass().add("text-subtle");
        cap.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        valueLabel.getStyleClass().add(accentClass);
        valueLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        box.getChildren().addAll(cap, valueLabel);
        return box;
    }

    private VBox buildRightTransferConsole() {
        VBox panel = new VBox(16);
        panel.getStyleClass().add("surface-panel");

        // Segmented Toggle (Deposit vs Withdraw)
        HBox segWrap = new HBox(6);
        segWrap.getStyleClass().add("wallet-seg-wrap");

        btnModeDeposit.setMaxWidth(Double.MAX_VALUE);
        btnModeWithdraw.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnModeDeposit, Priority.ALWAYS);
        HBox.setHgrow(btnModeWithdraw, Priority.ALWAYS);
        btnModeDeposit.getStyleClass().add("wallet-seg-btn");
        btnModeWithdraw.getStyleClass().add("wallet-seg-btn");

        btnModeDeposit.setOnAction(e -> setMode(false, true));
        btnModeWithdraw.setOnAction(e -> setMode(true, true));
        segWrap.getChildren().addAll(btnModeDeposit, btnModeWithdraw);

        buildDepositPane();
        buildWithdrawPane();

        StackPane formStack = new StackPane(depositPane, withdrawPane);
        formStack.setAlignment(Pos.TOP_LEFT);

        panel.getChildren().addAll(segWrap, formStack);
        return panel;
    }

    private void buildDepositPane() {
        Label lblBank = new Label("Chọn nguồn tiền / Ngân hàng nạp");
        lblBank.getStyleClass().add("field-label");

        FlowPane bankFlow = new FlowPane(8, 8);
        for (String bank : DEPOSIT_BANKS) {
            Button chip = new Button(bank);
            chip.getStyleClass().add("bank-chip");
            chip.setOnAction(e -> selectDepositBank(bank));
            depositBankBtns.add(chip);
            bankFlow.getChildren().add(chip);
        }

        HBox amtHeader = new HBox(6);
        amtHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblAmt = new Label("Số tiền muốn nạp (VNĐ)");
        lblAmt.getStyleClass().add("field-label");
        Region amtSpacer = new Region();
        HBox.setHgrow(amtSpacer, Priority.ALWAYS);
        Button btnResetDep = new Button("Đặt lại");
        btnResetDep.getStyleClass().add("btn-link-subtle");
        btnResetDep.setStyle("-fx-font-size: 11px; -fx-padding: 0;");
        btnResetDep.setOnAction(e -> {
            txtDepositAmount.clear();
            lblDepositError.setVisible(false);
            lblDepositError.setManaged(false);
            SoundEffects.playReactionTap();
        });
        amtHeader.getChildren().addAll(lblAmt, amtSpacer, btnResetDep);

        txtDepositAmount.setPromptText("Nhập số tiền hoặc bấm cộng nhanh (+15M x2 = 30M)...");
        txtDepositAmount.getStyleClass().add("bidx-input");
        txtDepositAmount.textProperty().addListener((obs, o, n) -> refreshDepositPreview());

        HBox quickRow = new HBox(7);
        long[] presets = {10_000_000L, 15_000_000L, 25_000_000L, 50_000_000L, 100_000_000L};
        String[] labels = {"+10M", "+15M", "+25M", "+50M", "+100M"};
        for (int i = 0; i < presets.length; i++) {
            long val = presets[i];
            Button btn = new Button(labels[i]);
            btn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.getStyleClass().add("btn-quick-chip");
            btn.setOnAction(e -> {
                double parsed = parseAmount(txtDepositAmount.getText());
                long current = parsed > 0 ? (long) Math.floor(parsed) : 0L;
                txtDepositAmount.setText(String.valueOf(current + val));
                lblDepositError.setText("");
                lblDepositError.setVisible(false);
                lblDepositError.setManaged(false);
                BidXAnimations.popScale(btn, 1.07);
                BidXAnimations.popScale(txtDepositAmount, 1.03);
                SoundEffects.playReactionTap();
            });
            quickRow.getChildren().add(btn);
        }

        VBox previewBox = new VBox(4);
        previewBox.getStyleClass().add("wallet-preview-bar");
        lblDepositPreview.getStyleClass().add("text-accent-emerald");
        lblDepositPreview.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        lblDepositRef.getStyleClass().add("text-muted");
        lblDepositRef.setStyle("-fx-font-size: 11.5px;");
        previewBox.getChildren().addAll(lblDepositPreview, lblDepositRef);

        lblDepositError.getStyleClass().add("text-accent-crimson");
        lblDepositError.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        lblDepositError.setManaged(false);
        lblDepositError.setVisible(false);

        btnConfirmDeposit.setMaxWidth(Double.MAX_VALUE);
        btnConfirmDeposit.getStyleClass().add("btn-emerald");
        btnConfirmDeposit.setStyle("-fx-padding: 13 20 13 20; -fx-font-size: 14px;");
        btnConfirmDeposit.setOnAction(e -> handleDepositSubmit());
        BidXAnimations.applyButtonJuice(btnConfirmDeposit);
        BidXAnimations.attachButtonGlowPulse(btnConfirmDeposit);

        depositPane.getChildren().addAll(
                lblBank, bankFlow,
                amtHeader, txtDepositAmount, quickRow,
                previewBox, lblDepositError, btnConfirmDeposit);
        selectDepositBank(DEPOSIT_BANKS[0]);
    }

    private void buildWithdrawPane() {
        Label lblBank = new Label("Ngân hàng nhận tiền");
        lblBank.getStyleClass().add("field-label");

        FlowPane bankFlow = new FlowPane(8, 8);
        for (String bank : WITHDRAW_BANKS) {
            Button chip = new Button(bank);
            chip.getStyleClass().add("bank-chip");
            chip.setOnAction(e -> selectWithdrawBank(bank));
            withdrawBankBtns.add(chip);
            bankFlow.getChildren().add(chip);
        }

        HBox bankFields = new HBox(12);
        VBox accBox = new VBox(5);
        HBox.setHgrow(accBox, Priority.ALWAYS);
        HBox accHeader = new HBox(6);
        accHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblAcc = new Label("Số tài khoản nhận *");
        lblAcc.getStyleClass().add("field-label");
        Region accSpacer = new Region();
        HBox.setHgrow(accSpacer, Priority.ALWAYS);
        Button btnFillLinked = new Button("Dùng TK liên kết");
        btnFillLinked.getStyleClass().add("btn-link-subtle");
        btnFillLinked.setStyle("-fx-font-size: 11px; -fx-padding: 0;");
        btnFillLinked.setOnAction(e -> {
            txtBankAccount.setText(sellerStyle ? "1903 8866 9988" : "1028 6688 9999");
            if (txtAccountHolder.getText() == null || txtAccountHolder.getText().isBlank()) {
                txtAccountHolder.setText(currentUserName.toUpperCase(Locale.ROOT));
            }
            lblWithdrawError.setText("");
            lblWithdrawError.setVisible(false);
            lblWithdrawError.setManaged(false);
            SoundEffects.playReactionTap();
        });
        accHeader.getChildren().addAll(lblAcc, accSpacer, btnFillLinked);
        txtBankAccount.setPromptText("VD: 1028 6688 9999");
        txtBankAccount.getStyleClass().add("bidx-input");
        accBox.getChildren().addAll(accHeader, txtBankAccount);

        VBox holderBox = new VBox(5);
        HBox.setHgrow(holderBox, Priority.ALWAYS);
        Label lblHolder = new Label("Tên chủ tài khoản *");
        lblHolder.getStyleClass().add("field-label");
        txtAccountHolder.setPromptText("VD: TUAN DAI");
        txtAccountHolder.getStyleClass().add("bidx-input");
        holderBox.getChildren().addAll(lblHolder, txtAccountHolder);

        bankFields.getChildren().addAll(accBox, holderBox);

        HBox amtHeader = new HBox(6);
        amtHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblAmt = new Label("Số tiền muốn rút (VNĐ)");
        lblAmt.getStyleClass().add("field-label");
        Region amtSpacer = new Region();
        HBox.setHgrow(amtSpacer, Priority.ALWAYS);
        Button btnResetWdr = new Button("Đặt lại");
        btnResetWdr.getStyleClass().add("btn-link-subtle");
        btnResetWdr.setStyle("-fx-font-size: 11px; -fx-padding: 0;");
        btnResetWdr.setOnAction(e -> {
            txtWithdrawAmount.clear();
            lblWithdrawError.setVisible(false);
            lblWithdrawError.setManaged(false);
            SoundEffects.playReactionTap();
        });
        amtHeader.getChildren().addAll(lblAmt, amtSpacer, btnResetWdr);

        txtWithdrawAmount.setPromptText("Nhập số tiền hoặc bấm cộng nhanh (+15M x2 = 30M)...");
        txtWithdrawAmount.getStyleClass().add("bidx-input");
        txtWithdrawAmount.textProperty().addListener((obs, o, n) -> refreshWithdrawPreview());

        HBox quickWithdrawRow = new HBox(7);
        long[] withdrawPresets = {10_000_000L, 15_000_000L, 25_000_000L, 50_000_000L};
        String[] withdrawLabels = {"+10M", "+15M", "+25M", "+50M"};
        for (int i = 0; i < withdrawPresets.length; i++) {
            long val = withdrawPresets[i];
            Button btn = new Button(withdrawLabels[i]);
            btn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.getStyleClass().add("btn-quick-chip");
            btn.setOnAction(e -> {
                double parsed = parseAmount(txtWithdrawAmount.getText());
                long current = parsed > 0 ? (long) Math.floor(parsed) : 0L;
                long next = current + val;
                if (currentBalance > 0 && next > (long) Math.floor(currentBalance)) {
                    next = (long) Math.floor(currentBalance);
                }
                txtWithdrawAmount.setText(String.valueOf(next));
                lblWithdrawError.setVisible(false);
                lblWithdrawError.setManaged(false);
                BidXAnimations.popScale(btn, 1.07);
                BidXAnimations.popScale(txtWithdrawAmount, 1.03);
                SoundEffects.playReactionTap();
            });
            quickWithdrawRow.getChildren().add(btn);
        }
        Button btnMax = new Button("Rút hết");
        btnMax.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnMax, Priority.ALWAYS);
        btnMax.getStyleClass().add("btn-quick-chip");
        btnMax.setOnAction(e -> {
            long maxBal = (long) Math.floor(currentBalance);
            txtWithdrawAmount.setText(maxBal > 0 ? String.valueOf(maxBal) : "");
            lblWithdrawError.setVisible(false);
            lblWithdrawError.setManaged(false);
            BidXAnimations.popScale(btnMax, 1.07);
            BidXAnimations.popScale(txtWithdrawAmount, 1.03);
            SoundEffects.playReactionTap();
        });
        quickWithdrawRow.getChildren().add(btnMax);

        VBox previewBox = new VBox(4);
        previewBox.getStyleClass().add("wallet-preview-bar-withdraw");
        lblWithdrawPreview.getStyleClass().add("text-accent-orange");
        lblWithdrawPreview.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label feeNote = new Label("Chuyển khoản Napas 24/7 • Miễn phí rút tiền 0 đ • Nhận tiền sau vài giây");
        feeNote.getStyleClass().add("text-muted");
        feeNote.setStyle("-fx-font-size: 11.5px;");
        previewBox.getChildren().addAll(lblWithdrawPreview, feeNote);

        lblWithdrawError.getStyleClass().add("text-accent-crimson");
        lblWithdrawError.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        lblWithdrawError.setManaged(false);
        lblWithdrawError.setVisible(false);

        btnConfirmWithdraw.setMaxWidth(Double.MAX_VALUE);
        btnConfirmWithdraw.getStyleClass().add("btn-fire");
        btnConfirmWithdraw.setStyle("-fx-padding: 13 20 13 20; -fx-font-size: 14px;");
        btnConfirmWithdraw.setOnAction(e -> handleWithdrawSubmit());
        BidXAnimations.applyButtonJuice(btnConfirmWithdraw);
        BidXAnimations.attachButtonGlowPulse(btnConfirmWithdraw);

        withdrawPane.getChildren().addAll(
                lblBank, bankFlow,
                bankFields,
                amtHeader, txtWithdrawAmount, quickWithdrawRow,
                previewBox, lblWithdrawError, btnConfirmWithdraw);
        selectWithdrawBank(WITHDRAW_BANKS[0]);
    }

    public void openDepositMode() {
        setMode(false, true);
    }

    public void openWithdrawMode() {
        setMode(true, true);
    }

    private void setMode(boolean withdraw, boolean animate) {
        this.withdrawMode = withdraw;
        depositPane.setVisible(!withdraw);
        depositPane.setManaged(!withdraw);
        withdrawPane.setVisible(withdraw);
        withdrawPane.setManaged(withdraw);

        btnModeDeposit.getStyleClass().remove("wallet-seg-deposit-active");
        btnModeWithdraw.getStyleClass().remove("wallet-seg-withdraw-active");
        if (withdraw) {
            btnModeWithdraw.getStyleClass().add("wallet-seg-withdraw-active");
            btnModeDeposit.setGraphic(SvgIcons.create(SvgIcons.ARROW_DOWN, 14, "#94A3B8"));
            btnModeWithdraw.setGraphic(SvgIcons.create(SvgIcons.ARROW_UP, 14, "#FFFFFF"));
        } else {
            btnModeDeposit.getStyleClass().add("wallet-seg-deposit-active");
            btnModeDeposit.setGraphic(SvgIcons.create(SvgIcons.ARROW_DOWN, 14, "#FFFFFF"));
            btnModeWithdraw.setGraphic(SvgIcons.create(SvgIcons.ARROW_UP, 14, "#94A3B8"));
        }

        if (animate) {
            SoundEffects.playReactionTap();
            BidXAnimations.fadeSlideIn(withdraw ? withdrawPane : depositPane, 0, 8);
        }
        refreshDepositPreview();
        refreshWithdrawPreview();
    }

    private void selectDepositBank(String bank) {
        selectedDepositBank = bank;
        for (Button btn : depositBankBtns) {
            btn.getStyleClass().remove("bank-chip-active");
            if (bank.equals(btn.getText())) {
                btn.getStyleClass().add("bank-chip-active");
            }
        }
        refreshDepositPreview();
    }

    private void selectWithdrawBank(String bank) {
        selectedWithdrawBank = bank;
        for (Button btn : withdrawBankBtns) {
            btn.getStyleClass().remove("bank-chip-active-withdraw");
            if (bank.equals(btn.getText())) {
                btn.getStyleClass().add("bank-chip-active-withdraw");
            }
        }
        refreshWithdrawPreview();
    }

    private void refreshDepositPreview() {
        double amt = parseAmount(txtDepositAmount.getText());
        if (amt > 0) {
            lblDepositPreview.setText("Nạp +" + FormatUtils.formatVnd(amt)
                    + "  •  Số dư sau nạp: " + FormatUtils.formatVnd(currentBalance + amt));
        } else {
            lblDepositPreview.setText("Số dư hiện tại: " + FormatUtils.formatVnd(currentBalance));
        }
        lblDepositRef.setText("Nguồn: " + selectedDepositBank
                + "  •  Nội dung CK: BIDX NAP " + currentUserId + "  •  Tự động 24/7");
    }

    private void refreshWithdrawPreview() {
        double amt = parseAmount(txtWithdrawAmount.getText());
        if (amt > 0) {
            if (amt > currentBalance) {
                lblWithdrawPreview.setText("Vượt quá số dư khả dụng (" + FormatUtils.formatVnd(currentBalance) + ")");
            } else {
                lblWithdrawPreview.setText("Rút " + FormatUtils.formatVnd(amt)
                        + " về " + selectedWithdrawBank
                        + "  •  Còn lại: " + FormatUtils.formatVnd(currentBalance - amt));
            }
        } else {
            lblWithdrawPreview
                    .setText("Khả dụng để rút: " + FormatUtils.formatVnd(currentBalance) + "  •  Phí rút: 0 đ");
        }
    }

    private void handleDepositSubmit() {
        lblDepositError.setVisible(false);
        lblDepositError.setManaged(false);
        double amt = parseAmount(txtDepositAmount.getText());
        if (amt < 100_000) {
            showDepositError("Số tiền nạp tối thiểu là 100.000 đ.");
            BidXAnimations.shake(txtDepositAmount);
            return;
        }
        if (onDeposit != null) {
            onDeposit.accept(amt, selectedDepositBank);
        }
        txtDepositAmount.clear();
    }

    private void handleWithdrawSubmit() {
        lblWithdrawError.setVisible(false);
        lblWithdrawError.setManaged(false);
        String accNum = txtBankAccount.getText() != null ? txtBankAccount.getText().trim() : "";
        String holder = txtAccountHolder.getText() != null ? txtAccountHolder.getText().trim() : "";
        double amt = parseAmount(txtWithdrawAmount.getText());

        if (accNum.replaceAll("\\s+", "").length() < 6) {
            showWithdrawError("Vui lòng nhập số tài khoản ngân hàng hợp lệ (tối thiểu 6 chữ số).");
            BidXAnimations.shake(txtBankAccount);
            return;
        }
        if (holder.isEmpty()) {
            showWithdrawError("Vui lòng nhập tên chủ tài khoản nhận tiền.");
            BidXAnimations.shake(txtAccountHolder);
            return;
        }
        if (amt <= 0) {
            showWithdrawError("Vui lòng nhập số tiền cần rút lớn hơn 0 đ.");
            BidXAnimations.shake(txtWithdrawAmount);
            return;
        }
        if (amt > currentBalance) {
            showWithdrawError("Số dư khả dụng chỉ còn " + FormatUtils.formatVnd(currentBalance) + ", không đủ để rút.");
            BidXAnimations.shake(txtWithdrawAmount);
            return;
        }
        String compactAcc = accNum.replaceAll("\\s+", "");
        String maskedAcc = compactAcc.length() > 4
                ? "••" + compactAcc.substring(compactAcc.length() - 4)
                : compactAcc;
        String destination = selectedWithdrawBank + " " + maskedAcc + " (" + holder.toUpperCase(Locale.ROOT) + ")";
        if (onWithdraw != null) {
            onWithdraw.accept(amt, destination);
        }
        txtWithdrawAmount.clear();
    }

    private void showDepositError(String msg) {
        lblDepositError.setText(msg);
        lblDepositError.setVisible(true);
        lblDepositError.setManaged(true);
    }

    private void showWithdrawError(String msg) {
        lblWithdrawError.setText(msg);
        lblWithdrawError.setVisible(true);
        lblWithdrawError.setManaged(true);
    }

    private double parseAmount(String raw) {
        if (raw == null) {
            return -1;
        }
        String clean = raw.trim().replaceAll("[,._\\s]", "");
        if (clean.isEmpty()) {
            return -1;
        }
        try {
            double v = Double.parseDouble(clean);
            return Double.isFinite(v) ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public void updateProfile(UserProfile profile) {
        if (profile == null) {
            return;
        }
        this.currentUserId = profile.userId();
        this.currentUserName = profile.name();
        this.currentBalance = profile.balance();

        lblCardHolder.setText(profile.name().toUpperCase(Locale.ROOT));
        lblCardAccountId.setText(profile.userId());
        if (txtAccountHolder.getText() == null || txtAccountHolder.getText().isBlank()) {
            txtAccountHolder.setText(profile.name().toUpperCase(Locale.ROOT));
        }

        if (lastAnimatedBalance < 0) {
            lblCardBalance.setText(FormatUtils.formatVnd(currentBalance));
        } else if (Math.abs(currentBalance - lastAnimatedBalance) > 0.5) {
            BidXAnimations.animateCurrency(lblCardBalance, lastAnimatedBalance, currentBalance);
            BidXAnimations.pricePopFlash(lblCardBalance);
        }
        lastAnimatedBalance = currentBalance;
        refreshDepositPreview();
        refreshWithdrawPreview();
    }

    public void updateTransactions(List<WalletTxDTO> transactions) {
        double totalIn = 0;
        double totalOut = 0;
        int count = 0;
        if (transactions != null) {
            count = transactions.size();
            for (WalletTxDTO tx : transactions) {
                if ("TOP_UP".equalsIgnoreCase(tx.type()) || "PAYOUT_RECEIVED".equalsIgnoreCase(tx.type())) {
                    totalIn += Math.abs(tx.amount());
                } else if ("WITHDRAW".equalsIgnoreCase(tx.type())) {
                    totalOut += Math.abs(tx.amount());
                }
            }
        }
        lblStatIn.setText("+" + FormatUtils.formatCompactVnd(totalIn));
        lblStatOut.setText("-" + FormatUtils.formatCompactVnd(totalOut));
        lblStatCount.setText(count + " lệnh");
    }

    public boolean isWithdrawMode() {
        return withdrawMode;
    }
}
