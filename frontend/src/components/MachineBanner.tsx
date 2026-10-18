import React from 'react';
import { MapPin, Info, AlertTriangle } from 'lucide-react';
import type { MachineResponse } from '../types/machine';
import { MachineStatusBadge } from './StatusBadge';

interface MachineBannerProps {
  machine: MachineResponse;
}

export const MachineBanner: React.FC<MachineBannerProps> = ({ machine }) => {
  const isInactive = machine.status !== 'ACTIVE';

  return (
    <div className="w-full bg-white rounded-2xl p-5 border border-brand-border shadow-card mb-6 transition-all">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2 mb-1.5">
            <span className="px-2.5 py-0.5 rounded-lg bg-brand-primary/10 text-brand-primary font-mono font-bold text-xs">
              {machine.machineId}
            </span>
            <MachineStatusBadge status={machine.status} />
          </div>
          <h1 className="text-xl sm:text-2xl font-bold text-brand-primary tracking-tight">
            {machine.name || `Máy bán hàng ${machine.machineId}`}
          </h1>
          <p className="text-xs sm:text-sm text-brand-text-muted flex items-center gap-1.5 mt-1">
            <MapPin className="w-3.5 h-3.5 text-brand-primary" />
            Vị trí hoạt động cố định • Quét mã QR trực tiếp tại thân máy
          </p>
        </div>

        {/* Quick status box */}
        <div className="flex items-center gap-3 p-3 rounded-xl bg-brand-bg border border-brand-border/60 text-xs text-brand-text">
          <Info className="w-4 h-4 text-brand-primary shrink-0" />
          <div className="leading-relaxed">
            <span className="font-semibold block text-brand-primary">Nhận hàng tự động</span>
            Khay nhả hàng nằm tại ngăn bên dưới thân máy
          </div>
        </div>
      </div>

      {/* Warning message if machine is inactive or has a message */}
      {isInactive && (
        <div
          id="machine-inactive-banner"
          className="mt-4 p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-sm flex items-start gap-2.5"
        >
          <AlertTriangle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold block">Máy hiện đang tạm ngừng hoạt động!</span>
            <p className="text-xs text-rose-700 mt-0.5">
              {machine.message || 'Hệ thống đang bảo trì hoặc tạm ngưng phục vụ. Quý khách vui lòng không thực hiện đặt hàng vào lúc này.'}
            </p>
          </div>
        </div>
      )}

      {!isInactive && machine.message && (
        <div className="mt-4 p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs flex items-center gap-2">
          <Info className="w-4 h-4 text-amber-600 shrink-0" />
          <span>{machine.message}</span>
        </div>
      )}
    </div>
  );
};
