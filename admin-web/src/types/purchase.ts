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


