import React from 'react';
import { X, Plus, Minus, Trash2, ShoppingBag, ArrowRight } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { formatVnd } from '../utils/formatters';
import { getAvailableStock } from '../types/product';

interface CartDrawerProps {
  onProceedToCheckout: () => void;
  machineActive?: boolean;
}

export const CartDrawer: React.FC<CartDrawerProps> = ({ onProceedToCheckout, machineActive = true }) => {
  const { items, isCartOpen, setIsCartOpen, updateQuantity, removeFromCart, clearCart, totalItems, totalAmountVnd } =
    useCart();

  if (!isCartOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-hidden">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/45 backdrop-blur-xs transition-opacity"
        onClick={() => setIsCartOpen(false)}
      />

      <div className="fixed inset-y-0 right-0 max-w-full flex pl-10">
        <aside
          aria-label="Giỏ hàng khách hàng"
          className="w-screen max-w-md glass-drawer shadow-2xl flex flex-col justify-between"
        >
          {/* Header */}
          <div className="p-4 sm:p-5 border-b border-emerald-500/15 flex items-center justify-between bg-white/40 backdrop-blur-md">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-xl bg-brand-primary/10 text-brand-primary border border-emerald-500/20 flex items-center justify-center">
                <ShoppingBag className="w-4 h-4" />
              </div>
              <h2 className="font-bold text-lg text-brand-text">Giỏ hàng của bạn</h2>
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-brand-accent/30 font-bold text-brand-primary border border-brand-accent/50 shadow-2xs">
                {totalItems} món
              </span>
            </div>
            <button
              id="close-cart-btn"
              type="button"
              onClick={() => setIsCartOpen(false)}
              className="p-2 rounded-xl text-brand-text-muted hover:text-brand-text hover:bg-white/60 transition-colors"
              aria-label="Đóng giỏ hàng"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Body: Items list */}
          <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-3">
            {items.length === 0 ? (
              <div className="h-full flex flex-col items-center justify-center text-center p-6 text-brand-text-muted">
                <div className="w-16 h-16 rounded-2xl glass-surface flex items-center justify-center mb-3">
                  <ShoppingBag className="w-8 h-8 text-brand-primary/40 stroke-1" />
                </div>
                <h3 className="font-semibold text-brand-text mb-1">Giỏ hàng đang trống</h3>
                <p className="text-xs max-w-xs leading-relaxed">
                  Quý khách vui lòng chọn các sản phẩm nước ngọt hoặc bánh snack từ danh sách máy bán hàng.
                </p>
              </div>
            ) : (
              items.map((item) => {
                const maxAvailable = getAvailableStock(item.product);
                const isMaxReached = item.quantity >= maxAvailable;

                return (
                  <div
                    key={item.product.productId}
                    id={`cart-item-${item.product.productId}`}
                    className="p-3.5 rounded-2xl glass-surface flex items-center justify-between gap-3 transition-all hover:border-emerald-500/35"
                  >
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="px-2 py-0.5 rounded-lg text-[10px] font-bold bg-brand-primary/10 text-brand-primary border border-brand-primary/15">
                          Khay 0{item.product.slotNumber}
                        </span>
                        <span className="text-xs text-brand-text-muted font-medium">
                          Tồn: {maxAvailable}
                        </span>
                      </div>
                      <h4 className="font-bold text-sm text-brand-text truncate">
                        {item.product.name}
                      </h4>
                      <p className="text-xs font-black text-brand-primary mt-0.5">
                        {formatVnd(item.product.priceVnd)}
                      </p>
                    </div>

                    {/* Quantity controls */}
                    <div className="flex items-center gap-1.5 shrink-0">
                      <button
                        type="button"
                        onClick={() => updateQuantity(item.product.productId, item.quantity - 1)}
                        className="w-8 h-8 rounded-xl bg-white/80 border border-brand-border hover:bg-white active:scale-95 flex items-center justify-center text-brand-text shadow-2xs transition-all min-h-[32px]"
                        aria-label="Giảm số lượng"
                      >
                        <Minus className="w-3.5 h-3.5" />
                      </button>
                      <span className="w-7 text-center font-extrabold text-sm text-brand-text">
                        {item.quantity}
                      </span>
                      <button
                        type="button"
                        disabled={isMaxReached}
                        onClick={() => updateQuantity(item.product.productId, item.quantity + 1)}
                        className={`w-8 h-8 rounded-xl border flex items-center justify-center transition-all min-h-[32px] ${
                          isMaxReached
                            ? 'bg-gray-100/60 text-gray-400 border-gray-200/50 cursor-not-allowed'
                            : 'bg-white/80 border-brand-border hover:bg-white active:scale-95 text-brand-text shadow-2xs'
                        }`}
                        aria-label="Tăng số lượng"
                      >
                        <Plus className="w-3.5 h-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => removeFromCart(item.product.productId)}
                        className="w-8 h-8 ml-1 rounded-xl hover:bg-rose-50 text-gray-400 hover:text-rose-600 flex items-center justify-center transition-colors min-h-[32px]"
                        aria-label="Xóa sản phẩm"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>

          {/* Footer: Summary & Checkout Button */}
          {items.length > 0 && (
            <div className="p-4 sm:p-5 border-t border-emerald-500/15 bg-white/60 backdrop-blur-md space-y-3">
              <div className="space-y-1.5 text-xs">
                <div className="flex justify-between text-brand-text-muted">
                  <span>Số lượng sản phẩm:</span>
                  <span className="font-semibold text-brand-text">{totalItems} món</span>
                </div>
                <div className="flex justify-between text-base font-bold text-brand-text pt-1.5 border-t border-dashed border-emerald-500/20">
                  <span>Tổng tiền thanh toán:</span>
                  <span className="text-xl text-brand-primary tracking-tight font-black">
                    {formatVnd(totalAmountVnd)}
                  </span>
                </div>
              </div>

              <div className="pt-2 flex gap-2">
                <button
                  type="button"
                  onClick={clearCart}
                  className="px-3.5 py-3 rounded-2xl border border-gray-300/60 bg-white/60 hover:bg-white text-xs font-semibold text-brand-text-muted hover:text-brand-text transition-colors min-h-[44px]"
                >
                  Xóa hết
                </button>
                <button
                  id="proceed-checkout-btn"
                  type="button"
                  disabled={!machineActive || items.length === 0}
                  onClick={onProceedToCheckout}
                  className={`flex-1 flex items-center justify-center gap-2 py-3 px-4 rounded-2xl font-bold text-sm shadow-md transition-all duration-150 min-h-[44px] ${
                    machineActive && items.length > 0
                      ? 'bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white shadow-md hover:shadow-floating border border-emerald-400/25'
                      : 'bg-gray-200/60 text-gray-400 cursor-not-allowed border border-gray-300/40'
                  }`}
                >
                  <span>Đặt hàng & Thanh toán</span>
                  <ArrowRight className="w-4 h-4 text-brand-accent" />
                </button>
              </div>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
};
