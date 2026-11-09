import request from '@/utils/request'
import type {ProductDetailVO, CategoryTreeVO, PageResponse} from '@/types/product'
import type {ProductPackageDetailVO} from '@/types/package'
import type {InventoryPageVO} from '@/types/inventory'

export function getPosProductList(params: {pageNum?:number;pageSize?:number;status?:number}):Promise<PageResponse<ProductDetailVO>> {
  return request({url:'/api/admin/pos/catalog/products',method:'get',params})
}
export function getPosPackageList(params: {storeId?:number;pageNum?:number;pageSize?:number;status?:number}):Promise<PageResponse<ProductPackageDetailVO>> {
  return request({url:'/api/admin/pos/catalog/packages',method:'get',params})
}
export function getPosInventoryList(params: {storeId?:number;pageNum?:number;pageSize?:number}):Promise<PageResponse<InventoryPageVO>> {
  return request({url:'/api/admin/pos/catalog/inventory',method:'get',params})
}
export function getPosCategoryTree():Promise<CategoryTreeVO[]> {
  return request({url:'/api/admin/pos/catalog/categories',method:'get'})
}

export interface StockPreview {storeId:string;sufficient:boolean;issues:string[]}
/** 只读物料核对，不创建订单或收款请求。 */
export async function checkPosStock(data:{storeId:number;customerId:number|null;items:Record<string,unknown>[]}):Promise<StockPreview> {
  const result:StockPreview=await request({url:'/api/admin/pos/catalog/stock-preview',method:'post',data})
  if (!result || typeof result.storeId!=='string' || result.storeId!==String(data.storeId)
      || typeof result.sufficient!=='boolean' || !Array.isArray(result.issues) || result.issues.length>1000
      || !result.issues.every(issue=>typeof issue==='string' && !!issue.trim() && issue.length<=1000)
      || result.sufficient!==(result.issues.length===0)) throw Error('STOCK_PREVIEW_INVALID')
  return result
}
