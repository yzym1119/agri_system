/**
 * 套餐管理相关类型定义
 */
import type { ProductUnitVO } from './product'

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
  saleUnits?: ProductUnitVO[] // 套餐档案维护物料时的可销售单位
  saleUnitId?: number
  unitPrice?: number // 后端分摊成交单价，可能超过两位小数
  totalPrice?: number // 后端权威物料成交金额，金额按分保存
  allowSpecialFund?: number
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
  stock?: number | null // 本店可售套数；null 表示单位或库存资料待核对
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
