import type { Product } from '../types/product';

/**
 * Stable product-to-image mapping based on standard database seed UUIDs.
 */
const STABLE_PRODUCT_IMAGE_MAP: Record<string, string> = {
  // Slot 1: Nước suối Aqua 500ml
  '550e8400-e29b-41d4-a716-446655440001': '/images/products/aquafina.png',
  // Slot 2: Snack Lay's vị tự nhiên
  '550e8400-e29b-41d4-a716-446655440002': '/images/products/snack.png',
  // Slot 3: Kẹo cao su Doublemint
  '550e8400-e29b-41d4-a716-446655440003': '/images/products/keo.png',
};

/**
 * Resolves the display image path for a product.
 *
 * Mapping priority:
 * 1. If product.imageUrl is provided and is a valid non-placeholder URL, respect it.
 * 2. Match by stable database UUID (productId).
 * 3. Match by canonical product name keywords (Aquafina, Snack Lay's, Doublemint).
 * 4. Fallback to null (allowing the component to render the graceful fallback icon).
 */
export function resolveProductImage(product: Product): string | null {
  if (!product) return null;

  // 1. Explicit valid URL on product (ignore dummy placeholder domains like example.com)
  if (product.imageUrl && !product.imageUrl.includes('example.com')) {
    return product.imageUrl;
  }

  // 2. Stable identifier mapping (UUID)
  if (product.productId && STABLE_PRODUCT_IMAGE_MAP[product.productId]) {
    return STABLE_PRODUCT_IMAGE_MAP[product.productId];
  }

  // 3. Normalized keyword fallback for robust offline/test environments
  const normalized = (product.name || '').toLowerCase().trim();
  if (normalized.includes('aqua') || normalized.includes('nước suối') || normalized.includes('aquafina')) {
    return '/images/products/aquafina.png';
  }
  if (normalized.includes('snack') || normalized.includes('lay')) {
    return '/images/products/snack.png';
  }
  if (normalized.includes('kẹo') || normalized.includes('doublemint') || normalized.includes('gum')) {
    return '/images/products/keo.png';
  }

  return product.imageUrl || null;
}

