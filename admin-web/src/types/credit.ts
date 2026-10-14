export interface CreditAccountItem {
  id: number
  customerId?: number
  customerName?: string
  customerPhone?: string
  orderId?: number
  orderNo?: string
  creditAmount: number
  paidAmount: number
  creditReductionAmount?: number | null
  refundedRepaymentAmount?: number | null
  remainingAmount: number
  dueDate?: string
  status: number
  remark?: string
  createdAt?: string
}

export interface CreditRepaymentItem {
  id: number
  repaymentNo: string
  requestKey?: string
  customerId?: number
  storeId?: number
  amount: number
  refundedAmount?: number | null
  paymentMethod: number
  transactionId?: string
  remark?: string
  createdAt?: string
  allocations?: CreditRepaymentAllocation[]
}

export interface CreditRepaymentAllocation {
  id: number
  creditAccountId?: number | null
  orderId?: number | null
  orderNo?: string | null
  amount: number
  refundedAmount?: number | null
}

export interface CreditRepayRequest {
  customerId: number
  requestKey: string
  amount: number
  paymentMethod: number
  transactionId?: string
  remark?: string
}

export interface CreditStatementGenerateRequest {
  customerId: number
  startDate: string
  endDate: string
  remark?: string
}

export interface CreditStatementResult {
  id: number
  customerId: number
  statementNo: string
  statementDate?: string
  startDate: string
  endDate: string
  totalCreditAmount: number
  totalPaidAmount: number
  totalRemainingAmount: number
  remark?: string
  createdAt?: string
}

export interface CreditDebtSummary {
  storeId?: number
  totalDebt: number
  creditLimit: number
  remainingCredit: number
  latestDebtDate?: string
}
