export interface OperationLogItem {
  id: number
  staffId?: number
  module?: string
  action?: string
  method?: string
  requestUri?: string
  requestParams?: string
  ip?: string
  costTime?: number
  status?: number
  errorMsg?: string
  traceId?: string
  createdAt?: string
}

export interface OperationLogQueryParams {
  staffId?: number
  module?: string
  pageNum?: number
  pageSize?: number
}

export interface OperationLogPageResult {
  records: OperationLogItem[]
  total: number
  pageNum: number
  pageSize: number
}
