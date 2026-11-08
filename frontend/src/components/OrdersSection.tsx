import React from 'react';
import {
  ShoppingBag,
  ArrowLeft,
  Clock,
  QrCode,
  PackageCheck,
  RotateCcw,
} from 'lucide-react';
import type { MachineResponse } from '../types/machine';

interface OrdersSectionProps {
  machine?: MachineResponse | null;
  onBackToHome: () => void;
  onOpenCheckout?: () => void;
}

export const OrdersSection: React.FC<OrdersSectionProps> = ({
  machine,
  onBackToHome,
}) => {
  // Check if there is an active or saved order in sessionStorage
  const lastOrderCode = typeof window !== 'undefined' ? sessionStorage.getItem('last_order_code') : null;
  const lastOrderStatus = typeof window !== 'undefined' ? sessionStorage.getItem('last_order_status') : null;
  const lastOrderAmount = typeof window !== 'undefined' ? sessionStorage.getItem('last_order_amount') : null;

  const hasRecentOrder = Boolean(lastOrderCode);

  return (
    <div className="w-full max-w-4xl mx-auto space-y-6 sm:space-y-8 animate-fade-in">
      {/* Top back button and title header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <button
          type="button"
          onClick={onBackToHome}
          className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-white/80 hover:bg-white text-xs font-bold text-brand-primary border border-emerald-500/20 shadow-2xs backdrop-blur-md transition-all active:scale-95 cursor-pointer"
        >
          <ArrowLeft className="w-4 h-4 text-brand-primary" />
          <span>Quay lại đặt hàng</span>
        </button>

        {machine && (
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-950/70 text-brand-accent text-xs font-mono font-bold border border-emerald-400/20">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            <span>Mã máy: {machine.machineId}</span>
            <span className="text-white/60 font-sans font-normal hidden sm:inline">• {machine.name}</span>
          </div>
        )}
      </div>

      {/* Main Container Card */}
      <div className="rounded-3xl bg-white/95 backdrop-blur-md shadow-card border border-emerald-500/15 p-6 sm:p-8">
        <div className="flex items-center gap-3 mb-6">
          <div className="w-10 h-10 rounded-2xl bg-brand-primary/10 flex items-center justify-center text-brand-primary border border-emerald-500/20">
            <ShoppingBag className="w-5 h-5 text-brand-primary" />
          </div>
          <div>
            <h2 className="text-xl sm:text-2xl font-black text-brand-primary tracking-tight">
              Đơn hàng của bạn
            </h2>
            <p className="text-xs sm:text-sm text-brand-text-muted">
              Lịch sử các phiên đặt hàng và trạng thái xuất hàng tại máy
            </p>
          </div>
        </div>

        {hasRecentOrder ? (
          <div className="space-y-4">
            <div className="p-5 rounded-2xl border border-emerald-500/20 bg-emerald-50/40 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
              <div className="space-y-1.5">
                <div className="flex items-center gap-2">
                  <span className="font-mono font-extrabold text-sm text-brand-primary">
                    Mã đơn: #{lastOrderCode}
                  </span>
                  <span className="text-xs px-2.5 py-0.5 rounded-full font-bold bg-emerald-100 text-emerald-800 border border-emerald-300">
                    {lastOrderStatus || 'Đang xử lý'}
                  </span>
                </div>
                <p className="text-xs text-brand-text-muted">
                  Máy bán hàng: <strong className="text-brand-primary">{machine?.name || 'VM001'}</strong>
                </p>
                {lastOrderAmount && (
                  <p className="text-sm font-bold text-brand-primary">
                    Tổng tiền: {lastOrderAmount}
                  </p>
                )}
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={onBackToHome}
                  className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-brand-primary text-white text-xs font-bold hover:bg-brand-primary-hover active:scale-95 transition-all shadow-xs cursor-pointer"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Mua thêm món mới</span>
                </button>
              </div>
            </div>
          </div>
        ) : (
          /* Empty orders state */
          <div className="py-12 px-4 text-center flex flex-col items-center justify-center max-w-md mx-auto">
            <div className="w-16 h-16 rounded-3xl bg-emerald-50 border border-emerald-200 flex items-center justify-center text-emerald-600 mb-4 shadow-2xs">
              <ShoppingBag className="w-8 h-8 stroke-[1.5]" />
            </div>
            <h3 className="text-base font-extrabold text-brand-primary mb-1">
              Chưa có đơn hàng nào trong phiên này
            </h3>
            <p className="text-xs text-brand-text-muted mb-6 leading-relaxed">
              Bạn chưa tạo đơn hàng nào gần đây. Hãy chọn các sản phẩm nước khoáng, bánh snack thơm ngon tại máy để đặt hàng ngay!
            </p>
            <button
              type="button"
              onClick={onBackToHome}
              className="inline-flex items-center gap-2 px-6 py-2.5 rounded-full bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white font-extrabold text-xs shadow-md transition-all cursor-pointer border border-emerald-400/25"
            >
              <ShoppingBag className="w-4 h-4 text-brand-accent" />
              <span>Khám phá danh mục món</span>
            </button>
          </div>
        )}

        {/* Info guide on how order tracking works */}
        <div className="mt-8 pt-6 border-t border-emerald-500/10 grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div className="p-3.5 rounded-xl bg-emerald-50/50 border border-emerald-500/10 flex items-center gap-2.5">
            <Clock className="w-4 h-4 text-emerald-700 shrink-0" />
            <span className="text-[11px] text-brand-text font-medium">Lưu phiên đặt hàng trong trình duyệt</span>
          </div>
          <div className="p-3.5 rounded-xl bg-emerald-50/50 border border-emerald-500/10 flex items-center gap-2.5">
            <QrCode className="w-4 h-4 text-emerald-700 shrink-0" />
            <span className="text-[11px] text-brand-text font-medium">Quét VietQR từ mọi ngân hàng</span>
          </div>
          <div className="p-3.5 rounded-xl bg-emerald-50/50 border border-emerald-500/10 flex items-center gap-2.5">
            <PackageCheck className="w-4 h-4 text-emerald-700 shrink-0" />
            <span className="text-[11px] text-brand-text font-medium">Nhận hàng tự động trong 3 giây</span>
          </div>
        </div>
      </div>
    </div>
  );
};
