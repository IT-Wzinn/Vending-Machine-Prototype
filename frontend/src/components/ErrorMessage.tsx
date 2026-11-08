import React from 'react';
import { AlertTriangle, RefreshCw, ServerCrash, HelpCircle } from 'lucide-react';

interface ErrorMessageProps {
  title?: string;
  message: string;
  code?: string;
  onRetry?: () => void;
}

export const ErrorMessage: React.FC<ErrorMessageProps> = ({
  title = 'Đã xảy ra sự cố',
  message,
  code,
  onRetry,
}) => {
  const isNetworkError = code === 'NETWORK_ERROR';
  const isNotFound = code === 'MACHINE_NOT_FOUND' || code === 'NOT_FOUND';

  return (
    <div
      id="error-state-card"
      className="w-full glass-card rounded-3xl border border-rose-300/50 p-6 sm:p-8 text-center shadow-card max-w-lg mx-auto my-6"
    >
      <div className="w-14 h-14 rounded-2xl bg-rose-500/15 text-rose-700 flex items-center justify-center mx-auto mb-4 border border-rose-500/25">
        {isNetworkError ? (
          <ServerCrash className="w-7 h-7" />
        ) : isNotFound ? (
          <HelpCircle className="w-7 h-7" />
        ) : (
          <AlertTriangle className="w-7 h-7" />
        )}
      </div>

      <h2 className="text-lg sm:text-xl font-bold text-brand-text mb-2">{title}</h2>
      
      <p className="text-sm text-brand-text-muted leading-relaxed mb-4">{message}</p>

      {code && (
        <div className="inline-block px-3 py-1 rounded-xl glass-pill text-gray-700 font-mono text-xs mb-6">
          Mã lỗi: {code}
        </div>
      )}

      {onRetry && (
        <div>
          <button
            type="button"
            onClick={onRetry}
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-2xl bg-brand-primary hover:bg-brand-primary-hover active:scale-95 text-white text-sm font-semibold shadow-md border border-emerald-400/20 transition-all min-h-[44px]"
          >
            <RefreshCw className="w-4 h-4" />
            <span>Thử lại kết nối</span>
          </button>
        </div>
      )}
    </div>
  );
};
