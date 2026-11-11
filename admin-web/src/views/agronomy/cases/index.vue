<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue';
import { useUserStore } from '@/store/user';
import { usePermissionStore } from '@/store/permission';
import { createAdminCases, caseError } from '@/api/cases';
import { caseStatusLabel, type WorkSummary, type WorkDetail, type WorkEntry, type WorkOperation } from '../../../../../packages/mobile-contracts/src/staff-cases';
import type { CaseStatus } from '../../../../../packages/mobile-contracts/src/customer-cases';
import type { WorkPending, WorkInput } from '../../../../../packages/mobile-contracts/src/staff-case-submission';
const user = useUserStore(), permission = usePermissionStore(), service = createAdminCases();
const has = (p: string) => permission.permissions.includes('agronomy:case:' + p);
const canRead = computed(() => has('query')), canAssign = computed(() => canRead.value && has('manage')), canReply = computed(() => canRead.value && has('review'));
const canStaffList = computed(() => canAssign.value && permission.permissions.includes('staff:list'));
const canStoreList = computed(() => canAssign.value && !!service.current()?.crossStore && permission.permissions.includes('store:list'));
const selectedStore = ref(service.current()?.storeId ?? ''), filter = ref<CaseStatus | ''>(''), rows = ref<WorkSummary[]>([]), nextAfter = ref<string | null>(null), detail = ref<WorkDetail | null>(null), entries = ref<WorkEntry[]>([]), historyAfter = ref<string | null>(null);
const loading = ref(false), loaded = ref(false), detailLoading = ref(false), busy = ref(false), drawer = ref(false), error = ref(''), message = ref(''), blocked = ref(false), pending = ref<WorkPending | null>(null);
const storeOptions = ref<{
    id: string;
    name: string;
}[]>([]), staffOptions = ref<{
    id: string;
    name: string;
}[]>([]);
const editing = ref<WorkInput | null>(null), body = ref(''), source = ref(''), target = ref(''), confirmed = ref(false), endConfirmed = ref(false);
let alive = true, epoch = 0, listRun = 0, detailRun = 0, staffRun = 0, storeRun = 0;
const capture = () => ({ identity: JSON.stringify(service.current()), store: selectedStore.value, filter: filter.value, epoch });
const current = (s: ReturnType<typeof capture>) => alive && !!user.token && !!service.current() && canRead.value && s.identity === JSON.stringify(service.current()) && s.store === selectedStore.value && s.filter === filter.value && s.epoch === epoch;
function restore() { const s = capture(); if (!current(s))
    return; try {
    const saved = service.journal.current();
    if (saved?.command.requestKey !== pending.value?.command.requestKey)
        endConfirmed.value = false;
    if (pending.value && JSON.stringify(saved) !== JSON.stringify(pending.value)) {
        const result = service.journal.settled(pending.value);
        if (!result)
            throw Error('STORAGE_RECORD_CHANGED');
        editing.value = null;
        body.value = '';
        source.value = '';
        confirmed.value = false;
    }
    pending.value = saved;
    blocked.value = false;
}
catch (e) {
    blocked.value = true;
    error.value = caseError(e);
} }
async function loadPage(more = false) { const s = capture(), run = ++listRun; if (!current(s))
    return; loading.value = true; error.value = ''; if (!more) {
    rows.value = [];
    nextAfter.value = null;
    loaded.value = false;
} try {
    const p = await service.cases.page({ ...(s.store ? { storeId: s.store } : {}), ...(s.filter ? { status: s.filter } : {}), ...(more && nextAfter.value ? { after: nextAfter.value } : {}) }, service.current()!.stamp);
    if (current(s) && run === listRun) {
        rows.value = more ? [...rows.value, ...p.rows] : p.rows;
        nextAfter.value = p.nextAfter;
        loaded.value = true;
    }
}
catch {
    if (current(s) && run === listRun)
        error.value = '问题列表未能读取，请刷新重试。';
}
finally {
    if (current(s) && run === listRun)
        loading.value = false;
} }
async function searchStores(keyword = '') { const s = capture(), run = ++storeRun; if (!current(s) || !canStoreList.value)
    return; try {
    const options = await service.storeOptions(keyword, service.current()!.stamp);
    if (current(s) && run === storeRun)
        storeOptions.value = options;
}
catch {
    if (current(s) && run === storeRun)
        error.value = '门店选项未能读取，请重新搜索。';
} }
async function searchStaff(keyword = '') { const s = capture(), run = ++staffRun, caseId = detail.value?.work.problem.id; if (!current(s) || !canStaffList.value || !detail.value || editing.value?.operation !== 'ASSIGN')
    return; try {
    const options = await service.staffOptions(detail.value.work.problem.storeId, keyword, service.current()!.stamp);
    if (current(s) && run === staffRun && detail.value?.work.problem.id === caseId && editing.value?.operation === 'ASSIGN')
        staffOptions.value = options;
}
catch {
    if (current(s) && run === staffRun)
        error.value = '员工选项未能读取，请重新搜索。';
} }
async function openRow(w: WorkSummary) { if (busy.value)
    return; const s = capture(), run = ++detailRun; if (!current(s))
    return; drawer.value = true; detail.value = null; entries.value = []; historyAfter.value = null; editing.value = null; body.value = ''; source.value = ''; confirmed.value = false; detailLoading.value = true; error.value = ''; try {
    const d = await service.cases.detail(w.problem.id, undefined, service.current()!.stamp);
    if (current(s) && run === detailRun && drawer.value) {
        detail.value = d;
        entries.value = d.entries;
        historyAfter.value = d.nextAfter;
        restore();
    }
}
catch {
    if (current(s) && run === detailRun)
        error.value = '问题详情未能读取，可能已重新分配或权限发生变化。';
}
finally {
    if (current(s) && run === detailRun)
        detailLoading.value = false;
} }
async function moreHistory() { const s = capture(), original = detail.value; if (!current(s) || !original || !historyAfter.value || detailLoading.value || busy.value)
    return; const run = ++detailRun; detailLoading.value = true; try {
    const d = await service.cases.detail(original.work.problem.id, historyAfter.value, service.current()!.stamp);
    if (!current(s) || run !== detailRun || detail.value !== original)
        return;
    if (d.work.problem.version !== original.work.problem.version) {
        historyAfter.value = null;
        confirmed.value = false;
        editing.value = null;
        error.value = '问题已有更新，请刷新详情后重新核对。';
        return;
    }
    entries.value = [...entries.value, ...d.entries];
    historyAfter.value = d.nextAfter;
}
catch {
    if (current(s) && run === detailRun)
        error.value = '较早记录未能读取，请重试。';
}
finally {
    if (current(s) && run === detailRun)
        detailLoading.value = false;
} }
function choose(op: WorkOperation) { const s = capture(); if (!current(s) || !detail.value || busy.value || detailLoading.value || pending.value || blocked.value || op === 'ASSIGN' && !canAssign.value || op === 'HUMAN_REPLY' && !canReply.value)
    return; const p = detail.value.work.problem; editing.value = { operation: op, caseId: p.id, expectedVersion: p.version, expectedFactsVersion: p.factsVersion, expectedStatus: p.status, body: '', sourceText: '' }; body.value = ''; source.value = ''; target.value = ''; confirmed.value = false; staffOptions.value = []; if (op === 'ASSIGN')
    void searchStaff(); }
function cancelEdit() { if (!busy.value && !pending.value) {
    editing.value = null;
    confirmed.value = false;
    body.value = '';
    source.value = '';
} }
async function submit() {
    const s = capture(), e = editing.value;
    if (!current(s) || !e || busy.value || pending.value || blocked.value || !confirmed.value || detail.value?.work.problem.id !== e.caseId || detail.value.work.problem.version !== e.expectedVersion || e.operation === 'ASSIGN' && (!canAssign.value || !target.value) || e.operation === 'HUMAN_REPLY' && (!canReply.value || !body.value.trim() || !source.value.trim()))
        return;
    busy.value = true;
    error.value = '';
    const input: WorkInput = { ...e, body: body.value, sourceText: e.operation === 'HUMAN_REPLY' ? source.value : '', ...(e.operation === 'ASSIGN' ? { assignedStaffId: target.value } : {}) };
    try {
        await service.journal.submit(input);
        if (current(s)) {
            editing.value = null;
            body.value = '';
            source.value = '';
            confirmed.value = false;
            restore();
            message.value = e.operation === 'ASSIGN' ? '已分配，可由该员工在手机查看。' : '答复已保存，客户可查看。';
            busy.value = false;
            await openRow(detail.value!.work);
            await loadPage();
        }
    }
    catch (err) {
        if (current(s)) {
            error.value = caseError(err);
            restore();
            confirmed.value = false;
        }
    }
    finally {
        if (current(s))
            busy.value = false;
    }
}
async function recover(action: 'query' | 'retry' | 'end') { const s = capture(); if (!current(s) || busy.value || blocked.value || !pending.value || action === 'end' && !endConfirmed.value)
    return; busy.value = true; error.value = ''; try {
    const value = action === 'query' ? await service.journal.query() : action === 'retry' ? await service.journal.retry() : await service.journal.endStale(endConfirmed.value);
    if (current(s)) {
        message.value = value ? '原确认已核对，请查看最新记录。' : '原确认仍待核对，请保留原稿。';
        restore();
        if (!pending.value) {
            editing.value = null;
            body.value = '';
            source.value = '';
            confirmed.value = false;
            busy.value = false;
            if (detail.value)
                await openRow(detail.value.work);
            await loadPage();
        }
    }
}
catch (e) {
    if (current(s)) {
        error.value = caseError(e);
        restore();
    }
}
finally {
    if (current(s))
        busy.value = false;
} }
function openOriginal() { const s = capture(); if (!current(s) || !pending.value)
    return; void openRow({ problem: { id: pending.value.caseId } } as WorkSummary); }
const entryLabel = (e: WorkEntry) => ({ QUESTION: '客户提问', SUPPLEMENT: '客户补充', HUMAN_REQUEST: '请求门店帮助', ASSIGN: '门店分配', HUMAN_REPLY: '门店答复' })[e.type];
watch(() => JSON.stringify(service.current()), () => { ++epoch; ++listRun; ++detailRun; ++staffRun; ++storeRun; selectedStore.value = service.current()?.storeId ?? ''; rows.value = []; nextAfter.value = null; detail.value = null; entries.value = []; drawer.value = false; editing.value = null; body.value = ''; source.value = ''; confirmed.value = false; endConfirmed.value = false; pending.value = null; blocked.value = false; busy.value = false; loading.value = false; detailLoading.value = false; loaded.value = false; error.value = ''; message.value = ''; staffOptions.value = []; storeOptions.value = []; if (canRead.value) {
    restore();
    void loadPage();
} }, { immediate: true, flush: 'sync' });
watch([selectedStore, filter], () => { ++epoch; ++listRun; ++detailRun; ++staffRun; ++storeRun; detail.value = null; entries.value = []; editing.value = null; drawer.value = false; confirmed.value = false; loading.value = false; detailLoading.value = false; staffOptions.value = []; if (canRead.value) {
    restore();
    void loadPage();
} });
watch([body, source, target], () => { confirmed.value = false; }, { flush: 'sync' });
watch(drawer, v => { if (!v) {
    ++detailRun;
    detail.value = null;
    entries.value = [];
    editing.value = null;
    body.value = '';
    source.value = '';
    confirmed.value = false;
    detailLoading.value = false;
} });
onUnmounted(() => { alive = false; ++epoch; ++listRun; ++detailRun; ++staffRun; ++storeRun; });
</script>
<template><div class="page-container case-workspace">
 <div class="page-header"><div><h2>农技问题</h2><p>{{canAssign?'查看门店问题，安排员工处理并回复客户。':'查看分配给你的问题；专业回复按实际权限开放。'}}</p></div></div>
 <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="mb-16"/><el-alert v-if="message" :title="message" type="success" :closable="false" class="mb-16"/>
 <el-card v-if="pending||blocked" shadow="never" class="mb-16"><template #header><strong>原确认待核对</strong></template><template v-if="pending"><p>问题 {{pending.caseId}} · {{pending.operation==='ASSIGN'?'分配员工':'答复客户'}}</p><p class="case-text">{{pending.command.body||'分配至员工 '+pending.command.assignedStaffId}}</p><p v-if="pending.command.sourceText" class="case-text muted">{{pending.command.sourceText}}</p><div class="case-actions"><el-button :disabled="busy||blocked" @click="recover('query')">核对原结果</el-button><el-button :disabled="busy||blocked" @click="recover('retry')">重试原确认</el-button><el-button :disabled="busy" @click="openOriginal">查看原问题</el-button></div><p class="muted">问题已更新时，可核对新版本后结束过时确认；单凭“尚未找到”不能结束。</p><el-checkbox v-model="endConfirmed" :disabled="busy">我确认核对并结束过时确认</el-checkbox><el-button link :disabled="busy||blocked||!endConfirmed" @click="recover('end')">核对并结束</el-button></template><p v-if="blocked">本机原稿未能可靠核实，不能覆盖或另发。</p><el-button v-if="blocked" @click="restore">重新读取原稿</el-button></el-card>
 <el-card v-if="canRead" shadow="never"><div class="case-actions mb-16"><el-select v-if="canStoreList" v-model="selectedStore" :disabled="busy" filterable remote :remote-method="searchStores" placeholder="选择门店" @visible-change="(v:boolean)=>v&&searchStores()"><el-option v-if="selectedStore&&!storeOptions.some(s=>s.id===selectedStore)" :value="selectedStore" :label="'门店 '+selectedStore"/><el-option v-for="s in storeOptions" :key="s.id" :value="s.id" :label="s.name"/></el-select><span v-else>本店 {{selectedStore}}</span><el-select v-model="filter" :disabled="busy" placeholder="全部状态"><el-option label="全部状态" value=""/><el-option v-for="s in (['WAITING_REVIEW','COLLECTING','ACTIVE'] as const)" :key="s" :value="s" :label="caseStatusLabel(s)"/></el-select><el-button :loading="loading" @click="loadPage(false)">刷新列表</el-button></div>
 <el-table :data="rows" v-loading="loading" row-key="problem.id"><el-table-column label="客户问题" min-width="260"><template #default="{row}"><p class="case-preview">{{row.problem.question}}</p><span class="muted">{{[row.problem.facts.region,row.problem.facts.crop,row.problem.facts.growthStage].filter(Boolean).join(' · ')||'客户尚未补充农情'}}</span></template></el-table-column><el-table-column label="状态" width="130"><template #default="{row}">{{caseStatusLabel(row.problem.status)}}</template></el-table-column><el-table-column label="处理员工" width="130"><template #default="{row}">{{row.assignedStaffId?'员工 '+row.assignedStaffId:'待分配'}}</template></el-table-column><el-table-column label="更新时间" width="175"><template #default="{row}">{{row.problem.updatedAt.replace('T',' ')}}</template></el-table-column><el-table-column label="操作" width="90" fixed="right"><template #default="{row}"><el-button link type="primary" :disabled="busy" @click="openRow(row)">查看处理</el-button></template></el-table-column><template #empty><span>{{loaded?'当前筛选下没有可查看的问题。':'列表尚未读取，请刷新重试。'}}</span></template></el-table><el-button v-if="nextAfter" class="mt-16" :loading="loading" @click="loadPage(true)">加载更多</el-button></el-card>
 <el-drawer v-model="drawer" title="查看与处理农技问题" size="min(760px, 100vw)" :close-on-click-modal="false"><div v-loading="detailLoading"><template v-if="detail"><div class="case-heading"><h3>客户问题</h3><el-tag>{{caseStatusLabel(detail.work.problem.status)}}</el-tag></div><p class="case-text">{{detail.work.problem.question}}</p><el-descriptions :column="1" border><el-descriptions-item label="地区">{{detail.work.problem.facts.region||'未提供'}}</el-descriptions-item><el-descriptions-item label="作物">{{detail.work.problem.facts.crop||'未提供'}}</el-descriptions-item><el-descriptions-item label="阶段">{{detail.work.problem.facts.growthStage||'未提供'}}</el-descriptions-item><el-descriptions-item label="处理员工">{{detail.work.assignedStaffId?'员工 '+detail.work.assignedStaffId:'待分配'}}</el-descriptions-item></el-descriptions><el-button class="mt-16" :disabled="busy" @click="openRow(detail.work)">刷新当前情况</el-button>
 <section v-if="detail.latestReply" class="case-section"><h3>最近门店答复</h3><el-alert v-if="detail.latestReply.factsVersion!==detail.work.problem.factsVersion" title="客户后来补充了情况，此答复针对此前情况。" type="warning" :closable="false"/><p class="case-text">{{detail.latestReply.body}}</p><p class="case-text muted">依据与适用条件：{{detail.latestReply.sourceText}}</p></section>
 <section class="case-section"><div v-if="!editing" class="case-actions"><el-button v-if="canAssign" :disabled="busy||!!pending||blocked" @click="choose('ASSIGN')">分配员工</el-button><el-button v-if="canReply" type="primary" :disabled="busy||!!pending||blocked" @click="choose('HUMAN_REPLY')">答复客户</el-button></div>
 <el-form v-if="editing" label-position="top"><h3>{{editing.operation==='ASSIGN'?'分配处理员工':'答复客户'}}</h3><template v-if="editing.operation==='ASSIGN'"><el-form-item v-if="canStaffList" label="本店在职员工"><el-select v-model="target" :disabled="busy||!!pending" filterable remote :remote-method="searchStaff" placeholder="搜索员工姓名"><el-option v-for="s in staffOptions" :key="s.id" :value="s.id" :label="s.name"/></el-select></el-form-item><el-button v-if="service.current()?.storeId===detail.work.problem.storeId" :disabled="busy||!!pending" @click="target=service.current()!.staffId">由我处理</el-button><p class="muted">接手人须有农技问题查询权限，由服务端核实。{{!canStaffList?'你没有员工列表权限，可由自己处理或联系管理员分配。':''}}</p></template><el-form-item :label="editing.operation==='ASSIGN'?'分配说明（可选）':'答复内容'"><el-input v-model="body" type="textarea" :rows="5" maxlength="4000" show-word-limit :disabled="busy||!!pending"/></el-form-item><el-form-item v-if="editing.operation==='HUMAN_REPLY'" label="判断依据、适用条件与注意事项（必填）"><el-input v-model="source" type="textarea" :rows="3" maxlength="2000" show-word-limit :disabled="busy||!!pending"/></el-form-item><el-checkbox v-model="confirmed" :disabled="busy||!!pending">我已核对当前农情、补充记录和本次{{editing.operation==='ASSIGN'?'分配':'答复'}}</el-checkbox><div class="case-actions mt-16"><el-button :disabled="busy||!!pending" @click="cancelEdit">取消编辑</el-button><el-button type="primary" :loading="busy" :disabled="!confirmed||!!pending||blocked||(editing.operation==='ASSIGN'?!target:!body.trim()||!source.trim())" @click="submit">确认{{editing.operation==='ASSIGN'?'分配':'发送答复'}}</el-button></div></el-form></section>
 <section class="case-section"><h3>沟通记录 <small class="muted">最近在前</small></h3><article v-for="e in entries" :key="e.id" class="case-entry"><div class="case-heading"><strong>{{entryLabel(e)}}</strong><span class="muted">{{e.createdAt.replace('T',' ')}}</span></div><p class="case-text">{{e.body||'已分配处理员工'}}</p><p v-if="e.sourceText" class="case-text muted">依据：{{e.sourceText}}</p></article><el-button v-if="historyAfter" :loading="detailLoading" :disabled="busy" @click="moreHistory">查看较早记录</el-button></section></template><el-empty v-else-if="!detailLoading" description="未能读取当前问题，请关闭后重新进入。"/></div></el-drawer>
</div></template>
<style scoped>.case-actions,.case-heading{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.case-heading{justify-content:space-between}.case-actions :deep(.el-select){width:220px}.case-text{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7}.case-preview{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;white-space:pre-wrap;overflow-wrap:anywhere}.muted{color:var(--el-text-color-secondary)}.case-section{margin-top:24px;padding-top:16px;border-top:1px solid var(--el-border-color-lighter)}.case-entry{padding:16px 0;border-bottom:1px solid var(--el-border-color-lighter)}.case-entry:first-of-type{padding-top:0}.case-workspace h3{font-size:16px}</style>
