import React from 'react';
import { useAdminAuth } from '../../context/AdminAuthContext';
import { ShieldCheck, LogOut, RefreshCw, ShoppingBag } from 'lucide-react';

interface AdminLayoutProps {
  children: React.ReactNode;
  title: string;
  subtitle?: string;
  breadcrumbs?: Array<{ label: string; href?: string; onClick?: () => void }>;
  onRefresh?: () => void;
  isRefreshing?: boolean;
}

export const AdminLayout: React.FC<AdminLayoutProps> = ({
  children,
  title,
  subtitle,
  breadcrumbs,
  onRefresh,
  isRefreshing = false,
}) => {
  const { adminUser, logout } = useAdminAuth();

  return (
    <div className="min-h-screen bg-brand-bg flex flex-col font-sans">
      {/* Top Admin Navbar */}
      <header className="sticky top-0 z-40 bg-brand-primary text-white border-b border-brand-primary-light/40 shadow-card">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            {/* Logo & Portal Branding */}
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-brand-accent/20 border border-brand-accent/30 flex items-center justify-center text-brand-accent shadow-sm">
                <ShieldCheck className="w-6 h-6" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-extrabold tracking-tight text-white text-base sm:text-lg">
                    Vending Admin
                  </span>
                  <span className="px-2 py-0.5 text-xs font-semibold rounded-full bg-brand-accent text-brand-primary uppercase tracking-wider">
                    Portal
                  </span>
                </div>
                <p className="text-xs text-brand-accent/80 hidden sm:block">
                  Trung tâm quản trị & đối soát đơn hàng
                </p>
              </div>
            </div>

            {/* Admin User Info & Navigation Actions */}
            <div className="flex items-center gap-2 sm:gap-4">
              <a
                href="/"
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-white/10 text-white/90 hover:bg-white/20 transition-colors"
                title="Quay lại giao diện khách mua hàng"
              >
                <ShoppingBag className="w-3.5 h-3.5" />
                <span className="hidden md:inline">Giao diện khách</span>
              </a>

              {adminUser && (
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-xs">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                  <span className="text-white/80 font-medium">{adminUser.username}</span>
                </div>
              )}

              <button
                type="button"
                onClick={logout}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg bg-rose-500/20 text-rose-300 hover:bg-rose-500/30 border border-rose-500/30 transition-colors"
                title="Đăng xuất khỏi hệ thống quản trị"
              >
                <LogOut className="w-3.5 h-3.5" />
                <span>Đăng xuất</span>
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Main Page Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {/* Breadcrumb Navigation if provided */}
        {breadcrumbs && breadcrumbs.length > 0 && (
          <nav className="flex items-center gap-2 text-xs text-brand-text-muted mb-4 overflow-x-auto pb-1" aria-label="Breadcrumb">
            {breadcrumbs.map((crumb, idx) => {
              const isLast = idx === breadcrumbs.length - 1;
              return (
                <React.Fragment key={idx}>
                  {idx > 0 && <span className="text-brand-text-subtle">/</span>}
                  {isLast ? (
                    <span className="font-semibold text-brand-text">{crumb.label}</span>
                  ) : (
                    <button
                      type="button"
                      onClick={crumb.onClick}
                      className="hover:text-brand-primary hover:underline font-medium"
                    >
                      {crumb.label}
                    </button>
                  )}
                </React.Fragment>
              );
            })}
          </nav>
        )}

        {/* Page Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6 pb-4 border-b border-brand-border">
          <div>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-brand-text tracking-tight">
              {title}
            </h1>
            {subtitle && (
              <p className="text-sm text-brand-text-muted mt-1">
                {subtitle}
              </p>
            )}
          </div>

          {onRefresh && (
            <div>
              <button
                type="button"
                onClick={onRefresh}
                disabled={isRefreshing}
                className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-xl bg-white border border-brand-border text-brand-text hover:bg-brand-bg hover:border-brand-text-subtle shadow-sm transition-all disabled:opacity-60"
              >
                <RefreshCw className={`w-4 h-4 text-brand-primary ${isRefreshing ? 'animate-spin' : ''}`} />
                <span>Làm mới dữ liệu</span>
              </button>
            </div>
          )}
        </div>

        {/* Content Body */}
        {children}
      </main>

      {/* Admin Footer */}
      <footer className="mt-auto py-4 border-t border-brand-border bg-white/50 text-center text-xs text-brand-text-muted">
        <p>Vending Machine Prototype — Admin Reconciliation Portal © 2026. All rights reserved.</p>
      </footer>
    </div>
  );
};
