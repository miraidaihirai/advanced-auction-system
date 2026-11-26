package network;

/**
 * Thông tin hồ sơ người dùng (bao gồm vai trò và số dư ví) đồng bộ từ Server.
 */
public record UserProfile(
        String userId,
        String name,
        String role,
        double balance
) {
    public boolean isBidder() {
        return "BIDDER".equalsIgnoreCase(role);
    }

    public boolean isSeller() {
        return "SELLER".equalsIgnoreCase(role);
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
