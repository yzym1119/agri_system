import request from '@/utils/request'
import type {
    CustomerBenefitLogVO,
    CustomerCouponVO,
    CustomerCreateRequest,
    CustomerDetailVO,
    CustomerListVO,
    CustomerQuery,
    CustomerUpdateRequest,
    GrantCouponRequest,
    RechargeRequest,
    RechargeResultVO,
    WalletTransactionQuery,
    WalletTransactionVO
} from '@/types/customer'
import type {PageResponse} from '@/types/store'

/**
 * 客户管理API
 */

/**
 * 分页查询客户列表
 */
export function getCustomerList(params: CustomerQuery): Promise<PageResponse<CustomerListVO>> {
  return request({
    url: '/api/admin/customer/list',
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
 * 获取客户详情
 */
export function getCustomerDetail(id: number): Promise<CustomerDetailVO> {
  return request({
    url: `/api/admin/customer/${id}`,
    method: 'get'
  }).then((response: any) => {
    // 后端直接返回 CustomerDetailVO，request 拦截器已经处理了响应
    if (response && response.id) {
      return response
    } else if (response && response.data && response.data.id) {
      return response.data
    } else {
      console.error('客户详情响应格式错误:', response)
      // 如果响应为空或格式不对，可能是请求失败，让错误向上传播
      throw new Error('获取客户详情失败')
    }
  }).catch((error: any) => {
    console.error('获取客户详情失败:', error)
    throw error
  })
}

/**
 * 客户充值
 */
export function rechargeCustomer(data: RechargeRequest): Promise<RechargeResultVO> {
  return request({
    url: '/api/admin/customer/recharge',
    method: 'post',
    data: {
      customerId: data.customerId,
      amount: data.amount,
      paymentMethod: data.paymentMethod
    }
  })
}

/**
 * 手动发券
 */
export function grantCoupon(data: GrantCouponRequest): Promise<{ success: boolean; count: number }> {
  return request({
    url: '/api/admin/customer/grant-coupon',
    method: 'post',
    data
  })
}

/**
 * 查询钱包交易记录
 */
export function getWalletTransactions(
  params: WalletTransactionQuery
): Promise<PageResponse<WalletTransactionVO>> {
  return request({
    url: '/api/admin/customer/wallet-transactions',
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
 * 创建客户
 */
export function createCustomer(data: CustomerCreateRequest): Promise<CustomerDetailVO> {
  return request({
    url: '/api/admin/customer',
    method: 'post',
    data
  }).then((response: any) => {
    if (response && response.id) {
      return response
    } else if (response && response.data && response.data.id) {
      return response.data
    } else {
      console.error('创建客户响应格式错误:', response)
      throw new Error('创建客户数据格式错误')
    }
  })
}

/**
 * 更新客户
 */
export function updateCustomer(id: number, data: CustomerUpdateRequest): Promise<CustomerDetailVO> {
  return request({
    url: `/api/admin/customer/${id}`,
    method: 'put',
    data
  }).then((response: any) => {
    // 后端直接返回 CustomerDetailVO，需要处理响应格式
    if (response && response.id) {
      return response
    } else if (response && response.data && response.data.id) {
      return response.data
    } else {
      console.error('更新客户响应格式错误:', response)
      throw new Error('更新客户数据格式错误')
    }
  })
}

/**
 * 删除客户
 */
export function deleteCustomer(id: number): Promise<void> {
  return request({
    url: `/api/admin/customer/${id}`,
    method: 'delete'
  })
}

/**
 * 查询客户权益流水
 */
export function getBenefitLogs(
  customerId: number,
  pageNum: number = 1,
  pageSize: number = 10
): Promise<PageResponse<CustomerBenefitLogVO>> {
  return request({
    url: `/api/admin/customer/${customerId}/benefit-logs`,
    method: 'get',
    params: { pageNum, pageSize }
  }).then((response: any) => {
    const page =
      (response && response.records ? response : null) ||
      (response && response.data && response.data.records ? response.data : null)
    if (page) {
      return {
        records: page.records || [],
        total: page.total ?? page.totalRow ?? 0,
        pageNum: page.pageNum ?? page.current ?? pageNum,
        pageSize: page.pageSize ?? page.size ?? pageSize
      }
    }
    return { records: [], total: 0, pageNum, pageSize }
  })
}

/**
 * 查询客户优惠券列表
 */
export function getCustomerCoupons(
  customerId: number,
  status?: number,
  pageNum: number = 1,
  pageSize: number = 10
): Promise<PageResponse<CustomerCouponVO>> {
  return request({
    url: `/api/admin/customer/${customerId}/coupons`,
    method: 'get',
    params: { status, pageNum, pageSize }
  }).then((response: any) => {
    const page =
      (response && response.records ? response : null) ||
      (response && response.data && response.data.records ? response.data : null)
    if (page) {
      return {
        records: page.records || [],
        total: page.total ?? page.totalRow ?? 0,
        pageNum: page.pageNum ?? page.current ?? pageNum,
        pageSize: page.pageSize ?? page.size ?? pageSize
      }
    }
    return { records: [], total: 0, pageNum, pageSize }
  })
}
