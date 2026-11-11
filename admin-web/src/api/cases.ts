import request from '@/utils/request';
import { useUserStore } from '@/store/user';
import { usePermissionStore } from '@/store/permission';
import { createStaffCases, type CaseStaff } from '../../../packages/mobile-contracts/src/staff-cases';
import { createStaffCaseSubmissions } from '../../../packages/mobile-contracts/src/staff-case-submission';
import { normalizeLongId } from '../../../packages/mobile-contracts/src/ids';
export function createAdminCases() {
    const user = useUserStore(), permission = usePermissionStore();
    const origin = () => new URL(request.defaults.baseURL || window.location.origin, window.location.origin).origin;
    const originalOrigin = origin();
    function current(): CaseStaff | null { const staffId = normalizeLongId(user.userInfo?.id), storeId = normalizeLongId(user.userInfo?.extra?.storeId); if (!user.token || !staffId || !storeId || origin() !== originalOrigin)
        return null; return { stamp: user.token, staffId, storeId, permissions: [...permission.permissions], crossStore: !!user.userInfo?.extra?.roleCodes?.some(c => c === 'ADMIN' || c === 'SUPER_ADMIN') }; }
    async function send(r: {
        url: string;
        method: 'GET' | 'POST';
        data?: Record<string, unknown>;
    }, stamp: string) { try {
        return await request({ ...r, expectedSessionToken: stamp });
    }
    catch (e) {
        const code = (e as {
            apiBusinessCode?: number;
        })?.apiBusinessCode;
        if (code === 10001 || code === 10006 || code === 10007)
            throw Error('BUSINESS_REJECTED');
        throw e;
    } }
    const cases = createStaffCases({ current, request: send });
    const journal = createStaffCaseSubmissions({ current, api: cases, createKey: async () => crypto.randomUUID(), storage: { read: k => localStorage.getItem('agri.admin.case.v1.' + encodeURIComponent(originalOrigin) + '.' + k), write: (k, v) => localStorage.setItem('agri.admin.case.v1.' + encodeURIComponent(originalOrigin) + '.' + k, v), remove: k => localStorage.removeItem('agri.admin.case.v1.' + encodeURIComponent(originalOrigin) + '.' + k) } });
    function check(stamp: string, code: string) { const a = current(); if (!a || a.stamp !== stamp)
        throw Error('STALE_SESSION'); if (!a.permissions.includes('agronomy:case:query') || !a.permissions.includes('agronomy:case:manage') || !a.permissions.includes(code))
        throw Error('FORBIDDEN'); return a; }
    async function options(url: string, params: Record<string, unknown>, stamp: string, code: string) { const a = check(stamp, code), page: any = await request({ url, method: 'get', params, expectedSessionToken: stamp }); if (JSON.stringify(current()) !== JSON.stringify(a))
        throw Error('STALE_SESSION'); if (!Array.isArray(page?.records) || page.records.length > 20)
        throw Error('CASE_RESPONSE_INVALID'); return page.records as Record<string, any>[]; }
    return { current, cases, journal,
        async storeOptions(keyword: string, stamp: string) { const a = check(stamp, 'store:list'); if (!a.crossStore)
            throw Error('FORBIDDEN'); return (await options('/api/admin/stores', { name: keyword || undefined, pageNum: 1, pageSize: 20 }, stamp, 'store:list')).map(s => { const id = normalizeLongId(s.id); if (!id || typeof s.name !== 'string')
            throw Error('CASE_RESPONSE_INVALID'); return { id, name: s.name }; }); },
        async staffOptions(storeId: string, keyword: string, stamp: string) { const a = check(stamp, 'staff:list'); if (!normalizeLongId(storeId) || !a.crossStore && storeId !== a.storeId)
            throw Error('FORBIDDEN'); return (await options('/api/admin/staff', { storeId, name: keyword || undefined, status: 1, pageNum: 1, pageSize: 20 }, stamp, 'staff:list')).filter(s => normalizeLongId(s.storeId) === storeId && s.status === 1).map(s => { const id = normalizeLongId(s.id); if (!id || typeof s.name !== 'string')
            throw Error('CASE_RESPONSE_INVALID'); return { id, name: s.name }; }); },
    };
}
export function caseError(e: unknown) { const code = e instanceof Error ? e.message : ''; if (code === 'CASE_REJECTED_NO_CHANGE')
    return '本次操作已明确拒绝，未新增记录。请刷新当前问题后重新核对。'; if (code === 'CASE_REQUEST_INVALID')
    return '请核对答复内容、判断依据、员工和字段长度，原稿尚未准备完成。'; if (code.startsWith('STORAGE_'))
    return '本机原确认无法可靠核实，请保留记录并重试；不要清缓存或另发一次。'; if (code === 'CASE_ORIGINAL_UNRESOLVED')
    return '原确认仍可能生效，不能结束等待；请核对原结果。'; return '操作结果未核实，请核对原请求或重试原确认，不要另发一次。'; }
