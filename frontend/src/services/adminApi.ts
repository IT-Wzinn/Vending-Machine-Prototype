import { ApiError } from '../types/api';
import type { ApiErrorResponse } from '../types/api';
import type {
  AdminLoginRequest,
  AdminLoginResponse,
  Page,
  ReconciliationOrderSummary,
  ReconciliationOrderDetail,
  ReconciliationDecisionRequest,
  ReconciliationDecisionResponse,
} from '../types/admin';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

// In-memory token storage (NEVER in localStorage/sessionStorage)
let inMemoryToken: string | null = null;
let unauthorizedHandler: (() => void) | null = null;

export function setInMemoryToken(token: string | null): void {
  inMemoryToken = token;
}

export function getInMemoryToken(): string | null {
  return inMemoryToken;
}

export function registerUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
}

/**
 * Core admin request helper with credentials: 'include', CSRF protection, and error mapping
 */
async function adminRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const url = `${API_BASE_URL}${endpoint}`;
  const method = (options.method || 'GET').toUpperCase();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  };

  // Add Bearer token header if present in memory (for cross-origin or non-cookie clients)
  if (inMemoryToken) {
    headers['Authorization'] = `Bearer ${inMemoryToken}`;
  }

  // Security Rule: Send X-Requested-With for state-changing HTTP methods
  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    headers['X-Requested-With'] = 'XMLHttpRequest';
  }

  try {
    const response = await fetch(url, {
      ...options,
      headers,
      credentials: 'include', // Always send HttpOnly cookies cross-origin/same-origin
    });

    if (!response.ok) {
      // 401 Unauthorized -> Trigger session invalidation callback
      if (response.status === 401) {
        if (unauthorizedHandler) {
          unauthorizedHandler();
        }
      }

      let errorCode = `HTTP_${response.status}`;
      let errorMessage = `Yêu cầu thất bại với mã lỗi ${response.status}`;
      let requestId: string | undefined;

      try {
        const errorBody: ApiErrorResponse = await response.json();
        if (errorBody && errorBody.error) {
          errorCode = errorBody.error.code || errorCode;
          errorMessage = errorBody.error.message || errorMessage;
          requestId = errorBody.error.requestId;
        }
      } catch {
        if (response.status === 401) {
          errorCode = 'UNAUTHORIZED';
          errorMessage = 'Phiên làm việc đã hết hạn hoặc thông tin đăng nhập không hợp lệ. Vui lòng đăng nhập lại.';
        } else if (response.status === 403) {
          errorCode = 'FORBIDDEN';
          errorMessage = 'Yêu cầu bị từ chối do thiếu header bảo mật hoặc không đủ quyền truy cập.';
        } else if (response.status === 404) {
          errorCode = 'NOT_FOUND';
          errorMessage = 'Không tìm thấy tài nguyên yêu cầu.';
        } else if (response.status === 409) {
          errorCode = 'CONFLICT';
          errorMessage = 'Xung đột dữ liệu hoặc thao tác trùng lặp.';
        } else if (response.status === 500) {
          errorCode = 'INTERNAL_SERVER_ERROR';
          errorMessage = 'Lỗi máy chủ nội bộ. Vui lòng liên hệ quản trị viên.';
        }
      }

      throw new ApiError(errorMessage, errorCode, response.status, requestId);
    }

    // Direct JSON response parsing matching backend ResponseEntities
    const data: T = await response.json();
    return data;
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
 * Admin Authentication API
 * POST /api/v1/admin/auth/login
 */
export async function loginAdmin(data: AdminLoginRequest): Promise<AdminLoginResponse> {
  const result = await adminRequest<AdminLoginResponse>('/api/v1/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify(data),
  });

  if (result && result.token) {
    setInMemoryToken(result.token);
  }

  return result;
}

/**
 * Fetch paginated reconciliation orders list
 * GET /api/v1/admin/reconciliation/orders
 */
export async function fetchReconciliationOrders(params: {
  page?: number;
  size?: number;
  status?: string;
  orderCode?: string;
} = {}): Promise<Page<ReconciliationOrderSummary>> {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set('page', String(params.page));
  if (params.size !== undefined) searchParams.set('size', String(params.size));
  if (params.status && params.status.trim() !== '') searchParams.set('status', params.status.trim());
  if (params.orderCode && params.orderCode.trim() !== '') searchParams.set('orderCode', params.orderCode.trim());

  const queryString = searchParams.toString();
  const endpoint = `/api/v1/admin/reconciliation/orders${queryString ? `?${queryString}` : ''}`;

  return adminRequest<Page<ReconciliationOrderSummary>>(endpoint);
}

/**
 * Fetch detailed order reconciliation information
 * GET /api/v1/admin/reconciliation/orders/{orderId}
 */
export async function fetchReconciliationOrderDetail(orderId: string): Promise<ReconciliationOrderDetail> {
  return adminRequest<ReconciliationOrderDetail>(`/api/v1/admin/reconciliation/orders/${encodeURIComponent(orderId)}`);
}

/**
 * Submit reconciliation administrative decision
 * POST /api/v1/admin/orders/{orderId}/reconciliation/decisions
 */
export async function submitReconciliationDecision(
  orderId: string,
  decision: ReconciliationDecisionRequest
): Promise<ReconciliationDecisionResponse> {
  return adminRequest<ReconciliationDecisionResponse>(
    `/api/v1/admin/orders/${encodeURIComponent(orderId)}/reconciliation/decisions`,
    {
      method: 'POST',
      body: JSON.stringify(decision),
    }
  );
}

export { API_BASE_URL };
