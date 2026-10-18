import React from 'react';
import { X, Plus, Minus, Trash2, ShoppingBag, ArrowRight } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { formatVnd } from '../utils/formatters';

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
        className="fixed inset-0 bg-black/40 backdrop-blur-xs transition-opacity"
        onClick={() => setIsCartOpen(false)}
      />

      <div className="fixed inset-y-0 right-0 max-w-full flex pl-10">
        <aside
          aria-label="Giỏ hàng khách hàng"
          className="w-screen max-w-md bg-white shadow-2xl flex flex-col justify-between"
        >
          {/* Header */}
          <div className="p-4 sm:p-5 border-b border-brand-border flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-brand-primary/10 text-brand-primary flex items-center justify-center">
                <ShoppingBag className="w-4 h-4" />
              </div>
              <h2 className="font-bold text-lg text-brand-text">Giỏ hàng của bạn</h2>
              <span className="text-xs px-2 py-0.5 rounded-full bg-brand-accent/40 font-bold text-brand-primary">
                {totalItems} món
              </span>
            </div>
            <button
              id="close-cart-btn"
              type="button"
              onClick={() => setIsCartOpen(false)}
              className="p-2 rounded-lg text-brand-text-muted hover:text-brand-text hover:bg-gray-100 transition-colors"
              aria-label="Đóng giỏ hàng"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Body: Items list */}
          <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-3">
            {items.length === 0 ? (
              <div className="h-full flex flex-col items-center justify-center text-center p-6 text-brand-text-muted">
                <div className="w-16 h-16 rounded-full bg-gray-100 flex items-center justify-center mb-3">
                  <ShoppingBag className="w-8 h-8 text-gray-400 stroke-1" />
                </div>
                <h3 className="font-semibold text-brand-text mb-1">Giỏ hàng đang trống</h3>
                <p className="text-xs max-w-xs leading-relaxed">
                  Quý khách vui lòng chọn các sản phẩm nước ngọt hoặc bánh snack từ danh sách máy bán hàng.
                </p>
              </div>
            ) : (
              items.map((item) => (
                <div
                  key={item.product.productId}
                  id={`cart-item-${item.product.productId}`}
                  className="p-3.5 rounded-xl border border-brand-border bg-brand-bg/50 flex items-center justify-between gap-3 transition-all"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-brand-primary/10 text-brand-primary">
                        Khay 0{item.product.slotNumber}
                      </span>
                      <span className="text-xs text-brand-text-muted">
                        Tồn: {item.product.stockQuantity}
                      </span>
                    </div>
                    <h4 className="font-semibold text-sm text-brand-text truncate">
                      {item.product.name}
                    </h4>
                    <p className="text-xs font-bold text-brand-primary mt-0.5">
                      {formatVnd(item.product.priceVnd)}
                    </p>
                  </div>

                  {/* Quantity controls */}
                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      type="button"
                      onClick={() => updateQuantity(item.product.productId, item.quantity - 1)}
                      className="w-8 h-8 rounded-lg bg-white border border-brand-border hover:bg-gray-50 active:scale-95 flex items-center justify-center text-brand-text shadow-2xs transition-all"
                      aria-label="Giảm số lượng"
                    >
                      <Minus className="w-3.5 h-3.5" />
                    </button>
                    <span className="w-7 text-center font-bold text-sm text-brand-text">
                      {item.quantity}
                    </span>
                    <button
                      type="button"
                      disabled={item.quantity >= item.product.stockQuantity}
                      onClick={() => updateQuantity(item.product.productId, item.quantity + 1)}
                      className={`w-8 h-8 rounded-lg border flex items-center justify-center transition-all ${
                        item.quantity >= item.product.stockQuantity
                          ? 'bg-gray-100 text-gray-400 border-gray-200 cursor-not-allowed'
                          : 'bg-white border-brand-border hover:bg-gray-50 active:scale-95 text-brand-text shadow-2xs'
                      }`}
                      aria-label="Tăng số lượng"
                    >
                      <Plus className="w-3.5 h-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => removeFromCart(item.product.productId)}
                      className="w-8 h-8 ml-1 rounded-lg hover:bg-rose-50 text-gray-400 hover:text-rose-600 flex items-center justify-center transition-colors"
                      aria-label="Xóa sản phẩm"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>

          {/* Footer: Summary & Checkout Button */}
          {items.length > 0 && (
            <div className="p-4 sm:p-5 border-t border-brand-border bg-white space-y-3">
              <div className="space-y-1.5 text-xs">
                <div className="flex justify-between text-brand-text-muted">
                  <span>Số lượng sản phẩm:</span>
                  <span className="font-semibold text-brand-text">{totalItems} món</span>
                </div>
                <div className="flex justify-between text-base font-bold text-brand-text pt-1 border-t border-dashed border-brand-border">
                  <span>Tổng tiền thanh toán:</span>
                  <span className="text-xl text-brand-primary tracking-tight font-extrabold">
                    {formatVnd(totalAmountVnd)}
                  </span>
                </div>
              </div>

              <div className="pt-2 flex gap-2">
                <button
                  type="button"
                  onClick={clearCart}
                  className="px-3 py-2.5 rounded-xl border border-gray-200 text-xs font-semibold text-brand-text-muted hover:text-brand-text hover:bg-gray-50 transition-colors"
                >
                  Xóa hết
                </button>
                <button
                  id="proceed-checkout-btn"
                  type="button"
                  disabled={!machineActive || items.length === 0}
                  onClick={onProceedToCheckout}
                  className={`flex-1 flex items-center justify-center gap-2 py-3 rounded-xl font-bold text-sm shadow-md transition-all duration-150 ${
                    machineActive && items.length > 0
                      ? 'bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white hover:shadow-lg'
                      : 'bg-gray-200 text-gray-500 cursor-not-allowed'
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
