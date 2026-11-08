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
    <div className="w-full glass-card rounded-3xl p-5 sm:p-6 mb-6 transition-all relative overflow-hidden">
      {/* Subtle organic light accent */}
      <div
        aria-hidden="true"
        className="absolute -top-10 -right-10 w-44 h-44 rounded-full pointer-events-none"
        style={{
          background: 'radial-gradient(circle, rgba(216,243,106,0.18) 0%, rgba(18,61,50,0) 70%)',
        }}
      />

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 relative z-10">
        <div>
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <span className="px-3 py-1 rounded-xl bg-brand-primary/10 text-brand-primary font-mono font-bold text-xs tracking-wider border border-brand-primary/15">
              {machine.machineId}
            </span>
            <MachineStatusBadge status={machine.status} />
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-brand-primary tracking-tight">
            {machine.name || `Máy bán hàng ${machine.machineId}`}
          </h1>
          <p className="text-xs sm:text-sm text-brand-text-muted flex items-center gap-1.5 mt-1.5 font-medium">
            <MapPin className="w-3.5 h-3.5 text-brand-primary shrink-0" />
            <span>Vị trí hoạt động cố định • Quét mã QR trực tiếp tại thân máy</span>
          </p>
        </div>

        {/* Quick status box */}
        <div className="flex items-center gap-3 p-3.5 rounded-2xl glass-pill text-xs text-brand-text shadow-2xs">
          <div className="w-8 h-8 rounded-xl bg-brand-primary/10 flex items-center justify-center shrink-0">
            <Info className="w-4 h-4 text-brand-primary" />
          </div>
          <div className="leading-relaxed">
            <span className="font-bold block text-brand-primary">Nhận hàng tự động</span>
            Khay nhả hàng nằm tại ngăn bên dưới thân máy
          </div>
        </div>
      </div>

      {/* Warning message if machine is inactive or has a message */}
      {isInactive && (
        <div
          id="machine-inactive-banner"
          className="mt-4 p-4 rounded-2xl bg-rose-50/90 border border-rose-200 text-rose-900 text-sm flex items-start gap-3 backdrop-blur-sm"
        >
          <AlertTriangle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold block">Máy hiện đang tạm ngừng hoạt động!</span>
            <p className="text-xs text-rose-700 mt-0.5 leading-relaxed">
              {machine.message || 'Hệ thống đang bảo trì hoặc tạm ngưng phục vụ. Quý khách vui lòng không thực hiện đặt hàng vào lúc này.'}
            </p>
          </div>
        </div>
      )}

      {!isInactive && machine.message && (
        <div className="mt-4 p-3.5 rounded-2xl bg-amber-50/90 border border-amber-200 text-amber-900 text-xs flex items-center gap-2.5 backdrop-blur-sm">
          <Info className="w-4 h-4 text-amber-600 shrink-0" />
          <span className="font-medium">{machine.message}</span>
        </div>
      )}
    </div>
  );
};
