import { decodeCustomerEnrollment, type CustomerEnrollment } from './customer-auth.js';
import type { createCustomerAuth } from './customer-session.js';
type Options = {
  auth: Pick<ReturnType<typeof createCustomerAuth>, 'current' | 'register' | 'enrollment' | 'completeEnrollment'>;
  storage: { read(): string | null; write(value: string): void; remove(): void };
  createKey(): Promise<string>;
};
const uuid = (value: unknown): value is string => typeof value === 'string'
  && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(value);

/** 一处AppID+源站专属存储槽，仅固定默认姓名申请号；无令牌、号码或自动重试。 */
export function createCustomerEnrollment({ auth, storage, createKey }: Options) {
  let pinned: string | null | undefined, busy = false;
  function raw() { try { return storage.read(); } catch { throw Error('STORAGE_READ_FAILED'); } }
  function current(): { requestKey: string } | null {
    const saved = raw();
    if (pinned !== undefined && pinned !== saved) throw Error('STORAGE_RECORD_CHANGED');
    if (saved === null) { pinned = null; return null; }
    let value: unknown; try { value = JSON.parse(saved); } catch { throw Error('STORAGE_RECORD_INVALID'); }
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error('STORAGE_RECORD_INVALID');
    const data = value as Record<string, unknown>;
    if (Object.keys(data).length !== 2 || data.schemaVersion !== 1 || !uuid(data.requestKey)) throw Error('STORAGE_RECORD_INVALID');
    pinned = saved; return { requestKey: data.requestKey };
  }
  function same(epoch: number) { if (auth.current().epoch !== epoch) throw Error('STALE_SESSION'); }
  function actor(verified: boolean) {
    const value = auth.current();
    if (value.kind === 'GUEST') throw Error('CUSTOMER_LOGIN_REQUIRED');
    if (verified && (value.kind !== 'ONBOARDING' || value.state !== 'PHONE_VERIFIED')) throw Error('PHONE_VERIFICATION_REQUIRED');
    return value.epoch;
  }
function finish(result: unknown, requestKey: string, epoch: number): { result: CustomerEnrollment; epoch: number } {
    same(epoch); const decoded = decodeCustomerEnrollment(result, requestKey);
    if (current()?.requestKey !== requestKey) throw Error('STORAGE_RECORD_CHANGED');
    if (['CREATED', 'LINKED', 'REJECTED'].includes(decoded.status)) {
      same(epoch); try { storage.remove(); } catch { throw Error('STORAGE_REMOVE_FAILED'); }
      if (raw() !== null) throw Error('STORAGE_REMOVE_FAILED');
      pinned = null;
      return { result: decoded, epoch: auth.completeEnrollment(epoch) };
    }
    return { result: decoded, epoch };
  }
  return {
    current,
    async submit() {
      if (busy) throw Error('ENROLLMENT_BUSY'); busy = true;
      try {
        const epoch = actor(true); let record = current();
        if (!record) {
          const requestKey = await createKey(); same(epoch);
          if (!uuid(requestKey)) throw Error('CUSTOMER_REQUEST_INVALID');
          if (current()) throw Error('STORAGE_RECORD_CHANGED');
          const body = JSON.stringify({ schemaVersion: 1, requestKey }); pinned = body;
          try { storage.write(body); } catch { throw Error('STORAGE_WRITE_FAILED'); }
          if (raw() !== body) throw Error('STORAGE_WRITE_FAILED');
          record = { requestKey };
        }
        same(epoch);
        return finish(await auth.register(record.requestKey, epoch), record.requestKey, epoch);
      } finally { busy = false; }
    },
    async query() {
      if (busy) throw Error('ENROLLMENT_BUSY'); busy = true;
      try {
        const epoch = actor(false), record = current();
        if (!record) return null;
        return finish(await auth.enrollment(record.requestKey, epoch), record.requestKey, epoch);
      } finally { busy = false; }
    },
  };
}
