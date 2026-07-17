import request from '@/utils/request'
import type {
  CommissionRuleFormData,
  CommissionRuleItem,
  CommissionRuleQueryParams,
  GoalPageResult,
  SalaryBillDetail,
  StaffMonthlyGoalFormData,
  StaffPerformanceDashboard,
  StaffPerformanceLogItem,
  StaffSalaryBillItem
} from '@/types/performance'

export function getCommissionRules(params: CommissionRuleQueryParams): Promise<CommissionRuleItem[]> {
  return request({
    url: '/api/admin/performance/rule',
    method: 'get',
    params
  })
}

export function saveCommissionRule(data: CommissionRuleFormData): Promise<number> {
  return request({
    url: '/api/admin/performance/rule',
    method: 'post',
    data
  })
}

export function setStaffMonthlyGoal(data: StaffMonthlyGoalFormData): Promise<void> {
  return request({
    url: '/api/admin/performance/goal',
    method: 'post',
    data
  })
}

export function getSalaryBills(params: {
  month?: string
  status?: number
  page?: number
  size?: number
}): Promise<StaffSalaryBillItem[]> {
  return request({
    url: '/api/admin/performance/bill',
    method: 'get',
    params
  })
}

export function confirmSalaryBill(id: number): Promise<void> {
  return request({
    url: `/api/admin/performance/bill/${id}/confirm`,
    method: 'post'
  })
}

export function getStaffPerformanceDashboard(staffId: number): Promise<StaffPerformanceDashboard> {
  return request({
    url: '/api/staff/performance/dashboard',
    method: 'get',
    params: { staffId }
  })
}

export function getStaffPerformanceLogs(params: {
  staffId: number
  month?: string
  page?: number
  size?: number
}): Promise<StaffPerformanceLogItem[]> {
  return request({
    url: '/api/staff/performance/logs',
    method: 'get',
    params
  })
}

export function deleteCommissionRule(id: number): Promise<void> {
  return request({
    url: `/api/admin/performance/rule/${id}`,
    method: 'delete'
  })
}

export function getGoalHistory(params: {
  staffId?: number
  monthFrom?: string
  monthTo?: string
  page?: number
  size?: number
}): Promise<GoalPageResult> {
  return request({
    url: '/api/admin/performance/goal',
    method: 'get',
    params
  })
}

export function getBillDetail(id: number): Promise<SalaryBillDetail> {
  return request({
    url: `/api/admin/performance/bill/${id}`,
    method: 'get'
  })
}

/** 员工自助 — 我的绩效看板 */
export function getMyPerformanceDashboard(): Promise<StaffPerformanceDashboard> {
  return request({
    url: '/api/admin/my/performance/dashboard',
    method: 'get'
  })
}

/** 员工自助 — 我的绩效流水 */
export function getMyPerformanceLogs(params?: {
  month?: string
  page?: number
  size?: number
}): Promise<StaffPerformanceLogItem[]> {
  return request({
    url: '/api/admin/my/performance/logs',
    method: 'get',
    params
  })
}
