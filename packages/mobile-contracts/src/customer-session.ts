import { decodeResponse, type HttpResponse } from './session.js';
import { decodeCustomerOnboarding, decodeCustomerEnrollment, decodeCustomerProfile,
  type CustomerCredential, type CustomerLogin, type CustomerOnboarding, type CustomerProfile } from './customer-auth.js';

export type CustomerRequest = { url: string; method: 'GET' | 'POST'; data?: Record<string, unknown>; header?: Record<string, string> };
type State = { kind: 'GUEST' } | ({ kind: 'ONBOARDING' } & Exclude<CustomerOnboarding, { state: 'LINKED' }>)
  | { kind: 'CUSTOMER'; login: CustomerLogin; profile: CustomerProfile | null };
export type CustomerSnapshot = { epoch: number } & ({ kind: 'GUEST' }
  | { kind: 'ONBOARDING'; state: 'PHONE_REQUIRED' | 'PHONE_VERIFIED'; phoneMasked: string | null; expiresAt: number }
  | { kind: 'CUSTOMER'; customerId: string; name: string; profile: CustomerProfile | null });
const payload = (value: unknown): value is unknown => value !== undefined && value !== null;
const safeCode = (value: unknown): value is string => typeof value === 'string' && /^[\x21-\x7e]{1,512}$/.test(value);
function key(value: string) { if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(value)) throw Error('CUSTOMER_REQUEST_INVALID'); }

/** 凭据仅私有内存保存，页面只取脱敏快照。所有异步结果均绑定原会话。 */
export function createCustomerAuth(send: (request: CustomerRequest) => Promise<HttpResponse>, wechatCode: () => Promise<string>) {
  let epoch = 0, readVersion = 0, busy: number | null = null;
  let state: State = { kind: 'GUEST' };
  const same = (ticket: number) => { if (ticket !== epoch) throw Error('STALE_SESSION'); };
  const revoke = () => { state = { kind: 'GUEST' }; busy = null; ++readVersion; ++epoch; };
  const credential = (): CustomerCredential => {
    if (state.kind === 'GUEST') throw Error('CUSTOMER_LOGIN_REQUIRED');
    const source = state.kind === 'CUSTOMER' ? state.login : state;
    return { tokenName: source.tokenName, tokenValue: source.tokenValue };
  };
  async function call(request: CustomerRequest, ticket: number, identity?: CustomerCredential, current: () => boolean = () => true) {
    same(ticket); if (!current()) throw Error('STALE_SESSION');
    let response: HttpResponse;
    try { response = await send({ ...request, ...(identity ? { header: { [identity.tokenName]: identity.tokenValue } } : {}) }); }
    catch { same(ticket); if (!current()) throw Error('STALE_SESSION'); throw Error('RESULT_UNKNOWN'); }
    same(ticket); if (!current()) throw Error('STALE_SESSION');
    try { return decodeResponse('raw', response, payload); }
    catch (error) { if (error instanceof Error && error.message === 'UNAUTHORIZED') revoke(); throw error; }
  }
  function current(): CustomerSnapshot {
    if (state.kind === 'CUSTOMER') return { epoch, kind: 'CUSTOMER', customerId: state.login.customerId, name: state.login.name, profile: state.profile ? { ...state.profile } : null };
    if (state.kind === 'ONBOARDING') return { epoch, kind: 'ONBOARDING', state: state.state, phoneMasked: state.phoneMasked, expiresAt: state.expiresAt };
    return { epoch, kind: 'GUEST' };
  }
  function beginWechatLogin() {
    revoke(); const ticket = epoch; let accepted = false;
    const result = (async () => {
      let code: string;
      try { code = await wechatCode(); } catch { same(ticket); throw Error('WECHAT_LOGIN_FAILED'); }
      same(ticket); if (!safeCode(code)) throw Error('WECHAT_LOGIN_FAILED');
      const reply = decodeCustomerOnboarding(await call({ url: '/auth/client/onboarding/wechat', method: 'POST', data: { code } }, ticket), 'issued');
      if (reply.state === 'PHONE_VERIFIED') throw Error('CUSTOMER_RESPONSE_INVALID');
      state = reply.state === 'LINKED' ? { kind: 'CUSTOMER', login: reply.login, profile: null } : { kind: 'ONBOARDING', ...reply };
      accepted = true;
    })();
    return { result, cancel() { if (!accepted && epoch === ticket) revoke(); } };
  }
  function beginPhoneAuthorization(code: string) {
    if (state.kind !== 'ONBOARDING' || state.state !== 'PHONE_REQUIRED') throw Error('PHONE_AUTHORIZATION_REQUIRED');
    if (busy === epoch) throw Error('AUTH_BUSY');
    if (!safeCode(code)) throw Error('PHONE_AUTHORIZATION_REQUIRED');
    const ticket = epoch, identity = credential(), expiresAt = state.expiresAt; busy = ticket; let accepted = false;
    const result = (async () => {
      try {
        const reply = decodeCustomerOnboarding(await call({ url: '/auth/client/onboarding/phone', method: 'POST', data: { code } }, ticket, identity), 'issued');
        if (reply.state !== 'PHONE_VERIFIED' || reply.expiresAt !== expiresAt) throw Error('CUSTOMER_RESPONSE_INVALID');
        state = { kind: 'ONBOARDING', ...reply }; accepted = true;
      } finally { if (busy === ticket) busy = null; }
    })();
    return { result, cancel() { if (!accepted && epoch === ticket) revoke(); } };
  }
  return {
    current, beginWechatLogin, beginPhoneAuthorization,
    completeEnrollment(expectedEpoch: number) {
      same(expectedEpoch);
      if (state.kind === 'GUEST') throw Error('CUSTOMER_LOGIN_REQUIRED');
      // 申请结果不是客户登录；即使页面隐藏，终态也结束原临时凭据，已有正式客户不被退出。
      if (state.kind === 'ONBOARDING') revoke();
      return epoch;
    },
    async refreshProfile() {
      if (state.kind !== 'CUSTOMER') throw Error('CUSTOMER_LOGIN_REQUIRED');
      const ticket = epoch, id = state.login.customerId, identity = credential(), version = ++readVersion;
      try {
        const profile = decodeCustomerProfile(await call({ url: '/auth/client/info', method: 'GET' }, ticket, identity, () => version === readVersion));
        if (profile.customerId !== id) throw Error('CUSTOMER_IDENTITY_CHANGED');
        if (state.kind !== 'CUSTOMER') throw Error('STALE_SESSION');
        state = { ...state, profile }; return { ...profile };
      } catch (error) {
        if (ticket === epoch && version === readVersion) revoke();
        throw error;
      }
    },
    async register(requestKey: string, expectedEpoch: number) {
      same(expectedEpoch); key(requestKey);
      if (state.kind !== 'ONBOARDING' || state.state !== 'PHONE_VERIFIED') throw Error('PHONE_VERIFICATION_REQUIRED');
      return decodeCustomerEnrollment(await call({ url: '/auth/client/onboarding/registration', method: 'POST', data: { requestKey } }, expectedEpoch, credential()), requestKey);
    },
    async enrollment(requestKey: string, expectedEpoch: number) {
      same(expectedEpoch); key(requestKey);
      return decodeCustomerEnrollment(await call({ url: '/auth/client/onboarding/registration/' + requestKey, method: 'GET' }, expectedEpoch, credential()), requestKey);
    },
    async logout() {
      const source = state.kind === 'CUSTOMER' ? credential() : null;
      revoke();
      if (source) {
        try { await send({ url: '/auth/client/logout', method: 'POST', header: { [source.tokenName]: source.tokenValue } }); }
        catch { /* 本机先失效，网络失败不恢复旧凭据。 */ }
      }
    },
  };
}
