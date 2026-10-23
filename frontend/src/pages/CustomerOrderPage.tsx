import React, { useEffect, useState, useCallback } from 'react';
import { Navbar } from '../components/Navbar';
import { MachineBanner } from '../components/MachineBanner';
import { ProductCard } from '../components/ProductCard';
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
import { RefreshCw, AlertCircle } from 'lucide-react';

interface CustomerOrderPageProps {
  initialMachineId?: string;
}

export const CustomerOrderPage: React.FC<CustomerOrderPageProps> = ({ initialMachineId }) => {
  const { setIsCartOpen } = useCart();

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

  // If machine does not exist or invalid code returned from backend
  if (!loading && error && (error.code === 'MACHINE_NOT_FOUND' || error.code === 'NOT_FOUND')) {
    return (
      <div className="min-h-screen bg-brand-bg text-brand-text flex flex-col justify-between">
        <Navbar machine={null} />
        <main className="flex-1 max-w-5xl w-full mx-auto px-4 py-6">
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
    <div className="min-h-screen bg-brand-bg text-brand-text flex flex-col justify-between">
      {/* Header */}
      <Navbar machine={machine} />

      {/* Main Container */}
      <main className="flex-1 max-w-5xl w-full mx-auto px-4 py-6">
        {loading ? (
          <div>
            {/* Banner skeleton */}
            <div className="w-full h-32 bg-white rounded-2xl border border-brand-border animate-pulse mb-6" />
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
            {/* Machine banner with status & info */}
            {machine && <MachineBanner machine={machine} />}

            {/* Catalog Section Header */}
            <div className="flex items-center justify-between gap-3 mb-4">
              <div>
                <h2 className="text-lg sm:text-xl font-bold text-brand-text">Danh mục sản phẩm</h2>
                <p className="text-xs text-brand-text-muted">Chọn món bằng cách bấm "Chọn mua" bên dưới</p>
              </div>
              <button
                type="button"
                onClick={loadMachineData}
                aria-label="Làm mới tồn kho"
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-brand-border bg-white hover:bg-gray-50 text-xs font-semibold text-brand-text-muted transition-colors shadow-2xs"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Cập nhật tồn kho</span>
              </button>
            </div>

            {/* Products Grid */}
            {products.length === 0 ? (
              <div className="bg-white rounded-2xl border border-brand-border p-8 text-center shadow-card">
                <AlertCircle className="w-10 h-10 text-brand-text-muted mx-auto mb-2" />
                <h3 className="font-bold text-base text-brand-text">Không có sản phẩm</h3>
                <p className="text-xs text-brand-text-muted mt-1">
                  Máy hiện chưa được nạp sản phẩm vào các khay lò xo.
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-6">
                {products.map((product) => (
                  <ProductCard
                    key={product.productId}
                    product={product}
                    machineActive={isMachineActive}
                  />
                ))}
              </div>
            )}
          </div>
        )}
      </main>

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
      <footer className="w-full border-t border-brand-border/60 bg-white/60 py-6 text-center text-xs text-brand-text-muted mt-12">
        <div className="max-w-5xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-brand-primary" />
              <span className="font-semibold text-brand-primary">Smart Vending Machine Prototype</span>
            </div>
            <span className="text-brand-text-subtle hidden sm:inline">•</span>
            <a
              href="/admin"
              className="text-[11px] font-semibold text-brand-text-muted hover:text-brand-primary hover:underline transition-colors"
            >
              Cổng quản trị đối soát
            </a>
          </div>
          <p className="text-[11px] text-brand-text-subtle">
            Hệ thống bán hàng tự động tích hợp ESP32 • Spring Boot • React TypeScript
          </p>
        </div>
      </footer>
    </div>
  );
};
