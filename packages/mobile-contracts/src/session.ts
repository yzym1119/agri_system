export type ActorSession = {
  epoch: number; staffId: string; storeId: string; name: string;
  tokenName: string; tokenValue: string; permissions: string[];
};
export type HttpResponse = { statusCode: number; data?: unknown };
type ObjectValue = Record<string, unknown>;
const object = (value: unknown): value is ObjectValue => !!value && typeof value === 'object' && !Array.isArray(value);
function id(value: unknown): string {
  if (typeof value === 'number' && Number.isSafeInteger(value) && value > 0) return String(value);
  if (typeof value === 'string' && /^[1-9]\d*$/.test(value)) return value;
  throw new Error('IDENTITY_INVALID');
}
export function decodeLogin(value: unknown): Omit<ActorSession, 'epoch'> {
  if (!object(value) || value.tokenName !== 'Authorization' || typeof value.tokenValue !== 'string' || !value.tokenValue.trim()
    || !object(value.userInfo) || !object(value.userInfo.extra) || typeof value.userInfo.name !== 'string'
    || !Array.isArray(value.userInfo.permissions) || !value.userInfo.permissions.every(p => typeof p === 'string' && !!p.trim())) {
    throw new Error('IDENTITY_INVALID');
  }
  return { tokenName: value.tokenName, tokenValue: value.tokenValue,
    staffId: id(value.userInfo.id), storeId: id(value.userInfo.extra.storeId), name: value.userInfo.name,
    permissions: [...value.userInfo.permissions] };
}
export function createActorSession() {
  let epoch = 0;
  let actor: ActorSession | null = null;
  return {
    beginLogin() { actor = null; return ++epoch; },
    acceptLogin(ticket: number, payload: unknown) {
      if (ticket !== epoch || actor !== null) return false;
      actor = { ...decodeLogin(payload), epoch }; return true;
    },
    revoke(ticket: number) {
      if (ticket !== epoch) return false;
      actor = null; epoch++; return true;
    },
    current(): ActorSession | null { return actor ? { ...actor, permissions: [...actor.permissions] } : null; },
    isCurrent(ticket: number) { return !!actor && epoch === ticket; },
    isTicket(ticket: number) { return epoch === ticket; },
    refreshUser(ticket: number, userInfo: unknown) {
      if (!actor || ticket !== epoch) throw new Error('STALE_SESSION');
      const next = decodeLogin({ tokenName: actor.tokenName, tokenValue: actor.tokenValue, userInfo });
      if (next.staffId !== actor.staffId) throw new Error('IDENTITY_CHANGED');
      actor = { ...next, epoch: ++epoch };
    },
  };
}

/** 端点明确选裸值或R包装体；响应校验器由对应端点提供。 */
export function decodeResponse<T>(kind: 'raw' | 'wrapped', response: HttpResponse, validate: (value: unknown) => value is T): T {
  const status = response.statusCode, body = response.data;
  if (status === 401) throw new Error('UNAUTHORIZED');
  if (status === 403) throw new Error('FORBIDDEN');
  if (!Number.isInteger(status) || status < 100 || status >= 500) throw new Error('RESULT_UNKNOWN');
  const envelope = object(body) && Number.isInteger(body.code) && Object.prototype.hasOwnProperty.call(body, 'data');
  if (envelope && typeof body.message === 'string' && body.code !== 0) {
    if (body.code === 10003 || body.code === 401) throw new Error('UNAUTHORIZED');
    if (body.code === 10004) throw new Error('FORBIDDEN');
    if (body.code === 10002 || body.code === 10005) throw new Error('RESULT_UNKNOWN');
    throw new Error('BUSINESS_REJECTED');
  }
  if (status < 200 || status >= 300) throw new Error('RESULT_UNKNOWN');
  if (kind === 'wrapped' && (!envelope || body.code !== 0)) throw new Error('RESPONSE_INVALID');
  const value = kind === 'wrapped' && object(body) ? body.data : body;
  if (!validate(value)) throw new Error('RESPONSE_INVALID');
  return value;
}

export async function requestInSession<T>(session: ReturnType<typeof createActorSession>,
  send: (actor: ActorSession) => Promise<HttpResponse>, kind: 'raw' | 'wrapped', validate: (value: unknown) => value is T,
  isCurrentRequest: () => boolean = () => true): Promise<T> {
  const actor = session.current();
  if (!actor) throw new Error('UNAUTHORIZED');
  let response: HttpResponse;
  try { response = await send(actor); }
  catch {
    if (!session.isCurrent(actor.epoch) || !isCurrentRequest()) throw new Error('STALE_SESSION');
    throw new Error('RESULT_UNKNOWN');
  }
  if (!session.isCurrent(actor.epoch) || !isCurrentRequest()) throw new Error('STALE_SESSION');
  try { return decodeResponse(kind, response, validate); }
  catch (error) {
    if (error instanceof Error && error.message === 'UNAUTHORIZED') session.revoke(actor.epoch);
    throw error;
  }
}
