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

describe('FreshVend Navigation & Redesigned Views', () => {
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
      productId: 'prod-001',
      name: 'Nước suối Aqua 500ml',
      description: 'Nước uống tinh khiết',
      imageUrl: null,
      priceVnd: 8000,
      slotNumber: 1,
      stockQuantity: 5,
      available: true,
    },
    {
      productId: 'prod-002',
      name: "Snack Lay's vị tự nhiên",
      description: "Snack khoai tây Lay's giòn tan",
      imageUrl: null,
      priceVnd: 15000,
      slotNumber: 2,
      stockQuantity: 3,
      available: true,
    },
    {
      productId: 'prod-003',
      name: 'Kẹo cao su Doublemint',
      description: 'Kẹo cao su Doublemint thơm mát',
      imageUrl: null,
      priceVnd: 5000,
      slotNumber: 3,
      stockQuantity: 2,
      available: true,
    },
  ];

  it('renders Hero Banner and mockup category filters on Home tab', async () => {
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

    await waitFor(() => {
      expect(screen.getByText(/Đặt hàng nhanh chóng/i)).toBeInTheDocument();
      expect(screen.getByText('Chạm')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Bắt đầu đặt hàng/i })).toBeInTheDocument();
      expect(screen.getByText('🥛 Nước giải khát')).toBeInTheDocument();
      expect(screen.getByText('🍿 Snack & Bánh')).toBeInTheDocument();
      expect(screen.getByText('🍬 Đồ ăn nhẹ')).toBeInTheDocument();
    });
  });

  it('switches smoothly to Support view and toggles FAQ', async () => {
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

    await waitFor(() => {
      expect(screen.getByText('Nước suối Aqua 500ml')).toBeInTheDocument();
    });

    // Click "Hỗ trợ" in navbar
    const supportNavButtons = screen.getAllByRole('button', { name: /Hỗ trợ/i });
    fireEvent.click(supportNavButtons[0]);

    // Verify Support view is rendered
    expect(screen.getByText('Trung tâm Hỗ trợ Khách hàng')).toBeInTheDocument();
    expect(screen.getByText('1900 8888')).toBeInTheDocument();
    expect(screen.getByText('Quy trình mua sắm 3 bước đơn giản')).toBeInTheDocument();
    expect(screen.getByText(/Tài khoản ngân hàng đã bị trừ tiền/i)).toBeInTheDocument();

    // Click "Quay lại đặt hàng"
    const backBtn = screen.getByRole('button', { name: /Quay lại đặt hàng/i });
    fireEvent.click(backBtn);

    // Should return to Home
    expect(screen.getByText(/Đặt hàng nhanh chóng/i)).toBeInTheDocument();
  });

  it('switches to About Us view and displays technology pillars', async () => {
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

    await waitFor(() => {
      expect(screen.getByText('Nước suối Aqua 500ml')).toBeInTheDocument();
    });

    // Click "Về chúng tôi" in navbar
    const aboutNavButtons = screen.getAllByRole('button', { name: /Về chúng tôi/i });
    fireEvent.click(aboutNavButtons[0]);

    // Verify About view
    expect(screen.getByRole('heading', { level: 2, name: /Về FreshVend/i })).toBeInTheDocument();
    expect(screen.getByText('1. IoT & Vi Điều Khiển')).toBeInTheDocument();
    expect(screen.getByText('2. Mua Sắm Touchless')).toBeInTheDocument();
    expect(screen.getByText('3. Thanh Toán Tức Thì')).toBeInTheDocument();

    // Click "Quay lại đặt hàng"
    const backBtn = screen.getByRole('button', { name: /Quay lại đặt hàng/i });
    fireEvent.click(backBtn);

    expect(screen.getByText(/Đặt hàng nhanh chóng/i)).toBeInTheDocument();
  });

  it('filters product list when clicking category filter pills', async () => {
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

    await waitFor(() => {
      expect(screen.getByText('Nước suối Aqua 500ml')).toBeInTheDocument();
      expect(screen.getByText("Snack Lay's vị tự nhiên")).toBeInTheDocument();
      expect(screen.getByText('Kẹo cao su Doublemint')).toBeInTheDocument();
    });

    // Click "Nước giải khát"
    fireEvent.click(screen.getByText('🥛 Nước giải khát'));
    expect(screen.getByText('Nước suối Aqua 500ml')).toBeInTheDocument();
    expect(screen.queryByText("Snack Lay's vị tự nhiên")).not.toBeInTheDocument();
    expect(screen.queryByText('Kẹo cao su Doublemint')).not.toBeInTheDocument();

    // Click "Xem tất cả" to reset
    fireEvent.click(screen.getByText('Xem tất cả'));
    expect(screen.getByText('Nước suối Aqua 500ml')).toBeInTheDocument();
    expect(screen.getByText("Snack Lay's vị tự nhiên")).toBeInTheDocument();
    expect(screen.getByText('Kẹo cao su Doublemint')).toBeInTheDocument();
  });
});

