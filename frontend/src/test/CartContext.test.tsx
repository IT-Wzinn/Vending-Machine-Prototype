import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import React from 'react';
import { CartProvider, useCart } from '../context/CartContext';
import type { Product } from '../types/product';

const mockProductA: Product = {
  productId: 'prod-001',
  name: 'Nước suối Aqua',
  description: '500ml',
  imageUrl: null,
  priceVnd: 8000,
  slotNumber: 1,
  stockQuantity: 3,
  available: true,
};

const mockProductOutOfStock: Product = {
  productId: 'prod-002',
  name: 'Kẹo cao su',
  description: null,
  imageUrl: null,
  priceVnd: 5000,
  slotNumber: 2,
  stockQuantity: 0,
  available: false,
};

describe('CartContext', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <CartProvider>{children}</CartProvider>
  );

  it('initializes with empty cart and zero total', () => {
    const { result } = renderHook(() => useCart(), { wrapper });
    expect(result.current.items).toEqual([]);
    expect(result.current.totalItems).toBe(0);
    expect(result.current.totalAmountVnd).toBe(0);
  });

  it('adds an in-stock product to cart successfully', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    act(() => {
      const res = result.current.addToCart(mockProductA, 1);
      expect(res.success).toBe(true);
    });

    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].product.productId).toBe('prod-001');
    expect(result.current.items[0].quantity).toBe(1);
    expect(result.current.totalItems).toBe(1);
    expect(result.current.totalAmountVnd).toBe(8000);
  });

  it('rejects adding an out-of-stock product', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    act(() => {
      const res = result.current.addToCart(mockProductOutOfStock, 1);
      expect(res.success).toBe(false);
      expect(res.message).toContain('hết hàng');
    });

    expect(result.current.items).toHaveLength(0);
  });

  it('prevents adding quantity exceeding available stock', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    // Stock is 3
    act(() => {
      result.current.addToCart(mockProductA, 2);
    });

    act(() => {
      const res = result.current.addToCart(mockProductA, 2); // 2 + 2 = 4 > 3
      expect(res.success).toBe(false);
      expect(res.message).toContain('Chỉ còn 3 sản phẩm');
    });

    // Remains at 2
    expect(result.current.items[0].quantity).toBe(2);
  });

  it('updates quantity and removes item when quantity reaches zero', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    act(() => {
      result.current.addToCart(mockProductA, 2);
    });

    // Update to 3
    act(() => {
      result.current.updateQuantity('prod-001', 3);
    });
    expect(result.current.items[0].quantity).toBe(3);
    expect(result.current.totalAmountVnd).toBe(24000);

    // Update to 0 removes the item
    act(() => {
      result.current.updateQuantity('prod-001', 0);
    });
    expect(result.current.items).toHaveLength(0);
    expect(result.current.totalItems).toBe(0);
    expect(result.current.totalAmountVnd).toBe(0);
  });

  it('clears all items when clearCart is called', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    act(() => {
      result.current.addToCart(mockProductA, 1);
    });
    expect(result.current.items).toHaveLength(1);

    act(() => {
      result.current.clearCart();
    });
    expect(result.current.items).toHaveLength(0);
  });
});
