# MQTT Contract — Vending Machine Prototype

**Phiên bản:** 1.0  
**Trạng thái:** Draft — Giai đoạn 1 (chưa triển khai)  
**Nguồn yêu cầu:** `PRODUCT_SPEC.md §13`, `PRODUCT_SPEC.md §14`  
**Cập nhật lần cuối:** 2026-10-18

---

## 1. Tổng quan

Backend Java và ESP32 giao tiếp qua MQTT. Backend là nơi duy nhất có quyền phát lệnh nhả hàng. ESP32 chỉ thực thi lệnh hợp lệ và báo cáo kết quả.

```
BACKEND (Publisher)          MQTT BROKER (TLS)           ESP32 (Subscriber)
     │                             │                             │
     │── vending/VM001/commands ──►│──── vending/VM001/commands ►│
     │                             │                             │
     │◄── vending/VM001/events ────│◄─── vending/VM001/events ──│
     │                             │                             │
     │◄── vending/VM001/heartbeat ─│◄── vending/VM001/heartbeat ─│
```

**Nguyên tắc cốt lõi:**
- MQTT QoS 1 (at-least-once) được sử dụng cho commands và events.
- ESP32 tự bảo vệ khỏi lệnh trùng lặp bằng cách ghi nhớ `commandId` đã xử lý.
- Giao dịch ngân hàng được xác minh trước khi bất kỳ lệnh MQTT nào được phát ra.
- Không có frontend hay thiết bị nào khác được phép publish vào topic `commands`.

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
| Clean session | `true` cho ESP32, `false` cho backend (để nhận message khi offline) — TBD |

> **TBD:** Lựa chọn broker cụ thể, hostname, clean session policy và MQTT version sẽ được chốt ở Giai đoạn 6.

### 2.2. Xác thực

Tất cả client phải xác thực bằng username/password. Không cho phép kết nối ẩn danh.

| Client | Username | Quyền |
|---|---|---|
| Backend Java | `backend-vm001` (ví dụ) | Publish: `commands`; Subscribe: `events`, `heartbeat` |
| ESP32 VM001 | `esp32-vm001` (ví dụ) | Subscribe: `commands`; Publish: `events`, `heartbeat` |

> **Quy tắc ACL bắt buộc:** Broker phải cấu hình ACL (Access Control List) để:
> - ESP32 không thể publish vào topic `commands`.
> - Backend không thể subscribe wildcard không cần thiết.
> - Không có client nào được truy cập topic của machine khác.

---

## 3. Topic convention

Sử dụng `{machineId}` để namespace theo từng máy. MVP chỉ có một máy `VM001`.

| Topic | Hướng | Mục đích |
|---|---|---|
| `vending/VM001/commands` | Backend → ESP32 | Backend gửi lệnh nhả hàng |
| `vending/VM001/events` | ESP32 → Backend | ESP32 gửi kết quả xác nhận và kết quả nhả hàng |
| `vending/VM001/heartbeat` | ESP32 → Backend | ESP32 báo cáo trạng thái định kỳ |

**Quy tắc:**
- Không dùng retained message cho topic `commands` (lệnh nhả hàng không được lưu lại).
- Không dùng wildcard subscribe trừ khi được cấu hình rõ ràng và hạn chế.

---

## 4. Payload — Lệnh nhả hàng (Command)

Backend publish vào `vending/VM001/commands` khi đơn hàng đã được xác minh thanh toán.

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
| `commandId` | string (UUID v4) | ✅ | ID duy nhất của lệnh — dùng để chống trùng lặp |
| `machineId` | string | ✅ | Phải khớp với machine ID của ESP32. ESP32 phải từ chối nếu không khớp. |
| `orderId` | string (UUID v4) | ✅ | ID đơn hàng liên kết — dùng để ESP32 tham chiếu trong event phản hồi |
| `slotNumber` | integer (1-3) | ✅ | Khay vật lý cần kích hoạt. Phải là 1, 2 hoặc 3. |
| `action` | string | ✅ | Hành động yêu cầu. MVP chỉ có `"DISPENSE"`. |
| `createdAt` | string (ISO 8601 UTC) | ✅ | Thời điểm lệnh được tạo |
| `expiresAt` | string (ISO 8601 UTC) | ✅ | Lệnh hết hạn sau thời điểm này — ESP32 phải từ chối nếu quá hạn |
| `parameters.motorRunMs` | integer | ✅ | Thời gian chạy động cơ tối đa (ms). **Giá trị sẽ được chốt sau khi kiểm thử phần cứng thực tế.** |

### 4.3. Quy tắc xác thực trên ESP32

ESP32 phải từ chối lệnh và publish event `COMMAND_REJECTED` nếu:

1. JSON không parse được.
2. Thiếu bất kỳ trường bắt buộc nào.
3. `machineId` không khớp với machine ID của thiết bị.
4. `slotNumber` không nằm trong phạm vi 1–3.
5. `action` không phải `"DISPENSE"`.
6. `expiresAt` < thời điểm hiện tại của thiết bị (lệnh đã hết hạn).
7. `commandId` đã được xử lý trước đó (lệnh trùng lặp).

> **Cảnh báo:** ESP32 không được phép suy ra slot từ bất kỳ giá trị nào ngoài trường `slotNumber` đã được kiểm tra. Không được hardcode mapping slot trong firmware nếu đã có trong payload.

---

## 5. Payload — Xác nhận nhận lệnh (Command Accepted)

ESP32 publish vào `vending/VM001/events` ngay khi nhận và xác nhận lệnh hợp lệ, trước khi kích hoạt động cơ.

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

## 6. Payload — Từ chối lệnh (Command Rejected)

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
| `DUPLICATE_COMMAND` | `commandId` đã được xử lý trước đó |
| `INVALID_MACHINE` | `machineId` không khớp |
| `INVALID_SLOT` | `slotNumber` nằm ngoài phạm vi hợp lệ |
| `INVALID_PAYLOAD` | JSON không hợp lệ hoặc thiếu trường bắt buộc |
| `UNKNOWN_ACTION` | `action` không được hỗ trợ |

---

## 7. Payload — Kết quả nhả hàng (Dispense Result)

ESP32 publish sau khi chu kỳ động cơ hoàn thành (thành công hoặc thất bại).

### 7.1. Thành công (Motor cycle hoàn thành)

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

### 7.2. Thất bại

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

### 7.3. Mô tả trường Dispense Result

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `eventId` | string (UUID) | ✅ | ID duy nhất của event này |
| `commandId` | string (UUID) | ✅ | ID lệnh gốc gây ra event này |
| `machineId` | string | ✅ | Machine ID |
| `orderId` | string (UUID) | ✅ | Order ID liên kết |
| `eventType` | string | ✅ | Phải là `"DISPENSE_RESULT"` |
| `slotNumber` | integer (1-3) | ✅ | Slot đã được thực thi |
| `result` | string | ✅ | `"SUCCESS"` hoặc `"FAILURE"` |
| `verificationLevel` | string | ✅ | Xem bảng bên dưới |
| `failureCode` | string \| null | ❌ | Mã lỗi nếu `result = "FAILURE"` |
| `failureMessage` | string \| null | ❌ | Mô tả lỗi nếu có |
| `timestamp` | string (ISO 8601 UTC) | ✅ | Thời điểm kết thúc chu kỳ |

**Giá trị `verificationLevel`:**

| Giá trị | Điều kiện sử dụng |
|---|---|
| `MOTOR_CYCLE_ONLY` | Động cơ đã hoàn thành chu kỳ theo thời gian đặt trước. Không có cảm biến xác nhận hàng rơi vật lý. Đây là mức mặc định nếu không có cảm biến. |
| `SENSOR_CONFIRMED` | Cảm biến hồng ngoại hoặc cảm biến tương đương đã xác nhận hàng đi qua điểm cảm biến. Chỉ sử dụng khi phần cứng thực sự hỗ trợ. |

> **Bắt buộc:** Backend **không** được diễn giải `MOTOR_CYCLE_ONLY` là bảo đảm hàng đã rơi. Phải thông báo rõ cho khách hàng.

**Giá trị `failureCode` thường gặp:**

| Giá trị | Mô tả |
|---|---|
| `MOTOR_TIMEOUT` | Động cơ không hoàn thành trong thời gian tối đa |
| `MOTOR_STALL` | Phát hiện động cơ bị kẹt (nếu có cơ chế phát hiện) |
| `SLOT_BLOCKED` | Cảm biến phát hiện ngăn bị kẹt (nếu có) |
| `HARDWARE_ERROR` | Lỗi phần cứng không xác định |

---

## 8. Payload — Heartbeat

ESP32 publish định kỳ (khuyến nghị mỗi 30–60 giây) vào `vending/VM001/heartbeat`.

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
| `uptimeSeconds` | integer | ✅ | Thời gian hoạt động kể từ lần khởi động gần nhất |
| `wifiRssi` | integer | ✅ | Cường độ tín hiệu Wi-Fi (dBm). Mức khuyến nghị > -70 dBm |
| `mqttReconnectCount` | integer | ✅ | Số lần kết nối lại MQTT kể từ lần khởi động |
| `slots[].slotNumber` | integer | ✅ | Số khay (1, 2 hoặc 3) |
| `slots[].lastDispenseResult` | string \| null | ❌ | Kết quả nhả hàng gần nhất |
| `slots[].lastDispenseAt` | string \| null | ❌ | Thời điểm nhả hàng gần nhất |
| `timestamp` | string (ISO 8601 UTC) | ✅ | Thời điểm gửi heartbeat |

> **Heartbeat ≠ Motor OK:** Backend nhận heartbeat biết rằng ESP32 đang kết nối và hoạt động. Điều này không có nghĩa là mọi động cơ đều hoạt động tốt về mặt cơ học.

---

## 9. QoS và Retained Message

| Topic | QoS | Retained | Lý do |
|---|---|---|---|
| `vending/VM001/commands` | QoS 1 | **Không (false)** | Lệnh nhả hàng cũ không được tự động gửi lại khi ESP32 kết nối lại. |
| `vending/VM001/events` | QoS 1 | Không | Events là phản hồi tức thời, không cần retained. |
| `vending/VM001/heartbeat` | QoS 0 | Không | Heartbeat không cần đảm bảo delivery; mất một heartbeat là chấp nhận được. |

> **Tại sao QoS 1 cho commands và events?** QoS 1 đảm bảo message đến ít nhất một lần. Idempotency được xử lý ở tầng ứng dụng (ESP32 kiểm tra `commandId` đã xử lý chưa; Backend kiểm tra `commandId` trong DB trước khi xử lý event).

---

## 10. Xử lý lệnh trùng lặp (Idempotency)

### 10.1. Phía ESP32

ESP32 phải duy trì danh sách `commandId` đã xử lý (sliding window hoặc queue giới hạn kích thước, ví dụ 20–50 entries).

Khi nhận lệnh:
1. Kiểm tra `commandId` có trong danh sách đã xử lý chưa.
2. Nếu có → publish `COMMAND_REJECTED` với `rejectionReason: "DUPLICATE_COMMAND"`.
3. Nếu chưa → xử lý lệnh và thêm `commandId` vào danh sách.

> **TBD:** Cách lưu danh sách commandId đã xử lý khi ESP32 restart (NVS Flash, EEPROM hay chỉ RAM) sẽ được quyết định ở Giai đoạn 6 dựa trên phần cứng thực tế. Nếu lưu trong RAM, danh sách mất sau khi restart — lệnh cũ có thể được tái kích hoạt nếu ESP32 reboot sau khi nhận lệnh nhưng trước khi publish event.

### 10.2. Phía Backend

Backend phải:
1. Kiểm tra DB: mỗi order chỉ có tối đa một `dispense_command` active.
2. Kiểm tra DB: không tạo command mới nếu order đã ở trạng thái `DISPENSING`, `COMPLETED`, `DISPENSE_FAILED`.
3. Khi nhận event từ ESP32, kiểm tra `commandId` có khớp với command đang chờ không.
4. Xử lý event idempotently — nhận cùng event nhiều lần không thay đổi trạng thái.

---

## 11. Xử lý timeout và mất kết nối

### 11.1. Backend timeout

Sau khi publish command:
1. Backend chờ event `COMMAND_ACCEPTED` trong thời gian `T1` (ví dụ: 30 giây).
2. Nếu không nhận được `COMMAND_ACCEPTED` trong `T1`: chuyển order sang `NEEDS_RECONCILIATION`.
3. Nếu nhận `COMMAND_ACCEPTED`: chờ event `DISPENSE_RESULT` trong thời gian `T2` (ví dụ: 60 giây).
4. Nếu không nhận được `DISPENSE_RESULT` trong `T2`: chuyển order sang `NEEDS_RECONCILIATION`.

> **Bắt buộc:** Không tự động phát lại lệnh nhả hàng khi timeout. Kết quả vật lý không xác định — có thể động cơ đã chạy nhưng event bị mất. Cần quản trị viên xem xét thủ công.

**Giá trị timeout TBD:**

| Timeout | Giá trị đề xuất | Trạng thái |
|---|---|---|
| `T1` — Chờ COMMAND_ACCEPTED | 30 giây | TBD — điều chỉnh sau khi test thực tế |
| `T2` — Chờ DISPENSE_RESULT | 60 giây | TBD — điều chỉnh sau khi đo thời gian chạy động cơ thực tế |

### 11.2. ESP32 khi mất kết nối MQTT

- Tất cả động cơ phải dừng ngay.
- Sau khi kết nối lại, **không** xử lý lệnh nào từ trước kết nối lại (xem phần retained message ở §9).
- Sau khi kết nối lại, chỉ xử lý lệnh mới với `expiresAt` còn hợp lệ.
- Publish heartbeat ngay sau khi kết nối lại thành công.

### 11.3. ESP32 reboot

- Khởi động với tất cả động cơ OFF.
- Không bắt đầu bất kỳ chu kỳ nhả hàng nào tự động.
- Chờ lệnh mới từ backend.
- Danh sách `commandId` trong RAM bị xóa — cần cân nhắc lưu vào NVS (xem §10.1).

---

## 12. Ánh xạ Slot ↔ GPIO (TBD)

Ánh xạ chính xác giữa `slotNumber` (1, 2, 3) và chân GPIO vật lý trên ESP32 chưa thể xác định vì phần cứng chưa được thiết kế.

> **TBD — Giai đoạn 6:** GPIO mapping sẽ được ghi vào đây sau khi:
> 1. Chọn board ESP32 cụ thể.
> 2. Thiết kế sơ đồ kết nối với motor driver.
> 3. Kiểm tra điện áp và không có xung đột với Wi-Fi/UART.

**Ví dụ placeholder (không phải giá trị cuối cùng):**

| `slotNumber` | GPIO (ví dụ) | Mô tả |
|---|---|---|
| 1 | GPIO_XX | Kết nối đến Driver slot 1 (TBD) |
| 2 | GPIO_XX | Kết nối đến Driver slot 2 (TBD) |
| 3 | GPIO_XX | Kết nối đến Driver slot 3 (TBD) |

> **Cảnh báo:** Không hardcode GPIO pin trong firmware cho đến khi sơ đồ điện được kiểm tra thực tế. Sử dụng constant được định nghĩa trong file config riêng biệt.

---

## 13. Ví dụ luồng đầy đủ

```
T=0s    Backend xác minh webhook thanh toán thành công
T=0.1s  Backend lưu dispense_command vào DB với status=PENDING
T=0.2s  Backend publish command vào vending/VM001/commands (QoS 1)
T=0.5s  ESP32 nhận command, kiểm tra hợp lệ
T=0.6s  ESP32 publish COMMAND_ACCEPTED vào vending/VM001/events
T=0.7s  Backend nhận COMMAND_ACCEPTED, cập nhật DB
T=0.7s  Backend chuyển order sang DISPENSING
T=0.8s  ESP32 kích hoạt motor slot 2
T=3.6s  ESP32 dừng motor (sau motorRunMs)
T=3.7s  ESP32 publish DISPENSE_RESULT (SUCCESS, MOTOR_CYCLE_ONLY)
T=3.8s  Backend nhận DISPENSE_RESULT, cập nhật DB
T=3.9s  Backend chuyển order sang COMPLETED
T=4.0s  Frontend polling nhận status COMPLETED
T=4.1s  Hiển thị thông báo cho khách hàng
```

---

## 14. Các mục TBD cần xác nhận

| # | Nội dung TBD | Lý do chưa chốt | Giai đoạn |
|---|---|---|---|
| 1 | Tên broker, hostname và port | Chưa chọn broker cụ thể | Giai đoạn 6 |
| 2 | MQTT version (3.1.1 hay 5.0) | Phụ thuộc vào thư viện Java và firmware | Giai đoạn 6 |
| 3 | Clean session policy | Phụ thuộc vào broker và yêu cầu reliability | Giai đoạn 6 |
| 4 | Timeout T1 và T2 | Cần đo thực tế trên phần cứng | Giai đoạn 6 |
| 5 | GPIO mapping slot 1/2/3 | Chưa thiết kế sơ đồ phần cứng | Giai đoạn 6 |
| 6 | `motorRunMs` cho từng slot | Cần đo thực tế sau khi có động cơ và lò xo thật | Giai đoạn 6 |
| 7 | Cách lưu commandId đã xử lý khi reboot | Phụ thuộc vào model ESP32 và yêu cầu phần cứng | Giai đoạn 6 |
| 8 | Driver động cơ và kiểu điều khiển | Chưa chọn loại động cơ và driver | Giai đoạn 6 |
| 9 | Heartbeat interval | Cần cân bằng giữa battery (nếu dùng) và độ trễ phát hiện offline | Giai đoạn 6 |
| 10 | Cảm biến xác nhận hàng rơi | Phụ thuộc vào thiết kế cơ khí và ngân sách | Giai đoạn 6 |

---

## 15. Điều không được làm

| Bên | Điều cấm |
|---|---|
| **Backend** | Publish lệnh khi chưa xác minh giao dịch ngân hàng thật |
| **Backend** | Tự động phát lại lệnh khi timeout mà không biết kết quả vật lý |
| **Backend** | Xem MQTT delivery thành công là bằng chứng sản phẩm đã nhả |
| **ESP32** | Kích hoạt động cơ khi không nhận được lệnh hợp lệ |
| **ESP32** | Xử lý lệnh đã hết hạn |
| **ESP32** | Xử lý lệnh trùng `commandId` |
| **ESP32** | Chạy động cơ vượt quá `motorRunMs` mà không có lý do bảo vệ |
| **ESP32** | Dùng `verificationLevel: "SENSOR_CONFIRMED"` khi không có cảm biến |
| **Frontend** | Kết nối trực tiếp tới MQTT broker |
| **Frontend** | Gửi lệnh nhả hàng trực tiếp |
