import React from 'react';
import type { MachineStatus } from '../types/machine';

interface MachineStatusBadgeProps {
  status: MachineStatus;
}

export const MachineStatusBadge: React.FC<MachineStatusBadgeProps> = ({ status }) => {
  switch (status) {
    case 'ACTIVE':
      return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-500/15 text-emerald-900 border border-emerald-500/30 backdrop-blur-xs shadow-2xs">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
          Hoạt động
        </span>
      );
    case 'INACTIVE':
      return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-rose-500/15 text-rose-900 border border-rose-500/30 backdrop-blur-xs shadow-2xs">
          <span className="w-2 h-2 rounded-full bg-rose-500" />
          Tạm ngừng
        </span>
      );
    case 'MAINTENANCE':
      return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-500/15 text-amber-900 border border-amber-500/30 backdrop-blur-xs shadow-2xs">
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
      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-gray-200/60 text-gray-600 border border-gray-300/50 backdrop-blur-xs shadow-2xs">
        Hết hàng
      </span>
    );
  }

  if (stockQuantity <= 2) {
    return (
      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-500/15 text-amber-900 border border-amber-400/40 backdrop-blur-xs shadow-2xs">
        Sắp hết: {stockQuantity}
      </span>
    );
  }

  return (
    <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-500/15 text-emerald-900 border border-emerald-400/40 backdrop-blur-xs shadow-2xs">
      Còn hàng: {stockQuantity}
    </span>
  );
};
