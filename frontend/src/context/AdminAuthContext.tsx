import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { AdminUser } from '../types/admin';
import { loginAdmin, setInMemoryToken, registerUnauthorizedHandler } from '../services/adminApi';

interface AdminAuthContextType {
  adminUser: AdminUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  authError: string | null;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
  clearAuthError: () => void;
}

const AdminAuthContext = createContext<AdminAuthContextType | undefined>(undefined);

export const AdminAuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [adminUser, setAdminUser] = useState<AdminUser | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [authError, setAuthError] = useState<string | null>(null);

  const logout = useCallback(() => {
    setAdminUser(null);
    setInMemoryToken(null);
    setAuthError(null);
  }, []);

  useEffect(() => {
    // When backend returns 401, invalidate state in memory
    registerUnauthorizedHandler(() => {
      logout();
    });
  }, [logout]);

  const login = async (username: string, password: string): Promise<boolean> => {
    setIsLoading(true);
    setAuthError(null);

    try {
      const response = await loginAdmin({
        username: username.trim(),
        password,
      });

      setAdminUser({ username: response.username });
      setIsLoading(false);
      return true;
    } catch (err: unknown) {
      setIsLoading(false);
      const message = err instanceof Error ? err.message : 'Đăng nhập không thành công. Vui lòng kiểm tra lại.';
      setAuthError(message);
      return false;
    }
  };

  const clearAuthError = () => {
    setAuthError(null);
  };

  return (
    <AdminAuthContext.Provider
      value={{
        adminUser,
        isAuthenticated: !!adminUser,
        isLoading,
        authError,
        login,
        logout,
        clearAuthError,
      }}
    >
      {children}
    </AdminAuthContext.Provider>
  );
};

export const useAdminAuth = (): AdminAuthContextType => {
  const context = useContext(AdminAuthContext);
  if (!context) {
    throw new Error('useAdminAuth must be used within an AdminAuthProvider');
  }
  return context;
};
