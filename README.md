# Vending Machine Prototype

> Hệ thống máy bán hàng tự động tích hợp thanh toán thực tế và điều khiển phần cứng ESP32 qua MQTT.

## Mục tiêu dự án

Xây dựng hệ thống máy bán hàng tự động cho phép khách hàng:

1. Quét mã QR cố định trên máy để truy cập trang đặt hàng.
2. Xem danh sách sản phẩm, chọn và tạo đơn hàng.
3. Thanh toán bằng chuyển khoản ngân hàng qua mã QR động.
4. Nhận sản phẩm vật lý sau khi backend xác minh giao dịch và điều khiển đúng khay nhả hàng.

**Yêu cầu cốt lõi:** Thanh toán phải được xác minh tự động thông qua webhook ngân hàng thực tế. Không chấp nhận ảnh chụp màn hình, nút bấm "đã thanh toán" hay bất kỳ giá trị nào do frontend gửi lên làm bằng chứng thanh toán.

## Kiến trúc hệ thống

```
KHÁCH HÀNG (Điện thoại)
     │ Quét QR cố định
     ▼
FRONTEND (React + TypeScript + Vite + Tailwind CSS)
     │ HTTPS REST API
     ▼
BACKEND (Java 21 + Spring Boot 3.x + Maven)
     │
     ├── PostgreSQL (Flyway) ── Lưu đơn hàng, sản phẩm, giao dịch, lệnh nhả hàng
     │
     ├── Payment Provider ── Nhận webhook xác minh chuyển khoản thực tế
     │
     └── MQTT Broker (TLS) ── Truyền lệnh nhả hàng và nhận kết quả
               │
               ▼
         ESP32 CONTROLLER
               │
        ┌──────┼──────┐
        ▼      ▼      ▼
     SLOT 1  SLOT 2  SLOT 3
     Motor   Motor   Motor
```

## Cấu trúc thư mục

```
Vending Machine Prototype/
├── PRODUCT_SPEC.md          # Đặc tả sản phẩm đầy đủ (nguồn yêu cầu chính thức)
├── IMPLEMENTATION_PLAN.md   # Lộ trình triển khai theo giai đoạn
├── README.md                # File này
├── .gitignore               # Các file bị loại trừ khỏi Git
├── .env.example             # Mẫu biến môi trường (không chứa giá trị thật)
├── docker-compose.yml       # Cấu hình Docker cho môi trường local (tạo ở Giai đoạn 2)
│
├── docs/
│   ├── API_CONTRACT.md      # Hợp đồng REST API giữa Frontend và Backend
│   ├── MQTT_CONTRACT.md     # Hợp đồng MQTT giữa Backend và ESP32
│   ├── TESTING.md           # Hướng dẫn kiểm thử (tạo ở giai đoạn sau)
│   └── DEPLOYMENT.md        # Hướng dẫn triển khai (tạo ở giai đoạn sau)
│
├── frontend/                # Giao diện React (Giai đoạn 3)
│   └── README.md
│
├── backend/                 # Backend Spring Boot Java 21 (Giai đoạn 2)
│   └── README.md
│
└── firmware/                # Firmware Arduino ESP32 (Giai đoạn 6)
    └── README.md
```

## Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Frontend | React 18, TypeScript, Vite, Tailwind CSS |
| Backend | Java 21, Spring Boot 3.x, Maven |
| Database | PostgreSQL, Flyway |
| IoT Controller | ESP32, Arduino framework |
| Giao tiếp IoT | MQTT over TLS |
| Thanh toán | Webhook chuyển khoản ngân hàng thực tế (TBD: SePay / PayOS) |
| Containerization | Docker, Docker Compose |
| Version control | Git, GitHub |

## Phát triển cục bộ

> **Lưu ý:** Hướng dẫn chi tiết sẽ được cập nhật khi từng thành phần hoàn thành.

### Yêu cầu môi trường

- Java 21 JDK (khuyến nghị Eclipse Temurin 21)
- Maven 3.9+
- Node.js 20+ (hoặc phiên bản LTS mới nhất)
- npm 10+
- Docker Desktop (cho PostgreSQL và phát triển local)
- Git 2.x

### Cấu hình môi trường

```bash
# Sao chép file cấu hình mẫu
cp .env.example .env

# Chỉnh sửa .env và điền giá trị thật
# KHÔNG commit file .env vào Git
```

### Chạy Backend (Giai đoạn 2 trở đi)

```bash
cd backend
# Hướng dẫn chi tiết sẽ có trong backend/README.md
```

### Chạy Frontend (Giai đoạn 3 trở đi)

```bash
cd frontend
# Hướng dẫn chi tiết sẽ có trong frontend/README.md
```

### Nạp firmware ESP32 (Giai đoạn 6 trở đi)

```bash
cd firmware
# Hướng dẫn chi tiết sẽ có trong firmware/README.md
```

## Tài liệu tham khảo

- **Yêu cầu sản phẩm đầy đủ:** [PRODUCT_SPEC.md](./PRODUCT_SPEC.md)
- **Kế hoạch triển khai:** [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)
- **Hợp đồng REST API:** [docs/API_CONTRACT.md](./docs/API_CONTRACT.md)
- **Hợp đồng MQTT:** [docs/MQTT_CONTRACT.md](./docs/MQTT_CONTRACT.md)

## Bảo mật

- Không bao giờ commit file `.env` hoặc thông tin đăng nhập thật.
- Dùng `.env.example` làm mẫu với giá trị placeholder.
- Thông tin MQTT credentials, payment API key và database password phải được quản lý qua biến môi trường hoặc secret manager của platform triển khai.
- Admin password phải được đặt lại ngay sau lần deploy đầu tiên.

## Giới hạn đã biết (sẽ cập nhật theo tiến trình)

- Máy bán hàng MVP chỉ hỗ trợ 1 sản phẩm / 1 đơn hàng, 3 slot độc lập.
- Chưa có cảm biến xác nhận hàng rơi — hệ thống chỉ báo cáo hoàn thành chu kỳ động cơ (`MOTOR_CYCLE_ONLY`).
- Nhà cung cấp thanh toán chưa được chốt (TBD — xem [PRODUCT_SPEC.md §22.2](./PRODUCT_SPEC.md)).

## Trạng thái giai đoạn

| Giai đoạn | Nội dung | Trạng thái |
|---|---|---|
| 0 | Kiểm tra dự án và xác nhận tính khả thi | ✅ Hoàn thành |
| 1 | Khởi tạo cấu trúc và hợp đồng giao tiếp | ✅ Hoàn thành |
| 2 | Khởi tạo backend Java và database | ⏳ Chưa bắt đầu |
| 3 | Khởi tạo frontend | ⏳ Chưa bắt đầu |
| 4 | Hoàn thiện nghiệp vụ đơn hàng | ⏳ Chưa bắt đầu |
| 5 | Tích hợp thanh toán và xác minh webhook | ⏳ Chưa bắt đầu |
| 6 | Tích hợp MQTT và firmware ESP32 | ⏳ Chưa bắt đầu |
| 7 | Tích hợp toàn bộ hệ thống | ⏳ Chưa bắt đầu |
| 8 | Triển khai và cấu hình môi trường thực tế | ⏳ Chưa bắt đầu |
| 9 | Nghiệm thu và tài liệu bàn giao | ⏳ Chưa bắt đầu |
