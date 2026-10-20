import request from '@/utils/request'
import type {
    PurchaseCancelRequest,
    PurchaseCreateRequest,
    PurchaseDetailVO,
    PurchasePageQuery,
    PurchasePageVO,
    PurchaseSubmitRequest,
    ReceivePurchaseRequest
} from '@/types/purchase'
import type { PageResponse } from '@/types/store'

/**
 * 采购管理 API
 */

// 分页查询采购单
export function getReceivingPurchaseList(params: PurchasePageQuery): Promise<PageResponse<PurchasePageVO>> {
  return request({url:'/api/admin/purchase/receiving-orders',method:'get',params}).then((page:any)=>({
    records:page?.records||[],total:page?.totalRow??page?.total??0,
    pageNum:page?.pageNumber??1,pageSize:page?.pageSize??params.pageSize??10
  }))
}
export function getReceivingPurchaseDetail(orderId:number):Promise<PurchaseDetailVO> {
  return request({url:`/api/admin/purchase/receiving-orders/${orderId}`,method:'get'})
}

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

/**
 * 采购实际收货（零售事实链 Task9：按批次登记原始单位数量/单价，支持日期未知）
 */
export function receivePurchase(data: ReceivePurchaseRequest): Promise<{ receiptId: number; batches: import('@/types/purchase').ReceiptBatchLabel[] }> {
  return request({
    url: '/api/admin/purchase/receipts',
    method: 'post',
    data
  }).then((response: any) => response?.data || response)
}

