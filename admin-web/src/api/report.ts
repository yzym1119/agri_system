import axios from 'axios'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'
import type {
  ChurnRiskItem,
  CreditRiskSummary,
  CustomerRankItem,
  DashboardSummary,
  InventoryValueSummary,
  SalesTrendItem,
  SlowMovingInventoryItem
} from '@/types/report'

export function getDashboardToday(storeId?: number): Promise<DashboardSummary> {
  return request({
    url: '/api/admin/report/dashboard/today',
    method: 'get',
    params: storeId ? { storeId } : undefined
  })
}

export function getCreditRisk(storeId?: number): Promise<CreditRiskSummary> {
  return request({
    url: '/api/admin/report/finance/credit',
    method: 'get',
    params: storeId ? { storeId } : undefined
  })
}

export function getInventoryValue(storeId?: number): Promise<InventoryValueSummary> {
  return request({
    url: '/api/admin/report/inventory/value',
    method: 'get',
    params: storeId ? { storeId } : undefined
  })
}

export function getSlowMovingInventory(storeId?: number): Promise<SlowMovingInventoryItem[]> {
  return request({
    url: '/api/admin/report/inventory/slow-moving',
    method: 'get',
    params: storeId ? { storeId } : undefined
  })
}

export function getSalesTrend(params: {
  startTime: string
  endTime: string
  storeId?: number
}): Promise<SalesTrendItem[]> {
  return request({
    url: '/api/admin/report/sales/trend',
    method: 'get',
    params
  })
}

export function getTopCustomers(limit: number, storeId?: number): Promise<CustomerRankItem[]> {
  return request({
    url: '/api/admin/report/customer/top',
    method: 'get',
    params: { limit, ...(storeId ? { storeId } : {}) }
  })
}

export function getChurnRisks(storeId?: number): Promise<ChurnRiskItem[]> {
  return request({
    url: '/api/admin/report/customer/churn',
    method: 'get',
    params: storeId ? { storeId } : undefined
  })
}

const resolveFilename = (contentDisposition?: string, fallback: string = 'report.xlsx') => {
  if (!contentDisposition) return fallback
  const utf8Match = contentDisposition.match(/filename\*=utf-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }
  const plainMatch = contentDisposition.match(/filename="?([^"]+)"?/i)
  if (plainMatch?.[1]) {
    return decodeURIComponent(plainMatch[1])
  }
  return fallback
}

const triggerBlobDownload = (blob: Blob, filename: string) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

async function downloadReport(url: string, params?: Record<string, string | number | undefined>, fallbackName?: string) {
  const userStore = useUserStore()
  const response = await axios.get(`${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}${url}`, {
    params,
    responseType: 'blob',
    headers: userStore.token ? { Authorization: userStore.token } : undefined
  })
  const filename = resolveFilename(response.headers['content-disposition'], fallbackName)
  triggerBlobDownload(response.data, filename)
}

export function exportSalesTrend(params: {
  startTime: string
  endTime: string
  storeId?: number
}) {
  return downloadReport('/api/admin/report/sales/trend/export', params, '销售趋势.xlsx')
}

export function exportTopCustomers(limit: number, storeId?: number) {
  return downloadReport('/api/admin/report/customer/top/export', { limit, ...(storeId ? { storeId } : {}) }, '大户排行榜.xlsx')
}

export function exportChurnRisks(storeId?: number) {
  return downloadReport('/api/admin/report/customer/churn/export', storeId ? { storeId } : undefined, '流失预警.xlsx')
}

export function exportSlowMovingInventory(storeId?: number) {
  return downloadReport('/api/admin/report/inventory/slow-moving/export', storeId ? { storeId } : undefined, '滞销商品.xlsx')
}
