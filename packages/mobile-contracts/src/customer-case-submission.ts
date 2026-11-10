import { isPositiveLongId } from './ids.js';
import { encodeCaseCreate, encodeCaseChange, decodeCaseReceipt, decodeCaseSubmission, isCaseRequestKey,
  type CaseCreate, type CaseChange, type CaseFacts, type CaseOperation, type CaseReceipt, type createCustomerCases } from './customer-cases.js';
import type { createCustomerAuth } from './customer-session.js';

type Phase = 'PREPARED' | 'SENT';
type Base = { schemaVersion: 1; customerId: string; phase: Phase };
export type PendingCase = Base & ({ operation: 'CREATE'; caseId: null; expectedFactsVersion: null; command: CaseCreate }
  | { operation: Exclude<CaseOperation, 'CREATE'>; caseId: string; expectedFactsVersion: string; command: CaseChange });
export type CaseSubmissionInput = { operation: 'CREATE'; storeId: string; question: string; facts: CaseFacts; requestHuman?: boolean }
  | { operation: Exclude<CaseOperation, 'CREATE'>; caseId: string; expectedVersion: string; expectedFactsVersion: string; body: string; facts?: CaseFacts };
export type CaseSettlement = { status: 'COMPLETED'; receipt: CaseReceipt } | { status: 'REJECTED'; receipt: null };
type Options = {
  auth: Pick<ReturnType<typeof createCustomerAuth>, 'current'>;
  api: Pick<ReturnType<typeof createCustomerCases>, 'create' | 'change' | 'submission'>;
  storage: { read(customerId: string): string | null; write(customerId: string, value: string): void; remove(customerId: string): void };
  createKey(): Promise<string>;
};
type Actor = { customerId: string; epoch: number };
const id = (v: unknown): string => { if (!isPositiveLongId(v)) throw Error('CASE_REQUEST_INVALID'); return v; };
const object = (v: unknown): Record<string, unknown> => {
  if (!v || typeof v !== 'object' || Array.isArray(v)) throw Error('STORAGE_RECORD_INVALID');
  return v as Record<string, unknown>;
};

/** 原文仅存在同AppID/源站/客户槽中；无凭据，不以UNKNOWN当失败，旧会话不清原记录。 */
export function createCaseSubmissions({ auth, api, storage, createKey }: Options) {
  const pinned = new Map<string, string | null>();
  // 仅保存已核实结果供同运行时重建页面收尾，不是另一份可写业务事实。
  const settlements = new Map<string, Map<string, { signature: string; value: CaseSettlement }>>();
  let busy = false;
  function actor(): Actor {
    const a = auth.current();
    if (a.kind !== 'CUSTOMER') throw Error('CUSTOMER_LOGIN_REQUIRED');
    return { customerId: id(a.customerId), epoch: a.epoch };
  }
  function same(a: Actor) {
    const now = auth.current();
    if (now.kind !== 'CUSTOMER' || now.customerId !== a.customerId || now.epoch !== a.epoch) throw Error('STALE_SESSION');
  }
  function raw(customerId: string) {
    try { return storage.read(customerId); } catch { throw Error('STORAGE_READ_FAILED'); }
  }
  function parse(saved: string, customerId: string): PendingCase {
    try {
      const r = object(JSON.parse(saved));
      if (r.schemaVersion !== 1 || r.customerId !== customerId || r.phase !== 'PREPARED' && r.phase !== 'SENT') throw Error();
      const common: Base = { schemaVersion: 1, customerId, phase: r.phase };
      let record: PendingCase;
      if (r.operation === 'CREATE' && r.caseId === null && r.expectedFactsVersion === null) {
        record = { ...common, operation: 'CREATE', caseId: null, expectedFactsVersion: null, command: encodeCaseCreate(object(r.command) as CaseCreate) };
      } else if (r.operation === 'SUPPLEMENT' || r.operation === 'HUMAN_REQUEST') {
        const command = encodeCaseChange(object(r.command) as CaseChange, r.operation), expectedFactsVersion = id(r.expectedFactsVersion);
        if (BigInt(expectedFactsVersion) > BigInt(command.expectedVersion)) throw Error();
        record = { ...common, operation: r.operation, caseId: id(r.caseId), expectedFactsVersion, command };
      } else throw Error();
      // 严格版本/字段/顺序，未知附加内容或凭据不被悄悄接受或重写。
      if (JSON.stringify(record) !== saved) throw Error();
      return record;
    } catch { throw Error('STORAGE_RECORD_INVALID'); }
  }
  function load(a: Actor): PendingCase | null {
    same(a); const saved = raw(a.customerId);
    if (pinned.has(a.customerId) && pinned.get(a.customerId) !== saved) throw Error('STORAGE_RECORD_CHANGED');
    if (saved === null) { pinned.set(a.customerId, null); return null; }
    const record = parse(saved, a.customerId); pinned.set(a.customerId, saved); return record;
  }
  function persist(a: Actor, value: PendingCase, previous: PendingCase | null) {
    same(a);
    if (JSON.stringify(load(a)) !== JSON.stringify(previous)) throw Error('STORAGE_RECORD_CHANGED');
    const saved = JSON.stringify(value); pinned.set(a.customerId, saved);
    try { storage.write(a.customerId, saved); } catch { throw Error('STORAGE_WRITE_FAILED'); }
    if (raw(a.customerId) !== saved) throw Error('STORAGE_WRITE_FAILED');
  }
  function clear(a: Actor, original: PendingCase) {
    same(a);
    if (JSON.stringify(load(a)) !== JSON.stringify(original)) throw Error('STORAGE_RECORD_CHANGED');
    try { storage.remove(a.customerId); } catch { throw Error('STORAGE_REMOVE_FAILED'); }
    if (raw(a.customerId) !== null) throw Error('STORAGE_REMOVE_FAILED');
    pinned.set(a.customerId, null);
  }
  function signature(record: PendingCase) { return JSON.stringify({ ...record, phase: 'SENT' }); }
  function finish(a: Actor, original: PendingCase, value: CaseSettlement) {
    clear(a, original);
    let owner = settlements.get(a.customerId);
    if (!owner) { owner = new Map(); settlements.set(a.customerId, owner); }
    owner.set(original.command.requestKey, { signature: signature(original), value });
  }
  function verified(value: unknown, record: PendingCase) {
    const receipt = decodeCaseReceipt(value, record.command.requestKey, record.caseId ?? undefined);
    const version = record.operation === 'CREATE' ? 1n : BigInt(record.command.expectedVersion) + 1n;
    const factsVersion = record.operation === 'CREATE' ? 1n : BigInt(record.expectedFactsVersion) + (record.operation === 'SUPPLEMENT' ? 1n : 0n);
    if (BigInt(receipt.version) !== version || BigInt(receipt.factsVersion) !== factsVersion
        || record.operation === 'CREATE' && receipt.status !== (record.command.requestHuman ? 'WAITING_REVIEW' : 'COLLECTING')
        || record.operation === 'HUMAN_REQUEST' && receipt.status !== 'WAITING_REVIEW'
        || record.operation === 'SUPPLEMENT' && receipt.status === 'ACTIVE') throw Error('CASE_RESPONSE_INVALID');
    return receipt;
  }
  async function send(a: Actor, pending: PendingCase, found?: CaseReceipt) {
    same(a);
    let original = pending;
    const firstAttempt = pending.phase === 'PREPARED' && found === undefined;
    if (pending.phase === 'PREPARED') {
      original = { ...pending, phase: 'SENT' }; persist(a, original, pending);
    }
    try {
      const response = original.operation === 'CREATE' ? await api.create(original.command, a.epoch)
        : await api.change(original.caseId, original.operation, original.command, a.epoch);
      same(a); const receipt = verified(response, original);
      if (found !== undefined && JSON.stringify(receipt) !== JSON.stringify(found)) throw Error('CASE_RESPONSE_INVALID');
      finish(a, original, { status: 'COMPLETED', receipt }); return receipt;
    } catch (error) {
      same(a);
      // 仅确知这是唯一首次发送且收到事务业务拒绝时，才允许保留页面草稿编辑。
      // 曾发送、刷新恢复或UNKNOWN后的拒绝，不能证明原请求未落库。
      if (firstAttempt && error instanceof Error && error.message === 'BUSINESS_REJECTED') {
        finish(a, original, { status: 'REJECTED', receipt: null }); throw Error('CASE_REJECTED_NO_CHANGE');
      }
      throw error;
    }
  }
  async function run<T>(action: () => Promise<T>): Promise<T> {
    if (busy) throw Error('CASE_BUSY'); busy = true;
    try { return await action(); } finally { busy = false; }
  }
  return {
    current() { return load(actor()); },
    settled(original: PendingCase): CaseSettlement | null {
      const a = actor();
      if (original.customerId !== a.customerId) throw Error('STALE_SESSION');
      load(a);
      const source = parse(JSON.stringify(original), a.customerId);
      const found = settlements.get(a.customerId)?.get(source.command.requestKey);
      if (!found) return null;
      if (found.signature !== signature(source)) throw Error('STORAGE_RECORD_CHANGED');
      return found.value.status === 'COMPLETED' ? { status: 'COMPLETED', receipt: { ...found.value.receipt } } : { status: 'REJECTED', receipt: null };
    },
    submit(input: CaseSubmissionInput) {
      return run(async () => {
        const a = actor(); if (load(a)) throw Error('CASE_PENDING_EXISTS');
        const requestKey = await createKey(); same(a);
        if (!isCaseRequestKey(requestKey)) throw Error('CASE_REQUEST_INVALID');
        if (settlements.get(a.customerId)?.has(requestKey)) throw Error('CASE_REQUEST_INVALID');
        if (load(a)) throw Error('CASE_PENDING_EXISTS');
        const common: Base = { schemaVersion: 1, customerId: a.customerId, phase: 'PREPARED' };
        let record: PendingCase;
        if (input.operation === 'CREATE') {
          record = { ...common, operation: 'CREATE', caseId: null, expectedFactsVersion: null,
            command: encodeCaseCreate({ requestKey, storeId: input.storeId, question: input.question, facts: input.facts, ...(input.requestHuman === undefined ? {} : { requestHuman: input.requestHuman }) }) };
        } else {
          const command = encodeCaseChange({ requestKey, expectedVersion: input.expectedVersion, body: input.body, ...(input.facts ? { facts: input.facts } : {}) }, input.operation);
          const expectedFactsVersion = id(input.expectedFactsVersion);
          if (BigInt(expectedFactsVersion) > BigInt(command.expectedVersion)) throw Error('CASE_REQUEST_INVALID');
          record = { ...common, operation: input.operation, caseId: id(input.caseId), expectedFactsVersion, command };
        }
        persist(a, record, null); return send(a, record);
      });
    },
    retry() { return run(async () => { const a = actor(), record = load(a); if (!record) return null; return send(a, record); }); },
    query() {
      return run(async () => {
        const a = actor(), record = load(a); if (!record) return null;
        const result = decodeCaseSubmission(await api.submission(record.command.requestKey, a.epoch), record.command.requestKey); same(a);
        if (result.status === 'UNKNOWN') return null;
        const found = verified(result.receipt, record);
        // 收据不带原文；已找到不可变原号后，以完全相同内容重放让后端摘要再次核实。
        // UNKNOWN不会触发此POST；同号异体拒绝时仍保留原记录。
        return send(a, record, found);
      });
    },
  };
}
