import React, { useEffect, useState, useCallback } from 'react';
import { AdminLayout } from '../../components/admin/AdminLayout';
import {
  OrderStatusBadge,
  VerificationStatusBadge,
  ReservationHeldBadge,
} from '../../components/admin/StatusBadge';
import { ReconciliationDecisionModal } from '../../components/admin/ReconciliationDecisionModal';
import type {
  ReconciliationOrderDetail,
  ReconciliationDecisionResponse,
} from '../../types/admin';
import { fetchReconciliationOrderDetail } from '../../services/adminApi';
import { formatVnd, formatDateTime } from '../../utils/formatters';
import {
  ArrowLeft,
  Clock,
  CreditCard,
  Cpu,
  History,
  AlertTriangle,
  CheckCircle2,
  AlertCircle,
  HelpCircle,
  ShieldCheck,
  Package,
} from 'lucide-react';

interface ReconciliationOrderDetailPageProps {
  orderId: string;
  onBack: () => void;
}

export const ReconciliationOrderDetailPage: React.FC<ReconciliationOrderDetailPageProps> = ({
  orderId,
  onBack,
}) => {
  const [order, setOrder] = useState<ReconciliationOrderDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [isDecisionModalOpen, setIsDecisionModalOpen] = useState<boolean>(false);
  const [successBanner, setSuccessBanner] = useState<string | null>(null);

  const loadOrderDetail = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await fetchReconciliationOrderDetail(orderId);
      setOrder(data);
      setLoading(false);
    } catch (err: unknown) {
      setLoading(false);
      const msg =
        err instanceof Error ? err.message : 'Không thể tải thông tin chi tiết đơn hàng đối soát.';
      setError(msg);
    }
  }, [orderId]);

  useEffect(() => {
    loadOrderDetail();
  }, [loadOrderDetail]);

  const handleDecisionSuccess = (response: ReconciliationDecisionResponse) => {
    setSuccessBanner(
      `Đã ghi nhận thành công quyết định [${response.action}] cho đơn hàng ${response.publicOrderCode}.`
    );
    // Reload order detail to show updated audit logs and status
    loadOrderDetail();
  };

  /**
   * Determine Scenario Classification as required by product spec:
   * 1. Paid but unconfirmed dispense
   * 2. Late payment after order expiration
   * 3. Needs further investigation
   */
  const getReconciliationScenario = (detail: ReconciliationOrderDetail) => {
    const hasLateTx = detail.paymentTransactions?.some(
      (tx) => tx.verificationStatus === 'LATE_PAYMENT'
    );
    const isLateByTimestamp =
      detail.paidAt &&
      detail.paymentExpiresAt &&
      new Date(detail.paidAt).getTime() > new Date(detail.paymentExpiresAt).getTime();

    if (hasLateTx || isLateByTimestamp) {
      return {
        type: 'LATE_PAYMENT',
        title: 'Thanh toán muộn sau khi đơn đã hết hạn',
        description:
          'Khách hàng chuyển khoản sau thời gian đơn hàng có hiệu lực. Giữ chỗ kho có thể đã được giải phóng tự động bởi hệ thống.',
        color: 'rose',
        icon: Clock,
      };
    }

    const hasFailedDispense = detail.dispenseCommands?.some(
      (cmd) => cmd.status === 'FAILED' || cmd.status === 'TIMEOUT' || cmd.failureCode
    );
    const hasPendingDispense =
      detail.status === 'NEEDS_RECONCILIATION' ||
      detail.status === 'DISPENSE_FAILED' ||
      (detail.status === 'PAID' && !detail.completedAt);

    if (detail.paidAt && (hasFailedDispense || hasPendingDispense)) {
      return {
        type: 'UNCONFIRMED_DISPENSE',
        title: 'Thanh toán thành công nhưng chưa xác nhận xuất hàng',
        description:
          'Tiền đã vào tài khoản nhưng thiết bị bán hàng gặp sự cố phần cứng, mất kết nối mạng hoặc không phản hồi xác nhận nhả hàng.',
        color: 'amber',
        icon: AlertTriangle,
      };
    }

    return {
      type: 'INVESTIGATION_NEEDED',
      title: 'Đơn hàng đang cần điều tra thêm',
      description:
        'Đơn hàng có các thông tin cần đối soát bổ sung hoặc đang chờ xác minh từ đối tác thanh toán / kỹ thuật viên hiện trường.',
      color: 'blue',
      icon: HelpCircle,
    };
  };

  return (
    <AdminLayout
      title={order ? `Đơn hàng ${order.publicOrderCode}` : 'Chi tiết đơn đối soát'}
      subtitle="Thông tin giao dịch thanh toán, lịch sử thiết bị và quyết định xử lý"
      breadcrumbs={[
        { label: 'Danh sách đối soát', onClick: onBack },
        { label: order ? order.publicOrderCode : 'Đang tải...' },
      ]}
      onRefresh={loadOrderDetail}
      isRefreshing={loading}
    >
      {/* Back button */}
      <div className="mb-4">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex items-center gap-1.5 text-xs font-bold text-brand-primary hover:text-brand-primary-hover hover:underline transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Quay lại danh sách</span>
        </button>
      </div>

      {/* Success Notification Banner */}
      {successBanner && (
        <div className="mb-6 p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 flex items-start justify-between gap-3">
          <div className="flex items-start gap-2.5">
            <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
            <p className="text-xs font-semibold">{successBanner}</p>
          </div>
          <button
            type="button"
            onClick={() => setSuccessBanner(null)}
            className="text-xs font-bold text-emerald-700 hover:text-emerald-900"
          >
            Đóng
          </button>
        </div>
      )}

      {/* Error Banner */}
      {error && (
        <div className="mb-6 p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 flex items-start justify-between gap-3">
          <div className="flex items-start gap-2.5">
            <AlertCircle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold text-sm">Không thể tải thông tin đơn</p>
              <p className="text-xs text-rose-700 mt-0.5">{error}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={loadOrderDetail}
            className="px-3 py-1.5 text-xs font-bold rounded-lg bg-rose-600 text-white hover:bg-rose-700 transition-colors shrink-0"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* Loading Skeleton */}
      {loading && !order && (
        <div className="space-y-6 animate-pulse">
          <div className="h-24 bg-white rounded-2xl border border-brand-border p-6" />
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="h-64 bg-white rounded-2xl border border-brand-border p-6" />
            <div className="h-64 bg-white rounded-2xl border border-brand-border p-6 md:col-span-2" />
          </div>
        </div>
      )}

      {order && (
        <div className="space-y-6">
          {/* SCENARIO CLASSIFICATION BANNER */}
          {(() => {
            const scenario = getReconciliationScenario(order);
            const ScenarioIcon = scenario.icon;
            const bgClass =
              scenario.color === 'rose'
                ? 'bg-rose-50 border-rose-200 text-rose-900'
                : scenario.color === 'amber'
                ? 'bg-amber-50 border-amber-200 text-amber-900'
                : 'bg-blue-50 border-blue-200 text-blue-900';
            const iconClass =
              scenario.color === 'rose'
                ? 'text-rose-600'
                : scenario.color === 'amber'
                ? 'text-amber-600'
                : 'text-blue-600';

            return (
              <div
                className={`p-4 sm:p-5 rounded-2xl border ${bgClass} shadow-sm flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4`}
              >
                <div className="flex items-start gap-3.5">
                  <div className="p-2.5 rounded-xl bg-white shadow-xs shrink-0 mt-0.5">
                    <ScenarioIcon className={`w-6 h-6 ${iconClass}`} />
                  </div>
                  <div>
                    <span className="text-[11px] font-extrabold uppercase tracking-wider block opacity-75">
                      Phân loại tình huống đối soát
                    </span>
                    <h3 className="text-base sm:text-lg font-black tracking-tight mt-0.5">
                      {scenario.title}
                    </h3>
                    <p className="text-xs sm:text-sm opacity-90 mt-1 max-w-2xl leading-relaxed">
                      {scenario.description}
                    </p>
                  </div>
                </div>

                {/* Primary Action Button */}
                <button
                  type="button"
                  onClick={() => setIsDecisionModalOpen(true)}
                  className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-5 py-3 rounded-xl bg-brand-primary text-white text-xs sm:text-sm font-extrabold shadow-card hover:bg-brand-primary-hover transition-all shrink-0"
                >
                  <ShieldCheck className="w-4 h-4 text-brand-accent" />
                  <span>Ra quyết định đối soát</span>
                </button>
              </div>
            );
          })()}

          {/* TOP OVERVIEW CARD */}
          <div className="bg-white rounded-2xl border border-brand-border p-6 shadow-sm">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
              {/* Order Status */}
              <div>
                <span className="block text-xs text-brand-text-subtle mb-1">Trạng thái đơn hàng</span>
                <div className="flex items-center gap-2">
                  <OrderStatusBadge status={order.status} size="lg" />
                </div>
              </div>

              {/* Total Amount */}
              <div>
                <span className="block text-xs text-brand-text-subtle mb-1">Tổng tiền thanh toán</span>
                <span className="text-2xl font-black text-brand-primary">
                  {formatVnd(order.totalAmountVnd)}
                </span>
              </div>

              {/* Reservation Status (Directly from backend DTO, NO extrapolation) */}
              <div>
                <span className="block text-xs text-brand-text-subtle mb-1">
                  Trạng thái giữ chỗ kho (Tồn kho vật lý)
                </span>
                <div className="pt-0.5">
                  <ReservationHeldBadge isHeld={order.isReservationHeld} />
                </div>
              </div>

              {/* Transfer Reference */}
              <div>
                <span className="block text-xs text-brand-text-subtle mb-1">Mã nội dung CK</span>
                <span className="font-mono font-bold text-brand-text text-sm bg-brand-bg px-2.5 py-1 rounded-lg border border-brand-border inline-block">
                  {order.transferReference || 'N/A'}
                </span>
              </div>
            </div>
          </div>

          {/* TWO COLUMNS: DETAILS & LOGS */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Left Column (2 Cols): Machine, Product, Payment, Dispense */}
            <div className="lg:col-span-2 space-y-6">
              {/* 1. Machine & Product Inventory Information */}
              <div className="bg-white rounded-2xl border border-brand-border p-6 shadow-sm space-y-4">
                <div className="flex items-center gap-2 border-b border-brand-border pb-3">
                  <Package className="w-5 h-5 text-brand-primary" />
                  <h3 className="font-extrabold text-brand-text text-base">
                    Sản phẩm & Tồn kho máy
                  </h3>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                  <div className="space-y-1">
                    <span className="text-brand-text-subtle">Máy bán hàng:</span>
                    <p className="font-bold text-brand-text text-sm">
                      {order.machineName || order.machineCode || 'N/A'}{' '}
                      <span className="font-mono text-xs text-brand-text-muted">
                        ({order.machineCode || 'N/A'})
                      </span>
                    </p>
                  </div>

                  <div className="space-y-1">
                    <span className="text-brand-text-subtle">Sản phẩm đặt mua:</span>
                    <p className="font-bold text-brand-text text-sm">
                      {order.productName || 'N/A'}{' '}
                      <span className="text-brand-text-muted">(Số lượng: {order.quantity})</span>
                    </p>
                  </div>

                  <div className="space-y-1">
                    <span className="text-brand-text-subtle">Vị trí hộc hàng:</span>
                    <p className="font-bold text-brand-text">
                      Hộc số {order.slotNumber ?? 'Chưa chỉ định'}
                    </p>
                  </div>

                  <div className="space-y-1">
                    <span className="text-brand-text-subtle">Tồn kho hiện tại:</span>
                    <p className="font-medium text-brand-text">
                      Tồn thực tế:{' '}
                      <strong className="text-brand-text font-bold">
                        {order.currentStockQuantity ?? 'N/A'}
                      </strong>{' '}
                      | Đang giữ chỗ:{' '}
                      <strong className="text-amber-700 font-bold">
                        {order.currentReservedQuantity ?? 'N/A'}
                      </strong>
                    </p>
                  </div>
                </div>
              </div>

              {/* 2. Payment Transactions History */}
              <div className="bg-white rounded-2xl border border-brand-border p-6 shadow-sm space-y-4">
                <div className="flex items-center justify-between border-b border-brand-border pb-3">
                  <div className="flex items-center gap-2">
                    <CreditCard className="w-5 h-5 text-brand-primary" />
                    <h3 className="font-extrabold text-brand-text text-base">
                      Giao dịch thanh toán ({order.paymentTransactions?.length || 0})
                    </h3>
                  </div>
                </div>

                {order.paymentTransactions && order.paymentTransactions.length > 0 ? (
                  <div className="space-y-3">
                    {order.paymentTransactions.map((tx) => (
                      <div
                        key={tx.transactionId}
                        className="p-4 rounded-xl border border-brand-border bg-brand-bg/50 space-y-2 text-xs"
                      >
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <span className="font-mono font-bold text-brand-text">
                              {tx.providerTransactionId}
                            </span>
                            <VerificationStatusBadge status={tx.verificationStatus} />
                          </div>
                          <span className="font-black text-brand-primary text-sm">
                            {formatVnd(tx.amountVnd)}
                          </span>
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-brand-text-muted pt-1">
                          <div>
                            Thời gian nhận:{' '}
                            <strong className="text-brand-text">
                              {formatDateTime(tx.receivedAt)}
                            </strong>
                          </div>
                          <div>
                            Trạng thái GD:{' '}
                            <span className="font-semibold text-brand-text">
                              {tx.transactionStatus}
                            </span>
                          </div>
                          {tx.transferReference && (
                            <div className="sm:col-span-2">
                              Nội dung CK:{' '}
                              <code className="font-mono font-bold bg-white px-1.5 py-0.5 rounded border border-brand-border text-brand-text">
                                {tx.transferReference}
                              </code>
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-brand-text-subtle italic py-2">
                    Chưa ghi nhận giao dịch thanh toán nào từ đối tác ngân hàng.
                  </p>
                )}
              </div>

              {/* 3. Dispense Commands & Hardware Events */}
              <div className="bg-white rounded-2xl border border-brand-border p-6 shadow-sm space-y-4">
                <div className="flex items-center gap-2 border-b border-brand-border pb-3">
                  <Cpu className="w-5 h-5 text-brand-primary" />
                  <h3 className="font-extrabold text-brand-text text-base">
                    Lệnh nhả hàng & Thiết bị phần cứng ({order.dispenseCommands?.length || 0})
                  </h3>
                </div>

                {order.dispenseCommands && order.dispenseCommands.length > 0 ? (
                  <div className="space-y-3">
                    {order.dispenseCommands.map((cmd) => (
                      <div
                        key={cmd.commandId}
                        className="p-4 rounded-xl border border-brand-border bg-brand-bg/50 space-y-2 text-xs"
                      >
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <span className="font-bold text-brand-text">
                              Lệnh hộc #{cmd.slotNumber}
                            </span>
                            <span
                              className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                                cmd.status === 'SUCCESS'
                                  ? 'bg-emerald-100 text-emerald-800'
                                  : cmd.status === 'FAILED'
                                  ? 'bg-rose-100 text-rose-800'
                                  : 'bg-amber-100 text-amber-800'
                              }`}
                            >
                              {cmd.status}
                            </span>
                          </div>
                          <span className="text-brand-text-subtle">
                            Số lần thử: <strong>{cmd.attemptCount}</strong>
                          </span>
                        </div>

                        {cmd.failureCode && (
                          <div className="p-2 rounded-lg bg-rose-50 border border-rose-200 text-rose-800 text-xs">
                            <strong className="font-bold">Mã lỗi: {cmd.failureCode}</strong>
                            {cmd.failureMessage && (
                              <p className="mt-0.5 text-[11px]">{cmd.failureMessage}</p>
                            )}
                          </div>
                        )}

                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-brand-text-muted text-[11px] pt-1">
                          <div>Phát hành: {formatDateTime(cmd.publishedAt)}</div>
                          <div>Xác nhận: {formatDateTime(cmd.acknowledgedAt)}</div>
                          <div>Kết thúc: {formatDateTime(cmd.finishedAt)}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-brand-text-subtle italic py-2">
                    Chưa có lệnh nhả hàng nào được gửi tới thiết bị.
                  </p>
                )}
              </div>
            </div>

            {/* Right Column (1 Col): Order Timeline & Reconciliation Audit Logs */}
            <div className="space-y-6">
              {/* Order Timestamps */}
              <div className="bg-white rounded-2xl border border-brand-border p-6 shadow-sm space-y-3">
                <div className="flex items-center gap-2 border-b border-brand-border pb-3">
                  <Clock className="w-5 h-5 text-brand-primary" />
                  <h3 className="font-extrabold text-brand-text text-base">Tiến trình thời gian</h3>
                </div>

                <div className="space-y-3 text-xs">
                  <div className="flex justify-between py-1 border-b border-brand-border/60">
                    <span className="text-brand-text-muted">Thời điểm tạo đơn:</span>
                    <span className="font-medium text-brand-text">
                      {formatDateTime(order.createdAt)}
                    </span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-brand-border/60">
                    <span className="text-brand-text-muted">Hết hạn thanh toán:</span>
                    <span className="font-medium text-brand-text">
                      {formatDateTime(order.paymentExpiresAt)}
                    </span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-brand-border/60">
                    <span className="text-brand-text-muted">Xác nhận thanh toán:</span>
                    <span className="font-medium text-brand-text">
                      {formatDateTime(order.paidAt)}
                    </span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-brand-border/60">
                    <span className="text-brand-text-muted">Bắt đầu nhả hàng:</span>
                    <span className="font-medium text-brand-text">
                      {formatDateTime(order.dispensingStartedAt)}
                    </span>
                  </div>
                  <div className="flex justify-between py-1">
                    <span className="text-brand-text-muted">Thời điểm hoàn tất:</span>
                    <span className="font-medium text-brand-text">
                      {formatDateTime(order.completedAt)}
                    </span>
                  </div>
                </div>
              </div>

              {/* Reconciliation Audit Trail */}
              <div className="bg-white rounded-2xl border border-brand-border p-6 shadow-sm space-y-4">
                <div className="flex items-center gap-2 border-b border-brand-border pb-3">
                  <History className="w-5 h-5 text-brand-primary" />
                  <h3 className="font-extrabold text-brand-text text-base">
                    Nhật ký đối soát ({order.auditLogs?.length || 0})
                  </h3>
                </div>

                {order.auditLogs && order.auditLogs.length > 0 ? (
                  <div className="space-y-3">
                    {order.auditLogs.map((log) => (
                      <div
                        key={log.auditId}
                        className="p-3.5 rounded-xl border border-brand-border bg-brand-bg/50 space-y-2 text-xs"
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-brand-primary font-mono text-[11px]">
                            {log.action}
                          </span>
                          <span className="text-[11px] text-brand-text-subtle">
                            {formatDateTime(log.createdAt)}
                          </span>
                        </div>

                        <div className="text-brand-text text-xs bg-white p-2.5 rounded-lg border border-brand-border leading-relaxed">
                          {log.reason}
                        </div>

                        {log.confirmationReference && (
                          <div className="text-[11px] text-brand-text-muted">
                            Mã tham chiếu:{' '}
                            <code className="font-mono font-bold text-blue-700">
                              {log.confirmationReference}
                            </code>
                          </div>
                        )}

                        <div className="flex items-center justify-between text-[11px] text-brand-text-subtle pt-1 border-t border-brand-border/60">
                          <span>
                            Người thực hiện:{' '}
                            <strong className="text-brand-text">{log.adminUsername}</strong>
                          </span>
                          <span>
                            {log.previousStatus} → <strong>{log.newStatus}</strong>
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-brand-text-subtle italic py-2">
                    Chưa có quyết định đối soát nào được ghi nhận cho đơn hàng này.
                  </p>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Decision Modal */}
      {order && (
        <ReconciliationDecisionModal
          orderId={order.orderId}
          publicOrderCode={order.publicOrderCode}
          currentStatus={order.status}
          isReservationHeld={order.isReservationHeld}
          isOpen={isDecisionModalOpen}
          onClose={() => setIsDecisionModalOpen(false)}
          onSuccess={handleDecisionSuccess}
        />
      )}
    </AdminLayout>
  );
};
