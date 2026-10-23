import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ReconciliationDecisionModal } from '../components/admin/ReconciliationDecisionModal';
import * as adminApi from '../services/adminApi';

vi.mock('../services/adminApi', async (importOriginal) => {
  const actual = await importOriginal<typeof adminApi>();
  return {
    ...actual,
    submitReconciliationDecision: vi.fn(),
  };
});

describe('ReconciliationDecisionModal Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const defaultProps = {
    orderId: 'normal-order-uuid-99',
    publicOrderCode: 'VM001-20261022-0001',
    currentStatus: 'NEEDS_RECONCILIATION' as const,
    isReservationHeld: true,
    isOpen: true,
    onClose: vi.fn(),
    onSuccess: vi.fn(),
  };

  it('renders all 4 decision options with descriptions', () => {
    render(<ReconciliationDecisionModal {...defaultProps} />);

    expect(screen.getByText('Quyết định đối soát đơn hàng')).toBeInTheDocument();
    expect(screen.getByText('Ghi chú tra soát')).toBeInTheDocument();
    expect(screen.getByText('Duyệt xuất thủ công')).toBeInTheDocument();
    expect(screen.getByText('Chờ hoàn tiền')).toBeInTheDocument();
    expect(screen.getByText('Đã hoàn tiền (Khai báo)')).toBeInTheDocument();
  });

  it('validates that reason is required for any decision', async () => {
    render(<ReconciliationDecisionModal {...defaultProps} />);

    const confirmBtn = screen.getByRole('button', { name: /Xác nhận thông tin/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(
        screen.getByText('Vui lòng nhập lý do hoặc nội dung đối soát.')
      ).toBeInTheDocument();
    });
    expect(adminApi.submitReconciliationDecision).not.toHaveBeenCalled();
  });

  it('requires confirmationReference for REFUND_CONFIRMED and warns about declaration proof', async () => {
    render(<ReconciliationDecisionModal {...defaultProps} />);

    // Click REFUND_CONFIRMED tab
    const refundConfirmedTab = screen.getByText('Đã hoàn tiền (Khai báo)');
    fireEvent.click(refundConfirmedTab);

    // Verify mandatory disclaimer
    expect(
      screen.getByText(/không phải bằng chứng backend đã tự xác minh hoàn tiền ngân hàng/i)
    ).toBeInTheDocument();

    // Verify confirmation reference input appears
    expect(
      screen.getByLabelText(/Mã tham chiếu hoàn tiền/i)
    ).toBeInTheDocument();

    // Fill reason only
    const reasonInput = screen.getByPlaceholderText(/Nhập chi tiết căn cứ đối soát/i);
    fireEvent.change(reasonInput, { target: { value: 'Đã chuyển tiền qua app Vietcombank' } });

    const confirmBtn = screen.getByRole('button', { name: /Xác nhận thông tin/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/Mã tham chiếu hoàn tiền \(mã giao dịch ngân hàng \/ UNC\) là bắt buộc/)
      ).toBeInTheDocument();
    });
    expect(adminApi.submitReconciliationDecision).not.toHaveBeenCalled();
  });

  it('displays non-completion disclaimer when REFUND_PENDING is selected', () => {
    render(<ReconciliationDecisionModal {...defaultProps} />);

    const refundPendingTab = screen.getByText('Chờ hoàn tiền');
    fireEvent.click(refundPendingTab);

    expect(
      screen.getByText(/không được xem là đã hoàn tiền thành công/i)
    ).toBeInTheDocument();
  });

  it('displays manual technician delivery disclaimer when MANUAL_DISPENSE_APPROVED is selected', () => {
    render(<ReconciliationDecisionModal {...defaultProps} />);

    const manualDispenseTab = screen.getByText('Duyệt xuất thủ công');
    fireEvent.click(manualDispenseTab);

    expect(
      screen.getByText(/không thông báo rằng máy đã nhả hàng/i)
    ).toBeInTheDocument();
  });

  it('advances to step 2 (confirmation) and executes decision with anti-double submit protection', async () => {
    const onSuccessMock = vi.fn();
    let resolveSubmit: (val: any) => void = () => {};
    vi.mocked(adminApi.submitReconciliationDecision).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveSubmit = resolve;
        })
    );

    render(<ReconciliationDecisionModal {...defaultProps} onSuccess={onSuccessMock} />);

    // Select MANUAL_DISPENSE_APPROVED
    fireEvent.click(screen.getByText('Duyệt xuất thủ công'));

    // Fill reason
    const reasonInput = screen.getByPlaceholderText(/Nhập chi tiết căn cứ đối soát/i);
    fireEvent.change(reasonInput, { target: { value: 'Kỹ thuật viên Nam đã giao trực tiếp lon nước cho khách tại máy.' } });

    // Step 1 -> Step 2
    fireEvent.click(screen.getByRole('button', { name: /Xác nhận thông tin/i }));

    await waitFor(() => {
      expect(
        screen.getByText('Xác nhận thực hiện quyết định đối soát')
      ).toBeInTheDocument();
      expect(screen.getByText('MANUAL_DISPENSE_APPROVED')).toBeInTheDocument();
    });

    // Step 2: Submit decision
    const submitBtn = screen.getByRole('button', { name: /Gửi quyết định đối soát/i });
    fireEvent.click(submitBtn);

    // Verify double-click prevention
    expect(screen.getByText('Đang gửi quyết định...')).toBeInTheDocument();
    expect(submitBtn).toBeDisabled();

    // Try second click while in-flight
    fireEvent.click(submitBtn);
    expect(adminApi.submitReconciliationDecision).toHaveBeenCalledTimes(1);

    // Resolve API response
    resolveSubmit({
      auditId: 'audit-99',
      orderId: 'normal-order-uuid-99',
      publicOrderCode: 'VM001-20261022-0001',
      action: 'MANUAL_DISPENSE_APPROVED',
      previousStatus: 'NEEDS_RECONCILIATION',
      newStatus: 'COMPLETED',
      reason: 'Kỹ thuật viên Nam đã giao trực tiếp lon nước cho khách tại máy.',
      reservationReleased: false,
      recordedAt: new Date().toISOString(),
      message: 'Decision recorded',
    });

    await waitFor(() => {
      expect(onSuccessMock).toHaveBeenCalledTimes(1);
      expect(defaultProps.onClose).toHaveBeenCalled();
    });
  });

  it('strictly blocks action on protected dev order 4648baae-0c1d-44b5-8974-d09ba3363c05', async () => {
    render(
      <ReconciliationDecisionModal
        {...defaultProps}
        orderId="4648baae-0c1d-44b5-8974-d09ba3363c05"
      />
    );

    // Verify dev order notice is displayed
    expect(screen.getByText(/ĐƠN HÀNG BẢO VỆ DEV/i)).toBeInTheDocument();

    const submitStepBtn = screen.getByRole('button', { name: /Xác nhận thông tin/i });
    expect(submitStepBtn).toBeDisabled();
    expect(adminApi.submitReconciliationDecision).not.toHaveBeenCalled();
  });

  it('strictly blocks action when order is already in terminal COMPLETED state', () => {
    render(
      <ReconciliationDecisionModal
        {...defaultProps}
        currentStatus="COMPLETED"
      />
    );

    expect(screen.getByText(/ĐƠN HÀNG ĐÃ Ở TRẠNG THÁI KẾT THÚC/i)).toBeInTheDocument();
    const submitStepBtn = screen.getByRole('button', { name: /Xác nhận thông tin/i });
    expect(submitStepBtn).toBeDisabled();
  });
});
