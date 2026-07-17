/**
 * 套餐管理相关类型定义
 */

export type { PageResponse } from './api'

/**
 * 套餐明细DTO
 */
export interface PackageItemDTO {
  productId: number
  quantity: number
  unit: string
  sortOrder?: number
}

/**
 * 套餐明细VO（用于表单编辑）
 */
export interface ProductPackageItemVO {
  id?: number
  productId: number
  productName?: string
  productSpecification?: string
  quantity: number
  unit?: string
  sortOrder?: number
  // 商品详细信息（用于价格计算）
  productMainUnit?: string // 主单位
  productAuxUnit?: string // 辅单位
  productMainToAuxRatio?: number // 主辅换算比例
  productSuggestedRetailPrice?: number // 商品零售价（最小单位单价）
  currentUnitPrice?: number // 当前单位单价（计算后）
  subtotal?: number // 小计（单价 * 数量）
}

/**
 * 套餐详情VO
 */
export interface ProductPackageDetailVO {
  id: number
  code: string
  name: string
  suggestedRetailPrice: number
  vipPrice?: number
  minPrice?: number
  status: number
  remark?: string
  createdAt?: string
  updatedAt?: string
  items: ProductPackageItemVO[]
  stock?: number // 可销售库存数（理论最大库存，基于子商品库存计算）
}

/**
 * 创建套餐请求
 */
export interface ProductPackageCreateRequest {
  name: string
  suggestedRetailPrice: number
  vipPrice?: number
  minPrice?: number
  items: PackageItemDTO[]
  remark?: string
}

/**
 * 更新套餐请求
 */
export interface ProductPackageUpdateRequest {
  id: number
  name: string
  suggestedRetailPrice: number
  vipPrice?: number
  minPrice?: number
  items: PackageItemDTO[]
  remark?: string
}

/**
 * 套餐查询参数
 */
export interface ProductPackageQueryParams {
  code?: string
  name?: string
  status?: number
  storeId?: number // 门店ID（用于计算套餐库存）
  pageNum?: number
  pageSize?: number
}
