import React from 'react';
import {
  Sparkles,
  Cpu,
  Smartphone,
  Leaf,
  ShieldCheck,
  ArrowLeft,
  ShoppingBag,
  Layers,
  Zap,
} from 'lucide-react';
import type { MachineResponse } from '../types/machine';

interface AboutSectionProps {
  machine?: MachineResponse | null;
  onBackToHome: () => void;
}

export const AboutSection: React.FC<AboutSectionProps> = ({ machine, onBackToHome }) => {
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

      {/* Hero Overview Card */}
      <div className="rounded-3xl bg-white/95 backdrop-blur-md shadow-card border border-emerald-500/15 p-6 sm:p-8">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-12 h-12 rounded-2xl bg-brand-primary flex items-center justify-center text-brand-accent border border-emerald-400/30 shadow-xs">
            <Leaf className="w-6 h-6 text-brand-accent" />
          </div>
          <div>
            <div className="inline-flex items-center gap-1.5 text-[11px] font-extrabold uppercase tracking-widest text-emerald-700">
              <Sparkles className="w-3 h-3 text-emerald-600" />
              <span>Bio-Tech Living Ecosystem</span>
            </div>
            <h2 className="text-2xl sm:text-3xl font-black text-brand-primary tracking-tight">
              Về FreshVend
            </h2>
          </div>
        </div>

        <p className="text-sm sm:text-base text-brand-text leading-relaxed mt-2">
          <strong>FreshVend</strong> là hệ thống máy bán hàng tự động thông minh thế hệ mới kết hợp giữa giải pháp vi điều khiển
          nhúng <strong>IoT</strong>, thanh toán ngân hàng tức thì qua <strong>VietQR</strong> và triết lý thiết kế 
          <strong> Sylva Living World</strong> mang sinh thái thiên nhiên trong lành vào nhịp sống hiện đại.
        </p>

        {/* 3 Pillars of Innovation */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-8">
          {/* Pillar 1 */}
          <div className="p-5 rounded-2xl bg-emerald-50/60 border border-emerald-500/15 flex flex-col gap-3">
            <div className="w-10 h-10 rounded-xl bg-brand-primary text-brand-accent flex items-center justify-center shadow-2xs">
              <Cpu className="w-5 h-5 text-brand-accent" />
            </div>
            <h3 className="font-extrabold text-base text-brand-primary">1. IoT & Vi Điều Khiển</h3>
            <p className="text-xs text-brand-text-muted leading-relaxed">
              Trang bị vi điều khiển ESP32 xử lý lệnh xuất kho qua MQTT HiveMQ Broker thời gian thực, tích hợp cảm biến rơi hồng ngoại đảm bảo nhả hàng chuẩn xác 100%.
            </p>
          </div>

          {/* Pillar 2 */}
          <div className="p-5 rounded-2xl bg-emerald-50/60 border border-emerald-500/15 flex flex-col gap-3">
            <div className="w-10 h-10 rounded-xl bg-brand-primary text-brand-accent flex items-center justify-center shadow-2xs">
              <Smartphone className="w-5 h-5 text-brand-accent" />
            </div>
            <h3 className="font-extrabold text-base text-brand-primary">2. Mua Sắm Touchless</h3>
            <p className="text-xs text-brand-text-muted leading-relaxed">
              Trải nghiệm đặt hàng hoàn toàn không chạm (Zero-Touch Public Keypad): Khách hàng quét QR trên máy và hoàn tất toàn bộ thao tác ngay trên chiếc điện thoại cá nhân.
            </p>
          </div>

          {/* Pillar 3 */}
          <div className="p-5 rounded-2xl bg-emerald-50/60 border border-emerald-500/15 flex flex-col gap-3">
            <div className="w-10 h-10 rounded-xl bg-brand-primary text-brand-accent flex items-center justify-center shadow-2xs">
              <Zap className="w-5 h-5 text-brand-accent" />
            </div>
            <h3 className="font-extrabold text-base text-brand-primary">3. Thanh Toán Tức Thì</h3>
            <p className="text-xs text-brand-text-muted leading-relaxed">
              Tích hợp cổng SePay Webhook đối soát giao dịch VietQR tự động chỉ từ 1–2 giây, loại bỏ hoàn toàn nỗi lo kẹt tiền mặt hay tiền thừa xu lẻ.
            </p>
          </div>
        </div>

        {/* Technical Stack Architecture */}
        <div className="mt-8 pt-6 border-t border-emerald-500/10">
          <h3 className="text-sm font-extrabold uppercase tracking-wider text-brand-primary mb-4 flex items-center gap-2">
            <Layers className="w-4 h-4 text-emerald-600" />
            <span>Kiến trúc Kỹ thuật Hệ thống FreshVend</span>
          </h3>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
            <div className="p-3 rounded-xl bg-white border border-emerald-500/15 shadow-2xs">
              <span className="text-[10px] font-bold uppercase tracking-wider text-emerald-800 block">Frontend</span>
              <span className="font-extrabold text-xs text-brand-primary">React 19 + TypeScript</span>
              <span className="text-[10px] text-brand-text-muted block mt-0.5">Sylva 3D & Tailwind CSS</span>
            </div>
            <div className="p-3 rounded-xl bg-white border border-emerald-500/15 shadow-2xs">
              <span className="text-[10px] font-bold uppercase tracking-wider text-emerald-800 block">Backend</span>
              <span className="font-extrabold text-xs text-brand-primary">Spring Boot 3</span>
              <span className="text-[10px] text-brand-text-muted block mt-0.5">REST API & Polling</span>
            </div>
            <div className="p-3 rounded-xl bg-white border border-emerald-500/15 shadow-2xs">
              <span className="text-[10px] font-bold uppercase tracking-wider text-emerald-800 block">IoT Firmware</span>
              <span className="font-extrabold text-xs text-brand-primary">ESP32 SoC</span>
              <span className="text-[10px] text-brand-text-muted block mt-0.5">HiveMQ MQTT Broker</span>
            </div>
            <div className="p-3 rounded-xl bg-white border border-emerald-500/15 shadow-2xs">
              <span className="text-[10px] font-bold uppercase tracking-wider text-emerald-800 block">Thanh toán</span>
              <span className="font-extrabold text-xs text-brand-primary">VietQR + SePay</span>
              <span className="text-[10px] text-brand-text-muted block mt-0.5">Realtime Webhook</span>
            </div>
          </div>
        </div>

        {/* Commitment Banner & CTA */}
        <div className="mt-8 p-5 rounded-2xl bg-gradient-to-r from-emerald-900 to-brand-primary text-white flex flex-col sm:flex-row items-center justify-between gap-4 shadow-sm border border-emerald-400/25">
          <div className="flex items-center gap-3">
            <ShieldCheck className="w-8 h-8 text-brand-accent shrink-0" />
            <div>
              <h4 className="font-extrabold text-sm text-white">Cam kết chất lượng 100%</h4>
              <p className="text-xs text-white/80">Sản phẩm được bảo quản ở nhiệt độ chuẩn 4–8°C, luôn tươi mát.</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onBackToHome}
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-brand-accent hover:bg-lime-300 text-brand-primary font-black text-xs transition-all active:scale-95 shadow-xs cursor-pointer shrink-0"
          >
            <ShoppingBag className="w-4 h-4 text-brand-primary" />
            <span>Khám phá sản phẩm ngay</span>
          </button>
        </div>
      </div>
    </div>
  );
};

