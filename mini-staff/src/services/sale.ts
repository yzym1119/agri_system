import { staffAuth } from './auth';
import { posRecovery } from './pos-recovery';
import {
  isCatalogEnvelope, decodeCatalog, decodeCustomers, decodeQuote, toSaleSummary, buildSaleCommand, isOrderId,
  resolveSaleOptions,
  type CatalogProduct, type PosCustomer, type SaleLine, type PriceQuote, type SaleOptions,
} from '@agri/mobile-contracts/sale';

/** 目录与报价是 {code:"200",data} 自定义包装；通用 R 解码不适用，这里显式解包。 */
const envelope = (value: unknown): value is Record<string, unknown> => isCatalogEnvelope(value);

/** 商品档案（全公司同一份，与后台商品管理同源），不是本店临时数据。 */
export const readCatalog = async (epoch: number): Promise<CatalogProduct[]> => {
  const body = await staffAuth.request({
    url: '/api/admin/pos/catalog/products?pageNum=1&pageSize=200', method: 'GET',
  }, envelope, epoch);
  return decodeCatalog(body);
};

/** 服务端关键词搜索（编码/名称/条码），商品超首页 200 条时仍可找到。 */
export const searchCatalog = async (keyword: string, epoch: number): Promise<CatalogProduct[]> => {
  const body = await staffAuth.request({
    url: '/api/admin/pos/catalog/products?pageNum=1&pageSize=50&keyword=' + encodeURIComponent(keyword),
    method: 'GET',
  }, envelope, epoch);
  return decodeCatalog(body);
};

/** 本店库存（只读参考）：门店由后端按登录员工解析，前端不传 storeId 以免读错店。 */
export const readStock = async (epoch: number): Promise<Record<string, { quantity: string; unit: string }>> => {
  const body = await staffAuth.request({
    url: '/api/admin/pos/catalog/inventory?pageNum=1&pageSize=500', method: 'GET',
  }, envelope, epoch);
  if (!isCatalogEnvelope(body) || !body.data || typeof body.data !== 'object') return {};
  const rows = (body.data as { records?: unknown }).records;
  if (!Array.isArray(rows)) return {};
  const stock: Record<string, { quantity: string; unit: string }> = {};
  for (const row of rows) {
    if (!row || typeof row !== 'object') continue;
    const value = row as Record<string, unknown>;
    const id = typeof value.productId === 'number' || typeof value.productId === 'string' ? String(value.productId) : '';
    const quantity = typeof value.totalQuantity === 'number' || typeof value.totalQuantity === 'string'
      ? String(value.totalQuantity) : '';
    if (!id || !quantity) continue;
    stock[id] = { quantity, unit: typeof value.unit === 'string' ? value.unit : '' };
  }
  return stock;
};

/** 客户档案检索（姓名/编码/手机号）；关键字为空时返回最近建档的客户，供直接挑选。 */
export const searchCustomers = async (keyword: string, epoch: number): Promise<PosCustomer[]> => {
  const query = keyword.trim() ? '&keyword=' + encodeURIComponent(keyword.trim()) : '';
  const body = await staffAuth.request({
    url: '/api/admin/pos/catalog/customers?limit=20' + query, method: 'GET',
  }, envelope, epoch);
  return decodeCustomers(body);
};

export const quoteProduct = async (
  productId: string,
  unit: string,
  epoch: number,
  customerId: string | null = null,
): Promise<PriceQuote> => {
  const customer = customerId ? '&customerId=' + encodeURIComponent(customerId) : '';
  // 门店随报价一起传：接口本来就按门店解析价格，不传等于默认把门店维度丢掉。
  const store = staffAuth.current()?.storeId;
  const storeParam = store ? '&storeId=' + encodeURIComponent(store) : '';
  const body = await staffAuth.request({
    url: '/api/admin/products/' + encodeURIComponent(productId)
      + '/price-quote?saleUnit=' + encodeURIComponent(unit) + customer + storeParam,
    method: 'GET',
  }, envelope, epoch);
  return decodeQuote(body, productId);
};

/** 唯一销售写入口：交给 V133 持久请求协调器，结果未知只返回 VERIFYING，不自动换号重收。 */
export function submitSale(lines: SaleLine[], epoch: number, options: Partial<SaleOptions> = {}) {
  const actor = staffAuth.current();
  if (!actor || actor.epoch !== epoch) throw new Error('STALE_SESSION');
  const resolved: SaleOptions = resolveSaleOptions(options);
  const summary = toSaleSummary(lines, resolved);
  return posRecovery.submitOnce(summary, (requestKey, recoveryActor) => staffAuth.request({
    url: '/api/admin/orders/pos', method: 'POST',
    data: buildSaleCommand(requestKey, recoveryActor, lines, resolved),
  }, isOrderId, recoveryActor.epoch));
}
