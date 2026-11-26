package com.auction.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;

/**
 * Bộ chọn Emoji màu 3D kiểu Messenger kèm thanh tìm kiếm, phân loại danh mục
 * và bộ dựng tin nhắn chat giàu màu sắc (hoạt động mượt trên Windows JavaFX 21, Dark & Light Mode).
 */
public final class BidXEmojiPicker {

    public record EmojiDef(String code, String category, String keywords, String label) {
    }

    private static final List<EmojiDef> EMOJIS = List.of(
            // 1. Mặt cười
            new EmojiDef("😀", "FACES", "cuoi vui happy smile grinning", "Cười tươi"),
            new EmojiDef("😄", "FACES", "cuoi vui haha smile", "Cười rạng rỡ"),
            new EmojiDef("😁", "FACES", "cuoi nhe rang grin", "Cười toe toét"),
            new EmojiDef("😂", "FACES", "cuoi ra nuoc mat lol haha", "Cười ra nước mắt"),
            new EmojiDef("🤣", "FACES", "cuoi lan lon rofl haha", "Cười lăn lộn"),
            new EmojiDef("😍", "FACES", "yeu thich tim mat heart eyes dep", "Yêu thích quá"),
            new EmojiDef("🥰", "FACES", "yeu thuong tim cute", "Ngập tràn yêu thương"),
            new EmojiDef("😎", "FACES", "ngau chat cool kinh ram vip", "Chất chơi ngầu"),
            new EmojiDef("🤩", "FACES", "sao ngoi sao star tuyet dinh", "Quá đỉnh"),
            new EmojiDef("🥳", "FACES", "tiec chuc mung party win thang", "Ăn mừng"),
            new EmojiDef("😉", "FACES", "nhay mat wink", "Nháy mắt"),
            new EmojiDef("😜", "FACES", "le luoi tinh nghich playful", "Tinh nghịch"),
            new EmojiDef("🤑", "FACES", "tien giau money rich dai gia", "Đại gia chốt đơn"),
            new EmojiDef("😮", "FACES", "ngac nhien wow o", "Bất ngờ Wow"),
            new EmojiDef("🤔", "FACES", "suy nghi think", "Đang cân nhắc"),
            new EmojiDef("😅", "FACES", "mo hoi cuoi guong sweat", "Hú hồn"),

            // 2. Cảm xúc & Nổi bật
            new EmojiDef("❤️", "HEARTS", "tim do yeu love heart", "Trái tim đỏ"),
            new EmojiDef("🧡", "HEARTS", "tim cam orange heart", "Trái tim cam"),
            new EmojiDef("💛", "HEARTS", "tim vang gold heart", "Trái tim vàng"),
            new EmojiDef("💖", "HEARTS", "tim lap lanh sparkle heart", "Tim lấp lánh"),
            new EmojiDef("🔥", "HEARTS", "lua nong hot chay fire", "Cháy quá HOT"),
            new EmojiDef("⚡", "HEARTS", "set nhanh zap flash", "Nhảy giá thần tốc"),
            new EmojiDef("✨", "HEARTS", "lap lanh dep sparkle", "Lung linh"),
            new EmojiDef("💯", "HEARTS", "100 diem chuan tuyet doi", "Chuẩn 100 điểm"),

            // 3. Cử chỉ
            new EmojiDef("👍", "HANDS", "like thich ok tot good", "Quá tuyệt Like"),
            new EmojiDef("👏", "HANDS", "vo tay chuc mung clap", "Vỗ tay khen"),
            new EmojiDef("🙌", "HANDS", "hoan ho tuyet voi yay", "Hoan hô"),
            new EmojiDef("👌", "HANDS", "ok chuan chot", "Chốt OK"),
            new EmojiDef("🤝", "HANDS", "bat tay giao dich deal", "Chốt giao dịch"),
            new EmojiDef("💪", "HANDS", "manh me quyet tam strong", "Quyết tâm thắng"),

            // 4. Đấu giá & VIP
            new EmojiDef("💎", "VIP", "kim cuong diamond vip sang", "Kim cương VIP"),
            new EmojiDef("👑", "VIP", "vuong mien vua crown king top", "Vua sàn đấu giá"),
            new EmojiDef("🏆", "VIP", "cup vo dich win trophy thang", "Cúp chiến thắng"),
            new EmojiDef("💰", "VIP", "tui tien money giau", "Tiền về đầy túi"),
            new EmojiDef("🚀", "VIP", "ten lua bay gia rocket moon", "Bay giá cực mạnh"),
            new EmojiDef("🎉", "VIP", "phao hoa chuc mung party", "Pháo giấy ăn mừng"),
            new EmojiDef("🎯", "VIP", "trung dich muc tieu target", "Nhắm chuẩn mục tiêu"),
            new EmojiDef("🎁", "VIP", "qua tang gift", "Quà VIP"),

            // 5. Vật phẩm & Siêu Xe
            new EmojiDef("🏎️", "ITEMS", "sieu xe dua car supercar ferrari lambo", "Siêu xe"),
            new EmojiDef("🚗", "ITEMS", "o to xe hoi car porsche", "Xe sang"),
            new EmojiDef("⌚", "ITEMS", "dong ho watch rolex patek", "Đồng hồ xa xỉ"),
            new EmojiDef("📷", "ITEMS", "may anh camera leica", "Máy ảnh cổ"),
            new EmojiDef("💻", "ITEMS", "laptop may tinh macbook dell", "Laptop"),
            new EmojiDef("🎨", "ITEMS", "tranh nghe thuat art", "Nghệ thuật"),
            new EmojiDef("🍾", "ITEMS", "ruou champagne whisky", "Rượu thượng hạng"),
            new EmojiDef("💍", "ITEMS", "nhan kim cuong ring", "Nhẫn kim cương")
    );

    private static final Map<String, EmojiDef> BY_CODE = new LinkedHashMap<>();
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    static {
        for (EmojiDef def : EMOJIS) {
            BY_CODE.put(def.code(), def);
        }
    }

    private BidXEmojiPicker() {
    }

    public static List<EmojiDef> allEmojis() {
        return EMOJIS;
    }

    /**
     * Dựng TextFlow kết hợp văn bản và emoji màu sắc nét 3D cho bong bóng chat.
     */
    public static TextFlow buildRichChatFlow(String message, String textStyleClass) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(2);
        if (message == null || message.isEmpty()) {
            return flow;
        }
        StringBuilder plainBuf = new StringBuilder();
        int i = 0;
        int len = message.length();
        while (i < len) {
            String matched = matchEmojiAt(message, i);
            if (matched != null) {
                if (!plainBuf.isEmpty()) {
                    flow.getChildren().add(createStyledText(plainBuf.toString(), textStyleClass));
                    plainBuf.setLength(0);
                }
                ImageView iv = createEmojiView(matched, 18);
                iv.setTranslateY(3);
                flow.getChildren().add(iv);
                i += matched.length();
            } else {
                plainBuf.append(message.charAt(i));
                i++;
            }
        }
        if (!plainBuf.isEmpty()) {
            flow.getChildren().add(createStyledText(plainBuf.toString(), textStyleClass));
        }
        return flow;
    }

    private static Text createStyledText(String content, String styleClass) {
        Text t = new Text(content);
        if (styleClass != null && !styleClass.isBlank()) {
            t.getStyleClass().add(styleClass);
        }
        t.setStyle("-fx-font-size: 12.5px;");
        return t;
    }

    private static String matchEmojiAt(String text, int index) {
        for (String code : BY_CODE.keySet()) {
            if (text.startsWith(code, index)) {
                return code;
            }
        }
        return null;
    }

    public static ImageView createEmojiView(String code, double size) {
        Image img = getEmojiImage(code, 48);
        ImageView iv = new ImageView(img);
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        return iv;
    }

    /**
     * Tạo Popup chọn Emoji kiểu Messenger với thanh tìm kiếm + danh mục + lưới 8 cột.
     */
    public static Popup createPickerPopup(Consumer<String> onEmojiSelected) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        boolean light = !ThemeManager.isDarkMode();

        VBox card = new VBox(10);
        card.setPrefWidth(346);
        card.setMaxWidth(346);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setPadding(new Insets(12, 12, 10, 12));
        card.setStyle(light
                ? "-fx-background-color: #FFFFFF; -fx-background-radius: 16; -fx-border-color: rgba(15,23,42,0.14);"
                        + " -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.22), 24, 0.12, 0, 8);"
                : "-fx-background-color: #182034; -fx-background-radius: 16; -fx-border-color: rgba(255,255,255,0.14);"
                        + " -fx-border-radius: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.72), 28, 0.2, 0, 10);");

        // Thanh tìm kiếm biểu tượng cảm xúc
        HBox searchRow = new HBox(8);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.setPadding(new Insets(6, 12, 6, 12));
        searchRow.setStyle(light
                ? "-fx-background-color: #F1F5F9; -fx-background-radius: 20;"
                : "-fx-background-color: #25304B; -fx-background-radius: 20;");
        Node searchIcon = SvgIcons.create(SvgIcons.SEARCH, 14, light ? "#64748B" : "#94A3B8");
        TextField txtSearch = new TextField();
        txtSearch.setPromptText("Tìm kiếm biểu tượng cảm xúc");
        HBox.setHgrow(txtSearch, Priority.ALWAYS);
        txtSearch.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0;"
                + " -fx-font-size: 12.5px; -fx-text-fill: " + (light ? "#0F172A;" : "#F8FAFC;")
                + " -fx-prompt-text-fill: #94A3B8;");
        searchRow.getChildren().addAll(searchIcon, txtSearch);

        Label lblCategoryTitle = new Label("Biểu tượng phổ biến");
        lblCategoryTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: "
                + (light ? "#64748B;" : "#94A3B8;"));

        FlowPane grid = new FlowPane(4, 4);
        grid.setPrefWrapLength(314);
        grid.setPadding(new Insets(2, 0, 4, 0));

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(214);
        scroll.setMaxHeight(214);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("bidx-scroll");
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        final String[] activeCategory = {"ALL"};

        Runnable renderGrid = () -> {
            grid.getChildren().clear();
            String q = txtSearch.getText() != null ? txtSearch.getText().trim().toLowerCase(Locale.ROOT) : "";
            for (EmojiDef def : EMOJIS) {
                if (!"ALL".equals(activeCategory[0]) && !def.category().equals(activeCategory[0]) && q.isEmpty()) {
                    continue;
                }
                if (!q.isEmpty() && !def.keywords().contains(q)
                        && !def.label().toLowerCase(Locale.ROOT).contains(q)
                        && !def.code().contains(q)) {
                    continue;
                }
                Button tile = new Button();
                tile.setGraphic(createEmojiView(def.code(), 24));
                tile.setPrefSize(37, 37);
                tile.setMinSize(37, 37);
                tile.setMaxSize(37, 37);
                String baseStyle = "-fx-background-color: transparent; -fx-background-radius: 10; -fx-padding: 0; -fx-cursor: hand;";
                String hoverStyle = light
                        ? "-fx-background-color: #E2E8F0; -fx-background-radius: 10; -fx-padding: 0; -fx-cursor: hand;"
                        : "-fx-background-color: rgba(255,255,255,0.12); -fx-background-radius: 10; -fx-padding: 0; -fx-cursor: hand;";
                tile.setStyle(baseStyle);
                tile.setOnMouseEntered(e -> {
                    tile.setStyle(hoverStyle);
                    tile.setScaleX(1.16);
                    tile.setScaleY(1.16);
                });
                tile.setOnMouseExited(e -> {
                    tile.setStyle(baseStyle);
                    tile.setScaleX(1.0);
                    tile.setScaleY(1.0);
                });
                tile.setTooltip(new Tooltip(def.label()));
                tile.setOnAction(e -> {
                    if (onEmojiSelected != null) {
                        onEmojiSelected.accept(def.code());
                    }
                    SoundEffects.playReactionTap();
                });
                grid.getChildren().add(tile);
            }
            if (grid.getChildren().isEmpty()) {
                Label none = new Label("Không tìm thấy biểu tượng phù hợp");
                none.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px; -fx-padding: 16;");
                grid.getChildren().add(none);
            }
        };

        txtSearch.textProperty().addListener((obs, o, n) -> {
            lblCategoryTitle.setText(n == null || n.isBlank() ? "Biểu tượng phổ biến" : "Kết quả tìm kiếm");
            renderGrid.run();
        });

        // Thanh danh mục dưới đáy giống Messenger
        HBox tabBar = new HBox(4);
        tabBar.setAlignment(Pos.CENTER);
        tabBar.setPadding(new Insets(6, 0, 0, 0));
        tabBar.setStyle(light
                ? "-fx-border-color: rgba(15,23,42,0.08) transparent transparent transparent; -fx-border-width: 1 0 0 0;"
                : "-fx-border-color: rgba(255,255,255,0.08) transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        String[][] tabs = {
                {"ALL", "😀", "Tất cả biểu tượng"},
                {"FACES", "😍", "Mặt cười & cảm xúc"},
                {"HEARTS", "🔥", "Trái tim & Nổi bật"},
                {"HANDS", "👍", "Cử chỉ & tương tác"},
                {"VIP", "💎", "Đấu giá & VIP"},
                {"ITEMS", "🏎️", "Siêu xe & Vật phẩm"}
        };
        String idleTabStyle = "-fx-background-color: transparent; -fx-background-radius: 9; -fx-padding: 4; -fx-cursor: hand;";
        String activeTabStyle = light
                ? "-fx-background-color: #DBEAFE; -fx-background-radius: 9; -fx-padding: 4; -fx-cursor: hand;"
                : "-fx-background-color: rgba(56,189,248,0.22); -fx-background-radius: 9; -fx-padding: 4; -fx-cursor: hand;";
        List<Button> tabButtons = new ArrayList<>();
        for (String[] t : tabs) {
            Button tb = new Button();
            tb.setGraphic(createEmojiView(t[1], 19));
            tb.setPrefSize(48, 34);
            tb.setStyle(idleTabStyle);
            tb.setTooltip(new Tooltip(t[2]));
            tb.setOnAction(e -> {
                activeCategory[0] = t[0];
                txtSearch.clear();
                lblCategoryTitle.setText(t[2]);
                for (Button b : tabButtons) {
                    b.setStyle(idleTabStyle);
                }
                tb.setStyle(activeTabStyle);
                renderGrid.run();
            });
            tabButtons.add(tb);
            tabBar.getChildren().add(tb);
        }
        tabButtons.get(0).setStyle(activeTabStyle);

        renderGrid.run();
        card.getChildren().addAll(searchRow, lblCategoryTitle, scroll, tabBar);
        popup.getContent().add(card);
        return popup;
    }

    public static void showAboveNode(Popup popup, Node anchor) {
        if (popup == null || anchor == null || anchor.getScene() == null || anchor.getScene().getWindow() == null) {
            return;
        }
        Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        double x = Math.max(12, b.getMaxX() - 346);
        double y = Math.max(12, b.getMinY() - 322);
        popup.show(anchor.getScene().getWindow(), x, y);
    }

    // ---------- Glossy 3D Vector Emoji Rasterizer (Zero-Dependency Full Color on Windows) ----------

    public static Image getEmojiImage(String code, int size) {
        String key = code + "_" + size;
        return IMAGE_CACHE.computeIfAbsent(key, k -> renderEmoji(code, size));
    }

    private static Image renderEmoji(String code, int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        switch (code) {
            case "😀" -> drawFace(g, s, "OPEN_SMILE");
            case "😄" -> drawFace(g, s, "HAPPY_EYES");
            case "😁" -> drawFace(g, s, "GRIN");
            case "😂" -> drawFace(g, s, "JOY");
            case "🤣" -> drawFace(g, s, "ROFL");
            case "😍" -> drawFace(g, s, "HEART_EYES");
            case "🥰" -> drawFace(g, s, "LOVING");
            case "😎" -> drawFace(g, s, "COOL");
            case "🤩" -> drawFace(g, s, "STAR_EYES");
            case "🥳" -> drawFace(g, s, "PARTY");
            case "😉" -> drawFace(g, s, "WINK");
            case "😜" -> drawFace(g, s, "TONGUE");
            case "🤑" -> drawFace(g, s, "MONEY");
            case "😮" -> drawFace(g, s, "WOW");
            case "🤔" -> drawFace(g, s, "THINK");
            case "😅" -> drawFace(g, s, "SWEAT");

            case "❤️" -> drawHeart(g, s, new Color(255, 71, 97), new Color(190, 18, 60), false);
            case "🧡" -> drawHeart(g, s, new Color(255, 165, 64), new Color(234, 88, 12), false);
            case "💛" -> drawHeart(g, s, new Color(254, 224, 96), new Color(217, 119, 6), false);
            case "💖" -> drawHeart(g, s, new Color(255, 117, 195), new Color(219, 39, 119), true);
            case "🔥" -> drawFlame(g, s);
            case "⚡" -> drawBolt(g, s);
            case "✨" -> drawSparkles(g, s);
            case "💯" -> drawHundred(g, s);

            case "👍" -> drawBadgeIcon(g, s, new Color(253, 211, 106), new Color(217, 119, 6), "LIKE");
            case "👏" -> drawBadgeIcon(g, s, new Color(253, 224, 130), new Color(234, 128, 22), "CLAP");
            case "🙌" -> drawBadgeIcon(g, s, new Color(251, 191, 36), new Color(217, 119, 6), "YAY");
            case "👌" -> drawBadgeIcon(g, s, new Color(52, 211, 153), new Color(5, 150, 105), "OK");
            case "🤝" -> drawBadgeIcon(g, s, new Color(96, 165, 250), new Color(37, 99, 235), "DEAL");
            case "💪" -> drawBadgeIcon(g, s, new Color(251, 146, 60), new Color(220, 38, 38), "PWR");

            case "💎" -> drawDiamond(g, s);
            case "👑" -> drawCrown(g, s);
            case "🏆" -> drawTrophy(g, s);
            case "💰" -> drawMoneyBag(g, s);
            case "🚀" -> drawRocket(g, s);
            case "🎉" -> drawPartyPopper(g, s);
            case "🎯" -> drawTarget(g, s);
            case "🎁" -> drawGift(g, s);

            case "🏎️" -> drawCar(g, s, new Color(239, 68, 68), new Color(251, 191, 36));
            case "🚗" -> drawCar(g, s, new Color(56, 189, 248), new Color(226, 232, 240));
            case "⌚" -> drawWatch(g, s);
            case "📷" -> drawCamera(g, s);
            case "💻" -> drawLaptop(g, s);
            case "🎨" -> drawPalette(g, s);
            case "🍾" -> drawBottle(g, s);
            case "💍" -> drawRing(g, s);
            default -> drawFace(g, s, "OPEN_SMILE");
        }

        g.dispose();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return new Image(new ByteArrayInputStream(baos.toByteArray()));
        } catch (Exception e) {
            return null;
        }
    }

    private static void drawFace(Graphics2D g, int s, String kind) {
        if ("ROFL".equals(kind)) {
            g.rotate(Math.toRadians(-16), s / 2.0, s / 2.0);
        }
        int pad = Math.max(3, s / 12);
        int d = s - pad * 2;
        RadialGradientPaint rgp = new RadialGradientPaint(
                new Point2D.Float(s * 0.36f, s * 0.30f),
                d * 0.72f,
                new float[]{0f, 0.58f, 1f},
                new Color[]{new Color(255, 243, 148), new Color(255, 200, 44), new Color(235, 134, 14)});
        g.setPaint(rgp);
        g.fillOval(pad, pad, d, d);
        g.setColor(new Color(180, 95, 10, 120));
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(pad, pad, d, d);

        // Specular highlight
        g.setColor(new Color(255, 255, 255, 75));
        g.fillOval((int) (s * 0.24), (int) (s * 0.14), (int) (s * 0.36), (int) (s * 0.18));

        Color brown = new Color(92, 48, 12);
        g.setColor(brown);

        switch (kind) {
            case "COOL" -> {
                g.setColor(new Color(24, 30, 44));
                g.fillRoundRect((int) (s * 0.15), (int) (s * 0.34), (int) (s * 0.31), (int) (s * 0.21), 8, 8);
                g.fillRoundRect((int) (s * 0.54), (int) (s * 0.34), (int) (s * 0.31), (int) (s * 0.21), 8, 8);
                g.setStroke(new BasicStroke(2.6f));
                g.drawLine((int) (s * 0.45), (int) (s * 0.40), (int) (s * 0.55), (int) (s * 0.40));
                g.setColor(new Color(255, 255, 255, 110));
                g.drawLine((int) (s * 0.20), (int) (s * 0.39), (int) (s * 0.30), (int) (s * 0.49));
                g.drawLine((int) (s * 0.59), (int) (s * 0.39), (int) (s * 0.69), (int) (s * 0.49));
                g.setColor(brown);
                g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Double(s * 0.30, s * 0.52, s * 0.40, s * 0.24, 200, 140, Arc2D.OPEN));
            }
            case "HEART_EYES" -> {
                drawMiniHeart(g, s * 0.32, s * 0.40, s * 0.15, new Color(239, 35, 60));
                drawMiniHeart(g, s * 0.68, s * 0.40, s * 0.15, new Color(239, 35, 60));
                drawOpenMouth(g, s, true);
            }
            case "STAR_EYES" -> {
                drawMiniStar(g, s * 0.32, s * 0.40, s * 0.14, new Color(147, 51, 234));
                drawMiniStar(g, s * 0.68, s * 0.40, s * 0.14, new Color(147, 51, 234));
                drawOpenMouth(g, s, true);
            }
            case "LOVING" -> {
                g.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Double(s * 0.24, s * 0.35, s * 0.16, s * 0.12, 10, 160, Arc2D.OPEN));
                g.draw(new Arc2D.Double(s * 0.60, s * 0.35, s * 0.16, s * 0.12, 10, 160, Arc2D.OPEN));
                g.draw(new Arc2D.Double(s * 0.34, s * 0.52, s * 0.32, s * 0.18, 200, 140, Arc2D.OPEN));
                drawMiniHeart(g, s * 0.18, s * 0.62, s * 0.11, new Color(244, 63, 94));
                drawMiniHeart(g, s * 0.82, s * 0.60, s * 0.11, new Color(244, 63, 94));
                drawMiniHeart(g, s * 0.78, s * 0.20, s * 0.09, new Color(244, 63, 94));
            }
            case "JOY", "ROFL" -> {
                g.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Double(s * 0.23, s * 0.34, s * 0.18, s * 0.13, 15, 150, Arc2D.OPEN));
                g.draw(new Arc2D.Double(s * 0.59, s * 0.34, s * 0.18, s * 0.13, 15, 150, Arc2D.OPEN));
                drawOpenMouth(g, s, true);
                // Blue tears of joy
                g.setColor(new Color(56, 189, 248));
                g.fillOval((int) (s * 0.10), (int) (s * 0.48), (int) (s * 0.15), (int) (s * 0.18));
                g.fillOval((int) (s * 0.75), (int) (s * 0.48), (int) (s * 0.15), (int) (s * 0.18));
            }
            case "PARTY" -> {
                // Party hat
                Polygon hat = new Polygon(
                        new int[]{(int) (s * 0.14), (int) (s * 0.44), (int) (s * 0.18)},
                        new int[]{(int) (s * 0.26), (int) (s * 0.15), (int) (s * 0.02)}, 3);
                g.setColor(new Color(59, 130, 246));
                g.fillPolygon(hat);
                g.setColor(new Color(236, 72, 153));
                g.fillOval((int) (s * 0.14), (int) (s * 0.01), (int) (s * 0.10), (int) (s * 0.10));
                g.setColor(brown);
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Double(s * 0.26, s * 0.37, s * 0.15, s * 0.11, 10, 160, Arc2D.OPEN));
                g.draw(new Arc2D.Double(s * 0.59, s * 0.37, s * 0.15, s * 0.11, 10, 160, Arc2D.OPEN));
                g.setColor(new Color(244, 63, 94));
                g.fillRoundRect((int) (s * 0.48), (int) (s * 0.62), (int) (s * 0.34), (int) (s * 0.11), 6, 6);
            }
            case "WINK" -> {
                g.fillOval((int) (s * 0.28), (int) (s * 0.35), (int) (s * 0.12), (int) (s * 0.14));
                g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Double(s * 0.58, s * 0.37, s * 0.16, s * 0.10, 10, 160, Arc2D.OPEN));
                g.draw(new Arc2D.Double(s * 0.28, s * 0.50, s * 0.44, s * 0.26, 200, 140, Arc2D.OPEN));
            }
            case "TONGUE" -> {
                g.fillOval((int) (s * 0.27), (int) (s * 0.34), (int) (s * 0.12), (int) (s * 0.14));
                g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Double(s * 0.58, s * 0.36, s * 0.16, s * 0.10, 10, 160, Arc2D.OPEN));
                drawOpenMouth(g, s, false);
                g.setColor(new Color(244, 63, 94));
                g.fillRoundRect((int) (s * 0.43), (int) (s * 0.66), (int) (s * 0.16), (int) (s * 0.20), 10, 10);
            }
            case "MONEY" -> {
                g.setColor(new Color(5, 150, 105));
                g.setFont(new Font("Segoe UI", Font.BOLD, (int) (s * 0.26)));
                g.drawString("$", (int) (s * 0.24), (int) (s * 0.47));
                g.drawString("$", (int) (s * 0.59), (int) (s * 0.47));
                drawOpenMouth(g, s, false);
                g.setColor(new Color(16, 185, 129));
                g.fillRoundRect((int) (s * 0.41), (int) (s * 0.64), (int) (s * 0.18), (int) (s * 0.22), 8, 8);
            }
            case "WOW" -> {
                g.fillOval((int) (s * 0.27), (int) (s * 0.33), (int) (s * 0.12), (int) (s * 0.15));
                g.fillOval((int) (s * 0.61), (int) (s * 0.33), (int) (s * 0.12), (int) (s * 0.15));
                g.fillOval((int) (s * 0.39), (int) (s * 0.55), (int) (s * 0.22), (int) (s * 0.24));
            }
            case "THINK" -> {
                g.fillOval((int) (s * 0.28), (int) (s * 0.36), (int) (s * 0.11), (int) (s * 0.13));
                g.fillOval((int) (s * 0.61), (int) (s * 0.34), (int) (s * 0.11), (int) (s * 0.13));
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine((int) (s * 0.36), (int) (s * 0.63), (int) (s * 0.62), (int) (s * 0.60));
                g.setColor(new Color(245, 158, 11));
                g.fillRoundRect((int) (s * 0.22), (int) (s * 0.72), (int) (s * 0.32), (int) (s * 0.14), 8, 8);
            }
            case "SWEAT" -> {
                g.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Double(s * 0.24, s * 0.35, s * 0.16, s * 0.11, 10, 160, Arc2D.OPEN));
                g.draw(new Arc2D.Double(s * 0.60, s * 0.35, s * 0.16, s * 0.11, 10, 160, Arc2D.OPEN));
                drawOpenMouth(g, s, true);
                g.setColor(new Color(56, 189, 248));
                g.fillOval((int) (s * 0.74), (int) (s * 0.14), (int) (s * 0.14), (int) (s * 0.22));
            }
            case "HAPPY_EYES" -> {
                g.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Double(s * 0.24, s * 0.35, s * 0.16, s * 0.12, 10, 160, Arc2D.OPEN));
                g.draw(new Arc2D.Double(s * 0.60, s * 0.35, s * 0.16, s * 0.12, 10, 160, Arc2D.OPEN));
                drawOpenMouth(g, s, true);
            }
            case "GRIN" -> {
                g.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Double(s * 0.24, s * 0.34, s * 0.16, s * 0.11, 10, 160, Arc2D.OPEN));
                g.draw(new Arc2D.Double(s * 0.60, s * 0.34, s * 0.16, s * 0.11, 10, 160, Arc2D.OPEN));
                g.setColor(Color.WHITE);
                g.fillRoundRect((int) (s * 0.24), (int) (s * 0.56), (int) (s * 0.52), (int) (s * 0.18), 12, 12);
                g.setColor(brown);
                g.setStroke(new BasicStroke(1.4f));
                g.drawRoundRect((int) (s * 0.24), (int) (s * 0.56), (int) (s * 0.52), (int) (s * 0.18), 12, 12);
            }
            default -> { // OPEN_SMILE
                g.fillOval((int) (s * 0.28), (int) (s * 0.33), (int) (s * 0.12), (int) (s * 0.15));
                g.fillOval((int) (s * 0.60), (int) (s * 0.33), (int) (s * 0.12), (int) (s * 0.15));
                drawOpenMouth(g, s, true);
            }
        }
    }

    private static void drawOpenMouth(Graphics2D g, int s, boolean teeth) {
        g.setColor(new Color(120, 42, 24));
        g.fill(new Arc2D.Double(s * 0.24, s * 0.45, s * 0.52, s * 0.36, 180, 180, Arc2D.CHORD));
        if (teeth) {
            g.setColor(Color.WHITE);
            g.fillRect((int) (s * 0.29), (int) (s * 0.63), (int) (s * 0.42), (int) (s * 0.07));
        }
        g.setColor(new Color(244, 63, 94));
        g.fill(new Arc2D.Double(s * 0.36, s * 0.69, s * 0.28, s * 0.13, 0, 180, Arc2D.CHORD));
    }

    private static void drawMiniHeart(Graphics2D g, double cx, double cy, double r, Color color) {
        GeneralPath p = new GeneralPath();
        p.moveTo(cx, cy + r * 0.9);
        p.curveTo(cx - r * 1.4, cy, cx - r * 1.1, cy - r * 1.1, cx, cy - r * 0.35);
        p.curveTo(cx + r * 1.1, cy - r * 1.1, cx + r * 1.4, cy, cx, cy + r * 0.9);
        p.closePath();
        g.setColor(color);
        g.fill(p);
    }

    private static void drawMiniStar(Graphics2D g, double cx, double cy, double r, Color color) {
        Polygon star = new Polygon();
        for (int i = 0; i < 10; i++) {
            double rad = (i % 2 == 0) ? r : r * 0.45;
            double angle = Math.toRadians(-90 + i * 36);
            star.addPoint((int) Math.round(cx + Math.cos(angle) * rad), (int) Math.round(cy + Math.sin(angle) * rad));
        }
        g.setColor(color);
        g.fillPolygon(star);
    }

    private static void drawHeart(Graphics2D g, int s, Color c1, Color c2, boolean sparkles) {
        GeneralPath p = new GeneralPath();
        double cx = s * 0.5;
        p.moveTo(cx, s * 0.86);
        p.curveTo(s * 0.04, s * 0.55, s * 0.08, s * 0.12, cx, s * 0.32);
        p.curveTo(s * 0.92, s * 0.12, s * 0.96, s * 0.55, cx, s * 0.86);
        p.closePath();
        g.setPaint(new GradientPaint(0, 0, c1, s, s, c2));
        g.fill(p);
        g.setColor(new Color(255, 255, 255, 95));
        g.fillOval((int) (s * 0.22), (int) (s * 0.25), (int) (s * 0.18), (int) (s * 0.12));
        if (sparkles) {
            drawMiniStar(g, s * 0.82, s * 0.22, s * 0.12, new Color(253, 224, 71));
            drawMiniStar(g, s * 0.20, s * 0.78, s * 0.09, new Color(253, 224, 71));
        }
    }

    private static void drawFlame(Graphics2D g, int s) {
        GeneralPath outer = new GeneralPath();
        outer.moveTo(s * 0.50, s * 0.08);
        outer.curveTo(s * 0.86, s * 0.38, s * 0.90, s * 0.88, s * 0.50, s * 0.92);
        outer.curveTo(s * 0.10, s * 0.88, s * 0.14, s * 0.40, s * 0.50, s * 0.08);
        outer.closePath();
        g.setPaint(new GradientPaint(0, s * 0.1f, new Color(255, 196, 0), 0, s * 0.9f, new Color(239, 45, 45)));
        g.fill(outer);

        GeneralPath inner = new GeneralPath();
        inner.moveTo(s * 0.50, s * 0.36);
        inner.curveTo(s * 0.70, s * 0.56, s * 0.70, s * 0.84, s * 0.50, s * 0.86);
        inner.curveTo(s * 0.30, s * 0.84, s * 0.30, s * 0.56, s * 0.50, s * 0.36);
        inner.closePath();
        g.setColor(new Color(254, 240, 138));
        g.fill(inner);
    }

    private static void drawBolt(Graphics2D g, int s) {
        Polygon p = new Polygon(
                new int[]{(int) (s * 0.58), (int) (s * 0.20), (int) (s * 0.48), (int) (s * 0.38), (int) (s * 0.80), (int) (s * 0.52)},
                new int[]{(int) (s * 0.06), (int) (s * 0.54), (int) (s * 0.54), (int) (s * 0.94), (int) (s * 0.44), (int) (s * 0.44)}, 6);
        g.setPaint(new GradientPaint(0, 0, new Color(254, 240, 138), s, s, new Color(245, 158, 11)));
        g.fillPolygon(p);
    }

    private static void drawSparkles(Graphics2D g, int s) {
        drawMiniStar(g, s * 0.42, s * 0.46, s * 0.32, new Color(250, 204, 21));
        drawMiniStar(g, s * 0.76, s * 0.24, s * 0.17, new Color(56, 189, 248));
        drawMiniStar(g, s * 0.74, s * 0.74, s * 0.15, new Color(244, 114, 182));
    }

    private static void drawHundred(Graphics2D g, int s) {
        g.setColor(new Color(239, 68, 68));
        g.setFont(new Font("Segoe UI", Font.BOLD, (int) (s * 0.44)));
        FontMetrics fm = g.getFontMetrics();
        String txt = "100";
        g.drawString(txt, (s - fm.stringWidth(txt)) / 2, (int) (s * 0.58));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) (s * 0.16), (int) (s * 0.72), (int) (s * 0.84), (int) (s * 0.68));
        g.drawLine((int) (s * 0.22), (int) (s * 0.83), (int) (s * 0.78), (int) (s * 0.79));
    }

    private static void drawBadgeIcon(Graphics2D g, int s, Color c1, Color c2, String label) {
        g.setPaint(new GradientPaint(0, 0, c1, s, s, c2));
        g.fillRoundRect(4, 6, s - 8, s - 12, 14, 14);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, (int) (s * (label.length() > 3 ? 0.25 : 0.30))));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, (s - fm.stringWidth(label)) / 2, (s + fm.getAscent() - fm.getDescent()) / 2);
    }

    private static void drawDiamond(Graphics2D g, int s) {
        Polygon top = new Polygon(
                new int[]{(int) (s * 0.24), (int) (s * 0.76), (int) (s * 0.92), (int) (s * 0.08)},
                new int[]{(int) (s * 0.18), (int) (s * 0.18), (int) (s * 0.40), (int) (s * 0.40)}, 4);
        g.setColor(new Color(125, 211, 252));
        g.fillPolygon(top);
        Polygon bottom = new Polygon(
                new int[]{(int) (s * 0.08), (int) (s * 0.92), (int) (s * 0.50)},
                new int[]{(int) (s * 0.40), (int) (s * 0.40), (int) (s * 0.88)}, 3);
        g.setPaint(new GradientPaint(0, s * 0.4f, new Color(56, 189, 248), s, s, new Color(2, 132, 199)));
        g.fillPolygon(bottom);
        g.setColor(new Color(224, 242, 254, 160));
        Polygon facet = new Polygon(
                new int[]{(int) (s * 0.32), (int) (s * 0.68), (int) (s * 0.50)},
                new int[]{(int) (s * 0.40), (int) (s * 0.40), (int) (s * 0.88)}, 3);
        g.fillPolygon(facet);
    }

    private static void drawCrown(Graphics2D g, int s) {
        Polygon p = new Polygon(
                new int[]{(int) (s * 0.12), (int) (s * 0.24), (int) (s * 0.36), (int) (s * 0.50), (int) (s * 0.64), (int) (s * 0.76), (int) (s * 0.88), (int) (s * 0.82), (int) (s * 0.18)},
                new int[]{(int) (s * 0.34), (int) (s * 0.55), (int) (s * 0.24), (int) (s * 0.52), (int) (s * 0.24), (int) (s * 0.55), (int) (s * 0.34), (int) (s * 0.78), (int) (s * 0.78)}, 9);
        g.setPaint(new GradientPaint(0, 0, new Color(254, 240, 138), s, s, new Color(217, 119, 6)));
        g.fillPolygon(p);
        g.setColor(new Color(239, 68, 68));
        g.fillOval((int) (s * 0.45), (int) (s * 0.60), (int) (s * 0.10), (int) (s * 0.10));
    }

    private static void drawTrophy(Graphics2D g, int s) {
        g.setPaint(new GradientPaint(0, 0, new Color(253, 224, 71), s, s, new Color(217, 119, 6)));
        g.fillRoundRect((int) (s * 0.24), (int) (s * 0.14), (int) (s * 0.52), (int) (s * 0.44), 14, 18);
        g.fillRect((int) (s * 0.43), (int) (s * 0.56), (int) (s * 0.14), (int) (s * 0.18));
        g.fillRoundRect((int) (s * 0.28), (int) (s * 0.72), (int) (s * 0.44), (int) (s * 0.14), 6, 6);
        g.setStroke(new BasicStroke(3f));
        g.drawOval((int) (s * 0.12), (int) (s * 0.20), (int) (s * 0.18), (int) (s * 0.22));
        g.drawOval((int) (s * 0.70), (int) (s * 0.20), (int) (s * 0.18), (int) (s * 0.22));
    }

    private static void drawMoneyBag(Graphics2D g, int s) {
        g.setPaint(new GradientPaint(0, 0, new Color(253, 230, 138), s, s, new Color(180, 120, 40)));
        g.fillOval((int) (s * 0.16), (int) (s * 0.30), (int) (s * 0.68), (int) (s * 0.60));
        g.fillRoundRect((int) (s * 0.32), (int) (s * 0.14), (int) (s * 0.36), (int) (s * 0.22), 8, 8);
        g.setColor(new Color(220, 38, 38));
        g.fillRect((int) (s * 0.30), (int) (s * 0.30), (int) (s * 0.40), (int) (s * 0.08));
        g.setColor(new Color(22, 101, 52));
        g.setFont(new Font("Segoe UI", Font.BOLD, (int) (s * 0.34)));
        g.drawString("$", (int) (s * 0.40), (int) (s * 0.72));
    }

    private static void drawRocket(Graphics2D g, int s) {
        g.rotate(Math.toRadians(35), s / 2.0, s / 2.0);
        g.setPaint(new GradientPaint(0, 0, Color.WHITE, s, s, new Color(203, 213, 225)));
        g.fillOval((int) (s * 0.33), (int) (s * 0.12), (int) (s * 0.34), (int) (s * 0.62));
        g.setColor(new Color(239, 68, 68));
        g.fillOval((int) (s * 0.39), (int) (s * 0.10), (int) (s * 0.22), (int) (s * 0.20));
        g.fillPolygon(new int[]{(int) (s * 0.33), (int) (s * 0.18), (int) (s * 0.35)},
                new int[]{(int) (s * 0.52), (int) (s * 0.72), (int) (s * 0.70)}, 3);
        g.fillPolygon(new int[]{(int) (s * 0.67), (int) (s * 0.82), (int) (s * 0.65)},
                new int[]{(int) (s * 0.52), (int) (s * 0.72), (int) (s * 0.70)}, 3);
        g.setColor(new Color(56, 189, 248));
        g.fillOval((int) (s * 0.42), (int) (s * 0.34), (int) (s * 0.16), (int) (s * 0.16));
        g.setColor(new Color(251, 146, 60));
        g.fillOval((int) (s * 0.41), (int) (s * 0.72), (int) (s * 0.18), (int) (s * 0.18));
    }

    private static void drawPartyPopper(Graphics2D g, int s) {
        Polygon cone = new Polygon(
                new int[]{(int) (s * 0.14), (int) (s * 0.56), (int) (s * 0.34)},
                new int[]{(int) (s * 0.86), (int) (s * 0.64), (int) (s * 0.42)}, 3);
        g.setPaint(new GradientPaint(0, s, new Color(245, 158, 11), s, 0, new Color(236, 72, 153)));
        g.fillPolygon(cone);
        g.setColor(new Color(56, 189, 248));
        g.fillOval((int) (s * 0.54), (int) (s * 0.20), (int) (s * 0.14), (int) (s * 0.14));
        g.setColor(new Color(250, 204, 21));
        g.fillOval((int) (s * 0.74), (int) (s * 0.36), (int) (s * 0.12), (int) (s * 0.12));
        g.setColor(new Color(52, 211, 153));
        g.fillOval((int) (s * 0.38), (int) (s * 0.16), (int) (s * 0.11), (int) (s * 0.11));
        g.setColor(new Color(244, 63, 94));
        g.fillOval((int) (s * 0.68), (int) (s * 0.56), (int) (s * 0.12), (int) (s * 0.12));
    }

    private static void drawTarget(Graphics2D g, int s) {
        g.setColor(new Color(239, 68, 68));
        g.fillOval(5, 5, s - 10, s - 10);
        g.setColor(Color.WHITE);
        g.fillOval((int) (s * 0.22), (int) (s * 0.22), (int) (s * 0.56), (int) (s * 0.56));
        g.setColor(new Color(239, 68, 68));
        g.fillOval((int) (s * 0.35), (int) (s * 0.35), (int) (s * 0.30), (int) (s * 0.30));
    }

    private static void drawGift(Graphics2D g, int s) {
        g.setColor(new Color(239, 68, 68));
        g.fillRoundRect((int) (s * 0.16), (int) (s * 0.34), (int) (s * 0.68), (int) (s * 0.52), 8, 8);
        g.setColor(new Color(250, 204, 21));
        g.fillRect((int) (s * 0.44), (int) (s * 0.34), (int) (s * 0.12), (int) (s * 0.52));
        g.fillRect((int) (s * 0.16), (int) (s * 0.54), (int) (s * 0.68), (int) (s * 0.11));
        g.setStroke(new BasicStroke(3f));
        g.drawOval((int) (s * 0.28), (int) (s * 0.18), (int) (s * 0.20), (int) (s * 0.16));
        g.drawOval((int) (s * 0.52), (int) (s * 0.18), (int) (s * 0.20), (int) (s * 0.16));
    }

    private static void drawCar(Graphics2D g, int s, Color body, Color stripe) {
        g.setColor(body);
        g.fillRoundRect((int) (s * 0.08), (int) (s * 0.46), (int) (s * 0.84), (int) (s * 0.26), 12, 12);
        Polygon roof = new Polygon(
                new int[]{(int) (s * 0.24), (int) (s * 0.38), (int) (s * 0.66), (int) (s * 0.78)},
                new int[]{(int) (s * 0.48), (int) (s * 0.30), (int) (s * 0.30), (int) (s * 0.48)}, 4);
        g.fillPolygon(roof);
        g.setColor(new Color(186, 230, 253));
        g.fillRoundRect((int) (s * 0.38), (int) (s * 0.34), (int) (s * 0.26), (int) (s * 0.12), 4, 4);
        g.setColor(stripe);
        g.fillRect((int) (s * 0.12), (int) (s * 0.56), (int) (s * 0.76), (int) (s * 0.05));
        g.setColor(new Color(15, 23, 42));
        g.fillOval((int) (s * 0.18), (int) (s * 0.64), (int) (s * 0.20), (int) (s * 0.20));
        g.fillOval((int) (s * 0.62), (int) (s * 0.64), (int) (s * 0.20), (int) (s * 0.20));
        g.setColor(new Color(226, 232, 240));
        g.fillOval((int) (s * 0.23), (int) (s * 0.69), (int) (s * 0.10), (int) (s * 0.10));
        g.fillOval((int) (s * 0.67), (int) (s * 0.69), (int) (s * 0.10), (int) (s * 0.10));
    }

    private static void drawWatch(Graphics2D g, int s) {
        g.setColor(new Color(148, 163, 184));
        g.fillRoundRect((int) (s * 0.34), (int) (s * 0.08), (int) (s * 0.32), (int) (s * 0.84), 8, 8);
        g.setColor(new Color(245, 158, 11));
        g.fillOval((int) (s * 0.20), (int) (s * 0.20), (int) (s * 0.60), (int) (s * 0.60));
        g.setColor(new Color(15, 23, 42));
        g.fillOval((int) (s * 0.26), (int) (s * 0.26), (int) (s * 0.48), (int) (s * 0.48));
        g.setColor(new Color(253, 224, 71));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(s / 2, s / 2, s / 2, (int) (s * 0.34));
        g.drawLine(s / 2, s / 2, (int) (s * 0.63), s / 2);
    }

    private static void drawCamera(Graphics2D g, int s) {
        g.setColor(new Color(51, 65, 85));
        g.fillRoundRect((int) (s * 0.12), (int) (s * 0.28), (int) (s * 0.76), (int) (s * 0.52), 10, 10);
        g.fillRoundRect((int) (s * 0.24), (int) (s * 0.20), (int) (s * 0.24), (int) (s * 0.12), 6, 6);
        g.setColor(new Color(203, 213, 225));
        g.fillOval((int) (s * 0.32), (int) (s * 0.36), (int) (s * 0.36), (int) (s * 0.36));
        g.setColor(new Color(14, 165, 233));
        g.fillOval((int) (s * 0.39), (int) (s * 0.43), (int) (s * 0.22), (int) (s * 0.22));
    }

    private static void drawLaptop(Graphics2D g, int s) {
        g.setColor(new Color(71, 85, 105));
        g.fillRoundRect((int) (s * 0.18), (int) (s * 0.20), (int) (s * 0.64), (int) (s * 0.46), 6, 6);
        g.setColor(new Color(56, 189, 248));
        g.fillRect((int) (s * 0.23), (int) (s * 0.25), (int) (s * 0.54), (int) (s * 0.36));
        g.setColor(new Color(148, 163, 184));
        g.fillRoundRect((int) (s * 0.08), (int) (s * 0.68), (int) (s * 0.84), (int) (s * 0.10), 6, 6);
    }

    private static void drawPalette(Graphics2D g, int s) {
        g.setColor(new Color(253, 230, 138));
        g.fillOval((int) (s * 0.12), (int) (s * 0.16), (int) (s * 0.76), (int) (s * 0.68));
        g.setColor(new Color(239, 68, 68));
        g.fillOval((int) (s * 0.26), (int) (s * 0.30), (int) (s * 0.14), (int) (s * 0.14));
        g.setColor(new Color(59, 130, 246));
        g.fillOval((int) (s * 0.46), (int) (s * 0.24), (int) (s * 0.14), (int) (s * 0.14));
        g.setColor(new Color(16, 185, 129));
        g.fillOval((int) (s * 0.64), (int) (s * 0.36), (int) (s * 0.14), (int) (s * 0.14));
        g.setColor(new Color(168, 85, 247));
        g.fillOval((int) (s * 0.28), (int) (s * 0.52), (int) (s * 0.14), (int) (s * 0.14));
    }

    private static void drawBottle(Graphics2D g, int s) {
        g.setColor(new Color(21, 128, 61));
        g.fillRoundRect((int) (s * 0.34), (int) (s * 0.36), (int) (s * 0.32), (int) (s * 0.52), 10, 10);
        g.fillRect((int) (s * 0.43), (int) (s * 0.14), (int) (s * 0.14), (int) (s * 0.24));
        g.setColor(new Color(250, 204, 21));
        g.fillRect((int) (s * 0.36), (int) (s * 0.52), (int) (s * 0.28), (int) (s * 0.16));
    }

    private static void drawRing(Graphics2D g, int s) {
        g.setColor(new Color(203, 213, 225));
        g.setStroke(new BasicStroke(4.5f));
        g.drawOval((int) (s * 0.22), (int) (s * 0.34), (int) (s * 0.56), (int) (s * 0.52));
        Polygon gem = new Polygon(
                new int[]{(int) (s * 0.34), (int) (s * 0.66), (int) (s * 0.50)},
                new int[]{(int) (s * 0.24), (int) (s * 0.24), (int) (s * 0.42)}, 3);
        g.setColor(new Color(56, 189, 248));
        g.fillPolygon(gem);
    }
}
