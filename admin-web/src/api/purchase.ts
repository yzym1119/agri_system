import request from '@/utils/request'
import type {
    PurchaseCancelRequest,
    PurchaseCreateRequest,
    PurchaseDetailVO,
    PurchaseInboundRequest,
    PurchasePageQuery,
    PurchasePageVO,
    PurchaseSubmitRequest
} from '@/types/purchase'
import type { PageResponse } from '@/types/store'

/**
 * 采购管理 API
 */

// 分页查询采购单
export function getPurchaseList(params: PurchasePageQuery): Promise<PageResponse<PurchasePageVO>> {
  return request({
    url: '/api/admin/purchase/list',
    method: 'get',
    params
  }).then((response: any) => {
    const page =
      (response && response.records ? response : null) ||
      (response && response.data && response.data.records ? response.data : null)
    if (page) {
      return {
        records: page.records || [],
        total: page.total ?? page.totalRow ?? 0,
        pageNum: page.pageNum ?? page.current ?? 1,
        pageSize: page.pageSize ?? page.size ?? params.pageSize ?? 10
      }
    }
    return { records: [], total: 0, pageNum: 1, pageSize: params.pageSize || 10 }
  })
}

// 创建采购单
export function createPurchase(data: PurchaseCreateRequest): Promise<{ orderId: number }> {
  return request({
    url: '/api/admin/purchase/create',
    method: 'post',
    data
  }).then((response: any) => response?.data || response)
}

// 提交采购单
export function submitPurchase(data: PurchaseSubmitRequest): Promise<void> {
  return request({
    url: '/api/admin/purchase/submit',
    method: 'post',
    data
  })
}

// 采购入库
export function inboundPurchase(data: PurchaseInboundRequest): Promise<void> {
  return request({
    url: '/api/admin/purchase/inbound',
    method: 'post',
    data
  })
}

// 取消采购单
export function cancelPurchase(data: PurchaseCancelRequest): Promise<void> {
  return request({
    url: '/api/admin/purchase/cancel',
    method: 'post',
    data
  })
}

// 查询采购单详情
export function getPurchaseDetail(orderId: number): Promise<PurchaseDetailVO> {
  return request({
    url: `/api/admin/purchase/${orderId}`,
    method: 'get'
  }).then((response: any) => response?.data || response)
}

