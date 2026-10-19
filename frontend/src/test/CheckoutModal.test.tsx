import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { CheckoutModal } from '../components/CheckoutModal';
import { CartProvider, useCart } from '../context/CartContext';
import * as api from '../services/api';
import type { Order } from '../types/order';

vi.mock('../services/api', () => ({
  createOrder: vi.fn(),
  getOrderStatus: vi.fn(),
}));

const mockProduct = {
  productId: 'prod-123',
  name: 'Trà ô long Tea+ 450ml',
  description: 'Trà ô long thanh mát',
  imageUrl: null,
  priceVnd: 12000,
  slotNumber: 1,
  stockQuantity: 5,
  available: true,
};

const sampleOrder: Order = {
  orderId: 'ord-abc-123',
  publicOrderCode: 'VM001-20261019-A1B2',
  machineId: 'VM001',
  product: {
    productId: 'prod-123',
    name: 'Trà ô long Tea+ 450ml',
    priceVnd: 12000,
  },
  quantity: 1,
  totalAmountVnd: 12000,
  status: 'PENDING_PAYMENT',
  payment: {
    transferReference: 'VM001A1B2',
    paymentQrData: '00020101021238540010A00000072701240006970422011003456789990208QRIBFTTA53037045405120005802VN62120808VM001A1B26304ABCD',
    bankAccountNumber: '0345678999',
    bankAccountName: 'SMART VENDING MACHINE',
    bankName: 'MBBank',
    amountVnd: 12000,
    paymentExpiresAt: '2026-10-19T10:15:00Z',
    paidAt: null,
  },
  createdAt: '2026-10-19T10:00:00Z',
};

// Helper component that initializes cart with a product
const CartSetup = ({ children }: { children: React.ReactNode }) => {
  const { addToCart } = useCart();
  return (
    <div>
      <button onClick={() => addToCart(mockProduct)} data-testid="add-btn">
        Add
      </button>
      {children}
    </div>
  );
};

describe('CheckoutModal Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders confirmation view with product details and no self-mark-paid button', async () => {
    const handleClose = vi.fn();
    render(
      <CartProvider>
        <CartSetup>
          <CheckoutModal machineId="VM001" isOpen={true} onClose={handleClose} />
        </CartSetup>
      </CartProvider>
    );

    // Add item to cart
    fireEvent.click(screen.getByTestId('add-btn'));

    expect(screen.getByText('Xác nhận đơn hàng')).toBeInTheDocument();
    expect(screen.getByText('Trà ô long Tea+ 450ml')).toBeInTheDocument();
    expect(screen.getByText('12.000 ₫')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Xác nhận đặt hàng/i })).toBeInTheDocument();

    // Verify absence of any self-marking or fake payment button
    expect(screen.queryByText(/Tôi đã chuyển khoản/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Đã thanh toán/i)).not.toBeInTheDocument();
  });

  it('creates order, displays VietQR, and polls getOrderStatus until PAID', async () => {
    vi.mocked(api.createOrder).mockResolvedValueOnce(sampleOrder);
    vi.mocked(api.getOrderStatus).mockResolvedValueOnce({
      orderId: 'ord-abc-123',
      status: 'PAID',
      updatedAt: '2026-10-19T10:01:00Z',
      message: 'Thanh toán thành công. Đang chuẩn bị sản phẩm cho bạn...',
    });

    const handleClose = vi.fn();
    render(
      <CartProvider>
        <CartSetup>
          <CheckoutModal machineId="VM001" isOpen={true} onClose={handleClose} />
        </CartSetup>
      </CartProvider>
    );

    // Add product and click create order
    fireEvent.click(screen.getByTestId('add-btn'));
    fireEvent.click(screen.getByRole('button', { name: /Xác nhận đặt hàng/i }));

    // Wait for QR code view to appear
    await waitFor(() => {
      expect(screen.getByText('VM001-20261019-A1B2')).toBeInTheDocument();
    });

    expect(screen.getByText(/MBBank/)).toBeInTheDocument();
    expect(screen.getByText(/Nội dung CK: VM001A1B2/)).toBeInTheDocument();
    expect(screen.getByText(/Đang chờ tín hiệu thanh toán từ ngân hàng/)).toBeInTheDocument();

    // Fast-forward / trigger polling response
    await waitFor(
      () => {
        expect(screen.getByTestId('payment-success-view')).toBeInTheDocument();
      },
      { timeout: 4000 }
    );

    expect(screen.getByText('Thanh toán đã được xác minh thành công!')).toBeInTheDocument();
    expect(screen.getByText('ĐÃ THANH TOÁN (PAID)')).toBeInTheDocument();
  });

  it('displays EXPIRED view when backend order status transitions to EXPIRED', async () => {
    vi.mocked(api.createOrder).mockResolvedValueOnce(sampleOrder);
    vi.mocked(api.getOrderStatus).mockResolvedValueOnce({
      orderId: 'ord-abc-123',
      status: 'EXPIRED',
      updatedAt: '2026-10-19T10:15:00Z',
      message: 'Đơn hàng đã hết hạn thanh toán.',
    });

    render(
      <CartProvider>
        <CartSetup>
          <CheckoutModal machineId="VM001" isOpen={true} onClose={vi.fn()} />
        </CartSetup>
      </CartProvider>
    );

    fireEvent.click(screen.getByTestId('add-btn'));
    fireEvent.click(screen.getByRole('button', { name: /Xác nhận đặt hàng/i }));

    await waitFor(() => {
      expect(screen.getByText('VM001-20261019-A1B2')).toBeInTheDocument();
    });

    await waitFor(
      () => {
        expect(screen.getByTestId('payment-expired-view')).toBeInTheDocument();
      },
      { timeout: 4000 }
    );

    expect(screen.getByText('Đơn hàng đã hết hạn thanh toán')).toBeInTheDocument();
  });

  it('displays NEEDS_RECONCILIATION view when backend order status transitions to NEEDS_RECONCILIATION', async () => {
    vi.mocked(api.createOrder).mockResolvedValueOnce(sampleOrder);
    vi.mocked(api.getOrderStatus).mockResolvedValueOnce({
      orderId: 'ord-abc-123',
      status: 'NEEDS_RECONCILIATION',
      updatedAt: '2026-10-19T10:16:00Z',
      message: 'Đơn hàng cần đối soát hoàn tiền.',
    });

    render(
      <CartProvider>
        <CartSetup>
          <CheckoutModal machineId="VM001" isOpen={true} onClose={vi.fn()} />
        </CartSetup>
      </CartProvider>
    );

    fireEvent.click(screen.getByTestId('add-btn'));
    fireEvent.click(screen.getByRole('button', { name: /Xác nhận đặt hàng/i }));

    await waitFor(() => {
      expect(screen.getByText('VM001-20261019-A1B2')).toBeInTheDocument();
    });

    await waitFor(
      () => {
        expect(screen.getByTestId('payment-reconciliation-view')).toBeInTheDocument();
      },
      { timeout: 4000 }
    );

    expect(screen.getByText('Giao dịch cần đối soát')).toBeInTheDocument();
  });
});
