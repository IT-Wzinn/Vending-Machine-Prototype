# Frontend — Vending Machine Customer Interface

Giao diện khách hàng cho hệ thống máy bán hàng tự động thông minh (Smart Vending Machine Prototype). Được thiết kế mobile-first, tối ưu hóa cho người dùng truy cập từ mã QR cố định dán trên thân máy, kết nối trực tiếp với backend Spring Boot.

---

## 1. Công nghệ sử dụng

- **Core:** [React 19](https://react.dev/) + [TypeScript](https://www.typescriptlang.org/)
- **Bundler & Dev Server:** [Vite](https://vite.dev/)
- **Styling:** [Tailwind CSS v3](https://tailwindcss.com/)
- **Iconography:** [Lucide React](https://lucide.dev/)
- **QR Code:** [qrcode.react](https://github.com/zpao/qrcode.react)
- **Kiểm thử tự động:** [Vitest](https://vitest.dev/) + [Testing Library (React & DOM)](https://testing-library.com/)

---

## 2. Cấu trúc thư mục

```text
frontend/
├── .env.example              # Mẫu cấu hình biến môi trường
├── index.html                # HTML entrypoint (Google Fonts, SEO meta)
├── package.json              # Quản lý dependencies & scripts
├── postcss.config.js         # Cấu hình PostCSS cho Tailwind
├── tailwind.config.js        # Hệ thống màu thương hiệu & design system
├── tsconfig.json             # Cấu hình TypeScript root
├── tsconfig.app.json         # Cấu hình TypeScript cho client code
├── vite.config.ts            # Cấu hình Vite & Vitest
└── src/
    ├── App.tsx               # Root component tích hợp CartProvider
    ├── index.css             # Tailwind base, utilities & glassmorphism
    ├── main.tsx              # DOM root mount
    ├── components/           # Các UI component tái sử dụng
    │   ├── CartDrawer.tsx    # Ngăn trượt giỏ hàng, cập nhật số lượng, tính tổng
    │   ├── CheckoutModal.tsx # Xác nhận đặt hàng & hiển thị thanh toán
    │   ├── ErrorMessage.tsx  # Thông báo lỗi mạng hoặc lỗi API với nút thử lại
    │   ├── LoadingSkeleton.tsx # Shimmer skeleton khi đang tải dữ liệu
    │   ├── MachineBanner.tsx # Hiển thị tên máy, mã định danh và trạng thái
    │   ├── Navbar.tsx        # Thanh điều hướng trên cùng kèm huy hiệu giỏ hàng
    │   ├── ProductCard.tsx   # Card sản phẩm (khay số, ảnh, giá VND, nút mua)
    │   └── StatusBadge.tsx   # Badge trạng thái máy và số lượng tồn kho
    ├── context/
    │   └── CartContext.tsx   # Quản lý giỏ hàng, tính tổng tiền VND, giới hạn tồn
    ├── pages/
    │   ├── CustomerOrderPage.tsx # Trang đặt hàng chính truy cập từ QR
    │   └── InvalidMachinePage.tsx# Trang thông báo khi mã máy không hợp lệ/không tồn tại
    ├── services/
    │   ├── api.ts            # API client kết nối backend Spring Boot
    │   └── mockData.ts       # Dữ liệu mock độc lập cho kiểm thử
    ├── test/                 # Test suites tự động
    │   ├── CartContext.test.tsx
    │   ├── CustomerOrderPage.test.tsx
    │   ├── MachineBanner.test.tsx
    │   ├── ProductCard.test.tsx
    │   ├── formatters.test.ts
    │   └── setup.ts          # Setup jest-dom matchers
    ├── types/                # Type definitions chuẩn theo API contract
    │   ├── api.ts
    │   ├── machine.ts
    │   ├── order.ts
    │   └── product.ts
    └── utils/
        └── formatters.ts     # Định dạng tiền VND số nguyên (vd: 8.000 ₫)
```

---

## 3. Cấu hình biến môi trường

Tạo file `.env` từ file mẫu `.env.example`:

```bash
cp .env.example .env
```

Nội dung `.env`:
```env
# Địa chỉ backend REST API
VITE_API_BASE_URL=http://localhost:8080
```

> **Quy tắc bảo mật:** Tuyệt đối không lưu secret, API key ngân hàng hoặc mật khẩu trong frontend.

---

## 4. Hướng dẫn chạy và kiểm thử

Di chuyển vào thư mục `frontend`:

```bash
cd frontend
```

### 4.1. Cài đặt dependencies

```bash
npm install
```

### 4.2. Chạy môi trường phát triển (Dev server)

```bash
npm run dev
```

Server sẽ khởi chạy tại: `http://localhost:5173`

- Truy cập máy kiểm thử mặc định: `http://localhost:5173/machine/VM001`
- Hoặc thông qua query param: `http://localhost:5173/?machineId=VM001`
- Khi truy cập máy không tồn tại (vd: `/machine/VM999`), hệ thống sẽ hiển thị giao diện báo lỗi thân thiện.

### 4.3. Chạy kiểm thử tự động (Unit & Integration tests)

```bash
npm test
```

Chạy toàn bộ 19 test cases cho các luồng:
- Kiểm tra tính hợp lệ của hàm format tiền tệ VND
- Giỏ hàng: thêm sản phẩm, tăng/giảm số lượng, chặn vượt quá tồn kho, chặn sản phẩm hết hàng
- Card sản phẩm: hiển thị khay lò xo, giá, trạng thái hết hàng và nút bấm
- Banner máy: hiển thị thông tin máy và cảnh báo khi máy bảo trì/tạm ngưng
- Trang đặt hàng: tải dữ liệu thành công, xử lý lỗi mất kết nối, xử lý mã máy không tồn tại, và quy trình thanh toán

### 4.4. Build production

```bash
npm run build
```

Kiểm tra TypeScript typecheck (`tsc -b`) và đóng gói tài nguyên vào thư mục `dist/`.

---

## 5. Tích hợp Backend và Phân định ranh giới Giai đoạn (Phase Boundary)

### 5.1. Các API Backend Phase 2 đã tích hợp thực tế
- `GET /api/v1/machines/{machineId}`: Xác thực máy bán hàng tồn tại và đang `ACTIVE`.
- `GET /api/v1/machines/{machineId}/products`: Lấy danh sách sản phẩm theo từng khay vật lý (Slot 1, 2, 3), kèm đơn giá VND, số lượng tồn kho và trạng thái sẵn sàng mua.

### 5.2. Các chức năng thuộc Giai đoạn 4 & 5 (Chưa có Backend)
- **Tạo đơn hàng & Lưu database:** API `POST /api/v1/orders` thuộc **Giai đoạn 4** (Claude phụ trách).
- **Thanh toán chuyển khoản & Webhook:** Thuộc **Giai đoạn 5**.
- **Điều khiển động cơ nhả hàng ESP32 & MQTT:** Thuộc **Giai đoạn 6 & 7**.

Khi khách hàng bấm "Xác nhận đặt hàng", frontend gửi request đến `POST /api/v1/orders`. Nếu backend chưa triển khai endpoint này (trả về 404), frontend hiển thị thông báo rõ ràng về dependency của Phase 4 và **tuyệt đối không giả lập thanh toán thành công hay tự phát lệnh nhả hàng**.
