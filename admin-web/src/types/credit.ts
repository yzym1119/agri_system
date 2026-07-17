export interface CreditAccountItem {
  id: number
  customerId?: number
  customerName?: string
  customerPhone?: string
  orderId?: number
  orderNo?: string
  creditAmount: number
  paidAmount: number
  remainingAmount: number
  dueDate?: string
  status: number
  remark?: string
  createdAt?: string
}

export interface CreditRepaymentItem {
  id: number
  repaymentNo: string
  amount: number
  paymentMethod: number
  transactionId?: string
  remark?: string
  createdAt?: string
}

export interface CreditStatementGenerateRequest {
  customerId: number
  startDate: string
  endDate: string
  createdBy?: number
  remark?: string
}

export interface CreditDebtSummary {
  totalDebt: number
  creditLimit: number
  remainingCredit: number
  latestDebtDate?: string
}
