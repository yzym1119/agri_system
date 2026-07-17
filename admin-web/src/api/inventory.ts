import request from '@/utils/request'
import type {
    InventoryAdjustRequest,
    InventoryDetailVO,
    InventoryPageVO,
    InventoryQuery,
    InventoryTransferRecordQuery,
    InventoryTransferRecordVO,
    InventoryTransactionQuery,
    InventoryTransactionVO,
    InventoryTransferResult,
    InventoryTransferRequest
} from '@/types/inventory'
import type { PageResponse } from '@/types/store'

/**
 * 库存管理 API
 */

// 分页查询库存快照
export function getInventoryList(params: InventoryQuery): Promise<PageResponse<InventoryPageVO>> {
  return request({
    url: '/api/admin/inventory/list',
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

// 库存详情（含批次）
export function getInventoryDetail(storeId: number, productId: number): Promise<InventoryDetailVO> {
  return request({
    url: '/api/admin/inventory/detail',
    method: 'get',
    params: { storeId, productId }
  }).then((response: any) => {
    if (response && response.data) {
      return response.data
    }
    return response
  })
}

// 调拨
export function transferInventory(data: InventoryTransferRequest): Promise<InventoryTransferResult> {
  return request({
    url: '/api/admin/inventory/transfer',
    method: 'post',
    data
  })
}

// 库存调整（盘点）
export function adjustInventory(data: InventoryAdjustRequest): Promise<any> {
  return request({
    url: '/api/admin/inventory/adjust',
    method: 'post',
    data
  })
}

// 分页查询库存流水
export function getInventoryTransactions(params: InventoryTransactionQuery): Promise<PageResponse<InventoryTransactionVO>> {
  return request({
    url: '/api/admin/inventory/transactions',
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

export function getInventoryTransferRecords(params: InventoryTransferRecordQuery): Promise<PageResponse<InventoryTransferRecordVO>> {
  return request({
    url: '/api/admin/inventory/transfers',
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

/** 智能库存阈值推荐 */
export function getSmartThresholdRecommendations(params?: {
  storeId?: number
  days?: number
}): Promise<any[]> {
  return request({
    url: '/api/admin/inventory/warning/smart-threshold',
    method: 'get',
    params
  })
}

