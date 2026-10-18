# API Contract — Vending Machine Prototype

**Phiên bản:** 1.1<br>
**Trạng thái:** Draft — Cập nhật sau rà soát Giai đoạn 1 (chưa triển khai)<br>
**Nguồn yêu cầu:** `PRODUCT_SPEC.md §11`
**Thay đổi so với v1.0:** JWT cookie design (F6), chuẩn hóa order response (F1), auth endpoints tách riêng (F2), message trong status (F3), MACHINE_INACTIVE (F4), admin order detail schema (F5), slotNumber clarification (F8)
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
| `error.message` | string | Thông báo lỗi thân thiện, an toàn để hiển thị cho người dùng |
| `error.timestamp` | string (ISO 8601) | Thời điểm lỗi xảy ra |
| `error.requestId` | string (UUID) | ID request để trace log (nếu có) |

> **Quy tắc bảo mật:** Không bao giờ trả về stack trace, tên table/column database, địa chỉ broker MQTT, thông tin kết nối nội bộ, JWT payload hay bất kỳ thông tin kỹ thuật nhạy cảm nào trong response lỗi cho client.

### 1.4. HTTP Status Code

| Code | Ý nghĩa | Khi nào dùng |
|---|---|---|
| `200 OK` | Thành công | GET, PUT, PATCH thành công |
| `201 Created` | Tạo mới thành công | POST tạo order |
| `204 No Content` | Thành công, không có body | Logout |
| `400 Bad Request` | Dữ liệu đầu vào không hợp lệ | Thiếu field, sai kiểu, vi phạm quy tắc nghiệp vụ |
| `401 Unauthorized` | Chưa xác thực hoặc token hết hạn | Truy cập endpoint admin mà chưa đăng nhập |
| `403 Forbidden` | Không có quyền | Đã xác thực nhưng không đủ quyền |
| `404 Not Found` | Không tìm thấy tài nguyên | Machine, product, order không tồn tại |
| `409 Conflict` | Xung đột trạng thái | Sản phẩm đã hết hàng |
| `422 Unprocessable Entity` | Dữ liệu hợp lệ nhưng vi phạm nghiệp vụ | Machine inactive, số lượng không hợp lệ |
| `429 Too Many Requests` | Vượt rate limit | Gọi API quá nhiều lần |
| `500 Internal Server Error` | Lỗi server | Lỗi nội bộ không kiểm soát được |

### 1.5. Xác thực Admin — JWT trong HttpOnly Cookie

Admin authentication sử dụng JWT được lưu trong cookie HttpOnly. Token không bao giờ xuất hiện trong response body, localStorage hay bất kỳ nơi nào JavaScript có thể đọc được.

#### 1.5.1. Thuộc tính cookie

| Thuộc tính | Giá trị Production | Giá trị Local Dev | Lý do |
|---|---|---|---|
| `HttpOnly` | `true` | `true` | JavaScript không đọc được, ngăn XSS đánh cắp token |
| `Secure` | `true` | `false` | Chỉ gửi qua HTTPS (bỏ trong local HTTP) |
| `SameSite` | `None` | `Lax` | `None` bắt buộc cho cross-origin SPA + API trên domain khác nhau |
| `Path` | `/api/v1` | `/api/v1` | Chỉ gửi cho API, không gửi kèm request tĩnh |
| `Max-Age` | `28800` (8 giờ) | `28800` | Không dùng refresh token trong MVP |
| Tên cookie | `admin_access_token` | `admin_access_token` | |

> **Tại sao `SameSite=None` trong production?** Frontend (Vercel) và Backend (hosting riêng) sẽ ở hai domain khác nhau. Trình duyệt chỉ gửi cookie trong cross-origin fetch request khi `SameSite=None; Secure`. Nếu dùng `SameSite=Strict` hoặc `SameSite=Lax`, cookie sẽ không được gửi và admin không đăng nhập được từ SPA.

> **`SameSite=None` và CSRF:** Vì `SameSite=None` không ngăn được cross-site request, cần thêm cơ chế CSRF bổ sung (xem §1.5.3).

#### 1.5.2. JWT Payload

```json
{
  "sub": "admin",
  "tokenVersion": 1,
  "iat": 1729224000,
  "exp": 1729252800
}
```

| Trường | Mô tả |
|---|---|
| `sub` | Username của admin |
| `tokenVersion` | Phiên bản token, dùng để vô hiệu hóa khi logout — phải khớp với giá trị trong database |
| `iat` | Thời điểm phát hành (issued at) |
| `exp` | Thời điểm hết hạn (8 giờ sau `iat`) |

> **Không hoàn toàn stateless:** Backend phải kiểm tra `tokenVersion` trong database khi xác thực token. Nếu admin logout, `tokenVersion` tăng lên trong DB; token cũ tuy chưa hết hạn cũng bị từ chối. Đây là đánh đổi cần thiết giữa tính stateless và khả năng thu hồi token.

#### 1.5.3. Bảo vệ CSRF và cơ chế Defense-in-Depth

Vì môi trường production yêu cầu `SameSite=None` để SPA (trên domain riêng như Vercel) gửi được cookie sang Backend API, hệ thống phải triển khai cơ chế phòng vệ CSRF đa tầng (Defense-in-Depth):

> **Nguyên tắc cốt lõi:** Tuyệt đối không coi `X-Requested-With` hoặc CORS riêng lẻ là biện pháp bảo vệ đầy đủ:
> - Nếu chỉ dùng CORS: CORS là chính sách do trình duyệt thi hành và chỉ kích hoạt đối với các request không phải "Simple Request". Các thẻ HTML truyền thống (`<form>`, `<iframe>`) có thể gửi POST request mà không hề kích hoạt CORS preflight.
> - Nếu chỉ dùng `X-Requested-With`: Header này có thể bị giả mạo bởi các non-browser client hoặc nếu xuất hiện lỗ hổng XSS trên các subdomain liên quan.
> - Vì vậy, an toàn CSRF bắt buộc phải dựa trên sự kết hợp đồng thời của 4 chốt chặn: Cookie flags (`SameSite=None; Secure; HttpOnly`), Custom Header (`X-Requested-With`), Strict CORS Whitelist, và Server-side Origin/Referer Validation.

1. **Bắt buộc Custom Header `X-Requested-With`:**
   Mọi mutating request (`POST`, `PUT`, `PATCH`, `DELETE`) đến admin endpoints bắt buộc phải kèm header:
   ```
   X-Requested-With: XMLHttpRequest
   ```
   - **Chống Form-based CSRF:** Các cuộc tấn công CSRF truyền thống sử dụng thẻ HTML (`<form method="POST">`, `<iframe src="...">`) chỉ có thể gửi "simple requests" và **không thể** tự ý chèn custom HTTP header như `X-Requested-With`. Khi thiếu header này, Backend lập tức từ chối với `403 Forbidden` (`error.code: "CSRF_HEADER_MISSING"`).
   - **Kích hoạt CORS Preflight:** Khi một trang web thứ ba dùng JavaScript (`fetch` hoặc `axios`) cố tình đính kèm header `X-Requested-With`, trình duyệt bắt buộc phải gửi một HTTP preflight request (`OPTIONS`) trước khi gửi request thật.

2. **CORS Whitelist nghiêm ngặt:**
   Trình duyệt tự động đính kèm header `Origin: https://attacker.com` vào preflight `OPTIONS` request. Backend chỉ cho phép origin nằm trong danh sách whitelist tin cậy (Frontend domain). Khi origin không khớp, preflight thất bại và trình duyệt chặn không cho gửi request mutating thật sự sang backend.

3. **Thuật toán kiểm tra `Origin` và `Referer` trực tiếp trên Server (Server-side Validation):**
   Để độc lập bảo vệ hệ thống trước mọi sai sót cấu hình trình duyệt, Security Filter của Backend xử lý theo thuật toán nghiêm ngặt sau cho tất cả các mutating request (`POST`, `PUT`, `PATCH`, `DELETE`):
   - **Bước 1 — Kiểm tra `Origin`:**
     - Nếu header `Origin` có mặt: So sánh giá trị chính xác với whitelist (ví dụ: `https://<frontend-domain>` hoặc `http://localhost:5173`). Nếu khớp ➔ cho phép đi tiếp; nếu không khớp ➔ lập tức từ chối với `403 Forbidden` (`error.code: "CSRF_ORIGIN_INVALID"`).
     - Nếu `Origin` có giá trị `null` (thường do iframe sandbox, chuyển hướng cross-origin, hoặc `data:` URL): Lập tức từ chối với `403 Forbidden` (`error.code: "CSRF_ORIGIN_INVALID"`).
   - **Bước 2 — Fallback kiểm tra `Referer`:**
     - Nếu `Origin` vắng mặt (xảy ra ở một số trình duyệt khi gọi same-origin hoặc chế độ ẩn danh): Kiểm tra header `Referer`.
     - Phân tích cú pháp URL của `Referer`, trích xuất `scheme://host[:port]`. So sánh origin trích xuất được với whitelist. Nếu khớp ➔ cho phép đi tiếp; nếu không khớp hoặc URL `Referer` bị malformed ➔ từ chối với `403 Forbidden` (`error.code: "CSRF_REFERER_INVALID"`).
   - **Bước 3 — Xử lý khi thiếu cả hai header:**
     - Nếu cả `Origin` và `Referer` đều vắng mặt trên mutating request gửi từ trình duyệt: Lập tức từ chối với `403 Forbidden` (`error.code: "CSRF_HEADER_MISSING"`).

> **Không có ngoại lệ bỏ qua (No Bypass):** Mọi request làm thay đổi dữ liệu (state-changing request) có sử dụng xác thực cookie nếu vắng mặt hoặc không vượt qua kiểm tra `Origin`/`Referer` đều bị từ chối với HTTP 403. Hệ thống tuyệt đối không âm thầm bỏ qua (silent bypass) việc kiểm tra nguồn gốc dưới bất kỳ lý do nào. Các request chỉ đọc (`GET`, `HEAD`) không làm thay đổi trạng thái nhưng vẫn được bảo vệ bởi lớp xác thực JWT cookie.

> **Lưu ý cho Frontend:** Mọi lệnh gọi fetch/axios đến admin API phải luôn cấu hình `credentials: 'include'` và đính kèm header `X-Requested-With: XMLHttpRequest`.

#### 1.5.4. Cấu hình CORS

Backend phải cấu hình CORS tường minh theo origin cụ thể của Frontend:

```http
Access-Control-Allow-Origin: https://<frontend-domain>
Access-Control-Allow-Credentials: true
Access-Control-Allow-Headers: Content-Type, X-Requested-With
Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
Access-Control-Max-Age: 3600
```

> **Quy tắc bất biến:**
> - Tuyệt đối **không** dùng `Access-Control-Allow-Origin: *` vì tiêu chuẩn bảo mật trình duyệt cấm sử dụng wildcard `*` khi `Access-Control-Allow-Credentials: true`.
> - Trong môi trường local development, chỉ cho phép origin của Vite dev server (ví dụ: `http://localhost:5173`).

#### 1.5.5. Xử lý token hết hạn

- Backend trả `401 Unauthorized` với `error.code: "TOKEN_EXPIRED"`.
- Frontend nhận `401` → xóa trạng thái admin cục bộ → redirect về trang login.
- Không có cơ chế tự gia hạn token trong MVP; admin phải đăng nhập lại.

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

**Giá trị `status`:**

| Giá trị | Ý nghĩa |
|---|---|
| `ACTIVE` | Máy đang hoạt động bình thường |
| `INACTIVE` | Máy tạm ngừng — không cho phép đặt hàng |
| `MAINTENANCE` | Máy đang bảo trì |

**Response 404 Not Found:**

```json
{
  "error": {
    "code": "MACHINE_NOT_FOUND",
    "message": "Máy bán hàng không tồn tại.",
    "timestamp": "2026-10-18T10:30:00Z",
    "requestId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

> **Quy tắc:** `machineId` không phải secret và không cấp quyền quản trị. Backend phải xác nhận machine tồn tại **và** active ở mọi bước xử lý đơn hàng, không chỉ ở bước này.

---

### 2.2. `GET /api/v1/machines/{machineId}/products`

**Mục đích:** Lấy danh sách sản phẩm đang được bán tại máy.

**Phân quyền:** Công khai.

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
| `slotNumber` | integer (1-3) | ✅ | Số khay vật lý, dùng để hiển thị UI. Khách hàng không được gửi giá trị này lên để điều khiển slot. |
| `stockQuantity` | integer | ✅ | Tồn kho theo database (không phải cảm biến vật lý — xem PRODUCT_SPEC.md §9.4) |
| `available` | boolean | ✅ | `true` nếu sản phẩm đang active và `stockQuantity > 0` |

> **Quy tắc quan trọng về `slotNumber`:** Trường này được trả về để Frontend có thể hiển thị thông tin "Khay số X". Tuy nhiên, khi khách hàng tạo đơn hàng, họ chỉ gửi `productId` — Backend tự xác định slot từ cấu hình sản phẩm đáng tin cậy trong database. Backend không bao giờ tin `slotNumber` do client gửi lên.

> **Cache:** Frontend có thể cache catalog để hiển thị, nhưng backend phải luôn kiểm tra lại giá và tồn kho khi tạo đơn hàng.

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
| `machineId` | string | ✅ | Phải là machine đang `ACTIVE` |
| `productId` | string (UUID) | ✅ | Phải là product đang active và thuộc machine này |
| `quantity` | integer | ✅ | MVP: chỉ chấp nhận `1` |

> **Quy tắc nghiệp vụ bắt buộc:**
> - Backend **không** tin bất kỳ giá tiền do frontend gửi lên.
> - Backend tự tính `totalAmountVnd` từ `priceVnd` trong database.
> - Backend kiểm tra `stockQuantity > 0` và machine `ACTIVE`.
> - Backend tạo `transferReference` duy nhất để khớp giao dịch ngân hàng.
> - MVP: `quantity` phải là `1`.

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

**Lỗi có thể trả về:**

| Tình huống | HTTP Code | `error.code` | Thông báo mẫu cho khách |
|---|---|---|---|
| Machine không tồn tại | `404` | `MACHINE_NOT_FOUND` | "Máy bán hàng không tồn tại." |
| Machine tồn tại nhưng inactive/maintenance | `422` | `MACHINE_INACTIVE` | "Máy bán hàng hiện đang tạm ngừng hoạt động." |
| Product không tồn tại hoặc inactive | `404` | `PRODUCT_NOT_FOUND` | "Sản phẩm không tồn tại." |
| Product không thuộc machine này | `422` | `PRODUCT_NOT_IN_MACHINE` | "Sản phẩm không có tại máy này." |
| Hết hàng | `409` | `PRODUCT_OUT_OF_STOCK` | "Sản phẩm này hiện đã hết hàng." |
| `quantity` khác `1` (MVP) | `400` | `INVALID_QUANTITY` | "Chỉ có thể đặt 1 sản phẩm mỗi lần." |
| Thiếu field bắt buộc | `400` | `VALIDATION_ERROR` | "Dữ liệu không hợp lệ. Vui lòng thử lại." |

---

### 3.2. `GET /api/v1/orders/{orderId}`

**Mục đích:** Lấy chi tiết đơn hàng. Frontend dùng để hiển thị trang thanh toán và theo dõi trạng thái.

**Phân quyền:** Công khai — `orderId` là UUID ngẫu nhiên đủ khó đoán. Không dùng sequential integer ID.

**Response luôn có cấu trúc đầy đủ bất kể trạng thái.** Các trường chưa có giá trị trả về `null`.

**Response 200 OK — cấu trúc đầy đủ:**

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
    "dispensing": {
      "startedAt": null,
      "completedAt": null,
      "result": null,
      "verificationLevel": null,
      "message": null
    },
    "createdAt": "2026-10-18T10:30:00Z",
    "updatedAt": "2026-10-18T10:30:00Z"
  }
}
```

**Ví dụ khi trạng thái là `DISPENSING`:**

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
    "status": "DISPENSING",
    "payment": {
      "transferReference": "VM001AB3F",
      "paymentQrData": "...",
      "bankAccountNumber": "123456789",
      "bankAccountName": "NGUYEN VAN A",
      "bankName": "Vietcombank",
      "amountVnd": 8000,
      "paymentExpiresAt": "2026-10-18T10:45:00Z",
      "paidAt": "2026-10-18T10:32:15Z"
    },
    "dispensing": {
      "startedAt": "2026-10-18T10:32:16Z",
      "completedAt": null,
      "result": null,
      "verificationLevel": null,
      "message": "Đang chuẩn bị sản phẩm cho bạn..."
    },
    "createdAt": "2026-10-18T10:30:00Z",
    "updatedAt": "2026-10-18T10:32:16Z"
  }
}
```

**Ví dụ khi trạng thái là `COMPLETED`:**

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

**Ví dụ khi trạng thái là `DISPENSE_FAILED` hoặc `NEEDS_RECONCILIATION`:**

```json
{
  "data": {
    "orderId": "550e8400-e29b-41d4-a716-446655440010",
    "status": "DISPENSE_FAILED",
    "dispensing": {
      "startedAt": "2026-10-18T10:32:16Z",
      "completedAt": "2026-10-18T10:33:17Z",
      "result": "FAILURE",
      "verificationLevel": "MOTOR_CYCLE_ONLY",
      "message": "Máy có thể gặp sự cố khi nhả hàng. Vui lòng liên hệ nhân viên hỗ trợ và không thanh toán thêm lần nữa."
    }
  }
}
```

**Mô tả trường `dispensing`:**

| Trường | Kiểu | Mô tả |
|---|---|---|
| `startedAt` | string \| null | Thời điểm bắt đầu xử lý nhả hàng |
| `completedAt` | string \| null | Thời điểm kết thúc (thành công hoặc thất bại) |
| `result` | `"SUCCESS"` \| `"FAILURE"` \| null | Kết quả được báo cáo bởi ESP32 |
| `verificationLevel` | `"MOTOR_CYCLE_ONLY"` \| `"SENSOR_CONFIRMED"` \| null | Mức độ xác nhận vật lý |
| `message` | string \| null | Thông báo an toàn cho khách hàng, phù hợp với trạng thái hiện tại |

> **Quy tắc `verificationLevel`:** Không bao giờ trả về `"SENSOR_CONFIRMED"` nếu phần cứng chưa có cảm biến. Mặc định là `"MOTOR_CYCLE_ONLY"` — nghĩa là động cơ hoàn thành chu kỳ nhưng không có bằng chứng độc lập rằng hàng đã rơi xuống.

> **Quy tắc `message`:** Chỉ chứa thông báo thân thiện cho khách hàng theo SPEC §6.6 và §6.7. Không chứa stack trace, lỗi kỹ thuật, địa chỉ MQTT, thông tin nội bộ hay bất kỳ secret nào.

**Response 404:** Order không tồn tại.

---

### 3.3. `GET /api/v1/orders/{orderId}/status`

**Mục đích:** Endpoint nhẹ cho frontend polling trạng thái.

**Phân quyền:** Công khai.

**Response 200 OK:**

```json
{
  "data": {
    "orderId": "550e8400-e29b-41d4-a716-446655440010",
    "status": "PAID",
    "updatedAt": "2026-10-18T10:32:15Z",
    "message": null
  }
}
```

**Khi trạng thái cần thông báo cho khách (`DISPENSE_FAILED`, `NEEDS_RECONCILIATION`, `EXPIRED`):**

```json
{
  "data": {
    "orderId": "550e8400-e29b-41d4-a716-446655440010",
    "status": "NEEDS_RECONCILIATION",
    "updatedAt": "2026-10-18T10:33:17Z",
    "message": "Đơn hàng của bạn đang được kiểm tra. Vui lòng không thanh toán thêm lần nữa và liên hệ nhân viên nếu cần hỗ trợ."
  }
}
```

| Trường | Kiểu | Mô tả |
|---|---|---|
| `orderId` | string (UUID) | ID đơn hàng |
| `status` | string | Trạng thái hiện tại (xem §6) |
| `updatedAt` | string (ISO 8601) | Thời điểm trạng thái thay đổi gần nhất |
| `message` | string \| null | Thông báo an toàn cho khách hàng khi cần, null nếu không |

> **Polling:** Frontend poll endpoint này mỗi 2–3 giây. Dừng polling khi trạng thái là terminal: `COMPLETED`, `DISPENSE_FAILED`, `NEEDS_RECONCILIATION`, `EXPIRED`, `CANCELLED`. Không tạo vòng lặp vô tận; giới hạn tối đa bằng `paymentExpiresAt` + buffer.

---

## 4. Endpoint — Webhook thanh toán

### 4.1. `POST /api/v1/webhooks/payments/{provider}`

**Mục đích:** Nhận thông báo thanh toán từ nhà cung cấp. Đây là endpoint quan trọng nhất — mọi lệnh nhả hàng đều bắt đầu từ đây.

**Phân quyền:** Phải xác thực bằng cơ chế của nhà cung cấp (signature header, token, IP whitelist tùy provider).

**Path parameters:**

| Tham số | Kiểu | Mô tả |
|---|---|---|
| `provider` | string | Tên provider, ví dụ `sepay`, `payos` |

> **TBD:** Cấu trúc request body, header xác thực và cơ chế verify signature phụ thuộc hoàn toàn vào nhà cung cấp thanh toán được chọn. Sẽ cập nhật ở Giai đoạn 5.

**Quy tắc xử lý bắt buộc:**

1. Xác thực chữ ký/token của provider trước khi xử lý bất kỳ dữ liệu nào.
2. Reject ngay (`401` hoặc `400`) nếu xác thực thất bại.
3. Kiểm tra `provider_transaction_id` đã tồn tại trong DB chưa (idempotency).
4. Kiểm tra số tiền, tài khoản nhận, mã tham chiếu chuyển khoản và trạng thái giao dịch.
5. Trả về HTTP 200 (hoặc code mà provider yêu cầu) kể cả khi giao dịch không khớp order — tránh provider retry liên tục.
6. Giao dịch không khớp phải được lưu vào DB để reconciliation thủ công. Không silently discard.
7. Webhook trùng lặp không tạo thêm dispensing command.
8. Không phát lệnh nhả hàng cho giao dịch sai số tiền, giao dịch đến sau khi order đã expired, hay giao dịch cho order đã huỷ.

**Interface `PaymentProvider` (khái niệm — không phụ thuộc provider cụ thể):**

```
createPaymentInstructions(order)   → payment QR data, transfer reference
verifyWebhookSignature(request)    → boolean
parsePaymentEvent(request)         → PaymentEvent
matchTransactionToOrder(event)     → Order | null
recordVerifiedTransaction(event)   → void
```

---

## 5. Endpoint — Xác thực (Auth)

Namespace: `/api/v1/auth/`

Các endpoint này dùng cho admin authentication. Không yêu cầu cookie hợp lệ để gọi (vì dùng để lấy cookie), nhưng có rate limit để chống brute force.

### 5.1. `POST /api/v1/auth/login`

**Mục đích:** Đăng nhập admin. Server set cookie `admin_access_token`.

**Phân quyền:** Công khai (rate limited).

**Request body:**

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
    "username": "admin",
    "expiresAt": "2026-10-19T10:30:00Z"
  }
}
```

Response headers sẽ bao gồm:
```
Set-Cookie: admin_access_token=<jwt>; HttpOnly; Secure; SameSite=None; Path=/api/v1; Max-Age=28800
```

> **Lưu ý:** Token **không** xuất hiện trong response body. Frontend chỉ biết thời gian hết hạn để quản lý UI. Trình duyệt tự động lưu cookie và gửi kèm các request tiếp theo.

**Response 401 Unauthorized:**

```json
{
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Tên đăng nhập hoặc mật khẩu không đúng.",
    "timestamp": "...",
    "requestId": "..."
  }
}
```

> **Bảo mật:** Trả về thông báo chung chung — không phân biệt "username không tồn tại" hay "sai password" để tránh username enumeration.

---

### 5.2. `POST /api/v1/auth/logout`

**Mục đích:** Đăng xuất admin. Server xóa cookie và vô hiệu hóa token qua `tokenVersion`.

**Phân quyền:** Ưu tiên gọi khi có cookie hợp lệ, nhưng xử lý graceful khi cookie hết hạn hoặc không có.

**Request:** Không cần body. Cookie `admin_access_token` được đọc tự động bởi trình duyệt.

**Hành vi:**

1. Nếu cookie hợp lệ và token còn thời hạn: tăng `tokenVersion` trong DB, xóa cookie.
2. Nếu cookie hết hạn hoặc không hợp lệ: vẫn xóa cookie phía client (trả về `204`).
3. Không trả về lỗi `401` khi logout — mục đích luôn là xóa trạng thái đã đăng nhập.

**Response 204 No Content** (không có body):

```
Set-Cookie: admin_access_token=; HttpOnly; Secure; SameSite=None; Path=/api/v1; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT
```

> **Quy tắc xóa cookie:** Phải gửi `Set-Cookie` với `Max-Age=0` và `Expires` trong quá khứ. Phải khớp đúng `Path`, `Domain` và `SameSite` với cookie gốc, nếu không trình duyệt sẽ không xóa được cookie.

---

## 6. Trạng thái đơn hàng (Order State Machine)

| Trạng thái | Mô tả |
|---|---|
| `PENDING_PAYMENT` | Đơn hàng mới tạo, đang chờ thanh toán |
| `PAID` | Giao dịch ngân hàng đã được xác minh thành công |
| `DISPENSING` | Lệnh nhả hàng đã được ghi nhận bền vững và phát tới ESP32 |
| `COMPLETED` | ESP32 báo cáo hoàn thành chu kỳ động cơ |
| `DISPENSE_FAILED` | ESP32 báo cáo lỗi xác định |
| `NEEDS_RECONCILIATION` | Kết quả không chắc chắn — cần quản trị viên xem xét thủ công |
| `EXPIRED` | Đơn hàng hết thời gian thanh toán |
| `CANCELLED` | Đơn hàng bị hủy trước khi thanh toán |

**Cho phép chuyển trạng thái:**

| Từ | Sự kiện | Sang |
|---|---|---|
| `PENDING_PAYMENT` | Giao dịch hợp lệ được xác minh qua webhook | `PAID` |
| `PENDING_PAYMENT` | Hết thời gian thanh toán | `EXPIRED` |
| `PENDING_PAYMENT` | Hủy trước khi thanh toán | `CANCELLED` |
| `PAID` | Lệnh nhả hàng được ghi nhận bền vững vào DB và đã phát đi | `DISPENSING` |
| `DISPENSING` | ESP32 báo hoàn thành thành công | `COMPLETED` |
| `DISPENSING` | ESP32 báo lỗi xác định (motor timeout, stall, hardware error) | `DISPENSE_FAILED` |
| `DISPENSING` | ESP32 báo kết quả bất định (UNCERTAIN) hoặc timeout không nhận được phản hồi | `NEEDS_RECONCILIATION` |

**Bất biến:**

- Đơn hàng đã `PAID` không bao giờ quay về `PENDING_PAYMENT`.
- Webhook trùng lặp không thay đổi trạng thái đơn hàng đã `PAID` trở về trước.
- Backend phải commit trạng thái `PAID` và bản ghi lệnh nhả hàng vào DB trước khi publish MQTT — không giữ database transaction mở trong khi chờ mạng.

---

## 7. Endpoint quản trị (Admin)

Tất cả endpoint trong namespace `/api/v1/admin/**` yêu cầu cookie `admin_access_token` hợp lệ.

Tất cả request mutating (POST, PUT, PATCH, DELETE) phải kèm header `X-Requested-With: XMLHttpRequest` (chống CSRF).

Frontend phải dùng `credentials: 'include'` cho tất cả fetch đến admin endpoints.

### 7.1. `GET /api/v1/admin/products`

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

### 7.2. `POST /api/v1/admin/products`

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

### 7.3. `PUT /api/v1/admin/products/{productId}`

**Mục đích:** Cập nhật thông tin sản phẩm (đầy đủ).

---

### 7.4. `PATCH /api/v1/admin/products/{productId}/stock`

**Mục đích:** Điều chỉnh tồn kho (admin nạp hàng vào máy).

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
| `reason` | string | Lý do thay đổi — bắt buộc để audit log |

---

### 7.5. `GET /api/v1/admin/orders`

**Mục đích:** Danh sách đơn hàng gần đây.

**Query parameters:**

| Tham số | Mô tả | Mặc định |
|---|---|---|
| `page` | Số trang (0-indexed) | `0` |
| `size` | Số bản ghi mỗi trang | `20` |
| `status` | Lọc theo trạng thái | (tất cả) |

---

### 7.6. `GET /api/v1/admin/orders/{orderId}`

**Mục đích:** Chi tiết đầy đủ của đơn hàng dành cho admin, bao gồm lịch sử giao dịch thanh toán và lệnh nhả hàng.

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
      "slotNumber": 1,
      "priceVnd": 8000
    },
    "quantity": 1,
    "unitPriceVnd": 8000,
    "totalAmountVnd": 8000,
    "transferReference": "VM001AB3F",
    "status": "COMPLETED",
    "createdAt": "2026-10-18T10:30:00Z",
    "updatedAt": "2026-10-18T10:32:19Z",
    "paidAt": "2026-10-18T10:32:15Z",
    "dispensingStartedAt": "2026-10-18T10:32:16Z",
    "completedAt": "2026-10-18T10:32:19Z",
    "paymentTransaction": {
      "transactionId": "550e8400-e29b-41d4-a716-446655440050",
      "provider": "sepay",
      "providerTransactionId": "TXN123456789",
      "amountVnd": 8000,
      "verificationStatus": "VERIFIED",
      "receivedAt": "2026-10-18T10:32:14Z"
    },
    "dispenseCommand": {
      "commandId": "550e8400-e29b-41d4-a716-446655440020",
      "slotNumber": 1,
      "status": "COMPLETED",
      "attemptCount": 1,
      "publishedAt": "2026-10-18T10:32:16Z",
      "acknowledgedAt": "2026-10-18T10:32:16.500Z",
      "finishedAt": "2026-10-18T10:32:19Z",
      "failureCode": null,
      "failureMessage": null
    }
  }
}
```

> **Lưu ý bảo mật:** Không bao giờ trả về raw webhook payload đầy đủ hoặc thông tin ngân hàng nhạy cảm của khách hàng cho admin response. Chỉ trả về thông tin đã được sanitize và cần thiết cho vận hành.

---

### 7.7. `GET /api/v1/admin/devices/status`

**Mục đích:** Xem trạng thái kết nối và heartbeat gần nhất của ESP32.

**Response 200 OK:**

```json
{
  "data": {
    "machineId": "VM001",
    "isOnline": true,
    "lastHeartbeatAt": "2026-10-18T10:29:55Z",
    "secondsSinceLastHeartbeat": 30,
    "firmwareVersion": "1.0.0",
    "uptimeSeconds": 3600,
    "wifiRssi": -65,
    "slots": [
      { "slotNumber": 1, "lastDispenseResult": "SUCCESS", "lastDispenseAt": "2026-10-18T09:00:00Z" },
      { "slotNumber": 2, "lastDispenseResult": null, "lastDispenseAt": null },
      { "slotNumber": 3, "lastDispenseResult": null, "lastDispenseAt": null }
    ]
  }
}
```

> **Lưu ý:** `isOnline` dựa trên heartbeat trong khoảng thời gian hợp lệ — không phải bằng chứng động cơ hoạt động bình thường về mặt cơ học.

---

### 7.8. `GET /api/v1/admin/dispense/failed`

**Mục đích:** Danh sách đơn hàng ở trạng thái `DISPENSE_FAILED` hoặc `NEEDS_RECONCILIATION` cần xử lý thủ công.

---

## 8. Health Check

### 8.1. `GET /actuator/health`

**Mục đích:** Kiểm tra sức khỏe tổng thể của backend (Spring Boot Actuator).

**Phân quyền:** Public hoặc restricted — cấu hình theo môi trường.

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

## 9. Các mục TBD cần xác nhận

| # | Nội dung TBD | Lý do chưa chốt | Giai đoạn cần giải quyết |
|---|---|---|---|
| 1 | Cấu trúc webhook body của payment provider | Phụ thuộc vào provider được chọn | Giai đoạn 5 |
| 2 | Cơ chế xác thực webhook (signature header) | Phụ thuộc vào provider được chọn | Giai đoạn 5 |
| 3 | Format dữ liệu QR thanh toán (`paymentQrData`) | Phụ thuộc vào VietQR / provider format | Giai đoạn 5 |
| 4 | Production backend URL và domain | Phụ thuộc vào hosting chọn | Giai đoạn 8 |
| 5 | Rate limiting policy chi tiết (login, order creation, status polling) | Cần đánh giá sau khi có backend thật | Giai đoạn 2-4 |
| 6 | `Domain` attribute trong cookie nếu cần subdomain sharing | Phụ thuộc vào cấu hình DNS production | Giai đoạn 8 |

---

## 10. Lưu ý quan trọng về bảo mật

1. **Giá tiền:** Frontend **không** gửi giá — backend tính từ database.
2. **Slot number:** Frontend **không** gửi slot number để ra lệnh — backend lấy từ cấu hình sản phẩm.
3. **Trạng thái thanh toán:** Chỉ backend xác nhận dựa trên webhook thật — không có endpoint public nào cho phép set `PAID`.
4. **UUID làm order ID:** Tránh sequential integer ID để ngăn enumeration attack.
5. **CORS:** Chỉ cho phép origin của frontend — không dùng `*`. Bắt buộc `allowCredentials: true` cho cookie.
6. **Admin endpoints:** Phải kiểm tra cookie và `tokenVersion` trên backend — frontend ẩn trang admin là không đủ.
7. **CSRF:** Mọi request mutating đến admin phải kèm `X-Requested-With: XMLHttpRequest`.
8. **Token:** JWT không xuất hiện trong response body, không lưu localStorage, không log trong server.
9. **Logout cookie:** Phải khớp đầy đủ `Path`, `SameSite`, `Secure` khi xóa cookie.
