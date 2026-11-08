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

  // Popularity badge based on slot
  const getSlotBadge = (slot: number) => {
    if (slot === 1) return { label: '🔥 Phổ biến', color: 'bg-emerald-100 text-emerald-800 border-emerald-300' };
    if (slot === 2) return { label: '⭐ Bán chạy', color: 'bg-amber-100 text-amber-900 border-amber-300' };
    if (slot === 3) return { label: '🌱 Mới', color: 'bg-teal-100 text-teal-800 border-teal-300' };
    return { label: '✨ Nổi bật', color: 'bg-slate-100 text-slate-800 border-slate-300' };
  };

  const slotBadge = getSlotBadge(product.slotNumber);

  return (
    <article
      id={`product-card-${product.slotNumber}`}
      aria-label={`${product.name} - Khoang 0${product.slotNumber}`}
      className={`group relative rounded-2xl transition-all duration-200 overflow-hidden border hover:shadow-lg ${
        isBuyable
          ? 'bg-white border-gray-200/80 shadow-sm'
          : 'bg-gray-50 border-gray-200/60 opacity-75 grayscale-[15%]'
      }`}
    >
      {/* Horizontal layout: Image left + Info right — Enlarged scale */}
      <div className="flex p-5 sm:p-6 gap-4 sm:gap-5 items-center">
        {/* Product Image — clean square frame */}
        <div className="relative w-28 h-28 sm:w-32 sm:h-32 shrink-0 rounded-2xl bg-gradient-to-b from-gray-50 to-white flex items-center justify-center overflow-hidden border border-gray-100 p-2">
          {imageSrc && !imageError ? (
            <img
              src={imageSrc}
              alt={product.name}
              onError={() => setImageError(true)}
              className="w-full h-full object-contain transition-transform duration-300 group-hover:scale-110 drop-shadow-[0_6px_14px_rgba(0,0,0,0.1)]"
              loading="lazy"
            />
          ) : (
            <div className="flex flex-col items-center justify-center gap-1.5 p-2 text-center">
              <Package className="w-12 h-12 text-brand-primary/30 stroke-1" />
              <span className="text-[11px] font-semibold text-brand-text-muted">Hình minh họa mẫu</span>
            </div>
          )}
        </div>

        {/* Product Info — right side */}
        <div className="flex-1 min-w-0 flex flex-col justify-between self-stretch">
          <div>
            {/* Top row: Name & Slot Popularity Badge */}
            <div className="flex items-start justify-between gap-2.5">
              <h3 className="font-extrabold text-base sm:text-lg text-gray-900 group-hover:text-brand-primary transition-colors line-clamp-1 leading-snug">
                {product.name}
              </h3>
              <span
                className={`shrink-0 text-xs font-extrabold px-2.5 py-0.5 rounded-full border shadow-2xs whitespace-nowrap ${slotBadge.color}`}
              >
                {slotBadge.label}
              </span>
            </div>

            {/* Description */}
            {product.description ? (
              <p className="text-xs sm:text-sm text-gray-500 mt-1 line-clamp-1 leading-relaxed">
                {product.description}
              </p>
            ) : (
              <p className="text-xs sm:text-sm text-gray-400 italic mt-1">Sản phẩm chất lượng cao</p>
            )}

            {/* Slot label + Stock status */}
            <div className="flex items-center gap-2 mt-2 flex-wrap">
              <div className="inline-flex items-center gap-1 text-xs font-mono font-bold text-gray-400">
                <span>KHOANG 0{product.slotNumber}</span>
                <span>•</span>
                <span>Khay 0{product.slotNumber}</span>
              </div>
              <StockBadge stockQuantity={product.stockQuantity} available={product.available} />
            </div>
          </div>

          {/* Bottom row: Price + Button */}
          <div className="flex items-center justify-between gap-2.5 mt-3.5 pt-2.5 border-t border-gray-100">
            <span className="text-xl sm:text-2xl font-black text-brand-primary tracking-tight whitespace-nowrap">
              {formatVnd(product.priceVnd)}
            </span>

            <button
              id={`add-to-cart-btn-${product.slotNumber}`}
              type="button"
              disabled={!isBuyable}
              onClick={handleAddToCart}
              aria-label={isBuyable ? `Chọn mua ${product.name}` : `Hết hàng ${product.name}`}
              className={`flex items-center justify-center gap-1.5 px-4 sm:px-5 py-2.5 rounded-full font-bold text-xs sm:text-sm min-h-[44px] whitespace-nowrap shrink-0 transition-all duration-150 cursor-pointer ${
                isBuyable
                  ? 'bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white shadow-sm border border-emerald-400/25'
                  : 'bg-gray-100 text-gray-400 cursor-not-allowed border border-gray-200'
              }`}
            >
              {feedback ? (
                feedback.type === 'success' ? (
                  <>
                    <Check className="w-3.5 h-3.5 stroke-[2.5]" />
                    <span>Đã thêm</span>
                  </>
                ) : (
                  <>
                    <AlertCircle className="w-3.5 h-3.5 stroke-[2.5]" />
                    <span>Đầy kho</span>
                  </>
                )
              ) : isBuyable ? (
                <>
                  <Plus className="w-3.5 h-3.5 stroke-[2.5]" />
                  <span>Chọn mua</span>
                </>
              ) : (
                <span>Hết hàng</span>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Toast Feedback */}
      {feedback && (
        <div
          className={`mx-4 mb-3 text-xs font-bold px-3 py-1.5 rounded-lg text-center animate-fade-in ${
            feedback.type === 'success'
              ? 'bg-emerald-50 text-emerald-800 border border-emerald-200'
              : 'bg-rose-50 text-rose-800 border border-rose-200'
          }`}
        >
          {feedback.message}
        </div>
      )}
    </article>
  );
};
