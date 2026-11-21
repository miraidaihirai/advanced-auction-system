package com.auction.session;

import com.auction.model.Admin;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;

/**
 * Liệt kê các vai trò người dùng trong hệ thống đấu giá.
 * Phân quyền dựa trên kiểu dữ liệu cụ thể (Admin, Seller, Bidder) kế thừa từ User.
 */
public enum UserRole {
    BIDDER,
    SELLER,
    ADMIN,
    UNKNOWN;

    /**
     * Xác định vai trò trực tiếp từ kiểu dữ liệu runtime (class type) của đối tượng User.
     *
     * @param user đối tượng người dùng cụ thể (Admin, Seller, Bidder)
     * @return UserRole tương ứng
     */
    public static UserRole fromUser(User user) {
        if (user == null) return UNKNOWN;
        if (user instanceof Admin) return ADMIN;
        if (user instanceof Seller) return SELLER;
        if (user instanceof Bidder) return BIDDER;
        return fromString(user.getRoleName());
    }

    /**
     * Xác định vai trò từ Class type của đối tượng User.
     *
     * @param clazz kiểu lớp kế thừa từ User
     * @return UserRole tương ứng
     */
    public static UserRole fromClass(Class<?> clazz) {
        if (clazz == null) return UNKNOWN;
        if (Admin.class.isAssignableFrom(clazz)) return ADMIN;
        if (Seller.class.isAssignableFrom(clazz)) return SELLER;
        if (Bidder.class.isAssignableFrom(clazz)) return BIDDER;
        return UNKNOWN;
    }

    /**
     * Chuyển chuỗi vai trò sang UserRole tương ứng.
     *
     * @param raw chuỗi vai trò thô (ví dụ: "SELLER", "ADMIN", "BIDDER")
     * @return UserRole tương ứng; trả về UNKNOWN nếu không nhận ra
     */
    public static UserRole fromString(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        String upper = raw.trim().toUpperCase();
        if (upper.endsWith("ADMIN") || upper.contains("ADMIN")) return ADMIN;
        if (upper.endsWith("SELLER") || upper.contains("SELLER")) return SELLER;
        if (upper.endsWith("BIDDER") || upper.contains("BIDDER")) return BIDDER;
        return UNKNOWN;
    }
}
