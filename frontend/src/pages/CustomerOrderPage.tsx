import React, { useEffect, useState, useCallback } from 'react';
import { Navbar, type NavTab } from '../components/Navbar';
import { HeroBanner } from '../components/HeroBanner';
import { ProductCard } from '../components/ProductCard';
import { SupportSection } from '../components/SupportSection';
import { AboutSection } from '../components/AboutSection';
import { OrdersSection } from '../components/OrdersSection';
import { CatalogSkeleton } from '../components/LoadingSkeleton';
import { ErrorMessage } from '../components/ErrorMessage';
import { CartDrawer } from '../components/CartDrawer';
import { CheckoutModal } from '../components/CheckoutModal';
import { InvalidMachinePage } from './InvalidMachinePage';
import type { MachineResponse } from '../types/machine';
import type { Product } from '../types/product';
import { ApiError } from '../types/api';
import { getMachine, getProductCatalog } from '../services/api';
import { useCart } from '../context/CartContext';
import { RefreshCw, AlertCircle, Sparkles, ChevronRight } from 'lucide-react';
import { SylvaBackground } from '../components/SylvaBackground';
import { MobileActionDock } from '../components/MobileActionDock';

interface CustomerOrderPageProps {
  initialMachineId?: string;
}

export const CustomerOrderPage: React.FC<CustomerOrderPageProps> = ({ initialMachineId }) => {
  const { setIsCartOpen } = useCart();

  // Navigation tab state
  const [activeTab, setActiveTab] = useState<NavTab>('home');

  // Category filter state
  const [selectedCategory, setSelectedCategory] = useState<string>('all');

  // Helper to extract machineId from URL path (/machine/:id) or query (?machineId=)
  const extractMachineId = (): string => {
    if (initialMachineId) return initialMachineId;

    if (typeof window !== 'undefined') {
      // 1. Path match: /machine/VM001
      const pathMatch = window.location.pathname.match(/\/machine\/([a-zA-Z0-9_-]+)/);
      if (pathMatch && pathMatch[1]) {
        return pathMatch[1].toUpperCase();
      }

      // 2. Query param: ?machineId=VM001 or ?m=VM001
      const params = new URLSearchParams(window.location.search);
      const queryMachine = params.get('machineId') || params.get('m');
      if (queryMachine) {
        return queryMachine.toUpperCase();
      }
    }

    // Default test machine per instructions and seed data
    return 'VM001';
  };

  const [machineId, setMachineId] = useState<string>(extractMachineId);
  const [machine, setMachine] = useState<MachineResponse | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<{ code: string; message: string } | null>(null);
  const [isCheckoutOpen, setIsCheckoutOpen] = useState<boolean>(false);

  // Sync URL when machine changes
  const handleSelectMachine = (newId: string) => {
    setMachineId(newId);
    if (typeof window !== 'undefined' && window.history) {
      window.history.pushState({}, '', `/machine/${newId}`);
    }
  };

  const loadMachineData = useCallback(async () => {
    if (!machineId) {
      setError({
        code: 'MACHINE_NOT_FOUND',
        message: 'Thiếu mã định danh máy bán hàng. Vui lòng quét mã QR trên thân máy.',
      });
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // 1. Verify machine
      const machineData = await getMachine(machineId);
      setMachine(machineData);

      // 2. Fetch product catalog
      const catalogData = await getProductCatalog(machineId);
      setProducts(catalogData.products || []);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError({
          code: err.code,
          message: err.message,
        });
      } else {
        setError({
          code: 'CLIENT_ERROR',
          message: 'Không thể kết nối đến hệ thống máy bán hàng.',
        });
      }
    } finally {
      setLoading(false);
    }
  }, [machineId]);

  useEffect(() => {
    loadMachineData();
  }, [loadMachineData]);

  const isMachineActive = machine?.status === 'ACTIVE';

  // Category filter helper
  const filteredProducts = products.filter((p) => {
    if (selectedCategory === 'all') return true;
    const name = p.name.toLowerCase();
    const desc = (p.description || '').toLowerCase();
    if (selectedCategory === 'drink') {
      return (
        name.includes('nước') ||
        name.includes('aqua') ||
        name.includes('trà') ||
        name.includes('cà phê') ||
        desc.includes('khoáng') ||
        desc.includes('uống') ||
        p.slotNumber === 1
      );
    }
    if (selectedCategory === 'snack') {
      return (
        name.includes('snack') ||
        name.includes("lay's") ||
        name.includes('bánh') ||
        desc.includes('khoai tây') ||
        p.slotNumber === 2
      );
    }
    if (selectedCategory === 'light') {
      return (
        name.includes('kẹo') ||
        name.includes('gum') ||
        name.includes('doublemint') ||
        p.slotNumber === 3
      );
    }
    return true;
  });

  // If machine does not exist or invalid code returned from backend
  if (!loading && error && (error.code === 'MACHINE_NOT_FOUND' || error.code === 'NOT_FOUND')) {
    return (
      <div className="min-h-screen bg-brand-bg/85 text-brand-text flex flex-col justify-between relative">
        <SylvaBackground />
        <Navbar machine={null} activeTab={activeTab} onTabChange={setActiveTab} />
        <main className="flex-1 max-w-7xl w-full mx-auto px-4 py-6 relative z-10">
          <InvalidMachinePage
            currentMachineId={machineId}
            errorMessage={error.message}
            onSelectMachine={handleSelectMachine}
          />
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-brand-bg/85 text-brand-text flex flex-col justify-between relative">
      {/* 3D Sylva Living World background behind */}
      <SylvaBackground />

      {/* Header with Navigation Pills */}
      <Navbar machine={machine} activeTab={activeTab} onTabChange={setActiveTab} />

      {/* Main Container — Expanded for wide, spacious presentation matching mockup */}
      <main className="flex-1 max-w-[1440px] xl:max-w-[1500px] w-full mx-auto px-4 sm:px-6 lg:px-8 py-4 sm:py-6 relative z-10 pb-28 sm:pb-8">
        {loading ? (
          <div>
            {/* Banner skeleton */}
            <div className="w-full h-32 sm:h-44 glass-card rounded-3xl animate-pulse mb-6 border border-emerald-500/15" />
            {/* Catalog skeleton */}
            <CatalogSkeleton />
          </div>
        ) : error ? (
          <ErrorMessage
            title="Không thể tải danh sách sản phẩm"
            message={error.message}
            code={error.code}
            onRetry={loadMachineData}
          />
        ) : (
          <div>
            {/* View Switching based on activeTab */}
            {activeTab === 'support' && (
              <SupportSection machine={machine} onBackToHome={() => setActiveTab('home')} />
            )}

            {activeTab === 'about' && (
              <AboutSection machine={machine} onBackToHome={() => setActiveTab('home')} />
            )}

            {activeTab === 'orders' && (
              <OrdersSection
                machine={machine}
                onBackToHome={() => setActiveTab('home')}
                onOpenCheckout={() => setIsCheckoutOpen(true)}
              />
            )}

            {activeTab === 'home' && (
              <div>
                {/* Hero Section with 3D Vending Machine & Headline — Seamless in front of background */}
                <HeroBanner
                  machineImageSrc="/images/freshvend-machine-3d.png"
                  onStartOrder={() => {
                    const catalogEl = document.getElementById('catalog-container');
                    if (catalogEl) {
                      catalogEl.scrollIntoView({ behavior: 'smooth' });
                    }
                  }}
                />

                {/* Product Catalog — solid white, enlarged scale and pulled up to overlap machine feet */}
                <div
                  id="catalog-container"
                  className="relative z-20 -mt-24 sm:-mt-36 lg:-mt-44 xl:-mt-52 rounded-3xl sm:rounded-[36px] bg-white shadow-2xl border border-gray-100/90 p-6 sm:p-9 lg:p-11 transition-all"
                >
                  {/* Catalog Header Bar — Enlarged scale */}
                  <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 sm:gap-5 mb-6 sm:mb-8">
                    {/* Title with vertical green accent bar and machine identity caption */}
                    <div className="flex items-start gap-4">
                      <div className="w-2 h-12 sm:h-14 rounded-full bg-brand-primary shrink-0 mt-0.5" />
                      <div>
                        <h2 className="text-2xl sm:text-3xl lg:text-4xl font-black text-brand-primary tracking-tight">
                          Danh mục sản phẩm
                        </h2>
                        <p className="text-xs sm:text-sm lg:text-base text-brand-text-muted font-medium mt-1">
                          Khám phá các sản phẩm đa dạng, chất lượng tại{' '}
                          <span className="font-semibold text-brand-primary">
                            {machine?.name || 'Máy bán hàng tầng 1'}
                          </span>
                        </p>
                      </div>
                    </div>

                    {/* Category Filter Pills (Mockup) — Enlarged scale */}
                    <div className="flex items-center gap-2.5 sm:gap-3 flex-wrap">
                      <button
                        type="button"
                        onClick={() => setSelectedCategory('all')}
                        className={`flex items-center gap-2 px-4 sm:px-5 py-2.5 rounded-full text-xs sm:text-sm font-bold transition-all cursor-pointer ${
                          selectedCategory === 'all'
                            ? 'bg-brand-primary text-white shadow-xs'
                            : 'bg-emerald-50 text-brand-primary hover:bg-emerald-100/70 border border-emerald-500/15'
                        }`}
                      >
                        <Sparkles className="w-4 h-4" />
                        <span>Tất cả</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setSelectedCategory('drink')}
                        className={`flex items-center gap-2 px-4 sm:px-5 py-2.5 rounded-full text-xs sm:text-sm font-bold transition-all cursor-pointer ${
                          selectedCategory === 'drink'
                            ? 'bg-brand-primary text-white shadow-xs'
                            : 'bg-emerald-50 text-brand-primary hover:bg-emerald-100/70 border border-emerald-500/15'
                        }`}
                      >
                        <span>🥛 Nước giải khát</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setSelectedCategory('snack')}
                        className={`flex items-center gap-2 px-4 sm:px-5 py-2.5 rounded-full text-xs sm:text-sm font-bold transition-all cursor-pointer ${
                          selectedCategory === 'snack'
                            ? 'bg-brand-primary text-white shadow-xs'
                            : 'bg-emerald-50 text-brand-primary hover:bg-emerald-100/70 border border-emerald-500/15'
                        }`}
                      >
                        <span>🍿 Snack & Bánh</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setSelectedCategory('light')}
                        className={`flex items-center gap-2 px-4 sm:px-5 py-2.5 rounded-full text-xs sm:text-sm font-bold transition-all cursor-pointer ${
                          selectedCategory === 'light'
                            ? 'bg-brand-primary text-white shadow-xs'
                            : 'bg-emerald-50 text-brand-primary hover:bg-emerald-100/70 border border-emerald-500/15'
                        }`}
                      >
                        <span>🍬 Đồ ăn nhẹ</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setSelectedCategory('other')}
                        className={`hidden sm:flex items-center gap-2 px-4 sm:px-5 py-2.5 rounded-full text-xs sm:text-sm font-bold transition-all cursor-pointer ${
                          selectedCategory === 'other'
                            ? 'bg-brand-primary text-white shadow-xs'
                            : 'bg-emerald-50 text-brand-primary hover:bg-emerald-100/70 border border-emerald-500/15'
                        }`}
                      >
                        <span>⭕ Khác</span>
                      </button>

                      {/* Reset filter / Xem tất cả button */}
                      <button
                        type="button"
                        onClick={() => setSelectedCategory('all')}
                        className="ml-auto flex items-center gap-1.5 text-xs sm:text-sm font-bold text-emerald-700 hover:text-brand-primary hover:underline transition-colors px-2 py-1 cursor-pointer"
                      >
                        <span>Xem tất cả</span>
                        <ChevronRight className="w-4 h-4" />
                      </button>

                      {/* Stock Refresh button */}
                      <button
                        type="button"
                        onClick={loadMachineData}
                        aria-label="Làm mới tồn kho"
                        className="p-2.5 rounded-xl border border-emerald-500/20 bg-white hover:bg-emerald-50 text-brand-primary shadow-2xs active:scale-95 transition-all cursor-pointer"
                        title="Cập nhật tồn kho"
                      >
                        <RefreshCw className="w-4 h-4 text-brand-primary" />
                      </button>
                    </div>
                  </div>

                  {/* Products Grid — Spacious and enlarged */}
                  {products.length === 0 ? (
                    <div className="glass-card rounded-3xl p-10 text-center shadow-card">
                      <AlertCircle className="w-12 h-12 text-brand-text-muted mx-auto mb-2" />
                      <h3 className="font-bold text-lg text-brand-text">Không có sản phẩm</h3>
                      <p className="text-xs sm:text-sm text-brand-text-muted mt-1">
                        Máy hiện chưa được nạp sản phẩm vào các khay lò xo.
                      </p>
                    </div>
                  ) : filteredProducts.length === 0 ? (
                    <div className="p-10 text-center bg-emerald-50/50 rounded-2xl border border-emerald-500/15">
                      <AlertCircle className="w-8 h-8 text-emerald-600 mx-auto mb-2" />
                      <p className="text-sm font-bold text-brand-primary">Không có sản phẩm trong danh mục này</p>
                      <button
                        type="button"
                        onClick={() => setSelectedCategory('all')}
                        className="mt-3 px-5 py-2 rounded-xl bg-brand-primary text-white text-xs sm:text-sm font-bold"
                      >
                        Xem tất cả sản phẩm
                      </button>
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5 sm:gap-6 lg:gap-7">
                      {filteredProducts.map((product) => (
                        <ProductCard
                          key={product.productId}
                          product={product}
                          machineActive={isMachineActive}
                        />
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </main>

      {/* Mobile Floating Action Dock */}
      <MobileActionDock
        machineActive={isMachineActive}
        onOpenCart={() => setIsCartOpen(true)}
        onProceedToCheckout={() => {
          setIsCartOpen(false);
          setIsCheckoutOpen(true);
        }}
      />

      {/* Cart Drawer */}
      <CartDrawer
        machineActive={isMachineActive}
        onProceedToCheckout={() => {
          setIsCartOpen(false);
          setIsCheckoutOpen(true);
        }}
      />

      {/* Checkout Modal */}
      <CheckoutModal
        machineId={machineId}
        isOpen={isCheckoutOpen}
        onClose={() => setIsCheckoutOpen(false)}
      />

      {/* Footer */}
      <footer className="w-full glass-header py-6 text-center text-xs text-brand-text-muted mt-12 relative z-10">
        <div className="max-w-[1440px] xl:max-w-[1500px] mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-brand-primary shadow-xs" />
              <span className="font-bold text-brand-primary">Smart Vending Machine Prototype</span>
            </div>
            <span className="text-brand-text-subtle hidden sm:inline">•</span>
            <a
              href="/admin"
              className="text-[11px] font-bold text-brand-primary/85 hover:text-brand-primary hover:underline transition-colors"
            >
              Cổng quản trị đối soát
            </a>
          </div>
          <p className="text-[11px] text-brand-text-muted font-medium">
            Hệ thống bán hàng tự động tích hợp ESP32 • Spring Boot • React TypeScript
          </p>
        </div>
      </footer>
    </div>
  );
};
