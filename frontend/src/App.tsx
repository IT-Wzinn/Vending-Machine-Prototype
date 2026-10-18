import React from 'react';
import { CartProvider } from './context/CartContext';
import { CustomerOrderPage } from './pages/CustomerOrderPage';

export const App: React.FC = () => {
  return (
    <CartProvider>
      <CustomerOrderPage />
    </CartProvider>
  );
};

export default App;
