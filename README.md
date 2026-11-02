# advanced-auction-system
Bài tập lớn Lập trình nâng cao: Hệ thống đấu giá trực tuyến (Java, JavaFX, Client-Server)

> Đây là bản **đã sửa lỗi** của [miraidaihirai/advanced-auction-system](https://github.com/miraidaihirai/advanced-auction-system),
> do [trungngo9207-commits](https://github.com/trungngo9207-commits) sửa ngày 30/10/2026.
> Kế hoạch và phân công của nhóm: xem [TASKS.md](TASKS.md).

## Dự án hiện có gì
- **Đấu giá:** `Auction`, `BidTransaction`, `AuctionManager` (Singleton)
- **Người dùng:** `User` (abstract), `Bidder`, `Seller`
- **Sản phẩm:** `Item` (abstract), `Electronics`, `Art`, `Vehicle` + `ItemFactory` (Factory Method)
- **Observer Pattern** thông báo giá mới; đặt giá đồng thời an toàn bằng `ReentrantLock`
- **Vòng đời phiên:** `OPEN → RUNNING → FINISHED → PAID / CANCELED`, tự đóng khi hết giờ
- **Custom exceptions:** `InvalidBidException`, `AuctionClosedException`, `AuthenticationException`
- **Socket server đa luồng** (cổng 8888) broadcast giá realtime tới các client; `AuctionClient`

## Cách chạy
Cần **JDK 21** và **Maven ≥ 3.6**.

| Việc | Lệnh |
|---|---|
| Chạy test JUnit 5 | `mvn clean test` |
| Chạy demo console | `mvn exec:java` |
| Bật server | `mvn compile` rồi `java -cp target/classes network.AuctionServer` |
| Vào như một người dùng (mỗi người một terminal) | `nc localhost 8888`, rồi gõ `LOGIN BIDDER-01 password123`, `LIST`, `JOIN AUC-001`, `BID AUC-001 10000000` |

- Tài khoản mẫu: `SELLER-01`, `BIDDER-01`, `BIDDER-02`, `BIDDER-03`, mật khẩu đều là `password123`.
- Các lệnh khác: `INFO <auctionId>`, `LEAVE <auctionId>`, `REGISTER <id> <tên> <mật khẩu> <BIDDER|SELLER>`, `QUIT`.
- Hai phiên mẫu (`AUC-001`, `AUC-002`) tự đóng **sau 1 phút** kể từ lúc bật server.
- Trên nhánh `feature/ui`, lớp main là `com.auction.Main`.

## Các lỗi đã tìm thấy

1. **`dev` và `main` không biên dịch được (46 lỗi `javac`).**
   Merge `a31dfb5` ("Merge branch 'dev' into feature/ui") giải quyết conflict bằng cách **giữ cả hai phía**, nên code cũ bị dán vào giữa code mới.
   Lỗi này lan sang `dev` qua PR #3 và sang `main` qua PR #8:
   - `Main.java`: demo tuần 6 cũ bị dán nối sau demo mới
   - `model/Auction.java`: nguyên thân class `Auction` cũ nằm bên trong class mới
   - `manager/AuctionManager.java`: constructor `HashMap` cũ và `placeBid` 3 tham số cũ bị chèn vào giữa constructor và `placeBid` mới
   - `Item/Item.java`: khai báo field bị lặp bên trong constructor, thừa một dấu `}`
   - `Item/Electronics.java`: field `warrantyPeriod` khai báo 2 lần
2. **Mất `serialVersionUID`** ở 3 class exception khi merge PR #7.
3. **Conflict giữa các nhánh:** PR #6 (`feature/ui` → `dev`) bị conflict `pom.xml` (cả hai bên cùng thêm file). `feature/socket-server-broadcast` conflict với `main` do lịch sử merge chéo.
4. **Lỗ hổng xác thực** trên `feature/socket-server-broadcast`: hàm `placeBid(auctionId, bidderId, amount)` tự lấy mật khẩu đã lưu của user để đặt giá, tức là đặt giá thay người khác mà không cần mật khẩu. Hàm này không được gọi ở đâu.
5. **`feature/ui`:** `Main.java` được chuyển vào `com/auction/` nhưng thiếu `package com.auction;`. `pom.xml` trỏ `mainClass` tới `com.example.demo.HelloApplication`, một class không tồn tại.
6. **Test không thực sự chạy:** `pom.xml` không cố định version `maven-surefire-plugin`. Với Maven < 3.9 (ví dụ Maven 3.6.3 cài bằng `apt`), Maven dùng surefire 2.12.4, bản này không hỗ trợ JUnit 5. Kết quả là `Tests run: 0 … BUILD SUCCESS` (trông như pass nhưng không test gì), hoặc crash VM nếu sai JDK.
7. **`mvn exec:java` treo 15 giây** sau khi demo xong, kèm cảnh báo `thread auction-timeout ... will linger`.

## Những gì đã sửa

| Commit | Nhánh | Nội dung |
|---|---|---|
| `e5ec97a` | `main`, `dev` | Xoá phần code cũ bị trùng ở 5 file trên (chỉ xoá, không thêm logic mới); khôi phục `serialVersionUID` |
| `c52a907` | `feature/socket-server-broadcast` | Merge `main` đã sửa vào; bỏ hàm `placeBid` 3 tham số; giữ cấu hình JavaFX của `main` |
| `ec646b7` | `feature/ui` | Merge `main` đã sửa vào; gộp 2 file `pom.xml` và trỏ `mainClass` về `com.auction.Main`; thêm `package com.auction;` |
| `498dd46` | tất cả | Cố định `maven-surefire-plugin` 3.2.5 để JUnit 5 chạy trên mọi version Maven |
| `1b97770` | tất cả | `exec:java` không chờ thread daemon `auction-timeout` nữa (`cleanupDaemonThreads=false`) |

**Đã kiểm tra** (JDK 21, với cả Maven 3.6.3 và 3.9.9):
- Mọi nhánh đều biên dịch được và không còn dấu conflict (`<<<<<<<`)
- Merge thử mọi nhánh vào `main` và `dev`: không còn conflict (kể cả `feature/ui` → `dev` của PR #6)
- `mvn clean test`: **2/2 test pass** trên `main` và `feature/ui`
- `mvn exec:java`: demo chạy hết trong khoảng 2 giây, không còn cảnh báo
- Chạy server + 2 client: client đã `JOIN` nhận được `NOTIFY BID` ngay khi người khác đặt giá

## Việc còn lại

### Theo kế hoạch trong TASKS.md
- [ ] **Giao diện JavaFX** (Huy Hưng): 5 màn hình (Login/Register, Danh sách phiên, Chi tiết sản phẩm, Đấu giá realtime, Quản lý sản phẩm cho Seller), Controller theo MVC, và nối dữ liệu từ Socket lên giao diện bằng `Platform.runLater()`.
  Hiện **chưa có màn hình nào**. File `login.fxml` cũ (commit `1f9d5cf`) đã bị xoá ở `07a5949` khi đổi cấu trúc thư mục; có thể lấy lại bằng `git show 1f9d5cf:src/UI/login.fxml`.
- [ ] **Lớp `Admin`** (tuần 6, Trường): chưa có.
- [ ] **Unit test JUnit 5** (Trường): mới có 2 test cho socket server (`AuctionServerTest`). Còn thiếu test cho logic đặt giá, ngoại lệ, vòng đời phiên và nhiều thread cùng bid. Chưa đo coverage (cần JaCoCo, mục tiêu ≥ 60%).
- [ ] **Checkstyle** theo Google Java Style trong `pom.xml` (Xuân Thắng)
- [ ] **GitHub Actions CI**: `.github/workflows/maven.yml` (Xuân Thắng)
- [ ] **Serialization**: lưu phiên đấu giá và tài khoản ra file (Xuân Thắng)

### Vấn đề kỹ thuật nên xử lý
- [ ] Package `Item` và `Item.Factory` viết hoa, sai quy ước Java (Checkstyle sẽ báo lỗi); nên đổi thành `item` và `item.factory`.
- [ ] Thống nhất cấu trúc package: `feature/ui` mới chuyển mỗi `Main` vào `com.auction`, các package khác vẫn ở gốc. Nhóm nên chọn một kiểu, ví dụ đưa hết vào `com.auction.*`.
- [ ] Trên `main`, `javafx-maven-plugin` vẫn trỏ tới `com.example.demo.HelloApplication`; sẽ hết khi merge PR #6 (`feature/ui`).
- [ ] `groupId` / `artifactId` trong `pom.xml` vẫn là giá trị mẫu (`com.example` / `demo`).
- [ ] Server chưa có lệnh để Seller tạo phiên mới, và 2 phiên mẫu đóng sau 1 phút nên khó demo lâu.
- [ ] Repo gốc vẫn đang là bản lỗi; cần đưa các bản sửa ở đây về (xem bên dưới).

## Đưa bản sửa về repo gốc
Repo này đang để **private**. Chủ repo cần được thêm làm collaborator (hoặc repo được chuyển sang public), sau đó chạy trong repo gốc:

```bash
git remote add fixed https://github.com/trungngo9207-commits/advanced-auction-system.git
git fetch fixed
git checkout main && git merge fixed/main && git push origin main
git checkout dev  && git merge fixed/dev  && git push origin dev
```

Làm tương tự với `feature/ui` và `feature/socket-server-broadcast`. Tất cả đều merge sạch, vì bản sửa được làm tiếp trên lịch sử sẵn có của repo gốc.
