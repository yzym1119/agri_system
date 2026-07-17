export interface CommissionRuleItem {
  id: number
  ruleCategory: number
  targetId?: number
  targetName?: string
  staffId?: number
  calcMode: string
  calcValue: number
  maxPerItem?: number
  maxPerOrder?: number
  priority?: number
  enabled: number
  remark?: string
  createdAt?: string
}

export interface CommissionRuleQueryParams {
  ruleCategory?: number
  targetId?: number
  staffId?: number
  enabled?: number
}

export interface CommissionRuleFormData {
  id?: number
  ruleCategory: number
  targetId?: number
  staffId?: number
  calcMode: string
  calcValue: number
  maxPerItem?: number
  maxPerOrder?: number
  priority?: number
  enabled: boolean
  remark?: string
}

export interface StaffMonthlyGoalFormData {
  staffId: number
  month: string
  targetSalesAmount?: number
  targetNewCustomer?: number
  targetCommission?: number
}

export interface StaffSalaryBillItem {
  id: number
  staffId: number
  month: string
  totalCommission?: number
  totalSales?: number
  newCustomerCount?: number
  status: number
  createdAt?: string
  confirmedAt?: string
}

export interface StaffPerformanceDashboard {
  monthSales?: number
  monthCommission?: number
  newCustomerCount?: number
  targetSalesAmount?: number
  targetCommission?: number
  goalProgress?: number
}

export interface StaffPerformanceLogItem {
  id: number
  staffId: number
  storeId?: number
  bizType?: number
  bizId?: number
  orderItemId?: number
  amount?: number
  calcDesc?: string
  needReview?: number
  createdAt?: string
}

export interface GoalHistoryItem {
  id: number
  staffId: number
  staffName?: string
  month: string
  targetSalesAmount?: number
  targetNewCustomer?: number
  targetCommission?: number
  createdAt?: string
}

export interface GoalPageResult {
  records: GoalHistoryItem[]
  total: number
}

export interface SalaryBillDetail {
  id: number
  staffId: number
  staffName?: string
  month: string
  totalSales?: number
  totalCommission?: number
  newCustomerCount?: number
  status: number
  createdAt?: string
  confirmedAt?: string
  lineItems: StaffPerformanceLogItem[]
}
