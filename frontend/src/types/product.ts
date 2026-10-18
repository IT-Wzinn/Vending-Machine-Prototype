export interface Product {
  productId: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  priceVnd: number;
  slotNumber: number;
  stockQuantity: number;
  available: boolean;
}

export interface ProductCatalogResponse {
  machineId: string;
  products: Product[];
}
