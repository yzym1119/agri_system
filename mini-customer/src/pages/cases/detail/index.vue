<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { isPositiveLongId } from '@agri/mobile-contracts/ids';
import type { CaseDetail, CaseReceipt, CaseOperation } from '@agri/mobile-contracts/customer-cases';
import type { PendingCase } from '@agri/mobile-contracts/customer-case-submission';
import { customerAuth, customerCases, caseSubmissions, caseErrorText } from '../../../services/runtime';
import { largeText } from '../../../services/preferences';
const snapshot = ref(customerAuth.current()), caseId = ref(''), record = ref<CaseDetail | null>(null);
const loading = ref(false), busy = ref(false), error = ref(''), stale = ref(false), pending = ref<PendingCase | null>(null), blocked = ref(false);
const body = ref(''), region = ref(''), crop = ref(''), growthStage = ref(''), moreFacts = ref(false), factsEdited = ref(false);
const showHistory = ref(false), expandedQuestion = ref(false);
const latestReply = computed(() => record.value?.latestReply ?? null);
const locked = computed(() => !record.value || loading.value || busy.value || stale.value || blocked.value || pending.value !== null);
let visible = false, generation = 0, lastOwner = '';
let rejectedOriginal = false;
let lastSent: { epoch: number; caseId: string; body: string; result?: CaseReceipt } | null = null;
function active(t: number, epoch: number) { return visible && generation === t && customerAuth.current().epoch === epoch; }
function label(status: string) { return status === 'WAITING_REVIEW' ? '等待门店回复' : status === 'ACTIVE' ? '门店已回复' : '情况已保存，可继续补充'; }
function entryLabel(type: string) { return type === 'HUMAN_REPLY' ? '门店答复' : type === 'SUPPLEMENT' ? '我补充的情况' : type === 'HUMAN_REQUEST' ? '我已请求门店协助' : '我的问题'; }
function restore() {
  rejectedOriginal = false;
  try {
    const previous = pending.value, value = caseSubmissions.current();
    let resolved: CaseReceipt | null = null;
    if (previous && value?.command.requestKey !== previous.command.requestKey) {
      const outcome = caseSubmissions.settled(previous);
      if (!outcome) throw Error('STORAGE_RECORD_CHANGED');
      if (outcome.status === 'COMPLETED') {
        resolved = outcome.receipt;
        if (previous.caseId === caseId.value && previous.operation === 'SUPPLEMENT' && body.value === previous.command.body) { body.value = ''; factsEdited.value = false; }
      } else { rejectedOriginal = true; error.value = '这次没有发送成功，可以核对内容后重新发送。'; }
    }
    pending.value = value; blocked.value = false; return resolved;
  } catch (e) { blocked.value = true; error.value = caseErrorText(e); return null; }
}
onLoad((params: Record<string, unknown> | undefined) => { caseId.value = isPositiveLongId(params?.id) ? params.id : ''; });
async function load(more = false) {
  if (!visible || !caseId.value || snapshot.value.kind !== 'CUSTOMER' || more && loading.value) return;
  if (more && !record.value?.nextAfter) return;
  const t = ++generation, epoch = snapshot.value.epoch, id = caseId.value, previous = record.value;
  loading.value = true; error.value = '';
  if (!more) { record.value = null; stale.value = false; }
  try {
    const next = await customerCases.detail(id, more ? previous?.nextAfter ?? undefined : undefined, epoch);
    if (active(t, epoch) && caseId.value === id) {
      if (more && previous && previous.problem.version !== next.problem.version) {
        stale.value = true; error.value = '问题有新情况或新回复，请刷新后再继续。'; return;
      }
      record.value = more && previous ? { ...next, entries: [...previous.entries, ...next.entries] } : next;
      if (!factsEdited.value) { region.value = next.problem.facts.region; crop.value = next.problem.facts.crop; growthStage.value = next.problem.facts.growthStage; }
    }
  } catch (e) {
    if (active(t, epoch)) error.value = caseErrorText(e);
    else if (visible && generation === t && customerAuth.current().kind === 'GUEST') { snapshot.value = customerAuth.current(); record.value = null; body.value = ''; pending.value = null; }
  } finally { if (active(t, epoch)) loading.value = false; }
}
onShow(async () => {
  visible = true; ++generation; snapshot.value = customerAuth.current(); loading.value = false; busy.value = false; error.value = ''; record.value = null; stale.value = false;
  const owner = snapshot.value.kind + ':' + snapshot.value.epoch + ':' + caseId.value;
  if (owner !== lastOwner) { body.value = ''; region.value = ''; crop.value = ''; growthStage.value = ''; pending.value = null; blocked.value = false; factsEdited.value = false; lastOwner = owner; }
  if (!caseId.value) { error.value = '问题编号无效，请返回重新选择。'; return; }
  if (snapshot.value.kind === 'CUSTOMER') {
    if (lastSent?.epoch === snapshot.value.epoch && lastSent.caseId === caseId.value && lastSent.result && body.value === lastSent.body) { body.value = ''; factsEdited.value = false; lastSent = null; }
    restore(); await load();
  }
});
function hide() { visible = false; ++generation; loading.value = false; }
onHide(hide); onUnload(hide);
function openLogin() { uni.navigateTo({ url: snapshot.value.kind === 'ONBOARDING' && snapshot.value.state === 'PHONE_VERIFIED' ? '/pages/identity/claim/index' : '/pages/login/index' }); }
function openList() { uni.navigateTo({ url: '/pages/cases/list/index' }); }
function openOriginal() {
  if (pending.value?.operation === 'CREATE') uni.reLaunch({ url: '/pages/ask/index' });
  else if (pending.value?.caseId && pending.value.caseId !== caseId.value) uni.navigateTo({ url: '/pages/cases/detail/index?id=' + pending.value.caseId });
}
function failure(e: unknown, t: number, epoch: number) {
  if (!visible || generation !== t) return;
  if (customerAuth.current().epoch !== epoch) {
    if (customerAuth.current().kind === 'GUEST') { snapshot.value = customerAuth.current(); record.value = null; body.value = ''; pending.value = null; error.value = '请重新登录本人账号，原发送记录会保留。'; }
    return;
  }
  error.value = caseErrorText(e); restore();
}
async function send(operation: Exclude<CaseOperation, 'CREATE'>) {
  if (!visible || locked.value || snapshot.value.kind !== 'CUSTOMER' || !record.value) return;
  if (operation === 'SUPPLEMENT' && !body.value.trim()) { error.value = '先填写想补充的情况。'; return; }
  const t = generation, epoch = snapshot.value.epoch, original = record.value.problem;
  const sent: { epoch: number; caseId: string; body: string; result?: CaseReceipt } = { epoch, caseId: original.id, body: body.value }; lastSent = sent;
  busy.value = true; error.value = '';
  try {
    const result = await caseSubmissions.submit({ operation, caseId: original.id, expectedVersion: original.version, expectedFactsVersion: original.factsVersion,
      body: operation === 'SUPPLEMENT' ? body.value : '希望门店协助处理这个问题',
      ...(operation === 'SUPPLEMENT' ? { facts: { region: region.value, crop: crop.value, growthStage: growthStage.value } } : {}) });
    if (customerAuth.current().epoch === epoch) sent.result = result;
    if (active(t, epoch)) { pending.value = null; if (operation === 'SUPPLEMENT') body.value = ''; factsEdited.value = false; busy.value = false; await load(); }
  } catch (e) { failure(e, t, epoch); }
  finally { if (active(t, epoch)) busy.value = false; }
}
function supplement() { return send('SUPPLEMENT'); }
function askHuman() { return send('HUMAN_REQUEST'); }
async function recover(retry: boolean) {
  if (!visible || busy.value || snapshot.value.kind !== 'CUSTOMER') return;
  const t = generation, epoch = snapshot.value.epoch; busy.value = true; error.value = '';
  try {
    const settled = restore(); if (blocked.value || rejectedOriginal) return;
    const result = settled ?? await (retry ? caseSubmissions.retry() : caseSubmissions.query());
    if (active(t, epoch)) {
      if (result) {
        pending.value = null; busy.value = false;
        if (result.caseId === caseId.value) { body.value = ''; factsEdited.value = false; await load(); }
        else uni.navigateTo({ url: '/pages/cases/detail/index?id=' + result.caseId });
      } else {
        restore();
        if (!pending.value && lastSent?.epoch === epoch && lastSent.caseId === caseId.value && lastSent.result) { body.value = ''; factsEdited.value = false; busy.value = false; await load(); }
        else error.value = '暂未找到确认结果，原内容已保留，请稍后再核对。';
      }
    }
  } catch (e) { failure(e, t, epoch); }
  finally { if (active(t, epoch)) busy.value = false; }
}
function checkOriginal() { return recover(false); }
function retryOriginal() { return recover(true); }
</script>
<template>
  <view class="customer-page with-navigation" :class="{ 'large-text': largeText }">
    <CustomerNavigation active="ask" /><ReadingHeader title="问题与回复" />
    <button class="button button-secondary" hover-class="button-pressed" @tap="openList">我问过的问题</button>
    <view v-if="snapshot.kind !== 'CUSTOMER'" class="section"><text class="paragraph">请登录本人账号，再查看问题和门店回复。</text><button class="button" hover-class="button-pressed" @tap="openLogin">登录查看</button></view>
    <template v-else>
      <CasePending v-if="pending || blocked" :pending="pending" :blocked="blocked" :busy="busy" :can-open="pending?.operation === 'CREATE' || !!pending?.caseId && pending.caseId !== caseId" @check="checkOriginal" @retry="retryOriginal" @open="openOriginal" />
      <text v-if="loading && !record" class="loading">正在读取问题…</text>
      <view v-if="error" class="notice" role="alert">{{ error }}<button class="button button-secondary" hover-class="button-pressed" :disabled="loading || busy || !caseId" @tap="load(false)">刷新问题与回复</button></view>
      <template v-if="record">
        <view class="section"><text class="status">{{ label(record.problem.status) }}</text><text class="case-message" :class="{ 'case-preview': !expandedQuestion }">{{ record.problem.question }}</text><button v-if="record.problem.question.length > 60" class="button button-secondary" hover-class="button-pressed" @tap="expandedQuestion = !expandedQuestion">{{ expandedQuestion ? '收起我的问题' : '展开我的问题' }}</button><text class="help">提交于 {{ record.problem.createdAt.slice(0, 16).replace('T', ' ') }}</text></view>
        <view v-if="latestReply" class="case-entry case-entry-reply"><text class="panel-title">{{ record.problem.status === 'WAITING_REVIEW' ? '门店上次答复' : '门店最新答复' }}</text><text v-if="latestReply.factsVersion !== record.problem.factsVersion" class="notice case-block">此答复针对此前情况。你已补充新情况，请等门店进一步确认。</text><text class="case-message">{{ latestReply.body }}</text><text class="help case-message">答复依据：{{ latestReply.sourceText }}</text><text class="help">{{ latestReply.createdAt.slice(0, 16).replace('T', ' ') }}</text></view>
        <text v-if="record.problem.status === 'WAITING_REVIEW'" class="paragraph secondary">门店还未回复，可以继续补充现场情况。着急时请到首页联系门店。</text>
        <button class="button button-secondary" hover-class="button-pressed" :disabled="loading || busy" @tap="load(false)">刷新回复</button>
        <view class="section">
          <text class="panel-title">接着补充情况</text><text class="paragraph secondary">文字会接在原问题后面，之前的内容会保留。</text>
          <textarea v-model="body" class="case-textarea" :maxlength="4000" :disabled="locked" placeholder="有什么新情况，或门店需要了解的信息？" aria-label="补充情况" />
          <button class="button button-secondary" hover-class="button-pressed" :disabled="locked" @tap="moreFacts = !moreFacts">{{ moreFacts ? '收起种植情况' : '确认地区、作物和阶段' }}</button>
          <view v-if="moreFacts"><text class="field-label">种植地区</text><input v-model="region" class="search-input" :maxlength="100" :disabled="locked" placeholder="不清楚可留空" @input="factsEdited = true" /><text class="field-label">作物名称</text><input v-model="crop" class="search-input" :maxlength="50" :disabled="locked" placeholder="不清楚可留空" @input="factsEdited = true" /><text class="field-label">生长阶段</text><input v-model="growthStage" class="search-input" :maxlength="100" :disabled="locked" placeholder="不清楚可留空" @input="factsEdited = true" /></view>
          <button class="button" hover-class="button-pressed" :disabled="locked" @tap="supplement">{{ busy ? '正在发送…' : '发送补充' }}</button>
          <button v-if="record.problem.status !== 'WAITING_REVIEW'" class="button button-secondary" hover-class="button-pressed" :disabled="locked" @tap="askHuman">请门店帮忙</button>
        </view>
        <button class="button button-secondary" hover-class="button-pressed" @tap="showHistory = !showHistory">{{ showHistory ? '收起之前的对话' : '查看之前的对话' }}</button>
        <view v-if="showHistory" class="section"><text class="panel-title">之前说过的情况与回复</text><text class="help">最新内容在前。门店答复会注明依据。</text>
          <view v-for="entry in record.entries" :key="entry.id" class="case-entry" :class="{ 'case-entry-reply': entry.type === 'HUMAN_REPLY' }"><text class="status">{{ entryLabel(entry.type) }}</text><text v-if="entry.type === 'HUMAN_REPLY' && entry.factsVersion !== record.problem.factsVersion" class="help">这是针对此前情况的答复。</text><text class="case-message">{{ entry.body }}</text><text v-if="entry.sourceText" class="help case-message">答复依据：{{ entry.sourceText }}</text><text class="help">{{ entry.createdAt.slice(0, 16).replace('T', ' ') }}</text></view>
          <button v-if="record.nextAfter" class="button button-secondary" hover-class="button-pressed" :disabled="loading || busy || stale" @tap="load(true)">{{ loading ? '正在读取…' : '更早的内容' }}</button>
        </view>
      </template>
    </template>
  </view>
</template>
