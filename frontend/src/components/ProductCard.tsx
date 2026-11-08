import React, { useState } from 'react';
import { Plus, Check, AlertCircle, Package } from 'lucide-react';
import type { Product } from '../types/product';
import { formatVnd } from '../utils/formatters';
import { resolveProductImage } from '../utils/productImages';
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
  const imageSrc = resolveProductImage(product);

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

  // Modern botanical placeholder colors per slot
  const slotColors = [
    'from-emerald-600/15 via-teal-600/10 to-transparent text-emerald-900',
    'from-amber-600/15 via-orange-600/10 to-transparent text-amber-900',
    'from-teal-700/15 via-emerald-700/10 to-transparent text-teal-900',
  ];
  const colorClass = slotColors[(product.slotNumber - 1) % slotColors.length] || slotColors[0];

  return (
    <div
      id={`product-card-${product.slotNumber}`}
      className={`group relative glass-card rounded-3xl transition-all duration-300 overflow-hidden flex flex-col justify-between ${
        isBuyable
          ? 'glass-card-hover'
          : 'opacity-70 bg-white/40 backdrop-blur-md'
      }`}
    >
      {/* Top badges: Slot and Stock */}
      <div className="p-4 pb-0 flex items-center justify-between gap-2">
        <span className="inline-flex items-center px-3 py-1 rounded-xl text-xs font-extrabold tracking-wider uppercase bg-brand-primary/10 text-brand-primary border border-emerald-500/20 backdrop-blur-xs">
          Khay 0{product.slotNumber}
        </span>
        <StockBadge stockQuantity={product.stockQuantity} available={product.available} />
      </div>

      {/* Product Image / Illustration */}
      <div className="px-4 py-3 flex items-center justify-center">
        <div
          className={`w-full h-40 rounded-2xl flex items-center justify-center bg-gradient-to-br ${colorClass} border border-emerald-500/15 relative overflow-hidden backdrop-blur-xs shadow-inner`}
        >
          {/* Subtle top inner reflection */}
          <div aria-hidden="true" className="absolute inset-x-0 top-0 h-1/2 bg-gradient-to-b from-white/20 to-transparent pointer-events-none" />
          {imageSrc && !imageError ? (
            <img
              src={imageSrc}
              alt={product.name}
              onError={() => setImageError(true)}
              className="w-full h-full object-contain p-2.5 transition-transform duration-300 group-hover:scale-105 drop-shadow-sm relative z-10"
              loading="lazy"
            />
          ) : (
            <div className="flex flex-col items-center justify-center gap-1.5 p-3 text-center relative z-10">
              <Package className="w-12 h-12 text-brand-primary/40 stroke-1" />
              <span className="text-[11px] font-semibold text-brand-text-muted">Hình minh họa mẫu</span>
            </div>
          )}
        </div>
      </div>

      {/* Details */}
      <div className="px-4 pb-4 flex-1 flex flex-col justify-between gap-3.5">
        <div>
          <h2 className="font-extrabold text-base sm:text-lg text-brand-text group-hover:text-brand-primary transition-colors line-clamp-1">
            {product.name}
          </h2>
          {product.description ? (
            <p className="text-xs text-brand-text-muted mt-1 line-clamp-2 leading-relaxed">
              {product.description}
            </p>
          ) : (
            <p className="text-xs text-brand-text-subtle italic mt-1">Sản phẩm chất lượng cao</p>
          )}
        </div>

        {/* Price & Action */}
        <div className="pt-3 border-t border-emerald-500/15 flex items-center justify-between gap-2">
          <div>
            <span className="text-[10px] uppercase font-bold tracking-wider text-brand-text-muted block">Đơn giá</span>
            <span className="text-xl sm:text-2xl font-black text-brand-primary tracking-tight font-sans">
              {formatVnd(product.priceVnd)}
            </span>
          </div>

          <button
            id={`add-to-cart-btn-${product.slotNumber}`}
            type="button"
            disabled={!isBuyable}
            onClick={handleAddToCart}
            className={`flex items-center justify-center gap-1.5 px-4 py-2.5 rounded-2xl font-bold text-xs sm:text-sm min-h-[44px] min-w-[108px] transition-all duration-150 ${
              isBuyable
                ? 'bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white shadow-md hover:shadow-floating border border-emerald-400/25 cursor-pointer'
                : 'bg-gray-200/60 text-gray-400 cursor-not-allowed border border-gray-300/40 backdrop-blur-xs'
            }`}
          >
            {feedback ? (
              feedback.type === 'success' ? (
                <>
                  <Check className="w-4 h-4 text-brand-accent stroke-[2.5]" />
                  <span>Đã thêm</span>
                </>
              ) : (
                <>
                  <AlertCircle className="w-4 h-4 text-rose-300 stroke-[2.5]" />
                  <span>Đầy kho</span>
                </>
              )
            ) : isBuyable ? (
              <>
                <Plus className="w-4 h-4 text-brand-accent stroke-[2.5]" />
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
            className={`text-[11px] font-bold px-3 py-1.5 rounded-xl text-center transition-all animate-fade-in ${
              feedback.type === 'success' ? 'bg-emerald-100/90 text-emerald-900 border border-emerald-300/60' : 'bg-rose-100/90 text-rose-900 border border-rose-300/60'
            }`}
          >
            {feedback.message}
          </div>
        )}
      </div>
    </div>
  );
};
