# ESP32 Device Simulator (MQTT over TLS)

Chương trình giả lập phần cứng máy bán hàng tự động ESP32, kết nối tới MQTT Broker (EMQX Cloud) qua TLS để kiểm thử luồng phát lệnh nhả hàng và nhận kết quả với Backend Spring Boot theo đúng chuẩn **`docs/MQTT_CONTRACT.md`**.

---

## 1. Cấu trúc thư mục

```text
device-simulator/
├── .env.example        # File mẫu cấu hình biến môi trường
├── .gitignore           # Bỏ qua .env, cache và virtual environment
├── README.md            # Tài liệu hướng dẫn sử dụng và schema
├── requirements.txt     # Danh sách thư viện Python phụ thuộc
├── simulator.py         # Mã nguồn chính của Device Simulator
└── test_simulator.py    # Bộ Unit Test tự động kiểm tra schema & logic
```

---

## 2. Yêu cầu hệ thống & Cài đặt

- **Python:** 3.10 trở lên (khuyến nghị Python 3.12).
- Cài đặt thư viện:

```bash
cd device-simulator
pip install -r requirements.txt
```

---

## 3. Cấu hình (.env)

Tạo file `.env` từ file mẫu `.env.example`:

```bash
cp .env.example .env
```

Điền mật khẩu xác thực MQTT của thiết bị ESP32 vào `.env`:

```ini
# Broker configuration
MQTT_BROKER=f18d138e.ala.asia-southeast1.emqxsl.com
MQTT_PORT=8883
MQTT_TLS=true

# Authentication credentials
MQTT_USERNAME=esp32_vm001
MQTT_PASSWORD=DIEN_MAT_KHAU_CUA_BAN_VAO_DAY

# Client and Machine identification
MQTT_CLIENT_ID=esp32-vm001-sim
MACHINE_ID=VM001

# Operational parameters
HEARTBEAT_INTERVAL=30
MOTOR_DELAY_SECONDS=2.0

# Simulation scenario: SUCCESS | FAILURE | UNCERTAIN
SIMULATOR_SCENARIO=SUCCESS
```

> **Lưu ý bảo mật:** File `.env` chứa mật khẩu đã được đưa vào `.gitignore`, tuyệt đối không commit lên Git.

---

## 4. Các kịch bản mô phỏng (Scenarios)

Bạn có thể chỉ định kịch bản mô phỏng trực tiếp qua tham số dòng lệnh `-s` hoặc cấu hình trong `.env`:

1. **`SUCCESS` (Mặc định):**
   - Nhận lệnh -> Gửi `COMMAND_ACCEPTED`.
   - Chờ 2 giây mô phỏng motor quay.
   - Gửi `DISPENSE_RESULT` với `result: "SUCCESS"`.
   - Backend sẽ hoàn tất đơn hàng (`COMPLETED`) và trừ kho thực tế.

2. **`FAILURE`:**
   - Nhận lệnh -> Gửi `COMMAND_ACCEPTED`.
   - Chờ 2 giây.
   - Gửi `DISPENSE_RESULT` với `result: "FAILURE"`, `failureCode: "MOTOR_TIMEOUT"`, `failureMessage: "Simulated motor stall or drop sensor timeout"`.
   - Backend sẽ đánh dấu đơn hàng `DISPENSE_FAILED` và giải phóng tạm giữ kho (không trừ tồn kho).

3. **`UNCERTAIN`:**
   - Nhận lệnh -> Gửi `COMMAND_ACCEPTED`.
   - Chờ 2 giây.
   - Gửi `DISPENSE_RESULT` với `result: "UNCERTAIN"`, `failureCode: "POWER_GLITCH"`.
   - Backend chuyển đơn hàng sang trạng thái `NEEDS_RECONCILIATION` để bảo vệ tồn kho và chờ đối soát thủ công.

---

## 5. Hướng dẫn chạy Simulator

### A. Chạy tự kiểm thử (Mock Test, không cần broker)
Dùng để kiểm tra nhanh cú pháp, tính toàn vẹn của DTO và schema:

```bash
python simulator.py --mock-test
```
Hoặc chạy bộ Unit Test toàn diện:

```bash
python test_simulator.py
```

### B. Chạy kết nối thật tới EMQX Cloud

**Kịch bản mặc định (SUCCESS):**
```bash
python simulator.py
```

**Kịch bản mô phỏng kẹt motor / lỗi nhả hàng (FAILURE):**
```bash
python simulator.py --scenario FAILURE
```

**Kịch bản mô phỏng mất nguồn giữa chừng (UNCERTAIN):**
```bash
python simulator.py --scenario UNCERTAIN
```

**Tùy biến thời gian trễ motor và chu kỳ heartbeat:**
```bash
python simulator.py --motor-delay 1.5 --heartbeat-interval 15
```

---

## 6. Tham chiếu MQTT Topics & Schema

### A. Topics
| Chiều | Topic | QoS | Chức năng |
|---|---|:---:|---|
| ESP32 -> EMQX | `vending/VM001/heartbeat` | 0 | Báo cáo định kỳ trạng thái máy sống |
| Backend -> ESP32 | `vending/VM001/commands` | 1 | Lệnh nhả hàng phát ra từ Backend |
| ESP32 -> EMQX | `vending/VM001/events` | 1 | Báo cáo nhận lệnh và kết quả nhả hàng |
| Backend -> ESP32 | `vending/VM001/events/ack` | 1 | Backend xác nhận đã lưu event vào DB |

### B. Heartbeat Message (`vending/VM001/heartbeat`)
```json
{
  "machineId": "VM001",
  "firmwareVersion": "esp32-sim-1.0.0",
  "uptimeSeconds": 120,
  "wifiRssi": -55,
  "mqttReconnectCount": 0,
  "slots": [
    {
      "slotNumber": 1,
      "lastDispenseResult": "SUCCESS",
      "lastDispenseAt": "2026-11-02T15:00:00Z"
    }
  ],
  "timestamp": "2026-11-02T15:02:00.000Z"
}
```

### C. Dispense Command Message (`vending/VM001/commands`)
```json
{
  "commandId": "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
  "machineId": "VM001",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "slotNumber": 1,
  "action": "DISPENSE",
  "createdAt": "2026-11-02T15:00:00Z",
  "expiresAt": "2026-11-02T15:01:00Z",
  "parameters": {
    "motorRunMs": 800
  }
}
```

### D. Device Event Message (`vending/VM001/events`)
**1. COMMAND_ACCEPTED:**
```json
{
  "eventId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "commandId": "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
  "machineId": "VM001",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "COMMAND_ACCEPTED",
  "slotNumber": 1,
  "timestamp": "2026-11-02T15:00:01.000Z"
}
```

**2. DISPENSE_RESULT (SUCCESS):**
```json
{
  "eventId": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
  "commandId": "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
  "machineId": "VM001",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "DISPENSE_RESULT",
  "slotNumber": 1,
  "result": "SUCCESS",
  "verificationLevel": "MOTOR_CYCLE_ONLY",
  "timestamp": "2026-11-02T15:00:03.000Z"
}
```

**3. DISPENSE_RESULT (FAILURE):**
```json
{
  "eventId": "c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f",
  "commandId": "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
  "machineId": "VM001",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "DISPENSE_RESULT",
  "slotNumber": 1,
  "result": "FAILURE",
  "verificationLevel": "MOTOR_CYCLE_ONLY",
  "failureCode": "MOTOR_TIMEOUT",
  "failureMessage": "Simulated motor stall or drop sensor timeout",
  "timestamp": "2026-11-02T15:00:03.000Z"
}
```

### E. Event ACK Message (`vending/VM001/events/ack`)
```json
{
  "ackId": "d4e5f6a7-b8c9-0d1e-2f3a-4b5c6d7e8f9a",
  "eventId": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
  "commandId": "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
  "machineId": "VM001",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSED",
  "timestamp": "2026-11-02T15:00:03.100Z"
}
```

