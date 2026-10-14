import request from '@/utils/request'
import type {
  AfterSalesAuditRequest,
  AfterSalesDetail,
  AfterSalesPageItem,
  AfterSalesPageQuery,
  AfterSalesRefundRequest,
  PageResult
} from '@/types/afterSales'

/**
 * 售后管理 API
 */

/**
 * 创建退货售后（管理端）
 */
export function createAfterSales(data: {
  quoteId: string
  orderId: number
  refundAmount: number
  reason: string
  items: Array<{
    orderItemId: number
    quantity: number
    refundAmount: number
  }>
}): Promise<number> {
  return request({
    url: '/api/admin/after-sales',
    method: 'post',
    data
  })
}

export function getAfterSalesPage(params: AfterSalesPageQuery): Promise<PageResult<AfterSalesPageItem>> {
  return request({
    url: '/api/admin/after-sales',
    method: 'get',
    params
  }).then((page: any) => ({
    records: page?.records || [],
    total: page?.total ?? page?.totalRow ?? 0,
    totalRow: page?.totalRow ?? 0,
    pageNumber: page?.pageNumber ?? params.pageNum ?? 1,
    pageSize: page?.pageSize ?? params.pageSize ?? 20
  }))
}

export function getAfterSalesDetail(afterSalesId: number): Promise<AfterSalesDetail> {
  return request({
    url: `/api/admin/after-sales/${afterSalesId}`,
    method: 'get'
  })
}

export function auditAfterSales(afterSalesId: number, data: AfterSalesAuditRequest): Promise<void> {
  return request({
    url: `/api/admin/after-sales/${afterSalesId}/audit`,
    method: 'post',
    data
  })
}

export function refundAfterSales(afterSalesId: number, data: AfterSalesRefundRequest): Promise<void> {
  return request({
    url: `/api/admin/after-sales/${afterSalesId}/refund`,
    method: 'post',
    data
  })
}








