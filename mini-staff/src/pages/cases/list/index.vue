<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { caseStatusLabel, type WorkSummary } from '@agri/mobile-contracts/staff-cases';
import type { CaseStatus } from '@agri/mobile-contracts/customer-cases';
import { staffAuth } from '../../../services/auth';
import { staffCases, caseActor, caseSubmissions, caseError } from '../../../services/cases';
const rows = ref<WorkSummary[]>([]), nextAfter = ref<string | null>(null), loading = ref(false), loaded = ref(false), allowed = ref(false), manage = ref(false), message = ref(''), pendingId = ref('');
const state = ref<CaseStatus | undefined>(undefined), states = ['全部问题', '待门店答复', '待补充情况', '已答复'], codes = [undefined, 'WAITING_REVIEW', 'COLLECTING', 'ACTIVE'] as const;
let visible = false, generation = 0, stamp = '';
const current = (t = generation) => visible && allowed.value && t === generation && caseActor()?.stamp === stamp;
async function search(more = false) {
    if (!current() || loading.value && more)
        return;
    const t = ++generation, filter = state.value;
    loading.value = true;
    message.value = '';
    if (!more) {
        rows.value = [];
        nextAfter.value = null;
        loaded.value = false;
    }
    try {
        const page = await staffCases.page({ storeId: caseActor()!.storeId, ...(state.value ? { status: state.value } : {}), ...(more && nextAfter.value ? { after: nextAfter.value } : {}) }, stamp);
        if (!current(t) || filter !== state.value)
            return;
        rows.value = more ? [...rows.value, ...page.rows] : page.rows;
        nextAfter.value = page.nextAfter;
        loaded.value = true;
    }
    catch {
        expired(t);
        if (current(t))
            message.value = '问题列表未能读取，请刷新重试；也可联系店长核对权限。';
    }
    finally {
        if (t === generation)
            loading.value = false;
    }
}
function expired(t: number) { if (visible && t === generation && !caseActor()) {
    allowed.value = false;
    rows.value = [];
    pendingId.value = '';
    message.value = '';
    uni.reLaunch({ url: '/pages/login/index' });
} }
onShow(async () => {
    visible = true;
    const t = ++generation;
    allowed.value = false;
    rows.value = [];
    pendingId.value = '';
    message.value = '';
    loading.value = true;
    try {
        await staffAuth.refresh();
        if (!visible || t !== generation)
            return;
        const a = caseActor();
        if (!a)
            throw Error('STALE_SESSION');
        stamp = a.stamp;
        allowed.value = a.permissions.includes('agronomy:case:query');
        manage.value = a.permissions.includes('agronomy:case:manage');
        if (!allowed.value) {
            message.value = '当前账号没有农技问题查询权限。';
            return;
        }
        try {
            pendingId.value = caseSubmissions.current()?.caseId ?? '';
        }
        catch (e) {
            message.value = caseError(e);
        }
        await search();
    }
    catch {
        if (visible && t === generation)
            uni.reLaunch({ url: '/pages/login/index' });
    }
    finally {
        if (t === generation)
            loading.value = false;
    }
});
onHide(() => { visible = false; generation++; allowed.value = false; loading.value = false; rows.value = []; pendingId.value = ''; message.value = ''; });
function choose(e: {
    detail: {
        value: string;
    };
}) { if (!current())
    return; state.value = codes[Number(e.detail.value)]; void search(); }
function open(id: string) { if (current())
    uni.navigateTo({ url: '/pages/cases/detail/index?caseId=' + id }); }
</script>
<template><view class="page">
 <view v-if="allowed" class="card"><text class="t-sub">{{manage?'本店农技问题':'分配给我的问题'}}</text><text class="t-meta mt4">{{manage?'查看本店队列；需要分配时到后台操作。':'只显示店长交给你处理的问题。'}}</text><picker :range="states" @change="choose"><text class="picker-value mt12">{{states[codes.indexOf(state)]}}</text></picker><button class="btn btn-ghost mt8" :disabled="loading" @click="search(false)">刷新列表</button></view>
 <view v-if="pendingId" class="alert alert-info"><text>有一份原确认需要核对。</text><button class="btn btn-ghost mt8" @click="open(pendingId)">查看原问题并核对</button></view>
 <view v-if="message" class="alert alert-danger" role="status"><text>{{message}}</text></view><view v-if="loading" class="card"><text class="t-meta">正在读取问题…</text></view>
 <view v-for="w in rows" :key="w.problem.id" class="card" hover-class="tap-hover" @click="open(w.problem.id)"><view class="row-between"><text class="chip chip-line">{{caseStatusLabel(w.problem.status)}}</text><text class="t-cap">{{w.assignedStaffId?'已分配':'待分配'}}</text></view><text class="t-sub mt12 case-preview">{{w.problem.question}}</text><text class="t-meta mt8">{{[w.problem.facts.region,w.problem.facts.crop,w.problem.facts.growthStage].filter(Boolean).join(' · ')||'客户尚未补充农情'}}</text><text class="t-cap mt8">{{w.problem.updatedAt.replace('T',' ')}}</text></view>
 <view v-if="loaded&&!loading&&!rows.length&&!message" class="card empty"><text class="empty-text">{{manage?'当前筛选下没有问题。':'当前筛选下没有分配给你的问题。'}}</text></view><button v-if="nextAfter" class="btn btn-ghost" :disabled="loading" @click="search(true)">加载更多</button>
</view></template>
<style scoped>.case-preview{display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden;white-space:pre-wrap;overflow-wrap:anywhere}</style>
