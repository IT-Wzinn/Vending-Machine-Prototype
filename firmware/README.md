# Firmware

Firmware ESP32 — Arduino framework, MQTT qua TLS, điều khiển 3 slot động cơ độc lập.

> Thư mục này sẽ được khởi tạo đầy đủ ở **Giai đoạn 6**.

## Phụ trách

Firmware: theo phân chia trách nhiệm trong `IMPLEMENTATION_PLAN.md §2.4`.
MQTT phía backend: **Claude**.

## Lưu ý bảo mật quan trọng

Không đưa thông tin sau vào source code hay Git:
- Wi-Fi SSID và password
- MQTT username và password
- MQTT broker host/port

Sử dụng file `include/secrets.h` (đã có trong `.gitignore`) hoặc cơ chế tương đương.

## Tham khảo

- Đặc tả Firmware: [PRODUCT_SPEC.md §14](../PRODUCT_SPEC.md) — ESP32 Firmware Specification
- Hợp đồng MQTT: [docs/MQTT_CONTRACT.md](../docs/MQTT_CONTRACT.md)
