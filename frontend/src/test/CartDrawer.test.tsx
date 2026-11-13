import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import React, { useEffect, useRef } from 'react';
import { CartDrawer } from '../components/CartDrawer';
import { CartProvider, useCart } from '../context/CartContext';
import type { Product } from '../types/product';

const CartPopulator: React.FC<{
  product: Product;
  quantity?: number;
  openDrawer?: boolean;
  children?: React.ReactNode;
}> = ({ product, quantity = 1, openDrawer = true, children }) => {
  const { addToCart, setIsCartOpen } = useCart();
  const initializedRef = useRef(false);

  useEffect(() => {
    if (!initializedRef.current) {
      initializedRef.current = true;
      addToCart(product, quantity);
      if (openDrawer) {
        setIsCartOpen(true);
      }
    }
  }, [addToCart, setIsCartOpen, product, quantity, openDrawer]);

  return <>{children}</>;
};

describe('CartDrawer Component', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('renders nothing when isCartOpen is false', () => {
    const { container } = render(
      <CartProvider>
        <CartDrawer onProceedToCheckout={vi.fn()} />
      </CartProvider>
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders empty cart message when open but no items in cart', () => {
    const OpenEmptyCart: React.FC = () => {
      const { setIsCartOpen } = useCart();
      useEffect(() => {
        setIsCartOpen(true);
      }, [setIsCartOpen]);
      return <CartDrawer onProceedToCheckout={vi.fn()} />;
    };

    render(
      <CartProvider>
        <OpenEmptyCart />
      </CartProvider>
    );

    expect(screen.getByText('Giỏ hàng đang trống')).toBeInTheDocument();
  });

  it('displays accurate available stock and enables plus button when below available stock', () => {
    const productInStock: Product = {
      productId: 'prod-instock-1',
      name: 'Trà Xanh Không Độ',
      description: null,
      imageUrl: null,
      priceVnd: 10000,
      slotNumber: 2,
      stockQuantity: 5,
      reservedQuantity: 1,
      availableStock: 4,
      available: true,
    };

    render(
      <CartProvider>
        <CartPopulator product={productInStock} quantity={2}>
          <CartDrawer onProceedToCheckout={vi.fn()} />
        </CartPopulator>
      </CartProvider>
    );

    // Shows accurate available stock
    expect(screen.getByText('Tồn: 4')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument(); // current qty

    const plusButton = screen.getByRole('button', { name: /Tăng số lượng/i });
    expect(plusButton).toBeEnabled();
  });

  it('disables plus button and prevents increasing quantity when quantity reaches availableStock', () => {
    const productLimitedStock: Product = {
      productId: 'prod-limit-1',
      name: 'Cà phê Highlands',
      description: null,
      imageUrl: null,
      priceVnd: 15000,
      slotNumber: 3,
      stockQuantity: 3,
      reservedQuantity: 2,
      availableStock: 1,
      available: true,
    };

    render(
      <CartProvider>
        <CartPopulator product={productLimitedStock} quantity={1}>
          <CartDrawer onProceedToCheckout={vi.fn()} />
        </CartPopulator>
      </CartProvider>
    );

    expect(screen.getByText('Tồn: 1')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();

    const plusButton = screen.getByRole('button', { name: /Tăng số lượng/i });
    expect(plusButton).toBeDisabled();
    expect(plusButton.className).toContain('cursor-not-allowed');

    // Clicking plus should not change quantity
    fireEvent.click(plusButton);
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('correctly uses fallback available stock (0) and disables plus button when availableStock is omitted with stockQuantity = 2 and reservedQuantity = 2', () => {
    const legacyProductHeld: Product = {
      productId: 'prod-legacy-aqua',
      name: 'Nước suối Aqua Legacy',
      description: null,
      imageUrl: null,
      priceVnd: 8000,
      slotNumber: 1,
      stockQuantity: 2,
      reservedQuantity: 2,
      // availableStock omitted
      available: true,
    };

    // Pre-seed session storage before CartProvider mounts so items are loaded with legacy item
    sessionStorage.setItem(
      'vending_cart',
      JSON.stringify([{ product: legacyProductHeld, quantity: 1 }])
    );

    const OpenCart: React.FC = () => {
      const { setIsCartOpen } = useCart();
      useEffect(() => {
        setIsCartOpen(true);
      }, [setIsCartOpen]);
      return <CartDrawer onProceedToCheckout={vi.fn()} />;
    };

    render(
      <CartProvider>
        <OpenCart />
      </CartProvider>
    );

    // Fallback calculation: Math.max(0, 2 - 2) = 0
    expect(screen.getByText('Tồn: 0')).toBeInTheDocument();

    const plusButton = screen.getByRole('button', { name: /Tăng số lượng/i });
    expect(plusButton).toBeDisabled();
    expect(plusButton.className).toContain('cursor-not-allowed');
  });
});
