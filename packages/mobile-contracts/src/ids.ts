/** 当前Java Long主键合同；已丢精度的number不能猜回原ID。 */
export function isPositiveLongId(value: unknown): value is string {
  return typeof value === 'string' && /^[1-9]\d{0,18}$/.test(value)
    && (value.length < 19 || value <= '9223372036854775807');
}
export function normalizeLongId(value: unknown): string | null {
  if (isPositiveLongId(value)) return value;
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? String(value) : null;
}
