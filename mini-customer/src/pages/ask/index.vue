<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import type { PendingCase } from '@agri/mobile-contracts/customer-case-submission';
import type { CaseReceipt } from '@agri/mobile-contracts/customer-cases';
import type { PublicStore } from '@agri/mobile-contracts/customer-catalog';
import { customerAuth, caseSubmissions, publicCatalog, serviceConfigured, caseErrorText } from '../../services/runtime';
import { largeText } from '../../services/preferences';
const snapshot = ref(customerAuth.current()), pending = ref<PendingCase | null>(null), blocked = ref(false);
const busy = ref(false), error = ref(''), stores = ref<PublicStore[]>([]), storeLoading = ref(false), storeError = ref(''), nextAfter = ref<string | null>(null);
const storesLoaded = ref(false);
const question = ref(''), region = ref(''), crop = ref(''), growthStage = ref(''), selectedStoreId = ref(''), moreFacts = ref(false), completed = ref<CaseReceipt | null>(null);
const locked = computed(() => busy.value || blocked.value || pending.value !== null);
const storeName = computed(() => stores.value.find(s => s.storeId === selectedStoreId.value)?.name || (pending.value?.operation === 'CREATE' ? '原提交门店' : '请选择门店'));
let visible = false, generation = 0, lastOwner = '';
let rejectedOriginal = false;
let attempt: { epoch: number; result?: CaseReceipt; rejected?: boolean } | null = null;
function live(t: number) { return visible && generation === t; }
function active(t: number, epoch: number) { return live(t) && customerAuth.current().epoch === epoch; }
function clearForm() { question.value = ''; region.value = ''; crop.value = ''; growthStage.value = ''; selectedStoreId.value = ''; }
function restore() {
  rejectedOriginal = false;
  try {
    const previous = pending.value, value = caseSubmissions.current();
    let resolved: CaseReceipt | null = null;
    if (previous && value?.command.requestKey !== previous.command.requestKey) {
      const outcome = caseSubmissions.settled(previous);
      if (!outcome) throw Error('STORAGE_RECORD_CHANGED');
      if (outcome.status === 'COMPLETED') {
        resolved = outcome.receipt; completed.value = resolved;
        if (previous.operation === 'CREATE') clearForm();
        attempt = null;
      } else { rejectedOriginal = true; error.value = '这次没有发送成功，可以核对填写内容后重新发送。'; }
    }
    pending.value = value; blocked.value = false;
    if (value?.operation === 'CREATE') {
      question.value = value.command.question; selectedStoreId.value = value.command.storeId;
      region.value = value.command.facts.region; crop.value = value.command.facts.crop; growthStage.value = value.command.facts.growthStage;
    }
    if (!value && attempt?.epoch === snapshot.value.epoch && attempt.result) {
      completed.value = attempt.result; clearForm(); attempt = null;
    }
    return resolved;
  } catch (e) { blocked.value = true; error.value = caseErrorText(e); return null; }
}
async function loadStores(more = false) {
  if (!visible || !serviceConfigured || storeLoading.value && more) return;
  const t = generation, epoch = snapshot.value.epoch;
  storeLoading.value = true; storeError.value = '';
  if (!more) { stores.value = []; nextAfter.value = null; storesLoaded.value = false; }
  try {
    const page = await publicCatalog.stores(more ? nextAfter.value ?? undefined : undefined);
    if (active(t, epoch)) {
      stores.value = more ? [...stores.value, ...page.rows] : page.rows; nextAfter.value = page.nextAfter; storesLoaded.value = true;
      if (!pending.value && !stores.value.some(s => s.storeId === selectedStoreId.value)) selectedStoreId.value = '';
    }
  } catch { if (active(t, epoch)) storeError.value = '没有读到门店，请稍后再试。'; }
  finally { if (active(t, epoch)) storeLoading.value = false; }
}
onShow(async () => {
  visible = true; ++generation; snapshot.value = customerAuth.current(); busy.value = false; error.value = '';
  const owner = snapshot.value.kind + ':' + snapshot.value.epoch;
  if (owner !== lastOwner) { clearForm(); pending.value = null; blocked.value = false; completed.value = null; lastOwner = owner; }
  if (snapshot.value.kind === 'CUSTOMER') { restore(); await loadStores(); }
});
function hide() { visible = false; ++generation; storeLoading.value = false; }
onHide(hide); onUnload(hide);
function openLogin() { uni.navigateTo({ url: snapshot.value.kind === 'ONBOARDING' && snapshot.value.state === 'PHONE_VERIFIED' ? '/pages/identity/claim/index' : '/pages/login/index' }); }
function openList() { uni.navigateTo({ url: '/pages/cases/list/index' }); }
function openOriginal() {
  const id = pending.value?.caseId ?? completed.value?.caseId;
  if (id) uni.navigateTo({ url: '/pages/cases/detail/index?id=' + id });
}
function chooseStore(event: { detail: { value: string | number } }) {
  if (locked.value) return;
  const n = Number(event.detail.value); if (Number.isInteger(n) && stores.value[n]) selectedStoreId.value = stores.value[n]!.storeId;
}
function failure(e: unknown, t: number, epoch: number) {
  if (!live(t)) return;
  if (customerAuth.current().epoch !== epoch) {
    if (customerAuth.current().kind === 'GUEST') { snapshot.value = customerAuth.current(); pending.value = null; clearForm(); error.value = '请重新登录本人账号，原发送记录会保留。'; }
    return;
  }
  error.value = caseErrorText(e); restore();
}
async function submit() {
  if (locked.value || snapshot.value.kind !== 'CUSTOMER' || !visible) return;
  if (!question.value.trim()) { error.value = '先说说遇到了什么问题。'; return; }
  if (!stores.value.some(s => s.storeId === selectedStoreId.value)) { error.value = '请选择接收问题的门店。'; return; }
  const t = generation, epoch = snapshot.value.epoch; busy.value = true; error.value = ''; completed.value = null;
  const original: { epoch: number; result?: CaseReceipt } = { epoch }; attempt = original;
  try {
    const result = await caseSubmissions.submit({ operation: 'CREATE', storeId: selectedStoreId.value, question: question.value,
      facts: { region: region.value, crop: crop.value, growthStage: growthStage.value }, requestHuman: true });
    if (customerAuth.current().epoch === epoch) original.result = result;
    if (active(t, epoch)) { pending.value = null; clearForm(); uni.navigateTo({ url: '/pages/cases/detail/index?id=' + result.caseId }); }
  } catch (e) { failure(e, t, epoch); }
  finally { if (active(t, epoch)) busy.value = false; }
}
async function recover(retry: boolean) {
  if (busy.value || snapshot.value.kind !== 'CUSTOMER' || !visible) return;
  const t = generation, epoch = snapshot.value.epoch; busy.value = true; error.value = '';
  try {
    const settled = restore(); if (blocked.value || rejectedOriginal) return;
    const result = settled ?? await (retry ? caseSubmissions.retry() : caseSubmissions.query());
    if (active(t, epoch)) {
      if (result) { pending.value = null; clearForm(); completed.value = result; uni.navigateTo({ url: '/pages/cases/detail/index?id=' + result.caseId }); }
      else { restore(); if (!completed.value) error.value = '暂未找到确认结果，原内容已保留，请稍后再核对。'; }
    }
  } catch (e) { failure(e, t, epoch); }
  finally { if (active(t, epoch)) busy.value = false; }
}
function checkOriginal() { return recover(false); }
function retryOriginal() { return recover(true); }
</script>
<template>
  <view class="customer-page with-navigation" :class="{ 'large-text': largeText }">
    <CustomerNavigation active="ask" /><ReadingHeader title="问农技" />
    <text class="title">遇到问题，<text class="title-line">先说说情况。</text></text>
    <text class="paragraph secondary">当前由门店回复。说不清楚的地方可以稍后补充，也可以到首页联系门店。</text>
    <text v-if="!serviceConfigured" class="notice case-block">门店服务暂未连接，请先到首页联系门店。</text>
    <button class="button button-secondary" hover-class="button-pressed" @tap="openList">我问过的问题 · 查看回复</button>
    <view v-if="snapshot.kind !== 'CUSTOMER'" class="section">
      <text class="paragraph">登录并核对本人档案后，问题和门店回复会保存在一起。</text>
      <button class="button" hover-class="button-pressed" @tap="openLogin">登录并核对本人档案</button>
    </view>
    <template v-else>
      <view v-if="completed" class="panel"><text class="status">上次问题已发送</text><button class="button" hover-class="button-pressed" @tap="openOriginal">查看问题与回复</button></view>
      <CasePending v-if="pending || blocked" :pending="pending" :blocked="blocked" :busy="busy" :can-open="!!pending?.caseId" @check="checkOriginal" @retry="retryOriginal" @open="openOriginal" />
      <text v-if="error" class="notice case-block" role="alert">{{ error }}</text>
      <text class="field-label">遇到了什么问题？</text>
      <textarea v-model="question" class="case-textarea" :disabled="locked" :maxlength="2000" placeholder="例如：哪种作物、哪里不对劲、什么时候开始的。" aria-label="问题描述" />
      <text class="field-label">发给哪家门店？</text>
      <text v-if="storeLoading" class="loading">正在读取门店…</text>
      <view v-if="storeError" class="notice">{{ storeError }}<button class="button button-secondary" hover-class="button-pressed" :disabled="busy" @tap="loadStores(false)">重新读取门店</button></view>
      <picker v-if="stores.length" :range="stores" range-key="name" :disabled="locked" @change="chooseStore"><view class="selection-input">{{ storeName }} <text aria-hidden="true">⌄</text></view></picker>
      <text v-else-if="storesLoaded && !storeLoading && !storeError" class="help">暂时没有可接收问题的门店，请到首页联系门店。</text>
      <button v-if="nextAfter" class="button button-secondary" hover-class="button-pressed" :disabled="storeLoading || locked" @tap="loadStores(true)">查找更多门店</button>
      <button class="button button-secondary" hover-class="button-pressed" :disabled="locked" @tap="moreFacts = !moreFacts">{{ moreFacts ? '收起种植情况' : '补充种植情况（可以稍后填）' }}</button>
      <view v-if="moreFacts">
        <text class="field-label">在哪个地方种植？</text><input v-model="region" class="search-input" :maxlength="100" :disabled="locked" placeholder="村、乡镇或所在地区，不清楚可不填" />
        <text class="field-label">种的是什么？</text><input v-model="crop" class="search-input" :maxlength="50" :disabled="locked" placeholder="作物名称，不清楚可不填" />
        <text class="field-label">长到什么阶段了？</text><input v-model="growthStage" class="search-input" :maxlength="100" :disabled="locked" placeholder="例如苗期，不清楚可不填" />
      </view>
      <button class="button" hover-class="button-pressed" :disabled="locked || storeLoading || !serviceConfigured" @tap="submit">{{ busy ? '正在发送…' : '发给门店' }}</button>
      <text class="help">系统保存问题后，可在“我问过的问题”查看回复和继续补充。</text>
    </template>
  </view>
</template>
