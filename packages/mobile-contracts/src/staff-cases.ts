import { isPositiveLongId } from './ids.js';
import { decodeCaseSummary, decodeCaseEntry, decodeCaseDetail, decodeCaseReceipt, decodeCaseSubmission, isCaseRequestKey, type CaseSummary, type CaseEntry, type CaseStatus } from './customer-cases.js';
export type WorkSummary = {
    problem: CaseSummary;
    customerId: string;
    assignedStaffId: string | null;
};
export type WorkEntry = Omit<CaseEntry, 'type'> & {
    type: CaseEntry['type'] | 'ASSIGN';
};
export type WorkDetail = {
    work: WorkSummary;
    entries: WorkEntry[];
    nextAfter: string | null;
    latestReply: CaseEntry | null;
};
export type WorkPage = {
    rows: WorkSummary[];
    nextAfter: string | null;
};
export type WorkOperation = 'ASSIGN' | 'HUMAN_REPLY';
export type WorkCommand = {
    requestKey: string;
    expectedVersion: string;
    body: string;
    sourceText: string;
    assignedStaffId?: string;
};
export type CaseStaff = {
    stamp: string;
    staffId: string;
    storeId: string;
    permissions: string[];
    crossStore: boolean;
};
export type CaseStaffPort = {
    current(): CaseStaff | null;
    request(r: {
        url: string;
        method: 'GET' | 'POST';
        data?: Record<string, unknown>;
    }, stamp: string): Promise<unknown>;
};
const invalid = () => Error('CASE_RESPONSE_INVALID');
const id = (v: unknown) => { if (!isPositiveLongId(v))
    throw invalid(); return v; };
const object = (v: unknown): Record<string, unknown> => { if (!v || typeof v !== 'object' || Array.isArray(v))
    throw invalid(); return v as Record<string, unknown>; };
export const caseStatusLabel = (s: CaseStatus) => ({ COLLECTING: '待补充情况', WAITING_REVIEW: '待门店答复', ACTIVE: '已答复' })[s];
export function casePermission(actor: CaseStaff, code: 'query' | 'manage' | 'review') { return actor.permissions.includes('agronomy:case:' + code); }
function summary(v: unknown): WorkSummary {
    const r = object(v);
    return { problem: decodeCaseSummary(r.problem), customerId: id(r.customerId), assignedStaffId: r.assignedStaffId === null ? null : id(r.assignedStaffId) };
}
function ordered<T>(rows: unknown, next: unknown, decode: (v: unknown) => T, getId: (v: T) => string) {
    if (!Array.isArray(rows) || rows.length > 20)
        throw invalid();
    const values = rows.map(decode), after = next === null ? null : id(next);
    if (values.some((v, i) => i > 0 && BigInt(getId(values[i - 1]!)) <= BigInt(getId(v))) || after !== null && (values.length !== 20 || getId(values[19]!) !== after))
        throw invalid();
    return { values, after };
}
export function decodeWorkPage(v: unknown): WorkPage { const r = object(v), p = ordered(r.rows, r.nextAfter, summary, v => v.problem.id); return { rows: p.values, nextAfter: p.after }; }
export function decodeWorkDetail(v: unknown, expectedId: string): WorkDetail {
    const r = object(v), work = summary(r.work);
    if (work.problem.id !== id(expectedId))
        throw invalid();
    const decode = (value: unknown): WorkEntry => {
        const e = object(value);
        if (e.type !== 'ASSIGN')
            return decodeCaseEntry(e, work.problem);
        if (e.actorType !== 'STAFF' || typeof e.body !== 'string' || e.body.length > 4000 || /[\u0000-\u0009\u000b\u000c\u000e-\u001f\u007f]/.test(e.body) || e.sourceText !== '')
            throw invalid();
        // 分配记录不强求正文；其余农情/时间/版本沿同一问题快照解码。
        const proof = decodeCaseSummary({ ...work.problem, id: e.id, facts: e.facts, version: e.version, factsVersion: e.factsVersion, createdAt: e.createdAt });
        if (BigInt(proof.version) > BigInt(work.problem.version) || BigInt(proof.factsVersion) > BigInt(work.problem.factsVersion))
            throw invalid();
        return { id: proof.id, type: 'ASSIGN', actorType: 'STAFF', body: e.body, sourceText: '', facts: proof.facts, version: proof.version, factsVersion: proof.factsVersion, createdAt: proof.createdAt };
    };
    const p = ordered(r.entries, r.nextAfter, decode, e => e.id);
    const latest = decodeCaseDetail({ problem: work.problem, entries: p.values.filter(e => e.type !== 'ASSIGN'), nextAfter: null, latestReply: r.latestReply }, expectedId).latestReply;
    return { work, entries: p.values, nextAfter: p.after, latestReply: latest };
}
export function encodeWorkCommand(v: WorkCommand, op: WorkOperation): WorkCommand {
    if (!isCaseRequestKey(v.requestKey) || !isPositiveLongId(v.expectedVersion) || BigInt(v.expectedVersion) >= 9223372036854775807n
        || typeof v.body !== 'string' || v.body.length > 4000 || /[\u0000-\u0009\u000b\u000c\u000e-\u001f\u007f]/.test(v.body)
        || typeof v.sourceText !== 'string' || v.sourceText.length > 2000 || /[\u0000-\u0009\u000b\u000c\u000e-\u001f\u007f]/.test(v.sourceText))
        throw Error('CASE_REQUEST_INVALID');
    const c = { requestKey: v.requestKey, expectedVersion: v.expectedVersion, body: v.body, sourceText: v.sourceText };
    if (op === 'HUMAN_REPLY' && v.body.trim() && v.sourceText.trim() && v.assignedStaffId === undefined)
        return c;
    if (op === 'ASSIGN' && v.sourceText === '' && isPositiveLongId(v.assignedStaffId))
        return { ...c, assignedStaffId: v.assignedStaffId };
    throw Error('CASE_REQUEST_INVALID');
}
export function createStaffCases(port: CaseStaffPort) {
    const base = '/api/admin/agronomy/cases';
    function actor(stamp?: string, op?: WorkOperation) { const a = port.current(); if (!a || !a.stamp || stamp !== undefined && a.stamp !== stamp)
        throw Error('STALE_SESSION'); id(a.staffId); id(a.storeId); if (!casePermission(a, 'query') || op && !casePermission(a, op === 'ASSIGN' ? 'manage' : 'review'))
        throw Error('FORBIDDEN'); return a; }
    function same(a: CaseStaff) { const n = actor(a.stamp); if (n.staffId !== a.staffId || n.storeId !== a.storeId || JSON.stringify(n) !== JSON.stringify(a))
        throw Error('STALE_SESSION'); }
    function scope(a: CaseStaff, w: WorkSummary) { if (!(a.crossStore && casePermission(a, 'manage')) && w.problem.storeId !== a.storeId || !casePermission(a, 'manage') && w.assignedStaffId !== a.staffId)
        throw Error('CASE_SCOPE_INVALID'); }
    async function read(a: CaseStaff, r: Parameters<CaseStaffPort['request']>[0]) { same(a); const v = await port.request(r, a.stamp); same(a); return v; }
    return {
        async page(filter: {
            storeId?: string;
            after?: string;
            status?: CaseStatus;
        } = {}, stamp?: string) { const a = actor(stamp), q = []; if (filter.storeId !== undefined) {
            id(filter.storeId);
            if (filter.storeId !== a.storeId && !(a.crossStore && casePermission(a, 'manage')))
                throw Error('CASE_SCOPE_INVALID');
            q.push('storeId=' + filter.storeId);
        } if (filter.after !== undefined)
            q.push('after=' + id(filter.after)); if (filter.status !== undefined) {
            if (!caseStatusLabel(filter.status))
                throw invalid();
            q.push('status=' + filter.status);
        } const p = decodeWorkPage(await read(a, { url: base + (q.length ? '?' + q.join('&') : ''), method: 'GET' })); p.rows.forEach(w => { scope(a, w); if (filter.storeId && w.problem.storeId !== filter.storeId || filter.after && BigInt(w.problem.id) >= BigInt(filter.after) || filter.status && w.problem.status !== filter.status)
            throw invalid(); }); return p; },
        async detail(caseId: string, after?: string, stamp?: string) { const a = actor(stamp), d = decodeWorkDetail(await read(a, { url: base + '/' + id(caseId) + (after ? '?after=' + id(after) : ''), method: 'GET' }), caseId); scope(a, d.work); if (after && d.entries.some(e => BigInt(e.id) >= BigInt(after)))
            throw invalid(); return d; },
        async change(caseId: string, op: WorkOperation, command: WorkCommand, stamp?: string) { const a = actor(stamp, op), data = encodeWorkCommand(command, op), r = decodeCaseReceipt(await read(a, { url: base + '/' + id(caseId) + '/' + (op === 'ASSIGN' ? 'assignment' : 'replies'), method: 'POST', data }), data.requestKey, caseId); if (BigInt(r.version) !== BigInt(data.expectedVersion) + 1n || op === 'HUMAN_REPLY' && r.status !== 'ACTIVE')
            throw invalid(); return r; },
        async submission(key: string, stamp?: string) { const a = actor(stamp); if (!isCaseRequestKey(key))
            throw Error('CASE_REQUEST_INVALID'); return decodeCaseSubmission(await read(a, { url: base + '/submissions/' + key, method: 'GET' }), key); },
    };
}
