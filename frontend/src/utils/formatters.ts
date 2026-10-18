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
