import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ProductCard } from '../components/ProductCard';
import { CartProvider } from '../context/CartContext';
import type { Product } from '../types/product';

const inStockProduct: Product = {
  productId: 'prod-001',
  name: 'Nước suối Aqua 500ml',
  description: 'Nước tinh khiết mát lành',
  imageUrl: null,
  priceVnd: 8000,
  slotNumber: 1,
  stockQuantity: 5,
  available: true,
};

const outOfStockProduct: Product = {
  productId: 'prod-002',
  name: 'Kẹo cao su Doublemint',
  description: null,
  imageUrl: null,
  priceVnd: 5000,
  slotNumber: 3,
  stockQuantity: 0,
  available: false,
};

describe('ProductCard', () => {
  it('renders product details correctly', () => {
    render(
      <CartProvider>
        <ProductCard product={inStockProduct} />
      </CartProvider>
    );

    expect(screen.getByText('KHOANG 01')).toBeInTheDocument();
    expect(screen.getByText('Khay 01')).toBeInTheDocument();
    expect(screen.getByText('Nước suối Aqua 500ml')).toBeInTheDocument();
    expect(screen.getByText('8.000 ₫')).toBeInTheDocument();
    expect(screen.getByText(/Còn hàng: 5/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Chọn mua/i })).toBeEnabled();
  });

  it('renders out-of-stock badge and disables action button', () => {
    render(
      <CartProvider>
        <ProductCard product={outOfStockProduct} />
      </CartProvider>
    );

    expect(screen.getByText('KHOANG 03')).toBeInTheDocument();
    expect(screen.getByText('Khay 03')).toBeInTheDocument();
    expect(screen.getByText('Kẹo cao su Doublemint')).toBeInTheDocument();
    expect(screen.getByText('5.000 ₫')).toBeInTheDocument();
    expect(screen.getAllByText('Hết hàng').length).toBeGreaterThanOrEqual(1);

    const button = screen.getByRole('button', { name: /Hết hàng/i });
    expect(button).toBeDisabled();
  });

  it('disables button when machine is inactive', () => {
    render(
      <CartProvider>
        <ProductCard product={inStockProduct} machineActive={false} />
      </CartProvider>
    );

    const button = screen.getByRole('button', { name: /Hết hàng/i });
    expect(button).toBeDisabled();
  });

  it('renders correct product image and falls back to placeholder on load error', () => {
    render(
      <CartProvider>
        <ProductCard product={inStockProduct} />
      </CartProvider>
    );

    const image = screen.getByRole('img', { name: 'Nước suối Aqua 500ml' });
    expect(image).toBeInTheDocument();
    expect(image).toHaveAttribute('src', '/images/products/aquafina.png');

    // Trigger image error to test fallback
    fireEvent.error(image);
    expect(screen.getByText('Hình minh họa mẫu')).toBeInTheDocument();
  });

  it('provides feedback when product is added to cart', () => {
    render(
      <CartProvider>
        <ProductCard product={inStockProduct} />
      </CartProvider>
    );

    const button = screen.getByRole('button', { name: /Chọn mua/i });
    fireEvent.click(button);

    expect(screen.getByText('Đã thêm vào giỏ!')).toBeInTheDocument();
  });

  it('renders out-of-stock when availableStock is 0 even if physical stockQuantity > 0 (Aqua incident scenario)', () => {
    const aquaReservedProduct: Product = {
      productId: 'prod-aqua-held',
      name: 'Nước suối Aqua 500ml',
      description: 'Nước uống tinh khiết chai 500ml',
      imageUrl: null,
      priceVnd: 8000,
      slotNumber: 1,
      stockQuantity: 2,
      reservedQuantity: 2,
      availableStock: 0,
      available: false,
    };

    render(
      <CartProvider>
        <ProductCard product={aquaReservedProduct} />
      </CartProvider>
    );

    expect(screen.getAllByText('Hết hàng').length).toBeGreaterThanOrEqual(1);
    const button = screen.getByRole('button', { name: /Hết hàng/i });
    expect(button).toBeDisabled();
  });

  it('renders low-stock badge and enables buying when availableStock is 1 (stock 2, reserved 1)', () => {
    const aquaLowStockProduct: Product = {
      productId: 'prod-aqua-low',
      name: 'Nước suối Aqua 500ml',
      description: 'Nước uống tinh khiết chai 500ml',
      imageUrl: null,
      priceVnd: 8000,
      slotNumber: 1,
      stockQuantity: 2,
      reservedQuantity: 1,
      availableStock: 1,
      available: true,
    };

    render(
      <CartProvider>
        <ProductCard product={aquaLowStockProduct} />
      </CartProvider>
    );

    expect(screen.getByText('Sắp hết: 1')).toBeInTheDocument();
    const button = screen.getByRole('button', { name: /Chọn mua/i });
    expect(button).toBeEnabled();
  });

  it('renders out-of-stock and disables button when availableStock is omitted with stockQuantity = 2 and reservedQuantity = 2', () => {
    const legacyAquaHeldProduct: Product = {
      productId: 'prod-aqua-legacy-held',
      name: 'Nước suối Aqua 500ml',
      description: 'Nước uống tinh khiết chai 500ml',
      imageUrl: null,
      priceVnd: 8000,
      slotNumber: 1,
      stockQuantity: 2,
      reservedQuantity: 2,
      // availableStock omitted (legacy API response)
      available: true,
    };

    render(
      <CartProvider>
        <ProductCard product={legacyAquaHeldProduct} />
      </CartProvider>
    );

    expect(screen.getAllByText('Hết hàng').length).toBeGreaterThanOrEqual(1);
    const button = screen.getByRole('button', { name: /Hết hàng/i });
    expect(button).toBeDisabled();
  });
});
