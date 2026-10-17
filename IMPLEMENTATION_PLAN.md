# IMPLEMENTATION_PLAN.md

# Vending Machine Prototype — Implementation Plan

## 1. Mục tiêu

Xây dựng hệ thống máy bán hàng tự động theo từng giai đoạn, bảo đảm mỗi giai đoạn có thể kiểm tra độc lập trước khi tích hợp với các phần còn lại.

Các mục tiêu chính:

- Khách hàng quét QR cố định trên máy để truy cập trang đặt hàng.
- Khách hàng xem sản phẩm, chọn số lượng và tạo đơn hàng.
- Hệ thống hiển thị QR thanh toán theo đơn hàng.
- Backend xác minh giao dịch thanh toán tự động thông qua webhook của nhà cung cấp.
- Sau khi xác minh thanh toán thành công, backend gửi lệnh qua MQTT để ESP32 điều khiển đúng vị trí bán hàng.
- Hệ thống cập nhật trạng thái đơn hàng, tồn kho và kết quả giao hàng.
- Quản trị viên có thể quản lý sản phẩm, giá và tồn kho.

**Tài liệu bắt buộc phải tuân thủ:** `PRODUCT_SPEC.md`.

Nếu tài liệu này có điểm chưa rõ, hãy dừng tại điểm đó, trình bày vấn đề và đề xuất phương án. Không tự ý thay đổi yêu cầu nghiệp vụ hoặc kiến trúc.

---

## 2. Quy tắc làm việc bắt buộc

### 2.1. Đọc tài liệu trước khi lập trình

Trước khi thực hiện bất kỳ thay đổi code nào, hãy:

1. Đọc toàn bộ `PRODUCT_SPEC.md`.
2. Đọc toàn bộ `IMPLEMENTATION_PLAN.md`.
3. Kiểm tra cấu trúc thư mục và trạng thái Git hiện tại.
4. Kiểm tra môi trường, các phần mềm đã cài đặt và các dịch vụ đang có.
5. Liệt kê những gì đã có, còn thiếu, chưa rõ và có thể ảnh hưởng đến giai đoạn hiện tại.

Không cài phần mềm hoặc tạo hàng loạt file trước khi hoàn thành bước kiểm tra này.

### 2.2. Chỉ làm một giai đoạn mỗi lần

- Chỉ thực hiện giai đoạn được yêu cầu trong prompt hiện tại.
- Không tự động chuyển sang giai đoạn tiếp theo.
- Không tạo trước các tính năng thuộc giai đoạn sau, trừ khi cần một phần tối thiểu để kiểm thử.
- Sau khi hoàn thành, phải báo cáo kết quả và chờ xác nhận.
- Nếu kiểm thử thất bại, ưu tiên tìm nguyên nhân và sửa trong phạm vi giai đoạn hiện tại.

### 2.3. Kiểm tra môi trường trước khi cài đặt

Trước khi cài đặt phần mềm:

1. Kiểm tra hệ điều hành và công cụ hiện có.
2. Kiểm tra phiên bản của các công cụ cần thiết.
3. Liệt kê phần mềm còn thiếu cho giai đoạn hiện tại.
4. Chỉ cài đặt những thành phần thực sự cần thiết.
5. Sau khi cài, chạy lệnh kiểm tra để xác nhận cài đặt thành công.

Nếu cần quyền quản trị, đăng nhập tài khoản, cấp quyền, tạo tài khoản dịch vụ hoặc thực hiện thao tác có rủi ro, hãy dừng lại và hướng dẫn người dùng thực hiện.

Không tự ý xóa dữ liệu, ghi đè cấu hình quan trọng hoặc thay đổi môi trường hiện có khi chưa được xác nhận.

### 2.4. Phân chia trách nhiệm giữa các model

- **Gemini:** phụ trách các file trong `frontend/`.
- **Claude:** phụ trách các file trong `backend/`.
- **Firmware:** làm việc trong `firmware/`, tuân theo giao thức MQTT đã thống nhất.
- **API contract:** là giao diện giao tiếp chính thức giữa frontend và backend.
- **MQTT contract:** là giao thức giao tiếp chính thức giữa backend và ESP32.

Không để hai model tự ý chỉnh sửa cùng một module hoặc tự thay đổi API, cấu trúc dữ liệu, topic MQTT và payload.

Nếu cần sửa chéo module, phải nêu rõ lý do, các file bị ảnh hưởng và kế hoạch kiểm thử hồi quy trước khi thực hiện.

### 2.5. Bảo vệ thông tin nhạy cảm

- Không đưa mật khẩu, OTP, API secret, private key hoặc thông tin đăng nhập vào prompt, source code hay Git.
- Dùng biến môi trường và file `.env` cục bộ để lưu thông tin nhạy cảm.
- Cung cấp `.env.example` với các giá trị mẫu không chứa bí mật.
- Bảo đảm `.env` và các file chứa secret nằm trong `.gitignore`.
- Không đưa thông tin bí mật vào frontend vì biến môi trường frontend có thể bị công khai khi build.
- Không sử dụng giao dịch tiền thật hoặc kích hoạt động cơ thật khi chưa được người dùng cho phép.

---

## 3. Kiến trúc mục tiêu

Tuân theo kiến trúc đã mô tả trong `PRODUCT_SPEC.md`.

| Thành phần | Công nghệ định hướng | Phạm vi |
|---|---|---|
| Frontend | React, TypeScript, Vite, Tailwind CSS | Giao diện khách hàng và quản trị |
| Backend | Java 21, Spring Boot 3.x, Maven | API, nghiệp vụ, thanh toán, điều khiển máy |
| Database | PostgreSQL, Flyway | Lưu sản phẩm, đơn hàng, thanh toán, tồn kho |
| IoT firmware | ESP32 | Điều khiển động cơ và báo cáo trạng thái |
| Message broker | MQTT qua TLS | Truyền lệnh và nhận trạng thái từ ESP32 |
| Payment | Nhà cung cấp hỗ trợ webhook | Nhận và xác minh giao dịch |
| Local development | Docker Compose khi phù hợp | Chạy các dịch vụ phục vụ phát triển |
| Version control | Git và GitHub | Lưu các mốc ổn định |

Không bắt buộc phải cài Docker hoặc triển khai cloud ngay từ đầu. Chỉ thiết lập những gì cần cho giai đoạn hiện tại.

---

## 4. Cấu trúc thư mục mục tiêu

Cấu trúc cuối cùng có thể điều chỉnh khi cần, nhưng phải giữ ranh giới module rõ ràng.

```text
Vending-Machine-Prototype/
├── PRODUCT_SPEC.md
├── IMPLEMENTATION_PLAN.md
├── README.md
├── .gitignore
├── .env.example
├── docker-compose.yml
├── frontend/
├── backend/
├── firmware/
└── docs/
    ├── API_CONTRACT.md
    ├── MQTT_CONTRACT.md
    ├── TESTING.md
    └── DEPLOYMENT.md
```

Không cần tạo tất cả file ngay ở giai đoạn đầu. Chỉ tạo file khi có mục đích sử dụng rõ ràng.

---

## 5. Các giai đoạn triển khai

### Giai đoạn 0 — Kiểm tra dự án và xác nhận tính khả thi

**Mục tiêu:** Hiểu tình trạng hiện tại trước khi thay đổi bất cứ thứ gì.

Công việc:

1. Đọc `PRODUCT_SPEC.md` và `IMPLEMENTATION_PLAN.md`.
2. Kiểm tra thư mục dự án, Git repository và các file hiện có.
3. Kiểm tra các công cụ đã cài: Java, Maven, Node.js, npm, Git, Docker và công cụ dành cho ESP32 nếu có.
4. Xác định các công cụ thực sự cần thiết cho bước khởi tạo.
5. Xác định những quyết định chưa được chốt:
   - Model ESP32 và driver động cơ.
   - Loại động cơ, cách xác định vị trí bán hàng và cảm biến xác nhận giao hàng nếu có.
   - Nhà cung cấp thanh toán và cách xác minh giao dịch.
   - MQTT broker và cách cấp thông tin kết nối.
   - Môi trường triển khai backend và database.
6. Kiểm tra sớm khả năng đăng ký, cấu hình và thử nghiệm các dịch vụ cần thiết, đặc biệt là thanh toán và MQTT.
7. Báo cáo những rủi ro có thể ngăn cản việc hoàn thành sản phẩm thực tế.

**Không được:** tự ý cài toàn bộ phần mềm, tạo toàn bộ code hoặc triển khai lên cloud.

**Điều kiện hoàn thành:**

- Có báo cáo môi trường hiện tại.
- Có danh sách phần mềm cần cài và phần mềm đã có.
- Có danh sách quyết định cần người dùng xác nhận.
- Có đề xuất thứ tự triển khai phù hợp.
- Chưa có thay đổi code ngoài những gì người dùng đã cho phép.

**Git checkpoint:** chưa bắt buộc tạo commit nếu chưa có thay đổi.

### Giai đoạn 1 — Khởi tạo cấu trúc và thống nhất giao thức

**Mục tiêu:** Tạo nền tảng chung để các module có thể phát triển độc lập.

Công việc:

1. Khởi tạo cấu trúc thư mục cần thiết.
2. Tạo hoặc kiểm tra `.gitignore` và `.env.example`.
3. Tạo `README.md` hướng dẫn cài đặt và chạy dự án ở mức cơ bản.
4. Xác định API contract giữa frontend và backend trong `docs/API_CONTRACT.md`.
5. Xác định MQTT contract giữa backend và firmware trong `docs/MQTT_CONTRACT.md`.
6. Thống nhất tên trường dữ liệu, kiểu dữ liệu, trạng thái đơn hàng, mã lỗi và định dạng thời gian.
7. Xác định cách xử lý yêu cầu lặp, xác thực thiết bị và tương quan lệnh với đơn hàng.

API và MQTT contract phải dựa trên `PRODUCT_SPEC.md`. Nếu có xung đột hoặc thiếu thông tin, hãy báo cáo trước khi tự chọn phương án.

**Điều kiện hoàn thành:**

- Cấu trúc thư mục rõ ràng.
- Các hợp đồng giao tiếp được ghi thành tài liệu.
- Không có secret trong repository.
- Các bên có thể đọc và triển khai theo cùng một hợp đồng.

**Git checkpoint:** `chore: initialize project structure and contracts`

### Giai đoạn 2 — Khởi tạo backend Java và database

**Model phụ trách:** Claude.

**Mục tiêu:** Có backend Spring Boot chạy được và kết nối PostgreSQL.

Công việc:

1. Khởi tạo backend bằng Java 21, Spring Boot 3.x và Maven.
2. Thiết lập cấu hình theo môi trường, không hardcode thông tin kết nối.
3. Thiết lập PostgreSQL và Flyway.
4. Tạo schema theo `PRODUCT_SPEC.md`.
5. Tạo các lớp theo trách nhiệm rõ ràng: controller, service, repository, entity và DTO khi phù hợp.
6. Tạo health endpoint.
7. Tạo các API sản phẩm tối thiểu theo API contract.
8. Viết kiểm thử cho logic nghiệp vụ cơ bản.
9. Chạy build và kiểm thử tự động.

Nếu chưa có PostgreSQL, trước tiên xác định phương án local phù hợp. Không yêu cầu người dùng tạo database cloud nếu chưa cần thiết.

**Điều kiện hoàn thành:**

- Backend build thành công.
- Ứng dụng khởi động được.
- Health endpoint trả về kết quả mong đợi.
- Migration chạy thành công trên database thử nghiệm.
- API sản phẩm hoạt động đúng hợp đồng.
- Các bài kiểm thử đã viết đều qua.
- Báo cáo rõ những phần chưa kiểm thử được.

**Git checkpoint:** `feat: initialize Java backend and database`

### Giai đoạn 3 — Khởi tạo frontend

**Model phụ trách:** Gemini.

**Mục tiêu:** Có giao diện hoạt động và có thể tích hợp với backend.

Công việc:

1. Khởi tạo React, TypeScript, Vite và Tailwind CSS.
2. Tạo cấu trúc component và cấu hình API client.
3. Tạo trang khách hàng truy cập từ QR cố định.
4. Hiển thị danh sách sản phẩm, giá, số lượng còn hàng và lựa chọn mua.
5. Tạo giỏ hàng hoặc luồng chọn sản phẩm phù hợp với `PRODUCT_SPEC.md`.
6. Tạo các trạng thái loading, lỗi, hết hàng và không có dữ liệu.
7. Tạo giao diện theo dõi đơn hàng và khu vực thanh toán theo API contract.
8. Tạo khu vực quản trị nếu phạm vi giao diện đã được xác định.
9. Viết kiểm thử phù hợp cho các luồng quan trọng.

Có thể sử dụng dữ liệu mẫu trong quá trình phát triển giao diện, nhưng phải ghi rõ đâu là dữ liệu mẫu và đâu là dữ liệu từ backend.

Không được giả lập thanh toán thành công trong luồng production.

**Điều kiện hoàn thành:**

- Frontend chạy được ở môi trường local.
- Giao diện hiển thị đúng dữ liệu mẫu hoặc dữ liệu từ backend.
- Luồng chọn sản phẩm hoạt động.
- Các trạng thái lỗi và loading có thể quan sát được.
- Frontend không chứa secret hoặc nghiệp vụ xác minh thanh toán.
- Các bài kiểm thử đã viết đều qua.

**Git checkpoint:** `feat: initialize customer frontend`

### Giai đoạn 4 — Hoàn thiện nghiệp vụ đơn hàng

**Model phụ trách:** Claude cho backend; Gemini chỉ điều chỉnh frontend khi cần và phải tuân theo API contract.

**Mục tiêu:** Hoàn thiện luồng đặt hàng trước khi kết nối thanh toán tiền thật.

Công việc:

1. Tạo đơn hàng từ sản phẩm được chọn.
2. Kiểm tra giá và tồn kho ở backend.
3. Tính tổng tiền phía backend; không tin tổng tiền do frontend gửi lên.
4. Lưu đơn hàng và các mặt hàng thuộc đơn hàng vào database.
5. Quản lý trạng thái đơn hàng theo state machine trong `PRODUCT_SPEC.md`.
6. Xử lý trường hợp hết hàng, dữ liệu không hợp lệ và đơn hàng trùng lặp.
7. Hoàn thiện API tra cứu trạng thái đơn hàng.
8. Viết kiểm thử cho các trường hợp thành công và thất bại.

Không được trừ tồn kho hoặc chuyển trạng thái chỉ dựa vào việc khách đã mở trang QR thanh toán.

**Điều kiện hoàn thành:**

- Tạo đơn hàng thành công.
- Dữ liệu được lưu đúng trong database.
- Tổng tiền được tính và xác minh ở backend.
- Các trường hợp sai dữ liệu và hết hàng được xử lý.
- Các trạng thái đơn hàng tuân thủ hợp đồng.
- Kiểm thử nghiệp vụ và kiểm thử database đều qua.

**Git checkpoint:** `feat: implement order management`

### Giai đoạn 5 — Tích hợp thanh toán và xác minh webhook

**Model phụ trách:** Claude.

**Mục tiêu:** Xây dựng luồng xác minh giao dịch an toàn trước khi cho phép điều khiển máy.

Công việc:

1. Chốt nhà cung cấp thanh toán và tài liệu tích hợp.
2. Xác nhận phương thức tạo QR thanh toán và nhận thông báo giao dịch.
3. Tạo thông tin thanh toán gắn với đúng đơn hàng.
4. Xác minh webhook theo cơ chế chữ ký hoặc phương thức xác thực chính thức của nhà cung cấp.
5. Đối chiếu số tiền, tài khoản nhận, mã tham chiếu và trạng thái giao dịch theo yêu cầu thực tế.
6. Xử lý webhook gửi lại hoặc gửi nhiều lần bằng cơ chế idempotency.
7. Dùng transaction và ràng buộc dữ liệu phù hợp để tránh xử lý một giao dịch nhiều lần.
8. Ghi nhận giao dịch và lịch sử trạng thái.
9. Tạo kiểm thử cho webhook hợp lệ, không hợp lệ, sai số tiền và giao dịch lặp.
10. Thử nghiệm bằng môi trường sandbox hoặc dữ liệu giả lập được kiểm soát trước khi dùng tiền thật.

**Quy tắc an toàn bắt buộc:**

- Chỉ chuyển đơn hàng sang trạng thái đã thanh toán khi backend xác minh được giao dịch hợp lệ.
- Không tin ảnh chụp màn hình, nội dung frontend hoặc thông báo do trình duyệt tự gửi.
- Không điều khiển động cơ chỉ vì khách đã quét QR.
- Không giả định rằng mọi ngân hàng hoặc mã QR đều hỗ trợ webhook.
- Không đưa thông tin nhạy cảm vào log.

**Điều kiện hoàn thành:**

- Backend tạo được thông tin thanh toán theo phương thức đã chọn.
- Webhook được xác minh theo tài liệu nhà cung cấp.
- Giao dịch sai hoặc giả mạo bị từ chối.
- Giao dịch gửi lặp không tạo ra nhiều lần xử lý thanh toán.
- Đơn hàng chỉ được xác nhận thanh toán khi đủ điều kiện.
- Có kết quả kiểm thử và hướng dẫn cấu hình webhook.

**Git checkpoint:** `feat: integrate verified payment webhook`

### Giai đoạn 6 — Tích hợp MQTT và firmware ESP32

**Model phụ trách:** phần firmware thuộc `firmware/`; Claude phụ trách backend MQTT. Phải tuân thủ MQTT contract.

**Mục tiêu:** Backend có thể gửi lệnh đến đúng thiết bị và nhận phản hồi.

Công việc:

1. Xác định model ESP32, driver, động cơ, nguồn điện và sơ đồ đấu nối thực tế.
2. Chốt MQTT broker, TLS, thông tin xác thực và quyền truy cập topic.
3. Thiết lập kết nối MQTT trong backend.
4. Tạo lệnh giao hàng theo đúng đơn hàng và vị trí bán hàng.
5. Tạo firmware nhận lệnh, kiểm tra dữ liệu và thực hiện điều khiển.
6. Xác định cơ chế xác nhận đã nhận lệnh, đang thực hiện, hoàn thành hoặc thất bại.
7. Xử lý lệnh trùng, mất kết nối, timeout và khởi động lại thiết bị.
8. Ghi nhận trạng thái lệnh ở backend.
9. Kiểm thử bằng thiết bị giả lập hoặc MQTT test client trước khi điều khiển động cơ thật.
10. Sau khi kiểm thử mô phỏng đạt yêu cầu, thử nghiệm từng động cơ với sự giám sát trực tiếp.

**Quy tắc an toàn bắt buộc:**

- Firmware phải xác thực cấu trúc và phạm vi của lệnh.
- Không cho phép lệnh không hợp lệ kích hoạt động cơ.
- Không gửi lệnh điều khiển lại một cách mù quáng khi chưa xác định trạng thái lệnh trước đó.
- Không giả định đã giao hàng thành công chỉ vì MQTT đã gửi hoặc nhận được lệnh.
- Không thử động cơ thật nếu chưa xác nhận điện áp, dòng điện, driver và cách đấu nối.
- Cần xác định rõ giới hạn thời gian chạy động cơ và cách dừng an toàn theo phần cứng thực tế.

**Điều kiện hoàn thành:**

- Backend kết nối MQTT thành công.
- Thiết bị nhận được lệnh đúng topic và payload.
- Thiết bị phản hồi trạng thái theo hợp đồng.
- Lệnh lỗi, lệnh trùng và mất kết nối được xử lý.
- Đã kiểm thử mô phỏng trước khi thử nghiệm phần cứng.
- Kết quả điều khiển động cơ thật được ghi nhận riêng, không nhầm với kết quả mô phỏng.

**Git checkpoint:** `feat: integrate MQTT and ESP32 firmware`

### Giai đoạn 7 — Tích hợp toàn bộ hệ thống

**Mục tiêu:** Chạy hoàn chỉnh luồng từ khách đặt hàng đến máy giao sản phẩm.

Luồng kiểm thử chính:

1. Khách quét QR cố định.
2. Frontend tải danh sách sản phẩm từ backend.
3. Khách chọn sản phẩm.
4. Backend kiểm tra giá và tồn kho, sau đó tạo đơn hàng.
5. Frontend hiển thị thông tin thanh toán.
6. Khách thực hiện thanh toán thử nghiệm.
7. Backend xác minh giao dịch qua webhook.
8. Backend phát lệnh MQTT sau khi xác minh thanh toán.
9. ESP32 xử lý lệnh và phản hồi trạng thái.
10. Backend cập nhật trạng thái đơn hàng.
11. Frontend hiển thị kết quả cho khách.
12. Hệ thống cập nhật tồn kho theo quy tắc trong `PRODUCT_SPEC.md`.

Kiểm thử bổ sung:

- Thanh toán thất bại.
- Webhook gửi lặp.
- Thanh toán sai số tiền.
- Đơn hàng hết hạn hoặc bị hủy.
- Thiết bị mất kết nối.
- Lệnh giao hàng timeout.
- Động cơ không phản hồi hoặc giao hàng thất bại.
- Khách làm mới trang hoặc mở lại trang đơn hàng.
- Hai khách cùng đặt sản phẩm cuối cùng.
- Backend hoặc thiết bị khởi động lại giữa chừng.

**Điều kiện hoàn thành:**

- Các luồng chính hoạt động từ đầu đến cuối.
- Các trường hợp lỗi quan trọng đã được kiểm thử.
- Không có lệnh giao hàng phát sinh từ thanh toán chưa được xác minh.
- Trạng thái đơn hàng, giao dịch và lệnh thiết bị không mâu thuẫn.
- Tồn kho không bị cập nhật sai do xử lý lặp.
- Có danh sách lỗi còn tồn tại và mức độ ảnh hưởng.

**Git checkpoint:** `test: verify end-to-end vending flow`

### Giai đoạn 8 — Triển khai và cấu hình môi trường thực tế

**Mục tiêu:** Đưa các thành phần cần thiết lên môi trường triển khai phù hợp.

Công việc:

1. Chọn nơi triển khai frontend, backend, database và MQTT broker.
2. Xác nhận yêu cầu về chi phí, giới hạn sử dụng, kết nối mạng và thời gian hoạt động.
3. Thiết lập tài khoản dịch vụ và quyền truy cập cần thiết.
4. Cấu hình biến môi trường và secret trên môi trường triển khai.
5. Chạy database migration theo quy trình an toàn.
6. Cấu hình HTTPS và các URL cần thiết.
7. Cấu hình webhook công khai của nhà cung cấp thanh toán.
8. Kiểm tra backend có thể kết nối database và MQTT broker.
9. Kiểm tra logging, xử lý lỗi và cách khôi phục dịch vụ.
10. Chạy kiểm thử smoke test sau triển khai.

Không tự động tạo tài khoản, chấp nhận điều khoản, phát sinh chi phí hoặc triển khai lên môi trường thật khi chưa có sự xác nhận của người dùng.

**Điều kiện hoàn thành:**

- Frontend truy cập được qua URL đã cấu hình.
- Backend hoạt động và có thể được kiểm tra.
- Database migration thành công.
- Webhook thanh toán truy cập được và được xác minh.
- MQTT được cấu hình an toàn.
- Không có secret trong repository hoặc bundle frontend.
- Có hướng dẫn chạy, triển khai và xử lý lỗi thường gặp.

**Git checkpoint:** `deploy: prepare production environment`

### Giai đoạn 9 — Nghiệm thu và tài liệu bàn giao

**Mục tiêu:** Bảo đảm nhóm có thể chạy, trình diễn và tiếp tục phát triển sản phẩm.

Công việc:

1. Chạy lại toàn bộ các bài kiểm thử có thể tự động hóa.
2. Kiểm tra luồng chính bằng thiết bị thực nếu phần cứng đã sẵn sàng.
3. Kiểm tra bảo mật cơ bản, cấu hình secret và quyền truy cập.
4. Cập nhật README và tài liệu cài đặt.
5. Ghi rõ cách chạy frontend, backend, database và firmware.
6. Ghi rõ các dịch vụ bên ngoài cần có.
7. Ghi nhận các giới hạn đã biết, phần chưa hoàn thiện và hướng phát triển tiếp theo.

**Điều kiện hoàn thành:**

- Có hướng dẫn chạy dự án từ đầu.
- Có kết quả kiểm thử cuối cùng.
- Có danh sách chức năng đã hoàn thành và chưa hoàn thành.
- Có thể trình diễn luồng bán hàng theo phạm vi thực tế.
- Không tuyên bố hệ thống đã sẵn sàng vận hành thật nếu thanh toán, bảo mật hoặc phần cứng chưa được kiểm chứng.

**Git checkpoint:** `docs: finalize implementation and handover`

---

## 6. Quy trình bắt buộc cho mỗi giai đoạn

Khi bắt đầu một giai đoạn, Antigravity phải thực hiện theo trình tự:

1. Đọc lại tài liệu đặc tả và kế hoạch.
2. Kiểm tra trạng thái Git và các thay đổi chưa commit.
3. Kiểm tra môi trường hiện tại.
4. Liệt kê các bước sẽ thực hiện.
5. Chỉ thực hiện phần việc được yêu cầu.
6. Chạy các lệnh build, test, lint hoặc kiểm tra phù hợp.
7. Kiểm tra các file đã thay đổi và tác động sang module khác.
8. Báo cáo kết quả theo mẫu ở mục 7.
9. Dừng và chờ xác nhận của người dùng.

Nếu gặp lỗi:

- Không che giấu lỗi hoặc báo thành công khi chưa có bằng chứng.
- Đọc log và xác định nguyên nhân có thể xảy ra.
- Sửa trong phạm vi giai đoạn hiện tại.
- Nếu không thể sửa an toàn, dừng lại và nêu rõ thông tin cần người dùng cung cấp.

---

## 7. Mẫu báo cáo bắt buộc sau mỗi giai đoạn

Antigravity phải báo cáo theo cấu trúc sau:

### A. Kết quả

- Giai đoạn:
- Trạng thái: Hoàn thành / Chưa hoàn thành / Bị chặn.
- Các chức năng đã hoàn thành:

### B. File thay đổi

- File mới:
- File chỉnh sửa:
- File đã xóa:
- Lý do thay đổi file nằm ngoài module phụ trách, nếu có:

### C. Lệnh đã chạy

Ghi các lệnh quan trọng đã thực hiện và kết quả thực tế.

Ví dụ:

```text
Lệnh: mvn test
Kết quả: PASS
Mô tả: Các bài kiểm thử đã chạy thành công.
```

Không ghi PASS nếu lệnh chưa được chạy hoặc không có bằng chứng kết quả.

### D. Kiểm thử

- Build:
- Unit test:
- Integration test:
- Lint:
- Kiểm thử thủ công:
- Những trường hợp chưa kiểm thử:

### E. Cấu hình và phần mềm

- Phần mềm đã sử dụng:
- Phần mềm đã cài:
- Cấu hình đã thay đổi:
- Các bước người dùng phải tự thực hiện:

### F. Vấn đề còn lại

- Lỗi chưa xử lý:
- Rủi ro:
- Quyết định cần người dùng xác nhận:
- Việc cần làm tiếp theo:

### G. Git

- Trạng thái Git:
- Commit đã tạo, nếu có:
- Các thay đổi chưa commit:

**Kết thúc báo cáo bằng yêu cầu xác nhận trước khi chuyển sang giai đoạn tiếp theo.**

---

## 8. Quy tắc Git và lưu mốc ổn định

Trước khi sửa code:

1. Chạy `git status`.
2. Kiểm tra các thay đổi chưa commit.
3. Không ghi đè hoặc xóa thay đổi của người dùng.
4. Nếu thư mục chưa phải Git repository, chỉ khởi tạo khi được phép.

Sau khi một giai đoạn đạt yêu cầu:

1. Kiểm tra `git diff`.
2. Xác nhận không có secret hoặc file sinh tự động không cần thiết.
3. Chạy lại các kiểm thử cần thiết.
4. Tạo commit với thông điệp rõ ràng.
5. Xác nhận commit tồn tại bằng `git log` hoặc lệnh phù hợp.

Không tự động push lên GitHub nếu người dùng chưa yêu cầu.

Nếu một giai đoạn mới gây lỗi:

- Kiểm tra diff và log trước.
- Ưu tiên sửa lỗi hoặc hoàn tác đúng phần thay đổi gây lỗi.
- Không tự ý dùng `git reset --hard`, xóa dữ liệu hoặc viết lại lịch sử Git.
- Chỉ quay lại commit ổn định sau khi xác định rõ phạm vi ảnh hưởng và được người dùng xác nhận khi cần.

---

## 9. Nguyên tắc phối hợp giữa các module

### Frontend

- Gemini sở hữu `frontend/`.
- Frontend giao tiếp với backend qua API contract.
- Không tự ý thêm endpoint hoặc thay đổi định dạng response mà không thống nhất.
- Không chứa logic bí mật hoặc tự xác nhận giao dịch thanh toán.

### Backend

- Claude sở hữu `backend/`.
- Backend là nơi kiểm tra giá, tồn kho, trạng thái đơn hàng và xác minh thanh toán.
- Backend là thành phần quyết định khi nào được phát lệnh giao hàng.
- Không để frontend gửi trực tiếp lệnh điều khiển động cơ.

### Firmware

- Firmware nằm trong `firmware/`.
- Backend và firmware giao tiếp qua MQTT contract.
- Phải có cơ chế xử lý lệnh trùng, timeout và trạng thái lỗi phù hợp.
- Không coi việc gửi MQTT thành công là bằng chứng sản phẩm đã rơi xuống.

### API contract và MQTT contract

Nếu thay đổi hợp đồng giao tiếp:

1. Nêu rõ lý do thay đổi.
2. Cập nhật tài liệu hợp đồng.
3. Xác định các module bị ảnh hưởng.
4. Cập nhật kiểm thử liên quan.
5. Kiểm tra tương thích trước khi tích hợp.

---

## 10. Quy tắc về chất lượng và phạm vi

- Ưu tiên chức năng chạy được, có kiểm thử và dễ hiểu hơn kiến trúc quá phức tạp.
- Không tự ý thêm tính năng ngoài phạm vi chỉ vì công cụ có thể tạo ra chúng.
- Không tạo dữ liệu giả nhưng trình bày như dữ liệu thật.
- Không coi build thành công là bằng chứng toàn bộ hệ thống đã hoạt động.
- Không coi kiểm thử mô phỏng là bằng chứng phần cứng thật hoạt động.
- Không coi webhook giả lập là bằng chứng nhà cung cấp thanh toán thực tế đã được tích hợp.
- Không tuyên bố hoàn thành nếu các tiêu chí nghiệm thu của giai đoạn chưa đạt.
- Nếu một yêu cầu không thể thực hiện do thiếu tài khoản, quyền truy cập, thiết bị hoặc thông tin kỹ thuật, hãy dừng tại điểm đó và hướng dẫn người dùng.

---

## 11. Prompt khung dùng cho từng giai đoạn

Khi yêu cầu Antigravity thực hiện một giai đoạn, sử dụng prompt khung sau và thay phần trong ngoặc vuông:

> Hãy thực hiện Giai đoạn [SỐ VÀ TÊN GIAI ĐOẠN] trong `IMPLEMENTATION_PLAN.md`.
>
> Trước khi sửa code, hãy đọc toàn bộ `PRODUCT_SPEC.md` và `IMPLEMENTATION_PLAN.md`. Kiểm tra trạng thái Git, cấu trúc dự án, môi trường hiện tại và các phần mềm đã cài.
>
> Chỉ thực hiện phạm vi của giai đoạn này. Liệt kê các bước sẽ làm và phần mềm còn thiếu trước khi cài đặt. Chỉ cài những gì thực sự cần thiết. Nếu cần quyền quản trị, đăng nhập, cấp quyền, phát sinh chi phí hoặc thực hiện thao tác có rủi ro, hãy dừng lại và hướng dẫn tôi.
>
> Tuân thủ ranh giới module, API contract và MQTT contract. Không tự ý sửa module của model khác, thay đổi kiến trúc hoặc thực hiện giai đoạn tiếp theo.
>
> Sau khi thực hiện, hãy chạy các lệnh build và kiểm thử phù hợp. Chỉ báo PASS khi có kết quả thực tế. Không che giấu lỗi, không giả lập kết quả và không đưa secret vào source code.
>
> Cuối cùng, báo cáo đầy đủ theo mục 7 của `IMPLEMENTATION_PLAN.md`: file đã thay đổi, lệnh đã chạy, kiểm thử đã qua, lỗi còn lại, việc cần tôi thực hiện và trạng thái Git.
>
> Dừng lại sau báo cáo và chờ tôi xác nhận trước khi chuyển sang giai đoạn tiếp theo.

---

## 12. Quy tắc cuối cùng

`PRODUCT_SPEC.md` xác định **sản phẩm cần xây dựng**.

`IMPLEMENTATION_PLAN.md` xác định **thứ tự triển khai, cách kiểm thử, trách nhiệm từng module và điều kiện hoàn thành**.

Không được xem việc tạo xong code là hoàn thành sản phẩm. Chỉ đánh dấu hoàn thành khi các tiêu chí tương ứng đã được kiểm tra bằng kết quả thực tế.

Mọi thay đổi quan trọng đối với yêu cầu sản phẩm hoặc kiến trúc phải được thống nhất trước khi triển khai.