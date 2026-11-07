import { isPositiveLongId, normalizeLongId } from './ids.js';
import type { RecoveryActor, SaleSummary } from './submission.js';

/** 移动开单第一版：只支持普通商品、现金结算；价格一律以后端报价为准，本机不计算成交价。 */
export type CatalogProduct = {
  id: string; code: string; name: string; mainUnit: string;
  suggestedRetailPrice: string; baseUnitName: string;
  allowFractionalBaseQuantity: boolean; status: number;
};
export type PriceQuote = {
  quoteId: string; productId: string; saleUnit: string; suggestedUnitPrice: string;
};
export type SaleLine = {
  productId: string; name: string; unit: string; quantity: string; quotedUnitPrice: string;
};
/** 第一版只开放现金与赊销；余额/混合支付需要钱包验证码与支付明细，留待后续阶段。 */
export type SaleOptions = {
  paymentType: 1 | 6; customerId: string | null; deliveryType: 1 | 2;
  receiverName: string | null; receiverPhone: string | null; receiverAddress: string | null;
  remark: string | null;
};
export const defaultSaleOptions: SaleOptions = {
  paymentType: 1, customerId: null, deliveryType: 1,
  receiverName: null, receiverPhone: null, receiverAddress: null, remark: null,
};

/** 与后端 PosOrderInputPolicy 同口径的输入校验；不定价、不核销、不改钱货。 */
export function resolveSaleOptions(partial: Partial<SaleOptions> = {}): SaleOptions {
  const options: SaleOptions = { ...defaultSaleOptions, ...partial };
  if (options.paymentType !== 1 && options.paymentType !== 6) throw new Error('PAYMENT_INVALID');
  if (options.paymentType === 6 && !isPositiveLongId(options.customerId)) throw new Error('CUSTOMER_REQUIRED');
  if (options.customerId !== null && !isPositiveLongId(options.customerId)) throw new Error('CUSTOMER_INVALID');
  if (options.deliveryType !== 1 && options.deliveryType !== 2) throw new Error('DELIVERY_INVALID');
  if (options.deliveryType === 2 && (!text(options.receiverName, 100) || !text(options.receiverPhone, 50)
    || !text(options.receiverAddress, 500))) throw new Error('RECEIVER_REQUIRED');
  if (options.deliveryType === 1 && (options.receiverName !== null || options.receiverPhone !== null
    || options.receiverAddress !== null)) throw new Error('RECEIVER_UNEXPECTED');
  if (options.remark !== null && !text(options.remark, 200)) throw new Error('REMARK_INVALID');
  return options;
}

type Envelope = { code: string; data: unknown };
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const text = (v: unknown, max = 200): v is string => typeof v === 'string' && !!v.trim() && v.length <= max;
const price = (v: unknown): v is string => typeof v === 'string' && /^\d{1,9}(\.\d{1,2})?$/.test(v);
const quantity = (v: unknown): v is string => typeof v === 'string'
  && /^(0|[1-9]\d*)(\.\d{1,4})?$/.test(v) && v.length <= 24;

/** 目录与报价端点是 {code:"200",data:...} 自定义包装，code 为字符串，不能套通用 R 解码。 */
export const isCatalogEnvelope = (v: unknown): v is Envelope =>
  object(v) && v.code === '200' && Object.prototype.hasOwnProperty.call(v, 'data');

function pageRecords(data: unknown): unknown[] {
  if (Array.isArray(data)) return data;
  if (object(data) && Array.isArray(data.records)) return data.records;
  throw new Error('CATALOG_INVALID');
}

export function decodeCatalog(envelope: unknown): CatalogProduct[] {
  if (!isCatalogEnvelope(envelope)) throw new Error('CATALOG_INVALID');
  const rows = pageRecords(envelope.data);
  if (rows.length > 1000) throw new Error('CATALOG_INVALID');
  return rows.map(row => {
    if (!object(row)) throw new Error('CATALOG_INVALID');
    const id = normalizeLongId(row.id);
    if (!id || !text(row.code, 100) || !text(row.name, 200) || !text(row.mainUnit, 50)
      || row.suggestedRetailPrice === null || row.suggestedRetailPrice === undefined
      || !price(String(row.suggestedRetailPrice))) throw new Error('CATALOG_INVALID');
    return {
      id, code: row.code, name: row.name, mainUnit: row.mainUnit,
      suggestedRetailPrice: String(row.suggestedRetailPrice),
      baseUnitName: text(row.baseUnitName, 50) ? row.baseUnitName : row.mainUnit,
      allowFractionalBaseQuantity: row.allowFractionalBaseQuantity === true,
      status: typeof row.status === 'number' ? row.status : 1,
    };
  });
}

export function decodeQuote(envelope: unknown, productId: string): PriceQuote {
  if (!isCatalogEnvelope(envelope) || !object(envelope.data)) throw new Error('QUOTE_INVALID');
  const data = envelope.data;
  const quoted = normalizeLongId(data.productId);
  if (!quoted || quoted !== productId || !text(data.quoteId, 100) || !text(data.saleUnit, 50)
    || !price(String(data.suggestedUnitPrice ?? ''))) throw new Error('QUOTE_INVALID');
  return { quoteId: data.quoteId, productId: quoted, saleUnit: data.saleUnit,
    suggestedUnitPrice: String(data.suggestedUnitPrice) };
}

/** 金额一律按整数分解析；只用于本机恢复摘要与展示，不参与成交金额计算。 */
export function toCents(value: string): number {
  if (!price(value)) throw new Error('PRICE_INVALID');
  const [whole, fraction = ''] = value.split('.');
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
}
function quantityScaled(value: string): number {
  const [whole, fraction = ''] = value.split('.');
  return Number(whole) * 10000 + Number(fraction.padEnd(4, '0'));
}
export function lineCents(line: SaleLine): number {
  if (!quantity(line.quantity) || quantityScaled(line.quantity) <= 0) throw new Error('QUANTITY_INVALID');
  return Math.round(toCents(line.quotedUnitPrice) * quantityScaled(line.quantity) / 10000);
}

/** summary 只保存可核对的有限事实，不含凭据、联系人或可重发请求体。 */
export function toSaleSummary(lines: SaleLine[], options: SaleOptions = defaultSaleOptions): SaleSummary {
  if (!Array.isArray(lines) || lines.length === 0 || lines.length > 500) throw new Error('SUMMARY_INVALID');
  const parsed = lines.map(line => {
    if (!isPositiveLongId(line.productId) || !text(line.unit, 32) || !quantity(line.quantity)
      || !price(line.quotedUnitPrice) || quantityScaled(line.quantity) <= 0) throw new Error('SUMMARY_INVALID');
    return { productId: line.productId, packageId: null, unit: line.unit,
      quantity: line.quantity,
      lineCents: Math.round(toCents(line.quotedUnitPrice) * quantityScaled(line.quantity) / 10000) };
  });
  const totalCents = parsed.reduce((sum, line) => sum + line.lineCents, 0);
  if (!Number.isSafeInteger(totalCents) || totalCents <= 0) throw new Error('SUMMARY_INVALID');
  return { totalCents, netCents: totalCents, paymentMethod: options.paymentType,
    customerId: options.customerId, lines: parsed };
}

/** 提交命令不含金额字段：成交价由后端按 quotedUnitPrice 重新报价并核对。 */
export function buildSaleCommand(requestKey: string, actor: RecoveryActor, lines: SaleLine[],
                                 options: SaleOptions = defaultSaleOptions): Record<string, unknown> {
  if (!isPositiveLongId(actor.staffId) || !isPositiveLongId(actor.storeId)) throw new Error('UNAUTHORIZED');
  resolveSaleOptions(options);
  return {
    requestKey, storeId: actor.storeId, staffId: actor.staffId,
    paymentType: options.paymentType, customerId: options.customerId,
    deliveryType: options.deliveryType,
    receiverName: options.receiverName, receiverPhone: options.receiverPhone,
    receiverAddress: options.receiverAddress, remark: options.remark,
    items: lines.map(line => ({
      productId: line.productId, itemType: 1, quantity: line.quantity,
      unit: line.unit, quotedUnitPrice: line.quotedUnitPrice,
    })),
  };
}

export const isOrderId = (v: unknown): v is unknown =>
  v !== null && v !== undefined && normalizeLongId(v) !== null;
