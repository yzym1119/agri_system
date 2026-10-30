import { isPositiveLongId as positiveId, normalizeLongId } from './ids.js';
export type RecoveryActor = Readonly<{ staffId: string; storeId: string; epoch: number }>;
export type SaleSummary = {
  totalCents: number; netCents: number; paymentMethod: number; customerId: string | null;
  lines: { productId: string; packageId: string | null; unit: string; quantity: string; lineCents: number }[];
};
export type PendingSaleRecord = {
  schemaVersion: 1; kind: 'POS'; requestKey: string; scope: { staffId: string; storeId: string };
  summary: SaleSummary; createdAt: number; state: 'VERIFYING';
};
export type POSResult = { requestKey: string; status: 'UNKNOWN' | 'PENDING' | 'COMPLETED' | 'CANCELLED'; orderId: string | null };
type Storage = { readStorage(key: string): string | null; writeStorage(key: string, value: string): void; removeStorage(key: string): void };
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const MAX_RECORD_LENGTH = 200000;
const cents = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const uuid = (v: unknown): v is string => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(v);
function quantity(v: unknown): v is string {
  if (typeof v !== 'string' || !/^(0|[1-9]\d*)(\.\d{1,4})?$/.test(v) || v.length > 24) return false;
  const [whole, fraction = ''] = v.split('.');
  const scaled = Number(whole + fraction.padEnd(4, '0'));
  return Number.isSafeInteger(scaled) && scaled > 0;
}
function copySummary(value: unknown): SaleSummary {
  if (!object(value) || !cents(value.totalCents) || !cents(value.netCents) || value.netCents > value.totalCents
    || ![1,2,3,4,5,6,10].includes(value.paymentMethod as number)
    || !(value.customerId === null || positiveId(value.customerId)) || !Array.isArray(value.lines)
    || value.lines.length === 0 || value.lines.length > 500) throw new Error('SUMMARY_INVALID');
  const lines = value.lines.map(line => {
    if (!object(line) || !positiveId(line.productId) || !(line.packageId === null || positiveId(line.packageId))
      || typeof line.unit !== 'string' || !line.unit.trim() || line.unit.length > 32 || !quantity(line.quantity)
      || !cents(line.lineCents)) throw new Error('SUMMARY_INVALID');
    return { productId: line.productId, packageId: line.packageId, unit: line.unit, quantity: line.quantity, lineCents: line.lineCents };
  });
  return { totalCents: value.totalCents, netCents: value.netCents, paymentMethod: value.paymentMethod as number,
    customerId: value.customerId, lines };
}
const orderId = normalizeLongId;
function decodeResult(value: unknown, key: string): POSResult {
  if (!object(value) || value.requestKey !== key || !['UNKNOWN','PENDING','COMPLETED','CANCELLED'].includes(value.status as string)
    || (value.status === 'COMPLETED' ? !orderId(value.orderId) : value.orderId !== null)) throw new Error('RESPONSE_INVALID');
  return { requestKey: key, status: value.status as POSResult['status'], orderId: orderId(value.orderId) };
}

/** 仅管理POS本机有限摘要；不保存凭据/可重发请求体，不替代服务端请求行锁。 */
export function createPOSRecovery(storage: Storage, currentActor: () => RecoveryActor | null, createRequestKey: () => Promise<string>) {
  const observed = new Map<string, string>();
  const blocked = new Set<string>();
  const busy = new Set<string>();
  function actor(): RecoveryActor {
    const value = currentActor();
    if (!value || !positiveId(value.staffId) || !positiveId(value.storeId)
      || !Number.isSafeInteger(value.epoch) || value.epoch < 1) throw new Error('UNAUTHORIZED');
    return Object.freeze({ staffId: value.staffId, storeId: value.storeId, epoch: value.epoch });
  }
  const storageKey = (a: RecoveryActor) => `agri:pos-pending:v1:${a.staffId}:${a.storeId}`;
  function sameActor(a: RecoveryActor) {
    const now = currentActor();
    if (!now || now.epoch !== a.epoch || now.staffId !== a.staffId || now.storeId !== a.storeId) throw new Error('STALE_SESSION');
  }
  function read(a: RecoveryActor): PendingSaleRecord | null {
    const key = storageKey(a);
    if (blocked.has(key)) throw new Error('STORAGE_BLOCKED');
    try {
      const raw = storage.readStorage(key);
      if (observed.has(key) && observed.get(key) !== raw) throw new Error();
      if (raw === null) return null;
      if (typeof raw !== 'string' || raw.length > MAX_RECORD_LENGTH) throw new Error();
      const saved: unknown = JSON.parse(raw);
      if (!object(saved) || saved.schemaVersion !== 1 || saved.kind !== 'POS' || saved.state !== 'VERIFYING'
        || !uuid(saved.requestKey) || !object(saved.scope) || saved.scope.staffId !== a.staffId || saved.scope.storeId !== a.storeId
        || typeof saved.createdAt !== 'number' || !Number.isSafeInteger(saved.createdAt) || saved.createdAt < 1) throw new Error();
      const record: PendingSaleRecord = { schemaVersion: 1, kind: 'POS', requestKey: saved.requestKey,
        scope: {staffId:a.staffId,storeId:a.storeId}, summary: copySummary(saved.summary), createdAt: saved.createdAt, state: 'VERIFYING' };
      observed.set(key, raw);
      return record;
    } catch { blocked.add(key); throw new Error('STORAGE_BLOCKED'); }
  }
  function remove(a: RecoveryActor) {
    const key = storageKey(a);
    read(a);
    try {
      storage.removeStorage(key);
      if (storage.readStorage(key) !== null) throw new Error();
      observed.delete(key);
    } catch { blocked.add(key); throw new Error('STORAGE_BLOCKED'); }
  }
  async function action(kind: 'read' | 'cancel' | 'finish', confirmed: boolean, expected: string | null,
    request: (key: string, actor: RecoveryActor) => Promise<unknown>): Promise<POSResult> {
    if (kind !== 'read' && !confirmed) throw new Error('CONFIRMATION_REQUIRED');
    const a = actor(), key = storageKey(a);
    if (busy.has(key)) throw new Error('BUSY');
    const original = read(a);
    if (!original) throw new Error('NO_PENDING_REQUEST');
    busy.add(key);
    try {
      let value: unknown;
      try { value = await request(original.requestKey, a); }
      catch { sameActor(a); read(a); throw new Error('RESULT_UNKNOWN'); }
      sameActor(a); read(a);
      const result = decodeResult(value, original.requestKey);
      if (kind === 'finish') {
        if (result.status !== 'COMPLETED' || result.orderId !== expected) throw new Error('RESULT_CHANGED');
        remove(a);
      } else if (kind === 'cancel' && result.status === 'CANCELLED') remove(a);
      return result;
    } finally { busy.delete(key); }
  }
  return {
    restore() { return read(actor()); },
    async submitOnce(summary: unknown, send: (requestKey: string, actor: RecoveryActor) => Promise<unknown>) {
      const a = actor(), key = storageKey(a);
      if (busy.has(key)) throw new Error('BUSY');
      if (read(a)) throw new Error('RECOVERY_REQUIRED');
      const snapshot = copySummary(summary);
      busy.add(key);
      try {
        const requestKey = await createRequestKey();
        sameActor(a);
        if (!uuid(requestKey)) throw new Error('REQUEST_KEY_INVALID');
        if (read(a)) throw new Error('RECOVERY_REQUIRED');
        const record: PendingSaleRecord = { schemaVersion: 1, kind: 'POS', requestKey,
          scope: { staffId:a.staffId,storeId:a.storeId }, summary: snapshot, createdAt: Date.now(), state: 'VERIFYING' };
        const raw = JSON.stringify(record);
        if (raw.length > MAX_RECORD_LENGTH) throw new Error('SUMMARY_INVALID');
        try {
          storage.writeStorage(key, raw);
          if (storage.readStorage(key) !== raw) throw new Error();
          observed.set(key, raw);
        } catch { blocked.add(key); throw new Error('STORAGE_BLOCKED'); }
        let result: unknown;
        try { result = await send(requestKey, a); }
        catch { sameActor(a); read(a); return { status: 'VERIFYING' as const, requestKey, orderId: null }; }
        sameActor(a); read(a);
        const id = orderId(result);
        return id ? { status: 'COMPLETED' as const, requestKey, orderId:id }
          : { status: 'VERIFYING' as const, requestKey, orderId:null };
      } finally { busy.delete(key); }
    },
    lookupOriginal: (read: (key: string, actor: RecoveryActor) => Promise<unknown>) => action('read', false, null, read),
    cancelPending: (confirmed: boolean, cancel: (key: string, actor: RecoveryActor) => Promise<unknown>) => action('cancel', confirmed, null, cancel),
    finishCompleted: (expectedOrderId: string, confirmed: boolean, read: (key: string, actor: RecoveryActor) => Promise<unknown>) => action('finish', confirmed, expectedOrderId, read),
  };
}
