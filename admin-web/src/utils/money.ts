/**
 * 金额计算统一入口（仅展示口径）
 *
 * 成交金额以服务端报价与订单落库金额为准，前端不得据此推算成交价。
 * 数量沿用既有万分位口径，单价按分取整，结果 HALF_UP 到分；
 * 全程用 BigInt 计算，避免浮点累积误差。
 */

const QUANTITY_SCALE = 10000
const PRICE_SCALE = 100

function toScaled(value: number | string | null | undefined, scale: number): bigint | null {
  if (value == null || value === '') return null
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return null
  return BigInt(Math.round(numeric * scale))
}

/** 单价 × 数量 的小计（元，保留 2 位小数）。非法输入按 0 处理，不抛错、不猜测。 */
export function subtotalAmount(
  quantity: number | string | null | undefined,
  unitPrice: number | string | null | undefined
): number {
  const quantityScaled = toScaled(quantity, QUANTITY_SCALE)
  const priceScaled = toScaled(unitPrice, PRICE_SCALE)
  if (quantityScaled === null || priceScaled === null) return 0
  const scaled = quantityScaled * priceScaled
  const negative = scaled < 0n
  const magnitude = negative ? -scaled : scaled
  // 万分位 × 分 → 分，正数 HALF_UP
  const cents = (magnitude + BigInt(QUANTITY_SCALE / 2)) / BigInt(QUANTITY_SCALE)
  return Number(negative ? -cents : cents) / PRICE_SCALE
}

/** 金额累加（元，保留 2 位小数）。跳过非法值，不因脏数据中断展示。 */
export function sumAmount(values: readonly (number | string | null | undefined)[]): number {
  let cents = 0n
  for (const value of values) {
    const scaled = toScaled(value, PRICE_SCALE)
    if (scaled !== null) cents += scaled
  }
  return Number(cents) / PRICE_SCALE
}
