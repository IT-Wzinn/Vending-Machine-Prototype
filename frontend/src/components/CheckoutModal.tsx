import React, { useState } from 'react';
import { X, CheckCircle2, Clock, AlertCircle, ShieldAlert, Loader2, Sparkles } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { createOrder } from '../services/api';
import { formatVnd } from '../utils/formatters';
import type { Order } from '../types/order';
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
              {order ? 'Thông tin thanh toán' : 'Xác nhận đơn hàng'}
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
              /* REAL ORDER PAYMENT PRESENTATION (If returned by Backend) */
              <div className="space-y-5">
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
