export interface ApiResponse<T> {
  data: T;
}

export interface ApiErrorDetail {
  code: string;
  message: string;
  timestamp: string;
  requestId: string;
}

export interface ApiErrorResponse {
  error: ApiErrorDetail;
}

export class ApiError extends Error {
  public code: string;
  public status: number;
  public requestId?: string;

  constructor(message: string, code: string, status: number, requestId?: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.requestId = requestId;
  }
}
