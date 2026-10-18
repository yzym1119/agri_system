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
  baseUnitName?: string | null
  allowFractionalBaseQuantity?: boolean | null
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
  pricingUnit?: string
  units?: ProductUnitVO[]
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
  barcode?: string
  registrationNo?: string
  storageConditions?: string
  shelfLifeDays?: number
  usageMethod?: string
  minStock?: number
  maxStock?: number
  reorderQuantity?: number
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
  barcode?: string
  registrationNo?: string
  storageConditions?: string
  shelfLifeDays?: number
  usageMethod?: string
  minStock?: number
  maxStock?: number
  reorderQuantity?: number
}

/**
 * 更新商品请求
 */
export interface ProductUpdateRequest {
  id?: number
  name?: string
  categoryId?: number
  specification?: string
  dosageForm?: string
  manufacturer?: string
  mainUnit?: string
  auxUnit?: string
  mainToAuxRatio?: number
  suggestedRetailPrice?: number
  vipPrice?: number
  minPrice?: number
  imageUrl?: string
  detailText?: string
  isOnline?: number
  onlinePrice?: number
  baseShippingFee?: number
  allowSpecialFund?: number
  remark?: string
  barcode?: string
  registrationNo?: string
  storageConditions?: string
  shelfLifeDays?: number
  usageMethod?: string
  minStock?: number
  maxStock?: number
  reorderQuantity?: number
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
  /** 销售单位（零售事实链 Task7/9：按销售单位报价，可空） */
  saleUnit?: string | null
  /** 渠道（可空） */
  channel?: string | null
  /** 规则版本（空则后端默认 "1"） */
  ruleVersion?: string | null
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
  status?: number
  /** 销售单位（可空，空则由后端按基准单位处理） */
  saleUnit?: string | null
  /** 渠道（可空） */
  channel?: string | null
  /** 规则版本（留空不提交，由后端默认 "1"） */
  ruleVersion?: string | null
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
  /** 销售单位（非空时更新） */
  saleUnit?: string | null
  /** 渠道（非空时更新） */
  channel?: string | null
  /** 规则版本（留空不提交，不覆盖） */
  ruleVersion?: string | null
}

/**
 * 商品单位VO（零售事实链 Task9：多单位作业界面）
 */
export interface ProductUnitVO {
  id?: number
  productId?: number
  unitName: string
  /** 1 个该单位 = N 个基准单位 */
  baseQuantityPerUnit: number
  /** 1-基准单位 0-非基准单位 */
  isBaseUnit: number
  allowPurchase?: number
  allowSale?: number
  allowInventory?: number
  allowTransfer?: number
  barcode?: string | null
  status?: number
  /** 历史单位（只读，不可修改） */
  readOnly?: boolean
  projectionStatus?: string
}

/**
 * 商品单位新增/替换请求（PUT 全量替换）
 */
export interface ProductUnitUpsertCommand {
  id?: number | null
  unitName: string
  baseQuantityPerUnit: number
  isBaseUnit: number
  allowPurchase?: number
  allowSale?: number
  allowInventory?: number
  allowTransfer?: number
  barcode?: string | null
  status?: number
}
