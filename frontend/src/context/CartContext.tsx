import React, { createContext, useContext, useState, useEffect } from 'react';
import type { Product } from '../types/product';

export interface CartItem {
  product: Product;
  quantity: number;
}

interface CartContextType {
  items: CartItem[];
  addToCart: (product: Product, qty?: number) => { success: boolean; message?: string };
  updateQuantity: (productId: string, qty: number) => { success: boolean; message?: string };
  removeFromCart: (productId: string) => void;
  clearCart: () => void;
  totalItems: number;
  totalAmountVnd: number;
  isCartOpen: boolean;
  setIsCartOpen: (open: boolean) => void;
}

const CartContext = createContext<CartContextType | undefined>(undefined);

export const CartProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [items, setItems] = useState<CartItem[]>(() => {
    // Optional: retrieve saved cart for this session
    try {
      const saved = sessionStorage.getItem('vending_cart');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });
  const [isCartOpen, setIsCartOpen] = useState(false);

  useEffect(() => {
    try {
      sessionStorage.setItem('vending_cart', JSON.stringify(items));
    } catch {
      // Ignore storage errors
    }
  }, [items]);

  const addToCart = (product: Product, qty = 1): { success: boolean; message?: string } => {
    if (!product.available || product.stockQuantity <= 0) {
      return { success: false, message: 'Sản phẩm đã hết hàng trong máy.' };
    }

    if (qty <= 0) {
      return { success: false, message: 'Số lượng phải lớn hơn 0.' };
    }

    const existingIndex = items.findIndex((i) => i.product.productId === product.productId);

    if (existingIndex > -1) {
      const currentQty = items[existingIndex].quantity;
      const newQty = currentQty + qty;

      if (newQty > product.stockQuantity) {
        return {
          success: false,
          message: `Chỉ còn ${product.stockQuantity} sản phẩm trong khay số ${product.slotNumber}.`,
        };
      }

      const updated = [...items];
      updated[existingIndex] = {
        ...updated[existingIndex],
        quantity: newQty,
      };
      setItems(updated);
      return { success: true };
    } else {
      if (qty > product.stockQuantity) {
        return {
          success: false,
          message: `Chỉ còn ${product.stockQuantity} sản phẩm trong khay số ${product.slotNumber}.`,
        };
      }

      setItems([...items, { product, quantity: qty }]);
      return { success: true };
    }
  };

  const updateQuantity = (productId: string, qty: number): { success: boolean; message?: string } => {
    if (qty <= 0) {
      removeFromCart(productId);
      return { success: true };
    }

    const item = items.find((i) => i.product.productId === productId);
    if (!item) return { success: false, message: 'Sản phẩm không có trong giỏ hàng.' };

    if (qty > item.product.stockQuantity) {
      return {
        success: false,
        message: `Số lượng tối đa có thể chọn là ${item.product.stockQuantity}.`,
      };
    }

    setItems((prev) =>
      prev.map((i) => (i.product.productId === productId ? { ...i, quantity: qty } : i))
    );
    return { success: true };
  };

  const removeFromCart = (productId: string) => {
    setItems((prev) => prev.filter((i) => i.product.productId !== productId));
  };

  const clearCart = () => {
    setItems([]);
  };

  const totalItems = items.reduce((sum, item) => sum + item.quantity, 0);

  // Integer VND summation — avoid float math
  const totalAmountVnd = items.reduce((sum, item) => sum + item.product.priceVnd * item.quantity, 0);

  return (
    <CartContext.Provider
      value={{
        items,
        addToCart,
        updateQuantity,
        removeFromCart,
        clearCart,
        totalItems,
        totalAmountVnd,
        isCartOpen,
        setIsCartOpen,
      }}
    >
      {children}
    </CartContext.Provider>
  );
};

export const useCart = (): CartContextType => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
};
