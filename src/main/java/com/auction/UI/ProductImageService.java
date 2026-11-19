package com.auction.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;

/**
 * Quản lý lưu trữ và nạp ảnh sản phẩm cho thẻ đấu giá và phòng đấu giá.
 * Hỗ trợ upload ảnh từ máy tính, lưu vào thư mục data/images/ và tự động tạo ảnh mẫu sắc nét.
 */
public final class ProductImageService {

    private static final String IMAGE_DIR = "data/images";
    private static final String REGISTRY_FILE = "data/images/image_registry.properties";

    private static final Map<String, String> registry = new ConcurrentHashMap<>();
    private static final Map<String, Image> memoryCache = new ConcurrentHashMap<>();

    static {
        initDirectory();
        loadRegistry();
        initDefaultSampleImages();
    }

    private ProductImageService() {
    }

    private static void initDirectory() {
        File dir = new File(IMAGE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private static void loadRegistry() {
        File file = new File(REGISTRY_FILE);
        if (file.exists()) {
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
                for (String key : props.stringPropertyNames()) {
                    registry.put(key, props.getProperty(key));
                }
            } catch (IOException e) {
                System.err.println("[ProductImageService] Lỗi nạp registry ảnh: " + e.getMessage());
            }
        }
    }

    private static void saveRegistry() {
        File file = new File(REGISTRY_FILE);
        Properties props = new Properties();
        props.putAll(registry);
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "Auction Product Image Registry");
        } catch (IOException e) {
            System.err.println("[ProductImageService] Lỗi lưu registry ảnh: " + e.getMessage());
        }
    }

    /**
     * Lưu ảnh người dùng upload vào thư mục data/images/ và đăng ký khóa.
     */
    public static String saveProductImage(String key, File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            return null;
        }

        initDirectory();
        String extension = "";
        String name = sourceFile.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = name.substring(dotIndex);
        } else {
            extension = ".png";
        }

        String safeKey = key.replaceAll("[^a-zA-Z0-9_-]", "_");
        String targetFileName = "IMG_" + safeKey + "_" + System.currentTimeMillis() + extension;
        File targetFile = new File(IMAGE_DIR, targetFileName);

        try {
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            String absolutePath = targetFile.getAbsolutePath();
            registerImage(key, absolutePath);
            return absolutePath;
        } catch (IOException e) {
            System.err.println("[ProductImageService] Không thể sao chép ảnh upload: " + e.getMessage());
            return sourceFile.getAbsolutePath();
        }
    }

    public static void registerImage(String key, String imagePath) {
        if (key != null && imagePath != null) {
            registry.put(key.toLowerCase(), imagePath);
            memoryCache.remove(key.toLowerCase());
            saveRegistry();
        }
    }

    /**
     * Nạp ảnh vào ImageView với góc bo tròn tự động.
     */
    public static void loadIntoImageView(ImageView imageView, String id, String name, String directPath, double width, double height) {
        if (imageView == null) {
            return;
        }

        Image image = getImage(id, name, directPath, width, height);
        imageView.setImage(image);
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(false);

        // Bo góc mềm mại cho ảnh
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        imageView.setClip(clip);
    }

    public static void loadIntoImageView(ImageView imageView, String id, String name, double width, double height) {
        loadIntoImageView(imageView, id, name, null, width, height);
    }

    /**
     * Lấy đối tượng Image tương ứng theo id, tên hoặc đường dẫn trực tiếp.
     */
    public static Image getImage(String id, String name, String directPath, double width, double height) {
        // 1. Kiểm tra directPath nếu có
        if (directPath != null && !directPath.isBlank()) {
            File directFile = new File(directPath);
            if (directFile.exists()) {
                return new Image(directFile.toURI().toString(), width, height, false, true);
            }
        }

        // 2. Tra cứu trong registry theo ID
        if (id != null) {
            String path = registry.get(id.toLowerCase());
            if (path != null) {
                File file = new File(path);
                if (file.exists()) {
                    return new Image(file.toURI().toString(), width, height, false, true);
                }
            }
        }

        // 3. Tra cứu trong registry theo Name
        if (name != null) {
            String path = registry.get(name.toLowerCase());
            if (path != null) {
                File file = new File(path);
                if (file.exists()) {
                    return new Image(file.toURI().toString(), width, height, false, true);
                }
            }
        }

        // 4. Fallback: Tạo ảnh placeholder danh mục sang trọng
        String cacheKey = (name != null ? name : "default") + "_" + (int) width + "_" + (int) height;
        return memoryCache.computeIfAbsent(cacheKey, k -> generatePlaceholderImage(name, (int) width, (int) height));
    }

    /**
     * Sinh ảnh Dark Luxury đại diện cho sản phẩm nếu chưa có ảnh upload.
     */
    private static Image generatePlaceholderImage(String productName, int width, int height) {
        int w = Math.max(width, 240);
        int h = Math.max(height, 140);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        String lower = (productName != null) ? productName.toLowerCase() : "";
        Color topColor;
        Color bottomColor;
        Color accentColor;
        String categoryTag;
        String iconText;

        if (lower.contains("đồng hồ") || lower.contains("watch")) {
            topColor = new Color(30, 27, 75);
            bottomColor = new Color(15, 23, 42);
            accentColor = new Color(245, 158, 11);
            categoryTag = "[ ĐỒNG HỒ CAO CẤP ]";
            iconText = "LUXURY TIMEPIECE";
        } else if (lower.contains("dell") || lower.contains("laptop") || lower.contains("máy ảnh") || lower.contains("camera")) {
            topColor = new Color(15, 23, 42);
            bottomColor = new Color(2, 6, 23);
            accentColor = new Color(56, 189, 248);
            categoryTag = "[ THIẾT BỊ ĐIỆN TỬ ]";
            iconText = "TECH & GADGETS";
        } else if (lower.contains("bình") || lower.contains("vase") || lower.contains("tranh") || lower.contains("art")) {
            topColor = new Color(46, 16, 101);
            bottomColor = new Color(15, 23, 42);
            accentColor = new Color(236, 72, 153);
            categoryTag = "[ NGHỆ THUẬT & CỔ VẬT ]";
            iconText = "VINTAGE ANTIQUE";
        } else if (lower.contains("xe") || lower.contains("car") || lower.contains("auto")) {
            topColor = new Color(69, 10, 10);
            bottomColor = new Color(15, 23, 42);
            accentColor = new Color(239, 68, 68);
            categoryTag = "[ PHƯƠNG TIỆN GIAO THÔNG ]";
            iconText = "PREMIUM VEHICLE";
        } else {
            topColor = new Color(20, 24, 39);
            bottomColor = new Color(11, 15, 25);
            accentColor = new Color(245, 158, 11);
            categoryTag = "[ SẢN PHẨM ĐẤU GIÁ ]";
            iconText = "AUCTION ITEM";
        }

        // Vẽ gradient nền
        java.awt.GradientPaint gp = new java.awt.GradientPaint(0, 0, topColor, 0, h, bottomColor);
        g.setPaint(gp);
        g.fillRect(0, 0, w, h);

        // Vẽ viền tinh tế
        g.setColor(new Color(255, 255, 255, 25));
        g.drawRect(2, 2, w - 5, h - 5);

        // Vẽ Category tag pill
        g.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 40));
        g.fillRoundRect(w / 2 - 80, 20, 160, 24, 8, 8);
        g.setColor(accentColor);
        g.drawRoundRect(w / 2 - 80, 20, 160, 24, 8, 8);
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics fmTag = g.getFontMetrics();
        g.drawString(categoryTag, (w - fmTag.stringWidth(categoryTag)) / 2, 36);

        // Vẽ Tiêu đề nổi bật
        g.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g.setColor(Color.WHITE);
        FontMetrics fmIcon = g.getFontMetrics();
        g.drawString(iconText, (w - fmIcon.stringWidth(iconText)) / 2, h / 2 + 10);

        // Vẽ Tên sản phẩm tóm tắt ở dưới
        String displayTitle = (productName != null && !productName.isBlank()) ? productName : "Phiên đấu giá trực tuyến";
        if (displayTitle.length() > 28) {
            displayTitle = displayTitle.substring(0, 25) + "...";
        }
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(156, 163, 175));
        FontMetrics fmTitle = g.getFontMetrics();
        g.drawString(displayTitle, (w - fmTitle.stringWidth(displayTitle)) / 2, h - 22);

        g.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return new Image(new ByteArrayInputStream(baos.toByteArray()), width, height, false, true);
        } catch (IOException e) {
            return null;
        }
    }

    private static void initDefaultSampleImages() {
        // Tự động tạo ảnh mẫu cho AUC-001 (Laptop) và AUC-002 (Vase) nếu chưa có
        File sample1 = new File(IMAGE_DIR, "auc_001_dell_xps.png");
        if (!sample1.exists()) {
            createAndSaveImageFile(sample1, "Laptop Dell XPS 15", "[ THIẾT BỊ ĐIỆN TỬ ]", "DELL XPS 15 ULTRABOOK", new Color(15, 23, 42), new Color(2, 6, 23), new Color(56, 189, 248));
        }
        if (sample1.exists() && !registry.containsKey("auc-001")) {
            registry.put("auc-001", sample1.getAbsolutePath());
        }

        File sample2 = new File(IMAGE_DIR, "auc_002_antique_vase.png");
        if (!sample2.exists()) {
            createAndSaveImageFile(sample2, "Vintage Antique Vase", "[ NGHỆ THUẬT & CỔ VẬT ]", "VINTAGE PORCELAIN VASE", new Color(46, 16, 101), new Color(15, 23, 42), new Color(236, 72, 153));
        }
        if (sample2.exists() && !registry.containsKey("auc-002")) {
            registry.put("auc-002", sample2.getAbsolutePath());
        }

        saveRegistry();
    }

    private static void createAndSaveImageFile(File target, String title, String tag, String highlight, Color top, Color bottom, Color accent) {
        int w = 500;
        int h = 260;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        java.awt.GradientPaint gp = new java.awt.GradientPaint(0, 0, top, 0, h, bottom);
        g.setPaint(gp);
        g.fillRect(0, 0, w, h);

        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 35));
        g.fillRoundRect(w / 2 - 110, 30, 220, 30, 8, 8);
        g.setColor(accent);
        g.drawRoundRect(w / 2 - 110, 30, 220, 30, 8, 8);
        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        FontMetrics fmTag = g.getFontMetrics();
        g.drawString(tag, (w - fmTag.stringWidth(tag)) / 2, 50);

        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        g.setColor(Color.WHITE);
        FontMetrics fmH = g.getFontMetrics();
        g.drawString(highlight, (w - fmH.stringWidth(highlight)) / 2, h / 2 + 15);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g.setColor(new Color(156, 163, 175));
        FontMetrics fmT = g.getFontMetrics();
        g.drawString(title, (w - fmT.stringWidth(title)) / 2, h - 35);

        g.dispose();

        try {
            ImageIO.write(img, "png", target);
        } catch (IOException e) {
            System.err.println("[ProductImageService] Lỗi tạo file ảnh mẫu: " + e.getMessage());
        }
    }
}
