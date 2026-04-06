import request from '@/utils/request'

export function sendWalletAuthCode(data: { customerId: number }): Promise<any> {
  return request({
    url: '/api/client/public/wallet/auth-code/send',
    method: 'post',
    data
  })
}

export function verifyWalletAuthCode(data: { customerId: number; authCode: string }): Promise<any> {
  return request({
    url: '/api/client/public/wallet/auth-code/verify',
    method: 'post',
    data
  })
}









