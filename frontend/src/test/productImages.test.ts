import { describe, it, expect } from 'vitest';
import { resolveProductImage } from '../utils/productImages';
import type { Product } from '../types/product';

describe('productImages - Image Mapping & Fallback Utility', () => {
  it('maps backend seed product IDs to the correct local product image assets', () => {
    const aquaProduct: Product = {
      productId: '550e8400-e29b-41d4-a716-446655440001',
      name: 'Nước suối Aqua 500ml',
      description: null,
      imageUrl: 'https://example.com/images/aqua-500ml.jpg',
      priceVnd: 8000,
      slotNumber: 1,
      stockQuantity: 4,
      available: true,
    };

    const snackProduct: Product = {
      productId: '550e8400-e29b-41d4-a716-446655440002',
      name: "Snack Lay's vị tự nhiên",
      description: null,
      imageUrl: null,
      priceVnd: 15000,
      slotNumber: 2,
      stockQuantity: 3,
      available: true,
    };

    const keoProduct: Product = {
      productId: '550e8400-e29b-41d4-a716-446655440003',
      name: 'Kẹo cao su Doublemint',
      description: null,
      imageUrl: null,
      priceVnd: 5000,
      slotNumber: 3,
      stockQuantity: 0,
      available: false,
    };

    expect(resolveProductImage(aquaProduct)).toBe('/images/products/aquafina.png');
    expect(resolveProductImage(snackProduct)).toBe('/images/products/snack.png');
    expect(resolveProductImage(keoProduct)).toBe('/images/products/keo.png');
  });

  it('matches product image by name keywords when ID is non-standard', () => {
    const aquaByName: Product = {
      productId: 'prod-custom-99',
      name: 'Aquafina chai 500ml',
      description: null,
      imageUrl: null,
      priceVnd: 10000,
      slotNumber: 1,
      stockQuantity: 2,
      available: true,
    };

    const snackByName: Product = {
      productId: 'prod-custom-88',
      name: "Bánh Snack khoai tây Lay's",
      description: null,
      imageUrl: null,
      priceVnd: 16000,
      slotNumber: 2,
      stockQuantity: 5,
      available: true,
    };

    const keoByName: Product = {
      productId: 'prod-custom-77',
      name: 'Kẹo cao su Doublemint hương bạc hà',
      description: null,
      imageUrl: null,
      priceVnd: 6000,
      slotNumber: 3,
      stockQuantity: 10,
      available: true,
    };

    expect(resolveProductImage(aquaByName)).toBe('/images/products/aquafina.png');
    expect(resolveProductImage(snackByName)).toBe('/images/products/snack.png');
    expect(resolveProductImage(keoByName)).toBe('/images/products/keo.png');
  });

  it('preserves valid custom image URLs not matching dummy placeholder domain', () => {
    const customProduct: Product = {
      productId: 'prod-special',
      name: 'Nước ép cam nguyên chất',
      description: null,
      imageUrl: 'https://cdn.freshvend.vn/assets/orange-juice.png',
      priceVnd: 25000,
      slotNumber: 4,
      stockQuantity: 2,
      available: true,
    };

    expect(resolveProductImage(customProduct)).toBe('https://cdn.freshvend.vn/assets/orange-juice.png');
  });

  it('returns null gracefully for products without match or image', () => {
    const unknownProduct: Product = {
      productId: 'unknown-uuid',
      name: 'Sản phẩm thử nghiệm X',
      description: null,
      imageUrl: null,
      priceVnd: 20000,
      slotNumber: 4,
      stockQuantity: 1,
      available: true,
    };

    expect(resolveProductImage(unknownProduct)).toBeNull();
  });
});

