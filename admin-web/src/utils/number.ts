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



/** 库存/退货数量只去显示尾零；未知保持待核对，不参与钱货计算。 */
export function formatQuantity(value?: number | string | null): string {
  if (value == null || value === '' || !Number.isFinite(Number(value))) return '待核对'
  return Number(value).toFixed(8).replace(/\.?0+$/, '')
}
