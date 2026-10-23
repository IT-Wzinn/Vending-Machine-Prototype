// Types for Admin Authentication and Reconciliation Dashboard

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'DISPENSING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'DISPENSE_FAILED'
  | 'NEEDS_RECONCILIATION';

export interface AdminUser {
  username: string;
}

export interface AdminLoginRequest {
  username: string;
  password: string;
}

export interface AdminLoginResponse {
  username: string;
  token: string;
  expiresInSeconds: number;
  message: string;
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface ReconciliationOrderSummary {
  orderId: string;
  publicOrderCode: string;
  status: OrderStatus;
  machineCode?: string;
  productName?: string;
  slotNumber?: number;
  quantity?: number;
  totalAmountVnd: number;
  transferReference?: string;
  paidAt?: string;
  createdAt: string;
  updatedAt?: string;
  hasDispenseCommand: boolean;
  latestDispenseStatus?: string;
  paymentVerified: boolean;
  isReservationHeld?: boolean;
}

export interface ReconciliationPaymentTx {
  transactionId: string;
  providerTransactionId: string;
  amountVnd: number;
  transferReference?: string;
  verificationStatus: string;
  transactionStatus: string;
  receivedAt: string;
  createdAt: string;
}

export interface ReconciliationDispenseCmd {
  commandId: string;
  slotNumber: number;
  status: string;
  attemptCount: number;
  failureCode?: string;
  failureMessage?: string;
  publishedAt?: string;
  acknowledgedAt?: string;
  finishedAt?: string;
  createdAt: string;
}

export interface ReconciliationDeviceEvent {
  eventId: string;
  commandId: string;
  eventType: string;
  slotNumber: number;
  createdAt: string;
}

export interface ReconciliationAuditRecord {
  auditId: string;
  adminUsername: string;
  action: string;
  previousStatus: OrderStatus;
  newStatus: OrderStatus;
  reason: string;
  confirmationReference?: string;
  createdAt: string;
}

export interface ReconciliationOrderDetail {
  orderId: string;
  publicOrderCode: string;
  status: OrderStatus;
  totalAmountVnd: number;
  quantity: number;
  transferReference?: string;
  paymentExpiresAt?: string;
  paidAt?: string;
  dispensingStartedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt?: string;
  isReservationHeld?: boolean;
  machineId?: string;
  machineCode?: string;
  machineName?: string;
  productId?: string;
  productName?: string;
  slotNumber?: number;
  currentStockQuantity?: number;
  currentReservedQuantity?: number;
  unitPriceVnd?: number;
  paymentTransactions: ReconciliationPaymentTx[];
  dispenseCommands: ReconciliationDispenseCmd[];
  deviceEvents: ReconciliationDeviceEvent[];
  auditLogs: ReconciliationAuditRecord[];
}

export type ReconciliationDecisionType =
  | 'REFUND_PENDING'
  | 'REFUND_CONFIRMED'
  | 'MANUAL_DISPENSE_APPROVED'
  | 'INVESTIGATION_NOTE';

export interface ReconciliationDecisionRequest {
  decision: ReconciliationDecisionType;
  reason: string;
  confirmationReference?: string;
}

export interface ReconciliationDecisionResponse {
  auditId: string;
  orderId: string;
  publicOrderCode: string;
  action: string;
  previousStatus: OrderStatus;
  newStatus: OrderStatus;
  reason: string;
  confirmationReference?: string;
  reservationReleased: boolean;
  recordedAt: string;
  message: string;
}
