import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { CustomerOrderPage } from '../pages/CustomerOrderPage';
import { CartProvider } from '../context/CartContext';
import * as api from '../services/api';
import { ApiError } from '../types/api';

vi.mock('../services/api', () => ({
  getMachine: vi.fn(),
  getProductCatalog: vi.fn(),
  createOrder: vi.fn(),
  getOrder: vi.fn(),
  getOrderStatus: vi.fn(),
  API_BASE_URL: 'http://localhost:8080',
}));

describe('CustomerOrderPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  const mockMachine = {
    machineId: 'VM001',
    name: 'Máy bán hàng tầng 1',
    status: 'ACTIVE' as const,
    message: null,
  };

  const mockProducts = [
    {
      productId: '550e8400-e29b-41d4-a716-446655440001',
      name: 'Nước suối Aqua 500ml',
      description: 'Nước uống tinh khiết',
      imageUrl: null,
      priceVnd: 8000,
      slotNumber: 1,
      stockQuantity: 5,
      available: true,
    },
    {
      productId: '550e8400-e29b-41d4-a716-446655440002',
      name: "Snack Lay's",
      description: null,
      imageUrl: null,
      priceVnd: 15000,
      slotNumber: 2,
      stockQuantity: 3,
      available: true,
    },
    {
      productId: '550e8400-e29b-41d4-a716-446655440003',
      name: 'Kẹo cao su',
      description: null,
      imageUrl: null,
      priceVnd: 5000,
      slotNumber: 3,
      stockQuantity: 0,
      available: false,
    },
  ];

  it('renders products and machine info when API calls succeed', async () => {
    vi.mocked(api.getMachine).mockResolvedValue(mockMachine);
    vi.mocked(api.getProductCatalog).mockResolvedValue({
      machineId: 'VM001',
      products: mockProducts,
    });

    render(
      <CartProvider>
        <CustomerOrderPage initialMachineId="VM001" />
      </CartProvider>
    );

    // Initial loading
    expect(screen.getByRole('banner')).toBeInTheDocument();

    // Wait for data load
    await waitFor(() => {
      expect(screen.getByText('Máy bán hàng tầng 1')).toBeInTheDocument();
      expect(screen.getByText('Nước suối Aqua 500ml')).toBeInTheDocument();
      expect(screen.getByText("Snack Lay's")).toBeInTheDocument();
      expect(screen.getByText('Kẹo cao su')).toBeInTheDocument();
    });

    expect(screen.getByText('8.000 ₫')).toBeInTheDocument();
    expect(screen.getByText('15.000 ₫')).toBeInTheDocument();
  });

  it('displays error message when backend connection fails', async () => {
    vi.mocked(api.getMachine).mockRejectedValue(
      new ApiError(
        'Không thể kết nối đến máy chủ backend. Vui lòng kiểm tra dịch vụ backend đang chạy.',
        'NETWORK_ERROR',
        0
      )
    );

    render(
      <CartProvider>
        <CustomerOrderPage initialMachineId="VM001" />
      </CartProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/Không thể tải danh sách sản phẩm/i)).toBeInTheDocument();
      expect(screen.getByText(/Không thể kết nối đến máy chủ backend/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Thử lại kết nối/i })).toBeInTheDocument();
    });
  });

  it('displays InvalidMachinePage when machine does not exist (404 / MACHINE_NOT_FOUND)', async () => {
    vi.mocked(api.getMachine).mockRejectedValue(
      new ApiError('Máy bán hàng không tồn tại.', 'MACHINE_NOT_FOUND', 404)
    );

    render(
      <CartProvider>
        <CustomerOrderPage initialMachineId="VM999" />
      </CartProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Không tìm thấy máy bán hàng')).toBeInTheDocument();
      expect(screen.getByText(/Máy bán hàng không tồn tại/i)).toBeInTheDocument();
      expect(screen.getByText(/Máy thử nghiệm mặc định \(VM001\)/i)).toBeInTheDocument();
    });
  });

  it('allows adding product to cart, opening cart drawer, and shows Phase 4 notice on checkout', async () => {
    vi.mocked(api.getMachine).mockResolvedValue(mockMachine);
    vi.mocked(api.getProductCatalog).mockResolvedValue({
      machineId: 'VM001',
      products: mockProducts,
    });
    // POST /api/v1/orders returns 404 because Phase 4 is not yet implemented on backend
    vi.mocked(api.createOrder).mockRejectedValue(
      new ApiError('Endpoint chưa được hỗ trợ.', 'NOT_FOUND', 404)
    );

    render(
      <CartProvider>
        <CustomerOrderPage initialMachineId="VM001" />
      </CartProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Nước suối Aqua 500ml')).toBeInTheDocument();
    });

    // Click "Chọn mua" on slot 1
    const addButtons = screen.getAllByRole('button', { name: /Chọn mua/i });
    fireEvent.click(addButtons[0]);

    // Open cart
    const cartButton = screen.getByRole('button', { name: /Xem giỏ hàng/i });
    fireEvent.click(cartButton);

    // Cart drawer shows item and total
    expect(screen.getByText('Giỏ hàng của bạn')).toBeInTheDocument();
    expect(screen.getAllByText('Khay 01').length).toBeGreaterThanOrEqual(2);

    // Click Proceed to checkout
    const checkoutButton = screen.getByRole('button', { name: /Đặt hàng & Thanh toán/i });
    fireEvent.click(checkoutButton);

    // Checkout modal opens
    expect(screen.getByText('Xác nhận đơn hàng')).toBeInTheDocument();
    expect(screen.getByText('Khay xuất hàng:')).toBeInTheDocument();

    // Confirm create order
    const confirmButton = screen.getByRole('button', { name: /Xác nhận đặt hàng/i });
    fireEvent.click(confirmButton);

    // Expect Phase 4 notice to appear
    await waitFor(() => {
      expect(screen.getByText(/Thông báo chuyển giao giai đoạn \(Phase 4 Dependency\)/i)).toBeInTheDocument();
      expect(screen.getByText(/POST \/api\/v1\/orders.*Giai đoạn 4/i)).toBeInTheDocument();
    });
  });
});
