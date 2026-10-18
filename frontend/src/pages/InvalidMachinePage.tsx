import React, { useState } from 'react';
import { ArrowRight, QrCode } from 'lucide-react';

interface InvalidMachinePageProps {
  currentMachineId?: string;
  errorMessage?: string;
  onSelectMachine: (machineId: string) => void;
}

export const InvalidMachinePage: React.FC<InvalidMachinePageProps> = ({
  currentMachineId,
  errorMessage,
  onSelectMachine,
}) => {
  const [inputVal, setInputVal] = useState(currentMachineId || '');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (inputVal.trim()) {
      onSelectMachine(inputVal.trim().toUpperCase());
    }
  };

  return (
    <div className="min-h-[80vh] flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md bg-white rounded-3xl border border-brand-border p-6 sm:p-8 text-center shadow-card">
        <div className="w-16 h-16 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center mx-auto mb-4 border border-amber-100">
          <QrCode className="w-8 h-8" />
        </div>

        <h1 className="text-xl sm:text-2xl font-bold text-brand-primary tracking-tight mb-2">
          Không tìm thấy máy bán hàng
        </h1>

        <p className="text-xs sm:text-sm text-brand-text-muted leading-relaxed mb-6">
          {errorMessage ||
            `Mã máy "${currentMachineId || 'không xác định'}" không tồn tại trên hệ thống hoặc đang tạm ngừng dịch vụ. Quý khách vui lòng quét lại mã QR dán trên thân máy.`}
        </p>

        {/* Input manual machine code */}
        <form onSubmit={handleSubmit} className="space-y-3 mb-6">
          <label htmlFor="manual-machine-input" className="block text-xs font-semibold text-brand-text text-left">
            Nhập mã máy trên thân máy (Ví dụ: VM001)
          </label>
          <div className="flex gap-2">
            <input
              id="manual-machine-input"
              type="text"
              value={inputVal}
              onChange={(e) => setInputVal(e.target.value)}
              placeholder="Nhập mã máy (VM001)..."
              className="flex-1 px-4 py-2.5 rounded-xl border border-brand-border text-sm font-mono uppercase focus:outline-none focus:ring-2 focus:ring-brand-primary/20 focus:border-brand-primary"
            />
            <button
              type="submit"
              className="px-4 py-2.5 rounded-xl bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white font-semibold text-sm transition-all"
            >
              Kiểm tra
            </button>
          </div>
        </form>

        {/* Quick button to try default seed machine VM001 */}
        <div className="pt-4 border-t border-brand-border/60">
          <p className="text-xs text-brand-text-subtle mb-3">Hoặc chọn máy kiểm thử mặc định:</p>
          <button
            id="try-vm001-btn"
            type="button"
            onClick={() => onSelectMachine('VM001')}
            className="w-full flex items-center justify-between p-3.5 rounded-xl bg-brand-bg hover:bg-brand-accent/20 border border-brand-border text-xs font-semibold text-brand-primary transition-all"
          >
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span>Máy thử nghiệm mặc định (VM001)</span>
            </div>
            <ArrowRight className="w-4 h-4 text-brand-primary" />
          </button>
        </div>
      </div>
    </div>
  );
};
