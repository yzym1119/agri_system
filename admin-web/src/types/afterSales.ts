import type { PageRequest } from './api'

export interface AfterSalesPageQuery extends PageRequest {
  keyword?: string
  storeId?: number
  type?: number
  status?: number
  startTime?: string
  endTime?: string
}

export interface AfterSalesItem {
  id?: number
  orderItemId?: number
  productId?: number
  productName?: string
  unit?: string
  quantity?: number
  refundAmount?: number
}

export interface AfterSalesPageItem {
  id: number
  afterSalesNo?: string
  orderId?: number
  orderNo?: string
  orderStatus?: number
  customerId?: number
  customerName?: string
  customerPhone?: string
  storeId?: number
  storeName?: string
  type?: number
  status?: number
  refundAmount?: number
  creditReductionAmount?: number | null
  cashRefundAmount?: number | null
  reason?: string
  auditRemark?: string
  auditBy?: number
  auditByName?: string
  auditTime?: string
  refundBy?: number
  refundByName?: string
  refundTime?: string
  createdAt?: string
}

export interface AfterSalesDetail extends AfterSalesPageItem {
  items?: AfterSalesItem[]
}

export interface AfterSalesAuditRequest {
  approved: boolean
  remark?: string
}

export interface AfterSalesRefundRequest {
  refundAmount?: number
  quoteId: string
}

export interface PageResult<T> {
  records: T[]
  total?: number
  totalRow?: number
  pageNum?: number
  pageSize?: number
  pageNumber?: number
}
