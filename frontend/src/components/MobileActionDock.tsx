import React from 'react';
import { ShoppingBag, ArrowRight } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { formatVnd } from '../utils/formatters';

interface MobileActionDockProps {
  onOpenCart: () => void;
  onProceedToCheckout: () => void;
  machineActive?: boolean;
}

export const MobileActionDock: React.FC<MobileActionDockProps> = ({
  onOpenCart,
  onProceedToCheckout,
  machineActive = true,
}) => {
  const { totalItems, totalAmountVnd } = useCart();

  if (totalItems === 0) return null;

  return (
    <aside
      aria-label="Thanh tác vụ thanh toán nhanh"
      style={{ bottom: 'calc(0.75rem + env(safe-area-inset-bottom, 0px))' }}
      className="fixed inset-x-3.5 z-30 sm:hidden glass-modal rounded-2xl p-2 px-3.5 shadow-2xl border border-emerald-500/30 flex items-center justify-between gap-3 animate-in fade-in slide-in-from-bottom-4 duration-200"
    >
      {/* Cart Summary trigger - opens cart drawer for item review */}
      <button
        id="mobile-cart-summary-btn"
        type="button"
        onClick={onOpenCart}
        aria-label="Xem chi tiết giỏ hàng"
        className="flex items-center gap-2.5 text-left active:scale-95 transition-transform cursor-pointer"
      >
        <div className="relative w-9 h-9 rounded-xl bg-brand-primary flex items-center justify-center text-brand-accent shadow-xs shrink-0 border border-emerald-400/25">
          <ShoppingBag className="w-4.5 h-4.5 text-brand-accent" />
          <span
            id="mobile-cart-badge-count"
            className="absolute -top-1.5 -right-1.5 w-4.5 h-4.5 bg-brand-accent text-brand-primary text-[10px] font-black rounded-full flex items-center justify-center shadow-xs"
          >
            {totalItems}
          </span>
        </div>
        <div className="flex flex-col leading-tight">
          <span className="text-[10px] font-bold uppercase tracking-wider text-brand-text-muted">
            {totalItems} món đã chọn
          </span>
          <span className="text-sm font-black text-brand-primary tracking-tight font-sans">
            {formatVnd(totalAmountVnd)}
          </span>
        </div>
      </button>

      {/* Prominent Checkout CTA - opens checkout flow directly */}
      <button
        id="mobile-checkout-btn"
        type="button"
        disabled={!machineActive}
        onClick={onProceedToCheckout}
        aria-label="Thanh toán ngay"
        className={`flex items-center gap-1.5 px-3.5 py-2 rounded-xl font-extrabold text-xs tracking-wide shadow-md transition-all active:scale-95 min-h-[44px] cursor-pointer ${
          machineActive
            ? 'bg-brand-primary hover:bg-brand-primary-hover text-white shadow-floating border border-emerald-400/25'
            : 'bg-gray-300 text-gray-500 cursor-not-allowed border border-gray-400/30'
        }`}
      >
        <span>Thanh toán</span>
        <ArrowRight className="w-3.5 h-3.5 text-brand-accent stroke-[2.5]" />
      </button>
    </aside>
  );
};

