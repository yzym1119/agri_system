import request from '@/utils/request'
import type {
  OperationLogPageResult,
  OperationLogQueryParams
} from '@/types/log'

export async function getOperationLogs(params: OperationLogQueryParams): Promise<OperationLogPageResult> {
  const pageNum = params.pageNum || 1
  const pageSize = params.pageSize || 20
  return request({
    url: '/api/admin/logs',
    method: 'get',
    params
  }).then((response: any) => ({
    records: response?.records || [],
    total: response?.total ?? response?.totalRow ?? 0,
    pageNum,
    pageSize
  }))
}
