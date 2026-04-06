import request from '@/utils/request'

/**
 * 调整客户赊销额度
 */
export function updateCreditLimit(customerId: number, creditLimit: number): Promise<void> {
  return request({
    url: '/api/admin/credit/limit',
    method: 'put',
    params: {
      customerId,
      creditLimit
    }
  })
}

/**
 * 录入还款并执行核销
 */
export function repayCredit(data: {
  customerId: number
  storeId?: number
  amount: number
  paymentMethod: number
  transactionId?: string
  remark?: string
  createdBy?: number
}): Promise<any> {
  return request({
    url: '/api/admin/credit/repay',
    method: 'post',
    data
  })
}

/**
 * 查询欠款单据（支持分页）
 */
export function getCreditAccounts(customerId?: number, pageNum?: number, pageSize?: number, status?: number): Promise<any> {
  const params: any = {}
  if (customerId != null) params.customerId = customerId
  if (status != null) params.status = status
  if (pageNum != null) params.pageNum = pageNum
  if (pageSize != null) params.pageSize = pageSize
  return request({
    url: '/api/admin/credit/accounts',
    method: 'get',
    params
  })
}

/**
 * 查询还款记录（无分页）
 */
export function getCreditRepayments(customerId: number): Promise<any> {
  return request({
    url: '/api/admin/credit/repayments',
    method: 'get',
    params: { customerId }
  })
}









