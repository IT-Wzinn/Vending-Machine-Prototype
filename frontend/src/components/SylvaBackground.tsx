import React, { useEffect, useRef, useState } from 'react';

export interface SylvaBackgroundProps {
  className?: string;
  style?: React.CSSProperties;
  forceReducedMotion?: boolean;
}

/**
 * SylvaBackground
 * Ambient 3D procedural moss & living world layer from ThreeUI Sylva Living Green.
 * Runs in an isolated, sandboxed background frame to guarantee zero interference with
 * FreshVend's core business logic, cart, and payment state.
 */
export const SylvaBackground: React.FC<SylvaBackgroundProps> = ({
  className = '',
  style,
  forceReducedMotion = false,
}) => {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [isReady, setIsReady] = useState(false);
  const [isReducedMotion, setIsReducedMotion] = useState<boolean>(() => {
    if (forceReducedMotion) return true;
    if (typeof window !== 'undefined' && window.matchMedia) {
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    }
    return false;
  });

  // Track system prefers-reduced-motion changes
  useEffect(() => {
    if (forceReducedMotion) {
      setIsReducedMotion(true);
      return;
    }
    if (typeof window === 'undefined' || !window.matchMedia) return;

    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const handleChange = (e: MediaQueryListEvent) => {
      setIsReducedMotion(e.matches);
    };

    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, [forceReducedMotion]);

  // Forward pointer movements to iframe so 3D parallax & butterfly react without blocking clicks
  useEffect(() => {
    if (typeof window === 'undefined' || isReducedMotion) return;

    let ticking = false;
    const handlePointerMove = (e: PointerEvent) => {
      if (e.pointerType === 'touch') return;
      if (!ticking) {
        window.requestAnimationFrame(() => {
          if (iframeRef.current?.contentWindow) {
            iframeRef.current.contentWindow.postMessage(
              {
                type: 'POINTER_MOVE',
                clientX: e.clientX,
                clientY: e.clientY,
                innerWidth: window.innerWidth,
                innerHeight: window.innerHeight,
              },
              '*'
            );
          }
          ticking = false;
        });
        ticking = true;
      }
    };

    const handlePointerLeave = () => {
      if (iframeRef.current?.contentWindow) {
        iframeRef.current.contentWindow.postMessage({ type: 'POINTER_LEAVE' }, '*');
      }
    };

    window.addEventListener('pointermove', handlePointerMove, { passive: true });
    window.addEventListener('pointerleave', handlePointerLeave, { passive: true });

    return () => {
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerleave', handlePointerLeave);
    };
  }, [isReducedMotion]);

  // Pause rendering when browser tab is inactive to preserve battery & CPU
  useEffect(() => {
    if (typeof document === 'undefined') return;

    const handleVisibilityChange = () => {
      if (iframeRef.current?.contentWindow) {
        iframeRef.current.contentWindow.postMessage(
          {
            type: 'VISIBILITY',
            hidden: document.hidden,
          },
          '*'
        );
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, []);

  const srcUrl = `/sylva/sylva-scene.html${isReducedMotion ? '?reduced=1' : ''}`;

  return (
    <div
      aria-hidden="true"
      data-testid="sylva-background-container"
      className={`fixed inset-0 pointer-events-none overflow-hidden select-none z-0 ${className}`}
      style={{
        backgroundColor: '#123D32',
        ...style,
      }}
    >
      {/* Fallback ambient gradient while WebGL is booting */}
      <div
        className={`absolute inset-0 transition-opacity duration-700 ease-out pointer-events-none ${
          isReady ? 'opacity-0' : 'opacity-100'
        }`}
        style={{
          background: 'radial-gradient(circle at 50% 60%, rgba(216,243,106,0.12) 0%, rgba(18,61,50,0.98) 75%)',
        }}
      />

      {/* Sandboxed Scene Frame */}
      <iframe
        ref={iframeRef}
        title="Sylva Living Green 3D Ambient Scene"
        src={srcUrl}
        loading="lazy"
        sandbox="allow-scripts allow-same-origin"
        onLoad={() => setIsReady(true)}
        className="w-full h-full border-0 block pointer-events-none"
        style={{
          position: 'absolute',
          inset: 0,
          width: '100%',
          height: '100%',
        }}
      />

      {/* Atmospheric Botanical Side Mist / Hiệu ứng sương mù 2 bên sườn website (Nhẹ nhàng, êm dịu) */}
      <div
        aria-hidden="true"
        data-testid="side-mist-container"
        className="absolute inset-0 pointer-events-none overflow-hidden select-none z-10 opacity-75"
      >
        {/* Left Flank Mist (Sương mù bồng bềnh êm dịu bên trái) */}
        <div className="absolute top-0 bottom-0 left-0 w-[220px] sm:w-[300px] lg:w-[400px] xl:w-[460px] pointer-events-none">
          {/* Base soft edge gradient */}
          <div
            className="absolute inset-0 pointer-events-none"
            style={{
              background:
                'linear-gradient(to right, rgba(235, 245, 240, 0.22) 0%, rgba(220, 240, 230, 0.12) 40%, rgba(216, 243, 106, 0.04) 65%, transparent 100%)',
            }}
          />

          {/* Floating Billowing Mist Puff 1 (Upper-left) */}
          <div
            className={`absolute -top-12 -left-20 w-80 h-[480px] rounded-full blur-3xl pointer-events-none ${
              isReducedMotion ? 'opacity-50' : 'animate-mist-left'
            }`}
            style={{
              background:
                'radial-gradient(circle at 40% 45%, rgba(255, 255, 255, 0.20) 0%, rgba(220, 245, 230, 0.12) 50%, transparent 80%)',
            }}
          />

          {/* Floating Billowing Mist Puff 2 (Mid-lower left) */}
          <div
            className={`absolute top-1/3 -left-16 w-96 h-96 rounded-full blur-3xl pointer-events-none ${
              isReducedMotion ? 'opacity-45' : 'animate-mist-slow'
            }`}
            style={{
              background:
                'radial-gradient(circle at 45% 50%, rgba(240, 253, 244, 0.18) 0%, rgba(167, 243, 208, 0.10) 55%, transparent 80%)',
              animationDelay: '-5s',
            }}
          />

          {/* Ground Mist hugging lower terrain on left */}
          <div
            className={`absolute -bottom-20 -left-10 w-[400px] h-72 rounded-full blur-2xl pointer-events-none ${
              isReducedMotion ? 'opacity-55' : 'animate-mist-left'
            }`}
            style={{
              background:
                'radial-gradient(circle at 35% 65%, rgba(236, 253, 245, 0.22) 0%, rgba(209, 250, 229, 0.12) 55%, transparent 85%)',
              animationDuration: '24s',
            }}
          />
        </div>

        {/* Right Flank Mist (Sương mù bồng bềnh êm dịu bên phải) */}
        <div className="absolute top-0 bottom-0 right-0 w-[220px] sm:w-[300px] lg:w-[400px] xl:w-[460px] pointer-events-none">
          {/* Base soft edge gradient */}
          <div
            className="absolute inset-0 pointer-events-none"
            style={{
              background:
                'linear-gradient(to left, rgba(235, 245, 240, 0.22) 0%, rgba(220, 240, 230, 0.12) 40%, rgba(216, 243, 106, 0.04) 65%, transparent 100%)',
            }}
          />

          {/* Floating Billowing Mist Puff 1 (Upper-right framing machine) */}
          <div
            className={`absolute -top-16 -right-20 w-96 h-[500px] rounded-full blur-3xl pointer-events-none ${
              isReducedMotion ? 'opacity-50' : 'animate-mist-right'
            }`}
            style={{
              background:
                'radial-gradient(circle at 60% 45%, rgba(255, 255, 255, 0.20) 0%, rgba(220, 245, 230, 0.12) 50%, transparent 80%)',
            }}
          />

          {/* Floating Billowing Mist Puff 2 (Mid-right framing machine neon glow) */}
          <div
            className={`absolute top-1/4 -right-16 w-[400px] h-[400px] rounded-full blur-3xl pointer-events-none ${
              isReducedMotion ? 'opacity-45' : 'animate-mist-slow'
            }`}
            style={{
              background:
                'radial-gradient(circle at 65% 50%, rgba(240, 253, 244, 0.18) 0%, rgba(167, 243, 208, 0.10) 55%, transparent 80%)',
              animationDelay: '-8s',
            }}
          />

          {/* Ground Mist hugging lower right terrain */}
          <div
            className={`absolute -bottom-20 -right-10 w-[420px] h-76 rounded-full blur-2xl pointer-events-none ${
              isReducedMotion ? 'opacity-55' : 'animate-mist-right'
            }`}
            style={{
              background:
                'radial-gradient(circle at 65% 65%, rgba(236, 253, 245, 0.22) 0%, rgba(209, 250, 229, 0.12) 55%, transparent 85%)',
              animationDuration: '26s',
            }}
          />
        </div>
      </div>
    </div>
  );
};

