import request from '@/utils/request'
import type {POSOrderCreateRequest} from '@/types/pos'
import type {OrderDetail, OrderListQuery, OrderPageResponse} from '@/types/order'

/**
 * 订单管理 API
 */

/**
 * POS 创建订单
 */
export function createPOSOrder(data: POSOrderCreateRequest): Promise<{ id: number }> {
  return request({
    url: '/api/admin/orders/pos',
    method: 'post',
    data
  }).then((response: any) => {
    if (response && response.data) {
      return response.data
    }
    return response
  })
}

/**
 * 分页查询订单列表
 */
export function getOrderList(params: OrderListQuery): Promise<OrderPageResponse> {
  return request({
    url: '/api/admin/orders',
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

/**
 * 查询订单详情
 */
export function getOrderDetail(orderId: number): Promise<OrderDetail> {
  return request({
    url: `/api/admin/orders/${orderId}`,
    method: 'get'
  })
}

/**
 * 自提核销
 */
export function verifyPickup(data: { pickupCode: string }): Promise<void> {
  return request({
    url: '/api/admin/orders/pickup/verify',
    method: 'post',
    data
  })
}

/**
 * 发货
 */
export function shipOrder(orderId: number, data: {
  logisticsCompany?: string
  trackingNo?: string
  deliveryStaffId?: number
}): Promise<void> {
  return request({
    url: `/api/admin/orders/${orderId}/ship`,
    method: 'post',
    data
  })
}

/**
 * 退款
 */
export function refundOrder(orderId: number, data: {
  refundAmount: number
  refundReason: string
  items?: Array<{
    orderItemId: number
    quantity: number
    refundAmount: number
  }>
}): Promise<void> {
  return request({
    url: `/api/admin/orders/${orderId}/refund`,
    method: 'post',
    data
  })
}

/**
 * 确认收货（管理员强制确认）
 */
export function confirmReceipt(orderId: number): Promise<void> {
  return request({
    url: `/api/admin/orders/${orderId}/confirm-receipt`,
    method: 'post'
  })
}
