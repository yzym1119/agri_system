import { createCustomerAuth } from '@agri/mobile-contracts/customer-session';
import { createCustomerEnrollment } from '@agri/mobile-contracts/customer-enrollment';
import { createPublicCatalog } from '@agri/mobile-contracts/customer-catalog';
import { createPublicKnowledge } from '@agri/mobile-contracts/customer-knowledge';
import { createCustomerCases } from '@agri/mobile-contracts/customer-cases';
import { createCaseSubmissions } from '@agri/mobile-contracts/customer-case-submission';
import { createMobilePlatform, type SecureRandomPort } from '@agri/mobile-contracts';
import { createCustomerTransport } from './transport';

declare const wx: SecureRandomPort & { getAccountInfoSync(): { miniProgram: { appId: string } } };
const transport = createCustomerTransport(import.meta.env.VITE_API_BASE_URL ?? '', import.meta.env.DEV, options => uni.request(options));
export const serviceConfigured = transport.configured;
export const publicCatalog = createPublicCatalog(transport.sendPublic);
export const publicKnowledge = createPublicKnowledge(transport.sendPublic);
export const customerAuth = createCustomerAuth(transport.send, () => {
  if (!serviceConfigured) return Promise.reject(Error('SERVICE_NOT_CONFIGURED'));
  return new Promise((resolve, reject) => uni.login({ provider: 'weixin', timeout: 10000,
    success: result => typeof result.code === 'string' && result.code ? resolve(result.code) : reject(Error('WECHAT_LOGIN_FAILED')),
    fail: () => reject(Error('WECHAT_LOGIN_FAILED')) }));
});
const random = typeof wx !== 'undefined' && typeof wx.getRandomValues === 'function'
  ? { getRandomValues: (options: Parameters<SecureRandomPort['getRandomValues']>[0]) => wx.getRandomValues(options) } : undefined;
const platform = createMobilePlatform(uni, random);
export const customerCases = createCustomerCases(customerAuth);
let caseNamespace: string | null = null;
function caseSlot(customerId: string) {
  const appId = typeof wx !== 'undefined' ? wx.getAccountInfoSync().miniProgram.appId : '';
  if (!serviceConfigured || !/^wx[a-zA-Z0-9]{16}$/.test(appId)) throw Error('APP_ID_REQUIRED');
  const actual = 'agri:customer:case:v1:' + appId + ':' + transport.origin;
  if (caseNamespace !== null && caseNamespace !== actual) throw Error('APP_ID_CHANGED');
  caseNamespace = actual; return actual + ':' + customerId;
}
export const caseSubmissions = createCaseSubmissions({ auth: customerAuth, api: customerCases,
  storage: { read: id => platform.readStorage(caseSlot(id)), write: (id, value) => platform.writeStorage(caseSlot(id), value), remove: id => platform.removeStorage(caseSlot(id)) },
  createKey: () => platform.createRequestKey(),
});
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

export function caseErrorText(error: unknown): string {
  const code = error instanceof Error ? error.message : '';
  if (code.startsWith('STORAGE_')) return '本机原问题暂时无法核对。请保留记录、稍后再试或联系门店，不要另发一遍。';
  if (code.startsWith('RANDOM_') || code.startsWith('APP_ID_')) return '当前微信环境还不能安全提交，请更新微信或联系门店。';
  if (code === 'CASE_REJECTED_NO_CHANGE') return '这次没有发送成功，请核对门店和填写内容后再发送。';
  if (code === 'CASE_PENDING_EXISTS' || code === 'CASE_BUSY') return '还有一次发送正在核对，请先处理原问题。';
  if (['CUSTOMER_LOGIN_REQUIRED', 'UNAUTHORIZED', 'STALE_SESSION'].includes(code)) return '请重新登录本人账号，原提交记录会保留。';
  if (code === 'SERVICE_NOT_CONFIGURED') return '门店服务暂未连接，请先联系门店。';
  return '发送结果暂未核实，请保留原内容，先核对结果，不要另发一遍。';
}
