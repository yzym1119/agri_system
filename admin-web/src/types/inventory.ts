/**
 * 库存管理相关类型定义
 */

export interface InventoryQuery {
  storeId?: number
  productName?: string
  pageNum?: number
  pageSize?: number
}

export interface InventoryPageVO {
  productId: number
  productName?: string
  specification?: string
  manufacturer?: string
  storeId?: number
  storeName?: string
  totalQuantity?: number
  unit?: string
  mainUnit?: string
  auxUnit?: string
  mainToAuxRatio?: number
}

export interface BatchDetailVO {
  id: number
  storeId?: number
  productId?: number
  batchNo?: string
  productionDate?: string
  expireDate?: string
  quantity?: number
  costPrice?: number
  isActive?: number
  isExpired?: boolean
  isExpiringSoon?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface InventoryDetailVO {
  id?: number
  storeId: number
  productId: number
  quantity: number
  storeName?: string
  productName?: string
  unit?: string
  batches?: BatchDetailVO[]
}

export interface InventoryTransferRequest {
  fromStoreId: number
  toStoreId: number
  productId: number
  quantity: number
}

export interface InventoryAdjustRequest {
  storeId: number
  productId: number
  quantityDiff: number
  type: number // 1-盘盈，2-盘亏
  remark?: string
}

export interface InventoryTransactionQuery {
  storeId?: number
  productName?: string
  type?: number
  startTime?: string
  endTime?: string
  pageNum?: number
  pageSize?: number
}

export interface InventoryTransactionVO {
  id: number
  storeId?: number
  storeName?: string
  productId?: number
  productName?: string
  productSpec?: string
  manufacturer?: string
  quantity?: number
  afterQuantity?: number
  type?: number
  typeName?: string
  referenceNo?: string
  createdBy?: number
  createdByName?: string
  createdAt?: string
  remark?: string
  mainUnit?: string
  auxUnit?: string
  mainToAuxRatio?: number
}

export interface PageResponse<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}


