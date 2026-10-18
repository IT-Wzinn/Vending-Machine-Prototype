import type { ApiResponse, ApiErrorResponse } from '../types/api';
import { ApiError } from '../types/api';
import type { MachineResponse } from '../types/machine';
import type { ProductCatalogResponse } from '../types/product';
import type { Order, CreateOrderRequest, OrderStatusResponse } from '../types/order';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

/**
 * Standard fetch helper with centralized error handling and contract parsing
 */
async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE_URL}${endpoint}`;
  
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  };

  try {
    const response = await fetch(url, {
      ...options,
      headers,
    });

    if (!response.ok) {
      let errorCode = 'UNKNOWN_ERROR';
      let errorMessage = `Yêu cầu thất bại với mã lỗi HTTP ${response.status}`;
      let requestId: string | undefined;

      try {
        const errorBody: ApiErrorResponse = await response.json();
        if (errorBody && errorBody.error) {
          errorCode = errorBody.error.code || errorCode;
          errorMessage = errorBody.error.message || errorMessage;
          requestId = errorBody.error.requestId;
        }
      } catch {
        // Fallback for non-JSON response (e.g. 404 from raw proxy/server)
        if (response.status === 404) {
          errorCode = 'NOT_FOUND';
          errorMessage = 'Không tìm thấy tài nguyên hoặc endpoint chưa được hỗ trợ.';
        } else if (response.status === 500) {
          errorCode = 'INTERNAL_SERVER_ERROR';
          errorMessage = 'Lỗi máy chủ nội bộ. Vui lòng thử lại sau.';
        }
      }

      throw new ApiError(errorMessage, errorCode, response.status, requestId);
    }

    const json: ApiResponse<T> = await response.json();
    return json.data;
  } catch (err: unknown) {
    if (err instanceof ApiError) {
      throw err;
    }
    // Network / connectivity error (e.g., backend offline)
    throw new ApiError(
      'Không thể kết nối đến máy chủ backend. Vui lòng kiểm tra dịch vụ backend đang chạy.',
      'NETWORK_ERROR',
      0
    );
  }
}

/**
 * 2.1 GET /api/v1/machines/{machineId}
 * Check machine existence and status
 */
export async function getMachine(machineId: string): Promise<MachineResponse> {
  return request<MachineResponse>(`/api/v1/machines/${encodeURIComponent(machineId)}`);
}

/**
 * 2.2 GET /api/v1/machines/{machineId}/products
 * Fetch product catalog for specific vending machine
 */
export async function getProductCatalog(machineId: string): Promise<ProductCatalogResponse> {
  return request<ProductCatalogResponse>(`/api/v1/machines/${encodeURIComponent(machineId)}/products`);
}

/**
 * 3.1 POST /api/v1/orders
 * Attempt to create order (Implemented in Phase 4 Backend)
 */
export async function createOrder(data: CreateOrderRequest): Promise<Order> {
  return request<Order>('/api/v1/orders', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

/**
 * 3.2 GET /api/v1/orders/{orderId}
 * Fetch full order details
 */
export async function getOrder(orderId: string): Promise<Order> {
  return request<Order>(`/api/v1/orders/${encodeURIComponent(orderId)}`);
}

/**
 * 3.3 GET /api/v1/orders/{orderId}/status
 * Light polling endpoint for order status
 */
export async function getOrderStatus(orderId: string): Promise<OrderStatusResponse> {
  return request<OrderStatusResponse>(`/api/v1/orders/${encodeURIComponent(orderId)}/status`);
}

export { API_BASE_URL };
