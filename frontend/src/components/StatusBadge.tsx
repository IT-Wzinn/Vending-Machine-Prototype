import React from 'react';
import type { MachineStatus } from '../types/machine';

interface MachineStatusBadgeProps {
  status: MachineStatus;
}

export const MachineStatusBadge: React.FC<MachineStatusBadgeProps> = ({ status }) => {
  switch (status) {
    case 'ACTIVE':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-800 border border-emerald-200">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
          Hoạt động
        </span>
      );
    case 'INACTIVE':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-50 text-rose-800 border border-rose-200">
          <span className="w-2 h-2 rounded-full bg-rose-500" />
          Tạm ngừng
        </span>
      );
    case 'MAINTENANCE':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-200">
          <span className="w-2 h-2 rounded-full bg-amber-500" />
          Đang bảo trì
        </span>
      );
    default:
      return null;
  }
};

interface StockBadgeProps {
  stockQuantity: number;
  available: boolean;
}

export const StockBadge: React.FC<StockBadgeProps> = ({ stockQuantity, available }) => {
  if (!available || stockQuantity <= 0) {
    return (
      <span className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium bg-gray-100 text-gray-500 border border-gray-200">
        Hết hàng
      </span>
    );
  }

  if (stockQuantity <= 2) {
    return (
      <span className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200">
        Sắp hết: {stockQuantity}
      </span>
    );
  }

  return (
    <span className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
      Còn hàng: {stockQuantity}
    </span>
  );
};
