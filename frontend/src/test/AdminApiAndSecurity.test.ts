import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  loginAdmin,
  fetchReconciliationOrders,
  submitReconciliationDecision,
  setInMemoryToken,
  getInMemoryToken,
  registerUnauthorizedHandler,
} from '../services/adminApi';

describe('Admin API Security & Request Contract', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    setInMemoryToken(null);
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('verifies zero passwords or tokens in localStorage or sessionStorage', async () => {
    const mockResponse = {
      username: 'admin',
      token: 'jwt-secret-token-12345',
      expiresInSeconds: 3600,
      message: 'Login successful',
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockResponse,
    });

    await loginAdmin({ username: 'admin', password: 'SuperSecretPassword123!' });

    // Verify token is in memory
    expect(getInMemoryToken()).toBe('jwt-secret-token-12345');

    // STRICT CHECK: Nothing sensitive stored in browser storage
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('password')).toBeNull();
    expect(localStorage.getItem('admin_token')).toBeNull();
    expect(sessionStorage.getItem('token')).toBeNull();
    expect(sessionStorage.getItem('password')).toBeNull();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('attaches X-Requested-With: XMLHttpRequest on mutating POST requests (CSRF protection)', async () => {
    let capturedHeaders: HeadersInit | undefined;
    let capturedCredentials: RequestCredentials | undefined;

    globalThis.fetch = vi.fn().mockImplementation((_url, init) => {
      capturedHeaders = init?.headers;
      capturedCredentials = init?.credentials;
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({
          auditId: 'audit-1',
          orderId: 'order-1',
          publicOrderCode: 'VM-001',
          action: 'INVESTIGATION_NOTE',
          previousStatus: 'NEEDS_RECONCILIATION',
          currentStatus: 'NEEDS_RECONCILIATION',
          newStatus: 'NEEDS_RECONCILIATION',
          reason: 'Checked machine log',
          reservationReleased: false,
          processedAt: new Date().toISOString(),
          recordedAt: new Date().toISOString(),
          message: 'Success',
        }),
      });
    });

    await submitReconciliationDecision('order-1', {
      decision: 'INVESTIGATION_NOTE',
      reason: 'Checked machine log',
    });

    expect(capturedCredentials).toBe('include');
    expect((capturedHeaders as Record<string, string>)['X-Requested-With']).toBe('XMLHttpRequest');
  });

  it('does NOT attach X-Requested-With on GET requests', async () => {
    let capturedHeaders: HeadersInit | undefined;

    globalThis.fetch = vi.fn().mockImplementation((_url, init) => {
      capturedHeaders = init?.headers;
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({
          content: [],
          totalPages: 0,
          totalElements: 0,
          size: 10,
          number: 0,
          first: true,
          last: true,
          empty: true,
        }),
      });
    });

    await fetchReconciliationOrders({ page: 0, size: 10 });

    expect((capturedHeaders as Record<string, string>)['X-Requested-With']).toBeUndefined();
  });

  it('triggers unauthorizedHandler on 401 response and maps error message', async () => {
    const unauthorizedCallback = vi.fn();
    registerUnauthorizedHandler(unauthorizedCallback);

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({
        error: {
          code: 'UNAUTHORIZED',
          message: 'Phiên làm việc đã hết hạn.',
        },
      }),
    });

    await expect(fetchReconciliationOrders()).rejects.toThrow('Phiên làm việc đã hết hạn.');
    expect(unauthorizedCallback).toHaveBeenCalledTimes(1);
  });

  it('handles 403 Forbidden with proper descriptive message', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 403,
      json: async () => ({
        error: {
          code: 'CSRF_HEADER_MISSING',
          message: 'Thiếu header bảo mật CSRF: X-Requested-With: XMLHttpRequest',
        },
      }),
    });

    await expect(
      submitReconciliationDecision('order-1', {
        decision: 'INVESTIGATION_NOTE',
        reason: 'Note',
      })
    ).rejects.toThrow('Thiếu header bảo mật CSRF: X-Requested-With: XMLHttpRequest');
  });

  it('handles 409 Conflict with descriptive message', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({
        error: {
          code: 'ORDER_TERMINAL_STATE',
          message: 'Đơn hàng đã ở trạng thái kết thúc (COMPLETED hoặc CANCELLED). Không thể thay đổi quyết định đối soát.',
        },
      }),
    });

    await expect(
      submitReconciliationDecision('order-1', {
        decision: 'REFUND_PENDING',
        reason: 'Refund',
      })
    ).rejects.toThrow('Đơn hàng đã ở trạng thái kết thúc');
  });

  it('handles network failure with friendly NETWORK_ERROR ApiError', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(fetchReconciliationOrders()).rejects.toThrow(
      'Không thể kết nối đến máy chủ backend.'
    );
  });
});
