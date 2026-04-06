/**
 * 客户管理相关类型定义
 */

/**
 * 客户列表VO
 */
export interface CustomerListVO {
  id: number
  code: string
  name: string
  phone?: string
  type: number // 1-普通，2-VIP
  vipLevel?: number
  vipEndDate?: string
  balance?: number // 本金余额
  specialFundBalance?: number // 专用金余额
  frozenSpecialFund?: number // 冻结专用金
  points?: number // 积分
  bindStaffId?: number
  bindStaffName?: string
  status: number
  createdAt?: string
}

/**
 * 客户查询参数
 */
export interface CustomerQuery {
  name?: string
  phone?: string
  type?: number
  status?: number
  bindStaffId?: number
  pageNum?: number
  pageSize?: number
}

/**
 * 客户详情VO
 */
export interface CustomerDetailVO {
  id: number
  code: string
  name: string
  phone?: string
  type: number
  vipLevel?: number
  vipStartDate?: string
  vipEndDate?: string
  address?: string
  idCard?: string
  creditLimit?: number
  totalDebt?: number
  totalOrderAmount?: number
  totalOrderCount?: number
  lastOrderTime?: string
  balance?: number
  specialFundBalance?: number
  frozenSpecialFund?: number
  points?: number
  bindStaffId?: number
  bindStaffName?: string
  staffName?: string
  status: number
  remark?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * 充值请求
 */
export interface RechargeRequest {
  customerId: number
  amount: number
  paymentMethod: number // 1-现金，2-微信，3-支付宝
}

/**
 * 充值响应
 */
export interface RechargeResultVO {
  balance: number
  coupons?: Array<{
    id: number
    name: string
    amount: number
  }>
}

/**
 * 发券请求
 */
export interface GrantCouponRequest {
  customerId: number
  templateId: number
  count?: number
}

/**
 * 钱包交易记录VO
 */
export interface WalletTransactionVO {
  id: number
  walletType: number // 1-本金，2-专用金，3-积分
  transactionType: number // 1-充值，2-消费，3-退款，4-赠送，5-扣除
  amount: number
  balanceBefore: number
  balanceAfter: number
  referenceType?: string
  referenceId?: number
  remark?: string
  paymentMethod?: number // 支付方式：1-现金，2-微信，3-支付宝（仅充值交易有效）
  createdAt: string
  createdByName?: string
}

/**
 * 钱包交易查询参数
 */
export interface WalletTransactionQuery {
  customerId: number
  walletType?: number
  transactionType?: number
  pageNum?: number
  pageSize?: number
}

/**
 * 客户创建请求
 */
export interface CustomerCreateRequest {
  name: string
  phone: string
  idCard?: string
  address?: string
  bindStaffId?: number
  remark?: string
}

/**
 * 客户更新请求
 */
export interface CustomerUpdateRequest {
  name?: string
  idCard?: string
  address?: string
  bindStaffId?: number
  remark?: string
}

/**
 * VIP月度权益发放记录VO
 */
export interface VipMonthlyRecordVO {
  id: number
  customerId: number
  yearMonth: string
  vipLevel: number
  recordType?: number // 记录类型：1-系统自动发放（月度权益），2-充值奖励发放
  grantTime: string
  content?: string
}

/**
 * VIP月度权益发放记录查询参数
 */
export interface VipMonthlyRecordQuery {
  customerId: number
  pageNum?: number
  pageSize?: number
}

/**
 * 客户权益流水VO
 */
export interface CustomerBenefitLogVO {
  id: number
  customerId: number
  sourceType: number // 1-每月权益(自动) 2-充值赠送 3-手动发放
  benefitContent: string
  associatedId?: number
  createdBy?: number
  createdByName?: string
  createTime: string
}

/**
 * 客户权益流水查询参数
 */
export interface CustomerBenefitLogQuery {
  customerId: number
  pageNum?: number
  pageSize?: number
}

/**
 * 客户优惠券VO
 */
export interface CustomerCouponVO {
  id: number
  customerId: number
  name: string
  amount: number
  minOrderAmount: number
  startTime: string
  endTime: string
  status: number // 1-未使用, 2-已使用, 3-已过期, 4-已作废
  limitCategoryId?: number
  limitCategoryName?: string
  limitProductId?: number
  usedOrderId?: number
  usedTime?: string
  createdAt: string
}

/**
 * 客户优惠券查询参数
 */
export interface CustomerCouponQuery {
  customerId: number
  status?: number // 1-未使用, 2-已使用, 3-已过期, 4-已作废
  pageNum?: number
  pageSize?: number
}

