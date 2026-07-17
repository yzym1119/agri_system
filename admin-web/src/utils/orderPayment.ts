const CREDIT_PAYMENT_TYPE = 6
const COUPON_PAYMENT_TYPE = 5

function toAmount(value: unknown): number {
  const amount = Number(value ?? 0)
  return Number.isFinite(amount) ? amount : 0
}

function roundAmount(value: number): number {
  return Number(toAmount(value).toFixed(2))
}

function hasOwnAmount(source: any, key: string): boolean {
  return source != null && source[key] != null && source[key] !== ''
}

export function getPaymentTypeText(type?: number | null): string {
  const map: Record<number, string> = {
    1: '现金',
    2: '微信',
    3: '支付宝',
    4: '本金余额',
    5: '优惠券',
    6: '赊销',
    8: '专用金',
    10: '混合支付'
  }
  return type != null ? (map[type] || '其他') : '未知'
}

export function getOrderDisplayPaymentMethod(order: any): string {
  if (order?.paymentMethodName) {
    return order.paymentMethodName
  }
  if (order?.paymentMethod != null) {
    return getPaymentTypeText(Number(order.paymentMethod))
  }

  const payments = Array.isArray(order?.payments) ? order.payments : []
  const types: number[] = Array.from(new Set<number>(
    payments
      .map((payment: any) => Number(payment?.paymentType))
      .filter((type: number) => Number.isFinite(type) && type !== COUPON_PAYMENT_TYPE)
      .map((type: number) => (type === 8 ? 4 : type))
  ))

  if (types.length === 0) {
    return '未知'
  }
  if (types.length === 1) {
    return getPaymentTypeText(types[0])
  }
  return getPaymentTypeText(10)
}

export function getOrderPaymentSummary(order: any) {
  const totalAmount = roundAmount(order?.totalAmount)
  const discountAmount = roundAmount(order?.discountAmount)
  const couponAmount = roundAmount(order?.couponAmount)
  const netReceivable = roundAmount(Math.max(totalAmount - discountAmount - couponAmount, 0))

  const payments = Array.isArray(order?.payments) ? order.payments : []
  const paymentBreakdown = payments.reduce((summary: { receivedAmount: number; creditAmount: number }, payment: any) => {
    const amount = toAmount(payment?.amount)
    const paymentType = Number(payment?.paymentType)
    if (amount <= 0 || !Number.isFinite(paymentType)) {
      return summary
    }
    if (paymentType === COUPON_PAYMENT_TYPE) {
      return summary
    }
    if (paymentType === CREDIT_PAYMENT_TYPE) {
      summary.creditAmount += amount
    } else {
      summary.receivedAmount += amount
    }
    return summary
  }, { receivedAmount: 0, creditAmount: 0 })

  let receivedAmount = hasOwnAmount(order, 'receivedAmount')
    ? toAmount(order.receivedAmount)
    : payments.length > 0
      ? paymentBreakdown.receivedAmount
      : Number(order?.paymentMethod) === CREDIT_PAYMENT_TYPE
        ? 0
        : toAmount(order?.payAmount)

  let creditAmount = hasOwnAmount(order, 'creditAmount')
    ? toAmount(order.creditAmount)
    : payments.length > 0
      ? paymentBreakdown.creditAmount
      : Number(order?.paymentMethod) === CREDIT_PAYMENT_TYPE
        ? netReceivable
        : Math.max(netReceivable - receivedAmount, 0)

  receivedAmount = roundAmount(Math.max(receivedAmount, 0))
  creditAmount = roundAmount(Math.max(creditAmount, 0))

  return {
    totalAmount,
    discountAmount,
    couponAmount,
    netReceivable,
    receivedAmount,
    creditAmount,
    oweAmount: creditAmount,
    unpaidAmount: roundAmount(Math.max(netReceivable - receivedAmount - creditAmount, 0)),
    hasCredit: creditAmount > 0.001
  }
}
