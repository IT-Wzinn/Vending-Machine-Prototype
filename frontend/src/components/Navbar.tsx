import React from 'react';
import { ShoppingBag, Store } from 'lucide-react';
import { useCart } from '../context/CartContext';
import type { MachineResponse } from '../types/machine';
import { formatVnd } from '../utils/formatters';

interface NavbarProps {
  machine?: MachineResponse | null;
}

export const Navbar: React.FC<NavbarProps> = ({ machine }) => {
  const { totalItems, totalAmountVnd, setIsCartOpen } = useCart();

  return (
    <header className="sticky top-0 z-40 w-full glass-header transition-all duration-200">
      <div className="max-w-5xl mx-auto px-4 h-12 sm:h-13 flex items-center justify-between gap-3">
        {/* Brand identity */}
        <div className="flex items-center gap-2 sm:gap-2.5">
          <div className="w-8 h-8 rounded-xl bg-brand-primary flex items-center justify-center text-brand-accent shadow-2xs border border-emerald-400/25 shrink-0">
            <Store className="w-4 h-4" />
          </div>
          <div className="flex items-center gap-2">
            <span className="font-extrabold text-base sm:text-lg text-brand-primary tracking-tight">FreshVend</span>
          </div>
        </div>

        {/* Machine indicator & Cart trigger */}
        <div className="flex items-center gap-2 sm:gap-2.5">
          {machine && (
            <div className="flex items-center gap-1.5 px-2.5 sm:px-3 py-1 rounded-full glass-pill text-xs font-medium text-brand-text shadow-2xs">
              <span
                className={`w-2 h-2 rounded-full shrink-0 ${
                  machine.status === 'ACTIVE' ? 'bg-emerald-500 animate-pulse' : 'bg-rose-500'
                }`}
              />
              <span className="font-mono font-bold text-brand-primary text-[11px] sm:text-xs tracking-wider">
                {machine.machineId}
              </span>
              <span className="text-[10px] sm:text-[11px] font-semibold text-brand-text-muted hidden xs:inline">
                {machine.status === 'ACTIVE' ? '• Trực tuyến' : '• Tạm ngừng'}
              </span>
            </div>
          )}

          <button
            id="open-cart-button"
            type="button"
            onClick={() => setIsCartOpen(true)}
            aria-label="Xem giỏ hàng"
            className="relative flex items-center gap-2 px-3 sm:px-3.5 py-1.5 sm:py-2 rounded-xl sm:rounded-2xl bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white shadow-sm hover:shadow-floating transition-all duration-150 min-h-[40px] sm:min-h-[44px] border border-emerald-400/20 cursor-pointer"
          >
            <div className="relative">
              <ShoppingBag className="w-4 h-4 sm:w-4.5 sm:h-4.5 text-brand-accent" />
              {totalItems > 0 && (
                <span
                  id="cart-badge-count"
                  className="absolute -top-1.5 -right-2 w-4.5 h-4.5 bg-brand-accent text-brand-primary text-[10px] sm:text-xs font-extrabold rounded-full flex items-center justify-center shadow-xs animate-bounce"
                >
                  {totalItems}
                </span>
              )}
            </div>
            <div className="hidden sm:flex flex-col items-start leading-tight">
              <span className="text-[9px] uppercase tracking-wider font-semibold text-white/75">Giỏ hàng</span>
              <span className="text-xs font-bold text-brand-accent">
                {totalItems > 0 ? formatVnd(totalAmountVnd) : '0 ₫'}
              </span>
            </div>
          </button>
        </div>
      </div>
    </header>
  );
};
