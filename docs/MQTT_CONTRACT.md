# MQTT Contract — Vending Machine Prototype

**Phiên bản:** 1.1<br>
**Trạng thái:** Draft — Cập nhật sau rà soát Giai đoạn 1 (chưa triển khai)<br>
**Nguồn yêu cầu:** `PRODUCT_SPEC.md §13`, `PRODUCT_SPEC.md §14`
**Thay đổi so với v1.0:** F7 — Session policy, NVS persistence, command state machine trên ESP32, xử lý trạng thái không xác định, làm rõ exactly-once limitation
**Cập nhật lần cuối:** 2026-10-18

---

## 1. Tổng quan

Backend Java và ESP32 giao tiếp qua MQTT. Backend là nơi duy nhất có quyền phát lệnh nhả hàng sau khi đã xác minh thanh toán thực tế. ESP32 chỉ thực thi lệnh hợp lệ và báo cáo kết quả.

```
BACKEND (Publisher)          MQTT BROKER (TLS)           ESP32 (Subscriber)
     │                             │                             │
     │── vending/VM001/commands ──►│──── vending/VM001/commands ►│
     │                             │                             │
     │◄── vending/VM001/events ────│◄─── vending/VM001/events ──│
     │                             │                             │
     │◄── vending/VM001/heartbeat ─│◄── vending/VM001/heartbeat ─│
```

**Nguyên tắc cốt lõi không thể vi phạm:**

1. Giao dịch ngân hàng phải được Backend xác minh trước khi bất kỳ lệnh MQTT nào được phát ra.
2. Mỗi lệnh có `commandId` UUID duy nhất và `expiresAt` — hai cơ chế bảo vệ độc lập nhau.
3. Backend ghi nhận lệnh vào database bền vững trước khi publish lên MQTT.
4. ESP32 ghi nhận `commandId` đã xử lý vào NVS Flash để bảo vệ qua reboot.
5. Không tự động phát lại lệnh nhả hàng khi kết quả không xác định.
6. Không có frontend hay thiết bị nào khác được phép publish vào topic `commands`.

**Giới hạn bảo đảm rõ ràng:**

> MQTT QoS 1 (at-least-once) kết hợp với `commandId` idempotency và `expiresAt` cung cấp bảo vệ tốt trong thực tiễn, nhưng **không thể bảo đảm exactly-once physical dispensing**. Kịch bản không thể giải quyết hoàn toàn bằng phần mềm: motor đã quay nhưng ESP32 mất điện trước khi gửi event kết quả — trường hợp này phải được xử lý bằng quy trình đối soát thủ công (`NEEDS_RECONCILIATION`). Nếu không có cảm biến xác nhận hàng rơi, hệ thống không thể biết chắc sản phẩm đã được giao hay chưa.

---

## 2. Cấu hình broker

### 2.1. Kết nối

| Thông số | Giá trị |
|---|---|
| Giao thức | MQTT over TLS (MQTTS) |
| Port | `8883` |
| TLS version | TLS 1.2 hoặc TLS 1.3 |
| MQTT version | MQTT 3.1.1 hoặc MQTT 5.0 (TBD — xác nhận khi chọn broker) |
| Broker | TBD — EMQX Cloud Serverless / HiveMQ Cloud / tùy chọn khác |

> **TBD:** Broker cụ thể, hostname và MQTT version sẽ được chốt ở Giai đoạn 6.

### 2.2. Xác thực

Tất cả client phải xác thực bằng username/password. Không cho phép kết nối ẩn danh.

| Client | Username (ví dụ) | Quyền |
|---|---|---|
| Backend Java | `backend-vm001` | Publish: `vending/VM001/commands`; Subscribe: `vending/VM001/events`, `vending/VM001/heartbeat` |
| ESP32 VM001 | `esp32-vm001` | Subscribe: `vending/VM001/commands`; Publish: `vending/VM001/events`, `vending/VM001/heartbeat` |

> **ACL bắt buộc:** Broker phải cấu hình ACL để ESP32 không thể publish vào topic `commands`, backend không subscribe wildcard không cần thiết, và không client nào truy cập topic của machine khác.

### 2.3. Session policy

| Client | Clean Session | Lý do |
|---|---|---|
| **Backend Java** | `false` (persistent session) | Backend giữ session để không mất QoS 1 events từ ESP32 khi backend tạm thời bị ngắt kết nối ngắn |
| **ESP32** | `true` (clean session) | ESP32 bắt đầu với session sạch sau mỗi lần kết nối để tránh nhận lại các message QoS 1 cũ có thể đã hết hạn |

**Hệ quả của `cleanSession=true` cho ESP32:**

Khi ESP32 offline, broker sẽ **không** lưu trữ QoS 1 message dành cho ESP32. Nếu Backend publish command trong khi ESP32 offline, message sẽ không được giao sau khi ESP32 reconnect. Hệ quả:

- Backend phải phát hiện ESP32 offline (qua heartbeat timeout) trước khi publish command.
- Nếu ESP32 offline tại thời điểm cần giao hàng → Backend không publish command → Chờ hoặc chuyển sang `NEEDS_RECONCILIATION`.
- Sau khi ESP32 reconnect và gửi heartbeat → Backend có thể publish command mới (nếu lệnh chưa hết hạn).

**Kịch bản nguy hiểm được ngăn chặn bởi `cleanSession=true`:**

```
1. Backend publish command (QoS 1) → broker nhận và ACK
2. ESP32 offline trước khi nhận
3. Backend timeout T1 → order → NEEDS_RECONCILIATION
4. Admin chưa xử lý reconciliation
5. ESP32 reconnect
   → Nếu cleanSession=true: ESP32 KHÔNG nhận lại command đã stale → AN TOÀN
   → Nếu cleanSession=false: ESP32 CÓ THỂ nhận lại command → NGUY HIỂM nếu expiresAt chưa qua
```

**Lớp bảo vệ bổ sung:** `expiresAt` kiểm tra thêm lớp an toàn thứ hai. Ngay cả nếu một message cũ nào đó được giao, ESP32 phải từ chối lệnh đã hết hạn.

---

## 3. Topic convention

Sử dụng `{machineId}` để namespace theo từng máy. MVP chỉ có một máy `VM001`.

| Topic | Hướng | Mục đích |
|---|---|---|
| `vending/VM001/commands` | Backend → ESP32 | Backend gửi lệnh nhả hàng |
| `vending/VM001/events` | ESP32 → Backend | ESP32 gửi xác nhận và kết quả |
| `vending/VM001/heartbeat` | ESP32 → Backend | ESP32 báo cáo trạng thái định kỳ |

**Quy tắc:**

- **Không dùng retained message** cho topic `commands` — lệnh nhả hàng không được lưu lại trên broker.
- Không dùng wildcard subscribe trừ khi được cấu hình rõ ràng và hạn chế.

---

## 4. Payload — Lệnh nhả hàng (Command)

Backend publish vào `vending/VM001/commands` **sau khi** đã ghi nhận bản ghi lệnh vào database.

### 4.1. Schema

```json
{
  "commandId": "550e8400-e29b-41d4-a716-446655440020",
  "machineId": "VM001",
  "orderId": "550e8400-e29b-41d4-a716-446655440010",
  "slotNumber": 2,
  "action": "DISPENSE",
  "createdAt": "2026-10-18T10:32:16.000Z",
  "expiresAt": "2026-10-18T10:33:16.000Z",
  "parameters": {
    "motorRunMs": 800
  }
}
```

### 4.2. Mô tả trường

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `commandId` | string (UUID v4) | ✅ | ID duy nhất của lệnh — dùng để chống trùng lặp qua NVS |
| `machineId` | string | ✅ | Phải khớp với machine ID của ESP32. Từ chối nếu không khớp. |
| `orderId` | string (UUID v4) | ✅ | ID đơn hàng liên kết — tham chiếu trong event phản hồi |
| `slotNumber` | integer (1-3) | ✅ | Khay vật lý cần kích hoạt. Phải là 1, 2 hoặc 3. |
| `action` | string | ✅ | MVP chỉ có `"DISPENSE"`. |
| `createdAt` | string (ISO 8601 UTC) | ✅ | Thời điểm lệnh được tạo |
| `expiresAt` | string (ISO 8601 UTC) | ✅ | Hết hạn sau thời điểm này — ESP32 phải từ chối nếu quá hạn |
| `parameters.motorRunMs` | integer | ✅ | Thời gian chạy động cơ tối đa (ms). **Giá trị TBD sau khi kiểm thử phần cứng thực tế.** |

### 4.3. Quy tắc xác thực trên ESP32

ESP32 phải từ chối lệnh và publish event `COMMAND_REJECTED` nếu:

1. JSON không parse được.
2. Thiếu bất kỳ trường bắt buộc nào.
3. `machineId` không khớp với machine ID của thiết bị.
4. `slotNumber` không nằm trong phạm vi 1–3.
5. `action` không phải `"DISPENSE"`.
6. `expiresAt` < thời điểm hiện tại của thiết bị (lệnh đã hết hạn).
7. `commandId` đã có trong NVS Flash (lệnh trùng lặp đã xử lý từ trước).
8. Có một lệnh đang được thực thi (motor đang chạy) — phải từ chối để tránh concurrent dispensing.

---

## 5. Trạng thái lệnh trên ESP32 (Command State Machine)

ESP32 quản lý mỗi lệnh qua các trạng thái sau. Đây là nền tảng cho logic idempotency và xử lý reboot.

```
                  Nhận command
                       │
                       ▼
              ┌─────────────────┐
              │   VALIDATING    │ ← Đang kiểm tra: signature, expiresAt, NVS
              └────────┬────────┘
                       │
            ┌──────────┴──────────┐
        Hợp lệ                 Không hợp lệ
            │                      │
            ▼                      ▼
   ┌─────────────────┐    ┌─────────────────┐
   │    ACCEPTED     │    │    REJECTED     │ → Publish COMMAND_REJECTED
   │(ghi vào NVS,   │    └─────────────────┘
   │ publish event) │
   └────────┬────────┘
            │ Bắt đầu chạy motor
            ▼
   ┌─────────────────┐
   │   EXECUTING     │ ← Motor đang quay
   └────────┬────────┘
            │
  ┌─────────┴──────────┐
Motor kết thúc       Motor timeout/lỗi/mất điện
  bình thường            │
  │              ┌───────┴────────────┐
  ▼         Kết quả           Không xác định
┌──────────┐  xác định         (reboot giữa chừng)
│COMPLETED │  (failure)              │
│          │      │                  ▼
│          │      ▼         ┌─────────────────┐
└──────────┘ ┌──────────┐  │    UNCERTAIN    │ → Publish DISPENSE_RESULT (result=UNCERTAIN)
(Publish     │  FAILED  │  └─────────────────┘
SUCCESS)     └──────────┘
             (Publish FAILURE)
```

**Quy tắc lưu trữ NVS theo trạng thái:**

| Trạng thái | Lưu vào NVS | Nội dung |
|---|---|---|
| `ACCEPTED` | ✅ Ngay khi accept | `commandId`, `slotNumber`, `state=ACCEPTED`, timestamp |
| `EXECUTING` | ✅ Trước khi kích hoạt motor | Cập nhật `state=EXECUTING`, `motorStartedAt` |
| `COMPLETED` | ✅ Sau khi motor dừng | Cập nhật `state=COMPLETED`, `result=SUCCESS` |
| `FAILED` | ✅ Sau khi phát hiện lỗi | Cập nhật `state=FAILED`, `failureCode` |
| `REJECTED` | ❌ Không lưu | Lệnh invalid không cần theo dõi |

---

## 6. Payload — Xác nhận nhận lệnh (Command Accepted)

ESP32 publish vào `vending/VM001/events` ngay khi nhận và xác nhận lệnh hợp lệ, **trước** khi kích hoạt động cơ.

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440030",
  "commandId": "550e8400-e29b-41d4-a716-446655440020",
  "machineId": "VM001",
  "orderId": "550e8400-e29b-41d4-a716-446655440010",
  "eventType": "COMMAND_ACCEPTED",
  "slotNumber": 2,
  "timestamp": "2026-10-18T10:32:16.500Z"
}
```

---

## 7. Payload — Từ chối lệnh (Command Rejected)

ESP32 publish khi lệnh không vượt qua xác thực.

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440031",
  "commandId": "550e8400-e29b-41d4-a716-446655440020",
  "machineId": "VM001",
  "orderId": "550e8400-e29b-41d4-a716-446655440010",
  "eventType": "COMMAND_REJECTED",
  "slotNumber": null,
  "rejectionReason": "COMMAND_EXPIRED",
  "timestamp": "2026-10-18T10:32:16.500Z"
}
```

**Giá trị `rejectionReason`:**

| Giá trị | Mô tả |
|---|---|
| `COMMAND_EXPIRED` | Lệnh đã quá thời gian `expiresAt` |
| `DUPLICATE_COMMAND` | `commandId` đã có trong NVS (đã xử lý trước đó) |
| `INVALID_MACHINE` | `machineId` không khớp với thiết bị |
| `INVALID_SLOT` | `slotNumber` nằm ngoài phạm vi hợp lệ (chỉ chấp nhận 1, 2, 3) |
| `INVALID_PAYLOAD` | JSON không hợp lệ hoặc thiếu trường bắt buộc |
| `UNKNOWN_ACTION` | `action` không được hỗ trợ (chỉ chấp nhận `DISPENSE`) |
| `BUSY` | Đang thực thi lệnh khác — từ chối để tránh concurrent dispensing |
| `REBOOT_BEFORE_EXECUTION` | Thiết bị reboot khi lệnh mới ở trạng thái `ACCEPTED` (motor chưa quay) |

---

## 8. Payload — Kết quả nhả hàng (Dispense Result)

ESP32 publish sau khi chu kỳ động cơ kết thúc. Bao gồm cả trường hợp kết quả không xác định.

### 8.1. Thành công (Motor cycle hoàn thành bình thường)

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440032",
  "commandId": "550e8400-e29b-41d4-a716-446655440020",
  "machineId": "VM001",
  "orderId": "550e8400-e29b-41d4-a716-446655440010",
  "eventType": "DISPENSE_RESULT",
  "slotNumber": 2,
  "result": "SUCCESS",
  "verificationLevel": "MOTOR_CYCLE_ONLY",
  "failureCode": null,
  "failureMessage": null,
  "timestamp": "2026-10-18T10:32:18.900Z"
}
```

### 8.2. Thất bại xác định

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440033",
  "commandId": "550e8400-e29b-41d4-a716-446655440020",
  "machineId": "VM001",
  "orderId": "550e8400-e29b-41d4-a716-446655440010",
  "eventType": "DISPENSE_RESULT",
  "slotNumber": 2,
  "result": "FAILURE",
  "verificationLevel": "MOTOR_CYCLE_ONLY",
  "failureCode": "MOTOR_TIMEOUT",
  "failureMessage": "Motor did not complete cycle within expected time.",
  "timestamp": "2026-10-18T10:32:19.000Z"
}
```

### 8.3. Kết quả không xác định (Uncertain)

ESP32 publish event này khi startup và phát hiện NVS có lệnh ở trạng thái `EXECUTING` (nghĩa là reboot xảy ra giữa chừng khi motor đang chạy hoặc vừa chạy xong nhưng chưa gửi được result).

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440034",
  "commandId": "550e8400-e29b-41d4-a716-446655440020",
  "machineId": "VM001",
  "orderId": "550e8400-e29b-41d4-a716-446655440010",
  "eventType": "DISPENSE_RESULT",
  "slotNumber": 2,
  "result": "UNCERTAIN",
  "verificationLevel": "MOTOR_CYCLE_ONLY",
  "failureCode": "REBOOT_DURING_EXECUTION",
  "failureMessage": "Device rebooted while motor was in EXECUTING state. Physical outcome unknown.",
  "timestamp": "2026-10-18T10:35:00.000Z"
}
```

**Hành vi Backend khi nhận `result=UNCERTAIN`:**

- Chuyển order sang `NEEDS_RECONCILIATION`.
- **Không** tự động phát lại lệnh.
- **Không** diễn giải là thành công hay thất bại.
- Quản trị viên phải xác nhận thủ công kết quả vật lý.

### 8.4. Mô tả trường

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `eventId` | string (UUID) | ✅ | ID duy nhất của event này |
| `commandId` | string (UUID) | ✅ | ID lệnh gốc gây ra event này |
| `machineId` | string | ✅ | Machine ID |
| `orderId` | string (UUID) | ✅ | Order ID liên kết |
| `eventType` | string | ✅ | Phải là `"DISPENSE_RESULT"` |
| `slotNumber` | integer (1-3) | ✅ | Slot đã được thực thi |
| `result` | string | ✅ | `"SUCCESS"`, `"FAILURE"` hoặc `"UNCERTAIN"` |
| `verificationLevel` | string | ✅ | Xem §8.5 |
| `failureCode` | string \| null | ❌ | Mã lỗi nếu `result != "SUCCESS"` |
| `failureMessage` | string \| null | ❌ | Mô tả kỹ thuật cho operator |
| `timestamp` | string (ISO 8601 UTC) | ✅ | Thời điểm kết thúc chu kỳ hoặc phát hiện lỗi |

### 8.5. Giá trị `verificationLevel`

| Giá trị | Điều kiện sử dụng |
|---|---|
| `MOTOR_CYCLE_ONLY` | Động cơ hoàn thành chu kỳ. Không có cảm biến xác nhận hàng rơi vật lý. **Đây là mức mặc định nếu không có cảm biến.** |
| `SENSOR_CONFIRMED` | Cảm biến hồng ngoại hoặc tương đương đã xác nhận hàng đi qua. **Chỉ dùng khi phần cứng thực sự hỗ trợ.** |

> **Bắt buộc:** Backend không được diễn giải `MOTOR_CYCLE_ONLY` là bảo đảm hàng đã rơi. Frontend phải hiển thị thông báo trung thực theo SPEC §6.6.

### 8.6. Giá trị `failureCode`

| Giá trị | Mô tả |
|---|---|
| `MOTOR_TIMEOUT` | Động cơ không hoàn thành trong `motorRunMs` |
| `MOTOR_STALL` | Phát hiện động cơ bị kẹt (nếu có cơ chế phát hiện) |
| `SLOT_BLOCKED` | Cảm biến phát hiện khay bị kẹt (nếu có) |
| `HARDWARE_ERROR` | Lỗi phần cứng không xác định |
| `REBOOT_DURING_EXECUTION` | Reboot xảy ra khi motor đang ở trạng thái `EXECUTING` |

---

## 9. Payload — Heartbeat

ESP32 publish định kỳ (khuyến nghị mỗi 30–60 giây — TBD) vào `vending/VM001/heartbeat`.

```json
{
  "machineId": "VM001",
  "firmwareVersion": "1.0.0",
  "uptimeSeconds": 3600,
  "wifiRssi": -65,
  "mqttReconnectCount": 0,
  "slots": [
    { "slotNumber": 1, "lastDispenseResult": "SUCCESS", "lastDispenseAt": "2026-10-18T09:00:00Z" },
    { "slotNumber": 2, "lastDispenseResult": null, "lastDispenseAt": null },
    { "slotNumber": 3, "lastDispenseResult": null, "lastDispenseAt": null }
  ],
  "timestamp": "2026-10-18T10:29:55.000Z"
}
```

**Mô tả trường:**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `machineId` | string | ✅ | Machine ID |
| `firmwareVersion` | string | ✅ | Phiên bản firmware |
| `uptimeSeconds` | integer | ✅ | Thời gian hoạt động kể từ lần boot gần nhất |
| `wifiRssi` | integer | ✅ | Cường độ tín hiệu Wi-Fi (dBm). Khuyến nghị > -70 dBm |
| `mqttReconnectCount` | integer | ✅ | Số lần reconnect MQTT kể từ lần boot |
| `slots[].lastDispenseResult` | string \| null | ❌ | Kết quả nhả hàng gần nhất từ NVS |
| `timestamp` | string (ISO 8601 UTC) | ✅ | Thời điểm gửi heartbeat |

> **Heartbeat ≠ Motor OK:** Backend biết ESP32 đang kết nối, không phải bằng chứng cơ học động cơ hoạt động bình thường.

---

## 10. QoS và Retained Message

| Topic | QoS | Retained | Lý do |
|---|---|---|---|
| `vending/VM001/commands` | QoS 1 | **Không (false)** | Không lưu lại lệnh cũ; xử lý stale message qua `expiresAt` và NVS |
| `vending/VM001/events` | QoS 1 | Không | Events tức thời; idempotency qua Backend kiểm tra DB |
| `vending/VM001/heartbeat` | QoS 0 | Không | Mất heartbeat đơn lẻ là chấp nhận được; QoS 0 giảm tải broker |

**Tại sao QoS 1 cho commands và events?**

QoS 1 đảm bảo message đến ít nhất một lần. Idempotency được xử lý ở tầng ứng dụng:

- **Phía ESP32:** Kiểm tra `commandId` trong NVS — đã xử lý thì reject với `DUPLICATE_COMMAND`.
- **Phía Backend:** Kiểm tra `commandId` trong DB trước khi xử lý event; xử lý event trùng lặp không thay đổi trạng thái.

---

## 11. Xử lý lệnh trùng lặp, NVS Persistence và Durable Event Outbox

### 11.1. ESP32 — NVS Flash storage & Durable Event Outbox

Khi cấu hình ESP32 với `cleanSession=true`, broker MQTT sẽ **không** lưu trữ trạng thái phiên làm việc hay các gói tin QoS 1 đang gửi dở qua các lần reboot. Do đó, **chỉ riêng MQTT QoS 1 không thể bảo đảm gửi event bền vững nếu ESP32 bị mất điện hoặc crash**.

Để bảo đảm không bao giờ mất thông tin kết quả nhả hàng (đặc biệt là trạng thái `UNCERTAIN`), ESP32 phải triển khai cơ chế **NVS Durable Event Outbox**.

**Dữ liệu lưu trong NVS:**

```
key: "cmd_active"     → commandId của lệnh đang active (UUID v4 hoặc rỗng)
key: "cmd_state"      → ACCEPTED | EXECUTING | COMPLETED | FAILED | UNCERTAIN
key: "cmd_slot"       → slotNumber của lệnh active
key: "cmd_started"    → timestamp khi bắt đầu chạy motor
key: "cmd_orderid"    → orderId liên kết
key: "cmd_expires"    → expiresAt timestamp (epoch ms)

key: "outbox_pending" → boolean (true nếu có event chưa nhận được PUBACK từ broker)
key: "outbox_event"   → JSON string chứa toàn bộ payload của event kết quả gần nhất
```

**Sliding window của commandId đã hoàn thành:** Lưu 20 `commandId` gần nhất đã kết thúc (`COMPLETED`, `FAILED`, `UNCERTAIN`) để loại trừ lệnh gửi lại (duplicate command).

---

### 11.2. Quy trình NVS Event Outbox và Startup sau Reboot

```
Boot / Reconnect
  │
  ├── 1. Kiểm tra NVS Outbox: outbox_pending == true?
  │   ├── CÓ: Tồn tại event chưa nhận được PUBACK từ broker (do mất mạng hoặc crash trước đó)
  │   │   → Kết nối MQTT broker
  │   │   → Republish chính xác payload từ "outbox_event" (giữ nguyên eventId và commandId gốc) lên topic vending/{machineId}/events (QoS 1)
  │   │   → Khi nhận được MQTT PUBACK từ broker:
  │   │   │   → Cập nhật NVS: outbox_pending = false
  │   └── KHÔNG: Không có event tồn đọng
  │
  ├── 2. Đọc NVS "cmd_active" & "cmd_state":
  │   │
  │   ├── cmd_state == ACCEPTED?
  │   │   → Motor chưa từng được kích hoạt trước khi reboot (GPIO luôn LOW)
  │   │   → TUYỆT ĐỐI KHÔNG TỰ KHỞI ĐỘNG MOTOR SAU KHI REBOOT
  │   │   → ESP32 publish COMMAND_REJECTED với rejectionReason="REBOOT_BEFORE_EXECUTION"
  │   │   → Xóa cmd_active, đưa cmd_state về IDLE
  │   │   → Backend nhận COMMAND_REJECTED hoặc timeout T1 ➔ chuyển đơn hàng sang NEEDS_RECONCILIATION
  │   │
  │   ├── cmd_state == EXECUTING?
  │   │   → ESP32 bị sập nguồn / reboot trong lúc motor đang quay hoặc vừa quay xong chưa kịp lưu COMPLETED
  │   │   → Kết quả vật lý là hoàn toàn BẤT ĐỊNH (UNCERTAIN)
  │   │   → Cập nhật NVS: cmd_state = UNCERTAIN
  │   │   → Tạo payload DISPENSE_RESULT với result="UNCERTAIN", failureCode="REBOOT_DURING_EXECUTION"
  │   │   → Lưu payload vào "outbox_event", set "outbox_pending = true"
  │   │   → Publish event lên MQTT (QoS 1) và chờ PUBACK để xóa outbox
  │   │   → TUYỆT ĐỐI KHÔNG KÍCH HOẠT MOTOR LẦN THỨ HAI
  │   │
  │   ├── cmd_state == COMPLETED / FAILED / UNCERTAIN?
  │   │   → Quá trình nhả hàng đã kết thúc trước đó. Nếu outbox_pending đã false thì không cần làm gì thêm.
  │   │
  │   └── NVS rỗng / cmd_state == IDLE?
  │       → Khởi động bình thường, tất cả chân điều khiển motor ở mức LOW (OFF).
```

---

### 11.3. Phân tích các tình huống mất điện và giới hạn của MQTT PUBACK

#### 11.3.1. Phân tích 4 tình huống mất điện điển hình

| Tình huống mất điện | Trạng thái tại thời điểm mất điện | Hành vi của ESP32 sau khi có điện lại | Hành vi của Backend |
|---|---|---|---|
| **1. Trước khi lưu NVS** | Gói tin lệnh vừa tới bộ đệm RAM của Wi-Fi, chưa kịp ghi Flash NVS. | NVS hoàn toàn rỗng. ESP32 khởi động IDLE bình thường, motor không quay. | Backend chờ hết timeout T1 (không nhận được `COMMAND_ACCEPTED`) ➔ chuyển đơn hàng sang `NEEDS_RECONCILIATION`. Tuyệt đối an toàn. |
| **2. Sau khi lưu NVS `ACCEPTED` nhưng trước khi chạy motor** | NVS đã ghi `cmd_state = ACCEPTED`, chân GPIO vẫn ở mức LOW (chưa cấp điện motor). | ESP32 đọc NVS thấy `ACCEPTED`. Vì motor chưa chạy, ESP32 **tuyệt đối không bật motor**. ESP32 gửi `COMMAND_REJECTED` (`reason: REBOOT_BEFORE_EXECUTION`), dọn NVS về IDLE. | Backend nhận `COMMAND_REJECTED` (hoặc timeout T1) ➔ chuyển order sang `NEEDS_RECONCILIATION`. Không có nguy cơ giao hàng trùng. |
| **3. Khi motor đang quay (`EXECUTING`)** | Motor đang nhận điện và quay dở hành trình thì mất nguồn. Không rõ sản phẩm đã rơi khỏi lò xo chưa. | ESP32 khởi động lại, đọc NVS thấy `cmd_state = EXECUTING`. Chuyển ngay sang `UNCERTAIN`, lưu `outbox_event`, set `outbox_pending = true`. Publish event `UNCERTAIN` với `failureCode = REBOOT_DURING_EXECUTION`. | Backend nhận `UNCERTAIN` ➔ lập tức chuyển order sang `NEEDS_RECONCILIATION`, thông báo khách hàng và chờ operator kiểm tra cơ học. |
| **4. Sau khi quay xong motor nhưng trước khi nhận PUBACK** | Motor đã hoàn thành chu kỳ, NVS đã lưu `outbox_event` với kết quả `SUCCESS`, `outbox_pending = true`, nhưng mạng rớt hoặc reboot trước khi nhận ACK. | ESP32 khởi động lại, kiểm tra thấy `outbox_pending == true`. ESP32 kết nối broker và republish chính xác event `SUCCESS` (giữ nguyên `eventId` và `commandId`). | Backend nhận event `SUCCESS` ➔ chuyển order sang `COMPLETED`, trừ tồn kho. Nếu Backend đã nhận trước đó thì bỏ qua duplicate. |

#### 11.3.2. Sập nguồn hoặc reboot liên tiếp nhiều lần (Repeated Reboots)

- Nếu ESP32 bị mất điện liên tục trong khi đang khởi động hoặc đang cố gửi event `UNCERTAIN`, dữ liệu trong Flash NVS (`outbox_pending = true`, `outbox_event`) không bị mất đi nhờ tính bền vững của bộ nhớ Flash.
- Trong mọi lần boot, firmware luôn kiểm tra NVS Outbox trước tiên. Event chưa gửi thành công sẽ tiếp tục được giữ và republish cho đến khi nhận được xác nhận `PUBACK` từ broker.

#### 11.3.3. Giới hạn bản chất của MQTT PUBACK

> **Cảnh báo kiến trúc quan trọng:**
> - Gói tin `PUBACK` mà ESP32 nhận được **chỉ là biên nhận tầng mạng giữa ESP32 và MQTT Broker**, xác nhận broker đã lưu gói tin QoS 1 vào buffer.
> - **`PUBACK` không chứng minh rằng Backend Java đã nhận được message hay đã commit dữ liệu thành công vào database.**
> - Tính toàn vẹn đầu-cuối (End-to-End Reliability) được bảo đảm nhờ sự phối hợp của 3 yếu tố:
>   1. Backend duy trì **Persistent Session (`cleanSession = false`)** với broker: Khi Backend gặp sự cố hoặc khởi động lại, Broker sẽ lưu trữ các message QoS 1 từ ESP32 và gửi lại ngay khi Backend kết nối lại.
>   2. Backend xử lý event trong transaction cơ sở dữ liệu (`device_events` + cập nhật trạng thái đơn hàng).
>   3. Backend xử lý idempotent: Dù Broker hay ESP32 có redeliver event nhiều lần, Backend chỉ cập nhật trạng thái ở lần đầu tiên.

---

### 11.4. Backend — Idempotency và Tiếp nhận Event trùng lặp

Do cơ chế QoS 1 redelivery và cơ chế Outbox tự gửi lại của ESP32, Backend có thể nhận cùng một event nhiều lần. Backend phải đảm bảo:

1. **Khớp lệnh:** Kiểm tra `commandId` và `eventId` trong bảng `dispense_commands` và `device_events`.
2. **Idempotent State Transition:**
   - Nếu đơn hàng đã chuyển sang trạng thái tương ứng (`COMPLETED`, `DISPENSE_FAILED`, hoặc `NEEDS_RECONCILIATION`), việc nhận lại event trùng lặp **không được kích hoạt thay đổi trạng thái lần thứ hai**, không kích hoạt trừ tồn kho lần hai, và không gửi thông báo trùng lặp.
   - Ghi nhận mọi event nhận được vào `device_events` để phục vụ audit/truy vết.
3. **An toàn vật lý:** Vì motor vật lý trên ESP32 chỉ kích hoạt tối đa một lần cho mỗi `commandId`, việc nhận lại event chỉ là đồng bộ trạng thái ở tầng phần mềm, hoàn toàn không có rủi ro nhả thêm hàng.

---

## 12. Xử lý Timeout, Hết hạn lệnh và Retransmission

### 12.1. Phân biệt Message Retransmission vs. Physical Dispensing Retry

Đây là nguyên tắc an toàn quan trọng nhất của hệ thống:

| Hành động | Định nghĩa | Cho phép? | Điều kiện & Quy tắc |
|---|---|:---:|---|
| **Truyền tải lại gói tin MQTT (Message Retransmission)** | Gửi lại gói tin MQTT chứa lệnh nhả hàng ở tầng mạng khi nghi ngờ rớt gói. | **Hạn chế** | - **BẮT BUỘC giữ nguyên `commandId` duy nhất ban đầu**.<br>- Chỉ được gửi khi `expiresAt` chưa hết hạn.<br>- ESP32 dùng NVS để chặn kích hoạt motor lần hai nếu lệnh cũ đã từng chạy. |
| **Kích hoạt lại motor vật lý (Physical Dispensing Retry)** | Tạo ra lệnh mới để quay motor thêm một lần nữa cho đơn hàng chưa rõ kết quả. | ❌ **CẤM TỰ ĐỘNG** | - **Tuyệt đối không tự động phát sinh `commandId` mới** để retry khi chưa xác minh được trạng thái vật lý của máy.<br>- Mọi thao tác quay lại motor phải do Quản trị viên thực hiện sau khi đối soát thực địa. |

---

### 12.2. Kiểm tra thời hạn lệnh (`expiresAt`) đa tầng trên ESP32

Để ngăn chặn tuyệt đối việc motor kích hoạt trễ khi khách hàng đã rời khỏi máy:

1. **Kiểm tra tầng 1 (Ngay khi nhận lệnh từ MQTT):**
   - ESP32 so sánh `currentTime >= expiresAt`.
   - Nếu đã hết hạn: từ chối ngay lập tức, publish `COMMAND_REJECTED` với `reason: "COMMAND_EXPIRED"`, không ghi nhận vào trạng thái active của NVS.
2. **Kiểm tra tầng 2 (Ngay trước khi kích hoạt chân GPIO điều khiển motor):**
   - Sau khi hoàn thành các bước ghi NVS, ngay trước khi cấp xung điện cho motor driver, ESP32 kiểm tra lại `currentTime >= expiresAt` một lần nữa.
   - Nếu đã quá hạn trong quá trình chuẩn bị: hủy kích hoạt motor, cập nhật NVS `cmd_state = FAILED`, publish `DISPENSE_RESULT` với `failureCode = "COMMAND_EXPIRED_BEFORE_EXECUTION"`.

---

### 12.3. Quy trình Timeout trên Backend và nguyên tắc không diễn giải sai

```
T=0:    Backend publish command lên MQTT
T=T1:   Chờ COMMAND_ACCEPTED (T1 = 30 giây đề xuất — TBD)
  │
  ├── Nhận ACCEPTED → tiếp tục chờ DISPENSE_RESULT
  │   T=T1+T2: Chờ DISPENSE_RESULT (T2 = 60 giây đề xuất — TBD)
  │     │
  │     ├── Nhận SUCCESS → order → COMPLETED (trừ tồn kho)
  │     ├── Nhận FAILURE → order → DISPENSE_FAILED (giải phóng giữ chỗ tồn kho)
  │     ├── Nhận UNCERTAIN → order → NEEDS_RECONCILIATION (giữ nguyên tồn kho chờ duyệt)
  │     └── Timeout T2 không nhận gì → order → NEEDS_RECONCILIATION
  │
  └── Timeout T1 không nhận ACCEPTED → order → NEEDS_RECONCILIATION
```

> **Quy tắc bất biến khi xử lý Timeout:**
> - **Timeout KHÔNG PHẢI là bằng chứng nhả hàng thất bại:** Khi hết thời gian chờ T1 hoặc T2, có thể motor đã quay nhưng gói tin kết quả bị kẹt trên mạng hoặc ESP32 bị ngắt kết nối.
> - Backend **tuyệt đối không** tự ý đánh dấu đơn hàng là `DISPENSE_FAILED` khi bị timeout.
> - Backend **tuyệt đối không** tự động hoàn tiền hoặc cho phép khách đặt lại mà chưa qua đối soát.
> - Đơn hàng bị timeout **bắt buộc phải chuyển sang `NEEDS_RECONCILIATION`** để bảo vệ tài chính và tồn kho.

**Giá trị timeout TBD:**

| Timeout | Đề xuất | Trạng thái |
|---|---|---|
| `T1` — Chờ COMMAND_ACCEPTED | 30 giây | TBD — điều chỉnh sau test thực tế |
| `T2` — Chờ DISPENSE_RESULT | 60 giây | TBD — điều chỉnh sau đo thời gian chạy motor thực tế |

**Lưu ý timer implementation:** Backend nên implement timeout qua scheduled job hoặc persistent timer trong database (không phải in-memory timer duy nhất), vì backend có thể restart trong khoảng thời gian chờ.

### 12.4. ESP32 khi mất kết nối MQTT

1. Tất cả motor phải dừng ngay nếu đang không trong chu kỳ thực thi (không cấp điện liên tục).
2. Nếu mất kết nối khi đang `EXECUTING`: tiếp tục chạy cho hết chu kỳ thời gian `motorRunMs` (để tránh kẹt cơ giữa chừng), cập nhật NVS kết quả, lưu vào outbox và republish DISPENSE_RESULT ngay sau khi reconnect và nhận được kết nối MQTT.
3. Sau khi reconnect, với `cleanSession=true`: ESP32 không nhận lại command cũ bị tích lũy trên broker.
4. Publish heartbeat ngay sau khi reconnect thành công.
5. Kiểm tra NVS Outbox (§11.2) ngay sau khi reconnect để gửi bất kỳ event nào chưa được broker xác nhận PUBACK.

### 12.5. Backend khi mất kết nối MQTT

1. Backend phải có cơ chế reconnect tự động và tiếp tục xử lý pending orders sau khi kết nối lại thành công.
2. Với `cleanSession=false` cho backend: Sau khi reconnect, backend nhận lại các QoS 1 events từ ESP32 mà broker đã buffer trong thời gian backend gián đoạn.
3. Backend xử lý các events này theo quy tắc idempotency (§11.3) — không tạo duplicate state transition hay trừ tồn kho trùng lặp.

---

## 13. Ví dụ luồng đầy đủ — Happy path

```
T=0s     Backend xác minh webhook thanh toán → ghi vào payment_transactions
T=0.1s   Backend ghi dispense_command vào DB (status=PENDING)
T=0.2s   Backend publish command vào vending/VM001/commands (QoS 1)
T=0.5s   ESP32 nhận command
T=0.5s   ESP32 ghi NVS: cmd_state=ACCEPTED
T=0.6s   ESP32 publish COMMAND_ACCEPTED
T=0.7s   Backend nhận COMMAND_ACCEPTED → cập nhật dispense_command status=ACKNOWLEDGED
T=0.7s   Backend chuyển order → DISPENSING
T=0.8s   ESP32 ghi NVS: cmd_state=EXECUTING, motorStartedAt=...
T=0.8s   ESP32 kích hoạt motor slot 2
T=3.6s   ESP32 dừng motor (sau motorRunMs)
T=3.6s   ESP32 ghi NVS: cmd_state=COMPLETED, result=SUCCESS
T=3.7s   ESP32 publish DISPENSE_RESULT (SUCCESS, MOTOR_CYCLE_ONLY)
T=3.8s   Backend nhận DISPENSE_RESULT → ghi device_events
T=3.9s   Backend chuyển order → COMPLETED, cập nhật tồn kho
T=4.0s   Frontend polling nhận status COMPLETED
T=4.1s   Hiển thị thông báo cho khách hàng
```

## 14. Ví dụ luồng — Reboot trong khi EXECUTING

```
T=0s     Backend publish command
T=0.5s   ESP32 nhận, ghi NVS: state=ACCEPTED
T=0.6s   ESP32 publish COMMAND_ACCEPTED
T=0.8s   ESP32 ghi NVS: state=EXECUTING, bắt đầu motor
T=1.5s   ESP32 mất điện / reboot (motor dừng không xác định)
--- reboot ---
T=10s    ESP32 boot, đọc NVS: state=EXECUTING
T=10.1s  ESP32 ghi NVS: state=UNCERTAIN
T=10.5s  ESP32 reconnect MQTT, publish heartbeat
T=10.6s  ESP32 publish DISPENSE_RESULT(result=UNCERTAIN, REBOOT_DURING_EXECUTION)
T=10.7s  Backend nhận → chuyển order → NEEDS_RECONCILIATION
T=10.8s  Frontend nhận status NEEDS_RECONCILIATION + message cho khách hàng
[Admin xem xét thủ công và quyết định hành động tiếp theo]
```

---

## 15. Ánh xạ Slot ↔ GPIO (TBD)

Ánh xạ giữa `slotNumber` (1, 2, 3) và chân GPIO chưa được xác định vì phần cứng chưa thiết kế.

> **TBD — Giai đoạn 6:** GPIO mapping sẽ được ghi vào đây sau khi chọn board ESP32, thiết kế sơ đồ kết nối với motor driver và kiểm tra điện áp không xung đột Wi-Fi/UART.

| `slotNumber` | GPIO | Mô tả |
|---|---|---|
| 1 | TBD | Kết nối Driver slot 1 |
| 2 | TBD | Kết nối Driver slot 2 |
| 3 | TBD | Kết nối Driver slot 3 |

> **Cảnh báo:** Không hardcode GPIO trong firmware cho đến khi sơ đồ điện được kiểm tra thực tế. Sử dụng constant trong file config riêng.

---

## 16. Các mục TBD cần xác nhận

| # | Nội dung TBD | Lý do chưa chốt | Giai đoạn |
|---|---|---|---|
| 1 | Broker hostname và port | Chưa chọn broker | Giai đoạn 6 |
| 2 | MQTT version (3.1.1 hay 5.0) | Phụ thuộc thư viện Java và Arduino | Giai đoạn 6 |
| 3 | Timeout T1 và T2 | Cần đo thực tế trên phần cứng | Giai đoạn 6 |
| 4 | GPIO mapping slot 1/2/3 | Chưa thiết kế sơ đồ phần cứng | Giai đoạn 6 |
| 5 | `motorRunMs` cho từng slot | Cần đo sau khi có motor và lò xo thật | Giai đoạn 6 |
| 6 | Driver động cơ và kiểu điều khiển | Chưa chọn loại motor và driver | Giai đoạn 6 |
| 7 | Heartbeat interval | Cân bằng giữa tần suất phát hiện offline và tải broker | Giai đoạn 6 |
| 8 | Cảm biến xác nhận hàng rơi | Phụ thuộc thiết kế cơ khí và ngân sách | Giai đoạn 6 |
| 9 | NVS key naming convention chi tiết | Sẽ xác định khi viết firmware | Giai đoạn 6 |

---

## 17. Điều không được làm

| Bên | Điều cấm |
|---|---|
| **Backend** | Publish lệnh khi chưa xác minh giao dịch ngân hàng thật |
| **Backend** | Publish lệnh khi chưa ghi nhận bản ghi vào DB |
| **Backend** | Tự động phát lại lệnh sau timeout |
| **Backend** | Xem MQTT delivery thành công là bằng chứng sản phẩm đã nhả |
| **Backend** | Xem `MOTOR_CYCLE_ONLY` là bảo đảm sản phẩm đã rơi |
| **ESP32** | Kích hoạt motor khi không có lệnh hợp lệ |
| **ESP32** | Xử lý lệnh đã hết hạn (`expiresAt` đã qua) |
| **ESP32** | Xử lý lệnh trùng `commandId` (kiểm tra NVS) |
| **ESP32** | Chạy motor vượt quá `motorRunMs` mà không có lý do bảo vệ |
| **ESP32** | Dùng `verificationLevel=SENSOR_CONFIRMED` khi không có cảm biến |
| **ESP32** | Tự khởi động motor sau reboot mà không có lệnh mới hợp lệ |
| **Frontend** | Kết nối trực tiếp tới MQTT broker |
| **Frontend** | Gửi lệnh nhả hàng theo bất kỳ kênh nào |
