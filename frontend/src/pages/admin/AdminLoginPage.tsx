import React, { useState } from 'react';
import { useAdminAuth } from '../../context/AdminAuthContext';
import { ShieldCheck, Lock, User, Eye, EyeOff, AlertCircle, ArrowLeft } from 'lucide-react';

interface AdminLoginPageProps {
  onLoginSuccess?: () => void;
}

export const AdminLoginPage: React.FC<AdminLoginPageProps> = ({ onLoginSuccess }) => {
  const { login, isLoading, authError, clearAuthError } = useAdminAuth();

  const [username, setUsername] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setValidationError(null);
    clearAuthError();

    const trimmedUser = username.trim();
    if (!trimmedUser) {
      setValidationError('Vui lòng nhập tên đăng nhập quản trị.');
      return;
    }

    if (!password) {
      setValidationError('Vui lòng nhập mật khẩu.');
      return;
    }

    const success = await login(trimmedUser, password);
    if (success && onLoginSuccess) {
      onLoginSuccess();
    }
  };

  return (
    <div className="min-h-screen bg-brand-bg flex flex-col justify-center py-12 sm:px-6 lg:px-8 font-sans">
      <div className="sm:mx-auto sm:w-full sm:max-w-md px-4">
        {/* Top Back Link */}
        <div className="mb-6 flex justify-between items-center">
          <a
            href="/"
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-brand-primary hover:text-brand-primary-hover hover:underline transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Quay lại Kiosk khách hàng</span>
          </a>
        </div>

        {/* Logo and Title */}
        <div className="text-center">
          <div className="mx-auto w-14 h-14 rounded-2xl bg-brand-primary flex items-center justify-center text-brand-accent shadow-card mb-4">
            <ShieldCheck className="w-8 h-8" />
          </div>
          <h2 className="text-2xl sm:text-3xl font-extrabold text-brand-text tracking-tight">
            Đăng nhập Quản trị
          </h2>
          <p className="mt-2 text-sm text-brand-text-muted">
            Hệ thống đối soát đơn hàng và quản lý máy bán hàng
          </p>
        </div>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4">
        <div className="bg-white py-8 px-6 shadow-floating rounded-2xl border border-brand-border sm:px-10">
          {/* Error Banner */}
          {(authError || validationError) && (
            <div className="mb-6 p-4 rounded-xl bg-rose-50 border border-rose-200 flex items-start gap-3">
              <AlertCircle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
              <div className="text-sm text-rose-800">
                <p className="font-semibold">{validationError || 'Đăng nhập không thành công'}</p>
                {authError && <p className="mt-0.5 text-xs text-rose-700">{authError}</p>}
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-5" noValidate>
            {/* Username field */}
            <div>
              <label htmlFor="admin-username" className="block text-sm font-semibold text-brand-text mb-1.5">
                Tên đăng nhập
              </label>
              <div className="relative rounded-xl shadow-sm">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-brand-text-subtle">
                  <User className="h-5 h-5" />
                </div>
                <input
                  id="admin-username"
                  name="username"
                  type="text"
                  autoComplete="username"
                  required
                  value={username}
                  onChange={(e) => {
                    setUsername(e.target.value);
                    if (validationError) setValidationError(null);
                  }}
                  disabled={isLoading}
                  placeholder="Nhập username quản trị..."
                  className="block w-full pl-11 pr-4 py-2.5 sm:text-sm bg-brand-bg/50 border border-brand-border rounded-xl text-brand-text placeholder-brand-text-subtle focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-transparent transition-all"
                />
              </div>
            </div>

            {/* Password field */}
            <div>
              <label htmlFor="admin-password" className="block text-sm font-semibold text-brand-text mb-1.5">
                Mật khẩu
              </label>
              <div className="relative rounded-xl shadow-sm">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-brand-text-subtle">
                  <Lock className="h-5 h-5" />
                </div>
                <input
                  id="admin-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (validationError) setValidationError(null);
                  }}
                  disabled={isLoading}
                  placeholder="••••••••••••"
                  className="block w-full pl-11 pr-11 py-2.5 sm:text-sm bg-brand-bg/50 border border-brand-border rounded-xl text-brand-text placeholder-brand-text-subtle focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-transparent transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-brand-text-subtle hover:text-brand-text transition-colors"
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showPassword ? <EyeOff className="h-5 h-5" /> : <Eye className="h-5 h-5" />}
                </button>
              </div>
            </div>

            {/* Submit Button */}
            <div className="pt-2">
              <button
                type="submit"
                disabled={isLoading}
                className="w-full flex justify-center items-center py-3 px-4 border border-transparent rounded-xl shadow-sm text-sm font-bold text-white bg-brand-primary hover:bg-brand-primary-hover focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-brand-primary transition-all disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {isLoading ? (
                  <div className="flex items-center gap-2">
                    <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    <span>Đang xác thực...</span>
                  </div>
                ) : (
                  'Đăng nhập Hệ thống'
                )}
              </button>
            </div>
          </form>

          {/* Security & Configuration Notice */}
          <div className="mt-6 pt-6 border-t border-brand-border text-center">
            <p className="text-xs text-brand-text-subtle leading-relaxed">
              Tài khoản quản trị đầu tiên được khởi tạo an toàn thông qua biến môi trường{' '}
              <code className="px-1.5 py-0.5 rounded bg-brand-bg text-brand-text font-mono text-[11px]">
                APP_ADMIN_BOOTSTRAP_USERNAME
              </code>{' '}
              và{' '}
              <code className="px-1.5 py-0.5 rounded bg-brand-bg text-brand-text font-mono text-[11px]">
                APP_ADMIN_BOOTSTRAP_PASSWORD
              </code>.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
