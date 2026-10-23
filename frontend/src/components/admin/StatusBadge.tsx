import React from 'react';
import type { OrderStatus } from '../../types/admin';
import { AlertTriangle, CheckCircle, Clock, XCircle, RotateCcw, Box } from 'lucide-react';

interface OrderStatusBadgeProps {
  status: OrderStatus | string;
  size?: 'sm' | 'md' | 'lg';
}

export const OrderStatusBadge: React.FC<OrderStatusBadgeProps> = ({ status, size = 'md' }) => {
  const sizeClasses = {
    sm: 'px-2 py-0.5 text-[11px] gap-1',
    md: 'px-2.5 py-1 text-xs gap-1.5',
    lg: 'px-3 py-1.5 text-sm gap-2',
  }[size];

  switch (status) {
    case 'NEEDS_RECONCILIATION':
      return (
        <span className={`inline-flex items-center font-bold rounded-lg bg-amber-500/15 text-amber-800 border border-amber-500/30 ${sizeClasses}`}>
          <AlertTriangle className="w-3.5 h-3.5 text-amber-600 shrink-0" />
          <span>CẦN ĐỐI SOÁT</span>
        </span>
      );
    case 'PAID':
      return (
        <span className={`inline-flex items-center font-bold rounded-lg bg-blue-500/15 text-blue-800 border border-blue-500/30 ${sizeClasses}`}>
          <CheckCircle className="w-3.5 h-3.5 text-blue-600 shrink-0" />
          <span>ĐÃ THANH TOÁN</span>
        </span>
      );
    case 'DISPENSING':
      return (
        <span className={`inline-flex items-center font-bold rounded-lg bg-purple-500/15 text-purple-800 border border-purple-500/30 ${sizeClasses}`}>
          <RotateCcw className="w-3.5 h-3.5 text-purple-600 animate-spin shrink-0" />
          <span>ĐANG NHẢ HÀNG</span>
        </span>
      );
    case 'COMPLETED':
      return (
        <span className={`inline-flex items-center font-bold rounded-lg bg-emerald-500/15 text-emerald-800 border border-emerald-500/30 ${sizeClasses}`}>
          <CheckCircle className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
          <span>HOÀN THÀNH</span>
        </span>
      );
    case 'DISPENSE_FAILED':
      return (
        <span className={`inline-flex items-center font-bold rounded-lg bg-rose-500/15 text-rose-800 border border-rose-500/30 ${sizeClasses}`}>
          <XCircle className="w-3.5 h-3.5 text-rose-600 shrink-0" />
          <span>LỖI NHẢ HÀNG</span>
        </span>
      );
    case 'EXPIRED':
      return (
        <span className={`inline-flex items-center font-bold rounded-lg bg-slate-500/15 text-slate-700 border border-slate-500/30 ${sizeClasses}`}>
          <Clock className="w-3.5 h-3.5 text-slate-500 shrink-0" />
          <span>HẾT HẠN</span>
        </span>
      );
    case 'CANCELLED':
      return (
        <span className={`inline-flex items-center font-bold rounded-lg bg-gray-500/15 text-gray-700 border border-gray-500/30 ${sizeClasses}`}>
          <XCircle className="w-3.5 h-3.5 text-gray-500 shrink-0" />
          <span>ĐÃ HỦY / HOÀN TIỀN</span>
        </span>
      );
    case 'PENDING_PAYMENT':
      return (
        <span className={`inline-flex items-center font-bold rounded-lg bg-amber-50 text-amber-700 border border-amber-200 ${sizeClasses}`}>
          <Clock className="w-3.5 h-3.5 text-amber-500 shrink-0" />
          <span>CHỜ THANH TOÁN</span>
        </span>
      );
    default:
      return (
        <span className={`inline-flex items-center font-medium rounded-lg bg-gray-100 text-gray-700 border border-gray-200 ${sizeClasses}`}>
          <span>{status}</span>
        </span>
      );
  }
};

interface VerificationStatusBadgeProps {
  status: string;
}

export const VerificationStatusBadge: React.FC<VerificationStatusBadgeProps> = ({ status }) => {
  switch (status) {
    case 'VERIFIED':
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-semibold rounded-md bg-emerald-100 text-emerald-800 border border-emerald-200">
          <CheckCircle className="w-3 h-3 text-emerald-600" />
          <span>Hợp lệ</span>
        </span>
      );
    case 'LATE_PAYMENT':
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-semibold rounded-md bg-rose-100 text-rose-800 border border-rose-200">
          <Clock className="w-3 h-3 text-rose-600" />
          <span>Thanh toán muộn</span>
        </span>
      );
    case 'UNDERPAID':
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-semibold rounded-md bg-amber-100 text-amber-800 border border-amber-200">
          <AlertTriangle className="w-3 h-3 text-amber-600" />
          <span>Thiếu tiền</span>
        </span>
      );
    case 'DUPLICATE':
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-semibold rounded-md bg-indigo-100 text-indigo-800 border border-indigo-200">
          <RotateCcw className="w-3 h-3 text-indigo-600" />
          <span>Chuyển thừa/trùng</span>
        </span>
      );
    default:
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded-md bg-gray-100 text-gray-700 border border-gray-200">
          <span>{status}</span>
        </span>
      );
  }
};

interface ReservationHeldBadgeProps {
  isHeld?: boolean | null;
}

export const ReservationHeldBadge: React.FC<ReservationHeldBadgeProps> = ({ isHeld }) => {
  if (isHeld === undefined || isHeld === null) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded-md bg-gray-100 text-gray-500">
        <Box className="w-3 h-3 text-gray-400" />
        <span>Chưa rõ giữ chỗ</span>
      </span>
    );
  }

  if (isHeld) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-bold rounded-md bg-emerald-100 text-emerald-800 border border-emerald-300">
        <Box className="w-3 h-3 text-emerald-600" />
        <span>ĐANG GIỮ CHỖ KHO (1)</span>
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-semibold rounded-md bg-slate-100 text-slate-600 border border-slate-300">
      <Box className="w-3 h-3 text-slate-400" />
      <span>ĐÃ NHẢ GIỮ CHỖ (0)</span>
    </span>
  );
};
