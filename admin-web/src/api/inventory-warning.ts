import request from '@/utils/request'
import type { InventoryDecisionRequest, InventoryRiskVO } from '@/types/inventory'

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

/**
 * 查询库存风险建议（零售事实链 Task8/Task9）
 *
 * 只提示、不自动执行：日期未知/临期/过期、低库存/积压、低毛利。
 * ALWAYS 传 storeId：不传时后端按全部门店汇总，作业界面必须显式指定门店。
 */
export function getInventoryRisks(storeId: number): Promise<InventoryRiskVO[]> {
  return request({
    url: '/api/admin/inventory/warning/risks',
    method: 'get',
    params: { storeId }
  }).then((response: any) => {
    if (Array.isArray(response)) return response
    if (response && Array.isArray(response.data)) return response.data
    return []
  })
}

/**
 * 记录库存风险人工决策（仅追加留痕，不触发任何自动动作）
 */
export function recordInventoryDecision(data: InventoryDecisionRequest): Promise<{ decisionId: number }> {
  return request({
    url: '/api/admin/inventory/decisions',
    method: 'post',
    data
  }).then((response: any) => response?.data || response)
}
