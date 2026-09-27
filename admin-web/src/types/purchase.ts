/**
 * 采购管理相关类型定义
 */

export interface PurchasePageQuery {
  storeId?: number
  supplierName?: string
  orderNo?: string
  status?: number
  createdStartTime?: string
  createdEndTime?: string
  pageNum?: number
  pageSize?: number
}

export interface PurchasePageVO {
  id: number
  orderNo: string
  storeId?: number
  storeName?: string
  supplierName?: string
  totalAmount?: number
  status: number
  remark?: string
  createdAt?: string
}

export interface PurchaseItemDTO {
  productId: number
  qty: number
  unit?: string
  price: number
}

export interface PurchaseCreateRequest {
  storeId: number
  supplierId?: number
  supplierName?: string
  items: PurchaseItemDTO[]
  remark?: string
}

export interface PurchaseSubmitRequest {
  orderId: number
}

export interface PurchaseCancelRequest {
  orderId: number
}

export interface PurchaseInboundItem {
  productId: number
  quantity: number
  costPrice: number
  productionDate?: string
  expireDate?: string
}

export interface PurchaseInboundRequest {
  orderId: number
  items?: PurchaseInboundItem[]
}

export interface PurchaseItemVO {
  id?: number
  productId: number
  productCode?: string
  productName?: string
  specification?: string
  manufacturer?: string
  qty: number
  unit?: string
  price: number
  amount?: number
}

export interface PurchaseDetailVO {
  id: number
  orderNo: string
  storeId: number
  storeName?: string
  supplierId?: number
  supplierName?: string
  totalAmount?: number
  status: number
  statusDesc?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
  items: PurchaseItemVO[]
}

/**
 * 收货批次（零售事实链 Task9：一个收货行可包含多个批次）
 * 日期未知时不填 productionDate/expiryDate，由后端标记批次日期未知
 */
export interface ReceiptBatchCommand {
  supplierBatchNo?: string | null
  /** 原始单位（如 箱/盒） */
  originalUnit: string
  /** 原始单位数量，须大于 0 */
  originalQuantity: number
  /** 原始单位单价，须大于 0 */
  originalUnitCost: number
  productionDate?: string | null
  expiryDate?: string | null
  inspectionReference?: string | null
}

/**
 * 收货行（采购明细维度，可含两个及以上批次）
 */
export interface ReceiptLineCommand {
  purchaseOrderItemId: number
  productId: number
  remark?: string | null
  batches: ReceiptBatchCommand[]
}

/**
 * 采购实际收货请求（零售事实链 Task9 收货界面）
 */
export interface ReceivePurchaseRequest {
  purchaseOrderId: number
  lines: ReceiptLineCommand[]
  /** 例外说明（如数量短缺、批次异常） */
  exceptionReason?: string | null
  remark?: string | null
}


