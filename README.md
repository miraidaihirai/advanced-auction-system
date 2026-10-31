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
| `fix: repair broken merge resolution…` | `main`, `dev` | Xoá phần code cũ bị trùng ở 5 file trên (chỉ xoá, không thêm logic mới); khôi phục `serialVersionUID` |
| `Merge repaired main into feature/socket-server-broadcast` | `feature/socket-server-broadcast` | Merge `main` đã sửa vào; bỏ hàm `placeBid` 3 tham số; giữ cấu hình JavaFX của `main` |
| `Merge repaired main into feature/ui` | `feature/ui` | Merge `main` đã sửa vào; gộp 2 file `pom.xml` và trỏ `mainClass` về `com.auction.Main`; thêm `package com.auction;` |
| `build: pin maven-surefire-plugin 3.2.5…` | tất cả | Cố định `maven-surefire-plugin` 3.2.5 để JUnit 5 chạy trên mọi version Maven |
| `build: stop exec:java waiting 15s…` | tất cả | `exec:java` không chờ thread daemon `auction-timeout` nữa (`cleanupDaemonThreads=false`) |

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
- [ ] Đưa nốt `feature/ui` đã sửa về repo gốc (xem Bước 1 bên dưới).

## Tình trạng repo gốc (cập nhật 31/10/2026)
- ✅ Đại (King Uet) đã kéo bản sửa về `main`, `dev` và `feature/socket-server-broadcast`.
- ⏳ `feature/ui` trên repo gốc vẫn là commit cũ `5b49dd8`, nên PR #6 còn conflict cho tới khi làm Bước 1.
- ⚠️ `feature/thang` đang trỏ vào `main` cũ (bản lỗi, không biên dịch được). Thắng nhớ chạy `git pull --no-rebase origin dev` trước khi làm tiếp.

## Hướng dẫn merge phần JavaFX của Hưng mà không bị conflict

### Bước 1 – Đại (King Uet) đồng bộ bản sửa (chỉ làm 1 lần)
Trong thư mục repo gốc, chạy script `dong-bo-ban-sua.sh` (đã gửi riêng):

```bash
bash dong-bo-ban-sua.sh
```

Script tự cập nhật `main`, `dev`, `feature/socket-server-broadcast` và `feature/ui` lên bản sửa mới nhất. Bản `feature/ui` này đã hết conflict với `dev` (đã thử). Script không đụng tới commit của người khác, và tự dừng nếu trên GitHub có commit mới mà nó chưa biết.

**Sau Bước 1, cả nhóm:** lịch sử các commit sửa lỗi được làm gọn lại, nên ai đã `pull` nhánh `main`, `dev` hoặc `feature/socket-server-broadcast` từ ngày 30/10 thì đồng bộ lại (commit hoặc `git stash` code đang làm trước):

```bash
git fetch origin
git checkout dev && git reset --hard origin/dev     # làm tương tự với main nếu có
```

### Bước 2 – Hưng chạy trên máy mình, **trước khi** push phần JavaFX

```bash
# 1. Lưu hết code JavaFX thành commit
git add .
git commit -m "feat: add JavaFX screens"

# 2. Lấy bản feature/ui đã sửa, rồi lấy dev mới nhất
git pull --no-rebase origin feature/ui
git pull --no-rebase origin dev

# 3. Kiểm tra trước khi đẩy
mvn clean test        # phải thấy: Tests run: 2, Failures: 0 ... BUILD SUCCESS
mvn javafx:run        # mở được giao diện

# 4. Đẩy lên, PR #6 tự cập nhật
git push origin feature/ui
```

Conflict, nếu có, được xử lý **trên máy Hưng**, là người hiểu code UI nhất. Nó chỉ có thể xảy ra ở `pom.xml` hoặc `Main.java`, và chỉ khi Hưng cũng sửa các file đó.

### Nếu `pom.xml` bị conflict (thấy `<<<<<<<`, `=======`, `>>>>>>>`)
1. Giữ **toàn bộ** phần của bản đã sửa: `maven-surefire-plugin` 3.2.5, `cleanupDaemonThreads`, các dependency JUnit.
2. Thêm phần của Hưng vào, ví dụ dependency mới.
3. Dòng `<mainClass>` của `javafx-maven-plugin` đổi thành class JavaFX của Hưng (class `extends Application`, ví dụ `com.auction.App`).
4. Xoá 3 dòng đánh dấu, rồi chạy `git add pom.xml && git commit`.
5. **Chạy lại `mvn clean test`** trước khi push.

> ⚠️ **Đừng giữ cả hai bên một cách máy móc.** Chính kiểu giải quyết đó ở commit `a31dfb5` đã làm cả project không biên dịch được.

### Quy tắc chung để sau này không bị conflict nữa
1. **Luôn `git pull --no-rebase origin dev`** trước khi bắt đầu làm và trước khi tạo PR.
2. **Không sửa file của người khác** nếu không cần. Phần UI chỉ nên gọi `AuctionClient` và cập nhật giao diện bằng `Platform.runLater()`, không sửa `model/` hay `network/`.
3. **`pom.xml` chỉ thêm, không định dạng lại cả file**, và báo nhóm mỗi khi sửa.
4. File `.fxml` để trong `src/main/resources/`. Không commit `.vscode/` hay `target/`.
5. **Giải quyết conflict xong thì `mvn clean test` rồi mới commit.**

Nếu thấy khó, Hưng có thể push phần JavaFX lên một nhánh mới (ví dụ `feature/javafx-ui`) để merge thử và xử lý conflict giúp ở repo này trước.
