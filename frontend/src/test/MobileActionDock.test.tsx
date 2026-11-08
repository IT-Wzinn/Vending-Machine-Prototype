import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MobileActionDock } from '../components/MobileActionDock';
import { CartProvider, useCart } from '../context/CartContext';
import React, { useEffect, useRef } from 'react';
import type { Product } from '../types/product';

const mockProduct: Product = {
  productId: 'prod-01',
  name: 'Nước suối Aqua 500ml',
  description: 'Nước uống tinh khiết',
  imageUrl: null,
  priceVnd: 10000,
  slotNumber: 1,
  stockQuantity: 5,
  available: true,
};

const CartPopulator: React.FC<{ children: React.ReactNode; count?: number }> = ({
  children,
  count = 2,
}) => {
  const { addToCart } = useCart();
  const addedRef = useRef(false);

  useEffect(() => {
    if (!addedRef.current) {
      addedRef.current = true;
      addToCart(mockProduct, count);
    }
  }, [addToCart, count]);

  return <>{children}</>;
};

describe('MobileActionDock Component', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('does not render when cart is empty', () => {
    render(
      <CartProvider>
        <MobileActionDock onOpenCart={vi.fn()} onProceedToCheckout={vi.fn()} />
      </CartProvider>
    );

    expect(screen.queryByRole('complementary', { name: /Thanh tác vụ thanh toán nhanh/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/món đã chọn/i)).not.toBeInTheDocument();
  });

  it('renders correctly with item count and total when cart has items', () => {
    render(
      <CartProvider>
        <CartPopulator count={2}>
          <MobileActionDock onOpenCart={vi.fn()} onProceedToCheckout={vi.fn()} />
        </CartPopulator>
      </CartProvider>
    );

    expect(screen.getByRole('complementary', { name: /Thanh tác vụ thanh toán nhanh/i })).toBeInTheDocument();
    expect(screen.getByText(/2\s+món đã chọn/i)).toBeInTheDocument();
    expect(screen.getByText('20.000 ₫')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Thanh toán ngay/i })).toBeInTheDocument();
  });

  it('calls onOpenCart when clicking cart summary area', () => {
    const handleOpenCart = vi.fn();
    const handleProceed = vi.fn();

    render(
      <CartProvider>
        <CartPopulator count={1}>
          <MobileActionDock onOpenCart={handleOpenCart} onProceedToCheckout={handleProceed} />
        </CartPopulator>
      </CartProvider>
    );

    const summaryBtn = screen.getByRole('button', { name: /Xem chi tiết giỏ hàng/i });
    fireEvent.click(summaryBtn);

    expect(handleOpenCart).toHaveBeenCalledTimes(1);
    expect(handleProceed).not.toHaveBeenCalled();
  });

  it('calls onProceedToCheckout when clicking checkout button', () => {
    const handleOpenCart = vi.fn();
    const handleProceed = vi.fn();

    render(
      <CartProvider>
        <CartPopulator count={1}>
          <MobileActionDock onOpenCart={handleOpenCart} onProceedToCheckout={handleProceed} />
        </CartPopulator>
      </CartProvider>
    );

    const checkoutBtn = screen.getByRole('button', { name: /Thanh toán ngay/i });
    fireEvent.click(checkoutBtn);

    expect(handleProceed).toHaveBeenCalledTimes(1);
    expect(handleOpenCart).not.toHaveBeenCalled();
  });

  it('disables checkout button when machineActive is false', () => {
    render(
      <CartProvider>
        <CartPopulator count={1}>
          <MobileActionDock
            machineActive={false}
            onOpenCart={vi.fn()}
            onProceedToCheckout={vi.fn()}
          />
        </CartPopulator>
      </CartProvider>
    );

    const checkoutBtn = screen.getByRole('button', { name: /Thanh toán ngay/i });
    expect(checkoutBtn).toBeDisabled();
    expect(checkoutBtn).toHaveClass('cursor-not-allowed');
  });
});
