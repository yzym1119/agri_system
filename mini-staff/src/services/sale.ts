import { staffAuth } from './auth';
import { posRecovery } from './pos-recovery';
import {
  isCatalogEnvelope, decodeCatalog, decodeQuote, toSaleSummary, buildSaleCommand, isOrderId,
  resolveSaleOptions,
  type CatalogProduct, type SaleLine, type PriceQuote, type SaleOptions,
} from '@agri/mobile-contracts/sale';

/** 目录与报价是 {code:"200",data} 自定义包装；通用 R 解码不适用，这里显式解包。 */
const envelope = (value: unknown): value is Record<string, unknown> => isCatalogEnvelope(value);

export const readCatalog = async (epoch: number): Promise<CatalogProduct[]> => {
  const body = await staffAuth.request({
    url: '/api/admin/pos/catalog/products?pageNum=1&pageSize=200', method: 'GET',
  }, envelope, epoch);
  return decodeCatalog(body);
};

export const quoteProduct = async (productId: string, unit: string, epoch: number): Promise<PriceQuote> => {
  const body = await staffAuth.request({
    url: '/api/admin/products/' + encodeURIComponent(productId) + '/price-quote?saleUnit=' + encodeURIComponent(unit),
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
