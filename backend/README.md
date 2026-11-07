# Backend — Vending Machine Core API & Services

Hệ thống máy chủ xử lý nghiệp vụ trung tâm của dự án **Vending Machine Prototype**, xây dựng trên nền tảng **Java 21**, **Spring Boot 3.3.5**, **PostgreSQL 16** và giao tiếp IoT qua **MQTT over TLS**.

Backend đóng vai trò là "bộ não" duy nhất có thẩm quyền xác thực thanh toán, quản lý tồn kho, cấp phát lệnh xuất hàng tới vi điều khiển ESP32 và phục vụ cổng thông tin quản trị, đối soát.


## 1. Công nghệ & Thư viện sử dụng

- **Ngôn ngữ & Runtime:** Java 21 (Eclipse Temurin LTS).
- **Framework chính:** Spring Boot 3.3.5.
  - `spring-boot-starter-web`: REST API và Webhook handling.
  - `spring-boot-starter-data-jpa` / Hibernate 6.5: Quản lý truy xuất cơ sở dữ liệu.
  - `spring-boot-starter-validation`: Xác thực dữ liệu đầu vào (DTO validation).
  - `spring-boot-starter-security`: Bảo vệ API quản trị, xác thực JWT và lọc CSRF.
  - `spring-boot-starter-actuator`: Health check (`/actuator/health`).
- **Database & Migration:** PostgreSQL 16+, Flyway Community Edition (Migration scripts V1 đến V4).
- **Giao thức IoT:** Eclipse Paho MQTTv3 Client (`org.eclipse.paho.client.mqttv3`) kết nối EMQX Cloud Serverless qua TLS (Port 8883).
- **Bảo mật:** HMAC-SHA256 JWT trong HttpOnly Cookie, bảo vệ CSRF qua custom header `X-Requested-With: XMLHttpRequest`, mã hóa mật khẩu admin bằng BCrypt.
- **Kiểm thử:** JUnit 5, Mockito, Spring Boot Test, H2 In-Memory Database (mô phỏng PostgreSQL dialect cho test suite độc lập).

---

## 2. Cấu trúc thư mục mã nguồn

```text
backend/
├── pom.xml                               # Cấu hình Maven dependencies & build plugins
├── Dockerfile                            # Multi-stage Dockerfile (Eclipse Temurin JRE 21)
├── .dockerignore
├── .env.render.example                   # Mẫu biến môi trường cho môi trường Cloud Render
├── mvnw / mvnw.cmd                       # Maven Wrapper
└── src/
    ├── main/
    │   ├── java/com/vendingmachine/
    │   │   ├── VendingMachineApplication.java
    │   │   ├── admin/                    # Phân hệ quản trị và đối soát
    │   │   │   ├── controller/           # AdminAuthController, AdminReconciliationController
    │   │   │   ├── dto/                  # Login, OrderDetail, DecisionRequest DTOs
    │   │   │   ├── entity/               # AdminUser, ReconciliationAuditLog
    │   │   │   ├── repository/           # AdminUserRepository, ReconciliationAuditLogRepository
    │   │   │   ├── security/             # AdminJwtService, AdminSecurityFilter, AdminBootstrapRunner
    │   │   │   └── service/              # AdminAuthService, AdminReconciliationService
    │   │   ├── common/                   # Cấu hình dùng chung, exception handling
    │   │   │   ├── config/               # CorsConfig, JacksonConfig, MvcConfig
    │   │   │   ├── exception/            # BusinessException, GlobalExceptionHandler
    │   │   │   └── response/             # ApiResponse, ErrorResponse wrapper
    │   │   ├── device/                   # Quản lý nhật ký sự kiện thiết bị
    │   │   │   ├── entity/               # DeviceEvent
    │   │   │   └── repository/           # DeviceEventRepository
    │   │   ├── dispense/                 # Điều khiển xuất hàng và giao tiếp MQTT
    │   │   │   ├── adapter/              # DeviceAdapter interface, SimulatedDeviceAdapter
    │   │   │   │   └── mqtt/             # MqttDeviceAdapter, MqttProperties
    │   │   │   ├── dto/                  # DispenseCommandMessage, DeviceEventMessage, EventAckMessage, HeartbeatMessage
    │   │   │   ├── entity/               # DispenseCommand
    │   │   │   ├── repository/           # DispenseCommandRepository
    │   │   │   └── service/              # DispenseService, DispenseServiceImpl
    │   │   ├── machine/                  # Quản lý máy bán hàng
    │   │   │   ├── controller/           # MachineController
    │   │   │   ├── entity/               # Machine, MachineStatus
    │   │   │   └── service/              # MachineService
    │   │   ├── order/                    # Quản lý đơn hàng và vòng đời khóa kho
    │   │   │   ├── controller/           # OrderController
    │   │   │   ├── dto/                  # CreateOrderRequestDto, OrderResponseDto
    │   │   │   ├── entity/               # Order, OrderStatus
    │   │   │   ├── repository/           # OrderRepository
    │   │   │   └── service/              # OrderService, OrderExpirationService
    │   │   ├── payment/                  # Thanh toán VietQR & SePay Webhook
    │   │   │   ├── controller/           # PaymentWebhookController
    │   │   │   ├── dto/                  # SepayWebhookDto, VietQrResponseDto
    │   │   │   ├── entity/               # PaymentTransaction
    │   │   │   └── service/              # PaymentService, VietQrService
    │   │   └── product/                  # Danh mục sản phẩm & quản lý slot
    │   │       ├── controller/           # ProductController
    │   │       ├── entity/               # Product
    │   │       └── service/              # ProductService
    │   └── resources/
    │       ├── application.yml           # Cấu hình mặc định của Spring Boot
    │       └── db/migration/             # Flyway database migrations (V1 -> V4)
    │           ├── V1__initial_schema.sql
    │           ├── V2__seed_data.sql
    │           ├── V3__reconciliation_audit_logs.sql
    │           └── V4__reservation_lifecycle_and_admin_security.sql
    └── test/                             # 180 Unit & Integration Tests tự động
```

---

## 3. Các tính năng nghiệp vụ cốt lõi

1. **Khóa kho tạm giữ an toàn (Concurrency-Safe Stock Hold):**
   - Khi khách hàng tạo đơn hàng, hệ thống tăng `reserved_quantity` để giữ chỗ sản phẩm. Tồn kho khả dụng = `stock_quantity - reserved_quantity`.
   - Ngăn chặn triệt để tình trạng hai khách hàng cùng quét mua 1 món đồ cuối cùng (race condition).
   - Tự động hủy đơn và hoàn trả tạm giữ sau 15 phút nếu khách không chuyển khoản.

2. **Xử lý Webhook SePay & VietQR:**
   - Tạo chuỗi tham chiếu chuyển khoản duy nhất `transfer_reference` (vd: `VM0015UGY`).
   - Webhook SePay được xử lý hoàn toàn tự động, xác thực số tiền chuyển khoản, kiểm tra chống trùng giao dịch (`provider_transaction_id`).
   - Hỗ trợ chế độ Demo (`PAYMENT_DEMO_MODE=true`) phục vụ thử nghiệm an toàn không dùng tiền thật.

3. **Giao tiếp IoT MQTT hai pha (Two-Phase Dispensing):**
   - Phát lệnh `DispenseCommandMessage` qua topic `vending/{machineId}/commands` (QoS 1).
   - Lắng nghe sự kiện xác nhận `COMMAND_ACCEPTED` và kết quả `DISPENSE_RESULT` (`SUCCESS`, `FAILURE`, `UNCERTAIN`).
   - Chỉ khi kết quả là `SUCCESS`, đơn hàng mới chuyển sang `COMPLETED` và trừ kho thực tế `stock_quantity`.
   - Gửi bản tin `EventAckMessage` (`vending/{machineId}/events/ack`) để ESP32 xóa outbox lưu trữ trong Flash NVS.

4. **Cổng thông tin quản trị & Đối soát (Admin Reconciliation):**
   - Đăng nhập bảo mật JWT trong HttpOnly Cookie (`app_admin_token`).
   - Chống CSRF nghiêm ngặt: yêu cầu bắt buộc header `X-Requested-With: XMLHttpRequest` cho các thao tác ghi dữ liệu.
   - Quản lý danh sách đơn hàng cần can thiệp (`NEEDS_RECONCILIATION`), xem chi tiết timeline và nhật ký sự kiện phần cứng.
   - Ra quyết định đối soát: `REFUND`, `DISPENSE_RETRY`, `MARK_COMPLETED` kèm lý do giải trình lưu vào audit log.

---

## 4. Cấu hình biến môi trường

Các biến môi trường được chuẩn hóa tại `backend/.env.render.example`:

| Tên biến | Kiểu giá trị mẫu | Mô tả |
|---|---|---|
| `PORT` | `8080` | Cổng HTTP lắng nghe của server |
| `SPRING_PROFILES_ACTIVE` | `local` hoặc `prod` | Profile Spring Boot |
| `DATABASE_URL` | `jdbc:postgresql://.../neondb?sslmode=require` | Chuỗi kết nối JDBC PostgreSQL |
| `DATABASE_USERNAME` | `<user>` | Tên đăng nhập database |
| `DATABASE_PASSWORD` | `<password>` | Mật khẩu kết nối database |
| `APP_ADMIN_JWT_SECRET` | Chuỗi ngẫu nhiên $\ge 32$ ký tự | Khóa bí mật ký JWT HMAC-SHA256 |
| `FRONTEND_ORIGIN` | `https://vending-app.vercel.app` | Cấu hình nguồn cho phép gọi CORS |
| `PAYMENT_DEMO_MODE` | `true` hoặc `false` | Bật/tắt chế độ demo thanh toán |
| `MQTT_ENABLED` | `true` hoặc `false` | Bật adapter MQTT kết nối broker thực |
| `MQTT_BROKER_URL` | `ssl://cluster.emqxsl.com:8883` | URL broker EMQX qua TLS |
| `MQTT_USERNAME` | `backend-vending-001` | Username xác thực MQTT |
| `MQTT_PASSWORD` | `<password>` | Mật khẩu xác thực MQTT |

---

## 5. Hướng dẫn chạy và kiểm thử

### 5.1. Yêu cầu môi trường
- Java 21 JDK (Eclipse Temurin khuyến nghị).
- Maven 3.9+ (hoặc dùng trực tiếp `./mvnw`).
- Cơ sở dữ liệu PostgreSQL (hoặc dùng H2 in-memory khi chạy test).

### 5.2. Chạy toàn bộ Test Suite
```bash
cd backend
./mvnw clean test
```
*Kết quả:* Toàn bộ **180 tests** tự động chạy độc lập trên profile `test` (H2 In-Memory), đạt tỷ lệ thành công 100% (0 failures, 0 errors, 0 skipped).

### 5.3. Khởi chạy Backend Local
```bash
cd backend
./mvnw spring-boot:run
```
Endpoint kiểm tra sức khỏe ứng dụng:
`http://localhost:8080/actuator/health` ➔ `{"status":"UP"}`

### 5.4. Đóng gói Container Docker
```bash
cd backend
docker build -t vending-machine-backend:latest .
```

---

## 6. Tài liệu tham khảo

- Hợp đồng REST API: [docs/API_CONTRACT.md](../docs/API_CONTRACT.md)
- Hợp đồng giao tiếp MQTT: [docs/MQTT_CONTRACT.md](../docs/MQTT_CONTRACT.md)
- Hướng dẫn triển khai Cloud: [docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md)
- Báo cáo bàn giao phần mềm: [docs/SOFTWARE_HANDOVER_REPORT.md](../docs/SOFTWARE_HANDOVER_REPORT.md)
