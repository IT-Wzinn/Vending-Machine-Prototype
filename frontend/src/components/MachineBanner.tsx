import React from 'react';
import { Cpu, AlertTriangle, Info } from 'lucide-react';
import type { MachineResponse } from '../types/machine';
import { MachineStatusBadge } from './StatusBadge';

interface MachineBannerProps {
  machine: MachineResponse;
}

export const MachineBanner: React.FC<MachineBannerProps> = ({ machine }) => {
  const isInactive = machine.status !== 'ACTIVE';

  return (
    <section aria-label="Thông tin máy bán hàng" className="mb-3.5 sm:mb-4">
      {/* Compact Machine HUD Strip */}
      <div className="w-full glass-card rounded-2xl px-3.5 py-2.5 sm:px-4 sm:py-3 transition-all flex items-center justify-between gap-3 shadow-2xs border border-emerald-500/15">
        {/* Machine Identity */}
        <div className="flex items-center gap-2.5 sm:gap-3 min-w-0">
          <div className="w-8 h-8 rounded-xl bg-brand-primary/10 border border-emerald-500/20 flex items-center justify-center shrink-0 text-brand-primary">
            <Cpu className="w-4 h-4" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="font-mono font-bold text-xs sm:text-sm text-brand-primary tracking-wide">
                {machine.machineId}
              </span>
              <span className="text-brand-text-subtle text-xs hidden sm:inline">•</span>
              <h1 className="text-xs sm:text-sm font-bold text-brand-text truncate">
                {machine.name || `Máy bán hàng ${machine.machineId}`}
              </h1>
            </div>
            <p className="text-[11px] text-brand-text-muted hidden sm:block truncate mt-0.5">
              Hệ thống bán hàng thông minh tự động nhả sản phẩm theo khay
            </p>
          </div>
        </div>

        {/* Machine Status Badge */}
        <div className="shrink-0 flex items-center gap-2">
          <MachineStatusBadge status={machine.status} />
        </div>
      </div>

      {/* Warning message if machine is inactive */}
      {isInactive && (
        <div
          id="machine-inactive-banner"
          className="mt-2.5 p-3 rounded-2xl bg-rose-50/90 border border-rose-200 text-rose-900 text-xs sm:text-sm flex items-start gap-2.5 backdrop-blur-sm shadow-2xs"
        >
          <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold block">Máy hiện đang tạm ngừng hoạt động!</span>
            <p className="text-xs text-rose-700 mt-0.5 leading-relaxed">
              {machine.message || 'Hệ thống đang bảo trì hoặc tạm ngưng phục vụ. Quý khách vui lòng không thực hiện đặt hàng vào lúc này.'}
            </p>
          </div>
        </div>
      )}

      {!isInactive && machine.message && (
        <div className="mt-2.5 p-2.5 rounded-2xl bg-amber-50/90 border border-amber-200 text-amber-900 text-xs flex items-center gap-2 backdrop-blur-sm shadow-2xs">
          <Info className="w-4 h-4 text-amber-600 shrink-0" />
          <span className="font-medium text-xs">{machine.message}</span>
        </div>
      )}
    </section>
  );
};
