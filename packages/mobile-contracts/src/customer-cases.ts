import { isPositiveLongId } from './ids.js';
import type { createCustomerAuth } from './customer-session.js';

export type CaseStatus = 'COLLECTING' | 'WAITING_REVIEW' | 'ACTIVE';
export type CaseFacts = { region: string; crop: string; growthStage: string };
export type CaseSummary = { id: string; storeId: string; question: string; facts: CaseFacts; status: CaseStatus;
  version: string; factsVersion: string; createdAt: string; updatedAt: string };
export type CaseEntry = { id: string; type: 'QUESTION' | 'SUPPLEMENT' | 'HUMAN_REQUEST' | 'HUMAN_REPLY';
  actorType: 'CUSTOMER' | 'STAFF'; body: string; sourceText: string; facts: CaseFacts;
  version: string; factsVersion: string; createdAt: string };
export type CaseReceipt = { requestKey: string; caseId: string; entryId: string; version: string; factsVersion: string; status: CaseStatus };
export type CaseSubmission = { requestKey: string; status: 'UNKNOWN'; receipt: null }
  | { requestKey: string; status: 'COMPLETED'; receipt: CaseReceipt };
export type CasePage = { rows: CaseSummary[]; nextAfter: string | null };
export type CaseDetail = { problem: CaseSummary; entries: CaseEntry[]; nextAfter: string | null; latestReply: CaseEntry | null };
export type CaseCreate = { requestKey: string; storeId: string; question: string; facts: CaseFacts; requestHuman?: boolean };
export type CaseChange = { requestKey: string; expectedVersion: string; body: string; facts?: CaseFacts };
export type CaseOperation = 'CREATE' | 'SUPPLEMENT' | 'HUMAN_REQUEST';
type Auth = Pick<ReturnType<typeof createCustomerAuth>, 'current' | 'requestCustomer'>;
const object = (v: unknown): Record<string, unknown> => {
  if (!v || typeof v !== 'object' || Array.isArray(v)) throw Error('CASE_RESPONSE_INVALID');
  return v as Record<string, unknown>;
};
const id = (v: unknown): string => { if (!isPositiveLongId(v)) throw Error('CASE_RESPONSE_INVALID'); return v; };
export const isCaseRequestKey = (v: unknown): v is string => typeof v === 'string'
  && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(v);
function uuid(v: unknown): string { if (!isCaseRequestKey(v)) throw Error('CASE_REQUEST_INVALID'); return v; }
function plain(v: unknown, max: number, multi = false, required = false): string {
  if (typeof v !== 'string' || v.length > max || required && !v.trim()
      || /[\u0000-\u001f\u007f]/.test(multi ? v.replace(/[\r\n]/g, '') : v)) throw Error('CASE_RESPONSE_INVALID');
  return v;
}
function status(v: unknown): CaseStatus {
  if (v !== 'COLLECTING' && v !== 'WAITING_REVIEW' && v !== 'ACTIVE') throw Error('CASE_RESPONSE_INVALID');
  return v;
}
function timestamp(v: unknown): string {
  if (typeof v !== 'string') throw Error('CASE_RESPONSE_INVALID');
  const m = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?$/.exec(v);
  if (!m || +m[2]! > 23 || +m[3]! > 59 || +(m[4] ?? '0') > 59) throw Error('CASE_RESPONSE_INVALID');
  const day = new Date(m[1] + 'T00:00:00Z');
  if (!Number.isFinite(day.getTime()) || day.toISOString().slice(0, 10) !== m[1]) throw Error('CASE_RESPONSE_INVALID');
  return v;
}
function facts(v: unknown): CaseFacts {
  const r = object(v);
  return { region: plain(r.region, 100), crop: plain(r.crop, 50), growthStage: plain(r.growthStage, 100) };
}
function versions(v: unknown, f: unknown): { version: string; factsVersion: string } {
  const version = id(v), factsVersion = id(f);
  if (BigInt(factsVersion) > BigInt(version)) throw Error('CASE_RESPONSE_INVALID');
  return { version, factsVersion };
}
export function decodeCaseSummary(v: unknown): CaseSummary {
  const r = object(v);
  return { id: id(r.id), storeId: id(r.storeId), question: plain(r.question, 2000, true, true), facts: facts(r.facts),
    status: status(r.status), ...versions(r.version, r.factsVersion), createdAt: timestamp(r.createdAt), updatedAt: timestamp(r.updatedAt) };
}
function cursor(v: unknown): string | null { return v === null ? null : id(v); }
function ordered<T>(v: unknown, decode: (row: unknown) => T, getId: (row: T) => string, next: unknown): { rows: T[]; nextAfter: string | null } {
  if (!Array.isArray(v) || v.length > 20) throw Error('CASE_RESPONSE_INVALID');
  const rows = v.map(decode), after = cursor(next);
  if (rows.some((r, n) => n > 0 && BigInt(getId(rows[n - 1]!)) <= BigInt(getId(r)))
      || after !== null && (rows.length !== 20 || getId(rows[19]!) !== after)) throw Error('CASE_RESPONSE_INVALID');
  return { rows, nextAfter: after };
}
export function decodeCasePage(value: unknown): CasePage {
  const r = object(value); return ordered(r.rows, decodeCaseSummary, r => r.id, r.nextAfter);
}
export function decodeCaseEntry(value: unknown, problem: CaseSummary): CaseEntry {
    const e = object(value), v = versions(e.version, e.factsVersion);
    if (!['QUESTION', 'SUPPLEMENT', 'HUMAN_REQUEST', 'HUMAN_REPLY'].includes(String(e.type))
        || e.actorType !== (e.type === 'HUMAN_REPLY' ? 'STAFF' : 'CUSTOMER')
        || BigInt(v.version) > BigInt(problem.version) || BigInt(v.factsVersion) > BigInt(problem.factsVersion)) throw Error('CASE_RESPONSE_INVALID');
    return { id: id(e.id), type: e.type as CaseEntry['type'], actorType: e.actorType as CaseEntry['actorType'],
      body: plain(e.body, 4000, true, true), sourceText: plain(e.sourceText, 2000, true, e.type === 'HUMAN_REPLY'),
      facts: facts(e.facts), ...v, createdAt: timestamp(e.createdAt) };
}
export function decodeCaseDetail(value: unknown, expectedId: string): CaseDetail {
  const r = object(value), problem = decodeCaseSummary(r.problem);
  if (problem.id !== id(expectedId)) throw Error('CASE_RESPONSE_INVALID');
  const page = ordered(r.entries, value => decodeCaseEntry(value, problem), r => r.id, r.nextAfter);
  const latestReply = r.latestReply === null ? null : decodeCaseEntry(r.latestReply, problem);
  if (latestReply && latestReply.type !== 'HUMAN_REPLY'
      || page.rows.some(row => row.type === 'HUMAN_REPLY' && (!latestReply || BigInt(row.version) > BigInt(latestReply.version)
        || row.id === latestReply.id && JSON.stringify(row) !== JSON.stringify(latestReply)))) throw Error('CASE_RESPONSE_INVALID');
  return { problem, entries: page.rows, nextAfter: page.nextAfter, latestReply };
}
export function decodeCaseReceipt(value: unknown, expectedKey: string, expectedId?: string): CaseReceipt {
  const r = object(value);
  if (r.requestKey !== uuid(expectedKey) || expectedId !== undefined && r.caseId !== id(expectedId)) throw Error('CASE_RESPONSE_INVALID');
  return { requestKey: expectedKey, caseId: id(r.caseId), entryId: id(r.entryId), ...versions(r.version, r.factsVersion), status: status(r.status) };
}
export function decodeCaseSubmission(value: unknown, expectedKey: string): CaseSubmission {
  const r = object(value);
  if (r.requestKey !== uuid(expectedKey)) throw Error('CASE_RESPONSE_INVALID');
  if (r.status === 'UNKNOWN' && r.receipt === null) return { requestKey: expectedKey, status: 'UNKNOWN', receipt: null };
  if (r.status === 'COMPLETED') return { requestKey: expectedKey, status: 'COMPLETED', receipt: decodeCaseReceipt(r.receipt, expectedKey) };
  throw Error('CASE_RESPONSE_INVALID');
}
export function encodeCaseCreate(value: CaseCreate): CaseCreate {
  if (value.requestHuman !== undefined && typeof value.requestHuman !== 'boolean') throw Error('CASE_REQUEST_INVALID');
  return { requestKey: uuid(value.requestKey), storeId: id(value.storeId), question: plain(value.question, 2000, true, true), facts: facts(value.facts), requestHuman: value.requestHuman === true };
}
export function encodeCaseChange(value: CaseChange, operation: Exclude<CaseOperation, 'CREATE'>): CaseChange {
  const base = { requestKey: uuid(value.requestKey), expectedVersion: id(value.expectedVersion), body: plain(value.body, 4000, true, true) };
  if (operation === 'SUPPLEMENT') return { ...base, facts: facts(value.facts) };
  if (operation !== 'HUMAN_REQUEST' || value.facts !== undefined) throw Error('CASE_REQUEST_INVALID');
  return base;
}
/** 仅正式客户会话；不暴露凭据，不用临时号码证明读写本人问题。 */
export function createCustomerCases(auth: Auth) {
  const base = '/api/client/agronomy/cases';
  function epoch(expected?: number) {
    const who = auth.current();
    if (expected !== undefined && who.epoch !== expected) throw Error('STALE_SESSION');
    if (who.kind !== 'CUSTOMER') throw Error('CUSTOMER_LOGIN_REQUIRED');
    return who.epoch;
  }
  return {
    async page(after?: string, filter?: CaseStatus, expectedEpoch?: number) {
      const ticket = epoch(expectedEpoch), query: string[] = [];
      if (after !== undefined) query.push('after=' + id(after));
      if (filter !== undefined) query.push('status=' + status(filter));
      const page = decodeCasePage(await auth.requestCustomer({ url: base + (query.length ? '?' + query.join('&') : ''), method: 'GET' }, ticket));
      if (page.rows.some(row => after !== undefined && BigInt(row.id) >= BigInt(after) || filter !== undefined && row.status !== filter)) throw Error('CASE_RESPONSE_INVALID');
      return page;
    },
    async detail(caseId: string, after?: string, expectedEpoch?: number) {
      const ticket = epoch(expectedEpoch), url = base + '/' + id(caseId) + (after !== undefined ? '?after=' + id(after) : '');
      const detail = decodeCaseDetail(await auth.requestCustomer({ url, method: 'GET' }, ticket), caseId);
      if (detail.entries.some(row => after !== undefined && BigInt(row.id) >= BigInt(after))) throw Error('CASE_RESPONSE_INVALID');
      return detail;
    },
    async create(command: CaseCreate, expectedEpoch?: number) {
      const ticket = epoch(expectedEpoch), data = encodeCaseCreate(command);
      const value = decodeCaseReceipt(await auth.requestCustomer({ url: base, method: 'POST', data }, ticket), data.requestKey);
      if (value.version !== '1' || value.factsVersion !== '1' || value.status !== (data.requestHuman ? 'WAITING_REVIEW' : 'COLLECTING')) throw Error('CASE_RESPONSE_INVALID');
      return value;
    },
    async change(caseId: string, operation: Exclude<CaseOperation, 'CREATE'>, command: CaseChange, expectedEpoch?: number) {
      const ticket = epoch(expectedEpoch), data = encodeCaseChange(command, operation), suffix = operation === 'SUPPLEMENT' ? 'supplements' : 'human-requests';
      const value = decodeCaseReceipt(await auth.requestCustomer({ url: base + '/' + id(caseId) + '/' + suffix, method: 'POST', data }, ticket), data.requestKey, caseId);
      if (BigInt(value.version) !== BigInt(data.expectedVersion) + 1n || operation === 'HUMAN_REQUEST' && value.status !== 'WAITING_REVIEW') throw Error('CASE_RESPONSE_INVALID');
      return value;
    },
    async submission(requestKey: string, expectedEpoch?: number) {
      const ticket = epoch(expectedEpoch), key = uuid(requestKey);
      return decodeCaseSubmission(await auth.requestCustomer({ url: base + '/submissions/' + key, method: 'GET' }, ticket), key);
    },
  };
}
