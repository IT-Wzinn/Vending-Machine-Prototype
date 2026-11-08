import React, { useState, useEffect } from 'react';
import {
  X,
  CheckCircle2,
  Clock,
  AlertCircle,
  ShieldAlert,
  Loader2,
  Sparkles,
  Copy,
  Check,
  RotateCw,
  Radio,
  PackageCheck,
} from 'lucide-react';
import { useCart } from '../context/CartContext';
import { createOrder, getOrderStatus } from '../services/api';
import { formatVnd } from '../utils/formatters';
import { resolveProductImage } from '../utils/productImages';
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
  const [copiedField, setCopiedField] = useState<string | null>(null);

  // Poll order status every 3s while order is PENDING_PAYMENT, PAID, or DISPENSING
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
      const createdOrder = await createOrder({
        machineId,
        productId: currentItem.product.productId,
        quantity: 1, // MVP constraint: 1 unit per dispensing cycle
      });

      setOrder(createdOrder);
      clearCart();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
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

  const handleCopy = (text: string, fieldName: string) => {
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(() => {
        setCopiedField(fieldName);
        setTimeout(() => setCopiedField(null), 2000);
      });
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
      case 'CANCELLED':
        return 'Đơn hàng đã hủy';
      default:
        return 'Thông tin thanh toán';
    }
  };

  // Resolve product image for visual presentation
  const orderProductImage = order?.product
    ? resolveProductImage({
        productId: order.product.productId,
        name: order.product.name,
        priceVnd: order.product.priceVnd,
        slotNumber: 1,
        stockQuantity: 1,
        available: true,
        description: null,
        imageUrl: null,
      })
    : currentItem
    ? resolveProductImage(currentItem.product)
    : null;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      {/* Backdrop */}
      <div className="fixed inset-0 bg-black/55 backdrop-blur-xs transition-opacity" onClick={onClose} />

      <div className="min-h-full flex items-center justify-center p-3 sm:p-4">
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="checkout-modal-title"
          className="relative w-full max-w-lg glass-modal rounded-3xl shadow-2xl overflow-hidden transition-all my-6 sm:my-8 max-h-[92vh] flex flex-col border border-emerald-500/25"
        >
          {/* Header */}
          <div className="px-5 sm:px-6 py-3.5 sm:py-4 border-b border-emerald-500/15 flex items-center justify-between bg-white/50 backdrop-blur-md shrink-0">
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-brand-primary animate-pulse" />
              <h2 id="checkout-modal-title" className="font-extrabold text-base sm:text-lg text-brand-primary tracking-tight">
                {getModalTitle()}
              </h2>
            </div>
            <button
              id="close-checkout-modal-btn"
              type="button"
              onClick={onClose}
              className="p-1.5 rounded-xl text-brand-text-muted hover:text-brand-text hover:bg-white/60 transition-colors"
              aria-label="Đóng cửa sổ"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          <div className="p-4 sm:p-6 overflow-y-auto">
            {order ? (
              /* REAL ORDER PAYMENT & SMART DISPENSE TERMINAL */
              <div className="space-y-4">
                {order.status === 'PAID' || order.status === 'DISPENSING' || order.status === 'COMPLETED' ? (
                  /* VERIFIED PAYMENT / DISPENSING / COMPLETED */
                  <div className="space-y-4 text-center" data-testid="payment-success-view">
                    {/* Status Hero Card */}
                    <div
                      className={`p-4 sm:p-5 rounded-3xl border transition-all ${
                        order.status === 'COMPLETED'
                          ? 'bg-emerald-500/15 border-emerald-500/35 text-emerald-950 shadow-sm'
                          : order.status === 'DISPENSING'
                          ? 'bg-blue-500/15 border-blue-500/35 text-blue-950 shadow-sm'
                          : 'bg-emerald-500/15 border-emerald-500/30 text-emerald-950'
                      }`}
                    >
                      <div
                        className={`w-14 h-14 sm:w-16 sm:h-16 ${
                          order.status === 'COMPLETED'
                            ? 'bg-emerald-600 text-white shadow-md'
                            : order.status === 'DISPENSING'
                            ? 'bg-blue-600 text-white shadow-md'
                            : 'bg-emerald-600 text-white shadow-md'
                        } rounded-2xl flex items-center justify-center mx-auto mb-3`}
                      >
                        {order.status === 'COMPLETED' ? (
                          <PackageCheck className="w-8 h-8 sm:w-9 sm:h-9" />
                        ) : order.status === 'DISPENSING' ? (
                          <RotateCw className="w-8 h-8 sm:w-9 sm:h-9 animate-spin" />
                        ) : (
                          <CheckCircle2 className="w-8 h-8 sm:w-9 sm:h-9" />
                        )}
                      </div>

                      <h3
                        className={`text-base sm:text-lg font-black tracking-tight ${
                          order.status === 'COMPLETED'
                            ? 'text-emerald-900'
                            : order.status === 'DISPENSING'
                            ? 'text-blue-900'
                            : 'text-emerald-900'
                        }`}
                      >
                        {order.status === 'COMPLETED'
                          ? 'Đã xuất hàng thành công!'
                          : order.status === 'DISPENSING'
                          ? 'Máy đang thực hiện nhả hàng...'
                          : 'Thanh toán đã được xác minh thành công!'}
                      </h3>

                      <p className="text-xs text-brand-text mt-1.5 leading-relaxed max-w-sm mx-auto">
                        {order.status === 'COMPLETED'
                          ? 'Sản phẩm đã được đưa ra khay nhận hàng bên dưới. Quý khách vui lòng lấy hàng. Cảm ơn quý khách!'
                          : order.status === 'DISPENSING'
                          ? 'Cơ cấu động cơ đang kích hoạt để đẩy sản phẩm ra khay. Vui lòng chờ trong giây lát...'
                          : 'Hệ thống đã nhận được tiền từ ngân hàng. Máy bán hàng đang chuẩn bị nhả sản phẩm...'}
                      </p>
                    </div>

                    {/* SMART DISPENSE TERMINAL — Physical Dispense Stepper */}
                    <div className="p-3.5 sm:p-4 rounded-2xl glass-surface border border-emerald-500/20 text-left space-y-3">
                      <div className="flex items-center justify-between pb-2 border-b border-emerald-500/15">
                        <span className="text-[11px] font-black uppercase tracking-wider text-brand-primary flex items-center gap-1.5">
                          <Radio className="w-3.5 h-3.5 text-brand-primary" />
                          Tiến trình nhả hàng vật lý
                        </span>
                        <span className="px-2 py-0.5 rounded-full font-mono font-bold text-[10px] bg-brand-primary/10 text-brand-primary border border-brand-primary/15">
                          {order.status === 'COMPLETED'
                            ? 'BƯỚC 4/4'
                            : order.status === 'DISPENSING'
                            ? 'BƯỚC 3/4'
                            : 'BƯỚC 1/4'}
                        </span>
                      </div>

                      <div className="space-y-3 text-xs">
                        {/* Step 1: Payment Verified */}
                        <div className="flex items-start gap-3">
                          <div className="w-6 h-6 rounded-full bg-emerald-500 text-white flex items-center justify-center shrink-0 mt-0.5 shadow-xs">
                            <Check className="w-3.5 h-3.5 stroke-[3]" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between">
                              <span className="font-bold text-brand-primary">1. Xác nhận thanh toán</span>
                              <span className="text-[10px] font-mono text-emerald-700 font-bold">XONG</span>
                            </div>
                            <p className="text-[11px] text-brand-text-muted mt-0.5">
                              Giao dịch ngân hàng đã xác minh thành công qua SePay Webhook
                            </p>
                          </div>
                        </div>

                        {/* Step 2: Command Dispatched via MQTT */}
                        <div className="flex items-start gap-3">
                          <div
                            className={`w-6 h-6 rounded-full flex items-center justify-center shrink-0 mt-0.5 shadow-xs ${
                              order.status === 'DISPENSING' || order.status === 'COMPLETED'
                                ? 'bg-emerald-500 text-white'
                                : 'bg-blue-500 text-white animate-pulse'
                            }`}
                          >
                            {order.status === 'DISPENSING' || order.status === 'COMPLETED' ? (
                              <Check className="w-3.5 h-3.5 stroke-[3]" />
                            ) : (
                              <Loader2 className="w-3.5 h-3.5 animate-spin" />
                            )}
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between">
                              <span className="font-bold text-brand-primary">2. Phát lệnh đến máy</span>
                              <span
                                className={`text-[10px] font-mono font-bold ${
                                  order.status === 'DISPENSING' || order.status === 'COMPLETED'
                                    ? 'text-emerald-700'
                                    : 'text-blue-700'
                                }`}
                              >
                                {order.status === 'DISPENSING' || order.status === 'COMPLETED'
                                  ? 'ĐÃ GỬI'
                                  : 'ĐANG PHÁT...'}
                              </span>
                            </div>
                            <p className="text-[11px] text-brand-text-muted mt-0.5">
                              Backend ghi nhận bản ghi bền vững và phát lệnh qua MQTT tới ESP32
                            </p>
                          </div>
                        </div>

                        {/* Step 3: Motor Executing */}
                        <div className="flex items-start gap-3">
                          <div
                            className={`w-6 h-6 rounded-full flex items-center justify-center shrink-0 mt-0.5 shadow-xs ${
                              order.status === 'COMPLETED'
                                ? 'bg-emerald-500 text-white'
                                : order.status === 'DISPENSING'
                                ? 'bg-blue-500 text-white animate-spin'
                                : 'bg-gray-200 text-gray-400'
                            }`}
                          >
                            {order.status === 'COMPLETED' ? (
                              <Check className="w-3.5 h-3.5 stroke-[3]" />
                            ) : order.status === 'DISPENSING' ? (
                              <RotateCw className="w-3.5 h-3.5" />
                            ) : (
                              <span className="w-2 h-2 rounded-full bg-gray-400" />
                            )}
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between">
                              <span className="font-bold text-brand-primary">3. Động cơ quay lò xo</span>
                              <span
                                className={`text-[10px] font-mono font-bold ${
                                  order.status === 'COMPLETED'
                                    ? 'text-emerald-700'
                                    : order.status === 'DISPENSING'
                                    ? 'text-blue-700'
                                    : 'text-gray-400'
                                }`}
                              >
                                {order.status === 'COMPLETED'
                                  ? 'XONG'
                                  : order.status === 'DISPENSING'
                                  ? 'ĐANG QUAY...'
                                  : 'CHỜ'}
                              </span>
                            </div>
                            <p className="text-[11px] text-brand-text-muted mt-0.5">
                              Động cơ khay xoay lò xo để đẩy sản phẩm ra khỏi ngăn chứa
                            </p>
                          </div>
                        </div>

                        {/* Step 4: Product Dispensed */}
                        <div className="flex items-start gap-3">
                          <div
                            className={`w-6 h-6 rounded-full flex items-center justify-center shrink-0 mt-0.5 shadow-xs ${
                              order.status === 'COMPLETED'
                                ? 'bg-emerald-500 text-white'
                                : 'bg-gray-200 text-gray-400'
                            }`}
                          >
                            {order.status === 'COMPLETED' ? (
                              <Check className="w-3.5 h-3.5 stroke-[3]" />
                            ) : (
                              <span className="w-2 h-2 rounded-full bg-gray-400" />
                            )}
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between">
                              <span className="font-bold text-brand-primary">4. Rơi vào máng nhận</span>
                              <span
                                className={`text-[10px] font-mono font-bold ${
                                  order.status === 'COMPLETED' ? 'text-emerald-700' : 'text-gray-400'
                                }`}
                              >
                                {order.status === 'COMPLETED' ? 'XONG' : 'CHỜ'}
                              </span>
                            </div>
                            <p className="text-[11px] text-brand-text-muted mt-0.5">
                              Hệ thống xác nhận chu kỳ nhả sản phẩm hoàn tất. Quý khách vui lòng lấy hàng tại máng nhận
                            </p>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Order Details Summary */}
                    <div className="p-3.5 sm:p-4 rounded-2xl glass-surface text-left space-y-2 text-xs">
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Mã đơn hàng:</span>
                        <span className="font-mono font-bold text-brand-primary">{order.publicOrderCode}</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Sản phẩm:</span>
                        <span className="font-bold text-brand-text">{order.product?.name}</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Số tiền đã xác minh:</span>
                        <span className="font-bold text-emerald-700">{formatVnd(order.totalAmountVnd)}</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Trạng thái:</span>
                        <span
                          className={`px-2.5 py-0.5 rounded-full font-bold text-[10px] ${
                            order.status === 'COMPLETED'
                              ? 'bg-emerald-500/15 text-emerald-800 border border-emerald-500/30'
                              : order.status === 'DISPENSING'
                              ? 'bg-blue-500/15 text-blue-800 border border-blue-500/30'
                              : 'bg-emerald-500/15 text-emerald-800 border border-emerald-500/30'
                          }`}
                        >
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
                        className="w-full py-3 px-4 rounded-2xl bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white text-xs sm:text-sm font-bold shadow-md hover:shadow-floating border border-emerald-400/25 transition-all min-h-[44px] cursor-pointer"
                      >
                        {order.status === 'COMPLETED' ? 'Lấy hàng & Đóng cửa sổ' : 'Hoàn tất / Đóng'}
                      </button>
                    </div>
                  </div>
                ) : order.status === 'EXPIRED' ? (
                  /* EXPIRED ORDER */
                  <div className="space-y-4 text-center py-2" data-testid="payment-expired-view">
                    <div className="w-16 h-16 bg-amber-100 rounded-full flex items-center justify-center mx-auto text-amber-600 shadow-sm border border-amber-300/40">
                      <Clock className="w-9 h-9" />
                    </div>
                    <div>
                      <h3 className="text-base sm:text-lg font-bold text-amber-900">
                        Đơn hàng đã hết hạn thanh toán
                      </h3>
                      <p className="text-xs text-brand-text-muted mt-1 leading-relaxed max-w-sm mx-auto">
                        Thời gian giữ chỗ 15 phút đã kết thúc và đơn hàng đã bị hủy tự động. Quý khách vui lòng đặt lại đơn mới.
                      </p>
                    </div>

                    <div className="p-4 rounded-2xl glass-surface border-amber-300/40 text-left space-y-2 text-xs">
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Mã đơn hàng:</span>
                        <span className="font-mono font-bold text-brand-text">{order.publicOrderCode}</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Trạng thái:</span>
                        <span className="px-2.5 py-0.5 rounded-full bg-amber-500/15 text-amber-800 border border-amber-400/30 font-bold text-[10px]">
                          HẾT HẠN (EXPIRED)
                        </span>
                      </div>
                    </div>

                    <div className="pt-2">
                      <button
                        type="button"
                        onClick={onClose}
                        className="w-full py-3 px-4 rounded-2xl border border-gray-300/60 bg-white/70 hover:bg-white text-xs sm:text-sm font-semibold text-brand-text transition-colors min-h-[44px] cursor-pointer"
                      >
                        Đóng cửa sổ
                      </button>
                    </div>
                  </div>
                ) : order.status === 'NEEDS_RECONCILIATION' ? (
                  /* NEEDS RECONCILIATION */
                  <div className="space-y-4 text-center py-2" data-testid="payment-reconciliation-view">
                    <div className="w-16 h-16 bg-purple-100 rounded-full flex items-center justify-center mx-auto text-purple-600 shadow-sm border border-purple-300/40">
                      <ShieldAlert className="w-9 h-9" />
                    </div>
                    <div>
                      <h3 className="text-base sm:text-lg font-bold text-purple-900">
                        Giao dịch cần đối soát
                      </h3>
                      <p className="text-xs text-brand-text-muted mt-1.5 leading-relaxed max-w-sm mx-auto">
                        Hệ thống ghi nhận tiền chuyển đến sau khi đơn hết hạn hoặc có bất thường. Máy không xuất hàng để bảo vệ khách. Vui lòng chụp màn hình thông tin đơn và liên hệ hỗ trợ.
                      </p>
                    </div>

                    <div className="p-4 rounded-2xl glass-surface border-purple-300/40 text-left space-y-2 text-xs">
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Mã đơn hàng:</span>
                        <span className="font-mono font-bold text-brand-text">{order.publicOrderCode}</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Nội dung đối soát:</span>
                        <span className="font-mono font-bold text-brand-primary">{order.payment?.transferReference}</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Trạng thái:</span>
                        <span className="px-2.5 py-0.5 rounded-full bg-purple-500/15 text-purple-800 border border-purple-400/30 font-bold text-[10px]">
                          CHỜ ĐỐI SOÁT (NEEDS_RECONCILIATION)
                        </span>
                      </div>
                    </div>

                    <div className="pt-2">
                      <button
                        type="button"
                        onClick={onClose}
                        className="w-full py-3 px-4 rounded-2xl border border-gray-300/60 bg-white/70 hover:bg-white text-xs sm:text-sm font-semibold text-brand-text transition-colors min-h-[44px] cursor-pointer"
                      >
                        Đóng cửa sổ
                      </button>
                    </div>
                  </div>
                ) : order.status === 'DISPENSE_FAILED' ? (
                  /* DISPENSE FAILED */
                  <div className="space-y-4 text-center py-2" data-testid="payment-dispense-failed-view">
                    <div className="w-16 h-16 bg-rose-100 rounded-full flex items-center justify-center mx-auto text-rose-600 shadow-sm border border-rose-300/40">
                      <AlertCircle className="w-9 h-9" />
                    </div>
                    <div>
                      <h3 className="text-base sm:text-lg font-bold text-rose-900">
                        Sự cố khi xuất hàng
                      </h3>
                      <p className="text-xs text-brand-text-muted mt-1.5 leading-relaxed max-w-sm mx-auto">
                        Máy gặp sự cố cơ cấu khi đẩy sản phẩm. Thông tin giao dịch đã được hệ thống ghi nhận. Quý khách vui lòng liên hệ nhân viên vận hành để được kiểm tra và hỗ trợ theo quy định.
                      </p>
                    </div>

                    <div className="p-4 rounded-2xl glass-surface border-rose-300/40 text-left space-y-2 text-xs">
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Mã đơn hàng:</span>
                        <span className="font-mono font-bold text-brand-text">{order.publicOrderCode}</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-brand-text-muted">Trạng thái:</span>
                        <span className="px-2.5 py-0.5 rounded-full bg-rose-500/15 text-rose-800 border border-rose-400/30 font-bold text-[10px]">
                          XUẤT HÀNG THẤT BẠI (DISPENSE_FAILED)
                        </span>
                      </div>
                    </div>

                    <div className="pt-2">
                      <button
                        type="button"
                        onClick={onClose}
                        className="w-full py-3 px-4 rounded-2xl border border-gray-300/60 bg-white/70 hover:bg-white text-xs sm:text-sm font-semibold text-brand-text transition-colors min-h-[44px] cursor-pointer"
                      >
                        Đóng cửa sổ
                      </button>
                    </div>
                  </div>
                ) : (
                  /* PENDING_PAYMENT: QR CODE & POLLING INDICATOR */
                  <>
                    <div className="p-3.5 sm:p-4 rounded-2xl bg-emerald-500/15 border border-emerald-500/30 text-center backdrop-blur-xs">
                      <span className="text-xs font-bold text-emerald-800 uppercase tracking-wider block mb-0.5">
                        Đơn hàng đã được tạo thành công
                      </span>
                      <p className="font-mono text-sm sm:text-base font-bold text-brand-primary">{order.publicOrderCode}</p>
                    </div>

                    {/* QR Code Presentation */}
                    {order.payment && order.payment.paymentQrData ? (
                      <div className="flex flex-col items-center justify-center p-4 sm:p-5 glass-surface rounded-2xl border border-emerald-500/20 shadow-2xs">
                        {/* Specular White QR Code Container */}
                        <div className="p-3.5 bg-white rounded-2xl shadow-md border border-gray-200/90 mb-3 relative">
                          <QRCodeSVG value={order.payment.paymentQrData} size={185} level="M" />
                        </div>

                        <span className="text-xs font-bold text-brand-primary">Quét mã bằng ứng dụng Ngân hàng</span>

                        <div className="mt-3.5 w-full space-y-2 text-xs">
                          {/* Bank & Account row with copy button */}
                          <div className="flex items-center justify-between p-2.5 rounded-xl bg-white/60 border border-emerald-500/15">
                            <div className="text-left">
                              <span className="text-[10px] uppercase font-bold text-brand-text-muted block">Ngân hàng & Số tài khoản</span>
                              <span className="font-mono font-bold text-brand-text text-xs">
                                {order.payment.bankName} • {order.payment.bankAccountNumber}
                              </span>
                            </div>
                            <button
                              type="button"
                              onClick={() => handleCopy(order.payment.bankAccountNumber, 'acc')}
                              aria-label="Sao chép số tài khoản"
                              className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-brand-primary/10 hover:bg-brand-primary/20 text-brand-primary font-bold text-[11px] transition-colors cursor-pointer"
                            >
                              {copiedField === 'acc' ? (
                                <>
                                  <Check className="w-3.5 h-3.5 text-emerald-600" />
                                  <span>Đã chép</span>
                                </>
                              ) : (
                                <>
                                  <Copy className="w-3.5 h-3.5" />
                                  <span>Chép STK</span>
                                </>
                              )}
                            </button>
                          </div>

                          {/* Account holder */}
                          <div className="flex justify-between items-center px-2.5 py-1 text-xs">
                            <span className="text-brand-text-muted">Chủ tài khoản:</span>
                            <span className="font-bold text-brand-text">{order.payment.bankAccountName}</span>
                          </div>

                          {/* Transfer reference with copy button */}
                          <div className="flex items-center justify-between p-2.5 rounded-xl bg-brand-primary/10 border border-brand-primary/20">
                            <div className="text-left">
                              <span className="text-[10px] uppercase font-bold text-brand-text-muted block">Nội dung chuyển khoản</span>
                              <span className="font-mono font-bold text-brand-primary text-xs">
                                Nội dung CK: {order.payment.transferReference}
                              </span>
                            </div>
                            <button
                              type="button"
                              onClick={() => handleCopy(order.payment.transferReference, 'ref')}
                              aria-label="Sao chép nội dung chuyển khoản"
                              className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-brand-primary hover:bg-brand-primary-hover text-white font-bold text-[11px] transition-colors cursor-pointer"
                            >
                              {copiedField === 'ref' ? (
                                <>
                                  <Check className="w-3.5 h-3.5 text-brand-accent" />
                                  <span>Đã chép</span>
                                </>
                              ) : (
                                <>
                                  <Copy className="w-3.5 h-3.5" />
                                  <span>Chép mã</span>
                                </>
                              )}
                            </button>
                          </div>
                        </div>
                      </div>
                    ) : null}

                    {/* Live Polling Status Indicator */}
                    <div className="flex items-center justify-center gap-2 p-3 rounded-2xl bg-brand-primary/10 border border-emerald-500/20 text-xs text-brand-primary font-medium backdrop-blur-xs">
                      <Loader2 className="w-4 h-4 animate-spin shrink-0 text-brand-primary" />
                      <span>Đang chờ tín hiệu thanh toán từ ngân hàng (tự động cập nhật mỗi 3s)...</span>
                    </div>

                    {/* Amount & Time limit */}
                    <div className="flex justify-between items-center p-3.5 sm:p-4 rounded-2xl glass-surface text-sm border border-emerald-500/15">
                      <div className="flex items-center gap-2 text-brand-text-muted font-medium text-xs">
                        <Clock className="w-4 h-4 text-brand-primary" />
                        <span>Hạn thanh toán:</span>
                      </div>
                      <span className="font-bold text-brand-primary text-xs">15 phút</span>
                    </div>

                    <p className="text-[11px] text-center text-brand-text-subtle">
                      Hệ thống máy sẽ tự động kích hoạt nhả hàng sau khi nhận tín hiệu giao dịch hợp lệ từ ngân hàng.
                    </p>

                    <div className="pt-2">
                      <button
                        type="button"
                        onClick={onClose}
                        className="w-full py-3 px-4 rounded-2xl border border-gray-300/60 bg-white/70 hover:bg-white text-xs sm:text-sm font-semibold text-brand-text transition-colors min-h-[44px] cursor-pointer"
                      >
                        Đóng / Quay lại sau
                      </button>
                    </div>
                  </>
                )}
              </div>
            ) : (
              /* ORDER CONFIRMATION FLOW */
              <div className="space-y-4">
                {/* MVP Prototype Notice */}
                <div className="p-3.5 rounded-2xl bg-brand-primary/10 border border-emerald-500/20 text-xs text-brand-primary flex items-start gap-2.5 backdrop-blur-xs">
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
                          className={`w-full p-3.5 rounded-2xl border text-left flex items-center justify-between text-xs transition-all cursor-pointer ${
                            selectedProductIndex === idx
                              ? 'border-brand-primary bg-emerald-500/15 font-semibold shadow-xs'
                              : 'glass-surface text-brand-text-muted hover:border-emerald-500/30'
                          }`}
                        >
                          <div className="flex items-center gap-2">
                            <span className="px-2 py-0.5 rounded-lg bg-brand-primary/10 text-brand-primary text-[10px] font-bold">
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

                {/* Selected Item Detail Card */}
                {currentItem && (
                  <div className="p-4 rounded-2xl glass-surface space-y-3 border border-emerald-500/15">
                    {orderProductImage && (
                      <div className="flex items-center gap-3 pb-3 border-b border-emerald-500/15">
                        <div className="w-14 h-14 rounded-xl bg-white/70 border border-emerald-500/15 p-1 flex items-center justify-center shrink-0">
                          <img
                            src={orderProductImage}
                            alt={currentItem.product.name}
                            className="w-full h-full object-contain"
                          />
                        </div>
                        <div>
                          <span className="text-[10px] font-mono font-bold uppercase tracking-wider text-brand-primary px-2 py-0.5 rounded-md bg-brand-primary/10">
                            Khay 0{currentItem.product.slotNumber}
                          </span>
                          <h4 className="font-bold text-sm text-brand-text mt-1">{currentItem.product.name}</h4>
                          <span className="text-xs font-black text-brand-primary">{formatVnd(currentItem.product.priceVnd)}</span>
                        </div>
                      </div>
                    )}

                    <div className="flex justify-between items-center text-xs">
                      <span className="text-brand-text-muted">Mã máy bán hàng:</span>
                      <span className="font-mono font-bold text-brand-primary">{machineId}</span>
                    </div>
                    <div className="flex justify-between items-center text-xs">
                      <span className="text-brand-text-muted">Sản phẩm:</span>
                      <span className="font-bold text-brand-text">{currentItem.product.name}</span>
                    </div>
                    <div className="flex justify-between items-center text-xs">
                      <span className="text-brand-text-muted">Khay xuất hàng:</span>
                      <span className="font-semibold text-brand-text">Khay số {currentItem.product.slotNumber}</span>
                    </div>
                    <div className="flex justify-between items-center text-xs">
                      <span className="text-brand-text-muted">Số lượng đơn hàng:</span>
                      <span className="font-semibold text-brand-text">1 sản phẩm</span>
                    </div>
                    <div className="pt-2 border-t border-emerald-500/15 flex justify-between items-center">
                      <span className="text-xs sm:text-sm font-bold text-brand-text">Tổng tiền thanh toán:</span>
                      <span className="text-lg sm:text-xl font-black text-brand-primary">
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
                        ? 'bg-amber-500/15 border-amber-400/30 text-amber-900 backdrop-blur-xs'
                        : 'bg-rose-500/15 border-rose-400/30 text-rose-900 backdrop-blur-xs'
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
                      <div className="mt-2 pt-2 border-t border-amber-300/40 text-[11px] text-amber-800">
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
                    className="flex-1 py-3 px-4 rounded-2xl border border-gray-300/60 bg-white/70 hover:bg-white text-xs sm:text-sm font-semibold text-brand-text transition-colors min-h-[44px] cursor-pointer"
                  >
                    Quay lại chọn món
                  </button>
                  <button
                    id="confirm-create-order-btn"
                    type="button"
                    disabled={loading || !currentItem}
                    onClick={handleCreateOrder}
                    className="flex-1 flex items-center justify-center gap-2 py-3 px-4 rounded-2xl bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white text-xs sm:text-sm font-bold shadow-md hover:shadow-floating border border-emerald-400/25 transition-all duration-150 disabled:bg-gray-300/60 disabled:cursor-not-allowed min-h-[44px] cursor-pointer"
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
