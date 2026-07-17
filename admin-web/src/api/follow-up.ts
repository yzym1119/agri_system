import request from '@/utils/request'

export interface RepurchaseAlertItem {
  customerName: string
  customerPhone: string
  productName: string
  lastPurchaseDate: string
  expectedRepurchaseDate: string
  daysUntilRepurchase: number
  bindStaffId?: number
}

/**
 * 查询需要复购跟进的客户
 */
export function getRepurchaseAlerts(params?: {
  staffId?: number
  limit?: number
}): Promise<RepurchaseAlertItem[]> {
  return request({
    url: '/api/admin/customer/follow-up/repurchase-alerts',
    method: 'get',
    params
  })
}
