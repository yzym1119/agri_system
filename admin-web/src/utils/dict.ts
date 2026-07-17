/**
 * 字典常量映射
 */

/**
 * 积分兑换单状态
 */
export const POINTS_ORDER_STATUS = {
  WAIT_AUDIT: 10, // 待审核
  AUDIT_PASSED: 20, // 备货中（已通过）
  AUDIT_REJECTED: 30, // 已拒绝
  NOTIFIED: 40, // 待自提（已通知）
  FINISHED: 50 // 已完成
} as const

export const POINTS_ORDER_STATUS_MAP: Record<number, string> = {
  [POINTS_ORDER_STATUS.WAIT_AUDIT]: '待审核',
  [POINTS_ORDER_STATUS.AUDIT_PASSED]: '备货中',
  [POINTS_ORDER_STATUS.AUDIT_REJECTED]: '已拒绝',
  [POINTS_ORDER_STATUS.NOTIFIED]: '待自提',
  [POINTS_ORDER_STATUS.FINISHED]: '已完成'
}

/**
 * 钱包类型
 */
export const WALLET_TYPE = {
  BALANCE: 1, // 本金
  SPECIAL_FUND: 2, // 专用金
  POINTS: 3 // 积分
} as const

export const WALLET_TYPE_MAP: Record<number, string> = {
  [WALLET_TYPE.BALANCE]: '本金',
  [WALLET_TYPE.SPECIAL_FUND]: '专用金',
  [WALLET_TYPE.POINTS]: '积分'
}

/**
 * 交易类型
 */
export const TRANSACTION_TYPE = {
  RECHARGE: 1, // 充值
  CONSUME: 2, // 消费
  REFUND: 3, // 退款
  GIFT: 4, // 赠送
  DEDUCT: 5 // 扣除
} as const

export const TRANSACTION_TYPE_MAP: Record<number, string> = {
  [TRANSACTION_TYPE.RECHARGE]: '充值',
  [TRANSACTION_TYPE.CONSUME]: '消费',
  [TRANSACTION_TYPE.REFUND]: '退款',
  [TRANSACTION_TYPE.GIFT]: '赠送',
  [TRANSACTION_TYPE.DEDUCT]: '扣除'
}

/**
 * 支付方式
 */
export const PAYMENT_METHOD = {
  CASH: 1, // 现金
  WECHAT: 2, // 微信
  ALIPAY: 3, // 支付宝
  BALANCE: 4, // 本金余额
  COUPON: 5, // 红包/优惠券
  CREDIT: 6, // 赊销 (单一标准值：6)
  SPECIAL_FUND: 8, // 专用金余额
  MIXED: 10 // 混合支付
} as const

export const PAYMENT_METHOD_MAP: Record<number, string> = {
  [PAYMENT_METHOD.CASH]: '现金',
  [PAYMENT_METHOD.WECHAT]: '微信',
  [PAYMENT_METHOD.ALIPAY]: '支付宝',
  [PAYMENT_METHOD.BALANCE]: '本金余额',
  [PAYMENT_METHOD.COUPON]: '红包/优惠券',
  [PAYMENT_METHOD.CREDIT]: '赊销',
  [PAYMENT_METHOD.SPECIAL_FUND]: '专用金余额',
  [PAYMENT_METHOD.MIXED]: '混合支付'
}

/**
 * 订单状态枚举（Order.orderStatus）
 */
export const ORDER_STATUS_MAP: Record<number, string> = {
  1: '待支付',
  2: '已支付',
  3: '待自提',
  4: '待发货',
  5: '已发货',
  6: '已完成',
  7: '已取消',
  8: '已退款',
  9: '部分退款'
}

export const ORDER_STATUS_TAG_MAP: Record<number, string> = {
  1: 'info',
  2: 'info',
  3: 'warning',
  4: 'warning',
  5: 'warning',
  6: 'success',
  7: 'info',
  8: 'danger',
  9: 'warning'
}








