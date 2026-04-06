/**
 * Number formatting utilities
 */
export function formatStock(value?: number | string | null): string {
  if (value == null || value === '') return ''
  const num = Number(value)
  if (isNaN(num)) return ''

  // Round to max 3 decimals
  const rounded = Math.round(num * 1000) / 1000

  // Convert to string with up to 3 decimals, then trim trailing zeros
  let s = rounded.toFixed(3)
  // remove trailing zeros and optional trailing dot
  s = s.replace(/\.?0+$/, '')
  return s
}

/**
 * Number formatting helpers
 */
// deprecated duplicate removed; use formatStock above


