import React, { useState } from 'react';
import { Plus, Check, AlertCircle, Package } from 'lucide-react';
import type { Product } from '../types/product';
import { formatVnd } from '../utils/formatters';
import { StockBadge } from './StatusBadge';
import { useCart } from '../context/CartContext';

interface ProductCardProps {
  product: Product;
  machineActive?: boolean;
}

export const ProductCard: React.FC<ProductCardProps> = ({ product, machineActive = true }) => {
  const { addToCart } = useCart();
  const [imageError, setImageError] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const isBuyable = machineActive && product.available && product.stockQuantity > 0;

  const handleAddToCart = () => {
    if (!isBuyable) return;

    const result = addToCart(product, 1);
    if (result.success) {
      setFeedback({ type: 'success', message: 'Đã thêm vào giỏ!' });
      setTimeout(() => setFeedback(null), 1500);
    } else {
      setFeedback({ type: 'error', message: result.message || 'Không thể thêm sản phẩm' });
      setTimeout(() => setFeedback(null), 2500);
    }
  };

  // Modern placeholder colors per slot
  const slotColors = [
    'from-emerald-700/20 to-teal-800/10 text-emerald-800',
    'from-amber-600/20 to-orange-700/10 text-amber-800',
    'from-blue-600/20 to-indigo-700/10 text-blue-800',
  ];
  const colorClass = slotColors[(product.slotNumber - 1) % slotColors.length] || slotColors[0];

  return (
    <div
      id={`product-card-${product.slotNumber}`}
      className={`group relative bg-white rounded-2xl border transition-all duration-200 overflow-hidden flex flex-col justify-between ${
        isBuyable
          ? 'border-brand-border hover:border-brand-primary/40 hover:shadow-card-hover'
          : 'border-gray-200 opacity-80 bg-gray-50/50'
      }`}
    >
      {/* Top badges: Slot and Stock */}
      <div className="p-4 pb-0 flex items-center justify-between gap-2">
        <span className="inline-flex items-center px-2.5 py-1 rounded-lg text-xs font-bold tracking-wide uppercase bg-brand-primary/10 text-brand-primary">
          Khay 0{product.slotNumber}
        </span>
        <StockBadge stockQuantity={product.stockQuantity} available={product.available} />
      </div>

      {/* Product Image / Illustration */}
      <div className="px-4 py-3 flex items-center justify-center">
        <div
          className={`w-full h-36 rounded-xl flex items-center justify-center bg-gradient-to-br ${colorClass} relative overflow-hidden`}
        >
          {product.imageUrl && !imageError ? (
            <img
              src={product.imageUrl}
              alt={product.name}
              onError={() => setImageError(true)}
              className="w-full h-full object-contain p-2 transition-transform duration-300 group-hover:scale-105"
              loading="lazy"
            />
          ) : (
            <div className="flex flex-col items-center justify-center gap-1.5 p-3 text-center">
              <Package className="w-12 h-12 text-brand-primary/40 stroke-1" />
              <span className="text-[11px] font-medium text-brand-text-muted">Hình minh họa mẫu</span>
            </div>
          )}
        </div>
      </div>

      {/* Details */}
      <div className="px-4 pb-4 flex-1 flex flex-col justify-between gap-3">
        <div>
          <h2 className="font-bold text-base sm:text-lg text-brand-text group-hover:text-brand-primary transition-colors line-clamp-1">
            {product.name}
          </h2>
          {product.description ? (
            <p className="text-xs text-brand-text-muted mt-0.5 line-clamp-2 leading-relaxed">
              {product.description}
            </p>
          ) : (
            <p className="text-xs text-brand-text-subtle italic mt-0.5">Sản phẩm chất lượng cao</p>
          )}
        </div>

        {/* Price & Action */}
        <div className="pt-2 border-t border-brand-border/60 flex items-center justify-between gap-2">
          <div>
            <span className="text-[11px] text-brand-text-muted block">Đơn giá</span>
            <span className="text-lg sm:text-xl font-extrabold text-brand-primary tracking-tight">
              {formatVnd(product.priceVnd)}
            </span>
          </div>

          <button
            id={`add-to-cart-btn-${product.slotNumber}`}
            type="button"
            disabled={!isBuyable}
            onClick={handleAddToCart}
            className={`flex items-center justify-center gap-1.5 px-3.5 py-2.5 rounded-xl font-semibold text-xs sm:text-sm min-h-[42px] transition-all duration-150 ${
              isBuyable
                ? 'bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white shadow-sm hover:shadow'
                : 'bg-gray-200 text-gray-500 cursor-not-allowed'
            }`}
          >
            {feedback ? (
              feedback.type === 'success' ? (
                <>
                  <Check className="w-4 h-4 text-brand-accent" />
                  <span>Đã thêm</span>
                </>
              ) : (
                <>
                  <AlertCircle className="w-4 h-4 text-rose-300" />
                  <span>Đầy kho</span>
                </>
              )
            ) : isBuyable ? (
              <>
                <Plus className="w-4 h-4 text-brand-accent" />
                <span>Chọn mua</span>
              </>
            ) : (
              <span>Hết hàng</span>
            )}
          </button>
        </div>

        {/* Dynamic feedback toast within card */}
        {feedback && (
          <div
            className={`text-[11px] font-medium px-2 py-1 rounded-md text-center transition-all animate-fade-in ${
              feedback.type === 'success' ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'
            }`}
          >
            {feedback.message}
          </div>
        )}
      </div>
    </div>
  );
};
