import React, { useState, useEffect } from 'react';
import { CartProvider } from './context/CartContext';
import { CustomerOrderPage } from './pages/CustomerOrderPage';
import { AdminAuthProvider, useAdminAuth } from './context/AdminAuthContext';
import { AdminLoginPage } from './pages/admin/AdminLoginPage';
import { ReconciliationDashboardPage } from './pages/admin/ReconciliationDashboardPage';
import { ReconciliationOrderDetailPage } from './pages/admin/ReconciliationOrderDetailPage';

export const AdminRouter: React.FC = () => {
  const { isAuthenticated } = useAdminAuth();
  const [currentPath, setCurrentPath] = useState<string>(() =>
    typeof window !== 'undefined' ? window.location.pathname : '/admin'
  );

  useEffect(() => {
    const handlePopState = () => {
      setCurrentPath(window.location.pathname);
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const navigate = (path: string) => {
    if (typeof window !== 'undefined' && window.history) {
      window.history.pushState({}, '', path);
    }
    setCurrentPath(path);
  };

  // If not authenticated, always show AdminLoginPage
  if (!isAuthenticated) {
    return (
      <AdminLoginPage
        onLoginSuccess={() => {
          navigate('/admin/reconciliation');
        }}
      />
    );
  }

  // Check if viewing order detail: /admin/reconciliation/:orderId
  const detailMatch = currentPath.match(/^\/admin\/reconciliation\/([a-zA-Z0-9_-]+)$/);
  if (detailMatch && detailMatch[1] && detailMatch[1] !== 'orders') {
    return (
      <ReconciliationOrderDetailPage
        orderId={detailMatch[1]}
        onBack={() => navigate('/admin/reconciliation')}
      />
    );
  }

  // Default to Reconciliation Dashboard
  return (
    <ReconciliationDashboardPage
      onSelectOrder={(orderId) => {
        navigate(`/admin/reconciliation/${orderId}`);
      }}
    />
  );
};

export const App: React.FC = () => {
  const [pathname, setPathname] = useState<string>(() =>
    typeof window !== 'undefined' ? window.location.pathname : '/'
  );

  useEffect(() => {
    const handlePopState = () => {
      setPathname(window.location.pathname);
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const isAdminRoute = pathname.startsWith('/admin');

  if (isAdminRoute) {
    return (
      <AdminAuthProvider>
        <AdminRouter />
      </AdminAuthProvider>
    );
  }

  return (
    <CartProvider>
      <CustomerOrderPage />
    </CartProvider>
  );
};

export default App;
