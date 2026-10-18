import { describe, it, expect } from 'vitest';
import { formatVnd } from '../utils/formatters';

describe('formatVnd', () => {
  it('formats regular amounts with thousand dots and currency symbol', () => {
    expect(formatVnd(8000)).toBe('8.000 ₫');
    expect(formatVnd(15000)).toBe('15.000 ₫');
    expect(formatVnd(120000)).toBe('120.000 ₫');
  });

  it('formats zero correctly', () => {
    expect(formatVnd(0)).toBe('0 ₫');
  });

  it('handles negative or invalid numbers safely', () => {
    expect(formatVnd(NaN)).toBe('0 ₫');
  });
});
