import React from 'react';
import { ChevronRight, Sparkles } from 'lucide-react';

interface HeroBannerProps {
  onStartOrder?: () => void;
  machineImageSrc?: string;
}

export const HeroBanner: React.FC<HeroBannerProps> = ({
  onStartOrder,
  machineImageSrc = '/images/freshvend-machine-3d.png',
}) => {
  const handleScrollToCatalog = () => {
    if (onStartOrder) {
      onStartOrder();
    } else {
      const catalogEl = document.getElementById('catalog-container');
      if (catalogEl) {
        catalogEl.scrollIntoView({ behavior: 'smooth' });
      }
    }
  };

  return (
    <section
      aria-label="FreshVend Smart Vending Hero"
      className="relative w-full pt-2 sm:pt-4 lg:pt-6 pb-20 sm:pb-28 lg:pb-36 select-none"
    >
      <div className="grid grid-cols-1 md:grid-cols-12 gap-8 lg:gap-10 items-center">
        {/* Left column: Text & CTA Button directly on top of 3D Sylva Background — pulled up higher */}
        <div className="md:col-span-6 lg:col-span-6 xl:col-span-6 flex flex-col items-start gap-4 sm:gap-4.5 lg:gap-5 z-20 -mt-10 sm:-mt-14 lg:-mt-20">
          {/* Top tag: VENDING MACHINE • SMART ORDER */}
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-black/40 backdrop-blur-md border border-white/20 text-xs sm:text-sm font-extrabold tracking-wider uppercase shadow-lg">
            <Sparkles className="w-4 h-4 text-brand-accent animate-pulse" />
            <span className="font-mono text-emerald-400">VENDING MACHINE</span>
            <span className="text-white/40">•</span>
            <span className="text-brand-accent">SMART ORDER</span>
          </div>

          {/* Headline: Đặt hàng nhanh chóng / Chạm là có ngay! */}
          <h1 className="text-3xl sm:text-4xl lg:text-5xl xl:text-[52px] font-black text-white leading-[1.2] sm:leading-[1.22] tracking-tight drop-shadow-[0_4px_18px_rgba(0,0,0,0.9)]">
            Đặt hàng nhanh chóng
            <span className="block mt-1 sm:mt-1.5">
              <span className="text-[#4ade80] inline-block drop-shadow-[0_0_15px_rgba(74,222,128,0.95)] drop-shadow-[0_0_30px_rgba(34,197,94,0.7)]">
                Chạm
              </span>{' '}
              là có ngay!
            </span>
          </h1>

          {/* Subtitle */}
          <p className="text-sm sm:text-base lg:text-lg text-white/95 font-medium max-w-md leading-normal sm:leading-relaxed drop-shadow-[0_2px_10px_rgba(0,0,0,0.85)]">
            Trải nghiệm mua sắm tiện lợi, hiện đại
            <br />
            cùng FreshVend
          </p>

          {/* CTA Button: Bắt đầu đặt hàng > */}
          <button
            type="button"
            onClick={handleScrollToCatalog}
            aria-label="Bắt đầu đặt hàng"
            className="mt-1 sm:mt-1.5 inline-flex items-center gap-2.5 px-7 py-3 sm:px-8 sm:py-3.5 rounded-full bg-gradient-to-r from-[#22C55E] to-[#16a34a] hover:from-[#16a34a] hover:to-[#15803d] active:scale-95 text-white font-extrabold text-sm sm:text-base shadow-[0_8px_24px_rgba(34,197,94,0.45)] hover:shadow-[0_10px_28px_rgba(34,197,94,0.6)] transition-all duration-200 cursor-pointer border border-white/30 group"
          >
            <span>🛒 Bắt đầu đặt hàng</span>
            <ChevronRight className="w-5 h-5 transition-transform duration-200 group-hover:translate-x-1" />
          </button>
        </div>

        {/* Right column: 3D Vending Machine slightly reduced in size per user request */}
        <div className="md:col-span-6 lg:col-span-6 xl:col-span-6 flex items-center justify-center md:justify-end lg:justify-center relative">
          <div className="relative group w-full max-w-[450px] sm:max-w-[520px] lg:max-w-[580px] xl:max-w-[620px] flex items-center justify-center">
            {/* Ambient neon green energy glow behind machine */}
            <div
              aria-hidden="true"
              className="absolute inset-0 m-auto w-64 h-64 sm:w-80 sm:h-80 lg:w-[400px] lg:h-[400px] rounded-full border border-[#D8F36A]/40 bg-[#D8F36A]/15 blur-3xl animate-pulse pointer-events-none"
            />
            {/* High-res transparent 3D Machine Image provided by user */}
            <img
              src={machineImageSrc}
              alt="FreshVend Smart Vending Machine 3D"
              className="relative z-10 w-full h-auto object-contain filter drop-shadow-[0_20px_40px_rgba(0,0,0,0.65)] scale-100 sm:scale-[1.03] lg:scale-[1.05] transition-transform duration-500 group-hover:scale-[1.08]"
            />
          </div>
        </div>
      </div>
    </section>
  );
};
