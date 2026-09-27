import request from '@/utils/request'
import type {POSOrderCreateRequest} from '@/types/pos'
import type {
  OrderDetail,
  OrderListQuery,
  OrderPageResponse,
  PickupFulfillmentDTO,
  PickupRequestCreateDTO,
  PickupWorkItemVO
} from '@/types/order'

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

/**
 * 查询门店取货待办工作项（零售事实链 Task9：库房取货工作台）
 * status 缺省为 PENDING
 */
export function getPickupWorkItems(storeId: number, status?: string): Promise<PickupWorkItemVO[]> {
  return request({
    url: '/api/admin/orders/pickup/requests',
    method: 'get',
    params: { storeId, status: status ?? undefined }
  }).then((response: any) => {
    if (Array.isArray(response)) return response
    if (response && Array.isArray(response.data)) return response.data
    return []
  })
}

/**
 * 创建库房取货请求（货架缺货或顾客要求后仓拣货时登记）
 */
export function createPickupRequest(orderId: number, data: PickupRequestCreateDTO): Promise<number> {
  return request({
    url: `/api/admin/orders/${orderId}/pickup/requests`,
    method: 'post',
    data
  }).then((response: any) => {
    if (response && typeof response === 'object' && 'data' in response) {
      return response.data
    }
    return response
  })
}

/**
 * 完成库房取货：扫描内部批次标签或显式确认批次与货位
 */
export function fulfillPickupRequest(requestId: number, data: PickupFulfillmentDTO): Promise<void> {
  return request({
    url: `/api/admin/orders/pickup/requests/${requestId}/fulfill`,
    method: 'post',
    data
  })
}
