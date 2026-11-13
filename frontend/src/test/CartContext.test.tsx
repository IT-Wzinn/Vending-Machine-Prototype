import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import React from 'react';
import { CartProvider, useCart } from '../context/CartContext';
import { getAvailableStock, type Product } from '../types/product';

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

  it('respects availableStock when physical stockQuantity is higher (reserved stock scenario)', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    const productWithReservations: Product = {
      productId: 'prod-reserved-1',
      name: 'Aqua 500ml',
      description: null,
      imageUrl: null,
      priceVnd: 8000,
      slotNumber: 1,
      stockQuantity: 2,
      reservedQuantity: 1,
      availableStock: 1,
      available: true,
    };

    act(() => {
      const res = result.current.addToCart(productWithReservations, 1);
      expect(res.success).toBe(true);
    });

    // Attempting to add 1 more exceeds availableStock (1)
    act(() => {
      const res = result.current.addToCart(productWithReservations, 1);
      expect(res.success).toBe(false);
      expect(res.message).toContain('Chỉ còn 1 sản phẩm');
    });

    // Zero availableStock product is rejected immediately
    const outOfAvailableStockProduct: Product = {
      productId: 'prod-reserved-all',
      name: 'Aqua Held',
      description: null,
      imageUrl: null,
      priceVnd: 8000,
      slotNumber: 1,
      stockQuantity: 2,
      reservedQuantity: 2,
      availableStock: 0,
      available: false,
    };

    act(() => {
      const res = result.current.addToCart(outOfAvailableStockProduct, 1);
      expect(res.success).toBe(false);
      expect(res.message).toContain('hết hàng');
    });
  });

  it('correctly calculates available stock via getAvailableStock fallback when API omits availableStock', () => {
    // 1. stockQuantity = 2, reservedQuantity = 2, availableStock omitted -> must be 0
    expect(getAvailableStock({ stockQuantity: 2, reservedQuantity: 2 })).toBe(0);
    expect(getAvailableStock({ stockQuantity: 2, reservedQuantity: 2, availableStock: undefined })).toBe(0);

    // 2. stockQuantity = 2, reservedQuantity = 1, availableStock omitted -> must be 1
    expect(getAvailableStock({ stockQuantity: 2, reservedQuantity: 1 })).toBe(1);

    // 3. stockQuantity = 5, reservedQuantity = 0, availableStock omitted -> must be 5
    expect(getAvailableStock({ stockQuantity: 5, reservedQuantity: 0 })).toBe(5);
    expect(getAvailableStock({ stockQuantity: 5 })).toBe(5);

    // 4. When availableStock is explicitly defined, it takes precedence
    expect(getAvailableStock({ stockQuantity: 5, reservedQuantity: 3, availableStock: 2 })).toBe(2);
    expect(getAvailableStock({ stockQuantity: 2, reservedQuantity: 2, availableStock: 0 })).toBe(0);
  });

  it('rejects adding to cart when legacy product has stockQuantity = 2, reservedQuantity = 2 and availableStock is omitted', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    const legacyHeldAqua: Product = {
      productId: 'prod-legacy-aqua-held',
      name: 'Aqua 500ml Legacy',
      description: null,
      imageUrl: null,
      priceVnd: 8000,
      slotNumber: 1,
      stockQuantity: 2,
      reservedQuantity: 2,
      // availableStock omitted
      available: true,
    };

    act(() => {
      const res = result.current.addToCart(legacyHeldAqua, 1);
      expect(res.success).toBe(false);
      expect(res.message).toContain('hết hàng');
    });
    expect(result.current.items).toHaveLength(0);
  });

  it('allows buying up to fallback available stock when legacy product has stockQuantity = 2, reservedQuantity = 1 and availableStock is omitted', () => {
    const { result } = renderHook(() => useCart(), { wrapper });

    const legacyLowAqua: Product = {
      productId: 'prod-legacy-aqua-low',
      name: 'Aqua 500ml Legacy Low',
      description: null,
      imageUrl: null,
      priceVnd: 8000,
      slotNumber: 1,
      stockQuantity: 2,
      reservedQuantity: 1,
      // availableStock omitted
      available: true,
    };

    act(() => {
      const res = result.current.addToCart(legacyLowAqua, 1);
      expect(res.success).toBe(true);
    });
    expect(result.current.items).toHaveLength(1);

    act(() => {
      const res = result.current.addToCart(legacyLowAqua, 1);
      expect(res.success).toBe(false);
      expect(res.message).toContain('Chỉ còn 1 sản phẩm');
    });
  });
});
