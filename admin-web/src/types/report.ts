export interface DashboardSummary {
  gmv?: number
  netSales?: number
  orderCount?: number
  newCreditAmount?: number
  newCustomerCount?: number
  pendingAfterSalesCount?: number
  pendingDeliveryCount?: number
  inventoryAlertCount?: number
}

export interface CreditRiskSummary {
  totalDebt?: number
  overdueDebt?: number
  repaymentRate?: number | null
  monthlyNewCredit?: number
  monthlyRepayment?: number
}

export interface InventoryValueSummary {
  totalInventoryValue?: number
  over180DaysValue?: number
}

export interface SlowMovingInventoryItem {
  productId: number
  productName: string
  batchNo?: string
  quantity?: number
  value?: number
  productionDate?: string
  createdAt?: string
  ageDays?: number
}

export interface SalesTrendItem {
  date: string
  saleAmount?: number
  costAmount?: number
  profit?: number
  profitRate?: number
  costStatus?: string
  costWarnings?: string[]
  costUnavailableSaleAmount?: number
}

export interface CustomerRankItem {
  customerId: number
  customerName?: string
  phone?: string
  totalOrderAmount?: number
  lastOrderTime?: string
  staffName?: string
}

export interface ChurnRiskItem {
  customerId: number
  customerName?: string
  phone?: string
  lastOrderTime?: string
  daysSinceLastOrder?: number
  totalOrderAmount?: number
}
