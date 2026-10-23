import React, { useState } from 'react';
import type {
  ReconciliationDecisionType,
  ReconciliationDecisionRequest,
  ReconciliationDecisionResponse,
  OrderStatus,
} from '../../types/admin';
import { submitReconciliationDecision } from '../../services/adminApi';
import {
  X,
  AlertTriangle,
  RotateCcw,
  CheckCircle,
  FileText,
  ShieldAlert,
  HelpCircle,
  AlertCircle,
  ArrowRight,
  ShieldCheck,
  Lock,
} from 'lucide-react';

// Protected dev order ID from prompt specifications
const PROTECTED_DEV_ORDER_ID = '4648baae-0c1d-44b5-8974-d09ba3363c05';

interface ReconciliationDecisionModalProps {
  orderId: string;
  publicOrderCode: string;
  currentStatus: OrderStatus;
  isReservationHeld?: boolean;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (response: ReconciliationDecisionResponse) => void;
}

export const ReconciliationDecisionModal: React.FC<ReconciliationDecisionModalProps> = ({
  orderId,
  publicOrderCode,
  currentStatus,
  isReservationHeld,
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [decision, setDecision] = useState<ReconciliationDecisionType>('INVESTIGATION_NOTE');
  const [reason, setReason] = useState<string>('');
  const [confirmationReference, setConfirmationReference] = useState<string>('');

  const [step, setStep] = useState<'FORM' | 'CONFIRM'>('FORM');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [validationErrors, setValidationErrors] = useState<{
    reason?: string;
    confirmationReference?: string;
  }>({});

  if (!isOpen) return null;

  const isTerminal = currentStatus === 'COMPLETED' || currentStatus === 'CANCELLED';
  const isProtectedOrder = orderId.toLowerCase() === PROTECTED_DEV_ORDER_ID.toLowerCase();

  const handleValidateForm = () => {
    const errors: { reason?: string; confirmationReference?: string } = {};

    if (!reason.trim()) {
      errors.reason = 'Vui lòng nhập lý do hoặc nội dung đối soát.';
    }

    if (decision === 'REFUND_CONFIRMED' && !confirmationReference.trim()) {
      errors.confirmationReference =
        'Mã tham chiếu hoàn tiền (mã giao dịch ngân hàng / UNC) là bắt buộc đối với quyết định REFUND_CONFIRMED.';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleProceedToConfirm = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (isProtectedOrder) {
      setErrorMessage(
        'Đơn hàng này thuộc danh sách dữ liệu được bảo vệ nghiêm ngặt trong môi trường dev. Không thể thực hiện quyết định đối soát!'
      );
      return;
    }

    if (isTerminal) {
      setErrorMessage(
        'Đơn hàng đã ở trạng thái kết thúc (COMPLETED hoặc CANCELLED). Không thể thay đổi quyết định đối soát.'
      );
      return;
    }

    if (handleValidateForm()) {
      setStep('CONFIRM');
    }
  };

  const handleExecuteDecision = async () => {
    if (isSubmitting) return; // Prevent double-submit

    setIsSubmitting(true);
    setErrorMessage(null);

    try {
      const payload: ReconciliationDecisionRequest = {
        decision,
        reason: reason.trim(),
        confirmationReference:
          decision === 'REFUND_CONFIRMED' ? confirmationReference.trim() : undefined,
      };

      const response = await submitReconciliationDecision(orderId, payload);
      setIsSubmitting(false);
      onSuccess(response);
      onClose();
    } catch (err: unknown) {
      setIsSubmitting(false);
      const msg =
        err instanceof Error
          ? err.message
          : 'Không thể ghi nhận quyết định đối soát. Vui lòng thử lại.';
      setErrorMessage(msg);
      // Stay in CONFIRM or return to FORM to display error
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-brand-text/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl max-w-xl w-full shadow-floating border border-brand-border overflow-hidden my-8 animate-in fade-in zoom-in-95 duration-150">
        {/* Modal Header */}
        <div className="px-6 py-4 bg-brand-primary text-white flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <ShieldCheck className="w-5 h-5 text-brand-accent" />
            <div>
              <h3 className="font-extrabold text-base sm:text-lg">
                Quyết định đối soát đơn hàng
              </h3>
              <p className="text-xs text-brand-accent/90 font-mono">
                Mã đơn: {publicOrderCode}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="p-1 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Protected Order Notice if applicable */}
        {isProtectedOrder && (
          <div className="p-4 bg-rose-50 border-b border-rose-200 text-rose-800 flex items-start gap-3">
            <Lock className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
            <div className="text-xs leading-relaxed">
              <strong className="font-bold">ĐƠN HÀNG BẢO VỆ DEV:</strong> Đơn này được đánh dấu
              bảo vệ trong quá trình kiểm thử phần mềm. Mọi hành vi thay đổi trạng thái đối soát trên
              database đều bị vô hiệu hóa để bảo vệ tính toàn vẹn dữ liệu.
            </div>
          </div>
        )}

        {/* Terminal Order Warning if applicable */}
        {!isProtectedOrder && isTerminal && (
          <div className="p-4 bg-amber-50 border-b border-amber-200 text-amber-800 flex items-start gap-3">
            <AlertCircle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
            <div className="text-xs leading-relaxed">
              <strong className="font-bold">ĐƠN HÀNG ĐÃ Ở TRẠNG THÁI KẾT THÚC:</strong> Đơn đã đạt
              trạng thái <span className="font-mono font-bold">{currentStatus}</span>. Backend sẽ từ
              chối mọi quyết định thay đổi trạng thái tiếp theo.
            </div>
          </div>
        )}

        {/* Error Alert */}
        {errorMessage && (
          <div className="p-4 mx-6 mt-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 flex items-start gap-2.5">
            <AlertTriangle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
            <div className="text-xs">
              <p className="font-bold">Không thể xử lý yêu cầu</p>
              <p className="mt-0.5">{errorMessage}</p>
            </div>
          </div>
        )}

        {step === 'FORM' ? (
          /* STEP 1: FORM SELECTION */
          <form onSubmit={handleProceedToConfirm} className="p-6 space-y-5">
            {/* Decision Types Tabs/Radio */}
            <div>
              <label className="block text-xs font-bold text-brand-text uppercase tracking-wider mb-2">
                Chọn hình thức xử lý đối soát
              </label>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                {/* 1. INVESTIGATION_NOTE */}
                <button
                  type="button"
                  onClick={() => {
                    setDecision('INVESTIGATION_NOTE');
                    setValidationErrors({});
                  }}
                  className={`p-3 text-left rounded-xl border text-xs transition-all ${
                    decision === 'INVESTIGATION_NOTE'
                      ? 'border-brand-primary bg-brand-primary/5 ring-1 ring-brand-primary text-brand-primary font-bold'
                      : 'border-brand-border bg-white text-brand-text hover:bg-brand-bg'
                  }`}
                >
                  <div className="flex items-center gap-2 mb-1">
                    <FileText className="w-4 h-4 text-brand-primary shrink-0" />
                    <span className="font-bold">Ghi chú tra soát</span>
                  </div>
                  <p className="text-[11px] text-brand-text-muted font-normal">
                    Lưu nhật ký điều tra, không đổi trạng thái đơn
                  </p>
                </button>

                {/* 2. MANUAL_DISPENSE_APPROVED */}
                <button
                  type="button"
                  onClick={() => {
                    setDecision('MANUAL_DISPENSE_APPROVED');
                    setValidationErrors({});
                  }}
                  className={`p-3 text-left rounded-xl border text-xs transition-all ${
                    decision === 'MANUAL_DISPENSE_APPROVED'
                      ? 'border-emerald-600 bg-emerald-50 ring-1 ring-emerald-600 text-emerald-900 font-bold'
                      : 'border-brand-border bg-white text-brand-text hover:bg-brand-bg'
                  }`}
                >
                  <div className="flex items-center gap-2 mb-1">
                    <CheckCircle className="w-4 h-4 text-emerald-600 shrink-0" />
                    <span className="font-bold">Duyệt xuất thủ công</span>
                  </div>
                  <p className="text-[11px] text-brand-text-muted font-normal">
                    Hoàn tất đơn do nhân viên đã xử lý giao hàng
                  </p>
                </button>

                {/* 3. REFUND_PENDING */}
                <button
                  type="button"
                  onClick={() => {
                    setDecision('REFUND_PENDING');
                    setValidationErrors({});
                  }}
                  className={`p-3 text-left rounded-xl border text-xs transition-all ${
                    decision === 'REFUND_PENDING'
                      ? 'border-amber-600 bg-amber-50 ring-1 ring-amber-600 text-amber-900 font-bold'
                      : 'border-brand-border bg-white text-brand-text hover:bg-brand-bg'
                  }`}
                >
                  <div className="flex items-center gap-2 mb-1">
                    <RotateCcw className="w-4 h-4 text-amber-600 shrink-0" />
                    <span className="font-bold">Chờ hoàn tiền</span>
                  </div>
                  <p className="text-[11px] text-brand-text-muted font-normal">
                    Hủy đơn, nhả giữ chỗ kho, chờ chuyển khoản lại
                  </p>
                </button>

                {/* 4. REFUND_CONFIRMED */}
                <button
                  type="button"
                  onClick={() => {
                    setDecision('REFUND_CONFIRMED');
                    setValidationErrors({});
                  }}
                  className={`p-3 text-left rounded-xl border text-xs transition-all ${
                    decision === 'REFUND_CONFIRMED'
                      ? 'border-blue-600 bg-blue-50 ring-1 ring-blue-600 text-blue-900 font-bold'
                      : 'border-brand-border bg-white text-brand-text hover:bg-brand-bg'
                  }`}
                >
                  <div className="flex items-center gap-2 mb-1">
                    <ShieldAlert className="w-4 h-4 text-blue-600 shrink-0" />
                    <span className="font-bold">Đã hoàn tiền (Khai báo)</span>
                  </div>
                  <p className="text-[11px] text-brand-text-muted font-normal">
                    Xác nhận mã GD hoàn tiền đã gửi cho khách
                  </p>
                </button>
              </div>
            </div>

            {/* Decision Warning / Disclaimers Block */}
            <div className="rounded-xl p-3.5 bg-brand-bg border border-brand-border text-xs leading-relaxed space-y-1.5">
              {decision === 'REFUND_CONFIRMED' && (
                <div className="text-blue-900 flex items-start gap-2">
                  <AlertTriangle className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
                  <div>
                    <strong className="font-bold">CẢNH BÁO QUAN TRỌNG:</strong> Quyết định{' '}
                    <code className="font-mono font-bold">REFUND_CONFIRMED</code> là thông tin xác
                    nhận do admin tự khai báo, <em>không phải bằng chứng backend đã tự xác minh hoàn tiền ngân hàng</em>.
                    Admin chịu trách nhiệm đối chiếu sao kê ngân hàng thật.
                  </div>
                </div>
              )}

              {decision === 'REFUND_PENDING' && (
                <div className="text-amber-900 flex items-start gap-2">
                  <AlertCircle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                  <div>
                    <strong className="font-bold">LƯU Ý NGHIỆP VỤ:</strong> Quyết định này chỉ ghi
                    nhận đơn hàng ở trạng thái chờ hoàn tiền và giải phóng số lượng giữ chỗ kho. Thao
                    tác này <strong>không được xem là đã hoàn tiền thành công</strong>.
                  </div>
                </div>
              )}

              {decision === 'MANUAL_DISPENSE_APPROVED' && (
                <div className="text-emerald-900 flex items-start gap-2">
                  <AlertCircle className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                  <div>
                    <strong className="font-bold">LƯU Ý THỰC HIỆN:</strong> Thao tác này{' '}
                    <strong>chỉ là ghi nhận quyết định quản trị</strong>, không gửi lệnh phần cứng và{' '}
                    <strong>không thông báo rằng máy đã nhả hàng</strong>. Nhân viên kỹ thuật phải
                    trực tiếp trao sản phẩm cho khách.
                  </div>
                </div>
              )}

              {decision === 'INVESTIGATION_NOTE' && (
                <div className="text-brand-text flex items-start gap-2">
                  <HelpCircle className="w-4 h-4 text-brand-primary shrink-0 mt-0.5" />
                  <div>
                    <strong className="font-bold">GHI CHÚ KIỂM TOÁN:</strong> Ghi lại thông tin liên
                    hệ khách hàng, tình trạng phần cứng hoặc nhật ký kiểm tra. Không tác động đến
                    trạng thái đơn hàng hay tồn kho máy.
                  </div>
                </div>
              )}
            </div>

            {/* Confirmation Reference field (ONLY for REFUND_CONFIRMED) */}
            {decision === 'REFUND_CONFIRMED' && (
              <div>
                <label
                  htmlFor="confirm-reference"
                  className="block text-xs font-bold text-brand-text mb-1"
                >
                  Mã tham chiếu hoàn tiền (Ngân hàng / UNC) <span className="text-rose-600">*</span>
                </label>
                <input
                  id="confirm-reference"
                  type="text"
                  value={confirmationReference}
                  onChange={(e) => {
                    setConfirmationReference(e.target.value);
                    if (validationErrors.confirmationReference) {
                      setValidationErrors((prev) => ({ ...prev, confirmationReference: undefined }));
                    }
                  }}
                  placeholder="Ví dụ: FT26102299887766 hoặc UNC-00123..."
                  className={`w-full px-3.5 py-2 text-sm bg-brand-bg border rounded-xl font-mono text-brand-text placeholder-brand-text-subtle focus:outline-none focus:ring-2 focus:ring-brand-primary transition-all ${
                    validationErrors.confirmationReference
                      ? 'border-rose-400 bg-rose-50/30'
                      : 'border-brand-border'
                  }`}
                />
                {validationErrors.confirmationReference && (
                  <p className="mt-1 text-xs text-rose-600">
                    {validationErrors.confirmationReference}
                  </p>
                )}
              </div>
            )}

            {/* Reason Textarea (Required for all) */}
            <div>
              <label htmlFor="decision-reason" className="block text-xs font-bold text-brand-text mb-1">
                Lý do / Nội dung đối soát <span className="text-rose-600">*</span>
              </label>
              <textarea
                id="decision-reason"
                rows={3}
                value={reason}
                onChange={(e) => {
                  setReason(e.target.value);
                  if (validationErrors.reason) {
                    setValidationErrors((prev) => ({ ...prev, reason: undefined }));
                  }
                }}
                placeholder="Nhập chi tiết căn cứ đối soát, kết quả làm việc với khách hàng hoặc kỹ thuật viên..."
                className={`w-full px-3.5 py-2 text-sm bg-brand-bg border rounded-xl text-brand-text placeholder-brand-text-subtle focus:outline-none focus:ring-2 focus:ring-brand-primary transition-all ${
                  validationErrors.reason ? 'border-rose-400 bg-rose-50/30' : 'border-brand-border'
                }`}
              />
              {validationErrors.reason && (
                <p className="mt-1 text-xs text-rose-600">{validationErrors.reason}</p>
              )}
            </div>

            {/* Action Buttons */}
            <div className="pt-2 flex items-center justify-end gap-3 border-t border-brand-border">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2.5 text-xs font-semibold rounded-xl text-brand-text bg-brand-bg border border-brand-border hover:bg-white transition-colors"
              >
                Hủy bỏ
              </button>
              <button
                type="submit"
                disabled={isTerminal || isProtectedOrder}
                className="inline-flex items-center gap-1.5 px-5 py-2.5 text-xs font-bold rounded-xl text-white bg-brand-primary hover:bg-brand-primary-hover transition-colors shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <span>Xác nhận thông tin</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </form>
        ) : (
          /* STEP 2: FINAL CONFIRMATION BEFORE SUBMIT */
          <div className="p-6 space-y-5">
            <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-amber-900">
              <h4 className="font-bold text-sm flex items-center gap-2 mb-2">
                <AlertTriangle className="w-4 h-4 text-amber-700" />
                <span>Xác nhận thực hiện quyết định đối soát</span>
              </h4>
              <p className="text-xs leading-relaxed text-amber-800">
                Vui lòng kiểm tra kỹ thông tin trước khi gửi. Quyết định sẽ được ghi lại vào nhật ký
                kiểm toán hệ thống và thay đổi trạng thái đơn hàng (nếu có).
              </p>
            </div>

            <div className="bg-brand-bg rounded-xl p-4 border border-brand-border space-y-2 text-xs">
              <div className="flex justify-between py-1 border-b border-brand-border/60">
                <span className="text-brand-text-muted">Mã đơn hàng:</span>
                <span className="font-mono font-bold text-brand-text">{publicOrderCode}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-brand-border/60">
                <span className="text-brand-text-muted">Hành động:</span>
                <span className="font-mono font-extrabold text-brand-primary">{decision}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-brand-border/60">
                <span className="text-brand-text-muted">Giữ chỗ kho hiện tại:</span>
                <span className="font-semibold text-brand-text">
                  {isReservationHeld === true
                    ? 'Đang giữ chỗ vật lý (1)'
                    : isReservationHeld === false
                    ? 'Đã nhả giữ chỗ (0)'
                    : 'Chưa xác định'}
                </span>
              </div>
              {decision === 'REFUND_CONFIRMED' && (
                <div className="flex justify-between py-1 border-b border-brand-border/60">
                  <span className="text-brand-text-muted">Mã tham chiếu hoàn tiền:</span>
                  <span className="font-mono font-bold text-blue-700">{confirmationReference}</span>
                </div>
              )}
              <div className="py-1">
                <span className="text-brand-text-muted block mb-1">Lý do:</span>
                <p className="text-brand-text font-medium bg-white p-2.5 rounded-lg border border-brand-border">
                  {reason}
                </p>
              </div>
            </div>

            {/* Double-submit and Confirmation Buttons */}
            <div className="pt-2 flex items-center justify-end gap-3 border-t border-brand-border">
              <button
                type="button"
                onClick={() => setStep('FORM')}
                disabled={isSubmitting}
                className="px-4 py-2.5 text-xs font-semibold rounded-xl text-brand-text bg-brand-bg border border-brand-border hover:bg-white transition-colors disabled:opacity-50"
              >
                Quay lại sửa
              </button>
              <button
                type="button"
                onClick={handleExecuteDecision}
                disabled={isSubmitting}
                className="inline-flex items-center gap-2 px-5 py-2.5 text-xs font-bold rounded-xl text-white bg-rose-600 hover:bg-rose-700 transition-colors shadow-sm disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {isSubmitting ? (
                  <>
                    <span className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    <span>Đang gửi quyết định...</span>
                  </>
                ) : (
                  <span>Gửi quyết định đối soát</span>
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
