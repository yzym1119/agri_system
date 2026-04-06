import request from '@/utils/request'

/**
 * 售后管理 API
 */

/**
 * 创建退货售后（管理端）
 */
export function createAfterSales(data: {
  orderId: number
  refundAmount: number
  reason: string
  items: Array<{
    orderItemId: number
    quantity: number
    refundAmount: number
  }>
}): Promise<{ id: number }> {
  return request({
    url: '/api/admin/after-sales',
    method: 'post',
    data
  })
}

/**
 * 查询订单的售后记录
 */
export function getAfterSalesByOrderId(orderId: number): Promise<any[]> {
  return request({
    url: `/api/admin/after-sales/order/${orderId}`,
    method: 'get'
  })
}









