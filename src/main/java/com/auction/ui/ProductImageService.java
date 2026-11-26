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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;

/**
 * Dịch vụ quản lý ảnh studio cao cấp và ảnh upload cho BidX.
 * Ưu tiên ảnh upload của Seller, sau đó ảnh studio đóng gói sẵn trong resources (/com/auction/images/),
 * cuối cùng mới fallback sang ảnh đồ họa dự phòng.
 */
public final class ProductImageService {

    private static final String IMAGE_DIR = "data/images";
    private static final String REGISTRY_FILE = "data/images/image_registry.properties";
    private static final String RESOURCE_PREFIX = "/com/auction/images/";

    private static final Map<String, String> registry = new ConcurrentHashMap<>();
    private static final Map<String, Image> memoryCache = new ConcurrentHashMap<>();

    /** Góc nhìn chi tiết cùng một sản phẩm (không bao giờ trộn ảnh sản phẩm khác) */
    public record GalleryAngle(String label, double vx, double vy, double vw, double vh) {
    }

    public static final List<GalleryAngle> GALLERY_ANGLES = List.of(
            new GalleryAngle("Toàn cảnh", 0.00, 0.00, 1.00, 1.00),
            new GalleryAngle("Cận cảnh", 0.14, 0.12, 0.72, 0.76),
            new GalleryAngle("Góc trái", 0.02, 0.10, 0.68, 0.80),
            new GalleryAngle("Góc phải", 0.30, 0.10, 0.68, 0.80));

    /** Danh sách ảnh studio mẫu để Seller chọn nhanh khi tạo yêu cầu mở phòng đấu giá */
    public static final Map<String, String> STUDIO_PRESETS = new LinkedHashMap<>();

    static {
        // Siêu Xe
        STUDIO_PRESETS.put("item_vehicle_porsche", "Siêu Xe: Porsche 911 Classic Silver");
        STUDIO_PRESETS.put("item_car_lambo", "Siêu Xe: Lamborghini Aventador SVJ");
        STUDIO_PRESETS.put("item_car_ferrari", "Siêu Xe: Ferrari SF90 Stradale");
        STUDIO_PRESETS.put("item_car_g63", "Siêu Xe: Mercedes-AMG G63 Matte");
        STUDIO_PRESETS.put("item_car_rollsroyce", "Siêu Xe: Rolls-Royce Phantom VIII");

        // Đồng hồ cổ & Luxury
        STUDIO_PRESETS.put("item_watch_classic", "Đồng hồ: Cơ Thụy Sĩ Vintage Gold");
        STUDIO_PRESETS.put("item_watch_skeleton", "Đồng hồ: Skeleton Tourbillon");
        STUDIO_PRESETS.put("item_watch_rolex", "Đồng hồ: Rolex Daytona 18K Gold");
        STUDIO_PRESETS.put("item_watch_royaloak", "Đồng hồ: Royal Oak Blue Dial");

        // Đồ điện tử & Hi-End
        STUDIO_PRESETS.put("item_laptop_xps", "Điện tử: Laptop Dell XPS 15 OLED");
        STUDIO_PRESETS.put("item_camera_leica", "Điện tử: Máy ảnh Film Leica M3");
        STUDIO_PRESETS.put("item_macbook_pro", "Điện tử: MacBook Pro M4 Max");
        STUDIO_PRESETS.put("item_headphone_hifi", "Điện tử: Tai nghe Hi-End Focal");

        // Nghệ thuật & Cổ vật
        STUDIO_PRESETS.put("item_vase_antique", "Nghệ thuật: Bình sứ cổ Thanh Hoa");
        STUDIO_PRESETS.put("item_art_painting", "Nghệ thuật: Tranh sơn dầu Hoàng Hôn");
        STUDIO_PRESETS.put("item_art_sculpture", "Nghệ thuật: Tượng Đồng Khổng Tước");
        STUDIO_PRESETS.put("item_art_piano", "Nghệ thuật: Grand Piano Steinway");

        // Khác (Xa xỉ & Sưu tầm độc bản)
        STUDIO_PRESETS.put("item_other_birkin", "Khác: Túi Hermès Birkin Da Cá Sấu");
        STUDIO_PRESETS.put("item_other_perfume", "Khác: Nước Hoa Hoàng Gia Oud Amber");
        STUDIO_PRESETS.put("item_other_whisky", "Khác: Rượu Whisky Chivas Regal Collector");
        STUDIO_PRESETS.put("item_other_diamond", "Khác: Nhẫn Kim Cương 3.5 Carat GIA");
        STUDIO_PRESETS.put("item_other_chess", "Khác: Bộ Cờ Vua Đá Cẩm Thạch Ý");

        initDirectory();
        loadRegistry();
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
            props.store(out, "BidX Product Image Registry");
        } catch (IOException e) {
            System.err.println("[ProductImageService] Lỗi lưu registry ảnh: " + e.getMessage());
        }
    }

    public static Image getBrandLogo(double size) {
        String key = "BRAND_LOGO_" + (int) size;
        return memoryCache.computeIfAbsent(key, k -> loadBundledResource("bidx_logo", size, size));
    }

    public static String saveProductImage(String key, File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            return null;
        }
        initDirectory();
        String name = sourceFile.getName();
        int dotIndex = name.lastIndexOf('.');
        String extension = (dotIndex > 0) ? name.substring(dotIndex) : ".png";

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
            registry.put(key.toLowerCase(Locale.ROOT), imagePath);
            memoryCache.clear();
            saveRegistry();
        }
    }

    public static void loadIntoImageView(
            ImageView imageView,
            String id,
            String name,
            String directPathOrKey,
            double width,
            double height) {
        loadIntoImageView(imageView, id, name, directPathOrKey, width, height, 14);
    }

    public static void loadIntoImageView(
            ImageView imageView,
            String id,
            String name,
            String directPathOrKey,
            double width,
            double height,
            double cornerRadius) {
        loadIntoImageViewWithAngle(imageView, id, name, directPathOrKey, width, height, cornerRadius, GALLERY_ANGLES.get(0));
    }

    public static void loadIntoImageViewWithAngle(
            ImageView imageView,
            String id,
            String name,
            String directPathOrKey,
            double width,
            double height,
            double cornerRadius,
            GalleryAngle angle) {
        if (imageView == null) {
            return;
        }
        Image image = getImage(id, name, directPathOrKey, width, height);
        imageView.setImage(image);
        if (!imageView.fitWidthProperty().isBound()) {
            imageView.setFitWidth(width);
        }
        if (!imageView.fitHeightProperty().isBound()) {
            imageView.setFitHeight(height);
        }
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);

        if (image != null && angle != null && image.getWidth() > 0 && image.getHeight() > 0
                && (angle.vw() < 0.999 || angle.vh() < 0.999)) {
            imageView.setViewport(new Rectangle2D(
                    image.getWidth() * angle.vx(),
                    image.getHeight() * angle.vy(),
                    image.getWidth() * angle.vw(),
                    image.getHeight() * angle.vh()));
        } else {
            imageView.setViewport(null);
        }

        if (!imageView.fitWidthProperty().isBound() && !imageView.fitHeightProperty().isBound()) {
            Rectangle clip = new Rectangle(width, height);
            clip.setArcWidth(cornerRadius);
            clip.setArcHeight(cornerRadius);
            imageView.setClip(clip);
        }
    }

    public static void loadIntoImageView(ImageView imageView, String id, String name, double width, double height) {
        loadIntoImageView(imageView, id, name, null, width, height, 14);
    }

    public static final String MULTI_IMAGE_DELIMITER = ";";

    public static List<String> parseImageKeys(String rawImageKey) {
        List<String> result = new ArrayList<>();
        if (rawImageKey == null || rawImageKey.isBlank()) {
            return result;
        }
        for (String part : rawImageKey.split(MULTI_IMAGE_DELIMITER)) {
            String trimmed = part != null ? part.trim() : "";
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public static String getPrimaryImageKey(String rawImageKey) {
        List<String> keys = parseImageKeys(rawImageKey);
        return keys.isEmpty() ? "" : keys.get(0);
    }

    public static String joinImageKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return "";
        }
        List<String> clean = new ArrayList<>();
        for (String k : keys) {
            if (k != null && !k.isBlank()) {
                String sanitized = k.trim().replace(MULTI_IMAGE_DELIMITER, "");
                if (!sanitized.isEmpty() && !clean.contains(sanitized)) {
                    clean.add(sanitized);
                }
            }
        }
        return String.join(MULTI_IMAGE_DELIMITER, clean);
    }

    /**
     * Trả về danh sách ảnh thật của chính sản phẩm đó (hỗ trợ nhiều ảnh Seller tải lên phân tách bằng ';').
     */
    public static List<String> getGalleryKeysFor(String primaryKey, String itemName) {
        List<String> parsed = parseImageKeys(primaryKey);
        if (!parsed.isEmpty()) {
            return parsed;
        }
        return List.of(resolveResourceKey(primaryKey, itemName));
    }

    public static Image getImage(String id, String name, String directPathOrKey, double width, double height) {
        String primaryKey = getPrimaryImageKey(directPathOrKey);
        // 1. Kiểm tra nếu primaryKey là key ảnh studio đóng gói sẵn hoặc file ảnh upload
        if (!primaryKey.isBlank()) {
            if (STUDIO_PRESETS.containsKey(primaryKey) || "bidx_logo".equals(primaryKey)) {
                Image bundled = getCachedBundled(primaryKey, width, height);
                if (bundled != null) {
                    return bundled;
                }
            }
            File directFile = new File(primaryKey);
            if (directFile.exists()) {
                return new Image(directFile.toURI().toString(), width * 1.5, height * 1.5, false, true);
            }
        }

        // 2. Tra cứu registry ảnh upload theo ID hoặc tên
        if (id != null) {
            String path = registry.get(id.toLowerCase(Locale.ROOT));
            if (path != null) {
                File f = new File(path);
                if (f.exists()) {
                    return new Image(f.toURI().toString(), width * 1.5, height * 1.5, false, true);
                }
            }
        }
        if (name != null) {
            String path = registry.get(name.toLowerCase(Locale.ROOT));
            if (path != null) {
                File f = new File(path);
                if (f.exists()) {
                    return new Image(f.toURI().toString(), width * 1.5, height * 1.5, false, true);
                }
            }
        }

        // 3. Ánh xạ thông minh sang bộ ảnh studio thật đóng gói trong resources
        String resourceKey = resolveResourceKey(primaryKey, name);
        Image studio = getCachedBundled(resourceKey, width, height);
        if (studio != null) {
            return studio;
        }

        // 4. Fallback đồ họa
        String cacheKey = (name != null ? name : "default") + "_" + (int) width + "_" + (int) height;
        return memoryCache.computeIfAbsent(cacheKey, k -> generateFallbackImage(name, (int) width, (int) height));
    }

    public static String resolveResourceKey(String directKey, String itemName) {
        if (directKey != null && STUDIO_PRESETS.containsKey(directKey)) {
            return directKey;
        }
        String lower = itemName != null ? itemName.toLowerCase(Locale.ROOT) : "";
        // Siêu Xe
        if (lower.contains("lamborghini") || lower.contains("aventador") || lower.contains("svj")) {
            return "item_car_lambo";
        }
        if (lower.contains("ferrari") || lower.contains("sf90") || lower.contains("stradale")) {
            return "item_car_ferrari";
        }
        if (lower.contains("mercedes") || lower.contains("g63") || lower.contains("amg")) {
            return "item_car_g63";
        }
        if (lower.contains("rolls") || lower.contains("phantom")) {
            return "item_car_rollsroyce";
        }
        if (lower.contains("porsche") || lower.contains("siêu xe") || lower.contains("xe ") || lower.contains("car") || lower.contains("coupe") || lower.contains("tesla")) {
            return "item_vehicle_porsche";
        }
        // Đồng hồ
        if (lower.contains("rolex") || lower.contains("daytona")) {
            return "item_watch_rolex";
        }
        if (lower.contains("royal oak") || lower.contains("audemars") || lower.contains("nautilus")) {
            return "item_watch_royaloak";
        }
        if (lower.contains("skeleton") || lower.contains("tourbillon")) {
            return "item_watch_skeleton";
        }
        if (lower.contains("đồng hồ") || lower.contains("dong ho") || lower.contains("watch") || lower.contains("patek") || lower.contains("calatrava")) {
            return "item_watch_classic";
        }
        // Đồ điện tử
        if (lower.contains("leica") || lower.contains("camera") || lower.contains("máy ảnh") || lower.contains("may anh") || lower.contains("film")) {
            return "item_camera_leica";
        }
        if (lower.contains("macbook") || lower.contains("apple")) {
            return "item_macbook_pro";
        }
        if (lower.contains("focal") || lower.contains("tai nghe") || lower.contains("headphone") || lower.contains("utopia")) {
            return "item_headphone_hifi";
        }
        if (lower.contains("dell") || lower.contains("laptop") || lower.contains("xps") || lower.contains("iphone")) {
            return "item_laptop_xps";
        }
        // Nghệ thuật
        if (lower.contains("bình") || lower.contains("binh") || lower.contains("vase") || lower.contains("sứ") || lower.contains("su ")) {
            return "item_vase_antique";
        }
        if (lower.contains("tượng") || lower.contains("khổng tước") || lower.contains("sculpture")) {
            return "item_art_sculpture";
        }
        if (lower.contains("piano") || lower.contains("steinway") || lower.contains("đàn")) {
            return "item_art_piano";
        }
        if (lower.contains("tranh") || lower.contains("art") || lower.contains("painting") || lower.contains("sơn dầu") || lower.contains("son dau")) {
            return "item_art_painting";
        }
        // Khác
        if (lower.contains("birkin") || lower.contains("hermès") || lower.contains("hermes") || lower.contains("túi")) {
            return "item_other_birkin";
        }
        if (lower.contains("nước hoa") || lower.contains("nuoc hoa") || lower.contains("perfume") || lower.contains("oud") || lower.contains("extrait")) {
            return "item_other_perfume";
        }
        if (lower.contains("whisky") || lower.contains("chivas") || lower.contains("macallan") || lower.contains("rượu") || lower.contains("scotch")) {
            return "item_other_whisky";
        }
        if (lower.contains("kim cương") || lower.contains("nhẫn") || lower.contains("diamond") || lower.contains("ring")) {
            return "item_other_diamond";
        }
        if (lower.contains("cờ vua") || lower.contains("chess") || lower.contains("cẩm thạch")) {
            return "item_other_chess";
        }
        return "item_other_diamond";
    }

    private static Image getCachedBundled(String resourceKey, double width, double height) {
        String cacheKey = "RES_" + resourceKey + "_" + (int) width + "x" + (int) height;
        return memoryCache.computeIfAbsent(cacheKey, k -> loadBundledResource(resourceKey, width, height));
    }

    private static Image loadBundledResource(String resourceKey, double width, double height) {
        String path = RESOURCE_PREFIX + resourceKey + ".jpg";
        try (InputStream is = ProductImageService.class.getResourceAsStream(path)) {
            if (is != null) {
                return new Image(is, width * 2.0, height * 2.0, false, true);
            }
        } catch (Exception e) {
            System.err.println("[ProductImageService] Không thể nạp ảnh studio: " + path + " -> " + e.getMessage());
        }
        return null;
    }

    private static Image generateFallbackImage(String productName, int width, int height) {
        int w = Math.max(width, 280);
        int h = Math.max(height, 160);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        java.awt.GradientPaint gp = new java.awt.GradientPaint(0, 0, new Color(24, 32, 54), 0, h, new Color(11, 15, 25));
        g.setPaint(gp);
        g.fillRect(0, 0, w, h);
        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g.setColor(new Color(255, 123, 36));
        String title = (productName != null && !productName.isBlank()) ? productName : "BidX Item";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, Math.max(12, (w - fm.stringWidth(title)) / 2), h / 2);
        g.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return new Image(new ByteArrayInputStream(baos.toByteArray()), width, height, false, true);
        } catch (IOException e) {
            return null;
        }
    }
}
