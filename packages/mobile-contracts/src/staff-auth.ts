import { createActorSession, decodeResponse, requestInSession, type ActorSession, type HttpResponse } from './session.js';
export type StaffRequest = { url: string; method: 'GET' | 'POST'; data?: Record<string, unknown>; header?: Record<string, string> };
type Sender = (request: StaffRequest) => Promise<HttpResponse>;
const anyPayload = (value: unknown): value is unknown => value !== undefined && value !== null;
const authorization = (actor: ActorSession) => ({ [actor.tokenName]: actor.tokenValue });

/** 沿当前AuthStaffController裸响应合同；只在内存保留令牌，不记录密码。 */
export function createStaffAuth(send: Sender) {
  const session = createActorSession();
  let refreshVersion = 0;
  function beginLogin(account: string, password: string) {
    if (!account.trim() || !password.trim()) throw new Error('CREDENTIALS_REQUIRED');
    const ticket = session.beginLogin();
    const result = (async () => {
      try {
        let response: HttpResponse;
        try { response = await send({ url: '/auth/staff/login', method: 'POST', data: { account: account.trim(), password, loginType: 'PASSWORD' } }); }
        catch { throw new Error('RESULT_UNKNOWN'); }
        if (!session.isTicket(ticket)) throw new Error('STALE_SESSION');
        const payload = decodeResponse('raw', response, anyPayload);
        if (!session.acceptLogin(ticket, payload)) throw new Error('STALE_SESSION');
      } catch (error) {
        if (!session.isTicket(ticket)) throw new Error('STALE_SESSION');
        session.revoke(ticket); throw error;
      }
    })();
    return { result, cancel() { if (!session.current()) session.revoke(ticket); } };
  }
  return {
    current: session.current,
    beginLogin,
    async request<T>(request: StaffRequest, validate: (value: unknown) => value is T, expectedEpoch: number) {
      const current = session.current();
      if (!current || current.epoch !== expectedEpoch) throw new Error('STALE_SESSION');
      return requestInSession(session, actor => send({ ...request, header: { ...request.header, ...authorization(actor) } }), 'raw', validate);
    },
    async login(account: string, password: string) { await beginLogin(account, password).result; },
    async refresh() {
      const version = ++refreshVersion;
      const actor = session.current();
      if (!actor) throw new Error('UNAUTHORIZED');
      try {
        const payload = await requestInSession(session, current => send({ url: '/auth/staff/info', method: 'GET', header: authorization(current) }), 'raw', anyPayload, () => version === refreshVersion);
        if (version !== refreshVersion) throw new Error('STALE_SESSION');
        session.refreshUser(actor.epoch, payload);
      } catch (error) {
        if (version !== refreshVersion) throw new Error('STALE_SESSION');
        if (error instanceof Error && error.message === 'STALE_SESSION') throw error;
        session.revoke(actor.epoch); throw error;
      }
    },
    async logout() {
      const actor = session.current();
      if (!actor) return;
      session.revoke(actor.epoch);
      try { await send({ url: '/auth/staff/logout', method: 'POST', header: authorization(actor) }); }
      catch { /* 本地已失效；远端断网不能恢复旧令牌。 */ }
    },
  };
}
