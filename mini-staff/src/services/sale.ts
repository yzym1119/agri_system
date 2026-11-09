import { staffAuth } from './auth';
import { posRecovery } from './pos-recovery';
import {
  isCatalogEnvelope, decodeCatalog, decodeCustomers, decodeQuote, decodePackages, decodePackageQuote,
  toSaleSummary, buildSaleCommand, isOrderId, resolveSaleOptions, buildStockCheckCommand, isStockPreview,
  type CatalogProduct, type PosCustomer, type SaleLine, type PriceQuote, type SaleOptions,
  type PosPackage, type PackageQuote,
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

/** 套餐目录：后端按当前员工门店算可售套数（子商品可用量整除取小），前端不再二次计算。
 *  该接口目前只支持分页、没有关键词参数，所以套餐搜索在本机按名称/编码过滤这一页。 */
export const readPackages = async (epoch: number): Promise<PosPackage[]> => {
  const body = await staffAuth.request({
    url: '/api/admin/pos/catalog/packages?pageNum=1&pageSize=200', method: 'GET',
  }, envelope, epoch);
  return decodePackages(body);
};

/** 套餐按套报价：每套价、总额与物料分摊都由后端算，quoteId 是唯一指纹。 */
export const quotePackage = async (
  packageId: string,
  packages: string,
  epoch: number,
  customerId: string | null = null,
): Promise<PackageQuote> => {
  const customer = customerId ? '&customerId=' + encodeURIComponent(customerId) : '';
  const store = staffAuth.current()?.storeId;
  const storeParam = store ? '&storeId=' + encodeURIComponent(store) : '';
  const body = await staffAuth.request({
    url: '/api/admin/product-packages/' + encodeURIComponent(packageId)
      + '/price-quote?quantity=' + encodeURIComponent(packages) + customer + storeParam,
    method: 'GET',
  }, envelope, epoch);
  return decodePackageQuote(body, packageId, packages);
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

/** 预检只读，失败不生成新的收款UUID或恢复记录。 */
export async function checkSaleStock(lines:SaleLine[],epoch:number,options:Partial<SaleOptions>={}) {
  const actor=staffAuth.current();if(!actor||actor.epoch!==epoch)throw Error('STALE_SESSION');
  const command=buildStockCheckCommand(actor,lines,resolveSaleOptions(options).customerId);
  const result=await staffAuth.request({url:'/api/admin/pos/catalog/stock-preview',method:'POST',data:command},isStockPreview,epoch);
  if(result.storeId!==actor.storeId)throw Error('STORE_MISMATCH');return result;
}
