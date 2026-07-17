import request from '@/utils/request'

export interface LowStockWarningItem {
  productCode?: string
  productName: string
  mainUnit?: string
  currentStock: number
  minStock: number
  gap: number
  reorderQuantity?: number
}

export interface OverstockWarningItem {
  productName: string
  mainUnit?: string
  currentStock: number
  maxStock: number
  excess: number
}

/**
 * 查询低库存预警
 */
export function getLowStockWarnings(storeId?: number): Promise<LowStockWarningItem[]> {
  return request({
    url: '/api/admin/inventory/warning/low-stock',
    method: 'get',
    params: { storeId }
  })
}

/**
 * 查询积压库存预警
 */
export function getOverstockWarnings(storeId?: number): Promise<OverstockWarningItem[]> {
  return request({
    url: '/api/admin/inventory/warning/overstock',
    method: 'get',
    params: { storeId }
  })
}
