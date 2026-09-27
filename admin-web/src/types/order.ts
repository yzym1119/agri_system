import type { PageRequest } from './api'

export interface OrderPaymentRecord {
  id: number
  paymentType: number
  paymentTypeName?: string
  amount: number
  couponId?: number | null
  outerTransNo?: string | null
  createdAt?: string | null
}

export interface OrderItemSummary {
  id: number
  productId?: number | null
  packageId?: number | null
  itemType?: number | null
  productName: string
  quantity: number
  unit?: string | null
  unitPrice: number
  totalPrice: number
  refundedQuantity?: number
  refundedAmount?: number
  refundableQuantity?: number
  refundableAmount?: number
  specification?: string | null
  manufacturer?: string | null
}

export interface OrderAfterSalesRecord {
  id?: number
  afterSalesNo?: string
  type?: number
  status?: number
  refundAmount?: number
  reason?: string
  createdAt?: string | null
  refundTime?: string | null
}

export interface OrderBase {
  id: number
  orderNo: string
  orderType: number
  orderStatus: number
  totalAmount: number
  discountAmount?: number
  couponAmount?: number
  payAmount: number
  receivedAmount?: number
  creditAmount?: number
  paymentMethod?: number | null
  paymentMethodName?: string | null
  customerId?: number | null
  customerName?: string | null
  customerPhone?: string | null
  customer?: {
    phone?: string | null
  } | null
  receiverName?: string | null
  receiverPhone?: string | null
  receiverAddress?: string | null
  createdAt?: string | null
  items?: OrderItemSummary[]
  payments?: OrderPaymentRecord[]
}

export interface OrderPageItem extends OrderBase {
  storeId?: number | null
  storeName?: string | null
  deliveryType?: number | null
  pickupCode?: string | null
}

export interface OrderDeliveryInfo {
  deliveryType?: number | null
  logisticsCompany?: string | null
  trackingNo?: string | null
  deliveryStaffName?: string | null
}

export interface OrderDetail extends OrderBase {
  storeId?: number | null
  storeName?: string | null
  storeAddress?: string | null
  pickupCode?: string | null
  operatorName?: string | null
  remark?: string | null
  delivery?: OrderDeliveryInfo | null
  afterSalesList?: OrderAfterSalesRecord[]
}

export interface OrderListQuery extends PageRequest {
  keyword?: string
  storeId?: number | null
  customerId?: number | null
  orderType?: number | null
  deliveryType?: number | null
  startTime?: string
  endTime?: string
  orderStatus?: string | null
}

export interface OrderPageResponse {
  records: OrderPageItem[]
  total?: number
  totalRow?: number
  pageNum?: number
  pageSize?: number
  pageNumber?: number
}

/**
 * 取货待办工作项VO（零售事实链 Task9：库房取货待办）
 */
export interface PickupWorkItemVO {
  /** 取货请求ID */
  id: number
  orderId?: number | null
  /** 订单号；订单缺失时为 null */
  orderNo?: string | null
  orderItemId?: number | null
  productId?: number | null
  productCode?: string | null
  productName?: string | null
  /** 请求拣货数量（基准单位） */
  requestedBaseQuantity?: number | null
  sourceLocationId?: number | null
  sourceLocationName?: string | null
  /** PENDING/FULFILLED */
  status?: string | null
  createdAt?: string | null
}

/**
 * 创建库房取货请求（零售事实链 Task5）
 */
export interface PickupRequestCreateDTO {
  orderItemId: number
  sourceLocationId?: number | null
}

/**
 * 完成库房取货（扫描内部批次标签或显式确认批次与货位）
 */
export interface PickupFulfillmentDTO {
  scannedCode?: string | null
  batchId?: number | null
  locationId?: number | null
}
