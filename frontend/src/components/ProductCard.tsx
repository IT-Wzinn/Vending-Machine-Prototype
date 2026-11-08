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

  return (
    <article
      id={`product-card-${product.slotNumber}`}
      aria-label={`${product.name} - Khoang 0${product.slotNumber}`}
      className={`group relative glass-card rounded-3xl transition-all duration-300 overflow-hidden flex flex-col justify-between ${
        isBuyable
          ? 'glass-card-hover'
          : 'opacity-75 bg-white/45 backdrop-blur-md grayscale-[20%]'
      }`}
    >
      {/* 1. Physical Compartment Identity (Slot Tag + Inventory Availability) */}
      <div className="p-3.5 sm:p-4 pb-0 flex items-center justify-between gap-2">
        <div className="inline-flex items-center gap-1.5 px-2.5 sm:px-3 py-1 rounded-xl text-xs font-extrabold tracking-wider bg-brand-primary/10 text-brand-primary border border-emerald-500/20 backdrop-blur-xs shadow-2xs">
          {/* Stainless-Steel Vending Coil Motif Icon */}
          <svg
            className="w-3.5 h-3.5 text-brand-primary/75 shrink-0"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M4 12c0-3.3 2.7-6 6-6s6 2.7 6 6-2.7 6-6 6" />
            <path d="M9 12c0-2.2 1.8-4 4-4s4 1.8 4 4-1.8 4-4 4" />
            <path d="M14 9.5c1.2.7 2 2 2 3.5 0 2.2-1.8 4-4 4" />
          </svg>
          <span className="font-mono text-[11px] font-black tracking-wider uppercase">
            KHOANG 0{product.slotNumber}
          </span>
          <span className="text-brand-primary/30 text-[10px]" aria-hidden="true">•</span>
          <span className="text-[11px] font-extrabold text-brand-primary">
            Khay 0{product.slotNumber}
          </span>
        </div>

        {/* 5. Inventory Availability State */}
        <StockBadge stockQuantity={product.stockQuantity} available={product.available} />
      </div>

      {/* 2. Tactile Bay Product Chamber with Helical Coil Silhouette */}
      <div className="px-3.5 sm:px-4 py-2.5 sm:py-3 flex items-center justify-center">
        <div
          className="w-full h-40 sm:h-44 rounded-2xl flex items-center justify-center glass-chamber relative overflow-hidden group-hover:border-emerald-500/30 transition-colors"
        >
          {/* Concentric Helical Dispense Coil Silhouette (Atmospheric Hardware Motif) */}
          <svg
            className="absolute inset-0 w-full h-full pointer-events-none opacity-20 text-emerald-950/35 select-none transition-transform duration-500 group-hover:scale-105"
            viewBox="0 0 200 160"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            aria-hidden="true"
          >
            <ellipse cx="100" cy="80" rx="66" ry="34" strokeDasharray="6 4" />
            <ellipse cx="100" cy="80" rx="46" ry="24" />
            <ellipse cx="100" cy="80" rx="26" ry="14" />
            <path d="M34 80 Q100 24 166 80 Q100 136 34 80" strokeWidth="1.2" strokeOpacity="0.4" />
          </svg>

          {/* Subtle specular top highlight overlay */}
          <div aria-hidden="true" className="absolute inset-x-0 top-0 h-1/2 bg-gradient-to-b from-white/30 to-transparent pointer-events-none" />

          {/* Product Image / Graceful Fallback */}
          {imageSrc && !imageError ? (
            <img
              src={imageSrc}
              alt={product.name}
              onError={() => setImageError(true)}
              className="w-full h-full object-contain p-2.5 sm:p-3 transition-transform duration-300 group-hover:scale-105 filter drop-shadow-[0_8px_14px_rgba(18,61,50,0.14)] relative z-10"
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

      {/* 3, 4, 6. Product Details, Price, and Add-to-Cart Action Tray */}
      <div className="px-3.5 sm:px-4 pb-3.5 sm:pb-4 flex-1 flex flex-col justify-between gap-3">
        <div>
          <h2 className="font-extrabold text-base sm:text-lg text-brand-text group-hover:text-brand-primary transition-colors line-clamp-1 tracking-tight">
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

        {/* 4. Prominent Price & 6. Add-to-Cart Action */}
        <div className="pt-2.5 sm:pt-3 border-t border-emerald-500/15 flex items-center justify-between gap-2">
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
            className={`flex items-center justify-center gap-1.5 px-3.5 sm:px-4 py-2 sm:py-2.5 rounded-2xl font-bold text-xs sm:text-sm min-h-[44px] min-w-[108px] transition-all duration-150 ${
              isBuyable
                ? 'bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white shadow-md hover:shadow-floating border border-emerald-400/25 cursor-pointer focus-visible:outline-2 focus-visible:outline-emerald-500'
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
    </article>
  );
};
