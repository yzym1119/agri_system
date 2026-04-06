/**
 * 积分商城相关类型定义
 */

/**
 * 积分礼品VO
 */
export interface PointsGoodsVO {
  id: number
  code: string
  name: string
  points: number
  stock: number
  imageUrl?: string
  description?: string
  status: number // 1-上架，0-下架
  sortOrder?: number
}

/**
 * 积分礼品保存请求
 */
export interface PointsGoodsSaveRequest {
  name: string
  points: number
  stock: number
  imageUrl?: string
  description?: string
  sortOrder?: number
}

/**
 * 积分兑换单VO
 */
export interface PointsExchangeOrderVO {
  id: number
  exchangeNo: string
  customerId: number
  customerName?: string
  goodsId: number
  goodsName?: string
  goodsImage?: string
  points: number
  quantity: number
  status: number
  statusDesc?: string
  pickupCode?: string
  receiverName?: string
  receiverPhone?: string
  receiverAddress?: string
  remark?: string
  createdAt?: string
}

/**
 * 积分兑换单查询参数
 */
export interface PointsOrderQuery {
  customerId?: number
  status?: number
  pageNum?: number
  pageSize?: number
}

/**
 * 审核积分兑换单请求
 */
export interface PointsAuditRequest {
  orderId: number
  pass: boolean
  remark?: string
}

/**
 * 通知取货请求
 */
export interface PointsNotifyRequest {
  orderId: number
  logisticsCompany?: string
  trackingNo?: string
}

/**
 * 核销请求
 */
export interface PointsVerifyRequest {
  pickupCode: string
}









