# API Contract — Vending Machine Prototype

**Phiên bản:** 1.0  
**Trạng thái:** Draft — Giai đoạn 1 (chưa triển khai)  
**Nguồn yêu cầu:** `PRODUCT_SPEC.md §11`  
**Cập nhật lần cuối:** 2026-10-18

---

## 1. Quy ước chung

### 1.1. Base URL

| Môi trường | URL |
|---|---|
| Local development | `http://localhost:8080` |
| Production | `https://<backend-domain>` (TBD — xác nhận sau khi chọn hosting) |

### 1.2. Định dạng dữ liệu

- **Content-Type:** `application/json` cho tất cả request/response body.
- **Encoding:** UTF-8.
- **Thời gian:** ISO 8601 với timezone UTC, ví dụ `2026-10-18T10:30:00Z`.
- **Tiền tệ:** Số nguyên VND (không dùng số thực / float). Ví dụ: `15000` = 15.000 đồng.
- **ID công khai:** UUID v4 dạng chuỗi, ví dụ `"550e8400-e29b-41d4-a716-446655440000"`.

### 1.3. Cấu trúc lỗi chuẩn

Tất cả lỗi trả về cùng cấu trúc JSON:

```json
{
  "error": {
    "code": "MACHINE_NOT_FOUND",
    "message": "Máy bán hàng không tồn tại hoặc đang tạm ngừng hoạt động.",
    "timestamp": "2026-10-18T10:30:00Z",
    "requestId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

| Trường | Kiểu | Mô tả |
|---|---|---|
| `error.code` | string | Mã lỗi dạng SCREAMING_SNAKE_CASE để frontend xử lý logic |
| `error.message` | string | Thông báo lỗi thân thiện (có thể hiển thị cho người dùng) |
| `error.timestamp` | string (ISO 8601) | Thời điểm lỗi xảy ra |
| `error.requestId` | string (UUID) | ID request để trace log (nếu có) |

> **Quy tắc bảo mật:** Không bao giờ trả về stack trace, tên table/column database, địa chỉ broker MQTT, thông tin kết nối nội bộ hay bất kỳ thông tin kỹ thuật nhạy cảm nào trong response lỗi cho client.

### 1.4. HTTP Status Code

| Code | Ý nghĩa | Khi nào dùng |
|---|---|---|
| `200 OK` | Thành công | GET, PUT thành công |
| `201 Created` | Tạo mới thành công | POST tạo order |
| `400 Bad Request` | Dữ liệu đầu vào không hợp lệ | Thiếu field, sai kiểu, vi phạm quy tắc nghiệp vụ |
| `401 Unauthorized` | Chưa xác thực | Truy cập endpoint admin mà chưa đăng nhập |
| `403 Forbidden` | Không có quyền | Đã xác thực nhưng không đủ quyền |
| `404 Not Found` | Không tìm thấy tài nguyên | Machine, product, order không tồn tại |
| `409 Conflict` | Xung đột trạng thái | Sản phẩm đã hết hàng, đơn hàng đã xử lý |
| `422 Unprocessable Entity` | Dữ liệu hợp lệ nhưng vi phạm nghiệp vụ | Số lượng không hợp lệ, máy tạm ngừng |
| `429 Too Many Requests` | Vượt rate limit | Gọi API quá nhiều lần trong thời gian ngắn |
| `500 Internal Server Error` | Lỗi server | Lỗi nội bộ không kiểm soát được |

### 1.5. Xác thực Admin

Các endpoint admin (`/api/v1/admin/**`) yêu cầu xác thực.

- **Phương thức:** HTTP Basic Auth hoặc JWT Bearer Token (TBD — quyết định ở Giai đoạn 2 khi cấu hình Spring Security).
- **Header (nếu dùng JWT):** `Authorization: Bearer <token>`

> **TBD:** Cơ chế xác thực cụ thể (Basic Auth vs JWT) sẽ được chốt ở Giai đoạn 2. Cả hai đều được Spring Security hỗ trợ. JWT phù hợp hơn cho SPA frontend.

---

## 2. Endpoint công khai — Máy và sản phẩm

### 2.1. `GET /api/v1/machines/{machineId}`

**Mục đích:** Kiểm tra máy có tồn tại và đang hoạt động không. Frontend gọi khi khách hàng mở trang từ QR code.

**Phân quyền:** Công khai (không cần xác thực).

**Path parameters:**

| Tham số | Kiểu | Mô tả |
|---|---|---|
| `machineId` | string | ID công khai của máy, ví dụ `VM001` |

**Response 200 OK:**

```json
{
  "data": {
    "machineId": "VM001",
    "name": "Máy bán hàng tầng 1",
    "status": "ACTIVE",
    "message": null
  }
}
```

**Response 404 Not Found:**

```json
{
  "error": {
    "code": "MACHINE_NOT_FOUND",
    "message": "Máy bán hàng không tồn tại hoặc đang tạm ngừng hoạt động.",
    "timestamp": "2026-10-18T10:30:00Z",
    "requestId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

**Giá trị `status`:**

| Giá trị | Ý nghĩa |
|---|---|
| `ACTIVE` | Máy đang hoạt động bình thường |
| `INACTIVE` | Máy tạm ngừng — không cho phép đặt hàng |
| `MAINTENANCE` | Máy đang bảo trì |

> **Quy tắc:** `machineId` không phải secret và không cấp quyền quản trị. Backend phải xác nhận machine tồn tại và active ở mọi bước xử lý đơn hàng, không chỉ ở bước này.

---

### 2.2. `GET /api/v1/machines/{machineId}/products`

**Mục đích:** Lấy danh sách sản phẩm đang được bán tại máy.

**Phân quyền:** Công khai.

**Path parameters:**

| Tham số | Kiểu | Mô tả |
|---|---|---|
| `machineId` | string | ID công khai của máy |

**Response 200 OK:**

```json
{
  "data": {
    "machineId": "VM001",
    "products": [
      {
        "productId": "550e8400-e29b-41d4-a716-446655440001",
        "name": "Nước suối Aqua 500ml",
        "description": "Nước uống tinh khiết Aqua chai 500ml",
        "imageUrl": "https://example.com/images/aqua-500ml.jpg",
        "priceVnd": 8000,
        "slotNumber": 1,
        "stockQuantity": 5,
        "available": true
      },
      {
        "productId": "550e8400-e29b-41d4-a716-446655440002",
        "name": "Snack Lay's vị tự nhiên",
        "description": null,
        "imageUrl": null,
        "priceVnd": 15000,
        "slotNumber": 2,
        "stockQuantity": 3,
        "available": true
      },
      {
        "productId": "550e8400-e29b-41d4-a716-446655440003",
        "name": "Kẹo cao su Doublemint",
        "description": null,
        "imageUrl": null,
        "priceVnd": 5000,
        "slotNumber": 3,
        "stockQuantity": 0,
        "available": false
      }
    ]
  }
}
```

**Các trường sản phẩm:**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `productId` | string (UUID) | ✅ | ID duy nhất của sản phẩm |
| `name` | string | ✅ | Tên sản phẩm |
| `description` | string \| null | ❌ | Mô tả ngắn |
| `imageUrl` | string \| null | ❌ | URL ảnh sản phẩm |
| `priceVnd` | integer | ✅ | Giá VND (số nguyên, không phải số thực) |
| `slotNumber` | integer (1-3) | ✅ | Khay vật lý (1, 2 hoặc 3) |
| `stockQuantity` | integer | ✅ | Số lượng tồn kho hiện tại (tồn kho theo database, không phải cảm biến vật lý) |
| `available` | boolean | ✅ | `true` nếu sản phẩm đang active và `stockQuantity > 0` |

> **Quy tắc:** Frontend có thể cache dữ liệu này để hiển thị, nhưng backend phải kiểm tra lại giá và tồn kho khi tạo đơn hàng. Không chứa `slotNumber` trong response là thông tin để frontend dùng trực tiếp — khách hàng không được chỉ định slot.

---

## 3. Endpoint — Đơn hàng

### 3.1. `POST /api/v1/orders`

**Mục đích:** Tạo đơn hàng mới. Backend kiểm tra giá và tồn kho từ database.

**Phân quyền:** Công khai.

**Request body:**

```json
{
  "machineId": "VM001",
  "productId": "550e8400-e29b-41d4-a716-446655440001",
  "quantity": 1
}
```

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `machineId` | string | ✅ | Phải là machine đang active |
| `productId` | string (UUID) | ✅ | Phải là product đang active của machine này |
| `quantity` | integer | ✅ | MVP: chỉ chấp nhận `1` |

> **Quy tắc nghiệp vụ bắt buộc:**
> - Backend **không** tin bất kỳ giá tiền do frontend gửi lên.
> - Backend tự tính `totalAmountVnd` từ `priceVnd` trong database.
> - Backend kiểm tra `stockQuantity > 0` và machine `ACTIVE`.
> - Backend tạo `transferReference` duy nhất để khớp giao dịch ngân hàng.
> - MVP: `quantity` phải là `1`. Giá trị khác trả về `400 Bad Request`.

**Response 201 Created:**

```json
{
  "data": {
    "orderId": "550e8400-e29b-41d4-a716-446655440010",
    "publicOrderCode": "VM001-20261018-AB3F",
    "machineId": "VM001",
    "product": {
      "productId": "550e8400-e29b-41d4-a716-446655440001",
      "name": "Nước suối Aqua 500ml",
      "priceVnd": 8000
    },
    "quantity": 1,
    "totalAmountVnd": 8000,
    "status": "PENDING_PAYMENT",
    "payment": {
      "transferReference": "VM001AB3F",
      "paymentQrData": "...",
      "bankAccountNumber": "123456789",
      "bankAccountName": "NGUYEN VAN A",
      "bankName": "Vietcombank",
      "amountVnd": 8000,
      "paymentExpiresAt": "2026-10-18T10:45:00Z"
    },
    "createdAt": "2026-10-18T10:30:00Z"
  }
}
```

**Các trường response:**

| Trường | Kiểu | Mô tả |
|---|---|---|
| `orderId` | string (UUID) | ID nội bộ của đơn hàng — dùng cho API status |
| `publicOrderCode` | string | Mã ngắn hiển thị cho khách hàng |
| `totalAmountVnd` | integer | Tổng tiền (từ database, không phải từ request) |
| `status` | string | Trạng thái đơn hàng (xem §5) |
| `payment.transferReference` | string | Nội dung chuyển khoản — dùng để khớp giao dịch |
| `payment.paymentQrData` | string | Dữ liệu QR (VietQR format hoặc format của provider) |
| `payment.paymentExpiresAt` | string (ISO 8601) | Thời hạn thanh toán |

**Lỗi có thể trả về:**

| Tình huống | HTTP Code | `error.code` |
|---|---|---|
| Machine không tồn tại hoặc inactive | `404` | `MACHINE_NOT_FOUND` |
| Product không tồn tại hoặc inactive | `404` | `PRODUCT_NOT_FOUND` |
| Product không thuộc machine này | `422` | `PRODUCT_NOT_IN_MACHINE` |
| Hết hàng | `409` | `PRODUCT_OUT_OF_STOCK` |
| `quantity` khác `1` (MVP) | `400` | `INVALID_QUANTITY` |
| Thiếu field bắt buộc | `400` | `VALIDATION_ERROR` |

---

### 3.2. `GET /api/v1/orders/{orderId}`

**Mục đích:** Lấy chi tiết đơn hàng và hướng dẫn thanh toán. Frontend dùng để hiển thị trang chờ thanh toán.

**Phân quyền:** Công khai — nhưng `orderId` là UUID ngẫu nhiên đủ khó đoán. Không dùng sequential integer ID cho endpoint này.

**Path parameters:**

| Tham số | Kiểu | Mô tả |
|---|---|---|
| `orderId` | string (UUID) | ID đơn hàng trả về từ `POST /api/v1/orders` |

**Response 200 OK:**

```json
{
  "data": {
    "orderId": "550e8400-e29b-41d4-a716-446655440010",
    "publicOrderCode": "VM001-20261018-AB3F",
    "machineId": "VM001",
    "product": {
      "productId": "550e8400-e29b-41d4-a716-446655440001",
      "name": "Nước suối Aqua 500ml",
      "priceVnd": 8000
    },
    "quantity": 1,
    "totalAmountVnd": 8000,
    "status": "PENDING_PAYMENT",
    "payment": {
      "transferReference": "VM001AB3F",
      "paymentQrData": "...",
      "bankAccountNumber": "123456789",
      "bankAccountName": "NGUYEN VAN A",
      "bankName": "Vietcombank",
      "amountVnd": 8000,
      "paymentExpiresAt": "2026-10-18T10:45:00Z",
      "paidAt": null
    },
    "dispensing": null,
    "createdAt": "2026-10-18T10:30:00Z",
    "updatedAt": "2026-10-18T10:30:00Z"
  }
}
```

**Khi đã thanh toán và đang nhả hàng:**

```json
{
  "data": {
    "orderId": "550e8400-e29b-41d4-a716-446655440010",
    "status": "DISPENSING",
    "payment": {
      "paidAt": "2026-10-18T10:32:15Z"
    },
    "dispensing": {
      "startedAt": "2026-10-18T10:32:16Z",
      "completedAt": null,
      "result": null
    }
  }
}
```

**Khi hoàn thành:**

```json
{
  "data": {
    "orderId": "550e8400-e29b-41d4-a716-446655440010",
    "status": "COMPLETED",
    "dispensing": {
      "startedAt": "2026-10-18T10:32:16Z",
      "completedAt": "2026-10-18T10:32:19Z",
      "result": "SUCCESS",
      "verificationLevel": "MOTOR_CYCLE_ONLY",
      "message": "Sản phẩm đã được nhả ra. Vui lòng lấy hàng tại ngăn dưới."
    }
  }
}
```

**Trường `dispensing.verificationLevel`:**

| Giá trị | Ý nghĩa |
|---|---|
| `MOTOR_CYCLE_ONLY` | Động cơ đã hoàn thành chu kỳ, không có cảm biến xác nhận hàng rơi |
| `SENSOR_CONFIRMED` | Cảm biến xác nhận hàng đã rơi (nếu phần cứng hỗ trợ) |

> **Bắt buộc:** Không bao giờ trả về `verificationLevel: "SENSOR_CONFIRMED"` nếu phần cứng chưa có cảm biến. Nếu không có cảm biến, luôn dùng `MOTOR_CYCLE_ONLY`.

---

### 3.3. `GET /api/v1/orders/{orderId}/status`

**Mục đích:** Endpoint nhẹ cho frontend polling trạng thái. Chỉ trả về status và các trường cần thiết để cập nhật UI.

**Phân quyền:** Công khai.

**Response 200 OK:**

```json
{
  "data": {
    "orderId": "550e8400-e29b-41d4-a716-446655440010",
    "status": "PAID",
    "updatedAt": "2026-10-18T10:32:15Z"
  }
}
```

> **Polling strategy:** Frontend nên poll endpoint này mỗi 2–3 giây. Phải có giới hạn tổng thời gian polling (ví dụ: dừng sau 20 phút hoặc khi đơn hàng đến trạng thái kết thúc). Không tạo vòng lặp vô tận.

---

## 4. Endpoint — Webhook thanh toán

### 4.1. `POST /api/v1/webhooks/payments/{provider}`

**Mục đích:** Nhận thông báo thanh toán từ nhà cung cấp. Đây là endpoint quan trọng nhất — mọi lệnh nhả hàng đều bắt đầu từ đây.

**Phân quyền:** Phải xác thực bằng cơ chế của nhà cung cấp (signature header, API key, IP whitelist tùy theo provider).

**Path parameters:**

| Tham số | Kiểu | Mô tả |
|---|---|---|
| `provider` | string | Tên provider, ví dụ `sepay`, `payos` |

> **TBD — Quan trọng:** Cấu trúc request body, header xác thực và cơ chế verify signature phụ thuộc hoàn toàn vào nhà cung cấp thanh toán được chọn. Tài liệu này sẽ được cập nhật sau khi chốt provider ở Giai đoạn 5.
>
> **Quy tắc bắt buộc:**
> 1. Xác thực chữ ký/token của provider trước khi xử lý bất kỳ dữ liệu nào.
> 2. Reject ngay (`401` hoặc `400`) nếu xác thực thất bại.
> 3. Kiểm tra `provider_transaction_id` đã tồn tại trong DB chưa (idempotency).
> 4. Trả về HTTP 200 (hoặc code mà provider yêu cầu) kể cả khi đơn hàng không khớp — tránh provider retry liên tục với giao dịch không hợp lệ.
> 5. Giao dịch không khớp phải được lưu vào DB để reconciliation thủ công.

---

## 5. Trạng thái đơn hàng (Order State Machine)

| Trạng thái | Mô tả |
|---|---|
| `PENDING_PAYMENT` | Đơn hàng mới tạo, đang chờ thanh toán |
| `PAID` | Giao dịch ngân hàng đã được xác minh thành công |
| `DISPENSING` | Lệnh nhả hàng đã được ghi nhận và gửi tới ESP32 |
| `COMPLETED` | ESP32 báo cáo hoàn thành chu kỳ động cơ |
| `DISPENSE_FAILED` | ESP32 báo cáo lỗi hoặc lệnh timeout |
| `NEEDS_RECONCILIATION` | Kết quả không chắc chắn, cần quản trị viên xem xét |
| `EXPIRED` | Đơn hàng hết thời gian thanh toán |
| `CANCELLED` | Đơn hàng bị hủy trước khi thanh toán |

**Cho phép chuyển trạng thái:**

| Từ | Sự kiện | Sang |
|---|---|---|
| `PENDING_PAYMENT` | Giao dịch hợp lệ được xác minh | `PAID` |
| `PENDING_PAYMENT` | Hết thời gian thanh toán | `EXPIRED` |
| `PENDING_PAYMENT` | Hủy trước khi thanh toán | `CANCELLED` |
| `PAID` | Lệnh nhả hàng được ghi nhận và phát đi | `DISPENSING` |
| `DISPENSING` | ESP32 báo hoàn thành thành công | `COMPLETED` |
| `DISPENSING` | ESP32 báo lỗi | `DISPENSE_FAILED` |
| `DISPENSING` | Timeout không có phản hồi | `NEEDS_RECONCILIATION` |

> **Bất biến:** Đơn hàng đã `PAID` không bao giờ quay về `PENDING_PAYMENT`. Webhook trùng lặp không thay đổi trạng thái đã confirm.

---

## 6. Endpoint quản trị (Admin)

Tất cả endpoint trong namespace `/api/v1/admin/**` đều yêu cầu xác thực admin.

### 6.1. `POST /api/v1/admin/auth/login`

**Mục đích:** Đăng nhập quản trị.

**Request:**

```json
{
  "username": "admin",
  "password": "your_password"
}
```

**Response 200 OK:**

```json
{
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresAt": "2026-10-19T10:30:00Z"
  }
}
```

> **TBD:** Cơ chế token (JWT stateless vs session-based) sẽ được quyết định ở Giai đoạn 2.

---

### 6.2. `GET /api/v1/admin/products`

**Mục đích:** Danh sách tất cả sản phẩm (bao gồm inactive).

**Response 200 OK:**

```json
{
  "data": {
    "products": [
      {
        "productId": "550e8400-e29b-41d4-a716-446655440001",
        "name": "Nước suối Aqua 500ml",
        "description": "...",
        "imageUrl": "...",
        "priceVnd": 8000,
        "slotNumber": 1,
        "stockQuantity": 5,
        "reservedQuantity": 0,
        "isActive": true,
        "createdAt": "2026-10-01T00:00:00Z",
        "updatedAt": "2026-10-18T09:00:00Z"
      }
    ]
  }
}
```

---

### 6.3. `POST /api/v1/admin/products`

**Mục đích:** Tạo sản phẩm mới.

**Request:**

```json
{
  "name": "Tên sản phẩm",
  "description": "Mô tả tùy chọn",
  "imageUrl": "https://example.com/image.jpg",
  "priceVnd": 10000,
  "slotNumber": 1,
  "stockQuantity": 10
}
```

**Ràng buộc:**

| Trường | Ràng buộc |
|---|---|
| `name` | Bắt buộc, không được rỗng |
| `priceVnd` | Bắt buộc, phải > 0 |
| `slotNumber` | Bắt buộc, phải là 1, 2 hoặc 3; mỗi slot chỉ có 1 sản phẩm active (MVP) |
| `stockQuantity` | Bắt buộc, phải ≥ 0 |

---

### 6.4. `PUT /api/v1/admin/products/{productId}`

**Mục đích:** Cập nhật thông tin sản phẩm.

**Request:** Tương tự POST, tất cả trường là optional (partial update).

---

### 6.5. `PATCH /api/v1/admin/products/{productId}/stock`

**Mục đích:** Điều chỉnh tồn kho (admin điền lại hàng vào máy).

**Request:**

```json
{
  "adjustment": 10,
  "reason": "Đã nạp thêm hàng vào máy"
}
```

| Trường | Kiểu | Mô tả |
|---|---|---|
| `adjustment` | integer | Số lượng thay đổi (dương = thêm, âm = bớt) |
| `reason` | string | Lý do thay đổi (bắt buộc để audit) |

---

### 6.6. `GET /api/v1/admin/orders`

**Mục đích:** Danh sách đơn hàng gần đây.

**Query parameters:**

| Tham số | Mô tả | Mặc định |
|---|---|---|
| `page` | Số trang | `0` |
| `size` | Số bản ghi mỗi trang | `20` |
| `status` | Lọc theo trạng thái | (tất cả) |

---

### 6.7. `GET /api/v1/admin/orders/{orderId}`

**Mục đích:** Chi tiết đơn hàng bao gồm lịch sử thanh toán và lệnh nhả hàng.

---

### 6.8. `GET /api/v1/admin/devices/status`

**Mục đích:** Xem trạng thái kết nối và heartbeat gần nhất của ESP32.

**Response 200 OK:**

```json
{
  "data": {
    "machineId": "VM001",
    "mqttConnected": true,
    "lastHeartbeatAt": "2026-10-18T10:29:55Z",
    "firmwareVersion": "1.0.0",
    "uptimeSeconds": 3600,
    "slots": [
      { "slotNumber": 1, "status": "OK" },
      { "slotNumber": 2, "status": "OK" },
      { "slotNumber": 3, "status": "OK" }
    ]
  }
}
```

> **Lưu ý:** `mqttConnected` ở đây nghĩa là backend đã nhận heartbeat trong khoảng thời gian hợp lệ — không phải là bằng chứng động cơ đang hoạt động bình thường về mặt cơ học.

---

### 6.9. `GET /api/v1/admin/dispense/failed`

**Mục đích:** Danh sách đơn hàng cần reconciliation hoặc xử lý thủ công.

---

### 6.10. `POST /api/v1/admin/auth/logout`

**Mục đích:** Đăng xuất admin (invalidate token nếu dùng JWT).

---

## 7. Health Check

### 7.1. `GET /actuator/health`

**Mục đích:** Kiểm tra sức khỏe tổng thể của backend (Spring Boot Actuator).

**Phân quyền:** Có thể public hoặc restricted tùy cấu hình.

**Response 200 OK:**

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "mqtt": { "status": "UP" }
  }
}
```

---

## 8. Các mục TBD cần xác nhận

| # | Nội dung TBD | Lý do chưa chốt | Giai đoạn cần giải quyết |
|---|---|---|---|
| 1 | Cơ chế xác thực admin (JWT vs Session) | Phụ thuộc vào cấu hình Spring Security | Giai đoạn 2 |
| 2 | Cấu trúc webhook body của payment provider | Phụ thuộc vào provider được chọn | Giai đoạn 5 |
| 3 | Cơ chế xác thực webhook (signature header) | Phụ thuộc vào provider được chọn | Giai đoạn 5 |
| 4 | Format dữ liệu QR thanh toán (`paymentQrData`) | Phụ thuộc vào VietQR / provider format | Giai đoạn 5 |
| 5 | Production backend URL | Phụ thuộc vào hosting chọn | Giai đoạn 8 |
| 6 | Rate limiting policy chi tiết | Cần đánh giá sau khi có backend thật | Giai đoạn 2-4 |

---

## 9. Lưu ý quan trọng về bảo mật

1. **Giá tiền:** Frontend **không** gửi giá — backend tính từ database.
2. **Slot number:** Frontend **không** gửi slot number — backend lấy từ cấu hình sản phẩm.
3. **Trạng thái thanh toán:** Chỉ backend xác nhận dựa trên webhook thật — không có endpoint public nào cho phép set `PAID`.
4. **UUID làm order ID:** Tránh sequential integer ID để ngăn enumeration attack.
5. **CORS:** Chỉ cho phép origin của frontend — cấu hình ở backend, không tin vào bất kỳ origin nào trong development.
6. **Admin endpoints:** Phải kiểm tra authorization trên backend — frontend ẩn trang admin là không đủ.
