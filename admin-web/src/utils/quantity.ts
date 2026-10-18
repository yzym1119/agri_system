/** 仅用于提前提示；最终以后台持久单位及原销售证据校验为准。 */
export function isWholeBaseQuantity(quantity: number | string, ratio: number | string): boolean {
  const fraction = (input: number | string): [bigint, bigint] | null => {
    const value = Number(input)
    if (!Number.isFinite(value) || value < 0) return null
    const raw = /e/i.test(String(input)) ? value.toFixed(12) : String(input)
    if (!/^\d+(\.\d+)?$/.test(raw)) return null
    const [whole, decimals = ''] = raw.split('.')
    return [BigInt(whole + decimals), 10n ** BigInt(decimals.length)]
  }
  const a = fraction(quantity), b = fraction(ratio)
  return !!a && !!b && b[0] > 0n && (a[0] * b[0]) % (a[1] * b[1]) === 0n
}
