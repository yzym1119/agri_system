import request from '@/utils/request'

/**
 * Send receipt payload to backend print service
 */
export function sendReceiptToPrinter(payload: any): Promise<any> {
  return request({
    url: '/api/admin/print/receipt',
    method: 'post',
    data: payload
  }).then((res: any) => {
    if (res && res.data) return res.data
    return res
  })
}











