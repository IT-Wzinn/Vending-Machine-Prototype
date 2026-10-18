export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'DISPENSING'
  | 'COMPLETED'
  | 'DISPENSE_FAILED'
  | 'NEEDS_RECONCILIATION'
  | 'EXPIRED'
  | 'CANCELLED';

export interface OrderProduct {
  productId: string;
  name: string;
  priceVnd: number;
}

export interface OrderPayment {
  transferReference: string;
  paymentQrData: string;
  bankAccountNumber: string;
  bankAccountName: string;
  bankName: string;
  amountVnd: number;
  paymentExpiresAt: string;
  paidAt: string | null;
}

export interface OrderDispensing {
  startedAt: string | null;
  completedAt: string | null;
  result: 'SUCCESS' | 'FAILURE' | null;
  verificationLevel: 'MOTOR_CYCLE_ONLY' | 'SENSOR_CONFIRMED' | null;
  message: string | null;
}

export interface Order {
  orderId: string;
  publicOrderCode: string;
  machineId: string;
  product: OrderProduct;
  quantity: number;
  totalAmountVnd: number;
  status: OrderStatus;
  payment: OrderPayment;
  dispensing?: OrderDispensing;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateOrderRequest {
  machineId: string;
  productId: string;
  quantity: number;
}

export interface OrderStatusResponse {
  orderId: string;
  status: OrderStatus;
  updatedAt: string;
  message: string | null;
}
