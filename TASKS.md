# 🚀 KẾ HOẠCH & PHÂN CÔNG BÀI TẬP LỚN LTNC (TUẦN 6 - TUẦN 10)
**Dự án:** Hệ thống Đấu giá Trực tuyến (Online Auction System)  
**Nhóm sinh viên:**
- Vũ Tuấn Đại (Leader)
- Trường
- Huy Hưng
- Xuân Thắng

**Quy chuẩn dự án:**
- Build tool: **Maven** (`pom.xml`)
- Kiến trúc: **MVC** (Model - View - Controller)
- Coding convention: **Google Java Style Guide** (Checkstyle)
- Commit convention: **Conventional Commits** (`feat:`, `fix:`, `test:`, `refactor:`, `docs:`)
- Kiểm thử tự động: **JUnit 5** (Code Coverage ≥ 60%) & **GitHub Actions** CI/CD

---

## 📅 TUẦN 6: Khởi tạo OOP & Môi trường
- **Vũ Tuấn Đại (Leader):** Xây dựng `Auction.java`, `BidTransaction.java`, cài đặt `AuctionManager.java` (Singleton Pattern), cấu hình Git/GitHub dev branch.
- **Trường:** Xây dựng hệ thống User: `User` (abstract), `Bidder`, `Seller`, `Admin` (Đóng gói, kế thừa, đa hình).
- **Xuân Thắng:** Xây dựng hệ thống Item: `Item` (abstract), `Electronics`, `Art`, `Vehicle` & `ItemFactory` (Factory Method Pattern).
- **Huy Hưng & Cả nhóm:** Cài đặt JavaFX SDK, SceneBuilder, chạy thử nghiệm ứng dụng JavaFX Hello World.

---

## 📅 TUẦN 7 & 8: Đa luồng, Observer, Ngoại lệ & GUI MVC
- **Vũ Tuấn Đại (Leader):**
  - Cài đặt **Observer Pattern** (`Subject`, `Observer`, `AuctionObserver`) để cập nhật sự kiện bid mới.
  - Xử lý **đấu giá đồng thời (Concurrency)** bằng `synchronized` / `ReentrantLock` trong `Auction.placeBid()` (chống race condition, lost update).
  - Tái cấu trúc mã nguồn theo chuẩn **SOLID**, quản lý merge nhánh `dev`.
- **Xuân Thắng:**
  - Viết logic vòng đời phiên: `OPEN` -> `RUNNING` -> `FINISHED` -> `PAID` / `CANCELED` (tự động đếm ngược và đóng phiên khi hết giờ).
  - Xây dựng hệ thống Custom Exceptions: `InvalidBidException`, `AuctionClosedException`, `AuthenticationException`.
- **Trường:**
  - Thiết lập thư mục `src/test/java/`, viết toàn bộ kiểm thử tự động với **JUnit 5**.
  - Kiểm thử logic đặt giá, ngoại lệ và giả lập nhiều thread cùng bid đồng thời.
- **Huy Hưng:**
  - Thiết kế giao diện FXML bằng SceneBuilder: Đăng ký, Đăng nhập, Danh sách phiên đấu giá, Chi tiết đấu giá.
  - Viết các `Controller` tách biệt logic theo mô hình **MVC**.

---

## 📅 TUẦN 9 & 10: Tích hợp Hệ thống, Socket Client-Server & CI/CD
- **Vũ Tuấn Đại (Leader):**
  - Xây dựng kiến trúc **Socket Multi-threaded Server** (phục vụ nhiều client cùng lúc).
  - Tích hợp **Observer Pattern qua Socket** để broadcast nhảy giá trực tiếp (realtime) tới các client.
- **Xuân Thắng:**
  - Cấu hình file `pom.xml` chuẩn Maven, tích hợp Checkstyle theo Google Java Style Guide.
  - Thiết lập CI/CD bằng **GitHub Actions** (`.github/workflows/maven.yml` tự động build và test khi push code).
  - Cài đặt **Serialization / Deserialization** để lưu trữ dữ liệu phiên và tài khoản ra file.
- **Trường:**
  - Xây dựng `AuctionClient.java` kết nối Socket tới Server.
  - Bổ sung Unit Test JUnit 5 cho toàn hệ thống, đảm bảo đạt chỉ tiêu **Code Coverage ≥ 60%**.
- **Huy Hưng:**
  - Hoàn thiện trọn bộ 5 màn hình JavaFX: Login/Register, Danh sách phiên, Chi tiết sản phẩm, Đấu giá trực tiếp realtime và Quản lý sản phẩm (Seller).
  - Nối luồng dữ liệu realtime từ Socket Client lên giao diện JavaFX bằng `Platform.runLater()`.