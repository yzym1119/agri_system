import { isPositiveLongId, normalizeLongId } from './ids.js';
import type { RecoveryActor, SaleSummary } from './submission.js';

/** 移动开单第一版：只支持普通商品、现金结算；价格一律以后端报价为准，本机不计算成交价。 */
export type CatalogProduct = {
  id: string; code: string; name: string; mainUnit: string;
  suggestedRetailPrice: string; baseUnitName: string;
  allowFractionalBaseQuantity: boolean; status: number;
  availableBaseQuantity: string | null; availabilityStoreId: string | null; pricingUnit: string | null;
};
export type PriceQuote = {
  quoteId: string; productId: string; saleUnit: string; suggestedUnitPrice: string;
};
/** 收银选客户投影：只含开单与配送所需字段，不含余额/欠款等资金档案。 */
export type PosCustomer = {
  id: string; code: string; name: string; phone: string;
  address: string | null; type: number; vipLevel: number | null; vipActive: boolean;
};
export type SaleLine = ProductSaleLine | PackageSaleLine;

/** 单品行：unit 是实际销售单位，quotedUnitPrice 是每单位后端报价。 */
export type ProductSaleLine = {
  kind?: 'PRODUCT'; productId: string; name: string; unit: string; quantity: string; quotedUnitPrice: string;
};
/** 套餐行：整套提交，quantity 是套数（正整数）；物料由后端按 packageQuoteId 展开，前端不拆行、不分摊。 */
export type PackageSaleLine = {
  kind: 'PACKAGE'; packageId: string; packageQuoteId: string; name: string; quantity: string;
  quotedUnitPrice: string; materials: PackageMaterial[];
};
export const isPackageLine = (line: SaleLine): line is PackageSaleLine => line.kind === 'PACKAGE';

/** 套餐组成：只用于核对“拿什么”，不参与计价。 */
export type PackageMaterial = { productId: string; name: string; quantity: string; unit: string };
export type PosPackage = {
  id: string; code: string; name: string; suggestedRetailPrice: string;
  /** 本店可售套数（按子商品可用基准量整除取小）；null 表示后端没给出可信值，未知不当 0。 */
  saleablePackages: string | null;
  materials: PackageMaterial[];
};
export type PackageQuote = {
  quoteId: string; packageId: string; quantity: string; suggestedUnitPrice: string; totalAmount: string;
};
/** 员工端只开放现金与当场交货/门店配送；赊销、余额、微信、支付宝、混合支付与改价留在后台收银台。 */
export type SaleOptions = {
  paymentType: 1; customerId: string | null; deliveryType: 1 | 2; remark: string | null;
};
export const defaultSaleOptions: SaleOptions = {
  paymentType: 1, customerId: null, deliveryType: 1, remark: null,
};

/** 与后端 PosOrderInputPolicy 同口径的输入校验；不定价、不核销、不改钱货。 */
export function resolveSaleOptions(partial: Partial<SaleOptions> = {}): SaleOptions {
  const options: SaleOptions = { ...defaultSaleOptions, ...partial };
  if (options.paymentType !== 1) throw new Error('PAYMENT_INVALID');
  if (options.customerId !== null && !isPositiveLongId(options.customerId)) throw new Error('CUSTOMER_INVALID');
  if (options.deliveryType !== 1 && options.deliveryType !== 2) throw new Error('DELIVERY_INVALID');
  // 门店配送不再手抄收货信息：必须选中客户，收货人/电话/地址由后端按客户档案带出。
  if (options.deliveryType === 2 && options.customerId === null) throw new Error('CUSTOMER_REQUIRED');
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
    const store = normalizeLongId(row.availabilityStoreId);
    const available = typeof row.availableBaseQuantity === 'string'
      && /^(0|[1-9]\d{0,59})(\.\d{1,8})?$/.test(row.availableBaseQuantity)
      && text(row.baseUnitName, 50) && store ? row.availableBaseQuantity : null;
    return {
      id, code: row.code, name: row.name, mainUnit: row.mainUnit,
      suggestedRetailPrice: String(row.suggestedRetailPrice),
      availableBaseQuantity: available, availabilityStoreId: store,
      pricingUnit: text(row.pricingUnit, 50) ? row.pricingUnit : null,
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

/** 后端 BigDecimal 可能以数字或字符串出现；展示字段只做形状校验，不重算。 */
const numeric = (v: unknown): string | null => {
  const raw = typeof v === 'number' ? String(v) : typeof v === 'string' ? v.trim() : '';
  return /^\d{1,12}(\.\d{1,8})?$/.test(raw) ? raw : null;
};
/** 套数只能正整数：半套套餐无法拆成物料行，后端也只接受整数套。 */
const packages = (v: unknown): v is string => typeof v === 'string' && /^[1-9]\d{0,3}$/.test(v);

function decodeMaterials(rows: unknown): PackageMaterial[] {
  if (!Array.isArray(rows) || rows.length === 0 || rows.length > 50) throw new Error('PACKAGE_INVALID');
  return rows.map(row => {
    if (!object(row)) throw new Error('PACKAGE_INVALID');
    const productId = normalizeLongId(row.productId);
    const materialQuantity = numeric(row.quantity);
    if (!productId || !text(row.productName, 200) || materialQuantity === null
      || Number(materialQuantity) <= 0 || !text(row.unit, 32)) throw new Error('PACKAGE_INVALID');
    return { productId, name: row.productName, quantity: materialQuantity, unit: row.unit };
  });
}

/** 套餐目录：只有 id/名称/每套价/组成/本店可售套数，不含成本与内部配置。 */
export function decodePackages(envelope: unknown): PosPackage[] {
  if (!isCatalogEnvelope(envelope)) throw new Error('PACKAGE_INVALID');
  const rows = pageRecords(envelope.data);
  if (rows.length > 200) throw new Error('PACKAGE_INVALID');
  return rows.map(row => {
    if (!object(row)) throw new Error('PACKAGE_INVALID');
    const id = normalizeLongId(row.id);
    const retail = row.suggestedRetailPrice === null || row.suggestedRetailPrice === undefined
      ? '' : String(row.suggestedRetailPrice);
    if (!id || !text(row.code, 100) || !text(row.name, 200) || !price(retail)) throw new Error('PACKAGE_INVALID');
    const stock = numeric(row.stock);
    return {
      id, code: row.code, name: row.name, suggestedRetailPrice: retail,
      // 可售套数是后端按子商品可用量取整除后的下限；缺值保持 null，不能当 0 处理。
      saleablePackages: stock === null ? null : String(Math.max(0, Math.floor(Number(stock)))),
      materials: decodeMaterials(row.items),
    };
  });
}

/** 套餐报价：每套价与总额都由后端算，前端只用 quoteId 作指纹。 */
export function decodePackageQuote(envelope: unknown, packageId: string, quantity: string): PackageQuote {
  if (!isCatalogEnvelope(envelope) || !object(envelope.data)) throw new Error('PACKAGE_QUOTE_INVALID');
  const data = envelope.data;
  const quoted = normalizeLongId(data.packageId);
  const echoed = numeric(data.quantity);
  if (!quoted || quoted !== packageId || !text(data.quoteId, 120) || !text(data.packageName, 200)
    || !price(String(data.suggestedUnitPrice ?? '')) || !price(String(data.totalAmount ?? ''))
    || echoed === null || Number(echoed) !== Number(quantity)) throw new Error('PACKAGE_QUOTE_INVALID');
  return { quoteId: data.quoteId, packageId: quoted, quantity: echoed,
    suggestedUnitPrice: String(data.suggestedUnitPrice), totalAmount: String(data.totalAmount) };
}

/** 客户检索与目录同一套 {code:"200",data:{records}} 包装。 */
export function decodeCustomers(envelope: unknown): PosCustomer[] {
  if (!isCatalogEnvelope(envelope)) throw new Error('CUSTOMER_INVALID');
  const rows = pageRecords(envelope.data);
  if (rows.length > 50) throw new Error('CUSTOMER_INVALID');
  return rows.map(row => {
    if (!object(row)) throw new Error('CUSTOMER_INVALID');
    const id = normalizeLongId(row.id);
    if (!id || !text(row.name, 100) || !text(row.phone, 20)) throw new Error('CUSTOMER_INVALID');
    if (row.address !== null && row.address !== undefined && typeof row.address !== 'string') {
      throw new Error('CUSTOMER_INVALID');
    }
    return {
      id, code: text(row.code, 50) ? row.code : '', name: row.name, phone: row.phone,
      address: text(row.address, 500) ? row.address.trim() : null,
      type: typeof row.type === 'number' ? row.type : 1,
      vipLevel: typeof row.vipLevel === 'number' ? row.vipLevel : null,
      vipActive: row.vipActive === true,
    };
  });
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

/** 单品与套餐共用的行校验；摘要与提交命令都必须先过这一步。 */
function assertSaleLine(line: SaleLine): void {
  if (isPackageLine(line)) {
    if (!isPositiveLongId(line.packageId) || !text(line.packageQuoteId, 120) || !packages(line.quantity)
      || !price(line.quotedUnitPrice)) throw new Error('SUMMARY_INVALID');
    return;
  }
  if (!isPositiveLongId(line.productId) || !text(line.unit, 32) || !quantity(line.quantity)
    || !price(line.quotedUnitPrice) || quantityScaled(line.quantity) <= 0) throw new Error('SUMMARY_INVALID');
}

/** summary 只保存可核对的有限事实，不含凭据、联系人或可重发请求体。
 *  套餐行只记 packageId + 套数，物料由后端按原报价展开，本机不伪造分摊。 */
export function toSaleSummary(lines: SaleLine[], options: SaleOptions = defaultSaleOptions): SaleSummary {
  if (!Array.isArray(lines) || lines.length === 0 || lines.length > 500) throw new Error('SUMMARY_INVALID');
  const parsed = lines.map(line => {
    assertSaleLine(line);
    if (isPackageLine(line)) {
      return { productId: null, packageId: line.packageId, unit: '套', quantity: line.quantity,
        lineCents: Math.round(toCents(line.quotedUnitPrice) * Number(line.quantity)) };
    }
    return { productId: line.productId, packageId: null, unit: line.unit,
      quantity: line.quantity,
      lineCents: Math.round(toCents(line.quotedUnitPrice) * quantityScaled(line.quantity) / 10000) };
  });
  const totalCents = parsed.reduce((sum, line) => sum + line.lineCents, 0);
  if (!Number.isSafeInteger(totalCents) || totalCents <= 0) throw new Error('SUMMARY_INVALID');
  return { totalCents, netCents: totalCents, paymentMethod: options.paymentType,
    customerId: options.customerId, lines: parsed };
}

/** 提交命令不含金额字段：成交价由后端按 quotedUnitPrice 重新报价并核对。
 *  套餐只提交 packageId/套数/packageQuoteId，物料与分摊由后端展开；前端不拆行。
 *  也不含收货人信息：门店配送由后端按 customerId 读客户档案带出，员工端不抄地址。 */
export function buildSaleCommand(requestKey: string, actor: RecoveryActor, lines: SaleLine[],
                                 options: SaleOptions = defaultSaleOptions): Record<string, unknown> {
  if (!isPositiveLongId(actor.staffId) || !isPositiveLongId(actor.storeId)) throw new Error('UNAUTHORIZED');
  resolveSaleOptions(options);
  // 命令本身也要挡非法行：调用方漏掉摘要校验时，不能让半套套餐发到后端。
  lines.forEach(assertSaleLine);
  return {
    requestKey, storeId: actor.storeId, staffId: actor.staffId,
    paymentType: options.paymentType, customerId: options.customerId,
    deliveryType: options.deliveryType,
    receiverName: null, receiverPhone: null, receiverAddress: null, remark: options.remark,
    items: lines.map(line => (isPackageLine(line)
      ? { packageId: line.packageId, itemType: 2, quantity: line.quantity, unit: '套',
          quotedUnitPrice: line.quotedUnitPrice, packageQuoteId: line.packageQuoteId }
      : { productId: line.productId, itemType: 1, quantity: line.quantity,
          unit: line.unit, quotedUnitPrice: line.quotedUnitPrice })),
  };
}

export const isOrderId = (v: unknown): v is unknown =>
  v !== null && v !== undefined && normalizeLongId(v) !== null;
