/**
 * 商品管理相关类型定义
 */

/**
 * 商品分类树VO
 */
export interface CategoryTreeVO {
  id: number
  code: string
  name: string
  parentId?: number
  parentName?: string // 父分类名称
  level?: number
  sortOrder?: number
  status: number
  remark?: string
  createdAt?: string
  children?: CategoryTreeVO[]
}

/**
 * 创建分类请求
 */
export interface CategoryCreateRequest {
  code?: string // 可选，留空则后台自动生成
  name: string
  parentId?: number | null
  sortOrder?: number
  remark?: string
}

/**
 * 更新分类请求
 */
export interface CategoryUpdateRequest {
  id: number
  name: string
  parentId?: number | null
  sortOrder?: number
  status?: number
  remark?: string
}

/**
 * 商品详情VO
 */
export interface ProductDetailVO {
  id: number
  code: string
  name: string
  categoryId?: number
  categoryName?: string
  specification?: string
  dosageForm?: string
  manufacturer?: string
  mainUnit: string
  auxUnit?: string
  mainToAuxRatio?: number
  suggestedRetailPrice: number
  vipPrice?: number
  minPrice?: number
  avgCostPrice?: number
  imageUrl?: string
  detailText?: string
  isOnline: number
  onlinePrice?: number
  baseShippingFee?: number
  allowSpecialFund?: number
  hasStrategy?: boolean // 是否存在价格策略
  status: number
  remark?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * 创建商品请求
 */
export interface ProductCreateRequest {
  code?: string // 可选，后端自动生成
  name: string
  categoryId?: number
  specification?: string
  dosageForm?: string
  manufacturer?: string
  mainUnit: string
  auxUnit?: string
  mainToAuxRatio?: number
  suggestedRetailPrice: number
  vipPrice?: number
  minPrice?: number
  imageUrl?: string
  detailText?: string
  isOnline?: number
  onlinePrice?: number
  baseShippingFee?: number
  allowSpecialFund?: number
  remark?: string
}

/**
 * 更新商品请求
 */
export interface ProductUpdateRequest {
  id: number
  name: string
  categoryId?: number
  specification?: string
  dosageForm?: string
  manufacturer?: string
  mainUnit: string
  auxUnit?: string
  mainToAuxRatio?: number
  suggestedRetailPrice: number
  vipPrice?: number
  minPrice?: number
  imageUrl?: string
  detailText?: string
  isOnline?: number
  onlinePrice?: number
  baseShippingFee?: number
  allowSpecialFund?: number
  remark?: string
}

/**
 * 商品查询参数
 */
export interface ProductQueryParams {
  code?: string
  name?: string
  categoryId?: number
  isOnline?: number
  status?: number
  pageNum?: number
  pageSize?: number
  keyword?: string
}

/**
 * 分页响应
 */
export interface PageResponse<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

/**
 * 价格规则VO
 */
export interface ProductPriceRuleVO {
  id: number
  productId: number
  storeId?: number | null // null表示全局
  storeName?: string
  customerType?: number | null // 1-普通，2-VIP，null表示全部
  vipLevel?: number | null // 1-5，仅VIP类型时有效
  priceType: number // 1-建议零售价，2-VIP价，3-最低限价
  price: number
  effectiveDate?: string
  expiryDate?: string
  status: number // 1-启用，0-停用
  createdAt?: string
  updatedAt?: string
}

/**
 * 创建价格规则请求
 */
export interface ProductPriceRuleCreateRequest {
  productId: number
  storeId?: number | null // null表示全局
  customerType?: number | null // 1-普通，2-VIP，null表示全部
  vipLevel?: number | null // 1-5，仅VIP类型时有效
  priceType: number // 1-建议零售价，2-VIP价，3-最低限价
  price: number
  effectiveDate?: string
  expiryDate?: string
}

/**
 * 更新价格规则请求
 */
export interface ProductPriceRuleUpdateRequest {
  id: number
  price: number
  effectiveDate?: string
  expiryDate?: string
  status?: number
}

