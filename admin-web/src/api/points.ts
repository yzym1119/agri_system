import request from '@/utils/request'
import type {
    PointsAuditRequest,
    PointsExchangeOrderVO,
    PointsGoodsSaveRequest,
    PointsGoodsVO,
    PointsNotifyRequest,
    PointsOrderQuery,
    PointsVerifyRequest
} from '@/types/points'
import type {PageResponse} from '@/types/store'

/**
 * 积分商城API
 */

// ==================== 积分礼品管理 ====================

/**
 * 查询积分礼品列表
 */
export function getPointsGoodsList(): Promise<PointsGoodsVO[]> {
  return request({
    url: '/api/admin/points/goods',
    method: 'get'
  }).then((response: any) => {
    if (Array.isArray(response)) {
      return response
    } else if (response && response.data && Array.isArray(response.data)) {
      return response.data
    }
    return []
  })
}

/**
 * 创建积分礼品
 */
export function createPointsGoods(data: PointsGoodsSaveRequest): Promise<PointsGoodsVO> {
  return request({
    url: '/api/admin/points/goods',
    method: 'post',
    data
  })
}

/**
 * 更新积分礼品
 */
export function updatePointsGoods(id: number, data: PointsGoodsSaveRequest): Promise<PointsGoodsVO> {
  return request({
    url: `/api/admin/points/goods/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除积分礼品
 */
export function deletePointsGoods(id: number): Promise<void> {
  return request({
    url: `/api/admin/points/goods/${id}`,
    method: 'delete'
  })
}

/**
 * 上架积分礼品
 */
export function onlinePointsGoods(id: number): Promise<void> {
  return request({
    url: `/api/admin/points/goods/${id}/on`,
    method: 'post'
  })
}

/**
 * 下架积分礼品
 */
export function offlinePointsGoods(id: number): Promise<void> {
  return request({
    url: `/api/admin/points/goods/${id}/off`,
    method: 'post'
  })
}

// ==================== 积分兑换单管理 ====================

/**
 * 查询积分兑换单列表
 */
export function getPointsOrderList(params: PointsOrderQuery): Promise<PageResponse<PointsExchangeOrderVO>> {
  return request({
    url: '/api/admin/points/orders',
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
        pageNum: page.pageNum ?? page.current ?? params.pageNum ?? 1,
        pageSize: page.pageSize ?? page.size ?? params.pageSize ?? 10
      }
    }
    return { records: [], total: 0, pageNum: params.pageNum || 1, pageSize: params.pageSize || 10 }
  })
}

/**
 * 审核积分兑换单
 */
export function auditPointsOrder(data: PointsAuditRequest): Promise<void> {
  return request({
    url: '/api/admin/points/orders/audit',
    method: 'post',
    data
  })
}

/**
 * 通知客户取货
 */
export function notifyPointsPickup(data: PointsNotifyRequest): Promise<void> {
  return request({
    url: '/api/admin/points/orders/notify',
    method: 'post',
    data
  })
}

/**
 * 核销积分兑换单
 */
export function verifyPointsOrder(data: PointsVerifyRequest): Promise<PointsExchangeOrderVO> {
  return request({
    url: '/api/admin/points/orders/verify',
    method: 'post',
    data
  })
}









