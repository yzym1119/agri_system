<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import type { CaseSummary, CaseStatus } from '@agri/mobile-contracts/customer-cases';
import { isPositiveLongId } from '@agri/mobile-contracts/ids';
import { customerAuth, customerCases, caseErrorText } from '../../../services/runtime';
import { largeText } from '../../../services/preferences';
const snapshot = ref(customerAuth.current()), rows = ref<CaseSummary[]>([]), loading = ref(false), loaded = ref(false), error = ref(''), nextAfter = ref<string | null>(null);
const filter = ref<CaseStatus | ''>('');
const filters: { value: CaseStatus | ''; label: string }[] = [{ value: '', label: '全部' }, { value: 'WAITING_REVIEW', label: '等门店回复' }, { value: 'ACTIVE', label: '门店已回复' }, { value: 'COLLECTING', label: '已保存情况' }];
let visible = false, generation = 0;
function active(t: number, epoch: number) { return visible && generation === t && customerAuth.current().epoch === epoch; }
function label(state: CaseStatus) { return state === 'WAITING_REVIEW' ? '等待门店回复' : state === 'ACTIVE' ? '门店已回复' : '情况已保存'; }
async function load(more = false) {
  if (!visible || more && loading.value || snapshot.value.kind !== 'CUSTOMER') return;
  const t = ++generation, epoch = snapshot.value.epoch, selected = filter.value;
  loading.value = true; error.value = '';
  if (!more) { rows.value = []; nextAfter.value = null; loaded.value = false; }
  try {
    const page = await customerCases.page(more ? nextAfter.value ?? undefined : undefined, selected || undefined, epoch);
    if (active(t, epoch)) { rows.value = more ? [...rows.value, ...page.rows] : page.rows; nextAfter.value = page.nextAfter; loaded.value = true; }
  } catch (e) {
    if (active(t, epoch)) error.value = caseErrorText(e);
    else if (visible && generation === t && customerAuth.current().kind === 'GUEST') { snapshot.value = customerAuth.current(); rows.value = []; error.value = '请重新登录本人账号。'; }
  } finally { if (active(t, epoch)) loading.value = false; }
}
onShow(async () => { visible = true; ++generation; snapshot.value = customerAuth.current(); rows.value = []; loaded.value = false; loading.value = false; nextAfter.value = null; error.value = ''; if (snapshot.value.kind === 'CUSTOMER') await load(); });
function hide() { visible = false; ++generation; loading.value = false; }
onHide(hide); onUnload(hide);
function select(value: CaseStatus | '') { if (filter.value !== value) { filter.value = value; return load(); } }
function openProblem(id: string) { if (isPositiveLongId(id)) uni.navigateTo({ url: '/pages/cases/detail/index?id=' + id }); }
function newProblem() { uni.reLaunch({ url: '/pages/ask/index' }); }
function openLogin() { uni.navigateTo({ url: snapshot.value.kind === 'ONBOARDING' && snapshot.value.state === 'PHONE_VERIFIED' ? '/pages/identity/claim/index' : '/pages/login/index' }); }
</script>
<template>
  <view class="customer-page with-navigation" :class="{ 'large-text': largeText }">
    <CustomerNavigation active="ask" /><ReadingHeader title="我问过的问题" />
    <text class="title">问题和回复，<text class="title-line">都在这里。</text></text>
    <button class="button" hover-class="button-pressed" @tap="newProblem">问一个新问题</button>
    <view v-if="snapshot.kind !== 'CUSTOMER'" class="section"><text class="paragraph">登录本人账号后，才能看到自己问过的问题。</text><button class="button button-secondary" hover-class="button-pressed" @tap="openLogin">登录查看</button></view>
    <template v-else>
      <view class="case-filters" role="group" aria-label="按回复情况筛选"><button v-for="item in filters" :key="item.value" class="case-filter" :class="{ 'case-filter-active': filter === item.value }" :aria-pressed="filter === item.value" hover-class="button-pressed" @tap="select(item.value)">{{ item.label }}</button></view>
      <text v-if="loading && !rows.length" class="loading">正在读取问题…</text>
      <view v-if="error" class="notice" role="alert">{{ error }}<button class="button button-secondary" hover-class="button-pressed" :disabled="loading" @tap="load(false)">重新读取</button></view>
      <text v-if="loaded && !loading && !error && !rows.length" class="paragraph secondary">{{ filter ? '这里暂时没有这一类问题，可以换个筛选再看看。' : '还没有提交过问题。遇到种植问题时，可以先说说情况。' }}</text>
      <button v-for="item in rows" :key="item.id" class="catalog-row case-row" hover-class="button-pressed" @tap="openProblem(item.id)"><text class="status">{{ label(item.status) }}</text><text class="case-preview">{{ item.question }}</text><text class="help">最近更新 {{ item.updatedAt.replace('T', ' ') }}</text><text class="case-open">查看问题与回复 ›</text></button>
      <button v-if="nextAfter" class="button button-secondary" hover-class="button-pressed" :disabled="loading" @tap="load(true)">{{ loading ? '正在读取…' : '更早的问题' }}</button>
    </template>
  </view>
</template>
