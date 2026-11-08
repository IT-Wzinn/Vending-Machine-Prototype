import React from 'react';
import { ShoppingBag, Home, Headphones, Info } from 'lucide-react';
import { useCart } from '../context/CartContext';
import type { MachineResponse } from '../types/machine';
import { formatVnd } from '../utils/formatters';

export type NavTab = 'home' | 'orders' | 'support' | 'about';

interface NavbarProps {
  machine?: MachineResponse | null;
  activeTab?: NavTab;
  onTabChange?: (tab: NavTab) => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  machine,
  activeTab = 'home',
  onTabChange,
}) => {
  const { totalItems, totalAmountVnd, setIsCartOpen } = useCart();

  const navItems: { id: NavTab; label: string; icon: React.ReactNode }[] = [
    { id: 'home', label: 'Trang chủ', icon: <Home className="w-3.5 h-3.5 sm:w-4 sm:h-4" /> },
    { id: 'orders', label: 'Đơn hàng', icon: <ShoppingBag className="w-3.5 h-3.5 sm:w-4 sm:h-4" /> },
    { id: 'support', label: 'Hỗ trợ', icon: <Headphones className="w-3.5 h-3.5 sm:w-4 sm:h-4" /> },
    { id: 'about', label: 'Về chúng tôi', icon: <Info className="w-3.5 h-3.5 sm:w-4 sm:h-4" /> },
  ];

  return (
    <header className="sticky top-0 z-40 w-full glass-header transition-all duration-200">
      <div className="max-w-[1440px] xl:max-w-[1500px] mx-auto px-4 sm:px-6 lg:px-8 h-16 sm:h-20 flex items-center justify-between gap-3 sm:gap-6">
        {/* Brand identity on left — Scaled up */}
        <button
          type="button"
          onClick={() => onTabChange?.('home')}
          aria-label="FreshVend Trang chủ"
          className="flex items-center gap-2.5 sm:gap-3.5 cursor-pointer select-none text-left active:scale-95 transition-transform"
        >
          <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-2xl overflow-hidden bg-brand-primary flex items-center justify-center shrink-0 shadow-sm border border-emerald-400/30">
            <img
              src="/images/freshvend-logo.png"
              alt="FreshVend"
              className="w-full h-full object-cover"
              onError={(e) => {
                // Hide broken image and keep container styled
                (e.target as HTMLElement).style.display = 'none';
              }}
            />
          </div>
          <span className="font-black text-xl sm:text-2xl lg:text-[26px] text-brand-primary tracking-tight font-sans">
            FreshVend
          </span>
        </button>

        {/* Center Navigation Pills (matches user mockup) — Scaled up */}
        <nav
          aria-label="Main Navigation"
          className="hidden md:flex items-center gap-1.5 sm:gap-2 p-1.5 rounded-full bg-white/60 backdrop-blur-md border border-emerald-500/20 shadow-xs"
        >
          {navItems.map((item) => {
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => onTabChange?.(item.id)}
                className={`flex items-center gap-2 px-4 sm:px-5 py-2 sm:py-2.5 rounded-full text-xs sm:text-sm font-extrabold transition-all duration-150 cursor-pointer ${
                  isActive
                    ? 'bg-white text-brand-primary shadow-sm border border-emerald-500/25'
                    : 'text-brand-text/75 hover:text-brand-primary hover:bg-white/70'
                }`}
              >
                <span className="w-4 h-4 sm:w-4.5 sm:h-4.5 flex items-center justify-center">{item.icon}</span>
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>

        {/* Right side actions: Machine indicator & Cart trigger — Scaled up */}
        <div className="flex items-center gap-2.5 sm:gap-4">
          {machine && (
            <div className="hidden sm:flex items-center gap-2 px-3.5 py-1.5 sm:py-2 rounded-full glass-pill text-xs sm:text-sm font-medium text-brand-text shadow-2xs border border-emerald-500/15">
              <span
                className={`w-2.5 h-2.5 rounded-full shrink-0 ${
                  machine.status === 'ACTIVE' ? 'bg-emerald-500 animate-pulse' : 'bg-rose-500'
                }`}
              />
              <span className="font-mono font-bold text-brand-primary text-xs sm:text-sm tracking-wider">
                {machine.machineId}
              </span>
              <span className="text-xs sm:text-sm font-semibold text-brand-text-muted hidden lg:inline">
                {machine.status === 'ACTIVE' ? '• Trực tuyến' : '• Tạm ngừng'}
              </span>
              <span className="sr-only">
                {machine.status === 'ACTIVE' ? 'Hoạt động' : 'Tạm ngừng'}
              </span>
            </div>
          )}

          <button
            id="open-cart-button"
            type="button"
            onClick={() => setIsCartOpen(true)}
            aria-label="Xem giỏ hàng"
            className="relative flex items-center gap-2.5 px-4 sm:px-5 py-2 sm:py-2.5 rounded-2xl bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white shadow-sm hover:shadow-floating transition-all duration-150 min-h-[44px] sm:min-h-[48px] border border-emerald-400/25 cursor-pointer"
          >
            <div className="relative">
              <ShoppingBag className="w-4.5 h-4.5 sm:w-5 sm:h-5 text-brand-accent" />
              {totalItems > 0 && (
                <span
                  id="cart-badge-count"
                  className="absolute -top-2 -right-2.5 w-5 h-5 bg-brand-accent text-brand-primary text-xs font-black rounded-full flex items-center justify-center shadow-xs animate-bounce"
                >
                  {totalItems}
                </span>
              )}
            </div>
            <div className="hidden sm:flex flex-col items-start leading-tight">
              <span className="text-[10px] sm:text-[11px] uppercase tracking-wider font-extrabold text-white/80">Giỏ hàng</span>
              <span className="text-xs sm:text-sm font-black text-brand-accent">
                {totalItems > 0 ? formatVnd(totalAmountVnd) : '0 ₫'}
              </span>
            </div>
          </button>
        </div>
      </div>

      {/* Mobile Sub-Navigation Bar for Small Screens */}
      <div className="md:hidden flex items-center justify-around px-3 py-2 border-t border-emerald-500/10 bg-white/80 backdrop-blur-md">
        {navItems.map((item) => {
          const isActive = activeTab === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onTabChange?.(item.id)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold transition-all cursor-pointer ${
                isActive
                  ? 'bg-brand-primary text-white shadow-xs'
                  : 'text-brand-text/75 hover:text-brand-primary'
              }`}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          );
        })}
      </div>
    </header>
  );
};
