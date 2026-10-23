/**
 * Format integer VND amount safely without float conversion
 * Example: 8000 -> 8.000 ₫
 */
export function formatVnd(amount: number): string {
  if (typeof amount !== 'number' || isNaN(amount)) {
    return '0 ₫';
  }
  const integerPart = Math.floor(amount);
  return `${integerPart.toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.')} ₫`;
}

/**
 * Format ISO datetime string to localized Vietnamese format
 * Example: 2026-10-22T04:15:00Z -> 11:15:00 22/10/2026
 */
export function formatDateTime(isoString?: string | null): string {
  if (!isoString) {
    return '—';
  }
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) {
      return '—';
    }
    const pad = (n: number) => n.toString().padStart(2, '0');
    const day = pad(d.getDate());
    const month = pad(d.getMonth() + 1);
    const year = d.getFullYear();
    const hours = pad(d.getHours());
    const minutes = pad(d.getMinutes());
    const seconds = pad(d.getSeconds());
    return `${hours}:${minutes}:${seconds} ${day}/${month}/${year}`;
  } catch {
    return '—';
  }
}

