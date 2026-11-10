import { isPositiveLongId, normalizeLongId } from './ids.js';

/** 客户身份不复用员工门店/权限会话；此处仅解码，不签发或保存凭据。 */
export type CustomerCredential = { tokenName: 'Authorization'; tokenValue: string };
export type CustomerLogin = CustomerCredential & { customerId: string; name: string; role: 'NORMAL' | 'VIP' };
export type CustomerProfile = { customerId: string; name: string; phoneMasked: string | null; bindingState: 'LINKED' };
export type CustomerPhoneStage =
  | { state: 'PHONE_REQUIRED'; expiresAt: number; phoneMasked: null }
  | { state: 'PHONE_VERIFIED'; expiresAt: number; phoneMasked: string };
export type CustomerOnboarding = { state: 'LINKED'; login: CustomerLogin } | (CustomerPhoneStage & CustomerCredential);
export type CustomerEnrollment = { requestKey: string } & (
  | { status: 'CREATED' | 'LINKED'; nextAction: 'LOGIN' }
  | { status: 'REVIEW_REQUIRED'; nextAction: 'CONTACT_STORE' }
  | { status: 'REJECTED'; nextAction: 'RESTART' }
  | { status: 'UNKNOWN'; nextAction: 'RETRY_SAME_REQUEST' }
);
const object = (value: unknown): value is Record<string, unknown> => !!value && typeof value === 'object' && !Array.isArray(value);
const empty = (value: unknown) => value === undefined || value === null;
const text = (value: unknown, max: number): value is string => typeof value === 'string' && !!value.trim()
  && value.length <= max && !/[\u0000-\u001f\u007f]/.test(value);
const invalid = () => new Error('CUSTOMER_RESPONSE_INVALID');
function credential(value: Record<string, unknown>): CustomerCredential {
  if (value.tokenName !== 'Authorization' || typeof value.tokenValue !== 'string' || !/^[\x21-\x7e]{1,1024}$/.test(value.tokenValue)) throw invalid();
  return { tokenName: 'Authorization', tokenValue: value.tokenValue };
}

/** 对应AuthService微信LoginResultVO。丢弃extra.phone等宽DTO字段，不接受员工/DEV登录。 */
export function decodeCustomerLogin(value: unknown): CustomerLogin {
  if (!object(value) || !object(value.userInfo)) throw invalid();
  const user = value.userInfo, id = normalizeLongId(user.id);
  if (!id || !text(user.name, 100) || (user.role !== 'NORMAL' && user.role !== 'VIP')
    || !object(user.extra) || user.extra.bindingState !== 'LINKED'
    || (!empty(user.permissions) && (!Array.isArray(user.permissions) || user.permissions.length !== 0))) throw invalid();
  return { ...credential(value), customerId: id, name: user.name, role: user.role };
}

/** 对应ClientOnboardingService.Reply：issued为POST签发，info为GET阶段读取。时间单位为epoch秒。 */
export function decodeCustomerOnboarding(value: unknown, mode: 'issued'): CustomerOnboarding;
export function decodeCustomerOnboarding(value: unknown, mode: 'info'): CustomerPhoneStage;
export function decodeCustomerOnboarding(value: unknown, mode: 'issued' | 'info'): CustomerOnboarding | CustomerPhoneStage {
  if (!object(value) || (mode !== 'issued' && mode !== 'info')) throw invalid();
  if (value.state === 'LINKED') {
    if (mode !== 'issued' || !empty(value.tokenName) || !empty(value.tokenValue) || !empty(value.expiresAt) || !empty(value.phoneMasked)) throw invalid();
    return { state: 'LINKED', login: decodeCustomerLogin(value.login) };
  }
  if ((value.state !== 'PHONE_REQUIRED' && value.state !== 'PHONE_VERIFIED') || !empty(value.login)
    || typeof value.expiresAt !== 'number' || !Number.isSafeInteger(value.expiresAt) || value.expiresAt <= 0
    || value.expiresAt > 253402300799) throw invalid();
  // 后端负责原600秒期限；手机时钟可能不准，解码不据本地时间拒绝、续期或授予客户身份。
  let stage: CustomerPhoneStage;
  if (value.state === 'PHONE_REQUIRED') {
    if (!empty(value.phoneMasked)) throw invalid();
    stage = { state: 'PHONE_REQUIRED', expiresAt: value.expiresAt, phoneMasked: null };
  } else {
    if (typeof value.phoneMasked !== 'string' || !/^1[3-9]\d\*{4}\d{4}$/.test(value.phoneMasked)) throw invalid();
    stage = { state: 'PHONE_VERIFIED', expiresAt: value.expiresAt, phoneMasked: value.phoneMasked };
  }
  if (mode === 'issued') return { ...stage, ...credential(value) };
  if (!empty(value.tokenName) || !empty(value.tokenValue)) throw invalid();
  return stage;
}

/** 对应AuthService.ClientProfile，不从登录宽DTO拼本人资料或钱包。 */
export function decodeCustomerProfile(value: unknown): CustomerProfile {
  if (!object(value) || !isPositiveLongId(value.customerId) || !text(value.name, 100) || value.bindingState !== 'LINKED') throw invalid();
  const phone = value.phoneMasked;
  if (phone !== null && (!text(phone, 200) || (phone !== '已登记联系方式' && !/^.{3}\*{4}.{4}$/.test(phone)))) throw invalid();
  return { customerId: value.customerId, name: value.name, phoneMasked: phone, bindingState: 'LINKED' };
}

/** 对应原申请Result。UNKNOWN不是未提交；CREATED/LINKED也不是客户token，须重新平台登录。 */
export function decodeCustomerEnrollment(value: unknown, expectedRequestKey: string): CustomerEnrollment {
  const safeKey = (key: unknown): key is string => typeof key === 'string'
    && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(key);
  if (!safeKey(expectedRequestKey) || !object(value) || !safeKey(value.requestKey)
    || !empty(value.tokenName) || !empty(value.tokenValue) || !empty(value.login)) throw invalid();
  if (value.requestKey !== expectedRequestKey) throw new Error('CUSTOMER_REQUEST_MISMATCH');
  const requestKey = value.requestKey;
  switch (value.status) {
    case 'CREATED': case 'LINKED':
      if (value.nextAction === 'LOGIN') return { requestKey, status: value.status, nextAction: 'LOGIN' }; break;
    case 'REVIEW_REQUIRED':
      if (value.nextAction === 'CONTACT_STORE') return { requestKey, status: 'REVIEW_REQUIRED', nextAction: 'CONTACT_STORE' }; break;
    case 'REJECTED':
      if (value.nextAction === 'RESTART') return { requestKey, status: 'REJECTED', nextAction: 'RESTART' }; break;
    case 'UNKNOWN':
      if (value.nextAction === 'RETRY_SAME_REQUEST') return { requestKey, status: 'UNKNOWN', nextAction: 'RETRY_SAME_REQUEST' }; break;
  }
  throw invalid();
}
