# Frontend — Vending Machine Customer & Admin Interface

Giao diện người dùng cho hệ thống máy bán hàng tự động thông minh (**FreshVend / Vending Machine Prototype**), bao gồm:
1. **Customer Mobile Web App:** Giao diện đặt hàng di động (mobile-first), tối ưu hóa cho người dùng quét mã QR cố định trên thân máy mà không cần cài đặt ứng dụng.
2. **Admin Reconciliation Portal:** Cổng quản trị và đối soát đơn hàng an toàn, hỗ trợ tra cứu lịch sử sự kiện phần cứng và ra quyết định xử lý khiếu nại.

---

## 1. Công nghệ & Thư viện sử dụng

- **Core Framework:** [React 19.2.8](https://react.dev/) + [TypeScript](https://www.typescriptlang.org/)
- **Bundler & Dev Server:** [Vite 8.3.4](https://vite.dev/)
- **Styling & Design System:** [Tailwind CSS v3](https://tailwindcss.com/) + Glassmorphism UX
- **Iconography:** [Lucide React](https://lucide.dev/)
- **QR Code Engine:** [qrcode.react](https://github.com/zpao/qrcode.react) (sinh mã VietQR chuẩn NAPAS 247)
- **Kiểm thử tự động:** [Vitest 5.0](https://vitest.dev/) + [Testing Library (React & DOM)](https://testing-library.com/) + JSDOM

---

## 2. Cấu trúc thư mục

```text
frontend/
├── .env.example              # Mẫu cấu hình biến môi trường
├── index.html                # HTML entrypoint (Google Fonts, SEO & Viewport mobile)
├── package.json              # Quản lý dependencies & scripts npm
├── postcss.config.js         # Cấu hình PostCSS cho Tailwind CSS
├── tailwind.config.js        # Design tokens, màu sắc và typography
├── tsconfig.json             # Cấu hình TypeScript root
├── tsconfig.app.json         # Cấu hình TypeScript cho client code
├── vite.config.ts            # Cấu hình Vite & Vitest environment
└── src/
    ├── App.tsx               # Root component tích hợp Router và CartProvider
    ├── index.css             # Tailwind base utilities & hiệu ứng giao diện
    ├── main.tsx              # Entrypoint gắn DOM root
    ├── components/           # Các UI component tái sử dụng
    │   ├── CartDrawer.tsx    # Ngăn kéo giỏ hàng, cập nhật số lượng, tính tổng
    │   ├── CheckoutModal.tsx # Xác nhận đặt hàng, hiển thị VietQR và polling trạng thái
    │   ├── ErrorMessage.tsx  # Thông báo lỗi mạng kèm nút thử lại
    │   ├── LoadingSkeleton.tsx # Hiệu ứng shimmer khi đang tải dữ liệu
    │   ├── MachineBanner.tsx # Hiển thị tên máy, mã máy và trạng thái hoạt động
    │   ├── Navbar.tsx        # Thanh điều hướng trên cùng kèm huy hiệu giỏ hàng
    │   ├── ProductCard.tsx   # Card sản phẩm (khay lò xo số, ảnh, giá VND, nút mua)
    │   ├── ReconciliationDecisionModal.tsx # Modal ra quyết định đối soát (Admin)
    │   └── StatusBadge.tsx   # Badge trạng thái máy, đơn hàng và tồn kho
    ├── context/
    │   └── CartContext.tsx   # Context giỏ hàng, tính tổng tiền VND, khóa số lượng
    ├── pages/
    │   ├── CustomerOrderPage.tsx            # Trang đặt hàng chính truy cập từ QR máy
    │   ├── InvalidMachinePage.tsx           # Báo lỗi khi mã máy không tồn tại
    │   ├── AdminLoginPage.tsx               # Trang đăng nhập quản trị viên
    │   ├── ReconciliationDashboardPage.tsx  # Dashboard danh sách đơn hàng đối soát
    │   └── ReconciliationOrderDetailPage.tsx# Chi tiết timeline đơn và log thiết bị
    ├── services/
    │   ├── api.ts            # Client gọi REST API Backend Spring Boot
    │   └── mockData.ts       # Dữ liệu mock phục vụ kiểm thử
    ├── test/                 # Test suites tự động (11 test files, 55 test cases)
    │   ├── AdminApiAndSecurity.test.ts
    │   ├── AdminLogin.test.tsx
    │   ├── CartContext.test.tsx
    │   ├── CheckoutModal.test.tsx
    │   ├── CustomerOrderPage.test.tsx
    │   ├── formatters.test.ts
    │   ├── MachineBanner.test.tsx
    │   ├── ProductCard.test.tsx
    │   ├── ReconciliationDashboard.test.tsx
    │   ├── ReconciliationDecisionModal.test.tsx
    │   ├── ReconciliationOrderDetail.test.tsx
    │   └── setup.ts          # Cấu hình Jest-DOM matchers cho Vitest
    ├── types/                # Type definitions chuẩn theo API contract
    │   ├── admin.ts
    │   ├── api.ts
    │   ├── machine.ts
    │   ├── order.ts
    │   └── product.ts
    └── utils/
        └── formatters.ts     # Hàm format tiền tệ VND chuẩn (vd: 8.000 ₫)
```

---

## 3. Cấu hình biến môi trường

Tạo file `.env` từ file mẫu `.env.example`:

```bash
cp .env.example .env
```

Nội dung `.env`:
```env
# URL của Backend REST API
VITE_API_BASE_URL=http://localhost:8080
```

> **Lưu ý bảo mật:** File `.env` chứa cấu hình public cho client, tuyệt đối không khai báo secret key ngân hàng hay mật khẩu quản trị tại đây.

---

## 4. Hướng dẫn chạy và kiểm thử

Di chuyển vào thư mục `frontend`:

```bash
cd frontend
```

### 4.1. Cài đặt thư viện phụ thuộc
```bash
npm install
```

### 4.2. Chạy môi trường phát triển (Dev server)
```bash
npm run dev
```
Truy cập ứng dụng tại `http://localhost:5173`:
- Trang đặt hàng máy `VM001`: `http://localhost:5173/machine/VM001` (hoặc `http://localhost:5173/?machineId=VM001`)
- Trang đăng nhập quản trị: `http://localhost:5173/admin/login`
- Dashboard đối soát: `http://localhost:5173/admin/reconciliation`

### 4.3. Chạy kiểm thử tự động
```bash
npm test
```
*Kết quả:* Chạy toàn bộ **55 test cases** trên **11 test files** kiểm thử toàn diện:
- Format tiền tệ VND.
- Giỏ hàng: thêm, cập nhật số lượng, chặn vượt quá tồn kho thực tế.
- Card sản phẩm và Banner máy.
- Quy trình tạo đơn, sinh mã VietQR và polling trạng thái đơn hàng.
- Trang đăng nhập quản trị: xác thực username, password, xử lý token.
- Dashboard đối soát: lọc danh sách đơn hàng, xem timeline chi tiết và mở modal ra quyết định đối soát.

### 4.4. Build đóng gói Production
```bash
npm run build
```
Thực hiện kiểm tra kiểu dữ liệu TypeScript (`tsc -b`) và đóng gói tài nguyên tĩnh tối ưu hóa vào thư mục `dist/`.

---

## 5. Tích hợp Hệ thống & Luồng Nghiệp vụ Hoàn Chỉnh

Frontend đã được tích hợp 100% với Backend Spring Boot và hoạt động ổn định trên môi trường kiểm thử:

1. **Khách hàng quét mã QR trên thân máy:**
   - Ứng dụng đọc `machineId` từ URL và gọi `GET /api/v1/machines/{machineId}` để xác minh máy còn hoạt động.
   - Tải danh sách sản phẩm theo từng khay vật lý (Slot 1, Slot 2, Slot 3) qua `GET /api/v1/machines/{machineId}/products`.
2. **Tạo đơn hàng & Khóa kho tạm thời:**
   - Gửi yêu cầu `POST /api/v1/orders`. Backend xác nhận và giữ chỗ sản phẩm (`reserved_quantity`), trả về mã đơn và chuỗi nội dung chuyển khoản `transfer_reference`.
3. **Thanh toán VietQR & Tự động cập nhật:**
   - Ứng dụng sinh mã VietQR động chuẩn NAPAS 247 để khách quét thanh toán bằng mọi ứng dụng ngân hàng.
   - Polling trạng thái đơn hàng `GET /api/v1/orders/{orderId}` mỗi 2 giây:
     - `PAID` ➔ Hiển thị thông báo đã nhận thanh toán.
     - `DISPENSING` ➔ Đang kích hoạt động cơ lò xo nhả hàng.
     - `COMPLETED` ➔ Xuất hàng thành công, hiển thị lời cảm ơn.
     - `NEEDS_RECONCILIATION` ➔ Thông báo kết quả cần đối soát an toàn cho khách hàng.
4. **Cổng đối soát dành cho Quản trị viên:**
   - Đăng nhập bảo mật JWT, tự động đính kèm header CSRF `X-Requested-With: XMLHttpRequest` cho mọi thao tác duyệt đơn.
   - Xem chi tiết nhật ký sự kiện phần cứng ESP32 (`device_events`) để kiểm tra lý do kẹt motor hoặc timeout.
   - Ra quyết định hoàn tiền (`REFUND`), nhả hàng lại (`DISPENSE_RETRY`) hoặc đánh dấu hoàn tất (`MARK_COMPLETED`).
