package com.auction.session;

import com.auction.model.Admin;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import java.io.IOException;
import network.AuctionClient;

/**
 * Quản lý phiên kết nối dùng chung cho toàn bộ ứng dụng client. Lưu trữ AuctionClient duy nhất (kết
 * nối Socket), thông tin người dùng đăng nhập (User cụ thể: Admin / Seller / Bidder) và vai trò
 * (UserRole) để phân quyền giao diện chính xác theo kiểu dữ liệu.
 */
public class ClientSession {

  private final AuctionClient auctionClient;

  /** Đối tượng người dùng cụ thể (Admin, Seller hoặc Bidder). */
  private volatile User currentUser;

  /** ID của người dùng đang đăng nhập (null nếu chưa đăng nhập). */
  private volatile String currentUserId;

  /** Tên hiển thị của người dùng đang đăng nhập. */
  private volatile String currentUserName;

  /** Vai trò của người dùng đang đăng nhập (UNKNOWN khi chưa xác định). */
  private volatile UserRole currentRole = UserRole.UNKNOWN;

  // -------------------------------------------------------------------------
  // Khởi tạo
  // -------------------------------------------------------------------------

  public ClientSession(String host, int port) {
    this.auctionClient = new AuctionClient(host, port);
  }

  // -------------------------------------------------------------------------
  // Kết nối Socket
  // -------------------------------------------------------------------------

  public synchronized void connect() throws IOException {
    if (!auctionClient.isConnected()) {
      auctionClient.connect();
    }
  }

  public AuctionClient getClient() {
    return auctionClient;
  }

  public boolean isConnected() {
    return auctionClient.isConnected();
  }

  public void disconnect() {
    auctionClient.disconnect();
  }

  // -------------------------------------------------------------------------
  // Thông tin người dùng & Phân quyền dựa trên kiểu dữ liệu (Data Type)
  // -------------------------------------------------------------------------

  /**
   * Thiết lập thông tin người dùng từ đối tượng User thực tế. Tự động xác định vai trò dựa trên
   * kiểu lớp cụ thể (Admin, Seller, Bidder).
   *
   * @param user đối tượng người dùng
   */
  public synchronized void setCurrentUser(User user) {
    this.currentUser = user;
    if (user != null) {
      this.currentUserId = user.getId();
      this.currentUserName = user.getName();
      this.currentRole = UserRole.fromUser(user);
    } else {
      clearCurrentUser();
    }
  }

  /**
   * Thiết lập thông tin người dùng từ chuỗi định danh và vai trò. Tự động tra cứu đối tượng User
   * thực tế trong AuctionManager nếu có.
   *
   * @param userId ID người dùng
   * @param displayName tên hiển thị
   * @param role vai trò
   */
  public synchronized void setCurrentUser(String userId, String displayName, UserRole role) {
    this.currentUserId = userId;
    this.currentUserName = displayName;
    this.currentRole = (role != null) ? role : UserRole.UNKNOWN;

    // Cố gắng tra cứu đối tượng User trong AuctionManager để có kiểu dữ liệu thực
    // tế
    try {
      User u = com.auction.manager.AuctionManager.getInstance().getUser(userId);
      if (u != null) {
        this.currentUser = u;
        this.currentRole = UserRole.fromUser(u);
      }
    } catch (Exception ignored) {
    }

    // Đảm bảo currentUser luôn được khởi tạo đối tượng cụ thể (Admin, Seller,
    // Bidder)
    // Mật khẩu phiên cần tối thiểu 8 ký tự theo ràng buộc của User.setPassword()
    if (this.currentUser == null && userId != null && !userId.isBlank()) {
      String sessionPwd = "password123";
      if (this.currentRole == UserRole.ADMIN) {
        this.currentUser = new Admin(userId, displayName, sessionPwd);
      } else if (this.currentRole == UserRole.SELLER) {
        this.currentUser = new Seller(userId, displayName, sessionPwd);
      } else {
        this.currentUser = new Bidder(userId, displayName, sessionPwd);
      }
    }
  }

  /** Xóa thông tin người dùng (khi đăng xuất). */
  public synchronized void clearCurrentUser() {
    this.currentUser = null;
    this.currentUserId = null;
    this.currentUserName = null;
    this.currentRole = UserRole.UNKNOWN;
  }

  public User getCurrentUser() {
    return currentUser;
  }

  public String getCurrentUserId() {
    return currentUserId;
  }

  public String getCurrentUserName() {
    return currentUserName;
  }

  public UserRole getCurrentRole() {
    return currentRole;
  }

  // -------------------------------------------------------------------------
  // Tiện ích kiểm tra vai trò dựa trực tiếp trên kiểu dữ liệu (Admin, Seller,
  // Bidder)
  /**
   * Kiểm tra người dùng có phải là Người bán (Seller) không. Ưu tiên kiểm tra kiểu dữ liệu thực tế
   * `currentUser instanceof Seller`.
   */
  public boolean isSeller() {
    return (currentUser instanceof Seller) || currentRole == UserRole.SELLER;
  }

  /**
   * Kiểm tra người dùng có phải là Quản trị viên (Admin) không. Ưu tiên kiểm tra kiểu dữ liệu thực
   * tế `currentUser instanceof Admin`.
   */
  public boolean isAdmin() {
    return (currentUser instanceof Admin) || currentRole == UserRole.ADMIN;
  }

  /**
   * Kiểm tra người dùng có phải là Người đặt giá (Bidder) không. Ưu tiên kiểm tra kiểu dữ liệu thực
   * tế `currentUser instanceof Bidder`.
   */
  public boolean isBidder() {
    return (currentUser instanceof Bidder) || currentRole == UserRole.BIDDER;
  }

  /** Trả về true nếu người dùng có quyền quản lý / tạo phòng đấu giá (Seller hoặc Admin). */
  public boolean canManageAuction() {
    return isSeller() || isAdmin();
  }
}
