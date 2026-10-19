import React, { useState, useEffect } from 'react';
import { X, CheckCircle2, Clock, AlertCircle, ShieldAlert, Loader2, Sparkles } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { createOrder, getOrderStatus } from '../services/api';
import { formatVnd } from '../utils/formatters';
import type { Order, OrderStatus } from '../types/order';
import { ApiError } from '../types/api';
import { QRCodeSVG } from 'qrcode.react';

interface CheckoutModalProps {
  machineId: string;
  isOpen: boolean;
  onClose: () => void;
}

export const CheckoutModal: React.FC<CheckoutModalProps> = ({ machineId, isOpen, onClose }) => {
  const { items, clearCart } = useCart();
  const [selectedProductIndex, setSelectedProductIndex] = useState(0);
  const [loading, setLoading] = useState(false);
  const [order, setOrder] = useState<Order | null>(null);
  const [errorNotice, setErrorNotice] = useState<{ code: string; message: string; isPhase4Dependency?: boolean } | null>(
    null
  );

  // Poll order status every 3s while order is PENDING_PAYMENT
  useEffect(() => {
    if (!order || !isOpen) return;

    const terminalStatuses: OrderStatus[] = [
      'COMPLETED',
      'DISPENSE_FAILED',
      'NEEDS_RECONCILIATION',
      'EXPIRED',
      'CANCELLED',
    ];

    if (terminalStatuses.includes(order.status)) {
      return;
    }

    const intervalId = setInterval(async () => {
      try {
        const res = await getOrderStatus(order.orderId);
        if (res.status !== order.status) {
          setOrder((prev) => (prev ? { ...prev, status: res.status } : null));
        }
      } catch (err) {
        console.warn('Polling order status failed:', err);
      }
    }, 3000);

    return () => clearInterval(intervalId);
  }, [order, isOpen]);

  if (!isOpen) return null;

  const currentItem = items[selectedProductIndex] || items[0];

  const handleCreateOrder = async () => {
    if (!currentItem) return;

    setLoading(true);
    setErrorNotice(null);

    try {
      // Call actual endpoint according to API_CONTRACT.md §3.1
      const createdOrder = await createOrder({
        machineId,
        productId: currentItem.product.productId,
        quantity: 1, // MVP constraint: 1 unit per dispensing cycle
      });

      setOrder(createdOrder);
      clearCart();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        // Check if 404/not found which indicates Phase 4 order endpoint is not yet present on backend
        const isPhase4Dependency = err.status === 404 || err.code === 'NOT_FOUND';
        setErrorNotice({
          code: err.code,
          message: isPhase4Dependency
            ? 'Endpoint tạo đơn hàng (POST /api/v1/orders) chưa được kích hoạt trên Backend. Theo IMPLEMENTATION_PLAN.md, chức năng này sẽ được triển khai tại Giai đoạn 4.'
            : err.message,
          isPhase4Dependency,
        });
      } else {
        setErrorNotice({
          code: 'CLIENT_ERROR',
          message: 'Không thể khởi tạo yêu cầu đặt hàng.',
        });
      }
    } finally {
      setLoading(false);
    }
  };

  const getModalTitle = () => {
    if (!order) return 'Xác nhận đơn hàng';
    switch (order.status) {
      case 'PAID':
        return 'Thanh toán thành công';
      case 'DISPENSING':
        return 'Đang nhả hàng';
      case 'COMPLETED':
        return 'Xuất hàng thành công';
      case 'EXPIRED':
        return 'Đơn hàng hết hạn';
      case 'NEEDS_RECONCILIATION':
        return 'Cần đối soát giao dịch';
      case 'DISPENSE_FAILED':
        return 'Sự cố xuất hàng';
      default:
        return 'Thông tin thanh toán';
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      {/* Backdrop */}
      <div className="fixed inset-0 bg-black/50 backdrop-blur-xs transition-opacity" onClick={onClose} />

      <div className="min-h-full flex items-center justify-center p-4">
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="checkout-modal-title"
          className="relative w-full max-w-lg bg-white rounded-3xl shadow-2xl border border-brand-border overflow-hidden transition-all my-8"
        >
          {/* Header */}
          <div className="px-6 py-4 border-b border-brand-border flex items-center justify-between bg-brand-bg/40">
            <h2 id="checkout-modal-title" className="font-bold text-lg text-brand-text">
              {getModalTitle()}
            </h2>
            <button
              id="close-checkout-modal-btn"
              type="button"
              onClick={onClose}
              className="p-1.5 rounded-lg text-brand-text-muted hover:text-brand-text hover:bg-gray-100 transition-colors"
              aria-label="Đóng cửa sổ"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          <div className="p-6">
            {order ? (
              /* REAL ORDER PAYMENT PRESENTATION */
              <div className="space-y-5">
                {order.status === 'PAID' || order.status === 'DISPENSING' || order.status === 'COMPLETED' ? (
                  /* VERIFIED PAYMENT / DISPENSING / COMPLETED */
                  <div className="space-y-4 text-center py-2" data-testid="payment-success-view">
                    <div className={`w-16 h-16 ${order.status === 'DISPENSING' ? 'bg-blue-100 text-blue-600' : 'bg-emerald-100 text-emerald-600'} rounded-full flex items-center justify-center mx-auto`}>
                      {order.status === 'DISPENSING' ? (
                        <Loader2 className="w-10 h-10 animate-spin" />
                      ) : (
                        <CheckCircle2 className="w-10 h-10" />
                      )}
                    </div>
                    <div>
                      <h3 className={`text-base font-bold ${order.status === 'DISPENSING' ? 'text-blue-900' : 'text-emerald-900'}`}>
                        {order.status === 'COMPLETED'
                          ? 'Đã xuất hàng thành công!'
                          : order.status === 'DISPENSING'
                          ? 'Máy đang thực hiện nhả hàng...'
                          : 'Thanh toán đã được xác minh thành công!'}
                      </h3>
                      <p className="text-xs text-brand-text-muted mt-1 leading-relaxed">
                        {order.status === 'COMPLETED'
                          ? 'Sản phẩm đã được đưa ra khay nhận hàng bên dưới. Quý khách vui lòng lấy hàng. Cảm ơn quý khách!'
                          : order.status === 'DISPENSING'
                          ? 'Cơ cấu động cơ đang kích hoạt để đẩy sản phẩm ra khay. Vui lòng chờ trong giây lát...'
                          : 'Hệ thống đã nhận được tiền từ ngân hàng. Máy bán hàng đang chuẩn bị nhả sản phẩm...'}
                      </p>
                    </div>

                    <div className="p-4 rounded-2xl bg-brand-bg border border-brand-border text-left space-y-2 text-xs">
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Mã đơn hàng:</span>
                        <span className="font-mono font-bold text-brand-primary">{order.publicOrderCode}</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Số tiền đã xác minh:</span>
                        <span className="font-bold text-emerald-700">{formatVnd(order.totalAmountVnd)}</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Trạng thái:</span>
                        <span className={`px-2 py-0.5 rounded-full font-bold text-[10px] ${
                          order.status === 'COMPLETED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : order.status === 'DISPENSING'
                            ? 'bg-blue-100 text-blue-800'
                            : 'bg-emerald-100 text-emerald-800'
                        }`}>
                          {order.status === 'COMPLETED'
                            ? 'HOÀN TẤT (COMPLETED)'
                            : order.status === 'DISPENSING'
                            ? 'ĐANG NHẢ HÀNG'
                            : 'ĐÃ THANH TOÁN (PAID)'}
                        </span>
                      </div>
                    </div>

                    <div className="pt-2">
                      <button
                        type="button"
                        onClick={onClose}
                        className="w-full py-3 px-4 rounded-xl bg-brand-primary hover:bg-brand-primary-hover text-white text-xs sm:text-sm font-bold shadow-md transition-colors"
                      >
                        Hoàn tất / Đóng
                      </button>
                    </div>
                  </div>
                ) : order.status === 'EXPIRED' ? (
                  /* EXPIRED ORDER */
                  <div className="space-y-4 text-center py-2" data-testid="payment-expired-view">
                    <div className="w-16 h-16 bg-amber-100 rounded-full flex items-center justify-center mx-auto text-amber-600">
                      <Clock className="w-10 h-10" />
                    </div>
                    <div>
                      <h3 className="text-base font-bold text-amber-900">
                        Đơn hàng đã hết hạn thanh toán
                      </h3>
                      <p className="text-xs text-brand-text-muted mt-1 leading-relaxed">
                        Thời gian giữ chỗ 15 phút đã kết thúc và đơn hàng đã bị hủy tự động. Vui lòng đặt lại đơn mới.
                      </p>
                    </div>

                    <div className="p-4 rounded-2xl bg-amber-50/60 border border-amber-200 text-left space-y-2 text-xs">
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Mã đơn hàng:</span>
                        <span className="font-mono font-bold text-brand-text">{order.publicOrderCode}</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Trạng thái:</span>
                        <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 font-bold text-[10px]">
                          HẾT HẠN (EXPIRED)
                        </span>
                      </div>
                    </div>

                    <div className="pt-2">
                      <button
                        type="button"
                        onClick={onClose}
                        className="w-full py-3 px-4 rounded-xl border border-gray-200 text-xs sm:text-sm font-semibold text-brand-text hover:bg-gray-50 transition-colors"
                      >
                        Đóng cửa sổ
                      </button>
                    </div>
                  </div>
                ) : order.status === 'NEEDS_RECONCILIATION' ? (
                  /* NEEDS RECONCILIATION */
                  <div className="space-y-4 text-center py-2" data-testid="payment-reconciliation-view">
                    <div className="w-16 h-16 bg-purple-100 rounded-full flex items-center justify-center mx-auto text-purple-600">
                      <ShieldAlert className="w-10 h-10" />
                    </div>
                    <div>
                      <h3 className="text-base font-bold text-purple-900">
                        Giao dịch cần đối soát
                      </h3>
                      <p className="text-xs text-brand-text-muted mt-1 leading-relaxed">
                        Hệ thống ghi nhận tiền chuyển đến sau khi đơn hết hạn hoặc có bất thường. Máy không xuất hàng để bảo vệ khách. Vui lòng chụp màn hình thông tin đơn và liên hệ hỗ trợ.
                      </p>
                    </div>

                    <div className="p-4 rounded-2xl bg-purple-50/60 border border-purple-200 text-left space-y-2 text-xs">
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Mã đơn hàng:</span>
                        <span className="font-mono font-bold text-brand-text">{order.publicOrderCode}</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Nội dung đối soát:</span>
                        <span className="font-mono font-bold text-brand-primary">{order.payment?.transferReference}</span>
                      </div>
                    </div>

                    <div className="pt-2">
                      <button
                        type="button"
                        onClick={onClose}
                        className="w-full py-3 px-4 rounded-xl border border-gray-200 text-xs sm:text-sm font-semibold text-brand-text hover:bg-gray-50 transition-colors"
                      >
                        Đóng cửa sổ
                      </button>
                    </div>
                  </div>
                ) : order.status === 'DISPENSE_FAILED' ? (
                  /* DISPENSE FAILED */
                  <div className="space-y-4 text-center py-2" data-testid="payment-dispense-failed-view">
                    <div className="w-16 h-16 bg-rose-100 rounded-full flex items-center justify-center mx-auto text-rose-600">
                      <AlertCircle className="w-10 h-10" />
                    </div>
                    <div>
                      <h3 className="text-base font-bold text-rose-900">
                        Sự cố khi xuất hàng
                      </h3>
                      <p className="text-xs text-brand-text-muted mt-1 leading-relaxed">
                        Máy gặp sự cố cơ cấu khi đẩy sản phẩm. Tiền của bạn đã được ghi nhận. Vui lòng liên hệ nhân viên vận hành để được hỗ trợ hoặc hoàn tiền.
                      </p>
                    </div>

                    <div className="pt-2">
                      <button
                        type="button"
                        onClick={onClose}
                        className="w-full py-3 px-4 rounded-xl border border-gray-200 text-xs sm:text-sm font-semibold text-brand-text hover:bg-gray-50 transition-colors"
                      >
                        Đóng cửa sổ
                      </button>
                    </div>
                  </div>
                ) : (
                  /* PENDING_PAYMENT: QR CODE & POLLING INDICATOR */
                  <>
                    <div className="p-4 rounded-2xl bg-emerald-50 border border-emerald-200 text-center">
                      <span className="text-xs font-bold text-emerald-800 uppercase tracking-wider block mb-1">
                        Đơn hàng đã được tạo thành công
                      </span>
                      <p className="font-mono text-sm font-bold text-brand-primary">{order.publicOrderCode}</p>
                    </div>

                    {/* QR Code Presentation */}
                    {order.payment && order.payment.paymentQrData ? (
                      <div className="flex flex-col items-center justify-center p-6 bg-brand-bg rounded-2xl border border-brand-border">
                        <div className="p-3 bg-white rounded-xl shadow-xs border border-brand-border mb-3">
                          <QRCodeSVG value={order.payment.paymentQrData} size={180} />
                        </div>
                        <span className="text-xs text-brand-text-muted">Quét mã bằng ứng dụng Ngân hàng</span>
                        <div className="mt-3 text-center space-y-1">
                          <p className="text-xs font-semibold text-brand-text">
                            Ngân hàng: {order.payment.bankName} • {order.payment.bankAccountNumber}
                          </p>
                          <p className="text-xs text-brand-text-muted">
                            Chủ tài khoản: <span className="font-bold">{order.payment.bankAccountName}</span>
                          </p>
                          <p className="text-xs font-mono font-bold text-brand-primary">
                            Nội dung CK: {order.payment.transferReference}
                          </p>
                        </div>
                      </div>
                    ) : null}

                    {/* Live Polling Status Indicator */}
                    <div className="flex items-center justify-center gap-2 p-3 rounded-xl bg-brand-primary/5 border border-brand-primary/10 text-xs text-brand-primary">
                      <Loader2 className="w-4 h-4 animate-spin shrink-0 text-brand-primary" />
                      <span>Đang chờ tín hiệu thanh toán từ ngân hàng (tự động cập nhật mỗi 3s)...</span>
                    </div>

                    {/* Amount & Time limit */}
                    <div className="flex justify-between items-center p-4 rounded-xl bg-gray-50 border border-gray-200 text-sm">
                      <div className="flex items-center gap-2 text-brand-text-muted">
                        <Clock className="w-4 h-4 text-brand-primary" />
                        <span>Hạn thanh toán:</span>
                      </div>
                      <span className="font-bold text-brand-primary">15 phút</span>
                    </div>

                    <p className="text-[11px] text-center text-brand-text-subtle">
                      Hệ thống máy sẽ tự động kích hoạt nhả hàng sau khi nhận tín hiệu giao dịch hợp lệ từ ngân hàng.
                    </p>

                    <div className="pt-2">
                      <button
                        type="button"
                        onClick={onClose}
                        className="w-full py-2.5 px-4 rounded-xl border border-gray-200 text-xs font-semibold text-brand-text hover:bg-gray-50 transition-colors"
                      >
                        Đóng / Quay lại sau
                      </button>
                    </div>
                  </>
                )}
              </div>
            ) : (
              /* ORDER CONFIRMATION FLOW */
              <div className="space-y-5">
                {/* MVP Prototype Notice */}
                <div className="p-3.5 rounded-xl bg-brand-primary/5 border border-brand-primary/15 text-xs text-brand-primary flex items-start gap-2.5">
                  <Sparkles className="w-4 h-4 text-brand-primary shrink-0 mt-0.5" />
                  <div className="leading-relaxed">
                    <span className="font-bold block">Giao thức nhả hàng vật lý (MVP)</span>
                    Để đảm bảo an toàn cơ cấu lò xo, mỗi chu kỳ nhả hàng phục vụ 1 sản phẩm với số lượng 1 chiếc.
                  </div>
                </div>

                {/* Multi-item selector if cart has > 1 product */}
                {items.length > 1 && (
                  <div>
                    <label className="block text-xs font-bold text-brand-text mb-2">
                      Chọn sản phẩm thực hiện thanh toán lần này:
                    </label>
                    <div className="space-y-2">
                      {items.map((item, idx) => (
                        <button
                          key={item.product.productId}
                          type="button"
                          onClick={() => setSelectedProductIndex(idx)}
                          className={`w-full p-3 rounded-xl border text-left flex items-center justify-between text-xs transition-all ${
                            selectedProductIndex === idx
                              ? 'border-brand-primary bg-emerald-50/40 font-semibold'
                              : 'border-brand-border bg-white text-brand-text-muted'
                          }`}
                        >
                          <div className="flex items-center gap-2">
                            <span className="px-1.5 py-0.5 rounded bg-brand-primary/10 text-brand-primary text-[10px] font-bold">
                              Khay 0{item.product.slotNumber}
                            </span>
                            <span>{item.product.name}</span>
                          </div>
                          <span className="font-bold text-brand-primary">
                            {formatVnd(item.product.priceVnd)}
                          </span>
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {/* Selected Item Detail */}
                {currentItem && (
                  <div className="p-4 rounded-2xl bg-brand-bg border border-brand-border space-y-3">
                    <div className="flex justify-between items-center text-sm">
                      <span className="text-brand-text-muted">Mã máy bán hàng:</span>
                      <span className="font-mono font-bold text-brand-primary">{machineId}</span>
                    </div>
                    <div className="flex justify-between items-center text-sm">
                      <span className="text-brand-text-muted">Sản phẩm:</span>
                      <span className="font-bold text-brand-text">{currentItem.product.name}</span>
                    </div>
                    <div className="flex justify-between items-center text-sm">
                      <span className="text-brand-text-muted">Khay xuất hàng:</span>
                      <span className="font-semibold text-brand-text">Khay số {currentItem.product.slotNumber}</span>
                    </div>
                    <div className="flex justify-between items-center text-sm">
                      <span className="text-brand-text-muted">Số lượng đơn hàng:</span>
                      <span className="font-semibold text-brand-text">1 sản phẩm</span>
                    </div>
                    <div className="pt-2 border-t border-brand-border flex justify-between items-center">
                      <span className="text-sm font-bold text-brand-text">Tổng tiền thanh toán:</span>
                      <span className="text-xl font-extrabold text-brand-primary">
                        {formatVnd(currentItem.product.priceVnd)}
                      </span>
                    </div>
                  </div>
                )}

                {/* Phase Dependency / Error Banner */}
                {errorNotice && (
                  <div
                    id="checkout-error-notice"
                    className={`p-4 rounded-2xl text-xs space-y-1.5 border ${
                      errorNotice.isPhase4Dependency
                        ? 'bg-amber-50 border-amber-200 text-amber-900'
                        : 'bg-rose-50 border-rose-200 text-rose-900'
                    }`}
                  >
                    <div className="flex items-center gap-2 font-bold">
                      {errorNotice.isPhase4Dependency ? (
                        <ShieldAlert className="w-4 h-4 text-amber-700 shrink-0" />
                      ) : (
                        <AlertCircle className="w-4 h-4 text-rose-700 shrink-0" />
                      )}
                      <span>
                        {errorNotice.isPhase4Dependency
                          ? 'Thông báo chuyển giao giai đoạn (Phase 4 Dependency)'
                          : 'Lỗi khởi tạo đơn hàng'}
                      </span>
                    </div>
                    <p className="leading-relaxed">{errorNotice.message}</p>
                    {errorNotice.isPhase4Dependency && (
                      <div className="mt-2 pt-2 border-t border-amber-200/80 text-[11px] text-amber-800">
                        <strong>Quy ước bảo mật Phase 3:</strong> Frontend không tự giả mạo giao dịch thanh toán thành công khi backend chưa hỗ trợ. Giao diện order đã hoàn thành sẵn sàng kết nối API `POST /api/v1/orders`.
                      </div>
                    )}
                  </div>
                )}

                {/* Actions */}
                <div className="flex items-center gap-3 pt-2">
                  <button
                    type="button"
                    onClick={onClose}
                    className="flex-1 py-3 px-4 rounded-xl border border-gray-200 text-xs sm:text-sm font-semibold text-brand-text hover:bg-gray-50 transition-colors"
                  >
                    Quay lại chọn món
                  </button>
                  <button
                    id="confirm-create-order-btn"
                    type="button"
                    disabled={loading || !currentItem}
                    onClick={handleCreateOrder}
                    className="flex-1 flex items-center justify-center gap-2 py-3 px-4 rounded-xl bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white text-xs sm:text-sm font-bold shadow-md transition-all duration-150 disabled:bg-gray-300 disabled:cursor-not-allowed"
                  >
                    {loading ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin text-brand-accent" />
                        <span>Đang xử lý...</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-4 h-4 text-brand-accent" />
                        <span>Xác nhận đặt hàng</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
