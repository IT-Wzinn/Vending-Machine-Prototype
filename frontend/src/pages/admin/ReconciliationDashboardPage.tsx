import React, { useEffect, useState, useCallback } from 'react';
import { AdminLayout } from '../../components/admin/AdminLayout';
import { OrderStatusBadge, ReservationHeldBadge } from '../../components/admin/StatusBadge';
import type { Page, ReconciliationOrderSummary } from '../../types/admin';
import { fetchReconciliationOrders } from '../../services/adminApi';
import { formatVnd, formatDateTime } from '../../utils/formatters';
import {
  Search,
  Filter,
  ChevronLeft,
  ChevronRight,
  ArrowRight,
  AlertTriangle,
  PackageCheck,
  Clock,
  Layers,
} from 'lucide-react';

interface ReconciliationDashboardPageProps {
  onSelectOrder: (orderId: string) => void;
}

export const ReconciliationDashboardPage: React.FC<ReconciliationDashboardPageProps> = ({
  onSelectOrder,
}) => {
  const [ordersPage, setOrdersPage] = useState<Page<ReconciliationOrderSummary> | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Filters state
  const [statusFilter, setStatusFilter] = useState<string>('NEEDS_RECONCILIATION');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [debouncedQuery, setDebouncedQuery] = useState<string>('');
  const [currentPage, setCurrentPage] = useState<number>(0);
  const pageSize = 10;

  // Debounce search query input by 400ms
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedQuery(searchQuery.trim());
      setCurrentPage(0); // Reset to first page on search
    }, 400);
    return () => clearTimeout(handler);
  }, [searchQuery]);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await fetchReconciliationOrders({
        page: currentPage,
        size: pageSize,
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        orderCode: debouncedQuery || undefined,
      });

      setOrdersPage(data);
      setLoading(false);
    } catch (err: unknown) {
      setLoading(false);
      const msg = err instanceof Error ? err.message : 'Không thể tải danh sách đơn hàng đối soát.';
      setError(msg);
    }
  }, [currentPage, statusFilter, debouncedQuery]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  const handleStatusChange = (newStatus: string) => {
    setStatusFilter(newStatus);
    setCurrentPage(0);
  };

  const statusOptions = [
    { value: 'NEEDS_RECONCILIATION', label: 'Cần đối soát (Ưu tiên)' },
    { value: 'PAID', label: 'Đã thanh toán' },
    { value: 'DISPENSE_FAILED', label: 'Lỗi nhả hàng' },
    { value: 'EXPIRED', label: 'Hết hạn' },
    { value: 'COMPLETED', label: 'Hoàn thành' },
    { value: 'CANCELLED', label: 'Đã hủy / Hoàn tiền' },
    { value: 'ALL', label: 'Tất cả trạng thái' },
  ];

  return (
    <AdminLayout
      title="Danh sách đối soát đơn hàng"
      subtitle="Theo dõi, tra soát và ghi nhận quyết định xử lý các đơn hàng ngoại lệ"
      onRefresh={loadOrders}
      isRefreshing={loading}
    >
      {/* Search & Filter Toolbar */}
      <div className="bg-white p-4 rounded-2xl border border-brand-border shadow-sm mb-6 space-y-3 sm:space-y-0 sm:flex sm:items-center sm:justify-between gap-4">
        {/* Search Input */}
        <div className="relative flex-1 max-w-md">
          <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-brand-text-subtle">
            <Search className="w-4 h-4" />
          </div>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Tìm theo mã đơn (vd: VM001-202610...)"
            className="w-full pl-9 pr-4 py-2 text-sm bg-brand-bg border border-brand-border rounded-xl text-brand-text placeholder-brand-text-subtle focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-transparent transition-all"
          />
        </div>

        {/* Filter Dropdown */}
        <div className="flex items-center gap-2">
          <Filter className="w-4 h-4 text-brand-text-muted shrink-0" />
          <select
            value={statusFilter}
            onChange={(e) => handleStatusChange(e.target.value)}
            className="text-sm font-medium bg-brand-bg border border-brand-border rounded-xl px-3 py-2 text-brand-text focus:outline-none focus:ring-2 focus:ring-brand-primary transition-all"
          >
            {statusOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 mb-6 flex items-start justify-between gap-3">
          <div className="flex items-start gap-2.5">
            <AlertTriangle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold text-sm">Lỗi tải dữ liệu đối soát</p>
              <p className="text-xs text-rose-700 mt-0.5">{error}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={loadOrders}
            className="px-3 py-1.5 text-xs font-bold rounded-lg bg-rose-600 text-white hover:bg-rose-700 transition-colors shrink-0"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* Main List Table / Cards */}
      {loading && !ordersPage ? (
        /* Loading Skeleton */
        <div className="space-y-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="bg-white p-5 rounded-2xl border border-brand-border shadow-sm animate-pulse flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="space-y-2.5 flex-1">
                <div className="h-5 bg-gray-200 rounded w-1/3" />
                <div className="h-4 bg-gray-100 rounded w-1/2" />
              </div>
              <div className="h-8 bg-gray-200 rounded w-28" />
            </div>
          ))}
        </div>
      ) : ordersPage && ordersPage.content.length > 0 ? (
        /* Orders List */
        <div className="space-y-3">
          {ordersPage.content.map((order) => (
            <div
              key={order.orderId}
              onClick={() => onSelectOrder(order.orderId)}
              className="bg-white p-5 rounded-2xl border border-brand-border shadow-sm hover:shadow-card-hover hover:border-brand-primary/40 transition-all cursor-pointer flex flex-col md:flex-row md:items-center justify-between gap-4 group"
            >
              {/* Left Column: Code, Status, Time, Product */}
              <div className="space-y-2 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono font-extrabold text-brand-text text-base group-hover:text-brand-primary transition-colors">
                    {order.publicOrderCode}
                  </span>
                  <OrderStatusBadge status={order.status} size="sm" />
                  <ReservationHeldBadge isHeld={order.isReservationHeld} />
                </div>

                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-brand-text-muted">
                  <span>Máy: <strong className="text-brand-text font-medium">{order.machineCode || 'N/A'}</strong></span>
                  <span>Sản phẩm: <strong className="text-brand-text font-medium">{order.productName || 'N/A'} (x{order.quantity || 1})</strong></span>
                  {order.slotNumber !== undefined && (
                    <span>Hộc: <strong className="text-brand-text font-medium">{order.slotNumber}</strong></span>
                  )}
                  {order.transferReference && (
                    <span>Nội dung CK: <code className="font-mono bg-brand-bg px-1.5 py-0.5 rounded text-brand-text font-semibold">{order.transferReference}</code></span>
                  )}
                </div>

                <div className="flex items-center gap-3 text-xs text-brand-text-subtle">
                  <span className="flex items-center gap-1">
                    <Clock className="w-3.5 h-3.5" />
                    Tạo: {formatDateTime(order.createdAt)}
                  </span>
                  {order.paidAt && (
                    <span>• TT: {formatDateTime(order.paidAt)}</span>
                  )}
                </div>
              </div>

              {/* Right Column: Amount & Action Link */}
              <div className="flex items-center justify-between md:justify-end gap-4 shrink-0 pt-3 md:pt-0 border-t md:border-t-0 border-brand-border">
                <div className="text-left md:text-right">
                  <span className="block text-xs text-brand-text-subtle">Tổng tiền</span>
                  <span className="text-lg font-black text-brand-primary">
                    {formatVnd(order.totalAmountVnd)}
                  </span>
                </div>

                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onSelectOrder(order.orderId);
                  }}
                  className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-bold rounded-xl bg-brand-bg text-brand-primary group-hover:bg-brand-primary group-hover:text-white border border-brand-border group-hover:border-transparent transition-all shadow-sm"
                >
                  <span>Chi tiết</span>
                  <ArrowRight className="w-4 h-4 group-hover:translate-x-0.5 transition-transform" />
                </button>
              </div>
            </div>
          ))}

          {/* Pagination Controls */}
          <div className="bg-white p-4 rounded-2xl border border-brand-border shadow-sm flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-brand-text-muted mt-6">
            <div>
              Tổng cộng <strong className="text-brand-text font-semibold">{ordersPage.totalElements}</strong> đơn hàng
              {ordersPage.totalPages > 1 && (
                <span> — Trang <strong className="text-brand-text">{ordersPage.number + 1}</strong> / {ordersPage.totalPages}</span>
              )}
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                disabled={ordersPage.first || loading}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-brand-border bg-brand-bg font-semibold text-brand-text hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
                <span>Trang trước</span>
              </button>

              <button
                type="button"
                onClick={() => setCurrentPage((p) => (ordersPage.last ? p : p + 1))}
                disabled={ordersPage.last || loading}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-brand-border bg-brand-bg font-semibold text-brand-text hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <span>Trang sau</span>
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      ) : (
        /* Empty State */
        <div className="bg-white rounded-2xl border border-brand-border p-12 text-center shadow-sm">
          <div className="w-16 h-16 rounded-2xl bg-brand-accent/20 border border-brand-accent/30 text-brand-primary mx-auto flex items-center justify-center mb-4">
            <PackageCheck className="w-8 h-8 text-brand-primary" />
          </div>
          <h3 className="text-lg font-bold text-brand-text mb-1">
            Không tìm thấy đơn hàng nào
          </h3>
          <p className="text-sm text-brand-text-muted max-w-sm mx-auto mb-6">
            {statusFilter === 'NEEDS_RECONCILIATION'
              ? 'Tuyệt vời! Hiện không có đơn hàng nào cần xử lý đối soát trong hệ thống.'
              : 'Không có đơn hàng nào khớp với điều kiện lọc hiện tại.'}
          </p>
          {statusFilter !== 'ALL' && (
            <button
              type="button"
              onClick={() => handleStatusChange('ALL')}
              className="inline-flex items-center gap-2 px-4 py-2 text-xs font-bold rounded-xl bg-brand-primary text-white hover:bg-brand-primary-hover transition-colors shadow-sm"
            >
              <Layers className="w-4 h-4" />
              <span>Xem tất cả trạng thái đơn</span>
            </button>
          )}
        </div>
      )}
    </AdminLayout>
  );
};
