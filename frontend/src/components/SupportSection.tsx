import React, { useState } from 'react';
import {
  HelpCircle,
  PhoneCall,
  MessageCircle,
  ChevronDown,
  QrCode,
  CreditCard,
  PackageCheck,
  ArrowLeft,
  AlertTriangle,
  Clock,
  Sparkles,
} from 'lucide-react';
import type { MachineResponse } from '../types/machine';

interface SupportSectionProps {
  machine?: MachineResponse | null;
  onBackToHome: () => void;
}

interface FaqItem {
  id: string;
  question: string;
  answer: string;
  category: string;
}

const FAQS: FaqItem[] = [
  {
    id: 'faq-1',
    category: 'Thanh toán',
    question: 'Tài khoản ngân hàng đã bị trừ tiền nhưng máy chưa nhả hàng thì phải làm sao?',
    answer:
      'Hệ thống FreshVend tích hợp SePay Webhook và giao thức MQTT HiveMQ kiểm tra xác nhận theo thời gian thực. Nếu máy gặp sự cố vật lý không thể nhả hàng sau khi thanh toán thành công, hệ thống sẽ tự động chuyển đơn sang trạng thái Cần đối soát (NEEDS_RECONCILIATION) và hoàn tiền về tài khoản ngân hàng của quý khách trong vòng 5-15 phút. Quý khách cũng có thể gọi hotline 1900 8888 để được hỗ trợ kiểm tra đối soát ngay lập tức.',
  },
  {
    id: 'faq-2',
    category: 'Vận hành',
    question: 'Sản phẩm bị kẹt trong lò xo xoay của máy thì giải quyết thế nào?',
    answer:
      'Tất cả các khoang hàng đều có cảm biến rơi hồng ngoại (Drop Sensor). Nếu cảm biến không nhận diện sản phẩm rơi xuống khay lấy hàng, máy sẽ tự động kích hoạt xoay bù 1/4 vòng lò xo. Nếu vẫn không thành công, lệnh xuất hàng sẽ được huỷ an toàn và tiền thanh toán sẽ được bảo lưu hoàn trả.',
  },
  {
    id: 'faq-3',
    category: 'Đơn hàng',
    question: 'Mã QR thanh toán VietQR có hiệu lực trong bao lâu?',
    answer:
      'Mỗi mã QR thanh toán có hiệu lực tối đa trong 5 phút kể từ thời điểm tạo. Quý khách nên hoàn tất chuyển khoản trước khi đồng hồ đếm ngược kết thúc để đảm bảo máy khoá giữ tồn kho chính xác.',
  },
  {
    id: 'faq-4',
    category: 'Hóa đơn & Phản hồi',
    question: 'Tôi muốn yêu cầu hóa đơn điện tử hoặc góp ý chất lượng dịch vụ?',
    answer:
      'Quý khách có thể gửi yêu cầu trực tiếp qua Zalo Official Account của FreshVend kèm theo Mã đơn hàng (Order Code) hoặc gọi hotline 1900 8888. Đội ngũ CSKH hỗ trợ 24/7 kể cả ngày lễ và chủ nhật.',
  },
];

export const SupportSection: React.FC<SupportSectionProps> = ({ machine, onBackToHome }) => {
  const [openFaq, setOpenFaq] = useState<string | null>('faq-1');

  const toggleFaq = (id: string) => {
    setOpenFaq((prev) => (prev === id ? null : id));
  };

  return (
    <div className="w-full max-w-4xl mx-auto space-y-6 sm:space-y-8 animate-fade-in">
      {/* Top back button and title header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <button
          type="button"
          onClick={onBackToHome}
          className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-white/80 hover:bg-white text-xs font-bold text-brand-primary border border-emerald-500/20 shadow-2xs backdrop-blur-md transition-all active:scale-95 cursor-pointer"
        >
          <ArrowLeft className="w-4 h-4 text-brand-primary" />
          <span>Quay lại đặt hàng</span>
        </button>

        {machine && (
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-950/70 text-brand-accent text-xs font-mono font-bold border border-emerald-400/20">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            <span>Mã máy: {machine.machineId}</span>
            <span className="text-white/60 font-sans font-normal hidden sm:inline">• {machine.name}</span>
          </div>
        )}
      </div>

      {/* Main Support Header Card */}
      <div className="rounded-3xl bg-white/95 backdrop-blur-md shadow-card border border-emerald-500/15 p-6 sm:p-8">
        <div className="flex items-center gap-3 mb-2">
          <div className="w-10 h-10 rounded-2xl bg-brand-primary/10 flex items-center justify-center text-brand-primary border border-emerald-500/20">
            <HelpCircle className="w-5 h-5 text-brand-primary" />
          </div>
          <div>
            <h2 className="text-xl sm:text-2xl font-black text-brand-primary tracking-tight">
              Trung tâm Hỗ trợ Khách hàng
            </h2>
            <p className="text-xs sm:text-sm text-brand-text-muted">
              Hướng dẫn đặt hàng, giải đáp thắc mắc và xử lý sự cố tức thì 24/7
            </p>
          </div>
        </div>

        {/* 3 Steps Guide */}
        <div className="mt-6 pt-6 border-t border-emerald-500/10">
          <h3 className="text-sm font-extrabold uppercase tracking-wider text-brand-primary mb-4 flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-emerald-600" />
            <span>Quy trình mua sắm 3 bước đơn giản</span>
          </h3>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Step 1 */}
            <div className="p-4 rounded-2xl bg-emerald-50/70 border border-emerald-500/15 flex flex-col gap-2.5">
              <div className="w-8 h-8 rounded-xl bg-brand-primary text-white flex items-center justify-center font-black text-sm">
                1
              </div>
              <div className="flex items-center gap-2 text-brand-primary font-bold text-sm">
                <QrCode className="w-4 h-4 text-emerald-600 shrink-0" />
                <span>Quét mã trên thân máy</span>
              </div>
              <p className="text-xs text-brand-text-muted leading-relaxed">
                Dùng camera điện thoại quét mã QR dán trên máy FreshVend để tải danh mục sản phẩm trực tiếp.
              </p>
            </div>

            {/* Step 2 */}
            <div className="p-4 rounded-2xl bg-emerald-50/70 border border-emerald-500/15 flex flex-col gap-2.5">
              <div className="w-8 h-8 rounded-xl bg-brand-primary text-white flex items-center justify-center font-black text-sm">
                2
              </div>
              <div className="flex items-center gap-2 text-brand-primary font-bold text-sm">
                <CreditCard className="w-4 h-4 text-emerald-600 shrink-0" />
                <span>Chọn món & Quét VietQR</span>
              </div>
              <p className="text-xs text-brand-text-muted leading-relaxed">
                Chọn sản phẩm mong muốn, kiểm tra giỏ hàng và mở bất kỳ app ngân hàng nào để quét mã VietQR.
              </p>
            </div>

            {/* Step 3 */}
            <div className="p-4 rounded-2xl bg-emerald-50/70 border border-emerald-500/15 flex flex-col gap-2.5">
              <div className="w-8 h-8 rounded-xl bg-brand-primary text-white flex items-center justify-center font-black text-sm">
                3
              </div>
              <div className="flex items-center gap-2 text-brand-primary font-bold text-sm">
                <PackageCheck className="w-4 h-4 text-emerald-600 shrink-0" />
                <span>Nhận hàng tại khay</span>
              </div>
              <p className="text-xs text-brand-text-muted leading-relaxed">
                Máy kích hoạt lò xo xoay nhả sản phẩm tự động trong 3 giây. Đẩy nắp mica và lấy món thưởng thức!
              </p>
            </div>
          </div>
        </div>

        {/* Support Hotline & Direct Contacts */}
        <div className="mt-6 pt-6 border-t border-emerald-500/10 grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="p-4 rounded-2xl bg-brand-primary text-white flex items-center gap-4 shadow-sm border border-emerald-400/20">
            <div className="w-12 h-12 rounded-2xl bg-white/15 flex items-center justify-center shrink-0">
              <PhoneCall className="w-6 h-6 text-brand-accent animate-pulse" />
            </div>
            <div>
              <span className="text-[11px] uppercase tracking-wider text-emerald-200 font-bold block">
                Tổng đài khẩn cấp 24/7
              </span>
              <a
                href="tel:19008888"
                className="text-2xl font-black text-white hover:text-brand-accent transition-colors tracking-tight font-mono"
              >
                1900 8888
              </a>
              <span className="text-[10px] text-white/70 block mt-0.5">Miễn phí cước gọi từ mọi mạng viễn thông</span>
            </div>
          </div>

          <div className="p-4 rounded-2xl bg-white border border-emerald-500/20 flex items-center gap-4 shadow-2xs">
            <div className="w-12 h-12 rounded-2xl bg-emerald-50 text-emerald-700 flex items-center justify-center shrink-0 border border-emerald-200">
              <MessageCircle className="w-6 h-6" />
            </div>
            <div>
              <span className="text-[11px] uppercase tracking-wider text-brand-text-muted font-bold block">
                Zalo Official Account
              </span>
              <span className="text-base font-extrabold text-brand-primary">FreshVend Official CSKH</span>
              <span className="text-[10px] text-brand-text-muted block mt-0.5">
                Nhắn tin gửi ảnh minh chứng hoặc biên lai đối soát
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* FAQ Accordion Section */}
      <div className="rounded-3xl bg-white/95 backdrop-blur-md shadow-card border border-emerald-500/15 p-6 sm:p-8">
        <div className="flex items-center gap-2 mb-4">
          <AlertTriangle className="w-5 h-5 text-amber-600" />
          <h3 className="text-lg font-black text-brand-primary tracking-tight">Câu hỏi thường gặp (FAQ)</h3>
        </div>

        <div className="space-y-3">
          {FAQS.map((faq) => {
            const isOpen = openFaq === faq.id;
            return (
              <div
                key={faq.id}
                className="rounded-2xl border border-emerald-500/15 overflow-hidden transition-colors bg-white/80"
              >
                <button
                  type="button"
                  onClick={() => toggleFaq(faq.id)}
                  aria-expanded={isOpen}
                  className="w-full px-4 sm:px-5 py-3.5 text-left flex items-center justify-between gap-3 hover:bg-emerald-50/50 transition-colors cursor-pointer"
                >
                  <div className="flex items-center gap-2.5">
                    <span className="text-[10px] font-black uppercase px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-800">
                      {faq.category}
                    </span>
                    <span className="text-xs sm:text-sm font-bold text-brand-primary">{faq.question}</span>
                  </div>
                  <ChevronDown
                    className={`w-4 h-4 text-emerald-800 transition-transform duration-200 shrink-0 ${
                      isOpen ? 'rotate-180 text-brand-primary' : ''
                    }`}
                  />
                </button>
                {isOpen && (
                  <div className="px-4 sm:px-5 pb-4 pt-1 text-xs sm:text-sm text-brand-text-muted leading-relaxed border-t border-emerald-500/10 bg-emerald-50/30">
                    {faq.answer}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Operating hours disclaimer */}
        <div className="mt-5 flex items-center gap-2 text-xs text-brand-text-muted bg-emerald-50/60 p-3 rounded-xl border border-emerald-500/10">
          <Clock className="w-4 h-4 text-emerald-700 shrink-0" />
          <span>Thời gian tiếp nhận giải quyết khiếu nại đối soát: Tự động 24/7/365.</span>
        </div>
      </div>
    </div>
  );
};

