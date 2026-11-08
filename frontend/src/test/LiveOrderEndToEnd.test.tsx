import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { CustomerOrderPage } from '../pages/CustomerOrderPage';
import { CartProvider } from '../context/CartContext';
import * as api from '../services/api';

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api');
  return {
    ...actual,
    getMachine: vi.fn(),
    getProductCatalog: vi.fn(),
    createOrder: vi.fn(),
    getOrderStatus: vi.fn(),
  };
});

describe('Live Order Flow Acceptance — FreshVend with Sylva Redesign', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  const liveMachine = {
    machineId: 'VM001',
    name: 'Máy bán hàng tầng 1',
    status: 'ACTIVE' as const,
    message: null,
  };

  const liveProducts = [
    {
      productId: '550e8400-e29b-41d4-a716-446655440001',
      name: 'Nước suối Aqua 500ml',
      description: 'Nước uống tinh khiết Aqua chai 500ml',
      imageUrl: 'https://example.com/images/aqua-500ml.jpg',
      priceVnd: 8000,
      slotNumber: 1,
      stockQuantity: 4,
      available: true,
    },
    {
      productId: '550e8400-e29b-41d4-a716-446655440002',
      name: "Snack Lay's vị tự nhiên",
      description: null,
      imageUrl: null,
      priceVnd: 15000,
      slotNumber: 2,
      stockQuantity: 3,
      available: true,
    },
    {
      productId: '550e8400-e29b-41d4-a716-446655440003',
      name: 'Kẹo cao su Doublemint',
      description: null,
      imageUrl: null,
      priceVnd: 5000,
      slotNumber: 3,
      stockQuantity: 0,
      available: false,
    },
  ];

  const liveCreatedOrder = {
    orderId: '0d8e5e23-30e9-45d6-979c-5aa422bd6b19',
    publicOrderCode: 'VM001-20261108-A238',
    machineId: 'VM001',
    product: {
      productId: '550e8400-e29b-41d4-a716-446655440001',
      name: 'Nước suối Aqua 500ml',
      priceVnd: 8000,
    },
    quantity: 1,
    totalAmountVnd: 8000,
    status: 'PENDING_PAYMENT' as const,
    payment: {
      transferReference: 'VM001UJW1',
      paymentQrData: '00020101021238540010A000000727012400069704220110DEMO1234560208QRIBFTTA5303704540480005802VN62130809VM001UJW163045F32',
      bankAccountNumber: 'DEMO123456',
      bankAccountName: 'DEMO SMART VENDING',
      bankName: 'MBBank',
      amountVnd: 8000,
      paymentExpiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
      paidAt: null,
    },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  it('runs the full visual and operational journey from product catalog to payment QR', async () => {
    vi.mocked(api.getMachine).mockResolvedValue(liveMachine);
    vi.mocked(api.getProductCatalog).mockResolvedValue({
      machineId: 'VM001',
      products: liveProducts,
    });
    vi.mocked(api.createOrder).mockResolvedValue(liveCreatedOrder);
    vi.mocked(api.getOrderStatus).mockResolvedValue({
      orderId: liveCreatedOrder.orderId,
      status: 'PENDING_PAYMENT',
      updatedAt: new Date().toISOString(),
      message: null,
    });

    render(
      <CartProvider>
        <CustomerOrderPage initialMachineId="VM001" />
      </CartProvider>
    );

    // 1. Verify Sylva Background is mounted and non-blocking
    const bgContainer = screen.getByTestId('sylva-background-container');
    expect(bgContainer).toBeInTheDocument();
    expect(bgContainer).toHaveClass('pointer-events-none');

    // 2. Machine Banner verification
    await waitFor(() => {
      expect(screen.getByText('Máy bán hàng tầng 1')).toBeInTheDocument();
      expect(screen.getAllByText('VM001').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('Hoạt động')).toBeInTheDocument();
    });

    // 3. Products verification
    expect(screen.getByText('Nước suối Aqua 500ml')).toBeInTheDocument();
    expect(screen.getByText("Snack Lay's vị tự nhiên")).toBeInTheDocument();
    expect(screen.getByText('Kẹo cao su Doublemint')).toBeInTheDocument();

    // 4. Verify stock badges and out-of-stock disablement
    expect(screen.getByText('Còn hàng: 4')).toBeInTheDocument();
    expect(screen.getByText('Còn hàng: 3')).toBeInTheDocument();
    expect(screen.getAllByText('Hết hàng').length).toBeGreaterThanOrEqual(1);

    const buyButtons = screen.getAllByRole('button', { name: /Chọn mua/i });
    expect(buyButtons.length).toBe(2);

    const outOfStockButton = screen.getByRole('button', { name: /Hết hàng/i });
    expect(outOfStockButton).toBeDisabled();

    // 5. Select Product on Slot 1
    fireEvent.click(buyButtons[0]);
    await waitFor(() => {
      expect(screen.getByText('Đã thêm')).toBeInTheDocument();
    });

    // 6. Open Cart Drawer
    const cartButton = screen.getByRole('button', { name: /Xem giỏ hàng/i });
    fireEvent.click(cartButton);

    expect(screen.getByText('Giỏ hàng của bạn')).toBeInTheDocument();
    expect(screen.getAllByText('Khay 01').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('8.000 ₫').length).toBeGreaterThanOrEqual(1);

    // 7. Proceed to Checkout Modal
    const checkoutButton = screen.getByRole('button', { name: /Đặt hàng & Thanh toán/i });
    fireEvent.click(checkoutButton);

    expect(screen.getByText('Xác nhận đơn hàng')).toBeInTheDocument();
    expect(screen.getByText('Khay xuất hàng:')).toBeInTheDocument();

    // 8. Confirm create order
    const confirmButton = screen.getByRole('button', { name: /Xác nhận đặt hàng/i });
    fireEvent.click(confirmButton);

    // 9. Payment QR Code screen appears with SePay VietQR data
    await waitFor(() => {
      expect(screen.getByText('Quét mã bằng ứng dụng Ngân hàng')).toBeInTheDocument();
      expect(screen.getByText('VM001-20261108-A238')).toBeInTheDocument();
      expect(screen.getByText(/VM001UJW1/)).toBeInTheDocument();
      expect(screen.getByText('DEMO SMART VENDING')).toBeInTheDocument();
      expect(screen.getByText(/MBBank/)).toBeInTheDocument();
    });

    // Verify polling was triggered
    expect(api.createOrder).toHaveBeenCalledWith({
      machineId: 'VM001',
      productId: '550e8400-e29b-41d4-a716-446655440001',
      quantity: 1,
    });
  });
});
