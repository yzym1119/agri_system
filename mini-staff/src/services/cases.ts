import { createStaffCases, type CaseStaff } from '@agri/mobile-contracts/staff-cases';
import { createStaffCaseSubmissions } from '@agri/mobile-contracts/staff-case-submission';
import { staffAuth } from './auth';
import { mobilePlatform } from './platform';
const base = (import.meta.env.VITE_API_BASE_URL ?? '').trim().replace(/\/$/, '');
declare const wx: {
    getAccountInfoSync(): {
        miniProgram: {
            appId: string;
        };
    };
};
export function caseActor(): CaseStaff | null { const a = staffAuth.current(); return a ? { stamp: String(a.epoch), staffId: a.staffId, storeId: a.storeId, permissions: a.permissions, crossStore: false } : null; }
const payload = (v: unknown): v is unknown => v !== null && v !== undefined;
export const staffCases = createStaffCases({ current: caseActor, request: (r, stamp) => { const a = staffAuth.current(); if (!a || String(a.epoch) !== stamp)
        return Promise.reject(Error('STALE_SESSION')); return staffAuth.request(r, payload, a.epoch); } });
let pinnedNamespace = '';
function namespace(owner: string) { const appId = wx.getAccountInfoSync().miniProgram.appId; if (!appId || !base)
    throw Error('STORAGE_SCOPE_INVALID'); const value = 'agri:staff:case:v1:' + appId + ':' + base + ':'; if (pinnedNamespace && pinnedNamespace !== value)
    throw Error('STORAGE_SCOPE_INVALID'); pinnedNamespace = value; return value + owner; }
export const caseSubmissions = createStaffCaseSubmissions({ current: caseActor, api: staffCases, createKey: () => mobilePlatform.createRequestKey(), storage: {
        read(owner) { const value = uni.getStorageSync(namespace(owner)); if (value === '' || value === undefined || value === null)
            return null; if (typeof value !== 'string')
            throw Error('STORAGE_RECORD_INVALID'); return value; },
        write: (owner, v) => uni.setStorageSync(namespace(owner), v), remove: owner => uni.removeStorageSync(namespace(owner)),
    } });
export function caseError(e: unknown) { const code = e instanceof Error ? e.message : ''; if (code === 'CASE_REJECTED_NO_CHANGE')
    return '本次操作已明确拒绝，未新增答复。请刷新问题并重新核对当前情况。'; if (code === 'CASE_REQUEST_INVALID')
    return '请核对答复内容、判断依据与字段长度，当前内容未能准备为提交原稿。'; if (code === 'FORBIDDEN')
    return '当前账号没有这项权限，请联系店长。'; if (code.startsWith('STORAGE_'))
    return '本机原稿无法可靠核对，请保留记录并重试，不要清缓存或另发一次。'; if (code === 'CASE_ORIGINAL_UNRESOLVED')
    return '原确认尚不能结束。请先核对原结果，不能另发一次。'; return '操作结果尚未核实，请先核对原请求；不要另发一次。'; }
