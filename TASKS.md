📢 KẾ HOẠCH & PHÂN CÔNG BÀI TẬP LỚN LTNC (TUẦN 6)

Dự án: Hệ thống Đấu giá (Auction System)

Nhóm: 4 thành viên (Tuấn Đại, Trường, Huy Hưng, Xuân Thắng)

🔥 1. NHIỆM VỤ CẤP TỐC (Hoàn thành TRƯỚC 17/10)

Tất cả 4 thành viên bắt buộc hoàn thành các bước thiết lập môi trường làm việc:

Gia nhập Discord nhóm để họp & trao đổi:

👉 Link: https://discord.gg/AFYsCUKGB

Cài đặt & đồng bộ công cụ Git / GitHub:

Đăng ký tài khoản GitHub (nếu chưa có).

Tải và cài đặt GitHub Desktop (khuyến nghị cho bạn nào chưa quen lệnh Git).

Bấm chấp nhận lời mời tham gia dự án tại link:

👉 https://github.com/miraidaihirai/online-auction-system-ltnc/invitations

Clone repo về máy và chuyển sang nhánh làm việc dev.

💻 2. PHÂN CÔNG CHUYÊN MÔN THEO THÀNH VIÊN (Hạn chót: Hết Tuần 6)

👤 1. Leader (Vũ Tuấn Đại)

Nhiệm vụ:

Viết các lớp phiên đấu giá: Auction.java, BidTransaction.java.

Triển khai mẫu thiết kế Singleton Pattern cho lớp điều phối trung tâm: AuctionManager.java.

Quản trị kho mã nguồn nhánh dev, hỗ trợ gỡ lỗi Git và review code cho các thành viên.

👤 2. Thành viên: Phạm Văn Trường

Nhiệm vụ:

Xây dựng cây kế thừa hệ thống Người dùng: Thiết kế lớp cha trừu tượng User.java (abstract).

Viết 3 lớp con kế thừa: Bidder.java, Seller.java, Admin.java.

Đảm bảo tuân thủ đầy đủ tính đóng gói OOP (encapsulation: private fields, getter/setter hợp lệ).

👤 3. Thành viên: Lê Huy Hưng

Nhiệm vụ:

Xây dựng cây kế thừa hệ thống Sản phẩm: Thiết kế lớp cha trừu tượng Item.java (abstract).

Viết 3 lớp con kế thừa phân loại sản phẩm: Electronics.java, Art.java, Vehicle.java.

Triển khai mẫu thiết kế Factory Method Pattern: Viết lớp ItemFactory.java để sinh đối tượng Item linh hoạt.

👤 4. Phụ trách giao diện chính: Nguyễn Xuân Thắng (UI Lead)

Nhiệm vụ:

Đi đầu trong việc tìm hiểu sâu về kiến trúc JavaFX và công cụ SceneBuilder.

Thiết kế và dựng khung FXML mẫu cho màn hình Đăng nhập / Đăng ký (Login & Register).

Phối hợp hướng dẫn và hỗ trợ 3 bạn còn lại tích hợp giao diện.

⚡ 3. NHIỆM VỤ CHUNG: CẢ 4 THÀNH VIÊN CÙNG LÀM

⚠️ Lưu ý đặc biệt: JavaFX là nội dung tự học bắt buộc, không được dạy trên lớp. Đề bài yêu cầu mỗi thành viên đều phải hiểu và giải thích được toàn bộ mã nguồn khi bảo vệ.

Nhiệm vụ bắt buộc của từng người trong tuần này:

Tự cài đặt môi trường JavaFX SDK + SceneBuilder trên máy tính cá nhân.

Tạo thử một project và chạy thành công ứng dụng Hello World (JavaFX).

Báo cáo trạng thái cài đặt lên kênh Discord nhóm để đảm bảo không ai bị tụt hậu về công nghệ.

📌 4. QUY TẮC LÀM VIỆC NHÓM

Quy tắc commit: Mỗi người tự code trên máy mình, kiểm tra chạy thử và commit thường xuyên lên nhánh dev.

Quy chuẩn thông điệp commit (Conventional Commits):

Thêm tính năng/lớp mới: feat: <nội dung>

Sửa lỗi: fix: <nội dung>

Viết tài liệu/README: docs: <nội dung>


===============================================================================
===============================================================================


📢 BTL LTNC: PHÂN CÔNG NHIỆM VỤ TUẦN 7 & 8 (ONLINE AUCTION SYSTEM)

1. Tuấn Đại (Leader) – Concurrency & Observer:
- Viết Observer Pattern để tự động bắn thông báo khi có bid mới.
- Xử lý đấu giá đồng thời (Concurrency) bằng synchronized / ReentrantLock trong hàm placeBid() (tránh lost update, race condition).
- Refactor mã nguồn theo chuẩn SOLID & quản lý nhánh dev.

2. Xuân Thắng – Logic Trạng thái & Custom Exceptions:
- Viết logic vòng đời phiên: OPEN -> RUNNING -> FINISHED -> PAID / CANCELED (tự động đóng phiên khi hết giờ).
- Tạo bộ ngoại lệ: InvalidBidException, AuctionClosedException, AuthenticationException và tích hợp ném lỗi vào hàm đặt giá.

3. Trường – Kiểm thử tự động (JUnit 5):
- Viết toàn bộ Unit Test (JUnit 5) trong thư mục src/test/java.
- Viết test cases kiểm tra: đặt giá hợp lệ, đặt giá thấp hơn giá hiện tại, đặt giá khi phiên đã đóng, và mô phỏng nhiều thread đặt giá cùng lúc.

4. Huy Hưng – Giao diện JavaFX (Chuẩn MVC):
- Thiết kế toàn bộ giao diện bằng SceneBuilder: Đăng nhập/Đăng ký, Danh sách phiên đấu giá, và Màn hình chi tiết đấu giá.
- Viết các Controller tương ứng để hứng sự kiện từ nút bấm và gọi sang dữ liệu backend (chuẩn mô hình MVC).

⚠️ LƯU Ý:
- Mỗi người tự làm mảng của mình và commit thường xuyên lên nhánh dev kèm tiền tố chuẩn: feat:, fix:, test:, refactor:.
- Hưng (làm UI) chủ động trao đổi với Thắng, Trường và Đại để thống nhất tên hàm trước khi gắn vào Controller nhé!

===============================================================================
===============================================================================


📢 BTL LTNC: PHÂN CÔNG NHIỆM VỤ TUẦN 9 & 10 (TÍCH HỢP HỆ THỐNG & SOCKET CLIENT-SERVER)

1. Tuấn Đại (Leader) – Server Đa Luồng & Realtime Broadcast:
- Dựng Socket Server đa luồng (Multi-threaded Server): phục vụ nhiều client kết nối cùng lúc.
- Tích hợp Observer Pattern qua Socket: khi có người đặt giá thành công, Server tự động bắn thông báo realtime cho tất cả các client đang xem phiên đó.
- Phụ trách tích hợp chung và quản lý nhánh dev -> main.

2. Xuân Thắng – CI/CD, Maven & Serialization:
- Chuẩn hóa file pom.xml Maven và tích hợp Checkstyle để kiểm tra coding convention.
- Thiết lập GitHub Actions: tự động build và chạy test tự động khi có commit/pull request.
- Cài đặt Serialization / Deserialization: lưu và đọc dữ liệu phiên đấu giá/người dùng vào file để không mất dữ liệu khi khởi động lại server.

3. Trường – Socket Client & Unit Test Coverage >= 60%:
- Viết AuctionClient để ứng dụng JavaFX kết nối tới Server, gửi lệnh đặt giá và nhận dữ liệu phản hồi.
- Bổ sung toàn bộ Unit Test (JUnit 5) cho các module, đảm bảo đạt chỉ tiêu Code Coverage >= 60% theo yêu cầu đề bài.

4. Huy Hưng – Hoàn thiện 5 Màn hình JavaFX (MVC):
- Hoàn thiện toàn bộ giao diện FXML và Controller kết nối backend:
  + Đăng nhập / Đăng ký
  + Danh sách phiên đấu giá
  + Chi tiết sản phẩm
  + Màn hình đấu giá trực tiếp (nhảy giá realtime)
  + Quản lý sản phẩm dành cho Seller (đăng hàng đấu giá mới)

⚠️ LƯU Ý KỸ THUẬT:
- Huy Hưng (UI) phối hợp với Trường (Client Socket) để khi Server bắn giá mới về, giao diện JavaFX tự động nhảy số thông qua Platform.runLater().
- Mọi người tiếp tục commit chuẩn tiền tố: feat:, fix:, test:, refactor:.

