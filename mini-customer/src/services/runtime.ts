import { createCustomerAuth } from '@agri/mobile-contracts/customer-session';
import { createCustomerEnrollment } from '@agri/mobile-contracts/customer-enrollment';
import { createMobilePlatform, type SecureRandomPort } from '@agri/mobile-contracts';
import { createCustomerTransport } from './transport';

declare const wx: SecureRandomPort & { getAccountInfoSync(): { miniProgram: { appId: string } } };
const transport = createCustomerTransport(import.meta.env.VITE_API_BASE_URL ?? '', import.meta.env.DEV, options => uni.request(options));
export const serviceConfigured = transport.configured;
export const customerAuth = createCustomerAuth(transport.send, () => {
  if (!serviceConfigured) return Promise.reject(Error('SERVICE_NOT_CONFIGURED'));
  return new Promise((resolve, reject) => uni.login({ provider: 'weixin', timeout: 10000,
    success: result => typeof result.code === 'string' && result.code ? resolve(result.code) : reject(Error('WECHAT_LOGIN_FAILED')),
    fail: () => reject(Error('WECHAT_LOGIN_FAILED')) }));
});
const random = typeof wx !== 'undefined' && typeof wx.getRandomValues === 'function'
  ? { getRandomValues: (options: Parameters<SecureRandomPort['getRandomValues']>[0]) => wx.getRandomValues(options) } : undefined;
const platform = createMobilePlatform(uni, random);
let namespace: string | null = null;
function slot() {
  const appId = typeof wx !== 'undefined' ? wx.getAccountInfoSync().miniProgram.appId : '';
  if (!serviceConfigured || !/^wx[a-zA-Z0-9]{16}$/.test(appId)) throw Error('APP_ID_REQUIRED');
  const actual = 'agri:customer:enrollment:v1:' + appId + ':' + transport.origin;
  if (namespace !== null && namespace !== actual) throw Error('APP_ID_CHANGED');
  namespace = actual; return actual;
}
export const customerEnrollment = createCustomerEnrollment({ auth: customerAuth,
  storage: { read: () => platform.readStorage(slot()), write: value => platform.writeStorage(slot(), value), remove: () => platform.removeStorage(slot()) },
  createKey: () => platform.createRequestKey(),
});

export function errorText(error: unknown): string {
  const code = error instanceof Error ? error.message : '';
  if (code.startsWith('STORAGE_')) return '本机申请记录暂时无法核对。请保留当前记录，稍后重试或联系门店，不要重复申请。';
  if (code === 'CUSTOMER_LOGIN_REQUIRED' || code === 'UNAUTHORIZED') return '登录已失效，请重新使用微信登录。';
  if (code === 'PHONE_VERIFICATION_REQUIRED') return '请先授权微信手机号，再继续核对档案。';
  if (code === 'WECHAT_LOGIN_FAILED') return '微信登录未完成。请稍后再试，也可以先返回首页。';
  if (code === 'SERVICE_NOT_CONFIGURED') return '门店服务暂未连接，请联系门店。';
  if (code === 'BUSINESS_REJECTED' || code === 'FORBIDDEN') return '门店暂时无法确认这次操作。请联系门店核对，原申请不会重新创建。';
  return '暂时没有取得确认结果。请稍后核对；如已提交申请，请不要重复申请。';
}
