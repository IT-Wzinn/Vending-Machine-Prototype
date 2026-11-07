# BÁO CÁO BÀN GIAO PHẦN MỀM & AUDIT CHECKPOINT (SOFTWARE HANDOVER REPORT)
**Dự án:** Vending Machine Prototype  
**Thời điểm lập báo cáo:** 2026-11-04  
**Trạng thái kiểm thử:** Pass toàn bộ bộ kiểm thử Backend (180/180), Frontend (55/55), Frontend Build, Python Simulator (11/11), và E2E Test thành công ngày 2026-11-04.

---

## 1. Kiến Trúc Dự Án & Công Nghệ (Project Architecture & Technologies)

Hệ thống **Vending Machine Prototype** được thiết kế theo mô hình Internet-of-Things (IoT) kết hợp Web Application hiện đại, cho phép khách hàng mua hàng không cần tiền mặt và không cần cài đặt ứng dụng:

```
[ Điện Thoại Khách Hàng ] (Quét mã QR cố định trên thân máy)
            │
            ▼ (HTTPS)
[ Frontend Web App (Vercel) ] (React 19 + TypeScript + Tailwind CSS)
            │
            ▼ (REST API qua HTTPS / JSON)
[ Backend Core (Render) ] (Spring Boot 3.3.5 / Java 21)
       │                 │
       ▼ (JDBC SSL)      ▼ (MQTT over TLS / Port 8883)
[ Database (Neon) ]  [ MQTT Broker (EMQX Cloud) ]
(PostgreSQL 16)          │
                         ▼ (MQTTS QoS 1)
               [ ESP32 Hardware / Device Simulator ]
```

### Công nghệ sử dụng:
- **Frontend (`frontend/`):**
  - **Framework & Tooling:** React 19.2.8, TypeScript 6.0, Vite 8.3.4, Tailwind CSS 3.4, Lucide React, QRCode.react.
  - **Testing:** Vitest 5.0, Testing Library (React + Jest-DOM + User-Event), JSDOM.
  - **Giao diện:** Mobile-first customer ordering (quét mã QR chọn sản phẩm, sinh VietQR động theo chuẩn NAPAS 247, polling trạng thái đơn hàng), Admin Reconciliation Dashboard (đăng nhập JWT HttpOnly cookie, xác thực CSRF qua header `X-Requested-With`, xem lịch sử giao dịch và đối soát thủ công).

- **Backend (`backend/`):**
  - **Core Framework:** Spring Boot 3.3.5, Java 21 (Eclipse Temurin), Spring Security, Spring Data JPA / Hibernate 6.5.
  - **Database Migration:** Flyway Community Edition (Migration V1 -> V4).
  - **IoT / Messaging:** Eclipse Paho MQTTv3 Client (`MqttDeviceAdapter`), quản lý vòng đời kết nối với EMQX Cloud qua TLS, tự động subscribe lại topic khi reconnect.
  - **Testing:** JUnit 5, Mockito, Spring Boot Test, H2 In-Memory Database (PostgreSQL dialect simulation), Surefire.

- **Thiết bị ngoại vi & Giả lập (`device-simulator/` & `firmware/`):**
  - **Python Device Simulator (`device-simulator/`):** Python 3.12, Paho-MQTT 2.x, Python-dotenv. Hỗ trợ mô phỏng toàn diện vòng đời lệnh nhả hàng: nhận lệnh, gửi `COMMAND_ACCEPTED`, delay mô phỏng motor chạy, gửi `DISPENSE_RESULT` (`SUCCESS`, `FAILURE`, `UNCERTAIN`), xử lý idempotency chống trùng lệnh, gửi heartbeat định kỳ 30s.
  - **ESP32 Firmware (`firmware/`):** Đặc tả kỹ thuật C++/Arduino đầy đủ tại `PRODUCT_SPEC.md §14` và `docs/MQTT_CONTRACT.md`, sẵn sàng cho giai đoạn nạp lên vi điều khiển ESP32 thật.

---

## 2. Các Tính Năng Đã Hoàn Thiện (Completed Software Features)

1. **Khách hàng đặt hàng (Customer Order Flow):**
   - Truy cập qua QR code URL không cần tài khoản: `/order?machineId=VM001`.
   - Hiển thị danh mục sản phẩm, tình trạng còn hàng theo thời gian thực.
   - Cơ chế khóa kho tạm thời (`reserved_quantity`): Khi tạo đơn hàng, hệ thống giữ chỗ số lượng sản phẩm để tránh trường hợp hai người cùng mua một món đồ cuối cùng (race condition).
   - Tự động hủy đơn và giải phóng kho tạm giữ khi hết hạn thanh toán (mặc định 15 phút).

2. **Thanh toán chuyển khoản VietQR & Webhook SePay:**
   - Tạo mã chuyển khoản chuẩn NAPAS 247 kèm chuỗi nội dung chuyển khoản duy nhất (`transfer_reference`).
   - Webhook SePay xử lý tự động với kiểm tra tính toàn vẹn và chống lặp giao dịch (`provider_transaction_id`).
   - Khớp đơn hàng theo đúng số tiền và nội dung chuyển khoản.
   - Hỗ trợ chế độ Demo (`PAYMENT_DEMO_MODE=true`) cho phép kiểm thử end-to-end mà không trừ tiền thật.

3. **Cơ chế nhả hàng qua MQTT over TLS (Dispense & IoT Protocol):**
   - Đóng gói lệnh nhả hàng chuẩn hóa `DispenseCommandMessage` phát tới topic `vending/{machineId}/commands` (QoS 1).
   - Tiếp nhận sự kiện hai pha từ thiết bị: `COMMAND_ACCEPTED` và `DISPENSE_RESULT`.
   - Backend phản hồi `EventAckMessage` (`vending/{machineId}/events/ack`) để thiết bị xác nhận dữ liệu đã được lưu bền vững vào database.
   - Tự động hoàn tất đơn hàng (`COMPLETED`) và trừ tồn kho thực tế (`stock_quantity`) khi kết quả nhả hàng là `SUCCESS`.
   - Đưa đơn hàng vào trạng thái cần đối soát (`NEEDS_RECONCILIATION`) và bảo vệ kho hàng khi kết quả là `UNCERTAIN` hoặc gặp sự cố bất thường.

4. **Trang Quản trị & Đối soát (Admin Reconciliation Portal):**
   - Đăng nhập bảo mật với JWT ký HMAC-SHA256 lưu trong HttpOnly Cookie (`app_admin_token`).
   - Cơ chế phòng chống CSRF nghiêm ngặt: yêu cầu header bắt buộc `X-Requested-With: XMLHttpRequest` cho mọi thao tác ghi/đổi trạng thái.
   - Lọc và tìm kiếm danh sách đơn hàng theo trạng thái: `NEEDS_RECONCILIATION`, `PAID`, `DISPENSING`, `DISPENSE_FAILED`, `EXPIRED`.
   - Xem chi tiết dòng thời gian đơn hàng (order timeline), lịch sử thanh toán và toàn bộ log sự kiện thiết bị (`device_events`).
   - Modal ra quyết định đối soát: `REFUND` (hoàn tiền), `DISPENSE_RETRY` (nhả hàng lại), `MARK_COMPLETED` (xác nhận thủ công) kèm lý do giải trình bắt buộc phục vụ kiểm toán.

---

## 3. Trạng Thái Triển Khai Cloud (Deployment Status)

| Thành phần | Nền tảng Cloud | Cấu hình & Hiện trạng |
|---|---|---|
| **Frontend** | **Vercel** | - Static hosting, CDN toàn cầu, SSL tự động.<br>- Build command: `npm run build` (`tsc -b && vite build`).<br>- Output directory: `dist`.<br>- Kết nối API backend qua biến môi trường `VITE_API_BASE_URL`. |
| **Backend** | **Render** | - Chạy qua Dockerfile multi-stage (Temurin Java 21 JRE).<br>- Port: `8080`.<br>- Health check: `/actuator/health` trả về `{"status":"UP"}`.<br>- Profile: `prod` (hoặc `local`).<br>- Toàn bộ biến môi trường chuẩn hóa tại `backend/.env.render.example`. |
| **Database** | **Neon.tech** | - PostgreSQL 16+ Serverless tại vùng Singapore (`ap-southeast-1`).<br>- Kết nối JDBC SSL: `jdbc:postgresql://.../neondb?sslmode=require`.<br>- Tự động migrate toàn bộ schema qua Flyway V1, V2, V3, V4 ngay khi backend khởi động. |
| **MQTT Broker** | **EMQX Cloud** | - Serverless Broker tại vùng `asia-southeast1`.<br>- Host: `f18d138e.ala.asia-southeast1.emqxsl.com`, Port: `8883` (TLS).<br>- Xác thực username/password riêng biệt cho Backend (`backend-vending-001`) và ESP32 (`esp32-vm001-sim`). |

---

## 4. Kết Quả Kiểm Thử E2E Thành Công Gần Nhất (Latest Successful E2E Test)

- **Ngày thực hiện:** 2026-11-04
- **Mã đơn hàng (Order Code):** `VM001-20261104-675E`
- **Số tiền thanh toán:** `8,000 VND`
- **Vị trí khay hàng (Slot):** `Slot 1` (Aquafina 500ml)
- **Thiết bị thực hiện:** Python ESP32 Device Simulator kết nối EMQX Cloud qua TLS
- **Kết quả giả lập nhả hàng (Simulator result):** `SUCCESS`
- **Xác nhận từ Backend (Backend ACK):** `PROCESSED`
- **Trạng thái đơn hàng sau cùng:** `COMPLETED`
- **Biến động tồn kho Aquafina:** `3 → 2` (Số lượng thực giảm từ 3 xuống 2 chai, tạm giữ giải phóng về 0)
- **Mức độ xác minh (Verification Level):** `MOTOR_CYCLE_ONLY`

> **LƯU Ý QUAN TRỌNG:**  
> Kiểm thử E2E trên được thực hiện thông qua **Bộ giả lập phần cứng ESP32 (ESP32 Device Simulator)** qua hạ tầng mạng thực (EMQX Cloud TLS, Neon DB, Spring Boot Backend).  
> **Chưa từng tiến hành thử nghiệm vật lý trên động cơ hoặc tủ máy thật (Physical dispensing has NOT been tested yet).** Mức độ xác thực mới dừng ở mức kiểm tra chu kỳ quay của mô hình motor (`MOTOR_CYCLE_ONLY`).

---

## 5. Báo Cáo Kết Quả Kiểm Thử Toàn Diện (Test Results)

Toàn bộ các bộ kiểm thử tự động của dự án đã được chạy kiểm tra không phá hủy (non-destructive) và cho kết quả thành công tuyệt đối:

### 1. Backend Test Suite (Maven / JUnit 5 / Spring Boot Test)
- **Lệnh thực hiện:** `.\mvnw.cmd test`
- **Môi trường:** Spring Test Profile (`application-test.yml`), Database H2 In-Memory, Flyway disabled trong test context.
- **Tổng số test:** **180**
- **Thành công:** **180**
- **Thất bại (Failures):** **0**
- **Lỗi (Errors):** **0**
- **Bỏ qua (Skipped):** **0**
- **Thời gian chạy:** 48.259s
- **Phạm vi kiểm thử:** Controller REST API, DTO serialization/deserialization, VietQR generation, SePay Webhook processing, Admin JWT security & CSRF filter, MqttDeviceAdapter lifecycle & reconnect, MqttDispenseIntegrationTest, Concurrency reservation tests.

### 2. Frontend Test Suite (React / Vitest)
- **Lệnh thực hiện:** `npm test` (trong thư mục `frontend/`)
- **Tổng số test files:** **11 / 11 files passed**
- **Tổng số unit/integration tests:** **55 / 55 tests passed**
- **Thành công:** **55**
- **Thất bại:** **0**
- **Thời gian chạy:** 17.30s
- **Phạm vi kiểm thử:** CustomerOrderPage, CheckoutModal, ProductCard, CartContext, MachineBanner, AdminLogin, ReconciliationDashboard, ReconciliationOrderDetail, ReconciliationDecisionModal, formatters, AdminApiAndSecurity.

### 3. Frontend Production Build
- **Lệnh thực hiện:** `npm run build` (`tsc -b && vite build`)
- **Kết quả:** Build thành công 100% trong 1.46s, 0 lỗi TypeScript, 0 cảnh báo biên dịch.
- **Dung lượng build:** HTML 1.13 kB, CSS 32.90 kB (gzip 6.74 kB), JS 355.91 kB (gzip 102.04 kB).

### 4. Device Simulator Test Suite (Python)
- **Lệnh thực hiện:** `python -m unittest test_simulator.py`
- **Tổng số tests:** **11 / 11 tests passed**
- **Thời gian chạy:** 0.029s
- **Tự kiểm thử Mock (`python simulator.py --mock-test`):** Toàn bộ schema Heartbeat, Command Accepted và Dispense Result khớp 100% với backend.

---

## 6. Điểm Kiểm Soát Git & Commit Hashes (Git Checkpoint & Commit Hashes)

Các commit mới nhất được kiểm tra và lưu vết:

- **`fe4a7e3`** — `feat(device-simulator): add ESP32 hardware simulator and test suite for MQTT validation`
  - Bổ sung bộ giả lập phần cứng ESP32 hoàn chỉnh kết nối MQTT TLS, cấu hình mẫu, tài liệu và 11 unit test.
- **`b65af43`** — `style(backend): format dispense controller test and trailing newlines`
  - Chuẩn hóa định dạng mã nguồn backend, format MockMvc trong DispenseControllerTest và các newline DTO.
- **`755c465`** — `chore: trigger Vercel deployment`
- **`042835d`** — `fix(mqtt): harden client lifecycle and reconnect subscriptions`
- **`c788361`** — `fix(dispense): validate command existence before persisting device event`
- **`89c4d75`** — `feat: implement MQTT broker integration and adapter for ESP32 hardware communication`

---

## 7. Các Thay Đổi Còn Lại Trong Working Tree (Remaining Uncommitted Changes)

- Tại thời điểm này, toàn bộ mã nguồn hợp lệ đã được commit an toàn vào nhánh `main`.
- Khi tạo báo cáo này, chỉ có duy nhất file `docs/SOFTWARE_HANDOVER_REPORT.md` xuất hiện dưới dạng tài liệu mới.
- **Các file và thư mục bị bỏ qua (Ignored) theo đúng quy tắc bảo mật:**
  - `backend/.env.render` (Chứa credentials Render & Neon thật — Tuyệt đối không commit).
  - `device-simulator/.env` (Chứa mật khẩu MQTT ESP32 thật — Đã ignore).
  - `frontend/.env` (Cấu hình local frontend — Đã ignore).
  - `secrets/backup_vending_dev_pre_e2e_20261025_084913.dump` (Bản dump sao lưu database — Đã ignore).
  - `device-simulator/.venv/`, `backend/target/`, `frontend/dist/`, `frontend/node_modules/` (Thư mục build & virtual env).

---

## 8. Hạn Chế Đã Biết (Known Limitations)

1. **Kiến trúc đơn máy (Single-Machine Scope):**
   - Hệ thống hiện tại đang tối ưu hóa cấu hình cho máy bán hàng mặc định mang mã `VM001`. Cơ chế quản lý cụm nhiều máy (fleet management) chưa được mở rộng ở giao diện.
2. **Cảm biến rơi hàng vật lý (Drop Sensor):**
   - Mức độ xác minh kết quả nhả hàng hiện tại là `MOTOR_CYCLE_ONLY` (mới xác nhận động cơ đã hoàn thành chu kỳ quay). Cần bổ sung cảm biến quang học / cảm biến hồng ngoại rơi hàng vật lý để đạt mức `PHYSICAL_DROP`.
3. **Chế độ thanh toán SePay Demo:**
   - Đang cấu hình ở chế độ Demo (`PAYMENT_DEMO_MODE=true`). Để đưa vào khai thác thương mại với tiền mặt thật, cần liên kết tài khoản ngân hàng doanh nghiệp và kích hoạt SePay Webhook có chữ ký bảo mật.
4. **Thời gian khởi động lạnh của Render Free Tier:**
   - Nếu backend trên Render không nhận request trong 15 phút, phiên bản miễn phí sẽ tự động ngủ (spin down), dẫn tới cold start kéo dài ~40-60 giây ở request đầu tiên. Nên cân nhắc nâng cấp gói trả phí hoặc cấu hình cron ping keep-alive.

---

## 9. Lộ Trình Tích Hợp Phần Cứng Thực Tế (Hardware Integration Roadmap)

Để chuyển từ giai đoạn **Mô phỏng phần mềm (Software Prototype)** sang **Máy bán hàng vật lý thực tế (Physical Vending Machine)**, các bước thực hiện theo lộ trình chuẩn như sau:

```
[ BƯỚC 1: Chuẩn Bị Bo Mạch & Mạch Điều Khiển ]
                     │
                     ▼
[ BƯỚC 2: Cài Đặt & Nạp Firmware ESP32 (Arduino / PlatformIO) ]
                     │
                     ▼
[ BƯỚC 3: Cấu Hình Kết Nối TLS & EMQX Broker ]
                     │
                     ▼
[ BƯỚC 4: Kiểm Thử Quay Động Cơ Không Tải Trên Bàn Thí Nghiệm ]
                     │
                     ▼
[ BƯỚC 5: Tích Hợp Cảm Biến Rơi Hàng (IR Beam Drop Sensor) ]
                     │
                     ▼
[ BƯỚC 6: Lắp Ráp Khung Vỏ Thùng Máy & Thử Nghiệm Thực Tế Toàn Diện ]
```

### Chi tiết từng bước:

1. **Chuẩn bị phần cứng (Hardware Bill of Materials):**
   - 01 Bo mạch vi điều khiển **ESP32-WROOM-32** (hỗ trợ Wi-Fi 2.4GHz & TLS).
   - 01 Module Relay 4 kênh hoặc Mạch điều khiển Mosfet (IRF540N) có Optocoupler cách ly quang để kích hoạt động cơ lò xo 12V/24V.
   - Nguồn cấp: Nguồn tổ ong 12V/24V DC cấp cho motor và module hạ áp DC-DC LM2596 (hoặc Buck converter) cấp nguồn 5V ổn định cho ESP32.
   - Công tắc hành trình (Microswitch) gắn trên trục lò xo để phát hiện điểm dừng chu kỳ 360 độ của motor.

2. **Nạp Firmware ESP32 (`firmware/`):**
   - Viết mã nguồn C++ trên nền **PlatformIO / Arduino Core for ESP32** theo đặc tả tại `PRODUCT_SPEC.md §14`.
   - Sử dụng thư viện `WiFiClientSecure` và `PubSubClient` (hoặc `esp-mqtt`).
   - Cấu hình lưu trữ bộ nhớ bất biến **NVS (Non-Volatile Storage)** để lưu các `commandId` đã thực hiện, đảm bảo nếu ESP32 bị reset đột ngột cũng không nhả hàng hai lần cho cùng một lệnh.

3. **Bảo mật thông tin cấu hình phần cứng:**
   - Tạo file `firmware/include/secrets.h` (đã nằm trong `.gitignore`) để cấu hình Wi-Fi SSID/Password và MQTT credentials (`esp32_vm001`).
   - Tích hợp chứng chỉ gốc CA của EMQX Cloud vào bộ nhớ Flash của ESP32 để xác thực SSL/TLS.

4. **Thử nghiệm trên bàn test (Bench Testing):**
   - Cấp nguồn cho ESP32 và động cơ lò xo trên bàn thử nghiệm.
   - Quan sát log Serial Monitor của ESP32 để xác nhận: kết nối Wi-Fi thành công, kết nối TLS tới EMQX thành công, gửi heartbeat định kỳ.
   - Đặt một đơn hàng qua Frontend demo để kích hoạt lệnh nhả hàng: kiểm tra motor quay đúng 1 vòng và dừng chính xác tại công tắc hành trình, gửi `COMMAND_ACCEPTED` và `DISPENSE_RESULT` lên MQTT.

5. **Nâng cấp cảm biến rơi hàng vật lý (Physical Drop Sensor):**
   - Lắp đặt cặp LED phát - thu hồng ngoại (IR Beam) hoặc cảm biến rung phía dưới máng nhận hàng.
   - Khi chai nước rơi cắt qua tia hồng ngoại, ESP32 ghi nhận tín hiệu và gửi kết quả kèm:  
     `"verificationLevel": "PHYSICAL_DROP"` thay vì `"MOTOR_CYCLE_ONLY"`.

6. **Lắp ráp hoàn thiện và nghiệm thu hiện trường:**
   - Lắp đặt toàn bộ linh kiện vào thân tủ máy bán hàng vật lý.
   - Dán mã QR cố định dẫn tới URL Frontend Vercel lên mặt trước tủ kính.
   - Thực hiện chuỗi 10 đơn hàng mua thử liên tiếp với các sản phẩm khác nhau trên 3 khay hàng để nghiệm thu toàn diện hệ thống.

