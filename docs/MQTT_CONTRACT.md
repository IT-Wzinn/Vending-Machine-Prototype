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
| Backend Java | `backend-vm001` | Publish: `vending/VM001/commands`, `vending/VM001/events/ack`; Subscribe: `vending/VM001/events`, `vending/VM001/heartbeat` |
| ESP32 VM001 | `esp32-vm001` | Subscribe: `vending/VM001/commands`, `vending/VM001/events/ack`; Publish: `vending/VM001/events`, `vending/VM001/heartbeat` |

> **ACL bắt buộc:** Broker phải cấu hình ACL để ESP32 không thể publish vào topic `commands` hay `events/ack`, backend không subscribe wildcard không cần thiết, và không client nào truy cập topic của machine khác.

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
| `vending/VM001/events/ack` | Backend → ESP32 | Backend xác nhận đã lưu bền vững event vào DB |
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
| `COMMAND_EXPIRED` | Lệnh đã quá thời gian `expiresAt` tại thời điểm kiểm tra |
| `COMMAND_EXPIRED_AFTER_REBOOT` | Lệnh `ACCEPTED` trong NVS đã quá hạn (`currentTime >= expiresAt`) sau khi thiết bị reboot và hoàn thành đồng bộ giờ |
| `DUPLICATE_COMMAND` | `commandId` đã có trong NVS (đã xử lý trước đó) |
| `INVALID_MACHINE` | `machineId` không khớp với thiết bị |
| `INVALID_SLOT` | `slotNumber` nằm ngoài phạm vi hợp lệ (chỉ chấp nhận 1, 2, 3) |
| `INVALID_PAYLOAD` | JSON không hợp lệ hoặc thiếu trường bắt buộc |
| `UNKNOWN_ACTION` | `action` không được hỗ trợ (chỉ chấp nhận `DISPENSE`) |
| `BUSY` | Đang thực thi lệnh khác — từ chối để tránh concurrent dispensing |
| `REBOOT_BEFORE_EXECUTION` | Thiết bị reboot khi lệnh ở trạng thái `ACCEPTED` nhưng không thể tiếp tục thực thi an toàn |
| `CLOCK_NOT_SYNCHRONIZED` | Đồng hồ ESP32 chưa đạt tiêu chuẩn Clock Trust qua SNTP (§13.1), từ chối kích hoạt motor |
| `CLOCK_SYNC_TIMEOUT` | Quá thời gian chờ đồng bộ SNTP sau khởi động/nhận lệnh, không thể xác minh `expiresAt` với đồng hồ tin cậy |

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
| `HARDWARE_ERROR` | Lỗi phần cứng không xác định hoặc hỏng phân vùng lưu trữ NVS |
| `REBOOT_DURING_EXECUTION` | Reboot xảy ra khi motor đang ở trạng thái `EXECUTING` (kết quả vật lý bất định) |
| `COMMAND_EXPIRED_AFTER_REBOOT` | Lệnh `ACCEPTED` trong NVS đã quá hạn sau khi thiết bị reboot và hoàn thành đồng bộ giờ |
| `CLOCK_SYNC_TIMEOUT_AFTER_REBOOT` | Hết thời gian chờ đồng bộ SNTP sau reboot, không thể xác minh `expiresAt` với đồng hồ tin cậy để chạy motor |

---

## 9. Payload — Xác nhận sự kiện phía ứng dụng (Event Acknowledgment)

Backend publish vào `vending/VM001/events/ack` (QoS 1) **CHỈ SAU KHI** đã hoàn tất ghi nhận và lưu trữ bền vững (**commit database transaction thành công**) event kết quả vào cơ sở dữ liệu (`device_events` và cập nhật trạng thái đơn hàng).

```json
{
  "ackId": "550e8400-e29b-41d4-a716-446655440040",
  "eventId": "550e8400-e29b-41d4-a716-446655440032",
  "commandId": "550e8400-e29b-41d4-a716-446655440020",
  "machineId": "VM001",
  "orderId": "550e8400-e29b-41d4-a716-446655440010",
  "status": "PROCESSED",
  "timestamp": "2026-10-18T10:32:19.500Z"
}
```

**Mô tả trường:**

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `ackId` | string (UUID) | ✅ | ID duy nhất của bản tin xác nhận |
| `eventId` | string (UUID) | ✅ | ID của event mà Backend đã xử lý và commit DB thành công |
| `commandId` | string (UUID) | ✅ | ID lệnh gốc liên kết |
| `machineId` | string | ✅ | Machine ID |
| `orderId` | string (UUID) | ✅ | Order ID liên kết |
| `status` | string | ✅ | Phải là `"PROCESSED"` |
| `timestamp` | string (ISO 8601 UTC) | ✅ | Thời điểm Backend commit dữ liệu vào DB thành công |

> **Quy tắc cốt lõi về Application-level ACK & Phục hồi lỗi:**
> 1. **Commit-First Policy:** Backend **tuyệt đối không** gửi `EVENT_ACK` khi transaction cơ sở dữ liệu chưa được commit bền vững.
> 2. **Xử lý sập nguồn sau commit (Crash after commit):** Nếu Backend gặp sự cố sập nguồn/crash ngay sau khi commit DB thành công nhưng trước khi kịp publish `EVENT_ACK`:
>    - Sau khi Backend khởi động lại hoặc ESP32 republish lại event từ NVS Outbox, Backend sẽ nhận được event trùng lặp.
>    - Backend tìm thấy bản ghi event đã tồn tại trong `device_events` ➔ **không áp dụng lại hiệu ứng nghiệp vụ** (không đổi trạng thái đơn hàng lần 2, không trừ tồn kho lần 2), nhưng **BẮT BUỘC publish lại bản tin `EVENT_ACK` tương ứng** để ESP32 có thể giải phóng bộ nhớ.
> 3. **Xóa đúng Event ID trên ESP32:** ESP32 **chỉ được phép xóa đúng bản ghi có `eventId` khớp chính xác** (`ack.eventId == nvs.outbox_event.eventId`) khỏi NVS Outbox. Nếu `eventId` không khớp, ESP32 giữ nguyên Outbox.

---

## 10. Payload — Heartbeat

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

## 11. QoS và Retained Message

| Topic | QoS | Retained | Lý do |
|---|---|---|---|
| `vending/VM001/commands` | QoS 1 | **Không (false)** | Không lưu lại lệnh cũ; xử lý stale message qua `expiresAt` và NVS |
| `vending/VM001/events` | QoS 1 | Không | Events tức thời; lưu trữ bền vững qua NVS Outbox phía ESP32 |
| `vending/VM001/events/ack` | QoS 1 | Không | Xác nhận ứng dụng từ Backend để ESP32 xóa outbox |
| `vending/VM001/heartbeat` | QoS 0 | Không | Mất heartbeat đơn lẻ là chấp nhận được; QoS 0 giảm tải broker |

**Nguyên tắc QoS 1 và Idempotency ở tầng ứng dụng:**

QoS 1 đảm bảo message đến ít nhất một lần. Idempotency được xử lý ở tầng ứng dụng:

- **Phía ESP32:** Kiểm tra `commandId` trong NVS — đã xử lý thì reject với `DUPLICATE_COMMAND`.
- **Phía Backend:** Kiểm tra `commandId`/`eventId` trong DB trước khi xử lý; nếu trùng thì không thay đổi trạng thái và gửi lại `EVENT_ACK`.

---

## 12. Xử lý lệnh trùng lặp, NVS Persistence và Durable Event Outbox

### 12.1. ESP32 — NVS Flash Storage, Trạng thái bền vững và Tính toàn vẹn Flash

Khi cấu hình ESP32 với `cleanSession=true`, broker MQTT sẽ **không** lưu trữ trạng thái phiên làm việc hay các gói tin QoS 1 đang gửi dở qua các lần reboot. Do đó, **chỉ riêng MQTT QoS 1 hoặc gói tin PUBACK của Broker không thể bảo đảm Backend đã nhận và xử lý event**.

Để đảm bảo không bao giờ mất thông tin kết quả nhả hàng (đặc biệt là kết quả `UNCERTAIN`), ESP32 phải triển khai cơ chế **NVS Durable Event Outbox** với quy tắc xác nhận tầng ứng dụng.

#### 12.1.1. Cấu trúc dữ liệu lưu trong NVS Flash

```
key: "cmd_active"     → commandId của lệnh đang active (UUID v4 hoặc rỗng)
key: "cmd_state"      → ACCEPTED | EXECUTING | COMPLETED | FAILED | UNCERTAIN
key: "cmd_slot"       → slotNumber của lệnh active
key: "cmd_started"    → timestamp khi bắt đầu chạy motor
key: "cmd_orderid"    → orderId liên kết
key: "cmd_expires"    → expiresAt timestamp (epoch ms)

key: "outbox_pending" → boolean (true nếu có event chưa nhận được EVENT_ACK từ Backend)
key: "outbox_event"   → JSON string chứa toàn bộ payload của event kết quả gần nhất (chứa eventId)
```

**Phân biệt ranh giới thực thi vật lý qua `cmd_state` bền vững:**
- `ACCEPTED`: Lệnh đã được nhận, cú pháp và chữ ký hợp lệ, đã ghi nhận vào NVS, nhưng **thực thi vật lý CHẮC CHẮN CHƯA BẮT ĐẦU** (chân GPIO điều khiển motor luôn ở mức LOW/OFF).
- `EXECUTING`: Đã ghi nhận chuyển trạng thái vào NVS và chân GPIO đã hoặc đang được kích hoạt cấp điện cho motor. **Thực thi vật lý CÓ THỂ ĐÃ BẮT ĐẦU**.
- `COMPLETED`: Chu kỳ quay motor đã kết thúc trọn vẹn bình thường.
- `FAILED`: Lệnh bị từ chối hoặc thất bại do lỗi xác định.
- `UNCERTAIN`: Đang trong chu kỳ `EXECUTING` thì gặp sự cố sập nguồn/reboot; kết quả cơ học là bất định.

**Sliding window của commandId đã hoàn thành:** Lưu 20 `commandId` gần nhất đã kết thúc (`COMPLETED`, `FAILED`, `UNCERTAIN`) để loại trừ lệnh gửi lại (duplicate command).

#### 12.1.2. Tính toàn vẹn NVS và Giới hạn thực tế khi mất nguồn (Torn Writes & Power Loss Reality)

Thiết kế hệ thống không ngộ nhận hay tuyên bố Flash có tính bền vững tuyệt đối vượt quá khả năng vật lý của vi điều khiển:
- **Thực tế phần cứng và giới hạn của ESP-IDF NVS:**
  - ESP-IDF NVS lưu trữ dữ liệu dạng key-value trên các Flash sector/page 4KB, gắn mã kiểm tra CRC32 cho từng entry riêng lẻ (32 bytes).
  - NVS **không phải là hệ thống cơ sở dữ liệu có tính giao dịch ACID toàn diện** trên nhiều khóa (multi-key atomicity). Mỗi key được ghi và cập nhật độc lập.
  - Nếu mất điện xảy ra đúng vào chu kỳ ghi Flash, một "torn write" (ghi dở dang, rách bản ghi) có thể xảy ra trên entry đó. Nghiêm trọng hơn, nếu sụt áp (brownout) xảy ra trong quá trình xóa trang hoặc dọn dẹp bộ nhớ (page compaction/garbage collection), tiêu đề trang (page header) hoặc bảng trạng thái entry có thể bị lỗi, dẫn đến lỗi phân vùng (`ESP_ERR_NVS_CORRUPT_KEY_SYSTEM_DATA` hoặc `ESP_ERR_NVS_NO_FREE_PAGES`). NVS **không thể tự động phục hồi từ mọi hỏng hóc vật lý của chip Flash**.
- **Quy tắc ghi tuần tự ưu tiên an toàn (State-First Sequencing):**
  Firmware bắt buộc phải commit trạng thái mới vào NVS *trước* khi thực hiện hành động vật lý tiếp theo. Cụ thể: chuyển `cmd_state = EXECUTING` vào NVS trước khi kéo chân GPIO lên HIGH. Nhờ đó, nếu mất điện rơi đúng tích tắc này, sau khi reboot thiết bị sẽ nhận diện ở mức "có thể đã chạy" (`UNCERTAIN`), tuyệt đối không bao giờ nhận diện nhầm là "chưa chạy" để kích hoạt motor lần hai.
- **Cơ chế xử lý hỏng hóc và Nguyên tắc Fail-Safe:**
  1. Thư viện NVS tự động xác thực CRC32 khi đọc dữ liệu. Entry bị torn write sẽ bị NVS coi là không hợp lệ và trả về mã lỗi (ví dụ `ESP_ERR_NVS_NOT_FOUND` hoặc lỗi phân vùng).
  2. **Nguyên tắc an toàn tối cao khi NVS lỗi:** Trong mọi tình huống phát hiện NVS bị hỏng phân vùng, lỗi CRC, lỗi đọc Flash hoặc không thể xác minh chắc chắn trạng thái lệnh:
     - Firmware **bắt buộc khóa cứng toàn bộ chân GPIO motor ở mức LOW (OFF)**.
     - **Tuyệt đối không kích hoạt motor** khi dữ liệu trạng thái không nguyên vẹn hoặc không đáng tin cậy.
     - Chuyển thiết bị sang trạng thái lỗi đối soát, phát mã lỗi chẩn đoán phần cứng (`HARDWARE_ERROR` / `STORAGE_CORRUPTED`) trong heartbeat hoặc bản tin alert để nhân viên kỹ thuật kiểm tra và xử lý tại chỗ.

---

### 12.2. Điều kiện xóa NVS Outbox và Quy trình Startup / Reconnect sau Reboot

> **ĐIỀU KIỆN XÓA OUTBOX CHÍNH XÁC:**
> - ESP32 **CHỈ ĐƯỢC XÓA** event khỏi NVS Outbox (`outbox_pending = false`, xóa `outbox_event`) khi và chỉ khi **nhận được bản tin `EVENT_ACK` từ Backend trên topic `vending/{machineId}/events/ack` có `eventId` khớp chính xác với `eventId` đang lưu trong Outbox (`ack.eventId == nvs.outbox_event.eventId`)**.
> - Nếu `ack.eventId` nhận được không khớp với `eventId` trong Outbox (ví dụ ACK của một event cũ đã xong), ESP32 **bỏ qua và giữ nguyên Outbox**.
> - Gói tin MQTT `PUBACK` từ Broker **chỉ là biên nhận tầng mạng** và **TUYỆT ĐỐI KHÔNG** được dùng làm căn cứ để xóa Outbox.
> - Chừng nào chưa nhận được `EVENT_ACK` khớp, event **bắt buộc phải được giữ nguyên vẹn trong Flash NVS qua mọi lần reboot** và được republish sau mỗi lần kết nối lại MQTT (hoặc theo timer retry).

**Sơ đồ quy trình Startup / Reconnect:**

```
Boot / Reconnect
  │
  ├── 0. Khởi tạo GPIO an toàn: Tất cả GPIO điều khiển motor lập tức gán LOW (OFF).
  │
  ├── 1. Kiểm tra NVS Outbox: outbox_pending == true?
  │   ├── CÓ: Tồn tại event chưa nhận được EVENT_ACK từ Backend
  │   │   → Kết nối MQTT broker, subscribe vending/{machineId}/events/ack
  │   │   → Republish chính xác payload từ "outbox_event" (giữ nguyên eventId và commandId gốc) lên topic vending/{machineId}/events (QoS 1)
  │   │   → Khi nhận được EVENT_ACK từ Backend có matching eventId:
  │   │   │   → Cập nhật NVS: outbox_pending = false, xóa outbox_event
  │   └── KHÔNG: Không có event tồn đọng
  │
  ├── 2. Đọc NVS "cmd_active" & "cmd_state":
  │   │
  │   ├── cmd_state == EXECUTING?
  │   │   → ESP32 bị sập nguồn / reboot trong lúc motor đang quay hoặc vừa quay xong chưa kịp lưu COMPLETED
  │   │   → Thực thi vật lý CÓ THỂ ĐÃ BẮT ĐẦU: Kết quả vật lý là hoàn toàn BẤT ĐỊNH (UNCERTAIN)
  │   │   → Chuyển sang trạng thái đối soát: Cập nhật NVS: cmd_state = UNCERTAIN
  │   │   → Tạo payload DISPENSE_RESULT với result="UNCERTAIN", failureCode="REBOOT_DURING_EXECUTION"
  │   │   → Lưu payload vào "outbox_event", set "outbox_pending = true"
  │   │   → Publish event lên MQTT (QoS 1) và chờ EVENT_ACK từ Backend để xóa outbox
  │   │   → TUYỆT ĐỐI KHÔNG BAO GIỜ TỰ ĐỘNG KÍCH HOẠT LẠI MOTOR
  │   │
  │   ├── cmd_state == ACCEPTED?
  │   │   → Lệnh đã được lưu bền vững, nhưng thực thi vật lý CHẮC CHẮN CHƯA BẮT ĐẦU (GPIO luôn LOW)
  │   │   → TUYỆT ĐỐI KHÔNG TỰ ĐỘNG HỦY BỎ LỆNH CHỈ VÌ CHƯA CÓ ĐỒNG HỒ NGAY LÚC BOOT
  │   │   → TUYỆT ĐỐI KHÔNG KÍCH HOẠT MOTOR KHI CHƯA XÁC MINH ĐƯỢC EXPIRESAT QUA ĐỒNG HỒ TIN CẬY
  │   │   → ESP32 giữ motor ở mức LOW (OFF), kết nối Wi-Fi và tiến hành đồng bộ SNTP (chờ tối đa 10–15s)
  │   │   │
  │   │   ├── Đồng bộ SNTP thành công và đạt tiêu chuẩn Clock Trust (§13.1):
  │   │   │   → Kiểm tra lại trạng thái NVS: cmd_state vẫn là ACCEPTED
  │   │   │   → So sánh thời gian thực tin cậy với cmd_expires (expiresAt):
  │   │   │   │
  │   │   │   ├── currentTime < cmd_expires (Lệnh VẪN CÒN HẠN & an toàn để thực thi):
  │   │   │   │   → Cập nhật NVS: cmd_state = EXECUTING, ghi cmd_started
  │   │   │   │   → KÍCH HOẠT MOTOR an toàn theo đúng thông số lệnh gốc
  │   │   │   │   → Hoàn thành chu kỳ: NVS cmd_state = COMPLETED, lưu DISPENSE_RESULT(SUCCESS) vào Outbox, publish MQTT
  │   │   │   │
  │   │   │   └── currentTime >= cmd_expires (Lệnh ĐÃ HẾT HẠN trong quá trình sập nguồn/boot):
  │   │   │       → Cập nhật NVS: cmd_state = FAILED
  │   │   │       → Tạo DISPENSE_RESULT(FAILURE, failureCode="COMMAND_EXPIRED_AFTER_REBOOT") hoặc COMMAND_REJECTED
  │   │   │       → Lưu Outbox, publish MQTT, dọn cmd_active
  │   │   │       → TUYỆT ĐỐI KHÔNG KÍCH HOẠT MOTOR
  │   │   │
  │   │   └── Hết thời gian chờ mà KHÔNG đạt Clock Trust (mạng rớt hoặc NTP lỗi):
  │   │       → Không thể xác minh hạn lệnh bằng đồng hồ tin cậy ➔ TUYỆT ĐỐI KHÔNG CHẠY MOTOR
  │   │       → Cập nhật NVS: cmd_state = FAILED
  │   │       → Tạo DISPENSE_RESULT(FAILURE, failureCode="CLOCK_SYNC_TIMEOUT_AFTER_REBOOT"), lưu Outbox
  │   │       → Dọn cmd_active, đưa máy về trạng thái IDLE
  │   │
  │   ├── cmd_state == COMPLETED / FAILED / UNCERTAIN?
  │   │   → Quá trình nhả hàng đã kết thúc trước đó. Nếu outbox_pending đã false thì không cần làm gì thêm.
  │   │
  │   └── NVS rỗng / cmd_state == IDLE?
  │       → Khởi động bình thường, tất cả chân điều khiển motor ở mức LOW (OFF), sẵn sàng nhận lệnh mới.
```

---

### 12.3. Phân tích các tình huống mất điện và xử lý khi mất Acknowledgment

#### 12.3.1. Phân tích 4 tình huống mất điện điển hình

| Tình huống mất điện | Trạng thái tại thời điểm mất điện | Hành vi của ESP32 sau khi có điện lại | Hành vi của Backend |
|---|---|---|---|
| **1. Trước khi lưu NVS** | Gói tin lệnh vừa tới bộ đệm RAM của Wi-Fi, chưa kịp ghi Flash NVS. | NVS hoàn toàn rỗng. ESP32 khởi động IDLE bình thường, motor không quay. | Backend chờ hết timeout T1 (không nhận được `COMMAND_ACCEPTED`) ➔ chuyển đơn hàng sang `NEEDS_RECONCILIATION`. Tuyệt đối an toàn. |
| **2. Sau khi lưu NVS `ACCEPTED` nhưng trước khi chạy motor** | NVS đã ghi `cmd_state = ACCEPTED`, chân GPIO vẫn ở mức LOW (chưa cấp điện motor). | ESP32 đọc NVS thấy `ACCEPTED`. Không tự ý hủy lệnh ngay. Đợi SNTP đạt Clock Trust. Nếu lệnh còn hạn (`now < expiresAt`), chuyển sang `EXECUTING` và quay motor an toàn. Nếu hết hạn hoặc SNTP timeout: báo `COMMAND_EXPIRED_AFTER_REBOOT` hoặc `CLOCK_SYNC_TIMEOUT`, motor không quay. | Nếu ESP32 chạy motor thành công ➔ nhận `SUCCESS` ➔ order `COMPLETED`. Nếu hết hạn/lỗi clock ➔ nhận failure/timeout ➔ order `NEEDS_RECONCILIATION`. Không có nguy cơ quay motor mù. |
| **3. Khi motor đang quay (`EXECUTING`)** | Motor đang nhận điện và quay dở hành trình thì mất nguồn. Không rõ sản phẩm đã rơi khỏi lò xo chưa. | ESP32 khởi động lại, đọc NVS thấy `cmd_state = EXECUTING`. Chuyển ngay sang `UNCERTAIN`, lưu `outbox_event`, set `outbox_pending = true`. Publish event `UNCERTAIN` với `failureCode = REBOOT_DURING_EXECUTION`. Tuyệt đối không bật lại motor. | Backend nhận `UNCERTAIN` ➔ lập tức chuyển order sang `NEEDS_RECONCILIATION`, commit DB và gửi lại `EVENT_ACK`. Chờ operator kiểm tra cơ học tại máy. |
| **4. Sau khi quay xong motor nhưng trước khi nhận EVENT_ACK** | Motor đã hoàn thành chu kỳ, NVS đã lưu `outbox_event` với kết quả `SUCCESS`, `outbox_pending = true`, nhưng mạng rớt hoặc reboot trước khi nhận `EVENT_ACK`. | ESP32 khởi động lại, kiểm tra thấy `outbox_pending == true`. ESP32 kết nối broker và republish chính xác event `SUCCESS` (giữ nguyên `eventId` và `commandId`). | Backend nhận event `SUCCESS` ➔ nếu chưa xử lý thì commit DB; nếu đã commit rồi thì bỏ qua chuyển trạng thái, nhưng **LUÔN GỬI LẠI `EVENT_ACK`**. |

#### 12.3.2. Hành vi khi bản tin EVENT_ACK bị thất lạc trên mạng hoặc Backend Crash sau Commit

1. **Backend Crash sau Commit:** Backend nhận event, ghi vào bảng `device_events` và commit transaction cập nhật đơn hàng thành công, nhưng tiến trình Backend bị crash ngay trước khi phát bản tin `EVENT_ACK` ra broker.
2. ESP32 không nhận được `EVENT_ACK` nên `outbox_pending` vẫn là `true` trong NVS Flash.
3. Sau khi kết nối lại hoặc hết retry timer (15 giây), ESP32 republish chính xác bản tin event đó (giữ nguyên `eventId` và `commandId`) lên topic `vending/{machineId}/events`.
4. **Xử lý Idempotent tại Backend:**
   - Backend truy vấn cơ sở dữ liệu và phát hiện `eventId` (hoặc `commandId`) đã tồn tại trong `device_events`.
   - Backend **bỏ qua hoàn toàn việc áp dụng hiệu ứng nghiệp vụ** (không thay đổi trạng thái đơn hàng lần 2, không trừ kho lần 2, không gọi webhook ra ngoài lần 2).
   - **BẮT BUỘC:** Backend **phải gửi lại bản tin `EVENT_ACK`** (chứa matching `eventId`) về topic `vending/{machineId}/events/ack`.
5. ESP32 nhận được `EVENT_ACK`, kiểm tra `ack.eventId == nvs.outbox_event.eventId`, và xóa event khỏi NVS Outbox.

#### 12.3.3. Sập nguồn hoặc reboot liên tiếp nhiều lần (Repeated Reboots)

- Nếu ESP32 bị mất điện liên tục trong khi đang khởi động hoặc đang cố gửi event `UNCERTAIN`, dữ liệu trong Flash NVS (`outbox_pending = true`, `outbox_event`) không bị mất đi nhờ tính bền vững của bộ nhớ Flash.
- Trong mọi lần boot, firmware luôn kiểm tra NVS Outbox trước tiên. Event chưa gửi thành công sẽ tiếp tục được giữ và republish cho đến khi nhận được xác nhận `EVENT_ACK` từ Backend.

---

### 12.4. Backend — Idempotency và Tiếp nhận Event trùng lặp

Do cơ chế QoS 1 redelivery và cơ chế Outbox tự gửi lại của ESP32, Backend có thể nhận cùng một event nhiều lần. Backend phải đảm bảo:

1. **Khớp lệnh:** Kiểm tra `commandId` và `eventId` trong bảng `dispense_commands` và `device_events`.
2. **Idempotent State Transition:**
   - Nếu đơn hàng đã chuyển sang trạng thái tương ứng (`COMPLETED`, `DISPENSE_FAILED`, hoặc `NEEDS_RECONCILIATION`), việc nhận lại event trùng lặp **không được kích hoạt thay đổi trạng thái lần thứ hai**, không kích hoạt trừ tồn kho lần hai, và không gửi thông báo trùng lặp.
   - Ghi nhận mọi event nhận được vào `device_events` để phục vụ audit/truy vết.
   - **Luôn gửi lại `EVENT_ACK`** tương ứng với `eventId` đã nhận.
3. **An toàn vật lý:** Vì motor vật lý trên ESP32 chỉ kích hoạt tối đa một lần cho mỗi `commandId`, việc nhận lại event chỉ là đồng bộ trạng thái ở tầng phần mềm, hoàn toàn không có rủi ro nhả thêm hàng.

---

## 13. Nguồn thời gian của ESP32, Hết hạn lệnh và Xử lý Timeout

### 13.1. Nguồn thời gian của ESP32, Tiêu chuẩn Đồng hồ Tin cậy (Clock Trust) và Quy tắc an toàn

1. **Nguồn thời gian phần cứng:** ESP32 không có pin nuôi RTC độc lập. Khi mất nguồn hoàn toàn, đồng hồ phần cứng nội bộ bị reset về mốc epoch 0 (1970-01-01T00:00:00Z). Thiết bị lấy thời gian thực UTC thông qua giao thức SNTP (`pool.ntp.org` hoặc NTP server chỉ định) ngay sau khi có kết nối Wi-Fi.
2. **Tiêu chuẩn công nhận Đồng hồ Tin cậy ở tầng ứng dụng (Application-level Clock Trust):**
   - **Giới hạn API của ESP-IDF lwIP SNTP:**
     > **Lưu ý kiến trúc quan trọng:** Stack lwIP SNTP trong ESP-IDF xử lý và kiểm tra tính hợp lệ của gói tin NTP ở tầng socket nội bộ (loại bỏ gói tin lỗi cú pháp, kiểm tra phản hồi từ máy chủ). Tuy nhiên, **ESP-IDF SNTP API tiêu chuẩn (`esp_sntp.h`) KHÔNG export các trường Stratum hay Leap Indicator ra cho tầng ứng dụng**. Do đó, firmware ứng dụng không thể và không giả định có thể truy cập trực tiếp các trường này thông qua API chuẩn của ESP-IDF mà không sửa đổi mã nguồn thư viện hệ thống.
   - **Các kiểm tra Clock Trust khả thi và có thể triển khai:**
     Đồng hồ ESP32 **CHỈ ĐƯỢC COI LÀ TIN CẬY** (`clock_trusted == true`) khi thỏa mãn **ĐỒNG THỜI** các điều kiện sau:
     1. **Trạng thái SNTP Engine:** Hàm API `sntp_get_sync_status() == SNTP_SYNC_STATUS_COMPLETED` (hoặc notification callback được kích hoạt qua `esp_sntp_set_time_sync_notification_cb`), xác nhận stack SNTP đã nhận gói phản hồi hợp lệ từ máy chủ NTP và đã hiệu chỉnh đồng hồ hệ thống.
     2. **Kiểm tra tính hợp lý tối thiểu (Build-time Sanity Check):** Thời gian UTC hệ thống sau đồng bộ phải thỏa mãn `currentTime >= BUILD_TIMESTAMP` (thời điểm biên dịch firmware, bảo đảm không lùi về 1970 hay các mốc thời gian quá khứ trước khi phần mềm được tạo ra) và không vượt quá một mốc tương lai phi lý (ví dụ: `BUILD_TIMESTAMP + 10 năm`).
     3. **Ý nghĩa của `BUILD_TIMESTAMP`:**
        > `BUILD_TIMESTAMP` **chỉ là một phép kiểm tra hợp lý tối thiểu (sanity check)** để phát hiện đồng hồ chưa đồng bộ (vẫn ở epoch 0 hoặc các năm trước khi thiết bị ra đời) hoặc thanh ghi RTC bị rác sau brownout. Nó **KHÔNG PHẢI là bằng chứng khẳng định đồng hồ chính xác tuyệt đối**.
     4. **QUY TẮC BẮT BUỘC VỀ NĂM HỆ THỐNG:**
        > **Chỉ kiểm tra năm (`year >= 2024`) KHÔNG ĐƯỢC COI là bằng chứng hợp lệ của việc đồng hồ đã đồng bộ!**
        > Sau sự cố sụt áp (brownout), reset bộ nhớ hoặc thanh ghi RTC RAM chưa khởi tạo, các ô nhớ có thể chứa giá trị rác ngẫu nhiên vẫn thỏa mãn điều kiện `year >= 2024`. Do đó, kiểm tra năm đơn thuần là không đủ tin cậy và không thể thay thế cho việc xác thực trạng thái hoàn tất của SNTP engine.
3. **Xử lý khi đồng hồ chưa tin cậy hoặc không hợp lệ (`clock_trusted == false`):**
   - **NGUYÊN TẮC AN TOÀN TỐI CAO:** ESP32 **TUYỆT ĐỐI KHÔNG ĐƯỢC PHÉP KÍCH HOẠT BẤT KỲ MOTOR NÀO** khi thời hạn lệnh (`expiresAt`) không thể xác minh bằng một đồng hồ tin cậy.
   - Khi nhận lệnh mới qua MQTT hoặc khi kiểm tra lệnh `ACCEPTED` tồn đọng sau boot:
     - ESP32 giữ nguyên motor ở mức LOW (OFF).
     - Cho phép một khoảng thời gian chờ có giới hạn (grace period: 5–10 giây cho lệnh mới, 10–15 giây sau reboot) để SNTP hoàn tất đồng bộ.
     - Nếu đạt tiêu chuẩn Clock Trust trong thời gian chờ: tiến hành kiểm tra `expiresAt` và xử lý tiếp.
     - Nếu hết thời gian chờ mà đồng hồ vẫn chưa tin cậy: từ chối lệnh với mã lỗi `CLOCK_NOT_SYNCHRONIZED` hoặc `CLOCK_SYNC_TIMEOUT`. Motor tuyệt đối không được cấp điện.
   - **Chống tự động retry vật lý:** Lệnh bị từ chối do lỗi đồng hồ tuyệt đối không được tự ý phát lại dưới dạng lệnh vật lý mới.

---

### 13.2. Phân biệt Message Retransmission vs. Physical Dispensing Retry

Đây là nguyên tắc an toàn quan trọng nhất của hệ thống:

| Hành động | Định nghĩa | Cho phép? | Điều kiện & Quy tắc |
|---|---|:---:|---|
| **Truyền tải lại gói tin MQTT (Message Retransmission)** | Gửi lại gói tin MQTT chứa lệnh nhả hàng ở tầng mạng khi nghi ngờ rớt gói. | **Hạn chế** | - **BẮT BUỘC giữ nguyên `commandId` duy nhất ban đầu**.<br>- Chỉ được gửi khi `expiresAt` chưa hết hạn.<br>- ESP32 dùng NVS để chặn kích hoạt motor lần hai nếu lệnh cũ đã từng chạy. |
| **Kích hoạt lại motor vật lý (Physical Dispensing Retry)** | Tạo ra lệnh mới để quay motor thêm một lần nữa cho đơn hàng chưa rõ kết quả. | ❌ **CẤM TỰ ĐỘNG** | - **Tuyệt đối không tự động phát sinh `commandId` mới** để retry khi chưa xác minh được trạng thái vật lý của máy.<br>- Mọi thao tác quay lại motor phải do Quản trị viên thực hiện sau khi đối soát thực địa. |

---

### 13.3. Kiểm tra thời hạn lệnh (`expiresAt`) đa tầng trên ESP32

Để ngăn chặn tuyệt đối việc motor kích hoạt trễ khi khách hàng đã rời khỏi máy:

1. **Kiểm tra tầng 1 (Ngay khi nhận lệnh từ MQTT hoặc sau khi khôi phục từ boot):**
   - Kiểm tra tiêu chuẩn `clock_trusted == true`. Nếu chưa tin cậy: chờ tối đa grace period; nếu vẫn thất bại ➔ từ chối với `CLOCK_NOT_SYNCHRONIZED` hoặc `CLOCK_SYNC_TIMEOUT`.
   - So sánh `currentTime >= expiresAt`. Nếu đã hết hạn: từ chối ngay lập tức, publish `COMMAND_REJECTED` với `reason: "COMMAND_EXPIRED"`, không ghi nhận vào trạng thái active của NVS.
2. **Kiểm tra tầng 2 (Ngay trước khi kích hoạt chân GPIO điều khiển motor):**
   - Sau khi hoàn thành các bước ghi NVS, ngay trước khi cấp xung điện cho motor driver, ESP32 kiểm tra lại `clock_trusted == true` và `currentTime >= expiresAt` một lần nữa.
   - Nếu đã quá hạn trong quá trình chuẩn bị: hủy kích hoạt motor, cập nhật NVS `cmd_state = FAILED`, publish `DISPENSE_RESULT` với `failureCode = "COMMAND_EXPIRED_BEFORE_EXECUTION"`.

---

### 13.4. Quy trình Timeout trên Backend và nguyên tắc không diễn giải sai

```
T=0:    Backend publish command lên MQTT
T=T1:   Chờ COMMAND_ACCEPTED (T1 = 30 giây đề xuất — TBD)
  │
  ├── Nhận ACCEPTED → tiếp tục chờ DISPENSE_RESULT
  │   T=T1+T2: Chờ DISPENSE_RESULT (T2 = 60 giây đề xuất — TBD)
  │     │
  │     ├── Nhận SUCCESS → commit DB → order → COMPLETED (trừ tồn kho) → gửi EVENT_ACK
  │     ├── Nhận FAILURE → commit DB → order → DISPENSE_FAILED (giải phóng giữ chỗ tồn kho) → gửi EVENT_ACK
  │     ├── Nhận UNCERTAIN → commit DB → order → NEEDS_RECONCILIATION (giữ nguyên tồn kho chờ duyệt) → gửi EVENT_ACK
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

### 13.5. ESP32 khi mất kết nối MQTT

1. Tất cả motor phải dừng ngay nếu đang không trong chu kỳ thực thi (không cấp điện liên tục).
2. Nếu mất kết nối khi đang `EXECUTING`: tiếp tục chạy cho hết chu kỳ thời gian `motorRunMs` (để tránh kẹt cơ giữa chừng), cập nhật NVS kết quả, lưu vào outbox và republish DISPENSE_RESULT ngay sau khi reconnect và nhận được kết nối MQTT.
3. Sau khi reconnect, với `cleanSession=true`: ESP32 không nhận lại command cũ bị tích lũy trên broker.
4. Publish heartbeat ngay sau khi reconnect thành công.
5. Kiểm tra NVS Outbox (§12.2) ngay sau khi reconnect để gửi bất kỳ event nào chưa nhận được `EVENT_ACK` từ Backend.

### 13.6. Backend khi mất kết nối MQTT

1. Backend phải có cơ chế reconnect tự động và tiếp tục xử lý pending orders sau khi kết nối lại thành công.
2. Với `cleanSession=false` cho backend: Sau khi reconnect, backend nhận lại các QoS 1 events từ ESP32 mà broker đã buffer trong thời gian backend gián đoạn.
3. Backend xử lý các events này theo quy tắc idempotency (§12.4) — không tạo duplicate state transition hay trừ tồn kho trùng lặp, và luôn gửi lại `EVENT_ACK` cho ESP32.

---

## 14. Ví dụ luồng đầy đủ — Happy path

```
T=0s     Backend xác minh webhook thanh toán → ghi vào payment_transactions
T=0.1s   Backend ghi dispense_command vào DB (status=PENDING)
T=0.2s   Backend publish command vào vending/VM001/commands (QoS 1)
T=0.5s   ESP32 nhận command, kiểm tra time_synced=true và expiresAt hợp lệ
T=0.5s   ESP32 ghi NVS: cmd_state=ACCEPTED
T=0.6s   ESP32 publish COMMAND_ACCEPTED
T=0.7s   Backend nhận COMMAND_ACCEPTED → cập nhật dispense_command status=ACKNOWLEDGED
T=0.7s   Backend chuyển order → DISPENSING
T=0.8s   ESP32 ghi NVS: cmd_state=EXECUTING, motorStartedAt=...
T=0.8s   ESP32 kích hoạt motor slot 2
T=3.6s   ESP32 dừng motor (sau motorRunMs)
T=3.6s   ESP32 ghi NVS: cmd_state=COMPLETED, outbox_event=DISPENSE_RESULT(SUCCESS), outbox_pending=true
T=3.7s   ESP32 publish DISPENSE_RESULT (SUCCESS, MOTOR_CYCLE_ONLY) vào vending/VM001/events (QoS 1)
T=3.8s   Backend nhận DISPENSE_RESULT → ghi device_events và chuyển order → COMPLETED, trừ tồn kho trong DB transaction
T=3.9s   DB commit thành công
T=3.95s  Backend publish EVENT_ACK vào vending/VM001/events/ack (QoS 1)
T=4.0s   ESP32 nhận EVENT_ACK → cập nhật NVS: outbox_pending=false, xóa outbox_event
T=4.0s   Frontend polling nhận status COMPLETED
T=4.1s   Hiển thị thông báo cho khách hàng
```

## 15. Ví dụ luồng xử lý Reboot

### 15.1. Ví dụ luồng — Reboot trong khi EXECUTING (Thực thi vật lý có thể đã bắt đầu)

```
T=0s     Backend publish command
T=0.5s   ESP32 nhận, kiểm tra Clock Trust và expiresAt, ghi NVS: state=ACCEPTED
T=0.6s   ESP32 publish COMMAND_ACCEPTED
T=0.8s   ESP32 ghi NVS: state=EXECUTING, bắt đầu chạy motor
T=1.5s   ESP32 mất điện / reboot (motor ngắt điện giữa chừng, kết quả vật lý bất định)
--- reboot ---
T=10s    ESP32 boot: tất cả GPIO motor lập tức gán LOW (OFF)
T=10.1s  ESP32 đọc NVS: state=EXECUTING → nhận diện thực thi vật lý có thể đã diễn ra
T=10.2s  ESP32 ghi NVS: state=UNCERTAIN, outbox_event=DISPENSE_RESULT(UNCERTAIN), outbox_pending=true
T=10.2s  TUYỆT ĐỐI KHÔNG BẬT LẠI MOTOR
T=10.5s  ESP32 kết nối Wi-Fi & MQTT broker, subscribe commands và events/ack, publish heartbeat
T=10.6s  ESP32 publish DISPENSE_RESULT(result=UNCERTAIN, REBOOT_DURING_EXECUTION) vào vending/VM001/events (QoS 1)
T=10.7s  Backend nhận → ghi device_events, chuyển order → NEEDS_RECONCILIATION, commit DB
T=10.75s Backend publish EVENT_ACK(matching eventId) vào vending/VM001/events/ack (QoS 1)
T=10.8s  ESP32 nhận EVENT_ACK → kiểm tra matching eventId → cập nhật NVS: outbox_pending=false, xóa outbox_event
T=10.8s  Frontend nhận status NEEDS_RECONCILIATION + thông báo an toàn cho khách hàng
[Quản trị viên đối soát vật lý thực địa tại máy để xử lý thủ công]
```

### 15.2. Ví dụ luồng — Reboot trong khi ACCEPTED (Lệnh chắc chắn chưa chạy vật lý)

```
T=0s     Backend publish command (expiresAt = T + 45s)
T=0.5s   ESP32 nhận, Clock Trust OK, ghi NVS: state=ACCEPTED (GPIO motor vẫn LOW)
T=0.6s   ESP32 mất điện / sập nguồn trước khi bắt đầu chu kỳ motor
--- reboot ---
T=8s     ESP32 boot: tất cả GPIO motor lập tức gán LOW (OFF)
T=8.1s   ESP32 đọc NVS: state=ACCEPTED → phân biệt chắc chắn motor chưa từng chạy
T=8.2s   ESP32 KHÔNG hủy lệnh ngay; giữ motor LOW, kết nối Wi-Fi và yêu cầu SNTP sync
T=10s    SNTP sync hoàn tất, đạt tiêu chuẩn Clock Trust (status COMPLETED, sanity check now >= BUILD_TIMESTAMP)
T=10.1s  ESP32 kiểm tra lại NVS (state=ACCEPTED) và so sánh: currentTime < expiresAt (vẫn còn hạn 35s)
T=10.2s  ESP32 ghi NVS: state=EXECUTING, cmd_started=now
T=10.3s  ESP32 kích hoạt motor an toàn
T=13.1s  ESP32 kết thúc chu kỳ motor, ghi NVS: state=COMPLETED, outbox_event=DISPENSE_RESULT(SUCCESS), outbox_pending=true
T=13.2s  ESP32 publish DISPENSE_RESULT(SUCCESS) lên vending/VM001/events (QoS 1)
T=13.3s  Backend nhận → ghi device_events, chuyển order → COMPLETED, trừ kho, commit DB
T=13.35s Backend publish EVENT_ACK vào vending/VM001/events/ack (QoS 1)
T=13.4s  ESP32 nhận EVENT_ACK → xóa matching eventId khỏi Outbox
```

*(Lưu ý: Nếu tại T=10.1s phát hiện `currentTime >= expiresAt`, hoặc nếu SNTP timeout sau 15s mà không đạt Clock Trust: ESP32 tuyệt đối không chạy motor, cập nhật NVS `state=FAILED`, lưu lỗi `COMMAND_EXPIRED_AFTER_REBOOT` hoặc `CLOCK_SYNC_TIMEOUT_AFTER_REBOOT` vào Outbox để báo về Backend).*

---

## 16. Ánh xạ Slot ↔ GPIO (TBD)

Ánh xạ giữa `slotNumber` (1, 2, 3) và chân GPIO chưa được xác định vì phần cứng chưa thiết kế.

> **TBD — Giai đoạn 6:** GPIO mapping sẽ được ghi vào đây sau khi chọn board ESP32, thiết kế sơ đồ kết nối với motor driver và kiểm tra điện áp không xung đột Wi-Fi/UART.

| `slotNumber` | GPIO | Mô tả |
|---|---|---|
| 1 | TBD | Kết nối Driver slot 1 |
| 2 | TBD | Kết nối Driver slot 2 |
| 3 | TBD | Kết nối Driver slot 3 |

> **Cảnh báo:** Không hardcode GPIO trong firmware cho đến khi sơ đồ điện được kiểm tra thực tế. Sử dụng constant trong file config riêng.

---

## 17. Các mục TBD cần xác nhận

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

## 18. Điều không được làm

| Bên | Điều cấm |
|---|---|
| **Backend** | Publish lệnh khi chưa xác minh giao dịch ngân hàng thật |
| **Backend** | Publish lệnh khi chưa ghi nhận bản ghi vào DB |
| **Backend** | Tự động phát lại lệnh nhả hàng sau timeout |
| **Backend** | Tự động tạo `commandId` mới để retry đơn hàng có kết quả bất định |
| **Backend** | Gửi `EVENT_ACK` trước khi hoàn tất commit transaction vào database |
| **Backend** | Xem MQTT delivery thành công là bằng chứng sản phẩm đã nhả |
| **Backend** | Xem `MOTOR_CYCLE_ONLY` là bảo đảm sản phẩm đã rơi |
| **ESP32** | Kích hoạt motor khi không có lệnh hợp lệ |
| **ESP32** | Kích hoạt motor khi đồng hồ chưa đạt tiêu chuẩn Clock Trust qua SNTP (§13.1) |
| **ESP32** | Xử lý lệnh đã hết hạn (`expiresAt` đã qua) |
| **ESP32** | Xử lý lệnh trùng `commandId` (kiểm tra NVS) |
| **ESP32** | Xóa NVS Outbox khi chưa nhận được `EVENT_ACK` có matching `eventId` từ Backend |
| **ESP32** | Tự động vứt bỏ lệnh `ACCEPTED` sau reboot khi chưa thực hiện chờ đồng bộ giờ tin cậy |
| **ESP32** | Tự ý kích hoạt motor sau reboot khi chưa xác minh được tính hợp lệ và thời hạn lệnh bằng đồng hồ tin cậy |
| **ESP32** | Tự động kích hoạt lại motor khi lệnh trước đó ở trạng thái `EXECUTING` (phải chuyển sang `UNCERTAIN`) |
| **ESP32** | Chạy motor vượt quá `motorRunMs` mà không có lý do bảo vệ |
| **ESP32** | Dùng `verificationLevel=SENSOR_CONFIRMED` khi không có cảm biến |
| **Frontend** | Kết nối trực tiếp tới MQTT broker |
| **Frontend** | Gửi lệnh nhả hàng theo bất kỳ kênh nào |
