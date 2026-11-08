# Firmware — ESP32 Hardware Controller

Mã nguồn điều khiển phần cứng cho máy bán hàng tự động thông minh (**FreshVend / Vending Machine Prototype**) chạy trên vi điều khiển **ESP32** (hỗ trợ các dòng ESP32-WROOM-32, ESP32-C3), kết nối tới MQTT Broker (EMQX Cloud) qua TLS để nhận lệnh xuất hàng và điều khiển 3 khay động cơ lò xo độc lập.

---

## 1. Kiến Trúc Phần Cứng & Nguyên Lý Hoạt Động

Hệ thống phần cứng FreshVend sử dụng vi điều khiển ESP32 làm trung tâm điều phối, kết nối trực tiếp với các module cơ điện:

```text
                           [ ESP32 CONTROLLER ]
               (Wi-Fi 2.4GHz + MQTTS TLS 1.2/1.3 + NVS Flash)
                                     │
         ┌───────────────────────────┼───────────────────────────┐
         ▼ (GPIO Outputs)            ▼ (GPIO Inputs)             ▼ (GPIO Inputs)
 [Mạch Driver / Relay 5V]   [Công Tắc Cam 360°]         [Cảm Biến Rơi Hàng IR]
  (Cách ly Optocoupler)      (Home Microswitch)          (Through-beam Sensor)
         │                           │                           │
  ┌──────┴──────┐             ┌──────┴──────┐             ┌──────┴──────┐
  ▼      ▼      ▼             ▼      ▼      ▼             ▼             ▼
Slot 1 Slot 2 Slot 3       Slot 1 Slot 2 Slot 3     Phát hiện vật phẩm thực tế
Motor  Motor  Motor        (Xác định điểm dừng)     (Nâng cấp lên SENSOR_CONFIRMED)
```

- **Slot 1:** Động cơ lò xo khay Nước suối Aquafina 500ml (tải trọng $\approx 500\text{g}$).
- **Slot 2:** Động cơ lò xo khay Snack Lay's (tải trọng $\approx 50\text{g}$).
- **Slot 3:** Động cơ lò xo khay Kẹo cao su Doublemint (tải trọng $\approx 30\text{g}$).

> **Hình ảnh tham khảo thiết kế:**
> - Bản vẽ thiết kế cơ khí và bố trí khoang tủ: [ban-ve-thiet-ke.png](../docs/images/ban-ve-thiet-ke.png)
> - Sơ đồ chân và vi điều khiển ESP32: [esp32-c3.png](../docs/images/esp32-c3.png)

---

## 2. Cấu Trúc Thư Mục Firmware (PlatformIO / Arduino)

```text
firmware/
├── platformio.ini         # Cấu hình môi trường build PlatformIO (board, framework, lib_deps)
├── README.md              # Tài liệu hướng dẫn phần cứng và firmware này
├── include/
│   ├── config.h           # Cấu hình hằng số hệ thống, MQTT topics, GPIO pinout
│   ├── secrets.h.example  # File mẫu cấu hình Wi-Fi SSID, mật khẩu và MQTT credentials
│   └── secrets.h          # File thực tế chứa thông tin đăng nhập (BỊ CHẶN BỞI .GITIGNORE)
└── src/
    ├── main.cpp           # Vòng lặp chính, kết nối Wi-Fi, MQTT client và SNTP sync
    ├── mqtt_manager.cpp   # Xử lý publish/subscribe các topic MQTT theo chuẩn hợp đồng
    ├── nvs_storage.cpp    # Quản lý bộ nhớ bất biến Flash NVS (idempotency, durable outbox)
    ├── motor_controller.cpp# Điều khiển xung kích hoạt motor và công tắc hành trình
    └── drop_sensor.cpp    # Đọc tín hiệu cảm biến quang hồng ngoại xác nhận hàng rơi
```

---

## 3. Quy Chuẩn Giao Tiếp MQTT over TLS (Port 8883)

Firmware tuân thủ 100% hợp đồng **`docs/MQTT_CONTRACT.md`**:

| Hướng | Topic | QoS | Chức năng |
|---|---|:---:|---|
| Backend ➔ ESP32 | `vending/VM001/commands` | 1 | Nhận lệnh xuất hàng từ Backend (cleanSession = `true`) |
| ESP32 ➔ Backend | `vending/VM001/events` | 1 | Phát sự kiện `COMMAND_ACCEPTED`, `COMMAND_REJECTED`, `DISPENSE_RESULT` |
| Backend ➔ ESP32 | `vending/VM001/events/ack` | 1 | Backend xác nhận đã lưu DB thành công (để ESP32 xóa NVS Outbox) |
| ESP32 ➔ Backend | `vending/VM001/heartbeat` | 0 | Phát định kỳ báo trạng thái máy sống (mỗi 30s) |

---

## 4. Máy Trạng Thái Lệnh & Lưu Trữ Bền Vững NVS (State Machine)

Để đảm bảo an toàn tuyệt đối, không nhả hàng 2 lần và không mất dấu vết giao dịch khi sụt nguồn:

```text
                  Nhận Command qua MQTT
                           │
                           ▼
                 [ Kiểm Tra Cú Pháp & Giờ ]
           (Clock Trust qua SNTP + Kiểm tra expiresAt)
                           │
                 ┌─────────┴─────────┐
             Hợp lệ               Quá hạn / Lỗi
                 │                     │
                 ▼                     ▼
         [ cmd_state=ACCEPTED ]    [ COMMAND_REJECTED ]
         (Ghi NVS Flash,           (Không quay motor)
          phát event ACCEPTED)
                 │
                 ▼
         [ cmd_state=EXECUTING ] ──> KÍCH HOẠT MOTOR
         (Ghi NVS TRƯỚC KHI cấp điện cho GPIO motor)
                 │
       ┌─────────┴──────────┐
  Hoàn thành              Sập nguồn / Lỗi giữa chừng
       │                             │
       ▼                             ▼
[ cmd_state=COMPLETED ]       [ cmd_state=UNCERTAIN ]
(Phát DISPENSE_RESULT         (Phát DISPENSE_RESULT
 với result="SUCCESS")         với result="UNCERTAIN")
       │                             │
       └──────────────┬──────────────┘
                      ▼
            [ Lưu NVS Durable Outbox ]
  (Chờ bản tin EVENT_ACK từ Backend mới được xóa)
```

- **State-First Sequencing:** Ghi trạng thái `EXECUTING` vào Flash trước khi bật motor. Nếu mất điện rơi vào tích tắc này, sau khi reboot ESP32 sẽ nhận diện là `UNCERTAIN`, chuyển đơn sang `NEEDS_RECONCILIATION` và tuyệt đối **không tự ý bật lại motor**.
- **Idempotency Window:** Lưu trữ 20 `commandId` gần nhất trong NVS để từ chối các lệnh trùng lặp (`DUPLICATE_COMMAND`).

---

## 5. Ràng Buộc Phần Cứng & An Toàn Điện

1. **Tuyệt đối không cấp nguồn motor từ chân ESP32:** Phải sử dụng mạch đệm công suất (Relay hoặc Mosfet) có **cách ly quang (Optocoupler)** để ngăn chặn xung điện cảm ứng đánh thủng vi điều khiển.
2. **Tránh các chân Strapping / Boot pins:** Tránh sử dụng `GPIO 0, 2, 12, 15` làm ngõ ra điều khiển motor mức LOW để không làm ESP32 rơi vào chế độ nạp flash hoặc brownout khi khởi động.
3. **Nguồn cấp độc lập:** Sử dụng nguồn DC 12V/24V định mức $\ge 5\text{A}$ cho động cơ và hạ áp qua module Buck DC-DC (LM2596/MP1584) cấp 5V ổn định cho ESP32.

---

## 6. Lưu Ý Bảo Mật Quan Trọng

Tuyệt đối không đưa thông tin sau vào mã nguồn hay commit lên Git:
- Tên mạng Wi-Fi (SSID) và Mật khẩu Wi-Fi.
- MQTT Username và Password.
- Private Keys hoặc chứng chỉ SSL nội bộ.

Sử dụng file mẫu `include/secrets.h.example` để tạo file `include/secrets.h` cục bộ trên máy của bạn (file này đã được đưa vào `.gitignore`).

---

## 7. Tài Liệu Tham Khảo

- Đặc tả kỹ thuật phần cứng: [PRODUCT_SPEC.md §14](../PRODUCT_SPEC.md)
- Hợp đồng giao tiếp MQTT: [docs/MQTT_CONTRACT.md](../docs/MQTT_CONTRACT.md)
- Báo cáo kiểm toán & Bàn giao: [docs/SOFTWARE_HANDOVER_REPORT.md](../docs/SOFTWARE_HANDOVER_REPORT.md)
