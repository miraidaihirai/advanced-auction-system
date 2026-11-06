# 🚀 KẾ HOẠCH & PHÂN CÔNG BÀI TẬP LỚN LTNC (TUẦN 6 - TUẦN 12)
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

---

## 📅 TUẦN 11 & 12: Tổng lực Ghép nối & Hoàn thiện Toàn bộ Hệ thống

Chia làm 2 nhóm chuyên trách: **Backend & Socket Server (Đại + Thắng)** và **Frontend JavaFX & Realtime (Hưng + Trường)**.

### 💻 NHÓM BACKEND (Đại & Thắng):
- **Vũ Tuấn Đại (Leader):**
  - Quản lý Client/Session Socket Server: Xử lý đa luồng mượt mà, tự động dọn dẹp khi client mất kết nối đột ngột.
  - Xây dựng logic tự động chốt phiên khi hết thời gian: xác định người thắng cuộc, chuyển trạng thái `FINISHED`/`PAID` và bắn thông báo riêng cho người thắng + người bán.
  - Quản lý kiểm duyệt code, merge nhánh `dev` chuẩn bị bản release.
- **Xuân Thắng:**
  - Xây dựng module lưu trữ lịch sử giao dịch đấu giá và dữ liệu hệ thống (Data Persistence).
  - Bổ sung các lớp kiểm tra nghiệp vụ chặt chẽ: chặn người bán tự đặt giá sản phẩm của mình, kiểm tra quyền Admin/Seller/Bidder.
  - Tối ưu kịch bản CI/CD trên GitHub Actions để chạy trơn tru toàn bộ test cases.

### 🎨 NHÓM FRONTEND (Hưng & Trường):
- **Huy Hưng:**
  - Hoàn thiện Màn hình đấu giá trực tiếp (Live Auction View): hiển thị giá nhảy realtime, đồng hồ đếm ngược và lịch sử các lượt bid theo thời gian thực.
  - Thiết kế Dashboard cho Seller (tạo phiên mới, xem danh sách hàng đã đăng) và giao diện Admin.
- **Trường:**
  - Làm cầu nối dữ liệu: nhận tín hiệu từ Socket client và bắn lên giao diện JavaFX bằng `Platform.runLater()` (đảm bảo không bị đơ/treo giao diện).
  - Viết hệ thống Alert/Dialog thông báo trực quan khi có lỗi (mất mạng, giá không hợp lệ, phiên kết thúc) hoặc khi đấu giá thành công.
  - Tinh chỉnh CSS toàn bộ các màn hình cho chuyên nghiệp, đồng bộ font chữ, bố cục.

### ⚠️ LƯU Ý PHỐI HỢP:
- Team Frontend (Hưng, Trường) và Team Backend (Đại, Thắng) cần thống nhất chuẩn bản tin dữ liệu truyền qua Socket trước khi code để lúc ghép không bị lệch kiểu dữ liệu.
- Mọi người tiếp tục commit đều tay với prefix chuẩn (`feat:`, `fix:`, `refactor:`).
