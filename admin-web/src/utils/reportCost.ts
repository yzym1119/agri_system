import type {SalesTrendItem} from '@/types/report'

export function hasUnverifiedCost(row:SalesTrendItem):boolean {
  return row.costStatus === '待核对' || !!row.costWarnings?.length || Number(row.costUnavailableSaleAmount || 0) !== 0
}

export function profitForChart(row:SalesTrendItem):number|null {
  return hasUnverifiedCost(row) ? null : row.profit ?? null
}
