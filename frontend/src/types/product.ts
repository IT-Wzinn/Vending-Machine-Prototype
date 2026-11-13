export interface Product {
  productId: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  priceVnd: number;
  slotNumber: number;
  stockQuantity: number;
  reservedQuantity?: number;
  availableStock?: number;
  available: boolean;
}

export interface ProductCatalogResponse {
  machineId: string;
  products: Product[];
}

/**
 * Resolves the purchasable inventory for a product across all customer-facing components.
 * Authoritative precedence:
 * 1. Explicit `availableStock` if provided by backend API.
 * 2. Fallback to `Math.max(0, stockQuantity - (reservedQuantity ?? 0))` if availableStock is undefined.
 */
export function getAvailableStock(product: {
  stockQuantity: number;
  reservedQuantity?: number;
  availableStock?: number;
}): number {
  if (product.availableStock !== undefined && product.availableStock !== null) {
    return Math.max(0, product.availableStock);
  }
  return Math.max(0, (product.stockQuantity ?? 0) - (product.reservedQuantity ?? 0));
}

