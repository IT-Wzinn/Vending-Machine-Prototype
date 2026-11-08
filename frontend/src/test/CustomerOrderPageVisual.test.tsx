import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { CustomerOrderPage } from '../pages/CustomerOrderPage';
import { CartProvider } from '../context/CartContext';
import * as api from '../services/api';

vi.mock('../services/api', () => ({
  getMachine: vi.fn(),
  getProductCatalog: vi.fn(),
  createOrder: vi.fn(),
  getOrder: vi.fn(),
  getOrderStatus: vi.fn(),
  API_BASE_URL: 'http://localhost:8080',
}));

describe('CustomerOrderPage — Checkpoint 2 Visual & UX Flow Verification', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  const mockMachine = {
    machineId: 'VM001',
    name: 'Máy bán hàng tầng 1 — Fresh Living',
    status: 'ACTIVE' as const,
    message: null,
  };

  const mockProducts = [
    {
      productId: 'prod-01',
      name: 'Nước suối khoáng tự nhiên 500ml',
      description: 'Nước khoáng tinh khiết từ mạch ngầm rừng sinh thái',
      imageUrl: null,
      priceVnd: 10000,
      slotNumber: 1,
      stockQuantity: 4,
      available: true,
    },
    {
      productId: 'prod-02',
      name: 'Trà ô long không đường 450ml',
      description: 'Hương vị trà mộc đậm đà nguyên bản',
      imageUrl: null,
      priceVnd: 15000,
      slotNumber: 2,
      stockQuantity: 1,
      available: true,
    },
    {
      productId: 'prod-03',
      name: 'Cà phê sữa đá đóng lon 240ml',
      description: 'Cà phê Robusta thơm nồng',
      imageUrl: null,
      priceVnd: 20000,
      slotNumber: 3,
      stockQuantity: 0,
      available: false,
    },
  ];

  it('guarantees SylvaBackground has pointer-events-none and does not block UI clicks', async () => {
    vi.mocked(api.getMachine).mockResolvedValue(mockMachine);
    vi.mocked(api.getProductCatalog).mockResolvedValue({ machineId: 'VM001', products: mockProducts });

    render(
      <CartProvider>
        <CustomerOrderPage initialMachineId="VM001" />
      </CartProvider>
    );

    // Verify background container exists and has pointer-events-none
    const bg = screen.getByTestId('sylva-background-container');
    expect(bg).toHaveClass('pointer-events-none');
    expect(bg).toHaveClass('fixed');
    expect(bg).toHaveClass('z-0');

    // Verify main content is above background with relative z-10
    const main = screen.getByRole('main');
    expect(main).toHaveClass('relative');
    expect(main).toHaveClass('z-10');
  });

  it('correctly enables buying in-stock products and disables out-of-stock products', async () => {
    vi.mocked(api.getMachine).mockResolvedValue(mockMachine);
    vi.mocked(api.getProductCatalog).mockResolvedValue({ machineId: 'VM001', products: mockProducts });

    render(
      <CartProvider>
        <CustomerOrderPage initialMachineId="VM001" />
      </CartProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Nước suối khoáng tự nhiên 500ml')).toBeInTheDocument();
      expect(screen.getByText('Cà phê sữa đá đóng lon 240ml')).toBeInTheDocument();
    });

    // Slot 1 (in-stock) is enabled
    const buyButtons = screen.getAllByRole('button', { name: /Chọn mua/i });
    const slot1Btn = buyButtons[0];
    expect(slot1Btn).not.toBeDisabled();
    expect(slot1Btn).toHaveClass('min-h-[44px]'); // Mobile touch-target compliance

    // Slot 3 (out-of-stock) is disabled
    const outOfStockBtn = screen.getByRole('button', { name: /Hết hàng/i });
    expect(outOfStockBtn).toBeDisabled();
    expect(outOfStockBtn).toHaveClass('cursor-not-allowed');

    // Click buy slot 1
    fireEvent.click(slot1Btn);
    await waitFor(() => {
      expect(screen.getByText('Đã thêm')).toBeInTheDocument();
    });

    // Verify cart badge reflects item
    await waitFor(() => {
      expect(document.getElementById('cart-badge-count')).toHaveTextContent('1');
    });
  });

  it('allows opening and closing the cart drawer without glitch', async () => {
    vi.mocked(api.getMachine).mockResolvedValue(mockMachine);
    vi.mocked(api.getProductCatalog).mockResolvedValue({ machineId: 'VM001', products: mockProducts });

    render(
      <CartProvider>
        <CustomerOrderPage initialMachineId="VM001" />
      </CartProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Nước suối khoáng tự nhiên 500ml')).toBeInTheDocument();
    });

    // Open cart
    const cartButton = screen.getByRole('button', { name: /Xem giỏ hàng/i });
    fireEvent.click(cartButton);

    expect(screen.getByText('Giỏ hàng của bạn')).toBeInTheDocument();

    // Close cart drawer
    const closeBtn = screen.getByRole('button', { name: /Đóng giỏ hàng/i });
    fireEvent.click(closeBtn);

    await waitFor(() => {
      expect(screen.queryByText('Giỏ hàng của bạn')).not.toBeInTheDocument();
    });
  });

  it('renders elegant empty catalog state when machine has no items', async () => {
    vi.mocked(api.getMachine).mockResolvedValue(mockMachine);
    vi.mocked(api.getProductCatalog).mockResolvedValue({ machineId: 'VM001', products: [] });

    render(
      <CartProvider>
        <CustomerOrderPage initialMachineId="VM001" />
      </CartProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Không có sản phẩm')).toBeInTheDocument();
      expect(screen.getByText(/Máy hiện chưa được nạp sản phẩm vào các khay lò xo/i)).toBeInTheDocument();
    });
  });
});
