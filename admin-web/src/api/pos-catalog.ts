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
