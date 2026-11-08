import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SylvaBackground } from '../components/SylvaBackground';

describe('SylvaBackground Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders background container with aria-hidden and pointer-events-none', () => {
    render(<SylvaBackground />);

    const container = screen.getByTestId('sylva-background-container');
    expect(container).toBeInTheDocument();
    expect(container).toHaveAttribute('aria-hidden', 'true');
    expect(container.className).toContain('pointer-events-none');
    expect(container.className).toContain('fixed');
    expect(container.className).toContain('inset-0');
  });

  it('renders sandboxed iframe pointing to sylva-scene.html', () => {
    render(<SylvaBackground />);

    const iframe = screen.getByTitle('Sylva Living Green 3D Ambient Scene');
    expect(iframe).toBeInTheDocument();
    expect(iframe.tagName.toLowerCase()).toBe('iframe');
    expect(iframe.getAttribute('src')).toBe('/sylva/sylva-scene.html');
    expect(iframe.getAttribute('sandbox')).toContain('allow-scripts');
  });

  it('applies reduced motion query parameter when forceReducedMotion is enabled', () => {
    render(<SylvaBackground forceReducedMotion={true} />);

    const iframe = screen.getByTitle('Sylva Living Green 3D Ambient Scene');
    expect(iframe.getAttribute('src')).toBe('/sylva/sylva-scene.html?reduced=1');
  });

  it('cleans up event listeners on unmount without crashing', () => {
    const removeEventListenerSpy = vi.spyOn(window, 'removeEventListener');
    const { unmount } = render(<SylvaBackground />);

    unmount();
    expect(removeEventListenerSpy).toHaveBeenCalled();
  });
});

