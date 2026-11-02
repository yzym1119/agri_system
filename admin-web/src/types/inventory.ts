/**
 * 库存管理相关类型定义
 */

export interface InventoryQuery {
  storeId?: number
  productName?: string
  onlyHasStock?: boolean
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
  requestKey: string
  unit?: string
  fromStoreId: number
  toStoreId: number
  productId: number
  quantity: number
  remark?: string
}

export interface InventoryTransferBatchDetail {
  batchId: number
  batchNo?: string
  expireDate?: string
  costPrice?: number
  quantity?: number
}

export interface InventoryTransferLinkDetail {
  fromBatchId?: number
  toBatchId?: number
  quantity?: number
  costPrice?: number
}

export interface InventoryTransferResult {
  transferId?: number
  transferNo?: string
  fromStoreId?: number
  toStoreId?: number
  productId?: number
  quantity?: number
  batchDetails?: InventoryTransferBatchDetail[]
  transferBatchDetails?: InventoryTransferLinkDetail[]
}

export interface InventoryAdjustRequest {
  requestKey: string
  expectedQuantity: number
  actualQuantity?: number
  unit?: string
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

export interface InventoryTransferRecordQuery {
  fromStoreId?: number
  toStoreId?: number
  productName?: string
  startTime?: string
  endTime?: string
  pageNum?: number
  pageSize?: number
}

export interface InventoryTransferRecordVO {
  transferId: number
  transferNo: string
  fromStoreId?: number
  fromStoreName?: string
  toStoreId?: number
  toStoreName?: string
  productId?: number
  productName?: string
  productSpec?: string
  quantity?: number
  batchCount?: number
  createdBy?: number
  createdByName?: string
  createdAt?: string
  remark?: string
}

/**
 * 货位VO（零售事实链 Task9：货位作业界面）
 */
export interface InventoryLocationVO {
  id: number
  storeId?: number
  /** 货位类型：SALES_FLOOR/STORE_BACKROOM/CENTRAL_WAREHOUSE/OTHER_STORE */
  locationType?: string
  /** 货位编码（门店内唯一，扫码解析用） */
  code?: string
  name?: string
  /** 状态：1启用 0停用 */
  status?: number
  createdAt?: string
}

/**
 * 库存风险建议VO（零售事实链 Task8/Task9：只提示，不自动执行）
 */
export interface InventoryRiskVO {
  kind: string
  productId?: number | null
  productName?: string | null
  productCode?: string | null
  storeId?: number | null
  batchId?: number | null
  internalReceiptBatchNo?: string | null
  currentStock?: number | null
  /** 数量与阈值的商品主单位。 */
  unit?: string | null
  minStock?: number | null
  maxStock?: number | null
  dateStatus?: string | null
  productionDate?: string | null
  expiryDate?: string | null
  daysToExpiry?: number | null
  observationWindowDays?: number | null
  evidenceCount?: number | null
  confidence?: string | null
  message?: string | null
}

/**
 * 库存风险人工决策请求（仅追加留痕，不触发自动动作）
 */
export interface InventoryDecisionRequest {
  riskKind: string
  /** PRODUCT/BATCH */
  entityType: string
  entityId: number
  suggestedAction?: string | null
  /** CONTINUE_SALE/INSPECT/REPLENISH/TRANSFER/RETURN_TO_VENDOR/WRITE_OFF/PAUSE_REPLENISHMENT/NO_ACTION */
  decisionAction: string
  decisionReason: string
}
