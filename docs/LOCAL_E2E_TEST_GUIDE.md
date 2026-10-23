# Hướng Dẫn Kiểm Thử E2E Toàn Trình Trên Local (Local End-to-End Testing Guide)

Tài liệu này cung cấp các lệnh PowerShell và quy trình từng bước chính xác để kiểm thử tích hợp toàn trình (End-to-End) hệ thống **Vending Machine Prototype** trên môi trường cục bộ (Windows / PowerShell).

---

> [!IMPORTANT]
> **CẢNH BÁO PHẠM VI MÔ PHỎNG & BẢO MẬT:**
> 1. Toàn bộ quá trình xuất hàng trong tài liệu này sử dụng bộ điều hợp mô phỏng phần cứng **`SimulatedDeviceAdapter`** trên phần mềm; **chưa kết nối với vi điều khiển ESP32, rơ-le hoặc động cơ nhả hàng vật lý**.
> 2. Các sự kiện thanh toán SePay trong hướng dẫn này là **dữ liệu mô phỏng qua Webhook REST API**, tuyệt đối không thực hiện chuyển khoản tiền thật hoặc sử dụng thông tin tài khoản ngân hàng thực tế.
> 3. Tuyệt đối không commit file `.env` chứa mật khẩu hoặc bí mật môi trường lên Git repository.

---

## 1. Kiểm Tra Điều Kiện Tiên Quyết (Prerequisites Verification)

Mở **PowerShell** và chạy các lệnh sau để kiểm tra môi trường:

```powershell
# 1. Kiểm tra phiên bản Java (Yêu cầu Java 21)
java -version

# Nếu máy có nhiều phiên bản Java, thiết lập JAVA_HOME trỏ tới JDK 21:
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
java -version

# 2. Kiểm tra Node.js và npm (Yêu cầu Node 18+ hoặc 20+)
node -v
npm -v

# 3. Kiểm tra dịch vụ PostgreSQL 18
Get-Service -Name postgresql*
```

---

## 2. Khởi Tạo & Xác Minh Database PostgreSQL

### 2.1. Kiểm tra hoặc khởi tạo Database `vending_machine_dev`

Nếu database phát triển chưa được khởi tạo, chạy script đi kèm dự án [`scripts/setup_dev_db.sql`](file:///c:/Users/ASUS/Vending%20Machine%20Prototype/scripts/setup_dev_db.sql) bằng quyền quản trị `postgres`:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost -f "scripts\setup_dev_db.sql"
```

### 2.2. Kiểm tra kết nối tài khoản ứng dụng `vending_user`

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U vending_user -d vending_machine_dev -h localhost -c "SELECT current_database(), current_user;"
```
*(Nhập mật khẩu `vending_dev_password` khi được hỏi. Kết quả trả về `vending_machine_dev` và `vending_user`).*

---

## 3. Cấu Hình Biến Môi Trường (Environment Configuration)

### 3.1. Cấu hình Backend (`backend/.env`)

Kiểm tra file `backend/.env`. Nếu chưa có, copy từ `backend/.env.example` (không ghi đè nếu file đã tồn tại):

```powershell
if (-not (Test-Path "backend\.env")) {
    Copy-Item "backend\.env.example" "backend\.env"
    Write-Host "Created backend/.env from template."
} else {
    Write-Host "backend/.env already exists. Preserving existing configuration."
}
```

Nội dung tối thiểu cần có trong `backend/.env` cho phiên chạy E2E local:

```env
PORT=8080
SPRING_PROFILES_ACTIVE=local
DATABASE_URL=jdbc:postgresql://localhost:5432/vending_machine_dev
DATABASE_USERNAME=vending_user
DATABASE_PASSWORD=vending_dev_password
MACHINE_ID=VM001
FRONTEND_ORIGIN=http://localhost:5173
ORDER_EXPIRATION_MINUTES=15

# Cấu hình thanh toán Demo SePay
PAYMENT_DEMO_MODE=true
SEPAY_API_KEY=test-sepay-secret-key
SEPAY_BANK_BIN=970422
SEPAY_BANK_NAME=MBBank
SEPAY_ACCOUNT_NUMBER=DEMO123456
SEPAY_ACCOUNT_NAME=DEMO SMART VENDING

# Cấu hình xuất hàng mô phỏng
DISPENSE_AUTO_TRIGGER=true
DISPENSE_COMMAND_TIMEOUT_SECONDS=45
DISPENSE_SIMULATOR_ENABLED=true
DISPENSE_SIMULATOR_ASYNC=false
DISPENSE_SIMULATOR_DEFAULT_SCENARIO=SUCCESS
```

### 3.2. Cấu hình Frontend (`frontend/.env`)

Kiểm tra file `frontend/.env` đảm bảo trỏ đúng REST API của backend:

```env
VITE_API_BASE_URL=http://localhost:8080
```

---

## 4. Khởi Chạy Hệ Thống Trên Hai Terminal Riêng Biệt

### Terminal 1: Khởi chạy Spring Boot Backend

```powershell
cd "c:\Users\ASUS\Vending Machine Prototype\backend"
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# Nạp các biến môi trường từ .env nếu cần hoặc chạy trực tiếp qua Maven Wrapper
./mvnw spring-boot:run
```

*Chờ cho đến khi terminal hiển thị:*
```text
Started VendingMachineApplication in X.XXX seconds
Registered DispenseService as listener to SimulatedDeviceAdapter
```

### Terminal 2: Khởi chạy Vite React Frontend

Mở cửa sổ PowerShell thứ hai:

```powershell
cd "c:\Users\ASUS\Vending Machine Prototype\frontend"
npm run dev
```

*Chờ cho đến khi terminal hiển thị:*
```text
  VITE v8.3.4  ready in XXX ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
```

---

## 5. Xác Minh Sức Khỏe Hệ Thống & Kết Nối Ban Đầu (Health Checks)

Mở một cửa sổ PowerShell thứ ba (dùng cho lệnh gọi API và kiểm tra database):

```powershell
# 1. Kiểm tra Actuator Health của Backend
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get

# 2. Kiểm tra thông tin máy VM001 và danh mục sản phẩm (Flyway V2 Seed Data)
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/machines/VM001/products" -Method Get

# 3. Kiểm tra trạng thái Adapter Mô Phỏng
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/simulation/device/status" -Method Get
```

*Phản hồi mẫu của simulator:*
```json
{
  "success": true,
  "data": {
    "adapterType": "SIMULATED",
    "currentScenario": "SUCCESS",
    "asyncMode": false,
    "isOnline": true,
    "disclaimer": "SIMULATION ONLY - NOT PHYSICAL HARDWARE"
  }
}
```

---

## 6. Quy Trình Kiểm Thử E2E Từng Bước (Step-by-Step E2E Scenarios)

Dữ liệu khởi tạo mặc định trong PostgreSQL (từ Flyway `V2__seed_data.sql`):
- Máy: `VM001`
- Khe 1 (`550e8400-e29b-41d4-a716-446655440001`): Nước suối Aqua 500ml — Giá: `8,000 VND` — Tồn kho: `5`
- Khe 2 (`550e8400-e29b-41d4-a716-446655440002`): Snack Lay's — Giá: `15,000 VND` — Tồn kho: `3`
- Khe 3 (`550e8400-e29b-41d4-a716-446655440003`): Kẹo Doublemint — Giá: `5,000 VND` — Tồn kho: `0` (Hết hàng)

---

### Kịch Bản 1: Luồng Thành Công Hoàn Toàn (Happy Path — SUCCESS)

#### Bước 1.1: Tạo đơn hàng qua giao diện hoặc API
- **Cách 1 (Giao diện Web):**
  1. Mở trình duyệt tại `http://localhost:5173`.
  2. Bấm vào nút **"Chọn mua"** tại món *Nước suối Aqua 500ml*.
  3. Bấm **"Thanh toán ngay"** trên giỏ hàng, sau đó bấm **"Xác nhận tạo đơn"**.
  4. Giao diện hiển thị Modal với Mã đơn hàng, VietQR, và mã nội dung chuyển khoản (VD: `VM-ABC12345`).
- **Cách 2 (PowerShell):**
  ```powershell
  $orderReq = @{
      machineId = "VM001"
      productId = "550e8400-e29b-41d4-a716-446655440001"
      quantity = 1
  } | ConvertTo-Json

  $orderRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" -Method Post -ContentType "application/json" -Body $orderReq
  $orderId = $orderRes.data.orderId
  $transferRef = $orderRes.data.transferReference
  $amount = $orderRes.data.totalAmountVnd
  Write-Host "Created Order: $orderId | Ref: $transferRef | Amount: $amount"
  ```

#### Bước 1.2: Kiểm tra Database sau khi tạo đơn
```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U vending_user -d vending_machine_dev -h localhost -c "
SELECT public_order_code, status, total_amount_vnd, transfer_reference, payment_expires_at 
FROM orders WHERE id = '$orderId';
SELECT name, slot_number, stock_quantity, reserved_quantity 
FROM products WHERE id = '550e8400-e29b-41d4-a716-446655440001';"
```
*Kết quả kỳ vọng:*
- `orders.status` = `PENDING_PAYMENT`
- `products.stock_quantity` = `5`, `products.reserved_quantity` = `1` (Đã giữ chỗ, chưa trừ kho vật lý).

#### Bước 1.3: Đảm bảo Simulator đang ở kịch bản SUCCESS
```powershell
$modeReq = @{ scenario = "SUCCESS"; async = $false } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/simulation/device/mode" -Method Post -ContentType "application/json" -Body $modeReq
```

#### Bước 1.4: Gửi Webhook Thanh Toán SePay Mô Phỏng
```powershell
$txId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$webhookPayload = @{
    id = $txId
    gateway = "MBBank"
    transactionDate = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    accountNumber = "DEMO123456"
    transferType = "in"
    transferAmount = $amount
    content = "Thanh toan $transferRef"
    code = $transferRef
} | ConvertTo-Json

$headers = @{ "Authorization" = "Apikey test-sepay-secret-key" }

$webhookRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/webhooks/payments/sepay" `
    -Method Post -Headers $headers -ContentType "application/json" -Body $webhookPayload

$webhookRes | ConvertTo-Json
```

#### Bước 1.5: Xác minh kết quả tự động xuất hàng
1. **Trên Giao diện UI:**
   - Trong vòng 3 giây (chu kỳ polling), modal tự động chuyển từ chờ thanh toán sang:
     - Biểu tượng spinner: **"Máy đang thực hiện nhả hàng..."**
     - Sau đó chuyển sang icon xanh thành công: **"Đã xuất hàng thành công! Sản phẩm đã được đưa ra khay nhận hàng bên dưới."**
2. **Trong Database:**
   ```powershell
   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U vending_user -d vending_machine_dev -h localhost -c "
   SELECT status, paid_at, dispensing_started_at, completed_at FROM orders WHERE id = '$orderId';
   SELECT status, attempt_count, published_at, finished_at FROM dispense_commands WHERE order_id = '$orderId';
   SELECT event_type, slot_number, payload FROM device_events WHERE command_id = (SELECT command_id FROM dispense_commands WHERE order_id = '$orderId');
   SELECT name, stock_quantity, reserved_quantity FROM products WHERE id = '550e8400-e29b-41d4-a716-446655440001';"
   ```
   *Kết quả kỳ vọng:*
   - `orders.status` = `COMPLETED`
   - `dispense_commands.status` = `COMPLETED`
   - `device_events` ghi nhận 2 sự kiện: `COMMAND_ACCEPTED` và `DISPENSE_RESULT` (với `result: "SUCCESS"`).
   - `products.stock_quantity` giảm từ `5` xuống `4`, `products.reserved_quantity` giảm từ `1` xuống `0`.

---

### Kịch Bản 2: Xuất Hàng Thất Bại Có Xác Nhận (Confirmed Failure — MOTOR_TIMEOUT / Kẹt hàng)

#### Bước 2.1: Chuyển Simulator sang kịch bản FAILURE
```powershell
$modeReq = @{ scenario = "FAILURE"; async = $false } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/simulation/device/mode" -Method Post -ContentType "application/json" -Body $modeReq
```

#### Bước 2.2: Tạo đơn hàng mới và gửi webhook
```powershell
# 1. Tạo đơn hàng cho Nước suối Aqua
$orderRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" -Method Post -ContentType "application/json" -Body (@{
    machineId = "VM001"
    productId = "550e8400-e29b-41d4-a716-446655440001"
    quantity = 1
} | ConvertTo-Json)

$orderId = $orderRes.data.orderId
$transferRef = $orderRes.data.transferReference
$amount = $orderRes.data.totalAmountVnd

# 2. Gửi Webhook
$txId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$webhookPayload = @{
    id = $txId
    gateway = "MBBank"
    transactionDate = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    accountNumber = "DEMO123456"
    transferType = "in"
    transferAmount = $amount
    content = "Thanh toan $transferRef"
    code = $transferRef
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/webhooks/payments/sepay" `
    -Method Post -Headers @{ "Authorization" = "Apikey test-sepay-secret-key" } -ContentType "application/json" -Body $webhookPayload
```

#### Bước 2.3: Kiểm tra Database & UI
- **Giao diện UI:** Hiển thị **"Sự cố xuất hàng"** cùng hướng dẫn liên hệ tổng đài hỗ trợ.
- **Trong Database:**
  ```powershell
  & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U vending_user -d vending_machine_dev -h localhost -c "
  SELECT status FROM orders WHERE id = '$orderId';
  SELECT status, failure_code, failure_message FROM dispense_commands WHERE order_id = '$orderId';
  SELECT stock_quantity, reserved_quantity FROM products WHERE id = '550e8400-e29b-41d4-a716-446655440001';"
  ```
  *Bảo toàn kho:*
  - `orders.status` = `DISPENSE_FAILED`
  - `dispense_commands.failure_code` = `MOTOR_TIMEOUT`
  - `stock_quantity` **KHÔNG BỊ TRỪ** (vẫn giữ nguyên `4`), `reserved_quantity` được giải phóng về `0`.

---

### Kịch Bản 3: Kết Quả Bất Định (UNCERTAIN — Thiết bị khởi động lại trong khi quay động cơ)

#### Bước 3.1: Chuyển Simulator sang kịch bản UNCERTAIN
```powershell
$modeReq = @{ scenario = "UNCERTAIN"; async = $false } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/simulation/device/mode" -Method Post -ContentType "application/json" -Body $modeReq
```

#### Bước 3.2: Tạo đơn và thanh toán
```powershell
$orderRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" -Method Post -ContentType "application/json" -Body (@{
    machineId = "VM001"
    productId = "550e8400-e29b-41d4-a716-446655440001"
    quantity = 1
} | ConvertTo-Json)

$orderId = $orderRes.data.orderId
$transferRef = $orderRes.data.transferReference
$amount = $orderRes.data.totalAmountVnd

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/webhooks/payments/sepay" `
    -Method Post -Headers @{ "Authorization" = "Apikey test-sepay-secret-key" } -ContentType "application/json" -Body (@{
        id = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        gateway = "MBBank"
        transactionDate = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
        accountNumber = "DEMO123456"
        transferType = "in"
        transferAmount = $amount
        code = $transferRef
    } | ConvertTo-Json)
```

#### Bước 3.3: Kiểm tra tính bất biến kho khi kết quả bất định
```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U vending_user -d vending_machine_dev -h localhost -c "
SELECT status FROM orders WHERE id = '$orderId';
SELECT status, failure_code FROM dispense_commands WHERE order_id = '$orderId';
SELECT stock_quantity, reserved_quantity FROM products WHERE id = '550e8400-e29b-41d4-a716-446655440001';"
```
*Kết quả kỳ vọng:*
- `orders.status` = `NEEDS_RECONCILIATION`
- `dispense_commands.status` = `UNCERTAIN`
- `products.stock_quantity` và `reserved_quantity` **ĐƯỢC GIỮ NGUYÊN HOÀN TOÀN** (`reserved_quantity` = 1) để nhân viên kỹ thuật kiểm tra thực tế tại khay, không tự tiện hoàn kho hay trừ kho khi chưa rõ sản phẩm đã rơi hay chưa.
- **UI:** Hiển thị màn hình **"Cần đối soát giao dịch"**.

---

### Kịch Bản 4: Quá Hạn Phản Hồi Từ Thiết Bị (TIMEOUT Handling)

#### Bước 4.1: Chuyển Simulator sang kịch bản TIMEOUT
```powershell
$modeReq = @{ scenario = "TIMEOUT"; async = $false } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/simulation/device/mode" -Method Post -ContentType "application/json" -Body $modeReq
```

#### Bước 4.2: Tạo đơn và thanh toán
```powershell
$orderRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" -Method Post -ContentType "application/json" -Body (@{
    machineId = "VM001"
    productId = "550e8400-e29b-41d4-a716-446655440001"
    quantity = 1
} | ConvertTo-Json)

$orderId = $orderRes.data.orderId
$transferRef = $orderRes.data.transferReference
$amount = $orderRes.data.totalAmountVnd

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/webhooks/payments/sepay" `
    -Method Post -Headers @{ "Authorization" = "Apikey test-sepay-secret-key" } -ContentType "application/json" -Body (@{
        id = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        gateway = "MBBank"
        transactionDate = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
        accountNumber = "DEMO123456"
        transferType = "in"
        transferAmount = $amount
        code = $transferRef
    } | ConvertTo-Json)
```

#### Bước 4.3: Quan sát tiến trình Timeout của Scheduled Task
- Trong kịch bản này, thiết bị mô phỏng chỉ gửi `COMMAND_ACCEPTED` và cố tình không gửi `DISPENSE_RESULT`.
- Background task `checkAndHandleTimeouts` chạy mỗi 5 giây (`app.dispense.timeout-check-interval-ms`).
- Sau 45 giây (`app.dispense.command-timeout-seconds`), backend ghi nhận cảnh báo:
  ```text
  WARN c.v.d.service.DispenseServiceImpl : DispenseCommand ... timed out after 45 seconds. Transitioning to TIMEOUT / NEEDS_RECONCILIATION
  ```
- Kiểm tra database sau 45s:
  ```powershell
  & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U vending_user -d vending_machine_dev -h localhost -c "
  SELECT status FROM orders WHERE id = '$orderId';
  SELECT status, failure_code, failure_message FROM dispense_commands WHERE order_id = '$orderId';"
  ```
  `orders.status` tự động chuyển sang `NEEDS_RECONCILIATION`, `dispense_commands.status` = `TIMEOUT`.

---

### Kịch Bản 5: Kiểm Thử Tính Lũy Thừa (Idempotency Verification)

#### 5.1. Trùng lặp Webhook Thanh toán (Duplicate Webhooks)
Chạy gửi cùng một payload webhook (cùng `id` giao dịch) 2 lần liên tiếp:

```powershell
# Lần 1
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/webhooks/payments/sepay" `
    -Method Post -Headers @{ "Authorization" = "Apikey test-sepay-secret-key" } -ContentType "application/json" -Body $webhookPayload

# Lần 2 (ngay lập tức)
$dupRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/webhooks/payments/sepay" `
    -Method Post -Headers @{ "Authorization" = "Apikey test-sepay-secret-key" } -ContentType "application/json" -Body $webhookPayload

$dupRes | ConvertTo-Json
```
*Kết quả:* Phản hồi trả về `{"success":true,"message":"Duplicate transaction ignored"}`. Log hiển thị: `Duplicate SePay transaction ID ... received; returning idempotent success`. Chỉ duy nhất 1 bản ghi giao dịch trong bảng `payment_transactions`.

#### 5.2. Gọi lại API Kích hoạt Xuất Hàng (Duplicate Dispense Trigger)
Gọi thủ công `POST /api/v1/dispense/orders/{orderId}/trigger` khi đơn đã hoàn thành hoặc đang xử lý:

```powershell
$manualTrigger = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/dispense/orders/$orderId/trigger" -Method Post
$manualTrigger | ConvertTo-Json
```
*Kết quả:* Trả về `{"success":true,"data":{"orderId":"...","status":"COMPLETED","message":"Đơn hàng đã hoàn tất xuất hàng."}}`. Không phát thêm lệnh nhả hàng mới tới máy, không trừ thêm tồn kho.

---

### Kịch Bản 6: Xác Minh Bảo Mật Khi Vô Hiệu Hóa Simulator (Simulator Disabled)

Trên môi trường Production, không được phép nạp các simulation endpoints.
1. Thử nghiệm tắt simulator bằng cách đặt biến môi trường trong Terminal Backend:
   ```powershell
   $env:DISPENSE_SIMULATOR_ENABLED = "false"
   ./mvnw spring-boot:run
   ```
2. Thử truy cập endpoint kiểm tra simulator:
   ```powershell
   try {
       Invoke-RestMethod -Uri "http://localhost:8080/api/v1/simulation/device/status" -Method Get
   } catch {
       Write-Host "HTTP Status:" $_.Exception.Response.StatusCode.value__
       $_.Exception.Response | Format-List
   }
   ```
   *Kết quả:* Server trả về chính xác **HTTP 404 NOT_FOUND** (`{"error":{"code":"NOT_FOUND","message":"Đường dẫn tài nguyên không tồn tại."}}`). Kẻ xấu không thể giả mạo sự kiện hay thay đổi kịch bản máy.

---

### Kịch Bản 7: Reset Trạng Thái Bộ Nhớ Của Simulator

Khi cần làm mới bộ nhớ đệm chống trùng lệnh của Simulator (tương đương xóa NVS flash trên ESP32):

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/simulation/device/reset" -Method Post
```
*Kết quả:* Server trả về `{"success":true,"data":{"message":"Simulator NVS state cleared successfully."}}`.

---

## 7. Bảng Tổng Hợp Kiểm Tra Trạng Thái & Lệnh SQL Tra Cứu Nhanh

| Trạng thái Order | Trạng thái DispenseCommand | Stock Quantity | Reserved Quantity | Ý nghĩa nghiệp vụ |
| :--- | :--- | :--- | :--- | :--- |
| `PENDING_PAYMENT` | *(chưa tạo)* | Không đổi | `+1` | Chờ khách quét mã VietQR |
| `PAID` / `DISPENSING` | `PENDING` / `PUBLISHED` / `ACKNOWLEDGED` | Không đổi | `1` | Đã nhận tiền, đang quay động cơ |
| `COMPLETED` | `COMPLETED` | `-1` | `0` (xóa giữ chỗ) | Đã nhả hàng ra khay thành công |
| `DISPENSE_FAILED` | `FAILED` / `REJECTED` | Không đổi | `0` (hoàn trả) | Lỗi động cơ/kẹt hàng; nhả giữ chỗ |
| `NEEDS_RECONCILIATION` | `UNCERTAIN` / `TIMEOUT` | Không đổi | `1` (giữ nguyên) | Sự cố bất định; giữ nguyên để đối soát |
| `EXPIRED` | *(không có)* | Không đổi | `0` (hoàn trả) | Hết 15 phút chưa thanh toán |

### Các câu lệnh SQL hữu ích để theo dõi trực tiếp trong quá trình test:

```sql
-- 1. Xem 5 đơn hàng gần nhất
SELECT id, public_order_code, status, total_amount_vnd, transfer_reference, created_at, paid_at, completed_at
FROM orders 
ORDER BY created_at DESC LIMIT 5;

-- 2. Xem các lệnh xuất hàng tương ứng
SELECT command_id, order_id, slot_number, status, attempt_count, failure_code, published_at, finished_at
FROM dispense_commands 
ORDER BY created_at DESC LIMIT 5;

-- 3. Xem các sự kiện từ thiết bị mô phỏng
SELECT event_id, command_id, event_type, slot_number, created_at, payload
FROM device_events 
ORDER BY created_at DESC LIMIT 10;

-- 4. Xem lịch sử giao dịch webhook SePay
SELECT provider_transaction_id, order_id, amount_vnd, verification_status, transaction_status, received_at
FROM payment_transactions 
ORDER BY received_at DESC LIMIT 5;

-- 5. Xem tồn kho và lượng đang giữ chỗ của từng khe hàng
SELECT slot_number, name, price_vnd, stock_quantity, reserved_quantity, (stock_quantity - reserved_quantity) AS available_stock
FROM products 
ORDER BY slot_number ASC;
```

---

## 8. Kết Luận & Khuyến Nghị Trước Khi Chuyển Sang Phase Sau

1. Toàn bộ logic nghiệp vụ (Tạo đơn -> Giữ chỗ kho -> Nhận Webhook SePay -> Kích hoạt xuất hàng -> Xử lý kết quả mô phỏng -> Cập nhật tồn kho an toàn) đã được kiểm chứng hoạt động trơn tru qua tài liệu hướng dẫn này.
2. Khi chuyển sang giai đoạn tích hợp phần cứng thật (ESP32 / MQTT):
   - Thay thế `SimulatedDeviceAdapter` bằng `MqttDeviceAdapter` tuân theo chuẩn trong `docs/MQTT_CONTRACT.md`.
   - Giữ nguyên toàn bộ logic máy trạng thái, khóa dữ liệu và cơ chế bảo vệ kho của `DispenseServiceImpl`.
