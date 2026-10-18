import React from 'react';
import { ShoppingBag, Sparkles, Store } from 'lucide-react';
import { useCart } from '../context/CartContext';
import type { MachineResponse } from '../types/machine';
import { formatVnd } from '../utils/formatters';

interface NavbarProps {
  machine?: MachineResponse | null;
}

export const Navbar: React.FC<NavbarProps> = ({ machine }) => {
  const { totalItems, totalAmountVnd, setIsCartOpen } = useCart();

  return (
    <header className="sticky top-0 z-40 w-full glass-header border-b border-brand-border/80 shadow-sm transition-all duration-200">
      <div className="max-w-5xl mx-auto px-4 h-16 flex items-center justify-between gap-3">
        {/* Brand identity */}
        <div className="flex items-center gap-2.5">
          <div className="w-10 h-10 rounded-xl bg-brand-primary flex items-center justify-center text-brand-accent shadow-md">
            <Store className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <span className="font-bold text-lg text-brand-primary tracking-tight">Smart Vending</span>
              <span className="hidden sm:inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-semibold bg-brand-accent/30 text-brand-primary border border-brand-accent/50">
                <Sparkles className="w-2.5 h-2.5" /> Prototype
              </span>
            </div>
            <p className="text-xs text-brand-text-muted hidden sm:block">Máy bán hàng tự động thông minh</p>
          </div>
        </div>

        {/* Machine indicator & Cart trigger */}
        <div className="flex items-center gap-2.5">
          {machine && (
            <div className="hidden xs:flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white border border-brand-border shadow-xs text-xs font-medium text-brand-text">
              <span
                className={`w-2 h-2 rounded-full ${
                  machine.status === 'ACTIVE' ? 'bg-emerald-500 animate-pulse' : 'bg-rose-500'
                }`}
              />
              <span className="text-brand-text-muted">Máy:</span>
              <span className="font-semibold text-brand-primary">{machine.machineId}</span>
            </div>
          )}

          <button
            id="open-cart-button"
            type="button"
            onClick={() => setIsCartOpen(true)}
            aria-label="Xem giỏ hàng"
            className="relative flex items-center gap-2 px-3.5 py-2 rounded-xl bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white shadow-md hover:shadow-lg transition-all duration-150"
          >
            <div className="relative">
              <ShoppingBag className="w-5 h-5 text-brand-accent" />
              {totalItems > 0 && (
                <span
                  id="cart-badge-count"
                  className="absolute -top-2 -right-2 w-5 h-5 bg-brand-accent text-brand-primary text-xs font-bold rounded-full flex items-center justify-center shadow-xs animate-bounce"
                >
                  {totalItems}
                </span>
              )}
            </div>
            <div className="hidden sm:flex flex-col items-start leading-tight">
              <span className="text-[11px] text-white/80">Giỏ hàng</span>
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
