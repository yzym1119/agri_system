<script setup lang="ts">
import { ref } from 'vue';
import { onLoad, onShow, onHide } from '@dcloudio/uni-app';
import { isPositiveLongId } from '@agri/mobile-contracts/ids';
import { caseStatusLabel, type WorkDetail, type WorkEntry } from '@agri/mobile-contracts/staff-cases';
import type { WorkPending } from '@agri/mobile-contracts/staff-case-submission';
import { staffAuth } from '../../../services/auth';
import { staffCases, caseActor, caseSubmissions, caseError } from '../../../services/cases';
const detail = ref<WorkDetail | null>(null), entries = ref<WorkEntry[]>([]), nextAfter = ref<string | null>(null), loading = ref(false), busy = ref(false), allowed = ref(false), canReply = ref(false), message = ref(''), body = ref(''), source = ref(''), confirmed = ref(false), endConfirmed = ref(false), pending = ref<WorkPending | null>(null), blocked = ref(false);
let id = '', visible = false, generation = 0, stamp = '';
const current = (t = generation) => visible && allowed.value && t === generation && caseActor()?.stamp === stamp;
function expired(t: number) { if (visible && t === generation && !caseActor()) {
    allowed.value = false;
    canReply.value = false;
    detail.value = null;
    entries.value = [];
    body.value = '';
    source.value = '';
    pending.value = null;
    confirmed.value = false;
    endConfirmed.value = false;
    message.value = '';
    uni.reLaunch({ url: '/pages/login/index' });
} }
onLoad((q) => { id = isPositiveLongId(q?.caseId) ? q!.caseId : ''; });
function restore() { if (!current())
    return; try {
    const saved = caseSubmissions.current();
    if (saved?.command.requestKey !== pending.value?.command.requestKey)
        endConfirmed.value = false;
    if (pending.value && JSON.stringify(saved) !== JSON.stringify(pending.value)) {
        const result = caseSubmissions.settled(pending.value);
        if (!result)
            throw Error('STORAGE_RECORD_CHANGED');
        body.value = result.status === 'REJECTED' ? pending.value.command.body : '';
        source.value = result.status === 'REJECTED' ? pending.value.command.sourceText : '';
        confirmed.value = false;
        message.value = result.status === 'COMPLETED' ? '原确认已完成，请刷新查看最新记录。' : result.status === 'ENDED' ? '旧确认已结束，请刷新后重新核对。' : caseError(Error('CASE_REJECTED_NO_CHANGE'));
    }
    pending.value = saved;
    blocked.value = false;
}
catch (e) {
    blocked.value = true;
    message.value = caseError(e);
} }
async function load() {
    if (!current())
        return;
    const t = ++generation;
    loading.value = true;
    confirmed.value = false;
    detail.value = null;
    entries.value = [];
    nextAfter.value = null;
    try {
        const d = await staffCases.detail(id, undefined, stamp);
        if (current(t)) {
            detail.value = d;
            entries.value = d.entries;
            nextAfter.value = d.nextAfter;
        }
    }
    catch {
        expired(t);
        if (current(t))
            message.value = '问题详情未能读取，请重试；可能已重新分配或权限有变化。';
    }
    finally {
        if (t === generation)
            loading.value = false;
    }
}
onShow(async () => {
    visible = true;
    const t = ++generation;
    allowed.value = false;
    detail.value = null;
    entries.value = [];
    pending.value = null;
    blocked.value = false;
    body.value = '';
    source.value = '';
    confirmed.value = false;
    endConfirmed.value = false;
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
        canReply.value = allowed.value && a.permissions.includes('agronomy:case:review');
        if (!allowed.value) {
            message.value = '当前账号没有问题查询权限。';
            return;
        }
        if (!id) {
            message.value = '问题编号无效，请从问题列表重新进入。';
            return;
        }
        restore();
        await load();
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
onHide(() => { visible = false; generation++; allowed.value = false; loading.value = false; busy.value = false; detail.value = null; entries.value = []; body.value = ''; source.value = ''; confirmed.value = false; pending.value = null; endConfirmed.value = false; message.value = ''; });
async function more() { if (!current() || loading.value || busy.value || !detail.value || !nextAfter.value)
    return; const t = generation, version = detail.value.work.problem.version, after = nextAfter.value; loading.value = true; try {
    const d = await staffCases.detail(id, after, stamp);
    if (!current(t))
        return;
    if (d.work.problem.version !== version) {
        message.value = '问题已有更新，请刷新后查看完整记录。';
        nextAfter.value = null;
        confirmed.value = false;
        return;
    }
    entries.value = [...entries.value, ...d.entries];
    nextAfter.value = d.nextAfter;
}
catch {
    expired(t);
    if (current(t))
        message.value = '较早记录读取未完成，请重试。';
}
finally {
    if (t === generation)
        loading.value = false;
} }
async function submit() {
    if (!current() || !canReply.value || busy.value || loading.value || blocked.value || pending.value || !detail.value || !confirmed.value || !body.value.trim() || !source.value.trim())
        return;
    const t = generation, p = detail.value.work.problem;
    busy.value = true;
    message.value = '';
    try {
        await caseSubmissions.submit({ operation: 'HUMAN_REPLY', caseId: p.id, expectedVersion: p.version, expectedFactsVersion: p.factsVersion, expectedStatus: p.status, body: body.value, sourceText: source.value });
        if (current(t)) {
            body.value = '';
            source.value = '';
            confirmed.value = false;
            message.value = '答复已保存，客户可查看。';
            restore();
            busy.value = false;
            await load();
        }
    }
    catch (e) {
        expired(t);
        if (current(t)) {
            message.value = caseError(e);
            restore();
            confirmed.value = false;
        }
    }
    finally {
        if (t === generation)
            busy.value = false;
    }
}
async function recover(action: 'query' | 'retry' | 'end') { if (!current() || busy.value || blocked.value || !pending.value || action === 'end' && !endConfirmed.value)
    return; const t = generation; busy.value = true; message.value = ''; try {
    const result = action === 'query' ? await caseSubmissions.query() : action === 'retry' ? await caseSubmissions.retry() : await caseSubmissions.endStale(endConfirmed.value);
    if (current(t)) {
        message.value = result ? '原确认已核对，请查看最新记录。' : '原确认仍待核对，请保留原稿。';
        restore();
        if (!pending.value) {
            body.value = '';
            source.value = '';
            busy.value = false;
            await load();
        }
    }
}
catch (e) {
    expired(t);
    if (current(t)) {
        message.value = caseError(e);
        restore();
    }
}
finally {
    if (t === generation)
        busy.value = false;
} }
function openOriginal() { if (current() && pending.value)
    uni.navigateTo({ url: '/pages/cases/detail/index?caseId=' + pending.value.caseId }); }
const setEnd = (e: Event) => { endConfirmed.value = !!(e as unknown as {
    detail: {
        value: boolean;
    };
}).detail.value; };
const setConfirm = (e: Event) => { confirmed.value = !!(e as unknown as {
    detail: {
        value: boolean;
    };
}).detail.value; };
const entryLabel = (e: WorkEntry) => ({ QUESTION: '客户提问', SUPPLEMENT: '客户补充', HUMAN_REQUEST: '请求门店帮助', ASSIGN: '门店分配', HUMAN_REPLY: '门店答复' })[e.type];
</script>
<template><view class="page">
 <view v-if="message" class="alert alert-info" role="status"><text>{{message}}</text></view>
 <view v-if="blocked" class="card"><text>本机原稿尚未核实，暂不能发送新答复。</text><button class="btn btn-ghost mt8" @click="restore">重新读取原稿</button></view>
 <view v-if="pending" class="card"><text class="t-sub">原确认待核对</text><text class="t-meta mt8">{{pending.operation==='ASSIGN'?'分配':'答复'}} · 问题 {{pending.caseId}}</text><text class="case-text mt8">{{pending.command.body}}</text><text class="t-meta case-text mt8">{{pending.command.sourceText}}</text><button v-if="pending.caseId!==id" class="btn btn-ghost mt8" @click="openOriginal">前往原问题</button><view class="row mt12"><button class="btn btn-primary grow" :disabled="busy" @click="recover('query')">核对原结果</button><button class="btn btn-ghost grow ml10" :disabled="busy" @click="recover('retry')">重试原确认</button></view><text class="t-cap mt12">若客户补充或问题已更新，可核对新版本后结束过时确认。未能核实前保留原号。</text><switch :checked="endConfirmed" @change="setEnd($event)"/><text class="t-meta">我确认要核对并结束过时确认</text><button class="btn btn-quiet mt8" :disabled="busy||!endConfirmed" @click="recover('end')">核对并结束过时确认</button></view>
 <view v-if="loading" class="card"><text class="t-meta">正在读取问题…</text></view>
 <template v-if="detail"><view class="card"><view class="row-between"><text class="t-sub">客户问题</text><text class="chip chip-line">{{caseStatusLabel(detail.work.problem.status)}}</text></view><text class="case-text mt12">{{detail.work.problem.question}}</text><view class="divider mt12"></view><text class="t-meta mt12">地区：{{detail.work.problem.facts.region||'未提供'}}</text><text class="t-meta mt8">作物：{{detail.work.problem.facts.crop||'未提供'}}</text><text class="t-meta mt8">阶段：{{detail.work.problem.facts.growthStage||'未提供'}}</text><button class="btn btn-ghost mt12" :disabled="loading||busy" @click="load">刷新当前情况</button></view>
 <view v-if="detail.latestReply" class="card"><text class="t-sub">最近门店答复</text><text v-if="detail.latestReply.factsVersion!==detail.work.problem.factsVersion" class="t-danger mt8">客户后来补充了情况，此答复针对此前情况。</text><text class="case-text mt12">{{detail.latestReply.body}}</text><text class="t-meta case-text mt8">依据与适用条件：{{detail.latestReply.sourceText}}</text></view>
 <view v-if="canReply&&!pending&&!blocked" class="card"><text class="t-sub">答复客户</text><text class="t-meta mt4">先阅读客户补充记录。情况不全时可在答复中说明还需核实什么。</text><textarea v-model="body" class="case-input mt12" placeholder="向客户说明处理建议或需要补充的情况" :maxlength="4000" :disabled="busy" @input="confirmed=false"/><textarea v-model="source" class="case-input mt12" placeholder="判断依据、适用条件与需注意的问题（必填）" :maxlength="2000" :disabled="busy" @input="confirmed=false"/><view class="row mt12"><switch :checked="confirmed" :disabled="busy" @change="setConfirm($event)"/><text class="t-meta grow">我已核对当前农情、补充记录和本次答复</text></view><button class="btn btn-primary mt12" :disabled="busy||loading||!confirmed||!body.trim()||!source.trim()" @click="submit">{{busy?'正在保存…':'保存并发给客户'}}</button></view>
 <view v-else-if="!canReply" class="card"><text class="t-meta">你可查看问题；专业回复需要店长授予相应权限。</text></view>
 <view class="section"><text class="section-title">沟通记录</text><text class="section-note">最近在前</text></view><view v-for="e in entries" :key="e.id" class="card"><text class="t-sub">{{entryLabel(e)}}</text><text class="t-cap mt4">{{e.createdAt.replace('T',' ')}}</text><text class="case-text mt8">{{e.body||'已分配处理员工'}}</text><text v-if="e.sourceText" class="t-meta case-text mt8">依据：{{e.sourceText}}</text></view><button v-if="nextAfter" class="btn btn-ghost" :disabled="loading||busy" @click="more">查看较早记录</button></template>
 <button v-if="allowed&&!detail&&!loading" class="btn btn-ghost" @click="load">重试读取详情</button>
</view></template>
<style scoped>.case-text{display:block;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.65}.case-input{box-sizing:border-box;width:100%;min-height:120px;padding:12px;border:1px solid var(--c-line);border-radius:8px;font-size:16px;line-height:1.6;background:var(--c-surface)}</style>
