import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ReconciliationDashboardPage } from '../pages/admin/ReconciliationDashboardPage';
import { AdminAuthProvider } from '../context/AdminAuthContext';
import * as adminApi from '../services/adminApi';
import type { Page, ReconciliationOrderSummary } from '../types/admin';

vi.mock('../services/adminApi', async (importOriginal) => {
  const actual = await importOriginal<typeof adminApi>();
  return {
    ...actual,
    fetchReconciliationOrders: vi.fn(),
  };
});

describe('ReconciliationDashboardPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockOrdersPage: Page<ReconciliationOrderSummary> = {
    content: [
      {
        orderId: 'order-uuid-1',
        publicOrderCode: 'VM001-20261022-0001',
        status: 'NEEDS_RECONCILIATION',
        machineCode: 'VM001',
        productName: 'Trà xanh không độ',
        slotNumber: 1,
        quantity: 1,
        totalAmountVnd: 12000,
        transferReference: 'VM001AABB',
        paidAt: '2026-10-22T04:15:00Z',
        createdAt: '2026-10-22T04:00:00Z',
        hasDispenseCommand: true,
        paymentVerified: true,
        isReservationHeld: true,
      },
      {
        orderId: 'order-uuid-2',
        publicOrderCode: 'VM001-20261022-0002',
        status: 'DISPENSE_FAILED',
        machineCode: 'VM001',
        productName: 'Nước tăng lực Redbull',
        slotNumber: 2,
        quantity: 1,
        totalAmountVnd: 15000,
        transferReference: 'VM001CCDD',
        paidAt: '2026-10-22T04:20:00Z',
        createdAt: '2026-10-22T04:05:00Z',
        hasDispenseCommand: true,
        paymentVerified: true,
        isReservationHeld: false,
      },
    ],
    totalPages: 2,
    totalElements: 12,
    size: 10,
    number: 0,
    first: true,
    last: false,
    empty: false,
  };

  const renderDashboard = (onSelectOrder = vi.fn()) => {
    return render(
      <AdminAuthProvider>
        <ReconciliationDashboardPage onSelectOrder={onSelectOrder} />
      </AdminAuthProvider>
    );
  };

  it('renders reconciliation orders list with order codes, amounts, and badges', async () => {
    vi.mocked(adminApi.fetchReconciliationOrders).mockResolvedValue(mockOrdersPage);

    renderDashboard();

    // Verify loading skeleton appears initially
    expect(screen.getByText('Danh sách đối soát đơn hàng')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('VM001-20261022-0001')).toBeInTheDocument();
      expect(screen.getByText('VM001-20261022-0002')).toBeInTheDocument();
      expect(screen.getByText('12.000 ₫')).toBeInTheDocument();
      expect(screen.getByText('15.000 ₫')).toBeInTheDocument();
    });

    // Check status badges
    expect(screen.getByText('CẦN ĐỐI SOÁT')).toBeInTheDocument();
    expect(screen.getByText('LỖI NHẢ HÀNG')).toBeInTheDocument();

    // Check reservation held badges (reflects backend boolean)
    expect(screen.getByText('ĐANG GIỮ CHỖ KHO (1)')).toBeInTheDocument();
    expect(screen.getByText('ĐÃ NHẢ GIỮ CHỖ (0)')).toBeInTheDocument();
  });

  it('renders empty state when no orders match filter', async () => {
    vi.mocked(adminApi.fetchReconciliationOrders).mockResolvedValue({
      content: [],
      totalPages: 0,
      totalElements: 0,
      size: 10,
      number: 0,
      first: true,
      last: true,
      empty: true,
    });

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText('Không tìm thấy đơn hàng nào')).toBeInTheDocument();
    });
  });

  it('handles API error and allows retry', async () => {
    vi.mocked(adminApi.fetchReconciliationOrders)
      .mockRejectedValueOnce(new Error('Máy chủ gián đoạn tạm thời'))
      .mockResolvedValueOnce(mockOrdersPage);

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText('Lỗi tải dữ liệu đối soát')).toBeInTheDocument();
      expect(screen.getByText('Máy chủ gián đoạn tạm thời')).toBeInTheDocument();
    });

    // Click retry button
    const retryBtn = screen.getByRole('button', { name: /Thử lại/i });
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByText('VM001-20261022-0001')).toBeInTheDocument();
    });
  });

  it('handles pagination navigation (next/previous page buttons)', async () => {
    vi.mocked(adminApi.fetchReconciliationOrders).mockResolvedValue(mockOrdersPage);

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText('VM001-20261022-0001')).toBeInTheDocument();
    });

    expect(screen.getByText(/Tổng cộng/i)).toBeInTheDocument();

    const prevBtn = screen.getByRole('button', { name: /Trang trước/i });
    const nextBtn = screen.getByRole('button', { name: /Trang sau/i });

    expect(prevBtn).toBeDisabled(); // first page
    expect(nextBtn).not.toBeDisabled();

    // Click next page
    fireEvent.click(nextBtn);

    await waitFor(() => {
      expect(adminApi.fetchReconciliationOrders).toHaveBeenCalledWith(
        expect.objectContaining({ page: 1 })
      );
    });
  });

  it('filters by order status dropdown selection', async () => {
    vi.mocked(adminApi.fetchReconciliationOrders).mockResolvedValue(mockOrdersPage);

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText('VM001-20261022-0001')).toBeInTheDocument();
    });

    const filterSelect = screen.getByRole('combobox');
    fireEvent.change(filterSelect, { target: { value: 'PAID' } });

    await waitFor(() => {
      expect(adminApi.fetchReconciliationOrders).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'PAID', page: 0 })
      );
    });
  });

  it('invokes onSelectOrder callback when clicking an order card or detail button', async () => {
    const onSelectOrderMock = vi.fn();
    vi.mocked(adminApi.fetchReconciliationOrders).mockResolvedValue(mockOrdersPage);

    renderDashboard(onSelectOrderMock);

    await waitFor(() => {
      expect(screen.getByText('VM001-20261022-0001')).toBeInTheDocument();
    });

    const detailButtons = screen.getAllByRole('button', { name: /Chi tiết/i });
    fireEvent.click(detailButtons[0]);

    expect(onSelectOrderMock).toHaveBeenCalledWith('order-uuid-1');
  });
});
