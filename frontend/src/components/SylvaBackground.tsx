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
    </div>
  );
};

