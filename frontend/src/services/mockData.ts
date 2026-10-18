import type { MachineResponse } from '../types/machine';
import type { Product } from '../types/product';

/**
 * ISOLATED MOCK DATA
 * Chú ý: Dữ liệu này CHỈ phục vụ mục đích kiểm thử tĩnh (unit test/storybook) hoặc tham chiếu giao diện khi phát triển.
 * Tuyệt đối KHÔNG tự ý fallback âm thầm sang mock data khi API backend gặp sự cố.
 */

export const MOCK_MACHINE_VM001: MachineResponse = {
  machineId: 'VM001',
  name: 'Máy bán hàng tầng 1 (Dữ liệu MOCK)',
  status: 'ACTIVE',
  message: null,
};

export const MOCK_MACHINE_INACTIVE: MachineResponse = {
  machineId: 'VM999',
  name: 'Máy bảo trì (Dữ liệu MOCK)',
  status: 'INACTIVE',
  message: 'Máy đang bảo trì định kỳ',
};

export const MOCK_PRODUCTS_VM001: Product[] = [
  {
    productId: 'mock-0000-0000-0001',
    name: 'Nước suối Aqua 500ml (MOCK)',
    description: 'Nước uống tinh khiết chai 500ml',
    imageUrl: null,
    priceVnd: 8000,
    slotNumber: 1,
    stockQuantity: 5,
    available: true,
  },
  {
    productId: 'mock-0000-0000-0002',
    name: "Snack Lay's vị tự nhiên (MOCK)",
    description: 'Khoai tây chiên giòn rụm gói 54g',
    imageUrl: null,
    priceVnd: 15000,
    slotNumber: 2,
    stockQuantity: 3,
    available: true,
  },
  {
    productId: 'mock-0000-0000-0003',
    name: 'Kẹo cao su Doublemint (MOCK)',
    description: 'Hương bạc hà the mát',
    imageUrl: null,
    priceVnd: 5000,
    slotNumber: 3,
    stockQuantity: 0,
    available: false,
  },
];
