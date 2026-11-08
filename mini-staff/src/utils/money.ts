/** 金额展示统一两位小数。
 *  后端金额是十进制字符串（"12.34"/"25"/"38.5"），页面不得各自截取，
 *  否则同一份数据在不同页面显示成 ¥25 与 ¥25.00。无法解析时原样返回，
 *  不把缺数据伪造成 0，避免把「没拿到」显示为「零元」。 */
export function money(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return '';
  const text = typeof value === 'number' ? String(value) : value.trim();
  if (text === '' || !/^-?\d+(\.\d+)?$/.test(text)) return text;
  return Number(text).toFixed(2);
}
