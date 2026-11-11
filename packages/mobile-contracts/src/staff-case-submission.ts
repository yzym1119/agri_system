import { isPositiveLongId } from './ids.js';
import { encodeWorkCommand, casePermission, type WorkCommand, type WorkOperation, type CaseStaff, type createStaffCases } from './staff-cases.js';
import { decodeCaseReceipt, decodeCaseSubmission, isCaseRequestKey, type CaseReceipt, type CaseStatus } from './customer-cases.js';
export type WorkInput = {
    operation: WorkOperation;
    caseId: string;
    expectedVersion: string;
    expectedFactsVersion: string;
    expectedStatus: CaseStatus;
    body: string;
    sourceText: string;
    assignedStaffId?: string;
};
export type WorkPending = {
    schema: 1;
    staffId: string;
    storeId: string;
    phase: 'PREPARED' | 'SENT';
    operation: WorkOperation;
    caseId: string;
    expectedFactsVersion: string;
    expectedStatus: CaseStatus;
    command: WorkCommand;
};
export type WorkSettlement = {
    status: 'COMPLETED';
    receipt: CaseReceipt;
} | {
    status: 'REJECTED' | 'ENDED';
    receipt: null;
};
type Options = {
    current(): CaseStaff | null;
    api: Pick<ReturnType<typeof createStaffCases>, 'change' | 'submission' | 'detail'>;
    storage: {
        read(owner: string): string | null;
        write(owner: string, value: string): void;
        remove(owner: string): void;
    };
    createKey(): Promise<string>;
};
const id = (v: unknown) => { if (!isPositiveLongId(v))
    throw Error('CASE_REQUEST_INVALID'); return v; };
/** 不存凭据。调用方将存储限定到真实源站/AppID；此处再按员工/门店隔离原稿。 */
export function createStaffCaseSubmissions({ current, api, storage, createKey }: Options) {
    const pinned = new Map<string, string | null>(), outcomes = new Map<string, {
        signature: string;
        value: WorkSettlement;
    }>();
    let busy = false;
    const actor = () => { const a = current(); if (!a || !a.stamp)
        throw Error('STALE_SESSION'); id(a.staffId); id(a.storeId); if (!casePermission(a, 'query'))
        throw Error('FORBIDDEN'); return a; };
    const owner = (a: CaseStaff) => a.staffId + ':' + a.storeId;
    function same(a: CaseStaff) { const n = actor(); if (JSON.stringify(n) !== JSON.stringify(a))
        throw Error('STALE_SESSION'); }
    function parse(raw: string, a: CaseStaff): WorkPending {
        try {
            const r = JSON.parse(raw);
            if (!r || r.schema !== 1 || r.staffId !== a.staffId || r.storeId !== a.storeId || !['PREPARED', 'SENT'].includes(r.phase) || !['ASSIGN', 'HUMAN_REPLY'].includes(r.operation) || !['COLLECTING', 'WAITING_REVIEW', 'ACTIVE'].includes(r.expectedStatus))
                throw Error();
            const p: WorkPending = { schema: 1, staffId: a.staffId, storeId: a.storeId, phase: r.phase, operation: r.operation, caseId: id(r.caseId), expectedFactsVersion: id(r.expectedFactsVersion), expectedStatus: r.expectedStatus, command: encodeWorkCommand(r.command, r.operation) };
            if (BigInt(p.expectedFactsVersion) > BigInt(p.command.expectedVersion) || JSON.stringify(p) !== raw)
                throw Error();
            return p;
        }
        catch {
            throw Error('STORAGE_RECORD_INVALID');
        }
    }
    function raw(a: CaseStaff) { try {
        return storage.read(owner(a));
    }
    catch {
        throw Error('STORAGE_READ_FAILED');
    } }
    function load(a: CaseStaff) { same(a); const value = raw(a), k = owner(a); if (pinned.has(k) && pinned.get(k) !== value)
        throw Error('STORAGE_RECORD_CHANGED'); const p = value === null ? null : parse(value, a); pinned.set(k, value); return p; }
    function matches(a: CaseStaff, p: WorkPending | null) { same(a); if (JSON.stringify(load(a)) !== JSON.stringify(p))
        throw Error('STORAGE_RECORD_CHANGED'); }
    function save(a: CaseStaff, p: WorkPending, old: WorkPending | null) { matches(a, old); const value = JSON.stringify(p), k = owner(a); pinned.set(k, value); try {
        storage.write(k, value);
    }
    catch {
        throw Error('STORAGE_WRITE_FAILED');
    } if (raw(a) !== value)
        throw Error('STORAGE_WRITE_FAILED'); }
    const signature = (p: WorkPending) => JSON.stringify({ ...p, phase: 'SENT' });
    function finish(a: CaseStaff, p: WorkPending, value: WorkSettlement) { matches(a, p); try {
        storage.remove(owner(a));
    }
    catch {
        throw Error('STORAGE_REMOVE_FAILED');
    } if (raw(a) !== null)
        throw Error('STORAGE_REMOVE_FAILED'); pinned.set(owner(a), null); outcomes.set(owner(a) + ':' + p.command.requestKey, { signature: signature(p), value }); }
    function verify(value: unknown, p: WorkPending) { const r = decodeCaseReceipt(value, p.command.requestKey, p.caseId); if (BigInt(r.version) !== BigInt(p.command.expectedVersion) + 1n || r.factsVersion !== p.expectedFactsVersion || r.status !== (p.operation === 'HUMAN_REPLY' ? 'ACTIVE' : p.expectedStatus))
        throw Error('CASE_RESPONSE_INVALID'); return r; }
    async function send(a: CaseStaff, p: WorkPending, found?: CaseReceipt) {
        same(a);
        const first = p.phase === 'PREPARED' && found === undefined;
        let original = p;
        if (p.phase === 'PREPARED') {
            original = { ...p, phase: 'SENT' };
            save(a, original, p);
        }
        try {
            const result = await api.change(original.caseId, original.operation, original.command, a.stamp);
            same(a);
            const receipt = verify(result, original);
            if (found && JSON.stringify(receipt) !== JSON.stringify(found))
                throw Error('CASE_RESPONSE_INVALID');
            finish(a, original, { status: 'COMPLETED', receipt });
            return receipt;
        }
        catch (error) {
            same(a);
            if (first && error instanceof Error && error.message === 'BUSINESS_REJECTED') {
                finish(a, original, { status: 'REJECTED', receipt: null });
                throw Error('CASE_REJECTED_NO_CHANGE');
            }
            throw error;
        }
    }
    async function run<T>(f: () => Promise<T>) { if (busy)
        throw Error('CASE_BUSY'); busy = true; try {
        return await f();
    }
    finally {
        busy = false;
    } }
    return {
        current() { return load(actor()); },
        settled(original: WorkPending): WorkSettlement | null { const a = actor(); load(a); const p = parse(JSON.stringify(original), a), r = outcomes.get(owner(a) + ':' + p.command.requestKey); if (!r)
            return null; if (r.signature !== signature(p))
            throw Error('STORAGE_RECORD_CHANGED'); return r.value.status === 'COMPLETED' ? { status: 'COMPLETED', receipt: { ...r.value.receipt } } : { ...r.value }; },
        submit(input: WorkInput) {
            return run(async () => {
                const a = actor();
                if (load(a))
                    throw Error('CASE_PENDING_EXISTS');
                if (!casePermission(a, input.operation === 'ASSIGN' ? 'manage' : 'review'))
                    throw Error('FORBIDDEN');
                const requestKey = await createKey();
                same(a);
                if (!isCaseRequestKey(requestKey) || outcomes.has(owner(a) + ':' + requestKey))
                    throw Error('CASE_REQUEST_INVALID');
                if (load(a))
                    throw Error('CASE_PENDING_EXISTS');
                const p: WorkPending = { schema: 1, staffId: a.staffId, storeId: a.storeId, phase: 'PREPARED', operation: input.operation, caseId: id(input.caseId), expectedFactsVersion: id(input.expectedFactsVersion), expectedStatus: input.expectedStatus,
                    command: encodeWorkCommand({ requestKey, expectedVersion: input.expectedVersion, body: input.body, sourceText: input.sourceText, ...(input.assignedStaffId === undefined ? {} : { assignedStaffId: input.assignedStaffId }) }, input.operation) };
                parse(JSON.stringify(p), a);
                save(a, p, null);
                return send(a, p);
            });
        },
        retry() { return run(async () => { const a = actor(), p = load(a); return p ? send(a, p) : null; }); },
        query() { return run(async () => { const a = actor(), p = load(a); if (!p)
            return null; const s = decodeCaseSubmission(await api.submission(p.command.requestKey, a.stamp), p.command.requestKey); matches(a, p); if (s.status === 'UNKNOWN')
            return null; return send(a, p, verify(s.receipt, p)); }); },
        endStale(confirmed: boolean) {
            return run(async () => {
                if (!confirmed)
                    throw Error('CASE_CONFIRM_REQUIRED');
                const a = actor(), p = load(a);
                if (!p)
                    return null;
                const d = await api.detail(p.caseId, undefined, a.stamp);
                matches(a, p);
                if (d.work.problem.id !== p.caseId || BigInt(d.work.problem.version) <= BigInt(p.command.expectedVersion))
                    throw Error('CASE_ORIGINAL_UNRESOLVED');
                // 单调version屏障阻止迟到旧确认成功；仍优先核原号，不能凭UNKNOWN单独解除。
                const s = decodeCaseSubmission(await api.submission(p.command.requestKey, a.stamp), p.command.requestKey);
                matches(a, p);
                if (s.status === 'COMPLETED')
                    return send(a, p, verify(s.receipt, p));
                finish(a, p, { status: 'ENDED', receipt: null });
                return 'ENDED' as const;
            });
        },
    };
}
