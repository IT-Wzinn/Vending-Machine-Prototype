import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ReconciliationOrderDetailPage } from '../pages/admin/ReconciliationOrderDetailPage';
import { AdminAuthProvider } from '../context/AdminAuthContext';
import * as adminApi from '../services/adminApi';
import type { ReconciliationOrderDetail } from '../types/admin';

vi.mock('../services/adminApi', async (importOriginal) => {
  const actual = await importOriginal<typeof adminApi>();
  return {
    ...actual,
    fetchReconciliationOrderDetail: vi.fn(),
    submitReconciliationDecision: vi.fn(),
  };
});

describe('ReconciliationOrderDetailPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const baseDetail: ReconciliationOrderDetail = {
    orderId: 'uuid-order-123',
    publicOrderCode: 'VM001-20261022-9999',
    status: 'NEEDS_RECONCILIATION',
    totalAmountVnd: 15000,
    quantity: 1,
    transferReference: 'VM001XYZ',
    createdAt: '2026-10-22T04:00:00Z',
    paymentExpiresAt: '2026-10-22T04:10:00Z',
    paidAt: '2026-10-22T04:05:00Z',
    dispensingStartedAt: '2026-10-22T04:05:02Z',
    completedAt: undefined,
    isReservationHeld: true,
    machineId: 'm-1',
    machineCode: 'VM001',
    machineName: 'Máy bán hàng tầng 1',
    productId: 'p-1',
    productName: 'Cà phê Highland lon',
    slotNumber: 3,
    currentStockQuantity: 4,
    currentReservedQuantity: 1,
    unitPriceVnd: 15000,
    paymentTransactions: [
      {
        transactionId: 'tx-1',
        providerTransactionId: 'FT261022123456',
        amountVnd: 15000,
        transferReference: 'VM001XYZ',
        verificationStatus: 'VERIFIED',
        transactionStatus: 'COMPLETED',
        receivedAt: '2026-10-22T04:05:00Z',
        createdAt: '2026-10-22T04:05:01Z',
      },
    ],
    dispenseCommands: [
      {
        commandId: 'cmd-1',
        slotNumber: 3,
        status: 'FAILED',
        attemptCount: 3,
        failureCode: 'MOTOR_TIMEOUT',
        failureMessage: 'Động cơ lò xo khay 3 không quay tới vị trí cảm biến quang học.',
        publishedAt: '2026-10-22T04:05:05Z',
        acknowledgedAt: '2026-10-22T04:05:07Z',
        finishedAt: '2026-10-22T04:05:25Z',
        createdAt: '2026-10-22T04:05:02Z',
      },
    ],
    deviceEvents: [],
    auditLogs: [
      {
        auditId: 'audit-1',
        adminUsername: 'operator_dan',
        action: 'INVESTIGATION_NOTE',
        previousStatus: 'NEEDS_RECONCILIATION',
        newStatus: 'NEEDS_RECONCILIATION',
        reason: 'Đã gọi điện thoại khách hàng xác nhận sự cố kẹt hàng lò xo.',
        createdAt: '2026-10-22T04:12:00Z',
      },
    ],
  };

  const renderDetailPage = (onBack = vi.fn()) => {
    return render(
      <AdminAuthProvider>
        <ReconciliationOrderDetailPage orderId="uuid-order-123" onBack={onBack} />
      </AdminAuthProvider>
    );
  };

  it('classifies scenario as "Thanh toán thành công nhưng chưa xác nhận xuất hàng" for paid failed dispense', async () => {
    vi.mocked(adminApi.fetchReconciliationOrderDetail).mockResolvedValue(baseDetail);

    renderDetailPage();

    await waitFor(
      () => {
        expect(
          screen.getByText('Thanh toán thành công nhưng chưa xác nhận xuất hàng')
        ).toBeInTheDocument();
        expect(screen.getByText('VM001-20261022-9999')).toBeInTheDocument();
        expect(screen.getByText('CẦN ĐỐI SOÁT')).toBeInTheDocument();
        expect(screen.getByText('ĐANG GIỮ CHỖ KHO (1)')).toBeInTheDocument();
        expect(screen.getByText(/Cà phê Highland lon/)).toBeInTheDocument();
        expect(screen.getByText('FT261022123456')).toBeInTheDocument();
        expect(screen.getByText(/MOTOR_TIMEOUT/)).toBeInTheDocument();
        expect(screen.getByText(/Đã gọi điện thoại khách hàng/)).toBeInTheDocument();
      },
      { timeout: 5000 }
    );
  });

  it('classifies scenario as "Thanh toán muộn sau khi đơn đã hết hạn" when transaction is late', async () => {
    const lateDetail: ReconciliationOrderDetail = {
      ...baseDetail,
      paymentExpiresAt: '2026-10-22T04:10:00Z',
      paidAt: '2026-10-22T04:15:00Z', // 5 minutes after expiration
      isReservationHeld: false, // Stock was released upon expiration
      paymentTransactions: [
        {
          transactionId: 'tx-2',
          providerTransactionId: 'FT-LATE-123',
          amountVnd: 15000,
          verificationStatus: 'LATE_PAYMENT',
          transactionStatus: 'COMPLETED',
          receivedAt: '2026-10-22T04:15:00Z',
          createdAt: '2026-10-22T04:15:01Z',
        },
      ],
      dispenseCommands: [],
    };

    vi.mocked(adminApi.fetchReconciliationOrderDetail).mockResolvedValue(lateDetail);

    renderDetailPage();

    await waitFor(() => {
      expect(
        screen.getByText('Thanh toán muộn sau khi đơn đã hết hạn')
      ).toBeInTheDocument();
      expect(screen.getByText('ĐÃ NHẢ GIỮ CHỖ (0)')).toBeInTheDocument();
      expect(screen.getByText('Thanh toán muộn')).toBeInTheDocument();
    });
  });

  it('opens decision modal when clicking "Ra quyết định đối soát"', async () => {
    vi.mocked(adminApi.fetchReconciliationOrderDetail).mockResolvedValue(baseDetail);

    renderDetailPage();

    await waitFor(() => {
      expect(screen.getByText('VM001-20261022-9999')).toBeInTheDocument();
    });

    const decisionBtn = screen.getByRole('button', { name: /Ra quyết định đối soát/i });
    fireEvent.click(decisionBtn);

    expect(screen.getByText('Quyết định đối soát đơn hàng')).toBeInTheDocument();
  });

  it('calls onBack callback when clicking "Quay lại danh sách"', async () => {
    const onBackMock = vi.fn();
    vi.mocked(adminApi.fetchReconciliationOrderDetail).mockResolvedValue(baseDetail);

    renderDetailPage(onBackMock);

    await waitFor(() => {
      expect(screen.getByText('VM001-20261022-9999')).toBeInTheDocument();
    });

    const backBtn = screen.getByRole('button', { name: /Quay lại danh sách/i });
    fireEvent.click(backBtn);

    expect(onBackMock).toHaveBeenCalledTimes(1);
  });
});
